/*
Copyright (C) 2011-2014 Sublime Software Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import com.msopentech.thali.toronionproxy.OnionProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Scanner;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.EXTRA_NO_CONNECTIVITY;

/**
 * This code was created using code from TorPlugin in Briar
 */
public class AndroidOnionProxyManager extends OnionProxyManager {
    private static final Logger LOG = LoggerFactory.getLogger(AndroidOnionProxyManager.class);

    private volatile BroadcastReceiver networkStateReceiver;
    private final Context context;

    public AndroidOnionProxyManager(Context context) {
        super(new AndroidOnionProxyContext(context));
        this.context = context;

        // Register to receive network status events
        networkStateReceiver = new NetworkStateReceiver();
        IntentFilter filter = new IntentFilter(CONNECTIVITY_ACTION);
        context.registerReceiver(networkStateReceiver, filter);
    }

    @Override
    public boolean start() throws IOException {
        if (super.start()) {
            // Register to receive network status events
            networkStateReceiver = new NetworkStateReceiver();
            IntentFilter filter = new IntentFilter(CONNECTIVITY_ACTION);
            context.registerReceiver(networkStateReceiver, filter);
            return true;
        }
        return false;
    }

    @Override
    public void stop() throws IOException {
        super.stop();
        if(networkStateReceiver != null) {
            context.unregisterReceiver(networkStateReceiver);
        }
    }

    @SuppressLint("NewApi")
    public boolean setExecutable(File f) {
        if(Build.VERSION.SDK_INT >= 9) {
            return f.setExecutable(true, true);
        } else {
            String[] command = { "chmod", "700", f.getAbsolutePath() };
            try {
                return Runtime.getRuntime().exec(command).waitFor() == 0;
            } catch(IOException e) {
                LOG.warn(e.toString(), e);
            } catch(InterruptedException e) {
                LOG.warn("Interrupted while executing chmod");
                Thread.currentThread().interrupt();
            } catch(SecurityException e) {
                LOG.warn(e.toString(), e);
            }
            return false;
        }
    }

    protected File installBinary() {
        InputStream in = null;
        OutputStream out = null;
        try {
            File executableFile = new File(torDirectory, "tor");
            // Unzip the Tor binary to the filesystem
            in = getZipInputStream(onionProxyContext.getTorExecutableZip());
            out = new FileOutputStream(executableFile);
            copy(in, out);
            // Make the Tor binary executable
            if(!setExecutable(executableFile)) {
                throw new RuntimeException("Could not make Tor executable");
            }
            return executableFile;
        } catch(IOException e) {
            throw new RuntimeException(e);
        } finally {
            tryToClose(in);
            tryToClose(out);
        }
    }

    private class NetworkStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context ctx, Intent i) {
            if(!running) return;
            boolean online = !i.getBooleanExtra(EXTRA_NO_CONNECTIVITY, false);
            if(online) {
                // Some devices fail to set EXTRA_NO_CONNECTIVITY, double check
                Object o = ctx.getSystemService(CONNECTIVITY_SERVICE);
                ConnectivityManager cm = (ConnectivityManager) o;
                NetworkInfo net = cm.getActiveNetworkInfo();
                if(net == null || !net.isConnected()) online = false;
            }
            LOG.info("Online: " + online);
            try {
                enableNetwork(online);
            } catch(IOException e) {
                LOG.warn(e.toString(), e);
            }
        }
    }
}
