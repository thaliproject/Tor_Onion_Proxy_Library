/*
Copyright (C) 2011-2014 Sublime Software Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

THIS CODE IS PROVIDED ON AN *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED,
INCLUDING WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A PARTICULAR PURPOSE,
MERCHANTABLITY OR NON-INFRINGEMENT.

See the Apache 2 License for the specific language governing permissions and limitations under the License.
*/

package com.msopentech.thali.toronionproxy;

import net.freehaven.tor.control.ConfigEntry;
import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.msopentech.thali.toronionproxy.FileUtilities.setToReadOnlyPermissions;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This is where all the fun is, this is the class that handles the heavy work. Note that you will most likely need
 * to actually call into the AndroidOnionProxyManager or JavaOnionProxyManager in order to create the right bindings
 * for your environment.
 * <p>
 * This class is thread safe but that's mostly because we hit everything over the head with 'synchronized'. Given the
 * way this class is used there shouldn't be any performance implications of this.
 * <p>
 * This class began life as TorPlugin from the Briar Project
 */
public class OnionProxyManager {
    private static final String[] EVENTS = {
            "CIRC", "ORCONN", "NOTICE", "WARN", "ERR", "BW", "STATUS_CLIENT"
    };

    private static final String OWNER = "__OwningControllerProcess";
    private static final int COOKIE_TIMEOUT = 10 * 1000; // Milliseconds
    private static final int HOSTNAME_TIMEOUT = 30 * 1000; // Milliseconds
    private static final Logger LOG = LoggerFactory.getLogger(OnionProxyManager.class);

    private final OnionProxyContext onionProxyContext;
    private final EventBroadcaster eventBroadcaster;
    private final EventHandler eventHandler;
    private final TorConfig config;
    private final TorInstaller torInstaller;

    private volatile Socket controlSocket = null;

    // If controlConnection is not null then this means that a connection exists and the Tor OP will die when
    // the connection fails.
    private volatile TorControlConnection controlConnection = null;
    private volatile int control_port;

    public OnionProxyManager(OnionProxyContext onionProxyContext) {
        this(onionProxyContext, null, null);
    }

    /**
     * Constructs an <code>OnionProxyManager</code> with the specified context
     *
     * @param onionProxyContext
     */
    public OnionProxyManager(OnionProxyContext onionProxyContext, EventBroadcaster eventBroadcaster,
                             EventHandler eventHandler) {
        if(onionProxyContext == null) {
            throw new IllegalArgumentException("onionProxyContext is null");
        }
        this.torInstaller = onionProxyContext.getInstaller();
        this.onionProxyContext = onionProxyContext;
        this.config = onionProxyContext.getConfig();
        this.eventBroadcaster = (eventBroadcaster == null) ? new DefaultEventBroadcaster() :
                eventBroadcaster;
        this.eventHandler = (eventHandler == null) ? new OnionProxyManagerEventHandler() :
                eventHandler;
    }

    public final OnionProxyContext getContext() {
        return onionProxyContext;
    }

    /**
     * This is a blocking call that will try to start the Tor OP, connect it to the network and get it to be fully
     * bootstrapped. Sometimes the bootstrap process just hangs for no apparent reason so the method will wait for the
     * given time for bootstrap to finish and if it doesn't then will restart the bootstrap process the given number of
     * repeats.
     *
     * @param secondsBeforeTimeOut Seconds to wait for boot strapping to finish
     * @param numberOfRetries      Number of times to try recycling the Tor OP before giving up on bootstrapping working
     * @return True if bootstrap succeeded, false if there is a problem or the bootstrap couldn't complete in the given
     * time.
     * @throws java.lang.InterruptedException - You know, if we are interrupted
     * @throws java.io.IOException            - IO Exceptions
     */
    public synchronized boolean startWithRepeat(int secondsBeforeTimeOut, int numberOfRetries, boolean enableLogging) throws
            InterruptedException, IOException {
        if (secondsBeforeTimeOut <= 0 || numberOfRetries < 0) {
            throw new IllegalArgumentException("secondsBeforeTimeOut >= 0 & numberOfRetries > 0");
        }

        try {
            for (int retryCount = 0; retryCount < numberOfRetries; ++retryCount) {
                start(enableLogging);

                // We will check every second to see if boot strapping has finally finished
                for (int secondsWaited = 0; secondsWaited < secondsBeforeTimeOut; ++secondsWaited) {
                    if (!isBootstrapped()) {
                        Thread.sleep(1000, 0);
                    } else {
                        eventBroadcaster.broadcastNotice("Tor started; process id = " + getTorPid());
                        return true;
                    }
                }

                // Bootstrapping isn't over so we need to restart and try again
                stop();
                // Experimentally we have found that if a Tor OP has run before and thus has cached descriptors
                // and that when we try to start it again it won't start then deleting the cached data can fix this.
                // But, if there is cached data and things do work then the Tor OP will start faster than it would
                // if we delete everything.
                // So our compromise is that we try to start the Tor OP 'as is' on the first round and after that
                // we delete all the files.
                // It can take a little bit for the Tor OP to detect the connection is dead and kill itself
                Thread.sleep(1000, 0);
                onionProxyContext.deleteDataDir();
            }

            return false;
        } finally {
            // Make sure we return the Tor OP in some kind of consistent state, even if it's 'off'.
            if (!isRunning()) {
                stop();
            }
        }
    }

    /**
     * Returns the socks port on the IPv4 localhost address that the Tor OP is listening on
     *
     * @return Discovered socks port
     * @throws java.io.IOException - File errors
     */
    public synchronized int getIPv4LocalHostSocksPort() throws IOException {
        if (!isRunning()) {
            throw new RuntimeException("Tor is not running!");
        }

        // This returns a set of space delimited quoted strings which could be Ipv4, Ipv6 or unix sockets
        String[] socksIpPorts = controlConnection.getInfo("net/listeners/socks").split(" ");

        for (String address : socksIpPorts) {
            if (address.contains("\"127.0.0.1:")) {
                // Remember, the last character will be a " so we have to remove that
                return Integer.parseInt(address.substring(address.lastIndexOf(":") + 1, address.length() - 1));
            }
        }

        throw new RuntimeException("We don't have an Ipv4 localhost binding for socks!");
    }

    /**
     * Publishes a hidden service
     *
     * @param hiddenServicePort The port that the hidden service will accept connections on
     * @param localPort         The local port that the hidden service will relay connections to
     * @return The hidden service's onion address in the form X.onion.
     * @throws java.io.IOException - File errors
     * @throws IllegalStateException if control service is not running
     */
    public synchronized String publishHiddenService(int hiddenServicePort, int localPort) throws IOException {
        if (controlConnection == null) {
            throw new IllegalStateException("Service is not running.");
        }

        LOG.info("Creating hidden service");
        if(!onionProxyContext.createHostnameFile()) {
            throw new IOException("Could not create hostnameFile");
        }

        // Watch for the hostname file being created/updated
        WriteObserver hostNameFileObserver = onionProxyContext.createHostnameDirObserver();

        File hostnameFile = config.getHostnameFile();
        File hostnameDir = hostnameFile.getParentFile();
        if (!setToReadOnlyPermissions(hostnameDir)) {
            throw new RuntimeException("Unable to set permissions on hostName dir");
        }

        // Use the control connection to update the Tor config
        List<String> config = Arrays.asList(
                "HiddenServiceDir " + hostnameDir.getAbsolutePath(),
                "HiddenServicePort " + hiddenServicePort + " 127.0.0.1:" + localPort);
        controlConnection.setConf(config);
        controlConnection.saveConf();
        // Wait for the hostname file to be created/updated
        if (!hostNameFileObserver.poll(HOSTNAME_TIMEOUT, MILLISECONDS)) {
            FileUtilities.listFilesToLog(hostnameFile.getParentFile());
            throw new RuntimeException("Wait for hidden service hostname file to be created expired.");
        }

        // Publish the hidden service's onion hostname in transport properties
        String hostname = new String(FileUtilities.read(hostnameFile), "UTF-8").trim();
        LOG.info("Hidden service config has completed.");

        return hostname;
    }

    /**
     * Kills the Tor OP Process. Once you have called this method nothing is going to work until you either call
     * startWithRepeat or start
     *
     * @throws java.io.IOException - File errors
     */
    public synchronized void stop() throws IOException {
        try {
            if (controlConnection == null) {
                return;
            }
            LOG.info("Stopping Tor");
            eventBroadcaster.broadcastNotice("Using control port to shutdown Tor");
            controlConnection.setConf("DisableNetwork", "1");
            controlConnection.shutdownTor("HALT");
            eventBroadcaster.broadcastNotice("sending HALT signal to Tor process");
        } finally {
            controlConnection = null;
            if (controlSocket != null) {
                try {
                    controlSocket.close();
                } finally {
                    controlSocket = null;
                }
            }
        }
    }

    /**
     * Checks to see if the Tor OP is running (e.g. fully bootstrapped) and open to network connections.
     *
     * @return True if running
     * @throws java.io.IOException - IO exceptions
     */
    public synchronized boolean isRunning() {
        try {
            return isBootstrapped() && isNetworkEnabled();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Tells the Tor OP if it should accept network connections
     *
     * @param enable If true then the Tor OP will accept SOCKS connections, otherwise not.
     * @throws java.io.IOException - IO exceptions
     */
    public synchronized void enableNetwork(boolean enable) throws IOException {
        if (controlConnection == null) {
            return;
        }
        LOG.info("Enabling network: " + enable);
        controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
    }

    /**
     * Specifies if Tor OP is accepting network connections
     *
     * @return True if network is enabled (that doesn't mean that the device is online, only that the Tor OP is trying
     * to connect to the network)
     * @throws java.io.IOException - IO exceptions
     */
    private synchronized boolean isNetworkEnabled() throws IOException {
        if (controlConnection == null) {
            return false;
        }

        List<ConfigEntry> disableNetworkSettingValues = controlConnection.getConf("DisableNetwork");
        boolean result = false;
        // It's theoretically possible for us to get multiple values back, if even one is false then we will
        // assume all are false
        for (ConfigEntry configEntry : disableNetworkSettingValues) {
            if (configEntry.value.equals("1")) {
                return false;
            } else {
                result = true;
            }
        }
        return result;
    }

    /**
     * Determines if the boot strap process has completed.
     *
     * @return True if complete
     */
    private synchronized boolean isBootstrapped() {
        if (controlConnection == null) {
            return false;
        }

        try {
            String phase = controlConnection.getInfo("status/bootstrap-phase");
            if (phase != null && phase.contains("PROGRESS=100")) {
                LOG.info("Tor has already bootstrapped");
                return true;
            }
        } catch (IOException e) {
            LOG.warn("Control connection is not responding properly to getInfo", e);
        }

        return false;
    }

    /**
     * Starts tor control service if it isn't already running.
     *
     * @param enableLogs enables system output of tor control
     * @return true if tor is already running OR tor has been successfully started
     * @throws IOException
     */
    public synchronized void start(boolean enableLogs) throws IOException {
         if (controlConnection != null) {
            return;
        }

        LOG.info("Starting Tor");
        if(!onionProxyContext.createCookieAuthFile()) {
            eventBroadcaster.broadcastNotice("Tor authentication cookie does not exist yet");
            throw new IOException("Failed to create cookie auth file: "
                    + onionProxyContext.getConfig().getCookieAuthFile().getAbsolutePath());
        }

        File cookieFile = config.getCookieAuthFile();
        WriteObserver cookieObserver = onionProxyContext.createCookieAuthFileObserver();

        // Start a new Tor process
        String torPath = config.getTorExecutableFile().getAbsolutePath();
        String configPath = config.getTorrcFile().getAbsolutePath();
        String pid = onionProxyContext.getProcessId();
        String[] cmd = {torPath, "-f", configPath, OWNER, pid};
        String[] env = getEnvironmentArgsForExec();
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        setEnvironmentArgsAndWorkingDirectoryForStart(processBuilder);
        Process torProcess = null;
        try {
            torProcess = processBuilder.start();
            CountDownLatch controlPortCountDownLatch = new CountDownLatch(1);
            eatStream(torProcess.getInputStream(), false, controlPortCountDownLatch);
            eatStream(torProcess.getErrorStream(), true, null);

            // On platforms other than Windows we run as a daemon and so we need to wait for the process to detach
            // or exit. In the case of Windows the equivalent is running as a service and unfortunately that requires
            // managing the service, such as turning it off or uninstalling it when it's time to move on. Any number
            // of errors can prevent us from doing the cleanup and so we would leave the process running around. Rather
            // than do that on Windows we just let the process run on the exec and hence don't look for an exit code.
            // This does create a condition where the process has exited due to a problem but we should hopefully
            // detect that when we try to use the control connection.
            if (OsData.getOsType() != OsData.OsType.WINDOWS) {
                int exit = torProcess.waitFor();
                torProcess = null;
                if (exit != 0) {
                    eventBroadcaster.broadcastNotice("Tor exited with value" + exit);
                    eventBroadcaster.getStatus().stopping();
                    LOG.warn("Tor exited with value " + exit);
                    throw new IOException("Tor exited with value: " + exit);
                }
            }

            // Wait for the auth cookie file to be created/updated
            if (cookieFile.length() == 0 && !cookieObserver.poll(COOKIE_TIMEOUT, MILLISECONDS)) {
                LOG.warn("Auth cookie not created");
                FileUtilities.listFilesToLog(config.getDataDir());
                eventBroadcaster.broadcastNotice("Tor authentication cookie file not created");
                eventBroadcaster.getStatus().stopping();
                throw new IOException("Auth cookie file not created: " + cookieFile.getAbsolutePath()
                        + ", len = " + cookieFile.length());
            }

            eventBroadcaster.broadcastNotice( "Waiting for control port...");
            // Now we should be able to connect to the new process
            controlPortCountDownLatch.await();

            eventBroadcaster.broadcastNotice( "Connecting to control port: " + control_port);
            controlSocket = new Socket("127.0.0.1", control_port);

            // Open a control connection and authenticate using the cookie file
            TorControlConnection controlConnection = new TorControlConnection(controlSocket);
            eventBroadcaster.broadcastNotice( "SUCCESS connected to Tor control port.");

            if (enableLogs) {
                controlConnection.setDebugging(System.out);
            }

            controlConnection.authenticate(FileUtilities.read(cookieFile));
            eventBroadcaster.broadcastNotice("SUCCESS - authenticated to control port.");

            controlConnection.resetConf(Collections.singletonList(OWNER));

            eventBroadcaster.broadcastNotice("adding control port event handler");
            controlConnection.setEventHandler(eventHandler);
            controlConnection.setEvents(Arrays.asList(EVENTS));
            eventBroadcaster.broadcastNotice("SUCCESS added control port event handler");

            // We only set the class property once the connection is in a known good state
            this.controlConnection = controlConnection;
            enableNetwork(true);

            LOG.info("Completed starting of tor");
        } catch (SecurityException e) {
            LOG.warn(e.toString(), e);
            throw new IOException(e);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while starting Tor", e);
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            if (controlConnection == null && torProcess != null) {
                // It's possible that something 'bad' could happen after we executed exec but before we takeOwnership()
                // in which case the Tor OP will hang out as a zombie until this process is killed. This is problematic
                // when we want to do things like
                LOG.warn("Destroying tor process");
                torProcess.destroy();
            }
        }
    }

    private void eatStream(final InputStream inputStream, final boolean stdError, final CountDownLatch countDownLatch) {
        new Thread() {
            @Override
            public void run() {
                Scanner scanner = new Scanner(inputStream);
                try {
                    while (scanner.hasNextLine()) {
                        if (stdError) {
                            LOG.error(scanner.nextLine());
                        } else {
                            String nextLine = scanner.nextLine();
                            // We need to find the line where it tells us what the control port is.
                            // The line that will appear in stdio with the control port looks like:
                            // Control listener listening on port 39717.
                            if (nextLine.contains("Control listener listening on port ")) {
                                // For the record, I hate regex so I'm doing this manually
                                control_port =
                                        Integer.parseInt(
                                                nextLine.substring(nextLine.lastIndexOf(" ") + 1, nextLine.length() - 1));
                                countDownLatch.countDown();
                            }
                            LOG.info(nextLine);
                        }
                    }
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        LOG.error("Couldn't close input stream in eatStream", e);
                    }
                }
            }
        }.start();
    }


    /**
     * Sets environment variables and working directory needed for Tor
     *
     * @param processBuilder we will call start on this to run Tor
     */
    private void setEnvironmentArgsAndWorkingDirectoryForStart(ProcessBuilder processBuilder) {
        processBuilder.directory(config.getConfigDir());
        Map<String, String> environment = processBuilder.environment();
        environment.put("HOME", config.getHomeDir().getAbsolutePath());
        switch (OsData.getOsType()) {
            case LINUX_32:
            case LINUX_64:
                // We have to provide the LD_LIBRARY_PATH because when looking for dynamic libraries
                // Linux apparently will not look in the current directory by default. By setting this
                // environment variable we fix that.
                environment.put("LD_LIBRARY_PATH", config.getLibraryPath().getAbsolutePath());
                break;
            default:
                break;
        }
    }

    private String[] getEnvironmentArgsForExec() {
        List<String> envArgs = new ArrayList<>();
        envArgs.add("HOME=" + config.getHomeDir().getAbsolutePath());
        switch (OsData.getOsType()) {
            case LINUX_32:
            case LINUX_64:
                // We have to provide the LD_LIBRARY_PATH because when looking for dynamic libraries
                // Linux apparently will not look in the current directory by default. By setting this
                // environment variable we fix that.
                envArgs.add("LD_LIBRARY_PATH=" + config.getLibraryPath().getAbsolutePath());
                break;
            default:
                break;
        }
        return envArgs.toArray(new String[envArgs.size()]);
    }

    /**
     * Setups and installs any files needed to run tor. If the tor files are already on the system, this does not
     * need to be invoked.
     *
     * @return true if tor installation is successful, otherwise false
     * @throws IOException
     */
    public boolean setup() throws IOException {
        return torInstaller != null && torInstaller.setup();
    }

    public boolean isIPv4LocalHostSocksPortOpen() {
        try {
            getIPv4LocalHostSocksPort();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sets the exit nodes through the tor control connection
     *
     * @param exitNodes
     * @return true if successfully set, otherwise false
     */
    public boolean setExitNode(String exitNodes) {
        //Based on config params from Orbot project
        if (!hasControlConnection()) {
            return false;
        }
        if (exitNodes == null || exitNodes.isEmpty()) {
            try {
                ArrayList<String> resetBuffer = new ArrayList<>();
                resetBuffer.add("ExitNodes");
                resetBuffer.add("StrictNodes");
                controlConnection.resetConf(resetBuffer);
                controlConnection.setConf("DisableNetwork", "1");
                controlConnection.setConf("DisableNetwork", "0");
            } catch (Exception ioe) {
                LOG.error("Connection exception occurred resetting exits", ioe);
                return false;
            }
        } else {
            try {
                controlConnection.setConf("GeoIPFile", config.getGeoIpFile().getCanonicalPath());
                controlConnection.setConf("GeoIPv6File", config.getGeoIpv6File().getCanonicalPath
                        ());
                controlConnection.setConf("ExitNodes", exitNodes);
                controlConnection.setConf("StrictNodes", "1");
                controlConnection.setConf("DisableNetwork", "1");
                controlConnection.setConf("DisableNetwork", "0");
            } catch (Exception ioe) {
                LOG.error("Connection exception occurred resetting exits", ioe);
                return false;
            }
        }
        return true;
    }

    public boolean disableNetwork(boolean isEnabled) {
        if (!hasControlConnection()) {
            return false;
        }
        try {
            controlConnection.setConf("DisableNetwork", isEnabled ? "0" : "1");
            return true;
        } catch (Exception e) {
            eventBroadcaster.broadcastDebug("error disabling network "
                    + e.getLocalizedMessage());
            return false;
        }
    }

    public boolean setNewIdentity() {
        if (!hasControlConnection()) {
            return false;
        }
        try {
            controlConnection.signal("NEWNYM");
            return true;
        } catch (IOException e) {
            eventBroadcaster.broadcastDebug("error requesting newnym: "
                    + e.getLocalizedMessage());
            return false;
        }
    }

    public boolean hasControlConnection() {
        return controlConnection != null;
    }

    public int getTorPid() {
        String pidS = getInfo("process/pid");
        return (pidS == null || pidS.isEmpty()) ? -1 : Integer.valueOf(pidS);
    }

    public String getInfo(String info) {
        if (!hasControlConnection()) {
            return null;
        }
        try {
            return controlConnection.getInfo(info);
        } catch (IOException e) {
            return null;
        }
    }

    public boolean reloadTorConfig() {
        if (!hasControlConnection()) {
            return false;
        }
        try {
            controlConnection.signal("HUP");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            restartTorProcess();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void restartTorProcess() throws Exception {
        killTorProcess(-1);
    }

    public void killTorProcess() throws Exception {
        killTorProcess(-9);
    }

    private void killTorProcess(int signal) throws Exception {
        //Based on logic from Orbot project
        String torFileName = config.getTorExecutableFile().getName();
        int procId;
        int killAttempts = 1;
        while ((procId = getTorPid()) != -1) {
            String pidString = String.valueOf(procId);
            execIgnoreException(format("busybox killall %d %s", signal, torFileName));
            execIgnoreException(format("toolbox kill %d %s", signal, pidString));
            execIgnoreException(format("busybox kill %d %s", signal, pidString));
            execIgnoreException(format("kill %d %s", signal, pidString));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            if (killAttempts++ > 4)
                throw new Exception("Cannot kill: " + config.getTorExecutableFile()
                        .getAbsolutePath());
        }
    }

    private static void execIgnoreException(String command) {
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
        }
    }
}
