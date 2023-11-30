package org.dtvkit.inputsource.cas;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;

public class CasHelper {
    private static final String TAG = "CasHelper";
    private static CasHelper mInstance;
    private Class<?> mCasUtils = null;

    private final DtvkitGlueClient.SignalHandler mCasSignalHandler =
            (signal, data) -> {
                if ("cas".equals(signal)) {
                    JSONObject casEvent = null;
                    try {
                        String casString = (String) (data.get("CasJsonString"));
                        if (!TextUtils.isEmpty(casString)) {
                            casEvent = new JSONObject(casString.trim());
                        }
                    } catch (Exception ignore) {
                    }
                    if (casEvent != null) {
                        onCasSignal(casEvent);
                    }
                } else if ("PlayerStatusChanged".equals(signal)) {
                    if (data != null) {
                        String state = data.optString("state");
                        if ("playing".equals(state)) {
                            String type = data.optString("type");
                            if ("dvblive".equals(type)) {
                                onPlayingStart(data.optString("uri"));
                            }
                        } else if ("off".equals(state)) {
                            onPlayingStopped();
                        }
                    }
                }
            };

    private CasHelper() {
        initCasMethods();
    }

    public static CasHelper getInstance() {
        if (mInstance == null) {
            synchronized (CasHelper.class) {
                mInstance = new CasHelper();
            }
        }
        return mInstance;
    }

    public synchronized void init(@NonNull Context context) {
        if (mCasUtils != null) {
            try {
                Method ins = mCasUtils.getDeclaredMethod("getInstance");
                Object casUtilsInstance = ins.invoke(null);
                Class<?> callbackClass =
                        Class.forName("com.droidlogic.dtvkit.cas.CasUtils$CasCallback");
                Method init = mCasUtils.getDeclaredMethod(
                        "init", android.content.Context.class, callbackClass);
                Object callbackProxy = java.lang.reflect.Proxy.newProxyInstance(
                        mCasUtils.getClassLoader(),
                        new Class[] {callbackClass},
                        (proxy, method, args) -> {
                            if (method.getName().equals("onCasRequest")) {
                                long arg1 = (long) args[0];
                                String arg2 = (String) args[1];
                                return casSessionRequest(arg1, arg2);
                            } else if (method.getName().equals("equals")) {
                                return proxy == args[0];
                            } else if (method.getName().equals("hashCode")) {
                                return System.identityHashCode(proxy);
                            } else if (method.getName().equals("toString")) {
                                return "";
                            }
                            return null;
                        }
                );
                init.invoke(casUtilsInstance, context, callbackProxy);
            } catch (Exception ignore) {}
            DtvkitGlueClient.getInstance().registerSignalHandler(mCasSignalHandler);
        }
    }

    public void destroy(@NonNull Context context) {
        if (mCasUtils != null) {
            try {
                Method ins = mCasUtils.getDeclaredMethod("getInstance");
                Object casUtilsInstance = ins.invoke(null);
                Method des = mCasUtils.getDeclaredMethod(
                        "destroy",
                        android.content.Context.class);
                des.invoke(casUtilsInstance, context);
            } catch (Exception ignore) {}
            DtvkitGlueClient.getInstance().unregisterSignalHandler(mCasSignalHandler);
        }
    }

    public String casSessionRequest(long session, String request) {
        String result = null;

        if (TextUtils.isEmpty(request)) {
            return null;
        }
        JSONArray args = new JSONArray();
        String cmd = "Player.setCADescramblerIoctl";
        try
        {
            args.put(request);
            args.put(session);
            JSONObject obj =  DtvkitGlueClient.getInstance().request(cmd, args);
            result = obj.optString("data", "");
        } catch (Exception ignore) {}
        return result;
    }

    private synchronized void initCasMethods() {
        try {
            mCasUtils = Class.forName("com.droidlogic.dtvkit.cas.CasUtils");
        } catch (Exception e) {
            mCasUtils = null;
            Log.i(TAG, "Not cas product");
        }
    }

    private synchronized void onCasSignal(JSONObject event) {
        try {
            if (mCasUtils != null) {
                Method ins = mCasUtils.getDeclaredMethod("getInstance");
                Object casUtilsInstance = ins.invoke(null);
                Method onCasSignal = mCasUtils.getDeclaredMethod(
                        "onCasSignal", org.json.JSONObject.class);
                onCasSignal.invoke(casUtilsInstance, event);
            }
        } catch (Exception ignore) {}
    }

    private synchronized void onPlayingStart(String dvbUri) {
        try {
            if (mCasUtils != null) {
                Method ins = mCasUtils.getDeclaredMethod("getInstance");
                Object casUtilsInstance = ins.invoke(null);
                Method onPlayingStart = mCasUtils.getDeclaredMethod(
                        "onPlayingStart", String.class);
                onPlayingStart.invoke(casUtilsInstance, dvbUri);
            }
        } catch (Exception ignore) {}
    }

    private synchronized void onPlayingStopped() {
        try {
            if (mCasUtils != null) {
                Method ins = mCasUtils.getDeclaredMethod("getInstance");
                Object casUtilsInstance = ins.invoke(null);
                Method onPlayingStopped = mCasUtils.getDeclaredMethod("onPlayingStopped");
                onPlayingStopped.invoke(casUtilsInstance);
            }
        } catch (Exception ignore) {}
    }
}
