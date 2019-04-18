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

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class TorConfigTest {

    private File sampleFile = new File("sample");

    @Test(expected = IllegalArgumentException.class)
    public void nullConstruct() {
        new TorConfig.Builder(null, null);
    }

    @Test
    public void defaultDataDir() {
        TorConfig config = new TorConfig.Builder(sampleFile, sampleFile).build();
        assertEquals(new File(sampleFile, "lib/tor").getPath(), config.getDataDir().getPath());
    }

    @Test
    public void defaultCookie() {
        TorConfig config = new TorConfig.Builder(sampleFile, sampleFile).build();
        assertEquals(new File(sampleFile, "lib/tor/control_auth_cookie").getPath(), config.getCookieAuthFile().getPath());
    }

    @Test
    public void defaultHostname() {
        TorConfig config = new TorConfig.Builder(sampleFile, sampleFile).build();
        assertEquals(new File(sampleFile, "lib/tor/hostname").getPath(), config.getHostnameFile().getPath());
    }

    @Test
    public void libraryPathRelativeToExecutable() {
        TorConfig config = new TorConfig.Builder(sampleFile, sampleFile).torExecutable(new File(sampleFile, "exedir/tor.real")).build();
        assertEquals(new File(sampleFile, "exedir").getPath(), config.getLibraryPath().getPath());
    }

    @Test
    public void defaultCookieWithDataDir() {
        File dataDir = new File("sample/datadir");
        TorConfig config = new TorConfig.Builder(sampleFile, sampleFile).dataDir(dataDir).build();
        assertEquals(new File(dataDir, "control_auth_cookie").getPath(), config.getCookieAuthFile().getPath());
    }

    @Test
    public void geoip() {
        TorConfig config = new TorConfig.Builder(sampleFile, sampleFile).build();
        assertEquals(new File(sampleFile, TorConfig.GEO_IP_NAME).getPath(), config.getGeoIpFile().getPath());
    }
}
