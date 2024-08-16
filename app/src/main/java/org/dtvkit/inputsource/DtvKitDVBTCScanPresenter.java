package com.droidlogic.dtvkit.inputsource;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils;
import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.settings.ConstantManager;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.app.DataProviderManager;
import com.droidlogic.settings.PropSettingManager;

import org.droidlogic.dtvkit.DtvkitGlueClient;

import org.dtvkit.inputsource.fvp.ClmManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class DtvKitDVBTCScanPresenter {
    private static final String TAG = "DtvKitDVBTCScanPresenter";

    public static final String COMMAND_DVBT_AUTO_SCAN = "Dvbt.startSearch";
    public static final String COMMAND_DVBT_MANUAL_SCAN_BY_FREQ = "Dvbt.startManualSearchByFreq";
    public static final String COMMAND_DVBT_MANUAL_SCAN_BY_ID = "Dvbt.startManualSearchById";
    public static final String COMMAND_DVBT_FINISH_SCAN = "Dvbt.finishSearch";
    public static final String COMMAND_DVBC_AUTO_SCAN = "Dvbc.startSearchEx";
    public static final String COMMAND_DVBC_MANUAL_SCAN_BY_FREQ = "Dvbc.startManualSearchByFreq";
    public static final String COMMAND_DVBC_MANUAL_SCAN_BY_ID = "Dvbc.startManualSearchById";
    public static final String COMMAND_DVBC_FINISH_SCAN = "Dvbc.finishSearch";
    public static final String COMMAND_DVB_GET_SERVICE_NUMBER = "Dvb.getNumberOfServices";
    public static final String COMMAND_DVB_GET_SERVICE_NUMBER_ON_SEARCH = "Dvb.getCategoryNumberOfServices";
    public static final String COMMAND_DVB_GET_SERVICE_LIST = "Dvb.getListOfServices";

    public static final int DVB_T = 0;
    public static final int DVB_C = 1;

    private final static int MSG_START_SEARCH = 1;
    private final static int MSG_STOP_SEARCH = 2; //doesn't tv provider, only send finish command to dtvkit
    private final static int MSG_FINISH_SEARCH = 3;//whole scan finish process
    private final static int MSG_ON_SIGNAL = 4;
    private final static int MSG_FINISH = 5;
    private final static int MSG_RELEASE= 6;
    private final static int MSG_START_BY_AUTOMATIC_MODE = 7;

    private final static int SCAN_MONITOR_STATUS_CHANGE = 0;
    private final static int SCAN_MONITOR_STATUS_UPDATE = 1;
    private final static int SCAN_MONITOR_STATUS_INVALID = 0xFF;

    private HandlerThread mHandlerThread = null;
    private Handler mScanWorkHandler = null;
    private ParameterManager mParameterManager = null;
    private DataManager mDataManager = null;
    private Context mContext = null;
    private UpdateScanView mUpdateScanView = null;
    private DtvkitGlueClient mDtvkitGlueClient = null;
    private JSONArray mServiceList = null;
    private String mInputId = null;
    private int mServiceNumber = 0;
    private int mDVBType = 0;
    private int mScanMode = DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO;
    private int  mManualFreqScan = DataManager.VALUE_FREQUENCY_MODE;
    private boolean mStartSyncChannelProvider = false;
    private boolean mStartScanProcess = false;
    private Activity mActivity = null;

    private final DtvkitGlueClient.SignalHandler mDtvKitSignalHandler = new DtvkitGlueClient.SignalHandler() {
        @Override
        public void onSignal(String signal, JSONObject data) {
            sendScanSignal(signal, data);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            String status = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            Log.d(TAG, "onReceive sync channel has finish intent = " + intent.toString() + "|status = " + status);
            if (status.equals(EpgSyncJobService.SYNC_FINISHED)) {
                if (null != mUpdateScanView) {
                    mUpdateScanView.updateScanStatus("Finished");
                    if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mScanMode) {
                        mUpdateScanView.updateScanSignalStrength(0);
                        mUpdateScanView.updateScanSignalQuality(0);
                        mUpdateScanView.finishScanView();
                    }
                }
                stopProviderSyncMonitor();
            } else if (status.equals(EpgSyncJobService.SYNC_ERROR)) {
                if (null != mUpdateScanView) {
                    mUpdateScanView.updateScanStatus("Error");
                    mUpdateScanView.finishScanView();
                }
                stopProviderSyncMonitor();
            }
        }
    };

    public DtvKitDVBTCScanPresenter(Activity activity, int dvbType, String inputId, UpdateScanView updateScanView){
        mActivity = activity;
        mContext = (Context)activity;
        mDVBType = dvbType;
        mInputId = inputId;
        mDtvkitGlueClient = DtvkitGlueClient.getInstance();
        mParameterManager = new ParameterManager(mContext, mDtvkitGlueClient);
        mDataManager = new DataManager(mContext);
        mUpdateScanView = updateScanView;
        initScanWorkHandler();
    }

    public boolean startManualScan(){
        return true;
    }

    public boolean startAutoScan(){
        mScanMode = DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO;
        if (null != mScanWorkHandler) {
            mScanWorkHandler.removeMessages(MSG_START_SEARCH);
            Message mess = mScanWorkHandler.obtainMessage(MSG_START_SEARCH, 0, 0, null);
            boolean info = mScanWorkHandler.sendMessageDelayed(mess, 0);
            Log.d(TAG, "startAutoScan MSG_START_SEARCH " + info);
            return true;
        }
        return false;
    }

    public boolean stopScan(boolean skipConfirmNetwork){
        if (mStartScanProcess) {
            sendFinishScan(skipConfirmNetwork);
        } else {
            sendStopScan();
        }
        return true;
    }

    public boolean setOperatorsType(){
        return true;
    }

    public boolean getNumberOfServices(){
        return true;
    }

    public boolean getListOfServices(){
        return true;
    }

    public boolean isStartScan() {
        return mStartScanProcess;
    }

    public boolean isSyncTvProvider() {
        return mStartSyncChannelProvider;
    }

    public void releaseScanResource() {
        Log.d(TAG, "releaseScanResource");


    }

    private void initScanWorkHandler() {
        Log.d(TAG, "initScanWorkHandler");
        mHandlerThread = new HandlerThread("TAG");
        mHandlerThread.start();
        mScanWorkHandler = new Handler(mHandlerThread.getLooper(), (Message msg)->{
            switch (msg.what) {
                case MSG_START_SEARCH: {
                    if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mScanMode) {
                        handleStartAutoScan();
                    } else {
                        //TBD:manual scan
                    }
                    break;
                }
                case MSG_STOP_SEARCH: {
                    handleStopScan();
                    break;
                }
                case MSG_FINISH_SEARCH: {
                    handleFinishScan(1 == msg.arg1);
                    break;
                }
                case MSG_ON_SIGNAL: {
                    handleScanSignal(msg.arg1, (JSONObject)msg.obj);
                    break;
                }
                case MSG_FINISH: {

                    break;
                }
                case MSG_RELEASE: {

                    break;
                }
                case MSG_START_BY_AUTOMATIC_MODE: {

                    break;
                }
                default:
                    break;
            }
            Log.d(TAG, "mScanWorkHandler handleMessage " + msg.what + " over");
            return true;
        });
    }

    private void releaseScanWorkHandler() {
        Log.d(TAG, "releaseHandler");
        mHandlerThread.getLooper().quitSafely();
        mScanWorkHandler.removeCallbacksAndMessages(null);
        mHandlerThread = null;
        mScanWorkHandler = null;
    }

    private void sendScanSignal(String signal, JSONObject data) {
        int scanStatus = SCAN_MONITOR_STATUS_INVALID;

        if ((null == signal) || null == mScanWorkHandler) {
            Log.e(TAG, "sendHandleScanSignal parameter error");
            return;
        }

        Log.d(TAG, "sendHandleScanSignal signal = " + signal);

        if ((DVB_T == mDVBType && signal.equals("DvbtStatusChanged")) || (DVB_C == mDVBType && signal.equals("DvbcStatusChanged"))) {
            scanStatus = SCAN_MONITOR_STATUS_CHANGE;
        } else if (signal.equals("UpdateMsgStatus")) {
            scanStatus = SCAN_MONITOR_STATUS_UPDATE;
        }

        if (SCAN_MONITOR_STATUS_INVALID != scanStatus) {
            mScanWorkHandler.removeMessages(MSG_ON_SIGNAL);
            Message msg = mScanWorkHandler.obtainMessage(MSG_ON_SIGNAL, scanStatus, 0, data);
            boolean info = mScanWorkHandler.sendMessageDelayed(msg, 0);
            Log.d(TAG, "sendMessage MSG_ON_SIGNAL " + info + "|scanStatus = " + scanStatus);
        }
    }

    private void sendFinishScan(boolean skipConfirmNetwork) {
        if (null != mScanWorkHandler) {
            mScanWorkHandler.removeMessages(MSG_FINISH_SEARCH);
            Message msg = mScanWorkHandler.obtainMessage(MSG_FINISH_SEARCH, skipConfirmNetwork ? 1 : 0, 0, null);
            boolean info = mScanWorkHandler.sendMessageDelayed(msg, 1000);
            Log.d(TAG, "sendFinishSearch MSG_FINISH_SEARCH " + info);
        }
    }

    private void sendStopScan() {
        if (null != mScanWorkHandler) {
            mScanWorkHandler.removeMessages(MSG_STOP_SEARCH);
            Message msg = mScanWorkHandler.obtainMessage(MSG_STOP_SEARCH);
            boolean info = mScanWorkHandler.sendMessageDelayed(msg, 0);
            Log.d(TAG, "sendStopScan MSG_STOP_SEARCH " + info);
        }
    }

    private void setCurrentDvbSource() {
        int source;
        if (DVB_T == mDVBType) {
            source = ParameterManager.SIGNAL_COFDM;
        } else {
            source = ParameterManager.SIGNAL_QAM;
        }

        JSONArray array = new JSONArray();
        try {
            array.put(source);
            mDtvkitGlueClient.request("Dvb.SetDvbSource", array);
            mParameterManager.saveStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM,
                    TvContractUtils.dvbSourceToDbString(source));
            EpgSyncJobService.setChannelTypeFilter(TvContractUtils.dvbSourceToChannelTypeString(source));
        } catch (Exception e) {
        }
    }

    private void handleStartAutoScan() {
        Log.d(TAG, "handleStartAutoScan");

        startScanMonitor();
        setDataProviderScanStatus(true);
        dtvkitFinishScan(false);
        setCurrentDvbSource();
        try {
            JSONArray args = prepareAutoScanParam();
            if (null != args) {
                mDtvkitGlueClient.request(DVB_T == mDVBType ? COMMAND_DVBT_AUTO_SCAN : COMMAND_DVBC_AUTO_SCAN, args);
                if (null != mUpdateScanView) {
                    mUpdateScanView.updateScanStatus("The channel scan may take a while to complete.");
                }
            } else {
                stopScanMonitor();
                setDataProviderScanStatus(false);
                dtvkitFinishScan(true);
                if (null != mUpdateScanView) {
                    mUpdateScanView.updateScanStatus("parameter not complete");
                }
            }
        } catch (Exception e) {
            stopScanMonitor();
            setDataProviderScanStatus(false);
            dtvkitFinishScan(true);
            if (null != mUpdateScanView) {
                mUpdateScanView.updateScanStatus("Failed to start search" + e.getMessage());
            }
        }
    }

    private void handleScanSignal(int scanStatus, JSONObject data) {
        int serviceNumber = getFoundServiceNumberOnSearch();
        int signalStrength = 0;
        int signalQuality = 0;

        if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO != mScanMode) {
            signalStrength = mParameterManager.getStrengthStatus();
            signalQuality = mParameterManager.getQualityStatus();
        }

        Log.d(TAG, "handleScanSignal scanStatus = " + scanStatus + "|serviceNumber = " + serviceNumber);
        if (SCAN_MONITOR_STATUS_CHANGE == scanStatus) {
            try {
                int progress = data.getInt("progress");
                if (null != mUpdateScanView) {
                    mUpdateScanView.updateScanProgress(progress);
                    mUpdateScanView.updateScanChannelNumber(serviceNumber, 0);
                    Log.d(TAG, "SCAN_MONITOR_STATUS_CHANGE progress = " + progress + "|serviceNumber = " + serviceNumber);
                    if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO != mScanMode) {
                        mUpdateScanView.updateScanSignalStrength(signalStrength);
                        mUpdateScanView.updateScanSignalQuality(signalQuality);
                        Log.d(TAG, "SCAN_MONITOR_STATUS_CHANGE signalStrength = " + signalStrength + "|signalQuality = " + signalQuality);
                    }
                }
            } catch (JSONException ignore) {
                Log.e(TAG, "getSearchProcess Exception = " + ignore.getMessage());
            }
        } else if (SCAN_MONITOR_STATUS_UPDATE == scanStatus) {
            if (null != mUpdateScanView) {
                if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO != mScanMode) {
                    mUpdateScanView.updateScanSignalStrength(signalStrength);
                    mUpdateScanView.updateScanSignalQuality(signalQuality);
                    Log.d(TAG, "SCAN_MONITOR_STATUS_UPDATE signalStrength = " + signalStrength + "|signalQuality = " + signalQuality);
                }
            }
        }
    }

    private void handleFinishScan(boolean skipConfirmNetwork) {
        Log.d(TAG, "handleFinishScan skipConfirmNetwork = " + skipConfirmNetwork);
        ClmManager clmManager = new ClmManager(mActivity);
        if (clmManager.checkNeedIpScan() && (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mScanMode)) {
            clmManager.addFinishCallback(new ClmManager.ClmFinishCallback() {
                @Override
                public void onClmFinishSuccess() {
                    finishScanProcess(true);
                }
                @Override
                public void onClmFinishFailed(boolean needRevert) {
                    finishScanProcess(true);
                }
            });
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    clmManager.clmHandleStart(ClmManager.FTI_SCAN);
                }
            });
        } else {
            if ((DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mScanMode) && !skipConfirmNetwork && notifyShowTargetRegion()) {
                Log.d(TAG, "finish scan flow after TargetRegion flow");
                return;
            } else {
                finishScanProcess(skipConfirmNetwork);
            }
        }
    }

    private void handleStopScan() {
        dtvkitFinishScan(true);
        stopScanMonitor();
    }

    private void handleReleaseScanResource() {
        if (mStartScanProcess) {
            stopScanMonitor();
        }
        if (mStartSyncChannelProvider) {
            stopProviderSyncMonitor();
        }
        releaseScanWorkHandler();
        setDataProviderScanStatus(false);
        mUpdateScanView = null;
        mContext = null;
    }

    private void sendHandlerFinish() {
        if (null != mScanWorkHandler) {
            mScanWorkHandler.removeMessages(MSG_FINISH);
            Message mess = mScanWorkHandler.obtainMessage(MSG_FINISH, 0, 0, null);
            boolean info = mScanWorkHandler.sendMessageDelayed(mess, 0);
            Log.d(TAG, "sendHandlerFinish MSG_FINISH " + info);
        }
    }

    private void finishScanProcess(boolean skipConfirmNetwork) {
        if (null != mUpdateScanView) {
            mUpdateScanView.updateScanStatus("Finishing search" + "");
            if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mScanMode) {
                mUpdateScanView.updateScanSignalQuality(0);
                mUpdateScanView.updateScanSignalStrength(0);
            }
        }
        mStartScanProcess = false;
        stopScanMonitor();
        dtvkitFinishScan(true);
        if (notifyShowLcnConflict()) {
            Log.d(TAG, "showLcnConflict");
            return;
        } else if ((DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mScanMode) && !skipConfirmNetwork && notifyShowNetworkInfo()) {
            Log.d(TAG, "ShowNetworkInfo");
            return;
        } else {
            updateChannelListAndCheckLcn(false);
        }
    }

    private void dtvkitFinishScan(boolean commitFlag) {
        try {
            JSONArray args = new JSONArray();
            args.put(commitFlag);
            mDtvkitGlueClient.request(DVB_T == mDVBType ? COMMAND_DVBT_FINISH_SCAN : COMMAND_DVBC_FINISH_SCAN, args);
        } catch (Exception e) {
            //TBD:update search status
            //setSearchStatus("Failed to finish search", e.getMessage());;
        }
    }

    private JSONArray prepareAutoScanParam() {
        JSONArray args = new JSONArray();

        if (DVB_T == mDVBType) {
            args.put(true);//whether clear old previous search  result
            args.put(false);//whether open nit search
            return args;
        } else if (DVB_C == mDVBType) {
            //TBD:how to prepare dvbc auto scan param
        }
        return null;
    }

    private int getServiceNumber() {
        int serviceNumber = 0;
        try {
            JSONObject obj = mDtvkitGlueClient.request(COMMAND_DVB_GET_SERVICE_NUMBER, new JSONArray());
            serviceNumber = obj.getInt("data");
            Log.i(TAG, "getFoundServiceNumber found = " + serviceNumber);
        } catch (Exception e) {
            Log.e(TAG, "getFoundServiceNumber Exception = " + e.getMessage());
        }
        return serviceNumber;
    }

    private int getFoundServiceNumberOnSearch() {
        int found = 0;
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request(COMMAND_DVB_GET_SERVICE_NUMBER_ON_SEARCH, new JSONArray());
            JSONObject datas = obj.getJSONObject("data");
            found = datas.getInt("total_num");
            Log.i(TAG, "getFoundServiceNumberOnSearch found = " + found);
        } catch (Exception ignore) {
            Log.e(TAG, "getFoundServiceNumberOnSearch Exception = " + ignore.getMessage());
        }
        return found;
    }

    private boolean notifyShowLcnConflict() {
        JSONArray array = mParameterManager.getConflictLcn();
        if (mParameterManager.needConfirmLcnInformation(array)) {
            if (null != mUpdateScanView) {
                //TBD:need check input parameter
                Bundle data = new Bundle();
                //mUpdateScanView.notifyUpdateViewEvent(data);
                return true;
            }
        }
        return false;
    }

    private boolean notifyShowNetworkInfo() {
        JSONArray array = mParameterManager.getNetworksOfRegion();
        if (mParameterManager.needConfirmNetWorkInformation(array)) {
            if (null != mUpdateScanView) {
                //TBD:need check input parameter
                Bundle data = new Bundle();
                //mUpdateScanView.notifyUpdateViewEvent(data);
                return true;
            }
        }
        return false;
    }

    private boolean notifyShowTargetRegion() {
        try {
            JSONArray countryArray = mParameterManager.getTargetRegions(TargetRegionManager.TARGET_REGION_COUNTRY, -1, -1, -1);
            JSONArray primaryArray = mParameterManager.getTargetRegions(TargetRegionManager.TARGET_REGION_PRIMARY,
                    countryArray.length() > 0 ? (int) (((JSONObject) (countryArray.get(0))).get("country_code")) : -1, -1, -1);
            JSONArray secondaryArray = mParameterManager.getTargetRegions(TargetRegionManager.TARGET_REGION_SECONDARY,
                    countryArray.length() > 0 ? (int) (((JSONObject) (countryArray.get(0))).get("country_code")) : -1,
                    primaryArray.length() > 0 ? (int) (((JSONObject) (primaryArray.get(0))).get("region_code")) : -1,
                    -1);
            JSONArray tertiaryArray = mParameterManager.getTargetRegions(TargetRegionManager.TARGET_REGION_TERTIARY,
                    countryArray.length() > 0 ? (int) (((JSONObject) (countryArray.get(0))).get("country_code")) : -1,
                    primaryArray.length() > 0 ? (int) (((JSONObject) (primaryArray.get(0))).get("region_code")) : -1,
                    secondaryArray.length() > 0 ? (int) (((JSONObject) (secondaryArray.get(0))).get("region_code")) : -1);
            if (mParameterManager.needConfirmTargetRegion(countryArray, primaryArray, secondaryArray, tertiaryArray)) {
                if (null != mUpdateScanView) {
                    //TBD:need check how to modify show TargetRegion
                    Bundle data = new Bundle();
                    //mUpdateScanView.notifyUpdateViewEvent(data);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.i(TAG,"getTargetRegions error " + e.getMessage());
        }
        return false;
    }

    private JSONArray getServiceList() {
        JSONArray result = null;
        try {
            JSONObject obj = mDtvkitGlueClient.request(COMMAND_DVB_GET_SERVICE_LIST, new JSONArray());
            JSONArray services = obj.getJSONArray("data");
            result = services;
            for (int i = 0; i < services.length(); i++) {
                JSONObject service = services.getJSONObject(i);
                Log.i(TAG, "getServiceList service = " + service.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "getServiceList Exception = " + e.getMessage());
        }
        return result;
    }

    private void updateChannelListAndCheckLcn(boolean needCheckLcn) {
        mServiceList = getServiceList();
        mServiceNumber = getServiceNumber();
        if (0 == mServiceNumber && null != mServiceList && mServiceList.length() > 0) {
            Log.d(TAG, "mServiceNumber error use mServiceList length = " + mServiceList.length());
            mServiceNumber = mServiceList.length();
        }
        startProviderSyncMonitor();
        Log.d(TAG, "updateChannelListAndCheckLcn mServiceNumber = " + mServiceNumber + "|needCheckLcn = " + needCheckLcn);

        Log.i(TAG, String.format("inputId: %s", mInputId));
        Bundle parameters = new Bundle();
        if (needCheckLcn) {
            parameters.putBoolean(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_LCN_CONFLICT, false);
        }

        if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO != mScanMode) {
            //TBD need add get manual scan param
        }
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mScanMode ? EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO : EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL);
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, DVB_T == mDVBType ? "DVB-T" : "DVB-C");

        Intent intent = new Intent(mContext, com.droidlogic.dtvkit.inputsource.DtvkitEpgSync.class);
        intent.putExtra("inputId", mInputId);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_CHANNEL, (mServiceNumber > 0));
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
        mContext.startService(intent);
        if (null != mUpdateScanView) {
            mUpdateScanView.updateScanStatus("Start save channel, please wait.");
        }
    }

    private void setDataProviderScanStatus(boolean isScan) {
        PropSettingManager.setProp(PropSettingManager.TV_SEARCHING_STATUS, isScan ? "1" : "0");
    }

    private void startProviderSyncMonitor() {
        mStartSyncChannelProvider = true;
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver,
                new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
    }

    private void stopProviderSyncMonitor() {
        mStartSyncChannelProvider = false;
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
    }

    private void startScanMonitor() {
        mStartScanProcess = true;
        mDtvkitGlueClient.registerSignalHandler(mDtvKitSignalHandler);
    }

    private void stopScanMonitor() {
        mStartScanProcess = false;
        mDtvkitGlueClient.unregisterSignalHandler(mDtvKitSignalHandler);
    }
}
