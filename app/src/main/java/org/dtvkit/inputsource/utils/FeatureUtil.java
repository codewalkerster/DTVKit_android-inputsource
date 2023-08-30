package com.droidlogic.dtvkit.inputsource.util;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import com.droidlogic.settings.PropSettingManager;

public class FeatureUtil {
    private static final String TAG = "FeatureUtil";

    public static boolean getFeatureSupportTimeshifting() {
        return !PropSettingManager.getBoolean(PropSettingManager.TIMESHIFT_DISABLE, false);
    }

    public static boolean getFeatureCasReady() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.cas.ready", false);
    }

    public static boolean getFeatureCompliance() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.compliance", true)
                || getFeatureCasReady();
    }

    public static boolean getFeatureDmxLimit() {
        return !PropSettingManager.getBoolean("vendor.tv.dtv.dmx.nolimit", true);
    }

    public static boolean getFeatureSupportRecordAVServiceOnly() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.dvr.rec_av_only", true);
    }

    public static boolean getFeatureSupportPip() {
        return getFeatureSupportFullPipFccArchitecture() && PropSettingManager.getBoolean(PropSettingManager.ENABLE_PIP_SUPPORT, false);
    }

    public static boolean getFeatureSupportFcc() {
        return getFeatureSupportFullPipFccArchitecture() && PropSettingManager.getBoolean(PropSettingManager.ENABLE_FCC_SUPPORT, false);
    }

    public static boolean getFeatureSupportFullPipFccArchitecture() {
        return PropSettingManager.getBoolean(PropSettingManager.ENABLE_FULL_PIP_FCC_ARCHITECTURE, false);
    }

    public static boolean getFeatureSupportFillSurface() {
        return PropSettingManager.getBoolean(PropSettingManager.ENABLE_FILL_SURFACE, false);
    }

    public static boolean getFeatureSupportNewTvApp() {
        return isSdkAfterAndroidQ() && PropSettingManager.getBoolean(PropSettingManager.MATCH_NEW_TV_APP_ENABLE, true);
    }

    public static boolean getFeatureSupportCaptioningManager() {
        return !getFeatureSupportNewTvApp() && PropSettingManager.getBoolean(PropSettingManager.CAPTIONING_MANAGER_ENABLE, true);
    }

    public static boolean getFeatureSupportManualTimeshift() {
        return getFeatureSupportNewTvApp() || PropSettingManager.getBoolean(PropSettingManager.MANUAL_TIMESHIFT_ENABLE, false);
    }

    public static boolean isSdkAfterAndroidQ() {
        return VERSION.SDK_INT > VERSION_CODES.P + 1;
    }

    public static boolean getFeatureTimeshiftingPriorityHigh() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.tf.priority_high", false);
    }

    public static boolean getFeatureSupportHbbTV() {
        boolean isSupport = PropSettingManager.getBoolean("vendor.tv.dtv.hbbtv.enable", false);
        Log.d(TAG, "getFeatureSupportHbbTV: " + isSupport);
        return isSupport;
    }

    /*
     * ff000000 represent alpha = 0Xff, R = 0, G = 0, B = O
     */
    public static int getFeatureSupportFillSurfaceColor() {
        int result = -1;
        String colorStr = PropSettingManager.getString(PropSettingManager.ENABLE_FILL_SURFACE_COLOR, "0");
        try {
            result = (int) Long.parseLong(colorStr, 16);
        } catch (Exception e) {
            Log.d(TAG, "getFeatureSupportFillSurfaceColor Exception = " + e.getMessage());
        }
        return result;
    }

    public static boolean getFeatureSupportFvp() {
        boolean isSupport = PropSettingManager.getBoolean("vendor.tv.dtv.fvp.enable", false);
        Log.d(TAG, "getFeatureSupportFvp: " + isSupport);
        return isSupport;
    }
    public static boolean getFeatureSupportTunerFramework() {
        boolean isSupport = PropSettingManager.getBoolean("vendor.tv.dtv.tuner.framework.enable", false);
        Log.d(TAG, "getFeatureSupportTunerFramework: " + isSupport);
        return isSupport;
    }
}