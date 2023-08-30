package droidlogic.dtvkit.tuner;

import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.frontend.ScanCallback;
import android.media.tv.tuner.frontend.Atsc3PlpInfo;

import android.util.Log;

public class NativeScanCallback implements ScanCallback {
    private static final String TAG = "NativeScanCallback";
    private static final boolean DEBUG = true;

    private static final int SCAN_MESSAGE_LOCKED = 0;
    private static final int SCAN_MESSAGE_UNLOCK = 1;
    private static final int SCAN_MESSAGE_END = 2;
    private static final int SCAN_MESSAGE_PROGRESS_PERCENT = 3;
    private static final int SCAN_MESSAGE_FREQUENCY = 4;
    private static final int SCAN_MESSAGE_SYMBOL_RATE = 5;
    private static final int SCAN_MESSAGE_PLP_IDS = 6;
    private static final int SCAN_MESSAGE_GROUP_IDS = 7;
    private static final int SCAN_MESSAGE_INPUT_STREAM_IDS = 8;
    private static final int SCAN_MESSAGE_DVBS_STANDARD = 9;
    private static final int SCAN_MESSAGE_DVBT_STANDARD = 10;
    private static final int SCAN_MESSAGE_ANALOG_TYPE = 11;
    private static final int SCAN_MESSAGE_HIERARCHY = 12;
    private static final int SCAN_MESSAGE_SIGNAL_TYPE = 13;
    private static final int SCAN_MESSAGE_DVBT_CELL_IDS = 14;

    TunerAdapter mTunerAdapter;

    public NativeScanCallback(TunerAdapter tunerAdapter) {
        mTunerAdapter = tunerAdapter;
    }

    @Override
    public void onLocked() {
        Log.d(TAG, "onLocked");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_LOCKED, null);
    }

    @Override
    public void onUnlocked() {
        Log.d(TAG, "onUnlocked");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_UNLOCK, null);
    }

    @Override
    public void onScanStopped() {
       Log.d(TAG, "onScanStopped");
       mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_END, null);
    }

    @Override
    public void onProgress(int percent) {
        Log.d(TAG, "onProgress percent:" + percent);
        int[] value = {percent};
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_PROGRESS_PERCENT, (Object[])getIntegerArray(value));
    }

    @Override
    public void onFrequenciesReported(int[] frequency) {
        Log.d(TAG, "onFrequenciesReported");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_FREQUENCY, (Object[])getIntegerArray(frequency));
    }

    @Override
    public void onSymbolRatesReported(int[] rate) {
        Log.d(TAG, "onSymbolRatesReported");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_SYMBOL_RATE, (Object[])getIntegerArray(rate));
    }

    @Override
    public void onPlpIdsReported(int[] plpIds) {
        Log.d(TAG, "onPlpIdsReported");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_PLP_IDS, (Object[])getIntegerArray(plpIds));
    }

    @Override
    public void onGroupIdsReported(int[] groupIds) {
        Log.d(TAG, "onGroupIdsReported");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_GROUP_IDS, (Object[])getIntegerArray(groupIds));
    }

    @Override
    public void onInputStreamIdsReported(int[] inputStreamIds) {
        Log.d(TAG, "onInputStreamIdsReported");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_INPUT_STREAM_IDS, (Object[])getIntegerArray(inputStreamIds));
    }

    @Override
    public void onDvbsStandardReported(int dvbsStandard) {
        Log.d(TAG, "onDvbsStandardReported");
        int[] value = {dvbsStandard};
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_DVBS_STANDARD, (Object[])getIntegerArray(value));
    }

    @Override
    public void onDvbtStandardReported(int dvbtStandard) {
        Log.d(TAG, "onDvbtStandardReported");
        int[] value = {dvbtStandard};
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_DVBT_STANDARD, (Object[])getIntegerArray(value));
    }

    @Override
    public void onAnalogSifStandardReported(int sif) {
        Log.d(TAG, "onAnalogSifStandardReported");
        int[] value = {sif};
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_ANALOG_TYPE, (Object[])getIntegerArray(value));
    }

    @Override
    public void onAtsc3PlpInfosReported(Atsc3PlpInfo[] atsc3PlpInfos) {
        Log.d(TAG, "onAtsc3PlpInfosReported");
    }

    @Override
    public void onHierarchyReported(int hierarchy) {
        Log.d(TAG, "onHierarchyReported");
        int[] value = {hierarchy};
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_HIERARCHY, (Object[])getIntegerArray(value));
    }

    @Override
    public void onSignalTypeReported(int signalType) {
        Log.d(TAG, "onSignalTypeReported");
        int[] value = {signalType};
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_SIGNAL_TYPE, (Object[])getIntegerArray(value));
    }

    public void onDvbtCellIdsReported(int[] dvbtCellIds) {
        Log.d(TAG, "onDvbtCellIdsReported");
        mTunerAdapter.nativeScanCallback(SCAN_MESSAGE_SIGNAL_TYPE, (Object[])getIntegerArray(dvbtCellIds));
    }

    private Integer[] getIntegerArray(int[] value) {
        if (0 == value.length) {
            return null;
        }
        Log.d(TAG, "getIntegerArray value length : " + value.length);
        Integer[] scanMessage = new Integer[value.length];
        for (int i = 0; i < value.length; i++) {
            scanMessage[i] = value[i];
            if (DEBUG) Log.d(TAG, "value index : " + i + " data : " + value[i]);
        }
        return scanMessage;
    }
}