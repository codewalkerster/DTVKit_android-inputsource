package org.droidlogic.dtvkit;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The type Dtvkit glue client.
 */
public class DtvkitGlueClient {
    private static final String TAG = "DtvkitGlueClient";
    public static final int INDEX_FOR_MAIN = 0;
    public static final int INDEX_FOR_PIP = 1;
    @SuppressLint("StaticFieldLeak")
    private static DtvkitGlueClient mSingleton;
    private final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private final CopyOnWriteArrayList<SignalHandler> mMainHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SignalHandler> mPipHandlers = new CopyOnWriteArrayList<>();
    private final IATFGlueSignalHandler.Stub mIGlueListener = new IATFGlueSignalHandler.Stub() {
        public void onRespond(int id, String signal, String data) throws RemoteException {
            if (DEBUG) {
                Log.i(TAG, "onRespond id=" + id + ", " + signal + " " + data);
            }
            JSONObject object;
            try {
                if (data.charAt(0) == '[') {
                    JSONArray array = new JSONArray(data);
                    object = new JSONObject();
                    object.put("result", array);
                } else {
                    object = new JSONObject(data);
                }
            } catch (Exception e) {
                return;
            }
            CopyOnWriteArrayList<SignalHandler> signalHandlers = (id == 0) ? mMainHandlers : mPipHandlers;
            for (SignalHandler handler : signalHandlers) {
                handler.onSignal(signal, object);
            }
        }
    };
    private Context mContext;
    private IATFGlueWrapper mService;
    private PidFilterListener mPidListener;
    private final IATFGluePidFilterListener.Stub mIFilterListener = new IATFGluePidFilterListener.Stub() {
        public void onPidFilterResponse(byte[] array) throws RemoteException {
            if (mPidListener != null) {
                ByteBuffer buffer = ByteBuffer.wrap(array);
                mPidListener.onPidFilterData(buffer);
            }
        }
    };
    private final ServiceConnection serConn = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "disconnect " + componentName);
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mService = IATFGlueWrapper.Stub.asInterface(service);
            Log.d(TAG, "connect " + componentName);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.w(TAG, "died " + name);
            mService = null;
            bindService();
        }
    };
    private SubtitleListener mSubListener;
    private final IATFGlueSubtitleListener.Stub mISubtitleListener = new IATFGlueSubtitleListener.Stub() {
        public void drawEx(int parserType, int src_width, int src_height, int dst_x, int dst_y,
                           int dst_width, int dst_height, ParceledListSlice<IntegerBlock> data) throws RemoteException {
            if (mSubListener != null) {
                int[] c = new int[src_width * src_height];
                int length = 0;
                for (IntegerBlock block : data.getList()) {
                    if (block.c != null) {
                        System.arraycopy(block.c, 0, c, length, block.c.length);
                        length += block.c.length;
                    }
                }
                mSubListener.drawEx(parserType, src_width, src_height, dst_x, dst_y, dst_width, dst_height, c);
            }
        }

        public void pauseEx(int pause) throws RemoteException {
            if (mSubListener != null) {
                mSubListener.pauseEx(pause);
            }
        }

        public void drawCC(boolean bShow, String json, int type) throws RemoteException {
            if (mSubListener != null) {
                mSubListener.drawCC(bShow, json, type);
            }
        }

        public void mixVideoEvent(int event) throws RemoteException {
            if (mSubListener != null) {
                mSubListener.mixVideoEvent(event);
            }
        }
    };
    private OverlayTarget mTarget;
    private final IATFGlueOverlayTarget.Stub mIOverlayListener = new IATFGlueOverlayTarget.Stub() {
        public void draw(int src_width, int src_height, int dst_x, int dst_y,
                         int dst_width, int dst_height, ParceledListSlice<IntegerBlock> data) throws RemoteException {
            if (mTarget != null) {
                int[] c = new int[src_width * src_height];
                int length = 0;
                for (IntegerBlock block : data.getList()) {
                    if (block.c != null) {
                        System.arraycopy(block.c, 0, c, length, block.c.length);
                        length += block.c.length;
                    }
                }
                mTarget.draw(src_width, src_height, dst_x, dst_y, dst_width, dst_height, c);
            }
        }
    };
    /**
     * Instantiates a new Dtvkit glue client.
     */
    public DtvkitGlueClient() {
        Application app = getApplication();
        if (app != null && app.getApplicationContext() != null) {
            mContext = app.getApplicationContext();
        }
        if (mContext != null) {
            bindService();
        } else {
            Log.wtf(TAG, "GlueClient Context is null");
        }
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static synchronized DtvkitGlueClient getInstance() {
        if (mSingleton == null) {
            mSingleton = new DtvkitGlueClient();
        }
        return mSingleton;
    }

    private Application getApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, "get Application failed");
        }
        return null;
    }

    /**
     * For feature upgrade use
     */
    public void setContext(Context context) {
        mContext = context;
    }

    /**
     * Register signal handler.
     *
     * @param handler the handler
     */
    public void registerSignalHandler(SignalHandler handler) {
        registerSignalHandler(handler, 0);
    }

    /**
     * Register signal handler.
     *
     * @param handler the handler
     * @param id      the id
     */
    public void registerSignalHandler(SignalHandler handler, int id) {
        Log.d(TAG, "registerSignalHandler " + handler);
        CopyOnWriteArrayList<SignalHandler> signalHandlers;
        if (id == 0) {
            signalHandlers = mMainHandlers;
        } else {
            signalHandlers = mPipHandlers;
        }
        if (!checkAndWaitService()) {
            return;
        }
        try {
            if (signalHandlers.isEmpty()) {
                mService.registerSignalHandler(id, mIGlueListener);
            }
            signalHandlers.remove(handler);
            signalHandlers.add(handler);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Unregister signal handler.
     *
     * @param handler the handler
     */
    public void unregisterSignalHandler(SignalHandler handler) {
        Log.d(TAG, "unregisterSignalHandler " + handler);
        int id = -1;
        CopyOnWriteArrayList<SignalHandler> signalHandlers = null;
        if (mMainHandlers.contains(handler)) {
            id = 0;
            signalHandlers = mMainHandlers;
        } else if (mPipHandlers.contains(handler)) {
            id = 1;
            signalHandlers = mPipHandlers;
        }
        if (!checkAndWaitService()) {
            return;
        }
        try {
            if (signalHandlers != null) {
                signalHandlers.remove(handler);
                if (signalHandlers.isEmpty()) {
                    mService.unregisterSignalHandler(id, mIGlueListener);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Sets pid filter listener.
     *
     * @param listener the listener
     */
    public void setPidFilterListener(PidFilterListener listener) {
        if (!checkAndWaitService()) {
            return;
        }
        mPidListener = listener;
        try {
            mService.setPidFilterListener(mIFilterListener);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Enable pid listener.
     *
     * @param enable the enable
     */
    public void enablePidListener(boolean enable) {
        if (!checkAndWaitService()) {
            return;
        }
        try {
            mService.enablePidListener(enable);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Sets overlay target, for MHeg5 Current
     *
     * @param target the target
     */
    public void setOverlayTarget(OverlayTarget target) {
        if (!checkAndWaitService()) {
            return;
        }
        mTarget = target;
        try {
            mService.setOverlayTarget(mIOverlayListener);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Remove overlay target.
     *
     * @param target the target
     */
    public void removeOverlayTarget(OverlayTarget target) {
        if (!checkAndWaitService()) {
            return;
        }
        if (mTarget == target) {
            mTarget = null;
            try {
                mService.removeOverlayTarget(mIOverlayListener);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * Sets subtitle listener.
     *
     * @param listener the listener
     */
    public void setSubtileListener(SubtitleListener listener) {
        if (!checkAndWaitService()) {
            return;
        }
        mSubListener = listener;
        try {
            mService.setSubtitleListener(mISubtitleListener);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Remove subtitle listener.
     *
     * @param listener the listener
     */
    public void removeSubtileListener(SubtitleListener listener) {
        if (!checkAndWaitService()) {
            return;
        }
        if (mSubListener == listener && listener != null) {
            mSubListener = null;
            try {
                mService.removeSubtitleListener(mISubtitleListener);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * Attach subtitle ctl.
     *
     * @param flag the flag
     */
    public void attachSubtitleCtl(int flag) {
        if (!checkAndWaitService()) {
            return;
        }
        try {
            mService.attachSubtitleCtl(flag);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Destroy subtitle ctl.
     */
    public void destroySubtitleCtl() {
        if (!checkAndWaitService()) {
            return;
        }
        try {
            mService.destroySubtitleCtl();
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Request json object.
     *
     * @param resource  the resource
     * @param arguments the arguments
     * @return the json object
     * @throws Exception the exception
     */
    public JSONObject request(String resource, JSONArray arguments) throws Exception {
        if (!checkAndWaitService()) {
            throw new JSONException("Service not available");
        }
        String param = "";
        if (arguments != null) {
            param = arguments.toString();
        }
        String reply = mService.request(resource, param);
        if (TextUtils.isEmpty(reply)) {
            throw new Exception("failed");
        } else {
            JSONObject result = new JSONObject(reply);
            if (result.getBoolean("accepted")) {
                return result;
            } else {
                throw new Exception(result.getString("data"));
            }
        }
    }

    private void bindService() {
        Log.d(TAG, "bindService()");
        String target_package = "com.droidlogic.dtvkit.inputsource";
        String target_component = target_package + ".service.GlueWrapperService";
        Intent intent = new Intent();
        intent.setClassName(target_package, target_component);
        PackageManager packageManager = mContext.getPackageManager();
        ResolveInfo info = packageManager.resolveService(intent, 0);
        if (info == null) {
            Log.e(TAG, "Can't find " + target_component);
            return;
        }
        mContext.bindService(intent, Context.BIND_AUTO_CREATE, Runnable::run, serConn);
        checkAndWaitService();
    }

    private boolean checkAndWaitService() {
        int count = 30;
        synchronized (this) {
            do {
                if (mService != null) {
                    return true;
                } else {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException ignored) {
                    }
                }
                count--;
            } while (count > 0);
        }
        Log.w(TAG, "GlueService not available");
        return false;
    }

    /**
     * The interface Signal handler.
     */
    public interface SignalHandler {
        /**
         * On signal.
         *
         * @param signal the signal
         * @param data   the data
         */
        void onSignal(String signal, JSONObject data);
    }

    /**
     * The interface Pid filter listener.
     */
    public interface PidFilterListener {
        /**
         * On pid filter data.
         *
         * @param data the data
         */
        void onPidFilterData(ByteBuffer data);
    }

    /**
     * The interface Overlay target.
     */
    public interface OverlayTarget {
        /**
         * Draw.
         *
         * @param src_width  the src width
         * @param src_height the src height
         * @param dst_x      the dst x
         * @param dst_y      the dst y
         * @param dst_width  the dst width
         * @param dst_height the dst height
         * @param data       the data
         */
        void draw(int src_width, int src_height, int dst_x, int dst_y, int dst_width, int dst_height, int[] data);
    }

    /**
     * The interface Subtitle listener.
     */
    public interface SubtitleListener {
        /**
         * Draw ex.
         *
         * @param parserType the parser type
         * @param src_width  the src width
         * @param src_height the src height
         * @param dst_x      the dst x
         * @param dst_y      the dst y
         * @param dst_width  the dst width
         * @param dst_height the dst height
         * @param data       the data
         */
        void drawEx(int parserType, int src_width, int src_height, int dst_x, int dst_y, int dst_width, int dst_height, int[] data);

        /**
         * Pause ex.
         *
         * @param pause the pause
         */
        void pauseEx(int pause);

        /**
         * Draw cc.
         *
         * @param bShow the b show
         * @param json  the json
         * @param type  the type
         */
        void drawCC(boolean bShow, String json, int type);

        /**
         * Mix video event.
         *
         * @param event the event
         */
        void mixVideoEvent(int event);
    }
}
