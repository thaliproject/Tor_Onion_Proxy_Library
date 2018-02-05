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

import com.msopentech.thali.toronionproxy.FileUtilities;
import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.OsData;
import com.msopentech.thali.toronionproxy.WriteObserver;

import java.io.*;

public final class JavaOnionProxyContext extends OnionProxyContext {

    @Override
    public boolean setup() throws IOException {
        if (!getWorkingDirectory().exists() && !getWorkingDirectory().mkdirs()) {
            throw new RuntimeException("Could not create root directory!");
        }

        FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(GEO_IP_NAME), geoIpFile);
        FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(GEO_IPV_6_NAME), geoIpv6File);
        FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(TORRC_NAME), torrcFile);

        switch(OsData.getOsType()) {
            case WINDOWS:
            case LINUX_32:
            case LINUX_64:
            case MAC:
                FileUtilities.extractContentFromZip(getWorkingDirectory(),
                        getAssetOrResourceByName(getPathToTorExecutable() + "tor.zip"));
                break;
            default:
                throw new RuntimeException("We don't support Tor on this OS yet");
        }

        // We need to edit the config file to specify exactly where the cookie/geoip files should be stored, on
        // Android this is always a fixed location relative to the configFiles which is why this extra step
        // wasn't needed in Briar's Android code. But in Windows it ends up in the user's AppData/Roaming. Rather
        // than track it down we just tell Tor where to put it.
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(new BufferedWriter(new FileWriter(getTorrcFile(), true)));
            printWriter.println("CookieAuthFile " + getCookieFile().getAbsolutePath());
            printWriter.println("PidFile "
                    + new File(getWorkingDirectory(), "pid").getAbsolutePath());

            // For some reason the GeoIP's location can only be given as a file name, not a path and it has
            // to be in the data directory so we need to set both
            printWriter.println("DataDirectory " + getWorkingDirectory().getAbsolutePath());
            printWriter.println("GeoIPFile " + getGeoIpFile().getAbsolutePath());
            printWriter.println("GeoIPv6File " + getGeoIpv6File().getAbsolutePath());
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
        setExecutable(getTorExecutableFile());
        return true;
    }

    public JavaOnionProxyContext(File workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public WriteObserver generateWriteObserver(File file) {
        try {
            return new JavaWatchObserver(file);
        } catch (IOException e) {
            throw new RuntimeException("Could not create JavaWatchObserver", e);
        }
    }

    @Override
    public String getProcessId() {
        // This is a horrible hack. It seems like more JVMs will return the process's PID this way, but not guarantees.
        String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        return processName.split("@")[0];
    }

    protected InputStream getAssetOrResourceByName(String fileName) throws IOException {
        return getClass().getResourceAsStream("/" + fileName);
    }

    private static void setExecutable(File fileBin) {
        fileBin.setReadable(true);
        fileBin.setExecutable(true);
        fileBin.setWritable(false);
        fileBin.setWritable(true, true);
    }

    /**
     * Files we pull out of the AAR or JAR are typically at the root but for executables outside
     * of Android the executable for a particular platform is in a specific sub-directory.
     * @return Path to executable in JAR Resources
     */
    protected String getPathToTorExecutable() {
        String path = "native/";
        switch (OsData.getOsType()) {
            case ANDROID:
                return "";
            case WINDOWS:
                return path + "windows/x86/"; // We currently only support the x86 build but that should work everywhere
            case MAC:
                return path +  "osx/x64/"; // I don't think there even is a x32 build of Tor for Mac, but could be wrong.
            case LINUX_32:
                return path + "linux/x86/";
            case LINUX_64:
                return path + "linux/x64/";
            default:
                throw new RuntimeException("We don't support Tor on this OS");
        }
    }

    public String getTorExecutableFileName() {
        switch(OsData.getOsType()) {
            case LINUX_32:
            case LINUX_64:
                return "tor";
            case WINDOWS:
                return "tor.exe";
            case MAC:
                return "tor.real";
            default:
                throw new RuntimeException("We don't support Tor on this OS");
        }
    }
}
