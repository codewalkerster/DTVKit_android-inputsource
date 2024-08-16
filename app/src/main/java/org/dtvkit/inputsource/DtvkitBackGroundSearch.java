package com.droidlogic.dtvkit.inputsource;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.icu.util.Calendar;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils;
import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.DtvkitEpgSync;
import com.droidlogic.dtvkit.inputsource.TargetRegionManager;
import com.droidlogic.fragment.DvbsParameterManager;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.settings.PropSettingManager;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.dtvkit.inputsource.fvp.ClmManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DtvkitBackGroundSearch {
    private static final String TAG = "DtvkitBackGroundSearch";

    private final com.droidlogic.dtvkit.inputsource.DataManager mDataManager;
    private final ParameterManager mParameterManager;
    private boolean mStartSync = false;
    private boolean mStartSearch = false;
    private JSONArray mServiceList = null;
    private int mFoundServiceNumber = 0;

    private final Context mContext;
    private final int mCurrentDvbSource;
    private final String mInputId;
    private final Handler mMainHandler = new Handler();
    public static final String AUTOMATIC_SEARCHING_ACTION = "com.droidlogic.dtvkit.inputsource.AutomaticSearching";
    public static final String SINGLE_FREQUENCY_STATUS_ITEM = "status_item";
    public static final String SINGLE_FREQUENCY_STATUS_SEARCH_START = "search_start";
    public static final String SINGLE_FREQUENCY_STATUS_SEARCH_TERMINATE = "search_terminate";
    public static final String SINGLE_FREQUENCY_STATUS_SEARCH_PROGRESS = "search_progress";
    public static final String SINGLE_FREQUENCY_STATUS_SEARCH_CHANNEL_NUMBER = "search_channel_number";
    public static final String SINGLE_FREQUENCY_STATUS_SEARCH_FINISH = "search_finish";
    public static final String SINGLE_FREQUENCY_STATUS_SAVE_START = "save_start";
    public static final String SINGLE_FREQUENCY_STATUS_SAVE_FINISH = "save_finish";
    public static final String SINGLE_FREQUENCY_CHANNEL_NAME = "channel_name";
    public static final String SINGLE_FREQUENCY_CHANNEL_DISPLAY_NUMBER = "display_number";
    public static final String SINGLE_FREQUENCY_TKGS_USER_MSG = "user_msg";
    public static final String SINGLE_FREQUENCY_SET_TARGET_REGION = "set_target_region";

    // receiver used
    private static final String WAKE_LOCK_NAME = "AutomaticSearchingWakeLock";
    private PowerManager.WakeLock mWakeLock = null;
    private PendingIntent mAlarmIntent = null;
    private String mUserMsg = "";
    private boolean mNeedSetTargetRegion = false;
    private boolean is_tkgs_standby_search = false;

    private final DtvkitGlueClient.SignalHandler mHandler = (signal, data) -> mMainHandler.post(() -> responseOnSignal(signal, data));

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            mMainHandler.post(() -> responseOnReceive(intent));
        }
    };

    public DtvkitBackGroundSearch(Context context, int dvbSource, String inputId,
                                  DataManager dataManager, ParameterManager parameterManager) {
        //auto scan mode
        mContext = context;
        mCurrentDvbSource = dvbSource;
        mInputId = inputId;
        mDataManager = dataManager;
        mParameterManager = parameterManager;
    }

    private boolean isNotSupportBackgroundSearch() {
        return (mCurrentDvbSource != ParameterManager.SIGNAL_COFDM
                && mCurrentDvbSource != ParameterManager.SIGNAL_QAM
                && mCurrentDvbSource != ParameterManager.SIGNAL_ISDBT
                && (mCurrentDvbSource != ParameterManager.SIGNAL_QPSK
                || !"TKGS".equals(mDataManager.getStringParameters(ParameterManager.DVBS_OPERATOR_MODE))));
    }

    private String getStartSearchCommand() {
        String ret = null;
        switch (mCurrentDvbSource) {
            case ParameterManager.SIGNAL_COFDM: {
                ret = "Dvbt.startSearch";
            }
            break;
            case ParameterManager.SIGNAL_QAM: {
                ret = "Dvbc.startSearchEx";
            }
            break;
            case ParameterManager.SIGNAL_QPSK: {
                ret = "Dvbs.startDvbUpdate";
            }
            break;
            case ParameterManager.SIGNAL_ISDBT: {
                ret = "Isdbt.startSearch";
                break;
            }
            default:
                break;
        }
        return ret;
    }

    private String getFinishSearchCommand() {
        String ret = null;
        switch (mCurrentDvbSource) {
            case ParameterManager.SIGNAL_COFDM: {
                ret = "Dvbt.finishSearch";
                break;
            }
            case ParameterManager.SIGNAL_QAM: {
                ret = "Dvbc.finishSearch";
                break;
            }
            case ParameterManager.SIGNAL_QPSK: {
                ret = "Dvbs.finishSearch";
                break;
            }
            case ParameterManager.SIGNAL_ISDBT: {
                ret = "Isdbt.finishSearch";
                break;
            }
            default:
                break;
        }
        return ret;
    }

    private JSONArray initAutoScanParameter() {
        //only for dvbt/dvbc
        JSONArray args = new JSONArray();
        switch (mCurrentDvbSource) {
            case ParameterManager.SIGNAL_COFDM:
                args.put(true);
                break;
            case ParameterManager.SIGNAL_QAM:
                args.put("full");
                break;
            case ParameterManager.SIGNAL_QPSK:
                args.put("quick");
                args.put(0x0301);
                args.put("fti");
                args.put(getLnbList().toString());
                args.put("standby");
                break;
            case ParameterManager.SIGNAL_ISDBT:
                args.put(true);
                break;
            default:
                break;
        }
        return args;
    }
    private JSONObject getLnbList() {
        DvbsParameterManager mDvbsParameterManager = mParameterManager.getDvbsParaManager();
        List<String> lnbList = mDvbsParameterManager.getLnbWrap().getLnbIdList();
        JSONObject lnbArgs = new JSONObject();
        JSONArray lnbArgs_array = new JSONArray();
        Log.i(TAG, "initSearchParameter lnbList = " + lnbList);
        try {
            for (String id : lnbList) {
                JSONObject obj = new JSONObject();
                if (!mDvbsParameterManager.getSatelliteNameListSelected(id).isEmpty()) {
                    obj.put("lnb", id);
                    lnbArgs_array.put(obj);
                }
            }
            Log.i(TAG, "initSearchParameter lnbArgs_array = " + lnbArgs_array);
            if (lnbArgs_array.length() > 0) {
                lnbArgs.put("lnblist", lnbArgs_array);
            }
        } catch (Exception e) {
            Log.e(TAG, "getLnbList" + e.getMessage());
        }
        return lnbArgs;
    }

    public void startBackGroundAutoSearch() {
        if (isNotSupportBackgroundSearch()) {
            return;
        }
        startMonitoringSearch();
        mFoundServiceNumber = 0;
        try {
            JSONArray args = new JSONArray();
            args.put(true); // Commit
            String finishCommand = getFinishSearchCommand();
            if (!TextUtils.isEmpty(finishCommand)) {
                DtvkitGlueClient.getInstance().request(getFinishSearchCommand(), args);
            }
        } catch (Exception e) {
            stopMonitoringSearch();
            Log.e(TAG, "startBackGroundAutoSearch Failed to finish search " + e.getMessage());
            return;
        }

        try {
            JSONArray args = initAutoScanParameter();
            String command = getStartSearchCommand();
            Log.d(TAG, "startBackGroundAutoSearch command = " + command + ", args = " + args);
            if (!TextUtils.isEmpty(command)) {
                DtvkitGlueClient.getInstance().request(command, args);
                mStartSearch = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "startBackGroundAutoSearch search Exception " + e.getMessage());
            stopSearch(false);
        }
        if (mStartSearch) {
            PropSettingManager.setProp(PropSettingManager.TV_SEARCHING_STATUS, "1");
        }
    }

    public void stopSearch(boolean commitNewService) {
        mStartSearch = false;
        stopMonitoringSearch();
        try {
            JSONArray args = new JSONArray();
            args.put(commitNewService);
            String finishCommand = getFinishSearchCommand();
            if (!TextUtils.isEmpty(finishCommand)) {
                DtvkitGlueClient.getInstance().request(getFinishSearchCommand(), args);
            }
        } catch (Exception e) {
            Log.e(TAG, "stopSearch failed to finish search Exception " + e.getMessage());
        }
    }

    private void refreshServiceList() {
        try {
            JSONArray args = new JSONArray();
            args.put(mCurrentDvbSource);
            DtvkitGlueClient.getInstance().request("Dvb.refreshServiceList", args);
        } catch (Exception e) {
            Log.e(TAG, "refreshServiceList failed " + e.getMessage());
        }
    }

    private void onSearchFinished() {
        mStartSearch = false;
        Log.i(TAG, "onSearchFinished");
        stopMonitoringSearch();
        if (mCurrentDvbSource == ParameterManager.SIGNAL_QPSK
                && "TKGS".equals(mDataManager.getStringParameters(ParameterManager.DVBS_OPERATOR_MODE))) {
            showTKGSUserMsg();
        }
        PropSettingManager.setProp(PropSettingManager.TV_SEARCHING_STATUS, "0");
        try {
            JSONArray args = new JSONArray();
            args.put(true); // Commit
            String finishCommand = getFinishSearchCommand();
            if (!TextUtils.isEmpty(finishCommand)) {
                DtvkitGlueClient.getInstance().request(getFinishSearchCommand(), args);
            }
        } catch (Exception e) {
            Log.e(TAG, "onSearchFinished failed to finish search Exception " + e.getMessage());
            return;
        }
        //update search results as After the search is finished, the lcn will be reordered
        try {
            mServiceList = DtvkitEpgSync.getServicesList();
            DtvkitEpgSync.setServicesToSync(mServiceList);
        } catch (Exception ignored) {}
        mFoundServiceNumber = getFoundServiceNumber();
        if (mFoundServiceNumber == 0 && mServiceList != null && mServiceList.length() > 0) {
            Log.d(TAG, "mFoundServiceNumber erro use mServiceList length = " + mServiceList.length());
            mFoundServiceNumber = mServiceList.length();
        }
        startMonitoringSync();

        Bundle parameters = new Bundle();
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL);
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, TvContractUtils.dvbSourceToDbString(mCurrentDvbSource));

        Intent intent = new Intent(mContext, DtvkitEpgSync.class);
        intent.putExtra("inputId", mInputId);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_CHANNEL, (mFoundServiceNumber > 0));
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
        mContext.startService(intent);
    }

    private void showTKGSUserMsg() {
        try {
            JSONObject mess = new JSONObject();
            mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_TKGS_USER_MSG);
            handleBackgroundMessage(mess);
        } catch (JSONException ignored) {
        }
    }

    private void setTargetRegion() {
        try {
            JSONObject mess = new JSONObject();
            mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_SET_TARGET_REGION);
            handleBackgroundMessage(mess);
        } catch (JSONException ignored) {
        }
    }

    private void startMonitoringSearch() {
        DtvkitGlueClient.getInstance().registerSignalHandler(mHandler);
        try {
            JSONObject mess = new JSONObject();
            mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SEARCH_START);
            Log.i(TAG, "startMonitoringSearch " + mess);
            handleBackgroundMessage(mess);
        } catch (JSONException ignored) {
        }
    }

    private void stopMonitoringSearch() {
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
        try {
            JSONObject mess = new JSONObject();
            mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SEARCH_FINISH);
            Log.i(TAG, "stopMonitoringSearch " + mess);
            handleBackgroundMessage(mess);
        } catch (JSONException ignored) {
        }
    }

    private void onSearchTerminate() {
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
        try {
            JSONObject mess = new JSONObject();
            mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SEARCH_TERMINATE);
            Log.i(TAG, "onSearchTerminate " + mess);
            handleBackgroundMessage(mess);
        } catch (JSONException ignored) {
        }
    }

    private void startMonitoringSync() {
        mStartSync = true;
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver,
                new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
        try {
            JSONObject mess = new JSONObject();
            mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SAVE_START);
            Log.i(TAG, "startMonitoringSync " + mess);
            handleBackgroundMessage(mess);
        } catch (JSONException ignored) {
        }
    }

    private void stopMonitoringSync(boolean error) {
        mStartSync = false;
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
        try {
            // JSONObject mess = getFirstTwoSearchedChannel(mFrequency, error);
            JSONObject mess = new JSONObject();
            mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SAVE_FINISH);
            Log.i(TAG, "stopMonitoringSync " + mess);
            handleBackgroundMessage(mess);
        } catch (Exception ignored) {
        }
    }

    private JSONObject getFirstTwoSearchedChannel(int frequency, boolean error) {
        JSONObject result = null;
        String firstServiceName;
        String firstServiceDisplayNumber;
        int foundFrequency;
        int foundCount = 0;
        if (mServiceList == null || mServiceList.length() == 0 || error) {
            try {
                result = new JSONObject();
                result.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SAVE_FINISH);
            } catch (JSONException e) {
                Log.e(TAG, "getFirstTwoSearchedChannel JSONException1 = " + e.getMessage());
            }
        } else {
            try {
                for (int i = 0; i < mServiceList.length(); i++) {
                    firstServiceName = mServiceList.getJSONObject(i).getString("name");
                    firstServiceDisplayNumber = String.format(Locale.ENGLISH, "%d", mServiceList.getJSONObject(i).getInt("lcn"));
                    foundFrequency = mServiceList.getJSONObject(i).getInt("freq");
                    if (foundFrequency == frequency) {
                        if (result == null) {
                            result = new JSONObject();
                            result.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SAVE_FINISH);
                        }
                        result.put(SINGLE_FREQUENCY_CHANNEL_NAME + foundCount, firstServiceName);
                        result.put(SINGLE_FREQUENCY_CHANNEL_DISPLAY_NUMBER + foundCount, firstServiceDisplayNumber);
                        foundCount++;
                        if (foundCount >= 2) {
                            break;
                        }
                    }
                }
                if (result == null) {
                    result = new JSONObject();
                    result.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SAVE_FINISH);
                }
            } catch (JSONException e) {
                Log.e(TAG, "getFirstTwoSearchedChannel JSONException2 = " + e.getMessage());
            }
        }
        return result;
    }

    private int getFoundServiceNumber() {
        int found = 0;
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request("Dvb.getNumberOfServices", new JSONArray());
            found = obj.getInt("data");
            Log.i(TAG, "getFoundServiceNumber found = " + found);
        } catch (Exception e) {
            Log.e(TAG, "getFoundServiceNumber Exception = " + e.getMessage());
        }
        return found;
    }

    private int getSearchProcess(JSONObject data) {
        int progress = 0;
        if (data == null) {
            return progress;
        }
        try {
            progress = data.getInt("progress");
        } catch (JSONException e) {
            Log.e(TAG, "getSearchProcess Exception = " + e.getMessage());
        }
        return progress;
    }

    private void responseOnSignal(String signal, JSONObject data) {
        if (signal.equals("DvbtStatusChanged") || signal.equals("DvbcStatusChanged")
            || signal.equals("DvbsStatusChanged") || signal.equals("IsdbtStatusChanged")) {
            int progress = getSearchProcess(data);
            if (progress < 0 || progress > 100) {
                Log.d(TAG, "Invalid progress " + progress + ", low level scan has been terminated");
                onSearchTerminate();
            } else if (progress < 100) {
                Log.d(TAG, "onSignal progress = " + progress);
                try {
                    JSONObject mess = new JSONObject();
                    mess.put(SINGLE_FREQUENCY_STATUS_ITEM, SINGLE_FREQUENCY_STATUS_SEARCH_PROGRESS);
                    mess.put(SINGLE_FREQUENCY_STATUS_SEARCH_PROGRESS, progress);
                    handleBackgroundMessage(mess);
                } catch (JSONException ignored) {
                }
            } else {
                Log.d(TAG, "onSignal search finished");
                ClmManager clmManager = new ClmManager(null);
                if (clmManager.checkNeedIpScan() && (ParameterManager.SIGNAL_COFDM  == mCurrentDvbSource)) {
                    Log.d(TAG, "need ip scan");
                    clmManager.addFinishCallback(new ClmManager.ClmFinishCallback() {
                        @Override
                        public void onClmFinishSuccess() {
                            prepareSearchFinished();
                        }
                        @Override
                        public void onClmFinishFailed(boolean needRevert) {
                            prepareSearchFinished();
                        }
                    });
                    Handler handler = new Handler(mContext.getMainLooper());
                    handler.post(() -> clmManager.clmHandleStart(ClmManager.BACKGROUND_SCAN));
                } else {
                    prepareSearchFinished();
                    Log.d(TAG, "not need ip scan");
                }
            }
        }
    }

    private void responseOnReceive(Intent intent) {
        if (intent != null) {
            String from = intent.getStringExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM);
            String status = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            if (EpgSyncJobService.SYNC_FINISHED.equals(status)) {
                if (!TAG.equals(from)) {
                    Log.i(TAG, "Sync Msg is from:" + from);
                    return;
                }
                stopMonitoringSync(false);
            } else if (EpgSyncJobService.SYNC_ERROR.equals(status)) {
                if (!TAG.equals(from)) {
                    Log.i(TAG, "Sync Error Msg is from:" + from);
                    return;
                }
                stopMonitoringSync(true);
            }
        } else {
            Log.d(TAG, "responseOnReceive null");
        }
    }

    public boolean isBackGroundSearching() {
        return mStartSearch;
    }

    public void handleScreenOn() {
        if (!mStartSync && mStartSearch) {
           //If is in syncing, we do nothing wait sync finish
           stopSearch(false);
           onSearchTerminate();
        }
        if (!TextUtils.isEmpty(mUserMsg)) {
            showTipsDialog(1, mUserMsg, false);
            mUserMsg = "";
        }
        if (mNeedSetTargetRegion) {
            showDialogForSetTargetRegion(mContext);
            mNeedSetTargetRegion = false;
        }
    }

    private void prepareSearchFinished( ) {
        if (is_tkgs_standby_search) {
            onSearchFinished();
        } else {
            if (needConfirmTargetRegion()) {
                setTargetRegion();
                // standby search,
                // true: save region info
                stopSearch(true);
                onSearchTerminate();
            } else {
                onSearchFinished();
            }
        }
    }

    private boolean needConfirmTargetRegion() {
        boolean needSetTargetRegion = false;
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
            needSetTargetRegion = mParameterManager.needConfirmTargetRegion(countryArray, primaryArray, secondaryArray, tertiaryArray);
        } catch (Exception e) {
            Log.e(TAG,"getTargetRegions error " + e.getMessage());
        }
        if (needSetTargetRegion) {
            Log.i(TAG, "needConfirmTargetRegion");
        }
        return needSetTargetRegion;
    }

    public void showDialogForSetTargetRegion(Context context) {
        final TargetRegionManager regionManager = new TargetRegionManager(context);
        regionManager.setRegionCallback(new TargetRegionManager.TargetRegionsCallbacks() {
            @Override
            public Map<String, Integer> requestRegionList(int target_id) {
                HashMap<String, Integer> map = new HashMap<String, Integer>();
                JSONArray array = null;
                switch (target_id) {
                    case TargetRegionManager.TARGET_REGION_COUNTRY:
                        array = mParameterManager.getTargetRegions(target_id, -1, -1, -1);
                        break;
                    case TargetRegionManager.TARGET_REGION_PRIMARY:
                        array = mParameterManager.getTargetRegions(target_id,
                                regionManager.getRegionCode(TargetRegionManager.TARGET_REGION_COUNTRY),
                                -1, -1);
                        break;
                    case TargetRegionManager.TARGET_REGION_SECONDARY:
                        array = mParameterManager.getTargetRegions(target_id,
                                regionManager.getRegionCode(TargetRegionManager.TARGET_REGION_COUNTRY),
                                regionManager.getRegionCode(TargetRegionManager.TARGET_REGION_PRIMARY),
                                -1);
                        break;
                    case TargetRegionManager.TARGET_REGION_TERTIARY:
                        array = mParameterManager.getTargetRegions(target_id,
                                regionManager.getRegionCode(TargetRegionManager.TARGET_REGION_COUNTRY),
                                regionManager.getRegionCode(TargetRegionManager.TARGET_REGION_PRIMARY),
                                regionManager.getRegionCode(TargetRegionManager.TARGET_REGION_SECONDARY));
                        break;
                }
                if (array != null && array.length() > 0) {
                    JSONObject region;
                    String region_name;
                    int region_code;
                    for (int i = 0; i < array.length(); i++) {
                        region = mParameterManager.getJSONObjectFromJSONArray(array, i);
                        region_name = mParameterManager.getTargetRegionName(target_id, region);
                        region_code = mParameterManager.getTargetRegionCode(target_id, region);
                        map.put(region_name, region_code);
                    }
                } else {
                    Log.d(TAG, "No regions for target_id " + target_id);
                }
                if (!map.isEmpty())
                    return map;
                return null;
            }

            @Override
            public boolean onRegionSelected(int target_id, int selection_id) {
                return true;
            }

            @Override
            public void onFinishWithSelections(int country, int primary, int secondary, int tertiary) {
                if (country != -1) {
                    mParameterManager.setTargetRegionSelection(
                            TargetRegionManager.TARGET_REGION_COUNTRY, country);
                }

                if (primary != -1) {
                    mParameterManager.setTargetRegionSelection(
                            TargetRegionManager.TARGET_REGION_PRIMARY, primary);
                }
                if (secondary != -1) {
                    mParameterManager.setTargetRegionSelection(
                            TargetRegionManager.TARGET_REGION_SECONDARY, secondary);
                }
                if (tertiary != -1) {
                    mParameterManager.setTargetRegionSelection(
                            TargetRegionManager.TARGET_REGION_TERTIARY, tertiary);
                }
                PropSettingManager.setProp(PropSettingManager.TV_SEARCHING_STATUS, "0");
                refreshServiceList();
                onSearchFinished();
            }
        });
        regionManager.start(false);
    }

    public void handleAlarm(Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Automatic searching action =" + action);
        if (!AUTOMATIC_SEARCHING_ACTION.equals(action)) {
            return;
        }
        int dvbSource = mCurrentDvbSource;
        int mode = mParameterManager.getIntParameters(ParameterManager.AUTO_SEARCHING_MODE);
        Log.d(TAG, "mode = " + mode + ", signal type= " + mCurrentDvbSource);
        if (isNotSupportBackgroundSearch()) {
            Log.w(TAG, "only dvbt/c/TKGS and ISDB-T will do automatic search.");
            return;
        }

        if (intent.getBooleanExtra("tkgs_standby_search", false)) {
            if (dvbSource != ParameterManager.SIGNAL_QPSK) {
                Log.i(TAG, "Currently not in DVBS format, finish");
                return;
            }
        }
        // reset data
        mUserMsg = "";
        mNeedSetTargetRegion = false;
        is_tkgs_standby_search = intent.getBooleanExtra("tkgs_standby_search", false);
        setNextAlarm(mContext);
        PowerManager powerManager = mContext.getSystemService(PowerManager.class);
        if (dvbSource != ParameterManager.SIGNAL_QPSK) {
            //avoid suspend when execute appointed pvr record
            if (mode == 1) { //standby mode
                if (powerManager.isInteractive()) {
                    Log.i(TAG, "Not in sleep mode, skip standby scan.");
                    return;
                }
                acquireWakeLock(mContext);
            }
            //if need to light the screen please run the interface below
            if (mode == 2) {//operate mode
                if (!powerManager.isInteractive()) {
                    Log.d(TAG, "checkSystemWakeUp wakeUp the android.");
                    long time = SystemClock.uptimeMillis();
                    wakeUp(powerManager, time);
                }
                showTipsDialog(0, mContext.getString(R.string.notice_automatic_scan),
                        dvbSource == ParameterManager.SIGNAL_COFDM);
                return;
            }
        } else {
            // Satellite TKgs case
            if (powerManager.isInteractive()) {
                Log.i(TAG, "Not in sleep mode, skip standby scan.");
                return;
            }
            acquireWakeLock(mContext);
        }
        startBackGroundAutoSearch();
    }

    private void setNextAlarm(Context context) {
        Intent intent = new Intent(AUTOMATIC_SEARCHING_ACTION);
        AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
        if (mCurrentDvbSource == ParameterManager.SIGNAL_QPSK
                && "TKGS".equals(mDataManager.getStringParameters(ParameterManager.DVBS_OPERATOR_MODE))) {
            if (mAlarmIntent != null) {
                alarmManager.cancel(mAlarmIntent);
            }
            intent.putExtra("tkgs_standby_search", true);
            mAlarmIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            long current = System.currentTimeMillis();
            long alarmTime = current + TimeUnit.HOURS.toMillis(8);
            Log.d(TAG, "setNextAlarm current =" + new Date(current) + "   alarmTime =" + new Date(alarmTime));
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, mAlarmIntent);
        } else {
            String hour = mParameterManager.getStringParameters(ParameterManager.AUTO_SEARCHING_HOUR);
            String minute = mParameterManager.getStringParameters(ParameterManager.AUTO_SEARCHING_MINUTE);
            int mode = mParameterManager.getIntParameters(ParameterManager.AUTO_SEARCHING_MODE);
            int repetition = mParameterManager.getIntParameters(ParameterManager.AUTO_SEARCHING_REPETITION);
            intent.putExtra("mode", mode + "");
            intent.putExtra("repetition", repetition + "");
            if (mAlarmIntent != null) {
                alarmManager.cancel(mAlarmIntent);
            }
            mAlarmIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            long current = System.currentTimeMillis();
            cal.setTimeInMillis(current);
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
            cal.set(Calendar.MINUTE, Integer.parseInt(minute));
            if (repetition == 0) {
                long alarmTime = cal.getTimeInMillis() + AlarmManager.INTERVAL_DAY;
                Log.d(TAG, "daily =" + new Date(alarmTime));
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime/*wakeAt*/, mAlarmIntent);
            } else if (repetition == 1) {
                long alarmTime = cal.getTimeInMillis() + AlarmManager.INTERVAL_DAY * 7;
                Log.d(TAG, "weekly =" + new Date(alarmTime));
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime/*wakeAt*/, mAlarmIntent);
            }
        }
    }

    private void handleBackgroundMessage(JSONObject msg) {
        {
            Log.d(TAG, "onMessageCallback " + msg);
            String status;
            try {
                status = msg.getString(DtvkitBackGroundSearch.SINGLE_FREQUENCY_STATUS_ITEM);
            } catch (Exception e) {
                Log.e(TAG, "onMessageCallback Exception " + e.getMessage());
                return;
            }
            switch (status) {
                case DtvkitBackGroundSearch.SINGLE_FREQUENCY_STATUS_SEARCH_TERMINATE:
                case DtvkitBackGroundSearch.SINGLE_FREQUENCY_STATUS_SAVE_FINISH: {
                    //standby mode
                    releaseWakeLock();
                    break;
                }
                case DtvkitBackGroundSearch.SINGLE_FREQUENCY_TKGS_USER_MSG: {
                    mUserMsg = mParameterManager.getTKGSUserMessage();
                    Log.i(TAG, "show TKGS msg:" + mUserMsg);
                    break;
                }
                case DtvkitBackGroundSearch.SINGLE_FREQUENCY_SET_TARGET_REGION: {
                    mNeedSetTargetRegion = true;
                    // live tv may use it
                    PropSettingManager.setProp(PropSettingManager.TV_SEARCHING_STATUS, "2");
                    break;
                }
            }
        }
    }

    private synchronized void acquireWakeLock(Context context) {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, context.getPackageName() + WAKE_LOCK_NAME);
            if (mWakeLock != null) {
                Log.d(TAG, "acquireWakeLock " + WAKE_LOCK_NAME + " " + mWakeLock);
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                mWakeLock.acquire();
            }
        }
        // Deep standby, need to hold lock from kernel
        if (android.os.SystemProperties.get("persist.sys.power.key.action", "0").equals("3")) {
            mParameterManager.acquireWakeLock();
        }
    }

    private synchronized void releaseWakeLock() {
        if (mWakeLock != null) {
            Log.d(TAG, "releaseWakeLock " + WAKE_LOCK_NAME + " " + mWakeLock);
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            mWakeLock = null;
        }
        if (android.os.SystemProperties.get("persist.sys.power.key.action", "0").equals("3")) {
            mParameterManager.releaseWakeLock();
        }
    }

    private void wakeUp(PowerManager powerManager, long time) {
        try {
            Class<?> cls = Class.forName("android.os.PowerManager");
            Method method = cls.getMethod("wakeUp", long.class);
            method.invoke(powerManager, time);
        } catch (Exception e) {
            Log.e(TAG, "wakeUp Exception = " + e.getMessage());
        }
    }

    private void showTipsDialog(int type, String titleText, boolean isDvbt) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(mContext, R.layout.confirm_search, null);
        final TextView titleView = dialogView.findViewById(R.id.dialog_title);
        final Button confirmButton = dialogView.findViewById(R.id.confirm);
        final Button cancelButton = dialogView.findViewById(R.id.cancel);

        if (type == 0) {
            // AutomaticSearch Confirm Dialog
            cancelButton.setOnClickListener(v -> alert.dismiss());
            //prevent exit key
            alert.setCancelable(false);
            confirmButton.requestFocus();
            confirmButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alert.dismiss();
                    Intent intent = new Intent();
                    intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, mInputId);
                    intent.setClassName(DataManager.KEY_PACKAGE_NAME, DataManager.KEY_ACTIVITY_DVBT);
                    intent.putExtra(DataManager.KEY_IS_DVBT, isDvbt);
                    intent.putExtra(DataManager.KEY_START_SCAN_FOR_AUTOMATIC, true);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                }
            });
        } else if (type == 1) {
            // TKGS UserMsg Dialog
            alert.setCancelable(true);
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            new Handler().postDelayed(alert::dismiss, 30000);
            alert.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    alert.dismiss();
                    return false;
                }
            });
        }
        titleView.setText(titleText);
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        alert.setView(dialogView);
        alert.show();
        WindowManager.LayoutParams params = alert.getWindow().getAttributes();
        params.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                500, mContext.getResources().getDisplayMetrics());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        alert.getWindow().setAttributes(params);
        alert.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
    }

}
