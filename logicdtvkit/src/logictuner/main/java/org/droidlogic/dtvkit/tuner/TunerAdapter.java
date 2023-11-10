package droidlogic.dtvkit.tuner;

import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.DemuxCapabilities;
import android.media.tv.tuner.Descrambler;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.frontend.Atsc3PlpInfo;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.media.tv.tuner.frontend.FrontendSettings;
import android.media.tv.tuner.frontend.FrontendStatus;
import android.media.tv.tuner.frontend.OnTuneEventListener;
import android.media.tv.tuner.frontend.ScanCallback;
import android.media.tv.tuner.Lnb;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.lang.Long;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dtvkit tool class.
 */
public class TunerAdapter {
    private static final String TAG = "TunerAdapter";
    private static final boolean DEBUG = true;

    public static int TUNER_TYPE_LIVE_0               = 0;
    public static int TUNER_TYPE_LIVE_1               = 1;
    public static int TUNER_TYPE_DVR_RECORD           = 2;
    public static int TUNER_TYPE_DVR_TIMESHIFT_RECORD = 3;
    public static int TUNER_TYPE_DVR_PLAY             = 4;
    public static int TUNER_TYPE_SCAN                 = 5;
    public static int TUNER_TYPE_LIVE_2               = 6;
    public static int TUNER_TYPE_BACKGROUND           = 7;

    private Tuner mTuner;
    private CallbackExecutor mFrontendExecutor;
    private CallbackExecutor mDemuxExecutor;
    private NativeScanCallback mNativeScanCallback;
    private Surface mSurface;
    //private Handler mCallbackHandler;
    //private HandlerThread mCallbackThread;
    private long mTuneEventListenerContext = 0;
    private long mScanCallbackConext = 0;
    private int mTunerClientId = 0;
    private int mTunerType = TUNER_TYPE_LIVE_0;

    private static native void nativeInit();
    private native void nativeSetup(int tunerClientId);
    private native void nativeRelease(int tunerClientId);
    private native void nativeTunerEventCallback(int tunerClientId, int TuneEvent);
    private native void nativeTunerSetSurface(Surface surface);
    private native void nativeTunerTestCase();//For JNI Testcase
    public native void nativeScanCallback(int tunerClientId, int scanMessageType, Object[] scanMessage);

    static {
        try {
            System.loadLibrary("dtvkit_tuner_jni");
            nativeInit();
        } catch (UnsatisfiedLinkError e) {
            Log.d(TAG, "tuner JNI library not found!");
        }
    }

    public TunerAdapter(Tuner tuner, int tunerType) {
        mTuner = tuner;
        mTunerClientId = TunerHelp.getClientId(mTuner);
        mTunerType = tunerType;
        nativeSetup(mTunerClientId);
        initCallbackThread();
        Log.d(TAG, "TunerAdapter mTunerClientId:" + mTunerClientId + " mTuner : " + mTuner + " mTunerType : " + mTunerType);
    }

    public void release() {
        Log.d(TAG, "release mTunerClientId :" + mTunerClientId);
        nativeRelease(mTunerClientId);
        releaseCallbackThread();
        if (null != mTuner) {
            mTuner.close();
            mTuner = null;
        }
        mTunerType = TUNER_TYPE_LIVE_0;
        mSurface = null;
        Log.d(TAG, "release finish");
    }

    public void setSurfaceToNative(Surface surface) {
        Log.d(TAG, "setSurfaceToNative mTunerClientId : " + mTunerClientId + "surface:" + surface);
        mSurface = surface;
        nativeTunerSetSurface(surface);
    }

    public void startTestCase() {
        Log.d(TAG, "startTestCase");
        nativeTunerTestCase();
    }

    public Tuner getTuenr() {
        Log.d(TAG, "getTuenr mTunerClientId:" + mTunerClientId + " mTuner : " + mTuner);
        return mTuner;
    }

    private void initCallbackThread() {
        mFrontendExecutor = new CallbackExecutor("Frontend Callback");
        mDemuxExecutor = new CallbackExecutor("Demux Callback");
    }

    private void releaseCallbackThread() {
        if (null != mFrontendExecutor) {
            mFrontendExecutor.release();
            mFrontendExecutor = null;
        }
        if (null != mDemuxExecutor) {
            mDemuxExecutor.release();
            mDemuxExecutor = null;
        }
    }

    private DemuxCapabilities getDemuxCapabilities() {
        DemuxCapabilities demuxCapabilities = mTuner.getDemuxCapabilities();
        if (DEBUG) Log.d(TAG, "DemuxCapabilities : " + demuxCapabilities);
        return demuxCapabilities;
   }

    private List<Integer> getFrontendIds() {
        Log.d(TAG, "getFrontendIds mTunerClientId :" + mTunerClientId);
        return TunerHelp.getFrontendIds(mTuner);
    }

    private FrontendInfo getFrontendInfo() {
        FrontendInfo frontendInfo = mTuner.getFrontendInfo();
        if (DEBUG) Log.d(TAG, "getFrontendInfo FrontendInfo : " + frontendInfo + " mTunerClientId : " + mTunerClientId);
        return frontendInfo;
    }

    private FrontendInfo getFrontendInfoById(int id) {
        FrontendInfo frontendInfo = TunerHelp.getFrontendInfoById(mTuner, id);
        if (DEBUG) Log.d(TAG, "getFrontendInfoById FrontendInfo : " + frontendInfo + " id : " + id
            + " mTunerClientId : " + mTunerClientId);
        return frontendInfo;
    }

    private int tune(FrontendSettings settings) {
        int result = Tuner.RESULT_UNKNOWN_ERROR;
        Log.d(TAG, "tune settings :" + settings + " mTunerClientId : " + mTunerClientId);
        if ((null != settings) && (settings instanceof FrontendSettings)) {
            result = mTuner.tune(settings);
        } else {
            Log.d(TAG, "tune input parameter error : " + settings);
        }
        if (DEBUG) Log.d(TAG," result : " + result);
        return result;
    }

    private int cancelTuning() {
        int result = mTuner.cancelTuning();
        if (DEBUG) Log.d(TAG, "cancelTuning result : " + result + " mTunerClientId : " + mTunerClientId);
        return result;
    }

    private int scan(FrontendSettings settings, int scanType, long scanCallbackContext) {
        if (DEBUG) Log.d(TAG, "scan : " + settings + " scanType : " + scanType + " scanCallbackContext : 0x" +
            Long.toHexString(scanCallbackContext) + " mScanCallbackConext : " + Long.toHexString(mScanCallbackConext) + " mTunerClientId : " + mTunerClientId);
        if ((0 == scanCallbackContext) || ((0 != mScanCallbackConext) && (scanCallbackContext != mScanCallbackConext))) {
            return Tuner.RESULT_INVALID_ARGUMENT;
        }
        mScanCallbackConext = scanCallbackContext;
        if (null == mNativeScanCallback) {
            mNativeScanCallback = new NativeScanCallback(this, mTunerClientId);
        }
        int result = mTuner.scan(settings, scanType, mFrontendExecutor, mNativeScanCallback);
        if (DEBUG) Log.d(TAG, "scan result : " + result);
        return result;
    }

    private int cancelScanning() {
        if (DEBUG) Log.d(TAG, "cancelScanning mTunerClientId : " + mTunerClientId);
        mScanCallbackConext = 0;
        mNativeScanCallback = null;
        int result = mTuner.cancelScanning();
        if (DEBUG) Log.d(TAG, "cancelScanning result : " + result);
        return result;
    }

    private void closeFrontend() {
        if (DEBUG) Log.d(TAG, "closeFrontend mTuner : " + mTuner + " mTunerClientId : " + mTunerClientId);
        mTuner.closeFrontend();
        return;
    }

    private void close() {
        if (DEBUG) Log.d(TAG, "close mTuner : " + mTuner + " mTunerClientId : " + mTunerClientId);
        mTuner.close();
        mTuner = null;
        mTunerClientId = Tuner.INVALID_FRONTEND_ID;
        return;
    }

    private FrontendStatus getFrontendStatus(int[] statusTypes) {
        FrontendStatus frontendStatus = mTuner.getFrontendStatus(statusTypes);
        if (DEBUG) Log.d(TAG, "getFrontendStatus statusTypes : " + statusTypes + " frontendStatus : " + frontendStatus + " mTunerClientId : " + mTunerClientId);
        return frontendStatus;
    }

    private void setOnTuneEventListener(long callbackContext) {
        if (DEBUG) Log.d(TAG, "setOnTuneEventListener callbackContext : 0x" + Long.toHexString(callbackContext) + " mTunerClientId : " + mTunerClientId);
        if (0 == callbackContext) {
            Log.e(TAG, "setOnTuneEventListener error not set callback context ");
            return;
        }
        mTuneEventListenerContext = callbackContext;
        mTuner.setOnTuneEventListener(mFrontendExecutor, (int tuneEvent)->{
            if (DEBUG) Log.d(TAG, "nativeTunerEventCallback tuneEvent : " + tuneEvent + ", mTuneEventListenerContext : 0x" + Long.toHexString(mTuneEventListenerContext));
            nativeTunerEventCallback(mTunerClientId, tuneEvent);
        });
    }

    private void clearOnTuneEventListener() {
        if (DEBUG) Log.d(TAG, "clearOnTuneEventListener callbackContext : 0x" + Long.toHexString(mTuneEventListenerContext) + " mTunerClientId : " + mTunerClientId);
        mTuner.clearOnTuneEventListener();
        mTuneEventListenerContext = 0;
    }

    private FilterAdapter openFilter(int mainType, int subType, long bufferSize, long callbackContext) {
        if (DEBUG) Log.d(TAG, "openFilter mainType : " + mainType + " subType : " + subType + " bufferSize : " + bufferSize
            + " callbackContext : 0x" + Long.toHexString(callbackContext) + " mTunerClientId : " + mTunerClientId);
        Filter filter = null;
        FilterAdapter.FilterAdapterCallback callback = null;

        if (0 == callbackContext) {
            filter = mTuner.openFilter(mainType, subType, bufferSize, null, null);
        } else {
            callback = new FilterAdapter.FilterAdapterCallback();
            filter = mTuner.openFilter(mainType, subType, bufferSize, mDemuxExecutor, callback);
        }

        if (null == filter) {
            Log.d(TAG, "openFilter error not request filter");
            return null;
        }
        FilterAdapter filterAdapter = new FilterAdapter(filter, mDemuxExecutor, callbackContext, mTunerClientId);
        if (null != callback) {
            callback.setCallbackFilterAdapter(filterAdapter);
        }
        Log.d(TAG, "request filter Id:" + filter.getId() + "|filterAdapter : " + filterAdapter);
        return filterAdapter;
    }

    private int getAvSyncHwId(FilterAdapter filter) {
        int syncHwId = 0;
        if ((null != filter) && (filter instanceof FilterAdapter)) {
            syncHwId = mTuner.getAvSyncHwId(filter.getFilter());
        }
        if (DEBUG) Log.d(TAG, "getAvSyncHwId filter : " + filter + " syncHwId : " +syncHwId + " mTunerClientId : " + mTunerClientId);
        return syncHwId;
    }

    private long getAvSyncTime(int avSyncHwId) {
        long syncTime = mTuner.getAvSyncTime(avSyncHwId);
        if (DEBUG) Log.d(TAG, "getAvSyncTime avSyncHwId : " + avSyncHwId + " syncTime = " + syncTime + " mTunerClientId : " + mTunerClientId);
        return syncTime;
    }

    private LnbAdapter openLnb(long callbackContext) {
        if (DEBUG) Log.d(TAG, "openLnb callbackContext : 0x" + Long.toHexString(callbackContext) + " mTunerClientId : " + mTunerClientId);
        if (0 == callbackContext) {
            if (DEBUG) Log.e(TAG, "openLnb callbackContext : 0x" + Long.toHexString(callbackContext));
            return null;
        }
        LnbAdapter.LnbNativeCallback callback = new LnbAdapter.LnbNativeCallback();
        Lnb lnb = mTuner.openLnb(mFrontendExecutor, callback);
        if (null == lnb) {
            Log.e(TAG, "openLnb error");
            return null;
        }
        LnbAdapter lnbAdapter = new LnbAdapter(lnb, callbackContext);
        callback.setCallbackLnbAdapter(lnbAdapter, mTunerClientId);
        if (DEBUG) Log.d(TAG, "openLnb lnbAdapter : " + lnbAdapter);
        return lnbAdapter;
    }

    private LnbAdapter openLnbByName(String name, long callbackContext) {
        if (DEBUG) Log.d(TAG, "openLnbByName callbackContext : 0x" + Long.toHexString(callbackContext) + " name : " + name
            + " mTunerClientId : " + mTunerClientId);

        if (0 == callbackContext) {
            if (DEBUG) Log.e(TAG, "openLnb callbackContext : 0x" + Long.toHexString(callbackContext));
            return null;
        }
        LnbAdapter.LnbNativeCallback callback = new LnbAdapter.LnbNativeCallback();
        Lnb lnb = mTuner.openLnbByName(name, mFrontendExecutor, callback);
        if (null == lnb) {
            Log.e(TAG, "openLnb error");
            return null;
        }
        LnbAdapter lnbAdapter = new LnbAdapter(lnb, callbackContext);
        callback.setCallbackLnbAdapter(lnbAdapter, mTunerClientId);
        if (DEBUG) Log.d(TAG, "openLnb lnbAdapter : " + lnbAdapter);
        return lnbAdapter;
    }

    private int connectCiCam(int ciCamId) {
        int result = mTuner.connectCiCam(ciCamId);
        if (DEBUG) Log.d(TAG, "connectCiCam ciCamId : " + ciCamId + " result : " + result
            + " mTunerClientId : " + mTunerClientId);
        return result;
    }

    private int disconnectCiCam() {
        int result = mTuner.disconnectCiCam();
        if (DEBUG) Log.d(TAG, "disconnectCiCam result : " + result + " mTunerClientId : " + mTunerClientId);
        return result;
    }

    private int connectFrontendToCiCam(int ciCamId) {
        int result = mTuner.connectFrontendToCiCam(ciCamId);
        if (DEBUG) Log.d(TAG, "connectFrontendToCiCam ciCamId : " + ciCamId + " result : " + result
            + " mTunerClientId : " + mTunerClientId);
        return result;
    }

    private int disconnectFrontendToCiCam(int ciCamId) {
        int result = mTuner.disconnectFrontendToCiCam(ciCamId);
        if (DEBUG) Log.d(TAG, "disconnectFrontendToCiCam ciCamId : " + ciCamId + " result : " + result
            + " mTunerClientId : " + mTunerClientId);
        return result;
    }

    private DescramblerAdapter openDescrambler() {
        Descrambler descrambler = mTuner.openDescrambler();
        DescramblerAdapter descramblerAdapter = new DescramblerAdapter(descrambler);
        if (DEBUG) Log.d(TAG, "openDescrambler : " + descramblerAdapter + " mTunerClientId : " + mTunerClientId);
        return descramblerAdapter;
    }

    private static class TunerHelp{
        static Method sGetFrontendIds;
        static Method sGetFrontendInfoById;
        static Field sGetClientId;

        static {
            try {
                Class<?> Tuner = Class.forName("android.media.tv.tuner.Tuner");
                sGetFrontendIds = Tuner.getDeclaredMethod("getFrontendIds");
                sGetFrontendInfoById = Tuner.getDeclaredMethod("getFrontendInfoById", int.class);
                sGetClientId = Tuner.getDeclaredField("mClientId");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static List<Integer> getFrontendIds(android.media.tv.tuner.Tuner tuner) {
            try {
                return (List<Integer>) sGetFrontendIds.invoke(tuner);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private static FrontendInfo getFrontendInfoById(android.media.tv.tuner.Tuner tuner, int id) {
            try {
                return (FrontendInfo) sGetFrontendInfoById.invoke(tuner, id);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private static int getClientId(android.media.tv.tuner.Tuner tuner) {
            try {
                sGetClientId.setAccessible(true);
                return (int) sGetClientId.get(tuner);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    public static class CallbackExecutor implements Executor {
        private Handler mCallbackHandler;
        private HandlerThread mCallbackThread;

        public CallbackExecutor(String callbackName) {
            mCallbackThread = new HandlerThread(callbackName);
            mCallbackThread.start();
            mCallbackHandler = new Handler(mCallbackThread.getLooper());
        }

        @Override
        public void execute(Runnable r) {
            if (null != mCallbackHandler) {
                mCallbackHandler.post(r);
            }
        }

        public void release() {
            mCallbackHandler.removeCallbacksAndMessages(null);
            mCallbackThread.getLooper().quitSafely();
            mCallbackThread = null;
            mCallbackHandler = null;
        }
    }
}
