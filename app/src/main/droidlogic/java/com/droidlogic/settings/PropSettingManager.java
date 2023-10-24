package com.droidlogic.settings;

import android.util.Log;

import java.lang.reflect.Method;

import com.droidlogic.app.SystemControlManager;

public class PropSettingManager {

    private static final String TAG = "PropSettingManager";
    private static final boolean DEBUG = true;

    public static final String TV_STREAM_REAL_TIME = "vendor.sys.tv.stream.realtime";//sync with TvTime.java
    public static final String TV_STREAM_LOCAL_TIME = "vendor.sys.tv.stream.localtime";//sync with TvTime.java
    public static final String TV_STREAM_CHANGE_TIME = "vendor.sys.tv.stream.offsetchange";//sync with TvTime.java
    public static final String TV_STREAM_NEXT_TIME_TIME = "vendor.sys.tv.stream.timeozone.next";//sync with TvTime.java
    public static final String TV_PIP_STREAM_TIME = "vendor.sys.tv.stream.realtime1";//for pip
    public static final String PVR_RECORD_MODE = "vendor.tv.dtv.dvr.mode";//used in dtvkit pvr
    public static final String TIMESHIFT_DISABLE = "vendor.tv.dtv.tf.disable";
    public static final String ENABLE_PIP_SUPPORT = "vendor.tv.dtv.enable.pip";
    public static final String ENABLE_FCC_SUPPORT = "vendor.tv.dtv.enable.fcc";
    public static final String ENABLE_MULTI_FREQUENCY_SUPPORT = "multi_frequency";
    public static final String ENABLE_TUNER_NUMBER = "tuner_number";
    public static final String ENABLE_FILL_SURFACE = "vendor.tv.dtv.fill.surface";
    public static final String ENABLE_FILL_SURFACE_COLOR = "vendor.tv.dtv.fill.color";
    public static final String ENABLE_FULL_PIP_FCC_ARCHITECTURE = "vendor.tv.dtv.pipfcc.architecture";
    public static final String PVR_RECORD_MODE_CHANNEL = "0";
    public static final String PVR_RECORD_MODE_FREQUENCY = "1";
    //add to match new tv app in android r
    public static final String MATCH_NEW_TV_APP_ENABLE = "vendor.sys.tv.new.tv_app";
    public static final String CAPTIONING_MANAGER_ENABLE = "vendor.sys.tv.captioning_manager_enable";
    public static final String MANUAL_TIMESHIFT_ENABLE = "vendor.sys.tv.manual_timeshift_enable";
    public static final String ACTON_CONTROL_TIMESHIFT = "action_control_timeshift";
    public static final String VALUE_CONTROL_TIMESHIFT = "value_control_timeshift";
    public static final String VALUE_TIMESHIFT_DURATION = "value_timeshift_duration";
    public static final String VALUE_TIMESHIFT_START_MODE = "value_timeshift_start_mode";
    //ci simulate test define
    public static final String CI_PROFILE_ADD_TEST = "vendor.sys.tv.add.profile";
    public static final String CI_PROFILE_TEST = "vendor.sys.tv.test.profile";
    public static final String CI_PROFILE_SEARCH_TEST = "vendor.sys.tv.test.search";
    public static final String CI_MENU_ITEM_TEST = "vendor.sys.tv.test.ci_item";
    public static final String CIPLUS_CARD_INSERTED_STATE = "vendor.sys.tv.ciplus.card.state";
    //EWS simulate test define
    public static final String EWS_TEST = "vendor.sys.tv.test.ews";
    //TKGS
    public static final String TKGS_DEBUG_ENABLE = "vendor.sys.tkgs.debug.enable";

    public static String getProp(String key) {
        String result = null;
        SystemControlManager systemControl = SystemControlManager.getInstance();
        if (systemControl != null && key != null) {
            systemControl.getProperty(key);
        }
        if (DEBUG) {
            Log.d(TAG, "getProp key = " + key + ", result = " + result);
        }
        return result;
    }

    public static boolean setProp(String key, String val) {
        boolean result = false;
        SystemControlManager systemControl = SystemControlManager.getInstance();
        if (systemControl != null && key != null) {
            systemControl.setProperty(key, val);
            result = true;
        }
        if (DEBUG) {
            Log.d(TAG, "setProp key = " + key + ", val = " + val + ", result = " + result);
        }
        return result;
    }

    public static long getLong(String key, long def) {
        long result = def;
        try {
            Class clz = Class.forName("android.os.SystemProperties");
            Method method = clz.getMethod("getLong", String.class, long.class);
            result = (long)method.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "getLong Exception = " + e.getMessage());
        }
        if (DEBUG) {
            Log.i(TAG, "getLong key = " + key + ", result = " + result);
        }
        return result;
    }

    public static int getInt(String key, int def) {
        int result = def;
        try {
            Class clz = Class.forName("android.os.SystemProperties");
            Method method = clz.getMethod("getInt", String.class, int.class);
            result = (int)method.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "getLInt Exception = " + e.getMessage());
        }
        if (DEBUG) {
            Log.i(TAG, "getInt key = " + key + ", result = " + result);
        }
        return result;
    }

    public static String getString(String key, String def) {
        String result = def;
        try {
            Class clz = Class.forName("android.os.SystemProperties");
            Method method = clz.getMethod("get", String.class, String.class);
            result = (String)method.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "getString Exception = " + e.getMessage());
        }
        if (DEBUG) {
            Log.i(TAG, "getString key = " + key + ", result = " + result);
        }
        return result;
    }

    public static boolean getBoolean(String key, boolean def) {
        boolean result = def;
        try {
            Class clz = Class.forName("android.os.SystemProperties");
            Method method = clz.getMethod("getBoolean", String.class, boolean.class);
            result = (boolean)method.invoke(clz, key, def);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "getBoolean Exception = " + e.getMessage());
        }
        return result;
    }

    public static long getCurrentStreamTime(boolean streamTime) {
        long result = System.currentTimeMillis();
        if (streamTime) {
            result = result + getLong(TV_STREAM_REAL_TIME, 0);
        }
        return result;
    }

    public static long getCurrentPipStreamTime(boolean streamTime) {
        long result = System.currentTimeMillis();
        if (streamTime) {
            result = result + getLong(TV_PIP_STREAM_TIME, 0);
        }
        return result;
    }

    public static long getStreamTimeDiff() {
        long result = 0;
        result = getLong(TV_STREAM_REAL_TIME, 0);
        return result;
    }

    public static boolean resetRecordFrequencyFlag() {
        boolean result = false;
        String pvrRecordMode = getString(PropSettingManager.PVR_RECORD_MODE, PropSettingManager.PVR_RECORD_MODE_CHANNEL);
        if (PropSettingManager.PVR_RECORD_MODE_FREQUENCY.equals(pvrRecordMode)) {
            result = setProp(PropSettingManager.PVR_RECORD_MODE, PropSettingManager.PVR_RECORD_MODE_CHANNEL);
        }
        return result;
    }

    public static void updateStreamTime(long realTime, long localTime, long timeOfChange, long nextTimeOffset) {
        long currentSystemTime = System.currentTimeMillis();
        setProp(PropSettingManager.TV_STREAM_REAL_TIME, String.valueOf(realTime - currentSystemTime));
        setProp(PropSettingManager.TV_STREAM_LOCAL_TIME, String.valueOf(localTime - currentSystemTime));
        setProp(PropSettingManager.TV_STREAM_CHANGE_TIME, String.valueOf(timeOfChange));
        setProp(PropSettingManager.TV_STREAM_NEXT_TIME_TIME, String.valueOf(nextTimeOffset));
    }
}
