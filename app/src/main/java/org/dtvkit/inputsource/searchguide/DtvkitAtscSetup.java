package com.droidlogic.dtvkit.inputsource.searchguide;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputService;
import android.media.tv.tuner.Tuner;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.searchguide.DtvkitDvbScanSelect;
import com.droidlogic.dtvkit.inputsource.DtvkitEpgSync;
import com.droidlogic.dtvkit.inputsource.PvrStatusConfirmManager;
import com.droidlogic.dtvkit.inputsource.R;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.settings.ConstantManager;
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

public class DtvkitAtscSetup extends DtvkitActivity {
    private static final String TAG = DtvkitAtscSetup.class.getSimpleName();

    private static final int DTV = 0;
    private static final int ATV = 1;
    private static final int DTV_ATV = 2;
    private static final String ATSC_C = "ATSC-C";
    private static final String ATSC_T = "ATSC-T";
    private static final String ATSC_C_STD = "ATSC-C-STD";
    private static final String ATSC_C_LRC = "ATSC-C-LRC";
    private static final String ATSC_C_HRC = "ATSC-C-HRC";
    private static final String ATSC_C_AUTO = "ATSC-C-AUTO";

    private UIElement UI;
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
            if (status != null
                    && (status.equals(EpgSyncJobService.SYNC_FINISHED)
                        || status.equals(EpgSyncJobService.SYNC_ERROR))) {
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
            Toast.makeText(DtvkitAtscSetup.this, R.string.sync_tv_provider, Toast.LENGTH_SHORT).show();
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
        setContentView(R.layout.atsc_setup);
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
            if (service.has("Unikey")) {
                freq = service.optInt("Freq");
                curATv = true;
            } else {
                freq = service.optInt("freq");
                curATv = false;
            }
            boolean isMatched = UI.mSearchMode != DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL
                    || (freq != 0 && UI.mManualFrequency == freq);
            // when search atv, return atv name or empty
            boolean mustReturnATv = UI.mSearchTvType == ATV;
            if (!curATv && mustReturnATv) {
                continue;
            }
            if (isMatched) {
                if (curATv) {
                    firstServiceName = service.optString("Name");
                    if (firstServiceName.length() == 0) {
                        firstServiceName = String.valueOf(service.optInt("Lcn"));
                    }
                    if (TextUtils.isDigitsOnly(firstServiceName)) {
                        firstServiceName = "Analog" + firstServiceName;
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
        mHandlerThread = new HandlerThread("ATSC_Setup");
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

    private String getScanTvTypeString(int searchTvType) {
        switch (searchTvType) {
            case 0:
                return "DIGITAL";
            case 1:
                return "ANALOG";
            case 2:
                return "ALL";
        }
        return "DIGITAL";
    }

    private int antennaTypeToInt(String antenna) {
        switch (antenna) {
            case ATSC_T:
                return 0;
            case ATSC_C_STD:
                return 1;
            case ATSC_C_LRC:
                return 2;
            case ATSC_C_HRC:
                return 3;
            case ATSC_C_AUTO:
                return 5;
        }
        return 0;
    }

    private String getAntennaTypeFromInt(int type) {
        String[] antennaTypes = {
                ATSC_T, ATSC_C_STD, ATSC_C_LRC, ATSC_C_HRC, ATSC_C_AUTO
        };
        return  type < antennaTypes.length ? antennaTypes[type] : antennaTypes[0];
    }

    private JSONArray getAtscRfChannelTable(String tvType, String antennaType) {
        try {
            JSONArray args = new JSONArray();
            args.put("ALL".equals(tvType) ? "DIGITAL" : tvType);
            args.put(antennaType);

            JSONObject obj =DtvkitGlueClient.getInstance()
                    .request("TvScan.getRfChannelTable", args);
            return obj.optJSONArray("data");
        } catch (Exception ignore) {}
        return null;
    }

    private void updateChannelNameContainer() {
        List<String> newList = new ArrayList<>();
        ArrayAdapter<String> adapter;
        int select = UI.mChannelNumberI;

        JSONArray list =
                getAtscRfChannelTable(getScanTvTypeString(UI.mSearchTvType), UI.mAntennaType);

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

    private void initSearchParameter(JSONArray args) {
        Log.d(TAG, "initSearchParameter autoSearch:" + isAutoSearch()
                + ", antennaType:" + UI.mAntennaType
                + ", SearchTvType:" + UI.mSearchTvType);
        String searchTvType = getScanTvTypeString(UI.mSearchTvType);
        String antennaType = UI.mAntennaType;
        if (isAutoSearch()) {
            args.put(searchTvType);
            args.put(antennaType);
            if ("ANALOG".equals(searchTvType)) {
                args.put(2000000);//afc
            }
            args.put(true);//clear old channels
        } else {
            args.put(searchTvType);
            args.put(antennaType);
            args.put(getChannelIndex());
            args.put(false);
        }
    }

    private int getChannelIndex() {
        int index = -1;
        String chName = (String) UI.spinner_manual_number.getSelectedItem();
        if (!TextUtils.isEmpty(chName)) {
            if (UI.mSearchTvType == ATV) {
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

        startMonitoringSearch();
        String failReason = null;
        JSONArray args = new JSONArray();
        initSearchParameter(args);
        boolean result;
        if (isAutoSearch()) {
            result = doStartAutoSearch(args);
        } else {
            result = doStartManualSearch(args);
        }
        mStartSearch = result;
        if (!result) {
            failReason = "Failed to start search: request error!";
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
            DtvkitGlueClient.getInstance().request("TvScan.finishSearch", args);
        } catch (Exception e) {
            UI.setSearchStatus("Failed to finish search\t" + e.getMessage());
        }
    }

    private boolean doStartAutoSearch(JSONArray args) {
        try {
            DtvkitGlueClient.getInstance().request("TvScan.startAutoSearch", args);
            mParameterManager.saveChannelIdForSource(-1);
        } catch (Exception e) {
            UI.setSearchStatus("Failed to start auto search\t" + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean doStartManualSearch(JSONArray args) {
        try {
            DtvkitGlueClient.getInstance().request("TvScan.startManualSearchById", args);
        } catch (Exception e) {
            UI.setSearchStatus("Failed to start manual search\t" + e.getMessage());
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

        int airCableType = (ATSC_T.equals(UI.mAntennaType)) ? 0: 1;
        try {
            if (UI.mSearchTvType != ATV) {
                String signalType = (ATSC_T.equals(UI.mAntennaType)) ? "atsct" : "atscc";
                JSONArray dTvList = DtvkitEpgSync.getAtscServicesList(signalType, "all");
                DtvkitEpgSync.setServicesToSync(dTvList);
                for (int i = 0; i < dTvList.length(); i++) {
                    mServiceList.put(dTvList.getJSONObject(i));
                }
            }
            if (UI.mSearchTvType != DTV) {
                JSONArray aTvList = DtvkitEpgSync.getAtvServicesList();
                DtvkitEpgSync.setATvServicesToSync(aTvList);
                //EN_ATV_SIG_TYPE_AIR = 0,
                //EN_ATV_SIG_TYPE_CABLE = 1
                for (int i = 0; i < aTvList.length(); i++) {
                    JSONObject item = aTvList.getJSONObject(i);
                    if (item.optInt("SigType") == airCableType) {
                        mServiceList.put(item);
                    }
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
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE,
                DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL != UI.mSearchMode ?
                        EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO :
                        EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL);
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE,
                airCableType == 0 ? ATSC_T : ATSC_C);
        if (UI.mSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL) {
            parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY, String.valueOf(UI.mManualFrequency));
        }
        parameters.putInt(EpgSyncJobService.BUNDLE_KEY_SYNC_HYBRID_MODE, UI.mSearchTvType);

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
            boolean valid = TextUtils.equals("TvStatusChanged", signal);
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
        boolean scanningAtv = "atv".equals(data.optString("started", ""));
        int strengthStatus = mParameterManager.getStrengthStatus();
        int qualityStatus = mParameterManager.getQualityStatus();
        UI.updateSignalInfo(String.format(Locale.US, "Frequency: %.2fMhz Strength: %d\t\tQuality: %d\t\t", (float) result[2] / (1000 * 1000), strengthStatus, qualityStatus));
        UI.setSearchStatus(String.format(Locale.ENGLISH, "Searching (%d%%)", result[0]));
        int[] found = getFoundServiceNumberOnSearch();
        if (scanningAtv) {
            UI.setATvProgress(result[0], found[1]);
        } else {
            UI.setDTvProgress(result[0], found[0]);
        }
        if (result[0] == 100) {
            if (UI.mSearchTvType == DTV || scanningAtv) {
                sendFinishSearch();
                if (UI.mSearchMode == DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL) {
                    UI.mManualFrequency = result[2];
                }
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
        Spinner spinner_search_mode;
        Spinner spinner_antenna_type;
        LinearLayout ll_adtv_type;
        Spinner spinner_adtv_type;
        LinearLayout ll_manual_number;
        Spinner spinner_manual_number;
        Button btn_option;
        Button btn_search;
        // logic code
        private int mSearchMode;
        private int mSearchTvType; // both: 2, dtv: 0, atv: 1
        private String mAntennaType = ATSC_T;
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
            spinner_search_mode = findViewById(R.id.public_search_mode_spinner);
            spinner_antenna_type = findViewById(R.id.antenna_type_spinner);
            spinner_adtv_type = findViewById(R.id.adtv_type_spinner);
            ll_adtv_type = findViewById(R.id.adtv_type_container);
            ll_manual_number = findViewById(R.id.manual_number_search);
            spinner_manual_number = findViewById(R.id.search_chNumber_in);
            btn_option = findViewById(R.id.option_set_btn);
            btn_search = findViewById(R.id.btn_start_search);
        }

        private void setEnabled(boolean enable) {
            spinner_search_mode.setEnabled(enable);
            spinner_antenna_type.setEnabled(enable);
            spinner_adtv_type.setEnabled(enable);
            btn_option.setEnabled(enable);
        }

        private void initOrUpdateView() {
            // data Initialize
            {
                mSearchMode = getSearchMode();
                mSearchTvType = getSearchTvType();
                mAntennaType = getAntennaType();

                //adjust antenna type
                int mwDtvSource = mParameterManager.getCurrentDvbSource();
                int antennaTypeInt = antennaTypeToInt(mAntennaType);
                if (mwDtvSource != ParameterManager.SIGNAL_ATSC_T &&
                        mwDtvSource != ParameterManager.SIGNAL_ATSC_C) {
                    int newDtvSource = (antennaTypeInt == 0) ?
                            ParameterManager.SIGNAL_ATSC_T : ParameterManager.SIGNAL_ATSC_C;
                    mParameterManager.setCurrentDvbSource(newDtvSource);
                } else {
                    //dtv source maybe changed by tuning, follow the source in mw
                    int diff = mwDtvSource - ParameterManager.SIGNAL_ATSC_T - antennaTypeInt;
                    if (mwDtvSource == ParameterManager.SIGNAL_ATSC_T && antennaTypeInt > 0) {
                        antennaTypeInt = 0;
                    } else if (mwDtvSource == ParameterManager.SIGNAL_ATSC_C && antennaTypeInt == 0) {
                        antennaTypeInt = 1;
                    }
                }
                if (antennaTypeInt != antennaTypeToInt(mAntennaType)) {
                    setAntennaType(getAntennaTypeFromInt(antennaTypeInt));
                }
                spinner_search_mode.setSelection(mSearchMode);
                spinner_adtv_type.setSelection(mSearchTvType);
                spinner_antenna_type.setSelection(antennaTypeToInt(mAntennaType));
            }
            // widget
            spinner_search_mode.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_search_mode position = " + position);
                    setSearchMode(position);
                    if (!isAutoSearch()) {
                        updateChannelNameContainer();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            spinner_antenna_type.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_antenna_type position = " + position);
                    setAntennaType(getAntennaTypeFromInt(position));
                    if (!isAutoSearch()) {
                        updateChannelNameContainer();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            spinner_adtv_type.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_adtv_type position = " + position);
                    mSearchTvType = position;
                    setSearchTvType(mSearchTvType);
                    if (!isAutoSearch()) {
                        updateChannelNameContainer();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            spinner_manual_number.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "spinner_manual_number position = " + position);
                    mChannelNumberI = position;
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            btn_option.setOnClickListener(v -> {
                Intent intentSet = new Intent(mIntent);
                String pvrStatus = intentSet.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
                String pvrFlag = PvrStatusConfirmManager.read(DtvkitAtscSetup.this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
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
                        mPvrStatusConfirmManager.showDialogToAppoint(DtvkitAtscSetup.this, autoSearch);
                    } else {
                        if (mStartSearch) {
                            Log.d(TAG, "mAntennaType:" + mAntennaType +
                                    ", mSearchTvType:" + mSearchTvType);
                            sendFinishSearch();
                        } else {
                            mPvrStatusConfirmManager.sendDvrCommand(DtvkitAtscSetup.this);
                            sendStopSearch();
                            sendStartSearch();
                        }
                    }
                }
            });
            btn_search.requestFocus();
        }

        @SuppressLint("SetTextI18n")
        private void setDTvProgress(int progress, int number) {
            runOnUiThread(() -> {
                ((ProgressBar) findViewById(R.id.dtv_search_progress)).setProgress(progress);
                if (number >= 0) {
                    ((TextView) findViewById(R.id.dtv_number)).setText(" DTV: " + number);
                }
            });
        }

        @SuppressLint("SetTextI18n")
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
                    btn_search.setText(R.string.strStopSearch);
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
            return mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE);
        }

        private void setSearchMode(int mode) {
            if (mode == DataManager.VALUE_PUBLIC_SEARCH_MODE_MANUAL) {
                ll_manual_number.setVisibility(View.VISIBLE);
                btn_search.setText(R.string.strManualSearch);
            } else {
                ll_manual_number.setVisibility(View.GONE);
                btn_search.setText(R.string.strStartSearch);
            }
            if (mode != mSearchMode) {
                mDataManager.saveIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE, mode);
            }
            mSearchMode = mode;
        }

        private String getAntennaType() {
            String val = mDataManager.getStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM);
            if (ATSC_T.equals(val)) {
                return val;
            } else if (ATSC_C.equals(val)) {
                return mDataManager.getStringParameters(ParameterManager.TV_KEY_TV_SEARCH_TYPE);
            } else {
                val = ATSC_T;
                mDataManager.saveStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM, ATSC_T);
            }
            return val;
        }

        private void setAntennaType(String type) {
            if (!TextUtils.equals(type, mAntennaType)) {
                if (type.equals(ATSC_T)) {
                    mDataManager.saveStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM, ATSC_T);
                } else {
                    mDataManager.saveStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM, ATSC_C);
                    mDataManager.saveStringParameters(ParameterManager.TV_KEY_TV_SEARCH_TYPE, type);
                }
            }
            mAntennaType = type;
        }

        private int getSearchTvType() {
            String val = mDataManager.getPrefs("ATSC_atv_dtv_flag");
            if (TextUtils.isEmpty(val)) {
                return DTV;
            } else {
                return Integer.parseInt(val);
            }
        }

        private void setSearchTvType(int tv_type) {
            if (tv_type == ATV) {
                ll_atv_search.setVisibility(View.VISIBLE);
                ll_dtv_search.setVisibility(View.GONE);
            } else if (tv_type == DTV) {
                ll_atv_search.setVisibility(View.GONE);
                ll_dtv_search.setVisibility(View.VISIBLE);
            } else {
                ll_atv_search.setVisibility(View.VISIBLE);
                ll_dtv_search.setVisibility(View.VISIBLE);
            }
            mDataManager.setPrefs("ATSC_atv_dtv_flag", String.valueOf(tv_type));
        }
    }
}
