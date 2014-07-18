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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class JavaOnionProxyContext implements OnionProxyContext {
    private final static String geoIpName = "geoip";
    private final static String torrcName = "torrc";
    private final File workingDirectory;
    private final File geoIpFile;
    private final File torrcFile;
    private final File torExecutableFile;
    private final File cookieFile;
    private final File hostnameFile;

    public JavaOnionProxyContext(String workingSubDirectoryName) {
        workingDirectory = new File("OnionProxyJavaTests", workingSubDirectoryName);
        geoIpFile = new File(getWorkingDirectory(), geoIpName);
        torrcFile = new File(getWorkingDirectory(), torrcName);
        torExecutableFile = new File(getWorkingDirectory(), getTorExecutableFileName());
        cookieFile = new File(getWorkingDirectory(), ".tor/control_auth_cookie");
        hostnameFile = new File(getWorkingDirectory(), "/hiddenservice/hostname");
    }

    @Override
    public void installFiles() throws IOException {
        if (workingDirectory.exists() == false && workingDirectory.mkdirs() == false) {
            throw new RuntimeException("Could not create root directory!");
        }
        FileUtilities.cleanInstallOneFile(getClass().getResourceAsStream("/" + geoIpName), geoIpFile);
        FileUtilities.cleanInstallOneFile(getClass().getResourceAsStream("/" + torrcName), torrcFile);
        String pathToTorExecutable = getPathToTorExecutable();

        switch(OsData.getOsType()) {
            case Windows:
                FileUtilities.cleanInstallOneFile(
                        getClass().getResourceAsStream(pathToTorExecutable + getTorExecutableFileName()),
                        torExecutableFile);
                break;
            case Mac:
                FileUtilities.extractContentFromZip(getWorkingDirectory(), getClass().getResourceAsStream(pathToTorExecutable + "tor.zip"));
                break;
            default:
                throw new RuntimeException("We don't support Tor on this OS yet");
        }
    }

    @Override
    public File getGeoIpFile() {
        return geoIpFile;
    }

    @Override
    public File getTorrcFile() {
        return torrcFile;
    }

    @Override
    public File getCookieFile() {
        return cookieFile;
    }

    @Override
    public File getHostNameFile() {
        return hostnameFile;
    }

    @Override
    public File getTorExecutableFile() {
        return torExecutableFile;
    }

    @Override
    public File getWorkingDirectory() {
        return workingDirectory;
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

    private String getTorExecutableFileName() {
        switch(OsData.getOsType()) {
            case Windows:
                return "tor.exe";
            case Mac:
                return "tor.real";
            default:
                throw new RuntimeException("We don't support Tor on this OS yet");
        }
    }

    /**
     * Returns the path in the resources directory to the right executable for this platform, the returned path will
     * always end in a slash.
     * @return A slash terminated path to the proper executable for this platform.
     */
    private String getPathToTorExecutable() {
        String path = "/native/";
        switch (OsData.getOsType()) {
            case Windows:
                return path + "windows/x86/"; // We currently only support the x86 build but that should work everywhere
            case Mac:
                return path +  "osx/x64/"; // I don't think there even is a x32 build of Tor for Mac, but could be wrong.
            default:
                throw new RuntimeException("We don't support Tor on this OS yet");
        }
    }
}
