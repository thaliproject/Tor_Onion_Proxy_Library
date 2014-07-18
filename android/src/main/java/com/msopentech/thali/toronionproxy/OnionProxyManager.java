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
import net.freehaven.tor.control.TorControlConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipInputStream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This class began life is TorPlugin from the Briar Project
 */
public abstract class OnionProxyManager {
    public static final String torWorkingDirectoryName = "tor";

    private static final String[] EVENTS = {
            "CIRC", "ORCONN", "NOTICE", "WARN", "ERR"
    };

    private static final String OWNER = "__OwningControllerProcess";
    private static final int COOKIE_TIMEOUT = 3000; // Milliseconds
    private static final int HOSTNAME_TIMEOUT = 30 * 1000; // Milliseconds
    private static final Logger LOG = LoggerFactory.getLogger(OnionProxyManager.class);

    protected final OnionProxyContext onionProxyContext;
    protected final File torDirectory;
    private final File geoIpFile, configFile;
    private final File cookieFile, hostnameFile;

    private volatile Socket controlSocket = null;
    // If controlConnection is not null then this means that a connection exists and the Tor OP will die when
    // the connection fails
    private volatile TorControlConnection controlConnection = null;
    private volatile int control_port;

    public OnionProxyManager(OnionProxyContext onionProxyContext) {
        this.onionProxyContext = onionProxyContext;
        torDirectory = new File(onionProxyContext.getWorkingDirectory(), torWorkingDirectoryName);

        if (torDirectory.exists() == false && torDirectory.mkdirs() == false) {
            throw new RuntimeException("Could not create tor working directory");
        }
        geoIpFile = new File(torDirectory, "geoip");
        configFile = new File(torDirectory, "torrc");
        cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
        hostnameFile = new File(torDirectory, "/hiddenservice/hostname");
    }

    /**
     * This is a blocking call that will try to start the Tor OP, connect it to the network and get it to be fully
     * boostrapped. Sometimes the bootstrap process just hangs for no apparent reason so the method will wait for the
     * given time for bootstrap to finish and if it doesn't then will restart the bootstrap process the given number of
     * repeats.
     * @param secondsBeforeTimeOut Seconds to wait for boot strapping to finish
     * @param numberOfRetries Number of times to try recycling the Tor OP before giving up on bootstrapping working
     * @return True if bootsrap succeeded, false if there is a problem or the bootstrap couldn't complete in the given
     * time.
     */
    public boolean startWithRepeat(int secondsBeforeTimeOut, int numberOfRetries)
            throws IOException, InterruptedException {
        if (secondsBeforeTimeOut <= 0 || numberOfRetries < 0) {
            throw new IllegalArgumentException("secondsBeforeTimeOut >= 0 & numberOfRetries > 0");
        }

        // This is sleezy but we have cases where an old instance of the Tor OP needs an extra second to
        // clean itself up. Without that time we can't do things like delete its binary (which we currently
        // do by default, something we hope to fix with https://github.com/thaliproject/Tor_Onion_Proxy_Library/issues/13
        Thread.sleep(1000,0);

        for(int retryCount = 0; retryCount < numberOfRetries; ++retryCount) {
            if (installAndStartTorOp() == false) {
                return false;
            }
            enableNetwork(true);

            // We will check every second to see if boot strapping has finally finished
            for(int secondsWaited = 0; secondsWaited < secondsBeforeTimeOut; ++secondsWaited) {
                if (isBootstrapped() == false) {
                    Thread.sleep(1000,0);
                } else {
                    return true;
                }
            }

            // Bootstrapping isn't over so we need to restart and try again
            stop();
            // It can take a little bit for the Tor OP to detect the connection is dead and kill itself
            Thread.sleep(1000,0);
        }

        return false;
    }

    /**
     * Finds a socks port that is running on an IPv4 localhost address.
     * @return Discovered socks port
     * @throws IOException
     */
    public int getIPv4LocalHostSocksPort() throws IOException {
        if (isRunning() == false) {
            throw new RuntimeException("Tor is not running!");
        }

        // This returns a set of space delimited quoted strings which could be Ipv4, Ipv6 or unix sockets
        String[] socksIpPorts = controlConnection.getInfo("net/listeners/socks").split(" ");

        for(String address : socksIpPorts) {
            if (address.contains("\"127.0.0.1:")) {
                // Remember, the last character will be a " so we have to remove that
                return Integer.parseInt(address.substring(address.lastIndexOf(":") + 1, address.length() - 1));
            }
        }

        throw new RuntimeException("We don't have an Ipv4 localhost binding for socks!");
    }

    /**
     * Publishes a hidden service
     * @param hiddenServicePort The port that the hidden service will accept connections on
     * @param localPort The local port that the hidden service will relay connections to
     * @return The hidden service's onion address in the form X.onion.
     */
    public String publishHiddenService(int hiddenServicePort, int localPort) throws IOException {
        if(controlConnection == null) {
            throw new RuntimeException("Service is not running.");
        }

        List<ConfigEntry> currentHiddenServices = controlConnection.getConf("HiddenServiceOptions");

        if ((currentHiddenServices.size() == 1 &&
                currentHiddenServices.get(0).key.equals("HiddenServiceOptions") &&
                currentHiddenServices.get(0).value.equals("")) == false) {
            throw new RuntimeException("Sorry, only one hidden service to a customer and we already have one. Please send complaints to https://github.com/thaliproject/Tor_Onion_Proxy_Library/issues/5 with your scenario so we can justify fixing this.");
        }

        LOG.info("Creating hidden service");
        try {
            if (hostnameFile.getParentFile().exists() == false &&
                    hostnameFile.getParentFile().mkdirs() == false) {
                throw new RuntimeException("Could not create hostnameFile parent directory");
            }

            if (hostnameFile.exists() == false && hostnameFile.createNewFile() == false) {
                throw new RuntimeException("Could not create hostnameFile");
            }

            // Watch for the hostname file being created/updated
            WriteObserver hostNameFileObserver = onionProxyContext.generateWriteObserver(hostnameFile);
            // Use the control connection to update the Tor config
            List<String> config = Arrays.asList(
                    "HiddenServiceDir " + hostnameFile.getParentFile().getAbsolutePath(),
                    "HiddenServicePort " + hiddenServicePort + " 127.0.0.1:" + localPort);
            controlConnection.setConf(config);
            controlConnection.saveConf();
            // Wait for the hostname file to be created/updated
            if(!hostNameFileObserver.poll(HOSTNAME_TIMEOUT, MILLISECONDS)) {
                FileUtilities.listFiles(hostnameFile.getParentFile());
                throw new RuntimeException("Wait for hidden service hostname file to be created expired.");
            }
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
        }
        // Publish the hidden service's onion hostname in transport properties
        String hostname = new String(FileUtilities.read(hostnameFile), "UTF-8").trim();
        LOG.info("Hidden service config has completed.");

        return hostname;
    }

    /**
     * Kills the Tor OP Process. Once you have called this method nothing is going to work until you either call
     * startWithRepeat or installAndStartTorOp
     * @throws IOException
     */
    public void stop() throws IOException {
        try {
            if (controlConnection == null) {
                return;
            }
            LOG.info("Stopping Tor");
            controlConnection.setConf("DisableNetwork", "1");
            controlConnection.shutdownTor("TERM");
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
        } finally {
            if (controlSocket != null) {
                controlSocket.close();
            }
            controlConnection = null;
            controlSocket = null;
        }
    }

    /**
     * Checks to see if the Tor OP is running (e.g. fully bootstrapped) and open to network connections.
     * @return
     * @throws IOException
     */
    public boolean isRunning() throws IOException {
        return isBootstrapped() && isNetworkEnabled();
    }

    /**
     * Tells the Tor OP if it should accept network connections
     * @param enable If true then the Tor OP will accept SOCKS connections, otherwise not.
     * @throws IOException
     */
    public void enableNetwork(boolean enable) throws IOException {
        if(controlConnection == null) {
            throw new RuntimeException("Tor is not running!");
        }
        LOG.info("Enabling network: " + enable);
        controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
    }

    /**
     * Specifies if Tor OP is accepting network connections
     * @return
     * @throws IOException
     */
    public boolean isNetworkEnabled() throws IOException {
        if (controlConnection == null) {
            throw new RuntimeException("Tor is not running!");
        }

        List<ConfigEntry> disableNetworkSettingValues = controlConnection.getConf("DisableNetwork");
        boolean result = false;
        // It's theoretically possible for us to get multiple values back, if even one is false then we will
        // assume all are false
        for(ConfigEntry configEntry : disableNetworkSettingValues) {
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
     * @return
     */
    public boolean isBootstrapped() {
        if (controlConnection == null) {
            return false;
        }

        String phase = null;
        try {
            phase = controlConnection.getInfo("status/bootstrap-phase");
        } catch (IOException e) {
            LOG.warn("Control connection is not responding properly to getInfo", e);
        }

        if(phase != null && phase.contains("PROGRESS=100")) {
            LOG.info("Tor has already bootstrapped");
            return true;
        }

        return false;
    }

    /**
     * Installs all necessary files and starts the Tor OP in offline mode (e.g. networkEnabled(false)). This would
     * only be used if you wanted to start the Tor OP so that the install and related is all done but aren't ready to
     * actually connect it to the network.
     * @return True if all files installed and Tor OP successfully started
     * @throws IOException
     */
    public synchronized boolean installAndStartTorOp() throws IOException {
        // The Tor OP will die if it looses the connection to its socket so if there is no controlSocket defined
        // then Tor is dead. This assumes, of course, that takeOwnership works and we can't end up with Zombies.
        if (controlConnection != null) {
            LOG.info("Tor is already running");
            return true;
        }

        // The code below is why this method is synchronized, we don't want two instances of it running at once
        // as the result would be a mess of screwed up files and connections.
        LOG.info("Tor is not running");

        // Install the binary, possibly overwriting an older version
        File torExecutableFile = installBinary();

        // Install the GeoIP database and config file
        if(!installConfig()) {
            LOG.info("Could not install Tor config");
            return false;
        }

        LOG.info("Starting Tor");
        if (cookieFile.getParentFile().exists() == false &&
                cookieFile.getParentFile().mkdirs() == false) {
            throw new RuntimeException("Could not create cookieFile parent directory");
        }

        // The original code from Briar watches individual files, not a directory and Android's file observer
        // won't work on files that don't exist. Rather than take 5 seconds to rewrite Briar's code I instead
        // just make sure the file exists
        if (cookieFile.exists() == false && cookieFile.createNewFile() == false) {
            throw new RuntimeException("Could not create cookieFile");
        }

        // Watch for the auth cookie file being created/updated
        WriteObserver cookieObserver = onionProxyContext.generateWriteObserver(cookieFile);
        // Start a new Tor process
        String torPath = torExecutableFile.getAbsolutePath();
        String configPath = configFile.getAbsolutePath();
        String pid = onionProxyContext.getProcessId();
        String[] cmd = { torPath, "-f", configPath, OWNER, pid };
        String[] env = { "HOME=" + torDirectory.getAbsolutePath() };
        Process torProcess = null;
        boolean startWorked = false;
        try {
            torProcess = Runtime.getRuntime().exec(cmd, env, torDirectory);
            CountDownLatch controlPortCountDownLatch = new CountDownLatch(1);
            eatStream(torProcess.getInputStream(), false, controlPortCountDownLatch);
            eatStream(torProcess.getErrorStream(), true, null);

            // On platforms other than Windows we run as a daemon and so we need to wait for the process to detach
            // or exit. In the case of Windows the equivalent is running as a service and unfortunately that requires
            // managing the service, such as turning it off or uninstalling it when it's time to move on. Any number
            // of errors can prevent us from doing the cleanup and so we would leave the process running around. Rather
            // than do that on Windows we just let the process run on the exec and hence we don't have a good way
            // of detecting any problems it has.
            if (OsData.getOsType() != OsData.OsType.Windows) {
                int exit = torProcess.waitFor();
                torProcess = null;
                if(exit != 0) {
                    LOG.warn("Tor exited with value " + exit);
                    return false;
                }
            }

            // Wait for the auth cookie file to be created/updated
            if(!cookieObserver.poll(COOKIE_TIMEOUT, MILLISECONDS)) {
                LOG.warn("Auth cookie not created");
                FileUtilities.listFiles(torDirectory);
                return false;
            }

            // Now we should be able to connect to the new process
            controlPortCountDownLatch.await();
            controlSocket = new Socket("127.0.0.1", control_port);

            // Open a control connection and authenticate using the cookie file
            TorControlConnection controlConnection = new TorControlConnection(controlSocket);
            controlConnection.authenticate(FileUtilities.read(cookieFile));
            // Tell Tor to exit when the control connection is closed
            controlConnection.takeOwnership();
            controlConnection.resetConf(Arrays.asList(OWNER));
            // Register to receive events from the Tor process
            controlConnection.setEventHandler(new OnionProxyManagerEventHandler());
            controlConnection.setEvents(Arrays.asList(EVENTS));

            // We only set the class property once the connection is in a known good state
            this.controlConnection = controlConnection;
            return true;
        } catch(SecurityException e1) {
            LOG.warn(e1.toString(), e1);
            return false;
        } catch(InterruptedException e1) {
            LOG.warn("Interrupted while starting Tor");
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (controlConnection == null && torProcess != null) {
                // It's possible that something 'bad' could happen after we executed exec but before we takeOwnership()
                // in which case the Tor OP will hang out as a zombie until this process is killed. This is problematic
                // when we want to do things like
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
                    while(scanner.hasNextLine()) {
                        if (stdError) {
                            LOG.error(scanner.nextLine());
                        } else {
                            String nextLine = scanner.nextLine();
                            // We need to find the line where it tells us what the control port is incase we had
                            // it pick one automatically.
                            // The line that will appear in stdio with the control port looks like:
                            // Control listener listening on port 39717.
                            if (nextLine.contains("Control listener listening on port ")) {
                                // For the record, I hate regex so I'm doing this manually
                                control_port = Integer.parseInt(nextLine.substring(nextLine.lastIndexOf(" ") + 1, nextLine.length() - 1));
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
     * Installs the Tor Onion Proxy binary
     * @return binary to execute from the command line
     */
    protected File installBinary() {
        ZipInputStream in = null;
        OutputStream out = null;
        try {
            // Unzip the Tor binary to the filesystem
            in = new ZipInputStream(onionProxyContext.getTorExecutableZip());
            File executableFile = new File(torDirectory, in.getNextEntry().getName());
            if (executableFile.exists() && executableFile.delete() == false) {
                throw new RuntimeException("Could not clean up Tor binary");
            }
            out = new FileOutputStream(executableFile);
            FileUtilities.copy(in, out);

            // Make the Tor binary executable
            if(!setExecutable(executableFile)) {
                throw new RuntimeException("Could not make Tor executable");
            }
            return executableFile;
        } catch(IOException e) {
            throw new RuntimeException(e);
        } finally {
            FileUtilities.tryToClose(in);
            FileUtilities.tryToClose(out);
        }
    }

    /**
     * Alas old versions of Android do not support setExecutable.
     * @param f File to make executable
     * @return True if it worked, otherwise false.
     */
    protected abstract boolean setExecutable(File f);

    protected boolean installConfig() {
        InputStream in = null;
        OutputStream out = null;
        try {
            FileUtilities.cleanInstallOneFile(onionProxyContext.getGeoIpZip(), geoIpFile);

            FileUtilities.cleanInstallOneFile(onionProxyContext.getTorrc(), configFile);
            // We need to edit the config file to specify exactly where the cookie/geoip files should be stored, on
            // Android this is always a fixed location relative to the configFiles which is why this extra step
            // wasn't needed in Briar's Android code. But in Windows it ends up in the user's AppData/Roaming. Rather
            // than track it down we just tell Tor where to put it.
            PrintWriter printWriter = null;
            try {
                printWriter = new PrintWriter(new BufferedWriter(new FileWriter(configFile, true)));
                printWriter.println("CookieAuthFile " + cookieFile.getAbsolutePath());
                // For some reason the GeoIP's location can only be given as a file name, not a path and it has
                // to be in the data directory so we need to set both
                printWriter.println("DataDirectory " + geoIpFile.getParentFile().getAbsolutePath());
                printWriter.println("GeoIPFile " + geoIpFile.getName());
            } finally {
                if (printWriter != null) {
                    printWriter.close();
                }
            }
            return true;
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
            return false;
        } finally {
            FileUtilities.tryToClose(in);
            FileUtilities.tryToClose(out);
        }
    }
}
