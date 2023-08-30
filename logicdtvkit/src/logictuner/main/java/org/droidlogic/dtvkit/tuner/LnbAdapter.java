package droidlogic.dtvkit.tuner;

import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.Lnb;
import android.media.tv.tuner.LnbCallback;
import android.util.Log;

import java.lang.Long;
import java.util.concurrent.Executor;

public class LnbAdapter {
    private static final String TAG = "LnbAdapter";
    private static final boolean DEBUG = true;

    private static final int LNB_CALLBACK_DISEQC_RX_OVERFLOW = 0;
    private static final int LNB_CALLBACK_DISEQC_RX_TIMEOUT = 1;
    private static final int LNB_CALLBACK_DISEQC_RX_PARITY_ERROR = 2;
    private static final int LNB_CALLBACK_LNB_OVERLOAD = 3;
    private static final int LNB_CALLBACK_DISEQC_MESSAGE = 4;

    private Lnb mLnb;
    private long mLnbCallbackContext;

    private native void nativeLnbCallback(int eventType, byte[] diseqcMessage);

    public LnbAdapter(Lnb lnb, long callbackContext) {
        mLnb = lnb;
        mLnbCallbackContext = callbackContext;
        Log.d(TAG, "callbackContext : 0x" + Long.toHexString(callbackContext));
    }

    //Must be call set Lnb to LnbAdapter;
    public void setLnb(Lnb lnb) {
        mLnb = lnb;
        Log.d(TAG, "setLnb mLnb :" + mLnb);
    }

    private void addCallback(long callbackContext) {
        if (DEBUG) Log.d(TAG, "addCallback callbackContext  : 0x" + Long.toHexString(callbackContext) +
            " mLnbCallbackContext : 0x" + Long.toHexString(mLnbCallbackContext));
        if ((0 != callbackContext) && (mLnbCallbackContext != callbackContext)) {
            mLnbCallbackContext = callbackContext;
        }
    }

    private void removeCallback(long callbackContext) {
        if (DEBUG) Log.d(TAG, "removeCallback callbackContext  : 0x" + Long.toHexString(callbackContext) +
            " mLnbCallbackContext : 0x" + Long.toHexString(mLnbCallbackContext));
        if ((0 != callbackContext) && (mLnbCallbackContext == callbackContext)) {
            mLnbCallbackContext = 0;
        }
    }

    private int setVoltage(int voltage) {
        if (DEBUG) Log.d(TAG, "setVoltage voltage:" + voltage);
        int result = mLnb.setVoltage(voltage);
        if (DEBUG) Log.d(TAG, "setVoltage result:" + result);
        return result;
    }

    private int setTone(int tone) {
        if (DEBUG) Log.d(TAG, "setTone tone:" + tone);
        int result = mLnb.setTone(tone);
        if (DEBUG) Log.d(TAG, "setTone result:" + result);
        return result;
    }

    private int setSatellitePosition(int position) {
        if (DEBUG) Log.d(TAG, "setSatellitePosition position:" + position);
        int result = mLnb.setSatellitePosition(position);
        if (DEBUG) Log.d(TAG, "setSatellitePosition result:" + result);
        return result;
    }

    private int sendDiseqcMessage(byte[] message) {
        if (DEBUG) Log.d(TAG, "sendDiseqcMessage message:" + message);
        int result = mLnb.sendDiseqcMessage(message);
        if (DEBUG) Log.d(TAG, "sendDiseqcMessage result:" + result);
        return result;
    }

    private void close() {
        if (DEBUG) Log.d(TAG, "close mLnb:" + mLnb);
        mLnb.close();
        mLnb = null;
        mLnbCallbackContext = 0;
        if (DEBUG) Log.d(TAG, "close mLnb finish");
    }

    boolean isClose() {
        if (null == mLnb) {
            return true;
        }
        return false;
    }

    public static class LnbNativeCallback implements LnbCallback {
        private LnbAdapter mLnbAdapter = null;

        public LnbNativeCallback () {}

        public void setCallbackLnbAdapter(LnbAdapter lnbAdapter) {
            mLnbAdapter = lnbAdapter;
        }

        @Override
        public void onEvent(int lnbEventType) {
            Log.d(TAG, "onEvent lnbEventType:" + lnbEventType + "mLnbAdapter : " + mLnbAdapter);
            if (mLnbAdapter.isClose()) {
                Log.d(TAG, "Lnb has close");
                return;
            }
            switch (lnbEventType) {
                case Lnb.EVENT_TYPE_DISEQC_RX_OVERFLOW: {
                    mLnbAdapter.nativeLnbCallback(LNB_CALLBACK_DISEQC_RX_OVERFLOW, null);
                    break;
                }
                case Lnb.EVENT_TYPE_DISEQC_RX_TIMEOUT: {
                    mLnbAdapter.nativeLnbCallback(LNB_CALLBACK_DISEQC_RX_TIMEOUT, null);
                    break;
                }
                case Lnb.EVENT_TYPE_DISEQC_RX_PARITY_ERROR: {
                    mLnbAdapter.nativeLnbCallback(LNB_CALLBACK_DISEQC_RX_PARITY_ERROR, null);
                    break;
                }
                case Lnb.EVENT_TYPE_LNB_OVERLOAD: {
                    mLnbAdapter.nativeLnbCallback(LNB_CALLBACK_DISEQC_RX_TIMEOUT, null);
                    break;
                }
            }
        }

        @Override
        public void onDiseqcMessage(byte[] diseqcMessage) {
            Log.d(TAG, "onDiseqcMessage diseqcMessage: " + diseqcMessage + "mLnbAdapter : " + mLnbAdapter);
            if (mLnbAdapter.isClose()) {
                Log.d(TAG, "Lnb has close");
                return;
            }
            mLnbAdapter.nativeLnbCallback(LNB_CALLBACK_DISEQC_MESSAGE, diseqcMessage);
        }
    }
}
