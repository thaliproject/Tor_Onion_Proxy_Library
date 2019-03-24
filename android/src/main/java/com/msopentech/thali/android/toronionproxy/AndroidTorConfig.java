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
import com.msopentech.thali.toronionproxy.TorConfig;

import java.io.File;

import static android.content.Context.MODE_PRIVATE;
import static org.torproject.android.binary.TorServiceConstants.TOR_ASSET_KEY;

/**
 * Creates config file that is compatible with Android.
 */
public class AndroidTorConfig {

    /**
     * Creates a tor config file, where installDir is the directory containing the tor executables and native libraries.
     * This should reference the native library folder managed by Android.
     *
     * The configDirName contains the location of user writable config files and data.
     */
    public static TorConfig createConfig(String alternativeInstallDirName, String configDirName, Context context) {
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File torExecutable = new File(nativeDir,  "tor.so");
        File configDir = context.getDir(configDirName, MODE_PRIVATE);

        if(torExecutable.exists()) {
            TorConfig.Builder builder = new TorConfig.Builder(nativeDir, configDir);
            builder.homeDir(nativeDir);
            return builder.build();
        } else {
            File alternativeInstallDir = context.getDir(alternativeInstallDirName, MODE_PRIVATE);
            TorConfig.Builder builder = new TorConfig.Builder(alternativeInstallDir, configDir);
            builder.dataDir(new File(configDir, ".tor"));
            builder.homeDir(alternativeInstallDir);
            return builder.build();
        }
    }
}
