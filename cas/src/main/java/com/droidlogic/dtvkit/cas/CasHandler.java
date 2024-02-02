package com.droidlogic.dtvkit.cas;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public abstract class CasHandler {
    protected int mCasSystem = 0;
    public static final int CAS_SYSTEM_IRDETO = 1;
    public static final int CAS_SYSTEM_NAGRA = 2;
    public abstract void destroy(@NonNull Context context);
    public abstract void handleCasProvider(@NonNull JSONObject casEvent);
    public abstract void onPlayStatusStart(@NonNull String uri);
    public abstract void onPlayingStopped();
    public abstract void getCasInitialStatus();
}
