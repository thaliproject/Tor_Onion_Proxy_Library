package com.msopentech.thali.toronionproxy;

import android.content.Context;

import com.msopentech.thali.android.installer.AndroidTorInstaller;
import com.msopentech.thali.toronionproxy.android.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class TestTorInstaller extends AndroidTorInstaller {
    public TestTorInstaller(Context context, File configDir) {
        super(context, configDir);
    }

    @Override
    public InputStream openBridgesStream() throws IOException {
        return context.getResources().openRawResource(R.raw.bridges);
    }
}
