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

import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.OsData;
import com.msopentech.thali.toronionproxy.WriteObserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class JavaOnionProxyContext implements OnionProxyContext {
    private File workingDirectory;

    public JavaOnionProxyContext(String workingSubDirectoryName) {
        workingDirectory = new File("OnionProxyJavaTests", workingSubDirectoryName);
        if (workingDirectory.exists() == false && workingDirectory.mkdirs() == false) {
            throw new RuntimeException("Could not create root directory!");
        }
    }

    @Override
    public InputStream getTorrc() throws IOException {
        return getClass().getResourceAsStream("/torrc");
    }

    @Override
    public InputStream getGeoIpZip() throws IOException {
        return getClass().getResourceAsStream("/geoip");
    }

    @Override
    public InputStream getTorExecutableZip() throws IOException {
        String path = "/native/";
        String osName = System.getProperty("os.name");
        if (osName.contains("Windows")) {
            path += "windows/x86/"; // We currently only support the x86 build but that should work everywhere
        } else {
            throw new RuntimeException("We don't support Tor on this OS yet");
        }
        return getClass().getResourceAsStream(path + "tor");
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
}
