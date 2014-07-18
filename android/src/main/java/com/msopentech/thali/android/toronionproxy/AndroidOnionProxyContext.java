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

package com.msopentech.thali.android.toronionproxy;

import android.content.Context;
import com.msopentech.thali.toronionproxy.FileUtilities;
import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.WriteObserver;

import java.io.File;
import java.io.IOException;

import static android.content.Context.MODE_PRIVATE;

public class AndroidOnionProxyContext implements OnionProxyContext {
    private final Context context;
    private final static String geoIpName = "geoip";
    private final static String torrcName = "torrc";
    private final static String torExecutableName = "tor";
    private final File workingDirectory;
    private final File geoIpFile;
    private final File torrcFile;
    private final File torExecutableFile;
    private final File cookieFile;
    private final File hostnameFile;

    public AndroidOnionProxyContext(Context context, String workingSubDirectoryName) {
        this.context = context;
        workingDirectory = context.getDir(workingSubDirectoryName, MODE_PRIVATE);
        geoIpFile = new File(getWorkingDirectory(), geoIpName);
        torrcFile = new File(getWorkingDirectory(), torrcName);
        torExecutableFile = new File(getWorkingDirectory(), torExecutableName);
        cookieFile = new File(getWorkingDirectory(), ".tor/control_auth_cookie");
        hostnameFile = new File(getWorkingDirectory(), "/hiddenservice/hostname");
    }

    @Override
    public void installFiles() throws IOException {
        if (workingDirectory.exists() == false && workingDirectory.mkdirs() == false) {
            throw new RuntimeException("Could not create Tor working directory.");
        }
        FileUtilities.cleanInstallOneFile(context.getResources().getAssets().open(geoIpName), geoIpFile);
        FileUtilities.cleanInstallOneFile(context.getResources().getAssets().open(torrcName), torrcFile);
        FileUtilities.cleanInstallOneFile(context.getResources().getAssets().open(torExecutableName), torExecutableFile);
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
        return new AndroidWriteObserver(file);
    }

    @Override
    public String getProcessId() {
        return String.valueOf(android.os.Process.myPid());
    }
}
