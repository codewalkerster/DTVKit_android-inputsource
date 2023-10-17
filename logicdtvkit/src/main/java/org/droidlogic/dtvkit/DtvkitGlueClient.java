package org.droidlogic.dtvkit;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.reflect.Method;


public class DtvkitGlueClient {
    private static final String TAG = "DtvkitGlueClient";

    public static final int INDEX_FOR_MAIN = 0;
    public static final int INDEX_FOR_PIP = 1;
    public static final int DIRECT_BUFFER_SIZE = 188;
    private static final int REQUEST_MESSAGE_TIMEOUT_SHORT_MILLIS = 1000;
    private static final int REQUEST_MESSAGE_TIMEOUT_LONG_MILLIS = 3000;
    private static final int REQUEST_MESSAGE_BOMB_MILLIS = 15000;
    private Handler mMainHandler = null;
    private static DtvkitGlueClient mSingleton = null;
    private final CopyOnWriteArrayList<Pair<Integer, SignalHandler>> mSignalHandlers = new CopyOnWriteArrayList<>();
    // debug used
    private final CopyOnWriteArrayList<Pair<String, SignalHandler>> mSignalHandlerFrom = new CopyOnWriteArrayList<>();
    // Notification object used to listen to the start of the rpcserver daemon.
    //private final ServiceNotification mServiceNotification = new ServiceNotification();
    //private static final int DTVKITSERVER_DEATH_COOKIE = 1000;
   // private IDTVKitServer mProxy = null;
    //private HALCallback mHALCallback;
    private OverlayTarget mTarget;
    private AudioHandler mAudioHandler;
    private ByteBuffer mDirectBuffer = null;
   // private SystemControlHandler mSysControlHandler;
    private SubtitleListener mListener;
    private PidFilterListener mPidListener;
    private native void nativeConnectDtvkit(DtvkitGlueClient client, ByteBuffer buffer);
    private native void nativeDisconnectDtvkit();
    private native void nativeSetMultiSurface(int index, Surface surface);
    private native void nativeSetSurface(Surface surface);
    private native void nativeSetSurfaceToPlayer(Surface surface);
    private native String nativeRequest(String resource, String json);
    private native void native_attachSubtitleCtl(int flag);
    private native void native_detachSubtitleCtl();
    private native void native_destroySubtitleCtl();
    private native boolean nativeIsdbtSupport();
    private native void native_UnCrypt(String src, String dest);
    private native void native_openUserData();
    private native void native_closeUserData();
    private native void native_nativeSubtitleSeekReset();
    private native void native_setRegionId(int regionId);
    private native void native_enablePidListener(boolean enable);

    static {
        try {
            Log.d(TAG, "calling load dtvkit jni");
            System.loadLibrary("dtvkit_jni");
            if (true == getFeatureSupportTunerFramework()) {
                System.loadLibrary("jdvrlib-jni");//workaround for vendor linker namespace,jdvrlib and dtvkit need same classloader. Can static link jdvrlib after debug
                System.loadLibrary("jcas_jni");
            }
        } catch (Exception e) {
            Log.d(TAG, "dtvkit_jni load error: " + e);
        }
    }

    //native callback
    public void notifySubtitleCallback(int width, int height, int dst_x, int dst_y, int dst_width, int dst_height, int[] data)
    {
         Log.d(TAG, "notifySubtitleCallBack received!!! width = " + width + ", height = " + height);
         if (mTarget != null) {
            mTarget.draw(width, height, dst_x, dst_y, dst_width, dst_height, data);
         }
    }

    public void notifyPidFilterData() {
        //Log.d(TAG, "notifyPidFilterData received!!!");
        try {
            if (mPidListener != null) {
                mDirectBuffer.clear();
                mPidListener.onPidFilterData(mDirectBuffer);//ByteBuffer data
            }
        } catch (NullPointerException e) {
            Log.d(TAG, "HBBTV PidListener NULL!");
        }
    }

    public void notifySubtitleCallbackEx(int type, int width, int height, int dst_x, int dst_y, int dst_width, int dst_height, int[] data)
    {
         Log.d(TAG, "notifySubtitleCallBackEx received!!! width = " + width + ", height = " + height);
         if (mListener != null) {
            mListener.drawEx(type, width, height, dst_x, dst_y, dst_width, dst_height, data);
         }
    }

    public void notifySubtitleCbCtlEx(int pause)
    {
         Log.d(TAG, "notifySubtitleCbCtlEx received!!! pause = " + pause);
         if (mListener != null) {
            mListener.pauseEx(pause);
         }
    }

    public void notifyCCSubtitleCallbackEx(boolean bShow, String json, int type) {
        Log.d(TAG, "notifyCCSubtitleCallbackEx type=" + type +", bShow=" + bShow);
        if (mListener != null) {
            mListener.drawCC(bShow, json, type);
        }
    }

    public void notifyDvbCallback(String resource, String json, int id) {
        Log.i(TAG, "notifyDvbCallback received!!! resource:" + resource + " (" + id + ")" + ",json" + json);
        JSONObject object;
        try {
            if (json.charAt(0) == '[') {
                JSONArray array = new JSONArray(json);
                object = new JSONObject();
                object.put("result", array);
            } else {
                object = new JSONObject(json);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return;
        }
        synchronized (mSignalHandlers) {
            for (Pair <Integer, SignalHandler> handler : mSignalHandlers) {
                if (handler.first == id)
                    handler.second.onSignal(resource, object);
            }
        }
    }

    public void notifyServerStateCallback(int state) {
        JSONObject object;
        try {
            object = new JSONObject();
            object.put("state", state);
        } catch (Exception e) {
            Log.e(TAG, "notifyServerStateCallback " + e.getMessage());
            return;
        }
        synchronized (mSignalHandlers) {
            for (Pair <Integer, SignalHandler> handler : mSignalHandlers) {
                handler.second.onSignal("stateOfdtvkit", object);
            }
        }
    }

    public void notifyMixVideoEventCallback(int event) {
        Log.d(TAG, "notifyMixVideoEventCallback received!!! event =" + event);
        if (mListener != null) {
            mListener.mixVideoEvent(event);
        }
    }

   public void doUnCrypt(String src, String dest) {
        native_UnCrypt(src, dest);
   }

   public void setMultiSurface(int index, Surface sh) {
        nativeSetMultiSurface(index, sh);
   }

   public void setDisplay(Surface sh) {
        nativeSetSurface(sh);
   }

   public void disConnectDtvkitClient() {
        nativeDisconnectDtvkit();
        native_detachSubtitleCtl();
   }


/*
    final class ServiceNotification extends IServiceNotification.Stub {
        @Override
        public void onRegistration(String fqName, String name, boolean preexisting) {
            Log.i(TAG, "rpcServer HIDL service started " + fqName + " " + name);
            connectToProxy();
        }
    }

    private void connectToProxy() {
        synchronized (mLock) {
            if (mProxy != null) {
                return;
            }

            try {
                mProxy = IDTVKitServer.getService();
                mProxy.linkToDeath(new DeathRecipient(), DTVKITSERVER_DEATH_COOKIE);
                mProxy.setCallback(mHALCallback, ConnectType.TYPE_EXTEND);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "connectToProxy: DTVKitServer HIDL service not found."
                        + " Did the service fail to start?", e);
            } catch (RemoteException e) {
                Log.e(TAG, "connectToProxy: DTVKitServer HIDL service not responding", e);
            }
        }

        Log.i(TAG, "connect to DTVKitServer HIDL service success");
    }

    private static class HALCallback extends IDTVKitServerCallback.Stub {
        DtvkitGlueClient DtvkitClient;
        HALCallback(DtvkitGlueClient dtvkitGlueClient) {
            DtvkitClient = dtvkitGlueClient;
    }

    public void notifyCallback(DTVKitHidlParcel parcel) {
        Log.i(TAG, "resource" + parcel.resource + ",json" + parcel.json);
        JSONObject object;
        try {
            object = new JSONObject(parcel.json);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return;
        }

        for (SignalHandler handler : DtvkitClient.mHandlers) {
            handler.onSignal(parcel.resource, object);
        }
        }
    }

    final class DeathRecipient implements HwBinder.DeathRecipient {
        DeathRecipient() {
        }

        @Override
        public void serviceDied(long cookie) {
            if (DTVKITSERVER_DEATH_COOKIE == cookie) {
                Log.e(TAG, "dtvkitserver HIDL service died cookie: " + cookie);
                synchronized (mLock) {
                    mProxy = null;
                }
            }
        }
    }
*/

    public interface AudioHandler {
       void onEvent(String signal, JSONObject data);
    }

    public interface SignalHandler {
        void onSignal(String signal, JSONObject data);
    }

    public interface OverlayTarget {
        void draw(int src_width, int src_height, int dst_x, int dst_y, int dst_width, int dst_height, int[] data);
    }

    public interface SubtitleListener {
        void drawEx(int parserType, int src_width, int src_height, int dst_x, int dst_y, int dst_width, int dst_height, int[] data);
        void pauseEx(int pause);
        void drawCC(boolean bShow, String json, int type);
        void mixVideoEvent(int event);
    }

    public interface PidFilterListener {
        void onPidFilterData(ByteBuffer data);
    }

    protected DtvkitGlueClient() {
        // Singleton
        mDirectBuffer = ByteBuffer.allocateDirect(DIRECT_BUFFER_SIZE);
        nativeConnectDtvkit(this, mDirectBuffer);
        /*
        int debuggable = SystemProperties.getInt("ro.debuggable", 0);
        if (debuggable == 1) {
            HandlerThread thread = new HandlerThread("GlueClientThread");
            thread.start();
            mMainHandler = new Handler(thread.getLooper());
        }
        */
    }

    public synchronized static DtvkitGlueClient getInstance() {
        if (mSingleton == null) {
            mSingleton = new DtvkitGlueClient();
        }
        return mSingleton;
    }

    /** @hide */
    public ArrayList<String> getSignalHandlerInfo() {
        ArrayList<String> info = new ArrayList<>();
        for (Pair<String, SignalHandler> pair : mSignalHandlerFrom) {
            info.add(pair.first);
        }
        return info;
    }

    public void registerSignalHandler(SignalHandler handler, int id) {
        Log.d(TAG, "registerSignalHandler " + handler);
        mSignalHandlers.removeIf(pair -> pair.second == handler);
        mSignalHandlers.add(new Pair<>(id, handler));
        // Debug
        String stackTraceString = Log.getStackTraceString(new Throwable());
        mSignalHandlerFrom.removeIf(pair -> pair.second == handler);
        mSignalHandlerFrom.add(new Pair<>(stackTraceString, handler));
    }

    public void registerSignalHandler(SignalHandler handler) {
        registerSignalHandler(handler, INDEX_FOR_MAIN);
    }

    public void unregisterSignalHandler(SignalHandler handler) {
        Log.d(TAG, "unregisterSignalHandler " + handler);
        mSignalHandlers.removeIf(pair -> pair.second == handler);
        mSignalHandlerFrom.removeIf(pair -> pair.second == handler);
    }

    public void setOverlayTarget(OverlayTarget target) {
        mTarget = target;
    }

    public void setSubtileListener(SubtitleListener Listener) {
        mListener = Listener;
    }

    public void removeSubtileListener(SubtitleListener Listener) {
        if (mListener == Listener)
            mListener = null;
    }

    public void removeOverlayTarget(OverlayTarget target) {
        if (mTarget == target)
            mTarget = null;
    }

    public void setPidFilterListener(PidFilterListener Listener) {
        mPidListener = Listener;
    }

    public JSONObject request(String resource, JSONArray arguments) throws Exception {
        final String reason = resource + " : " + arguments;
        long startTime = System.nanoTime();
        try {
            JSONObject object = new JSONObject(nativeRequest(resource, arguments.toString()));
            if (object.getBoolean("accepted")) {
                return object;
            } else {
                throw new Exception(object.getString("data"));
            }
        } catch (JSONException | RemoteException e) {
            throw new Exception(e.getMessage());
        } finally {
            long durationMs = (System.nanoTime() - startTime) / (1000 * 1000);
            if (durationMs > REQUEST_MESSAGE_TIMEOUT_LONG_MILLIS) {
                Log.e(TAG, "[critical]request (" + reason + ") took too long time (duration="
                    + durationMs + "ms)");
            } else if (durationMs > REQUEST_MESSAGE_TIMEOUT_SHORT_MILLIS) {
                Log.w(TAG, "[warning]request (" + reason + ") took a long time (duration="
                    + durationMs + "ms)");
            }
        }
    }

/*
    public String readBySysControl(int ftype, String name) {
        String value = null;
        if (mSysControlHandler != null) {
            value = mSysControlHandler.onReadSysFs(ftype, name);
        }
        return value;
    }

    public void writeBySysControl(int ftype, String name, String cmd) {
        if (mSysControlHandler != null) {
            mSysControlHandler.onWriteSysFs(ftype, name, cmd);
        }
    }

    public void setSystemControlHandler(SystemControlHandler l) {
          mSysControlHandler = l;
    }

    public interface SystemControlHandler {
        public String onReadSysFs(int ftype, String name);
        public void onWriteSysFs(int ftype, String name, String cmd);
    }
    */

    public void subtitleSeekReset() {
        native_nativeSubtitleSeekReset();
    }

    public void attachSubtitleCtl(int flag) {
        native_attachSubtitleCtl(flag);
    }

    public void destroySubtitleCtl() {
        native_destroySubtitleCtl();
    }

    public boolean isdbtSupport() {
        return nativeIsdbtSupport();
    }

    public void openUserData() {
        native_openUserData();
    }

    public void closeUserData() {
        native_closeUserData();
    }

    public void setRegionId(int regionId) {
        native_setRegionId(regionId);
    }

    public void enablePidListener(boolean enable) {
        native_enablePidListener(enable);
    }

    private static boolean getFeatureSupportTunerFramework() {
        boolean result = false;
        try {
            Class clz = Class.forName("android.os.SystemProperties");
            Method method = clz.getMethod("getBoolean", String.class, boolean.class);
            result = (boolean)method.invoke(clz, "vendor.tv.dtv.tuner.framework.enable", false);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "getBoolean Exception = " + e.getMessage());
        }
        return result;
    }
}
