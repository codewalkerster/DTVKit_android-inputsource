package com.droidlogic.dtvkit.inputsource;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputService;
import android.media.tv.tuner.Tuner;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.DtvkitDvbScanSelect;
import com.droidlogic.dtvkit.inputsource.DtvkitEpgSync;
import com.droidlogic.dtvkit.inputsource.PvrStatusConfirmManager;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.settings.ConstantManager;
import com.droidlogic.settings.PropSettingManager;
import com.droidlogic.dtvkit.inputsource.util.FeatureUtil;
import droidlogic.dtvkit.tuner.TunerAdapter;
import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
public class DtvkitIsdbtSetup extends Activity {
    private static final String TAG = DtvkitIsdbtSetup.class.getSimpleName();
    private SEARCH_STAGE mSearchingStage = SEARCH_STAGE.NOT_START;
    private enum SEARCH_STAGE {
        NOT_START,
        DTV_START,
        ATV_START,
    }

    private enum SEARCH_TV_TYPE {
        DTV,
        ATV,
        DTV_ATV,
    }

    private UIElement UI;
    private boolean mIsHybridSearch = false;
    private DataManager mDataManager;
    private ParameterManager mParameterManager = null;
    private boolean mStartSync = false;
    private boolean mStartSearch = false;
    private boolean mSyncFinish = false;
    private boolean mFinish = false;
    private final JSONArray mServiceList = new JSONArray();
    private int mFoundServiceNumber = 0;
    private PvrStatusConfirmManager mPvrStatusConfirmManager = null;
    private TunerAdapter mTunerAdapter = null;

    protected HandlerThread mHandlerThread = null;
    protected Handler mThreadHandler = null;

    private final static int MSG_START_SEARCH = 1;
    private final static int MSG_STOP_SEARCH = 2;
    private final static int MSG_FINISH_SEARCH = 3;
    private final static int MSG_ON_SIGNAL = 4;
    private final static int MSG_FINISH = 5;
    private final static int MSG_RELEASE= 6;

    private final DtvkitGlueClient.SignalHandler mHandler = (signal, data) -> {
        Map<String, Object> map = new HashMap<>();
        map.put("signal", signal);
        map.put("data", data);
        sendOnSignal(map);
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            String status = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            if (status.equals(EpgSyncJobService.SYNC_FINISHED)
                    || status.equals(EpgSyncJobService.SYNC_ERROR)) {
                UI.setSearchStatus("Finished");
                mStartSync = false;
                mSyncFinish = true;
                sendFinish();
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i(TAG, "key " + KeyEvent.keyCodeToString(keyCode));
        if (mStartSync) {
            Toast.makeText(DtvkitIsdbtSetup.this, R.string.sync_tv_provider, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mStartSearch) {
                sendFinishSearch();
            } else {
                stopMonitoringSearch();
                //stopSearch();
                sendStopSearch();
                //finish();
                sendFinish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.isdb_setup);
        mIsHybridSearch = PropSettingManager.getBoolean("vendor.tv.hybrid.search", false);
        Log.i(TAG, "HybridSearch:" + mIsHybridSearch);
        /************For tuner framework***************/
        if (FeatureUtil.getFeatureSupportTunerFramework()) {
            Tuner tuner = new Tuner(this, null, TvInputService.PRIORITY_HINT_USE_CASE_TYPE_SCAN);
            mTunerAdapter = new TunerAdapter(tuner, TunerAdapter.TUNER_TYPE_SCAN);
        }
        mParameterManager = new ParameterManager(this, DtvkitGlueClient.getInstance());
        mDataManager = new DataManager(this);
        mPvrStatusConfirmManager = new PvrStatusConfirmManager(this, mDataManager);
        Intent intent = getIntent();
        if (intent != null) {
            String status = intent.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
            mPvrStatusConfirmManager.setPvrStatus(status);
            Log.d(TAG, "onCreate status = " + status);
        }
        UI = new UIElement(intent);
        UI.initOrUpdateView();
        initHandler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mPvrStatusConfirmManager.registerCommandReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mPvrStatusConfirmManager.unRegisterCommandReceiver();
    }

    @Override
    public void finish() {
        //send search info to livetv if found any
        Log.d(TAG, "finish");
        Intent intent = new Intent();
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_TYPE_MANUAL_AUTO, UI.mSearchMode);
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_TYPE_DVBS_DVBT_DVBC, DtvkitDvbScanSelect.SEARCH_TYPE_DVBT);
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_FOUND_SERVICE_NUMBER, mFoundServiceNumber);
        if (mFoundServiceNumber > 0) {
            String firstServiceName = getFirstServiceName();
            intent.putExtra(DtvkitDvbScanSelect.SEARCH_FOUND_FIRST_SERVICE, firstServiceName);
            Log.d(TAG, "finish firstServiceName = " + firstServiceName);
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED, mSyncFinish ? intent : null);
        }
        super.finish();
    }

    private String getFirstServiceName() {
        String firstServiceName = "";
        for (int i = 0; i < mServiceList.length(); i++) {
            JSONObject service = mServiceList.optJSONObject(i);
            int freq;
            boolean curATv;
            if (service.has("uri")) {
                freq = service.optInt("freq");
                curATv = false;
            } else {
                freq = service.optInt("Freq");
                curATv = true;
            }
            boolean isMatched = UI.mSearchMode != DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL
                    || (freq != 0 && UI.mManualFrequency == freq);
            if (isMatched) {
                if (curATv) {
                    firstServiceName = service.optString("Name");
                    if (firstServiceName.length() == 0) {
                        firstServiceName = "xxxATV Program";
                    }
                    break;
                } else {
                    if (!service.optBoolean("hidden")) {
                        firstServiceName = service.optString("name");
                        break;
                    }
                }
            }
        }
        return firstServiceName;
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (mStartSearch) {
            sendFinishSearch();
        } else if (!mFinish) {
            stopMonitoringSearch();
            sendStopSearch();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        releaseHandler();
        stopMonitoringSearch();
        stopMonitoringSync();
        if (null != mTunerAdapter) {
            mTunerAdapter.release();
        }
    }

    private void initHandler() {
        Log.d(TAG, "initHandler");
        mHandlerThread = new HandlerThread("ISDBT_Setup");
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper(), msg -> {
            Log.d(TAG, "mThreadHandler handleMessage " + msg.what + " start");
            switch (msg.what) {
                case MSG_START_SEARCH: {
                    startSearch();
                    break;
                }
                case MSG_STOP_SEARCH: {
                    stopSearch();
                    break;
                }
                case MSG_FINISH_SEARCH: {
                    onSearchFinished();
                    break;
                }
                case MSG_ON_SIGNAL: {
                    dealOnSignal((Map<String, Object>)msg.obj);
                    break;
                }
                case MSG_FINISH: {
                    finish();
                    break;
                }
                case MSG_RELEASE: {
                    releaseInThread();
                    break;
                }
                default:
                    break;
            }
            Log.d(TAG, "mThreadHandler handleMessage " + msg.what + " over");
            return true;
        });
    }

    private void releaseInThread() {
        Log.d(TAG, "releaseInThread start");
        releaseHandler();
        Log.d(TAG, "releaseInThread end");
    }

    private void releaseHandler() {
        Log.d(TAG, "releaseHandler");
        mHandlerThread.getLooper().quitSafely();
        mThreadHandler.removeCallbacksAndMessages(null);
        mHandlerThread = null;
        mThreadHandler = null;
    }

    private void updateChannelNameContainer(boolean isAtv) {
        JSONArray list;
        List<String> newList = new ArrayList<>();
        ArrayAdapter<String> adapter;
        int select = UI.mChannelNumberI;
        int county_code_Brazil = ('b' << 16 | 'r' << 8 | 'a') & 0xFFFFFF;
        if (isAtv) {
            list = mParameterManager.getRfChannelTable(UI.mAntennaType, ParameterManager.DTV_TYPE_ATV, false);
        } else {
            list = mParameterManager.getIsdbtChannelTable(county_code_Brazil);
        }

        if (list == null || list.length() == 0) {
            Log.d(TAG, "updateChannelNameContainer can't find channel freq table");
            return;
        }

        try {
            for (int i = 0; i < list.length(); i++) {
                JSONObject channelTable = (JSONObject)list.get(i);
                int freq = channelTable.optInt("freq", 0);
                int channelNumber = channelTable.optInt("index", 0);
                String name = channelTable.optString("name", "ch" + channelNumber);
                newList.add("NO." + channelNumber + " " + name + " " + freq + "Hz");
            }
        } catch (Exception e) {
            Log.d(TAG, "got invalid channel freq table");
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, newList);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        UI.spinner_manual_number.setAdapter(adapter);
        select = (select < list.length()) ? select : 0;
        UI.spinner_manual_number.setSelection(select);
    }

    private boolean isInManualATV() {
        return UI.mSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL && UI.mSearchTvType == SEARCH_TV_TYPE.ATV;
    }

    private boolean initSearchParameter(JSONArray args) {
        Log.d(TAG, "initSearchParameter autoSearch:" + isAutoSearch()
                + ", antennaType:" + UI.mAntennaType
                + ", SearchTvType:" + UI.mSearchTvType
                + ", isFrequencySearch:" + UI.mSearchMethod);
        // antennaType == 1, cable atv
        // antennaType == 0, air dtv&atv
        if (isAutoSearch()) {
            if (mIsHybridSearch) {
                args.put(UI.mAntennaType == 1 ? "CABLE" : "AIR");
            } else if (mSearchingStage.ordinal() == SEARCH_STAGE.NOT_START.ordinal()) {
                // ISDB-T DTV
                args.put(true); // reTune
                args.put(UI.cb_network_search.isChecked());
            } else {
                // ATV
                args.put(UI.mAntennaType);
                args.put(1000000);
            }
            return true;
        } else {
            if (mIsHybridSearch) {
                Log.e(TAG, "atv channel list not ready;");
                return false;
            } else {
                int parameter = getParameter();
                if (UI.mSearchTvType == SEARCH_TV_TYPE.ATV) {
                    if (parameter >= 0) {
                        args.put(UI.mAntennaType);
                        if (DataManager.VALUE_FREQUENCY_MODE == UI.mSearchMethod) {
                            args.put(parameter * 1000);//khz to hz
                        } else {
                            args.put(parameter); // chName
                        }
                        args.put(1000000);
                    }
                    return parameter >= 0;
                } else {
                    if (parameter >= 0) {
                        args.put(true); // reTune
                        if (DataManager.VALUE_FREQUENCY_MODE == UI.mSearchMethod) {
                            args.put(mDataManager.getIntParameters(DataManager.KEY_NIT) > 0);
                            args.put(parameter * 1000);//khz to hz
                            args.put("6MHZ");
                            // args.put("8K");
                        } else {
                            args.put(parameter);
                        }
                        return true;
                    }
                }
            }
        }
        Log.e(TAG, "invalid parameters");
        return false;
    }

    private int getParameter() {
        int parameter = -1;
        Editable editable = UI.et_manual_frequency.getText();
        if (UI.mSearchMethod != DataManager.VALUE_FREQUENCY_MODE) {
            parameter = getChannelIndex();
        } else if (editable != null) {
            String value = editable.toString();
            if (!TextUtils.isEmpty(value)/* && TextUtils.isDigitsOnly(value)*/) {
                //float for frequency
                float toFloat = Float.parseFloat(value);
                parameter = (int)(toFloat * 1000.0f);//khz
            }
        }
        return parameter;
    }

    private int getChannelIndex() {
        int index = -1;
        String chName = (String) UI.spinner_manual_number.getSelectedItem();
        if (!TextUtils.isEmpty(chName)) {
            if (UI.mSearchTvType == SEARCH_TV_TYPE.ATV) {
                index = Integer.parseInt(chName.substring(chName.indexOf(" ") + 1, chName.lastIndexOf(" "))); // chName
            } else {
                index = Integer.parseInt(chName.substring(chName.indexOf(".") + 1, chName.indexOf(" ")));
            }
            int freq = Integer.parseInt(chName.substring(chName.lastIndexOf(" ") + 1, chName.indexOf("Hz")));
            Log.d(TAG, "getChannelIndex channel index = " + index
                    + ", freq = " + freq/1000 + "kHz");
        }
        if (index < 0) {
            Log.w(TAG, "getChannelIndex failed");
        }
        return index;
    }

    private void sendStartSearch() {
        if (mThreadHandler != null) {
            mThreadHandler.removeMessages(MSG_START_SEARCH);
            Message mess = mThreadHandler.obtainMessage(MSG_START_SEARCH, 0, 0, null);
            boolean info = mThreadHandler.sendMessageDelayed(mess, 0);
            Log.d(TAG, "sendMessage MSG_START_SEARCH " + info);
        }
    }

    private void startSearch() {
        UI.setSearchStatus("Searching");
        UI.setSearchProgressIndeterminate(false);
        if (mIsHybridSearch) {
            clearATvServices(UI.mAntennaType == 1 ? "CABLE" : "AIR");
        } else if (UI.mSearchTvType == SEARCH_TV_TYPE.DTV_ATV) {
            if (mSearchingStage == SEARCH_STAGE.NOT_START) {
                clearATvServices(UI.mAntennaType == 1 ? "CABLE" : "AIR");
            } else if (mSearchingStage == SEARCH_STAGE.DTV_START) {
                UI.setDTvProgress(100, -1);
            } else {
                UI.setSearchStatus("Stage is wrong");
                Log.e(TAG, mSearchingStage + " is wrong Stage");
                return;
            }
        }
        startMonitoringSearch();
        String failReason = null;
        int isFrequencySearch = UI.mSearchMethod;
        JSONArray args = new JSONArray();
        if (initSearchParameter(args)) {
            boolean result;
            if (isAutoSearch()) {
                result = doStartAutoSearch(args);
            } else {
                result = doStartManualSearch(args, isFrequencySearch == DataManager.VALUE_FREQUENCY_MODE);
            }
            mStartSearch = result;
            if (result) {
                if (mSearchingStage == SEARCH_STAGE.NOT_START) {
                    mSearchingStage = SEARCH_STAGE.DTV_START;
                } else if (mSearchingStage == SEARCH_STAGE.DTV_START) {
                    mSearchingStage = SEARCH_STAGE.ATV_START;
                } else {
                    Log.e(TAG, "error : mSearchingStage:" + mSearchingStage);
                    failReason = "Failed to start search:" + mSearchingStage;
                }
            } else {
                failReason = "Failed to start search: request error!";
            }
        } else {
            failReason = "parameter not complete!";
        }
        UI.setEnabled(failReason != null);
        if (failReason != null) {
            stopMonitoringSearch();
            UI.setSearchStatus(failReason);
        }
        // after startSearch then update status.
        if (mStartSearch) {
            UI.updateSearchButton(false);
        }
    }

    private void sendStopSearch() {
        if (mThreadHandler != null) {
            mThreadHandler.removeMessages(MSG_STOP_SEARCH);
            boolean info = mThreadHandler.sendEmptyMessage(MSG_STOP_SEARCH);
            Log.d(TAG, "sendMessage MSG_STOP_SEARCH " + info);
        }
    }

    private void stopSearch() {
        mStartSearch = false;
        try {
            JSONArray args = new JSONArray();
            args.put(true);
            if (mIsHybridSearch) {
                DtvkitGlueClient.getInstance().request("Tv.stopSearch", args);
            } else if (mSearchingStage.ordinal() == SEARCH_STAGE.ATV_START.ordinal()) {
                DtvkitGlueClient.getInstance().request("Atv.finishSearch", args);
            } else if (mSearchingStage.ordinal() == SEARCH_STAGE.DTV_START.ordinal()) {
                DtvkitGlueClient.getInstance().request("Isdbt.finishSearch", args);
            }
        } catch (Exception e) {
            UI.setSearchStatus("Failed to finish search\t" + e.getMessage());
        }
    }

    // type: AIR/CABLE/ALL
    private void clearATvServices(String type) {
        try {
            JSONArray args = new JSONArray();
            args.put(type);
            DtvkitGlueClient.getInstance().request("Atv.clearAllServices", args);
        } catch (Exception ignored) {}

    }

    private boolean doStartAutoSearch(JSONArray args) {
        String command;
        if (mIsHybridSearch) {
            command = "Tv.startAutoSearch";
        } else {
            if (UI.mSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_FULL) {
                command = "Atv.startFullSearch";
            } else if (UI.mSearchTvType == SEARCH_TV_TYPE.ATV || mSearchingStage == SEARCH_STAGE.DTV_START) {
                command = "Atv.startAutoSearch";
            } else {
                command = "Isdbt.startSearch";
            }
        }
        try {
            Log.d(TAG, "doStartSearch command = " + command + ", args = " + args);
            DtvkitGlueClient.getInstance().request(command, args);
            mParameterManager.saveChannelIdForSource(-1);
        } catch (Exception e) {
            UI.setSearchStatus("Failed to finish search\t" + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean doStartManualSearch(JSONArray args, boolean byFreq) {
        String command;
        if (mIsHybridSearch) {
            command = "Tv.startManualSearchByChannelId";
        } else {
            if (byFreq) {
                if (UI.mSearchTvType == SEARCH_TV_TYPE.DTV) {
                    command = "Isdbt.startManualSearchByFreq";
                } else {
                    command = "Atv.startManualSearchByFreq";
                }
            } else {
                if (UI.mSearchTvType == SEARCH_TV_TYPE.DTV) {
                    command = ("Isdbt.startManualSearchById");
                } else {
                    command = ("Atv.startManualSearchById");
                }
            }
        }
        try {
            Log.d(TAG, "doStartSearch command = " + command + ", args = " + args);
            DtvkitGlueClient.getInstance().request(command, args);
        } catch (Exception e) {
            UI.setSearchStatus("Failed to finish search\t" + e.getMessage());
            return false;
        }
        return true;
    }

    private void sendFinishSearch() {
        if (mThreadHandler != null) {
            mThreadHandler.removeMessages(MSG_FINISH_SEARCH);
            Message mess = mThreadHandler.obtainMessage(MSG_FINISH_SEARCH, 0, 0, null);
            boolean info = mThreadHandler.sendMessageDelayed(mess, 0);
            Log.d(TAG, "sendMessage MSG_FINISH_SEARCH " + info);
        }
    }

    private void onSearchFinished() {
        UI.setEnabled(false);
        UI.updateSearchButton(true);
        UI.setSearchStatus("Finishing search");
        UI.setSearchProgressIndeterminate(true);
        stopMonitoringSearch();
        stopSearch();
        //update search results as After the search is finished, the lcn will be reordered
        try {
            JSONArray dTvList = DtvkitEpgSync.getServicesList();
            DtvkitEpgSync.setServicesToSync(dTvList);
            JSONArray aTvList = DtvkitEpgSync.getAtvServicesList();
            DtvkitEpgSync.setATvServicesToSync(aTvList);
            if (UI.mAntennaType == 0) {
                for (int i = 0; i < dTvList.length(); i++) {
                    mServiceList.put(dTvList.getJSONObject(i));
                }
            }
            Log.d(TAG, "mServiceList ISdbT Number:" + mServiceList.length());
            for (int i = 0; i < aTvList.length(); i++) {
                JSONObject item = aTvList.getJSONObject(i);
                if (item.optInt("SigType") == UI.mAntennaType) {
                    mServiceList.put(item);
                }
            }
            mFoundServiceNumber = mServiceList.length();
            Log.d(TAG, "mServiceList Total Number:" + mServiceList.length());
        } catch (Exception ignored) {}
        UI.setSearchStatus("Updating guide");
        startMonitoringSync();
        // If the intent that started this activity is from Live Channels app
        String inputId = this.getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        Log.i(TAG, String.format("inputId: %s", inputId));
        //EpgSyncJobService.requestImmediateSync(this, inputId, true, new ComponentName(this, DtvkitEpgSync.class)); // 12 hours
        Bundle parameters = new Bundle();
        int isFrequencySearch = mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
        if (DataManager.VALUE_FREQUENCY_MODE == isFrequencySearch && DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL == UI.mSearchMode) {
            parameters.putInt(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY, getParameter() * 1000);
        }
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL != UI.mSearchMode ? EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO : EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL);
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, "ISDB-T");

        Intent intent = new Intent(this, com.droidlogic.dtvkit.inputsource.DtvkitEpgSync.class);
        intent.putExtra("inputId", inputId);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_CHANNEL, (mFoundServiceNumber > 0));
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
        startService(intent);
    }

    private void startMonitoringSearch() {
        DtvkitGlueClient.getInstance().registerSignalHandler(mHandler);
    }

    private void stopMonitoringSearch() {
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
    }

    private void startMonitoringSync() {
        mStartSync = true;
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver,
                new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
    }

    private void stopMonitoringSync() {
        mStartSync = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }

    private boolean isAutoSearch() {
        return DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL != UI.mSearchMode;
    }

    private int getFoundServiceNumber() {
        int found = 0;
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request("Atv.getNumberOfServices", new JSONArray());
            found = obj.getInt("data");
            Log.i(TAG, "getFoundServiceNumber found = " + found);
        } catch (Exception e) {
            Log.e(TAG, "getFoundServiceNumber Exception = " + e.getMessage());
        }
        return found;
    }

    private int[] getFoundServiceNumberOnSearch() {
        int[] found = {0, 0};
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request("Dvb.getCategoryNumberOfServices", new JSONArray());
            JSONObject data = obj.getJSONObject("data");
            found[0] = data.getInt("total_num");
            obj = DtvkitGlueClient.getInstance().request("Atv.getNumberOfServices", new JSONArray());
            found[1] = obj.getInt("data");
            Log.i(TAG, "getFoundServiceNumberOnSearch found = " + Arrays.toString(found));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return found;
    }

    private int[] getSearchProcess(JSONObject data) {
        int[] result = {-1, 0, 0};
        if (data == null) {
            return result;
        }
        try {
            result[0] = data.getInt("progress");
            result[1] = data.optInt("number");
            result[2] = data.optInt("frequency");
        } catch (JSONException e) {
            Log.e(TAG, "getSearchProcess Exception = " + e.getMessage());
        }
        return result;
    }

    private void sendOnSignal(final Map<String, Object> map) {
        if (mThreadHandler != null) {
            String signal = (String) map.get("signal");
            boolean valid = TextUtils.equals("IsdbtStatusChanged", signal)
                    || TextUtils.equals("AtvSearchProgress", signal);
            if (valid) {
                mThreadHandler.removeMessages(MSG_ON_SIGNAL);
                Message mess = mThreadHandler.obtainMessage(MSG_ON_SIGNAL, 0, 0, map);
                boolean info = mThreadHandler.sendMessageDelayed(mess, 0);
                Log.d(TAG, "sendMessage MSG_ON_SIGNAL " + info);
            }
        }
    }

    private void dealOnSignal(final Map<String, Object> map) {
        Log.d(TAG, "dealOnSignal map = " + map);
        if (map == null) {
            Log.d(TAG, "dealOnSignal null map");
            return;
        }
        String signal = (String) map.get("signal");
        JSONObject data = (JSONObject) map.get("data");
        assert signal != null;
        assert data != null;
        int[] result = getSearchProcess(data);
        if (result[0] < 0) {
            return;
        }
        Log.d(TAG, "onSignal progress = " + result[0]);
        int strengthStatus = mParameterManager.getStrengthStatus();
        int qualityStatus = mParameterManager.getQualityStatus();
        boolean isAtvSignal = signal.toUpperCase().contains("ATV");
        if (isAtvSignal) {
            UI.updateSignalInfo(String.format(Locale.US, "Frequency: %.2fMhz", (float) result[2] / (1000 * 1000)));
        } else {
            UI.updateSignalInfo(String.format(Locale.US, "Strength: %d\t\tQuality: %d\t\t", strengthStatus, qualityStatus));
        }
        UI.setSearchStatus(String.format(Locale.ENGLISH, "Searching (%d%%)", result[0]));
        int[] found = getFoundServiceNumberOnSearch();
        if (isAtvSignal) {
            UI.setATvProgress(result[0], found[1]);
        } else {
            UI.setDTvProgress(result[0], found[0]);
        }
        if (result[0] == 100) {
            if (mIsHybridSearch) {
                if (isAtvSignal) {
                    sendFinishSearch();
                }
            } else {
                if (!isAtvSignal && UI.mSearchTvType == SEARCH_TV_TYPE.DTV_ATV) {
                    sendStopSearch();
                    sendStartSearch();
                } else {
                    sendFinishSearch();
                }
            }
            if (UI.mSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL) {
                UI.mManualFrequency = result[2];
            }
        }
    }

    private void sendFinish() {
        if (mThreadHandler != null) {
            mFinish = true;
            mThreadHandler.removeMessages(MSG_FINISH);
            Message mess = mThreadHandler.obtainMessage(MSG_FINISH, 0, 0, null);
            boolean info = mThreadHandler.sendMessageDelayed(mess, 0);
            Log.d(TAG, "sendMessage MSG_FINISH " + info);
        }
    }

    private final class UIElement {
        LinearLayout ll_dtv_search;
        LinearLayout ll_atv_search;
        TextView tv_search_status;
        TextView tv_scan_signal_info;
        CheckBox cb_network_search;
        Spinner spinner_search_mode;
        Spinner spinner_antenna_type;
        LinearLayout ll_adtv_type;
        Spinner spinner_adtv_type;
        LinearLayout ll_search_method_container;
        Spinner spinner_search_method;
        LinearLayout ll_manual_frequency;
        EditText et_manual_frequency;
        LinearLayout ll_manual_number;
        Spinner spinner_manual_number;
        Button btn_option;
        Button btn_search;
        // logic code
        private int mSearchMode;
        private SEARCH_TV_TYPE mSearchTvType; // both: 2, dtv: 0, atv: 1
        private int mSearchMethod;
        private int mAntennaType = -1;
        private int mChannelNumberI;
        private int mManualFrequency;
        private long clickLastTime;

        private final Intent mIntent;

        public UIElement(Intent intent) {
            mIntent = new Intent(intent);
            ll_dtv_search = findViewById(R.id.dtv_search_layout);
            ll_atv_search = findViewById(R.id.atv_search_layout);
            tv_search_status = findViewById(R.id.tv_search_status);
            tv_scan_signal_info = findViewById(R.id.tv_scan_signal_info);
            cb_network_search = findViewById(R.id.network_checkbox);
            spinner_search_mode = findViewById(R.id.public_search_mode_spinner);
            spinner_antenna_type = findViewById(R.id.antenna_type_spinner);
            spinner_adtv_type = findViewById(R.id.adtv_type_spinner);
            ll_adtv_type = findViewById(R.id.adtv_type_container);
            ll_search_method_container = findViewById(R.id.search_method_container);
            spinner_search_method = findViewById(R.id.search_method_spinner);
            ll_manual_frequency = findViewById(R.id.manual_frequency_search);
            et_manual_frequency = findViewById(R.id.edtTxt_chFrequency_in);
            ll_manual_number = findViewById(R.id.manual_number_search);
            spinner_manual_number = findViewById(R.id.search_chNumber_in);
            btn_option = findViewById(R.id.option_set_btn);
            btn_search = findViewById(R.id.btn_start_search);
        }

        private void setEnabled(boolean enable) {
            cb_network_search.setEnabled(enable);
            spinner_search_mode.setEnabled(enable);
            spinner_antenna_type.setEnabled(enable);
            spinner_adtv_type.setEnabled(enable);
            spinner_search_method.setEnabled(enable);
            btn_option.setEnabled(enable);
        }

        private void initOrUpdateView() {
            // data Initialize
            {
                mSearchMode = getSearchMode();
                String tvType = getSearchTvType();
                if (!TextUtils.isEmpty(tvType)) {
                    mSearchTvType = SEARCH_TV_TYPE.valueOf(tvType);
                } else {
                    mSearchTvType = SEARCH_TV_TYPE.DTV;
                }
                // fix unexpectedly cases
                if ((mSearchTvType != SEARCH_TV_TYPE.ATV && mSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_FULL)
                    || (mSearchTvType == SEARCH_TV_TYPE.DTV_ATV && mSearchMode != DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO)){
                    mSearchMode = DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO;
                }
                String type = getAntennaType();
                if (TextUtils.isEmpty(type)) {
                    setAntennaType(0);
                } else if (TextUtils.equals(TvContract.Channels.TYPE_ATSC_C, type)) {
                    mAntennaType = 1;
                } else {
                    mAntennaType = 0;
                }
                mSearchMethod = getSearchMethod();
                mChannelNumberI = getChannelNumberI();
                updateSearchModeContent(false);
            }
            // widget
            cb_network_search.setOnClickListener(v -> {
                if (cb_network_search.isChecked()) {
                    mDataManager.saveIntParameters(DataManager.KEY_NIT, 1);
                } else {
                    mDataManager.saveIntParameters(DataManager.KEY_NIT, 0);
                }
            });
            spinner_search_mode.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_search_mode position = " + position);
                    setSearchMode(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            spinner_antenna_type.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_antenna_type position = " + position);
                    setAntennaType(position);
                    if (!isAutoSearch()) {
                        updateChannelNameContainer(isInManualATV());
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            spinner_adtv_type.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_adtv_type position = " + position);
                    if (SEARCH_TV_TYPE.values()[position] == SEARCH_TV_TYPE.ATV) {
                        mSearchingStage = SEARCH_STAGE.DTV_START;
                    } else {
                        mSearchingStage = SEARCH_STAGE.NOT_START;
                    }
                    mSearchTvType = SEARCH_TV_TYPE.values()[position];
                    setSearchTvType(mSearchTvType);
                    if (mSearchMode != DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO
                        && mSearchMethod == 1) {
                        updateChannelNameContainer(SEARCH_TV_TYPE.ATV.ordinal() == position);
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            spinner_search_method.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_search_method position = " + position);
                    if (mSearchMode != DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO) {
                        updateChannelNameContainer(isInManualATV());
                    }
                    setSearchMethod(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            et_manual_frequency.addTextChangedListener(new TextWatcher() {
                private String mText;
                private int mCursor;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after){ }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count){
                    Log.i(TAG,"text[" + text.toString() + "] start[" + start + "] count[" + count +"]");
                    mCursor = start;
                    mText = text.toString();
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!mText.equals("") && mCursor == 0) {
                        et_manual_frequency.removeTextChangedListener(this);
                        int index = et_manual_frequency.getSelectionStart();
                        if (isInManualATV()) {
                            s.insert(index, ".25");
                        } else {
                            s.insert(index, ".143");
                        }
                        et_manual_frequency.addTextChangedListener(this);
                        et_manual_frequency.setSelection(1);
                    }
                }
            });
            spinner_manual_number.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_manual_number position = " + position);
                    mChannelNumberI = position;
                    setChannelNumberI(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            btn_option.setOnClickListener(v -> {
                Intent intentSet = new Intent(mIntent);
                String pvrStatus = intentSet.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
                String pvrFlag = PvrStatusConfirmManager.read(DtvkitIsdbtSetup.this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
                if (pvrStatus != null && PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(pvrFlag)) {
                    intentSet.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, pvrStatus);
                } else {
                    intentSet.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, "");
                }
                intentSet.setClassName(DataManager.KEY_PACKAGE_NAME, DataManager.KEY_ACTIVITY_SETTINGS);
                startActivity(intentSet);
                mDataManager.saveIntParameters(DataManager.KEY_SELECT_SEARCH_ACTIVITY, DataManager.SELECT_SETTINGS);
            });
            btn_search.setOnClickListener(v -> {
                long currentTime = SystemClock.elapsedRealtime();
                if (currentTime - clickLastTime > 500) {
                    clickLastTime = currentTime;
                    boolean autoSearch = isAutoSearch();
                    mPvrStatusConfirmManager.setSearchType(autoSearch ? ConstantManager.KEY_DTVKIT_SEARCH_TYPE_AUTO : ConstantManager.KEY_DTVKIT_SEARCH_TYPE_MANUAL);
                    boolean checkPvr = mPvrStatusConfirmManager.needDeletePvrRecordings();
                    if (checkPvr) {
                        mPvrStatusConfirmManager.showDialogToAppoint(DtvkitIsdbtSetup.this, autoSearch);
                    } else {
                        if (mStartSearch) {
                            Log.d(TAG, "mAntennaType:" + mAntennaType + ", " + mSearchingStage);
                            if (isAutoSearch() && UI.mSearchTvType == SEARCH_TV_TYPE.DTV_ATV && (!mIsHybridSearch)) {
                                if (mSearchingStage == SEARCH_STAGE.DTV_START) {
                                    sendStopSearch();
                                    sendStartSearch();
                                } else {
                                    sendFinishSearch();
                                }
                            } else {
                                sendFinishSearch();
                            }
                        } else {
                            mPvrStatusConfirmManager.sendDvrCommand(DtvkitIsdbtSetup.this);
                            sendStopSearch();
                            sendStartSearch();
                        }
                    }
                }
            });
            if (mIsHybridSearch) {
                cb_network_search.setVisibility(View.GONE);
                spinner_search_mode.setSelection(DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO);
                spinner_search_mode.setEnabled(false);
                spinner_antenna_type.setSelection(mAntennaType);
                ll_adtv_type.setVisibility(View.GONE);
            } else {
                spinner_search_mode.setSelection(mSearchMode, true);
                spinner_antenna_type.setSelection(mAntennaType, true);
                spinner_adtv_type.setSelection(mSearchTvType.ordinal(), true);
            }
            btn_search.requestFocus();
        }

        private void setDTvProgress(int progress, int number) {
            runOnUiThread(() -> {
                ((ProgressBar) findViewById(R.id.dtv_search_progress)).setProgress(progress);
                if (number >= 0) {
                    ((TextView) findViewById(R.id.dtv_number)).setText(" DTV: " + number);
                }
            });
        }

        private void setATvProgress(int progress, int number) {
            runOnUiThread(() -> {
                ((ProgressBar) findViewById(R.id.atv_search_progress)).setProgress(progress);
                if (number >= 0) {
                    ((TextView) findViewById(R.id.atv_number)).setText(" ATV: " + number);
                }
            });
        }

        private void setSearchProgressIndeterminate(final Boolean indeterminate) {
            runOnUiThread(() -> {
                final ProgressBar bar1 = findViewById(R.id.dtv_search_progress);
                bar1.setIndeterminate(indeterminate);
                final ProgressBar bar2 = findViewById(R.id.atv_search_progress);
                bar2.setIndeterminate(indeterminate);
            });
        }

        private void setSearchStatus(String status) {
            runOnUiThread(() -> {
                tv_search_status.setText(status);
            });
        }

        private void updateSearchButton(final boolean strStart) {
            runOnUiThread(() -> {
                if (strStart) {
                    if (isAutoSearch()) {
                        btn_search.setText(R.string.strStartSearch);
                    } else {
                        btn_search.setText(R.string.strManualSearch);
                    }
                } else {
                    if (isAutoSearch() && UI.mSearchTvType == SEARCH_TV_TYPE.DTV_ATV && (!mIsHybridSearch) && mSearchingStage == SEARCH_STAGE.DTV_START) {
                        btn_search.setText(R.string.strSkip);
                    } else {
                        btn_search.setText(R.string.strStopSearch);
                    }
                }
            });
        }

        private void updateSignalInfo(final String info) {
            runOnUiThread(() -> {
                tv_scan_signal_info.setVisibility(View.VISIBLE);
                tv_scan_signal_info.setText(info);
            });
        }

        private int getSearchMode() {
            return mIsHybridSearch ? DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO : mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE);
        }
        private void setSearchMode(int mode) {
            enOrDisableADTvType(mAntennaType, mode);
            if (mode == DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL) {
                cb_network_search.setVisibility(View.GONE);
                spinner_search_method.setSelection(mSearchMethod);
                ll_search_method_container.setVisibility(View.VISIBLE);
                if (mSearchMethod == DataManager.VALUE_FREQUENCY_MODE) {
                    ll_manual_frequency.setVisibility(View.VISIBLE);
                } else {
                    ll_manual_number.setVisibility(View.VISIBLE);
                }
                if (mSearchTvType != SEARCH_TV_TYPE.DTV_ATV) {
                    spinner_adtv_type.setSelection(mSearchTvType.ordinal());
                } else {
                    spinner_adtv_type.setSelection(SEARCH_TV_TYPE.ATV.ordinal());
                }
                btn_search.setText(R.string.strManualSearch);
            } else {
                cb_network_search.setVisibility(View.VISIBLE);
                ll_search_method_container.setVisibility(View.GONE);
                if (mSearchMethod == DataManager.VALUE_FREQUENCY_MODE) {
                    ll_manual_frequency.setVisibility(View.GONE);
                } else {
                    ll_manual_number.setVisibility(View.GONE);
                }
                if (mIsHybridSearch) {
                    ll_adtv_type.setVisibility(View.GONE);
                } else if (mAntennaType == 1) {
                    spinner_adtv_type.setSelection(SEARCH_TV_TYPE.ATV.ordinal());
                } else {
                    spinner_adtv_type.setSelection(mSearchTvType.ordinal());
                }
                btn_search.setText(R.string.strStartSearch);
            }
            if (mode != mSearchMode) {
                mDataManager.saveIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE, mode);
            }
            mSearchMode = mode;
        }

        private void updateADTvTypeContent(int newSearchMode) {
            if (mIsHybridSearch) {
                return;
            }
            String[] array = getResources().getStringArray(R.array.public_adtv_type_entries);
            ArrayList<String> list = new ArrayList<>(Arrays.asList(array));
            if (newSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL) {
                list.remove(list.size() - 1);
            }
            if (spinner_adtv_type.getCount() == list.size()) {
                return;
            }
            if (mSearchTvType.ordinal() >= spinner_adtv_type.getCount()) {
                Log.d(TAG, "updateADTvTypeContent reset SearchTvType");
                mSearchTvType = SEARCH_TV_TYPE.DTV;
            } else {
                Log.d(TAG, "updateADTvTypeContent");
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(DtvkitIsdbtSetup.this, android.R.layout.simple_spinner_item, list);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner_adtv_type.setAdapter(adapter);
        }

        private void updateSearchModeContent(boolean selectOld) {
            if (mIsHybridSearch) {
                return;
            }
            String[] array = getResources().getStringArray(R.array.public_search_mode_entries);
            ArrayList<String> list = new ArrayList<>(Arrays.asList(array));
            if (mSearchTvType == SEARCH_TV_TYPE.ATV) {
                list.add("Full");
            }
            if (spinner_search_mode.getCount() == list.size()) {
                return;
            }
            Log.d(TAG, "updateSearchModeContent");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(DtvkitIsdbtSetup.this, android.R.layout.simple_spinner_item, list);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner_search_mode.setAdapter(adapter);
            if (selectOld) {
                if (mSearchMode < list.size()) {
                    spinner_search_mode.setSelection(mSearchMode, true);
                } else {
                    spinner_search_mode.setSelection(DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO, true);
                }
            }
        }

        private void enOrDisableADTvType(int newAntennaType, int newSearchMode) {
            if (newAntennaType == 1 || newSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_FULL) {
                spinner_adtv_type.setEnabled(false);
                spinner_adtv_type.setSelection(SEARCH_TV_TYPE.ATV.ordinal());
            } else {
                updateADTvTypeContent(newSearchMode);
                spinner_adtv_type.setEnabled(true);
                spinner_adtv_type.setSelection(mSearchTvType.ordinal());
            }
        }

        private String getAntennaType() {
            return mDataManager.getStringParameters(DataManager.KEY_ISDB_ANTENNA_TYPE);
        }
        private void setAntennaType(int type) {
            String dtvType;
            if (type == 1) {
                // cable
                dtvType = TvContract.Channels.TYPE_ATSC_C;
            } else {
                dtvType = TvContract.Channels.TYPE_ATSC_T;
            }
            enOrDisableADTvType(type, mSearchMode);
            if (type != mAntennaType) {
                mDataManager.saveStringParameters(DataManager.KEY_ISDB_ANTENNA_TYPE, dtvType);
            }
            mAntennaType = type;
        }

        private String getSearchTvType() {
            return getPrefs("ISDB_search_tv_type");
        }
        private void setSearchTvType(SEARCH_TV_TYPE tv_type) {
            if (tv_type == SEARCH_TV_TYPE.ATV) {
                ll_atv_search.setVisibility(View.VISIBLE);
                ll_dtv_search.setVisibility(View.GONE);
            } else if (tv_type == SEARCH_TV_TYPE.DTV) {
                ll_atv_search.setVisibility(View.GONE);
                ll_dtv_search.setVisibility(View.VISIBLE);
            } else {
                ll_atv_search.setVisibility(View.VISIBLE);
                ll_dtv_search.setVisibility(View.VISIBLE);
            }
            updateSearchModeContent(true);
            setPrefs("ISDB_search_tv_type", tv_type.toString());
        }

        private int getSearchMethod() {
            return mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
        }
        private void setSearchMethod(int position) {
            if (position == DataManager.VALUE_FREQUENCY_MODE) {
                ll_manual_number.setVisibility(View.GONE);
                ll_manual_frequency.setVisibility(View.VISIBLE);
            } else {
                ll_manual_frequency.setVisibility(View.GONE);
                ll_manual_number.setVisibility(View.VISIBLE);
                if (spinner_manual_number.getCount() > mChannelNumberI) {
                    spinner_manual_number.setSelection(mChannelNumberI);
                } else {
                    spinner_manual_number.setSelection(0);
                }
            }
            if (position != mSearchMethod) {
                mDataManager.saveIntParameters(DataManager.KEY_IS_FREQUENCY, position);
            }
            mSearchMethod = position;
        }

        private int getChannelNumberI() {
            return mDataManager.getIntParameters(DataManager.KEY_SEARCH_ISDBT_CHANNEL_NAME);
        }
        private void setChannelNumberI(int position) {
            mDataManager.saveIntParameters(DataManager.KEY_SEARCH_ISDBT_CHANNEL_NAME, position);
        }

        private final String PREFS_NAME = "search_pref";
        private String getPrefs(String key){
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            return sp.getString(key, "");
        }

        private void setPrefs(String key, String value){
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            if (value == null) {
                editor.remove(key);
            } else {
                editor.putString(key, value);
            }
            editor.apply();
        }
    }
}
