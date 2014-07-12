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

    private static final String OWNER = "__OwningControllerProcess";
    // TODO: Both SOCKS_PORT and CONTROL_PORT need to be made dynamic
    private static final int SOCKS_PORT = 59052, CONTROL_PORT = 59053;
    private static final int COOKIE_TIMEOUT = 3000; // Milliseconds
    private static final int HOSTNAME_TIMEOUT = 30 * 1000; // Milliseconds
    private static final Logger LOG = LoggerFactory.getLogger(OnionProxyManager.class);

    protected final OnionProxyContext onionProxyContext;
    protected final File torDirectory;
    private final File geoIpFile, configFile, doneFile;
    private final File cookieFile, hostnameFile;

    protected volatile boolean running = false;
    private volatile boolean networkEnabled = false;
    private volatile boolean bootstrapped = false;
    private volatile Socket controlSocket = null;
    private volatile TorControlConnection controlConnection = null;

    public OnionProxyManager(OnionProxyContext onionProxyContext) {
        this.onionProxyContext = onionProxyContext;
        torDirectory = new File(onionProxyContext.getWorkingDirectory(), torWorkingDirectoryName);

        if (torDirectory.exists() == false && torDirectory.mkdirs() == false) {
            throw new RuntimeException("Could not create tor working directory");
        }
        geoIpFile = new File(torDirectory, "geoip");
        configFile = new File(torDirectory, "torrc");
        doneFile = new File(torDirectory, "done");
        cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
        hostnameFile = new File(torDirectory, "hostname");
    }

    public boolean start() throws IOException {
        // Try to connect to an existing Tor process if there is one
        boolean startProcess = false;
        try {
            controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
            LOG.info("Tor is already running");
        } catch(IOException e) {
            LOG.info("Tor is not running");
            startProcess = true;
            // Install the binary, possibly overwriting an older version
            File torExecutableFile = installBinary();

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
            Process torProcess;
            try {
                torProcess = Runtime.getRuntime().exec(cmd, env, torDirectory);
            } catch(SecurityException e1) {
                LOG.warn(e1.toString(), e1);
                return false;
            }

            eatStream(torProcess.getInputStream(), false);
            eatStream(torProcess.getErrorStream(), true);

            try {
                // On platforms other than Windows we run as a daemon and so we need to wait for the process to detach
                // or exit. In the case of Windows the equivalent is running as a service and unfortunately that requires
                // managing the service, such as turning it off or uninstalling it when it's time to move on. Any number
                // of errors can prevent us from doing the cleanup and so we would leave the process running around. Rather
                // than do that on Windows we just let the process run on the exec and hence we don't have a good way
                // of detecting any problems it has.
                if (OsData.getOsType() != OsData.OsType.Windows) {
                    int exit = torProcess.waitFor();
                    if(exit != 0) {
                        LOG.warn("Tor exited with value " + exit);
                        return false;
                    }
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
        // Open a control connection and authenticate using the cookie file
        controlConnection = new TorControlConnection(controlSocket);
        controlConnection.authenticate(read(cookieFile));
        // Tell Tor to exit when the control connection is closed
        controlConnection.takeOwnership();
        controlConnection.resetConf(Arrays.asList(OWNER));
        // Register to receive events from the Tor process
        controlConnection.setEventHandler(this);
        controlConnection.setEvents(Arrays.asList(EVENTS));
        // If Tor was already running, find out whether it's bootstrapped
        if(!startProcess) {
            String phase = controlConnection.getInfo("status/bootstrap-phase");
            if(phase != null && phase.contains("PROGRESS=100")) {
                LOG.info("Tor has already bootstrapped");
                bootstrapped = true;
            }
        }
        return true;
    }

    private void eatStream(final InputStream inputStream, final boolean stdError) {
        new Thread() {
            @Override
            public void run() {
                Scanner scanner = new Scanner(inputStream);
                try {
                    while(scanner.hasNextLine()) {
                        if (stdError) {
                            LOG.error(scanner.nextLine());
                        } else {
                            LOG.info(scanner.nextLine());
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

    public int getSocksPort() {
        return SOCKS_PORT;
    }

    /**
     * Installs the Tor Onion Proxy binaries
     * @return binary to execute from the command line
     */
    abstract protected File installBinary();

    private boolean isConfigInstalled() {
        return geoIpFile.exists() && configFile.exists() && doneFile.exists();
    }

    private boolean installConfig() {
        InputStream in = null;
        OutputStream out = null;
        try {
            // Unzip the GeoIP database to the filesystem
            in = getZipInputStream(onionProxyContext.getGeoIpZip());
            out = new FileOutputStream(geoIpFile);
            copy(in, out);
            // Copy the config file to the filesystem
            in = getConfigInputStream();
            out = new FileOutputStream(configFile);
            copy(in, out);
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

    /**
     * Reads the input stream of a zip file, unzips it and returns
     * the output stream for the first file inside the zip file.
     * @param in Input stream of zipped file
     * @return Output stream of first file in zip file
     * @throws IOException
     */
    protected InputStream getZipInputStream(InputStream in) throws IOException {
        ZipInputStream zin = new ZipInputStream(in);
        if (zin.getNextEntry() == null) throw new IOException();
        return zin;
    }

    private InputStream getConfigInputStream() throws IOException {
        return onionProxyContext.getTorrc();
    }

    /**
     * Closes both input and output streams when done.
     * @param in
     * @param out
     * @throws IOException
     */
    protected void copy(InputStream in, OutputStream out) throws IOException {
        try {
            copyDontCloseInput(in, out);
        } finally {
            in.close();
        }
    }

    /**
     * Won't close the input stream when it's done, needed to handle ZipInputStreams
     * @param in Won't be closed
     * @param out Will be closed
     * @throws IOException
     */
    protected void copyDontCloseInput(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[4096];
            while(true) {
                int read = in.read(buf);
                if(read == -1) break;
                out.write(buf, 0, read);
            }
        } finally {
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

    protected void tryToClose(Closeable closeable) {
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

    public static void recursiveFileDelete(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                recursiveFileDelete(child);
            }
        }

        fileOrDirectory.delete();
    }
}
