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
 * Provides context information about the environment. Implementating classes provide logic for setting up
 * the specific environment
 */
abstract public class OnionProxyContext {

    protected final static String HIDDENSERVICE_DIRECTORY_NAME = "hiddenservice";
    protected final static String GEO_IP_NAME = "geoip";
    protected final static String GEO_IPV_6_NAME = "geoip6";
    protected final static String TORRC_NAME = "torrc";
    protected final File workingDirectory;
    protected final File geoIpFile;
    protected final File geoIpv6File;
    protected final File torrcFile;
    protected final File torExecutableFile;
    protected final File cookieFile;
    protected final File hostnameFile;

    /**
     * Constructs instance of <code>OnionProxyContext</code>
     *
     * @param workingDirectory the working/installation directory for tor
     */
    public OnionProxyContext(File workingDirectory) {
        if(workingDirectory == null) {
            throw new IllegalArgumentException("working directory is null");
        }
        this.workingDirectory = workingDirectory;
        geoIpFile = new File(getWorkingDirectory(), GEO_IP_NAME);
        geoIpv6File = new File(getWorkingDirectory(), GEO_IPV_6_NAME);
        torrcFile = new File(getWorkingDirectory(), TORRC_NAME);
        torExecutableFile = new File(getWorkingDirectory(), getTorExecutableFileName());
        cookieFile = new File(getWorkingDirectory(), ".tor/control_auth_cookie");
        hostnameFile = new File(getWorkingDirectory(), "/" + HIDDENSERVICE_DIRECTORY_NAME + "/hostname");
    }


    public final File getGeoIpFile() {
        return geoIpFile;
    }

    public final File getGeoIpv6File() {
        return geoIpv6File;
    }

    public final File getTorrcFile() {
        return torrcFile;
    }

    public final File getCookieFile() {
        return cookieFile;
    }

    public final File getHostNameFile() {
        return hostnameFile;
    }

    public final File getTorExecutableFile() {
        return torExecutableFile;
    }

    public final File getWorkingDirectory() {
        return workingDirectory;
    }

    public final String getHiddenserviceDirectoryName() {
        return HIDDENSERVICE_DIRECTORY_NAME;
    }

    /**
     * Sets up and installs the tor environment. Files will install to the workingDirectory
     *
     * @return true if installation a success, otherwise false
     */
    public abstract boolean setup() throws IOException;

    public abstract String getProcessId();

    public abstract WriteObserver generateWriteObserver(File file);

    public abstract String getTorExecutableFileName();

    @Override
    public String toString() {
        return "OnionProxyContext{" +
                "workingDirectory=" + workingDirectory +
                ", geoIpFile=" + geoIpFile +
                ", geoIpv6File=" + geoIpv6File +
                ", torrcFile=" + torrcFile +
                ", torExecutableFile=" + torExecutableFile +
                ", cookieFile=" + cookieFile +
                ", hostnameFile=" + hostnameFile +
                '}';
    }
}
