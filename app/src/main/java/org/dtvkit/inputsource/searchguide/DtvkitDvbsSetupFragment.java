package com.droidlogic.dtvkit.inputsource.searchguide;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.droidlogic.app.DataProviderManager;
import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.DtvkitDvbScanSelect;
import com.droidlogic.dtvkit.inputsource.DtvkitEpgSync;
import com.droidlogic.dtvkit.inputsource.DtvkitRequest;
import com.droidlogic.dtvkit.inputsource.PvrStatusConfirmManager;
import com.droidlogic.dtvkit.inputsource.R;
import com.droidlogic.fragment.DvbsParameterManager;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.fragment.SatelliteWrap;
import com.droidlogic.settings.ConstantManager;

import com.droidlogic.dtvkit.inputsource.searchguide.DataPresenter;
import com.droidlogic.dtvkit.inputsource.searchguide.DtvkitDvbsSetupFragment;
import com.droidlogic.dtvkit.inputsource.searchguide.OnNextListener;
import com.droidlogic.dtvkit.inputsource.searchguide.SearchStageFragment;
import com.droidlogic.dtvkit.inputsource.searchguide.SimpleListFragment;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DtvkitDvbsSetupFragment extends SearchStageFragment {
    private final String TAG = DtvkitDvbsSetupFragment.class.getSimpleName();
    private static final int REQUEST_CODE_SET_UP_SETTINGS = 1;
    private DataManager mDataManager;
    private boolean mStartSync = false;
    private boolean mStartSearch = false;
    private boolean mSyncFinish = false;
    private int mFoundServiceNumber = 0;
    private JSONArray mServiceList = null;
    private int mSearchType = -1;// 0 manual 1 auto
    private int mSearchDvbsType = -1;
    private PvrStatusConfirmManager mPvrStatusConfirmManager = null;
    private ParameterManager mParameterManager;
    private DvbsParameterManager mDvbsParameterManager;

    protected HandlerThread mHandlerThread = null;
    protected Handler mThreadHandler = null;

    private final static int MSG_FINISH_SEARCH = 1;
    private final static int MSG_ON_SIGNAL = 2;
    private final StringBuilder mTransponderFrequency = new StringBuilder();
    private long clickLastTime = 0;
    private final String inputId = com.droidlogic.dtvkit.inputsource.service.DtvkitSettingService.DTVKIT_INPUT_ID;;
    private String pvrStatus;

    // UI to Update
    private LinearLayout mChannelHolder;
    private TextView mSearchStatus;
    private Button mSetup;
    private Button mSearch;
    private Button mOptional;
    private TextView mChannelInfo;
    private ProgressBar mSearchProgress;

    private boolean mHideByDiSEqCMsg = false;
    private boolean mSearchByManualDiSEqC = false;
    private boolean mSearchByManualTKGS = false;

    private final DtvkitGlueClient.SignalHandler mHandler = (signal, data) -> {
        Log.d(TAG, "onSignal = " + signal + ", " + data);
        if (signal.equals("DvbsStatusChanged")) {
            int progress = getSearchProcess(data);
            if (mThreadHandler != null) {
                mThreadHandler.removeMessages(MSG_ON_SIGNAL);
                Message msg = mThreadHandler.obtainMessage(MSG_ON_SIGNAL, progress, 0, null);
                boolean info = mThreadHandler.sendMessageDelayed(msg, 0);
            }
        } else if (signal.equals("DiseqcConfirmRequired")) {
            mHideByDiSEqCMsg = true;
            if (getListener() != null) {
                getListener().onNext(DtvkitDvbsSetupFragment.this, DataPresenter.FRAGMENT_MANUAL_DISEQC);
            }
        }
        if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7) {
            switch (signal) {
                case "SelectOperatorRequired":
                    if (getListener() != null) {
                        getListener().onNext(DtvkitDvbsSetupFragment.this, DataPresenter.FRAGMENT_OPERATOR_LIST);
                    }
                    break;
                case "SelectSublistRequired":
                    if (getListener() != null) {
                        getListener().onNext(DtvkitDvbsSetupFragment.this, DataPresenter.FRAGMENT_OPERATOR_SUB_LIST);
                    }
                    break;
                case "SelectRegionRequired":
                    if (getListener() != null) {
                        getListener().onNext(DtvkitDvbsSetupFragment.this, DataPresenter.FRAGMENT_REGIONAL_CHANNELS);
                    }
                    break;
            }
        } else if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_ASTRA_HD_PLUS) {
            if (signal.equalsIgnoreCase("SelectServiceListRequired")) {
                JSONArray result = null;
                try {
                    result = data.optJSONArray("result");
                } catch (Exception e) {
                    Log.w(TAG, e.getMessage());
                }
                if (getListener() != null) {
                    getListener().onNext(DtvkitDvbsSetupFragment.this, DataPresenter.FRAGMENT_SERVICE_LIST, result);
                }
            }
        } else if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_SKY_D) {
            //TO DO
        } else if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_TKGS) {
            if (signal.equalsIgnoreCase("TkgsServiceListNotify")) {
                List<String> list = new ArrayList<>();
                try {
                    JSONArray result = data.getJSONArray("result");
                    if (result.length() == 0) {
                        Log.w(TAG, " result is null");
                        return;
                    }
                    for (int i = 0; i < result.length(); i++) {
                        list.add(((JSONObject) result.get(i)).getString("servicelist_name"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                getActivity().runOnUiThread(() -> {
                    showSearchInformationDialog(getContext(), "TkgsServiceListNotify", list, null);
                });
            } else if (signal.equals("TkgsVersionNotChangedNotify")) {
                getActivity().runOnUiThread(() -> {
                    showSearchInformationDialog(getContext(), "TkgsVersionNotChangedNotify", null, null);
                });
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            String status = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            if (status.equals(EpgSyncJobService.SYNC_FINISHED)
                    || status.equals(EpgSyncJobService.SYNC_ERROR)) {
                updateSearchUi(true, true, "Finished");
                mStartSync = false;
                mSyncFinish = true;
                finish();
            }
        }
    };

    public static DtvkitDvbsSetupFragment newInstance(String title) {
        DtvkitDvbsSetupFragment fragment = new DtvkitDvbsSetupFragment();
        fragment.setTitle(title);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDataManager = new DataManager(getActivity().getApplicationContext());
        mPvrStatusConfirmManager = new PvrStatusConfirmManager(getActivity().getApplicationContext(), mDataManager);
        mParameterManager = new ParameterManager(getActivity().getApplicationContext(), DtvkitGlueClient.getInstance());
        mDvbsParameterManager = DvbsParameterManager.getInstance(getActivity().getApplicationContext());
        mDvbsParameterManager.setCurrentOperator(DataPresenter.getOperateType());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initHandler();
        Intent cIntent = getActivity().getIntent();
        if (cIntent != null) {
            pvrStatus = cIntent.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
            mPvrStatusConfirmManager.setPvrStatus(pvrStatus);
            Log.d(TAG, "onCreate pvr status = " + pvrStatus);
        } else {
            pvrStatus = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sat_setup, container, false);
        mOptional = view.findViewById(R.id.option_set_btn);
        mOptional.setOnClickListener(v -> {
            Intent intentSet = new Intent();
            if (inputId != null) {
                intentSet.putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId);
            }
            String pvrFlag = PvrStatusConfirmManager.read(getActivity(), PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
            if (pvrStatus != null && PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(pvrFlag)) {
                intentSet.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, pvrStatus);
            } else {
                intentSet.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, "");
            }
            intentSet.setClassName(DataManager.KEY_PACKAGE_NAME, DataManager.KEY_ACTIVITY_SETTINGS);
            startActivity(intentSet);
            mDataManager.saveIntParameters(DataManager.KEY_SELECT_SEARCH_ACTIVITY, DataManager.SELECT_SETTINGS);
        });

        mChannelHolder = view.findViewById(R.id.channel_holder);
        mSearchStatus = view.findViewById(R.id.tv_search_status);
        mSetup = view.findViewById(R.id.setup);
        mSearch = view.findViewById(R.id.btn_start_search);
        mChannelInfo = view.findViewById(R.id.tv_scan_info);
        mSearchProgress = view.findViewById(R.id.proBar_search_progress);

        final Button importSatellite = (Button) view.findViewById(R.id.import_satellite);
        mSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long currentTime = SystemClock.elapsedRealtime();
                if (currentTime - clickLastTime > 500) {
                    clickLastTime = currentTime;
                    mPvrStatusConfirmManager.setSearchType(ConstantManager.KEY_DTVKIT_SEARCH_TYPE_MANUAL);
                    boolean checkPvr = mPvrStatusConfirmManager.needDeletePvrRecordings();
                    if (checkPvr) {
                        mPvrStatusConfirmManager.showDialogToAppoint(getActivity(), false);
                    } else {
                        if (mStartSearch) {
                            sendFinishSearch(true);
                            updateSearchUi(true, true, "Finishing search");
                        } else {
                            mPvrStatusConfirmManager.sendDvrCommand(getActivity());
                            sendFinishSearch(false); // stop before start
                            updateSearchUi(false, false, "Searching");
                            startSearch();
                        }
                    }
                }
            }
        });
        mSearch.requestFocus();

        mSetup.setOnClickListener(v -> {
            mPvrStatusConfirmManager.setSearchType(ConstantManager.KEY_DTVKIT_SEARCH_TYPE_MANUAL);
            boolean checkPvr = mPvrStatusConfirmManager.needDeletePvrRecordings();
            if (checkPvr) {
                mPvrStatusConfirmManager.showDialogToAppoint(getActivity(), false);
            } else {
                mPvrStatusConfirmManager.sendDvrCommand(getActivity());
                setUp();
            }
        });

        //ui set visibility gone as will be executed after first boot
        importSatellite.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mParameterManager.importDatabase(ConstantManager.DTVKIT_SATELLITE_DATA);
            }
        });

        CheckBox nit = (CheckBox) view.findViewById(R.id.network);
        CheckBox clear = (CheckBox) view.findViewById(R.id.clear_old);
        CheckBox dvbs2 = (CheckBox) view.findViewById(R.id.dvbs2);
        Spinner searchMode = (Spinner) view.findViewById(R.id.search_mode);
        Spinner fecMode = (Spinner) view.findViewById(R.id.fec_mode);
        Spinner modulationMode = (Spinner) view.findViewById(R.id.modulation_mode);
        EditText edit_start_freq = (EditText) view.findViewById(R.id.edit_start_freq);
        EditText edit_end_freq = (EditText) view.findViewById(R.id.edit_end_freq);
        edit_start_freq.setText(String.valueOf(DataManager.VALUE_BLIND_DEFAULT_START_FREQUENCY));
        edit_end_freq.setText(String.valueOf(DataManager.VALUE_BLIND_DEFAULT_END_FREQUENCY));

        nit.setChecked(mDataManager.getIntParameters(DataManager.KEY_DVBS_NIT) == 1);
        clear.setChecked(mDataManager.getIntParameters(DataManager.KEY_CLEAR) == 1);
        dvbs2.setChecked(mDataManager.getIntParameters(DataManager.KEY_DVBS2) == 1);
        nit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (nit.isChecked()) {
                    mDataManager.saveIntParameters(DataManager.KEY_DVBS_NIT, 1);
                } else {
                    mDataManager.saveIntParameters(DataManager.KEY_DVBS_NIT, 0);
                }
            }
        });
        clear.setOnClickListener(v -> {
            if (clear.isChecked()) {
                mDataManager.saveIntParameters(DataManager.KEY_CLEAR, 1);
            } else {
                mDataManager.saveIntParameters(DataManager.KEY_CLEAR, 0);
            }
        });
        dvbs2.setOnClickListener(v -> {
            if (dvbs2.isChecked()) {
                mDataManager.saveIntParameters(DataManager.KEY_DVBS2, 1);
            } else {
                mDataManager.saveIntParameters(DataManager.KEY_DVBS2, 0);
            }
        });
        searchMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "searchMode onItemSelected position = " + position);
                mDataManager.saveIntParameters(DataManager.KEY_SEARCH_MODE, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        fecMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "fecMode onItemSelected position = " + position);
                mDataManager.saveIntParameters(DataManager.KEY_FEC_MODE, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        modulationMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "modulationMode onItemSelected position = " + position);
                if (DataPresenter.getOperateType() != DvbsParameterManager.OPERATOR_M7 && DataPresenter.getOperateType() != DvbsParameterManager.OPERATOR_TKGS) {
                    mDataManager.saveIntParameters(DataManager.KEY_MODULATION_MODE, position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        int searchModeValue = mDataManager.getIntParameters(DataManager.KEY_SEARCH_MODE);
        searchMode.setSelection(DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_DEFAULT ? searchModeValue : DataManager.VALUE_SEARCH_MODE_SATELLITE);
        searchMode.setEnabled(DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_DEFAULT);

        fecMode.setSelection(mDataManager.getIntParameters(DataManager.KEY_FEC_MODE));
        modulationMode.setSelection(mDataManager.getIntParameters(DataManager.KEY_MODULATION_MODE));
        Spinner channelTypeSpinner = (Spinner) view.findViewById(R.id.channel_type);
        channelTypeSpinner.setSelection(getCurrentChannelType());
        channelTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (DataPresenter.getOperateType() != DvbsParameterManager.OPERATOR_M7 && DataPresenter.getOperateType() != DvbsParameterManager.OPERATOR_TKGS) {
                    updateChannelType(i);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        Spinner serviceTypeSpinner = (Spinner) view.findViewById(R.id.service_type);
        serviceTypeSpinner.setSelection(getCurrentServiceType());
        serviceTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (DataPresenter.getOperateType() != DvbsParameterManager.OPERATOR_M7 && DataPresenter.getOperateType() != DvbsParameterManager.OPERATOR_TKGS) {
                    updateServiceType(i);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        if (getArguments() != null) {
            mSearchByManualDiSEqC = getArguments().getBoolean("manualDiseqc", false);
            mSearchByManualTKGS = getArguments().getBoolean("manualTKGS", false);
        }

        if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7 || DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_TKGS) {
            Log.d(TAG, "disable UI widget");
            nit.setEnabled(false);
            fecMode.setEnabled(false);
            serviceTypeSpinner.setEnabled(false);
            channelTypeSpinner.setEnabled(false);

            //Set default parameters
            nit.setChecked(Boolean.TRUE);
            channelTypeSpinner.setSelection(0);
            serviceTypeSpinner.setSelection(0);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mStartSearch = false;
        releaseHandler();
        stopMonitoringSync();

    }

    private void initHandler() {
        Log.d(TAG, "initHandler");
        mHandlerThread = new HandlerThread("DtvkitDvbtSetup");
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper(), msg -> {
            Log.d(TAG, "mThreadHandler handleMessage " + msg.what + " start");
            switch (msg.what) {
                case MSG_FINISH_SEARCH: {
                    updateSearchUi(true, true, "Finishing search");
                    onSearchFinished();
                    break;
                }
                case MSG_ON_SIGNAL: {
                    dealOnSignal(msg.arg1);
                    break;
                }
                default:
                    break;
            }
            Log.d(TAG, "mThreadHandler handleMessage " + msg.what + " over");
            return true;
        });
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (mStartSync) {
            Toast.makeText(getActivity(), R.string.sync_tv_provider, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            updateSearchUi(true, true, "Finishing search");
            if (mStartSearch) {
                sendFinishSearch(false);
            } else {
                finish();
            }
            return true;
        }
        return false;
    }

    private void updateSearchUi(final boolean stop, final boolean progressBarUi, final String reason) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            updateSearchUiDirectly(stop, progressBarUi, reason);
        });
    }

    private void updateSearchUiDirectly(boolean stop, boolean progressBarUi, String reason) {
        if (mSearch != null) {
            mSearch.setText(stop ? R.string.strStartSearch : R.string.strStopSearch);
        }
        if (mSetup != null) {
            mSetup.setEnabled(stop);
        }
        if (mOptional != null) {
            mOptional.setEnabled(stop);
        }
        Log.i(TAG, String.format("Search status \"%s\"", reason));
        if (mSearchStatus != null) {
            mSearchStatus.setText(String.format("%s\t%s", reason, ""));
        }
        if (mChannelHolder != null) {
            mChannelHolder.setVisibility(View.GONE);
        }
        if (mChannelInfo != null) {
            mChannelInfo.setText("" + "\t\t" + "" + "\t\t" + "");
        }
        if (mSearchProgress != null) {
            mSearchProgress.setIndeterminate(progressBarUi);
        }
    }

    private void dealOnSignal(int progress) {
        Log.d(TAG, "onSignal progress = " + progress);
        if (!mStartSearch) {
            Log.w(TAG, "onSignal but search is finished.");
            return;
        }
        if (mSearchProgress != null) {
            if (mSearchProgress.getProgress() == progress) {
                return;
            }
        }
        int found = getFoundServiceNumberOnSearch();
        setSearchProgress(progress);
        int strengthStatus = mParameterManager.getStrengthStatus();
        int qualityStatus = mParameterManager.getQualityStatus();
        setSearchStatus(String.format(Locale.ENGLISH, "Searching (%d%%)", progress), "");
        setStrengthAndQualityStatus(String.format(Locale.getDefault(), "Strength: %d%%", strengthStatus), String.format(Locale.getDefault(), "Quality: %d%%", qualityStatus), String.format(Locale.getDefault(), "Channel: %d", found));
        if (progress >= 100) {
            sendFinishSearch(true);
            if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_TKGS) {
                String msg = mParameterManager.getTKGSUserMessage();
                getActivity().runOnUiThread(() -> {
                    showSearchInformationDialog(getContext(), "showUserMsg", null , msg);
                });
            }
        }
    }

    private void sendFinishSearch(boolean sync) {
        mStartSearch = false;
        stopMonitoringSearch();
        DtvkitRequest.getInstance().request(mThreadHandler, () -> {
            Log.d(TAG, "sendFinishSearch over");
            if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7
                    && !mSearchByManualDiSEqC) {
                mParameterManager.getDvbsParaManager().getSatelliteWrap().autoDiseqcStop();
            } else {
                JSONArray args = new JSONArray();
                args.put(true);
                try {
                    DtvkitGlueClient.getInstance().request("Dvbs.finishSearch", args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (sync) {
                updateSearchUi(true, true, "Finishing search");
                onSearchFinished();
            }
        }, null);
    }

    private void setSearchProgress(final int progress) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (mSearchProgress != null && getActivity() != null && mStartSearch) {
                final ProgressBar bar = mSearchProgress;
                bar.setProgress(progress);
            }

        });
    }

    private void releaseHandler() {
        Log.d(TAG, "releaseHandler");
        if (mThreadHandler != null) {
            mThreadHandler.removeCallbacksAndMessages(null);
        }
        if (mHandlerThread != null) {
            mHandlerThread.getLooper().quitSafely();
        }
        mThreadHandler = null;
        mHandlerThread = null;
    }

    private int getFilterServiceTypeInSearch() {
        int ret = 0;
        try {
            JSONArray array = new JSONArray();
            array.put(ParameterManager.SIGNAL_QPSK);
            JSONObject obj = DtvkitGlueClient.getInstance().request("Dvb.GetFilterServiceTypeInSearch", array);
            if (obj != null) {
                ret = obj.optInt("data", 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Dvb.GetFilterServiceTypeInSearch failed");
        }
        return ret;
    }

    private void SetFilterServiceTypeInSearch(int type) {
        if (type == 0x1f || type == 0x18 || type == 0x07) {
            //filter all, just equals no filter
            type = 0;
        }
        try {
            JSONArray array = new JSONArray();
            array.put(ParameterManager.SIGNAL_QPSK);
            array.put(type);
            DtvkitGlueClient.getInstance().request("Dvb.SetFilterServiceTypeInSearch", array);
        } catch (Exception e) {
            Log.w(TAG, "Dvb.SetFilterServiceTypeInSearch failed");
        }
    }

    /*
     * Filter Type Free channel: 0x01
     * Filter Type Scamble channel: 0x02
     * Filter Type network :        0x04 //not support in ui now
     * */
    private int getCurrentChannelType() {
        int ret = 0;//match ui type
        int filterType = getFilterServiceTypeInSearch();
        int channelType = filterType >> 3;
        if ((channelType & 0x03) == 0x03 || channelType == 0) {
            ret = 0;
        } else {
            ret = (channelType & 0x03);
        }
        return ret;
    }

    /*param range: 0 - 2*/
    private void updateChannelType(int type) {
        if (type == 0) {
            type = 0x03;
        }
        int filterType = getFilterServiceTypeInSearch();
        if (type != 7 && filterType == 0) {
            filterType = 0x1f;
        }
        SetFilterServiceTypeInSearch((filterType & 0x07) + (type << 3));
    }

    /*
     * Filter Type service tv: 0x01
     * Filter Type service radio: 0x02
     * Filter Type service other : 0x04 //not support in ui now
     * */
    private int getCurrentServiceType() {
        int ret = 0;
        int filterType = getFilterServiceTypeInSearch();
        int serviceType = filterType & 0x07;
        if ((serviceType & 0x03) == 0x03 || serviceType == 0) {
            ret = 0;
        } else {
            ret = (serviceType & 0x03);
        }
        return ret;
    }

    /*param range: 0 - 2*/
    private void updateServiceType(int type) {
        if (type == 0) {
            type = 0x07;
        }
        int filterType = getFilterServiceTypeInSearch();
        if (type != 7 && filterType == 0) {
            filterType = 0x1f;
        }
        SetFilterServiceTypeInSearch((filterType & 0x18) + type);
    }

    public void finish() {
        //send search info to livetv if found any
        Log.d(TAG, "finish");
        Intent intent = new Intent();
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_TYPE_MANUAL_AUTO, mSearchType);
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_TYPE_DVBS_DVBT_DVBC, DtvkitDvbScanSelect.SEARCH_TYPE_DVBS);
        intent.putExtra(DtvkitDvbScanSelect.SEARCH_FOUND_SERVICE_NUMBER, mFoundServiceNumber);
        if (mFoundServiceNumber > 0) {
            String firstServiceName = "";
            try {
                if (mServiceList != null && mServiceList.length() > 0) {
                    String searchMode = DataManager.KEY_SEARCH_MODE_LIST[mDataManager.getIntParameters(DataManager.KEY_SEARCH_MODE)];
                    Log.i(TAG, "finish searchMode : " + searchMode);
                    String currentTransponder = "";
                    if ("transponder".equals(searchMode)) {
                        mParameterManager.getDvbsParaManager().getLnbIdList().get(0).getFirstText();
                        currentTransponder = mParameterManager.getDvbsParaManager().getCurrentTransponder();
                        Log.i(TAG, "finish CurrentTransponder : " + currentTransponder);
                    }
                    firstServiceName = mServiceList.getJSONObject(0).getString("name");
                    for (int i = 0; i < mServiceList.length(); i++) {
                        if (!mServiceList.getJSONObject(i).getBoolean("hidden") && (TextUtils.isEmpty(currentTransponder) || currentTransponder.equals(mServiceList.getJSONObject(i).getString("transponder").replaceAll("/","")))) {
                            firstServiceName = mServiceList.getJSONObject(i).getString("name");
                            break;
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "finish JSONException = " + e.getMessage());
            }
            intent.putExtra(DtvkitDvbScanSelect.SEARCH_FOUND_FIRST_SERVICE, firstServiceName);
            Log.d(TAG, "finish firstServiceName = " + firstServiceName);
            getActivity().setResult(RESULT_OK, intent);
        } else {
            if (getActivity() != null) {
                getActivity().setResult(RESULT_CANCELED, mSyncFinish ? intent : null);
            }
        }
        if (getActivity() != null) {
            getActivity().finish();
        } else if (getListener() != null) {
            getListener().onNext(DtvkitDvbsSetupFragment.this, "finish");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7
                || (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_TKGS && mSearchByManualTKGS) ) {
            // auto start searching in m7 mode
            if (!mStartSearch) {
                updateSearchUi(false, false, "Searching");
                startSearch();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mPvrStatusConfirmManager.registerCommandReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mPvrStatusConfirmManager.unRegisterCommandReceiver();
    }

    private void setUp() {
        if (getListener() != null) {
            getListener().onNext(DtvkitDvbsSetupFragment.this, DataPresenter.FRAGMENT_MANUAL_DISEQC);
        }
    }

    private JSONArray initSearchParameter() {
        String searchMode = null;
        JSONArray args = new JSONArray();
        int opType = DataPresenter.getOperateType();
        Log.i(TAG, "initSearchParameter OperateType = " + opType);
        if (opType == DvbsParameterManager.OPERATOR_M7
            || opType == DvbsParameterManager.OPERATOR_ASTRA_HD_PLUS
            || opType == DvbsParameterManager.OPERATOR_SKY_D
            || opType == DvbsParameterManager.OPERATOR_AIRTEL
            || opType == DvbsParameterManager.OPERATOR_SUNDIRECT
            || opType == DvbsParameterManager.OPERATOR_CANALPLUS
            || opType == DvbsParameterManager.OPERATOR_TIVUSAT) {
            args.put("quick");
            args.put(opType);
            args.put("fti");
        } else if(opType == DvbsParameterManager.OPERATOR_TKGS) {
            Log.i(TAG,"mSearchByManualTKGS " + mSearchByManualTKGS);
            args.put("quick");
            args.put(opType);
            args.put(mSearchByManualTKGS ? "manual" :  "fti");
        } else {
            /*[scanmode, network, {lnblist: [{lnb:1},{lnb:2},..]}]*/
            int id = mDataManager.getIntParameters(DataManager.KEY_SEARCH_MODE);
            searchMode = DataManager.KEY_SEARCH_MODE_LIST[id];
            Log.i(TAG, "initSearchParameter searchMode = " + searchMode);
            args.put(searchMode);//arg1
            args.put(mDataManager.getIntParameters(DataManager.KEY_DVBS_NIT) == 1);//arg2
        }
        // handle lnb list
        List<String> lnbList = mDvbsParameterManager.getLnbWrap().getLnbIdList();
        JSONObject lnbArgs = new JSONObject();
        JSONArray lnbArgs_array = new JSONArray();
        Log.i(TAG, "initSearchParameter lnbList = " + lnbList);
        try {
            for (String id : lnbList) {
                JSONObject obj = new JSONObject();
                if (mDvbsParameterManager.getSatelliteNameListSelected(id).size() > 0) {
                    obj.put("lnb", id);
                    lnbArgs_array.put(obj);
                }
            }
            Log.i(TAG, "initSearchParameter lnbArgs_array = " + lnbArgs_array);
            if (lnbArgs_array.length() > 0) {
                lnbArgs.put("lnblist", lnbArgs_array);
            } else {
                return null;
            }
            if (TextUtils.equals(DataManager.KEY_SEARCH_MODE_LIST[2], searchMode)) {
                List<SatelliteWrap.Satellite> satellites = mDvbsParameterManager.getSatelliteWrap().getSatelliteList(DvbsParameterManager.OPERATOR_DEFAULT);
                for (SatelliteWrap.Satellite satellite : satellites) {
                    if (satellite.getLinkedLnb() != 0) {
                        List<SatelliteWrap.Transponder> transponders = mDvbsParameterManager.getSatelliteWrap().getTransponderList(satellite.getName());
                        for (SatelliteWrap.Transponder transponder : transponders) {
                            if (transponder.isSelected()) {
                                mTransponderFrequency.append(transponder.getFreq()).append(" ");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "initSearchParameter error = " + e.getMessage());
            return null;
        }
        args.put(lnbArgs.toString());//arg3

        // some operators require bouquet id
        if (opType == DvbsParameterManager.OPERATOR_AIRTEL) {
            args.put(DvbsParameterManager.OPERATOR_AIRTEL_BOUQUETID);
        }
        else if (opType == DvbsParameterManager.OPERATOR_SUNDIRECT) {
            args.put(DvbsParameterManager.OPERATOR_SUNDIRECT_BOUQUETID);
        }
        else if (opType == DvbsParameterManager.OPERATOR_CANALPLUS) {
            args.put(DvbsParameterManager.OPERATOR_CANALPLUS_BOUQUETID);
        }

        return args;
    }

    private void startSearch() {
        if (mStartSearch) {
            Log.w(TAG, "Search is going on");
            return;
        }
        mStartSearch = true;
        DtvkitRequest.getInstance().request(mThreadHandler, () -> {
            boolean success = false;
            if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7
                    && !mSearchByManualDiSEqC) {
                success = mParameterManager.getDvbsParaManager().getSatelliteWrap().autoDiSEqcRecognize();
            } else {
                try {
                    JSONArray args = initSearchParameter();
                    if (args != null) {
                        Log.i(TAG, "search parameter:" + args);
                        JSONObject resultObj = DtvkitGlueClient.getInstance().request("Dvbs.startSearchEx", args);
                        if (DataPresenter.getOperateType() != DvbsParameterManager.OPERATOR_TKGS &&
                                !resultObj.getString("data").equals("true")) {
                            throw new IllegalStateException(resultObj.getString("data"));
                        }
                        success = true;
                    } else {
                        updateSearchUi(true, false, getString(R.string.invalid_parameter));
                    }
                } catch (Exception e) {
                    updateSearchUi(true, false, e.getMessage());
                }
            }
            mStartSearch = success;
            if (success) {
                DataProviderManager.putBooleanValue(getActivity(), ConstantManager.KEY_IS_SEARCHING, true);
                startMonitoringSearch();
                mFoundServiceNumber = 0;
                mServiceList = null;
                mParameterManager.saveChannelIdForSource(-1);
            }
        }, null);
    }

    private void onSearchFinished() {
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
        mStartSync = true;
        setSearchStatus("Updating guide");
        startMonitoringSync();

        Log.i(TAG, String.format("inputId: %s", inputId));
        Bundle parameters = new Bundle();
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, "DVB-S");
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO);
        Intent intent = new Intent(getActivity(), com.droidlogic.dtvkit.inputsource.DtvkitEpgSync.class);

        String searchMode = DataManager.KEY_SEARCH_MODE_LIST[mDataManager.getIntParameters(DataManager.KEY_SEARCH_MODE)];
        if (mSearchByManualTKGS) {
            parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL);
        }
        if ("transponder".equals(searchMode)) {
            parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL);
            Log.i(TAG, "Updating guide TransponderFrequency: " + mTransponderFrequency);
            parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY, mTransponderFrequency.toString());
        }
        intent.putExtra("inputId", inputId);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_CHANNEL, (mFoundServiceNumber > 0));
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
        getActivity().startService(intent);
        DataProviderManager.putBooleanValue(getActivity(), ConstantManager.KEY_IS_SEARCHING, false);
    }

    private void startMonitoringSearch() {
        DtvkitGlueClient.getInstance().registerSignalHandler(mHandler);
    }

    private void stopMonitoringSearch() {
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
    }

    private void startMonitoringSync() {
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mReceiver,
                new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
    }

    private void stopMonitoringSync() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mReceiver);
    }

    private void setSearchStatus(final String status) {
        setSearchStatus(status, "");
    }

    private void setSearchStatus(final String status, final String description) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, String.format("Search status \"%s\"", status));
                if (mSearchStatus != null) {
                    mSearchStatus.setText(String.format("%s\t%s", status, description));
                }
            }
        });
    }

    private void setProgressBar(boolean status) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (mSearchProgress != null) {
                mSearchProgress.setIndeterminate(status);
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
            progress = data.getInt("progress");
        } catch (JSONException e) {
            Log.e(TAG, "getSearchProcess Exception = " + e.getMessage());
        }
        return progress;
    }

    private void setStrengthAndQualityStatus(final String strengthStatus, final String qualityStatus, final String channel) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (TextUtils.isEmpty(strengthStatus) && TextUtils.isEmpty(qualityStatus)) {
                if (mChannelHolder != null) {
                    mChannelHolder.setVisibility(View.GONE);
                }
            } else {
                if (mChannelHolder != null) {
                    mChannelHolder.setVisibility(View.VISIBLE);
                }
            }
            if (mChannelInfo != null) {
                mChannelInfo.setText(strengthStatus + "\t\t" + qualityStatus + "\t\t" + channel);
            }
        });
    }

    @Override
    public void onLifecycleChanged(int lifecycle) {
        super.onLifecycleChanged(lifecycle);
        Log.e(TAG, "onLifecycleChanged => " + lifecycle);
        if (lifecycle == SearchStageFragment.ACTIVITY_LIFECYCLE_ON_STOP) {
            if (mStartSearch) {
                sendFinishSearch(false);
            }
            releaseHandler();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_SKY_D
            || DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_ASTRA_HD_PLUS) {
            Log.i(TAG, "onHiddenChanged hidden => " + hidden + ", HideByDiSEqCMsg=" + mHideByDiSEqCMsg);
            if (hidden) {
                if (mHideByDiSEqCMsg) {
                    // abort search
                    sendFinishSearch(false);
                }
            } else {
                if (mHideByDiSEqCMsg) {
                    // restart search
                    startSearch();
                }
                mHideByDiSEqCMsg = false;
            }
        }
    }

    private void showSearchInformationDialog(final Context context, String searchType, List<String> data, String msg) {
        if (context == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(context, R.layout.confirm_search, null);
        final TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        final ListView listView = dialogView.findViewById(R.id.dialog_list);
        final Button confirm = dialogView.findViewById(R.id.confirm);
        final Button cancel = dialogView.findViewById(R.id.cancel);

        if ("TkgsVersionNotChangedNotify".equals(searchType)) {
            dialogTitle.setText(R.string.tkgs_setup_channel_list_change);
            confirm.requestFocus();
            alert.setCancelable(false);
            cancel.setOnClickListener(v -> {
                mParameterManager.setTKGSVersionCheckReply(false);
                alert.dismiss();
            });
            confirm.setOnClickListener(v -> {
                mParameterManager.setTKGSVersionCheckReply(true);
                alert.dismiss();
            });
        } else if ("TkgsServiceListNotify".equals(searchType)) {
            dialogTitle.setText(R.string.tkgs_setup_prefered_list_Type);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),android.R.layout.simple_list_item_1, data);
            listView.setAdapter(adapter);
            listView.setVisibility(View.VISIBLE);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mParameterManager.setTKGSServiceList(data.get(position));
                    alert.dismiss();
                }
            });
            confirm.setVisibility(View.GONE);
            cancel.setVisibility(View.GONE);
        } else if ("showUserMsg".equals(searchType)) {
            if (TextUtils.isEmpty(msg)) {
                return;
            }
            dialogTitle.setText(mSearchByManualTKGS ? "Update has been successfully completed.\n" + msg : msg);
            confirm.setVisibility(View.GONE);
            cancel.setVisibility(View.GONE);
            alert.setCancelable(true);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    alert.dismiss();
                }
            }, 30000);
            alert.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                    alert.dismiss();
                    return false;
                }
            });
        }

        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        alert.setView(dialogView);
        alert.show();
        WindowManager.LayoutParams params = alert.getWindow().getAttributes();
        params.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                500, context.getResources().getDisplayMetrics());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        alert.getWindow().setAttributes(params);
        alert.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
    }
}
