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

import java.io.File;
import java.io.IOException;

/**
 * Holds Tor configuration information.
 */
public final class TorConfig {

    public final static String GEO_IP_NAME = "geoip";
    public final static String GEO_IPV_6_NAME = "geoip6";
    public final static String TORRC_NAME = "torrc";
    private final static String HIDDEN_SERVICE_NAME = "hiddenservice";
    private final Object configLock = new Object();
    private File geoIpFile;
    private File geoIpv6File;
    private File torrcFile;
    private File torExecutableFile;
    private File hiddenServiceDir;
    private File dataDir;
    private File configDir;
    private File homeDir;
    private File hostnameFile;
    private File cookieAuthFile;
    private File libraryPath;
    private File resolveConf;
    private File controlPortFile;
    private File installDir;
    private int fileCreationTimeout;

    /**
     * Creates simplest default config. All tor files will be relative to the configDir root.
     *
     * @param configDir
     * @return
     */
    public static TorConfig createDefault(File configDir) {
        return new Builder(configDir, configDir).build();
    }

    /**
     * All files will be in single directory: collapses the data and config directories
     *
     * @param configDir
     * @return
     */
    public static TorConfig createFlatConfig(File configDir) {
        return createConfig(configDir, configDir, configDir);
    }

    public static TorConfig createConfig(File installDir, File configDir, File dataDir) {
        Builder builder = new Builder(installDir, configDir);
        builder.dataDir(dataDir);
        return builder.build();
    }

    public File getInstallDir() {
        return installDir;
    }

    public File getHiddenServiceDir() {
        return hiddenServiceDir;
    }

    public File getConfigDir() {
        return configDir;
    }

    public File getHomeDir() {
        return homeDir;
    }

    /**
     * The <base32-encoded-fingerprint>.onion domain name for this hidden service. If the hidden service is
     * restricted to authorized clients only, this file also contains authorization data for all clients.
     *
     * @return hostname file
     */
    public File getHostnameFile() {
        return hostnameFile;
    }

    public File getLibraryPath() {
        return libraryPath;
    }

    public File getDataDir() {
        return dataDir;
    }

    public File getGeoIpFile() {
        return geoIpFile;
    }

    public File getGeoIpv6File() {
        return geoIpv6File;
    }

    public File getTorrcFile() {
        return torrcFile;
    }

    public File getResolveConf() {
        return resolveConf;
    }

    public File getControlPortFile() {
        return controlPortFile;
    }

    /**
     * Resolves the tor configuration file. If the torrc file hasn't been set, then this method will attempt to
     * resolve the config file by looking in the root of the $configDir and then in $user.home directory
     *
     * @return torrc file
     * @throws IOException if torrc file is not resolved
     */
    public File resolveTorrcFile() throws IOException {
        synchronized (configLock) {
            if (torrcFile == null || !torrcFile.exists()) {
                File tmpTorrcFile = new File(configDir, TORRC_NAME);
                if (!tmpTorrcFile.exists()) {
                    tmpTorrcFile = new File(homeDir, "." + TORRC_NAME);
                    if (!tmpTorrcFile.exists()) {
                        torrcFile = new File(configDir, TORRC_NAME);
                        if (!torrcFile.createNewFile()) {
                            throw new IOException("Failed to create torrc file");
                        }
                    } else {
                        torrcFile = tmpTorrcFile;
                    }
                } else {
                    torrcFile = tmpTorrcFile;
                }
            }
            return torrcFile;
        }
    }

    public File getTorExecutableFile() {
        return torExecutableFile;
    }

    /**
     * Used for cookie authentication with the controller. Location can be overridden by the CookieAuthFile config option.
     * Regenerated on startup. See control-spec.txt in torspec for details. Only used when cookie authentication is enabled.
     *
     * @return
     */
    public File getCookieAuthFile() {
        return cookieAuthFile;
    }

    /**
     * When tor starts it waits for the control port and cookie auth files to be created before it proceeds to the
     * next step in startup. If these files are not created after a certain amount of time, then the startup has
     * failed.
     *
     * This method returns how much time to wait in seconds until failing the startup.
     */
    public int getFileCreationTimeout() {
        return fileCreationTimeout;
    }

    @Override
    public String toString() {
        return "TorConfig{" +
                "geoIpFile=" + geoIpFile +
                ", geoIpv6File=" + geoIpv6File +
                ", torrcFile=" + torrcFile +
                ", torExecutableFile=" + torExecutableFile +
                ", hiddenServiceDir=" + hiddenServiceDir +
                ", dataDir=" + dataDir +
                ", configDir=" + configDir +
                ", installDir=" + installDir +
                ", homeDir=" + homeDir +
                ", hostnameFile=" + hostnameFile +
                ", cookieAuthFile=" + cookieAuthFile +
                ", libraryPath=" + libraryPath +
                '}';
    }

    /**
     * Builder for TorConfig.
     */
    public static class Builder {

        private File torExecutableFile;
        private final File configDir;
        private File geoIpFile;
        private File geoIpv6File;
        private File torrcFile;
        private File hiddenServiceDir;
        private File dataDir;
        private File homeDir;
        private File libraryPath;
        private File cookieAuthFile;
        private File hostnameFile;
        private File resolveConf;
        private File controlPortFile;
        private File installDir;
        private int fileCreationTimeout;

        /**
         * Constructs a builder with the specified configDir and installDir. The install directory contains executable
         * and libraries, while the configDir is for writeable files.
         * <p>
         * For Linux, the LD_LIBRARY_PATH will be set to the home directory, Any libraries must be in the installDir.
         * <p>
         * For all platforms the configDir will be the default parent location of all files unless they are explicitly set
         * to a different location in this builder.
         *
         * @param configDir
         * @throws IllegalArgumentException if configDir is null
         */
        public Builder(File installDir, File configDir) {
            if (installDir == null) {
                throw new IllegalArgumentException("installDir is null");
            }
            if (configDir == null) {
                throw new IllegalArgumentException("configDir is null");
            }
            this.configDir = configDir;
            this.installDir = installDir;
        }

        /**
         * Home directory of user.
         * <p>
         * Default value: $home.user if $home.user environment property exists, otherwise $configDir. On Android, this
         * will always default to $configDir.
         *
         * @param homeDir the home directory of the user
         * @return builder
         */
        public Builder homeDir(File homeDir) {
            this.homeDir = homeDir;
            return this;
        }

        public Builder torExecutable(File file) {
            this.torExecutableFile = file;
            return this;
        }

        /**
         * Store data files for a hidden service in DIRECTORY. Every hidden service must have a separate directory.
         * You may use this option multiple times to specify multiple services. If DIRECTORY does not exist, Tor will
         * create it. (Note: in current versions of Tor, if DIRECTORY is a relative path, it will be relative to the
         * current working directory of Tor instance, not to its DataDirectory. Do not rely on this behavior; it is not
         * guaranteed to remain the same in future versions.)
         * <p>
         * Default value: $configDir/hiddenservices
         *
         * @param directory hidden services directory
         * @return builder
         */
        public Builder hiddenServiceDir(File directory) {
            this.hiddenServiceDir = directory;
            return this;
        }

        /**
         * A filename containing IPv6 GeoIP data, for use with by-country statistics.
         * <p>
         * Default value: $configDir/geoip6
         *
         * @param file geoip6 file
         * @return builder
         */
        public Builder geoipv6(File file) {
            this.geoIpv6File = file;
            return this;
        }

        /**
         * A filename containing IPv4 GeoIP data, for use with by-country statistics.
         * <p>
         * Default value: $configDir/geoip
         *
         * @param file geoip file
         * @return builder
         */
        public Builder geoip(File file) {
            this.geoIpFile = file;
            return this;
        }

        /**
         * Store working data in DIR. Can not be changed while tor is running.
         * <p>
         * Default value: $configDir/lib/tor
         *
         * @param directory directory where tor runtime data is stored
         * @return builder
         */
        public Builder dataDir(File directory) {
            this.dataDir = directory;
            return this;
        }

        /**
         * The configuration file, which contains "option value" pairs.
         * <p>
         * Default value: $configDir/torrc
         *
         * @param file
         * @return
         */
        public Builder torrc(File file) {
            this.torrcFile = file;
            return this;
        }

        public Builder installDir(File file) {
            this.installDir = file;
            return this;
        }

        public Builder libraryPath(File directory) {
            this.libraryPath = directory;
            return this;
        }

        public Builder cookieAuthFile(File file) {
            this.cookieAuthFile = file;
            return this;
        }

        public Builder hostnameFile(File file) {
            this.hostnameFile = file;
            return this;
        }

        public Builder resolveConf(File resolveConf) {
            this.resolveConf = resolveConf;
            return this;
        }

        /**
         * When tor starts it waits for the control port and cookie auth files to be created before it proceeds to the
         * next step in startup. If these files are not created after a certain amount of time, then the startup has
         * failed.
         *
         * This method specifies how much time to wait until failing the startup.
         *
         * @param timeout in seconds
         */
        public Builder fileCreationTimeout(int timeout) {
            this.fileCreationTimeout = timeout;
            return this;
        }

        /**
         * Builds torConfig and sets default values if not explicitly configured through builder.
         *
         * @return torConfig
         */
        public TorConfig build() {
            if(homeDir == null) {
                String userHome = System.getProperty("user.home");
                homeDir = (userHome != null && !"".equals(userHome) && !"/".equals(userHome)) ? new File(userHome) : configDir;
            }

            if (torExecutableFile == null) {
                torExecutableFile = new File(installDir, getTorExecutableFileName());
            }

            if (geoIpFile == null) {
                geoIpFile = new File(configDir, GEO_IP_NAME);
            }

            if (geoIpv6File == null) {
                geoIpv6File = new File(configDir, GEO_IPV_6_NAME);
            }

            if (torrcFile == null) {
                torrcFile = new File(configDir, TORRC_NAME);
            }

            if (hiddenServiceDir == null) {
                hiddenServiceDir = new File(configDir, HIDDEN_SERVICE_NAME);
            }

            if (dataDir == null) {
                dataDir = new File(configDir, "lib/tor");
            }

            if (libraryPath == null) {
                libraryPath = torExecutableFile.getParentFile();
            }

            if(hostnameFile == null) {
                hostnameFile = new File(dataDir, "hostname");
            }

            if(cookieAuthFile == null) {
                cookieAuthFile = new File(dataDir, "control_auth_cookie");
            }

            if(resolveConf == null) {
                resolveConf = new File(configDir, "resolv.conf");
            }

            if(controlPortFile == null) {
                controlPortFile = new File(dataDir, "control.txt");
            }

            if(fileCreationTimeout <= 0) {
                fileCreationTimeout = 15;
            }

            TorConfig config = new TorConfig();
            config.hiddenServiceDir = hiddenServiceDir;
            config.torExecutableFile = torExecutableFile;
            config.dataDir = dataDir;
            config.torrcFile = torrcFile;
            config.geoIpv6File = geoIpv6File;
            config.geoIpFile = geoIpFile;
            config.homeDir = homeDir;
            config.configDir = configDir;
            config.hostnameFile = hostnameFile;
            config.cookieAuthFile = cookieAuthFile;
            config.libraryPath = libraryPath;
            config.resolveConf = resolveConf;
            config.controlPortFile = controlPortFile;
            config.installDir = installDir;
            config.fileCreationTimeout = fileCreationTimeout;
            return config;
        }

        private static String getTorExecutableFileName() {
            switch (OsData.getOsType()) {
                case ANDROID:
                    return "libtor.so";
                case LINUX_32:
                case LINUX_64:
                case MAC:
                    return "tor";
                case WINDOWS:
                    return "tor.exe";
                default:
                    throw new RuntimeException("We don't support Tor on this OS");
            }
        }

    }
}
