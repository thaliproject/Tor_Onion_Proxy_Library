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
import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.WriteObserver;

import org.torproject.android.binary.TorResourceInstaller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeoutException;

import static android.content.Context.MODE_PRIVATE;
import static org.torproject.android.binary.TorResourceInstaller.streamToFile;
import static org.torproject.android.binary.TorServiceConstants.TORRC_ASSET_KEY;
import static org.torproject.android.binary.TorServiceConstants.TOR_ASSET_KEY;

public class AndroidOnionProxyContext extends OnionProxyContext {

    private final Context context;

    private final TorResourceInstaller resourceInstaller;

    public AndroidOnionProxyContext(Context context, String workingSubDirectoryName) {
        super(context.getDir(workingSubDirectoryName, MODE_PRIVATE));
        this.context = context;
        this.resourceInstaller = new TorResourceInstaller(context, getWorkingDirectory());
    }

    @Override
    public WriteObserver generateWriteObserver(File file) {
        return new AndroidWriteObserver(file);
    }

    @Override
    public String getTorExecutableFileName() {
        return TOR_ASSET_KEY;
    }

    @Override
    public boolean setup() throws IOException {
        try {
            if(resourceInstaller.installResources()) {
                InputStream is = context.getAssets().open(TORRC_ASSET_KEY);
                File outFile = new File(getWorkingDirectory(), TORRC_ASSET_KEY);
                return streamToFile(is, outFile, false, false);
            }
            return false;
        } catch (TimeoutException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getProcessId() {
        return String.valueOf(android.os.Process.myPid());
    }
}
