package com.droidlogic.dtvkit.inputsource;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.droidlogic.app.DataProviderManager;
import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.DtvkitEpgSync;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.settings.ConstantManager;
import com.droidlogic.settings.PropSettingManager;
import org.dtvkit.inputsource.fvp.ClmDialogFragment;
import org.dtvkit.inputsource.fvp.ClmManager;
import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import com.droidlogic.dtvkit.inputsource.util.FeatureUtil;
import droidlogic.dtvkit.tuner.TunerAdapter;

public class DtvkitDvbtSetup extends Activity {
    private static final String TAG = "DtvkitDvbtSetup";

    private boolean mIsDvbt = false;
    private DataManager mDataManager;
    private ParameterManager mParameterManager = null;
    private boolean mStartSync = false;
    private boolean mStartSearch = false;
    private boolean mSyncFinish = false;
    private boolean mFinish = false;
    private boolean mInAutomaticMode = false;
    private JSONArray mServiceList = null;
    private int mFoundServiceNumber = 0;
    private int mSearchManualAutoType = -1;// 0 manual 1 auto
    private int mSearchDvbcDvbtType = -1;
    private PvrStatusConfirmManager mPvrStatusConfirmManager = null;
    private DtvKitScanSignalPresenter mDtvKitScanSignalPresenter = null;

    private AutoNumberEditText mDvbcNitAutoEdit = null;
    private AutoNumberEditText mDvbcSymAutoEdit = null;
    private AutoNumberEditText mDvbcFreqAutoEdit = null;
    private String[] DVBC_AUTO_SCANTYPE = {"network", "quick", "full", "blind"};

    protected HandlerThread mHandlerThread = null;
    protected Handler mThreadHandler = null;

    private final static int MSG_START_SEARCH = 1;
    private final static int MSG_STOP_SEARCH = 2;
    private final static int MSG_FINISH_SEARCH = 3;
    private final static int MSG_ON_SIGNAL = 4;
    private final static int MSG_FINISH = 5;
    private final static int MSG_RELEASE= 6;
    private final static int MSG_START_BY_AUTOMATIC_MODE = 7;

    private long clickLastTime = 0;
    private JSONArray mChannelFreqTable = null;
    private int isFrequencyMode = 0;
    private boolean mUpdateSearchFlag = false;
    private TunerAdapter mTunerAdapter = null;

    private final DtvkitGlueClient.SignalHandler mHandler = new DtvkitGlueClient.SignalHandler() {
        @Override
        public void onSignal(String signal, JSONObject data) {
            Map<String, Object> map = new HashMap<String,Object>();
            map.put("signal", signal);
            map.put("data", data);
            sendOnSignal(map);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            String status = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            if (status.equals(EpgSyncJobService.SYNC_FINISHED)
                    || status.equals(EpgSyncJobService.SYNC_ERROR)) {
                setSearchStatus("Finished", "");
                setStrengthAndQualityStatus("","");
                mStartSync = false;
                mSyncFinish = true;
                sendFinish();
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mStartSync) {
            Toast.makeText(DtvkitDvbtSetup.this, R.string.sync_tv_provider, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mStartSearch) {
                //onSearchFinished();
                sendFinishSearch(false);
                mStartSync = true;
                return true;
            } else {
                stopMonitoringSearch();
                //stopSearch();
                sendStopSearch();
                //finish();
                sendFinish();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auto_setup);
        Intent cIntent = getIntent();
        final String inputId;
        final String pvrStatus;
        if (cIntent != null) {
            inputId = cIntent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
            pvrStatus = cIntent.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
        } else {
            inputId = null;
            pvrStatus = null;
        }

        /************For tuner framework***************/
        if (FeatureUtil.getFeatureSupportTunerFramework()) {
            Tuner tuner = new Tuner(this, null, TvInputService.PRIORITY_HINT_USE_CASE_TYPE_SCAN);
            mTunerAdapter = new TunerAdapter(tuner, TunerAdapter.TUNER_TYPE_SCAN);
        }

        mParameterManager = new ParameterManager(this, DtvkitGlueClient.getInstance());
        final Button optionSet = findViewById(R.id.option_set_btn);
        optionSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentSet = new Intent();
                if (inputId != null) {
                    intentSet.putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId);
                }
                String pvrFlag = PvrStatusConfirmManager.read(DtvkitDvbtSetup.this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
                if (pvrStatus != null && PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(pvrFlag)) {
                    intentSet.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, pvrStatus);
                } else {
                    intentSet.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, "");
                }
                intentSet.setClassName(DataManager.KEY_PACKAGE_NAME, DataManager.KEY_ACTIVITY_SETTINGS);
                startActivity(intentSet);
                mDataManager.saveIntParameters(DataManager.KEY_SELECT_SEARCH_ACTIVITY, DataManager.SELECT_SETTINGS);
            }
        });
        final Button startSearch = findViewById(R.id.btn_terrestrial_start_search);
        startSearch.setEnabled(true);
        startSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long currentTime = SystemClock.elapsedRealtime();
                if (currentTime - clickLastTime > 500) {
                    clickLastTime = currentTime;
                    if (!mInAutomaticMode) {
                        if (mStartSearch) {
                            sendFinishSearch(false);
                        } else {
                            startSearch.setText(R.string.strStopSearch);
                            onStartSearchClick();
                            if (null != mDtvKitScanSignalPresenter) {
                                mDtvKitScanSignalPresenter.stopMonitorSignal();
                            }
                        }
                    }
                }
            }
        });
        startSearch.requestFocus();
        mDataManager = new DataManager(this);
        mPvrStatusConfirmManager = new PvrStatusConfirmManager(this, mDataManager);
        Intent intent = getIntent();
        if (intent != null) {
            mIsDvbt = intent.getBooleanExtra(DataManager.KEY_IS_DVBT, false);
            String status = intent.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
            mPvrStatusConfirmManager.setPvrStatus(status);
            mInAutomaticMode = intent.getBooleanExtra(DataManager.KEY_START_SCAN_FOR_AUTOMATIC, false);
            if (mInAutomaticMode) {
                mDataManager.saveIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE, DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO);
            }
            Log.d(TAG, "onCreate mIsDvbt = " + mIsDvbt + ", status = " + status + ", isAutomaticMode= " + mInAutomaticMode);
        }
        ((TextView)findViewById(R.id.dvb_search)).setText(mIsDvbt ? R.string.strSearchDvbtDescription : R.string.strSearchDvbcDescription);
        initHandler();
        if (mInAutomaticMode) {
            if (mThreadHandler != null) {
                mThreadHandler.sendEmptyMessageDelayed(MSG_START_BY_AUTOMATIC_MODE, 2000);
            }
        }

        if (!isPipOrFccEnable()) {
            mDtvKitScanSignalPresenter = new DtvKitScanSignalPresenter(mParameterManager, mDataManager, mIsDvbt);
            mDtvKitScanSignalPresenter.registerUpdateView(new DtvKitScanSignalPresenter.UpdateView(){
                @Override
                public void updateSignalView(int strength, int quality){
                    setStrengthAndQualityStatus(String.format(Locale.ENGLISH, "Strength: %d%%", strength), String.format(Locale.ENGLISH, "Quality: %d%%", quality));
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mPvrStatusConfirmManager.registerCommandReceiver();
        initOrUpdateView(true);
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
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_TYPE_MANUAL_AUTO, mSearchManualAutoType);
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_TYPE_DVBS_DVBT_DVBC, mSearchDvbcDvbtType);
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_FOUND_SERVICE_NUMBER, mFoundServiceNumber);
        if (mFoundServiceNumber > 0) {
            String firstServiceName = "";
            try {
                if (mServiceList != null && mServiceList.length() > 0) {
                    firstServiceName = mServiceList.getJSONObject(0).getString("name");
                    String freq = "";
                    if (mSearchManualAutoType == 0) {
                        int isFrequencySearch = mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
                        freq = (DataManager.VALUE_FREQUENCY_MODE == isFrequencySearch) ? (getParameter() + "000") : getFreqOfChannelId();
                    }
                    Log.e(TAG, "finish freq = " + freq);
                    for (int i = 0;i < mServiceList.length();i++) {
                        if (mServiceList.getJSONObject(i).getBoolean("hidden") == false && (TextUtils.isEmpty(freq) || freq.equals(mServiceList.getJSONObject(i).getString("freq")))) {
                            firstServiceName = mServiceList.getJSONObject(i).getString("name");
                            break;
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "finish JSONException = " + e.getMessage());
            }
            intent.putExtra(DtvkitDvbScanSelect.SEARCH_FOUND_FIRST_SERVICE, firstServiceName);
            if (mParameterManager != null) {
                intent.putExtra(DtvkitDvbScanSelect.SEARCH_FOUND_LCN_STATE, mParameterManager.getAutomaticOrderingEnabled());
            }
            Log.d(TAG, "finish firstServiceName = " + firstServiceName);
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED, mSyncFinish ? intent : null);
        }
        sendScanFinishBroadcast();
        if (null != mTunerAdapter) {
            mTunerAdapter.release();
            mTunerAdapter = null;
        }
        super.finish();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (mStartSearch) {
            //onSearchFinished();
            sendFinishSearch(true);
        } else if (!mFinish) {
            stopMonitoringSearch();
            //stopSearch();
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
        if (null != mDtvKitScanSignalPresenter) {
            mDtvKitScanSignalPresenter.releaseSignalCheckResource();
        }
    }

    private void initHandler() {
        Log.d(TAG, "initHandler");
        mHandlerThread = new HandlerThread("DtvkitDvbtSetup");
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
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
                        prepareSearchFinished(msg.arg1 == 1);
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
                    case MSG_START_BY_AUTOMATIC_MODE: {
                        onStartSearchClick();
                        break;
                    }
                    default:
                        break;
                }
                Log.d(TAG, "mThreadHandler handleMessage " + msg.what + " over");
                return true;
            }
        });
    }

    private void onStartSearchClick() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int searchMode = mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE);
                boolean autoSearch = (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == searchMode);
                mPvrStatusConfirmManager.setSearchType(autoSearch ? ConstantManager.KEY_DTVKIT_SEARCH_TYPE_AUTO : ConstantManager.KEY_DTVKIT_SEARCH_TYPE_MANUAL);
                boolean checkPvr = mPvrStatusConfirmManager.needDeletePvrRecordings();
                if (checkPvr) {
                    mPvrStatusConfirmManager.showDialogToAppoint(DtvkitDvbtSetup.this, autoSearch);
                } else {
                    mPvrStatusConfirmManager.sendDvrCommand(DtvkitDvbtSetup.this);
                    sendStartSearch();
                }
            }
        });
    }

    private void sendRelease() {
        if (mThreadHandler != null) {
            mThreadHandler.removeMessages(MSG_RELEASE);
            Message mess = mThreadHandler.obtainMessage(MSG_RELEASE, 0, 0, null);
            boolean info = mThreadHandler.sendMessageDelayed(mess, 0);
            Log.d(TAG, "sendMessage MSG_RELEASE " + info);
        }
    }

    private void releaseInThread() {
        Log.d(TAG, "releaseInThread");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "releaseInThread start");
                releaseHandler();
                Log.d(TAG, "releaseInThread end");
            }
        }).start();
    }

    private void releaseHandler() {
        Log.d(TAG, "releaseHandler");
        mHandlerThread.getLooper().quitSafely();
        mThreadHandler.removeCallbacksAndMessages(null);
        mHandlerThread = null;
        mThreadHandler = null;
    }

    private void initOrUpdateView(boolean init) {
        LinearLayout public_type_in_container = (LinearLayout)findViewById(R.id.public_type_in_container);
        TextView dvb_search = (TextView)findViewById(R.id.dvb_search);
        View channel_holder = findViewById(R.id.channel_holder);
        TextView public_type_in = (TextView)findViewById(R.id.public_type_in_text);
        EditText public_type_edit = (EditText)findViewById(R.id.edtTxt_public_type_in);
        LinearLayout dvbt_bandwidth_container = (LinearLayout)findViewById(R.id.dvbt_bandwidth_container);
        Spinner dvbt_bandwidth_spinner = (Spinner)findViewById(R.id.dvbt_bandwidth_spinner);
        LinearLayout dvbt_mode_container = (LinearLayout)findViewById(R.id.dvbt_mode_container);
        Spinner dvbt_mode_spinner = (Spinner)findViewById(R.id.dvbt_mode_spinner);
        LinearLayout dvbt_type_container = (LinearLayout)findViewById(R.id.dvbt_type_container);
        Spinner dvbt_type_spinner = (Spinner)findViewById(R.id.dvbt_type_spinner);
        LinearLayout dvbc_mode_container = (LinearLayout)findViewById(R.id.dvbc_mode_container);
        Spinner dvbc_mode_spinner = (Spinner)findViewById(R.id.dvbc_mode_spinner);
        LinearLayout dvbc_symbol_container = (LinearLayout)findViewById(R.id.dvbc_symbol_container);
        EditText dvbc_symbol_edit = (EditText)findViewById(R.id.dvbc_symbol_edit);
        LinearLayout public_search_mode_container = (LinearLayout)findViewById(R.id.public_search_mode_container);
        Spinner public_search_mode_spinner = (Spinner)findViewById(R.id.public_search_mode_spinner);
        LinearLayout frequency_channel_container = (LinearLayout)findViewById(R.id.frequency_channel_container);
        Spinner frequency_channel_spinner = (Spinner)findViewById(R.id.frequency_channel_spinner);
        LinearLayout public_search_channel_name_container = (LinearLayout)findViewById(R.id.public_search_channel_container);
        Spinner public_search_channel_name_spinner = (Spinner)findViewById(R.id.public_search_channel_spinner);
        Button search = (Button)findViewById(R.id.btn_terrestrial_start_search);
        CheckBox nit = (CheckBox)findViewById(R.id.network);
        CheckBox checkBoxLcn = (CheckBox)findViewById(R.id.chk_lcn_switch);
        LinearLayout dvbc_operator_container = (LinearLayout)findViewById(R.id.dvbc_operator_container);
        LinearLayout dvbc_auto_scan_type_container = (LinearLayout)findViewById(R.id.dvbc_auto_scan_type_container);
        LinearLayout dvbc_networkid_container = (LinearLayout)findViewById(R.id.dvbc_network_id_container);
        LinearLayout dvbc_frequency_container = (LinearLayout) findViewById(R.id.dvbc_frequency_container);
        Spinner operator_spinner = (Spinner) findViewById(R.id.dvbc_operator_spinner);
        Spinner dvbc_auto_scan_type_spinner = (Spinner) findViewById(R.id.dvbc_auto_scan_type_spinner);
        EditText dvbc_networkid_editText = (EditText) findViewById(R.id.dvbc_networkid_edit);
        EditText dvbc_frequency_editText = (EditText) findViewById(R.id.dvbc_frequency_edit);

        isFrequencyMode = mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
        if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
            public_type_in.setText(R.string.search_frequency);
            public_type_edit.setHint(R.string.search_frequency_hint);
            public_type_in_container.setVisibility(View.VISIBLE);
            public_search_channel_name_container.setVisibility(View.GONE);
        } else {
            //public_type_in.setText(R.string.search_number);
            //public_type_edit.setHint(R.string.search_number_hint);//not needed
            public_type_in_container.setVisibility(View.GONE);
            public_search_channel_name_container.setVisibility(View.VISIBLE);
            updateChannelNameContainer();
        }
        public_type_edit.setText("");
        int value = mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE);
        public_search_mode_spinner.setSelection(value);
        nit.setVisibility(View.GONE);//dvbt,dvbc not use this any more
        if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == value) {
            if (mIsDvbt) {
                dvbc_mode_container.setVisibility(View.GONE);
                dvbc_symbol_container.setVisibility(View.GONE);
                frequency_channel_container.setVisibility(View.GONE);
                public_search_channel_name_container.setVisibility(View.GONE);
            } else {
                //dvbc_operator_container.setVisibility(View.VISIBLE);
                dvbc_auto_scan_type_container.setVisibility(View.VISIBLE);
                int autoScanTypePos = dvbc_auto_scan_type_spinner.getSelectedItemPosition();
                int visibility = View.GONE;
                if (autoScanTypePos < 2) {
                    visibility = View.VISIBLE;
                }
                dvbc_mode_container.setVisibility(View.GONE);
                dvbc_mode_spinner.setSelection(getResources().getStringArray(R.array.dvbc_mode_entries).length - 1);
                dvbc_networkid_container.setVisibility(visibility);
                dvbc_symbol_container.setVisibility(View.GONE);
                dvbc_frequency_container.setVisibility(visibility);
            }
            if (mParameterManager.checkIsFvpUkCountry()) {
                checkBoxLcn.setVisibility(View.GONE);
                checkBoxLcn.setChecked(true);
                nit.setText("Update Search");
                nit.setVisibility(View.VISIBLE);
                nit.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (nit.isChecked()) {
                            mUpdateSearchFlag = true;
                        } else {
                            mUpdateSearchFlag = false;
                        }
                    }
                });
            } else {
                checkBoxLcn.setVisibility(View.VISIBLE);
                checkBoxLcn.setChecked(mParameterManager.getAutomaticOrderingEnabled());
            }
            dvbt_bandwidth_container.setVisibility(View.GONE);
            dvbt_mode_container.setVisibility(View.GONE);
            dvbt_type_container.setVisibility(View.GONE);
            dvbc_operator_container.setVisibility(View.GONE);
            frequency_channel_container.setVisibility(View.GONE);
            public_search_channel_name_container.setVisibility(View.GONE);
            public_type_in_container.setVisibility(View.GONE);
            search.setText(R.string.strAutoSearch);
            if (!isPipOrFccEnable()) {
                findViewById(R.id.channel_holder).setVisibility(View.GONE);
                if (null != mDtvKitScanSignalPresenter) {
                    mDtvKitScanSignalPresenter.stopMonitorSignal();
                }
            }
        } else {
            if (!isPipOrFccEnable()) {
                findViewById(R.id.channel_holder).setVisibility(View.VISIBLE);
                if (null != mDtvKitScanSignalPresenter) {
                    mDtvKitScanSignalPresenter.startMonitorSignal();
                }
            }
            search.setText(R.string.strManualSearch);
            if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
                public_type_in_container.setVisibility(View.VISIBLE);
                public_search_channel_name_container.setVisibility(View.GONE);
            } else {
                public_type_in_container.setVisibility(View.GONE);
                public_search_channel_name_container.setVisibility(View.VISIBLE);
            }
            frequency_channel_container.setVisibility(View.VISIBLE);
            frequency_channel_spinner.setSelection(mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY));
            if (mIsDvbt) {
                dvbc_symbol_container.setVisibility(View.GONE);
                dvbc_mode_container.setVisibility(View.GONE);
                dvbt_bandwidth_container.setVisibility(View.VISIBLE);
                dvbt_mode_container.setVisibility(View.VISIBLE);
                dvbt_type_spinner.setSelection(mDataManager.getIntParameters(DataManager.KEY_DVBT_TYPE));
                if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
                    dvbt_type_container.setVisibility(View.VISIBLE);
                    dvbt_bandwidth_spinner.setEnabled(true);
                    dvbt_mode_spinner.setEnabled(true);
                    dvbt_bandwidth_spinner.setSelection(mDataManager.getIntParameters(DataManager.KEY_DVBT_BANDWIDTH));
                    dvbt_mode_spinner.setSelection(mDataManager.getIntParameters(DataManager.KEY_DVBT_MODE));
                } else {
                    dvbt_type_container.setVisibility(View.GONE);
                    dvbt_bandwidth_spinner.setEnabled(false);
                    dvbt_mode_spinner.setEnabled(false);
                    dvbt_bandwidth_spinner.setSelection(getParamIndexFromChannelTable(
                        public_search_channel_name_spinner.getSelectedItemPosition(), "bandWidth"));
                    dvbt_mode_spinner.setSelection(getParamIndexFromChannelTable(
                        public_search_channel_name_spinner.getSelectedItemPosition(), "mode"));
                }
            } else {
                dvbt_bandwidth_container.setVisibility(View.GONE);
                dvbt_mode_container.setVisibility(View.GONE);
                dvbt_type_container.setVisibility(View.GONE);
                dvbc_symbol_container.setVisibility(View.VISIBLE);
                dvbc_mode_container.setVisibility(View.VISIBLE);
                if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
                    dvbc_mode_spinner.setEnabled(true);
                    dvbc_mode_spinner.setSelection(mDataManager.getIntParameters(DataManager.KEY_DVBC_MODE));
                    //dvbc_symbol_edit.setText(mDataManager.getIntParameters(DataManager.KEY_DVBC_SYMBOL_RATE) + "");
                } else {
                    dvbc_mode_spinner.setEnabled(false);
                    dvbc_mode_spinner.setSelection(getParamIndexFromChannelTable(
                        public_search_channel_name_spinner.getSelectedItemPosition(), "modulation"));
                }
            }
            checkBoxLcn.setVisibility(View.GONE);
            dvbc_operator_container.setVisibility(View.GONE);
            dvbc_auto_scan_type_container.setVisibility(View.GONE);
            dvbc_networkid_container.setVisibility(View.GONE);
            dvbc_frequency_container.setVisibility(View.GONE);
        }
        if (init) {//init one time
            dvbt_bandwidth_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "dvbt_bandwidth_spinner onItemSelected position = " + position);
                    if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
                        mDataManager.saveIntParameters(DataManager.KEY_DVBT_BANDWIDTH, position);
                        trylockForScanParaChange();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            dvbt_mode_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "dvbt_mode_spinner onItemSelected position = " + position);
                    if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
                        mDataManager.saveIntParameters(DataManager.KEY_DVBT_MODE, position);
                        trylockForScanParaChange();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            dvbt_type_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (mDataManager.getIntParameters(DataManager.KEY_DVBT_TYPE) == position) {
                        Log.d(TAG, "dvbt_type_spinner same position = " + position);
                        return;
                    }
                    Log.d(TAG, "dvbt_type_spinner onItemSelected position = " + position);
                    mDataManager.saveIntParameters(DataManager.KEY_DVBT_TYPE, position);
                    initOrUpdateView(false);
                    trylockForScanParaChange();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            dvbc_mode_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "dvbc_mode_spinner onItemSelected position = " + position);
                    if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
                        mDataManager.saveIntParameters(DataManager.KEY_DVBC_MODE, position);
                        trylockForScanParaChange();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            public_search_mode_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE)) {
                        Log.d(TAG, "public_search_mode_spinner select same position = " + position);
                        return;
                    }
                    Log.d(TAG, "public_search_mode_spinner onItemSelected position = " + position);
                    mDataManager.saveIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE, position);
                    initOrUpdateView(false);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            frequency_channel_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY)) {
                        Log.d(TAG, "frequency_channel_container select same position = " + position);
                        return;
                    }
                    Log.d(TAG, "frequency_channel_container onItemSelected position = " + position);
                    mDataManager.saveIntParameters(DataManager.KEY_IS_FREQUENCY, position);
                    isFrequencyMode = position;
                    initOrUpdateView(false);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            public_search_channel_name_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "public_search_channel_name_spinner onItemSelected position = " + position);
                    if (mIsDvbt) {
                        mDataManager.saveIntParameters(DataManager.KEY_SEARCH_DVBT_CHANNEL_NAME, position);
                        dvbt_bandwidth_spinner.setSelection(getParamIndexFromChannelTable(position, "bandWidth"));
                        dvbt_mode_spinner.setSelection(getParamIndexFromChannelTable(position, "mode"));
                    } else {
                        mDataManager.saveIntParameters(DataManager.KEY_SEARCH_DVBC_CHANNEL_NAME, position);
                        dvbc_mode_spinner.setSelection(getParamIndexFromChannelTable(position, "modulation"));
                        mDvbcSymAutoEdit.updateDefault(getParamIndexFromChannelTable(position, "symbol"));
                    }
                    trylockForScanParaChange();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // TODO Auto-generated method stub
                }
            });
            checkBoxLcn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (checkBoxLcn.isChecked()) {
                        mParameterManager.setAutomaticOrderingEnabled(true);
                    } else {
                        mParameterManager.setAutomaticOrderingEnabled(false);
                    }
                }
            });
            public_type_edit.addTextChangedListener (new TextWatcher(){
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (null != s) {
                        Log.d(TAG, "frequency afterTextChanged = " + s.toString());
                        trylockForScanParaChange();
                    }
                }
            });
            if (!mIsDvbt) {
                dvbc_auto_scan_type_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        initOrUpdateView(false);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                try {
                    InvalidEditInputListener listener = new InvalidEditInputListener() {
                        @Override
                        public void onInputInvalid() {
                            Toast.makeText(DtvkitDvbtSetup.this, "Invalid input numbers!", Toast.LENGTH_SHORT).show();
                        }
                    };
                    mDvbcNitAutoEdit = new AutoNumberEditText(dvbc_networkid_editText,
                            0, 1, 99999, "", listener);
                    mDvbcSymAutoEdit = new AutoNumberEditText(dvbc_symbol_edit,
                            0, 1, 10000, "", listener);
                    mDvbcFreqAutoEdit = new AutoNumberEditText(dvbc_frequency_editText,
                            0, 44, 870, "MHz", listener);
                } catch (Exception e) {
                }
                JSONArray operatorList = mParameterManager.getOperatorsTypeList(ParameterManager.SIGNAL_QAM);
                List<String> operatorStrList = new ArrayList<>();
                try {
                    if (operatorList != null && operatorList.length() > 0) {
                        for (int i = 0; i < operatorList.length(); i++) {
                            JSONObject operator = (JSONObject) operatorList.get(i);
                            if (operator != null) {
                                String name = operator.optString("operators_name");
                                if (!TextUtils.isEmpty(name)) {
                                    operatorStrList.add(name);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                }
                if (operatorStrList.size() > 0) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, operatorStrList);
                    operator_spinner.setAdapter(adapter);
                    operator_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                            updateDvbcOperator(i);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> adapterView) {
                        }
                    });
                }
                if (mInAutomaticMode) {
                    dvbc_auto_scan_type_spinner.setSelection(2);
                }
                dvbc_symbol_edit.addTextChangedListener (new TextWatcher(){
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (null != s) {
                            Log.d(TAG, "dvbc_symbol_edit afterTextChanged = " + s.toString());
                            trylockForScanParaChange();
                        }
                    }
                });
            }
        }
        if (!mIsDvbt) {
            if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == value) {
                updateDvbcOperator(operator_spinner.getSelectedItemPosition());
                if (operator_spinner.getAdapter() != null) {
                    dvbc_operator_container.setVisibility(View.VISIBLE);
                }
            } else {
                if (isFrequencyMode == DataManager.VALUE_FREQUENCY_MODE) {
                    mDvbcSymAutoEdit.setEnabled(true);
                    mDvbcSymAutoEdit.updateDefault(mDataManager.getIntParameters(DataManager.KEY_DVBC_SYMBOL_RATE));
                } else {
                    mDvbcSymAutoEdit.setEnabled(false);
                    mDvbcSymAutoEdit.updateDefault(getParamIndexFromChannelTable(
                        public_search_channel_name_spinner.getSelectedItemPosition(), "symbol"));
                }
            }
        }
    }

    private void updateDvbcOperator(int index) {
        JSONArray operatorList = mParameterManager.getOperatorsTypeList(ParameterManager.SIGNAL_QAM);
        if (operatorList != null && operatorList.length() > index) {
            JSONObject operator = operatorList.optJSONObject(index);
            if (operator != null) {
                int networkID = operator.optInt("Networkid", 0);
                int symbolRate = operator.optInt("SymbolRate", 0);
                int freq = operator.optInt("Frequency", 0);
                freq = freq / 1000;
                updateDvbcOperator(networkID, symbolRate, freq);
            } else {
                updateDvbcOperator(0, 0, 0);
            }
        }
    }

    private void updateDvbcOperator(int networkid, int symbolRate, int freq) {
        if (mDvbcNitAutoEdit != null) {
            mDvbcNitAutoEdit.updateDefault(networkid);
        }
        if (mDvbcSymAutoEdit != null) {
            mDvbcSymAutoEdit.updateDefault(symbolRate);
        }
        if (mDvbcFreqAutoEdit != null) {
            mDvbcFreqAutoEdit.updateDefault(freq);
        }
    }

    private JSONArray initDvbcScanParamsEx() {
        Spinner dvbc_auto_scan_type_spinner = (Spinner) findViewById(R.id.dvbc_auto_scan_type_spinner);
        String scanType = DVBC_AUTO_SCANTYPE[dvbc_auto_scan_type_spinner.getSelectedItemPosition()];
        Spinner operator_spinner = (Spinner) findViewById(R.id.dvbc_operator_spinner);
        ArrayAdapter<String> operatorAdpater = (ArrayAdapter<String>) operator_spinner.getAdapter();
        String operator = "";
        if (operatorAdpater != null) {
            operator = (String) operatorAdpater.getItem(operator_spinner.getSelectedItemPosition());

            JSONArray operatorList = mParameterManager.getOperatorsTypeList(ParameterManager.SIGNAL_QAM);
            try {
                if (operatorList != null && operatorList.length() > 0) {
                    for (int i = 0; i < operatorList.length(); i++) {
                        JSONObject object = (JSONObject) operatorList.get(i);
                        if (object != null) {
                            if (object.optString("operators_name").equals(operator))
                            {
                                JSONArray args = new JSONArray();
                                args.put(ParameterManager.SIGNAL_QAM);
                                args.put(object.getInt("operators_value"));
                                DtvkitGlueClient.getInstance().request("Dvb.SetOperatorsType", args);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        JSONArray array = new JSONArray();
        array.put(scanType);
        array.put(operator);
        array.put(true);//retune, clear db
        array.put(DataManager.VALUE_DVBC_MODE_LIST[isAutoSearch() ? 5 : mDataManager.getIntParameters(DataManager.KEY_DVBC_MODE)]);
        if (mDvbcNitAutoEdit != null) {
            array.put(mDvbcNitAutoEdit.getValue());
        } else {
            array.put(0);
        }
        if (mDvbcFreqAutoEdit != null) {
            array.put(mDvbcFreqAutoEdit.getValue() * 1000 *1000);//hz
        } else {
            array.put(0);
        }
        if (mDvbcSymAutoEdit != null) {
            array.put(mDvbcSymAutoEdit.getValue());
        } else {
            array.put(0);
        }
        return array;
    }

    private void updateChannelNameContainer() {
        LinearLayout public_search_channel_name_container = (LinearLayout)findViewById(R.id.public_search_channel_container);
        Spinner public_search_channel_name_spinner = (Spinner)findViewById(R.id.public_search_channel_spinner);

        List<String> newList = new ArrayList<String>();
        ArrayAdapter<String> adapter = null;
        int select = mIsDvbt ? mDataManager.getIntParameters(DataManager.KEY_SEARCH_DVBT_CHANNEL_NAME) :
                mDataManager.getIntParameters(DataManager.KEY_SEARCH_DVBC_CHANNEL_NAME);
        mChannelFreqTable = mParameterManager.getChannelTable(mParameterManager.getCurrentCountryCode(), mIsDvbt, mDataManager.getIntParameters(DataManager.KEY_DVBT_TYPE) == 1);
        if (mChannelFreqTable == null || mChannelFreqTable.length() == 0) {
            Log.d(TAG, "updateChannelNameContainer can't find channel freq table");
            return;
        }
        try {
            for (int i = 0; i < mChannelFreqTable.length(); i++) {
                JSONObject channelTable = (JSONObject)mChannelFreqTable.get(i);
                int freq = channelTable.optInt("freq", 0);
                int channelNumber = channelTable.optInt("index", 0);
                String name = channelTable.optString("name", "ch" + channelNumber);
                StringBuilder builder =
                    new StringBuilder("NO." + channelNumber + " " + name + " " + freq + "Hz");
                newList.add(builder.toString());
            }
        } catch (Exception e) {
            Log.d(TAG, "got invalid channel freq table");
        }
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, newList);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        public_search_channel_name_spinner.setAdapter(adapter);
        select = (select < mChannelFreqTable.length()) ? select : 0;
        public_search_channel_name_spinner.setSelection(select);
    }

    private JSONArray initSearchParameter(JSONArray args) {
        if (args != null) {
            if (!(DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE))) {
                String parameter = getParameter();
                if (!TextUtils.isEmpty(parameter)) {
                    int isFrequencySearch = mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
                    if (mIsDvbt) {
                        if (DataManager.VALUE_FREQUENCY_MODE == isFrequencySearch) {
                            args.put(false);//nit not used, default false
                            args.put(Integer.valueOf(parameter) * 1000);//khz to hz
                            args.put(DataManager.VALUE_DVBT_BANDWIDTH_LIST[mDataManager.getIntParameters(DataManager.KEY_DVBT_BANDWIDTH)]);
                            args.put(DataManager.VALUE_DVBT_MODE_LIST[mDataManager.getIntParameters(DataManager.KEY_DVBT_MODE)]);
                            args.put(DataManager.VALUE_DVBT_TYPE_LIST[mDataManager.getIntParameters(DataManager.KEY_DVBT_TYPE)]);
                        } else {
                            parameter = getChannelIndex();
                            if (parameter == null) {
                                Log.d(TAG, "initSearchParameter dvbt search can't find channel index");
                                return null;
                            }
                            args.put(Integer.valueOf(parameter));
                        }
                    } else {
                        if (DataManager.VALUE_FREQUENCY_MODE == isFrequencySearch) {
                            args.put(false);//nit not used, default false
                            args.put(Integer.valueOf(parameter) * 1000);//khz to hz
                            args.put(DataManager.VALUE_DVBC_MODE_LIST[mDataManager.getIntParameters(DataManager.KEY_DVBC_MODE)]);
                            args.put(getUpdatedDvbcSymbolRate());
                        } else {
                            parameter = getChannelIndex();
                            if (parameter == null) {
                                Log.d(TAG, "initSearchParameter dvbc search can't find channel index");
                                return null;
                            }
                            args.put(Integer.valueOf(parameter));
                        }
                    }
                    return args;
                } else {
                    return null;
                }
            } else {
                args.put(false);//nit not used, default false
                return args;
            }
        } else {
            return null;
        }
    }

    private String getParameter() {
        String parameter = null;
        float MaxNumber = 1000.0f;
        float MinNumber = 0.0f;
        float ret = MinNumber;
        EditText public_type_edit = (EditText)findViewById(R.id.edtTxt_public_type_in);
        Editable editable = public_type_edit.getText();
        int isFrequencySearch = mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
        if (DataManager.VALUE_FREQUENCY_MODE != isFrequencySearch) {
            parameter = getChannelIndex();
        } else if (editable != null) {
            try {
            ret = Float.valueOf(editable.toString());
            Log.d(TAG, "search frequency is:"+ret);
            } catch (Exception e) {
            }
            if (ret > MaxNumber || ret <= MinNumber) {
                Toast.makeText(DtvkitDvbtSetup.this, R.string.manual_search_range, Toast.LENGTH_SHORT).show();
                return parameter;
            }

            String value = editable.toString();
            if (!TextUtils.isEmpty(value)/* && TextUtils.isDigitsOnly(value)*/) {
                //float for frequency
                float toFloat = Float.valueOf(value);
                int toInt = (int)(toFloat * 1000.0f);//khz
                parameter = String.valueOf(toInt);
            }
        }

        return parameter;
    }

    private int getUpdatedDvbcSymbolRate() {
        int parameter = DataManager.VALUE_DVBC_SYMBOL_RATE;
        if (mDvbcSymAutoEdit != null) {
            parameter = mDvbcSymAutoEdit.getValue();
        }
        mDataManager.saveIntParameters(DataManager.KEY_DVBC_SYMBOL_RATE, parameter);

        return parameter;
    }

    private String getChannelIndex() {
        String result = null;
        JSONObject channelInfo = null;

        int index = mIsDvbt ? mDataManager.getIntParameters(DataManager.KEY_SEARCH_DVBT_CHANNEL_NAME) :
                mDataManager.getIntParameters(DataManager.KEY_SEARCH_DVBC_CHANNEL_NAME);
        if (mChannelFreqTable == null) {
            JSONArray list = mParameterManager.getChannelTable(mParameterManager.getCurrentCountryCode(), mIsDvbt, mDataManager.getIntParameters(DataManager.KEY_DVBT_TYPE) == 1);
        }

        try {
            channelInfo = (index < mChannelFreqTable.length()) ? (JSONObject)(mChannelFreqTable.get(index)) : null;
        } catch (Exception e) {
        }
        if (channelInfo != null) {
            result = "" + channelInfo.optInt("index");
            Log.d(TAG, "getChannelIndex channel index = " +result +
                ", name = " + channelInfo.optString("name", "ch" +result) +
                ", freq = " + channelInfo.optInt("freq", 0)/1000 + "kHz");
        }
        return result;
    }

    private String getFreqOfChannelId() {
        String result = null;
        JSONObject channelInfo = null;

        int index = mIsDvbt ? mDataManager.getIntParameters(DataManager.KEY_SEARCH_DVBT_CHANNEL_NAME) :
                mDataManager.getIntParameters(DataManager.KEY_SEARCH_DVBC_CHANNEL_NAME);
        if (mChannelFreqTable == null) {
            mChannelFreqTable = mParameterManager.getChannelTable(mParameterManager.getCurrentCountryCode(), mIsDvbt, mDataManager.getIntParameters(DataManager.KEY_DVBT_TYPE) == 1);
        }
        try {
            channelInfo = (index < mChannelFreqTable.length()) ? (JSONObject)(mChannelFreqTable.get(index)) : null;
        } catch (Exception e) {
        }
        if (channelInfo != null) {
            result = "" + channelInfo.optInt("freq");
            Log.d(TAG, "getFreqOfChannelId freq = " +result +
                ", name = " + channelInfo.optString("name", "ch" +result) +
                ", freq = " + channelInfo.optInt("freq", 0)/1000 + "kHz");
        }
        return result;
    }

    private int getParamIndexFromChannelTable(int channelIndex, String param) {
        JSONObject channelTable = null;
        int index = 0;

        if (mChannelFreqTable == null) {
            mChannelFreqTable = mParameterManager.getChannelTable(mParameterManager.getCurrentCountryCode(), mIsDvbt, mDataManager.getIntParameters(DataManager.KEY_DVBT_TYPE) == 1);
        }

        if (mChannelFreqTable == null || mChannelFreqTable.length() == 0 || channelIndex >= mChannelFreqTable.length()) {
            Log.d(TAG, "getParamIndexFroChannelTable can't find channel freq table");
            return index;
        }
        try {
            channelTable = (JSONObject)mChannelFreqTable.get(channelIndex);
            if ("bandWidth".equalsIgnoreCase(param)) {
                String bandWidth = channelTable.optString("bandWidth", "");
                index = bandWidthStrToInt(bandWidth);
            } else if ("mode".equalsIgnoreCase(param)) {
                String transmission = channelTable.optString("mode", "");
                index = transmissionStrToInt(transmission);
            } else if ("modulation".equalsIgnoreCase(param)) {
                String modulation = channelTable.optString("modulation", "");
                index = modulationStrToInt(modulation);
            } else if ("symbol".equalsIgnoreCase(param)) {
                index = channelTable.optInt("symbol", 0);
            }
        } catch (Exception e){
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
        setSearchStatus("Searching", "");
        DataProviderManager.putIntValue(this, ConstantManager.KEY_LCN_STATE, mParameterManager.getAutomaticOrderingEnabled() ? 1 : 0);
        DataProviderManager.putBooleanValue(this,ConstantManager.KEY_IS_SEARCHING, true);
        setSearchProgressIndeterminate(false);
        startMonitoringSearch();
        mFoundServiceNumber = 0;
        updateSearchButton(false);
        try {
            JSONArray args = new JSONArray();
            args.put(false); // Commit
            DtvkitGlueClient.getInstance().request(mIsDvbt ? "Dvbt.finishSearch" : "Dvbc.finishSearch", args);
        } catch (Exception e) {
            setSearchStatus("Failed to finish search", e.getMessage());
            return;
        }

        try {
            JSONArray args = new JSONArray();
            args.put(true); // retune
            args = initSearchParameter(args);
            if (args != null) {
                String command = null;
                int searchMode = mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE);
                int isFrequencySearch = mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
                if (!(DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == searchMode)) {
                    if (isFrequencySearch == DataManager.VALUE_FREQUENCY_MODE) {
                        command = (mIsDvbt ? "Dvbt.startManualSearchByFreq" : "Dvbc.startManualSearchByFreq");
                    } else {
                        command = (mIsDvbt ? "Dvbt.startManualSearchById" : "Dvbc.startManualSearchById");
                    }
                    mSearchManualAutoType = DtvkitDvbScanSelect.SEARCH_TYPE_MANUAL;
                } else {
                    command = (mIsDvbt ? "Dvbt.startSearch" : "Dvbc.startSearchEx");
                    mSearchManualAutoType = DtvkitDvbScanSelect.SEARCH_TYPE_AUTO;
                    if (!mIsDvbt) {
                        args = initDvbcScanParamsEx();
                    }
                }
                if (mIsDvbt) {
                    mSearchDvbcDvbtType = DtvkitDvbScanSelect.SEARCH_TYPE_DVBT;
                } else {
                    mSearchDvbcDvbtType = DtvkitDvbScanSelect.SEARCH_TYPE_DVBC;
                }
                Log.d(TAG, "command = " + command + ", args = " + args.toString());
                DtvkitGlueClient.getInstance().request(command, args);
                mStartSearch = true;
                mParameterManager.saveChannelIdForSource(-1);
            } else {
                stopMonitoringSearch();
                setSearchStatus("parameter not complete", "");
                stopSearch();
            }
        } catch (Exception e) {
            stopMonitoringSearch();
            setSearchStatus("Failed to start search", e.getMessage());
            stopSearch();
        }
    }

    private void sendStopSearch() {
        if (mThreadHandler != null) {
            mThreadHandler.removeMessages(MSG_STOP_SEARCH);
            Message mess = mThreadHandler.obtainMessage(MSG_STOP_SEARCH, 0, 0, null);
            boolean info = mThreadHandler.sendMessageDelayed(mess, 0);
            Log.d(TAG, "sendMessage MSG_STOP_SEARCH " + info);
        }
    }

    private void stopSearch() {
        updateSearchButton(true);
        try {
            JSONArray args = new JSONArray();
            args.put(true); // Commit
            DtvkitGlueClient.getInstance().request(mIsDvbt ? "Dvbt.finishSearch" : "Dvbc.finishSearch", args);
        } catch (Exception e) {
            setSearchStatus("Failed to finish search", e.getMessage());
        } finally {
            mStartSearch = false;
        }
    }

    private void sendFinishSearch(boolean skipConfirmNetwork) {
        if (mThreadHandler != null) {
            mThreadHandler.removeMessages(MSG_FINISH_SEARCH);
            Message mess = mThreadHandler.obtainMessage(MSG_FINISH_SEARCH, skipConfirmNetwork ? 1 : 0, 0, null);
            boolean info = mThreadHandler.sendMessageDelayed(mess, 1000);
            Log.d(TAG, "sendMessage MSG_FINISH_SEARCH " + info);
        }
    }
    private void prepareSearchFinished(boolean skipConfirmNetwork) {
        boolean needSetTargetRegion = false;
        boolean autoSearch = mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE) == DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO;
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
                needSetTargetRegion = true;
            }
        } catch (Exception e) {
            Log.i(TAG,"getTargetRegions error " + e.getMessage());
        }

        ClmManager clmManager = new ClmManager(this);
        if (clmManager.checkNeedIpScan() && autoSearch) {
            Log.i(TAG,"handle ip scan");
            final boolean finalNeedSetTargetRegion = needSetTargetRegion;
            clmManager.addFinishCallback(new ClmManager.ClmFinishCallback() {
                @Override
                public void onClmFinishSuccess() {
                    onSearchFinished(true);
                }

                @Override
                public void onClmFinishFailed(boolean needRevert) {
                    Log.i(TAG,"onClmFinishFailed needRevert : " + needRevert + " finalNeedSetTargetRegion = " + finalNeedSetTargetRegion
                         + " autoSearch :" + autoSearch + " skipConfirmNetwork : " + skipConfirmNetwork);
                    if (needRevert) {
                        if (autoSearch && finalNeedSetTargetRegion && !skipConfirmNetwork) {
                            regionSetup();
                        } else {
                            onSearchFinished(true);
                        }
                    } else {
                        onSearchFinished(true);
                    }
                }
            });
            setSearchStatus("Handle IP Scan", "");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (true == mUpdateSearchFlag) {
                        clmManager.clmHandleStart(ClmManager.INTERACTIVE_RETUNE_SCAN);
                    } else {
                        clmManager.clmHandleStart(ClmManager.FTI_SCAN);
                    }
                }
            });
        } else {
            Log.i(TAG,"not handle ip scan");
            if (autoSearch && needSetTargetRegion && !skipConfirmNetwork) {
                regionSetup();
            } else {
                onSearchFinished(skipConfirmNetwork);
            }
        }

 /*
        if (autoSearch && needSetTargetRegion && !skipConfirmNetwork) {
            final TargetRegionManager regionManager = new TargetRegionManager(DtvkitDvbtSetup.this);
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
                                        regionManager.getRegionCode(regionManager.TARGET_REGION_COUNTRY),
                                        -1, -1);
                            break;
                        case TargetRegionManager.TARGET_REGION_SECONDARY:
                            array = mParameterManager.getTargetRegions(target_id,
                                        regionManager.getRegionCode(regionManager.TARGET_REGION_COUNTRY),
                                        regionManager.getRegionCode(regionManager.TARGET_REGION_PRIMARY),
                                        -1);
                            break;
                        case TargetRegionManager.TARGET_REGION_TERTIARY:
                            array = mParameterManager.getTargetRegions(target_id,
                                        regionManager.getRegionCode(regionManager.TARGET_REGION_COUNTRY),
                                        regionManager.getRegionCode(regionManager.TARGET_REGION_PRIMARY),
                                        regionManager.getRegionCode(regionManager.TARGET_REGION_SECONDARY));
                            break;
                    }
                    if (array != null && array.length() > 0) {
                        JSONObject region = null;
                        String region_name = null;
                        int region_code = -1;
                        for (int i = 0; i < array.length(); i++) {
                            region = mParameterManager.getJSONObjectFromJSONArray(array, i);
                            region_name = mParameterManager.getTargetRegionName(target_id, region);
                            region_code = mParameterManager.getTargetRegionCode(target_id, region);
                            map.put(region_name, region_code);
                        }
                    } else {
                        Log.d(TAG, "No regions for target_id " + target_id);
                    }
                    if (map.size() > 0)
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
                    onSearchFinished(skipConfirmNetwork);
                }
            });
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    regionManager.start(true);
                }
            });

        } else {
            onSearchFinished(skipConfirmNetwork);
        }
  */
    }

    private void onSearchFinished(boolean skipConfirmNetwork) {
        mStartSearch = false;
        runOnUiThread(() -> findViewById(R.id.btn_terrestrial_start_search).setEnabled(false));
        updateSearchButton(true);
        setSearchStatus("Finishing search", "");
        setStrengthAndQualityStatus("","");
        setSearchProgressIndeterminate(true);
        stopMonitoringSearch();

        try {
            JSONArray args = new JSONArray();
            args.put(true); // Commit
            DtvkitGlueClient.getInstance().request(mIsDvbt ? "Dvbt.finishSearch" : "Dvbc.finishSearch", args);
        } catch (Exception e) {
            setSearchStatus("Failed to finish search", e.getMessage());
            return;
        }
        boolean networksNeedUpdate = false;
        boolean lcnConflictNeedUpdate = false;
        boolean autoSearch = mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE) == DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO;
        JSONArray array = mParameterManager.getConflictLcn();
        if (mParameterManager.needConfirmLcnInformation(array)) {
            lcnConflictNeedUpdate = true;
        } else {
            array = mParameterManager.getNetworksOfRegion();
            if (mParameterManager.needConfirmNetWorkInformation(array)) {
                networksNeedUpdate = true;
            }
        }
        if (lcnConflictNeedUpdate) {
            final JSONArray conflictLcnArray = array;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showLcnConflictServiceConfirmDialog(DtvkitDvbtSetup.this, conflictLcnArray);
                }
            });
        } else if (autoSearch && !skipConfirmNetwork && networksNeedUpdate) {
            final JSONArray networkArray = array;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showNetworkInfoConfirmDialog(DtvkitDvbtSetup.this, networkArray);
                }
            });
        } else {
            updateChannelList();
        }
        DataProviderManager.putBooleanValue(this,ConstantManager.KEY_IS_SEARCHING, false);
    }

    private void updateChannelList() {
        updateChannelListAndCheckLcn(false);
    }

    private void updateChannelListAndCheckLcn(boolean needCheckLcn) {
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
        setSearchStatus("Updating guide", "");
        setStrengthAndQualityStatus("","");
        startMonitoringSync();

        // If the intent that started this activity is from Live Channels app
        String inputId = this.getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        Log.i(TAG, String.format("inputId: %s", inputId));
        //EpgSyncJobService.requestImmediateSync(this, inputId, true, new ComponentName(this, DtvkitEpgSync.class)); // 12 hours
        Bundle parameters = new Bundle();
        if (needCheckLcn) {
            parameters.putBoolean(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_LCN_CONFLICT, false);
        }
        int searchMode = mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE);
        int isFrequencySearch = mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY);
        if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO != searchMode) {
            String freq = (DataManager.VALUE_FREQUENCY_MODE == isFrequencySearch) ? (getParameter() + "000") : getFreqOfChannelId();
            parameters.putInt(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY, Integer.valueOf(freq));
        }
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == searchMode ? EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO : EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL);
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, mIsDvbt ? "DVB-T" : "DVB-C");

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

    private void setSearchStatus(final String status, final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, String.format("Search status \"%s\"", status));
                final TextView text = (TextView) findViewById(R.id.tv_search_status);
                text.setText(String.format("%s\t%s",status,description));
            }
        });
    }

    private void setStrengthAndQualityStatus(final String strengthStatus, final String qualityStatus) {
        setStrengthAndQualityStatus(strengthStatus, qualityStatus, "");
    }

    private void setStrengthAndQualityStatus(final String strengthStatus, final String qualityStatus, final String channel) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(strengthStatus) && TextUtils.isEmpty(qualityStatus)) {
                    findViewById(R.id.channel_holder).setVisibility(View.GONE);
                } else {
                    findViewById(R.id.channel_holder).setVisibility(View.VISIBLE);
                }
                Log.i(TAG, String.format("Strength: %s", strengthStatus));
                Log.i(TAG, String.format("Quality: %s", qualityStatus));
                TextView channelInfo = findViewById(R.id.tv_scan_info);
                TextView channelNumber = findViewById(R.id.tv_scan_ch_number);
                channelInfo.setText(strengthStatus + "\t\t" + qualityStatus + "\t\t");
                if (channel != null) {
                    channelNumber.setText(channel);
                }
            }
        });
    }

    private void setSearchProgress(final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ProgressBar bar = (ProgressBar) findViewById(R.id.proBar_search_progress);
                bar.setProgress(progress);
            }
        });
    }

    private void setSearchProgressIndeterminate(final Boolean indeterminate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ProgressBar bar = (ProgressBar) findViewById(R.id.proBar_search_progress);
                bar.setIndeterminate(indeterminate);
            }
        });
    }

    private boolean isAutoSearch() {
        return DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE);
    }

    private void updateSearchButton(final boolean strStart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((Button) findViewById(R.id.btn_terrestrial_start_search)).setText(strStart ? (isAutoSearch() ? R.string.strStartSearch : R.string.strManualSearch) : R.string.strStopSearch);
            }
        });
    }

    private int getFoundServiceNumber() {
        int found = 0;
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request("Dvb.getNumberOfServices", new JSONArray());
            found = obj.getInt("data");
            Log.i(TAG, "getFoundServiceNumber found = " + found);
        } catch (Exception ignore) {
            Log.e(TAG, "getFoundServiceNumber Exception = " + ignore.getMessage());
        }
        return found;
    }

        private int getFoundServiceNumberOnSearch() {
        int found = 0;
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request("Dvb.getCategoryNumberOfServices", new JSONArray());
            JSONObject datas = obj.getJSONObject("data");
            found = datas.getInt("total_num");
            Log.i(TAG, "getFoundServiceNumberOnSearch found = " + found);
        } catch (Exception ignore) {
            Log.e(TAG, "getFoundServiceNumberOnSearch Exception = " + ignore.getMessage());
        }
        return found;
    }

    private int getSearchProcess(JSONObject data) {
        int progress = 0;
        if (data == null) {
            return progress;
        }
        try {
            //Log.e("SearchProcess", "" + data.toString());
            progress = data.getInt("progress");
        } catch (JSONException ignore) {
            Log.e(TAG, "getSearchProcess Exception = " + ignore.getMessage());
        }
        return progress;
    }

    private void sendOnSignal(final Map<String, Object> map) {
        if (mThreadHandler != null) {
            String signal = (String) map.get("signal");
            if (TextUtils.equals("DvbtStatusChanged", signal)
                || TextUtils.equals("DvbcStatusChanged", signal)
                || TextUtils.equals("UpdateMsgStatus", signal)) {
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
        int strengthStatus = mParameterManager.getStrengthStatus();
        int qualityStatus = mParameterManager.getQualityStatus();
        int found = 0;
        if (mSearchManualAutoType == DtvkitDvbScanSelect.SEARCH_TYPE_MANUAL) {
            if (getSearchProcess(data) >= 100) {
                found = getFoundServiceNumberOnSearch();
            }
        } else {
            found = getFoundServiceNumberOnSearch();
        }
        if (signal != null && ((mIsDvbt && signal.equals("DvbtStatusChanged")) || (!mIsDvbt && signal.equals("DvbcStatusChanged")))) {
            int progress = getSearchProcess(data);
            Log.d(TAG, "onSignal progress = " + progress);
            setSearchProgress(progress);
            setSearchStatus(String.format(Locale.ENGLISH, "Searching (%d%%)", progress), "");
            setStrengthAndQualityStatus(String.format(Locale.ENGLISH, "Strength: %d%%", strengthStatus), String.format(Locale.ENGLISH, "Quality: %d%%", qualityStatus), String.format(Locale.ENGLISH, "Channel: %d", found));
            if (progress >= 100) {
                //onSearchFinished();
                sendFinishSearch(false);
            }
        } else if (signal != null && ((mIsDvbt && signal.equals("UpdateMsgStatus")) || (!mIsDvbt && signal.equals("UpdateMsgStatus")))) {
            Log.d(TAG, "UpdateMsgStatus: StrengthStatus = " + strengthStatus + "%, qualityStatus = " + qualityStatus + "%");
            setStrengthAndQualityStatus(String.format(Locale.getDefault(), "Strength: %d%%", strengthStatus), String.format(Locale.getDefault(), "Quality: %d%%", qualityStatus), null /* don't update channel */);
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

    private void showNetworkInfoConfirmDialog(final Context context, final JSONArray networkArray) {
        Log.d(TAG, "showNetworkInfoConfirmDialog networkArray = " + networkArray);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(context, R.layout.confirm_region_network, null);
        final TextView title = (TextView) dialogView.findViewById(R.id.dialog_title);
        final ListView listView = (ListView) dialogView.findViewById(R.id.lv_dialog);
        final List<HashMap<String, Object>> dataList = new ArrayList<HashMap<String, Object>>();
        if (networkArray != null && networkArray.length() > 0) {
            JSONObject networkObj = null;
            String name = null;
            for (int i = 0; i < networkArray.length(); i++) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                networkObj = mParameterManager.getJSONObjectFromJSONArray(networkArray, i);
                name = mParameterManager.getNetworkName(networkObj);
                map.put("name", name);
                dataList.add(map);
            }
        } else {
            Log.d(TAG, "showNetworkInfoConfirmDialog no networkArray");
            return;
        }
        SimpleAdapter adapter = new SimpleAdapter(context, dataList,
                R.layout.region_network_list,
                new String[] {"name"},
                new int[] {R.id.name});

        listView.setAdapter(adapter);
        listView.setSelection(mParameterManager.getCurrentNetworkIndex(networkArray));
        title.setText(R.string.search_confirm_network);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int position, long id) {
                int currentNetWorkIndex = mParameterManager.getCurrentNetworkIndex(networkArray);
                if (currentNetWorkIndex == position) {
                    Log.d(TAG, "showNetworkInfoConfirmDialog same position = " + position);
                    alert.dismiss();
                    return;
                }
                //execute by child thread
                mThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "showNetworkInfoConfirmDialog onItemClick position = " + position);
                        mParameterManager.setNetworkPreferredOfRegion(mParameterManager.getNetworkId(networkArray, position));
                        //ui need to be updated in main handler
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                alert.dismiss();
                            }
                        });
                    }
                });
            }
        });
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(TAG, "showNetworkInfoConfirmDialog onDismiss");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateChannelList();
                    }
                });
            }
        });

        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        alert.setView(dialogView);
        alert.show();
        WindowManager.LayoutParams params = alert.getWindow().getAttributes();
        params.width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500, context.getResources().getDisplayMetrics());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        alert.getWindow().setAttributes(params);
        //alert.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
    }

    private void showLcnConflictServiceConfirmDialog(final Context context, final JSONArray lcnConflictArray) {
        Log.d(TAG, "showLcnConflictServiceConfirmDialog lcnConflictArray = " + lcnConflictArray);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(context, R.layout.confirm_region_network, null);
        final TextView title = (TextView) dialogView.findViewById(R.id.dialog_title);
        final ListView listView = (ListView) dialogView.findViewById(R.id.lv_dialog);
        final List<HashMap<String, Object>> DATALIST = new ArrayList<HashMap<String, Object>>();
        final int[] DealtIndex = {0, 0};
        final int[] FinalIndex = {0};
        final boolean[] NeedShowNext = {false};
        if (lcnConflictArray != null && lcnConflictArray.length() > 0) {
            int[] updateDefaultIndex = getLcnConflictDataList(DATALIST, lcnConflictArray, DealtIndex[0]);
            DealtIndex[0] = updateDefaultIndex[0];
            DealtIndex[1] = updateDefaultIndex[1];
        } else {
            Log.d(TAG, "showLcnConflictServiceConfirmDialog no lcnConflictArray");
            return;
        }

        listView.setAdapter(getLcnConflictDataListSimpleAdapter(context, DATALIST));
        title.setText(R.string.search_confirm_lcn);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int position, long id) {
                boolean finalSelect = false;
                if (DATALIST.size() < position) {
                    Log.d(TAG, "showLcnConflictServiceConfirmDialog invalid position = " + position);
                    return;
                } else if (DATALIST.size() - 1 == position) {
                    //select end
                    Log.d(TAG, "showLcnConflictServiceConfirmDialog finalSelect position = " + position);
                    finalSelect = true;
                }
                int realIndex = DealtIndex[0] + position;
                JSONObject serviceObj = mParameterManager.getJSONObjectFromJSONArray(lcnConflictArray, realIndex);
                if (serviceObj == null) {
                    Log.d(TAG, "showLcnConflictServiceConfirmDialog service not found");
                    return;
                } else {
                    Log.d(TAG, "showLcnConflictServiceConfirmDialog serviceObj = " + serviceObj);
                }
                FinalIndex[0] = realIndex;
                //execute by child thread
                mThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "showLcnConflictServiceConfirmDialog onItemClick position = " + position + ", DealtIndex[0] = " + DealtIndex[0] + ", DealtIndex[1] = " + DealtIndex[1] + ", FinalIndex[0] = " + FinalIndex[0]);
                        String uri = null;
                        String tunerType = null;
                        try {
                            uri = serviceObj.getString("uri");
                            tunerType = serviceObj.getString("sig_name");
                        } catch (Exception e) {
                            Log.d(TAG, "showLcnConflictServiceConfirmDialog get uri Exception = " + e.getMessage());
                        }
                        if (uri != null && tunerType != null) {
                            Log.d(TAG, "showLcnConflictServiceConfirmDialog selectServiceKeepConflictLcn");
                            mParameterManager.selectServiceKeepConflictLcn(uri, tunerType);
                            //ui need to be updated in main handler
                            if (mParameterManager.hasSelectToEnd(lcnConflictArray, FinalIndex[0])) {
                                NeedShowNext[0] = false;
                            } else {
                                NeedShowNext[0] = true;
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //turn to next lcn
                                    if (NeedShowNext[0]) {
                                        Log.d(TAG, "showLcnConflictServiceConfirmDialog onItemClick select next");
                                        int[] updateDefaultIndex = getLcnConflictDataList(DATALIST, lcnConflictArray, DealtIndex[1]);
                                        DealtIndex[0] = updateDefaultIndex[0];
                                        DealtIndex[1] = updateDefaultIndex[1];
                                        listView.setAdapter(getLcnConflictDataListSimpleAdapter(context, DATALIST));
                                    } else {
                                        Log.d(TAG, "showLcnConflictServiceConfirmDialog onItemClick select over");
                                        alert.dismiss();
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (mParameterManager.hasSelectToEnd(lcnConflictArray, FinalIndex[0])) {
                    Log.d(TAG, "showLcnConflictServiceConfirmDialog select over onDismiss");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateChannelList();
                        }
                    });
                } else {
                    Log.d(TAG, "showLcnConflictServiceConfirmDialog select default for the rest");
                    mThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mParameterManager.dealRestLcnConflictAsDefault(lcnConflictArray, DealtIndex[0]);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateChannelList();
                                }
                            });
                        }
                    });
                }
            }
        });

        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        alert.setView(dialogView);
        alert.show();
        WindowManager.LayoutParams params = alert.getWindow().getAttributes();
        params.width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500, context.getResources().getDisplayMetrics());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        alert.getWindow().setAttributes(params);
        //alert.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
    }

    /*
    * return start index and next new index that need to be dealt
    */
    private int[] getLcnConflictDataList(List<HashMap<String, Object>> source, JSONArray values, int index) {
        int[] result = {-1, -1};
        result[0] = index;
        if (source != null && values != null && values.length() > 0 && values.length() > index && index > -1) {
            source.clear();
            JSONObject lcnConflictObj = null;
            String name = null;
            int lcn = -1;
            int previousLcn = -1;
            for (int i = index; i < values.length(); i++) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                lcnConflictObj = mParameterManager.getJSONObjectFromJSONArray(values, i);
                name = mParameterManager.getLcnServiceName(lcnConflictObj);
                lcn = mParameterManager.getLcnServiceLcnValue(lcnConflictObj);
                if (previousLcn != -1 && previousLcn != lcn) {
                    //record new start index
                    result[1] = i;
                    break;
                } else if (i == (values.length() - 1)) {
                    //search to the end
                    result[1] = values.length() - 1;
                }
                previousLcn = lcn;
                map.put("name", lcn + "  " + name);
                source.add(map);
            }
        }
        Log.d(TAG, "getLcnConflictDataList result[0] = " + result[0] + ", result[1] = " + result[1]);
        return result;
    }

    private SimpleAdapter getLcnConflictDataListSimpleAdapter(Context context, List<HashMap<String, Object>> source) {
        SimpleAdapter result = null;
        if (context != null && source != null && source.size() > 0) {
            result = new SimpleAdapter(context, source,
                R.layout.region_network_list,
                new String[] {"name"},
                new int[] {R.id.name});
        }
        return result;
    }

    private void trylockForScanParaChange(){
        if (isPipOrFccEnable()) {
            Log.d(TAG, "Pip and Fcc can't start signal check");
            return;
        }

        if (DataManager.VALUE_PUBLIC_SEARCH_MODE_AUTO == mDataManager.getIntParameters(DataManager.KEY_PUBLIC_SEARCH_MODE)) {
            Log.d(TAG, "trylockForScanParaChange auto not need trylock");
            return;
        }
        if (null != mDtvKitScanSignalPresenter) {
            Log.d(TAG, "trylockForScanParaChange");
            if (DataManager.VALUE_FREQUENCY_MODE == mDataManager.getIntParameters(DataManager.KEY_IS_FREQUENCY)) {
                EditText public_type_edit = (EditText)findViewById(R.id.edtTxt_public_type_in);
                Editable freq = public_type_edit.getText();
                if (mIsDvbt) {
                    mDtvKitScanSignalPresenter.freqTunerTryLock(freq.toString());
                } else {
                    EditText dvbc_symbol_edit = (EditText)findViewById(R.id.dvbc_symbol_edit);
                    Editable dvbcSymbol = dvbc_symbol_edit.getText();
                    mDtvKitScanSignalPresenter.dvbcSymbolTryLock(dvbcSymbol.toString(), freq.toString());
                }
            } else {
                mDtvKitScanSignalPresenter.channelTunerTryLock();
            }
        } else {
            Log.e(TAG, "trylockForScanParaChange DtvKitScanSignalPresenter is null");
        }
    }

    private int bandWidthStrToInt(String bandWidthStr) {
        int ret = 3;

        if ("5MHZ".equalsIgnoreCase(bandWidthStr)) {
            ret = 0;
        } else if ("6MHZ".equalsIgnoreCase(bandWidthStr)) {
            ret = 1;
        } else if ("7MHZ".equalsIgnoreCase(bandWidthStr)) {
            ret = 2;
        } else if ("8MHZ".equalsIgnoreCase(bandWidthStr)) {
            ret = 3;
        } else if ("10MHZ".equalsIgnoreCase(bandWidthStr)) {
            ret = 4;
        }

        return ret;
    }

    private int transmissionStrToInt(String transmission) {
        int ret = 3;

        if ("1K".equalsIgnoreCase(transmission)) {
            ret = 0;
        } else if ("2K".equalsIgnoreCase(transmission)) {
            ret = 1;
        } else if ("4K".equalsIgnoreCase(transmission)) {
            ret = 2;
        } else if ("8K".equalsIgnoreCase(transmission)) {
            ret = 3;
        } else if ("16K".equalsIgnoreCase(transmission)) {
            ret = 4;
        } else if ("16K".equalsIgnoreCase(transmission)) {
            ret = 5;
        }

        return ret;
    }

    private int modulationStrToInt(String modulation) {
        int ret = 5;//default auto

        if ("QAM16".equalsIgnoreCase(modulation)) {
            ret = 0;
        } else if ("QAM32".equalsIgnoreCase(modulation)) {
            ret = 1;
        } else if ("QAM64".equalsIgnoreCase(modulation)) {
            ret = 2;
        } else if ("QAM128".equalsIgnoreCase(modulation)) {
            ret = 3;
        } else if ("QAM256".equalsIgnoreCase(modulation)) {
            ret = 4;
        }

        return ret;
    }

    private void sendScanFinishBroadcast() {
        if (mParameterManager.checkIsFvpUkCountry()) {
            FvpSearchBroadcast.sendBroadcast(this);
        }
    }

    private boolean isPipOrFccEnable(){
        if (PropSettingManager.getBoolean(PropSettingManager.ENABLE_FULL_PIP_FCC_ARCHITECTURE, false)) {
            if (PropSettingManager.getBoolean(PropSettingManager.ENABLE_PIP_SUPPORT, false) || PropSettingManager.getBoolean(PropSettingManager.ENABLE_FCC_SUPPORT, false)) {
                return true;
            }
        }
        return false;
    }

    private void regionSetup() {
        Log.d(TAG, "regionSetup start");
        final TargetRegionManager regionManager = new TargetRegionManager(DtvkitDvbtSetup.this);
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
                                regionManager.getRegionCode(regionManager.TARGET_REGION_COUNTRY),
                                -1, -1);
                        break;
                    case TargetRegionManager.TARGET_REGION_SECONDARY:
                        array = mParameterManager.getTargetRegions(target_id,
                                regionManager.getRegionCode(regionManager.TARGET_REGION_COUNTRY),
                                regionManager.getRegionCode(regionManager.TARGET_REGION_PRIMARY),
                                -1);
                        break;
                    case TargetRegionManager.TARGET_REGION_TERTIARY:
                        array = mParameterManager.getTargetRegions(target_id,
                                regionManager.getRegionCode(regionManager.TARGET_REGION_COUNTRY),
                                regionManager.getRegionCode(regionManager.TARGET_REGION_PRIMARY),
                                regionManager.getRegionCode(regionManager.TARGET_REGION_SECONDARY));
                        break;
                }
                if (array != null && array.length() > 0) {
                    JSONObject region = null;
                    String region_name = null;
                    int region_code = -1;
                    for (int i = 0; i < array.length(); i++) {
                        region = mParameterManager.getJSONObjectFromJSONArray(array, i);
                        region_name = mParameterManager.getTargetRegionName(target_id, region);
                        region_code = mParameterManager.getTargetRegionCode(target_id, region);
                        map.put(region_name, region_code);
                    }
                } else {
                    Log.d(TAG, "No regions for target_id " + target_id);
                }
                if (map.size() > 0)
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
                onSearchFinished(false);
            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                regionManager.start(true);
            }
        });
    }

    /*private boolean hasSelectToEnd(JSONArray lcnConflictArray, int selectEndIndex) {
        boolean result = false;
        if (lcnConflictArray != null && lcnConflictArray.length() > 0) {
            if (selectEndIndex >= lcnConflictArray.length() - 1) {
                result = true;
                return result;
            }
            int finalLcn = -1;
            JSONObject lcnConflictObj = null;
            int conflictLcn = -1;
            for (int i = selectEndIndex; i < lcnConflictArray.length(); i++) {
                lcnConflictObj = mParameterManager.getJSONObjectFromJSONArray(lcnConflictArray, i);
                conflictLcn = mParameterManager.getLcnServiceLcnValue(lcnConflictObj);
                if (selectEndIndex == i) {
                    finalLcn = conflictLcn;
                    continue;
                }
                if (finalLcn != conflictLcn) {
                    result = false;
                    break;
                } else {
                    result = true;
                }
            }
        }
        Log.d(TAG, "hasSelectToEnd result = " + result);
        return result;
    }

    private void dealRestLcnConflictAsDefault(JSONArray lcnConflictArray, int restStartIndex) {
        if (lcnConflictArray != null && lcnConflictArray.length() > 0) {
            JSONObject lcnConflictObj = null;
            int lcn = -1;
            String tunerType = null;
            int previousLcn = -1;
            boolean needUpdate = false;
            for (int i = restStartIndex; i < lcnConflictArray.length(); i++) {
                lcnConflictObj = mParameterManager.getJSONObjectFromJSONArray(lcnConflictArray, i);
                lcn = mParameterManager.getLcnServiceLcnValue(lcnConflictObj);
                tunerType = mParameterManager.getLcnServiceTunerType(lcnConflictObj);
                if (previousLcn == -1) {
                    previousLcn = lcn;
                    needUpdate = true;
                } else if (previousLcn != lcn) {
                    previousLcn = lcn;
                    needUpdate = true;
                } else {
                    needUpdate = false;
                }
                if (needUpdate) {
                    Log.d(TAG, "dealRestLcnConflictAsDefault needUpdate previousLcn = " + previousLcn + ", tunerType = " + tunerType);
                    mParameterManager.selectDefaultLcnConflictProcess(previousLcn, tunerType);
                }
            }
        }
    }*/

    interface InvalidEditInputListener {
        public void onInputInvalid();
    }

    class AutoNumberEditText {
        private EditText mEditText;
        private int mDefault;
        private String mDefaultHint;
        private int mMinNumber;
        private int mMaxNumber;
        private String mUnit = "";
        private InvalidEditInputListener mListener;
        public AutoNumberEditText(EditText editText, int defaultVal, int min, int max, String unit, InvalidEditInputListener listener) throws Exception {
            if (editText != null) {
                mEditText = editText;
                if (defaultVal < min || defaultVal > max)
                    defaultVal = 0;
                if (min > max) {
                    min = max - 1;
                }
                if (unit == null) {
                    unit = "";
                }
                mDefault = defaultVal;
                mMinNumber = min;
                mMaxNumber = max;
                mListener = listener;
                mUnit = unit;
                mDefaultHint = (defaultVal == 0) ? "Auto" : defaultVal + " " + unit;
                editText.setHint(mDefaultHint);
                editText.setText("");
            } else {
                throw new Exception("Cannot use null EditText here.");
            }
            mEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        mEditText.setHint("" + mMinNumber + "~" + mMaxNumber + " " + mUnit);
                    } else {
                        String strValue = mEditText.getText().toString();
                        int value = mDefault;
                        try {
                            value = Integer.parseInt(strValue);
                        } catch (Exception e) {
                        }
                        if (value > mMaxNumber || value < mMinNumber) {
                            if (mListener != null && value != mDefault) {
                                mListener.onInputInvalid();
                            }
                            mEditText.setHint(mDefaultHint);
                            mEditText.setText("");
                        }else {
                            if (mDefault != value) {
                                mEditText.setHint(strValue);
                            } else {
                                mEditText.setHint(mDefaultHint);
                            }
                        }
                    }
                }
            });
        }
        public void updateDefault(int defaultVal) {
            if (defaultVal < mMinNumber || defaultVal > mMaxNumber)
                defaultVal = 0;
            if (defaultVal != mDefault) {
                mDefault = defaultVal;
                mDefaultHint = (defaultVal == 0) ? "Auto" : defaultVal + " " + mUnit;
            }
            mEditText.setHint(mDefaultHint);
            mEditText.setText("");
        }
        public int getValue() {
            int ret = mDefault;
            String strValue = mEditText.getText().toString();
            if (TextUtils.isEmpty(strValue)) {
                return ret;
            }
            try {
                ret = Integer.parseInt(strValue);
            } catch (Exception e) {
            }
            if (ret > mMaxNumber || ret < mMinNumber) {
                ret = mDefault;
            }
            return ret;
        }

        public void setEnabled(boolean enable) {
            mEditText.setEnabled(enable);
        }
    }
}
