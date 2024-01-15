package com.droidlogic.fragment;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.droidlogic.dtvkit.inputsource.searchguide.DataPresenter;
import com.droidlogic.fragment.ItemAdapter.ItemDetail;
import com.droidlogic.fragment.ItemListView.ListItemFocusedListener;
import com.droidlogic.fragment.ItemListView.ListItemSelectedListener;
import com.droidlogic.fragment.ItemListView.ListSwitchedListener;
import com.droidlogic.fragment.ItemListView.ListTypeSwitchedListener;
//import com.droidlogic.fragment.R.color;
import com.droidlogic.fragment.dialog.CustomDialog;
import com.droidlogic.fragment.dialog.DialogCallBack;
import com.droidlogic.fragment.dialog.DialogManager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.media.tv.TvInputInfo;
import android.widget.ProgressBar;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.widget.Toast;

import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;

import com.droidlogic.dtvkit.inputsource.DtvkitEpgSync;
import com.droidlogic.dtvkit.inputsource.R;

public class ScanDishSetupFragment extends com.droidlogic.dtvkit.inputsource.searchguide.SearchStageFragment {

    private static final String TAG = "ScanDishSetupFragment";
    private ItemAdapter mItemAdapterItem = null;
    private ItemAdapter mItemAdapterOption = null;
    private ItemAdapter mItemAdapterSatellites = null;
    private ItemListView mListViewItem = null;
    private ItemListView mListViewOption = null;
    private ItemListView mListSatellites = null;
    private LinkedList<ItemDetail> mItemDetailItem = new LinkedList<ItemDetail>();
    private LinkedList<ItemDetail> mItemDetailOption = new LinkedList<ItemDetail>();
    private LinkedList<ItemDetail> mItemDetailSatellites = new LinkedList<ItemDetail>();
    private String mCurrentListType = ParameterManager.ITEM_LNB;
    private String mCurrentListFocus = ItemListView.LIST_LEFT;
    private ParameterManager mParameterManager;
    private LinearLayout mSatelliteQuickKey;
    private LinearLayout mSatelliteQuickKey1;
    private LinearLayout mSatelliteQuickKey2;
    private TextView mItemTitleTextView;
    private TextView mItemTitleTextView2;
    private TextView mOptionTitleItemTextView;
    private LinearLayout mStrengthContainer;
    private LinearLayout mQualityContainer;
    private ProgressBar mStrengthProgressBar;
    private ProgressBar mQualityProgressBar;
    private TextView mStrengthTextView;
    private TextView mQualityTextView;
    private DialogManager mDialogManager = null;
    private boolean mStartTuneActionSuccessful = false;
    private boolean isTransponder = false;
    private int mOperatorCode = DvbsParameterManager.OPERATOR_DEFAULT;

    private TimerTask task = new TimerTask() {
        public void run() {
            (getActivity()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mParameterManager == null) {
                        return;
                    }
                    int strength = 0;
                    int quality = 0;
                    if (mStartTuneActionSuccessful) {
                        strength = mParameterManager.getStrengthStatus();
                        quality = mParameterManager.getQualityStatus();
                    }
                    if (mStrengthProgressBar != null && mQualityProgressBar != null &&
                            mStrengthTextView != null && mQualityTextView != null) {
                        mStrengthProgressBar.setProgress(strength);
                        mQualityProgressBar.setProgress(quality);
                        mStrengthTextView.setText(strength + "%");
                        mQualityTextView.setText(quality + "%");
                        //Log.d(TAG, "run task get strength and quality");
                    }
                }
            });
        }
    };
    private Timer timer = new Timer();
    private boolean mScheduled = false;

    private HandlerThread mHandlerThread;
    private Handler  mThreadHandler;

    private final int MSG_START_TUNE_ACTION = 0;
    private final int MSG_STOP_TUNE_ACTION = 1;
    private final int MSG_STOP_RELEAS_ACTION = 2;
    private final int VALUE_START_TUNE_DELAY = 1200;
    private boolean mIsStarted = false;
    private boolean mIsReleasing = false;

    private void initHandlerThread() {
        mHandlerThread = new HandlerThread("check-message-coming");
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_START_TUNE_ACTION:
                        if (mParameterManager != null) {
                            mIsStarted = true;
                            if (null == mParameterManager.startTuneAction()) {
                                Log.d(TAG, "mStartTuneActionSuccessful = false");
                                mStartTuneActionSuccessful = false;
                            } else {
                                Log.d(TAG, "mStartTuneActionSuccessful = true");
                                mStartTuneActionSuccessful = true;
                            }
                        }
                        break;
                    case MSG_STOP_TUNE_ACTION:
                        if (mParameterManager != null) {
                            mIsStarted = false;
                            mParameterManager.stopTuneAction();
                        }
                        break;
                    case MSG_STOP_RELEAS_ACTION:
                        if (!mThreadHandler.hasMessages(MSG_STOP_RELEAS_ACTION)) {
                            releaseHandler();
                        } else {
                            releaseMessage();
                        }
                        break;
                    default:
                        break;
                }
            }
        };
    }

    public ScanDishSetupFragment() {
        Log.d(TAG, "ScanDishSetupFragment create");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandlerThread();
        Log.d(TAG, "onCreate");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        mParameterManager = getParameterManager();
        mDialogManager = getDialogManager();
        String currentList = ParameterManager.ITEM_LNB;
        View rootView = inflater.inflate(R.layout.fragment_dish_setup, container, false);
        mSatelliteQuickKey1 = (LinearLayout) rootView.findViewById(R.id.function_key1);
        mSatelliteQuickKey2 = (LinearLayout) rootView.findViewById(R.id.function_key2);
        creatFour1();
        creatFour2();
        if (getArguments() != null) {
            mOperatorCode = getArguments().getInt("operator_code", DvbsParameterManager.OPERATOR_DEFAULT);
        }
        mParameterManager.getDvbsParaManager().setCurrentOperator(mOperatorCode);
        mStrengthContainer = (LinearLayout) rootView.findViewById(R.id.strength_container);
        mQualityContainer = (LinearLayout) rootView.findViewById(R.id.quality_container);
        mStrengthProgressBar = (ProgressBar)rootView.findViewById(R.id.proBar_strength);
        mQualityProgressBar = (ProgressBar)rootView.findViewById(R.id.proBar_quality);
        mStrengthTextView = (TextView)rootView.findViewById(R.id.strength_percent);
        mQualityTextView = (TextView)rootView.findViewById(R.id.quality_percent);

        mListViewItem = (ItemListView) rootView.findViewById(R.id.lv_item);
        mListViewOption = (ItemListView) rootView.findViewById(R.id.lv_option);
        mListSatellites = (ItemListView) rootView.findViewById(R.id.lv_item2);
        mItemDetailItem.addAll(mParameterManager.getDvbsParaManager().getLnbNameList());

        String dPosition = "";
        LinkedList<ItemDetail> mLnbIdList = mParameterManager.getDvbsParaManager().getLnbIdList();
        if (!mLnbIdList.isEmpty()) {
            dPosition = mLnbIdList.get(0).getFirstText();
        }

        mParameterManager.getDvbsParaManager().setCurrentLnbId(dPosition);
        mItemDetailSatellites.addAll(mParameterManager.getDvbsParaManager().getSatelliteNameList());
        mItemAdapterItem = new ItemAdapter(mItemDetailItem, getActivity());
        mItemAdapterSatellites = new ItemAdapter(mItemDetailSatellites, getActivity());
        mListViewItem.setAdapter(mItemAdapterItem);
        mListViewItem.setCurrentListSide(ItemListView.LIST_LEFT);
        mListViewItem.setSelection(0);
        mListSatellites.setAdapter(mItemAdapterSatellites);

        mItemTitleTextView = (TextView) rootView.findViewById(R.id.tv_item_title);
        mItemTitleTextView2 = (TextView) rootView.findViewById(R.id.tv_item_title2);
        mItemTitleTextView.setText(R.string.list_type_lnb);
        mItemTitleTextView2.setText(getActivity().getResources().getString(R.string.list_type_satellite) + "(LNB" + dPosition + ")");

        mListViewItem.setListItemSelectedListener(mListItemSelectedListener);
        mListViewItem.setListItemFocusedListener(mListItemFocusedListener);
        mListViewItem.setListSwitchedListener(mListSwitchedListener);
        mListViewItem.setListTypeSwitchedListener(mListTypeSwitchedListener);
        mListViewItem.setDataCallBack(mSingleSelectDialogCallBack);
        mListViewItem.setDtvkitGlueClient(mParameterManager.getDtvkitGlueClient());

        mListSatellites.setListItemSelectedListener(mListItemSelectedListener);
        mListSatellites.setListItemFocusedListener(mListItemFocusedListener);
        mListSatellites.setListSwitchedListener(mListSwitchedListener);
        mListSatellites.setListTypeSwitchedListener(mListTypeSwitchedListener);
        mListSatellites.setDataCallBack(mSingleSelectDialogCallBack);

        String currentLnb = mParameterManager.getDvbsParaManager().getCurrentLnbId();
        mItemDetailOption.addAll(mParameterManager.getDvbsParaManager().getLnbParamsWithId(currentLnb));
        mItemAdapterOption = new ItemAdapter(mItemDetailOption, getActivity());
        mListViewOption.setAdapter(mItemAdapterOption);


        mListViewOption.setListItemSelectedListener(mListItemSelectedListener);
        mListViewOption.setListItemFocusedListener(mListItemFocusedListener);
        mListViewOption.setListSwitchedListener(mListSwitchedListener);
        mListViewOption.setListTypeSwitchedListener(mListTypeSwitchedListener);
        mListViewOption.setListType(ItemListView.ITEM_OPTION);
        mListViewOption.setDataCallBack(mSingleSelectDialogCallBack);

        if ((TextUtils.equals(currentList, ItemListView.ITEM_LNB) && mItemDetailItem.size() > 0)) {
            mListViewItem.requestFocus();
            mListViewOption.cleanChoosed();
            mListSatellites.cleanChoosed();
            mParameterManager.getDvbsParaManager().setCurrentListDirection("left");
            View selectedView = mListViewItem.getSelectedView();
            if (selectedView != null) {
                mListViewItem.setChoosed(selectedView);
            }
            mListViewItem.setListType(mParameterManager.getDvbsParaManager().getCurrentListType());
        } else {
            mListViewItem.cleanChoosed();
            mListSatellites.cleanChoosed();
            mCurrentListFocus = "right";
            mParameterManager.getDvbsParaManager().setCurrentListDirection("right");
            View selectedView = mListViewOption.getSelectedView();
            if (selectedView != null) {
                mListViewOption.setChoosed(selectedView);
            }
        }

        mListViewOption.setOnItemSelectedListener(mListViewOption);
        mListViewItem.setOnItemSelectedListener(mListViewItem);
        initStrengthQualityUpdate();

        mParameterManager.getUbFreqs();

        return rootView;
    }

    private void releaseHandler() {
        Log.d(TAG, "releaseHandler");

        mIsReleasing = true;
        if (mThreadHandler != null) {
            mThreadHandler.removeCallbacksAndMessages(null);
            mThreadHandler = null;
        }

        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (getListener() == null) {
                getActivity().finish();
            } else {
                getListener().onNext(ScanDishSetupFragment.this, "back");
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mScheduled) {
            abortStrengthQualityUpdate();
        }
        if (mIsStarted) {
            mIsStarted = false;
            stopTune();
        }
        if (!mIsReleasing) {
            mIsReleasing = true;
            releaseMessage();
        }
        Log.d(TAG, "onStop");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //releaseHandler();
        //mParameterManager.stopTuneAction();
        Log.d(TAG, "onDestroy");
    }

    private void changeSatelliteQuickKeyLayout() {
        mSatelliteQuickKey.removeAllViews();
        mSatelliteQuickKey.addView(mSatelliteQuickKey1);
        mSatelliteQuickKey.addView(mSatelliteQuickKey2);
    }

    private void creatFour1() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = (View) inflater.inflate(R.layout.four_display1, null);
        mSatelliteQuickKey1.removeAllViews();
        mSatelliteQuickKey1.addView(view);
    }

    private void creatFour2() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = (View) inflater.inflate(R.layout.four_display2, null);
        mSatelliteQuickKey2.removeAllViews();
        mSatelliteQuickKey2.addView(view);
    }

    private void createConfirmAndExit1() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = (View) inflater.inflate(R.layout.confirm_exit_display, null);
        mSatelliteQuickKey1.removeAllViews();
        mSatelliteQuickKey1.addView(view);
    }

    private void createSatelliteAndScan2() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = (View) inflater.inflate(R.layout.satellite_scan_display, null);
        mSatelliteQuickKey2.removeAllViews();
        mSatelliteQuickKey2.addView(view);
    }

    private void createSetLimitAndSetLocation1() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = (View) inflater.inflate(R.layout.limit_location_display, null);
        mSatelliteQuickKey1.removeAllViews();
        mSatelliteQuickKey1.addView(view);
    }

    private void creatEditAndExit2() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = (View) inflater.inflate(R.layout.edit_exit_wheel_display, null);
        mSatelliteQuickKey2.removeAllViews();
        mSatelliteQuickKey2.addView(view);
    }

    private void initStrengthQualityUpdate() {
        startTune();
        if (mScheduled) {
            abortStrengthQualityUpdate();
        }
        timer.schedule(task, 1000, 1000);
        mScheduled = true;
        Log.d(TAG, "initStrengthQualityUpdate");
    }

    private void abortStrengthQualityUpdate() {
        if (mScheduled) {
            mScheduled = false;
            task.cancel();
            timer.cancel();
            Log.d(TAG, "abortStrengthQualityUpdate");
        }
    }

    private CustomDialog mCurrentCustomDialog = null;
    private CustomDialog mCurrentSubCustomDialog = null;
    private DialogCallBack mSingleSelectDialogCallBack = new DialogCallBack() {
        @Override
        public void onStatusChange(View view, String parameterKey, Bundle data) {
            Log.d(TAG, "onStatusChange parameterKey = " + parameterKey + ", data = " + data);
            String lnb = mParameterManager.getDvbsParaManager().getCurrentLnbId();
            switch (parameterKey) {
                case ParameterManager.KEY_SATELLITE:
                case ParameterManager.KEY_TRANSPONDER: {
                    int position = data.getInt("position");
                    if ("selected".equals(data.getString("action"))) {
                        if (parameterKey.equals(ParameterManager.KEY_SATELLITE)) {
                            List<String> sates = mParameterManager.getDvbsParaManager().getSatelliteNameListSelected();
                            String testSatellite = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                            if (!testSatellite.equals(sates.get(position))) {
                                mParameterManager.getDvbsParaManager().setCurrentSatellite(sates.get(position));
                                startTune();
                            }
                        } else {
                            LinkedList<ItemDetail> tps = mParameterManager.getDvbsParaManager().getTransponderList();
                            String testTp = mParameterManager.getDvbsParaManager().getCurrentTransponder();
                            if (!testTp.equals(tps.get(position).getFirstText())) {
                                //if support multi-tp
                                //mParameterManager.getDvbsParaManager().setCurrentTransponder(tps.get(position).getFirstText());
                                //else support single-tp
                                mParameterManager.getDvbsParaManager().selectSingleTransponder(tps.get(position).getFirstText(), true);
                                startTune();
                            }
                        }
                    }
                    if (mCurrentCustomDialog != null) {
                        mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                mCurrentCustomDialog.getDialogKey(), position);
                    }
                    mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                    break;
                }
                case ParameterManager.KEY_LNB_TYPE:
                    if (data != null && "selected".equals(data.getString("action"))) {
                        int pos = data.getInt("position");
                        int lnbType = 0;
                        if (pos == 1) {
                            lnbType = 4;
                        } else if (pos == 2) {
                            lnbType = 1;
                        } else {
                            lnbType = pos;
                        }
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getLnbInfo()
                                .editLnbType(lnbType);
                        if (lnbType == 0) {
                            mParameterManager.getDvbsParaManager()
                                    .getLnbWrap().getLnbById(lnb).getLnbInfo()
                                    .editLnb22Khz("false");
                        } else if (lnbType == 1 || lnbType == 4) {
                            mParameterManager.getDvbsParaManager()
                                    .getLnbWrap().getLnbById(lnb).getLnbInfo()
                                    .editLnb22Khz("auto");
                        }
                        if (mCurrentCustomDialog != null && TextUtils.equals(parameterKey, mCurrentCustomDialog.getDialogKey())) {
                            mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(), mCurrentCustomDialog.getDialogKey(), data.getInt("position"));
                            mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        }
                        if (pos == 3) {
                            mCurrentSubCustomDialog = mDialogManager.buildLnbCustomItemDialog(mSingleSelectDialogCallBack);
                            if (mCurrentSubCustomDialog != null) {
                                mCurrentSubCustomDialog.showDialog();
                            }
                        }
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        startTune();
                    }
                    break;
                case ParameterManager.KEY_LNB_CUSTOM:
                    if (data != null && "onClick".equals(data.getString("action"))) {
                        if ("ok".equals(data.getString("button"))) {
                            Log.d(TAG, "ok in clicked");
                            int lowLocal = data.getInt("lowLocal");
                            int highLocal = data.getInt("highLocal");
                            mParameterManager.getDvbsParaManager()
                                    .getLnbWrap().getLnbById(lnb).getLnbInfo()
                                    .updateLnbTypeFreq(lowLocal, highLocal);
                            startTune();
                        } else if ("cancel".equals(data.getString("button"))) {
                            Log.d(TAG, "cancel in clicked");
                        }
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                    }
                    break;
                case ParameterManager.KEY_UNICABLE: {
                    int unicable_position = data.getInt("position");
                    boolean isLeftAction = "left".equals(data.getString("action"));
                    boolean isRightAction = "right".equals(data.getString("action"));
                    boolean isSelectAction = "selected".equals(data.getString("action"));
                    if (unicable_position == 0 && (isLeftAction || isRightAction)) {
                        int current_version = mParameterManager.getDvbsParaManager()
                            .getCurrentLnbParaIntValue("unicable_version");
                        int min = 0;
                        int max = CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_UNICABLE_LIST.length - 1;
                        if (isLeftAction) {
                            if (current_version > min) {
                                current_version --;
                            } else {
                                current_version = max;
                            }
                        } else {
                            if (current_version < max) {
                                current_version ++;
                            } else {
                                current_version = min;
                            }
                        }
                        int channel = mParameterManager.getDvbsParaManager().getCurrentLnbParaIntValue("unicable_chan");
                        if (current_version == 1 && channel > 7) {
                            mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getUnicable()
                                .editUnicableChannel(0,
                                        mParameterManager.getDvbsParaManager().getUbFrequency(0));
                        }
                        mParameterManager.getDvbsParaManager()
                            .getLnbWrap().getLnbById(lnb).editUnicableVersion(current_version);
                        startTune();
                    } else if (unicable_position == 3 && (isLeftAction || isRightAction)) {
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getUnicable()
                                .switchUnicablePosition();
                        startTune();
                    } else if (unicable_position == 1 && (isLeftAction || isRightAction)) {
                        int min = 0;
                        int version = mParameterManager.getDvbsParaManager()
                            .getCurrentLnbParaIntValue("unicable_version");
                        int max = (version == 2) ? 31 : 7;
                        int unicableChannel = mParameterManager.getDvbsParaManager().getCurrentLnbParaIntValue("unicable_chan");
                        int updateChannel = unicableChannel;
                        if (isLeftAction) {
                            if (updateChannel > min) {
                                updateChannel --;
                            } else {
                                updateChannel = max;
                            }
                        } else {
                            if (updateChannel < max) {
                                updateChannel ++;
                            } else {
                                updateChannel = min;
                            }
                        }
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getUnicable()
                                .editUnicableChannel(updateChannel,
                                        mParameterManager.getDvbsParaManager().getUbFrequency(updateChannel));
                        startTune();
                    } else if (unicable_position == 2 && (isLeftAction || isRightAction || isSelectAction)) {
                        int channel = mParameterManager.getDvbsParaManager().getCurrentLnbParaIntValue("unicable_chan");
                        int freq = mParameterManager.getDvbsParaManager().getUbFrequency(channel);
                        int min = 950;
                        int max = 2150;
                        if (isLeftAction) {
                            if (freq > min) {
                                freq --;
                            } else {
                                freq = max;
                            }
                        } else if (isRightAction){
                            if (freq < max) {
                                freq ++;
                            } else {
                                freq = min;
                            }
                        } else {
                            if (freq < max) {
                                freq += 100;
                                if (freq > max) {
                                    freq = max;
                                }
                            } else {
                                freq = min;
                            }
                        }
                        mParameterManager.getDvbsParaManager().setUbFrequency(channel, freq);
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getUnicable()
                                .editUnicableChannel(channel, freq);
                        startTune();
                    }
                    if (mCurrentCustomDialog != null) {
                        mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                mCurrentCustomDialog.getDialogKey(), unicable_position);
                    }
                    mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                    break;
                }
                case ParameterManager.KEY_UB_FREQUENCY:
                    break;
                case ParameterManager.KEY_LNB_POWER: {
                    int position = data.getInt("position");
                    if ("selected".equals(data.getString("action"))) {
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getLnbInfo()
                                .editLnbPower((position > 0) ? true : false);
                        if (mCurrentCustomDialog != null) {
                            mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                    mCurrentCustomDialog.getDialogKey(), position);
                        }
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        startTune();
                    }
                    break;
                }
                case ParameterManager.KEY_22_KHZ: {
                    int position = data.getInt("position");
                    if ("selected".equals(data.getString("action"))) {
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getLnbInfo()
                                .editLnb22Khz((position > 0) ? "on" : "off");
                        if (mCurrentCustomDialog != null) {
                            mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                    mCurrentCustomDialog.getDialogKey(), position);
                        }
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        startTune();
                    }
                    break;
                }
                case ParameterManager.KEY_TONE_BURST: {
                    int position = data.getInt("position");
                    if ("selected".equals(data.getString("action"))) {
                        if (position > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_TONE_BURST_LIST.length - 1)) {
                            position = 0;
                        }
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb)
                                .editToneBurst(CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_TONE_BURST_LIST[position]);
                        if (mCurrentCustomDialog != null) {
                            mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                    mCurrentCustomDialog.getDialogKey(), position);
                        }
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        startTune();
                    }
                    break;
                }
                case ParameterManager.KEY_DISEQC1_0: {
                    int position = data.getInt("position");
                    if ("selected".equals(data.getString("action"))) {
                        if (position > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_0_LIST.length - 1)) {
                            position = 0;
                        }
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb)
                                .editCSwitch(position);
                        if (mCurrentCustomDialog != null) {
                            mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                    mCurrentCustomDialog.getDialogKey(), position);
                        }
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        startTune();
                    }
                    break;
                }
                case ParameterManager.KEY_DISEQC1_1: {
                    int position = data.getInt("position");
                    if ("selected".equals(data.getString("action"))) {
                        if (position > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_1_LIST.length - 1)) {
                            position = 0;
                        }
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb)
                                .editUSwitch(position);
                        if (mCurrentCustomDialog != null) {
                            mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                    mCurrentCustomDialog.getDialogKey(), position);
                        }
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        startTune();
                    }
                    break;
                }
                case ParameterManager.KEY_DISEQC2_x: {
                    int position = data.getInt("position");
                    if ("selected".equals(data.getString("action"))) {
                        if (position > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC2_X_LIST.length - 1)) {
                            position = 0;
                        }
                        if (mCurrentCustomDialog != null) {
                            mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(),
                                    mCurrentCustomDialog.getDialogKey(), position);
                        }
                        int motor = mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb).getMotor();
                        position = (motor > 2 && position == 1) ? 3 : (motor <= 2 && position == 1) ? 1 : (motor > 2) ? 2 : 0;
                        mParameterManager.getDvbsParaManager()
                                .getLnbWrap().getLnbById(lnb)
                                .editDiseqc2Abilities(position);
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                    }
                    break;
                }
                case ParameterManager.KEY_MOTOR:
                    if (data != null && "selected".equals(data.getString("action"))) {
                        boolean isDiseqcX_3 = false;
                        mParameterManager.getDvbsParaManager().getLnbWrap().getLnbById(lnb).editMotor(data.getInt("position"));
                        int position = data.getInt("position");
                        int diseqc2_x = mParameterManager.getDvbsParaManager().getLnbWrap().getLnbById(lnb).getDiseqc2Abilities();
                        diseqc2_x = (position > 2 && diseqc2_x % 2 == 1) ? 3 : (position <= 2 && diseqc2_x % 2 == 1) ? 1 : (position > 2) ? 2 : 0;
                        mParameterManager.getDvbsParaManager().getLnbWrap().getLnbById(lnb).editDiseqc2Abilities(diseqc2_x);
                        if (data.getInt("position") >= 1 && mCurrentCustomDialog != null && TextUtils.equals(parameterKey, mCurrentCustomDialog.getDialogKey())){
                            if (position == 2 || position == 4) {
                                isDiseqcX_3 = true;
                            }
                            String testSatellite = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                            if (!TextUtils.isEmpty(testSatellite)) {
                                mCurrentSubCustomDialog = mDialogManager.buildDiseqc1_2_ItemDialog(position, isDiseqcX_3, mSingleSelectDialogCallBack);
                                if (mCurrentSubCustomDialog != null) {
                                    mCurrentSubCustomDialog.showDialog();
                                }
                            } else {
                                Toast.makeText(getContext(), "Please select one satellite for diseqc setup", Toast.LENGTH_SHORT).show();
                            }
                        }
                        mCurrentCustomDialog.updateListView(mCurrentCustomDialog.getDialogTitle(), mCurrentCustomDialog.getDialogKey(), data.getInt("position"));
                        mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                        startTune();
                    }
                    break;
                case ParameterManager.KEY_DISEQC1_2: {
                    int motor = mParameterManager.getDvbsParaManager().getLnbWrap().getLnbById(lnb).getMotor();
                    boolean isDiseqcX_3 = (motor == 2 || motor == 4);
                    if (data != null && "selected".equals(data.getString("action"))) {
                        int diseqc1_2_position = data.getInt("position");
                        switch (diseqc1_2_position) {
                            case 3://dish limits east
                                mParameterManager.setDishELimits();
                                break;
                            case 4://dish limits west
                                mParameterManager.setDishWLimits();
                                break;
                            case 7: /*move*/ {
                                int direction = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("dish_dir");
                                int dishStep = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("dish_step");
                                mParameterManager.dishMove(direction, dishStep);
                                break;
                            }
                            case 8: /*stop move*/
                                mParameterManager.stopDishMove();
                                break;
                            case 9: /*save to position*/ {
                                String sateName = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                                mParameterManager.storeDishPosition(sateName);
                                break;
                            }
                            case 10: /*move to position*/ {
                                String sateName = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                                mParameterManager.moveDishToPosition(sateName);
                                break;
                            }
                            case 16: /*gotoxx*/ {
                                String sateName = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                                int locationIndex = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("diseqc_location");
                                String locationName = mParameterManager.getDvbsParaManager().getLnbWrap()
                                        .getLocationInfoByIndex(locationIndex).getName();
                                mParameterManager.getDvbsParaManager().getLnbWrap().actionToLocation(sateName, locationName);
                                break;
                            }
                            default:
                                break;
                        }
                    } else if (data != null && ("left".equals(data.getString("action")) || "right".equals(data.getString("action")))) {
                        int position = data.getInt("position");
                        boolean needBreak = false;
                        switch (position) {
                            case 0: /*satellite*/ {
                                List<String> satelliteList = mParameterManager.getDvbsParaManager().getSatelliteNameListSelected();
                                int indexMax = satelliteList.size() - 1;
                                int sateIndex = mParameterManager.getDvbsParaManager().getCurrentSatelliteIndex(true);
                                if ("left".equals(data.getString("action"))) {
                                    if (sateIndex != 0) {
                                        sateIndex = sateIndex - 1;
                                    } else {
                                        sateIndex = indexMax;
                                    }
                                } else {
                                    if (sateIndex < indexMax) {
                                        sateIndex = sateIndex + 1;
                                    } else {
                                        sateIndex = 0;
                                    }
                                }
                                mParameterManager.getDvbsParaManager().setCurrentSatellite(satelliteList.get(sateIndex));
                                int dish_Pos = mParameterManager.getDvbsParaManager()
                                        .getSatelliteWrap().getSatelliteByName(satelliteList.get(sateIndex)).getDishPos();
                                mParameterManager.getDvbsParaManager().setCurrentDiseqcValue("dish_pos", dish_Pos);
                                mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                                startTune();
                                break;
                            }
                            case 1: /*transponder*/ {
                                String sateName =  mParameterManager.getDvbsParaManager().getCurrentSatellite();
                                LinkedList<ItemDetail> tps = mParameterManager.getDvbsParaManager().getTransponderList(sateName);
                                int tpIndex = mParameterManager.getDvbsParaManager().getCurrentTransponderIndex();
                                int indexMax = tps.size() - 1;
                                if ("left".equals(data.getString("action"))) {
                                    if (tpIndex != 0) {
                                        tpIndex = tpIndex - 1;
                                    } else {
                                        tpIndex = indexMax;
                                    }
                                } else {
                                    if (tpIndex < indexMax) {
                                        tpIndex = tpIndex + 1;
                                    } else {
                                        tpIndex = 0;
                                    }
                                }
                                //if support multi-tp
                                //mParameterManager.getDvbsParaManager().setCurrentTransponder(tps.get(tpIndex).getFirstText());
                                //else support single-tp
                                mParameterManager.getDvbsParaManager().selectSingleTransponder(tps.get(tpIndex).getFirstText(), true);
                                mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                                startTune();
                                break;
                            }
                            case 2: /*dish limits status*/ {
                                int limitState = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("dish_limit_state");
                                if (limitState > 0) {
                                    limitState = 0;
                                } else {
                                    limitState = 1;
                                }
                                mParameterManager.getDvbsParaManager().setCurrentDiseqcValue("dish_limit_state", limitState);
                                mParameterManager.enableDishLimits(limitState == 1);
                                break;
                            }
                            case 5: /*dish move direction*/ {
                                int directionValue = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("dish_dir");
                                if ("left".equals(data.getString("action"))) {
                                    if (directionValue != 0) {
                                        directionValue = directionValue - 1;
                                    } else {
                                        directionValue = 2;
                                    }
                                } else {
                                    if (directionValue < 2) {
                                        directionValue = directionValue + 1;
                                    } else {
                                        directionValue = 0;
                                    }
                                }
                                mParameterManager.getDvbsParaManager().setCurrentDiseqcValue("dish_dir", directionValue);
                                break;
                            }
                            case 6: /*dish move step*/ {
                                int stepValue = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("dish_step");
                                if ("left".equals(data.getString("action"))) {
                                    if (stepValue != 0) {
                                        stepValue = stepValue - 1;
                                    } else {
                                        stepValue = 127;
                                    }
                                } else {
                                    if (stepValue < 127) {
                                        stepValue = stepValue + 1;
                                    } else {
                                        stepValue = 0;
                                    }
                                }
                                mParameterManager.getDvbsParaManager().setCurrentDiseqcValue("dish_step", stepValue);
                                break;
                            }
                            case 11: /* location city */ {
                                int index = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("diseqc_location");
                                List<String> locationNameList = mParameterManager.getDvbsParaManager().getLnbWrap()
                                        .getDiseqcLocationNames();
                                int indexMax = locationNameList.size() - 1;
                                if ("left".equals(data.getString("action"))) {
                                    if (index != 0) {
                                        index = index - 1;
                                    } else {
                                        index = indexMax;
                                    }
                                } else {
                                    if (index < indexMax) {
                                        index = index + 1;
                                    } else {
                                        index = 0;
                                    }
                                }
                                mParameterManager.getDvbsParaManager().setCurrentDiseqcValue("diseqc_location", index);
                                break;
                            }
                            case 12: /*location longitude direction*/
                            case 13: /*Location longitude*/
                            case 14: /*Location latitude direction*/
                            case 15: /*Location latitude*/{
                                int index = mParameterManager.getDvbsParaManager().getCurrentDiseqcValue("diseqc_location");
                                List<String> locationNameList = mParameterManager.getDvbsParaManager().getLnbWrap()
                                        .getDiseqcLocationNames();
                                if (index < (locationNameList.size() -1)) {
                                    if (("manual".equals(locationNameList.get(index)))) {
                                        boolean locationIsEast = mParameterManager.getDvbsParaManager().getLnbWrap()
                                                .getLocationInfoByIndex(index).isLongitudeEast();
                                        int locationLongitude = mParameterManager.getDvbsParaManager().getLnbWrap()
                                                .getLocationInfoByIndex(index).getLongitude();
                                        boolean locationIsNorth = mParameterManager.getDvbsParaManager().getLnbWrap()
                                                .getLocationInfoByIndex(index).isLatitudeNorth();
                                        int locationLatitude = mParameterManager.getDvbsParaManager().getLnbWrap()
                                                .getLocationInfoByIndex(index).getLatitude();
                                        if (position == 11) {
                                            locationIsEast = !locationIsEast;
                                        } else if (position == 12 || position == 14) {
                                            int value = 0;
                                            if (position == 12) value = locationLongitude;
                                            else value = locationLatitude;
                                            if ("left".equals(data.getString("action"))) {
                                                if (value > 0) {
                                                    value --;
                                                } else {
                                                    value = 1800;
                                                }
                                            } else {
                                                if (value < 1800) {
                                                    value ++;
                                                } else {
                                                    value = 0;
                                                }
                                            }
                                            if (position == 12) locationLongitude = value;
                                            else locationLatitude = value;
                                        } else if (position == 13) {
                                            locationIsNorth = !locationIsNorth;
                                        }
                                        mParameterManager.getDvbsParaManager().getLnbWrap().editManualLocation
                                                (locationIsEast, locationLongitude, locationIsNorth, locationLatitude);
                                    }
                                }
                                break;
                            }
                            default:
                                needBreak = true;
                                break;
                        }
                        if (!needBreak && mCurrentSubCustomDialog != null && mCurrentSubCustomDialog.isShowing()) {
                            mCurrentSubCustomDialog.updateDiseqc1_2_Dialog(motor, isDiseqcX_3);
                        } else {
                            Log.d(TAG, "mCurrentSubCustomDialog null or need break or not displayed");
                        }
                    }
                    break;
                }
                case ParameterManager.KEY_FUNCTION:
                    if (data != null) {
                        String action = data.getString("action");
                        String listType = data.getString("listType");
                        switch (action) {
                            case "add": {
                                if (ItemListView.ITEM_SATELLITE.equals(data.getString("listType"))) {
                                    mCurrentCustomDialog = mDialogManager.buildAddSatelliteDialogDialog(null, mSingleSelectDialogCallBack);
                                } else if (ItemListView.ITEM_TRANSPONDER.equals(data.getString("listType"))) {
                                    mCurrentCustomDialog = mDialogManager.buildAddTransponderDialogDialog(null, mSingleSelectDialogCallBack);
                                } else if (ItemListView.ITEM_LNB.equals(data.getString("listType"))) {
                                    mParameterManager.getDvbsParaManager().getLnbWrap().addEmptyLnb();
                                    mItemAdapterItem.reFill(mParameterManager.getDvbsParaManager().getLnbNameList());
                                    mCurrentCustomDialog = null;
                                } else {
                                    Log.d(TAG, "not sure");
                                    mCurrentCustomDialog = null;
                                }
                                if (mCurrentCustomDialog != null) {
                                    mCurrentCustomDialog.showDialog();
                                }
                                break;
                            }
                            case "edit":
                                if (ItemListView.ITEM_SATELLITE.equals(data.getString("listType"))) {
                                    mCurrentCustomDialog = mDialogManager.buildAddSatelliteDialogDialog(data.getString("parameter"), mSingleSelectDialogCallBack);
                                } else if (ItemListView.ITEM_TRANSPONDER.equals(data.getString("listType"))) {
                                    mCurrentCustomDialog = mDialogManager.buildAddTransponderDialogDialog(data.getString("parameter"), mSingleSelectDialogCallBack);
                                } else {
                                    //lnb list
                                    switchToRightList();
                                    mCurrentCustomDialog = null;
                                }
                                if (mCurrentCustomDialog != null) {
                                    mCurrentCustomDialog.showDialog();
                                }
                                break;
                            case "delete":
                                if (ItemListView.ITEM_SATELLITE.equals(data.getString("listType"))) {
                                    mCurrentCustomDialog = mDialogManager.buildRemoveSatelliteDialogDialog(data.getString("parameter"), mSingleSelectDialogCallBack);
                                } else if (ItemListView.ITEM_TRANSPONDER.equals(data.getString("listType"))) {
                                    mCurrentCustomDialog = mDialogManager.buildRemoveTransponderDialogDialog(data.getString("parameter"), mSingleSelectDialogCallBack);
                                } else if (ItemListView.ITEM_LNB.equals(data.getString("listType"))) {
                                    if (mParameterManager.getDvbsParaManager().getLnbIdList().size() < 2) {
                                        Toast.makeText(getContext(), "Cannot remove all lnbs", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    String focusedLnb = mParameterManager.getDvbsParaManager().getCurrentFocusedLnbId();
                                    String selectedLnb = mParameterManager.getDvbsParaManager().getCurrentLnbId();
                                    mParameterManager.getDvbsParaManager().getLnbWrap().removeLnb(focusedLnb);
                                    mListViewItem.cleanChoosed();
                                    LinkedList<ItemDetail> lnbs = mParameterManager.getDvbsParaManager().getLnbIdList();
                                    if (lnbs != null && lnbs.size() > 0) {
                                        if (selectedLnb != null && selectedLnb.equals(focusedLnb)) {
                                            //select first lnb
                                            mParameterManager.getDvbsParaManager().setCurrentLnbId(lnbs.get(0).getFirstText());
                                            startTune();
                                        } else {
                                            int selection = mListViewItem.getSelectedItemPosition();
                                            if (selection < lnbs.size()) {
                                                mParameterManager.getDvbsParaManager().setCurrentFocusedLnbId(lnbs.get(selection).getFirstText());
                                                if (mListViewItem.getSelectedView() != null) {
                                                    mListViewItem.setChoosed(mListViewItem.getSelectedView());
                                                }
                                            }
                                        }
                                        mCurrentCustomDialog = null;
                                    } else {
                                        mParameterManager.getDvbsParaManager().setCurrentLnbId("");
                                        mParameterManager.getDvbsParaManager().setCurrentFocusedLnbId("");
                                        stopTune();
                                    }
                                    mItemAdapterItem.reFill(mParameterManager.getDvbsParaManager().getLnbNameList());
                                    mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                                    if (!isTransponder) {
                                        mParameterManager.getDvbsParaManager().setCurrentListType(ParameterManager.ITEM_SATELLITE);
                                    } else {
                                        mParameterManager.getDvbsParaManager().setCurrentListType(ParameterManager.ITEM_TRANSPONDER);
                                    }
                                    mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getCurrentItemList());
                                } else {
                                    Log.d(TAG, "not sure");
                                    mCurrentCustomDialog = null;
                                }
                                if (mCurrentCustomDialog != null) {
                                    mCurrentCustomDialog.showDialog();
                                }
                                break;
                            case "scan":
                                stopAndRelease();
                                break;
                            default:
                                break;
                        }
                    }
                    break;
                case ParameterManager.KEY_ADD_SATELLITE:
                    if (data != null && "ok".equals(data.getString("button"))) {
                        String name_add = data.getString("value1");
                        boolean is_east_add = data.getBoolean("value2", true);
                        float position_add = Float.parseFloat(data.getString("value3"));
                        mParameterManager.getDvbsParaManager().getSatelliteWrap().addSatellite(name_add, is_east_add, position_add);
                        mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getSatelliteNameList());
                        // switchToLeftList();
                    }
                    break;
                case ParameterManager.KEY_EDIT_SATELLITE:
                    if (data != null && "ok".equals(data.getString("button"))) {
                        String new_name_edit = data.getString("value1");
                        boolean is_east_edit = data.getBoolean("value2", true);
                        float position_edit = Float.parseFloat(data.getString("value3"));
                        String old_name_edit = data.getString("value4");
                        mParameterManager.getDvbsParaManager().getSatelliteWrap().editSatellite(old_name_edit, new_name_edit, is_east_edit, position_edit);
                        if (old_name_edit.equals(mParameterManager.getDvbsParaManager().getCurrentSatellite())) {
                            mParameterManager.getDvbsParaManager().setCurrentSatellite(new_name_edit);
                            mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getSatelliteNameList());
                            mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                            startTune();
                        } else {
                            mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getSatelliteNameList());
                        }
                    }
                    break;
                case ParameterManager.KEY_REMOVE_SATELLITE:{
                    if (data != null && "ok".equals(data.getString("button"))) {
                        String name_remove = data.getString("value1");
                        mParameterManager.getDvbsParaManager().getSatelliteWrap().removeSatellite(name_remove);
                        String inputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
                        if (inputId != null) {
                            Log.d(TAG, "KEY_REMOVE_SATELLITE sync inputId = " + inputId);
                            Intent intent = new Intent(getActivity(), DtvkitEpgSync.class);
                            intent.putExtra("inputId", inputId);
                            intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
                            getActivity().startService(intent);
                        } else {
                            Log.d(TAG, "KEY_REMOVE_SATELLITE empty inputId");
                        }
                        //Log.d(TAG, "KEY_REMOVE_SATELLITE = " + mParameterManager.getCurrentSatellite());
                        if (TextUtils.equals(name_remove, mParameterManager.getDvbsParaManager().getCurrentSatellite())) {
                            mParameterManager.getDvbsParaManager().setCurrentSatellite("");
                            mParameterManager.getDvbsParaManager().setCurrentTransponder("");
                            mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getSatelliteNameList());
                            mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                            stopTune();
                        }
                        if (mListSatellites.getSelectedView() != null) {
                            mListSatellites.cleanChoosed();
                            mListSatellites.setChoosed(mListSatellites.getSelectedView());
                        }
                        mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getSatelliteNameList());
                    }
                    break;
                }
                case ParameterManager.KEY_ADD_TRANSPONDER:
                case ParameterManager.KEY_EDIT_TRANSPONDER:
                    if (data != null && "ok".equals(data.getString("button"))) {
                        String name_edit_t = data.getString("satellite");
                        String oldName = data.getString("oldName");
                        boolean isDvbs2 = data.getBoolean("system");
                        int new_frequency_edit_t = data.getInt("frequency");
                        String new_polarity_edit_t = data.getString("polarity");
                        int new_symbol_edit_t = data.getInt("symbol");
                        String new_fec_edit_t = data.getString("fec");
                        String new_modulation_edit_t = data.getString("modulation");
                        if (parameterKey == ParameterManager.KEY_ADD_TRANSPONDER) {
                            mParameterManager.getDvbsParaManager().getSatelliteWrap()
                                    .addTransponder(name_edit_t, new_frequency_edit_t,
                                            new_polarity_edit_t, new_symbol_edit_t, isDvbs2,
                                            new_modulation_edit_t, new_fec_edit_t, "auto");
                        } else {
                            mParameterManager.getDvbsParaManager().getSatelliteWrap()
                                    .editTransponder(name_edit_t, oldName, new_frequency_edit_t,
                                            new_polarity_edit_t, new_symbol_edit_t, isDvbs2,
                                            new_modulation_edit_t, new_fec_edit_t, "auto");
                            String testTransponder = mParameterManager.getDvbsParaManager().getCurrentTransponder();
                            if (testTransponder.equals(oldName)) {
                                //if support multi-tp
                                //mParameterManager.getDvbsParaManager()
                                //        .setCurrentTransponder(new_frequency_edit_t + new_polarity_edit_t + new_symbol_edit_t);
                                //else support single-tp
                                mParameterManager.getDvbsParaManager()
                                        .selectSingleTransponder(new_frequency_edit_t + new_polarity_edit_t + new_symbol_edit_t, true);
                                mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getCurrentItemList());
                                mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                                startTune();
                            }
                        }
                        mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getCurrentItemList());
                    }
                    break;
                case ParameterManager.KEY_REMOVE_TRANSPONDER:{
                    if (data != null && "ok".equals(data.getString("button"))) {
                        String sateName_remove_t = data.getString("satellite");
                        String tpName_remove_t = data.getString("transponder");
                        mParameterManager.getDvbsParaManager().getSatelliteWrap()
                                .removeTransponder(sateName_remove_t, tpName_remove_t);
                        String inputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
                        if (inputId != null) {
                            Log.d(TAG, "KEY_REMOVE_TRANSPONDER sync inputId = " + inputId);
                            Intent intent = new Intent(getActivity(), DtvkitEpgSync.class);
                            intent.putExtra("inputId", inputId);
                            intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
                            getActivity().startService(intent);
                        } else {
                            Log.d(TAG, "KEY_REMOVE_TRANSPONDER empty inputId");
                        }
                        if (mListSatellites.getSelectedView() != null) {
                            mListSatellites.cleanChoosed();
                            mListSatellites.setChoosed(mListSatellites.getSelectedView());
                        }
                        String testTransponder = mParameterManager.getDvbsParaManager().getCurrentTransponder();
                        if (testTransponder.equals(tpName_remove_t)) {
                            mParameterManager.getDvbsParaManager().setCurrentTransponder("");
                            mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                            stopTune();
                        }
                        mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getCurrentItemList());
                        //mItemAdapterItem.reFill(mParameterManager.getDvbsParaManager().getLnbNameList());
                    }
                    break;
                }
                default:
                    break;
            }
        }
    };

    private void startTune() {
        if (mThreadHandler != null) {
            if (mThreadHandler.hasMessages(MSG_START_TUNE_ACTION)) {
                mThreadHandler.removeMessages(MSG_START_TUNE_ACTION);
            }
            Log.d(TAG, "sendEmptyMessage startTune");
            mThreadHandler.sendEmptyMessageDelayed(MSG_START_TUNE_ACTION, VALUE_START_TUNE_DELAY);
        }
    }

    private void stopTune() {
        if (mThreadHandler != null) {
            if (mThreadHandler.hasMessages(MSG_START_TUNE_ACTION)) {
                mThreadHandler.removeMessages(MSG_START_TUNE_ACTION);
            }
            Log.d(TAG, "sendEmptyMessage stopTune");
            mThreadHandler.sendEmptyMessage(MSG_STOP_TUNE_ACTION);
        }
    }

    private void stopAndRelease() {
        Log.d(TAG, "stopAndRelease");
        abortStrengthQualityUpdate();
        stopTune();
        mParameterManager.saveUbFreqs();
        mParameterManager.getDvbsParaManager().resetSelections();
        releaseMessage();
    }

    private void releaseMessage() {
        Log.d(TAG, "releaseMessage");
        if (mThreadHandler != null) {
            if (mThreadHandler.hasMessages(MSG_STOP_RELEAS_ACTION)) {
                mThreadHandler.removeMessages(MSG_STOP_RELEAS_ACTION);
            }
            mThreadHandler.sendEmptyMessage(MSG_STOP_RELEAS_ACTION);
        }
    }

    private void switchToLeftList() {
        if (ItemListView.LIST_RIGHT.equals(mCurrentListFocus)) {
            mCurrentListFocus = ItemListView.LIST_MIDDLE;
        } else if (ItemListView.LIST_MIDDLE.equals(mCurrentListFocus)) {
            mCurrentListFocus = ItemListView.LIST_LEFT;
        }
        if (ItemListView.LIST_LEFT.equals(mCurrentListFocus)) {
            mListViewOption.cleanChoosed();
            mListSatellites.cleanChoosed();
            mListViewItem.cleanChoosed();
            mListViewItem.requestFocus();
            if (mListViewItem.getSelectedView() != null) {
                mListViewItem.setChoosed(mListViewItem.getSelectedView());
            }
            creatFour1();
            creatFour2();
            mParameterManager.getDvbsParaManager().setCurrentListDirection(ItemListView.LIST_LEFT);
        }
    }

    private void switchToRightList() {
        if (ItemListView.LIST_LEFT.equals(mCurrentListFocus)) {
            mCurrentListFocus = ItemListView.LIST_RIGHT;
        } else if (ItemListView.LIST_MIDDLE.equals(mCurrentListFocus)) {
            mCurrentListFocus = ItemListView.LIST_RIGHT;
        }
        if (ItemListView.LIST_RIGHT.equals(mCurrentListFocus)) {
            mListViewItem.cleanChoosed();
            mListSatellites.cleanChoosed();
            mListViewOption.requestFocus();
            mListViewOption.setChoosed(mListViewOption.getSelectedView());
            createConfirmAndExit1();
            createSatelliteAndScan2();
            mParameterManager.getDvbsParaManager().setCurrentListDirection("right");
        }
    }

    ListItemSelectedListener mListItemSelectedListener = new ListItemSelectedListener() {

        @Override
        public void onListItemSelected(int position, String type, boolean selected) {
            Log.d(TAG, "onListItemSelected position = " + position + ", type = " + type + ", selected = " + selected);
            if (ItemListView.LIST_LEFT.equals(mCurrentListFocus)) {
                LinkedList<ItemDetail> lnbList = mParameterManager.getDvbsParaManager().getLnbIdList();
                String currentLnb = mParameterManager.getDvbsParaManager().getCurrentLnbId();
                if (lnbList != null && lnbList.size() > 0) {
                    String selectedLnb = lnbList.get(position).getFirstText();
                    if (!currentLnb.equals(selectedLnb)) {
                        mParameterManager.getDvbsParaManager().setCurrentLnbId(selectedLnb);
                        startTune();
                    }
                }
                mItemAdapterItem.reFill(mParameterManager.getDvbsParaManager().getLnbNameList());
                mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getSatelliteNameList());

                String lnbKey = mParameterManager.getDvbsParaManager().getCurrentLnbId();
                String itemText = getContext().getString(R.string.list_type_satellite);
                String text = itemText + "(LNB" + lnbKey + ")";
                mItemTitleTextView2.setText(text);
                mListSatellites.setListType(ItemListView.ITEM_LNB);

            } else if (ItemListView.LIST_RIGHT.equals(mCurrentListFocus)) {
                String lnb = mParameterManager.getDvbsParaManager().getCurrentLnbId();
                boolean isSingle = mParameterManager.getDvbsParaManager().getLnbWrap().getLnbById(lnb).getLnbInfo().isSingle();
                if (!isSingle && position == 5) {
                    //22KHz must on when use double lnb mode.
                    return;
                }
                mCurrentCustomDialog = mDialogManager.buildItemDialogById(position, mSingleSelectDialogCallBack);
                if (mCurrentCustomDialog != null) {
                    mCurrentCustomDialog.showDialog();
                }
                mListSatellites.setListType(ItemListView.ITEM_OPTION);
            } else if (ItemListView.LIST_MIDDLE.equals(mCurrentListFocus)) {
                String listType = mParameterManager.getDvbsParaManager().getCurrentListType();
                if (ItemListView.ITEM_SATELLITE.equals(listType)) {
                    mListSatellites.setListType(ItemListView.ITEM_SATELLITE);
                    LinkedList<ItemDetail> sateAllList = mParameterManager.getDvbsParaManager().getSatelliteNameList();
                    String testSatellite = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                    String selectedSatellite = "";
                    if (sateAllList != null && sateAllList.size() > 0) {
                        selectedSatellite = sateAllList.get(position).getFirstText();
                    }
                    if (selectedSatellite != null && !(selectedSatellite.isEmpty())) {
                        String lnb = mParameterManager.getDvbsParaManager().getCurrentLnbId();
                        mParameterManager.getDvbsParaManager()
                                .getSatelliteWrap().getSatelliteList(DataPresenter.getOperateType())
                                .get(position).boundLnb(lnb, selected);
                        if (selectedSatellite.equals(testSatellite) && !selected) {
                            mParameterManager.getDvbsParaManager().setCurrentSatellite("");
                            mParameterManager.getDvbsParaManager().setCurrentTransponder("");
                        }
                    }
                } else if (ParameterManager.ITEM_TRANSPONDER.equals(listType)) {
                    mListSatellites.setListType(ItemListView.ITEM_TRANSPONDER);
                    LinkedList<ItemDetail> tps = mParameterManager.getDvbsParaManager().getTransponderList();
                    String selectTp = "";
                    if (tps != null && tps.size() > 0) {
                        selectTp = tps.get(position).getFirstText();
                    }
                    if (selectTp != null && !(selectTp.isEmpty())) {
                        //if UI support multi-tp
                        //String sateName = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                        //mParameterManager.getDvbsParaManager().getSatelliteWrap().selectTransponder(sateName, selectTp, selected);
                        //else support single tp
                        mParameterManager.getDvbsParaManager().selectSingleTransponder(selectTp, selected);
                        startTune();
                    }
                }
                mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getCurrentItemList());
                mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
            }
        }
    };

    ListItemFocusedListener mListItemFocusedListener = new ListItemFocusedListener() {

        @Override
        public void onListItemFocused(View parent, int position, String type) {
            Log.d(TAG, "onListItemFocused position = " + position + ", type = " + type + ", focus= " + mCurrentListFocus);
            /*if (ItemListView.LIST_LEFT.equals(mCurrentListFocus) && ItemListView.isRightList(type)) {
                mListViewOption.cleanChoosed();
            }*/
            if (ItemListView.LIST_LEFT.equals(mCurrentListFocus)) {
                mParameterManager.getDvbsParaManager().setCurrentListType(ParameterManager.ITEM_LNB);
                mItemDetailOption.clear();
                mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                if (mItemAdapterOption.getCount() > 0) {
                    mListViewOption.setSelection(0);
                }
                mListViewItem.cleanChoosed();
                mListViewOption.cleanChoosed();
                mListSatellites.cleanChoosed();
                View selectedView = mListViewItem.getSelectedView();
                if (selectedView != null) {
                    selectedView.requestFocus();
                    mListViewItem.setChoosed(selectedView);
                }
                LinkedList<ItemDetail> lnbs = mParameterManager.getDvbsParaManager().getLnbIdList();
                if (lnbs != null && lnbs.size() > 0) {
                    mParameterManager.getDvbsParaManager().setCurrentFocusedLnbId(lnbs.get(position).getFirstText());
                }
            } else if (ItemListView.LIST_RIGHT.equals(mCurrentListFocus)) {
                /*mListViewItem.cleanChoosed();
                if (isOptionType && position > 0 && !(ParameterManager.ITEM_LNB.equals(mCurrentListType))) {
                    String listType = ParameterManager.ITEM_LNB;
                    mItemAdapterItem.reFill(mParameterManager.getDvbsParaManager().getCurrentItemList());
                    mListViewItem.switchListToType(listType);
                }*/
            } else if (ItemListView.LIST_MIDDLE.equals(mCurrentListFocus)) {
                mParameterManager.getDvbsParaManager().setCurrentListDirection("middle");
                 //mParameterManager.getDvbsParaManager().setCurrentListType(ParameterManager.ITEM_SATELLITE);
            }
        }
    };

    ListSwitchedListener mListSwitchedListener = new ListSwitchedListener() {

        @Override
        public void onListSwitched(String direction) {
            Log.d(TAG, "onListSwitched direction = " + direction);
            if (direction != null) {
                mCurrentListFocus = direction;
            }
            String listType = mParameterManager.getDvbsParaManager().getCurrentListType();
            if (ItemListView.LIST_LEFT.equals(mCurrentListFocus)) {
                mListViewOption.cleanChoosed();
                mListSatellites.cleanChoosed();
                mListViewItem.requestFocus();
                if (mListViewItem.getSelectedView() != null) {
                    mListViewItem.setChoosed(mListViewItem.getSelectedView());
                }
                creatFour1();
                creatFour2();
                mParameterManager.getDvbsParaManager().setCurrentListDirection("left");
                mListSatellites.setListType(ItemListView.ITEM_LNB);
            } else if (ItemListView.LIST_RIGHT.equals(mCurrentListFocus)) {
                mListViewItem.cleanChoosed();
                mListSatellites.cleanChoosed();
                mListViewOption.requestFocus();
                mListViewOption.setChoosed(mListViewOption.getSelectedView());
                createConfirmAndExit1();
                createSatelliteAndScan2();
                mParameterManager.getDvbsParaManager().setCurrentListDirection("right");
                mListSatellites.setListType(ItemListView.ITEM_OPTION);
            } else if (ItemListView.LIST_MIDDLE.equals(mCurrentListFocus)) {
                mListViewItem.cleanChoosed();
                mListSatellites.requestFocus();
                mListViewOption.cleanChoosed();
                mListSatellites.setChoosed(mListSatellites.getSelectedView());
                creatFour1();
                creatFour2();
                mParameterManager.getDvbsParaManager().setCurrentListDirection("middle");
                if (ItemListView.ITEM_SATELLITE.equals(listType) || ItemListView.ITEM_LNB.equals(listType)) {
                    mListSatellites.setListType(ItemListView.ITEM_SATELLITE);
                    mParameterManager.getDvbsParaManager().setCurrentListType(ParameterManager.ITEM_SATELLITE);
                } else if (ParameterManager.ITEM_TRANSPONDER.equals(listType)) {
                    mListSatellites.setListType(ItemListView.ITEM_TRANSPONDER);
                    mParameterManager.getDvbsParaManager().setCurrentListType(ParameterManager.ITEM_TRANSPONDER);
                }
            }
            if (ParameterManager.ITEM_SATELLITE.equals(listType)) {
                //mListViewItem.setSelection(mParameterManager.getDvbsParaManager().getCurrentSatelliteIndex());
            } else if (ParameterManager.ITEM_TRANSPONDER.equals(listType)) {
                //mListViewItem.setSelection(mParameterManager.getDvbsParaManager().getCurrentTransponderIndex());
            }
        }
    };

    ListTypeSwitchedListener mListTypeSwitchedListener = new ListTypeSwitchedListener() {
        @Override
        public void onListTypeSwitched(String listType) {
            Log.d(TAG, "onListTypeSwitched listType = " + listType);

            mCurrentListType = listType;
            mParameterManager.getDvbsParaManager().setCurrentListType(mCurrentListType);
            mItemAdapterSatellites.reFill(mParameterManager.getDvbsParaManager().getCurrentItemList());
            mListSatellites.setAdapter(mItemAdapterSatellites);
            mListSatellites.cleanChoosed();
            int position = 0;
            if (ItemListView.ITEM_SATELLITE.equals(listType)) {
                isTransponder = false;
                position = mParameterManager.getDvbsParaManager().getCurrentSatelliteIndex();
                String lnbKey = mParameterManager.getDvbsParaManager().getCurrentLnbId();
                String itemText = getContext().getString(R.string.list_type_satellite);
                String text = itemText + "(LNB" + lnbKey + ")";
                mItemTitleTextView2.setText(text);

            } else if (ItemListView.ITEM_TRANSPONDER.equals(listType)) {
                isTransponder = true;
                String lnbKey = mParameterManager.getDvbsParaManager().getCurrentLnbId();
                String sate = mParameterManager.getDvbsParaManager().getCurrentSatellite();
                String itemText = getContext().getString(R.string.list_type_transponder);
                String text = itemText + "(LNB" + lnbKey + ":" + sate + ")";
                mItemTitleTextView2.setText(text);
                mItemAdapterOption.reFill(mParameterManager.getDvbsParaManager().getLnbParamsWithId());
                startTune();
            } else {
                //position = mParameterManager.getDvbsParaManager().getCurrentLnbIndex();
                //mItemTitleTextView.setText(R.string.list_type_lnb);
                //mParameterManager.getDvbsParaManager().setCurrentSatellite("");
                //mParameterManager.getDvbsParaManager().setCurrentTransponder("");
            }
            if (ItemListView.LIST_MIDDLE.equals(mCurrentListFocus)) {
                mListSatellites.setSelection(position);
                View selectedView = mListSatellites.getSelectedView();
                if (selectedView != null) {
                    selectedView.requestFocus();
                    mListSatellites.setChoosed(selectedView);
                }
            }
        }
    };
}

