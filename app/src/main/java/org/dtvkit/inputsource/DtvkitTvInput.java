package com.droidlogic.dtvkit.inputsource;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputHardwareInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.Hardware;
import android.media.tv.TvInputManager.HardwareCallback;
import android.media.tv.TvInputService;
import android.media.tv.TvStreamConfig;
import android.media.tv.TvTrackInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.MainThread;

import com.amlogic.hbbtv.HbbTvManager;
import com.amlogic.hbbtv.HbbTvManager.HbbTvApplicationStartCallBack;
import com.droidlogic.app.AudioConfigManager;
import com.droidlogic.app.AudioSystemCmdManager;
import com.droidlogic.app.DataProviderManager;
import com.droidlogic.app.SystemControlEvent;
import com.droidlogic.app.SystemControlManager;
import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.companionlibrary.model.Channel;
import com.droidlogic.dtvkit.companionlibrary.model.InternalProviderData;
import com.droidlogic.dtvkit.companionlibrary.model.Program;
import com.droidlogic.dtvkit.companionlibrary.model.RecordedProgram;
import com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils;
import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.parental.ContentRatingSystem;
import com.droidlogic.dtvkit.inputsource.parental.ContentRatingsManager;
import com.droidlogic.dtvkit.inputsource.util.FeatureUtil;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.settings.ConstantManager;
import com.droidlogic.settings.ConvertSettingManager;
import com.droidlogic.settings.CountryTimeZone;
import com.droidlogic.settings.PropSettingManager;
import com.droidlogic.settings.SysSettingManager;
import com.google.common.collect.Lists;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.droidlogic.dtvkit.IndentingPrintWriter;
import org.droidlogic.dtvkit.TvMTSSetting;

import org.dtvkit.inputsource.caption.AtvCcTool;
import org.dtvkit.inputsource.caption.CustomerFont;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Collections;
import android.media.tv.tuner.Tuner;
import droidlogic.dtvkit.tuner.TunerAdapter;

public class DtvkitTvInput extends TvInputService implements SystemControlEvent.DisplayModeListener,
        SystemControlEvent.AudioEventListener {
    private static final String TAG = "DtvkitTvInput";

    private static final int ASPECT_MODE_AUTO = 0;
    private static final int ASPECT_MODE_CUSTOM = 5;
    private static final int DISPLAY_MODE_NORMAL = 6;

    private static final int TTX_MODE_NORMAL = 0;
    private static final int TTX_MODE_TRANSPARENT = 1;
    private static final int TTX_MODE_SEPARATE = 2;

    protected TvInputInfo mTvInputInfo;
    protected TvInputInfo mPipTvInputInfo;

    private TvInputHardwareInfo mTvInputHardwareInfo;
    private TvInputHardwareInfo mPipTvInputHardwareInfo;

    private SysSettingManager mSysSettingManager;
    private SystemControlEvent mSystemControlEvent;

    protected HandlerThread mHandlerThread;
    protected Handler mInputThreadHandler;

    private Channel mMainDvbChannel;
    private Channel mPipDvbChannel;

    private final int HARDWARE_MAIN_DEVICE_ID = 19;
    private final int HARDWARE_PIP_DEVICE_ID = 119;

    private Hardware mMainHardware = null;
    private Hardware mPipHardware = null;

    private TvStreamConfig[] mMainStreamConfig = null;
    private TvStreamConfig mPipStreamConfig = null;

    /*associate audio*/
    protected boolean mAudioADAutoStart = false;
    protected int mAudioADMixingLevel = 50;
    protected int mAudioADVolume = 100;
    private static boolean bMainAudioOutput = true;

    /*teletext subtitle status when open teletext page*/
    protected boolean mSubFlagTtxPage = false;
    /*teletext region id*/
    private int mRegionId = 0;
    private String mDynamicDbSyncTag = "";
    private String mCiTuneServiceUri = "";

    // Mutex for all mutable shared state.
    private final Object mLock = new Object();
    private final Object mRecordingLock = new Object();

    private HashMap<Long, String> mCachedRecordingsPrograms = new HashMap<>();

    //ci authentication
    private boolean mCiAuthenticatedStatus = false;

    private ContentResolver mContentResolver;
    private TvInputManager mTvInputManager;

    private ContentRatingsManager mContentRatingsManager;
    private String mCurrentRatingSystem = "DVB";

    private String mInputId;

    private TvMTSSetting mTvMTSSetting;

    private enum SessionState {
        NONE, TUNED, RELEASING, RELEASED
    }

    private enum PlayerState {
        STOPPED, STARTING, PLAYING, BLOCKED, SCRAMBLED,
    }

    private enum RecorderState {
        STOPPED, STARTING, RECORDING
    }

    private enum State {
        NO, YES, DISABLED
    }

    private RecorderState timeshiftRecorderState = RecorderState.STOPPED;
    private boolean mIsTimeshiftingPlayed = false;
    private int numRecorders = 0;
    private int numActiveRecordings = 0;

    private static long mDtvkitTvInputSessionCount = 0;
    private long mDtvkitRecordingSessionCount = 0;
    private boolean recordingPending = false;

    private DataManager mDataManager;
    private ParameterManager mParameterManager;

    SystemControlManager mSystemControlManager;

    private String intentAction = "com.droidlogic.dtvkit.inputsource.AutomaticSearching";
    private AutomaticSearchingReceiver mAutomaticSearchingReceiver = null;
    public final int SUBTITLE_CTL_HK_DVB_SUB = 0x02;
    public final int SUBTITLE_CTL_HK_TTX_SUB = 0x04;
    public final int SUBTITLE_CTL_HK_SCTE27 = 0x08;
    public final int SUBTITLE_CTL_HK_TTX_PAG = 0x10;
    public final int SUBTITLE_CTL_HK_CC = 0x20;
    public final int SUBTITLE_CTL_HK_MHEG5S = 0x40;
    public final int Default_SubCtlFlg = (SUBTITLE_CTL_HK_DVB_SUB | SUBTITLE_CTL_HK_SCTE27
            | SUBTITLE_CTL_HK_TTX_SUB | SUBTITLE_CTL_HK_TTX_PAG
            /*SUBTITLE_CTL_HK_CC*/);

    //record all sessions by index
    private DtvkitRecordingSession mRecordingSession = null;
    private final Map<Long, DtvkitTvInputSession> mTunerSessions = new HashMap<>();

    private boolean mRecordingStarted = false;
    private final Object mTunerSessionLock = new Object();

    private static final int INDEX_FOR_MAIN = 0;
    private static final int INDEX_FOR_PIP = 1;

    //stream change and update dialog
    private AlertDialog mStreamChangeUpdateDialog = null;

    private AlarmManager mAlarmManager = null;

    private int mCusFeatureSubtitleCfg = 0;
    private int mCusFeatureAudioCfg = 0;

    private boolean mCusEnableDtvAutoTime = false;
    private AutoTimeManager mAutoTimeManager = null;
    private String mPinCode = null;

    private ConnectivityManager mConnectivityManager;

    private HbbTvManager mHbbTvManager = null;
    private DtvKitScheduleManager mDtvKitScheduleManager = null;

    public DtvkitTvInput() {
        Log.i(TAG, "newInstance");
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        if (args != null && args.length > 0) {
            // todo: do more thing
            pw.println(Arrays.toString(args));
            if (TextUtils.equals(args[0], "cmd") && args.length > 1) {
                try {
                    JSONObject cmd = new JSONObject(args[1]);
                    JSONObject result = DtvkitGlueClient.getInstance().request(
                            cmd.getString("command"), cmd.getJSONArray("json"));
                    pw.println(result.toString());
                } catch (Exception e) {
                    pw.println(e.getMessage());
                    e.printStackTrace();
                }
                return;
            }
        }
        if (mMainHandler != null) {
            mMainHandler.dump(pw, "");
        }
        pw.println("Current RecordingSession:");
        pw.increaseIndent();
        pw.println("RecordingStarted:" + mRecordingStarted);
        if (mRecordingSession != null) {
            mRecordingSession.dump(fd, writer, args);
        }
        pw.decreaseIndent();
        pw.println("Current TvSession:");
        pw.increaseIndent();
        for (DtvkitTvInputSession session : mTunerSessions.values()) {
            session.dump(fd, writer, args);
        }
        pw.decreaseIndent();
        pw.println("Information of Signal Handler:");
        pw.increaseIndent();
        for (String s : DtvkitGlueClient.getInstance().getSignalHandlerInfo()) {
            pw.println(s);
        }
        pw.decreaseIndent();
    }

    private void updateParentalControl() {
        boolean isParentControlEnabled = mTvInputManager.isParentalControlsEnabled();
        int rating = getCurrentMinAgeByBlockedRatings();
        Log.d(TAG, "updateParentalControl isParentControlEnabled = "
            + isParentControlEnabled + ", Rating = " + rating);
        if (isParentControlEnabled != getParentalControlOn()) {
            setParentalControlOn(isParentControlEnabled);
        }
        if (getParentalControlAge() != rating && isParentControlEnabled) {
            setParentalControlAge(rating);
        }
    }

    protected final BroadcastReceiver mParentalControlsBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (action.equals(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED)
                    || action.equals(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED)) {
                Log.d(TAG, "BLOCKED_RATINGS_CHANGED");
                updateParentalControl();
            }
        }
    };

    protected final BroadcastReceiver mBootBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
                    Log.d(TAG, "onReceive ACTION_LOCKED_BOOT_COMPLETED");
                    //can't init here as storage may not be ready
                } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                    Log.d(TAG, "onReceive ACTION_BOOT_COMPLETED");
                    sendEmptyMessageToInputThreadHandler(MSG_CHECK_TV_PROVIDER_READY);
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    updateTKGSAlarmTime();
                    CiPowerMonitor.getInstance(context).onReceiveScreenOff();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    if (mAutomaticSearchingReceiver != null) {
                        mAutomaticSearchingReceiver.onReceiveScreenOn();
                    }
                    CiPowerMonitor.getInstance(context).onReceiveScreenOn();
                }
            }
        }
    };

    protected final BroadcastReceiver mStorageEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                String mountPath = intent.getData().getPath();
                Log.d(TAG, "mStorageEventReceiver mountPath = " + mountPath);
                if (FeatureUtil.getFeatureSupportNewTvApp() && SysSettingManager.isStoragePath(mountPath)) {
                    if (mInputThreadHandler != null) {
                        Message mess = mInputThreadHandler.obtainMessage(MSG_ADD_DTVKIT_DISK_PATH, 0, 0, mountPath);
                        boolean info = mInputThreadHandler.sendMessageDelayed(mess, 0);
                        Log.d(TAG, "sendMessage MSG_ADD_DTVKIT_DISK_PATH " + info);
                    }
                }
            } else {
                Log.d(TAG, "mStorageEventReceiver other action");
            }
        }
    };

    protected final BroadcastReceiver mNetStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                setDnsProp();
            }
        }
    };

    protected final BroadcastReceiver mEwsTestBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PropSettingManager.getBoolean(PropSettingManager.EWS_TEST, false)) {
                Log.w(TAG, "EWS_CASE hasn't been enabled");
                return;
            }
            if (intent == null) {
                return;
            }
            try {
                if (ConstantManager.ACTION_EWS.equals(intent.getAction())) {
                    String command = intent.getStringExtra("ews_command");
                    DtvkitTvInputSession mainSession = getMainTunerSession();
                    if (ConstantManager.ACTION_EWS_NOTIFY.equals(command)) {
                        //am broadcast -a "action_ews" --es ews_command "action_ews_notify"
                        if (mainSession != null) {
                            Bundle EWSEvent = new Bundle();
                            EWSEvent.putInt("disaster_code", intent.getIntExtra("disaster_code", 0));
                            EWSEvent.putInt("authority", intent.getIntExtra("authority",0));
                            EWSEvent.putInt("location_type_code", intent.getIntExtra("location_type_code", 0));
                            EWSEvent.putString("disaster_code_str", "Corresponding disaster code string");
                            EWSEvent.putString("location_code", "location_code");
                            EWSEvent.putString("disaster_position", "disaster_position");
                            EWSEvent.putString("disaster_date", "disaster_date");
                            EWSEvent.putString("disaster_characteristic", "disaster_characteristic");
                            EWSEvent.putString("information_message", "information_message");
                            mainSession.notifySessionEvent(ConstantManager.ACTION_EWS_NOTIFY, EWSEvent);
                        }
                    } else if (ConstantManager.ACTION_EWS_CLOSE.equals(command)) {
                        //am broadcast -a "action_ews" --es ews_command "action_ews_close"
                        if (mainSession != null) {
                            mainSession.notifySessionEvent(ConstantManager.ACTION_EWS_CLOSE, null);
                        }
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "mEwsTestBroadcastReceiver Exception = " + e.getMessage());
            }
        }
    };

    protected final BroadcastReceiver mCiTestBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PropSettingManager.getBoolean(PropSettingManager.CI_PROFILE_TEST, false)) {
                Log.w(TAG, "TEST_CASE hasn't been enabled");
                return;
            }
            if (intent == null) {
                return;
            }
            try {
                String action = intent.getAction();
                if (ConstantManager.ACTION_CI_PLUS_INFO.equals(action)) {
                    String command = intent.getStringExtra(ConstantManager.CI_PLUS_COMMAND);
                    DtvkitTvInputSession mainSession = getMainTunerSession();
                    switch (command) {
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_REQUEST:
                            //op search request
                            //am broadcast -a ci_plus_info --es command "search_request" --ei search_module 1
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_REQUEST);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putInt(ConstantManager.VALUE_CI_PLUS_SEARCH_MODULE,
                                        intent.getIntExtra(ConstantManager.VALUE_CI_PLUS_SEARCH_MODULE, -1));
                                request.putString(ConstantManager.CI_PLUS_COMMAND,
                                        ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_REQUEST);
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_FINISHED:
                            //op search finished
                            //am broadcast -a ci_plus_info --es command "search_finished"
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_FINISHED);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND,
                                        ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_FINISHED);
                                request.putBoolean(ConstantManager.VALUE_CI_PLUS_SEARCH_RESULT_IS_OPERATOR_PROFILE_SUPPORTED,
                                        isOperatorProfileSupported());
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_CHANNEL_UPDATED:
                            //op search finished
                            //am broadcast -a ci_plus_info --es command "channel_updated"
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_CHANNEL_UPDATED);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND,
                                        ConstantManager.VALUE_CI_PLUS_COMMAND_CHANNEL_UPDATED);
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_HOST_CONTROL:
                            //op search finished
                            //am broadcast -a ci_plus_info --es command "host_control" --el host_control_channel 1 --es tune_type "service_tune"
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_HOST_CONTROL);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putLong(ConstantManager.VALUE_CI_PLUS_CHANNEL,
                                        intent.getLongExtra(ConstantManager.VALUE_CI_PLUS_CHANNEL, -1));
                                request.putString(ConstantManager.VALUE_CI_PLUS_TUNE_TYPE,
                                        intent.getStringExtra(ConstantManager.VALUE_CI_PLUS_TUNE_TYPE));
                                request.putString(ConstantManager.CI_PLUS_COMMAND,
                                        ConstantManager.VALUE_CI_PLUS_COMMAND_HOST_CONTROL);
                                request.putInt(ConstantManager.VALUE_CI_PLUS_TUNE_QUIETLY,
                                        intent.getIntExtra(ConstantManager.VALUE_CI_PLUS_TUNE_QUIETLY, 0));
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_IGNORE_INPUT:
                            //op search finished
                            //am broadcast -a ci_plus_info --es command "ignore_input"
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_IGNORE_INPUT);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND,
                                        ConstantManager.VALUE_CI_PLUS_COMMAND_IGNORE_INPUT);
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_RECEIVE_INPUT:
                            //op search finished
                            //am broadcast -a ci_plus_info --es command "receive_input"
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_RECEIVE_INPUT);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND,
                                        ConstantManager.VALUE_CI_PLUS_COMMAND_RECEIVE_INPUT);
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_PVR_PLAYBACK_STATUS:
                            //playback license
                            //am broadcast -a "ci_plus_info" --es command "PvrPlaybackStatus" --es tips "stop for no license" --ei errcode 1
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_PVR_PLAYBACK_STATUS);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND,
                                        ConstantManager.VALUE_CI_PLUS_COMMAND_PVR_PLAYBACK_STATUS);
                                request.putString("playback", intent.getStringExtra("tips"));
                                request.putInt("errcode", intent.getIntExtra("errcode", 0));
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_TRICK_LIMIT:
                            //trick limit
                            //am broadcast -a "ci_plus_info" --es command "trick_limit" --es "trick_limit" "content protection record" --ei errcode 1
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_TRICK_LIMIT);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_TRICK_LIMIT);
                                request.putString("trick_limit", intent.getStringExtra("trick_limit"));
                                request.putInt("trick_limit", intent.getIntExtra("errcode", 0));
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_DO_PVR_LIMITED:
                            //pvr start failed
                            //am broadcast -a "ci_plus_info" --es command "DoPVRLimited" --es "do_pvr_limited" "content protection record" --ei errcode 1
                            showToast(ConstantManager.VALUE_CI_PLUS_COMMAND_DO_PVR_LIMITED);
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_DO_PVR_LIMITED);
                                request.putString("do_pvr_limited", intent.getStringExtra("do_pvr_limited"));
                                request.putInt("do_pvr_limited", intent.getIntExtra("errcode", 0));
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_ECI_CONTENT_PROTECTION:
                            //eci content protection
                            //am broadcast -a "ci_plus_info" --es command "eci_content_protection" --es "content_protection" "content_protection"
                            if (mainSession != null) {
                                Bundle request = new Bundle();
                                request.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_ECI_CONTENT_PROTECTION);
                                request.putBoolean("is_content_protection", intent.getStringExtra("content_protection").equals("content_protection"));
                                mainSession.sendBundleToAppByTif(action, request);
                            }
                            break;
                        case ConstantManager.VALUE_CI_PLUS_COMMAND_TRICK_CONTROL:
                            //CIPLUS_HAS_TRICK_CONTROL
                            //am broadcast -a "ci_plus_info" --es command "track_control"
                            if (mainSession != null) {
                                Bundle playbackBundle = new Bundle();
                                playbackBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_TRICK_CONTROL);
                                mainSession.sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, playbackBundle);
                            }
                            break;
                        default:
                            break;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "mCiTestBroadcastReceiver Exception = " + e.getMessage());
            }
        }
    };

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String status = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            if (EpgSyncJobService.SYNC_FINISHED.equals(status)) {
                String from = intent.getStringExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM);
                DtvkitTvInputSession mainSession = getMainTunerSession();
                Bundle bundle = new Bundle();
                if (TextUtils.equals("DvbUpdatedChannel", from)
                    || TextUtils.equals("TVStatusChanged", from)) {
                    sendEmptyMessageToInputThreadHandler(MSG_STOP_MONITOR_SYNCING);
                    if (mainSession != null) {
                        mainSession.sendBundleToAppByTif(ConstantManager.EVENT_CHANNEL_LIST_UPDATED, bundle);
                        mainSession.sendMsgTsUpdate();
                    }
                } else if (TextUtils.equals("CiplusUpdateService", from)) {
                    sendEmptyMessageToInputThreadHandler(MSG_STOP_MONITOR_SYNCING);
                    if (TextUtils.equals(from, mDynamicDbSyncTag) && mainSession != null) {
                        bundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_CHANNEL_UPDATED);
                        mainSession.sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, bundle);
                    }
                    mDynamicDbSyncTag = "";
                } else {
                    Log.d(TAG, "syncReceiver, ignore Msg is from:" + from);
                }
            }
        }
    };

    private final ContentObserver mDroidLogicDbContentObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            if (mSystemControlManager != null) {
                String buildFingerprint = mSystemControlManager.getPropertyString("ro.build.fingerprint", "");
                //Only for trunk use, to match CiPlus case
                //Trunk has no separate CI menu, we use same security PIN code for CI CAM
                //When doing this CI case, please change PIN code in security menu first
                //Custom will has their own design, so use fingerprint to limit this logic.
                if (buildFingerprint.contains("Amlogic")) {
                    removeMessageInInputThreadHandler(MSG_CHECK_PIN_CODE_CHANGED);
                    sendDelayedEmptyMessageToInputThreadHandler(MSG_CHECK_PIN_CODE_CHANGED, 1000);
                }
            }
        }
    };

    private Handler mMainHandler;

    private void runOnMainThread(Runnable r) {
        // Android MainThread is the first thread, same as pid.
        if (Process.myPid() != Process.myTid()) {
            synchronized (this) {
                if (mMainHandler == null) {
                    mMainHandler = new Handler(Looper.getMainLooper());
                }
            }
            mMainHandler.post(r);
        } else {
            r.run();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        mHandlerThread = new HandlerThread("DtvkitInputWorker");
        mHandlerThread.start();
        initInputThreadHandler();
        mTvInputManager = (TvInputManager) this.getSystemService(Context.TV_INPUT_SERVICE);
        mContentRatingsManager = new ContentRatingsManager(getApplicationContext(), mTvInputManager);
        mContentResolver = getContentResolver();
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        mConnectivityManager = (ConnectivityManager) this.getSystemService(ConnectivityManager.class);
        mSystemControlManager = SystemControlManager.getInstance();
        mTvMTSSetting = TvMTSSetting.getInstance();

        mContentResolver.registerContentObserver(
                Uri.parse(DataProviderManager.CONTENT_URI + DataProviderManager.TABLE_STRING_NAME),
                true, mDroidLogicDbContentObserver);
        mContentResolver.registerContentObserver(TvContract.RecordedPrograms.CONTENT_URI,
                true, mRecordingsContentObserver);

        IntentFilter parentalControl = new IntentFilter();
        parentalControl.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
        parentalControl.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        registerReceiver(mParentalControlsBroadcastReceiver, parentalControl, 2/*RECEIVER_EXPORTED*/);

        IntentFilter boot = new IntentFilter();
        boot.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        boot.addAction(Intent.ACTION_BOOT_COMPLETED);
        boot.addAction(Intent.ACTION_SCREEN_ON);
        boot.addAction(Intent.ACTION_SCREEN_OFF);
        boot.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mBootBroadcastReceiver, boot);

        mAutomaticSearchingReceiver = new AutomaticSearchingReceiver();
        IntentFilter automaticSearch = new IntentFilter();
        automaticSearch.addAction(intentAction);
        registerReceiver(mAutomaticSearchingReceiver, automaticSearch, 2/*RECEIVER_EXPORTED*/);

        IntentFilter ciTest = new IntentFilter();
        ciTest.addAction(ConstantManager.ACTION_CI_PLUS_INFO);
        registerReceiver(mCiTestBroadcastReceiver, ciTest, 4/*RECEIVER_NOT_EXPORTED*/);

        IntentFilter storage = new IntentFilter();
        storage.addAction(Intent.ACTION_MEDIA_MOUNTED);
        storage.addDataScheme(ContentResolver.SCHEME_FILE);
        registerReceiver(mStorageEventReceiver, storage);

        IntentFilter netState = new IntentFilter();
        netState.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetStateChangeReceiver, netState);

        IntentFilter ewsTest = new IntentFilter();
        ewsTest.addAction(ConstantManager.ACTION_EWS);
        registerReceiver(mEwsTestBroadcastReceiver, ewsTest, 4/*RECEIVER_NOT_EXPORTED*/);

        setDnsProp();
        sendEmptyMessageToInputThreadHandler(MSG_START_CA_SETTINGS_SERVICE);
        sendEmptyMessageToInputThreadHandler(MSG_CHECK_TV_PROVIDER_READY);
    }

    //input work handler define
    //dtvkit init message 1~50
    protected static final int MSG_START_CA_SETTINGS_SERVICE = 1;
    protected static final int MSG_CHECK_TV_PROVIDER_READY = 2;
    protected static final int MSG_CHECK_DTVKIT_SATELLITE = 3;
    protected static final int MSG_ADD_DTVKIT_DISK_PATH = 4;
    protected static final int MSG_UPDATE_DTVKIT_DATABASE = 5;
    protected static final int MSG_START_MONITOR_SYNCING = 6;
    protected static final int MSG_STOP_MONITOR_SYNCING = 7;
    protected static final int MSG_CHECK_PIN_CODE_CHANGED = 8;
    protected static final int MSG_CHECK_CHANNEL_SEARCH_STATUS = 9;

    protected static final int PERIOD_RIGHT_NOW = 0;
    protected static final int PERIOD_CHECK_TV_PROVIDER_DELAY = 200;
    protected static final int PERIOD_TIPS_FOR_TOO_MUCH_TIME_MAX = 1500;

    //dtvkit tune session message 51~100

    //dtvkit tune record session message 101~150
    protected static final int MSG_UPDATE_RECORDING_FROM_DTVKIT = 101;
    protected static final int MSG_UPDATE_RECORDING_FROM_DB = 102;
    protected static final int MSG_HANDLE_RECORDING_FROM_DTVKIT = 103;

    protected static final int MSG_UPDATE_RECORDING_PROGRAM_DELAY = 200;
    protected static final int MSG_HANDLE_RECORDING_PROGRAM_DELAY = 200;

    private void initInputThreadHandler() {
        mInputThreadHandler = new Handler(mHandlerThread.getLooper(), (msg) -> {
            long startSystemMills = System.currentTimeMillis();
            switch (msg.what) {
                case MSG_START_CA_SETTINGS_SERVICE: {
                    //startCaSettingServices();
                    break;
                }
                case MSG_CHECK_TV_PROVIDER_READY: {
                    if (!checkTvProviderAvailable()) {
                        sendDelayedEmptyMessageToInputThreadHandler(MSG_CHECK_TV_PROVIDER_READY,
                                PERIOD_CHECK_TV_PROVIDER_DELAY);
                    } else {
                        Log.i(TAG, "initInputThreadHandler，initDtvkitTvInput");
                        mContentRatingsManager.update();
                        updateParentalControl();
                        initDtvkitTvInput();
                    }
                    break;
                }
                case MSG_CHECK_DTVKIT_SATELLITE: {
                    checkDtvkitSatelliteUpdateStatus();
                    break;
                }
                case MSG_UPDATE_DTVKIT_DATABASE: {
                    updateDtvkitDatabase();
                    break;
                }
                case MSG_UPDATE_RECORDING_FROM_DTVKIT: {
                    recordingGetListOfRecordingsAsync(String.valueOf(msg.obj));
                    break;
                }
                case MSG_UPDATE_RECORDING_FROM_DB: {
                    HashMap<Long, String> current = getRecordingsDb();
                    if (current.size() < mCachedRecordingsPrograms.size()) {
                        // delete case
                        onRecordingsChanged(current);
                    }
                    mCachedRecordingsPrograms = current;
                    break;
                }
                case MSG_HANDLE_RECORDING_FROM_DTVKIT: {
                    JSONArray data = (JSONArray) msg.obj;
                    synchronized (mRecordingLock) {
                        if (syncRecordingProgramsWithDtvkit(data) == 0) {
                            mCachedRecordingsPrograms.clear();
                        }
                    }
                    break;
                }
                case MSG_ADD_DTVKIT_DISK_PATH: {
                    recordingAddDiskPath(SysSettingManager.convertStoragePathToMediaPath(
                            (String) msg.obj) + "/" + SysSettingManager.PVR_DEFAULT_FOLDER);
                    break;
                }
                case MSG_START_MONITOR_SYNCING: {
                    LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(syncReceiver,
                            new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
                    break;
                }
                case MSG_STOP_MONITOR_SYNCING: {
                    LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(syncReceiver);
                    break;
                }
                case MSG_CHECK_PIN_CODE_CHANGED: {
                    String pinCode = mParameterManager.getStringParameters(ParameterManager.SECURITY_PASSWORD);
                    if (!pinCode.equals(mPinCode)) {
                        Log.d(TAG, "pin code changed, SetPinCodeToCam.");
                        mPinCode = pinCode;
                        mParameterManager.setPinCodeToCam(mPinCode);
                    }
                    break;
                }
                case MSG_CHECK_CHANNEL_SEARCH_STATUS: {
                    //Power off and on during channel search, the status in the search are not updated, update here
                    if (DataProviderManager.getBooleanValue(this, "is_channel_searching", false)) {
                        DataProviderManager.putBooleanValue(this,ConstantManager.KEY_IS_SEARCHING, false);
                    }
                    break;
                }

                default:
                    break;
            }
            long cost = System.currentTimeMillis() - startSystemMills;
            if (cost > PERIOD_TIPS_FOR_TOO_MUCH_TIME_MAX) {
                Log.w(TAG, "mInputThreadHandler ---> " + msg.what + " over cost = " + cost + " !!!");
            }
            return true;
        });
    }

    private void updateDtvkitDatabase() {
        Log.i(TAG, "update Dtvkit Database start");
        checkAndUpdateLcn();
        int dvbSource = getCurrentDvbSource();
        EpgSyncJobService.setChannelTypeFilter(TvContractUtils.dvbSourceToChannelTypeString(dvbSource));
        Bundle parameters = new Bundle();
        parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, "full");
        Intent intent = new Intent(this, DtvkitEpgSync.class);
        intent.putExtra("inputId", mInputId);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, "updateDtvkitDatabase");
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
        startService(intent);
    }

    private boolean sendEmptyMessageToInputThreadHandler(int what) {
        boolean result = false;
        Message mess = null;
        if (mInputThreadHandler != null) {
            mess = mInputThreadHandler.obtainMessage(what, 0, 0, null);
            Log.d(TAG, "sendEmpty message to InputThreadHandler, message: " + mess);
            result = mInputThreadHandler.sendMessageDelayed(mess, 0);
        }
        return result;
    }

    private boolean sendDelayedEmptyMessageToInputThreadHandler(int what, int delay) {
        boolean result = false;
        Message mess = null;
        if (mInputThreadHandler != null) {
            mess = mInputThreadHandler.obtainMessage(what, 0, 0, null);
            result = mInputThreadHandler.sendMessageDelayed(mess, delay);
        }
        return result;
    }

    private void removeMessageInInputThreadHandler(int what) {
        if (mInputThreadHandler != null) {
            mInputThreadHandler.removeMessages(what);
        }
    }

    private boolean mIsInited = false;
    private int mCheckTvProviderTimeOut = 5 * 1000;//10s

    private boolean checkTvProviderAvailable() {
        boolean result = false;
        ContentProviderClient tvProvider = this.getContentResolver()
                .acquireContentProviderClient(TvContract.AUTHORITY);
        if (tvProvider != null) {
            result = true;
            Log.d(TAG, "checkTvProviderAvailable ready");
        } else {
            mCheckTvProviderTimeOut -= 10;
            if (mCheckTvProviderTimeOut < 0) {
                Log.d(TAG, "checkTvProviderAvailable timeout");
            } else if (mCheckTvProviderTimeOut % 500 == 0) {//update per 500ms
                Log.d(TAG, "checkTvProviderAvailable wait count = "
                        + ((5 * 1000 - mCheckTvProviderTimeOut) / 10));
            }
        }
        return result;
    }

    private int getSubtitleFlag() {
        int ret = Default_SubCtlFlg;
        if (mSystemControlManager != null) {
            ret = mSystemControlManager.getPropertyInt("persist.vendor.tif.subtitleflg", Default_SubCtlFlg);
        }

        return ret;
    }

    private void firstSync() {
        final String firstProp = "persist.vendor.tv.first_search";
        boolean first = PropSettingManager.getBoolean(firstProp, true);
        if (first) {
            mInputThreadHandler.post(() -> {
                String iso = ParameterManager.getCurrentCountryIso3Name();
                CountryTimeZone zone = new CountryTimeZone();
                String[] time = zone.getTimezone(iso);
                if (time != null) {
                    int timeOffset = TimeZone.getTimeZone(time[0]).getRawOffset();
                    Log.i(TAG, "country:" + iso + ", timezone:" + timeOffset / 1000);
                    mParameterManager.setTimeZone(timeOffset / 1000);
                }
                PropSettingManager.setProp(firstProp, "false");
            });
        }
    }

    private synchronized void initDtvkitTvInput() {
        boolean hasInitFont = false;
        if (mIsInited) {
            Log.d(TAG, "initDtvkitTvInput already");
            return;
        }
        Log.d(TAG, "initDtvkitTvInput start");
        mSysSettingManager = new SysSettingManager(this);
        mDataManager = new DataManager(this);
        sendGetRecordingListMsg("initDtvkitTvInput");
        mSystemControlEvent = SystemControlEvent.getInstance(null);
        mSystemControlEvent.setDisplayModeListener(this);
        mSystemControlEvent.SetAudioEventListener(this);
        if (mSystemControlManager != null) {
            mSystemControlManager.setListener(mSystemControlEvent);
        }
        //DtvkitGlueClient.getInstance().setSystemControlHandler(mSysControlHandler);
        DtvkitGlueClient.getInstance().registerSignalHandler(mRecordingManagerHandler);
        DtvkitGlueClient.getInstance().registerSignalHandler(mChannelUpdateHandler);
        DtvkitGlueClient.getInstance().registerSignalHandler(mCIPLUSCardHandler);
        mParameterManager = new ParameterManager(this, DtvkitGlueClient.getInstance());
        if (null == mDtvKitScheduleManager) {
            mDtvKitScheduleManager = new DtvKitScheduleManager(this, DtvkitGlueClient.getInstance(), true);
        }
        updateRecorderNumber();
        sendEmptyMessageToInputThreadHandler(MSG_CHECK_DTVKIT_SATELLITE);
        sendEmptyMessageToInputThreadHandler(MSG_UPDATE_DTVKIT_DATABASE);
        sendDelayedEmptyMessageToInputThreadHandler(MSG_CHECK_CHANNEL_SEARCH_STATUS, 1000);
        resetRecordingPath();

        int subFlg = getSubtitleFlag();
        if (subFlg >= SUBTITLE_CTL_HK_DVB_SUB) {
            if ((subFlg & SUBTITLE_CTL_HK_CC) == SUBTITLE_CTL_HK_CC) {
                CustomerFont.initFontInTread(getApplicationContext());
                hasInitFont = true;
            }
        }
        if (AtvCcTool.getInstance().supportAtv()) {
            AtvCcTool.getInstance().startInit(getApplicationContext());
            if (!hasInitFont) {
                CustomerFont.initFontInTread(getApplicationContext());
            }
        }
        mParameterManager.initTextCharSet();
        mCusFeatureSubtitleCfg = getCustomFeatureSubtitleCfg();
        mCusFeatureAudioCfg = getCustomFeatureAudioCfg();
        mCusEnableDtvAutoTime = (getCustomFeatureAutoTimeCfg() > 0);
        mPinCode = mParameterManager.getStringParameters(ParameterManager.SECURITY_PASSWORD);
        if (mCusEnableDtvAutoTime) {
            AutoTimeManager.AutoManagerCallback autoManagerCallback = new AutoTimeManager.AutoManagerCallback() {
                @Override
                public long getDtvRealTime() {
                    return getDtvUTCTime();
                }

                @Override
                public void enableDtvTimeSync(boolean enable) {
                    enableDtvAutoTimeSync(enable);
                }

                @Override
                public void onTimeChangedBySystem() {
                    Log.d(TAG, "System time changed by network or manual, disable dtv time.");
                    enableDtvAutoTimeSync(false);
                    //if (getMainTunerSession() != null) {
                    //    getMainTunerSession().onTimeChangedBySystem();
                    //}
                }
            };
            mAutoTimeManager = new AutoTimeManager(DtvkitTvInput.this, autoManagerCallback);
            mAutoTimeManager.start();
        }
        firstSync();
        Log.d(TAG, "initDtvkitTvInput end");
        mIsInited = true;
    }

    private synchronized void updateRecorderNumber() {
        Log.d(TAG, "updateRecorderNumber start");
        numRecorders = recordingGetNumRecorders();
    }

    private synchronized void updateTunerNumber() {
        Log.d(TAG, "updateTunerNumber start");
        int realNumTuners = getNumTuners();
        int numTuners = realNumTuners;
        if (numTuners > 0) {
            boolean multiFrequencySupport = false;
            boolean supportPip = FeatureUtil.getFeatureSupportPip();
            boolean supportFcc = FeatureUtil.getFeatureSupportFcc();
            Log.d(TAG, "updateTunerNumber new count " + numTuners
                    + ", supportPip = " + supportPip
                    + ", supportFcc = " + supportFcc);
            //1.currently ohm_mxl258c has 4 tuner and can server for pip or fcc by configure,
            //    besides, it can support play multi frequency
            //    match for one record one play and one pip
            //2.currently ohm has 2 tuner but can only match for single frequency, besides,
            //    it can only debug pip or fcc
            //    match for one record and one play and pip
            //3.one tuner and only can play and record within same frequency
            //    match for one record and one play
            if (numTuners > 2) {
                multiFrequencySupport = true;
                if (supportFcc) {
                    numTuners = 2;//2 tuners that can work in two different frequency
                }
                if (supportPip) {
                    numTuners = 3;//3 tuners that can work in three different frequency
                }
            }
            boolean noChange = true;
            if (mTvInputInfo != null) {
                Bundle bundle = mTvInputInfo.getExtras();
                if (bundle != null) {
                    if (supportPip != bundle.getBoolean(PropSettingManager.ENABLE_PIP_SUPPORT, false)) {
                        Log.d(TAG, "updateTunerNumber pip support new status = " + supportPip);
                        noChange = false;
                    }
                    if (supportFcc != bundle.getBoolean(PropSettingManager.ENABLE_FCC_SUPPORT, false)) {
                        Log.d(TAG, "updateTunerNumber fcc support new status = " + supportFcc);
                        noChange = false;
                    }
                }
                if (mTvInputInfo.getTunerCount() != numTuners) {
                    Log.d(TAG, "updateTunerNumber new tunerCount = " + numTuners
                            + ", old tunerCount = " + mTvInputInfo.getTunerCount());
                    noChange = false;
                } else {
                    TvInputInfo latestInfo = mTvInputManager.getTvInputInfo(mTvInputInfo.getId());
                    if (latestInfo != null && latestInfo.getTunerCount() != numTuners) {
                        Log.d(TAG, "updateTunerNumber getTunerCount in TIF = " + latestInfo.getTunerCount());
                        noChange = false;
                    }
                }
            } else {
                noChange = false;
            }
            if (noChange) {
                Log.d(TAG, "updateTunerNumber noChange");
                return;
            }
            Bundle extras = new Bundle();
            extras.putBoolean(PropSettingManager.ENABLE_PIP_SUPPORT, supportPip);
            extras.putBoolean(PropSettingManager.ENABLE_FCC_SUPPORT, supportFcc);
            extras.putBoolean(PropSettingManager.ENABLE_MULTI_FREQUENCY_SUPPORT, multiFrequencySupport);
            extras.putInt(PropSettingManager.ENABLE_TUNER_NUMBER, realNumTuners);
            mTvInputInfo = buildTvInputInfo(mTvInputHardwareInfo, numTuners, false, extras);
        } else {
            mTvInputInfo = buildTvInputInfo(mTvInputHardwareInfo, 1, false, null);
        }
        mTvInputManager.updateTvInputInfo(mTvInputInfo);
        Log.d(TAG, "updateTunerNumber end");
    }

    private void checkDtvkitSatelliteUpdateStatus() {
        if (mDataManager != null && mParameterManager != null) {
            if (mDataManager.getIntParameters(DataManager.DTVKIT_IMPORT_SATELLITE_FLAG) > 0) {
                Log.d(TAG, "checkDtvkitSatelliteUpdateStatus has imported already");
            } else {
                if (mParameterManager.importSatellitesAndLnbs()) {
                    mDataManager.saveIntParameters(DataManager.DTVKIT_IMPORT_SATELLITE_FLAG, 1);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        unregisterReceiver(mParentalControlsBroadcastReceiver);
        unregisterReceiver(mBootBroadcastReceiver);
        unregisterReceiver(mAutomaticSearchingReceiver);
        unregisterReceiver(mCiTestBroadcastReceiver);
        unregisterReceiver(mEwsTestBroadcastReceiver);
        unregisterReceiver(mStorageEventReceiver);
        unregisterReceiver(mNetStateChangeReceiver);
        if (mAutoTimeManager != null) {
            mAutoTimeManager.release();
        }
        //mContentResolver.unregisterContentObserver(mContentObserver);
        mContentResolver.unregisterContentObserver(mDroidLogicDbContentObserver);
        mContentResolver.unregisterContentObserver(mRecordingsContentObserver);
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mRecordingManagerHandler);
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mChannelUpdateHandler);
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mCIPLUSCardHandler);
        //DtvkitGlueClient.getInstance().setSystemControlHandler(null);
        mHandlerThread.getLooper().quitSafely();
        mHandlerThread = null;
        mInputThreadHandler.removeCallbacksAndMessages(null);
        mInputThreadHandler = null;
        if (null != mDtvKitScheduleManager) {
            mDtvKitScheduleManager.release();
            mDtvKitScheduleManager = null;
        }
    }

    private boolean setTVAspectMode(int mode) {
        boolean used = false;
        try {
            JSONArray args = new JSONArray();
            args.put(mode);
            used = DtvkitGlueClient.getInstance().request("Dvb.setTVAspectMode", args)
                    .getBoolean("data");
            Log.i(TAG, "dvb setTVAspectMode, used:" + used);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return used;
    }

    private void createHbbTvManager() {
        if (mHbbTvManager != null) {
            return;
        }
        if (FeatureUtil.getFeatureSupportHbbTV()) {
            mHbbTvManager = HbbTvManager.getInstance();
        }
    }

    @Override
    public void HandleAudioEvent(int cmd, int param1, int param2, int param3) {
        if (cmd == AudioSystemCmdManager.AUDIO_SERVICE_CMD_START_DECODE) {
            // good chance to sync ad mix & volume to audio_hal
            Log.d(TAG, "HandleAudioEvent cmd " + "AUDIO_START_DECODE");
            DtvkitTvInputSession main = getMainTunerSession();
            if (main != null && main.readyToPlay()) {
                main.playerSetAdParams(true, 300);
            }
        }
    }

    @Override
    public void onSetDisplayMode(int mode) {
        Log.i(TAG, "onSetDisplayMode " + mode);
        setTVAspectMode(mode == DISPLAY_MODE_NORMAL ? ASPECT_MODE_AUTO : ASPECT_MODE_CUSTOM);
    }
    @Override
    public Session onCreateSession(String inputId, String sessionId) {
        Log.i(TAG, "onCreateSession " + inputId + " sessionId : " + sessionId);
        DtvkitTvInputSession session = null;
        if (inputId.contains(String.valueOf(HARDWARE_PIP_DEVICE_ID))) {
            if (FeatureUtil.getFeatureSupportPip()) {
                initDtvkitTvInput();
                session = new DtvkitPipTvSession(this, sessionId);
            }
        } else {
            initDtvkitTvInput();
            session = new DtvkitMainTvSession(this, sessionId);
        }
        setCIPlusServiceReady();
        addTunerSession(session);
        createHbbTvManager();
        return session;
    }

    @Override
    public final Session onCreateSession(String inputId) {
        Log.i(TAG, "onCreateSession " + inputId);
        DtvkitTvInputSession session = null;
        initDtvkitTvInput();
        if (inputId.contains(String.valueOf(HARDWARE_PIP_DEVICE_ID))) {
            if (FeatureUtil.getFeatureSupportPip()) {
                session = new DtvkitPipTvSession(this);
            }
        } else {
            session = new DtvkitMainTvSession(this);
        }
        setCIPlusServiceReady();
        addTunerSession(session);
        createHbbTvManager();
        return session;
    }

    private void addTunerSession(DtvkitTvInputSession session) {
        if (session == null) {
            return;
        }
        synchronized (mTunerSessionLock) {
            mTunerSessions.put(session.mCurrentSessionIndex, session);
            Log.i(TAG, "addTunerSession " + session);
        }
    }

    private void removeTunerSession(DtvkitTvInputSession session) {
        if (session == null) {
            return;
        }
        synchronized (mTunerSessionLock) {
            mTunerSessions.remove(session.mCurrentSessionIndex);
            Log.i(TAG, "removeTunerSession " + session);
        }
    }

    private boolean hasAnotherSession(DtvkitTvInputSession thiz) {
        synchronized (mTunerSessionLock) {
            DtvkitTvInputSession session;
            for (Map.Entry<Long, DtvkitTvInputSession> entry : mTunerSessions.entrySet()) {
                session = entry.getValue();
                if (thiz != session
                    && thiz.isPipSession() == session.isPipSession()
                    && session.mSurface != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private DtvkitTvInputSession getMainTunerSession() {
        DtvkitTvInputSession result = null;
        synchronized (mTunerSessionLock) {
            DtvkitTvInputSession session;
            long index;
            for (Map.Entry<Long, DtvkitTvInputSession> entry : mTunerSessions.entrySet()) {
                index = entry.getKey();
                session = entry.getValue();
                if (!session.isPipSession() && session.isSessionAvailable()) {
                    result = session;
                    Log.i(TAG, "getMainTunerSession index = " + index);
                }
            }
        }
        return result;
    }

    private DtvkitTvInputSession getPipTunerSession() {
        DtvkitTvInputSession result = null;
        synchronized (mTunerSessionLock) {
            DtvkitTvInputSession session;
            long index;
            for (Map.Entry<Long, DtvkitTvInputSession> entry : mTunerSessions.entrySet()) {
                index = entry.getKey();
                session = entry.getValue();
                if (session.isPipSession() && session.isTuned()) {
                    result = session;
                    Log.i(TAG, "getPipTunerSession index = " + index);
                }
            }
        }
        return result;
    }

    protected void setInputId(String name) {
        mInputId = name;
        Log.d(TAG, "set input id to " + mInputId);
    }
/*
    private class DtvkitOverlayView extends FrameLayout {
        private MhegOverlayView nativeOverlayView;
        private CiMenuView ciOverlayView;
        private TextView mText;
        private ImageView mTuningImage;
        private RelativeLayout mRelativeLayout;
        private CCSubtitleView mCCSubView = null;
        private SubtitleServerView mSubServerView;
        private TextView mCasOsm;
        private int w;
        private int h;
        private FrameLayout mHbbTvFrameLayout = null;

        private boolean mhegTookKey = false;
        private KeyEvent lastMhegKey = null;
        final private static int MHEG_KEY_INTERVAL = 65;
        private int mCasOsmDisplayMode = 0;

        public DtvkitOverlayView(Context context, Handler mainHandler) {
            super(context);
            Log.i(TAG, "Created" + this);
            int subFlg = getSubtitleFlag();
            boolean enableCC = false;
            if (subFlg >= SUBTITLE_CTL_HK_DVB_SUB) {
                if ((subFlg & SUBTITLE_CTL_HK_CC) == SUBTITLE_CTL_HK_CC) {
                    enableCC = true;
                }
            }
            mHbbTvFrameLayout = new FrameLayout(getContext());
            addView(mHbbTvFrameLayout);
            mSubServerView = new SubtitleServerView(getContext(), mainHandler);
            addView(mSubServerView);
            nativeOverlayView = new MhegOverlayView(getContext(), mainHandler);
            addView(nativeOverlayView);

            if (enableCC) {
                mCCSubView = new CCSubtitleView(getContext());
                addView(mCCSubView);
            }
            initRelativeLayout();
            mCasOsm = new TextView(getContext());
            addView(mCasOsm);
            mCasOsm.setVisibility(View.GONE);
            ciOverlayView = new CiMenuView(getContext());
            addView(ciOverlayView);
        }

        public void destroy() {
            Log.i(TAG, "Destroy " + this);
            if (mSubServerView != null) {
                mSubServerView.destroy();
                removeView(mSubServerView);
                mSubServerView = null;
            }
            if (nativeOverlayView != null) {
                nativeOverlayView.destroy();
                removeView(nativeOverlayView);
                nativeOverlayView = null;
            }
            if (ciOverlayView != null) {
                ciOverlayView.destroy();
                removeView(ciOverlayView);
                ciOverlayView = null;
            }
            if (mRelativeLayout != null) {
                removeView(mRelativeLayout);
                mText = null;
                mTuningImage = null;
                mRelativeLayout = null;
            }
            if (mCCSubView != null) {
                removeView(mCCSubView);
                mCCSubView = null;
            }
            if (mCasOsm != null) {
                removeView(mCasOsm);
                mCasOsm = null;
            }
            removeView(mHbbTvFrameLayout);
        }

        public void hideOverLay() {
            setVisibility(View.GONE);
            Log.d(TAG, "hideOverLay");
        }

        public void showOverLay() {
            setVisibility(View.VISIBLE);
            Log.d(TAG, "showOverLay");
        }

        private void initRelativeLayout() {
            if (mText == null && mRelativeLayout == null) {
                Log.d(TAG, "initRelativeLayout");
                mRelativeLayout = new RelativeLayout(getContext());
                mText = new TextView(getContext());
                mTuningImage = new ImageView(getContext());
                ViewGroup.LayoutParams linearLayoutParams = new ViewGroup.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                mRelativeLayout.setLayoutParams(linearLayoutParams);
                mRelativeLayout.setBackgroundColor(Color.TRANSPARENT);

                //add scrambled text
                RelativeLayout.LayoutParams textLayoutParams = new RelativeLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                textLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                mText.setGravity(Gravity.CENTER);
                mText.setTypeface(Typeface.DEFAULT_BOLD);
                mText.setTextColor(Color.WHITE);
                //mText.setText("RelativeLayout");
                mText.setVisibility(View.GONE);
                //add tuning view
                RelativeLayout.LayoutParams imageLayoutParams = new RelativeLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                ColorDrawable colorDrawable = new ColorDrawable();
                colorDrawable.setColor(Color.argb(255, 0, 0, 0));
                mTuningImage.setImageDrawable(colorDrawable);
                mTuningImage.setVisibility(View.GONE);

                mRelativeLayout.addView(mTuningImage, imageLayoutParams);
                mRelativeLayout.addView(mText, textLayoutParams);
                this.addView(mRelativeLayout);
            } else {
                Log.d(TAG, "initRelativeLayout already");
            }
        }

        public void setTeletextMix(int status) {
            int subFlg = getSubtitleFlag();
            if (subFlg >= SUBTITLE_CTL_HK_DVB_SUB) {
                mSubServerView.setTeletextTransparent(status == TTX_MODE_TRANSPARENT);
                if (status == TTX_MODE_SEPARATE) {
                    mSubServerView.setSize(1920 / 2, 0, 1920, 1080);
                } else {
                    mSubServerView.setSize(0, 0, 1920, 1080);
                }
            } else if (status == TTX_MODE_SEPARATE) {
                nativeOverlayView.setSize(1920 / 2, 0, 1920, 1080);
            } else {
                nativeOverlayView.setSize(0, 0, 1920, 1080);
            }
        }

        public void showBlockedText(String text, boolean isPip) {
            if (mText != null && mRelativeLayout != null) {
                Log.d(TAG, "showText:" + text);

                mText.setText(text);
                if (!TextUtils.isEmpty(text) && mText.getVisibility() != View.VISIBLE) {
                    mText.setVisibility(View.VISIBLE);
                }
                //display black background
                ColorDrawable colorDrawable = new ColorDrawable();
                int color = Color.argb(255, 0, 0, 0);
                if (mSystemControlManager != null) {
                    if (mSystemControlManager.getScreenColorForSignalChange() == 0) {
                        color = Color.argb(255, 0, 0, 0); //black
                    } else {
                        color = Color.argb(255, 3, 0, 247); //blue
                    }
                }
                colorDrawable.setColor(color);
                showTuningImage(colorDrawable);
            }
        }

        public void hideBlockedText() {
            try {
                DtvkitTvInputSession mainSession = getMainTunerSession();
                mainSession.mView.mSubServerView.clearSubtitle();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            if (mText != null && mRelativeLayout != null) {
                Log.d(TAG, "hideText");
                mText.setText("");
                if (mText.getVisibility() != View.GONE) {
                    mText.setVisibility(View.GONE);
                    //hide black background
                    hideTuningImage();
                }
            }
        }

        public void addHbbTvView(View view) {
            mHbbTvFrameLayout.addView(view);
        }

        public void showTuningImage(Drawable drawable) {
            if (mTuningImage != null && mRelativeLayout != null) {
                Log.d(TAG, "showTuningImage");
                if (drawable != null) {
                    mTuningImage.setImageDrawable(drawable);
                }
                if (mTuningImage.getVisibility() != View.VISIBLE) {
                    mTuningImage.setVisibility(View.VISIBLE);
                }
            }
        }

        public void hideTuningImage() {
            try {
                DtvkitTvInputSession mainSession = getMainTunerSession();
                mainSession.mView.mSubServerView.clearSubtitle();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            if (mTuningImage != null && mRelativeLayout != null) {
                Log.d(TAG, "hideTuningImage");
                if (mTuningImage.getVisibility() != View.GONE) {
                    mTuningImage.setVisibility(View.GONE);
                }
            }
        }

        public void setSize(int width, int height) {
            w = width;
            h = height;
            nativeOverlayView.setSize(width, height);
            mSubServerView.setSize(width, height);
        }

        public void setSize(int x, int y, int width, int height) {
            Log.i(TAG, "setSize for hbbtv in");
            nativeOverlayView.setSize(x, y, width, height);
            mSubServerView.setSize(x, y, width, height);
            Log.i(TAG, "setSize for hbbtv out");
        }

        public void showCCSubtitle(String json) {
            if (mCCSubView != null) {
                mCCSubView.showJsonStr(json);
            }
        }

        public void hideCCSubtitle() {
            if (mCCSubView != null) {
                mCCSubView.hide();
            }
        }

        public void prepareCasWindow(String msg, int osdMode, int anchor_x,
                                     int anchor_y, int w, int h, int bg, int alpah, int fg) {
            if (mCasOsm != null) {
                LayoutParams layoutParams = (LayoutParams) mCasOsm.getLayoutParams();
                if (osdMode == 8 || w == 0 || h == 0) {
                    layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                } else {
                    layoutParams.width = w;
                    layoutParams.height = h;
                }
                layoutParams.gravity = Gravity.NO_GRAVITY;
                float ax = (float) (anchor_x);
                float ay = (float) (anchor_y);
                float tx = (float) (this.w);
                float ty = (float) (this.h);
                if (ax / tx > 0.3 && ax / tx < 0.7) {
                    layoutParams.gravity |= Gravity.CENTER_HORIZONTAL;
                } else if (ax / tx <= 0.3) {
                    layoutParams.gravity |= Gravity.LEFT;
                } else if (ax / tx >= 0.7) {
                    layoutParams.gravity |= Gravity.RIGHT;
                }
                if (ay / ty > 0.3 && ay / ty < 0.7) {
                    layoutParams.gravity |= Gravity.CENTER_VERTICAL;
                } else if (ay / ty <= 0.3) {
                    layoutParams.gravity |= Gravity.TOP;
                } else if (ay / ty >= 0.7) {
                    layoutParams.gravity |= Gravity.BOTTOM;
                }
                mCasOsm.setLayoutParams(layoutParams);

                mCasOsm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                mCasOsm.setTypeface(Typeface.DEFAULT_BOLD);
                mCasOsm.setTextColor(Color.WHITE);

                mCasOsm.setText(msg);
                mCasOsm.setVisibility(View.GONE);
            }
        }

        public void showCasMessage(int displayMode) {
            if (mCasOsm != null) {
                mCasOsm.setVisibility(View.VISIBLE);
                mCasOsmDisplayMode = displayMode;
            }
        }

        public void clearCasView() {
            if (mCasOsm != null) {
                mCasOsm.setText("");
                mCasOsm.setVisibility(View.GONE);
                mCasOsmDisplayMode = 0;
            }
        }

        public int getCasOsmDisplayMode() {
            return mCasOsmDisplayMode;
        }

        public boolean checkMhegKeyLimit(KeyEvent event) {
            if (lastMhegKey == null)
                return false;
            if (lastMhegKey.getKeyCode() != event.getKeyCode())
                return false;
            if (event.getEventTime() - lastMhegKey.getEventTime() > MHEG_KEY_INTERVAL)
                return false;
            return true;
        }

        public boolean handleKeyDown(int keyCode, KeyEvent event) {
            boolean result;
            if (ciOverlayView.handleKeyDown(keyCode, event)) {
                mhegTookKey = false;
                result = true;
            } else if (!(checkMhegKeyLimit(event)) && mhegKeypress(keyCode)) {
                mhegTookKey = true;
                result = true;
                lastMhegKey = event;
            } else if (mCasOsm.getVisibility() == View.VISIBLE && ((mCasOsmDisplayMode & 0x1) == 0)) {
                clearCasView();
                result = true;
            } else if (mHbbTvManager != null && mHbbTvManager.handleKeyDown(keyCode, event)) {
                result = true;
                mhegTookKey = false;
                Log.d(TAG, "hbbtv manager the handle key down result = " + result);
            } else {
                mhegTookKey = false;
                result = false;
            }
            return result;
        }

        public boolean handleKeyUp(int keyCode, KeyEvent event) {
            boolean result;
            if (ciOverlayView.handleKeyUp(keyCode, event) || mhegTookKey) {
                result = true;
            } else if (mHbbTvManager != null && mHbbTvManager.handleKeyUp(keyCode, event)) {
                result = true;
                Log.d(TAG, "hbbtv manager the handle key up result = " + result);
            } else {
                result = false;
            }
            mhegTookKey = false;
            lastMhegKey = null;
            return result;
        }

        private boolean mhegKeypress(int keyCode) {
            boolean used = false;
            try {
                JSONArray args = new JSONArray();
                args.put(keyCode);
                used = DtvkitGlueClient.getInstance().request("Mheg.notifyKeypress", args)
                        .getBoolean("data");
                Log.i(TAG, "Mheg keypress, used:" + used);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return used;
        }
    }
*/
    private final DtvkitGlueClient.SignalHandler mRecordingManagerHandler = (signal, data) -> {
        if (signal.equals("RecordingsChanged")) {
            Log.i(TAG, "Recording onSignal: " + signal + " : " + data);
            sendGetRecordingListMsg(0, "SignalHandler", 1000);
        } else if (signal.equals("ListOfRecordings")) {
            Log.i(TAG, "Recording onSignal: " + signal);
            sendHandleRecordingListMsg(data);
        }
    };

    private final DtvkitGlueClient.SignalHandler mChannelUpdateHandler = (signal, data) -> {
        if (signal.equals("DvbUpdatedChannel")) {
            Log.i(TAG, "DvbUpdatedChannel");
            DtvkitTvInputSession mainSession = getMainTunerSession();
            if (mainSession != null) {
                mainSession.mTuneInfo.update = true;
                mainSession.mTuneInfo.isDTv = true;
                mainSession.mTuneInfo.dvbUri = data.optString("uri");
            }
            checkAndUpdateLcn();
            int dvbSource = getCurrentDvbSource();
            EpgSyncJobService.setChannelTypeFilter(TvContractUtils.dvbSourceToChannelTypeString(dvbSource));
            Intent intent = new Intent(this, DtvkitEpgSync.class);
            intent.putExtra("inputId", mInputId);
            intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, signal);
            startService(intent);
            sendEmptyMessageToInputThreadHandler(MSG_START_MONITOR_SYNCING);
        } else if (signal.equals("CiplusUpdateService")) {
            //update CiOpSearchRequest search result
            Log.i(TAG, "CiplusUpdateService");
            Bundle parameters = new Bundle();
            parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, "full");

            Intent intent = new Intent(this, DtvkitEpgSync.class);
            intent.putExtra("inputId", mInputId);
            intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, signal);
            intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
            startService(intent);
            if (TextUtils.isEmpty(mDynamicDbSyncTag)) {
                mDynamicDbSyncTag = signal;
                sendEmptyMessageToInputThreadHandler(MSG_START_MONITOR_SYNCING);
            } else if (TextUtils.equals(signal, mDynamicDbSyncTag)) {
                DtvkitTvInputSession mainSession = getMainTunerSession();
                if (mainSession != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_CHANNEL_UPDATED);
                    mainSession.sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, bundle);
                }
            }
        }
    };

    private final DtvkitGlueClient.SignalHandler mCIPLUSCardHandler = (signal, data) -> {
        if (signal.equals("CIPLUS_CARD_INSERT")) {
            Log.i(TAG, "CIPLUS_CARD_INSERT");
            PropSettingManager.setProp(PropSettingManager.CIPLUS_CARD_INSERTED_STATE, "insert");
        } else if (signal.equals("CIPLUS_CARD_REMOVE")) {
            Log.i(TAG, "CIPLUS_CARD_REMOVE");
            PropSettingManager.setProp(PropSettingManager.CIPLUS_CARD_INSERTED_STATE, "remove");
        }
    };


    private void sendGetRecordingListMsg(String fromWho) {
        sendGetRecordingListMsg(0, fromWho, MSG_UPDATE_RECORDING_PROGRAM_DELAY);
    }

    /**
     * type : 0 -> update from dtvkit, 1 -> update from db
     */
    private void sendGetRecordingListMsg(int type, String fromWho, int millisDelay) {
        if (mInputThreadHandler != null) {
            int what = type == 0 ? MSG_UPDATE_RECORDING_FROM_DTVKIT : MSG_UPDATE_RECORDING_FROM_DB;
            mInputThreadHandler.removeMessages(what);
            Message msg = mInputThreadHandler.obtainMessage(what, 0, 0, fromWho);
            mInputThreadHandler.sendMessageDelayed(msg, millisDelay);
        }
    }

    private void sendHandleRecordingListMsg(JSONObject object) {
        JSONArray data = null;
        try {
            data = object.optJSONArray("result");
        } catch (Exception e) {
            Log.w(TAG, e.getMessage());
        } finally {
            if (data == null) {
                data = new JSONArray();
            }
        }
        if (mInputThreadHandler != null) {
            mInputThreadHandler.removeMessages(MSG_HANDLE_RECORDING_FROM_DTVKIT);
            Message msg = mInputThreadHandler.obtainMessage(MSG_HANDLE_RECORDING_FROM_DTVKIT, 0, 0, data);
            mInputThreadHandler.sendMessageDelayed(msg, MSG_HANDLE_RECORDING_PROGRAM_DELAY);
        }
    }

    private HashMap<Long, String> getRecordingsDb() {
        HashMap<Long, String> lastest = new HashMap<>();
        Cursor cursor = null;
        final String[] PROJECTION = {
                TvContract.RecordedPrograms._ID,
                TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI,
        };
        try {
            cursor = mContentResolver.query(TvContract.RecordedPrograms.CONTENT_URI,
                    PROJECTION, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    lastest.put(cursor.getLong(0), cursor.getString(1));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "query Failed = " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return lastest;
    }

    private int updateRecordsFromDisk(ArrayList<Long> recordingsInDB, JSONArray recordings) {
        if (recordings.length() == 0) {
            if (recordingsInDB.size() != 0) {
                mContentResolver.delete(TvContract.RecordedPrograms.CONTENT_URI, "_id!=-1", null);
                Log.w(TAG, "delete recordings in tv.db");
                return 0;
            } else {
                return -1;
            }
        } else {
            if (recordings.length() == recordingsInDB.size()) {
                return -1;
            }
            /* in this case, we ignore change, because dtvkit
             * recordings may change later than tvProvider when
             * recording, it will cost much time when u-disk has
             * too many pvr files.
             */
            if (mRecordingStarted && (recordingsInDB.size() == recordings.length() + 1)) {
                Log.w(TAG, "don't update it");
                return -1;
            }
        }

        if (recordings.length() < 100) {
            for (int i = 0; i < recordings.length(); i++) {
                try {
                    Log.d(TAG, "insert dtvkit uri: " + recordings.getJSONObject(i).getString("uri"));
                } catch (JSONException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        } else {
            Log.i(TAG, "insert dtvkit uri log too many, suppress!");
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList();
        InternalProviderData data;


        Map<Long, String> currentActiveRecordings = null;
        if (mRecordingStarted) {
            currentActiveRecordings = filterCurrentActiveRecording(recordingGetActiveRecordings());
        }
        for (long id : recordingsInDB) {
            if (null != currentActiveRecordings) {
                if (currentActiveRecordings.containsKey(id)) {
                    Log.d(TAG, "active record not delete id = " + id);
                    continue;
                }
            }
            ops.add(ContentProviderOperation.newDelete(
                    TvContract.buildRecordedProgramUri(id))
                    .build());
        }

        int inValidNumber = 0;
        for (int i = 0; i < recordings.length(); i++) {
            try {
                if (Long.parseLong(recordings.getJSONObject(i).getString("length")) == 0) {
                    Log.e(TAG, "skip invalid length," + recordings.getJSONObject(i).getString("uri"));
                    inValidNumber++;
                    continue;
                }
                if (null != currentActiveRecordings) {
                    String uri = recordings.getJSONObject(i).getString("uri");
                    if (currentActiveRecordings.containsValue(uri)) {
                        Log.d(TAG, "active record not insert uri = " + uri);
                        continue;
                    }
                }
                String title = recordings.getJSONObject(i).getString("name");
                if (TextUtils.isEmpty(title)) {
                    title = recordings.getJSONObject(i).getString("service");
                }

                data = new InternalProviderData();
                try {
                    data.put(RecordedProgram.RECORD_FILE_PATH,
                            mDataManager.getStringParameters(DataManager.KEY_PVR_RECORD_PATH));
                    data.put(RecordedProgram.RECORD_SERVICE_TYPE,
                            recordings.getJSONObject(i).getBoolean("audio_only") ? TvContract.Channels.SERVICE_TYPE_AUDIO : TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO);
                } catch (InternalProviderData.ParseException e) {
                    Log.w(TAG, e.getMessage());
                }

                RecordedProgram recording = new RecordedProgram.Builder()
                        .setInputId(mInputId)
                        .setRecordingDataUri(recordings.getJSONObject(i).getString("uri"))
                        .setRecordingDataBytes(Long.valueOf(recordings.getJSONObject(i).getString("size")))
                        .setTitle(title)
                        .setStartTimeUtcMillis(Long.valueOf(recordings.getJSONObject(i).getString("date")) * 1000)
                        .setEndTimeUtcMillis(Long.valueOf(recordings.getJSONObject(i).getString("date")) * 1000
                                + Long.valueOf(recordings.getJSONObject(i).getString("length")) * 1000)
                        .setRecordingDurationMillis(Long.valueOf(recordings.getJSONObject(i).getString("length")) * 1000)
                        .setInternalProviderData(data)
                        .build();

                ops.add(ContentProviderOperation
                        .newInsert(TvContract.RecordedPrograms.CONTENT_URI)
                        .withValues(recording.toContentValues())
                        .build());
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        if (recordings.length() - inValidNumber == recordingsInDB.size()
                || recordings.length() == inValidNumber) {
            Log.w(TAG, "Ignore this update, reason("
                    + " recording size from dtvkit:" + recordings.length()
                    + ", recording size in databases:" + recordingsInDB.size()
                    + ", inValidNumber:" + inValidNumber);
            return -1;
        }

        int from = 0;
        int to = 0;
        int BATCH_MAX_NUMBER = 80;
        try {
            while (to < ops.size()) {
                from = to;
                to += BATCH_MAX_NUMBER;
                if (to > ops.size()) {
                    to = ops.size();
                }
                ArrayList<ContentProviderOperation> sub = Lists.newArrayList(ops.subList(from, to));
                mContentResolver.applyBatch(TvContract.AUTHORITY, sub);
            }
        } catch (Exception e) {
            Log.e(TAG, "recordings DB update [" + from + ", " + to + ") failed:" + e.getMessage());
            return -1;
        }

        Log.i(TAG, "sync recordings from disk to tv.db done!");
        return 1;
    }

    /* return value : changed or not */
    private int syncRecordingProgramsWithDtvkit(JSONArray recordings) {
        ArrayList<Long> recordingsInDB = new ArrayList<>();
        Cursor cursor = null;
        if (recordings == null) {
            Log.e(TAG, "null list when syncRecordingPrograms!");
            return -1;
        }
        try {
            cursor = mContentResolver.query(TvContract.RecordedPrograms.CONTENT_URI,
                    new String[]{TvContract.RecordedPrograms._ID}, null, null, null);
            while (null != cursor && cursor.moveToNext()) {
                recordingsInDB.add(cursor.getLong(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "RecordingPrograms query Failed = " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (mCachedRecordingsPrograms.size() < recordingsInDB.size()) {
            Log.i(TAG,"mCachedRecordingsPrograms is not the latest data");
            mCachedRecordingsPrograms = getRecordingsDb();
        }

        Log.d(TAG, "recordings: db[" + recordingsInDB.size() + "] dtvkit[" + recordings.length() + "]");
        return updateRecordsFromDisk(recordingsInDB, recordings);
    }

    @Override
    public RecordingSession onCreateRecordingSession(String inputId) {
        Log.d(TAG, "onCreateRecordingSession initDtvkitTvInput");
        initDtvkitTvInput();
        mDtvkitRecordingSessionCount++;
        mRecordingSession = new DtvkitRecordingSession(this, inputId);
        return mRecordingSession;
    }

    class DtvkitRecordingSession extends RecordingSession {
        private static final String TAG = "DtvkitRecordingSession";
        private Uri mChannelUri;
        private Uri mProgramUri;
        private Channel mChannel = null;
        private Program mProgram = null;
        private Context mContext;
        private String mInputId;
        private long startRecordTimeMillis = 0;
        private long endRecordTimeMillis = 0;
        private long startRecordSystemTimeMillis = 0;
        private long endRecordSystemTimeMillis = 0;
        private String recordingUri = null;
        private Uri mRecordedProgramUri = null;
        private boolean mTuned = false;
        private int mPath = -1;
        protected HandlerThread mRecordingHandlerThread = null;
        private Handler mRecordingProcessHandler = null;
        private long mCurrentRecordIndex = 0;
        private boolean mRecordStopAndSaveReceived = false;
        private boolean mIsNonAvProgram = false;
        protected TunerAdapter mRecordTunerAdapter = null;

        protected static final int MSG_RECORD_ON_TUNE = 0;
        protected static final int MSG_RECORD_ON_START = 1;
        protected static final int MSG_RECORD_ON_STOP = 2;
        protected static final int MSG_RECORD_UPDATE_RECORDING = 3;
        protected static final int MSG_RECORD_ON_RELEASE = 4;
        protected static final int MSG_RECORD_DO_FINAL_RELEASE = 5;
        private final String[] MSG_STRING = {
                "RECORD_ON_TUNE", "RECORD_ON_START", "RECORD_ON_STOP",
                "RECORD_UPDATE_RECORDING", "RECORD_ONR_RELEASE", "DO_FINAL_RELEASE"};

        protected static final int MSG_RECORD_UPDATE_RECORD_PERIOD = 3000;

        public DtvkitRecordingSession(Context context, String inputId) {
            super(context);
            if (FeatureUtil.getFeatureSupportTunerFramework()) {
                Tuner tuner = new Tuner(context, null, PRIORITY_HINT_USE_CASE_TYPE_RECORD);
                mRecordTunerAdapter = new TunerAdapter(tuner, TunerAdapter.TUNER_TYPE_RECORD);
            }
            mContext = context;
            mInputId = inputId;
            mCurrentRecordIndex = mDtvkitRecordingSessionCount;
            if (numRecorders == 0) {
                updateRecorderNumber();
            }
            updateTunerNumber();
            initRecordWorkThread();
            Log.i(TAG, "DtvkitRecordingSession create");
        }

        protected void initRecordWorkThread() {
            mRecordingHandlerThread = new HandlerThread("DtvkitRecordingSession-" + mCurrentRecordIndex);
            mRecordingHandlerThread.start();
            mRecordingProcessHandler = new Handler(mRecordingHandlerThread.getLooper(), new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    if (msg.what >= 0 && MSG_STRING.length > msg.what) {
                        Log.d(TAG, "handleMessage " + MSG_STRING[msg.what] + " start, index = " + mCurrentRecordIndex);
                    } else {
                        Log.d(TAG, "handleMessage " + msg.what + " start, index = " + mCurrentRecordIndex);
                    }
                    switch (msg.what) {
                        case MSG_RECORD_ON_TUNE: {
                            Uri uri = (Uri) msg.obj;
                            doTune(uri);
                            break;
                        }
                        case MSG_RECORD_ON_START: {
                            Uri uri = (Uri) msg.obj;
                            doStartRecording(uri);
                            break;
                        }
                        case MSG_RECORD_ON_STOP: {
                            doStopRecording();
                            doRelease();
                            break;
                        }
                        case MSG_RECORD_UPDATE_RECORDING: {
                            boolean insert = (msg.arg1 == 1);
                            boolean check = (msg.arg2 == 1);
                            updateRecordingToDb(insert, check, msg.obj);
                            break;
                        }
                        case MSG_RECORD_ON_RELEASE: {
                            break;
                        }
                        case MSG_RECORD_DO_FINAL_RELEASE: {
                            doFinalReleaseByThread();
                            break;
                        }
                        default:
                            break;
                    }
                    if (msg.what >= 0 && MSG_STRING.length > msg.what) {
                        Log.d(TAG, "handleMessage " + MSG_STRING[msg.what] + " over , index = " + mCurrentRecordIndex);
                    } else {
                        Log.d(TAG, "handleMessage " + msg.what + " over , index = " + mCurrentRecordIndex);
                    }
                    return true;
                }
            });
        }

        @Override
        public void onTune(Uri uri) {
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_ON_TUNE);
                Message mess = mRecordingProcessHandler.obtainMessage(MSG_RECORD_ON_TUNE, 0, 0, uri);
                boolean result = mRecordingProcessHandler.sendMessage(mess);
                Log.d(TAG, "onTune sendMessage result " + result + ", index = " + mCurrentRecordIndex);
            } else {
                Log.e(TAG, "onTune null mRecordingProcessHandler" + ", index = " + mCurrentRecordIndex);
            }
        }

        @Override
        public void onStartRecording(Uri uri) {
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_ON_START);
                Message mess = mRecordingProcessHandler.obtainMessage(MSG_RECORD_ON_START, 0, 0, uri);
                boolean result = mRecordingProcessHandler.sendMessage(mess);
                Log.d(TAG, "onStartRecording sendMessage result " + result + ", index = " + mCurrentRecordIndex);
            } else {
                Log.e(TAG, "onStartRecording null mRecordingProcessHandler" + ", index = " + mCurrentRecordIndex);
            }
        }

        @Override
        public void onStopRecording() {
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_ON_STOP);
                boolean result = mRecordingProcessHandler.sendEmptyMessage(MSG_RECORD_ON_STOP);
                Log.d(TAG, "onStopRecording sendMessage result " + result + ", index = " + mCurrentRecordIndex);
            } else {
                Log.e(TAG, "onStopRecording null mRecordingProcessHandler" + ", index = " + mCurrentRecordIndex);
            }
        }

        @Override
        public void onRelease() {
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_ON_RELEASE);
                boolean result = mRecordingProcessHandler.sendEmptyMessage(MSG_RECORD_ON_RELEASE);
                Log.d(TAG, "onRelease sendMessage result " + result + ", index = " + mCurrentRecordIndex);
            } else {
                Log.w(TAG, "onRelease null mRecordingProcessHandler" + ", index = " + mCurrentRecordIndex);
            }
            if (null != mRecordTunerAdapter) {
                mRecordTunerAdapter.release();
            }
        }

        public void doTune(Uri uri) {
            Log.i(TAG, "doTune for recording " + uri);
            if (ContentUris.parseId(uri) == -1) {
                Log.e(TAG, "DtvkitRecordingSession doTune invalid uri = " + uri);
                notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
                return;
            }

            mTuned = false;
            mRecordingStarted = false;

            DtvkitTvInputSession session = getMainTunerSession();
            if (session != null) {
                //need to reset record path as path may be set certain playing
                if (FeatureUtil.getFeatureSupportNewTvApp()) {
                    resetRecordingPath();
                }
            } else {
                resetRecordingPath();
            }
            numActiveRecordings = recordingGetNumActiveRecordings();
            Log.i(TAG, "numActiveRecordings: " + numActiveRecordings);

            /*want of resource*/
            if (numActiveRecordings >= numRecorders
                    || numActiveRecordings >= getNumRecordersLimit()) {
                if (FeatureUtil.getFeatureSupportTimeshifting()
                        && timeshiftRecorderState != RecorderState.STOPPED
                        && FeatureUtil.getFeatureTimeshiftingPriorityHigh()) {
                    Log.i(TAG, "No recording path available, no recorder");
                    Bundle event = new Bundle();
                    event.putString(ConstantManager.KEY_INFO, "No recording path available, no recorder");
                    notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
                    notifyError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY);
                    return;
                } else {
                    recordingPending = true;
                    boolean return_to_livetv = timeshiftRecorderState != RecorderState.STOPPED;
                    if (return_to_livetv) {
                        Log.i(TAG, "stopping timeshift [return live:" + return_to_livetv + "]");
                        playerStopTimeshiftRecording(return_to_livetv);
                    }
                    timeshiftRecorderState = RecorderState.STOPPED;
                    mIsTimeshiftingPlayed = false;
                }
            }

            boolean available = false;
            boolean tunerUsing = mParameterManager.isTunerInputConflictWithDtvKit();
            if (tunerUsing) {
                Log.i(TAG, "doTune Stop record due to tuner resource busy");
                Bundle event = new Bundle();
                event.putString(ConstantManager.KEY_INFO, "Stop record due to tuner resource busy");
                notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
                notifyError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY);
                return;
            }

            mChannelUri = uri;
            mChannel = TvContractUtils.getChannel(mContentResolver, uri);
            PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            if (!powerManager.isInteractive()) {
                //In standby mode, schedule recording on other source(for example HDMI), need to call this interface
                //Otherwise, all interfaces of Dvb cannot return, and an ANR exception occurs.
                DvbRequestDtvDevice();
            }
            if (recordingCheckAvailability(getChannelInternalDvbUri(mChannel))) {
                Log.i(TAG, "recording path available");
                StringBuffer tuneResponse = new StringBuffer();

                DtvkitGlueClient.getInstance().registerSignalHandler(mRecordingHandler);

                JSONObject tuneStat = recordingTune(getChannelInternalDvbUri(mChannel), tuneResponse);
                if (tuneStat != null) {
                    mTuned = getRecordingTuned(tuneStat);
                    mPath = getRecordingTunePath(tuneStat);
                    if (mTuned) {
                        Log.i(TAG, "recording tuned ok.");
                        notifyTuned(uri);
                    } else {
                        Log.i(TAG, "recording (path:" + mPath + ") tunning...");
                    }
                    available = true;
                } else {
                    if (tuneResponse.toString().equals("Failed to get a tuner to record")) {
                        Log.i(TAG, "record error no tuner to record");
                    } else if (tuneResponse.toString().equals("Invalid resource")) {
                        Log.i(TAG, "record error invalid channel");
                    }
                }
            }

            if (!available) {
                Log.i(TAG, "No recording path available, no tuner/demux");
                Bundle event = new Bundle();
                event.putString(ConstantManager.KEY_INFO, "No recording path available, no tuner/demux");
                notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
                notifyError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY);
            }
        }

        private void doStartRecording(Uri uri) {
            Log.i(TAG, "doStartRecording " + uri);
            mProgramUri = uri;
            mProgram = getProgram(uri);
            startRecordTimeMillis = PropSettingManager.getCurrentStreamTime(true);//start record time always is equal to the current stream time
            String dvbUri = getChannelInternalDvbUri(mChannel);
            Log.d(TAG, "startRecordTimeMillis :" + startRecordTimeMillis + "|startRecordSystemTimeMillis :" + startRecordSystemTimeMillis);

            StringBuffer recordingResponse = new StringBuffer();
            Log.i(TAG, "startRecording path:" + mPath);
            if (!recordingStartRecording(dvbUri, mPath, recordingResponse)) {
                Log.e(TAG, "record error : " + recordingResponse.toString());
                if (recordingResponse.toString().equals("Could not start record")) {
                    notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
                } else if (recordingResponse.toString().equals("Limited by minimum free disk space")) {
                    notifyError(TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
                } else {
                    notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
                }
            } else {
                startRecordSystemTimeMillis = SystemClock.uptimeMillis();
                mRecordingStarted = true;
                recordingUri = recordingResponse.toString();
                Log.i(TAG, "Recording started:" + recordingUri);
                synchronized (mRecordingLock) {
                    if (getRecordProgram(recordingUri) == null) {
                        insertNewRecording(recordingUri);
                    }
                }
                updateRecordProgramInfo();
            }
            recordingPending = false;
        }

        private void doStopRecording() {
            mRecordingSession = null;
            Log.i(TAG, "doStopRecording Started:" + mRecordingStarted);

            DtvkitGlueClient.getInstance().unregisterSignalHandler(mRecordingHandler);
            //update record status firstly to get accurate duration time
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_UPDATE_RECORDING);
                boolean result = mRecordingProcessHandler.sendMessage(mRecordingProcessHandler.obtainMessage(MSG_RECORD_UPDATE_RECORDING, 0, 0));
                Log.d(TAG, "doStopRecording sendMessage MSG_RECORD_UPDATE_RECORDING = " + result + ", index = " + mCurrentRecordIndex);
                if (result) {
                    mRecordStopAndSaveReceived = true;
                }
            } else {
                Log.i(TAG, "doStopRecording sendMessage MSG_RECORD_UPDATE_RECORDING null, index = " + mCurrentRecordIndex);
            }
            if (!recordingStopRecording(recordingUri)) {
                notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            } else {
                Log.i(TAG, "doStopRecording succeed, index = " + mCurrentRecordIndex);
            }
            mRecordingStarted = false;
            recordingPending = false;
        }

        private void updateRecordingToDb(boolean insert, boolean check, Object obj) {
            endRecordTimeMillis = PropSettingManager.getCurrentStreamTime(true);
            endRecordSystemTimeMillis = SystemClock.uptimeMillis();
            Log.d(TAG, "updateRecordingToDb:" + recordingUri);
            if (recordingUri == null) {
                // startRecording failed, so do Release
                if (mRecordingProcessHandler != null) {
                    mRecordingProcessHandler.removeMessages(MSG_RECORD_UPDATE_RECORDING);
                    mRecordingProcessHandler.removeMessages(MSG_RECORD_ON_STOP);
                    mRecordingProcessHandler.sendEmptyMessage(MSG_RECORD_ON_STOP);
                }
                return;
            }

            long recordingDurationMillis = endRecordSystemTimeMillis - startRecordSystemTimeMillis;
            long floorMs = recordingDurationMillis % 1000;
            Log.d(TAG, "updateRecordingToDb recordingDurationMillis:" + recordingDurationMillis);
            //keep the nearest second
            if (floorMs >= 500) {
                recordingDurationMillis = recordingDurationMillis + 1000 - floorMs;
            } else {
                recordingDurationMillis = recordingDurationMillis - floorMs;
            }
            RecordedProgram.Builder builder = null;
            InternalProviderData data = null;
            Program program = mProgram;
            Channel channel = mChannel;
            if (program == null) {
                program = getCurrentStreamProgram(mChannelUri, PropSettingManager.getCurrentStreamTime(true));
            }
            if (!insert) {
                Log.i(TAG, "updateRecordingToDb update");
                if (mRecordedProgramUri != null) {
                    ContentValues values = new ContentValues();
                    if (endRecordTimeMillis > -1) {
                        values.put(TvContract.RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, startRecordTimeMillis + recordingDurationMillis/*endRecordTimeMillis*/);//stream card may playback
                    }
                    if (recordingDurationMillis > 0) {
                        values.put(TvContract.RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS, recordingDurationMillis);
                    }
                    TvContentRating[] oldContentRatings = null;
                    TvContentRating[] currentContentRatings = null;
                    TvContentRating[] newContentRatings = null;
                    if (obj != null && obj instanceof RecordedProgram) {
                        oldContentRatings = ((RecordedProgram) obj).getContentRatings();
                    }
                    if (program != null) {
                        currentContentRatings = program.getContentRatings();
                    }
                    if (currentContentRatings != null) {
                        newContentRatings = updateRecordProgramRatings(oldContentRatings, currentContentRatings);
                    }
                    if (newContentRatings != null) {
                        values.put(TvContract.RecordedPrograms.COLUMN_CONTENT_RATING, TvContractUtils.contentRatingsToString(newContentRatings));
                    }
                    mContext.getContentResolver().update(mRecordedProgramUri,
                            values, null, null);
                    // if (isSdkAfterAndroidQ())
                    mContext.getContentResolver().notifyChange(
                            mRecordedProgramUri, mRecordingsContentObserver, 1 << 15);
                } else {
                    Log.i(TAG, "updateRecordingToDb update mRecordedProgramUri null");
                }
            }

            //recordingUri = null;
            if (!check) {
                boolean delete = false;
                if (obj != null && obj instanceof Bundle) {
                    delete = ((Bundle) obj).getBoolean("delete", false);
                }
                if (delete) {
                    Log.d(TAG, "updateRecordingToDb delete" + " index = " + mCurrentRecordIndex);
                    recordingStopRecording(recordingUri);
                    recordingRemoveRecording(recordingUri);
                    if (mRecordedProgramUri != null)
                        mContext.getContentResolver().delete(mRecordedProgramUri, null, null);
                    if (mRecordingProcessHandler != null)
                        mRecordingProcessHandler.removeMessages(MSG_RECORD_UPDATE_RECORDING);
                } else {
                    Log.d(TAG, "updateRecordingToDb notify stop mRecordedProgramUri = " + mRecordedProgramUri
                        + " mRecordingStarted = " + mRecordingStarted
                        + " index = " + mCurrentRecordIndex);
                    notifyRecordingStopped(mRecordedProgramUri);
                    if (mRecordStopAndSaveReceived || mRecordingStarted) {
                        if (mRecordingProcessHandler != null) {
                            mRecordingProcessHandler.removeMessages(MSG_RECORD_UPDATE_RECORDING);
                            boolean result = mRecordingProcessHandler.sendEmptyMessage(MSG_RECORD_DO_FINAL_RELEASE);
                            Log.d(TAG, "updateRecordingToDb sendMessage result = " + result + ", index = " + mCurrentRecordIndex);
                        } else {
                            Log.i(TAG, "updateRecordingToDb sendMessage null mRecordingProcessHandler" + ", index = " + mCurrentRecordIndex);
                        }
                    }
                }
            } else {
                if (mRecordingProcessHandler != null) {
                    boolean result = mRecordingProcessHandler.sendMessageDelayed(mRecordingProcessHandler.obtainMessage(MSG_RECORD_UPDATE_RECORDING, 0, 1), MSG_RECORD_UPDATE_RECORD_PERIOD);
                    Log.d(TAG, "updateRecordingToDb continue sendMessage MSG_RECORD_UPDATE_RECORDING = " + result + ", index = " + mCurrentRecordIndex);
                } else {
                    Log.i(TAG, "updateRecordingToDb sendMessage MSG_RECORD_UPDATE_RECORDING null, index = " + mCurrentRecordIndex);
                }
            }
        }

        private void doRelease() {
            Log.i(TAG, "doRelease");

            recordingPending = false;

            String uri = "";
            if (mChannel != null) {
                uri = getChannelInternalDvbUri(mChannel);
            } else if (mProgram != null) {
                uri = getChannelInternalDvbUri(
                        TvContractUtils.getChannel(mContentResolver,
                                TvContract.buildChannelUri(mProgram.getChannelId())));
            } else {
                return;
            }

            DtvkitGlueClient.getInstance().unregisterSignalHandler(mRecordingHandler);
            if ((mRecordingStarted && !mRecordStopAndSaveReceived) || mIsNonAvProgram) {
                if (mRecordingProcessHandler != null) {
                    Bundle b = null;
                    if (mIsNonAvProgram) {
                        b = new Bundle();
                        b.putBoolean("delete", true);
                        Log.d(TAG, "doRelease none Av Program");
                    }
                    //need update and stop record directly
                    updateRecordingToDb(false, false, b);
                    mRecordingProcessHandler.removeMessages(MSG_RECORD_UPDATE_RECORDING);
                } else {
                    Log.i(TAG, "doRelease sendMessage null mRecordingProcessHandler" + ", index = " + mCurrentRecordIndex);
                }
            }
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_DO_FINAL_RELEASE);
                boolean result = mRecordingProcessHandler.sendEmptyMessage(MSG_RECORD_DO_FINAL_RELEASE);
                Log.d(TAG, "doRelease sendMessage result = " + result + ", index = " + mCurrentRecordIndex);
            }
            if (!mRecordingStarted && mTuned) {
                recordingUnTune(mPath);
                return;
            }

            JSONArray scheduledRecordings = recordingGetListOfScheduledRecordings();
            if (scheduledRecordings != null) {
                for (int i = 0; i < scheduledRecordings.length(); i++) {
                    try {
                        if (getScheduledRecordingUri(scheduledRecordings.getJSONObject(i)).equals(uri)) {
                            Log.i(TAG, "removing recording uri from schedule: " + uri);
                            recordingRemoveScheduledRecording(uri);
                            break;
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        }

        private void doFinalReleaseByThread() {
            runOnMainThread(() -> {
                Log.d(TAG, "doFinalReleaseByThread start index = " + mCurrentRecordIndex);
                if (mRecordingHandlerThread != null) {
                    mRecordingHandlerThread.getLooper().quitSafely();
                    mRecordingHandlerThread = null;
                }
                if (mRecordingProcessHandler != null) {
                    mRecordingProcessHandler.removeCallbacksAndMessages(null);
                }
                mRecordingProcessHandler = null;
                Log.d(TAG, "doFinalReleaseByThread end  index = " + mCurrentRecordIndex);
            });
        }

        public boolean renameRecord(String name, String dvbUri) {
            try {
                JSONArray args = new JSONArray();
                args.put(dvbUri);
                args.put(name);
                DtvkitGlueClient.getInstance().request("Recording.renameRecording", args);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                return false;
            }
            return true;
        }

        private String getRecordName(Channel channel, Program program) {
            String name = "";
            if (channel != null) {
                name = channel.getDisplayName();
            }

            if (program == null) {
                program = getCurrentStreamProgram(mChannelUri, PropSettingManager.getCurrentStreamTime(true));
            }

            if (program != null) {
                String title = program.getTitle();
                if (title != null && title.length() > 0) {
                    name = name +" [" + title + "]";
                }
            }

            return name;
        }


        private Program getProgram(Uri uri) {
            Program program = null;
            Cursor cursor = null;
            try {
                if (uri != null) {
                    cursor = mContext.getContentResolver().query(uri, Program.PROJECTION, null, null, null);
                }
                while (null != cursor && cursor.moveToNext()) {
                    program = Program.fromCursor(cursor);
                }
            } catch (Exception e) {
                Log.e(TAG, "getProgram query Failed = " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return program;
        }

        private void insertNewRecording(String recordDataUri) {
            Program program = mProgram;
            Channel channel = mChannel;
            if (program == null) {
                program = getCurrentStreamProgram(mChannelUri, PropSettingManager.getCurrentStreamTime(true));
            }
            RecordedProgram.Builder builder;
            if (program == null) {
                long id = -1;
                if (channel != null) {
                    id = channel.getId();
                }
                builder = new RecordedProgram.Builder()
                        .setChannelId(id)
                        .setTitle(channel != null ? channel.getDisplayName() : null)
                        .setStartTimeUtcMillis(startRecordTimeMillis)
                        .setEndTimeUtcMillis(startRecordTimeMillis);
            } else {
                builder = new RecordedProgram.Builder(program);
                String name = getRecordName(channel, program);
                builder.setTitle(name);
                renameRecord(name, recordingUri);
                builder.setStartTimeUtcMillis(startRecordTimeMillis);
            }
            InternalProviderData data = new InternalProviderData();
            String currentPath = mDataManager.getStringParameters(DataManager.KEY_PVR_RECORD_PATH);
            int pathExist = 0;
            if (mSysSettingManager.isDeviceExist(currentPath)) {
                pathExist = 1;
            }
            if (SysSettingManager.isMediaPath(currentPath)) {
                currentPath = SysSettingManager.convertMediaPathToMountedPath(currentPath);
            }
            try {
                data.put(RecordedProgram.RECORD_FILE_PATH, currentPath);
                data.put(RecordedProgram.RECORD_STORAGE_EXIST, pathExist);
                data.put(RecordedProgram.RECORD_SERVICE_TYPE,
                        channel.getServiceType().equals(TvContract.Channels.SERVICE_TYPE_AUDIO) ? TvContract.Channels.SERVICE_TYPE_AUDIO : TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO);
            } catch (Exception e) {
                Log.e(TAG, "insertNewRecording Exception = " + e.getMessage());
            }
            RecordedProgram recording = builder.setInputId(mInputId)
                .setRecordingDataUri(recordDataUri)
                .setRecordingDataBytes(1024 * 1024l)
                .setRecordingDurationMillis(-1)
                .setInternalProviderData(data)
                .build();
            mRecordedProgramUri = mContext.getContentResolver().insert(TvContract.RecordedPrograms.CONTENT_URI,
                    recording.toContentValues());
            Log.d(TAG, "insertNewRecording insert " + mRecordedProgramUri);
            Bundle event = new Bundle();
            event.putString(ConstantManager.EVENT_RECORD_DATA_URI, recordDataUri != null ? recordDataUri : null);
            event.putString(ConstantManager.EVENT_RECORD_PROGRAM_URI, mRecordedProgramUri != null ? mRecordedProgramUri.toString() : null);
            notifySessionEvent(ConstantManager.EVENT_RECORD_PROGRAM_URI, event);

        }

        private void updateRecordProgramInfo() {
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_UPDATE_RECORDING);
                Log.d(TAG, "updateRecordProgramInfo update");
                Message msg = mRecordingProcessHandler.obtainMessage(MSG_RECORD_UPDATE_RECORDING,
                        0, 1, null);
                mRecordingProcessHandler.sendMessageDelayed(msg, 1000);
            }
        }

        private RecordedProgram getRecordProgram(String recordDataUri) {
            RecordedProgram result = null;
            Cursor cursor = null;
            if (TextUtils.isEmpty(recordDataUri)) {
                return null;
            }
            try {
                cursor = mContext.getContentResolver().query(TvContract.RecordedPrograms.CONTENT_URI,
                        RecordedProgram.PROJECTION, TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI + "=?",
                        new String[]{recordDataUri}, null);
                while (null != cursor && cursor.moveToNext()) {
                    result = RecordedProgram.fromCursor(cursor);
                    break;
                }
            } catch (Exception e) {
                Log.e(TAG, "getRecordProgram query Failed = " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return result;
        }

        private TvContentRating[] updateRecordProgramRatings(TvContentRating[] oldRatings, TvContentRating[] newRatings) {
            TvContentRating[] result = null;
            List<TvContentRating> allRatings = null;
            if (newRatings != null && newRatings.length > 0) {
                if (oldRatings == null || oldRatings.length == 0) {
                    result = newRatings;
                } else {
                    allRatings = new ArrayList(Arrays.asList(oldRatings));
                    for (TvContentRating tempNew : newRatings) {
                        if (!containsRating(oldRatings, tempNew)) {
                            allRatings.add(tempNew);
                        }
                    }
                    result = (TvContentRating[]) (allRatings.toArray());
                }
            }
            return result;
        }

        private boolean containsRating(TvContentRating[] ratings, TvContentRating rating) {
            boolean result = false;
            if (ratings != null && ratings.length > 0 && rating != null) {
                for (TvContentRating temp : ratings) {
                    if (rating.equals(temp)) {
                        result = true;
                        break;
                    }
                }
            }
            return result;
        }

        private Program getCurrentProgram(Uri channelUri) {
            return TvContractUtils.getCurrentProgram(mContext.getContentResolver(), channelUri);
        }

        private Program getCurrentStreamProgram(Uri channelUri, long streamTime) {
            return TvContractUtils.getCurrentProgramExt(mContext.getContentResolver(), channelUri, streamTime);
        }

        private void stopRecording(boolean delete) {
            Bundle b = null;

            if (delete) {
                b = new Bundle();
                b.putBoolean("delete", true);
            }
            if (mRecordingProcessHandler != null) {
                mRecordingProcessHandler.removeMessages(MSG_RECORD_UPDATE_RECORDING);
                boolean result = mRecordingProcessHandler.sendMessage(
                        mRecordingProcessHandler.obtainMessage(MSG_RECORD_UPDATE_RECORDING, 0, 0, b));
                Log.d(TAG, "stopRecording " + ", index = " + mCurrentRecordIndex);
                if (result) {
                    mRecordStopAndSaveReceived = true;
                }
            } else {
                Log.e(TAG, "stopRecording mRecordingProcessHandler is null, index = " + mCurrentRecordIndex);
            }
        }

        private final DtvkitGlueClient.SignalHandler mRecordingHandler = (signal, data) -> {
            Log.i(TAG, "recording onSignal: " + signal + " : " + data);
            if (signal.equals("TuneStatusChanged")) {
                String state = "fail";
                int path = -1;
                try {
                    state = data.getString("state");
                    path = data.getInt("path");
                } catch (JSONException ignore) {
                }
                Log.i(TAG, "tune to: " + path + ", " + state);
                if (path == -1 || path == 255 )
                    return;

                if ("ok".equals(state)) {
                    if (!mTuned) {
                        notifyTuned(mChannelUri);
                        mTuned = true;
                    }
                }
            } else if (signal.equals("RecordingStatusChanged")) {
                if (!recordingIsRecordingPathActive(data, mPath)) {
                    Log.d(TAG, "RecordingStatusChanged, stopped[path:" + mPath + "]");
                    if (mRecordingStarted) {
                        stopRecording(false);
                        doStopRecording();
                    }
                } else if (checkActiveRecordings(recordingGetActiveRecordings(data),
                        /*check the record_av_only rule*/
                        new Predicate<JSONObject>() {
                            @Override
                            public boolean test(JSONObject recording) {
                                return TextUtils.equals(recording.optString("serviceuri", "null"),
                                        getChannelInternalDvbUri(mChannel))
                                        && FeatureUtil.getFeatureSupportRecordAVServiceOnly()
                                        && !recording.optBoolean("is_av", true);
                            }
                        })) {
                    /*stop the recording*/
                    Log.i(TAG, "recording stopped due to non-AV service, feature rec_av_only enabled");
                    //stop recording in release by flag mIsNonAvProgram as record may be starting when receive this message
                    mIsNonAvProgram = true;
                    //stopRecording(true);

                    Bundle event = new Bundle();
                    event.putString(ConstantManager.KEY_INFO, "not allowed to record a non-av service");
                    notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
                    notifyError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY);
                }
            } else if (signal.equals("RecordingDiskFull")) {
                //tell application to deal current running pvr
                notifyError(TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
            } else if (signal.equals("RecordingsDiskRm")) {
                //tell application current pvr disk remove now
                Bundle event = new Bundle();
                event.putString(ConstantManager.KEY_INFO, "Stop record due to disk remove");
                notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
                doStopRecording();
            } else if (signal.equals("TuneResourceBusy")) {
                //tell application TuneResourceBusy and need to stop
                Bundle event = new Bundle();
                event.putString(ConstantManager.KEY_INFO, "Stop record due to tuner resource busy");
                notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
                doStopRecording();
            } else if (signal.equals("DvbNetworkChange") || signal.equals("DvbUpdatedService")) {
                if (mTvInputInfo != null && mTvInputInfo.getTunerCount() < 2) {
                    doStopRecording();
                }
            }
        };

        protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
            pw.increaseIndent();
            pw.println("recordingUri:" + mRecordedProgramUri);
            pw.println("startRecord       TimeMillis:" + startRecordTimeMillis);
            pw.println("endRecord         TimeMillis:" + endRecordTimeMillis);
            pw.println("startRecord SystemTimeMillis:" + startRecordSystemTimeMillis);
            pw.println("endRecord   SystemTimeMillis:" + endRecordSystemTimeMillis);
            pw.decreaseIndent();
        }
    }

    public class DtvkitPipTvSession extends DtvkitTvInputSession {
        DtvkitPipTvSession(DtvkitTvInput service, String sessionId) {
            super(service, sessionId, true);
        }
        DtvkitPipTvSession(DtvkitTvInput service) {
            super(service, true);
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            if (mPipTvInputInfo == null) {
                Log.e(TAG, "resource not ready!");
                return false;
            }
            if (surface != null) {
                acquireTvHardware();
            } else {
                if (!hasAnotherSession(this)) {
                    if (mPipHardware != null) {
                        mPipHardware.setSurface(null, null);
                    }
                    releaseTvHardware();
                }
            }
            return super.onSetSurface(surface);
        }

        @MainThread
        protected void acquireTvHardware() {
            if (mPipHardware != null) {
                return;
            }
            Hardware hardware = mTvInputManager.acquireTvInputHardware(HARDWARE_PIP_DEVICE_ID,
                    mPipTvInputInfo, mHardwareCallback);
            if (hardware == null) {
                Log.e(TAG, "Failed to acquire TvHardware:" + HARDWARE_PIP_DEVICE_ID);
            } else {
                hardware.setStreamVolume(1.0f);
            }
            mPipHardware = hardware;
        }

        @MainThread
        protected void releaseTvHardware() {
            super.releaseTvHardware();
            if (mPipHardware != null) {
                mTvInputManager.releaseTvInputHardware(HARDWARE_PIP_DEVICE_ID, mPipHardware);
                mPipHardware = null;
            }
        }

        @Override
        protected void preFirstTune() {
            setSurfaceTunnelId(INDEX_FOR_PIP, 2);
        }

        @Override
        public boolean readyToPlay() {
            if (mSurface != null && mPipHardware != null && mPipStreamConfig != null && mCanPlay) {
                return true;
            }
            Log.d(TAG, "Surface:" + mSurface + ", PipHardware:" + mPipHardware
                + ", PipStreamConfig:" + mPipStreamConfig +", ready:" + mCanPlay);
            return false;
        }

        @Override
        protected boolean doTune(Channel oldChannel, Channel newChannel, Uri channelUri,
                                 String dvbUri, boolean mhegTune) {
            onFinish(false, false);
            // MUST double check
            if (!isSessionAvailable()) {
                Log.e(TAG, "Abort tune because session is releasing...");
                return false;
            }
            DtvkitGlueClient.getInstance().registerSignalHandler(mHandler, INDEX_FOR_PIP);
            mTunedChannel = newChannel; // before play
            boolean playResult = playerPlay(INDEX_FOR_PIP, dvbUri, mAudioADAutoStart,
                    true, 0, "", "").equals("ok");
            if (!playResult) {
                mTunedChannel = null;
                DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
            }
            return playResult;
        }

        @Override
        protected void onFinish(boolean noStopATv, boolean ignoreFcc) {
            playerPipStop();
        }
    }

    public class DtvkitMainTvSession extends DtvkitTvInputSession {
        private Uri mFccPreviousBufferUri = null;
        private Uri mFccNextBufferUri = null;
        private boolean isDvrSession = false;

        DtvkitMainTvSession(DtvkitTvInput service, String sessionId) {
            super(service, sessionId, false);
            init();
        }
        DtvkitMainTvSession(DtvkitTvInput service) {
            super(service, false);
            init();
        }

        private void init() {
            setOverlayViewEnabled(true);
            if (hasAnotherSession(this)) {
                DtvkitTvInputSession oldSession = getMainTunerSession();
                // As early as possible to stop oldSession.
                // bugfix : from launcher to livetv, livetv splash launcher video once.
                if (oldSession != null && oldSession.isTuned()) {
                    Log.e(TAG, "stop old session!");
                    oldSession.onFinish(false, false);
                }
            }
        }

        public void setFccBufferUri(Uri previous, Uri next) {
            mFccPreviousBufferUri = previous;
            mFccNextBufferUri = next;
        }

        public List<Uri> getFccBufferUri() {
            List<Uri> uriList = new ArrayList<>();
            uriList.add(mFccPreviousBufferUri);
            uriList.add(mFccNextBufferUri);
            return uriList;
        }

        @MainThread
        protected void acquireTvHardware() {
            if (mMainHardware != null) {
                return;
            }
            Hardware hardware = mTvInputManager.acquireTvInputHardware(HARDWARE_MAIN_DEVICE_ID,
                    mTvInputInfo, mHardwareCallback);
            if (hardware == null) {
                Log.e(TAG, "Failed to acquire TvHardware:" + HARDWARE_MAIN_DEVICE_ID);
            } else {
                hardware.setStreamVolume(1.0f);
            }
            mMainHardware = hardware;
        }

        @MainThread
        protected void releaseTvHardware() {
            super.releaseTvHardware();
            if (mMainHardware != null) {
                mTvInputManager.releaseTvInputHardware(HARDWARE_MAIN_DEVICE_ID, mMainHardware);
                mMainHardware = null;
            }
        }

        @Override
        protected void preFirstTune() {
            setSurfaceTunnelId(INDEX_FOR_MAIN, 1);
            int aspect_mode = mSystemControlManager.GetDisplayMode(SystemControlManager.SourceInput.DTV.toInt());
            setTVAspectMode(aspect_mode == DISPLAY_MODE_NORMAL ? ASPECT_MODE_AUTO : ASPECT_MODE_CUSTOM);
            int subFlg = getSubtitleFlag();
            DtvkitGlueClient.getInstance().destroySubtitleCtl();
            DtvkitGlueClient.getInstance().attachSubtitleCtl(subFlg & 0xFF);
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            Log.d(TAG, "onSetSurface surface : " + surface);
            if (mTvInputInfo == null) {
                Log.e(TAG, "resource not ready!");
                return false;
            }
            if (surface != null) {
                AudioConfigManager.getInstance(getApplication())
                        .refreshAudioCfgBySrc(AudioConfigManager.AUDIO_OUTPUT_DELAY_SOURCE_DTV);
                acquireTvHardware();
            } else {
                if (!hasAnotherSession(this)) {
                    if (mMainHardware != null) {
                        onFinishAsync(false, false); // can't block setSurface here!
                        mMainHardware.setSurface(null, null);
                    }
                    releaseTvHardware();
                }
            }
            return super.onSetSurface(surface);
        }

        @Override
        public void onTimeShiftPlay(Uri uri) {
            isDvrSession = true;
            super.onTimeShiftPlay(uri);
        }

        @Override
        public boolean readyToPlay() {
            if (mSurface != null && mMainHardware != null && mMainStreamConfig != null && mCanPlay) {
                return true;
            }
            Log.d(TAG, "Surface:" + mSurface + ", MainHardware:" + mMainHardware
                + ", MainStreamConfig:" + Arrays.toString(mMainStreamConfig) + ", ready:" + mCanPlay);
            return false;
        }

        @Override
        public boolean onTune(Uri channelUri, Bundle params) {
            if (isDvrSession) {
                Log.e(TAG, "onTune isDvrSession, ignore");
                return false;
            }
            if (params != null) {
                String previous = params.getString("previous_buffer_uri");
                String next = params.getString("next_buffer_uri");
                Log.d(TAG, "test onTune previous = " + previous
                        + ", channelUri = " + channelUri
                        + ", next = " + next);
                if (FeatureUtil.getFeatureSupportFcc()) {
                    Uri p = previous != null ? Uri.parse(previous) : null;
                    Uri n = next != null ? Uri.parse(next) : null;
                    setFccBufferUri(p, n);
                }
            }
            return super.onTune(channelUri, params);
        }

        @Override
        protected boolean doTune(Channel oldChannel, Channel newChannel, Uri channelUri,
                                 String dvbUri, boolean mhegTune) {
            mParameterManager.saveChannelIdForSource(newChannel.getId());
            if (newChannel != null) {
                String previousCiNumber = null;
                String previousCiProfileVersion = null;
                String previousProfileName = null;
                if (oldChannel != null) {
                    previousCiNumber = TvContractUtils.getStringFromChannelInternalProviderData(
                            oldChannel, Channel.KEY_CHANNEL_CI_NUMBER, null);
                    previousProfileName = TvContractUtils.getStringFromChannelInternalProviderData(
                            oldChannel, Channel.KEY_CHANNEL_PROFILE, null);
                    previousCiProfileVersion = TvContractUtils.getStringFromChannelInternalProviderData(
                            oldChannel, Channel.KEY_CHANNEL_CI_PROFILE_VERSION, null);
                }
                String ciNumber = TvContractUtils.getStringFromChannelInternalProviderData(
                        newChannel, Channel.KEY_CHANNEL_CI_NUMBER, null);
                String profileName = TvContractUtils.getStringFromChannelInternalProviderData(
                        newChannel, Channel.KEY_CHANNEL_PROFILE, null);
                String ciProfileVersion = TvContractUtils.getStringFromChannelInternalProviderData(
                        newChannel, Channel.KEY_CHANNEL_CI_PROFILE_VERSION, null);
                if (!TextUtils.equals(previousCiNumber, ciNumber)) {
                    Log.i(TAG, "onTuneByHandlerThreadHandle ci changed = " + ciNumber
                            + "(" + (ciNumber == null ? "not ci channel" : "ci channel)"));
                    if ("v2".equalsIgnoreCase(previousCiProfileVersion)) {
                        playerNotifyCiProfileEvent("leave", previousCiNumber, previousProfileName, "v2");
                    }
                    if ("v2".equalsIgnoreCase(ciProfileVersion)) {
                        playerNotifyCiProfileEvent("enter", ciNumber, profileName, "v2");
                    }
                } else {
                    Log.i(TAG, "onTuneByHandlerThreadHandle ci ("
                            + (ciNumber == null ? "not ci channel" : "same ciNumber") + ")");
                }
            }
            if (mhegTune) {
                notifyChannelRetuned(channelUri);
                //mhegSuspend();
                //if (mhegGetNextTuneInfo(dvbUri) == 0) {
                //    notifyChannelRetuned(channelUri);
                //}
            }

            boolean mainMuteStatus = !FeatureUtil.getFeatureSupportFcc(); //Except for FCC, the Mute status of other scenes is controlled by LiveTV
            onFinish(Channel.isATV(newChannel), FeatureUtil.getFeatureSupportFcc() && !getFccBufferUri().isEmpty());
            userDataStatus(false);

            // start tune flow
            String previousUriStr = getChannelInternalDvbUriForFcc(mFccPreviousBufferUri);
            String nextUriStr = getChannelInternalDvbUriForFcc(mFccNextBufferUri);

            // MUST double check
            if (!isSessionAvailable()) {
                Log.e(TAG, "Abort tune because session is releasing...");
                return false;
            }
            DtvkitGlueClient.getInstance().registerSignalHandler(mHandler);
            if (mHbbTvManager != null) {
                mHbbTvManager.setTuneChannelUri(channelUri);
            }
            mTunedChannel = newChannel; // before play
            boolean playResult = false;
            if (null == dvbUri && channelUri != null) {
                Log.d(TAG, "Do Ip Channel Tune : " + newChannel.getAppLinkIntentUri());
                //Ip channel load url
                if (null != mHbbTvManager) {
                    mHbbTvManager.playIpChannel(channelUri, newChannel.getAppLinkIntentUri());
                    playResult = true;
                } else {
                    Log.d(TAG, "Do Ip Channel Tune error, hbbtv not init");
                }
            } else {
                if (!isPipSession() && Channel.isATV(oldChannel) != Channel.isATV(newChannel)) {
                    mMainHardware.setSurface(mSurface, Channel.isATV(mTunedChannel) ? mMainStreamConfig[1] : mMainStreamConfig[0]);
                }
                if (Channel.isATV(mTunedChannel)) {
                    playResult = playerPlay_ATV(mTunedChannel);
                    setATVMtsMode(mContext);
                } else {
                    playResult = playerPlay(INDEX_FOR_MAIN, dvbUri, mAudioADAutoStart,
                            mainMuteStatus, 0, previousUriStr, nextUriStr).equals("ok");
                }
                if (!isPipSession() && Channel.isATV(oldChannel) != Channel.isATV(newChannel)) {
                    mSystemControlManager.SetCurrentSourceInfo(
                            Channel.isATV(mTunedChannel) ? SystemControlManager.SourceInput.TV : SystemControlManager.SourceInput.DTV, 0, 0);
                }
            }
            if (playResult) {
                userDataStatus(true);
            } else {
                mTunedChannel = null;
                if (mHbbTvManager != null) {
                    mHbbTvManager.setTuneChannelUri(null);
                }
                if (mAutomaticSearchingReceiver.isBackGroundSearching()) {
                    //Channel switching will fail during background search
                    Log.i(TAG, "onTuneByHandlerThreadHandle : isBackGroundSearching");
                    Bundle event = new Bundle();
                    notifySessionEvent(ConstantManager.EVENT_PLAY_FAILED, event);
                }
                DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
            }
            return playResult;
        }

        @Override
        protected void onFinish(boolean noStopATv, boolean ignoreFcc) {
            Log.i(TAG, "onFinish noStopATv:" + noStopATv + ", ignoreFcc:" + ignoreFcc);
            if (noStopATv && Channel.isATV(mTunedChannel)) {
                Log.d(TAG, "onFinish No need to stop!");
            } else if (Channel.isATV(mTunedChannel)) {
                atvPlayerStop();
            } else {
                stopTimeshiftIfNeeded();
                if (!ignoreFcc) {
                    playerStop();
                }
                playerSetSubtitlesOn(false);
                playerSetTeletextOn(false, -1);
            }
        }
    }

    public abstract class DtvkitTvInputSession extends Session {
        protected final String TAG = getClass().getSimpleName() + "@" + mDtvkitTvInputSessionCount;
        protected Channel mTunedChannel = null;
        protected final Context mContext;

        private List<TvTrackInfo> mTunedTracks = new ArrayList<>();
        private RecordedProgram recordedProgram = null;
        private long originalStartPosition = TvInputManager.TIME_SHIFT_INVALID_TIME;
        private long startPosition = TvInputManager.TIME_SHIFT_INVALID_TIME;
        private long currentPosition = TvInputManager.TIME_SHIFT_INVALID_TIME;
        private float playSpeed = 0;
        private PlayerState playerState = PlayerState.STOPPED;
        private AvailableState timeshiftAvailable = new AvailableState();
        private int timeshiftBufferSizeMins = 60;
        private int timeshiftBufferSizeMBs = 0;
        private int timeshiftStartMode = 1; // auto or pause

        protected com.droidlogic.dtvkit.inputsource.DtvkitOverlayView mView = null;

        private boolean mTeleTextMixNormal = true;
        private int dvrSubtitleFlag = 0;

        private boolean mTimeShiftInited = false;
        private boolean mResourceOwnedByBr = true;

        private final long mCurrentSessionIndex;
        private HandlerThread mLivingHandlerThread = null;
        private Handler mHandlerThreadHandle = null;
        private Handler mMainHandle = null;

        private volatile SessionState mSessionState = SessionState.NONE;
        private boolean mIsPip;

        protected Surface mSurface;
        protected boolean mCanPlay = false;

        private int mWinWidth = 0;
        private int mWinHeight = 0;

        private DtvkitTvInput outService;
        private Uri mPendingTuneUri = null;

        private CaptioningManager mCaptioningManager = null;
        private final AudioSystemCmdManager mAudioSystemCmdManager;
        private int mCurrentAudioTrackId = -1;
        private ProviderSync mProviderSync = null;

        private long mLastTuneTime = 0;
        private boolean mIsTeletextStarted = false;
        private boolean mIsMhepAppStarted = false;
        private AlertDialog mProDialog;

        private final TuneInfo mTuneInfo = new TuneInfo();
        private final class TuneInfo {
            public boolean update = false;
            public boolean isDTv = true;
            public int frequency = 0;
            public String dvbUri = "";
        }
        protected TunerAdapter mTunerAdapter = null;

        private final class AvailableState {
            AvailableState() {
                state = State.NO;
            }

            public void setYes() {
                setYes(false);
            }

            public void setNo() {
                setNo(false);
            }

            public void setYes(boolean force) {
                set(State.YES, force);
            }

            public void setNo(boolean force) {
                set(State.NO, force);
            }

            public void disable() {
                set(State.DISABLED, true);
            }

            public boolean isAvailable() {
                return state == State.YES;
            }

            public String toString() {
                return state == State.NO ? "no" : (state == State.YES ? "yes" : "disabled");
            }

            private void set(State stat, boolean force) {
                if (force || state != State.DISABLED)
                    state = stat;
            }

            private State state;
        }
        DtvkitTvInputSession(DtvkitTvInput service, String sessionId, boolean isPip) {
            this(service, isPip);
            if (FeatureUtil.getFeatureSupportTunerFramework()) {
                Tuner tuner = new Tuner(service.getApplicationContext(), sessionId, PRIORITY_HINT_USE_CASE_TYPE_LIVE);
                if (true == isPip) {
                    mTunerAdapter = new TunerAdapter(tuner, TunerAdapter.TUNER_TYPE_PIP_FCC);
                } else {
                    mTunerAdapter = new TunerAdapter(tuner, TunerAdapter.TUNER_TYPE_DEFAULT);
                }
            }
        }

        DtvkitTvInputSession(DtvkitTvInput service, boolean isPip) {
            super(service.getApplicationContext());
            mContext = service.getApplicationContext();
            outService = service;
            mIsPip = isPip;
            mCurrentSessionIndex = mDtvkitTvInputSessionCount++;
            Log.i(TAG, "created " + this);
            mAudioSystemCmdManager = AudioSystemCmdManager.getInstance(getApplicationContext());
            mCaptioningManager =
                    (CaptioningManager) outService.getSystemService(Context.CAPTIONING_SERVICE);
            initWorkThread();
            if (!mIsPip) {
                mProviderSync = new ProviderSync();
                mProviderSync.run(new FvpChannelEnhancedInfoSync(outService, FvpChannelEnhancedInfoSync.FVP_SYNC_ALL_INFO));
            }
        }

        public boolean isPipSession() {
            return mIsPip;
        }

        public boolean isTuned() {
            return mSessionState == SessionState.TUNED;
        }

        public boolean isSessionAvailable() {
            return mSessionState == SessionState.NONE
                    || mSessionState == SessionState.TUNED;
        }

        private void initTimeShift() {
            if (!mTimeShiftInited && !mIsPip) {
                mTimeShiftInited = true;
                numActiveRecordings = recordingGetNumActiveRecordings();
                Log.i(TAG, "numActiveRecordings: " + numActiveRecordings);

                if (numRecorders == 0) {
                    updateRecorderNumber();
                }
                updateTunerNumber();
                if (numActiveRecordings < numRecorders) {
                    timeshiftAvailable.setYes(true);
                } else {
                    timeshiftAvailable.setNo(true);
                }

                playerSetTimeshiftBufferSize(getTimeshiftBufferSizeMins(), getTimeshiftBufferSizeMBs());
                resetRecordingPath();
            }
        }

        protected final HardwareCallback mHardwareCallback = new HardwareCallback() {
            final int STREAM_ID_NORMAL = 1;
            final int STREAM_ID_PIP    = 3;
            int streamId = 1;

            @Override
            public void onReleased() {
                runOnMainThread(() -> {
                    if (streamId == STREAM_ID_NORMAL) {
                        mMainStreamConfig = null;
                        Log.d(TAG, "MainHardware onReleased");
                    }
                    if (streamId == STREAM_ID_PIP) {
                        mPipStreamConfig = null;
                        Log.d(TAG, "PipHardware onReleased");
                    }
                });
            }

            @Override
            public void onStreamConfigChanged(TvStreamConfig[] configs) {
                if (configs == null || configs.length == 0) {
                    Log.w(TAG, "onStreamConfigChanged null");
                    return;
                }
                runOnMainThread(() -> {
                    Log.d(TAG, "onStreamConfigChanged configs : " + Arrays.toString(configs));
                    streamId = configs[0].getStreamId();
                    if (streamId == STREAM_ID_NORMAL) {
                        mMainStreamConfig = configs;
                        Message msg =  mMainHandle.obtainMessage(MSG_SET_SURFACE);
                        msg.arg1 = INDEX_FOR_MAIN;
                        mMainHandle.sendMessage(msg);
                    } else if (streamId == STREAM_ID_PIP) {
                        mPipStreamConfig = configs[0];
                        Message msg =  mMainHandle.obtainMessage(MSG_SET_SURFACE);
                        msg.arg1 = INDEX_FOR_PIP;
                        mMainHandle.sendMessage(msg);
                    } else {
                        Log.e(TAG, "onStreamConfigChanged, SHOULD NOT come here, streamId:" + streamId);
                    }
                });
            }
        };

        @Override
        public void onSetStreamVolume(float volume) {
            Log.i(TAG, "onSetStreamVolume " + volume);
            int index = Boolean.compare(mIsPip, false);
            Message msg = mHandlerThreadHandle.obtainMessage(MSG_SET_STREAM_VOLUME);
            msg.arg1 = Boolean.compare(volume > 0.0f, false);
            msg.arg2 = index;
            if (volume > 0.0f) {
                bMainAudioOutput = !mIsPip;
            }
            mHandlerThreadHandle.sendMessage(msg);
        }

        @Override
        public void onSetMain(boolean isMain) {
            Log.d(TAG, "onSetMain, isMain: " + isMain);
        }

        @Override
        public View onCreateOverlayView() {
            if (mIsPip) {
                return null;
            }
            Log.d(TAG, "onCreateOverlayView");
            if (mView == null) {
                mView = new com.droidlogic.dtvkit.inputsource.DtvkitOverlayView(outService, mMainHandle, checkEnableCC());
                mView.setSize(mWinWidth, mWinHeight);
            }
            mView.setOverlayTarget();
            mView.setOverlaySubtitleListener();
            initHbbtvResource();
            return mView;
        }

        private void doDestroyOverlay() {
            if (mIsPip) {
                return;
            }
            if (mView != null) {
                mView.destroy();
            }
            mView = null;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            Log.i(TAG, "onSetSurface " + surface);
            if (null != mTunerAdapter) {
                mTunerAdapter.setSurfaceToNative(surface);
            }
            mSurface = surface;
            if (surface != null) {
                Message msg =  mMainHandle.obtainMessage(MSG_SET_SURFACE);
                msg.arg1 = mIsPip ? INDEX_FOR_PIP : INDEX_FOR_MAIN;
                mMainHandle.sendMessage(msg);
            } else {
                if (!mIsPip && !hasAnotherSession(this)) {
                    mAudioSystemCmdManager.updateAudioPortGain(-1);
                    mSystemControlManager.SetDtvKitSourceEnable(0);
                    mSystemControlManager.SetCurrentSourceInfo(SystemControlManager.SourceInput.XXXX, 0, 0);
                }
            }
            return true;
        }

        private void fillSurfaceWithFixColor() {
            Rect frame = null;
            if (mView != null) {
                frame = getViewFrameOnScreen(mView);
            }
            if (mSurface == null || frame == null) {
                Log.w(TAG, "skip fillSurfaceWithFixColor, surface: " + mSurface
                        + ", frame: " + frame);
                return;
            }

            boolean lockCanvas = false;
            Canvas canvas = null;
            try {
                canvas = mSurface.lockCanvas(frame);
                lockCanvas = true;
                if (canvas != null) {
                    int color = FeatureUtil.getFeatureSupportFillSurfaceColor();
                    Log.d(TAG, "fillSurfaceWithFixColor color = " + Integer.toHexString(color));
                    canvas.drawColor(color);
                }
            } catch (Exception e) {
                Log.e(TAG, "fillSurfaceWithFixColor Exception = " + e.getMessage());
            } finally {
                if (lockCanvas) {
                    mSurface.unlockCanvasAndPost(canvas);
                } else {
                    Log.w(TAG, "fillSurfaceWithFixColor fail to lock surface");
                }
            }
        }

        private Rect getViewFrameOnScreen(View view) {
            Rect frame = new Rect();
            view.getGlobalVisibleRect(frame);
            RectF frameF = new RectF(frame);
            view.getMatrix().mapRect(frameF);
            frameF.round(frame);
            return frame;
        }

        private void sendFillSurface() {
            if (mMainHandle != null) {
                mMainHandle.removeMessages(MSG_FILL_SURFACE);
                mMainHandle.sendEmptyMessage(MSG_FILL_SURFACE);
            }
        }

        @Override
        public void onSurfaceChanged(int format, int width, int height) {
            Log.i(TAG, "onSurfaceChanged format:" + format + ", "
                + "(w=" + width + ",h=" + height + ")");
        }

        @Override
        public void onOverlayViewSizeChanged(int width, int height) {
            Log.i(TAG, "onOverlayViewSizeChanged "
                    + "from (w=" + mWinWidth + ",h=" + mWinHeight + ") "
                    + "to (w=" + width + ",h=" + height + ").");
            if (mWinWidth == width && mWinHeight == height) {
                return;
            }
            mWinWidth = width;
            mWinHeight = height;
            if (mView != null) {
                mView.setSize(width, height);
            }
        }

        @Override
        public boolean onTune(Uri channelUri, Bundle params) {
            if (readyToPlay()) {
                return onTune(channelUri);
            } else {
                mPendingTuneUri = channelUri;
                return false;
            }
        }

        @Override
        public boolean onTune(Uri channelUri) {
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_ON_TUNE);
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_ON_TUNE, 0, 0, channelUri);
                mHandlerThreadHandle.sendMessage(mess);
                Log.i(TAG, "onTune " + channelUri);
            }
            mIsTeletextStarted = false;
            mIsMhepAppStarted = false;
            return true;
        }

        @MainThread
        private void initHbbtvResource() {
            if (mIsPip) {
                return;
            }
            if (mHbbTvManager != null && mView != null) {
                Log.d(TAG, "init HbbtvResource");
                mHbbTvManager.setHbbTvManagerParams(this, mInputId, outService.getApplicationContext());
                mHbbTvManager.initHbbTvResource();
                mHbbTvManager.initBrowser();
                mView.addHbbTvView(mHbbTvManager.getHbbTvView(), mHbbTvManager);
            }
        }

        @MainThread
        private void deInitHbbtvResource() {
            if (mIsPip) {
                return;
            }
            if (mHbbTvManager != null) {
                Log.d(TAG, "deInit HbbtvResource");
                mHbbTvManager.releaseHbbTvResource();
            }
        }

        boolean stopTimeshiftIfNeeded() {
            boolean ret = false;
            if (FeatureUtil.getFeatureSupportTimeshifting()) {
                if (timeshiftRecorderState != RecorderState.STOPPED) {
                    Log.i(TAG, "reset timeshiftState to STOPPED.");
                    ret = stopTimeshiftRecordingInSession(false);
                    if (ret) {
                        timeshiftRecorderState = RecorderState.STOPPED;
                    }
                }
                timeshiftAvailable.setYes(true);
                mIsTimeshiftingPlayed = false;
            }
            return ret;
        }

        protected void onTuneByHandlerThreadHandle(Uri channelUri, boolean mhegTune) {
            if (!isSessionAvailable()) {
                Log.e(TAG, "Abort tune because of mSessionState:" + mSessionState);
                return;
            }
            initTimeShift();
            mSessionState = SessionState.TUNED;
            Log.i(TAG, "onTuneByHandlerThreadHandle " + channelUri);

            if (mMainHandle != null) {
                mMainHandle.sendEmptyMessage(MSG_HIDE_BLOCKED_TEXT);
//                mMainHandle.sendEmptyMessage(MSG_SHOW_TUNING_IMAGE);
                mMainHandle.removeMessages(MSG_SET_TELETEXT_MIX_NORMAL);
                mMainHandle.sendEmptyMessage(MSG_SET_TELETEXT_MIX_NORMAL);
            }

            Channel targetChannel = TvContractUtils.getChannel(mContentResolver, channelUri);
            if (channelUri == null) {
                targetChannel = createDummyATvChannel();
            } else if (targetChannel == null) {
                int dvbSource = getCurrentDvbSource();
                Channel firstDbValidChannel = getFirstChannel(dvbSource);
                if (firstDbValidChannel == null) {
                    if (ParameterManager.SIGNAL_ISDBT == dvbSource) {
                        firstDbValidChannel = createDummyATvChannel();
                    } else {
                        //if no channel,stop play
                        onFinish(false, false);
                        Log.e(TAG, "error:no channel.");
                        sendBundleToAppByTif(ConstantManager.EVENT_NO_CHANNEL_FOUND, new Bundle());
                        if (mMainHandle != null) {
                            mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                            Message msg = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                            msg.arg1 = 1;
                            mMainHandle.sendMessageDelayed(msg, 100);
                        }
                        return;
                    }
                } else {
                   Log.i(TAG, "Cannot find " + channelUri
                        + ", tuneTo _ID = " + firstDbValidChannel.getId()
                        + " " + firstDbValidChannel.getDisplayName());
                   notifyChannelRetuned(TvContract.buildChannelUri(firstDbValidChannel.getId()));
                }
                targetChannel = firstDbValidChannel;
            }

            if (targetChannel != null && mTunedChannel != null
                && targetChannel.getId() == mTunedChannel.getId()) {
                long time = SystemClock.uptimeMillis();
                if (time - mLastTuneTime < 1000) {
                    Log.w(TAG, "duplicate tune, last tune is " + (time - mLastTuneTime) +  "ms ago, abort!");
                    return;
                }
            }
            mLastTuneTime = SystemClock.uptimeMillis();

            String dvbUri;
            boolean isVirtual = TvContractUtils.getBooleanFromChannelInternalProviderData(
                        targetChannel, Channel.KEY_CHANNEL_CI_VIRTUAL_CHANNEL, false);
            if (isVirtual) {
                Log.i(TAG, "onTuneByHandlerThreadHandle isVirtual = " + isVirtual
                        + ", getInternalProviderData = " + targetChannel.getInternalProviderData());
                dvbUri = "vc://" + TvContractUtils.getStringFromChannelInternalProviderData(
                        targetChannel, Channel.KEY_CHANNEL_CI_NUMBER, null);
            } else {
                dvbUri = getChannelInternalDvbUri(targetChannel);
            }
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

            if (isIpChannel(targetChannel)) {
                Log.d(TAG, "do IP channel tune");
                dvbUri = null;
            }

            //comment it as need add pip
            //writeSysFs("/sys/class/video/video_global_output", "0");
            mAudioADAutoStart = mDataManager.getIntParameters(DataManager.TV_KEY_AD_SWITCH) == 1;
            mAudioSystemCmdManager.handleAdtvAudioEvent(
                    AudioSystemCmdManager.AUDIO_SERVICE_CMD_AD_SWITCH_ENABLE, mAudioADAutoStart ? 1 : 0, 0);
            mTuneInfo.isDTv = !Channel.isATV(targetChannel);
            boolean playResult = doTune(mTunedChannel, targetChannel, channelUri, dvbUri, mhegTune);
            if (channelUri == null && Channel.isATV(targetChannel)) {
                notifySessionEvent(ConstantManager.EVENT_TUNE_ATV_SNOW, null);
            }
            if (!isSessionAvailable()) {
                return;
            }
            if (!playResult) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);

                Log.e(TAG, "No play path available");
                Bundle event = new Bundle();
                event.putString(ConstantManager.KEY_INFO, "No play path available");
                notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
            }
            if (FeatureUtil.getFeatureSupportFillSurface()) {
                sendFillSurface();
            }
            Log.i(TAG, "onTuneByHandlerThreadHandle Done");
            return;
        }

        @Override
        public void onRelease() {
            Log.i(TAG, "onRelease");
            if (!isSessionAvailable()) {
                Log.w(TAG, "Session is released!!!");
                return;
            }
            if (null != mTunerAdapter) {
                mTunerAdapter.release();
            }
            mSessionState = SessionState.RELEASING;
            exitNumberSearch();
            doDestroyOverlay();
            mMainHandle.removeCallbacksAndMessages(null);
            if (!hasAnotherSession(this)) {
                deInitHbbtvResource();
            }
            mHandlerThreadHandle.sendEmptyMessage(MSG_DO_RELEASE);
        }

        private void doRelease() {
            Log.i(TAG, "doRelease");
            DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
            if (!hasAnotherSession(this)) {
                onFinish(true, false);
            }
            finalReleaseWorkThread();
            removeTunerSession(this);
            mSessionState = SessionState.RELEASED;
            if (null != mProviderSync) {
                mProviderSync.shutDown();
            }
            Log.i(TAG, "doRelease over");
        }

        protected abstract boolean doTune(Channel oldChannel, Channel newChannel,
                                          Uri channelUri, String dvbUri, boolean mhegTune);

        protected abstract void onFinish(boolean noStopATv, boolean ignoreFcc);
        @MainThread
        protected void onFinishAsync(final boolean noStopATv, final boolean ignoreFcc) {
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.postAtFrontOfQueue(() -> onFinish(noStopATv, ignoreFcc));
            }
        }

        protected abstract boolean readyToPlay();

        @MainThread
        protected void acquireTvHardware() {}

        @MainThread
        protected void releaseTvHardware() {
            mCanPlay = false;
        }

        protected void preFirstTune() {
            Log.d(TAG, "preFirstTune");
        }

        protected void tunePendingIfNeeded() {
            if (mPendingTuneUri != null) {
                Log.d(TAG, "tunePending " + mPendingTuneUri);
                Uri uri = mPendingTuneUri;
                if (TvContract.isRecordedProgramUri(mPendingTuneUri)) {
                    mHandlerThreadHandle.removeMessages(MSG_TIMESHIFT_PLAY);
                    Message mess = mHandlerThreadHandle.obtainMessage(MSG_TIMESHIFT_PLAY, 0, 0, uri);
                    boolean info = mHandlerThreadHandle.sendMessageDelayed(mess, 0);
                } else {
                    onTune(uri);
                }
                mPendingTuneUri = null;
            }
        }

        private synchronized void finalReleaseWorkThread() {
            Log.d(TAG, "finalReleaseWorkThread");
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeCallbacksAndMessages(null);
            }
            //don't set it to none
            //mMainHandle = null;
            //mHandlerThreadHandle = null;
            if (mLivingHandlerThread != null) {
                mLivingHandlerThread.quitSafely();
                mLivingHandlerThread = null;
            }
        }

        private Channel createDummyATvChannel() {
            InternalProviderData data = new InternalProviderData("ATV");
            try {
                data.put("vfmt", 0);
                data.put("frequency", 470000000);
                data.put("video_std", 0);
                data.put("audio_std", 0);
                data.put("is_auto_std", 0);
                data.put("fine_tune", 0);
                data.put("audio_compensation", 0);
                data.put("is_favourite", 0);
                data.put("multi_name", "");
                data.put("unikey", 0);
            } catch (InternalProviderData.ParseException e) {
                Log.e(TAG, e.getMessage());
            }

            return new Channel.Builder()
                    .setType(TvContract.Channels.TYPE_NTSC)
                    .setDisplayNumber("0")
                    .setServiceType(TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
                    .setOriginalNetworkId(0)
                    .setTransportStreamId(0)
                    .setServiceId(0)
                    .setInternalProviderData(data)
                    .setLocked(0)
                    .setChannelAntennaType(0)
                    .build();
        }

        public void onSetCaptionEnabled(boolean enabled) {
            Log.w(TAG, "caption switch will be controlled by mCaptionManager switch");
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            Log.i(TAG, "onSelectTrack " + type + ", " + trackId);
            boolean result = false;
            if (mHandlerThreadHandle != null) {
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_SELECT_TRACK, type, 0, trackId);
                result = mHandlerThreadHandle.sendMessage(mess);
            }
            return true;
        }

        private void doSelectTrack(int type, String trackId) {
            Log.i(TAG, "doSelectTrack " + type + ", " + trackId);
            if (type == TvTrackInfo.TYPE_AUDIO) {
                if (mResourceOwnedByBr) {
                    if (playerSelectAudioTrack((null == trackId) ? 0xFFFF : Integer.parseInt(trackId))) {
                        mCurrentAudioTrackId = Integer.parseInt(trackId);
                        notifyTrackSelected(type, trackId);
                        if (mHbbTvManager != null) {
                            mHbbTvManager.notifyTrackSelectedToHbbtv(type, trackId);
                        }
                    }
                } else {
                    mHbbTvManager.selectBroadbandTracksAndNotify(type, trackId);
                }
            } else if (type == TvTrackInfo.TYPE_SUBTITLE) {
                if (Channel.isATV(mTunedChannel)) {
                    AtvCcTool.getInstance().selectCCTrack(getApplicationContext(), trackId);
                    notifyTrackSelected(type, trackId);
                } else {
                    String sourceTrackId = trackId;
                    int subType = 4;//default sub
                    int isTele = 0;//default subtitle
                    if (!TextUtils.isEmpty(trackId) && !TextUtils.isDigitsOnly(trackId)) {
                        String[] nameValuePairs = trackId.split("&");
                        if (nameValuePairs.length >= 3) {
                            String[] nameValue = nameValuePairs[0].split("=");
                            String[] typeValue = nameValuePairs[1].split("=");
                            String[] teleValue = nameValuePairs[2].split("=");
                            if (nameValue.length == 2 && TextUtils.equals(nameValue[0], "id")
                                    && TextUtils.isDigitsOnly(nameValue[1])) {
                                trackId = nameValue[1];//parse id
                            }
                            if (typeValue.length == 2 && TextUtils.equals(typeValue[0], "type")
                                    && TextUtils.isDigitsOnly(typeValue[1])) {
                                subType = Integer.parseInt(typeValue[1]);//parse type
                            }
                            if (teleValue.length == 2 && TextUtils.equals(teleValue[0], "teletext")
                                    && TextUtils.isDigitsOnly(teleValue[1])) {
                                isTele = Integer.parseInt(teleValue[1]);//parse type
                            }
                        }
                        if (TextUtils.isEmpty(trackId) || !TextUtils.isDigitsOnly(trackId)) {
                            //notifyTrackSelected(type, sourceTrackId);
                            Log.d(TAG, "need trackId that only contains number sourceTrackId = "
                                    + sourceTrackId + ", trackId = " + trackId);
                            return;
                        }
                    }
                    if ((!FeatureUtil.getFeatureSupportCaptioningManager()
                            || (mCaptioningManager != null && mCaptioningManager.isEnabled()))
                            && selectSubtitleOrTeletext(isTele, subType, trackId)) {
                        notifyTrackSelected(type, sourceTrackId);
                        if (mHbbTvManager != null) {
                            mHbbTvManager.notifyTrackSelectedToHbbtv(type, sourceTrackId);
                        }
                    } else {
                        Log.d(TAG, "onSelectTrack mCaptioningManager closed or invalid sub");
                        notifyTrackSelected(type, null);
                        if (mHbbTvManager != null) {
                            mHbbTvManager.notifyTrackSelectedToHbbtv(type, null);
                        }
                    }
                }
            }
        }

        private void reloadHbbTvApplication() {
            runOnMainThread(()->{
                if (mHbbTvManager != null) {
                    Log.d(TAG,"reload the application when broadcast teletext off");
                    mHbbTvManager.reloadApplication();
                }
            });
        }

        private void closeHbbtvTeleTextApplication() {
            runOnMainThread(()->{
                 if (mHbbTvManager != null) {
                     mHbbTvManager.closeTeletextApplication();
                 }
            });
        }
        private boolean selectSubtitleOrTeletext(int isTele, int type, String indexId) {
            boolean result;
            Log.d(TAG, "selectSubtitleOrTeletext isTele = " + isTele
                    + ", type = " + type + ", indexId = " + indexId);
            if (TextUtils.isEmpty(indexId)) {//stop
                if (playerGetSubtitlesOn()) {
                    playerSetSubtitlesOn(false);//close if opened
                    Log.d(TAG, "selectSubtitleOrTeletext off setSubOff");
                    if ((mCusFeatureSubtitleCfg & 0x08) == 0x08) {
                        if (isSubtitleEnabled()) {
                            enableSubtitle(false);
                        }
                    }
                }
                if (playerIsTeletextOn()) {
                    boolean setTeleOff = playerSetTeletextOn(false, -1);//close if opened
                    Log.d(TAG, "selectSubtitleOrTeletext off setTeleOff = " + setTeleOff);
                    //reloadHbbTvApplication();
                    if (mSubFlagTtxPage) {
                        Log.d(TAG, "selectSubtitleOrTeletext ttx page exit, restart ttx sub");
                        playerSetSubtitlesOn(true);
                        mSubFlagTtxPage = false;
                    }
                    if (mMainHandle != null) {
                        mMainHandle.removeMessages(MSG_SET_TELETEXT_MIX_NORMAL);
                        mMainHandle.sendEmptyMessage(MSG_SET_TELETEXT_MIX_NORMAL);
                    }
                }
                boolean stopSub = playerSelectSubtitleTrack(0xFFFF);
                boolean stopTele = playerSelectTeletextTrack(0xFFFF);
                Log.d(TAG, "selectSubtitleOrTeletext stopSub = " + stopSub + ", stopTele = " + stopTele);
                result = true;
            } else if (TextUtils.isDigitsOnly(indexId)) {
                if (type == 4) {//sub
                    if (playerIsTeletextOn()) {
                        boolean setTeleOff = playerSetTeletextOn(false, -1);
                        Log.d(TAG, "selectSubtitleOrTeletext onSub setTeleOff = " + setTeleOff);
                    }
                    if ((mCusFeatureSubtitleCfg & 0x08) == 0x08) {
                        if (!isSubtitleEnabled()) {
                            enableSubtitle(true);
                        }
                    }
                    if (!playerGetSubtitlesOn()) {
                        playerSetSubtitlesOn(true);
                        Log.d(TAG, "selectSubtitleOrTeletext onSub setSubOn");
                    }
                    boolean startSub = playerSelectSubtitleTrack(Integer.parseInt(indexId));
                    Log.d(TAG, "selectSubtitleOrTeletext startSub = " + startSub);
                } else if (type == 6) {//teletext
                    if (playerGetSubtitlesOn()) {
                        playerSetSubtitlesOn(false);
                        mSubFlagTtxPage = true;
                        Log.d(TAG, "selectSubtitleOrTeletext onTele setSubOff");
                    }
                    if (!playerIsTeletextOn()) {
                        boolean setTeleOn = playerSetTeletextOn(true, Integer.parseInt(indexId));
                        Log.d(TAG, "selectSubtitleOrTeletext start setTeleOn = " + setTeleOn);
                        //closeHbbtvTeleTextApplication();
                    } else {
                        boolean startTele = false;
                        if ((getSubtitleFlag() & SUBTITLE_CTL_HK_TTX_PAG) == SUBTITLE_CTL_HK_TTX_PAG) {
                            startTele = playerSetTeletextOn(true, Integer.parseInt(indexId));
                        } else {
                            startTele = playerSelectTeletextTrack(Integer.parseInt(indexId));
                        }
                        Log.d(TAG, "selectSubtitleOrTeletext set setTeleOn = " + startTele);
                    }
                }
                result = true;
            } else {
                result = false;
                Log.d(TAG, "selectSubtitleOrTeletext unknown case");
            }
            return result;
        }

        private void initSubtitleOrTeletextIfNeed() {
            StringBuilder log = new StringBuilder("initSubtitleOrTeletextIfNeed");
            if (playerIsTeletextOn()) {
                String teletextTrackId = playerGetSelectedTeleTextTrackId();
                log.append(",Teletext:On teletextTrackId:").append(teletextTrackId);
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, teletextTrackId);
            } else if (playerGetSubtitlesOn()) {
                String subTrackId = playerGetSelectedSubtitleTrackId();
                log.append(",Subtitle:On subTrackId:").append(subTrackId);
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, subTrackId);;
            } else {
                log.append(",Teletext:Off Subtitle:Off");
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, null);
            }
            Log.d(TAG, log.toString());
        }

        private void updateTrackAndSelect(int isDvrPlaying, boolean clear) {
            if (Channel.isATV(mTunedChannel)) {
                List<TvTrackInfo> info = AtvCcTool.getInstance().getAtvCcTracks();
                notifyTracksChanged(info);
                if (info.isEmpty()) {
                    AtvCcTool.getInstance().stopCC();
                    notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, null);
                } else {
                    int selected = AtvCcTool.getInstance().getSavedCcChannel();
                    if (selected != -1) {
                        String trackId = AtvCcTool.getInstance().startCC(getApplicationContext(),
                                selected);
                        notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, trackId);
                    }
                }
            } else {
                boolean retSubtitle = (isDvrPlaying != 1) || (dvrSubtitleFlag != 1);

                if (clear) {
                    Log.w(TAG, "updateTrackAndSelect: clear Tracks, because not playing status");
                    mTunedTracks.clear();
                    notifyTracksChanged(mTunedTracks);
                    return;
                }
                if (retSubtitle && (!FeatureUtil.getFeatureSupportCaptioningManager()
                        || (mCaptioningManager != null && mCaptioningManager.isEnabled()))) {
                    playerSetSubtitlesOn(true);
                    if (isDvrPlaying == 1) {
                        dvrSubtitleFlag = 1;
                    }
                }
                mCurrentAudioTrackId = playerGetTracks(mTunedTracks, mTunedChannel, false);
                notifyTracksChanged(mTunedTracks);
                Log.i(TAG, "updateTrackAndSelect audio track selected: " + mCurrentAudioTrackId);
                notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, Integer.toString(mCurrentAudioTrackId));
                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, "0");
                initSubtitleOrTeletextIfNeed();
            }
        }

        @Override
        public void onUnblockContent(TvContentRating unblockedRating) {
            super.onUnblockContent(unblockedRating);
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_SET_UNBLOCK);
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_SET_UNBLOCK, unblockedRating);
                mHandlerThreadHandle.sendMessage(mess);
            }
        }

        @Override
        public void notifyVideoAvailable() {
            super.notifyVideoAvailable();
            if (mMainHandle != null) {
                mMainHandle.sendEmptyMessage(MSG_HIDE_BLOCKED_TEXT);
                mMainHandle.sendEmptyMessage(MSG_HIDE_TUNING_IMAGE);
            }

            int subFlg = getSubtitleFlag();
            if (mMainHandle != null && subFlg >= SUBTITLE_CTL_HK_DVB_SUB) {
                Message msg = mMainHandle.obtainMessage(MSG_SUBTITLE_SHOW_CLOSED_CAPTION, 0, 0, null);
                mMainHandle.sendMessage(msg);
            }
        }

        @Override
        public void notifyVideoUnavailable(final int reason) {
            boolean broadcastResource = true;
            if (mHbbTvManager != null ) {
                HbbTvManager.HbbTvApplicationStartCallBack hbbTvApplicationCallback = new HbbTvManager.HbbTvApplicationStartCallBack() {
                    public void onHbbtvApplicationStart(boolean applicationStatus) {
                        Log.d(TAG,"onHbbtvApplicationStart");
                        Bundle request = new Bundle();
                        request.putString("isRunning", applicationStatus ? "true" : "false");
                        sendBundleToAppByTif(ConstantManager.ACTION_HBBTV_APPLICATION_RUNNING, request);
                    }
                };
                mHbbTvManager.registerHbbtvApplicationStartCallBack(hbbTvApplicationCallback);
                broadcastResource = mHbbTvManager.isResourceOwnedByBr();
            }
            if (broadcastResource) {
                super.notifyVideoUnavailable(reason);
            }
            if (TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY == reason
                    || TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL == reason) {
                if (mMainHandle != null) {
                    mMainHandle.sendEmptyMessage(MSG_HIDE_BLOCKED_TEXT);
                    mMainHandle.sendEmptyMessage(MSG_HIDE_TUNING_IMAGE);
                }
            }
        }

        @Override
        public void onAppPrivateCommand(String action, Bundle data) {
            Log.i(TAG, "onAppPrivateCommand " + action + ", " + data);
            if ("action_teletext_start".equals(action) && data != null) {
                boolean start = data.getBoolean("action_teletext_start", false);
                Log.d(TAG, "do private cmd: action_teletext_start: " + start);
            } else if ("action_teletext_up".equals(action) && data != null) {
                boolean actionUp = data.getBoolean("action_teletext_up", false);
                Log.d(TAG, "do private cmd: action_teletext_up: " + actionUp);
                playerNotifyTeletextEvent(16);
            } else if ("action_teletext_down".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("action_teletext_down", false);
                Log.d(TAG, "do private cmd: action_teletext_down: " + actionDown);
                playerNotifyTeletextEvent(15);
            } else if ("action_teletext_number".equals(action) && data != null) {
                int number = data.getInt("action_teletext_number", -1);
                Log.d(TAG, "do private cmd: action_teletext_number: " + number);
                final int TT_EVENT_0 = 4;
                int hundred = (number % 1000) / 100;
                int decade = (number % 100) / 10;
                int unit = (number % 10);
                if (number >= 100) {
                    playerNotifyTeletextEvent(hundred + TT_EVENT_0);
                    playerNotifyTeletextEvent(decade + TT_EVENT_0);
                    playerNotifyTeletextEvent(unit + TT_EVENT_0);
                } else if (number >= 10) {
                    playerNotifyTeletextEvent(decade + TT_EVENT_0);
                    playerNotifyTeletextEvent(unit + TT_EVENT_0);
                } else {
                    playerNotifyTeletextEvent(unit + TT_EVENT_0);
                }
            } else if ("action_teletext_country".equals(action) && data != null) {
                int number = data.getInt("action_teletext_country", -1);
                Log.d(TAG, "do private cmd: action_teletext_country: " + number);
                final int TT_EVENT_0 = 4;
                int hundred = (number % 1000) / 100;
                int decade = (number % 100) / 10;
                int unit = (number % 10);
                if (number >= 100) {
                    playerNotifyTeletextEvent(hundred + TT_EVENT_0);
                    playerNotifyTeletextEvent(decade + TT_EVENT_0);
                    playerNotifyTeletextEvent(unit + TT_EVENT_0);
                } else if (number >= 10) {
                    playerNotifyTeletextEvent(decade + TT_EVENT_0);
                    playerNotifyTeletextEvent(unit + TT_EVENT_0);
                } else {
                    playerNotifyTeletextEvent(unit + TT_EVENT_0);
                }
            } else if ("quick_navigate_1".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("quick_navigate_1", false);
                Log.d(TAG, "do private cmd: quick_navigate_1: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(0);
                }
            } else if ("quick_navigate_2".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("quick_navigate_2", false);
                Log.d(TAG, "do private cmd: quick_navigate_2: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(1);
                }
            } else if ("quick_navigate_3".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("quick_navigate_3", false);
                Log.d(TAG, "do private cmd: quick_navigate_3: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(2);
                }
            } else if ("quick_navigate_4".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("quick_navigate_4", false);
                Log.d(TAG, "do private cmd: quick_navigate_4: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(3);
                }
            } else if ("previous_page".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("previous_page", false);
                Log.d(TAG, "do private cmd: previous_page: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(16);
                }
            } else if ("next_page".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("next_page", false);
                Log.d(TAG, "do private cmd: next_page: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(15);
                }
            } else if ("index_page".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("index_page", false);
                Log.d(TAG, "do private cmd: index_page: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(14);
                }
            } else if ("next_sub_page".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("next_sub_page", false);
                Log.d(TAG, "do private cmd: next_sub_page: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(17);
                }
            } else if ("previous_sub_page".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("previous_sub_page", false);
                Log.d(TAG, "do private cmd: previous_sub_page: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(18);
                }
            } else if ("back_page".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("back_page", false);
                Log.d(TAG, "do private cmd: back_page: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(19);
                }
            } else if ("forward_page".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("forward_page", false);
                Log.d(TAG, "do private cmd: forward_page: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(20);
                }
            } else if ("hold".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("hold", false);
                Log.d(TAG, "do private cmd: hold: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(21);
                }
            } else if ("reveal".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("reveal", false);
                Log.d(TAG, "do private cmd: reveal: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(22);
                }
            } else if ("clear".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("clear", false);
                Log.d(TAG, "do private cmd: clear: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(23);
                }
            } else if ("clock".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("clock", false);
                Log.d(TAG, "do private cmd: clock: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(24);
                }
            } else if ("mix_video".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("mix_video", false);
                Log.d(TAG, "do private cmd: mix_video: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(25);
                }
            } else if ("double_height".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("double_height", false);
                Log.d(TAG, "do private cmd: double_height: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(26);
                }
            } else if ("double_scroll_up".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("double_scroll_up", false);
                Log.d(TAG, "do private cmd: double_scroll_up: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(27);
                }
            } else if ("double_scroll_down".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("double_scroll_down", false);
                Log.d(TAG, "do private cmd: double_scroll_down: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(28);
                }
            } else if ("timer".equals(action) && data != null) {
                boolean actionDown = data.getBoolean("timer", false);
                Log.d(TAG, "do private cmd: timer: " + actionDown);
                if (actionDown) {
                    playerNotifyTeletextEvent(29);
                }
            } else if (ConstantManager.ACTION_TIF_CONTROL_OVERLAY.equals(action)) {
                boolean show = data.getBoolean(ConstantManager.KEY_TIF_OVERLAY_SHOW_STATUS, false);
                Log.d(TAG, "do private cmd:" + ConstantManager.ACTION_TIF_CONTROL_OVERLAY + ", show:" + show);
            } else if (ConstantManager.ACTION_TIF_BEFORE_TUNE.equals(action)) {
                Log.d(TAG, "do private ACTION_TIF_BEFORE_TUNE");
                //comment it as block osd will cover pip video
                /*if (mView != null) {
                    mView.showTuningImage(null);
                }*/
            } else if (TextUtils.equals(DataManager.ACTION_DTV_AUDIO_STREAM_TYPE, action) && data != null) {
                int type = data.getInt(action);
                int version = data.getInt("ver");
                // 108473 : audioType
                Log.d(TAG, "do private cmd: ACTION_DTV_AUDIO_STREAM_TYPE: " + type + ", ver=" + version);
                // critical sequence
                if (getPipTunerSession() != null) {
                    int mutePath = bMainAudioOutput ? INDEX_FOR_PIP : INDEX_FOR_MAIN;
                    playerSetAudioStreamType(mutePath, type);
                }
                int unmutePath = bMainAudioOutput ? INDEX_FOR_MAIN : INDEX_FOR_PIP;
                playerSetAudioStreamType(unmutePath, type);
                playerSetAdParams(true, 0);
            } else if (TextUtils.equals(DataManager.ACTION_DTV_ENABLE_AUDIO_AD, action) && data != null) {
                mAudioADAutoStart = data.getInt(DataManager.PARA_ENABLE) == 1;
                int type = data.getInt("type", -1);
                boolean recover = type != ConstantManager.AUDIO_STREAM_TYPE.NORMAL;
                Log.d(TAG, "do private cmd: ACTION_DTV_ENABLE_AUDIO_AD: " + mAudioADAutoStart
                        + ", type = " + type);
                // critical sequence
                if (getPipTunerSession() != null) {
                    int mutePath = bMainAudioOutput ? INDEX_FOR_PIP : INDEX_FOR_MAIN;
                    playerSetAudioDescriptionOn(mutePath, mAudioADAutoStart);
                }
                int unmutePath = bMainAudioOutput ? INDEX_FOR_MAIN : INDEX_FOR_PIP;
                playerSetAudioDescriptionOn(unmutePath, mAudioADAutoStart);
                if (mHbbTvManager != null) {
                    mHbbTvManager.setAudioDescriptions();
                }
            } else if (TextUtils.equals(DataManager.ACTION_AD_MIXING_LEVEL, action)) {
                mAudioADMixingLevel = data.getInt(DataManager.PARA_VALUE1);
                Log.d(TAG, "do private cmd: ACTION_AD_MIXING_LEVEL: " + mAudioADMixingLevel);
                int index = bMainAudioOutput ? INDEX_FOR_MAIN : INDEX_FOR_PIP;
                playerSetADMixLevel(index, mAudioADMixingLevel);
            } else if (TextUtils.equals(DataManager.ACTION_AD_VOLUME_LEVEL, action)) {
                mAudioADVolume = data.getInt(DataManager.PARA_VALUE1);
                Log.d(TAG, "do private cmd: ACTION_AD_VOLUME_LEVEL: " + mAudioADVolume);
                int index = bMainAudioOutput ? INDEX_FOR_MAIN : INDEX_FOR_PIP;
                playerSetADVolume(index, mAudioADVolume);
            } else if (TextUtils.equals(PropSettingManager.ACTON_CONTROL_TIMESHIFT, action)) {
                if (data != null) {
                    boolean status = data.getBoolean(
                            PropSettingManager.VALUE_CONTROL_TIMESHIFT, false);
                    if (status) {
                        int duration = data.getInt(
                                PropSettingManager.VALUE_TIMESHIFT_DURATION, timeshiftBufferSizeMins);
                        if (duration != timeshiftBufferSizeMins) {
                            timeshiftBufferSizeMins = duration;
                        }
                        timeshiftStartMode = data.getInt(
                                PropSettingManager.VALUE_TIMESHIFT_START_MODE, 1);
                        Log.d(TAG, "VALUE_TIMESHIFT_START_MODE:" + timeshiftStartMode);
                        sendMsgTryStartTimeshift(0);
                    } else {
                        sendMsgTryStopTimeshift(0);
                    }
                }
            } else if (TextUtils.equals("delete_profile", action)) {
                String ciNumber = data.getString("ci_number");
                Log.d(TAG, "do private cmd:" + "delete_profile" + ", ciNumber:" + ciNumber);
            } else if (TextUtils.equals("focus_info", action)) {
                boolean isProfile = data.getBoolean("is_profile");
                String ciNumber = data.getString("ci_number");
                String profileName = data.getString("profile_name");
                String ciEnterStatus = data.getString("status");
                Log.d(TAG, "do private cmd:" + "focus_info" + ", isProfile:" + isProfile
                        + ", ciNumber:" + ciNumber + ", profileName:" + profileName
                        + ", ciEnterStatus = " + ciEnterStatus);
                playerNotifyCiProfileEvent(ciEnterStatus, ciNumber, profileName, "v1");
            } else if (TextUtils.equals(ConstantManager.ACTION_CI_PLUS_INFO, action)) {
                String command = data.getString(ConstantManager.CI_PLUS_COMMAND);
                if (ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_REQUEST.equals(command)) {
                    int module = data.getInt(ConstantManager.VALUE_CI_PLUS_SEARCH_MODULE);
                    Log.d(TAG, "do private cmd:" + action + " module = " + module);
                    doOperatorSearch(module);
                } else {
                    Log.d(TAG, "do private cmd:" + action + " none");
                }
            } else if ("force_stop_record".equals(action)) {
                String uri = null;
                JSONArray activeRecordings = recordingGetActiveRecordings();
                if (activeRecordings != null) {
                    for (int i = 0; i < activeRecordings.length(); i++) {
                        try {
                            uri = activeRecordings.getJSONObject(i).getString("uri");
                        } catch (JSONException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                }
                if (uri != null) {
                    recordingStopRecording(uri);
                }
            } else if ("cas_request".equals(action)) {
                onCasRequest(data);
            } else if ("block_channel".equals(action)) {
                boolean lock = data.getBoolean("is_locked");
                long channelId = data.getLong("channel_id");
                requestBlockChannel(channelId, lock);
            } else if ("unblockContent".equals(action)) {
                long id = data.getLong("channel_id", -1);
                if (id >= 0) {
                    Log.w(TAG, "id:" + id + ", " + mTunedChannel);
                    if (mTunedChannel == null || id != mTunedChannel.getId()) {
                        return;
                    }
                }
                onUnblockContent(TvContentRating.createRating("com.android.tv", "DVB", "DVB_0"));
            } else if (TextUtils.equals(ConstantManager.ACTION_START_FVP_APP, action) && null != data) {
                String appUrl = data.getString("app_url");
                if (null != appUrl) {
                    if (null != mHbbTvManager) {
                        mHbbTvManager.loadUrlApplication(appUrl);
                    } else {
                        HbbTvManager.getInstance().registerHbbTvViewInitCallback(()->{
                            if ((null != mHbbTvManager) && (null != appUrl)) {
                                mHbbTvManager.loadUrlApplication(appUrl);
                            } else {
                                Log.e(TAG, "ACTION_START_FVP_APP load fail");
                            }
                        });
                        Log.d(TAG, "ACTION_START_FVP_APP HbbTvManager not init, need register callback");
                    }
                } else {
                    Log.e(TAG, "ACTION_START_FVP_APP error  HbbTvManager is null");
                }
            } else if (TextUtils.equals("pvr_seek_information", action)) {
                long seekTime = data.getLong("pvr_seek_time");
                playerSeekTo(seekTime / 1000);
            } else if ("action_atv_set_output_mode".equals(action) && data != null) {
                int value = data.getInt("mts_output_mode", 0);
                DataProviderManager.putIntValue(mContext, ConstantManager.ACTION_ATV_SET_MTS_MODE, value);
            } else if (TextUtils.equals("tvscan_number_search", action)) {
                int number = Integer.parseInt(data.getString("number_search_number"));
                enterNumberSearch();
                mHandlerThreadHandle.sendMessage(mHandlerThreadHandle.obtainMessage(MSG_NUMBER_SEARCH, number, 0));
            } else if (TextUtils.equals("action_ewbs_switch_off", action)) {
                if (mView != null) {
                    mView.hideEWBSAlarmView();
                }
                if (mHandlerThreadHandle != null) {
                    Message msg = mMainHandle.obtainMessage(MSG_BLOCK_MUTE_OR_UNMUTE, 0, 0);
                    mHandlerThreadHandle.sendMessage(msg);
                }
            } else if (TextUtils.equals(ConstantManager.EVENT_TUNE_ATV_SNOW, action)) {
                if (mHandlerThreadHandle != null) {
                    Message msg = mMainHandle.obtainMessage(MSG_TUNE_ATV_SNOW, 0, 0);
                    mHandlerThreadHandle.sendMessage(msg);
                }
            }
        }



        private void enterNumberSearch() {
            final AlertDialog.Builder builder = new AlertDialog.Builder(DtvkitTvInput.this);
            mProDialog = builder.create();
            final View dialogView = View.inflate(DtvkitTvInput.this, R.layout.confirm_search, null);
            final TextView title = dialogView.findViewById(R.id.dialog_title);
            dialogView.findViewById(R.id.confirm).setVisibility(View.INVISIBLE);
            dialogView.findViewById(R.id.cancel).setVisibility(View.INVISIBLE);
            title.setText(R.string.number_search_and_wait);
            mProDialog.setMessage(getResources().getString(R.string.searching));
            //prevent exit key
            mProDialog.setCancelable(false);
            mProDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            mProDialog.setView(dialogView);
            mProDialog.show();
            WindowManager.LayoutParams params = mProDialog.getWindow().getAttributes();
            params.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    500, getResources().getDisplayMetrics());
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            mProDialog.getWindow().setAttributes(params);
            mProDialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
            Log.w(TAG, "enterNumberSearch");
        }

        private void exitNumberSearch() {
            if (mProDialog != null && mProDialog.isShowing()) {
                Log.w(TAG, "exitNumberSearch");
                mProDialog.dismiss();
            }
            mProDialog = null;
        }

        @Override
        public void onTimeShiftPlay(Uri uri) {
            Log.i(TAG, "onTimeShiftPlay " + uri);
            if (!readyToPlay()) {
                mPendingTuneUri = uri;
                return;
            }
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TIMESHIFT_PLAY);
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_TIMESHIFT_PLAY, 0, 0, uri);
                boolean info = mHandlerThreadHandle.sendMessage(mess);
                Log.d(TAG, "onTimeShiftPlay sendMessage " + info);
            }
        }

        @Override
        public void onTimeShiftPause() {
            Log.i(TAG, "onTimeShiftPause ");
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TIMESHIFT_PAUSE);
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_TIMESHIFT_PAUSE, 0, 0, null);
                boolean info = mHandlerThreadHandle.sendMessage(mess);
                Log.d(TAG, "onTimeShiftPause sendMessage " + info);
            }
        }

        @Override
        public void onTimeShiftResume() {
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TIMESHIFT_RESUME);
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_TIMESHIFT_RESUME, 0, 0, null);
                boolean info = mHandlerThreadHandle.sendMessage(mess);
                Log.d(TAG, "onTimeShiftResume sendMessage " + info);
            }
        }

        @Override
        public void onTimeShiftSeekTo(long timeMs) {
            Log.i(TAG, "onTimeShiftSeekTo: " + timeMs);
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TIMESHIFT_SEEK);
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_TIMESHIFT_SEEK, 0, 0, timeMs);
                boolean info = mHandlerThreadHandle.sendMessage(mess);
                Log.d(TAG, "onTimeShiftSeekTo sendMessage " + info);
            }
        }

        @Override
        public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
            Log.i(TAG, "onTimeShiftSetPlaybackParams");
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TIMESHIFT_PLAY_SET_PLAYBACK_PARAMS);
                Message mess = mHandlerThreadHandle.obtainMessage(MSG_TIMESHIFT_PLAY_SET_PLAYBACK_PARAMS, 0, 0, params);
                boolean info = mHandlerThreadHandle.sendMessage(mess);
                Log.d(TAG, "onTimeShiftSetPlaybackParams sendMessage " + info);
            }
        }

        @Override
        public long onTimeShiftGetStartPosition() {
            String comments = "";
            if (timeshiftRecorderState != RecorderState.STOPPED) {
                long truncated = playerGetElapsedAndTruncated()[1];
                long diff = PropSettingManager.getStreamTimeDiff();

                if (originalStartPosition != 0 && originalStartPosition != TvInputManager.TIME_SHIFT_INVALID_TIME) {
                    startPosition = originalStartPosition + truncated + diff;
                }
                comments = " timeshifting. " + "(truncated:" + truncated + ", diff:" + diff + ")ms";
            }
            Log.i(TAG, "startPosition:" + startPosition + ", as date = "
                    + ConvertSettingManager.convertLongToDate(startPosition)
                    + comments);
            return startPosition;
        }

        @Override
        public long onTimeShiftGetCurrentPosition() {
            String comments = "";
            if (startPosition == 0) /* Playing back recorded program */ {
                if (playerState == PlayerState.PLAYING) {
                    long e_t_l[] = playerGetElapsedAndTruncated();
                    long length = e_t_l[2];
                    currentPosition = e_t_l[0];
                    if ((length - currentPosition) < 1000) {
                        currentPosition = length;//use length if recorder length - current play position < 1s
                    }
                    comments = "playing back record program.";
                    if (length == 0 && e_t_l[3] == -1) {
                        currentPosition = recordedProgram.getRecordingDurationMillis();
                        comments = "playback has stopped.";
                    }
                } else if (playerState == PlayerState.STOPPED) {
                    currentPosition = recordedProgram.getRecordingDurationMillis();
                    comments = "playback has stopped.";
                }
            } else if (timeshiftRecorderState == RecorderState.RECORDING) {
                long e_t_l[] = playerGetElapsedAndTruncated();
                long elapsed = e_t_l[0];
                long length = e_t_l[2];
                long diff = PropSettingManager.getStreamTimeDiff();

                if ((length - elapsed) < 1000 && playSpeed < 0.0)
                    currentPosition = PropSettingManager.getCurrentStreamTime(true);
                else
                    currentPosition = elapsed + originalStartPosition + diff;
                comments = "timeshifting." + ", (elapsed:" + elapsed + ", (length:" + length
                        + ", (playSpeed:" + playSpeed + ", diff:" + diff + ")ms";
            } else if (startPosition == TvInputManager.TIME_SHIFT_INVALID_TIME) {
                currentPosition = TvInputManager.TIME_SHIFT_INVALID_TIME;
                comments = "Invalid time.";
            } else {
                if (!mIsPip) {
                    currentPosition = PropSettingManager.getCurrentStreamTime(true);/*System.currentTimeMillis()*/
                } else {
                    currentPosition = PropSettingManager.getCurrentPipStreamTime(true);
                }
                comments = "live tv.";
            }
            Log.d(TAG, "currentPosition = " + currentPosition
                    + ", as date = " + ConvertSettingManager.convertLongToDate(currentPosition)
                    + ", " + comments);
            return currentPosition;
        }

        private RecordedProgram getRecordedProgram(Uri uri) {
            RecordedProgram recordedProgram = null;
            Cursor cursor = null;
            try {
                if (uri != null) {
                    cursor = outService.getContentResolver().query(
                            uri, RecordedProgram.PROJECTION, null, null, null);
                }
                while (null != cursor && cursor.moveToNext()) {
                    recordedProgram = RecordedProgram.fromCursor(cursor);
                }
            } catch (Exception e) {
                Log.e(TAG, "getRecordedProgram query Failed = " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return recordedProgram;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            boolean used = false;

            Log.i(TAG, "onKeyDown " + event);
            if (mCiAuthenticatedStatus) {
                Log.i(TAG, "onKeyDown skip as ci Authentication");
                showToast(R.string.play_ci_authentication);
                return true;
            }
            /* It's possible for a keypress to be registered before the overlay is created */
            if (mView == null) {
                used = super.onKeyDown(keyCode, event);
            } else {
                if (mView.handleKeyDown(keyCode, event, mIsMhepAppStarted)) {
                    used = true;
                } else if (keyCode == KeyEvent.KEYCODE_ZOOM_OUT) {
                    used = true;
                } else if (isTeletextNeedKeyCode(keyCode) && mIsTeletextStarted) {
                    if (playerIsTeletextOn()) {
                        used = true;
                    }
                } else {
                    used = super.onKeyDown(keyCode, event);
                }
            }
            return used;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            boolean used = false;
            Log.i(TAG, "onKeyUp " + event);
            if (mCiAuthenticatedStatus) {
                Log.i(TAG, "onKeyDown skip as ci Authentication");
                showToast(R.string.play_ci_authentication);
                return true;
            }
            /* It's possible for a keypress to be registered before the overlay is created */
            if (mView == null) {
                used = super.onKeyUp(keyCode, event);
            } else {
                if (mView.handleKeyUp(keyCode, event)) {
                    used = true;
                } else if (keyCode == KeyEvent.KEYCODE_ZOOM_OUT) {
                    if (playerIsTeletextOn()) {
                        playerStopTeletext();
                        notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, null);
                        reloadHbbTvApplication();
                    } else {
                        closeHbbtvTeleTextApplication();
                        playerStartTeletext(-1);
                        if (playerIsTeletextStarted()) {
                            notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, playerGetSelectedTeleTextTrackId());
                        } else {
                            showToast(R.string.strNoTeletext);
                        }
                    }
                    used = true;
                } else if (isTeletextNeedKeyCode(keyCode) && mIsTeletextStarted) {
                    if (playerIsTeletextOn()) {
                        dealTeletextKeyCode(keyCode);
                        used = true;
                    }
                } else {
                    used = super.onKeyUp(keyCode, event);
                }
            }
            return used;
        }

        private boolean isTeletextNeedKeyCode(int keyCode) {
            return keyCode == KeyEvent.KEYCODE_BACK ||
                    keyCode == KeyEvent.KEYCODE_CAPTIONS ||
                    keyCode == KeyEvent.KEYCODE_PROG_RED ||
                    keyCode == KeyEvent.KEYCODE_PROG_GREEN ||
                    keyCode == KeyEvent.KEYCODE_PROG_YELLOW ||
                    keyCode == KeyEvent.KEYCODE_PROG_BLUE ||
                    keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                    keyCode == KeyEvent.KEYCODE_0 ||
                    keyCode == KeyEvent.KEYCODE_1 ||
                    keyCode == KeyEvent.KEYCODE_2 ||
                    keyCode == KeyEvent.KEYCODE_3 ||
                    keyCode == KeyEvent.KEYCODE_4 ||
                    keyCode == KeyEvent.KEYCODE_5 ||
                    keyCode == KeyEvent.KEYCODE_6 ||
                    keyCode == KeyEvent.KEYCODE_7 ||
                    keyCode == KeyEvent.KEYCODE_8 ||
                    keyCode == KeyEvent.KEYCODE_9;
        }

        private void dealTeletextKeyCode(int keyCode) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    Log.d(TAG, "dealTeletextKeyCode close teletext");
                    playerSetTeletextOn(false, -1);
                    notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, null);
                    if (!mTeleTextMixNormal) {
                        mTeleTextMixNormal = true;
                        mView.setTeletextMix(TTX_MODE_NORMAL, isSubtitleCTL());
                        Message msg = mHandlerThreadHandle.obtainMessage(MSG_SET_RECTANGLE);
                        msg.arg1 = 0;
                        mHandlerThreadHandle.sendMessage(msg);
                    }

                    Log.d(TAG, "dealTeletextKeyCode mSubFlagTtxPage:" + mSubFlagTtxPage);
                    if (mSubFlagTtxPage) {
                        playerSetSubtitlesOn(true);
                        mSubFlagTtxPage = false;
                    }
                    break;
                case KeyEvent.KEYCODE_PROG_RED:
                    Log.d(TAG, "dealTeletextKeyCode quick_navigate_1");
                    playerNotifyTeletextEvent(0);
                    break;
                case KeyEvent.KEYCODE_PROG_GREEN:
                    Log.d(TAG, "dealTeletextKeyCode quick_navigate_2");
                    playerNotifyTeletextEvent(1);
                    break;
                case KeyEvent.KEYCODE_PROG_YELLOW:
                    Log.d(TAG, "dealTeletextKeyCode quick_navigate_3");
                    playerNotifyTeletextEvent(2);
                    break;
                case KeyEvent.KEYCODE_PROG_BLUE:
                    Log.d(TAG, "dealTeletextKeyCode quick_navigate_4");
                    playerNotifyTeletextEvent(3);
                    break;
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_DPAD_UP:
                    Log.d(TAG, "dealTeletextKeyCode next_page");
                    playerNotifyTeletextEvent(15);
                    break;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    Log.d(TAG, "dealTeletextKeyCode previous_page");
                    playerNotifyTeletextEvent(16);
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    playerNotifyTeletextEvent(18);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    playerNotifyTeletextEvent(17);
                    break;
                case KeyEvent.KEYCODE_CAPTIONS:
                    playerNotifyTeletextEvent(31);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    if (mParameterManager != null)
                        mParameterManager.switchNextTextCharRegion();
                    break;
                case KeyEvent.KEYCODE_0:
                case KeyEvent.KEYCODE_1:
                case KeyEvent.KEYCODE_2:
                case KeyEvent.KEYCODE_3:
                case KeyEvent.KEYCODE_4:
                case KeyEvent.KEYCODE_5:
                case KeyEvent.KEYCODE_6:
                case KeyEvent.KEYCODE_7:
                case KeyEvent.KEYCODE_8:
                case KeyEvent.KEYCODE_9: {
                    final int TT_EVENT_0 = 4;
                    int number = keyCode - KeyEvent.KEYCODE_0;
                    Log.d(TAG, "dealTeletextKeyCode number = " + number);
                    playerNotifyTeletextEvent(number + TT_EVENT_0);
                    break;
                }
                default:
                    break;
            }
        }

        protected final DtvkitGlueClient.SignalHandler mHandler = (signal, data) -> {
            {
                if (outService == null) {
                    return;
                }
                Log.i(TAG, "onSignal: " + signal + " : " + data);

                if (signal.equals("stateOfdtvkit")) {
                   int state = data.optInt("state");
                   if (state < 0) {
                        runOnMainThread(this::releaseTvHardware);
                   } else {
                        // time gap for dtvkit initialize
                        mMainHandle.postDelayed(this::acquireTvHardware, 2000);
                   }
               } else if (signal.equals("PlayerStatusChanged")) {
                    String state = "off";
                    String type = "dvblive";
                    String dvbUri = "";
                    try {
                        state = data.getString("state");
                        type = data.getString("type");
                        dvbUri = data.optString("uri");
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                    }
                    if (mTunedChannel != null) {
                        if ((!mTuneInfo.isDTv && !type.equals("ATV"))
                            || (mTuneInfo.isDTv && type.equals("ATV"))) {
                            Log.i(TAG, "Ignore signal: " + state);
                            return;
                        }
                    }
                    Log.i(TAG, "signal: " + state);
                    switch (state) {
                        case "playing":
                            /*
                             * type: "dvblive"         -> livetv streaming
                             * type: "dvbrecording"    -> dvr playback
                             * type: "dvbtimeshifting" -> timeshift streaming
                             */
                            if (mTuneInfo.update) {
                                mTuneInfo.update = false;
                                mTuneInfo.dvbUri = dvbUri;
                                sendMsgTsUpdate();
                            }
                            playerState = PlayerState.PLAYING;
                            if (type.equals("dvblive")) {
                                if (mTunedChannel == null) {
                                    return;
                                }
                                if (mTunedChannel.getServiceType().equals(TvContract.Channels.SERVICE_TYPE_AUDIO)) {
                                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY);
                                } else {
                                    notifyVideoAvailable();
                                }
                                if (mIsPip) {
                                    Log.d(TAG, "dvblive PIP only need video status");
                                    return;
                                }
                                try {
                                    if (!data.getJSONObject("content").getBoolean("is_av")) {
                                        timeshiftAvailable.setNo(false);
                                    }
                                } catch (JSONException e) {
                                    Log.d(TAG, "playing is_av JSONException = " + e.getMessage());
                                }
                                sendUpdateTrackMsg(PlayerState.PLAYING, false);
                                if (mTunedChannel.getServiceType().equals(TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)) {
                                    if (mHandlerThreadHandle != null) {
                                        mHandlerThreadHandle.removeMessages(MSG_CHECK_RESOLUTION);
                                        mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_CHECK_RESOLUTION, MSG_CHECK_RESOLUTION_PERIOD);//check resolution later
                                    }
                                }
                                if (mHandlerThreadHandle != null) {
                                    mHandlerThreadHandle.removeMessages(MSG_GET_SIGNAL_STRENGTH);
                                    mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_GET_SIGNAL_STRENGTH, MSG_GET_SIGNAL_STRENGTH_PERIOD);//check signal per 1s
                                    mHandlerThreadHandle.removeMessages(MSG_UPDATE_TRACK_INFO);
                                    mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_UPDATE_TRACK_INFO, MSG_UPDATE_TRACK_INFO_DELAY);
                                }
                            } else if (type.equals("dvbrecording")) {
                                setBlockMute(false);
                                startPosition = originalStartPosition = 0; // start position is always 0 when playing back recorded program
                                currentPosition = playerGetElapsedAndTruncated(data)[0];
                                Log.i(TAG, "dvbrecording currentPosition = " + currentPosition
                                    + "as date = " + ConvertSettingManager.convertLongToDate(startPosition));
                                runOnMainThread(() -> {
                                    notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
                                });
                                sendUpdateTrackMsg(PlayerState.PLAYING, true);
                            } else if (type.equals("dvbtimeshifting")) {
                                if (mTunedChannel == null) {
                                    return;
                                }
                                if (mTunedChannel.getServiceType().equals(TvContract.Channels.SERVICE_TYPE_AUDIO)) {
                                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY);
                                } else {
                                    notifyVideoAvailable();
                                }
                            } else {
                                if (type.equals("ATV")) {
                                    sendUpdateTrackMsg(PlayerState.PLAYING, false);
                                }
                                notifyVideoAvailable();
                            }
                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                Message msg = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                msg.arg1 = 1;
                                mMainHandle.sendMessageDelayed(msg, 100);
                            }
                            break;
                        case "audio_only_playing":
                            if (mTuneInfo.update) {
                                mTuneInfo.update = false;
                                mTuneInfo.dvbUri = dvbUri;
                                sendMsgTsUpdate();
                            }
                            playerState = PlayerState.PLAYING;
                            if (type.equals("dvblive")) {
                                if (mTunedChannel == null) {
                                    return;
                                }
                                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY);
                                sendUpdateTrackMsg(PlayerState.PLAYING, false);
                            }
                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                Message msg = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                msg.arg1 = 1;
                                mMainHandle.sendMessageDelayed(msg, 100);
                            }
                            break;
                        case "blocked":
                            String Rating = "";
                            int ratingAge = 4;
                            if (!type.equals("ATV")) {
                                try {
                                    ratingAge = data.getInt("rating");
                                    Rating = String.format("DVB_%d", ratingAge);
                                } catch (JSONException ignore) {
                                    Log.e(TAG, ignore.getMessage());
                                }
                            } else {
                                Log.d(TAG, "ATV chgannel blocked");
                                Rating = "DVB_4";
                            }

                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                Message msg = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                msg.arg1 = 0;
                                mMainHandle.sendMessageDelayed(msg, 0);
                            }
                            if (!mIsPip) {
                                playerSetSubtitlesOn(false);
                            }
                            if (!type.equals("dvbrecording")) {
                                playerState = PlayerState.BLOCKED;
                            } else {
                                setBlockMute(true);
                            }
                            dvrSubtitleFlag = 0;
                            ContentRatingSystem system = mContentRatingsManager
                                .getContentRatingSystem("com.android.tv" + "/" + mCurrentRatingSystem);
                            if (system != null) {
                                String ratedName = system.getRatedNameByAge(ratingAge);
                                if (TextUtils.isDigitsOnly(ratedName)) {
                                    ratedName = Rating;
                                }
                                notifyContentBlocked(TvContentRating.createRating(
                                    "com.android.tv", mCurrentRatingSystem, ratedName));
                            } else {
                                notifyContentBlocked(TvContentRating.createRating(
                                    "com.android.tv", "DVB", Rating));
                            }
                            break;
                        case "badsignal":
                            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL);
                            writeSysFs("/sys/class/video/disable_video", "2");
                            if (mIsPip) {
                                Log.d(TAG, "badsignal PIP only need video status");
                                return;
                            }
                            boolean isTeleOn = playerIsTeletextOn();
                            if (isTeleOn) {
                                playerSetTeletextOn(false, -1);
                                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, null);
                                Log.d(TAG, "close teletext when badsignal received");
                            }
                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_SET_TELETEXT_MIX_NORMAL);
                                mMainHandle.sendEmptyMessage(MSG_SET_TELETEXT_MIX_NORMAL);
                            }
                            if (type.equals("ATV")) {
                                sendUpdateTrackMsg(PlayerState.STOPPED, false);
                            }
                            break;
                        case "off":
                            if (mIsPip) {
                                return;
                            }
                            if (timeshiftRecorderState != RecorderState.STOPPED) {
                                stopTimeshiftRecordingInSession(false);
                                timeshiftRecorderState = RecorderState.STOPPED;
                                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
                            }
                            playerState = PlayerState.STOPPED;
                            dvrSubtitleFlag = 0;
                            if (recordedProgram != null) {
                                /*trigger the playback exit*/
                                currentPosition = recordedProgram.getRecordingDurationMillis();
                            }
                            notifySessionEvent(ConstantManager.EVENT_PLAY_STOP, null);
                            break;
                        case "starting":
                            boolean isAv = true;
                            try {
                                isAv = data.getJSONObject("content").getBoolean("is_av");
                            } catch (JSONException e) {
                                Log.d(TAG, "starting is_av JSONException = " + e.getMessage());
                                return;
                            }
                            playerState = PlayerState.STARTING;
                            if (mIsPip) {
                                Log.d(TAG, "starting no need to start mheg");
                                if (!isAv) {
                                    Log.d(TAG, "starting not av program and notify to pip view");
                                    Bundle pipTuningNext = new Bundle();
                                    pipTuningNext.putString(ConstantManager.KEY_PIP_ACTION, ConstantManager.VALUE_PIP_ACTION_TUNE_NEXT);
                                    notifySessionEvent(ConstantManager.EVENT_PIP_INFO, pipTuningNext);
                                    notifyChannelRetuned(null);
                                }
                                return;
                            }
                            if (mTunedChannel != null) {
                                if (TvContractUtils.getBooleanFromChannelInternalProviderData(
                                        mTunedChannel, Channel.KEY_IS_DATA_SERVICE, false)) {
                                    if (!isAv) {
                                        Log.d(TAG, "data_service isAv=false can not play well!");
                                        notifySessionEvent("signal_data_service", null);
                                    }
                                }
                            } else {
                                Log.d(TAG, "on signal starting null mTunedChannel");
                            }
                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                Message msg = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                msg.arg1 = 1;
                                mMainHandle.sendMessageDelayed(msg, 100);
                            }
                            sendUpdateTrackMsg(PlayerState.STARTING, false);
                            break;
                        case "scambled":
                            Log.i(TAG, "** scrambled **");
                            playerState = PlayerState.SCRAMBLED;
                            if (mMainHandle != null) {
                                mMainHandle.sendEmptyMessage(MSG_SHOW_BLOCKED_TEXT);
                            } else {
                                Log.d(TAG, "mMainHandle is null");
                            }
                            notifySessionEvent("signal_scrambled_service", null);
                            break;
                        case "not_running":
                            Log.i(TAG, "** not_running **");
                            notifySessionEvent("signal_invalid_service", null);
                            break;
                        default:
                            Log.i(TAG, "Unhandled state: " + state);
                            break;
                    }
                } else if (signal.equals("PlayerTimeshiftRecorderStatusChanged")) {
                    switch (playerGetTimeshiftRecorderState(data)) {
                        case "recording":
                            if (timeshiftRecorderState != RecorderState.RECORDING) {
                                timeshiftRecorderState = RecorderState.RECORDING;
                                startPosition = /*System.currentTimeMillis()*/PropSettingManager.getCurrentStreamTime(true);
                                originalStartPosition = PropSettingManager.getCurrentStreamTime(false);//keep the original time
                                runOnMainThread(() -> {
                                    Log.i(TAG, "recording originalStartPosition as date = "
                                        + ConvertSettingManager.convertLongToDate(originalStartPosition)
                                        + ", startPosition = "
                                        + ConvertSettingManager.convertLongToDate(startPosition));
                                    notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
                                });
                            }
                            break;
                        case "off":
                            timeshiftRecorderState = RecorderState.STOPPED;
                            tryStopTimeshifting(); // stop if "off" is not caused by user behaviour
                            startPosition = originalStartPosition = TvInputManager.TIME_SHIFT_INVALID_TIME;
                            notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
                            playerSetSubtitlesOn(false);
                            playerStopTeletext();
                            dvrSubtitleFlag = 0;
                            break;
                    }
                } else if (signal.equals("RecordingStatusChanged")) {
                    JSONArray activeRecordings = recordingGetActiveRecordings(data);

                    if (activeRecordings != null && activeRecordings.length() < numRecorders &&
                            timeshiftRecorderState == RecorderState.STOPPED) {
                        timeshiftAvailable.setYes();
                    }

                    if (checkActiveRecordings(activeRecordings,
                            new Predicate<JSONObject>() {
                                @Override
                                public boolean test(JSONObject recording) {
                                    if (TextUtils.equals(recording.optString("serviceuri", "null"), getChannelInternalDvbUri(mTunedChannel))
                                            && FeatureUtil.getFeatureSupportRecordAVServiceOnly()
                                            && !recording.optBoolean("is_av", true)) {
                                        return true;
                                    }
                                    return false;
                                }
                            })) {
                        Log.i(TAG, "timeshift stopped due to non-AV Service, feature rec_av_only enabled.");

                        Bundle event = new Bundle();
                        event.putString(ConstantManager.KEY_INFO, "not allowed to record a non-av service");
                        notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);

                        /*timeshiftAvailable will be disabled further in this tune*/
                        timeshiftAvailable.disable();

                        boolean return_to_livetv = timeshiftRecorderState != RecorderState.STOPPED;
                        if (return_to_livetv) {
                            Log.i(TAG, "stopping timeshift [return live:" + return_to_livetv + "]");
                            stopTimeshiftRecordingInSession(return_to_livetv);
                        }
                        timeshiftRecorderState = RecorderState.STOPPED;
                        mIsTimeshiftingPlayed = false;
                    }
                } else if (signal.equals("DvbUpdatedEventPeriods")) {
                    Log.i(TAG, "DvbUpdatedEventPeriods");
                    checkAndUpdateLcn();
                    int dvbSource = getCurrentDvbSource();
                    EpgSyncJobService.setChannelTypeFilter(TvContractUtils.dvbSourceToChannelTypeString(dvbSource));
                    Intent intent = new Intent(outService, DtvkitEpgSync.class);
                    intent.putExtra("inputId", mInputId);
                    intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_NEED_UPDATE_CHANNEL, false);
                    intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_CURRENT_PLAY_CHANNEL_ID, mTunedChannel != null ? mTunedChannel.getId() : -1);
                    try {
                        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FREQUENCY, data.getInt("frequency"));
                    } catch (Exception e) {

                    }
                    intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
                    startService(intent);
                } else if (signal.equals("DvbUpdatedEventNow")) {
                    Log.i(TAG, "DvbUpdatedEventNow");
                    checkAndUpdateLcn();
                    int dvbSource = getCurrentDvbSource();
                    EpgSyncJobService.setChannelTypeFilter(TvContractUtils.dvbSourceToChannelTypeString(dvbSource));
                    Intent intent = new Intent(outService, DtvkitEpgSync.class);
                    intent.putExtra("inputId", mInputId);
                    intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_NEED_UPDATE_CHANNEL, false);
                    intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_CURRENT_PLAY_CHANNEL_ID, mTunedChannel != null ? mTunedChannel.getId() : -1);
                    intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FREQUENCY, data.optInt("frequency"));
                    intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, signal);
                    startService(intent);
                } else if (signal.equals("CiTuneServiceInfo")) {
                    //tuned by dtvkit directly and then notify app
                    int s_id = 0, t_id = 0, onet_id = 0;
                    String tune_type = "";
                    int tune_quietly = 0;
                    try {
                        s_id = data.getInt("s_id");
                        t_id = data.getInt("t_id");
                        onet_id = data.getInt("onet_id");
                        tune_type = data.getString("tune_type");
                        tune_quietly = data.getInt("tune_quietly");
                    } catch (JSONException e) {
                        Log.e(TAG, "CiTuneServiceInfo JSONException = " + e.getMessage());
                    }
                    if (ConstantManager.VALUE_CI_PLUS_TUNE_TYPE_SERVICE.equals(tune_type)) {
                        Log.d(TAG, "CiTuneServiceInfo s_id " + s_id + " t_id " + t_id + " onet_id " + onet_id + " tune_type " + tune_type);
                        Channel foundChannelById = TvContractUtils.getChannelByNetworkTransportServiceId(outService.getContentResolver(), onet_id, t_id, s_id);
                        if (foundChannelById != null) {
                            mCiTuneServiceUri = getChannelInternalDvbUri(foundChannelById);
                            Log.i(TAG, "mCiTuneServiceUri : " + mCiTuneServiceUri);
                            removeMessageInInputThreadHandler(MSG_RESET_CI_TUNE_SERVICE_URI);
                            sendDelayedEmptyMessageToInputThreadHandler(MSG_RESET_CI_TUNE_SERVICE_URI, MSG_RESET_CI_TUNE_SERVICE_URI_DELAY);
                            Bundle channelBundle = new Bundle();
                            channelBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_HOST_CONTROL);
                            channelBundle.putString(ConstantManager.VALUE_CI_PLUS_TUNE_TYPE, ConstantManager.VALUE_CI_PLUS_TUNE_TYPE_SERVICE);
                            channelBundle.putLong(ConstantManager.VALUE_CI_PLUS_CHANNEL, foundChannelById.getId());
                            channelBundle.putInt(ConstantManager.VALUE_CI_PLUS_TUNE_QUIETLY, tune_quietly);
                            sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, channelBundle);
                        } else {
                            Log.d(TAG, "CiTuneServiceInfo hasn't found such channel");
                        }
                    } else if (ConstantManager.VALUE_CI_PLUS_TUNE_TYPE_TRANSPORT.equals(tune_type)) {
                        Log.d(TAG, "CiTuneServiceInfo s_id transport_tune");
                        Bundle channelBundle = new Bundle();
                        channelBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_HOST_CONTROL);
                        channelBundle.putString(ConstantManager.VALUE_CI_PLUS_TUNE_TYPE, ConstantManager.VALUE_CI_PLUS_TUNE_TYPE_TRANSPORT);
                        channelBundle.putLong(ConstantManager.VALUE_CI_PLUS_CHANNEL, -1);
                        channelBundle.putInt(ConstantManager.VALUE_CI_PLUS_TUNE_QUIETLY, 0);
                        sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, channelBundle);
                    }
                } else if (signal.equals("CIPLUS_PROFILE_SCAN_REQUIRED")) {
                    //tell app to search related module
                    try {
                        int module = data.getInt("value");
                        Log.d(TAG, "Ci operator request, need yes/no. module no. " + module);
                        if (PropSettingManager.getBoolean(PropSettingManager.CI_PROFILE_SEARCH_TEST, false)) {
                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_CI_UPDATE_PROFILE_CONFIRM);
                                mMainHandle.sendMessageDelayed(mMainHandle.obtainMessage(MSG_CI_UPDATE_PROFILE_CONFIRM, module, 0), MSG_SHOW_STREAM_CHANGE_DELAY);
                            }
                        } else {
                            Bundle searchBundle = new Bundle();
                            searchBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_REQUEST);
                            searchBundle.putInt(ConstantManager.VALUE_CI_PLUS_SEARCH_MODULE, module);
                            sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, searchBundle);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "CiOpSearchRequest Exception = " + e.getMessage());
                    }
                } else if (signal.equals("CIPLUS_PROFILE_SCAN_PROGRESS")) {
                    // only a status and skip it for the moment
                    try {
                        int status = data.getInt("value");
                        if (status == 3) {
                            Log.d(TAG, "Ci operator search has finished");
                            Bundle finishBundle = new Bundle();
                            finishBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_SEARCH_FINISHED);
                            finishBundle.putBoolean(ConstantManager.VALUE_CI_PLUS_SEARCH_RESULT_IS_OPERATOR_PROFILE_SUPPORTED, isOperatorProfileSupported());
                            sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, finishBundle);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "CiOpSearchFinished Exception = " + e.getMessage());
                    }
                } else if (signal.equals("CIPLUS_HAS_TRICK_CONTROL")) {
                    Bundle playbackBundle = new Bundle();
                    playbackBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_TRICK_CONTROL);
                    sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, playbackBundle);
                } else if (signal.equals("ignore_input")) {
                    //tell app to disable key event
                    try {
                        // String event = data.getString("data");
                        Log.d(TAG, "System is doing " + " can not handle user input");
                        Bundle inputBundle = new Bundle();
                        inputBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_IGNORE_INPUT);
                        // inputBundle.putString(ConstantManager.VALUE_CI_PLUS_EVENT_DETAIL, event);
                        sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, inputBundle);
                        mCiAuthenticatedStatus = true;
                    } catch (Exception e) {
                        Log.d(TAG, "IgnoreUserInput Exception = " + e.getMessage());
                    }
                } else if (signal.equals("receive_input")) {
                    //tell app to key event can work normally
                    try {
                        Log.d(TAG, "Work complete, input can received now");
                        Bundle inputBundle = new Bundle();
                        inputBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_RECEIVE_INPUT);
                        sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, inputBundle);
                        mCiAuthenticatedStatus = false;
                    } catch (Exception e) {
                        Log.d(TAG, "IgnoreUserInput Exception = " + e.getMessage());
                    }
                } else if (signal.equals("PvrPlaybackStatus")
                        || signal.equals("playback_camid_mismatch")
                        || signal.equals("playback_license_timeout")
                        || signal.equals("playback_license_received_after_timeout")) {
                    //Ciplus notify that PVR can't play
                    Bundle playbackBundle = new Bundle();
                    playbackBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_PVR_PLAYBACK_STATUS);
                    playbackBundle.putString("playback", data.optString("playback", ""));
                    playbackBundle.putInt("errcode", data.optInt("errcode", 0));
                    sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, playbackBundle);
                } else if (signal.equals("content_protection")
                        || signal.equals("no_content_protection")) {
                    Bundle playbackBundle = new Bundle();
                    playbackBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_ECI_CONTENT_PROTECTION);
                    playbackBundle.putBoolean("is_content_protection", signal.equals("content_protection"));
                    sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, playbackBundle);
                } else if (signal.equals("trick_limit")) {
                    //Ciplus notify that can't FF/FB when PVR play
                    Bundle playbackBundle = new Bundle();
                    playbackBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_TRICK_LIMIT);
                    playbackBundle.putString("trick_limit", data.optString("trick_limit", ""));
                    playbackBundle.putInt("trick_limit", data.optInt("errcode", 0));
                    sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, playbackBundle);
                } else if (signal.equals("pvr start failed")) {
                    //Ciplus notify that can't PVR
                    Bundle playbackBundle = new Bundle();
                    playbackBundle.putString(ConstantManager.CI_PLUS_COMMAND, ConstantManager.VALUE_CI_PLUS_COMMAND_DO_PVR_LIMITED);
                    playbackBundle.putString("do_pvr_limited", data.optString("do_pvr_limited", ""));
                    playbackBundle.putInt("do_pvr_limited", data.optInt("errcode", 0));
                    sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, playbackBundle);
                } else if (signal.equals("DvbNetworkChange") || signal.equals("DvbUpdatedService")) {
                    Log.i(TAG, "DvbNetworkChange or DvbUpdatedService");
                    //this event has been handled in dtvkit, tis should ignore it
                } else if (signal.equals("DvbUpdatedChannelData")) {
                    Log.i(TAG, "DvbUpdatedChannelData");
                    //check trackInfo update
                    sendUpdateTrackMsg(playerState, false);
                } else if (signal.equals("MhegAppStarted")) {
                    Log.i(TAG, "MhegAppStarted");
                    mIsMhepAppStarted = true;
                    if (mTunedChannel != null) {
                        if (mTunedChannel.getServiceType().equals(TvContract.Channels.SERVICE_TYPE_AUDIO)) {
                            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY);
                        } else {
                            notifyVideoAvailable();
                        }
                    } else {
                        Log.d(TAG, "on signal MhegAppStarted null mTunedChannel");
                    }
                } else if (signal.equals("hbbNotifyWindowSizeChanged")) {
                    try {
                        int x = data.getInt("x");
                        int y = data.getInt("y");
                        int width = data.getInt("width");
                        int height = data.getInt("height");
                        Log.d(TAG, "hbbNotifyWindowSizeChanged x= " + x + ", y = " + y + ",  width = " + width + ", height = " + height);
                        runOnMainThread(() -> {
                            if (mView != null) {
                                mView.setSize(x, y, x+width, y+height);
                            }
                        });
                    } catch (JSONException e) {
                      Log.e(TAG, e.getMessage());
                   }
                }
                else if (signal.equals("AppVideoPosition"))
                {
                   Log.i(TAG, "AppVideoPosition");
                   if (mHandlerThreadHandle != null) {
                       mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_CHECK_RESOLUTION, MSG_CHECK_RESOLUTION_PERIOD);
                   }
                   /*
                   if (getIsFixedTunnel()) {
                       SystemControlManager SysContManager = SystemControlManager.getInstance();
                       int UiSettingMode = SysContManager.GetDisplayMode(SystemControlManager.SourceInput.DTV.toInt());
                       int left,top,right,bottom;
                       left = 0;
                       top = 0;
                       right = 1920;
                       bottom = 1080;
                       int voff0, hoff0, voff1, hoff1;
                       voff0 = 0;
                       hoff0 = 0;
                       voff1 = 0;
                       hoff1 = 0;
                       try {
                          left = data.getInt("left");
                          top = data.getInt("top");
                          right = data.getInt("right");
                          bottom = data.getInt("bottom");
                          voff0 = data.getInt("crop-voff0");
                          hoff0 = data.getInt("crop-hoff0");
                          voff1 = data.getInt("crop-voff1");
                          hoff1 = data.getInt("crop-hoff1");
                       } catch (JSONException e) {
                          Log.e(TAG, e.getMessage());
                       }
                       //add for pip setting
                       if (!mIsPip) {
                           tvin_cutwin_t pq_overscan = SysContManager.GetOverscanParams(SystemControlManager.Display_Mode.DISPLAY_MODE_169);
                           String crop = new StringBuilder()
                               .append(voff0 + pq_overscan.vs).append(" ")
                               .append(hoff0 + pq_overscan.hs).append(" ")
                               .append(voff1 + pq_overscan.ve).append(" ")
                               .append(hoff1 + pq_overscan.he).toString();

                               if (UiSettingMode != 6) {//not afd
                                   Log.i(TAG, "Not AFD mode!");
                               } else {
                                   if (getIsFixedTunnel()) {
                                       Log.i(TAG, "playerSetVideoCrop when FixedTunnel ");
                                       playerSetVideoCrop(INDEX_FOR_MAIN, voff0 + pq_overscan.vs, hoff0 + pq_overscan.hs, voff1 + pq_overscan.ve, hoff1 + pq_overscan.he);
                                   } else {
                                       Log.i(TAG, "AppVideoPosition crop:("+crop+")");
                                       SysContManager.writeSysFs("/sys/class/video/crop", crop);
                                   }
                               }
                               Log.d(TAG, "AppVideoPosition layoutSurface("+left+","+top+","+right+","+bottom+")(LTRB)");
                               layoutSurface(left,top,right,bottom);
                       }
                   }
                   */
                }
                else if (signal.equals("ServiceRetuned")) {
                   String dvbUri = "";
                   Channel channel;
                   Uri retuneUri;
                   boolean found = false;
                   int i;
                   long id=0;
                   try {
                      dvbUri= data.getString("uri");
                   } catch (JSONException ignore) {
                   }
                   Log.i(TAG, "ServiceRetuned " + dvbUri);
                   //find the channel URI that matches the dvb uri of the retune
                   channel = TvContractUtils.getChannelWithDvbUri(mContentResolver, mInputId, dvbUri);
                   if (channel != null) {
                       found = true;
                       id = channel.getId();
                   }
                   if (found)
                   {
                      //rebuild the Channel URI from the current channel + the new ID
                      retuneUri = Uri.parse("content://android.media.tv/channel");
                      retuneUri = ContentUris.withAppendedId(retuneUri,id);
                      Log.i(TAG, "retune to " + retuneUri);

                      if (mHandlerThreadHandle != null) {
                          mHandlerThreadHandle.obtainMessage(MSG_ON_TUNE, 1/*mhegTune*/, 0, retuneUri).sendToTarget();
                      }
                   }
                   //else
                   //{
                      //if we couldn't find the channel uri for some reason,
                      // try restarting MHEG on the new service anyway
                      //mhegSuspend();
                      //mhegStartService(dvbUri);
                      //dealt in message queue
                      //if (mHandlerThreadHandle != null) {
                      //    mHandlerThreadHandle.obtainMessage(MSG_START_MHEG5, 1/*mhegSupend*/, 0, dvbUri).sendToTarget();
                      //}
                   //}
                }
                else if (signal.equals("RecordingDiskFull")) {
                    if (timeshiftRecorderState != RecorderState.STOPPED) {
                        /*free disk space exceeds the property's setting*/
                        Bundle event = new Bundle();
                        event.putString(ConstantManager.KEY_INFO, "Stop timeshift due to insufficient storage");
                        notifySessionEvent(ConstantManager.EVENT_RESOURCE_BUSY, event);
                    }
                } else if (signal.equals("RecordingsDiskRm")) {
                    tryStopTimeshifting();
                } else if (signal.equals("tt_mix_separate")) {
                    mMainHandle.sendEmptyMessage(MSG_SET_TELETEXT_MIX_SEPARATE);
                } else if (signal.equals("tt_mix_normal")) {
                    mMainHandle.sendEmptyMessage(MSG_SET_TELETEXT_MIX_NORMAL);
                } else if (signal.equals("SubtitleOpened")) {
                    mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_EVENT_SUBTITLE_OPENED, 2000);
                } else if (signal.equals("cas")) {
                    processCasEvents(data);
                } else if (signal.equals("FVP_SERVICE_NID_DONE")) {
                    if (!mIsPip) {
                        if (null == mProviderSync) {
                            mProviderSync = new ProviderSync();
                        }
                        mProviderSync.run(new FvpChannelEnhancedInfoSync(outService, FvpChannelEnhancedInfoSync.FVP_SYNC_CHANNEL_ENCHANCE_INFO));
                    }
                } else if (signal.equals("EWSNotify")) {
                    Bundle EWSEvent = new Bundle();
                    try {
                        EWSEvent.putInt("disaster_code",data.getInt("disaster_code"));
                        EWSEvent.putInt("authority",data.getInt("authority"));
                        EWSEvent.putInt("location_type_code",data.getInt("location_type_code"));
                        EWSEvent.putString("disaster_code_str",data.getString("disaster_code_str"));
                        EWSEvent.putString("location_code",data.getString("location_code"));
                        EWSEvent.putString("disaster_position",data.getString("disaster_position"));
                        EWSEvent.putString("disaster_date",data.getString("disaster_date"));
                        EWSEvent.putString("disaster_characteristic",data.getString("disaster_characteristic"));
                        EWSEvent.putString("information_message",data.getString("information_message"));
                        notifySessionEvent(ConstantManager.ACTION_EWS_NOTIFY, EWSEvent);
                    } catch (Exception e) {
                        Log.i(TAG,"EWSNotify error");
                    }
                } else if (signal.equals("EWBSNotify")) {
                    if (DataProviderManager.getBooleanValue(outService, ConstantManager.EWBS_SWITCH, false)) {
                        Bundle EWBSEvent = new Bundle();
                        try {
                            int start_end_flag = data.getInt("start_end_flag");
                            if (start_end_flag == 1) {
                                mMainHandle.post(() -> {
                                    mView.showEWBSAlarmView(data);
                                });

                                // Is there an alarm built-in sound, if there is a need to play an alarm MP3 file
                                // If the value is not 0, the mute channel sound is required, and the alarm sound is played
                                int has_builtin_sound = data.getInt("has_builtin_sound");

                                // The file ID of the current alarm sound (temporary code stream case does not support)
                                int builtin_sound_id = data.getInt("builtin_sound_id");

                                Message msg = mMainHandle.obtainMessage(MSG_BLOCK_MUTE_OR_UNMUTE, has_builtin_sound, 0);
                                mHandlerThreadHandle.sendMessage(msg);
                                EWBSEvent.putBoolean("has_builtin_sound", has_builtin_sound != 0);
                                EWBSEvent.putInt("builtin_sound_id", builtin_sound_id);
                            } else {
                                mMainHandle.post(() -> {
                                    mView.hideEWBSAlarmView();
                                });
                                Message msg = mMainHandle.obtainMessage(MSG_BLOCK_MUTE_OR_UNMUTE, 0, 0);
                                mHandlerThreadHandle.sendMessage(msg);
                            }
                            EWBSEvent.putBoolean("start_end_flag", start_end_flag == 1);
                            notifySessionEvent(ConstantManager.ACTION_EWBS_NOTIFY, EWBSEvent);
                        } catch (JSONException e) {
                            Log.i(TAG, "EWBSNotify error ");
                        }
                    }
                } else if (signal.equals("EWSClose")) {
                    notifySessionEvent(ConstantManager.ACTION_EWS_CLOSE, null);
                } else if (signal.equals("hbbNotifyAuthUrlUpdated")) {
                    if (!mIsPip) {
                        sendAuthUrlUpdateBroadcast(data);
                    } else {
                        Log.d(TAG, "Pip Session not handle hbbNotifyAuthUrlUpdated message ");
                    }
                } else if (signal.equals("AudioTrackSelected")) {
                    // after track changed, should update sound mode again
                    notifySessionEvent(ConstantManager.ACTION_AUDIO_TRACK_SELECTED, null);
                } else if (signal.equals("TkgsStartTuneUpdate")) {
                    notifySessionEvent(ConstantManager.ACTION_TKGS_START_TUNE_UPDATE, null);
                    mMainHandle.post(()->showToast(R.string.string_tune_update_tip));
                } else if (signal.equals("TkgsFinishTuneUpdate")) {
                    try {
                        JSONArray jsonArray = data.getJSONArray("result");
                        String msg = jsonArray.getJSONObject(0).getString("msg");
                        mMainHandle.post(()-> showTKGSUserMsgDialog(getApplicationContext(), msg));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendEmptyMessageToInputThreadHandler(MSG_UPDATE_DTVKIT_DATABASE);
                } else if (signal.equals("FVP_LINER_IP_SERVICE_DONE")) {
                    //TBD: to sync IP Channel URI
                     if (!mIsPip) {
                        if (null == mProviderSync) {
                            mProviderSync = new ProviderSync();
                        }
                        mProviderSync.run(new FvpChannelEnhancedInfoSync(outService, FvpChannelEnhancedInfoSync.FVP_SYNC_IP_CHANNEL_INFO));
                    }
                } else if (signal.equals("TVStatusChanged")) {
                    boolean tvScan = false;
                    String status = "";
                    int freq = 0;
                    try {
                        tvScan = TextUtils.equals(data.getString("name"), "tv_scan");
                        status = data.getString("status");
                        freq = data.optInt("freq");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (tvScan) {
                        if (TextUtils.equals("no_program_searched", status)) {
                            runOnMainThread(() -> {
                                exitNumberSearch();
                                if (mTunedChannel != null) {
                                    onTune(TvContract.buildChannelUri(mTunedChannel.getId()), null);
                                }
                            });
                        } else if (TextUtils.equals("dtv_air_searched", status)
                                || TextUtils.equals("atv_air_searched", status)
                                || TextUtils.equals("atv_cable_searched", status)) {
                            runOnMainThread(this::exitNumberSearch);
                            if (freq != 0) {
                                mTuneInfo.update = true;
                                mTuneInfo.frequency = freq;
                                mTuneInfo.isDTv = status.contains("dtv");
                                runOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mMainHardware != null) {
                                            Log.i(TAG, "update mMainStreamConfig isDTv:" + mTuneInfo.isDTv);
                                            mMainHardware.setSurface(mSurface, mTuneInfo.isDTv ? mMainStreamConfig[0] : mMainStreamConfig[1]);
                                            mSystemControlManager.SetCurrentSourceInfo(
                                                    mTuneInfo.isDTv ? SystemControlManager.SourceInput.DTV : SystemControlManager.SourceInput.TV, 0, 0);
                                        }
                                    }
                                });
                            } else {
                                Log.e(TAG, "wrong params");
                                return;
                            }
                            Bundle parameters = new Bundle();
                            Intent intent = new Intent(outService, DtvkitEpgSync.class);
                            intent.putExtra("inputId", mInputId);
                            intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, signal);
                            intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
                            startService(intent);
                            sendEmptyMessageToInputThreadHandler(MSG_START_MONITOR_SYNCING);
                        }
                    }
                }
            }
        };

        protected static final int MSG_ON_TUNE = 1;
        protected static final int MSG_CHECK_RESOLUTION = 2;
        protected static final int MSG_CHECK_PARENTAL_CONTROL = 3;
        protected static final int MSG_BLOCK_MUTE_OR_UNMUTE = 4;
        protected static final int MSG_SET_RECTANGLE = 5;
        protected static final int MSG_DO_RELEASE = 6;
        protected static final int MSG_RELEASE_WORK_THREAD = 7;
        protected static final int MSG_GET_SIGNAL_STRENGTH = 8;
        protected static final int MSG_UPDATE_TRACK_INFO = 9; // for HbbTv track update only
        protected static final int MSG_ENABLE_VIDEO = 10;
        protected static final int MSG_SET_UNBLOCK = 11;
        protected static final int MSG_CHECK_REC_PATH = 12;
        protected static final int MSG_SEND_DISPLAY_STREAM_CHANGE_DIALOG = 13;
        protected static final int MSG_TRY_START_TIMESHIFT = 14;
        protected static final int MSG_UPDATE_TRACKS_AND_SELECT = 15;
        protected static final int MSG_SELECT_TRACK = 16;
        // protected static final int MSG_DO_RELEASE_SPECIFIED_SESSION = 17;
        protected static final int MSG_TRY_STOP_TIMESHIFT = 18;
        protected static final int MSG_TS_UPDATE = 19;
        protected static final int MSG_RESET_CI_TUNE_SERVICE_URI = 20;
        protected static final int MSG_SET_STREAM_VOLUME = 21;
        protected static final int MSG_EVENT_SUBTITLE_OPENED = 22;
        protected static final int MSG_NUMBER_SEARCH = 23;
        protected static final int MSG_TUNE_ATV_SNOW = 24;

        //timeshift
        protected static final int MSG_TIMESHIFT_PLAY = 30;
        protected static final int MSG_TIMESHIFT_RESUME = 31;
        protected static final int MSG_TIMESHIFT_PAUSE = 32;
        protected static final int MSG_TIMESHIFT_SEEK = 33;
        protected static final int MSG_CHECK_REC_PATH_DIRECTLY = 34;
        protected static final int MSG_SCHEDULE_TIMESHIFT_RECORDING_TASK = 35;
        protected static final int MSG_TIMESHIFT_PLAY_SET_PLAYBACK_PARAMS = 36;

        //mheg5
        //protected static final int MSG_START_MHEG5 = 40;
        //protected static final int MSG_STOP_MHEG5 = 41;

        //ci plus update
        protected static final int MSG_CI_UPDATE_PROFILE_OVER = 50;
        protected static final int MSG_CI_UPDATE_PROFILE_CONFIRM = 51;
        protected static final int MSG_CI_UPDATE_PROFILE_OVER_DELAY = 3000;//3S

        protected static final int MSG_CHECK_RESOLUTION_PERIOD = 1000;//MS
        protected static final int MSG_UPDATE_TRACK_INFO_DELAY = 2000;//MS
        protected static final int MSG_CHECK_PARENTAL_CONTROL_PERIOD = 2000;//MS
        protected static final int MSG_RESET_CI_TUNE_SERVICE_URI_DELAY = 2000;//MS
        protected static final int MSG_BLOCK_MUTE_OR_UNMUTE_PERIOD = 100;//MS
        protected static final int MSG_GET_SIGNAL_STRENGTH_PERIOD = 1000;//MS
        protected static final int MSG_CHECK_REC_PATH_PERIOD = 1000;//MS
        protected static final int MSG_SHOW_STREAM_CHANGE_DELAY = 500;//MS

        protected static final int MSG_MAIN_HANDLE_DESTROY_OVERLAY = 1;
        protected static final int MSG_SHOW_BLOCKED_TEXT = 2;
        protected static final int MSG_HIDE_BLOCKED_TEXT = 3;
        protected static final int MSG_DISPLAY_STREAM_CHANGE_DIALOG = 4;
        protected static final int MSG_SUBTITLE_SHOW_CLOSED_CAPTION = 5;
        protected static final int MSG_SET_TELETEXT_MIX_NORMAL = 6;
        protected static final int MSG_SET_TELETEXT_MIX_TRANSPARENT = 7;
        protected static final int MSG_SET_TELETEXT_MIX_SEPARATE = 8;
        protected static final int MSG_FILL_SURFACE = 9;
        /*protected static final int MSG_EVENT_SUBTITLE_OPENED = 10;*/
        protected static final int MSG_EVENT_SHOW_HIDE_OVERLAY = 11;
        protected static final int MSG_SET_SURFACE = 12;

        protected static final int MSG_SHOW_TUNING_IMAGE = 20;
        protected static final int MSG_HIDE_TUNING_IMAGE = 21;

        protected static final int MSG_CAS_OSM_PREPARE_WINDOW = 30;
        protected static final int MSG_CAS_OSM_WINDOW_SHOW = 31;
        protected static final int MSG_CAS_OSM_WINDOW_CLEAR = 32;

        private void initWorkThread() {
            Log.d(TAG, "initWorkThread");
            mLivingHandlerThread = new HandlerThread(
                    "LivingThread-" + mCurrentSessionIndex);
            mLivingHandlerThread.start();
            mHandlerThreadHandle = new Handler(mLivingHandlerThread.getLooper(), (msg) -> {
                Log.d(TAG, "mHandlerThreadHandle [[[:" + msg.what);
                if (mLivingHandlerThread == null || !mLivingHandlerThread.isAlive()) {
                    Log.w(TAG, "LivingThread-" + mCurrentSessionIndex + " quit");
                    return true;
                }
                switch (msg.what) {
                    case MSG_ON_TUNE:
                        Uri channelUri = (Uri) msg.obj;
                        if (channelUri != null) {
                            onTuneByHandlerThreadHandle(channelUri, msg.arg1 != 0);
                        }
                        break;
                    case MSG_TUNE_ATV_SNOW:
                        onTuneByHandlerThreadHandle(null, false);
                        break;
                    case MSG_CHECK_RESOLUTION:
                        if (!checkRealTimeResolution()) {
                            if (mHandlerThreadHandle != null)
                                mHandlerThreadHandle.sendEmptyMessageDelayed(
                                        MSG_CHECK_RESOLUTION, MSG_CHECK_RESOLUTION_PERIOD);
                        }
                        break;
                    case MSG_CHECK_PARENTAL_CONTROL:
                        /* dtvkit handle this*/
                        break;
                    case MSG_BLOCK_MUTE_OR_UNMUTE:
                        boolean mute = msg.arg1 != 0;
                        setBlockMute(mute);
                        break;
                    case MSG_SET_STREAM_VOLUME:
                        if (!mTuneInfo.isDTv) {
                            if (msg.arg1 == 0) {
                                mAudioSystemCmdManager.closeTvAudio();
                            } else {
                                mAudioSystemCmdManager.openTvAudio(0);
                            }
                        } else {
                            if (msg.arg1 == 1) {
                                playerSetAdParams(true, 0);
                            }
                            playerSetMute(msg.arg1 == 0, msg.arg2);
                        }
                        break;
                    case MSG_EVENT_SUBTITLE_OPENED:
                        initSubtitleOrTeletextIfNeed();
                        break;
                    case MSG_SET_RECTANGLE: {
                        //dtvkit use 1920x1080 as virtual display resolution
                        //Rectangle to dtvkit need calculated base of 1920x1080
                        if (msg.arg1 == 1) {
                            playerSetRectangle(0, 1080/4, 1920/2, 1080/2);
                        } else {
                            playerSetRectangle(0, 0, 1920, 1080);
                        }
                        break;
                    }
                    case MSG_DO_RELEASE:
                        doRelease();
                        break;
                    case MSG_GET_SIGNAL_STRENGTH:
                        sendCurrentSignalInformation();
                        if (mHandlerThreadHandle != null) {
                            mHandlerThreadHandle.removeMessages(MSG_GET_SIGNAL_STRENGTH);
                            mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_GET_SIGNAL_STRENGTH,
                                    MSG_GET_SIGNAL_STRENGTH_PERIOD);//check signal per 1s
                        }
                        break;
                    case MSG_UPDATE_TRACK_INFO:
                        runOnMainThread(() -> {
                            if (mHbbTvManager != null) {
                                mResourceOwnedByBr = mHbbTvManager.checkIsBroadcastOwnResource();
                            }
                        });
                        if (!mResourceOwnedByBr) {
                            mHbbTvManager.getBroadbandTracksAndNotify();
                        }
                        if (mHandlerThreadHandle != null) {
                            mHandlerThreadHandle.removeMessages(MSG_UPDATE_TRACK_INFO);
                            mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_UPDATE_TRACK_INFO,
                                    MSG_UPDATE_TRACK_INFO_DELAY);
                        }
                        break;
                    case MSG_ENABLE_VIDEO:
                        //use dtvkit video mute
                        //writeSysFs("/sys/class/video/video_global_output", msg.arg1 == 1 ? "1" : "0");
                        break;
                    case MSG_SET_UNBLOCK:
                        if (msg.obj != null && msg.obj instanceof TvContentRating) {
                            setUnBlock((TvContentRating) msg.obj);
                        }
                        if (mMainHandle != null) {
                            mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                            Message message = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                            message.arg1 = 1;
                            mMainHandle.sendMessageDelayed(message, 0);
                        }
                        break;
                    case MSG_CHECK_REC_PATH:
                        if (resetRecordingPath() || (msg.arg2 == 1)/*start*/) {
                            /* path changed */
                            tryStartTimeshifting();
                        }
                        monitorTimeshiftRecordingPathAndTryRestart(true, false, false);
                        break;
                    case MSG_CHECK_REC_PATH_DIRECTLY:
                        //avoid message removed in some case
                        resetRecordingPath();
                        tryStartTimeshifting();
                        monitorTimeshiftRecordingPathAndTryRestart(true, false, false);
                        break;
                    case MSG_SEND_DISPLAY_STREAM_CHANGE_DIALOG:
                        break;
                    case MSG_TRY_START_TIMESHIFT:
                        resetRecordingPath();
                        tryStartTimeshifting();
                        break;
                    case MSG_TRY_STOP_TIMESHIFT:
                        tryStopTimeshifting();
                        break;
                    case MSG_UPDATE_TRACKS_AND_SELECT:
                        updateTrackAndSelect(msg.arg1, msg.arg2 == 1);
                        break;
                    case MSG_SELECT_TRACK:
                        doSelectTrack(msg.arg1, (String) msg.obj);
                        break;
                    case MSG_SCHEDULE_TIMESHIFT_RECORDING_TASK:
                        timeshiftRecordingTask();
                        break;
                    case MSG_TIMESHIFT_PLAY:
                        setTimeshiftPlay((Uri) msg.obj);
                        break;
                    case MSG_TIMESHIFT_RESUME:
                        setTimeshiftResume();
                        break;
                    case MSG_TIMESHIFT_PAUSE:
                        setTimeshiftPause();
                        break;
                    case MSG_TIMESHIFT_SEEK:
                        long timeMs = (long) msg.obj;
                        setTimeshiftSeek(timeMs);
                        break;
                    case MSG_TIMESHIFT_PLAY_SET_PLAYBACK_PARAMS:
                        PlaybackParams params = (PlaybackParams) msg.obj;
                        setTimeShiftSetPlaybackParams(params);
                        break;
                    /*
                    case MSG_START_MHEG5:
                        String dvbUri = (String) msg.obj;
                        boolean mhegSuspendStatus = msg.arg1 == 1;
                        startMheg(dvbUri, mhegSuspendStatus);
                        break;
                    case MSG_STOP_MHEG5:
                        mhegStop();
                        break;
                    */
                    case MSG_CI_UPDATE_PROFILE_OVER:
                        Bundle updateEvent = new Bundle();
                        updateEvent.putString(ConstantManager.CI_PLUS_COMMAND,
                                ConstantManager.VALUE_CI_PLUS_COMMAND_CHANNEL_UPDATED);
                        sendBundleToAppByTif(ConstantManager.ACTION_CI_PLUS_INFO, updateEvent);
                        break;
                    case MSG_TS_UPDATE:
                        String newUri = mTuneInfo.dvbUri;
                        Uri retuneUri;
                        long id = 0;
                        Channel channel = null;
                        List<Channel> channelList = TvContractUtils.getChannels(mContentResolver, mInputId);
                        if (!mTuneInfo.isDTv && mTuneInfo.frequency > 0) {
                            // atv case
                            int freq = mTuneInfo.frequency;
                            Log.d(TAG, "MSG_TS_UPDATE, freq:" + freq);
                            int antennaType = 0;
                            String dtvType = mDataManager.getStringParameters(DataManager.KEY_ISDB_ANTENNA_TYPE);
                            if (TextUtils.equals(TvContract.Channels.TYPE_ATSC_C, dtvType)) {
                                antennaType = 1;
                            }
                            for (Channel nextChannel : channelList) {
                                if (Channel.isATV(nextChannel) && nextChannel.getAntennaType() == antennaType
                                    && nextChannel.getFrequency() == freq) {
                                    channel = nextChannel;
                                    break;
                                }
                            }
                        } else {
                            Log.d(TAG, "MSG_TS_UPDATE, uri:" + newUri);
                            for (Channel nextChannel : channelList) {
                                InternalProviderData data = nextChannel.getInternalProviderData();
                                if (data == null) {
                                    continue;
                                }
                                String dvb = null;
                                try {
                                    dvb = (String) data.get("dvbUri");
                                } catch (InternalProviderData.ParseException e) {
                                    e.printStackTrace();
                                }
                                if (newUri.equals(dvb)) {
                                    channel = nextChannel;
                                    break;
                                }
                            }
                        }
                        boolean found = false;
                        if (channel != null) {
                            found = true;
                            id = channel.getId();
                            Log.d(TAG, "id = "+ id);
                        }
                        if (found) {
                            retuneUri = Uri.parse("content://android.media.tv/channel");
                            retuneUri = ContentUris.withAppendedId(retuneUri,id);
                            Log.i(TAG, "retune to " + retuneUri);
                            notifyChannelRetuned(retuneUri);
                            //sync
                            if (mHbbTvManager != null) {
                                mHbbTvManager.setTuneChannelUri(retuneUri);
                            }
                            mTunedChannel = channel;
                            if (playerState == PlayerState.PLAYING) {
                                Log.i(TAG, "re-notifyVideoAvailable");
                                if (mTunedChannel.getServiceType().equals(TvContract.Channels.SERVICE_TYPE_AUDIO)) {
                                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY);
                                } else {
                                    notifyVideoAvailable();
                                }
                            }
                        }
                        break;
                    case MSG_RESET_CI_TUNE_SERVICE_URI:
                        Log.i(TAG,"reset mCiTuneServiceUri");
                        mCiTuneServiceUri = "";
                        break;
                    case MSG_NUMBER_SEARCH:
                        onFinish(false, false);
                        // update liveTv mute status.
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
                        startNumberSearch(msg.arg1);
                        break;
                    default:
                        Log.d(TAG, "mHandlerThreadHandle initWorkThread default");
                        break;
                }
                Log.d(TAG, "mHandlerThreadHandle " + msg.what + " done]]]");
                return true;
            });
            mMainHandle = new Handler(Looper.getMainLooper(), new MainCallback(this));
        }

        private void startNumberSearch(int channelNumber) {
            String dtvType = mDataManager.getStringParameters(DataManager.KEY_ISDB_ANTENNA_TYPE);
            String type = "AIR";
            if (TextUtils.equals(TvContract.Channels.TYPE_ATSC_C, dtvType)) {
                type = "CABLE";
            }
            String command = "Tv.startManualSearchAndPlayByChannelId";
            JSONArray args = new JSONArray();
            args.put(type);
            args.put(String.valueOf(channelNumber));
            try {
                DtvkitGlueClient.getInstance().request(command, args);
                Log.d(TAG, "command = " + command + ", args = " + args);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), R.string.number_search_error, Toast.LENGTH_SHORT).show();
                exitNumberSearch();
            }
        }

        @Override
        public void notifyChannelRetuned(Uri channelUri) {
            super.notifyChannelRetuned(channelUri);
            mTuneInfo.update = false;
            Log.d(TAG, "notifyChannelRetuned:" + channelUri);
        }

        //use osd to hide video instead
        /*private void syncParentControlSetting() {
            int current_age, setting_min_age;

            current_age = getParentalControlAge();
            setting_min_age = getCurrentMinAgeByBlockedRatings();
            if (setting_min_age == 0xFF && getParentalControlOn()) {
                setParentalControlOn(false);
                notifyContentAllowed();
            }else if (setting_min_age >= 4 && setting_min_age <= 18 && current_age != setting_min_age) {
                setParentalControlAge(setting_min_age);
                if (current_age < setting_min_age) {
                    Log.e(TAG, "rating changed, oldAge < newAge : [" +current_age+ " < " +setting_min_age+ "], so will allow");
                    notifyContentAllowed();
                }
            }
        }*/

        private int getContentRatingsOfCurrentProgram() {
            int age = 0;
            String pc_rating;
            String rating_system;
            long currentStreamTime = 0;
            TvContentRating[] ratings;

            //get current position as timeshift may seek to related position
            if (mIsTimeshiftingPlayed) {
                currentStreamTime = onTimeShiftGetCurrentPosition();
            } else {
                currentStreamTime = PropSettingManager.getCurrentStreamTime(true);
            }
            Log.d(TAG, "currentStreamTime:(" + currentStreamTime + ")");
            if (currentStreamTime == 0) {
                return 0;
            }

            if (mTunedChannel == null) {
                Log.w(TAG, "getContentRatingsOfCurrentProgram null mTunedChannel");
                return age;
            }
            Program program = TvContractUtils.getCurrentProgramExt(
                    outService.getContentResolver(),
                    TvContract.buildChannelUri(mTunedChannel.getId()), currentStreamTime);

            ratings = program == null ? null : program.getContentRatings();
            if (ratings != null) {
                Log.d(TAG, "ratings:[" + ratings[0].flattenToString() + "]");
                pc_rating = ratings[0].getMainRating();
                rating_system = ratings[0].getRatingSystem();
                if (rating_system.equals("DVB")) {
                    String[] ageArray = pc_rating.split("_", 2);
                    if (ageArray[0].equals("DVB")) {
                        age = Integer.valueOf(ageArray[1]);
                    }
                }
            }
            return age;
        }

        private int getContentRatingsOfCurrentPlayingRecordedProgram() {
            int age = 0;
            String pc_rating;
            String rating_system;
            long currentStreamTime = 0;
            TvContentRating[] ratings;
            Log.d(TAG, "getContentRatingsOfCurrentPlayingRecordedProgram = " + recordedProgram);

            ratings = recordedProgram == null ? null : recordedProgram.getContentRatings();
            if (ratings != null) {
                Log.d(TAG, "ratings:[" + ratings[0].flattenToString() + "]");
                pc_rating = ratings[0].getMainRating();
                rating_system = ratings[0].getRatingSystem();
                if (rating_system.equals("DVB")) {
                    String[] ageArray = pc_rating.split("_", 2);
                    if (ageArray[0].equals("DVB")) {
                        age = Integer.valueOf(ageArray[1]);
                    }
                }
            }

            return age;
        }

        private void setBlockMute(boolean mute) {
            Log.d(TAG, "setBlockMute = " + mute);
            if (mIsPip) {
                playerSetPipMute(mute);
            } else {
                playerSetMute(mute);
            }
        }

        private void setUnBlock(TvContentRating unblockedRating) {
            Log.i(TAG, "setUnBlock " + unblockedRating);
            if (mTunedChannel != null && Channel.isATV(mTunedChannel)) {
                Log.d(TAG, "set ATV UnBlock ");
                requestATVUnblockContent();
            } else {
                requestUnblockContent(mIsPip ? INDEX_FOR_PIP : INDEX_FOR_MAIN);
            }
            notifyContentAllowed();
        }

        private void playerSetAdParams(boolean recover, int delayMs) {
            if (mHandlerThreadHandle == null) {
                return;
            }
            int index = bMainAudioOutput ? INDEX_FOR_MAIN : INDEX_FOR_PIP;
            mHandlerThreadHandle.removeCallbacks(adRunnable);
            adRunnable.setParams(index, recover);
            mHandlerThreadHandle.postDelayed(adRunnable, delayMs);
        }

        private final ADRunnable adRunnable = new ADRunnable();
        private class ADRunnable implements Runnable {
            private int index = 0;
            private boolean recover = true;
            public ADRunnable() {
            }

            public void setParams(int index, boolean recover) {
                this.index = index;
                this.recover = recover;
            }

            @Override
            public void run() {
                mAudioADMixingLevel = mDataManager.getIntParameters(DataManager.TV_KEY_AD_MIX);
                mAudioADVolume = mDataManager.getIntParameters(DataManager.TV_KEY_AD_VOLUME);
                int type = playerGetAudioStreamType();
                Log.d(TAG, "playerSetAdParams path=" + index
                        + ", mAudioADAutoStart = " + mAudioADAutoStart
                        + ", AudioStreamType = " + type
                        + ", mAudioADMixingLevel = " + mAudioADMixingLevel
                        + ", mAudioADVolume = " + mAudioADVolume);
                if (type == ConstantManager.AUDIO_STREAM_TYPE.NORMAL || !recover) {
                    playerSetADMixLevel(index, 0);
                    playerSetADVolume(index, 0);
                } else {
                    playerSetADMixLevel(index, mAudioADMixingLevel);
                    playerSetADVolume(index, mAudioADVolume);
                }
            }
        }

        private void sendUpdateTrackMsg(PlayerState playState, boolean isDvrPlaying) {
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_UPDATE_TRACKS_AND_SELECT);
                Message msg = mHandlerThreadHandle.obtainMessage(MSG_UPDATE_TRACKS_AND_SELECT);
                Log.d(TAG, "sendUpdateTrackMsg state:" + playState);
                if (playerState != PlayerState.PLAYING) {
                    // clear tracks
                    msg.arg2 = 1;
                } else {
                    msg.arg2 = 0;
                }
                if (isDvrPlaying) {
                    msg.arg1 = 1;
                } else {
                    msg.arg1 = 0;
                }
                mHandlerThreadHandle.sendMessage(msg);
            }
        }

        private class MainCallback implements Handler.Callback {
            private final WeakReference<DtvkitTvInputSession> outSession;
            MainCallback(DtvkitTvInputSession session) {
                outSession = new WeakReference<>(session);
            }

            public boolean handleMessage(Message msg) {
                DtvkitTvInputSession session = outSession.get();
                if (session == null) {
                    return false;
                }
                Log.d(session.TAG, "MainHandler [[[:" + msg.what);
                switch (msg.what) {
                    case MSG_SET_SURFACE:
                        boolean set = false;
                        if (mCanPlay) {
                            Log.w(TAG, "already setSurface");
                            break;
                        }
                        if (msg.arg1 == INDEX_FOR_MAIN) {
                            if (mMainHardware != null && mMainStreamConfig != null) {
                                Log.i(TAG, "MSG_SET_SURFACE:" + mSurface);
                                mMainHardware.setSurface(mSurface, mMainStreamConfig[0]);
                                set = true;
                            }
                        }
                        if (msg.arg1 == INDEX_FOR_PIP) {
                            if (mPipHardware != null && mPipStreamConfig != null) {
                                mPipHardware.setSurface(mSurface, mPipStreamConfig);
                                set = true;
                            }
                        }
                        if (set) {
                            mHandlerThreadHandle.post(DtvkitTvInputSession.this::preFirstTune);
                            mCanPlay = true;
                            if (mSurface != null) {
                                tunePendingIfNeeded();
                            }
                            // must be called after "setSurface"
                            /* refer this SOURCE_TYPE_DTV(1) in DroidLogicTvUtils.java */
                            mAudioSystemCmdManager.updateAudioPortGain(1);
                            mSystemControlManager.SetDtvKitSourceEnable(1);
                            mSystemControlManager.SetCurrentSourceInfo(SystemControlManager.SourceInput.DTV, 0, 0);
                        }
                        break;
                    case MSG_SHOW_BLOCKED_TEXT:
                        if (session.mView != null) {
                            String text = (String) msg.obj;
                            boolean blackColor = false;
                            if (null != mSystemControlManager) {
                                if (mSystemControlManager.getScreenColorForSignalChange() == 0) {
                                    blackColor = true;
                                }
                            }
                            session.mView.showBlockedText(text, session.mIsPip, blackColor);
                        }
                        break;
                    case MSG_HIDE_BLOCKED_TEXT:
                        if (session.mView != null) {
                            session.mView.hideBlockedText();
                        }
                        break;
                    case MSG_SHOW_TUNING_IMAGE:
                        if (session.mView != null) {
                            session.mView.showTuningImage(null);
                        }
                        break;
                    case MSG_HIDE_TUNING_IMAGE:
                        if (session.mView != null) {
                            session.mView.hideTuningImage();
                        }
                        break;
                    case MSG_DISPLAY_STREAM_CHANGE_DIALOG:
                        break;
                    case MSG_SET_TELETEXT_MIX_NORMAL:
                        if (!session.mTeleTextMixNormal) {
                            session.mTeleTextMixNormal = true;
                            session.mView.setTeletextMix(TTX_MODE_NORMAL, isSubtitleCTL());
                            Message msg1 = mHandlerThreadHandle.obtainMessage(MSG_SET_RECTANGLE);
                            msg1.arg1 = 0;
                            mHandlerThreadHandle.sendMessage(msg1);
                        }
                        break;
                    case MSG_SET_TELETEXT_MIX_TRANSPARENT: {
                        session.mTeleTextMixNormal = false;
                        session.mView.setTeletextMix(TTX_MODE_TRANSPARENT, isSubtitleCTL());
                        Message msg1 = mHandlerThreadHandle.obtainMessage(MSG_SET_RECTANGLE);
                        msg1.arg1 = 0;
                        mHandlerThreadHandle.sendMessage(msg1);
                    }
                    break;
                    case MSG_SET_TELETEXT_MIX_SEPARATE: {
                        session.mTeleTextMixNormal = false;
                        session.mView.setTeletextMix(TTX_MODE_SEPARATE, isSubtitleCTL());
                        Message msg1 = mHandlerThreadHandle.obtainMessage(MSG_SET_RECTANGLE);
                        msg1.arg1 = 1;
                        mHandlerThreadHandle.sendMessage(msg1);
                    }
                    break;
                    case MSG_SUBTITLE_SHOW_CLOSED_CAPTION:
                        String ccData = (String) msg.obj;
                        boolean bShow = msg.arg1 != 0;
                        if (session.mView != null) {
                            if (bShow && ccData != null) {
                                session.mView.showCCSubtitle(ccData);
                            } else {
                                session.mView.hideCCSubtitle();
                            }
                        }
                        break;
                    case MSG_FILL_SURFACE:
                        fillSurfaceWithFixColor();
                        break;
                    case MSG_EVENT_SHOW_HIDE_OVERLAY:
                        if (session.mView != null) {
                            boolean show = (msg.arg1 == 1);
                            if (show) {
                                session.mView.showOverLay();
                            } else {
                                session.mView.hideOverLay();
                            }
                        }
                        break;
                    case MSG_CI_UPDATE_PROFILE_CONFIRM:
                        showOpSearchConfirmDialog(session.outService.getApplicationContext(), msg.arg1);
                        break;
                    case MSG_CAS_OSM_PREPARE_WINDOW:
                        if (session.mView != null) {
                            Bundle args = (Bundle) (msg.obj);
                            String text = args.getString("msg", "");
                            int mode = args.getInt("mode", 0);
                            int x = args.getInt("anchor_x", 0);
                            int y = args.getInt("anchor_y", 0);
                            int w = args.getInt("w", 0);
                            int h = args.getInt("h", 0);
                            int bg = args.getInt("bg", 0);
                            int alpha = args.getInt("alpah", 0);
                            int fg = args.getInt("fg", 0);
                            session.mView.prepareCasWindow(text, mode, x, y, w, h, bg, alpha, fg);
                        }
                        break;
                    case MSG_CAS_OSM_WINDOW_SHOW:
                        if (session.mView != null) {
                            Bundle args = (Bundle) (msg.obj);
                            int mode = args.getInt("mode", 0);
                            int duration = args.getInt("duration", 0);
                            session.mView.showCasMessage(mode);
                            if (duration > 0) {
                                session.mMainHandle.removeMessages(MSG_CAS_OSM_WINDOW_CLEAR);
                                session.mMainHandle.sendEmptyMessageDelayed(MSG_CAS_OSM_WINDOW_CLEAR, duration * 1000);
                            }
                        }
                        break;
                    case MSG_CAS_OSM_WINDOW_CLEAR:
                        if (session.mView != null) {
                            session.mView.clearCasView();
                        }
                        break;
                    default:
                        Log.d(session.TAG, "MainHandler default");
                        break;
                }
                Log.d(session.TAG, "MainHandler " + msg.what + " done]]]");
                return false;
            }
        }

        private boolean checkRealTimeResolution() {
            boolean result = false;
            if (mTunedChannel == null) {
                return true;
            }
            String serviceType = mTunedChannel.getServiceType();
            //update video track resolution
            if (TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO.equals(serviceType)) {
                int[] videoSize = playerGetDTVKitVideoSize();
                String realtimeVideoFormat = null;//mSysSettingManager.getVideoFormatFromSys();
                if (videoSize[0] > 0 && videoSize[1] > 0 && videoSize[2] > 0
                        && videoSize[3] >= 0 && videoSize[3] <= 1) {
                    realtimeVideoFormat = videoSize[1] + (videoSize[3] == 1 ? "P" : "I");
                }
                result = !TextUtils.isEmpty(realtimeVideoFormat);
                if (result) {
                    Bundle formatBundle = new Bundle();
                    formatBundle.putString(ConstantManager.PI_FORMAT_KEY, realtimeVideoFormat);
                    notifySessionEvent(ConstantManager.EVENT_STREAM_PI_FORMAT, formatBundle);
                    Log.d(TAG, "checkRealTimeResolution notify realtimeVideoFormat = "
                            + realtimeVideoFormat + ", videoSize width = " + videoSize[0]
                            + ", height = " + videoSize[1]);
                }
            }
            return result;
        }

        private boolean sendCurrentSignalInformation() {
            if (mTunedChannel == null) {
                return false;
            }
            int[] signalInfo = getSignalStatus();
            Bundle signalBundle = new Bundle();
            signalBundle.putInt(ConstantManager.KEY_SIGNAL_STRENGTH, signalInfo[0]);
            signalBundle.putInt(ConstantManager.KEY_SIGNAL_QUALITY, signalInfo[1]);
            notifySessionEvent(ConstantManager.EVENT_SIGNAL_INFO, signalBundle);
            Log.d(TAG, "sendCurrentSignalInformation notify signalStrength = " + signalInfo[0]
                    + ", signalQuality = " + signalInfo[1]);
            return true;
        }

        private void setAdMain(int param1) {
            boolean result = playerSelectAudioTrack(param1);
            Log.d(TAG, "setAdMain result=" + result + ", setAudioStream " + param1);
        }

        private void setTimeshiftPlay(Uri uri) {
            Log.i(TAG, "setTimeshiftPlay " + uri);
            mSessionState = SessionState.TUNED;
            recordedProgram = getRecordedProgram(uri);
            if (recordedProgram != null) {
                playerState = PlayerState.PLAYING;
                onFinish(false, true);
                DtvkitGlueClient.getInstance().registerSignalHandler(mHandler);
                mAudioADAutoStart = mDataManager.getIntParameters(DataManager.TV_KEY_AD_SWITCH) == 1;
                mIsTimeshiftingPlayed = true;
                if (playerPlay(INDEX_FOR_MAIN, recordedProgram.getRecordingDataUri(),
                        mAudioADAutoStart, false, 0, "", "").equals("ok")) {
                    notifyChannelRetuned(uri);
                } else {
                    DtvkitGlueClient.getInstance().unregisterSignalHandler(mHandler);
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                }
            }
        }

        private void setTimeshiftResume() {
            Log.i(TAG, "setTimeShiftResume ");
            playerState = PlayerState.PLAYING;
            if (playerResume()) {
                playSpeed = 1;
            }
        }

        private void setTimeshiftPause() {
            Log.i(TAG, "setTimeshiftPause ");
            if (playTimeshiftRecordingInSession(true, true)) {
                Log.i(TAG, "starting pause playback ");
            } else {
                Log.i(TAG, "player pause ");
                if (playerPause()) {
                    playSpeed = 0;
                }
            }
        }

        private void setTimeshiftSeek(long timeMs) {
            Log.i(TAG, "setTimeShiftSeekTo:  " + timeMs);
            if (timeshiftRecorderState == RecorderState.RECORDING) {
                if (!mIsTimeshiftingPlayed) {
                    playTimeshiftRecordingInSession(false, timeMs != startPosition);
                } else {
                    //diff time may change within 1s, add extra 1s to prevent it from playing
                    // previous program when seeking to the beginning of current in timeshift mode
                    long rawMsPosition = (timeMs - (originalStartPosition + PropSettingManager.getStreamTimeDiff()));
                    long floorPosition = rawMsPosition % 1000;
                    long position = rawMsPosition / 1000 + (floorPosition > 0 ? 1 : 0);
                    playerSeekTo(Math.max(0, position));
                }
            } else {
                playerSeekTo(timeMs / 1000);
            }
        }

        private void setTimeShiftSetPlaybackParams(PlaybackParams params) {
            Log.i(TAG, "setTimeShiftSetPlaybackParams:  " + params);
            float speed = params.getSpeed();
            Log.i(TAG, "speed: " + speed);
            if (speed != playSpeed) {
                playTimeshiftRecordingInSession(false, true);
                if (playerSetSpeed(speed)) {
                    playSpeed = speed;
                }
            }
        }

        // use this instead of playerPlayTimeshiftRecording directly
        private boolean playTimeshiftRecordingInSession(boolean paused,             boolean current) {
            boolean ret = false;
            if (!mIsTimeshiftingPlayed) {
                /*
                  The mheg may hold an external_control in the dtvkit,
                  which upset the normal av process following, so stop it first,
                  thus, mheg will not be valid since here to the next onTune.
                */
                //mhegStop();
                ret = playerPlayTimeshiftRecording(paused, current);
                mIsTimeshiftingPlayed = ret;
            } else {
                Log.w(TAG, "playTimeshiftRecordingInSession state:" + timeshiftRecorderState);
            }
            return ret;
        }

        // use this instead of playerStopTimeshiftRecording directly
        boolean stopTimeshiftRecordingInSession(boolean returnToLive) {
            boolean ret = playerStopTimeshiftRecording(returnToLive);
            if (ret) {
                mIsTimeshiftingPlayed = false;
            }
            return ret;
        }

        private void tryStartTimeshifting() {
            boolean success = false;
            if (FeatureUtil.getFeatureSupportTimeshifting()) {
                if (timeshiftRecorderState == RecorderState.STOPPED) {
                    numActiveRecordings = recordingGetNumActiveRecordings();
                    Log.i(TAG, "numActiveRecordings: " + numActiveRecordings);
                    if (recordingPending) {
                        numActiveRecordings += 1;
                        Log.i(TAG, "recordingPending: +1");
                    }
                    if (numActiveRecordings >= numRecorders
                            || numActiveRecordings >= getNumRecordersLimit()) {
                        timeshiftAvailable.setNo();
                    }
                }
                playerSetTimeshiftBufferSize(getTimeshiftBufferSizeMins(), getTimeshiftBufferSizeMBs());
                Log.i(TAG, "tryStartTimeshifting timeshiftAvailable: " + timeshiftAvailable
                        + ", timeshiftRecorderState: " + timeshiftRecorderState);
                if (timeshiftAvailable.isAvailable()) {
                    if (timeshiftRecorderState != RecorderState.STOPPED) {
                        Log.w(TAG, "tryStartTimeshifting do nothing, timeshift is not stopped.");
                        return;
                    }
                    timeshiftRecorderState = RecorderState.STARTING;
                    if (playerStartTimeshiftRecording()) {
                        Log.i(TAG, "tryStartTimeshifting OK");
                        success = true;
                    } else {
                        timeshiftRecorderState = RecorderState.STOPPED;
                        Log.e(TAG, "tryStartTimeshifting fail");
                    }
                }
                if (!success) {
                    showToast(R.string.timeshift_unavailable_program);
                }
            } else  {
                showToast(R.string.timeshift_unsupported_system);
            }
            if (!success) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
            }
        }

        private void tryStopTimeshifting() {
            if (FeatureUtil.getFeatureSupportTimeshifting()) {
                Log.i(TAG, "tryStopTimeshifting timeshiftAvailable: " + timeshiftAvailable
                        + ", timeshiftRecorderState = " + timeshiftRecorderState);
                if (timeshiftAvailable.isAvailable()) {
                    // timeshiftRecorderState maybe changed to STOPPED state due to dtvkit
                    // internal reason (when usb unplug), not by user.
                    if ((timeshiftRecorderState == RecorderState.RECORDING || mIsTimeshiftingPlayed)) {
                        if (stopTimeshiftRecordingInSession(true)) {
                            Log.d(TAG, "tryStopTimeshifting OK");
                        } else {
                            Log.d(TAG, "tryStopTimeshifting fail");
                            notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
                        }
                    }
                } else {
                    Log.w(TAG, "tryStopTimeshifting not available");
                    notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
                }
            } else {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
            }
        }

        private void monitorTimeshiftRecordingPathAndTryRestart(boolean on, boolean now,
                                                                boolean start) {
            /*monitor the rec path*/
            if (mHandlerThreadHandle != null) {
                Log.d(TAG, "monitorTimeshiftRecordingPathAndTryRestart on = " + on
                        + ", now = " + now + ", start = " + start);
                if (mHandlerThreadHandle.hasMessages(MSG_CHECK_REC_PATH)) {
                    mHandlerThreadHandle.removeMessages(MSG_CHECK_REC_PATH);//remove pending message
                }
                if (on) {
                    Message mess = mHandlerThreadHandle.obtainMessage(now ? MSG_CHECK_REC_PATH_DIRECTLY : MSG_CHECK_REC_PATH, on ? 1 : 0, start ? 1 : 0);
                    boolean info = mHandlerThreadHandle.sendMessageDelayed(mess, now ? 0 : MSG_CHECK_REC_PATH_PERIOD);
                }
            }
        }

        public void sendMsgTryStartTimeshift(int delay) {
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TRY_START_TIMESHIFT);
                mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_TRY_START_TIMESHIFT, delay);
            }
        }

        public void sendMsgTryStopTimeshift(int delay) {
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TRY_STOP_TIMESHIFT);
                mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_TRY_STOP_TIMESHIFT, delay);
            }
        }

        public void sendMsgTsUpdate() {
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_TS_UPDATE);
                mHandlerThreadHandle.sendEmptyMessage(MSG_TS_UPDATE);
            }
        }

        @SuppressWarnings("unused")
        private void scheduleTimeshiftRecordingTask() {
            final long SCHEDULE_TIMESHIFT_RECORDING_DELAY_MILLIS = 1000 * 2;
            Log.i(TAG, "calling scheduleTimeshiftRecordingTask");
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_SCHEDULE_TIMESHIFT_RECORDING_TASK);
                mHandlerThreadHandle.sendEmptyMessageDelayed(MSG_SCHEDULE_TIMESHIFT_RECORDING_TASK, SCHEDULE_TIMESHIFT_RECORDING_DELAY_MILLIS);
            }
        }

        @SuppressWarnings("unused")
        private void removeScheduleTimeshiftRecordingTask() {
            Log.i(TAG, "calling removeScheduleTimeshiftRecordingTask");
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.removeMessages(MSG_SCHEDULE_TIMESHIFT_RECORDING_TASK);
            }
        }

        private void timeshiftRecordingTask() {
            Log.i(TAG, "timeshiftRecordingTask");
            resetRecordingPath();
            tryStartTimeshifting();
        }

        private boolean getIsFixedTunnel() {
            File userDir = new File("/sys/module/dvb_demux/");
            if (userDir.exists() || (PropSettingManager.getInt("vendor.tv.fixed_tunnel", 0) == 1)) {
                return true;
            }
            return false;
        }

        private int getTimeshiftBufferSizeMins() {
            return PropSettingManager.getInt("vendor.tv.dtv.tf.mins", timeshiftBufferSizeMins);
        }

        private int getTimeshiftBufferSizeMBs() {
            return PropSettingManager.getInt("vendor.tv.dtv.tf.mbs", timeshiftBufferSizeMBs);
        }

        private void sendBundleToAppByTif(String action, Bundle event) {
            Log.d(TAG, "sendBundleToAppByTif action = " + action + ", event = " + event);
            notifySessionEvent(action, event);
        }

        private void processCasEvents(JSONObject event) {
            JSONObject casEvent = null;
            try {
                String casString = (String) (event.get("CasJsonString"));
                if (!TextUtils.isEmpty(casString)) {
                    casEvent = new JSONObject(casString.trim());
                }
            } catch (Exception e) {
            }
            if (casEvent != null) {
                String type = casEvent.optString("type");
                if ("OsdAttr".equals(type)) {
                    if (mMainHandle != null) {
                        mMainHandle.removeMessages(MSG_CAS_OSM_PREPARE_WINDOW);
                        Message message = mMainHandle.obtainMessage(MSG_CAS_OSM_PREPARE_WINDOW);
                        Bundle args = new Bundle();
                        args.putString("msg", casEvent.optString("osdContent", ""));
                        args.putInt("mode", casEvent.optInt("osdMode", 0));
                        args.putInt("anchor_x", casEvent.optInt("osdX", 0));
                        args.putInt("anchor_y", casEvent.optInt("osdY", 0));
                        args.putInt("w", casEvent.optInt("osdW", 0));
                        args.putInt("h", casEvent.optInt("osdH", 0));
                        args.putInt("bg", casEvent.optInt("osdBackground", 0));
                        args.putInt("alpah", casEvent.optInt("osdAlpha", 0));
                        args.putInt("fg", casEvent.optInt("osdForeground", 0));
                        message.obj = (Object) (args);
                        mMainHandle.sendMessage(message);
                    }
                } else if ("OsdDisplay".equals(type)) {
                    if (mMainHandle != null) {
                        mMainHandle.removeMessages(MSG_CAS_OSM_WINDOW_CLEAR);
                        mMainHandle.removeMessages(MSG_CAS_OSM_WINDOW_SHOW);
                        Message message = mMainHandle.obtainMessage(MSG_CAS_OSM_WINDOW_SHOW);
                        Bundle args = new Bundle();
                        args.putInt("mode", casEvent.optInt("osdDisplayMode", 0));
                        args.putInt("duration", casEvent.optInt("osdDisplayDuration", 0));
                        message.obj = (Object) (args);
                        mMainHandle.sendMessage(message);
                    }
                } else {
                    if ("DscState".equals(type)) {
                        int state = casEvent.optInt("DscState", 0);
                        int pathType = casEvent.optInt("pathType", 0);
                        int scrambledMsg = state + MSG_SHOW_BLOCKED_TEXT;
                        if (pathType == 0 && mMainHandle != null) {
                            mMainHandle.sendEmptyMessage(scrambledMsg);
                        }
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("cas", casEvent.toString());
                    notifySessionEvent("cas_event", bundle);
                }
            }
        }

        private void onCasRequest(Bundle request) {
            if (mParameterManager != null) {
                long session = request.getLong("session");
                String casInputJson = request.getString("CasJsonString");
                String ret = mParameterManager.casSessionRequest(session, casInputJson);
                if (!TextUtils.isEmpty(ret)) {
                    Bundle bundle = new Bundle();
                    bundle.putString("cas", ret.trim());
                    notifySessionEvent("cas_event", bundle);
                }
            }
        }

        private void requestBlockChannel(long channelId, boolean isLocked) {
            Channel channel = getChannel(channelId);
            if (channel != null) {
                if (Channel.isATV(channel)) {
                    InternalProviderData data = channel.getInternalProviderData();
                    if (data == null) {
                        Log.d(TAG, "requestBlockChannel channel data is null ");
                        return;
                    }
                    if (mParameterManager != null) {
                        try {
                            int unikey = Integer.parseInt((String) data.get("unikey")); //unikey
                            mParameterManager.setATVChannelBlock(isLocked, unikey);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (mTunedChannel != null && (mTunedChannel.getId() == channelId)) {
                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                Message msg = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                msg.arg1 = isLocked ? 0 : 1;
                                mMainHandle.sendMessageDelayed(msg, 0);
                            }
                            if (!isLocked) {
                                notifyContentAllowed();
                            }
                        }
                    }
                } else {
                    String dvbUri = getChannelInternalDvbUri(channel);
                    if (mParameterManager != null) {
                        mParameterManager.setChannelBlock(isLocked, dvbUri);
                        if (mTunedChannel != null && (mTunedChannel.getId() == channelId)) {
                            if (mMainHandle != null) {
                                mMainHandle.removeMessages(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                Message msg = mMainHandle.obtainMessage(MSG_EVENT_SHOW_HIDE_OVERLAY);
                                msg.arg1 = isLocked ? 0 : 1;
                                mMainHandle.sendMessageDelayed(msg, 0);
                            }
                            if (!isLocked) {
                                notifyContentAllowed();
                            }
                        }
                    }
                }
            }
        }

        private Channel getChannel(long channelId) {
            return TvContractUtils.getChannel(mContentResolver, TvContract.buildChannelUri(channelId));
        }

        private String createQuerySelection(int dvbSource) {
            String result = null;

            switch (dvbSource) {
                case ParameterManager.SIGNAL_COFDM:
                    result = "type=\"TYPE_DVB_T\" or type=\"TYPE_DVB_T2\"";
                    break;
                case ParameterManager.SIGNAL_QAM:
                    result = "type=\"TYPE_DVB_C\"";
                    break;
                case ParameterManager.SIGNAL_QPSK:
                    result = "type=\"TYPE_DVB_S\" or type=\"TYPE_DVB_S2\"";
                    break;
                case ParameterManager.SIGNAL_ISDBT:
                    result = "type=\"TYPE_ISDB_T\" or type=\"TYPE_PAL\" or type=\"TYPE_NTSC\" or type=\"TYPE_SECAM\"";
                    break;
                default:
                    break;
            }
            return result;
        }

        private Channel getFirstChannel(int dvbSource) {
            Channel channel = null;
            Cursor cursor = null;
            Uri uri = TvContract.buildChannelsUriForInput(mInputId);
            String signalType = TvContractUtils.dvbSourceToChannelTypeString(dvbSource);
            String signalSelection = createQuerySelection(dvbSource);
            try {
                if (uri != null) {
                    cursor = outService.getContentResolver().query(uri, Channel.PROJECTION, signalSelection, null, null);
                }
                if (cursor == null || cursor.getCount() == 0) {
                    Log.w(TAG, "getFirstChannel total=0");
                    return null;
                }

                int antennaType = 0;
                if (dvbSource == ParameterManager.SIGNAL_ISDBT) {
                    String dtvType = mDataManager.getStringParameters(DataManager.KEY_ISDB_ANTENNA_TYPE);
                    if (TextUtils.equals(TvContract.Channels.TYPE_ATSC_C, dtvType)) {
                        antennaType = 1;
                    }
                }

                while (cursor.moveToNext()) {
                    Channel nextChannel = Channel.fromCursor(cursor);
                    Log.i(TAG, "channel " + nextChannel);
                    if ((nextChannel.getType().contains(signalType) && antennaType == 0)
                        || (Channel.isATV(nextChannel) && nextChannel.getAntennaType() == antennaType)) {
                        channel = nextChannel;
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, " " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return channel;
        }

        protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
            pw.increaseIndent();
            pw.println(this);
            pw.increaseIndent();
            if (mHandlerThreadHandle != null) {
                mHandlerThreadHandle.dump(pw, "");
            }
            pw.println("Session   state:" + mSessionState);
            pw.println("TimeShift state:" + timeshiftRecorderState);
            pw.println("LiveTv    state:" + playerState);
            pw.println("Current:" + mTunedChannel);
            if (mView != null) {
                pw.println("OverlayView:" + mWinWidth + "x" + mWinHeight);
                pw.println(mView);
                mView.test(args);
            }
            pw.println("Surface:" + mSurface);
            if (mHbbTvManager != null) {
                pw.println("HbbTv: enabled");
            }
            pw.decreaseIndent();
        }

        @Override
        public String toString() {
            return "[" + getClass().getSimpleName()
                    + ", index=" + mCurrentSessionIndex
                    + ", instance=0x" + Integer.toHexString(System.identityHashCode(this))
                    + "]";
        }

        private void sendAuthUrlUpdateBroadcast(JSONObject data) {
            if (null == data) {
                Log.d(TAG, "sendAuthUrlUpdateBroadcast error not have Auth Url");
                return;
            }
            try {
                if (true == data.getBoolean("accepted")) {
                    String authUrl = data.getString("auth_url");
                    Log.d(TAG, "sendAuthUrlUpdateBroadcast authUrl : " + authUrl);
                    Intent intent = new Intent();
                    Bundle fvpData = new Bundle();
                    fvpData.putString("auth_url", authUrl);
                    intent.setAction("com.android.tv.fvp.INTENT_ACTION");
                    intent.setComponent(new ComponentName("com.droidlogic.android.tv", "com.android.tv.fvp.FvpIntentReceiver"));
                    intent.putExtra("FVP_TYPE", "auth_update");
                    intent.putExtra("FVP_CONFIG", fvpData);
                    outService.sendBroadcast(intent);
                } else {
                    Log.d(TAG, "sendAuthUrlUpdateBroadcast accepted false ");
                }
            } catch (Exception e) {
                Log.i(TAG, "prepareAuthUrl Exception = " + e.getMessage());
            }
        }

        private boolean isIpChannel(Channel channel) {
            boolean isIpChannel = false;
            InternalProviderData data = channel.getInternalProviderData();
            if (null != data) {
                try {
                    isIpChannel = TextUtils.equals(data.get("service_type").toString(), "IP");
                } catch (Exception e) {
                    Log.d(TAG, "isIpChannel Exception : " + e);
                }
            }
            return isIpChannel;
        }

        private boolean playerSelectSubtitleTrack(int index) {
            return playerSelectSubtitleTrackById(INDEX_FOR_MAIN, index);
        }

        private boolean playerPipSelectSubtitleTrack(int index) {
            return playerSelectSubtitleTrackById(INDEX_FOR_PIP, index);
        }

        private boolean playerSelectSubtitleTrackById(int id, int index) {
            try {
                JSONArray args = new JSONArray();
                args.put(id);
                args.put(index);
                DtvkitGlueClient.getInstance().request("Player.setSubtitleStream", args);
            } catch (Exception e) {
                Log.e(TAG, "playerSelectSubtitleTrack = " + e.getMessage());
                return false;
            }
            return true;
        }

        private boolean playerSelectTeletextTrack(int index) {
            return playerSelectTeletextTrackById(INDEX_FOR_MAIN, index);
        }

        private boolean playerPipSelectTeletextTrack(int index) {
            return playerSelectTeletextTrackById(INDEX_FOR_PIP, index);
        }

        private boolean playerSelectTeletextTrackById(int id, int index) {
            try {
                JSONArray args = new JSONArray();
                args.put(id);
                args.put(index);
                DtvkitGlueClient.getInstance().request("Player.setTeletextStream", args);
            } catch (Exception e) {
                Log.e(TAG, "playerSelectTeletextTrack = " + e.getMessage());
                return false;
            }
            return true;
        }

        private boolean playerStartTeletext(int index) {
            return playerStartTeletextById(INDEX_FOR_MAIN, index);
        }

        private boolean playerPipStartTeletext(int index) {
            return playerStartTeletextById(INDEX_FOR_PIP, index);
        }

        //called when teletext on
        private boolean playerStartTeletextById(int id, int index) {
            try {
                JSONArray args = new JSONArray();
                args.put(id);
                args.put(index);
                mIsTeletextStarted = DtvkitGlueClient.getInstance().request("Player.startTeletext", args).getBoolean("data");
            } catch (Exception e) {
                Log.e(TAG, "playerStartTeletext = " + e.getMessage());
                return false;
            }
            return true;
        }

        private boolean playerStopTeletext() {
            return playerStopTeletext(INDEX_FOR_MAIN);
        }

        private boolean playerPipStopTeletext() {
            return playerStopTeletext(INDEX_FOR_PIP);
        }

        //called when teletext off
        private boolean playerStopTeletext(int index) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                DtvkitGlueClient.getInstance().request("Player.stopTeletext", args);
            } catch (Exception e) {
                Log.e(TAG, "playerStopTeletext = " + e.getMessage());
                return false;
            }
            return true;
        }

        private boolean playerPauseTeletext() {
            return playerPauseTeletext(INDEX_FOR_MAIN);
        }

        private boolean playerPipPauseTeletext() {
            return playerPauseTeletext(INDEX_FOR_PIP);
        }

        private boolean playerPauseTeletext(int index) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                DtvkitGlueClient.getInstance().request("Player.pauseTeletext", args);
            } catch (Exception e) {
                Log.e(TAG, "playerPauseTeletext = " + e.getMessage());
                return false;
            }
            return true;
        }

        private boolean playerResumeTeletext() {
            return playerResumeTeletext(INDEX_FOR_MAIN);
        }

        private boolean playerPipResumeTeletext() {
            return playerResumeTeletext(INDEX_FOR_PIP);
        }

        private boolean playerResumeTeletext(int index) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                DtvkitGlueClient.getInstance().request("Player.resumeTeletext", args);
            } catch (Exception e) {
                Log.e(TAG, "playerResumeTeletext = " + e.getMessage());
                return false;
            }
            return true;
        }

        private boolean playerIsTeletextDisplayed() {
            return playerIsTeletextDisplayed(INDEX_FOR_MAIN);
        }

        private boolean playerPipIsTeletextDisplayed() {
            return playerIsTeletextDisplayed(INDEX_FOR_PIP);
        }

        private boolean playerIsTeletextDisplayed(int index) {
            boolean on = false;
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                on = DtvkitGlueClient.getInstance().request("Player.isTeletextDisplayed", args).getBoolean("data");
            } catch (Exception e) {
                Log.e(TAG, "playerResumeTeletext = " + e.getMessage());
            }
            return on;
        }

        private boolean playerIsTeletextStarted() {
            return playerIsTeletextStarted(INDEX_FOR_MAIN);
        }

        private boolean playerPipIsTeletextStarted() {
            return playerIsTeletextStarted(INDEX_FOR_PIP);
        }

        private boolean playerIsTeletextStarted(int index) {
            boolean on = false;
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                on = DtvkitGlueClient.getInstance().request("Player.isTeletextStarted", args).getBoolean("data");
            } catch (Exception e) {
                Log.e(TAG, "playerIsTeletextStarted = " + e.getMessage());
            }
            Log.d(TAG, "playerIsTeletextStarted on = " + on);
            return on;
        }

        protected boolean playerSetTeletextOn(boolean on, int index) {
            return playerSetTeletextOnById(INDEX_FOR_MAIN, on, index);
        }

        private boolean playerPipSetTeletextOn(boolean on, int index) {
            return playerSetTeletextOnById(INDEX_FOR_PIP, on, index);
        }

        private boolean playerSetTeletextOnById(int id, boolean on, int index) {
            boolean result;
            if (on) {
                result = playerStartTeletextById(id, index);
            } else {
                result = playerStopTeletext(id);
            }
            return result;
        }

        private boolean playerIsTeletextOn() {
            return playerIsTeletextOn(INDEX_FOR_MAIN);
        }

        private boolean playerPipIsTeletextOn() {
            return playerIsTeletextOn(INDEX_FOR_PIP);
        }

        private boolean playerIsTeletextOn(int index) {
            return playerIsTeletextStarted(index);
        }

        private boolean playerNotifyTeletextEvent(int event) {
            return playerNotifyTeletextEvent(INDEX_FOR_MAIN, event);
        }

        private boolean playerPipNotifyTeletextEvent(int event) {
            return playerNotifyTeletextEvent(INDEX_FOR_PIP, event);
        }

        private boolean playerNotifyTeletextEvent(int index, int event) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(event);
                DtvkitGlueClient.getInstance().request("Player.notifyTeletextEvent", args);
            } catch (Exception e) {
                Log.e(TAG, "playerNotifyTeletextEvent = " + e.getMessage());
                return false;
            }
            return true;
        }
    }

    private boolean resetRecordingPath() {
        String newPath = mDataManager.getStringParameters(DataManager.KEY_PVR_RECORD_PATH);
        boolean changed = false;

        String path = SysSettingManager.convertMediaPathToMountedPath(newPath);
        if (!TextUtils.isEmpty(path) && !SysSettingManager.isMountedPathAvailable(path) && !FeatureUtil.getFeatureSupportNewTvApp()) {
            Log.d(TAG, "removable device has been moved and use default path");
            newPath = DataManager.PVR_DEFAULT_PATH;
            mDataManager.saveStringParameters(DataManager.KEY_PVR_RECORD_PATH, newPath);
            changed = true;
        } else {
            Log.d(TAG, "resetRecordingPath newLiveTv can set default path");
        }
        if (FeatureUtil.getFeatureSupportNewTvApp()) {
            if (TextUtils.isEmpty(newPath) || DataManager.PVR_DEFAULT_PATH.equals(newPath)) {
                Log.d(TAG, "sei livetv support removable storage only");
                return false;
            } else {
                Log.d(TAG, "resetRecordingPath newLiveTv newPath = " + newPath);
            }
        }
        recordingAddDiskPath(newPath);
        recordingSetDefaultDisk(newPath);
        return changed;
    }

    private String getChannelInternalDvbUri(Channel channel) {
        if (channel == null) {
            return "dvb://0000.0000.0000";
        }
        try {
            return channel.getInternalProviderData().get("dvbUri").toString();
        } catch (Exception e) {
            Log.e(TAG, "getChannelInternalDvbUri1 Exception = " + e.getMessage());
            return "dvb://0000.0000.0000";
        }
    }

    private String getChannelInternalDvbUriForFcc(Uri channelUri) {
        String result = "";
        if (channelUri == null) {
            return result;
        }
        Channel channel = null;
        try {
            if (getMainTunerSession() != null) {
                channel = TvContractUtils.getChannel(mContentResolver, channelUri);
            }
            if (channel != null) {
                result = channel.getInternalProviderData().get("dvbUri").toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "getChannelInternalDvbUri2 Exception = " + e.getMessage());
        }
        return result;
    }

    private void playerSetVideoCrop(int index, int voff0, int hoff0, int voff1, int hoff1) {
        synchronized (mLock) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(voff0);
                args.put(hoff0);
                args.put(voff1);
                args.put(hoff1);
                DtvkitGlueClient.getInstance().request("Player.setVideoWindow", args);
            } catch (Exception e) {
                Log.e(TAG, "playerSetVideoWindow = " + e.getMessage());
            }
        }
    }

    private void playerSetVolume(int volume) {
        playerSetVolume(volume, INDEX_FOR_MAIN);
    }

    private void playerSetPipVolume(int volume) {
        playerSetVolume(volume, INDEX_FOR_PIP);
    }

    private void playerSetVolume(int volume, int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(volume);
            DtvkitGlueClient.getInstance().request("Player.setVolume", args);
        } catch (Exception e) {
            Log.e(TAG, "playerSetVolume = " + e.getMessage());
        }
    }

    private void playerSetADMixLevel(int index, int level) {
        synchronized (mLock) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(level);
                DtvkitGlueClient.getInstance().request("Player.setADMixLevel", args);
            } catch (Exception e) {
                Log.e(TAG, "playerSetADMixLevel = " + e.getMessage());
            }
        }
    }

    private void playerSetADVolume(int index, int volume) {
        synchronized (mLock) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(volume);
                DtvkitGlueClient.getInstance().request("Player.setADVolume", args);
            } catch (Exception e) {
                Log.e(TAG, "playerSetADVolume = " + e.getMessage());
            }
        }
    }

    private void playerSetMute(boolean mute) {
        playerSetMute(mute, INDEX_FOR_MAIN);
    }

    private void playerSetPipMute(boolean mute) {
        playerSetMute(mute, INDEX_FOR_PIP);
    }

    private void playerSetMute(boolean mute, int index) {
        synchronized (mLock) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(mute);
                DtvkitGlueClient.getInstance().request("Player.setMute", args);
            } catch (Exception e) {
                Log.e(TAG, "playerSetMute = " + e.getMessage());
            }
        }
    }

    private boolean playerGetMute() {
        return playerGetMute(INDEX_FOR_MAIN);
    }

    private boolean playerGetMute(int index) {
        boolean result = false;
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            JSONObject obj = DtvkitGlueClient.getInstance().request("Player.getMute", args);
            if (obj != null && obj.getBoolean("accepted")) {
                result = obj.getBoolean("data");
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetMute = " + e.getMessage());
        }
        Log.d(TAG, "playerGetMute result = " + result);
        return result;
    }

    private void playerSetServiceMute(boolean mute) {
        playerSetServiceMute(mute, INDEX_FOR_MAIN);
    }

    private void playerSetServiceMute(boolean mute, int index) {
        synchronized (mLock) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(0);
                args.put(mute);
                DtvkitGlueClient.getInstance().request("Player.setServiceMute", args);
            } catch (Exception e) {
                Log.e(TAG, "playerSetServiceMute = " + e.getMessage());
            }
        }
    }

    protected void playerSetSubtitlesOn(boolean on) {
        playerSetSubtitlesOn(on, INDEX_FOR_MAIN);
    }

    private void playerSetPipSubtitlesOn(boolean on) {
        playerSetSubtitlesOn(on, INDEX_FOR_PIP);
    }

    private void playerSetSubtitlesOn(boolean on, int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(on);
            DtvkitGlueClient.getInstance().request("Player.setSubtitlesOn", args);
            Log.i(TAG, "playerSetSubtitlesOn on =  " + on);
        } catch (Exception e) {
            Log.e(TAG, "playerSetSubtitlesOn = " + e.getMessage());
        }
    }


    // atv player code start
    private boolean playerPlay_ATV(Channel channel) {
        JSONArray array = new JSONArray();
        InternalProviderData data = channel.getInternalProviderData();
        if (data == null) {
            return false;
        }
        try {
            array.put(Integer.parseInt((String) data.get("unikey"))); //unikey
            array.put(channel.getAntennaType()); //atv_sigtype
            array.put(Integer.parseInt((String) data.get("frequency"))); //freq
            array.put(Integer.parseInt((String) data.get("video_std"))); //vstd
            array.put(Integer.parseInt((String) data.get("audio_std"))); //astd
            array.put(Integer.parseInt((String) data.get("vfmt"))); //vfmt
            Log.i(TAG, "AtvPlayer playerPlay:" + channel);
            DtvkitGlueClient.getInstance().request("AtvPlayer.play", array);
        } catch (Exception e) {
            Log.d(TAG, "[debug]channel : " + channel);
            Log.d(TAG, "[debug]data : " + data);
            e.printStackTrace();
            return false;
        }
        return true;
    }
    // atv player code end

    private String playerPlay(int index, String dvbUri, boolean ad_enable, boolean disable_audio, int surface, String uri_cache1, String uri_cache2) {
        synchronized (mLock) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(dvbUri);
                args.put(ad_enable);
                args.put(disable_audio);
                args.put(surface);
                args.put(uri_cache1);
                args.put(uri_cache2);
                Log.d(TAG, "player.play: " + dvbUri);
                JSONObject resp = DtvkitGlueClient.getInstance().request("Player.play", args);
                boolean ok = resp.optBoolean("data");
                if (ok)
                    return "ok";
                else
                    return resp.optString("data", "");
            } catch (Exception e) {
                Log.e(TAG, "playerPlay = " + e.getMessage());
                return "unknown error";
            }
        }
    }

    private void setSurfaceTunnelId(int index, int tunnelId) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(tunnelId);
            JSONObject resp = DtvkitGlueClient.getInstance().request("Player.setSurface", args);
            boolean ok = resp.optBoolean("data");
            if (!resp.optBoolean("data")) {
                Log.d(TAG, "setSurfaceTunnelId resp: " + resp);
            }
        } catch (Exception e) {
            Log.e(TAG, "setSurfaceTunnelId = " + e.getMessage());
        }
    }

    private void atvPlayerStop() {
        try {
            Log.i(TAG, "AtvPlayer.stop");
            DtvkitGlueClient.getInstance().request("AtvPlayer.stop", new JSONArray());
        } catch (Exception ignored) {}
    }

    private void playerStop() {
        playerStop(INDEX_FOR_MAIN, 2); // stop FCC on index 0
        playerStop(INDEX_FOR_MAIN, 1); // stop main on index 0
    }

    private void playerStopAndKeepFcc() {
        playerStop(INDEX_FOR_MAIN, 1); // stop main on index 0
    }

    private void playerPipStop() {
        playerStop(INDEX_FOR_PIP, 0);  // stop main&FCC on index 1
                                       // notice we didn't run FCC on index 1
    }

    /* About stopMode, 0: stop Main&FCC, 1: stop Main, 2: stop FCC */
    private void playerStop(int index, int stopMode) {
        synchronized (mLock) {
            try {
                JSONArray args = new JSONArray();
                args.put(index);
                args.put(stopMode);
                DtvkitGlueClient.getInstance().request("Player.stop", args);
                Log.d(TAG, "playerStop idx:" + index + " stopMode:" + stopMode);
            } catch (Exception e) {
                Log.e(TAG, "playerStop = " + e.getMessage());
            }
        }
    }

    private boolean playerPause() {
        return playerPause(INDEX_FOR_MAIN);
    }

    private boolean playerPipPause() {
        return playerPause(INDEX_FOR_PIP);
    }

    private boolean playerPause(int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            DtvkitGlueClient.getInstance().request("Player.pause", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "playerPause = " + e.getMessage());
            return false;
        }
    }

    private boolean playerResume() {
        return playerResume(INDEX_FOR_MAIN);
    }

    private boolean playerPipResume() {
        return playerResume(INDEX_FOR_PIP);
    }

    private boolean playerResume(int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            DtvkitGlueClient.getInstance().request("Player.resume", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "playerResume = " + e.getMessage());
            return false;
        }
    }

    private void playerFastForward() {
        playerFastForward(INDEX_FOR_MAIN);
    }

    private void playerPipFastForward() {
        playerFastForward(INDEX_FOR_PIP);
    }

    private void playerFastForward(int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            DtvkitGlueClient.getInstance().request("Player.fastForward", args);
        } catch (Exception e) {
            Log.e(TAG, "playerFastForward = " + e.getMessage());
        }
    }

    private void playerFastRewind() {
        playerFastRewind(INDEX_FOR_MAIN);
    }

    private void playerPipFastRewind() {
        playerFastRewind(INDEX_FOR_PIP);
    }

    private void playerFastRewind(int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            DtvkitGlueClient.getInstance().request("Player.fastRewind", args);
        } catch (Exception e) {
            Log.e(TAG, "playerFastRewind = " + e.getMessage());
        }
    }

    private boolean playerSetSpeed(float speed) {
        return playerSetSpeed(INDEX_FOR_MAIN, speed);
    }

    private boolean playerPipSetSpeed(float speed) {
        return playerSetSpeed(INDEX_FOR_PIP, speed);
    }

    private boolean playerSetSpeed(int index, float speed) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put((long) (speed * 100.0));
            DtvkitGlueClient.getInstance().request("Player.setPlaySpeed", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "playerSetSpeed = " + e.getMessage());
            return false;
        }
    }

    private boolean playerSeekTo(long positionSecs) {
        return playerSeekTo(INDEX_FOR_MAIN, positionSecs);
    }

    private boolean playerPipSeekTo(long positionSecs) {
        return playerSeekTo(INDEX_FOR_PIP, positionSecs);
    }

    private boolean playerSeekTo(int index, long positionSecs) {
        boolean son = false;
        try {
            if (playerGetSubtitlesOn()) {
                son = true;
                playerSetSubtitlesOn(false);
            }
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(positionSecs);
            DtvkitGlueClient.getInstance().request("Player.seekTo", args);
            //DtvkitGlueClient.getInstance().subtitleSeekReset();
            runOnMainThread(() -> {
                DtvkitTvInputSession mainSession = getMainTunerSession();
                if (mainSession.mView != null) {
                    mainSession.mView.clearSubtitle();
                }
            });
            if (son) {
                playerSetSubtitlesOn(true);
            }
            return true;
        } catch (Exception e) {
            if (son)
                playerSetSubtitlesOn(true);
            Log.e(TAG, "playerSeekTo = " + e.getMessage());
            return false;
        }
    }

    private boolean playerStartTimeshiftRecording() {
        return playerStartTimeshiftRecording(INDEX_FOR_MAIN);
    }

    private boolean playerPipStartTimeshiftRecording() {
        return playerStartTimeshiftRecording(INDEX_FOR_PIP);
    }

    private boolean playerStartTimeshiftRecording(int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            DtvkitGlueClient.getInstance().request("Player.startTimeshiftRecording", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "playerStartTimeshiftRecording = " + e.getMessage());
            return false;
        }
    }

    private boolean playerStopTimeshiftRecording(boolean returnToLive) {
        return playerStopTimeshiftRecording(INDEX_FOR_MAIN, returnToLive);
    }

    private boolean playerPipStopTimeshiftRecording(boolean returnToLive) {
        return playerStopTimeshiftRecording(INDEX_FOR_PIP, returnToLive);
    }

    private boolean playerStopTimeshiftRecording(int index, boolean returnToLive) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(returnToLive);
            DtvkitGlueClient.getInstance().request("Player.stopTimeshiftRecording", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "playerStopTimeshiftRecording = " + e.getMessage());
            return false;
        }
    }

    private boolean playerPlayTimeshiftRecording(boolean startPlaybackPaused, boolean playFromCurrent) {
        return playerPlayTimeshiftRecording(INDEX_FOR_MAIN, startPlaybackPaused, playFromCurrent);
    }

    private boolean playerPipPlayTimeshiftRecording(boolean startPlaybackPaused, boolean playFromCurrent) {
        return playerPlayTimeshiftRecording(INDEX_FOR_PIP, startPlaybackPaused, playFromCurrent);
    }

    private boolean playerPlayTimeshiftRecording(int index, boolean startPlaybackPaused, boolean playFromCurrent) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(startPlaybackPaused);
            args.put(playFromCurrent);
            DtvkitGlueClient.getInstance().request("Player.playTimeshiftRecording", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "PlayerPlayTimeshiftRecording = " + e.getMessage());
            return false;
        }
    }

    private void playerSetRectangle(int x, int y, int width, int height) {
        playerSetRectangle(INDEX_FOR_MAIN, x, y, width, height);
    }

    private void playerSetRectangle(int index, int x, int y, int width, int height) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(x);
            args.put(y);
            args.put(width);
            args.put(height);
            DtvkitGlueClient.getInstance().request("Player.setRectangle", args);
        } catch (Exception e) {
            Log.e(TAG, "playerSetRectangle = " + e.getMessage());
        }
    }

    /*
    * return: selected audio trackId
    * */
    private int playerGetTracks(List<TvTrackInfo> tracks, Channel tunedChannel, boolean detailsAvailable) {
        if (tracks == null) {
            tracks = new ArrayList<>();
        } else {
            tracks.clear();
        }
        tracks.addAll(getVideoTrackInfoList(tunedChannel, detailsAvailable));
        int audioSelectedId = getAudioTrackInfoList(tracks, detailsAvailable);
        tracks.addAll(getSubtitleTrackInfoList());
        return audioSelectedId;
    }

    private List<TvTrackInfo> getVideoTrackInfoList(Channel tunedChannel, boolean detailsAvailable) {
        List<TvTrackInfo> tracks = new ArrayList<>();
        //video tracks
        try {
            TvTrackInfo.Builder track = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, Integer.toString(0));
            Bundle bundle = new Bundle();
            if (detailsAvailable) {
                //get values that need to get from sys and need wait for at least 1s after play
                /*String decodeInfo = mSysSettingManager.getVideoDecodeInfo();
                int videoWidth = mSysSettingManager.parseWidthFromVdecStatus(decodeInfo);
                int videoHeight = mSysSettingManager.parseHeightFromVdecStatus(decodeInfo);
                float videoFrameRate = mSysSettingManager.parseFrameRateStrFromVdecStatus(decodeInfo);
                String videoFrameFormat = mSysSettingManager.parseFrameFormatStrFromDi0Path();*/
                //use dtvkit interface
                int[] videoStatus = playerGetDTVKitVideoSize();
                int videoWidth = videoStatus[0];
                int videoHeight = videoStatus[1];
                float videoFrameRate = (float) videoStatus[2];
                String videoFrameFormat = "";
                if (videoStatus[3] == 1) {//1 means progressive
                    videoFrameFormat = "P";
                } else if (videoStatus[3] == 0) {
                    videoFrameFormat = "I";
                } else {
                    Log.w(TAG, "Invalid videoFrameFormat " + videoStatus[3]);
                    return tracks;
                }
                //set value
                track.setVideoWidth(videoWidth);
                track.setVideoHeight(videoHeight);
                track.setVideoFrameRate(videoFrameRate);
                bundle.putInt(ConstantManager.KEY_TVINPUTINFO_VIDEO_WIDTH, videoWidth);
                bundle.putInt(ConstantManager.KEY_TVINPUTINFO_VIDEO_HEIGHT, videoHeight);
                bundle.putFloat(ConstantManager.KEY_TVINPUTINFO_VIDEO_FRAME_RATE, videoFrameRate);
                bundle.putString(ConstantManager.KEY_TVINPUTINFO_VIDEO_FRAME_FORMAT, videoFrameFormat);
            }

            String videoCodec = tunedChannel != null ? tunedChannel.getVideoCodec() : "";
            TvTrackInfo videoInfo = getVideoStreamInfo();
            if (videoInfo != null) {
                String realCodec = "";
                if (videoInfo.getExtra() != null) {
                    realCodec = videoInfo.getExtra().getString("encoding");
                }
                // Workaround: stream changed, PMT not update cause wrong codec.
                if (!TextUtils.isEmpty(realCodec) && !TextUtils.equals(realCodec, videoCodec)) {
                    Log.d(TAG, "fix video_codec from " + videoCodec + " to " + realCodec);
                    videoCodec = realCodec;
                }
            }
            //set value
            bundle.putString(ConstantManager.KEY_TVINPUTINFO_VIDEO_CODEC, videoCodec != null ? videoCodec : "");
            track.setExtra(bundle);
            //build track
            tracks.add(track.build());
            Log.d(TAG, "getVideoTrackInfoList track bundle = " + bundle.toString());
        } catch (Exception e) {
            Log.e(TAG, "getVideoTrackInfoList Exception = " + e.getMessage());
        }
        return tracks;
    }

    private TvTrackInfo getVideoStreamInfo() {
        TvTrackInfo.Builder track = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, "0");
        try {
            JSONArray args = new JSONArray();
            args.put(0);
            JSONObject stream = DtvkitGlueClient.getInstance()
                    .request("Player.getVideoStreamInfo", args).getJSONObject("data");
            Bundle extra = new Bundle();
            extra.putString("encoding", stream.optString(ConstantManager.KEY_TVINPUTINFO_VIDEO_CODEC));
            track.setExtra(extra);
        } catch (Exception e) {
            Log.e(TAG, "getVideoStreamInfo Exception = " + e.getMessage());
        }
        return track.build();
    }

    //return: selected audio track id
    private int getAudioTrackInfoList(List<TvTrackInfo> tracks, boolean detailsAvailable) {
        int selectedTrackId = 0xFFFF;
        //audio tracks
        try {
            List<TvTrackInfo> audioTracks = new ArrayList<>();
            JSONArray args = new JSONArray();
            args.put(INDEX_FOR_MAIN);
            JSONArray audioStreams = DtvkitGlueClient.getInstance().request("Player.getListOfAudioStreams", args).getJSONArray("data");
            int undefinedIndex = 0;
            for (int i = 0; i < audioStreams.length(); i++) {
                JSONObject audioStream = audioStreams.getJSONObject(i);
                //Log.d(TAG, "getAudioTrackInfoList audioStream = " + audioStream.toString());
                TvTrackInfo.Builder track = new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, Integer.toString(audioStream.getInt("index")));
                Bundle bundle = new Bundle();
                String audioLang = ISO639Data.parse(audioStream.optString("language"));
                if ((mCusFeatureAudioCfg & 0x04) == 0) {
                    //trunk need trans und to "undefined", qaa to "original", qad and nar to "narrative"
                    if (TextUtils.isEmpty(audioLang) || ConstantManager.CONSTANT_UND_FLAG.equals(audioLang)) {
                        audioLang = ConstantManager.CONSTANT_UND_VALUE + ((undefinedIndex > 0) ? undefinedIndex : "");
                        undefinedIndex++;
                    } else if (ConstantManager.CONSTANT_QAA.equalsIgnoreCase(audioLang)) {
                        audioLang = ConstantManager.CONSTANT_ORIGINAL_AUDIO;
                    } else if (ConstantManager.CONSTANT_QAD.equalsIgnoreCase(audioLang)) {
                        audioLang = ConstantManager.CONSTANT_NAR_VALUE;
                    } else if (ConstantManager.CONSTANT_NAR.equalsIgnoreCase(audioLang)) {
                        audioLang = ConstantManager.CONSTANT_NAR_VALUE;
                    }
                }
                track.setLanguage(audioLang);
                track.setDescription(audioStream.optString("message"));

                boolean audioAd = audioStream.optBoolean("ad");
                boolean audioSS = audioStream.optBoolean("ss");
                boolean audioHi = audioStream.optBoolean("hi");
                bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_AUDIO_AD, audioAd);
                bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_AUDIO_SS, audioSS);
                bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_AUDIO_AD_SS, audioAd && audioSS);
                bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_AUDIO_HI, audioHi);

                String codes = audioStream.optString("codec");
                int pid = audioStream.getInt("pid");
                if (!TextUtils.isEmpty(codes)) {
                    bundle.putString(ConstantManager.KEY_AUDIO_CODES_DES, codes);
                    bundle.putString(ConstantManager.KEY_TVINPUTINFO_AUDIO_CODEC, codes);
                } else {
                    bundle.putString(ConstantManager.KEY_TVINPUTINFO_AUDIO_CODEC, "");
                }
                bundle.putInt(ConstantManager.KEY_TRACK_PID, pid);
                if (detailsAvailable) {
                    //can only be gotten after decode finished
                    int sampleRate = getAudioSamplingRateFromAudioPatch(pid);
                    int audioChannel = getAudioChannelFromAudioPatch(pid);
                    bundle.putInt(ConstantManager.KEY_TVINPUTINFO_AUDIO_SAMPLING_RATE, sampleRate);
                    bundle.putInt(ConstantManager.KEY_TVINPUTINFO_AUDIO_CHANNEL, audioChannel);
                    bundle.putInt(ConstantManager.AUDIO_PATCH_COMMAND_GET_AUDIO_CHANNEL_CONFIGURE,
                            getAudioChannelConfigureFromAudioPatch(pid));
                    track.setAudioChannelCount(audioChannel);
                    track.setAudioSampleRate(sampleRate);
                }
                bundle.putInt(ConstantManager.KEY_TVINPUTINFO_AUDIO_INDEX, audioStream.getInt("index"));
                track.setExtra(bundle);
                audioTracks.add(track.build());
                Log.d(TAG, "getAudioTrackInfoList track bundle = " + bundle);
            }
            ConstantManager.ascendTrackInfoOderByPid(audioTracks);
            tracks.addAll(audioTracks);

            for (int i = 0; i < audioStreams.length(); i++) {
                JSONObject audioStream = audioStreams.getJSONObject(i);
                if (audioStream.optBoolean("selected")) {
                    selectedTrackId = audioStream.getInt("index");
                    Log.i(TAG, "selectedAudioTrack index = " + selectedTrackId);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getAudioTrackInfoList Exception = " + e.getMessage());
        }
        return selectedTrackId;
    }

    private int getAudioSamplingRateFromAudioPatch(int audioPid) {
        int result = 0;
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        String temp = audioManager.getParameters(ConstantManager.AUDIO_PATCH_COMMAND_GET_SAMPLING_RATE);
        //sampling rate result "sample_rate=4800"
        //Log.d(TAG, "getAudioSamplingRateFromAudioPatch result = " + temp);
        try {
            if (temp != null) {
                String[] splitInfo = temp.split("=");
                if (splitInfo != null && splitInfo.length == 2 && "sample_rate".equals(splitInfo[0])) {
                    result = Integer.parseInt(splitInfo[1]);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getAudioSamplingRateFromAudioPatch Exception = " + e.getMessage());
        }
        Log.d(TAG, "getAudioSamplingRateFromAudioPatch result = " + result);
        return result;
    }

    private int getAudioChannelFromAudioPatch(int audioPid) {
        int result = 0;
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        String temp = audioManager.getParameters(ConstantManager.AUDIO_PATCH_COMMAND_GET_AUDIO_CHANNEL);
        //channel num result "channel_nums=2"
        //Log.d(TAG, "getAudioChannelFromAudioPatch result = " + temp);
        try {
            if (temp != null) {
                String[] splitInfo = temp.split("=");
                if (splitInfo != null && splitInfo.length == 2 && "channel_nums".equals(splitInfo[0])) {
                    result = Integer.parseInt(splitInfo[1]);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getAudioChannelFromAudioPatch Exception = " + e.getMessage());
        }
        Log.d(TAG, "getAudioChannelFromAudioPatch result = " + result);
        return result;
    }

    private int getAudioChannelConfigureFromAudioPatch(int audioPid) {
        int result = 0;
        //defines in audio patch
        //typedef enum {
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_UNKNOWN = 0,
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_C,/**< Center */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_MONO = TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_C,
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_R,/**< Left and right speakers */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_STEREO = TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_R,
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_C_R,/**< Left, center and right speakers */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_R_S,/**< Left, right and surround speakers */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_C_R_S,/**< Left,center right and surround speakers */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_R_SL_RS,/**< Left, right, surround left and surround right */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_C_R_SL_SR,/**< Left, center, right, surround left and surround right */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_C_R_SL_SR_LFE,/**< Left, center, right, surround left, surround right and lfe*/
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_5_1 = TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_C_R_SL_SR_LFE,
        //   TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_C_R_SL_SR_RL_RR_LFE, /**< Left, center, right, surround left, surround right, rear left, rear right and lfe */
        //    TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_7_1 = TIF_HAL_PLAYBACK_AUDIO_SOURCE_CHANNEL_CONFIGURATION_L_C_R_SL_SR_RL_RR_LFE
        //} TIF_HAL_Playback_AudioSourceChannelConfiguration_t;
        //channel_configuration result = "channel_configuration=1";
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        String temp = audioManager.getParameters(ConstantManager.AUDIO_PATCH_COMMAND_GET_AUDIO_CHANNEL_CONFIGURE);
        //Log.d(TAG, "getAudioChannelConfigureFromAudioPatch need add command in audio patch");
        try {
            if (temp != null) {
                String[] splitInfo = temp.split("=");
                if (splitInfo != null && splitInfo.length == 2 && "channel_configuration".equals(splitInfo[0])) {
                    result = Integer.valueOf(splitInfo[1]);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getAudioChannelConfigureFromAudioPatch Exception = " + e.getMessage());
        }
        Log.d(TAG, "getAudioChannelConfigureFromAudioPatch result = " + result);
        return result;
    }

    private List<TvTrackInfo> getSubtitleTrackInfoList() {
        return getSubtitleTrackInfoList(INDEX_FOR_MAIN);
    }

    private List<TvTrackInfo> getPipSubtitleTrackInfoList() {
        return getSubtitleTrackInfoList(INDEX_FOR_PIP);
    }

    private List<TvTrackInfo> getSubtitleTrackInfoList(int index) {
        List<TvTrackInfo> tracks = new ArrayList<>();
        //subtile tracks
        try {
            List<TvTrackInfo> subTracks = new ArrayList<>();
            JSONArray args = new JSONArray();
            args.put(index);
            JSONArray subtitleStreams = DtvkitGlueClient.getInstance().request("Player.getListOfSubtitleStreams", args).getJSONArray("data");
            int undefinedIndex = 0;
            for (int i = 0; i < subtitleStreams.length(); i++) {
                Bundle bundle = new Bundle();
                JSONObject subtitleStream = subtitleStreams.getJSONObject(i);
                //Log.d(TAG, "getSubtitleTrackInfoList subtitleStream = " + subtitleStream.toString());
                String trackId = null;
                if (subtitleStream.getBoolean("teletext")) {
                    bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_TELETEXT, true);
                    int teletextType = subtitleStream.getInt("teletext_type");
                    if (teletextType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE) {
                        bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_HARD_HEARING, false);
                        trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=1&hearing=0&flag=TTX";
                    } else if (teletextType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE_HARD_HEARING) {
                        bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_HARD_HEARING, true);
                        trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=1&hearing=1&flag=TTX-H.O.H";
                    } else {
                        continue;
                    }
                } else {
                    bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_TELETEXT, false);
                    int subtitletype = subtitleStream.getInt("subtitle_type");
                    bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_HARD_HEARING, false);
                    Boolean isHdSubtitle = false;
                    if (subtitletype >= ConstantManager.ADB_SUBTITLE_TYPE_DVB && subtitletype <= ConstantManager.ADB_SUBTITLE_TYPE_DVB_HD) {
                        if (subtitletype == ConstantManager.ADB_SUBTITLE_TYPE_DVB_HD) {
                            isHdSubtitle = true;
                        }
                        trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=0&hearing=0&flag=none" + "&hd=" + (isHdSubtitle ? "1" : "0");//TYPE_DTV_CC
                    } else if (subtitletype >= ConstantManager.ADB_SUBTITLE_TYPE_DVB_HARD_HEARING && subtitletype <= ConstantManager.ADB_SUBTITLE_TYPE_DVB_HARD_HEARING_HD) {
                        bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_HARD_HEARING, true);
                        if (subtitletype == ConstantManager.ADB_SUBTITLE_TYPE_DVB_HARD_HEARING_HD) {
                            isHdSubtitle = true;
                        }
                        trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=0&hearing=1&flag=H.O.H" + "&hd=" + (isHdSubtitle ? "1" : "0");//TYPE_DTV_CC
                    } else {
                        trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=0&hearing=0&flag=none" + "&hd=" + (isHdSubtitle ? "1" : "0");//TYPE_DTV_CC
                    }
                }
                TvTrackInfo.Builder track = new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, trackId);
                String subLang = ISO639Data.parse(subtitleStream.getString("language"));
                if (TextUtils.isEmpty(subLang) || ConstantManager.CONSTANT_UND_FLAG.equals(subLang)) {
                    subLang = ConstantManager.CONSTANT_UND_VALUE + ((undefinedIndex > 0) ? undefinedIndex : "");
                    undefinedIndex++;
                }
                track.setLanguage(subLang);
                int pid = subtitleStream.getInt("pid");
                bundle.putInt(ConstantManager.KEY_TRACK_PID, pid);
                bundle.putInt(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_INDEX, subtitleStream.getInt("index"));
                track.setExtra(bundle);
                subTracks.add(track.build());
            }
            ConstantManager.ascendTrackInfoOderByPid(subTracks);
            tracks.addAll(subTracks);
            List<TvTrackInfo> teleTracks = new ArrayList<>();
            JSONArray args1 = new JSONArray();
            args1.put(index);
            JSONArray teletextStreams = DtvkitGlueClient.getInstance().request("Player.getListOfTeletextStreams", args1).getJSONArray("data");
            undefinedIndex = 0;
            for (int i = 0; i < teletextStreams.length(); i++) {
                Bundle bundle = new Bundle();
                JSONObject teletextStream = teletextStreams.getJSONObject(i);
                //Log.d(TAG, "getSubtitleTrackInfoList teletextStream = " + teletextStream.toString());
                String trackId = null;
                int teletextType = teletextStream.getInt("teletext_type");
                if (teletextType == 1 || teletextType == 3 || teletextType == 4) {
                    bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_TELETEXT, true);
                    bundle.putBoolean(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_IS_HARD_HEARING, false);
                    trackId = "id=" + teletextStream.getInt("index") + "&" + "type=" + "6" + "&teletext=1&hearing=0&flag=none";//TYPE_DTV_TELETEXT_IMG
                } else {
                    continue;
                }
                TvTrackInfo.Builder track = new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, trackId);
                String teleLang = ISO639Data.parse(teletextStream.getString("language"));
                if (TextUtils.isEmpty(teleLang) || ConstantManager.CONSTANT_UND_FLAG.equals(teleLang)) {
                    teleLang = ConstantManager.CONSTANT_UND_VALUE + ((undefinedIndex > 0) ? undefinedIndex : "");
                    undefinedIndex++;
                }
                track.setLanguage(teleLang);
                int pid = teletextStream.getInt("pid");
                bundle.putInt(ConstantManager.KEY_TRACK_PID, pid);
                bundle.putInt(ConstantManager.KEY_TVINPUTINFO_SUBTITLE_INDEX, teletextStream.getInt("index"));
                track.setExtra(bundle);
                teleTracks.add(track.build());
            }
            ConstantManager.ascendTrackInfoOderByPid(teleTracks);
            tracks.addAll(teleTracks);
        } catch (Exception e) {
            Log.e(TAG, "getSubtitleTrackInfoList Exception = " + e.getMessage());
        }
        return tracks;
    }

    private boolean playerSelectAudioTrack(int index) {
        return playerSelectAudioTrack(INDEX_FOR_MAIN, index);
    }

    private boolean playerPipSelectAudioTrack(int index) {
        return playerSelectAudioTrack(INDEX_FOR_PIP, index);
    }

    private boolean playerSelectAudioTrack(int id, int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(id);
            args.put(index);
            DtvkitGlueClient.getInstance().request("Player.setAudioStream", args);
        } catch (Exception e) {
            Log.e(TAG, "playerSelectAudioTrack = " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean playerSetAudioDescriptionOn(int index, boolean on) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            args.put(on);
            DtvkitGlueClient.getInstance().request("Player.setAudioDescriptionOn", args);
        } catch (Exception e) {
            Log.e(TAG, "playerSetAudioDescriptionOn = " + e.getMessage());
            return false;
        }
        return true;
    }

    public int playerGetAudioStreamType() {
        int type = 0;

        try {
            JSONArray array = new JSONArray();
            array.put(0);
            JSONObject obj = DtvkitGlueClient.getInstance().request("Player.getAudioStreamType", array);
            if (obj != null && obj.getBoolean("accepted")) {
                type = obj.getInt("data");
            }
            Log.d(TAG, "getAudioType type = " + type);
        } catch (Exception e) {
            Log.e(TAG, "getAudioType failed, " + e.getMessage());
        }

        return type;
    }

    private int playerGetAtvMtsMode() {
        int defaultMTSmode = 0;
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request("Atv.getAudioOutmode", new JSONArray());
            if (obj != null && obj.getBoolean("accepted")) {
                defaultMTSmode = obj.getInt("data");
            }
            Log.d(TAG, "playerGetAtvMtsMode mode = " + defaultMTSmode);
        } catch (Exception e) {
            Log.e(TAG, "playerGetAtvMtsMode failed, " + e.getMessage());
        }
        return defaultMTSmode;
    }

    private void playerSetAtvMtsMode(int mode) {
        try {
            JSONArray args = new JSONArray();
            args.put(mode);
            DtvkitGlueClient.getInstance().request("Atv.setAudioOutmode", args);
        } catch (Exception e) {
            Log.e(TAG, "playerSetAtvMtsMode failed, " + e.getMessage());
        }
    }

    private void setATVMtsMode(Context context) {
        int setmode = 0;
        int savedmode = DataProviderManager.getIntValue(context, ConstantManager.ACTION_ATV_SET_MTS_MODE, playerGetAtvMtsMode());
        List<TvMTSSetting.Mode> supportMode = mTvMTSSetting.getAtvMTSMode().getOutList();
        Collections.sort(supportMode);
        for (TvMTSSetting.Mode mode : supportMode) {
            setmode = mode.getValue();
            if (setmode == savedmode) {
                break;
            }
        }
        playerSetAtvMtsMode(setmode);
    }

    private void playerSetAudioStreamType(int index, int type) {
        try {
            JSONArray array = new JSONArray();
            array.put(index);
            array.put(type);
            DtvkitGlueClient.getInstance().request("Player.setAudioStreamType", array);
        } catch (Exception e) {
            Log.e(TAG, "setAudioType failed, " + e.getMessage());
        }
    }

    private boolean playerHasDollyAssociateAudioTrack() {
        boolean result = false;
        List<TvTrackInfo> allAudioTrackList = new ArrayList<>();
        getAudioTrackInfoList(allAudioTrackList, false);
        if (allAudioTrackList.size() < 2) {
            return false;
        }
        Iterator<TvTrackInfo> iterator = allAudioTrackList.iterator();
        while (iterator.hasNext()) {
            TvTrackInfo a = iterator.next();
            if (a != null) {
                Bundle trackBundle = a.getExtra();
                if (trackBundle != null) {
                    String audioCodec = trackBundle.getString(ConstantManager.KEY_TVINPUTINFO_AUDIO_CODEC, null);
                    boolean isAd = trackBundle.getBoolean(ConstantManager.KEY_TVINPUTINFO_AUDIO_AD, false);
                    if (isAd && (TextUtils.equals(audioCodec, "AC3") || TextUtils.equals(audioCodec, "E-AC3"))) {
                        result = true;
                        break;
                    }
                }
            } else {
                continue;
            }
        }
        return result;
    }

    private boolean playerAudioIndexIsAssociate(int index) {
        boolean result = false;
        List<TvTrackInfo> allAudioTrackList = new ArrayList<>();
        getAudioTrackInfoList(allAudioTrackList, false);
        if (allAudioTrackList.size() <= index) {
            return false;
        }
        TvTrackInfo track = allAudioTrackList.get(index);
        if (track != null) {
            Bundle trackBundle = track.getExtra();
            if (trackBundle != null && trackBundle.getBoolean(ConstantManager.KEY_TVINPUTINFO_AUDIO_AD, false)) {
                result = true;
            }
        }
        return result;
    }

    //status has four value: enter exit play
    //notifyCiProfileEvent has been abandoned
    private boolean playerNotifyCiProfileEvent(String action, String ciNumber, String profileName, String profileVersion) {
        boolean result;
        try {
            JSONArray args = new JSONArray();
            args.put(action);
            args.put(ciNumber);
            args.put(profileName);
            args.put(profileVersion);
            DtvkitGlueClient.getInstance().request("Dvb.notifyCiProfileEvent", args);
            result = true;
        } catch (Exception e) {
            Log.e(TAG, "playerNotifyCiProfileEvent = " + e.getMessage());
            result = false;
        }
        return result;
    }

    private boolean doOperatorSearch(int module) {
        boolean result = false;
        Log.d(TAG, "doOperatorSearch: " + module);
        try {
            JSONArray args = new JSONArray();
            args.put(module);
            JSONObject resp = DtvkitGlueClient.getInstance().request("CIPlus.startOperatorProfileSearch", args);
            if (resp != null) {
                result = resp.optBoolean("data");
            }
            if (!result) {
                Log.d(TAG, "doOperatorSearch not ok: " + resp.optString("data", ""));
            }
        } catch (Exception e) {
            Log.e(TAG, "doOperatorSearch Exception = " + e.getMessage());
        }
        return result;
    }

    private int[] playerGetDTVKitVideoSize() {
        return playerGetDTVKitVideoSize(INDEX_FOR_MAIN);
    }

    private int[] playerPipGetDTVKitVideoSize() {
        return playerGetDTVKitVideoSize(INDEX_FOR_PIP);
    }

    private int[] playerGetDTVKitVideoSize(int index) {
        int[] result = {0, 0, 0, 0};
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            JSONObject videoStreams = DtvkitGlueClient.getInstance().request("Player.getDTVKitVideoResolution", args);
            if (videoStreams != null) {
                videoStreams = (JSONObject) videoStreams.get("data");
                if (!(videoStreams == null || videoStreams.length() == 0)) {
                    Log.d(TAG, "playerGetDTVKitVideoSize videoStreams = " + videoStreams.toString());
                    result[0] = (int) videoStreams.get("width");
                    result[1] = (int) videoStreams.get("height");
                    result[2] = (int) videoStreams.get("framerate");
                    result[3] = (int) videoStreams.get("progressive");//1 means progressive
                }
            } else {
                Log.d(TAG, "playerGetDTVKitVideoSize then get null");
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetDTVKitVideoSize = " + e.getMessage());
        }
        return result;
    }

    private String playerGetDTVKitVideoCodec() {
        return playerGetDTVKitVideoCodec(INDEX_FOR_MAIN);
    }

    private String playerPipGetDTVKitVideoCodec() {
        return playerGetDTVKitVideoCodec(INDEX_FOR_PIP);
    }

    private String playerGetDTVKitVideoCodec(int index) {
        String result = "";
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            JSONObject videoStreams = DtvkitGlueClient.getInstance().request("Player.getDTVKitVideoCodec", args);
            if (videoStreams != null) {
                videoStreams = (JSONObject) videoStreams.get("data");
                if (!(videoStreams == null || videoStreams.length() == 0)) {
                    Log.d(TAG, "playerGetDTVKitVideoCodec videoStreams = " + videoStreams);
                    //int videoPid = (int)videoStreams.get("video_pid");
                    result = videoStreams.getString("video_codec");
                }
            } else {
                Log.d(TAG, "playerGetDTVKitVideoCodec then get null");
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetDTVKitVideoCodec = " + e.getMessage());
        }
        return result;
    }

    private int playerGetSelectedSubtitleTrack() {
        return playerGetSelectedSubtitleTrack(INDEX_FOR_MAIN);
    }

    private int playerPipGetSelectedSubtitleTrack() {
        return playerGetSelectedSubtitleTrack(INDEX_FOR_PIP);
    }

    private int playerGetSelectedSubtitleTrack(int id) {
        int index = 0xFFFF;
        try {
            JSONArray args = new JSONArray();
            args.put(id);
            args.put(index);
            JSONArray subtitleStreams = DtvkitGlueClient.getInstance()
                    .request("Player.getListOfSubtitleStreams", args).getJSONArray("data");
            for (int i = 0; i < subtitleStreams.length(); i++) {
                JSONObject subtitleStream = subtitleStreams.getJSONObject(i);
                if (subtitleStream.getBoolean("selected")) {
                    boolean isTele = subtitleStream.getBoolean("teletext");
                    if (isTele) {
                        int teleType = subtitleStream.getInt("teletext_type");
                        if (teleType != ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE
                                && teleType != ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE_HARD_HEARING) {
                            Log.i(TAG, "playerGetSelectedSubtitleTrack skip teletext");
                            continue;
                        }
                    }
                    index = subtitleStream.getInt("index");
                    Log.i(TAG, "playerGetSelectedSubtitleTrack index = " + index);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetSelectedSubtitleTrack = " + e.getMessage());
        }
        Log.i(TAG, "playerGetSelectedSubtitleTrack index = " + index);
        return index;
    }

    private String playerGetSelectedSubtitleTrackId() {
        return playerGetSelectedSubtitleTrackId(INDEX_FOR_MAIN);
    }

    private String playerPipGetSelectedSubtitleTrackId() {
        return playerGetSelectedSubtitleTrackId(INDEX_FOR_PIP);
    }

    private String playerGetSelectedSubtitleTrackId(int index) {
        String trackId = null;
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            JSONArray subtitleStreams = DtvkitGlueClient.getInstance().request("Player.getListOfSubtitleStreams", args).getJSONArray("data");
            for (int i = 0; i < subtitleStreams.length(); i++) {
                JSONObject subtitleStream = subtitleStreams.getJSONObject(i);
                if (subtitleStream.getBoolean("selected")) {
                    boolean isTele = subtitleStream.getBoolean("teletext");
                    if (isTele) {
                        int teletextType = subtitleStream.getInt("teletext_type");
                        if (teletextType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE) {
                            trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=1&hearing=0&flag=TTX";//tele sub
                        } else if (teletextType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE_HARD_HEARING) {
                            trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=1&hearing=1&flag=TTX-H.O.H";//tele sub
                        } else {
                            continue;
                        }
                    } else {
                        int subtitletype = subtitleStream.getInt("subtitle_type");
                        Boolean isHdSubtitle = false;
                        if (subtitletype >= ConstantManager.ADB_SUBTITLE_TYPE_DVB && subtitletype <= ConstantManager.ADB_SUBTITLE_TYPE_DVB_HD) {
                            if (subtitletype == ConstantManager.ADB_SUBTITLE_TYPE_DVB_HD) {
                                isHdSubtitle = true;
                            }
                            trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=0&hearing=0&flag=none" + "&hd=" + (isHdSubtitle ? "1" : "0");//TYPE_DTV_CC
                        } else if (subtitletype >= ConstantManager.ADB_SUBTITLE_TYPE_DVB_HARD_HEARING && subtitletype <= ConstantManager.ADB_SUBTITLE_TYPE_DVB_HARD_HEARING_HD) {
                            if (subtitletype == ConstantManager.ADB_SUBTITLE_TYPE_DVB_HARD_HEARING_HD) {
                                isHdSubtitle = true;
                            }
                            trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=0&hearing=1&flag=H.O.H" + "&hd=" + (isHdSubtitle ? "1" : "0");//TYPE_DTV_CC
                        } else {
                            trackId = "id=" + subtitleStream.getInt("index") + "&" + "type=" + "4" + "&teletext=0&hearing=0&flag=none" + "&hd=" + (isHdSubtitle ? "1" : "0");//TYPE_DTV_CC
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetSelectedSubtitleTrackId = " + e.getMessage());
        }
        Log.i(TAG, "playerGetSelectedTeleTextTrack trackId = " + trackId);
        return trackId;
    }

    private int playerGetSelectedTeleTextTrack() {
        return playerGetSelectedTeleTextTrack(INDEX_FOR_MAIN);
    }

    private int playerPipGetSelectedTeleTextTrack() {
        return playerGetSelectedTeleTextTrack(INDEX_FOR_PIP);
    }

    private int playerGetSelectedTeleTextTrack(int id) {
        int index = 0xFFFF;
        try {
            JSONArray args = new JSONArray();
            args.put(id);
            args.put(index);
            JSONArray teletextStreams = DtvkitGlueClient.getInstance()
                    .request("Player.getListOfTeletextStreams", args).getJSONArray("data");
            for (int i = 0; i < teletextStreams.length(); i++) {
                JSONObject teletextStream = teletextStreams.getJSONObject(i);
                if (teletextStream.getBoolean("selected")) {
                    int teleType = teletextStream.getInt("teletext_type");
                    if (teleType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE
                            || teleType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE_HARD_HEARING) {
                        Log.i(TAG, "playerGetSelectedTeleTextTrack skip tele sub");
                        continue;
                    }
                    index = teletextStream.getInt("index");
                    Log.i(TAG, "playerGetSelectedTeleTextTrack index = " + index);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetSelectedTeleTextTrack Exception = " + e.getMessage());
        }
        return index;
    }

    private String playerGetSelectedTeleTextTrackId() {
        return playerGetSelectedTeleTextTrackId(INDEX_FOR_MAIN);
    }

    private String playerPipGetSelectedTeleTextTrackId() {
        return playerGetSelectedTeleTextTrackId(INDEX_FOR_PIP);
    }

    private String playerGetSelectedTeleTextTrackId(int index) {
        String trackId = null;
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            JSONArray teletextStreams = DtvkitGlueClient.getInstance().request("Player.getListOfTeletextStreams", args).getJSONArray("data");
            for (int i = 0; i < teletextStreams.length(); i++) {
                JSONObject teletextStream = teletextStreams.getJSONObject(i);
                if (teletextStream.getBoolean("selected")) {
                    int teleType = teletextStream.getInt("teletext_type");
                    if (teleType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE || teleType == ConstantManager.ADB_TELETEXT_TYPE_SUBTITLE_HARD_HEARING) {
                        Log.i(TAG, "playerGetSelectedTeleTextTrackId skip tele sub");
                        continue;
                    }
                    trackId = "id=" + teletextStream.getInt("index") + "&type=6" + "&teletext=1&hearing=0&flag=none";//TYPE_DTV_TELETEXT_IMG
                    Log.i(TAG, "playerGetSelectedTeleTextTrackId trackId = " + trackId);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetSelectedTeleTextTrackId Exception = " + e.getMessage());
        }
        Log.i(TAG, "playerGetSelectedTeleTextTrackId trackId = " + trackId);
        return trackId;
    }

    private int playerGetSelectedAudioTrack() {
        return playerGetSelectedAudioTrack(INDEX_FOR_MAIN);
    }

    private int playerPipGetSelectedAudioTrack() {
        return playerGetSelectedAudioTrack(INDEX_FOR_PIP);
    }

    private int playerGetSelectedAudioTrack(int id) {
        int index = 0xFFFF;
        try {
            JSONArray args = new JSONArray();
            args.put(id);
            args.put(index);
            JSONArray audioStreams = DtvkitGlueClient.getInstance().request("Player.getListOfAudioStreams", args).getJSONArray("data");
            for (int i = 0; i < audioStreams.length(); i++) {
                JSONObject audioStream = audioStreams.getJSONObject(i);
                if (audioStream.getBoolean("selected")) {
                    index = audioStream.getInt("index");
                    Log.i(TAG, "playerGetSelectedAudioTrack index = " + index);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "playerGetSelectedAudioTrack = " + e.getMessage());
        }
        return index;
    }

    private boolean playerGetSubtitlesOn() {
        return playerGetSubtitlesOn(INDEX_FOR_MAIN);
    }

    private boolean playerPipGetSubtitlesOn() {
        return playerGetSubtitlesOn(INDEX_FOR_PIP);
    }

    private boolean playerGetSubtitlesOn(int index) {
        boolean on = false;
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            on = DtvkitGlueClient.getInstance().request("Player.getSubtitlesOn", args).getBoolean("data");
        } catch (Exception e) {
            Log.e(TAG, "playerGetSubtitlesOn = " + e.getMessage());
        }
        Log.i(TAG, "playerGetSubtitlesOn on = " + on);
        return on;
    }

    private boolean getCurrentCountryIsNordig() {
        boolean is_nordig = false;

        try {
            JSONArray args = new JSONArray();
            is_nordig = DtvkitGlueClient.getInstance().request("Dvb.isNordigCountry", args).getBoolean("data");
            Log.i(TAG, "getCurrentCountryIsNordig:" + is_nordig);
        } catch (Exception e) {
            Log.e(TAG, "getCurrentCountryIsNordig " + e.getMessage());
        }
        return is_nordig;
    }

    private int getParentalControlAge() {
        int age = 0;

        try {
            JSONArray args = new JSONArray();
            age = DtvkitGlueClient.getInstance()
                    .request("Dvb.getParentalControlAge", args).getInt("data");
            Log.i(TAG, "getParentalControlAge:" + age);
        } catch (Exception e) {
            Log.e(TAG, "getParentalControlAge " + e.getMessage());
        }
        return age;
    }

    private void setParentalControlAge(int age) {

        try {
            JSONArray args = new JSONArray();
            args.put(age);
            DtvkitGlueClient.getInstance().request("Dvb.setParentalControlAge", args);
            Log.i(TAG, "setParentalControlAge:" + age);
        } catch (Exception e) {
            Log.e(TAG, "setParentalControlAge " + e.getMessage());
        }
    }

    private boolean getParentalControlOn() {
        boolean parental_control_enabled = false;
        try {
            JSONArray args = new JSONArray();
            parental_control_enabled = DtvkitGlueClient.getInstance()
                    .request("Dvb.getParentalControlOn", args).getBoolean("data");
            Log.i(TAG, "getParentalControlOn:" + parental_control_enabled);
        } catch (Exception e) {
            Log.e(TAG, "getParentalControlOn " + e.getMessage());
        }
        return parental_control_enabled;
    }

    private void setParentalControlOn(boolean parental_ctrl_enabled) {
        try {
            JSONArray args = new JSONArray();
            args.put(parental_ctrl_enabled);
            DtvkitGlueClient.getInstance().request("Dvb.setParentalControlOn", args);
            Log.i(TAG, "setParentalControlOn:" + parental_ctrl_enabled);
        } catch (Exception e) {
            Log.e(TAG, "setParentalControlOn " + e.getMessage());
        }
    }

    private void requestUnblockContent(int index) {
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            DtvkitGlueClient.getInstance().request("Player.unblock", args);
        } catch (Exception e) {
            Log.e(TAG, "requestUnblockContent " + e.getMessage());
        }
    }

    private void requestATVUnblockContent() {
        try {
            JSONArray args = new JSONArray();
            DtvkitGlueClient.getInstance().request("AtvPlayer.setChannelUnblock", args);
        } catch (Exception e) {
            Log.e(TAG, "requestUnblockContent " + e.getMessage());
        }
    }

    private int getCurrentMinAgeByBlockedRatings() {
        List<TvContentRating> ratingList = mTvInputManager.getBlockedRatings();
        int min_age = 0xFF;
        for (int i = 0; i < ratingList.size(); i++) {
            ContentRatingSystem.Rating rating = mContentRatingsManager.getRating(ratingList.get(i));
            if (rating == null) {
                continue;
            }
            if (rating.getAgeHint() < min_age) {
                mCurrentRatingSystem = ratingList.get(i).getRatingSystem();
                min_age = rating.getAgeHint();
            }
        }
        if (min_age == 0) {
            min_age = 4; // minimum age that DTVKit support
        }
        Log.d(TAG, "min_age=" + min_age + ", RatingSystem=" + mCurrentRatingSystem);
        return min_age;
    }

    private int getCustomFeatureSubtitleCfg() {
        int ret = 0;
        try {
            JSONArray args = new JSONArray();
            JSONObject jsonObject = (JSONObject) (DtvkitGlueClient.getInstance()
                    .request("Dvb.getCustomFeatureCfg", args).get("data"));
            if (jsonObject != null) {
                ret = jsonObject.optInt("subtitle");
            }
        } catch (Exception e) {
        }
        return ret;
    }

    private int getCustomFeatureAudioCfg() {
        int ret = 0;
        try {
            JSONArray args = new JSONArray();
            JSONObject jsonObject = (JSONObject) (DtvkitGlueClient.getInstance()
                    .request("Dvb.getCustomFeatureCfg", args).get("data"));
            if (jsonObject != null) {
                ret = jsonObject.optInt("audio");
            }
        } catch (Exception e) {
        }
        return ret;
    }

    private int getCustomFeatureAutoTimeCfg() {
        int ret = 0;
        try {
            JSONArray args = new JSONArray();
            JSONObject jsonObject = (JSONObject) (DtvkitGlueClient.getInstance()
                    .request("Dvb.getCustomFeatureCfg", args).get("data"));
            if (jsonObject != null) {
                ret = jsonObject.optInt("time");
            }
        } catch (Exception e) {
        }
        return ret;
    }

    private void enableDtvAutoTimeSync(boolean enable) {
        try {
            JSONArray args = new JSONArray();
            args.put(enable);
            DtvkitGlueClient.getInstance().request("Dvb.SetDTVTimeAutoSyncEnable", args);
        } catch (Exception e) {
        }
    }

    private long getDtvUTCTime() {
        long ret = 0;
        try {
            JSONObject time = DtvkitGlueClient.getInstance()
                    .request("Dvb.GetDTVRealUTCTime", new JSONArray());
            if (time != null) {
                ret = time.optLong("data");
            }
        } catch (Exception e) {
        }
        return ret;
    }

    private boolean isSubtitleEnabled() {
        boolean ret = true;
        try {
            JSONArray args = new JSONArray();
            ret = DtvkitGlueClient.getInstance()
                    .request("Player.getSubtitleGlobalOnOff", args).getBoolean("data");
        } catch (Exception e) {
        }
        return ret;
    }

    private void enableSubtitle(boolean enable) {
        try {
            JSONArray args = new JSONArray();
            args.put(enable);
            DtvkitGlueClient.getInstance().request("Player.setSubtitleGlobalOnOff", args);
        } catch (Exception e) {
        }
    }

    private JSONObject playerGetStatus() {
        return playerGetStatus(INDEX_FOR_MAIN);
    }

    private JSONObject playerPipGetStatus() {
        return playerGetStatus(INDEX_FOR_PIP);
    }

    private JSONObject playerGetStatus(int index) {
        JSONObject response = null;
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            response = DtvkitGlueClient.getInstance().request("Player.getStatus", args).getJSONObject("data");
        } catch (Exception e) {
            Log.e(TAG, "playerGetStatus = " + e.getMessage());
        }
        return response;
    }

    private int[] getSignalStatus() {
        int[] result = {0, 0};
        try {
            JSONArray args1 = new JSONArray();
            args1.put(0);
            JSONObject jsonObj = DtvkitGlueClient.getInstance().request("Dvb.getFrontend", args1);
            if (jsonObj != null) {
                Log.d(TAG, "getSignalStatus resultObj:" + jsonObj.toString());
            } else {
                Log.d(TAG, "getSignalStatus then get null");
            }
            JSONObject data = null;
            if (jsonObj != null) {
                data = (JSONObject) jsonObj.get("data");
            }
            if (data == null || data.length() == 0) {
                return result;
            } else {
                result[0] = (int) (data.get("strength"));
                result[1] = (int) (data.get("integrity"));
                return result;
            }
        } catch (Exception e) {
            Log.d(TAG, "getSignalStatus Exception " + e.getMessage() + ", trace=" + e.getStackTrace());
            e.printStackTrace();
        }
        return result;
    }

    private long playerGetElapsed() {
        return playerGetElapsedAndTruncated(playerGetStatus())[0];
    }

    private long[] playerGetElapsedAndTruncated() {
        return playerGetElapsedAndTruncated(playerGetStatus());
    }

    private long[] playerGetElapsedAndTruncated(JSONObject playerStatus) {
        long[] result = {0, 0, 0, 0};
        if (playerStatus != null) {
            try {
                JSONObject content = playerStatus.getJSONObject("content");
                if (content.has("elapsed")) {
                    result[0] = content.getLong("elapsed");
                }
                if (content.has("truncated")) {
                    result[1] = content.getLong("truncated");
                }
                if (content.has("length")) {
                    result[2] = content.getLong("length");
                }
                String playerState = playerStatus.optString("state", "");
                if (!TextUtils.isEmpty(playerState) && "off".equals(playerState)) {
                    result[3] = -1;
                }
            } catch (JSONException e) {
                Log.e(TAG, "playerGetElapsedAndTruncated = " + e.getMessage());
            }
        }
        return result;
    }

    private JSONObject playerGetTimeshiftRecorderStatus() {
        return playerGetTimeshiftRecorderStatus(INDEX_FOR_MAIN);
    }

    private JSONObject playerPipGetTimeshiftRecorderStatus() {
        return playerGetTimeshiftRecorderStatus(INDEX_FOR_PIP);
    }

    private JSONObject playerGetTimeshiftRecorderStatus(int index) {
        JSONObject response = null;
        try {
            JSONArray args = new JSONArray();
            args.put(index);
            response = DtvkitGlueClient.getInstance()
                    .request("Player.getTimeshiftRecorderStatus", args).getJSONObject("data");
        } catch (Exception e) {
            Log.e(TAG, "playerGetTimeshiftRecorderStatus = " + e.getMessage());
        }
        return response;
    }

    private JSONObject playerGetTimeshiftBufferSize() {
        JSONObject response = null;
        try {
            JSONArray args = new JSONArray();
            response = DtvkitGlueClient.getInstance().request("Player.getTimeshiftBufferSize", args);
        } catch (Exception e) {
            Log.e(TAG, "playerGetTimeshiftBufferSize = " + e.getMessage());
        }
        return response;
    }

    private int playerGetTimeshiftBufferSizeMins() {
        int timeshiftBufferSize = 0;
        JSONObject sizeObj = playerGetTimeshiftBufferSize();
        if (sizeObj != null) {
            try {
                if (sizeObj.has("mins"))
                    timeshiftBufferSize = sizeObj.getInt("mins");
            } catch (Exception e) {
                Log.e(TAG, "playerGetTimeshiftBufferSizeMins = " + e.getMessage());
            }
        }
        return timeshiftBufferSize;
    }

    private int playerGetTimeshiftBufferSizeMegabytes() {
        int timeshiftBufferSize = 0;
        JSONObject sizeObj = playerGetTimeshiftBufferSize();
        if (sizeObj != null) {
            try {
                if (sizeObj.has("mbs"))
                    timeshiftBufferSize = sizeObj.getInt("mbs");
            } catch (Exception e) {
                Log.e(TAG, "playerGetTimeshiftBufferSizeMegabytes = " + e.getMessage());
            }
        }
        return timeshiftBufferSize;
    }

    private boolean playerSetTimeshiftBufferSize(int timeshiftBufferSizeMins, int timeshiftBufferSizeMBs) {
        try {
            JSONArray args = new JSONArray();
            args.put(timeshiftBufferSizeMins);
            args.put(timeshiftBufferSizeMBs);
            DtvkitGlueClient.getInstance().request("Player.setTimeshiftBufferSize", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "playerSetTimeshiftBufferSize = " + e.getMessage());
            return false;
        }
    }

    private boolean recordingSetDefaultDisk(String disk_path) {
        try {
            Log.d(TAG, "setDefaultDisk: " + disk_path);
            JSONArray args = new JSONArray();
            args.put(disk_path);
            DtvkitGlueClient.getInstance().request("Recording.setDefaultDisk", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "recordingSetDefaultDisk = " + e.getMessage());
            return false;
        }
    }

    private boolean recordingAddDiskPath(String diskPath) {
        try {
            Log.d(TAG, "addDiskPath: " + diskPath);
            JSONArray args = new JSONArray();
            args.put(diskPath);
            DtvkitGlueClient.getInstance().request("Recording.addDiskPath", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "recordingAddDiskPath = " + e.getMessage());
            return false;
        }
    }

    private String playerGetTimeshiftRecorderState(JSONObject playerTimeshiftRecorderStatus) {
        String state = "off";
        if (playerTimeshiftRecorderStatus != null) {
            try {
                if (playerTimeshiftRecorderStatus.has("timeshiftrecorderstate")) {
                    state = playerTimeshiftRecorderStatus.getString("timeshiftrecorderstate");
                }
            } catch (JSONException e) {
                Log.e(TAG, "playerGetTimeshiftRecorderState = " + e.getMessage());
            }
        }
        return state;
    }

    private void DvbRequestDtvDevice() {
        try {
            JSONArray args = new JSONArray();
            args.put("");
            DtvkitGlueClient.getInstance().request("Dvb.requestDtvDevice", args);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private boolean checkActiveRecording(JSONObject recordingStatus) {
        boolean active = false;

        if (recordingStatus != null) {
            try {
                active = recordingStatus.getBoolean("active");
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return active;
    }

    private JSONObject recordingGetStatus() {
        JSONObject response = null;
        try {
            JSONArray args = new JSONArray();
            response = DtvkitGlueClient.getInstance()
                    .request("Recording.getStatus", args).getJSONObject("data");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return response;
    }

    private boolean recordingStartRecording(String dvbUri, int path, StringBuffer response) {
        try {
            JSONArray args = new JSONArray();
            args.put(dvbUri);
            args.put(path);
            response.insert(0, DtvkitGlueClient.getInstance()
                    .request("Recording.startRecording", args).getString("data"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            response.insert(0, e.getMessage());
            return false;
        }
    }

    private boolean recordingStopRecording(String dvbUri) {
        try {
            JSONArray args = new JSONArray();
            args.put(dvbUri);
            DtvkitGlueClient.getInstance().request("Recording.stopRecording", args);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    private boolean recordingCheckAvailability(String dvbUri) {
        try {
            JSONArray args = new JSONArray();
            args.put(dvbUri);
            DtvkitGlueClient.getInstance().request("Recording.checkAvailability", args);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    private JSONObject recordingTune(String dvbUri, StringBuffer response) {
        JSONObject tune = null;
        try {
            JSONArray args = new JSONArray();
            args.put(dvbUri);
            tune = DtvkitGlueClient.getInstance().request("Recording.tune", args).getJSONObject("data");
            Log.d(TAG, "recordingTune: " + tune.toString());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            response.insert(0, e.getMessage());
        }
        return tune;
    }

    private boolean recordingUnTune(int path) {
        try {
            JSONArray args = new JSONArray();
            args.put(path);
            DtvkitGlueClient.getInstance().request("Recording.unTune", args).get("data");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return true;
    }

    private int getRecordingTunePath(JSONObject tuneStat) {
        int path = -1;
        try {
            path = (tuneStat != null ? tuneStat.getInt("path") : 255);
        } catch (Exception ignored) {
        }
        return path;
    }

    private boolean getRecordingTuned(JSONObject tuneStat) {
        boolean tuned = false;
        try {
            tuned = (tuneStat != null ? tuneStat.getBoolean("tuned") : false);
        } catch (Exception e) {
        }
        return tuned;
    }

    private String getProgramInternalRecordingUri(JSONObject recordingStatus) {
        String uri = "dvb://0000.0000.0000.0000;0000";
        if (recordingStatus != null) {
            try {
                JSONArray activeRecordings = recordingStatus.getJSONArray("activerecordings");
                if (activeRecordings.length() == 1) {
                    uri = activeRecordings.getJSONObject(0).getString("uri");
                }
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return uri;
    }

    private JSONArray recordingGetActiveRecordings() {
        return recordingGetActiveRecordings(recordingGetStatus());
    }

    private JSONArray recordingGetActiveRecordings(JSONObject recordingStatus) {
        JSONArray activeRecordings = null;
        if (recordingStatus != null) {
            try {
                activeRecordings = recordingStatus.getJSONArray("activerecordings");
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return activeRecordings;
    }

    private boolean checkActiveRecordings(JSONArray activeRecordings, Predicate<JSONObject> check) {
        boolean checkedTrue = false;
        if (activeRecordings != null) {
            try {
                for (int i = 0; i < activeRecordings.length(); i++) {
                    if (check.test(activeRecordings.getJSONObject(i))) {
                        checkedTrue = true;
                        break;
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return checkedTrue;
    }

    private Map<Long, String> filterCurrentActiveRecording(JSONArray activeRecordings) {
        if ((null == activeRecordings) || (0 == activeRecordings.length()) || (0 == mCachedRecordingsPrograms.size()) ) {
            Log.d(TAG, "filterCurrentActiveRecording no active recording" );
            return null;
        }
        //filter active Recording
        Map<Long, String> currentRecordingMap = mCachedRecordingsPrograms.entrySet().stream().filter(map->{
            return checkActiveRecordings(activeRecordings,
                (JSONObject activeRecording)->{return TextUtils.equals(activeRecording.optString("uri"), map.getValue());});})
                    .collect(Collectors.toMap(p->p.getKey(), p->p.getValue()));
        Log.d(TAG, "filterCurrentActiveRecording currentRecordingMap : " + currentRecordingMap.toString());
        if (0 == currentRecordingMap.size()) {
            return null;
        }
        return currentRecordingMap;
    }

    private boolean recordingIsRecordingPathActive(JSONObject recordingStatus, int path) {
        boolean active = false;
        if (recordingStatus != null) {
            try {
                JSONArray activeRecordings;
                activeRecordings = recordingStatus.getJSONArray("activerecordings");
                for (int i = 0; i < activeRecordings.length(); i++) {
                    int activePath = activeRecordings.getJSONObject(i).getInt("path");
                    if (activePath == path) {
                        active = true;
                        break;
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return active;
    }


    private int recordingGetNumActiveRecordings() {
        int numRecordings = 0;
        JSONArray activeRecordings = recordingGetActiveRecordings();
        if (activeRecordings != null) {
            numRecordings = activeRecordings.length();
        }
        return numRecordings;
    }

    private int recordingGetNumRecorders() {
        int numberOfRecorders = 0;
        try {
            JSONArray args = new JSONArray();
            numberOfRecorders = DtvkitGlueClient.getInstance().request("Recording.getNumberOfRecorders", args).getInt("data");
            Log.i(TAG, "numberOfRecorders: " + numberOfRecorders);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return numberOfRecorders;
    }

    @SuppressWarnings("unused")
    private JSONArray recordingGetListOfRecordings() {
        JSONArray recordings = null;
        try {
            JSONArray args = new JSONArray();
            recordings = DtvkitGlueClient.getInstance().request("Recording.getListOfRecordings", args).getJSONArray("data");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return recordings;
    }

    private void recordingGetListOfRecordingsAsync(String fromWho) {
        try {
            Log.d(TAG, "recordingGetListOfRecordingsAsync from " + fromWho);
            JSONArray args = new JSONArray();
            DtvkitGlueClient.getInstance().request("Recording.getListOfRecordingsAsync", args);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private boolean recordingRemoveRecording(String uri) {
        try {
            JSONArray args = new JSONArray();
            args.put(uri);
            DtvkitGlueClient.getInstance().request("Recording.removeRecording", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    private int getNumTuners() {
        int numTuners = 0;
        try {
            JSONArray args = new JSONArray();
            args = DtvkitGlueClient.getInstance().request("Dvb.getListOfFrontends", args).getJSONArray("data");
            if (args != null) {
                Log.d(TAG, "getNumTuners args = " + args);
                numTuners = args.length();
            }
            Log.i(TAG, "numTuners: " + numTuners);
        } catch (Exception e) {
            Log.e(TAG, "getNumTuners = " + e.getMessage());
        }
        return numTuners;
    }

    private int getCurrentDvbSource() {
        int source = ParameterManager.SIGNAL_COFDM;
        try {
            JSONObject sourceReq = DtvkitGlueClient.getInstance()
                    .request("Dvb.GetDvbSource", new JSONArray());
            if (sourceReq != null) {
                source = sourceReq.optInt("data");
            }
        } catch (Exception e) {
        }
        return source;
    }

    private boolean isInOpProfileEnv() {
        int env = 0;
        try {
            JSONObject envObject = DtvkitGlueClient.getInstance()
                    .request("Dvb.IsOperatorProfileEnv", new JSONArray());
            if (envObject != null) {
                env = envObject.optInt("data");
            }
        } catch (Exception e) {
        }
        return env == 1;
    }

    private JSONArray recordingGetListOfScheduledRecordings() {
        JSONArray scheduledRecordings = null;
        try {
            JSONArray args = new JSONArray();
            scheduledRecordings = DtvkitGlueClient.getInstance()
                    .request("Recording.getListOfScheduledRecordings", args).getJSONArray("data");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return scheduledRecordings;
    }

    private String getScheduledRecordingUri(JSONObject scheduledRecording) {
        String uri = "dvb://0000.0000.0000;0000";
        if (scheduledRecording != null) {
            try {
                uri = scheduledRecording.getString("uri");
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return uri;
    }

    private boolean isOperatorProfileSupported() {
        boolean support = false;
        try {
            JSONObject jsonObject = DtvkitGlueClient.getInstance()
                    .request("CIPlus.isOperatorProfileSupported", new JSONArray());
            if (jsonObject != null) {
                support = "support".equals(jsonObject.optString("data"));
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return support;
    }

    private boolean recordingRemoveScheduledRecording(String uri) {
        try {
            JSONArray args = new JSONArray();
            args.put(uri);
            DtvkitGlueClient.getInstance().request("Recording.removeScheduledRecording", args);
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    private final ContentObserver mRecordingsContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (!TvContract.RecordedPrograms.CONTENT_URI.equals(uri)) {
                sendGetRecordingListMsg(1, "RecordingsContentObserver",
                        MSG_UPDATE_RECORDING_PROGRAM_DELAY);
            }
        }
    };

    private void onRecordingsChanged(HashMap<Long, String> current) {
        Log.d(TAG, "onRecordingsChanged");
        JSONArray activeRecordings = recordingGetActiveRecordings();
        Set<String> cur = new HashSet<>(current.values());
        Set<String> cache = new HashSet<>(mCachedRecordingsPrograms.values());
        Set<String> actives = new HashSet<>();
        if (activeRecordings != null && activeRecordings.length() > 0) {
            for (int j = 0; j < activeRecordings.length(); j++) {
                try {
                    String uri = activeRecordings.getJSONObject(j).getString("uri");
                    actives.add(uri);
                    Log.d(TAG, "activeRecordings : " + uri);
                } catch (JSONException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }

        Set<String> diffs_ignore_active = new HashSet<>(cache);
        diffs_ignore_active.removeAll(cur);
        diffs_ignore_active.removeAll(actives);
        if (diffs_ignore_active.size() > 0) {
            for (String uri : diffs_ignore_active) {
                Log.d(TAG, "remove invalid recording: " + uri);
                recordingRemoveRecording(uri);
            }
        }
    }

    @SuppressWarnings("unused")
    private Set<String> findDbMismatch(JSONArray recordings) {
        Set<String> ret = new HashSet<>();
        Set<String> uri_from_db = new HashSet<>();
        Set<String> uri_from_dtvkit = new HashSet<>();

        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(TvContract.RecordedPrograms.CONTENT_URI,
                    RecordedProgram.PROJECTION, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    RecordedProgram recordedProgram = RecordedProgram.fromCursor(cursor);
                    uri_from_db.add(recordedProgram.getRecordingDataUri());
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "query Failed = " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        for (int i = 0; i < recordings.length(); i++) {
            try {
                uri_from_dtvkit.add(recordings.getJSONObject(i).getString("uri"));
            } catch (JSONException e) {
                return ret;
            }
        }
        ret.addAll(uri_from_dtvkit);
        ret.removeAll(uri_from_db);
        return ret;
    }

    private TvInputInfo buildTvInputInfo(TvInputHardwareInfo hardwareInfo, int tunerCount,
                                         boolean isPip, Bundle extras) {
        TvInputInfo result = null;
        try {
            String label = null;
            boolean canRecord = true;
            if (isPip) {
                label = "DTVKit-PIP";
                canRecord = false;
            }
            result = new TvInputInfo.Builder(getApplicationContext(), new ComponentName(getApplicationContext(), DtvkitTvInput.class))
                    .setTvInputHardwareInfo(hardwareInfo)
                    .setLabel(label)
                    .setCanRecord(canRecord)
                    .setTunerCount(tunerCount)//will update it in code
                    .setExtras(extras)
                    .build();
        } catch (Exception e) {
            Log.d(TAG, "buildTvInputInfo Exception = " + e.getMessage());
        }
        return result;
    }

    @Override
    public TvInputInfo onHardwareAdded(TvInputHardwareInfo hardwareInfo) {
        Log.d(TAG, "onHardwareAdded ," + "DeviceId :" + hardwareInfo.getDeviceId());
        TvInputInfo tvInputInfo = null;
        switch (hardwareInfo.getDeviceId()) {
            case HARDWARE_MAIN_DEVICE_ID:
                mTvInputHardwareInfo = hardwareInfo;
                tvInputInfo = buildTvInputInfo(hardwareInfo, 1, false, null);
                setInputId(tvInputInfo.getId());
                mTvInputInfo = tvInputInfo;
                break;
            case HARDWARE_PIP_DEVICE_ID:
                if (mTvInputHardwareInfo == null) {
                    Log.d(TAG, "onHardwareAdded pip hardware need to copy main hardware info,"
                            + "but main is not ready");
                    return null;
                }
                if (shouldExposedPipTvInput()) {
                    mPipTvInputHardwareInfo = hardwareInfo;
                    tvInputInfo = buildTvInputInfo(hardwareInfo, 1, true, null);
                    mPipTvInputInfo = tvInputInfo;
                    // always hide PipTvInputInfo as only one source is needed,
                    // app should filter the tvinput.
                    Set<String> hiddenIds =
                            TvInputInfo.TvInputSettings.getHiddenTvInputIds(getApplicationContext(), 0);
                    if (!hiddenIds.contains(mPipTvInputInfo.getId())) {
                        hiddenIds.add(mPipTvInputInfo.getId());
                        TvInputInfo.TvInputSettings.putHiddenTvInputs(getApplicationContext(), hiddenIds, 0);
                    }
                }
                break;
            default:
                break;
        }
        return tvInputInfo;
    }

    @Override
    public String onHardwareRemoved(TvInputHardwareInfo hardwareInfo) {
        Log.d(TAG, "onHardwareRemoved ," + "DeviceId :" + hardwareInfo.getDeviceId());
        String id = null;
        switch (hardwareInfo.getDeviceId()) {
            case HARDWARE_MAIN_DEVICE_ID:
                if (mTvInputInfo != null) {
                    id = mTvInputInfo.getId();
                    mTvInputInfo = null;
                    mTvInputHardwareInfo = null;
                }
                break;
            case HARDWARE_PIP_DEVICE_ID:
                if (mPipTvInputInfo != null) {
                    id = mInputId;
                    mPipTvInputInfo = null;
                    mPipTvInputHardwareInfo = null;
                }
                break;
            default:
                break;
        }
        return id;
    }

/*
    private boolean getFeatureSupportTimeshifting() {
        return !PropSettingManager.getBoolean(PropSettingManager.TIMESHIFT_DISABLE, false);
    }

    private boolean getFeatureCasReady() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.cas.ready", false);
    }

    private boolean getFeatureCompliance() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.compliance", true)
                || getFeatureCasReady();
    }

    private boolean getFeatureDmxLimit() {
        return !PropSettingManager.getBoolean("vendor.tv.dtv.dmx.nolimit", true);
    }

    private boolean getFeatureSupportRecordAVServiceOnly() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.dvr.rec_av_only", true);
    }

    private boolean getFeatureSupportPip() {
        return getFeatureSupportFullPipFccArchitecture() && PropSettingManager.getBoolean(PropSettingManager.ENABLE_PIP_SUPPORT, false);
    }

    private boolean getFeatureSupportFcc() {
        return getFeatureSupportFullPipFccArchitecture() && PropSettingManager.getBoolean(PropSettingManager.ENABLE_FCC_SUPPORT, false);
    }

    private boolean getFeatureSupportFullPipFccArchitecture() {
        return PropSettingManager.getBoolean(PropSettingManager.ENABLE_FULL_PIP_FCC_ARCHITECTURE, false);
    }

    private boolean getFeatureSupportFillSurface() {
        return PropSettingManager.getBoolean(PropSettingManager.ENABLE_FILL_SURFACE, false);
    }

    private boolean getFeatureSupportNewTvApp() {
        return isSdkAfterAndroidQ() && PropSettingManager.getBoolean(PropSettingManager.MATCH_NEW_TV_APP_ENABLE, true);
    }

    private boolean getFeatureSupportCaptioningManager() {
        return !getFeatureSupportNewTvApp() && PropSettingManager.getBoolean(PropSettingManager.CAPTIONING_MANAGER_ENABLE, true);
    }

    private boolean getFeatureSupportManualTimeshift() {
        return getFeatureSupportNewTvApp() || PropSettingManager.getBoolean(PropSettingManager.MANUAL_TIMESHIFT_ENABLE, false);
    }

    private boolean isSdkAfterAndroidQ() {
        return VERSION.SDK_INT > VERSION_CODES.P + 1;
    }
*/

    /*
     * ff000000 represent alpha = 0Xff, R = 0, G = 0, B = O
     */
/*
    private int getFeatureSupportFillSurfaceColor() {
        int result = -1;
        String colorStr = PropSettingManager.getString(PropSettingManager.ENABLE_FILL_SURFACE_COLOR, "0");
        try {
            result = (int) Long.parseLong(colorStr, 16);
        } catch (Exception e) {
            Log.d(TAG, "getFeatureSupportFillSurfaceColor Exception = " + e.getMessage());
        }
        return result;
    }
*/
    private int getNumRecordersLimit() {
        if (numRecorders > 0)
            return FeatureUtil.getFeatureDmxLimit() ? 1 : numRecorders;
        return numRecorders;
    }
/*
    private boolean getFeatureTimeshiftingPriorityHigh() {
        return PropSettingManager.getBoolean("vendor.tv.dtv.tf.priority_high", false);
    }

    private boolean getFeatureSupportHbbTV() {
        boolean isSupport = PropSettingManager.getBoolean("vendor.tv.dtv.hbbtv.enable", false);
        Log.d(TAG, "getFeatureSupportHbbTV: " + isSupport);
        return isSupport;
    }
*/
    private Boolean shouldExposedPipTvInput() {
        if (FeatureUtil.getFeatureSupportPip()) {
            return true;
        }
        if (!FeatureUtil.isSdkAfterAndroidQ()) {
            return false;
        }
        String value = PropSettingManager.getString("ro.vendor.build.fingerprint", "/");
        Log.d(TAG, "platform:" + value);
        String brand = value.split("/")[0];
        if (TextUtils.equals(brand, "Amlogic")) {
            return true;
        }
        return false;
    }

/*
    private final DtvkitGlueClient.SystemControlHandler mSysControlHandler = new DtvkitGlueClient.SystemControlHandler() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public String onReadSysFs(int ftype, String name) {
            String value = null;
            if (mSystemControlManager != null) {
                if (ftype == SYSFS) {
                    value = mSystemControlManager.readSysFs(name);
                } else if (ftype == PROP) {
                    value = mSystemControlManager.getProperty(name);
                } else {
                    //printf error log
                }
           }
           return value;
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onWriteSysFs(int ftype, String name, String cmd) {
            if (mSystemControlManager != null) {
                if (ftype == SYSFS) {
                    mSystemControlManager.writeSysFs(name, cmd);
                } else if (ftype == PROP) {
                    mSystemControlManager.setProperty(name, cmd);
                } else {
                       //printf error log
                }
           }
        }
    }; */

    private void writeSysFs(String path, String value) {
        if (null != mSysSettingManager)
            mSysSettingManager.writeSysFs(path, value);
    }

    private void userDataStatus(boolean status) {
        // AFD is not obtained through subtitleserver.
        // int subFlg = getSubtitleFlag();
        // if (subFlg >= SUBTITLE_CTL_HK_DVB_SUB) {
        //     if (status) {
        //         DtvkitGlueClient.getInstance().openUserData(); //for video afd
        //     } else {
        //         DtvkitGlueClient.getInstance().closeUserData(); //for video afd
        //     }
        // }
    }

    private void checkAndUpdateLcn() {
        Log.d(TAG, "checkAndUpdateLcn");
        JSONArray array = mParameterManager.getConflictLcn();
        if (mParameterManager.needConfirmLcnInformation(array)) {
            Log.d(TAG, "checkAndUpdateLcn elect all default lcn");
            //select default for all conflict items
            mParameterManager.dealRestLcnConflictAsDefault(array, 0);
        }
    }

    private void showAutomaticSearchConfirmDialog(final Context context, boolean isDvbt) {
        if (context == null) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(context, R.layout.confirm_search, null);
        final TextView title = dialogView.findViewById(R.id.dialog_title);
        final Button confirm = dialogView.findViewById(R.id.confirm);
        final Button cancel = dialogView.findViewById(R.id.cancel);

        title.setText(R.string.notice_automatic_scan);
        confirm.requestFocus();
        cancel.setOnClickListener(v -> alert.dismiss());
        confirm.setOnClickListener(v -> {
            alert.dismiss();
            Intent intent = new Intent();
            intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, mInputId);
            intent.setClassName(DataManager.KEY_PACKAGE_NAME, DataManager.KEY_ACTIVITY_DVBT);
            intent.putExtra(DataManager.KEY_IS_DVBT, isDvbt);
            intent.putExtra(DataManager.KEY_START_SCAN_FOR_AUTOMATIC, true);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        });
        //prevent exit key
        alert.setCancelable(false);
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

    public class AutomaticSearchingReceiver extends BroadcastReceiver {
        private static final String TAG = "AutomaticSearchingReceiver";
        //hold wake lock for 5s to ensure the coming recording schedules
        private static final String WAKE_LOCK_NAME = "AutomaticSearchingReceiver";
        private Context mContext;
        private PowerManager.WakeLock mWakeLock = null;
        private PendingIntent mAlarmIntent = null;
        private DtvkitBackGroundSearch dtvkitBgSearch = null;
        private boolean isBgScanning = false;
        private String mUserMsg = "";
        private boolean mNeedSetTargetRegion = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Automatic searching onReceive");
            if (intent == null) return;

            mContext = context;
            String action = intent.getAction();
            Log.d(TAG, "Automatic searching action =" + action);
            if (action.equals(intentAction)) {
                int mode = mParameterManager.getIntParameters(mParameterManager.AUTO_SEARCHING_MODE);
                int dvbSource = getCurrentDvbSource();
                Log.d(TAG, "mode = " + mode + ", signal type= " + dvbSource);
                if (dvbSource != ParameterManager.SIGNAL_COFDM
                        && dvbSource != ParameterManager.SIGNAL_QAM
                        && (dvbSource != ParameterManager.SIGNAL_QPSK || !"TKGS".equals(mDataManager.getStringParameters(ParameterManager.DVBS_OPERATOR_MODE)))) {
                    Log.d(TAG, "only dvbt/c/TKGS will do automatic search.");
                    return;
                }
                JSONArray activeRecordings = recordingGetActiveRecordings();
                if (activeRecordings != null && activeRecordings.length() > 0) {
                    Log.i(TAG,"Recording in progress, give up channel search");
                    return;
                }

                if (intent.getBooleanExtra("tkgs_standby_search", false)) {
                    if (dvbSource != ParameterManager.SIGNAL_QPSK) {
                        Log.i(TAG,"Currently not in DVBS format, finish");
                        return;
                    }
                }
                if (dvbSource != ParameterManager.SIGNAL_QPSK) {
                    //if need to light the screen please run the interface below
                    if (mode == 2) {//operate mode
                        checkSystemWakeUp(context);
                    }
                    //avoid suspend when execute appointed pvr record
                    if (mode == 1) { //standby mode
                        if (isScreenOn(context)) {
                            Log.i(TAG, "Not in sleep mode, skip standby scan.");
                            return;
                        }
                        acquireWakeLock(context);
                    }

                    String strRepetition = intent.getStringExtra("repetition");
                    if (!TextUtils.isEmpty(strRepetition)) {
                        int repetition = Integer.parseInt(strRepetition);
                    }
                    setNextAlarm(context);
                    if (mode == 2) {
                        showAutomaticSearchConfirmDialog(context, dvbSource == ParameterManager.SIGNAL_COFDM);
                        return;
                    }
                } else {
                    if (isScreenOn(context)) {
                        Log.i(TAG, "Not in sleep mode, skip standby scan.");
                        return;
                    }
                    acquireWakeLock(context);
                    setNextAlarm(context);
                }
                final DtvkitBackGroundSearch.BackGroundSearchCallback backGroundSearchCallback = (mess) -> {
                    if (mess != null) {
                        Log.d(TAG, "onMessageCallback " + mess);
                        String status = null;
                        try {
                            status = mess.getString(DtvkitBackGroundSearch.SINGLE_FREQUENCY_STATUS_ITEM);
                        } catch (Exception e) {
                            Log.i(TAG, "onMessageCallback SINGLE_FREQUENCY_STATUS_ITEM Exception " + e.getMessage());
                        }

                        switch (status) {
                            case DtvkitBackGroundSearch.SINGLE_FREQUENCY_STATUS_SEARCH_TERMINATE:
                            case DtvkitBackGroundSearch.SINGLE_FREQUENCY_STATUS_SAVE_FINISH: {
                                Log.d(TAG, "waiting for doing something");
                                if (mode == 1 || intent.getBooleanExtra("tkgs_standby_search", false)) { //standby mode
                                    isBgScanning = false;
                                    releaseWakeLock();
                                }
                                break;
                            }
                            case DtvkitBackGroundSearch.SINGLE_FREQUENCY_TKGS_USER_MSG: {
                                Log.i(TAG,"show TKGS user msg");
                                mUserMsg = mParameterManager.getTKGSUserMessage();
                                break;
                            }
                            case DtvkitBackGroundSearch.SINGLE_FREQUENCY_SET_TARGET_REGION: {
                                mNeedSetTargetRegion = true;
                                break;
                            }
                        }
                    }
                };

                dtvkitBgSearch = new DtvkitBackGroundSearch(context, dvbSource, mInputId, backGroundSearchCallback);
                dtvkitBgSearch.startBackGroundAutoSearch();
                isBgScanning = true;
            }
        }

        public boolean isBackGroundSearching() {
            return isBgScanning;
        }

        public void onReceiveScreenOn() {
            if (isBgScanning && dtvkitBgSearch != null && !mNeedSetTargetRegion) {
                dtvkitBgSearch.handleScreenOn();
            }
            if (!TextUtils.isEmpty(mUserMsg)) {
                showTKGSUserMsgDialog(mContext, mUserMsg);
                mUserMsg = "";
            }
            if (mNeedSetTargetRegion) {
                dtvkitBgSearch.showDialogForSetTargetRegion(mContext);
                mNeedSetTargetRegion = false;
            }
        }

        private void checkSystemWakeUp(Context context) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean isScreenOpen = powerManager.isScreenOn();
            Log.d(TAG, "checkSystemWakeUp isScreenOpen = " + isScreenOpen);
            //Resume if the system is suspending
            if (!isScreenOpen) {
                Log.d(TAG, "checkSystemWakeUp wakeUp the android.");
                long time = SystemClock.uptimeMillis();
                wakeUp(powerManager, time);
            }
        }

        private void wakeUp(PowerManager powerManager, long time) {
            try {
                Class<?> cls = Class.forName("android.os.PowerManager");
                Method method = cls.getMethod("wakeUp", long.class);
                method.invoke(powerManager, time);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "wakeUp Exception = " + e.getMessage());
            }
        }

        private void goToSleep(PowerManager powerManager, long time) {
            try {
                Class<?> cls = Class.forName("android.os.PowerManager");
                Method method = cls.getMethod("goToSleep", long.class);
                method.invoke(powerManager, time);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "goToSleep Exception = " + e.getMessage());
            }
        }

        private synchronized void acquireWakeLock(Context context) {
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
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

        private boolean isScreenOn(Context context) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager.isScreenOn();
        }

        private void setNextAlarm(Context context) {
            Intent intent = new Intent(intentAction);
            if (getCurrentDvbSource()== ParameterManager.SIGNAL_QPSK && "TKGS".equals(mDataManager.getStringParameters(ParameterManager.DVBS_OPERATOR_MODE))) {
                if (mAlarmIntent != null) {
                    mAlarmManager.cancel(mAlarmIntent);
                }
                intent.putExtra("tkgs_standby_search", true);
                mAlarmIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                long current = System.currentTimeMillis();
                long alarmTime = current + TimeUnit.HOURS.toMillis(8);
                Log.d(TAG, "setNextAlarm current =" + new Date(current).toString() + "   alarmTime =" + new Date(alarmTime).toString());
                mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, mAlarmIntent);
            } else {
                String hour = mParameterManager.getStringParameters(mParameterManager.AUTO_SEARCHING_HOUR);
                String minute = mParameterManager.getStringParameters(mParameterManager.AUTO_SEARCHING_MINUTE);
                int mode = mParameterManager.getIntParameters(mParameterManager.AUTO_SEARCHING_MODE);
                int repetition = mParameterManager.getIntParameters(mParameterManager.AUTO_SEARCHING_REPETITION);
                intent.putExtra("mode", mode + "");
                intent.putExtra("repetition", repetition + "");
                if (mAlarmIntent != null) {
                    mAlarmManager.cancel(mAlarmIntent);
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
                    mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime/*wakeAt*/, mAlarmIntent);
                } else if (repetition == 1) {
                    long alarmTime = cal.getTimeInMillis() + AlarmManager.INTERVAL_DAY * 7;
                    Log.d(TAG, "weekly =" + new Date(alarmTime));
                    mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime/*wakeAt*/, mAlarmIntent);
                }
            }
        }
    }

    private void showToast(int stringRes) {
        Toast.makeText(DtvkitTvInput.this, stringRes, Toast.LENGTH_SHORT).show();
    }

    private void showToast(String str) {
        Toast.makeText(DtvkitTvInput.this, str, Toast.LENGTH_SHORT).show();
    }

    private void showOpSearchConfirmDialog(final Context context, int module) {
        if (context == null || module == -1) {
            Log.d(TAG, "showOpSearchConfirmDialog null context or module");
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(context, R.layout.confirm_search, null);
        final TextView title = dialogView.findViewById(R.id.dialog_title);
        final Button confirm = dialogView.findViewById(R.id.confirm);
        final Button cancel = dialogView.findViewById(R.id.cancel);

        title.setText(R.string.ci_profile_update_available);
        confirm.setText(R.string.ci_profile_channel_update_confirm);
        cancel.setText(R.string.ci_profile_channel_update_cancel);
        confirm.requestFocus();
        cancel.setOnClickListener(v -> alert.dismiss());
        confirm.setOnClickListener(v -> {
            confirm.setEnabled(false);
            cancel.setEnabled(false);
            title.setText(R.string.ci_profile_channel_updating);
            doOperatorSearch(module);
            alert.dismiss();
        });
        alert.setOnDismissListener(dialog -> Log.d(TAG, "showOpSearchConfirmDialog onDismiss"));
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

    private void showTKGSUserMsgDialog(final Context context, String msg) {
        if (context == null) {
            Log.d(TAG, "showTuneUpdateTipDialog null context");
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(context, R.layout.confirm_search, null);
        final TextView title = dialogView.findViewById(R.id.dialog_title);
        final Button confirm = dialogView.findViewById(R.id.confirm);
        final Button cancel = dialogView.findViewById(R.id.cancel);
        confirm.setVisibility(View.GONE);
        cancel.setVisibility(View.GONE);
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

        title.setText(msg);
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

    private void setDnsProp() {
        Network defaultNetwork;
        LinkProperties defLinkProperties = null;

        Log.d(TAG, "setDnsProp network change action");
        if (mConnectivityManager != null) {
            defaultNetwork = mConnectivityManager.getActiveNetwork();
            if (defaultNetwork != null) {
                defLinkProperties = mConnectivityManager.getLinkProperties(defaultNetwork);
            }
        }

        if (null != defLinkProperties) {
            Log.e(TAG, "setDnsProp dns size = " + defLinkProperties.getDnsServers().size());
            if (mSystemControlManager != null) {
                for (int i = 0; i < defLinkProperties.getDnsServers().size(); i++) {
                    String dnsPropName = "vendor.tv.dtv.net.dns" + (i + 1);
                    Log.e(TAG, "setDnsProp, dnsPropName = " + dnsPropName + ", dns = "
                            + defLinkProperties.getDnsServers().get(i).getHostAddress());
                    PropSettingManager.setProp(dnsPropName, defLinkProperties.getDnsServers().get(i).getHostAddress());
                }
            }
        }
    }
    private boolean checkEnableCC() {
        boolean enableCC = false;
        int subFlg = getSubtitleFlag();
        if (subFlg >= SUBTITLE_CTL_HK_DVB_SUB) {
            if ((subFlg & SUBTITLE_CTL_HK_CC) == SUBTITLE_CTL_HK_CC) {
                enableCC = true;
            }
        }
        return enableCC || AtvCcTool.getInstance().supportAtv();
    }
    public boolean isSubtitleCTL() {
       return getSubtitleFlag() >= SUBTITLE_CTL_HK_DVB_SUB;
    }

    private void setCIPlusServiceReady(){
        PropSettingManager.setProp("vendor.tv.dtv.ciservice.ready","true");
        Log.d(TAG, "CIPlusService initialize: set vendor.tv.dtv.ciservice.ready=true");
    }

    private void updateTKGSAlarmTime() {
        if (getCurrentDvbSource()== ParameterManager.SIGNAL_QPSK && "TKGS".equals(mDataManager.getStringParameters(ParameterManager.DVBS_OPERATOR_MODE))) {
            Intent intent = new Intent(intentAction);
            intent.putExtra("tkgs_standby_search", true);
            PendingIntent mAlarmIntent = PendingIntent.getBroadcast(DtvkitTvInput.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            if (mAlarmIntent != null) {
                mAlarmManager.cancel(mAlarmIntent);
            }
            long current = System.currentTimeMillis();
            long alarmTime = current + TimeUnit.MINUTES.toMillis(5);
            Log.d(TAG, "current =" + new Date(current).toString() + "   alarmTime =" + new Date(alarmTime).toString());
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, mAlarmIntent);
        }
    }
}
