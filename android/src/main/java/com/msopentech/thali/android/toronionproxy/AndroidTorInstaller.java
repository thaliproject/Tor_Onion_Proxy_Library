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
import android.util.Log;
import com.msopentech.thali.toronionproxy.TorInstaller;
import com.msopentech.thali.toronionproxy.android.R;
import org.torproject.android.binary.TorResourceInstaller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeoutException;

/**
 * Installs Tor for an Android app. This is a wrapper around the <code>TorResourceInstaller</code>.
 */
public class AndroidTorInstaller extends TorInstaller {

    private final TorResourceInstaller resourceInstaller;

    private static final String TAG = "TorInstaller";

    private final Context context;

    private File torrcFile;

   /**
     * The configDir will be the location of tor configuration files. It contains the files, geoip, geoip6,
     * bridges.txt and the default torrc file.
     *
     * The location of tor executable will be in the Android native library directory for the app.
     */
    public AndroidTorInstaller(Context context, File configDir) {
        this.resourceInstaller = new TorResourceInstaller(context, configDir);
        this.context = context;
    }

    public void updateTorConfigCustom(String content) throws IOException, TimeoutException {
        if(torrcFile == null) {
            throw new FileNotFoundException("Unable to find torrc file. Have you installed Tor resources?");
        }
        resourceInstaller.updateTorConfigCustom(torrcFile, content);
    }

    @Override
    public void setup() throws IOException {
        try {
            File torFile = resourceInstaller.installResources();
            if(torFile != null) {
                Log.d("AndroidTorInstaller", "tor executable = " + torFile.getAbsolutePath());
            } else {
                Log.w(TAG, "Failed to setup tor. No tor executable installed");
                throw new IOException("Failed to Failed to setup tor. No tor executable installed");
            }

            this.torrcFile = resourceInstaller.getTorrcFile();
            if(torrcFile != null) {
                Log.d("AndroidTorInstaller", "torrc = " + torrcFile.getAbsolutePath());
            } else {
                Log.w(TAG, "Failed to setup tor. No torrc file installed");
                throw new IOException("Failed to Failed to setup tor. No torrc file installed");
            }

        } catch (TimeoutException e) {
            Log.w(TAG, "Failed to setup tor: " + e.getMessage());
            throw new IOException(e);
        }
    }

    @Override
    public InputStream openBridgesStream() throws IOException {
        return context.getResources().openRawResource(R.raw.bridges);
    }
}