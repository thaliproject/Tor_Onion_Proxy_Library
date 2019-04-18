package com.msopentech.thali.android.toronionproxy;

import android.content.Context;
import com.msopentech.thali.toronionproxy.DefaultSettings;

public class AndroidDefaultTorSettings extends DefaultSettings {

    private final Context context;

    public AndroidDefaultTorSettings(Context context) {
        this.context = context;
    }

    @Override
    public boolean hasBridges() {
        return true;
    }
}
