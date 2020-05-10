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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.*;

import static com.msopentech.thali.toronionproxy.FileUtilities.setToReadOnlyPermissions;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This is where all the fun is, this is the class that handles the heavy work. Note that you will most likely need
 * to actually call into the AndroidOnionProxyManager or OnionProxyManager in order to create the right bindings
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
    private static final int HOSTNAME_TIMEOUT = 30;
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
        if(eventBroadcaster == null) {
            LOG.info("Event broadcast is null. Using default one");
            this.eventBroadcaster = new DefaultEventBroadcaster();
        } else {
            this.eventBroadcaster = eventBroadcaster;
        }
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
                start();

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
        if (!hostNameFileObserver.poll(HOSTNAME_TIMEOUT, SECONDS)) {
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
     * @throws IOException
     */
    public synchronized void start() throws IOException {
        if (controlConnection != null) {
            LOG.info("Control connection not null. aborting");
            return;
        }

        LOG.info("Starting Tor");
        Process torProcess = null;
        TorControlConnection controlConnection = findExistingTorConnection();
        boolean hasExistingTorConnection = controlConnection != null;
        if(!hasExistingTorConnection) {
            File controlPortFile = getContext().getConfig().getControlPortFile();
            controlPortFile.delete();
            if (!controlPortFile.getParentFile().exists()) controlPortFile.getParentFile().mkdirs();

            File cookieAuthFile = getContext().getConfig().getCookieAuthFile();
            cookieAuthFile.delete();
            if (!cookieAuthFile.getParentFile().exists()) cookieAuthFile.getParentFile().mkdirs();

            torProcess = spawnTorProcess();
            try {
                waitForControlPortFileCreation(controlPortFile);
                controlConnection = connectToTorControlSocket(controlPortFile);
            } catch (IOException e) {
                if(torProcess != null) torProcess.destroy();
                throw new IOException(e.getMessage());            }
        } else {
            LOG.info("Using existing Tor Process");
        }

        try {
            this.controlConnection = controlConnection;

            File cookieAuthFile = getContext().getConfig().getCookieAuthFile();
            waitForCookieAuthFileCreation(cookieAuthFile);
            controlConnection.authenticate(FileUtilities.read(cookieAuthFile));
            eventBroadcaster.broadcastNotice("SUCCESS - authenticated tor control port.");

            if(hasExistingTorConnection) {
                controlConnection.reloadConf();
                eventBroadcaster.broadcastNotice("Reloaded configuration file");
            }

            controlConnection.takeownership();
            controlConnection.resetOwningControllerProcess();
            eventBroadcaster.broadcastNotice("Took ownership of tor control port.");

            eventBroadcaster.broadcastNotice("adding control port event handler");
            controlConnection.setEventHandler(eventHandler);
            controlConnection.setEvents(Arrays.asList(EVENTS));
            eventBroadcaster.broadcastNotice("SUCCESS added control port event handler");

            enableNetwork(true);
        } catch (IOException e) {
            if(torProcess != null) torProcess.destroy();
            this.controlConnection = null;
            throw new IOException(e.getMessage());
        }

        LOG.info("Completed starting of tor");
    }

    /**
     * Finds existing tor control connection by trying to connect. Returns null if
     */
    private TorControlConnection findExistingTorConnection()  {
        File controlPortFile = getContext().getConfig().getControlPortFile();
        if(controlPortFile.exists()) {
            try {
                return connectToTorControlSocket(controlPortFile);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Looks in the specified <code>controlPortFile</code> for the port and attempts to open a control connection.
     */
    private TorControlConnection connectToTorControlSocket(File controlPortFile) throws IOException {
        TorControlConnection controlConnection;
        try {
            String[] controlPortTokens = new String(FileUtilities.read(controlPortFile)).trim().split(":");
            control_port = Integer.parseInt(controlPortTokens[1]);
            eventBroadcaster.broadcastNotice("Connecting to control port: " + control_port);
            controlSocket = new Socket(controlPortTokens[0].split("=")[1], control_port);
            controlConnection = new TorControlConnection(controlSocket);
            eventBroadcaster.broadcastNotice("SUCCESS connected to Tor control port.");
        } catch (IOException e) {
            throw new IOException(e.getMessage());
        } catch(ArrayIndexOutOfBoundsException e) {
            throw new IOException("Failed to read control port: " + new String(FileUtilities.read(controlPortFile)));
        }

        if (getContext().getSettings().hasDebugLogs()) {
            controlConnection.setDebugging(System.out);
        }
        return controlConnection;
    }

    /**
     * Spawns the tor native process from the existing Java process.
     */
    private Process spawnTorProcess() throws IOException {
        String pid = onionProxyContext.getProcessId();
        String[] cmd = {torExecutable().getAbsolutePath(), "-f", torrc().getAbsolutePath(), OWNER, pid};
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        setEnvironmentArgsAndWorkingDirectoryForStart(processBuilder);

        LOG.info("Starting process");
        Process torProcess;
        try {
            torProcess = processBuilder.start();
        } catch (SecurityException e) {
            LOG.warn(e.toString(), e);
            throw new IOException(e);
        }
        eatStream(torProcess.getErrorStream(), true);
        if (getContext().getSettings().hasDebugLogs()) {
            eatStream(torProcess.getInputStream(), false);
        }
        return torProcess;
    }

    /**
     * Waits for the control port file to be created by the Tor process. If there is any problem creating the file OR
     * if the timeout for the control port file to be created is exceeded, then an IOException is thrown.
     */
    private void waitForControlPortFileCreation(File controlPortFile) throws IOException {
        long controlPortStartTime = System.currentTimeMillis();
        LOG.info("Waiting for control port");
        boolean isCreated = controlPortFile.exists() || controlPortFile.createNewFile();
        WriteObserver controlPortFileObserver = onionProxyContext.createControlPortFileObserver();
        if (!isCreated || (controlPortFile.length() == 0 && !controlPortFileObserver.poll(config.getFileCreationTimeout(), SECONDS))) {
            LOG.warn("Control port file not created");
            FileUtilities.listFilesToLog(config.getDataDir());
            eventBroadcaster.broadcastNotice("Tor control port file not created");
            eventBroadcaster.getStatus().stopping();
            throw new IOException("Control port file not created: " + controlPortFile.getAbsolutePath()
                    + ", len = " + controlPortFile.length());
        }
        LOG.info("Created control port file: time = " + (System.currentTimeMillis() - controlPortStartTime) + "ms");
    }

    /**
     * Waits for the cookie auth file to be created by the Tor process. If there is any problem creating the file OR
     * if the timeout for the cookie auth file to be created is exceeded, then  an IOException is thrown.
     */
    private void waitForCookieAuthFileCreation(File cookieAuthFile) throws IOException {
        long cookieAuthStartTime = System.currentTimeMillis();
        LOG.info("Waiting for cookie auth file");
        boolean isCreated = cookieAuthFile.exists() || cookieAuthFile.createNewFile();
        WriteObserver cookieAuthFileObserver = onionProxyContext.createCookieAuthFileObserver();
        if (!isCreated || (cookieAuthFile.length() == 0 && !cookieAuthFileObserver.poll(config.getFileCreationTimeout(), SECONDS))) {
            LOG.warn("Cookie Auth file not created");
            eventBroadcaster.broadcastNotice("Cookie Auth file not created");
            eventBroadcaster.getStatus().stopping();
            throw new IOException("Cookie Auth file not created: " + cookieAuthFile.getAbsolutePath()
                    + ", len = " + cookieAuthFile.length());
        }
        LOG.info("Created cookie auth file: time = " + (System.currentTimeMillis() - cookieAuthStartTime) + "ms");
    }

    private void eatStream(final InputStream inputStream, boolean isError) {
        new Thread() {
            @Override
            public void run() {
                Scanner scanner = new Scanner(inputStream);
                try {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if(isError) {
                            LOG.error(line);
                            eventBroadcaster.broadcastException(line, new Exception());
                        } else {
                            LOG.info(line);
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

    private File torExecutable() throws IOException {
        File torExe = config.getTorExecutableFile();
        //Try removing platform specific extension
        if(!torExe.exists()) {
            torExe = new File(torExe.getParent(), "tor");
        }
        if(!torExe.exists()) {
            eventBroadcaster.broadcastNotice("Tor executable not found");
            eventBroadcaster.getStatus().stopping();
            LOG.error("Tor executable not found: " + torExe.getAbsolutePath());
            throw new IOException("Tor executable not found");
        }
        return torExe;
    }

    private File torrc() throws IOException {
        File torrc = config.getTorrcFile();
        if(torrc == null || !torrc.exists()) {
            eventBroadcaster.broadcastNotice("Torrc not found");
            eventBroadcaster.getStatus().stopping();
            LOG.error("Torrc not found: " + (torrc != null ? torrc.getAbsolutePath() : "N/A"));
            throw new IOException("Torrc not found");
        }
        return torrc;
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
    public void setup() throws IOException {
        if(torInstaller == null) {
            throw new IOException("No TorInstaller found");
        }
        torInstaller.setup();
    }

    public TorInstaller getTorInstaller() {
        return torInstaller;
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
        int killAttempts = 0;
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
            killAttempts++;
            if (killAttempts > 4)
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
