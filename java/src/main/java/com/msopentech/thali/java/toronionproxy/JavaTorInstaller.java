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
package com.msopentech.thali.java.toronionproxy;

import com.msopentech.thali.toronionproxy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.TimeoutException;

import static com.msopentech.thali.toronionproxy.FileUtilities.cleanInstallOneFile;
import static com.msopentech.thali.toronionproxy.FileUtilities.extractContentFromZip;
import static com.msopentech.thali.toronionproxy.FileUtilities.setPerms;

public final class JavaTorInstaller extends TorInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(JavaTorInstaller.class);

    private final TorConfig config;

    private final OnionProxyContext context;

    public JavaTorInstaller(OnionProxyContext context) {
        this.context = context;
        this.config = context.getConfig();
    }

    /**
     * The executable for a particular platform is in a specific sub-directory of the JAR file.
     *
     * @return relative path to executable in JAR Resources
     */
    private static String getPathToTorExecutable() {
        String path = "native/";
        switch (OsData.getOsType()) {
            case WINDOWS:
                return path + "windows/x86/"; // We currently only support the x86 build but that should work everywhere
            case MAC:
                return path + "osx/x64/"; // I don't think there even is a x32 build of Tor for Mac, but could be wrong.
            case LINUX_32:
                return path + "linux/x86/";
            case LINUX_64:
                return path + "linux/x64/";
            default:
                throw new RuntimeException("We don't support Tor on this OS");
        }
    }

    @Override
    public boolean setup() throws IOException {
        LOG.info("Setting up tor");
        if (!context.createDataDir()) {
            throw new IOException("Could not create data directory!");
        }

        LOG.info("Installing resources: geoip=" + config.getGeoIpFile().getAbsolutePath()
                + ", torrc =" + config.getTorrcFile().getAbsolutePath());
        cleanInstallOneFile(getAssetOrResourceByName(TorConfig.GEO_IP_NAME), config.getGeoIpFile());
        cleanInstallOneFile(getAssetOrResourceByName(TorConfig.GEO_IPV_6_NAME), config.getGeoIpv6File());
        cleanInstallOneFile(getAssetOrResourceByName(TorConfig.TORRC_NAME), config.getTorrcFile());


        LOG.info("Installing tor executable: " + config.getTorExecutableFile().getAbsolutePath());
        File torParent = config.getTorExecutableFile().getParentFile();
        extractContentFromZip(torParent.exists() ? torParent : config.getTorExecutableFile(),
                getAssetOrResourceByName(getPathToTorExecutable() + "tor.zip"));

        PrintWriter printWriter = null;
        try {
            LOG.info("Updating torrc file");
            printWriter = new PrintWriter(new BufferedWriter(new FileWriter(config.getTorrcFile(), true)));
            printWriter.println("CookieAuthFile " + config.getCookieAuthFile().getAbsolutePath());
            printWriter.println("PidFile "
                    + new File(config.getDataDir(), "pid").getAbsolutePath());
            printWriter.println("GeoIPFile " + config.getGeoIpFile().getAbsolutePath());
            printWriter.println("GeoIPv6File " + config.getGeoIpv6File().getAbsolutePath());
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
        setPerms(config.getTorExecutableFile());
        return true;
    }

    /**
     * Updates the content of torrc file, writing out the specified content. This is currently unsupported (see issue #69)
     *
     * @param content content of the custom torrc
     * @throws IOException
     * @throws TimeoutException
     */
    @Override
    public void updateTorConfigCustom(String content) throws IOException, TimeoutException {
        throw new UnsupportedOperationException();//TODO: implement
    }

}
