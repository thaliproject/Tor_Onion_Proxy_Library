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

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.zip.ZipInputStream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This class began life is TorPlugin from the Briar Project
 */
public abstract class OnionProxyManager implements EventHandler {
    public static final String torWorkingDirectoryName = "tor";

    private static final String[] EVENTS = {
            "CIRC", "ORCONN", "NOTICE", "WARN", "ERR"
    };
    // TODO: Both SOCKS_PORT and CONTROL_PORT need to be made dynamic
    private static final int SOCKS_PORT = 59052, CONTROL_PORT = 59053;
    private static final int COOKIE_TIMEOUT = 3000; // Milliseconds
    private static final int HOSTNAME_TIMEOUT = 30 * 1000; // Milliseconds
    private static final Logger LOG = LoggerFactory.getLogger(OnionProxyManager.class);

    private final OnionProxyContext onionProxyContext;
    private final File torDirectory, torFile, configFile, doneFile;
    private final File cookieFile, pidFile, hostnameFile;

    protected volatile boolean running = false;
    private volatile boolean networkEnabled = false;
    private volatile boolean bootstrapped = false;
    private volatile Process tor = null;
    private volatile int pid = -1;
    private volatile Socket controlSocket = null;
    private volatile TorControlConnection controlConnection = null;

    public OnionProxyManager(OnionProxyContext onionProxyContext) {
        this.onionProxyContext = onionProxyContext;
        torDirectory = new File(onionProxyContext.getWorkingDirectory(), torWorkingDirectoryName);

        if (torDirectory.exists() == false && torDirectory.mkdirs() == false) {
            throw new RuntimeException("Could not create tor working directory");
        }
        torFile = new File(torDirectory, "tor");
        configFile = new File(torDirectory, "torrc");
        doneFile = new File(torDirectory, "done");
        cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
        pidFile = new File(torDirectory, ".tor/pid");
        hostnameFile = new File(torDirectory, "hostname");
    }

    public boolean start() throws IOException {
        // Try to connect to an existing Tor process if there is one
        boolean startProcess = false;
        try {
            controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
            LOG.info("Tor is already running");
            if(readPidFile() == -1) {
                LOG.info("Could not read PID of Tor process");
                controlSocket.close();
                killZombieProcess();
                startProcess = true;
            } else {
                // TODO: Shouldn't this only be set to true when message is received on the EventHandler?
                bootstrapped = true;
            }
        } catch(IOException e) {
            LOG.info("Tor is not running");
            startProcess = true;
        }
        if(startProcess) {
            // Install the binary, possibly overwriting an older version
            if(!installBinary()) {
                LOG.warn("Could not install Tor binary");
                return false;
            }
            // Install the GeoIP database and config file if necessary
            if(!isConfigInstalled() && !installConfig()) {
                LOG.info("Could not install Tor config");
                return false;
            }
            LOG.info("Starting Tor");
            if (cookieFile.getParentFile().exists() == false &&
                    cookieFile.getParentFile().mkdirs() == false) {
                throw new RuntimeException("Could not create cookieFile parent directory");
            }

            if (cookieFile.createNewFile() == false) {
                throw new RuntimeException("Could not create cookieFile");
            }

            // Watch for the auth cookie file being created/updated
            WriteObserver cookieObserver = onionProxyContext.generateWriteObserver(cookieFile);
            // Start a new Tor process
            String torPath = torFile.getAbsolutePath();
            String configPath = configFile.getAbsolutePath();
            String[] cmd = { torPath, "-f", configPath };
            String[] env = { "HOME=" + torDirectory.getAbsolutePath() };
            try {
                tor = Runtime.getRuntime().exec(cmd, env, torDirectory);
            } catch(SecurityException e1) {
                LOG.warn(e1.toString(), e1);
                return false;
            }
            // Log the process's standard output until it detaches
            Scanner stdout = new Scanner(tor.getInputStream());
            try {
                while(stdout.hasNextLine()) LOG.info(stdout.nextLine());
            } finally {
                stdout.close();
            }

            try {
                // Wait for the process to detach or exit
                int exit = tor.waitFor();
                if(exit != 0) {
                    LOG.warn("Tor exited with value " + exit);
                    return false;
                }
                // Wait for the auth cookie file to be created/updated
                if(!cookieObserver.poll(COOKIE_TIMEOUT, MILLISECONDS)) {
                    LOG.warn("Auth cookie not created");
                    listFiles(torDirectory);
                    return false;
                }
            } catch(InterruptedException e1) {
                LOG.warn("Interrupted while starting Tor");
                Thread.currentThread().interrupt();
                return false;
            }
            // Now we should be able to connect to the new process
            controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
        }
        running = true;
        // Read the PID of the Tor process so we can kill it if necessary
        pid = readPidFile();
        // Open a control connection and authenticate using the cookie file
        controlConnection = new TorControlConnection(controlSocket);
        controlConnection.authenticate(read(cookieFile));
        // Tell Tor to exit when the control connection is closed
        controlConnection.takeOwnership();
        // Register to receive events from the Tor process
        controlConnection.setEventHandler(this);
        controlConnection.setEvents(Arrays.asList(EVENTS));
        return true;
    }

    /**
     * It's easy to leave tor processes hanging around. To deal with this apps that have strong life cycle management
     * will want a way to hook Tor cleanup code into that management. This method returns a runnable that will
     * clean up the tor bits that can then be hooked in to the life cycle management.
     * @return
     */
    public Runnable getShutdownRunnable() {
        return new Runnable() {
            public void run() {
                killTorProcess();
                killZombieProcess();
            }
        };
    }

    public int getSocksPort() {
        return SOCKS_PORT;
    }

    // TODO: Figure out if the process name (which we use to find zombies) is just the file name and if so figure
    // out how we can dynamically change the executable name (which should work) so we don't collide. This has
    // implications for storing the name and could potentially end up with us losing a binary forever if we forget
    // the name we used so we do need to think through this. But it could be as easy as just letting an app suggest
    // some name (munging a domain name perhaps) and then running on that.
    private boolean installBinary() {
        InputStream in = null;
        OutputStream out = null;
        try {
            // Unzip the Tor binary to the filesystem
            in = getTorInputStream();
            out = new FileOutputStream(torFile);
            copy(in, out);
            // Make the Tor binary executable
            if(!setExecutable(torFile)) {
                LOG.warn("Could not make Tor executable");
                return false;
            }
            return true;
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
            return false;
        } finally {
            tryToClose(in);
            tryToClose(out);
        }
    }

    private boolean isConfigInstalled() {
        return configFile.exists() && doneFile.exists();
    }

    private boolean installConfig() {
        InputStream in = null;
        OutputStream out = null;
        try {
            // Copy the config file to the filesystem
            in = getConfigInputStream();
            out = new FileOutputStream(configFile);
            copy(in, out);
            // Create a file to indicate that installation succeeded
            if (doneFile.createNewFile() == false) {
                throw new RuntimeException("Could not create doneFile");
            }
            return true;
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
            return false;
        } finally {
            tryToClose(in);
            tryToClose(out);
        }
    }

    private InputStream getTorInputStream() throws IOException {
        InputStream in = onionProxyContext.getTorExecutableZip();
        ZipInputStream zin = new ZipInputStream(in);
        if(zin.getNextEntry() == null) throw new IOException();
        return zin;
    }

    private InputStream getConfigInputStream() throws IOException {
        return onionProxyContext.getTorrc();
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[4096];
            while(true) {
                int read = in.read(buf);
                if(read == -1) break;
                out.write(buf, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    /**
     * Setting a file to executable differs in Android (and various versions of Android) than in generic Java
     * so we use this method to abstract out the details.
     * @param f The file to make executable
     * @return True if it worked, false if it did not
     */
    abstract protected boolean setExecutable(File f);

    private void tryToClose(Closeable closeable) {
        try {
            if(closeable != null) closeable.close();
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
        }
    }

    private void listFiles(File f) {
        if(f.isDirectory()) for(File child : f.listFiles()) listFiles(child);
        else LOG.info(f.getAbsolutePath());
    }

    private byte[] read(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        try {
            int offset = 0;
            while(offset < b.length) {
                int read = in.read(b, offset, b.length - offset);
                if(read == -1) throw new EOFException();
                offset += read;
            }
            return b;
        } finally {
            in.close();
        }
    }

    private int readPidFile() {
        // Read the PID of the Tor process so we can kill it if necessary
        try {
            return Integer.parseInt(new String(read(pidFile), "UTF-8").trim());
        } catch(IOException e) {
            LOG.warn("Could not read PID file", e);
        } catch(NumberFormatException e) {
            LOG.warn("Could not parse PID file", e);
        }
        return -1;
    }

    /**
     * Android has some problems with spawned processes having a life of their own even when the parent is killed.
     * In other OS's the problem is less extreme. We use this abstract method as a hook for the code to deal with this
     * issue in each environment.
     */
    abstract protected void killZombieProcess();

    private void killTorProcess() {
        if(tor != null) {
            LOG.info("Killing Tor via destroy()");
            tor.destroy();
        }
        if(pid != -1) {
            LOG.info("Killing Tor via killProcess(" + pid + ")");
            android.os.Process.killProcess(pid);
        }
    }

    // TODO: Right now we can only create a single hidden service but that is obviously broken, we will
    // eventually want to support multiple hidden services.

    /**
     * Publishes a hidden service
     * @param hiddenServicePort The port that the hidden service will accept connections on
     * @param localPort The local port that the hidden service will relay connections to
     * @return The hidden service's onion address in the form X.onion.
     */
    public String publishHiddenService(int hiddenServicePort, int localPort) throws IOException {
        if(!running) throw new RuntimeException("Service is not running.");
        if(!hostnameFile.exists()) {
            LOG.info("Creating hidden service");
            try {
                if (hostnameFile.getParentFile().exists() == false &&
                        hostnameFile.getParentFile().mkdirs() == false) {
                    throw new RuntimeException("Could not create hostnameFile parent directory");
                }

                if (hostnameFile.createNewFile() == false) {
                    throw new RuntimeException("Could not create hostnameFile");
                }

                // Watch for the hostname file being created/updated
                WriteObserver hostNameFileObserver = onionProxyContext.generateWriteObserver(hostnameFile);
                // Use the control connection to update the Tor config
                List<String> config = Arrays.asList(
                        "HiddenServiceDir " + torDirectory.getAbsolutePath(),
                        "HiddenServicePort " + hiddenServicePort + " 127.0.0.1:" + localPort);
                controlConnection.setConf(config);
                controlConnection.saveConf();
                // Wait for the hostname file to be created/updated
                if(!hostNameFileObserver.poll(HOSTNAME_TIMEOUT, MILLISECONDS)) {
                    listFiles(torDirectory);
                    throw new RuntimeException("Wait for hidden service hostname file to be created expired.");
                }
                if(!running) throw new RuntimeException("Service was turned off");
            } catch(IOException e) {
                LOG.warn(e.toString(), e);
            }
        }
        // Publish the hidden service's onion hostname in transport properties
        String hostname = new String(read(hostnameFile), "UTF-8").trim();
        LOG.info("Hidden service " + hostname);
        return hostname;
    }

    protected void enableNetwork(boolean enable) throws IOException {
        if(!running) {
            throw new RuntimeException("Tor is not running!");
        }
        LOG.info("Enabling network: " + enable);
        networkEnabled = enable;
        controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
    }

    public void stop() throws IOException {
        running = false;
        try {
            LOG.info("Stopping Tor");
            if(controlSocket == null)
                controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
            if(controlConnection == null) {
                controlConnection = new TorControlConnection(controlSocket);
                controlConnection.authenticate(read(cookieFile));
            }
            controlConnection.setConf("DisableNetwork", "1");
            controlConnection.shutdownTor("TERM");
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
        } finally {
            if (controlSocket != null) {
                controlSocket.close();
            }
            killTorProcess();
            killZombieProcess();
        }
    }

    public boolean isRunning() {
        return running && networkEnabled && bootstrapped;
    }

    // Event handler interfaces

    public void circuitStatus(String status, String id, List<String> path, Map<String, String> info) {
        String msg = "CircuitStatus: " + id + " " + status;
        String purpose = info.get("PURPOSE");
        if(purpose != null) msg += ", purpose: " + purpose;
        String hsState = info.get("HS_STATE");
        if(hsState != null) msg += ", state: " + hsState;
        String rendQuery = info.get("REND_QUERY");
        if(rendQuery != null) msg += ", service: " + rendQuery;
        if(!path.isEmpty()) msg += ", path: " + shortenPath(path);
        LOG.info(msg);
    }

    public void streamStatus(String status, String id, String target) {
        LOG.info("streamStatus: status: " + status + ", id: " + id + ", target: " + target);
    }

    public void orConnStatus(String status, String orName) {
        LOG.info("OR connection: status: " + status + ", orName: " + orName);
    }

    public void bandwidthUsed(long read, long written) {
        LOG.info("bandwidthUsed: read: " + read + ", written: " + written);
    }

    public void newDescriptors(List<String> orList) {
        Iterator<String> iterator = orList.iterator();
        StringBuilder stringBuilder = new StringBuilder();
        while(iterator.hasNext()) {
            stringBuilder.append(iterator.next());
        }
        LOG.info("newDescriptors: " + stringBuilder.toString());
    }

    public void message(String severity, String msg) {
        LOG.info("message: severity: " + severity + ", msg: " + msg);
        if(severity.equals("NOTICE") && msg.startsWith("Bootstrapped 100%")) {
            bootstrapped = true;
        }
    }

    @Override
    public void unrecognized(String type, String msg) {
        LOG.info("unrecognized: type: " + type + ", msg: " + msg);
    }

    private String shortenPath(List<String> path) {
        StringBuilder s = new StringBuilder();
        for(String id : path) {
            if(s.length() > 0) s.append(',');
            s.append(id.substring(1, 7));
        }
        return s.toString();
    }
}
