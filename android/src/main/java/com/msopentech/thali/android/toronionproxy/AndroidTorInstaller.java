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

import com.msopentech.thali.toronionproxy.TorInstaller;

import org.torproject.android.binary.TorResourceInstaller;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeoutException;

import static org.torproject.android.binary.TorResourceInstaller.streamToFile;
import static org.torproject.android.binary.TorServiceConstants.TORRC_ASSET_KEY;

/**
 * Installs Tor for an Android app. This is a wrapper around the <code>TorResourceInstaller</code>.
 *
 * This installer will override the torrc file that is provided by <code>org.torproject.android.binary</code>
 * with the torrc in the toronionproxy assets folder.
 */
public class AndroidTorInstaller extends TorInstaller {

    private final TorResourceInstaller resourceInstaller;

    private final Context context;

    private final File installDir;

    private static final String TAG = "TorInstaller";

    public AndroidTorInstaller(Context context, File installDir) {
        this.context = context;
        this.installDir = installDir;
        this.resourceInstaller = new TorResourceInstaller(context, installDir);
    }

    @Override
    public boolean setup() throws IOException {
        try {
            if (resourceInstaller.installResources()) {
                InputStream is = context.getAssets().open(TORRC_ASSET_KEY);
                File outFile = new File(installDir, TORRC_ASSET_KEY);
                return streamToFile(is, outFile, false, false);
            }
            Log.w(TAG, "Failed to setup tor");
            return false;
        } catch (TimeoutException e) {
            Log.w(TAG, "Failed to setup tor: " + e.getMessage());
            throw new IOException(e);
        }
    }
}
