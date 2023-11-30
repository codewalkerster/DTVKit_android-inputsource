package com.droidlogic.dtvkit.cas.ird;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.droidlogic.dtvkit.cas.CasProvider;
import com.droidlogic.dtvkit.cas.CasProviderManager;
import com.droidlogic.dtvkit.cas.CasUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

public class IrdHandler {
    private static final String TAG = "IrdHandler";
    private final Context mContext;
    private final ServiceHandleManager mServiceHandleManager;
    private final Handler mThreadHandler;
    private CasActionObserver mCasActionObserver;
    protected CasProviderManager mCasProviderManager;
    private final FingerPrintManager mFpManager;
    private final PvrProductManager mPvrProductManager;
    private boolean mCdSnReceived = false;
    private boolean mLoaderStatusReceived = false;
    private int mFsuSystemId = 0;
    private static final int DEFAULT_STATUS_DELAY = 150;
    private static final int DEFAULT_DOWNLOAD_ST_DELAY = 300;
    private static final int PRODUCT_STATUS_UPDATE_DELAY = 500;
    private static final int EMM_COUNT_UPDATE_DELAY = 500;
    private final Bundle mTimerUpdateBundle = new Bundle();

    //request in json
    private static final String CAS_REQUEST_SERVICE_STATUS =
            "{\"InvokeID\":1000}";
    private static final String CAS_REQUEST_LOADER_STATUS =
            "{\"InvokeID\":1001}";
    private static final String CAS_REQUEST_PRODUCT_STATUS =
            "{\"InvokeID\":1002, \"Parameter\":{\"Start\":%d,\"End\":%d}}";
    private static final String CAS_REQUEST_CLIENT_STATUS =
            "{\"InvokeID\":1003}";
    private static final String CAS_REQUEST_CDSN =
            "{\"InvokeID\":1004}";
    private static final String CAS_REQUEST_FSU_CANCEL =
            "{\"InvokeID\": 2000,\"Parameter\":{\"CASystemID\": %d}}";
    private static final String CAS_REQUEST_SERVICE_MONITOR =
            "{\"InvokeID\": 2002,\"Parameter\":{\"ServiceHandle\": %d,\"Enable\":%d}}";
    private static final String CAS_REQUEST_SET_TUNED_INFO =
            "{\"InvokeID\":2003,\"Parameter\":[{\"ServiceID\":%d,\"TransportID\":%d,\"NetworkID\":%d}]}";
    private static final String CAS_REQUEST_FSU_NOTIFY_CAT =
            "{\"InvokeID\":2004}";

    private static final int REQUEST_TYPE_SERVICE_STATUS = 1000;
    private static final int REQUEST_TYPE_LOADER_STATUS = 1001;
    private static final int REQUEST_TYPE_PRODUCT_STATUS = 1002;
    private static final int REQUEST_TYPE_CLIENT_STATUS = 1003;
    private static final int REQUEST_TYPE_CDSN = 1004;
    private static final int REQUEST_TYPE_FSU_CANCEL = 2000;
    private static final int REQUEST_TYPE_SERVICE_MONITOR = 2002;
    private static final int REQUEST_TYPE_SET_TUNED_INFO = 2003;
    private static final int REQUEST_TYPE_FSU_NOTIFY_CAT = 2004;

    private static final int CAS_MSG_TYPE_BANNER = 0;
    private static final int CAS_MSG_TYPE_TEXT = 1;
    private static final int CAS_MSG_TYPE_ATTRIBUTE = 2;
    //private static final int CAS_MSG_TYPE_FINGERPRINT = 3;
    private static final int CAS_MSG_TYPE_PVR = 4;
    private static final int CAS_MSG_TYPE_FSU_INFO = 5;
    private static final int CAS_MSG_TYPE_FSU_TUNE = 6;
    private static final int CAS_MSG_TYPE_FSU_RET = 7;
    private static final int CAS_MSG_TYPE_PARENTAL_PIN = 8;
    private static final int CAS_MSG_TYPE_ECM_MONITOR_STATUS = 15;
    private static final int CAS_MSG_TYPE_EMM_MONITOR_STATUS = 16;
    private static final int CAS_MSG_TYPE_SECURE_DL_STATUS = 17;
    private static final int CAS_MSG_TYPE_FLEX_STATUS = 18;
    private static final int CAS_MSG_TYPE_SECTION_COUNT = 19;
    private static final int CAS_MSG_TYPE_HOMING_CHANNEL = 20;
    private static final int CAS_MSG_TYPE_TMS_CHANGED = 21;
    private static final int CAS_MSG_TYPE_SO_USER = 22;

    private static final int CAS_MSG_ATTR_TYPE_NORMAL = 0;
    private static final int CAS_MSG_ATTR_TYPE_FORCE = 1;
    private static final int CAS_MSG_ATTR_TYPE_FP = 2;

    public static String ACTIONS_START_MONITORING = "start_monitoring";
    public static String ACTIONS_STOP_MONITORING = "stop_monitoring";
    public static String ACTIONS_CANCEL_FSU = "cancel_fsu";

    private static final int THREAD_MSG_UPDATE_DL_SECURE_STATE = CasUtils.MSG_IRD_CAS_START;
    private static final int THREAD_MSG_UPDATE_DL_FLE_XI_STATE = CasUtils.MSG_IRD_CAS_START + 1;
    private static final int THREAD_MSG_UPDATE_EMM_COUNT = CasUtils.MSG_IRD_CAS_START + 2;

    public IrdHandler(@NonNull Context context,
                      @NonNull Handler handler) {
        mContext = context;
        mServiceHandleManager = new ServiceHandleManager();
        mThreadHandler = handler;
        mCasProviderManager = new CasProviderManager();
        mFpManager = new FingerPrintManager(context, handler, mCasProviderManager);
        mPvrProductManager = new PvrProductManager();
        startMonitorActions(mContext);
        mThreadHandler.post(() -> mCasProviderManager.cleanForceMessagesOnStart(mContext));
        getCasInitialStatus();
    }

    public void onPlayStatusStart(@NonNull String dvbUri) {
        refreshStatusOnPlayingStart();
        String[] ids = dvbUri.replace("dvb://", "").split("\\.");
        if (ids.length > 3) {
            try {
                JSONObject args = new JSONObject();
                int on_id = Integer.parseInt(ids[0], 16);
                int ts_id = Integer.parseInt(ids[1], 16);
                int sid = Integer.parseInt(ids[2], 16);
                args.put("on_id", on_id);
                args.put("ts_id", ts_id);
                args.put("sid", sid);
                if (mThreadHandler != null) {
                    mThreadHandler.post(() -> casRequest(REQUEST_TYPE_SET_TUNED_INFO, args));
                }
            } catch (Exception ignored) {}
        }
    }

    public void onPlayingStopped() {
        //fake an fp message to clear fp cache
        try {
            JSONObject fp = new JSONObject();
            fp.put("enhanced", true);
            fp.put("start", false);
            mFpManager.onFpAdded(fp);
        } catch (Exception ignore) {};
    }

    public void threadHandleCasMessages(@NonNull Message msg) {
        if (msg.what == THREAD_MSG_UPDATE_EMM_COUNT) {
            String id = "cas.irdeto.info.client_status.Section";
            String value;
            synchronized (this) {
                value = mTimerUpdateBundle.getString("section", "");
                if (TextUtils.isEmpty(value))
                    value = (String)msg.obj;
                else
                    mTimerUpdateBundle.remove("section");
            }
            if (value != null)
                mCasProviderManager.putCasSettingsValue(mContext, id,
                        value.replace("emm_count", "EMM").replace("ecm_count", "ECM"));
        } else if (msg.what == THREAD_MSG_UPDATE_DL_SECURE_STATE) {
            String id = "cas.irdeto.info.client_status.SecureCore";
            String value;
            synchronized (this) {
                value = mTimerUpdateBundle.getString("secure_download", "");
                if (TextUtils.isEmpty(value))
                    value = (String)msg.obj;
                else
                    mTimerUpdateBundle.remove("secure_download");
            }
            if (value != null)
                mCasProviderManager.putCasSettingsValue(mContext, id, value);
        } else if (msg.what == THREAD_MSG_UPDATE_DL_FLE_XI_STATE) {
            String id = "cas.irdeto.info.client_status.FlexiCore";
            String value;
            synchronized (this) {
                value = mTimerUpdateBundle.getString("fle_xi_download", "");
                if (TextUtils.isEmpty(value))
                    value = (String)msg.obj;
                else
                    mTimerUpdateBundle.remove("fle_xi_download");
            }
            if (value != null)
                mCasProviderManager.putCasSettingsValue(mContext, id, value);
        }
    }

    public void handleCasProvider(@NonNull JSONObject casEvent) {
        Log.d(TAG, "handleCasProvider: " + casEvent);
        if (mThreadHandler == null || mCasProviderManager == null)
            return;
        int type = casEvent.optInt("msg_type", 0);
        JSONObject casRequestValue = casEvent.optJSONObject("status");
        if (type >= 1000 && casRequestValue == null) {
            //ioctl type
            return;
        }
        switch (type) {
            case CAS_MSG_TYPE_BANNER: {
                String id = "cas.irdeto.info.live_status";
                String value = casEvent.optString("status_msg", "");
                String pathType = casEvent.optString("service_type", "");
                mThreadHandler.removeCallbacks(mServiceStatusUpdater);
                mThreadHandler.postDelayed(mServiceStatusUpdater, DEFAULT_STATUS_DELAY);
                if (!TextUtils.isEmpty(pathType) && !TextUtils.isEmpty(value)) {
                    if (!"live_status".equalsIgnoreCase(pathType)) {
                        id = null;
                    }
                    if (CasIrdErrorCode.isEmmMsg(value)) {
                        if (CasIrdErrorCode.isFactoryRestMsg(value)) {
                            mThreadHandler.removeCallbacks(mProductStatusUpdater);
                            mThreadHandler.removeCallbacks(mClientStatusUpdater);
                            mThreadHandler.postDelayed(mProductStatusUpdater, DEFAULT_STATUS_DELAY);
                            mThreadHandler.postDelayed(mClientStatusUpdater, DEFAULT_STATUS_DELAY);
                        } else if (CasIrdErrorCode.isProductMsg(value)) {
                            if (!mThreadHandler.hasCallbacks(mProductStatusUpdater)) {
                                mThreadHandler.postDelayed(mProductStatusUpdater, PRODUCT_STATUS_UPDATE_DELAY);
                            }
                        } else if (CasIrdErrorCode.isClientMsg(value)) {
                            mThreadHandler.removeCallbacks(mClientStatusUpdater);
                            mThreadHandler.postDelayed(mClientStatusUpdater, DEFAULT_STATUS_DELAY);
                        }
                    }
                    if (CasIrdErrorCode.isEcmMsg(value)) {
                        String finalId = id;
                        mThreadHandler.removeCallbacks(mProductStatusUpdater);
                        mThreadHandler.post(mProductStatusUpdater);
                        mThreadHandler.postDelayed(() -> {
                            if (!TextUtils.isEmpty(finalId)) {
                                mCasProviderManager.putCasSettingsValue(mContext, finalId, value);
                            }
                        }, 50);
                    }
                }
                break;
            }
            case CAS_MSG_TYPE_TEXT: {
                boolean force = casEvent.optBoolean("force", false);
                String content = casEvent.optString("content", "");
                mThreadHandler.post(() ->
                        mCasProviderManager.putScreenMessage(
                                mContext,
                                force ? CAS_MSG_ATTR_TYPE_FORCE : CAS_MSG_ATTR_TYPE_NORMAL,
                                0, 0, 0, 0, 0, 0,
                                0, 0,
                                content.getBytes(),
                                CasUtils.getInstance()
                                        .createIrdFpOptions(0, 0, 0,
                                                0, 0, 0, 0),
                                0));
                break;
            }
            case CAS_MSG_TYPE_ATTRIBUTE: {
                int messageType = casEvent.optInt("message_type");
                if (messageType == CAS_MSG_ATTR_TYPE_FP) {
                    mFpManager.onFpAdded(casEvent);
                } else {
                    int fp_type = casEvent.optInt("fp_type", 0);
                    boolean enhanced = casEvent.optBoolean("enhanced", false);
                    int duration = casEvent.optInt("duration", 0);
                    boolean flashing = casEvent.optBoolean("flash", false);
                    int banner = casEvent.optInt("banner", 0);
                    int coverage = casEvent.optInt("coverage_percent", 0);
                    int scroll = casEvent.optInt("scrolling", 0);
                    String content = casEvent.optString("content", "");
                    boolean start = casEvent.optBoolean("start", true);
                    if (start && !TextUtils.isEmpty(content) && duration == 0) {
                        duration = Integer.MAX_VALUE;
                    }
                    byte[] data = CasUtils.getInstance().hexStrToBytes(content);
                    byte[] options = CasUtils.getInstance().createIrdFpOptions(
                            0, 0, 0, 0, 0, 0, 0);//default
                    if (!TextUtils.isEmpty(content) && messageType == CAS_MSG_ATTR_TYPE_FORCE) {
                        options = CasUtils.getInstance().createIrdFpOptions(
                                casEvent.optInt("location_x", 0),
                                casEvent.optInt("location_y", 0),
                                casEvent.optInt("bg_transparency", 0),
                                casEvent.optInt("bg_colour", 0),
                                casEvent.optInt("font_transparency", 0),
                                casEvent.optInt("font_colour", 0),
                                casEvent.optInt("font_type", 0)
                        );
                    }
                    byte[] finalOptions = options;
                    int finalDuration = duration;
                    mThreadHandler.post(() -> mCasProviderManager.putScreenMessage(
                            mContext, messageType, 1,
                            enhanced ? 1 : 0, finalDuration,
                            flashing ? 0 : 1, banner, coverage, fp_type, scroll,
                            data,
                            finalOptions,
                            0));
                }
                break;
            }
            case CAS_MSG_TYPE_PVR: {
                String id = null;
                boolean isPlayback = false;//true: PlayBack, false: Recording
                String value = casEvent.optString("status_msg", "");
                String pathType = casEvent.optString("service_type", "");
                mThreadHandler.removeCallbacks(mServiceStatusUpdater);
                mThreadHandler.postDelayed(mServiceStatusUpdater, 0);
                if (!TextUtils.isEmpty(pathType) && !TextUtils.isEmpty(value)) {
                    if ("record_status".equalsIgnoreCase(pathType)) {
                        id = "cas.irdeto.info.record_status";
                    } else if ("playback_status".equalsIgnoreCase(pathType)) {
                        id = "cas.irdeto.info.playback_status";
                        isPlayback = true;
                    }
                    String finalId = id;
                    boolean finalIsPlayback = isPlayback;
                    mThreadHandler.post(() -> {
                        if (!TextUtils.isEmpty(finalId)) {
                            mCasProviderManager.putCasSettingsValue(mContext, finalId, value);
                            if (!finalIsPlayback) {
                                mCasProviderManager.putCasSettingsValue(mContext,
                                        "cas.irdeto.info.timeshiftRecord_status",
                                        value);
                            }
                        }
                    });
                }
                break;
            }
            case CAS_MSG_TYPE_FSU_INFO:
            case CAS_MSG_TYPE_FSU_TUNE:
            case CAS_MSG_TYPE_FSU_RET: {
                int action = type - CAS_MSG_TYPE_FSU_INFO;
                String id = "cas.irdeto.info.fsu";
                JSONObject fsu = new JSONObject();
                try {
                    fsu.put("action_type", action);
                    if (type == CAS_MSG_TYPE_FSU_INFO) {
                        mFsuSystemId = casEvent.optInt("ca_system_id", 0);
                        fsu.put("forced_update",
                                casEvent.optBoolean("forced_update") ? 1 : 0);
                        fsu.put("duration_before_tuning",
                                casEvent.optInt("dur_before_Tune", 1));
                        fsu.put("duration_stay_on_fsu",
                                casEvent.optInt("dur_stay_on", 1));
                        fsu.put("repetition_rate_in_seconds",
                                casEvent.optInt("repetition_rate", 1));
                    } else if (type == CAS_MSG_TYPE_FSU_TUNE) {
                        int on_id = casEvent.optInt("network_id", 0);
                        int ts_id = casEvent.optInt("transport_id", 0);
                        int sid = casEvent.optInt("service_id", 0);
                        if (!fsuNeedChangeStream(on_id, ts_id, sid)) {
                            //put invalid uri for launcher to skip tune
                            fsu.put("original_network_id", 65535);
                            fsu.put("transport_id", 65535);
                            fsu.put("service_id", 65535);
                            mThreadHandler.postDelayed(this::sendFsuNotifyInLooper, DEFAULT_STATUS_DELAY);
                        } else {
                            fsu.put("original_network_id", on_id);
                            fsu.put("transport_id", ts_id);
                            fsu.put("service_id", sid);
                        }
                    } else {
                        mFsuSystemId = 0;//leave fsu
                        fsu.put("result", casEvent.optString("result"));
                    }
                    mThreadHandler.post(() ->
                            mCasProviderManager.putCasSettingsValue(mContext, id, fsu.toString()));
                    if (type == CAS_MSG_TYPE_FSU_RET) {
                        mThreadHandler.removeCallbacks(mClientStatusUpdater);
                        mThreadHandler.postDelayed(mClientStatusUpdater, DEFAULT_STATUS_DELAY);
                    }
                } catch (Exception ignored) {}
                break;
            }
            case CAS_MSG_TYPE_PARENTAL_PIN: {
                String id = "cas.irdeto.info.parental_pin";
                mThreadHandler.post(() ->
                        mCasProviderManager.putCasSettingsValue(mContext, id, casEvent.toString()));
                break;
            }
            case CAS_MSG_TYPE_ECM_MONITOR_STATUS:
            case CAS_MSG_TYPE_EMM_MONITOR_STATUS: {
                mThreadHandler.removeCallbacks(mServiceStatusUpdater);
                mThreadHandler.postDelayed(mServiceStatusUpdater, DEFAULT_STATUS_DELAY);
                String emType = (type == CAS_MSG_TYPE_ECM_MONITOR_STATUS) ? "ecm" : "emm";
                String id = "cas.irdeto.info.service_status." + emType + ".monitoring";
                mThreadHandler.post(() -> {
                    mServiceHandleManager.parseMonitoringStatus(emType, casEvent);
                    mCasProviderManager.putCasSettingsValue(mContext, id,
                            mServiceHandleManager.createMonitoringProvider(emType));
                });
                break;
            }
            case CAS_MSG_TYPE_SECURE_DL_STATUS:
            case CAS_MSG_TYPE_FLEX_STATUS: {
                int threadMsg = THREAD_MSG_UPDATE_DL_SECURE_STATE +
                        type - CAS_MSG_TYPE_SECURE_DL_STATUS;
                String updateKey = (threadMsg == THREAD_MSG_UPDATE_DL_SECURE_STATE) ?
                        "secure_download" : "fle_xi_download";
                String value = String.format(Locale.getDefault(),
                        "{\"LoadStatus\":\"%s\",\"DownloadStatus\":\"%s\",\"message\":%d}",
                        casEvent.optString("load_status", ""),
                        casEvent.optString("download_status", ""),
                        casEvent.optInt("message", 0));
                if (mThreadHandler.hasMessages(threadMsg)) {
                    synchronized (this) {
                        mTimerUpdateBundle.putString(updateKey, value);
                    }
                } else {
                    Message msg = mThreadHandler.obtainMessage(threadMsg);
                    msg.obj = value;
                    mThreadHandler.sendMessageDelayed(msg, DEFAULT_DOWNLOAD_ST_DELAY);
                }
                break;
            }
            case CAS_MSG_TYPE_SECTION_COUNT: {
                String value = casEvent.optString("section_count", "");
                int emmType = casEvent.optInt("type", 0);
                String emmMsg = casEvent.optString("status_msg");
                if (emmType == 1 && CasIrdErrorCode.isProductMsg(emmMsg)) {
                    if (!mThreadHandler.hasCallbacks(mProductStatusUpdater)) {
                        mThreadHandler.postDelayed(mProductStatusUpdater,
                                PRODUCT_STATUS_UPDATE_DELAY);
                    }
                }
                if (mThreadHandler.hasMessages(THREAD_MSG_UPDATE_EMM_COUNT)) {
                    synchronized (this) {
                        mTimerUpdateBundle.putString("section", value);
                    }
                } else {
                    Message msg = mThreadHandler.obtainMessage(THREAD_MSG_UPDATE_EMM_COUNT);
                    msg.obj = value;
                    mThreadHandler.sendMessageDelayed(msg, EMM_COUNT_UPDATE_DELAY);
                }
                break;
            }
            case CAS_MSG_TYPE_HOMING_CHANNEL: {
                Log.d(TAG, "Not support homing channel");
                break;
            }
            case CAS_MSG_TYPE_TMS_CHANGED: {
                mThreadHandler.removeCallbacks(mClientStatusUpdater);
                mThreadHandler.postDelayed(mClientStatusUpdater, DEFAULT_STATUS_DELAY);
                break;
            }
            case CAS_MSG_TYPE_SO_USER: {
                String command = casEvent.optString("command_data", "");
                mThreadHandler.post(() -> mCasProviderManager.putSoUserMessage(
                        mContext,
                        CasUtils.getInstance().hexStrToBytes(command), 0));
                break;
            }
            case REQUEST_TYPE_SERVICE_STATUS: {
                String idPrefix = "cas.irdeto.info.service_status.";
                String casRequestResult = casRequestValue.optString("Status", "fail");
                if ("ok".equalsIgnoreCase(casRequestResult)) {
                    JSONArray serviceStatus = casRequestValue.optJSONArray("Result");
                    if (serviceStatus != null && serviceStatus.length() > 0) {
                        mThreadHandler.post(() -> {
                            String emmValue = "";
                            String ecmValue = "[]";
                            String playbackValue = "";
                            try {
                                JSONArray ecmStatus = new JSONArray();
                                for (int i = 0; i < serviceStatus.length(); i ++) {
                                    JSONObject service = (JSONObject) serviceStatus.get(i);
                                    String serviceType = service.optString("Type", "");
                                    switch (serviceType) {
                                        case "emm":
                                        case "EMM":{
                                            emmValue = service.toString();
                                            break;
                                        }
                                        case "ecm":
                                        case "ECM":
                                        case "record":
                                        case "RECORD":{
                                            ecmStatus.put(service);
                                            break;
                                        }
                                        case "playback":
                                        case "PLAYBACK": {
                                            playbackValue = service.toString();
                                            break;
                                        }
                                        default:
                                            break;
                                    }
                                    ecmValue = ecmStatus.toString();
                                }
                            } catch (Exception ignored) {}
                            mCasProviderManager.putCasSettingsValue(mContext, idPrefix + "emm", emmValue);
                            mCasProviderManager.putCasSettingsValue(mContext, idPrefix + "ecm", ecmValue);
                            mCasProviderManager.putCasSettingsValue(mContext, idPrefix + "playback", playbackValue);
                            mServiceHandleManager.parseFromServiceStatus(serviceStatus);
                        });
                    }
                }
                break;
            }
            case REQUEST_TYPE_LOADER_STATUS: {
                String idPrefix = "cas.irdeto.info.loader_status.";
                String casRequestResult = casRequestValue.optString("Status", "fail");
                if ("ok".equalsIgnoreCase(casRequestResult)) {
                    JSONObject loaderStatus = casRequestValue.optJSONObject("Result");
                    if (loaderStatus != null) {
                        mLoaderStatusReceived = true;
                        mThreadHandler.post(() -> {
                            for (Iterator<String> it = loaderStatus.keys(); it.hasNext(); ) {
                                String key = it.next();
                                mCasProviderManager.putCasSettingsValue(mContext,
                                        idPrefix + key,
                                        "" + loaderStatus.optInt(key, -1));
                            }
                            mCasProviderManager.putCasSettingsValue(mContext,
                                    idPrefix + "firmware_version",
                                    "12345");//ird android has no such key, fake it
                            mCasProviderManager.putCasSettingsValue(mContext,
                                    idPrefix + "sn",
                                    "1234567890");//ird android has no such key, fake it
                        });
                    }
                }
                break;
            }
            case REQUEST_TYPE_PRODUCT_STATUS: {
                String idPrefix = "cas.irdeto.info.product_status";
                String casRequestResult = casRequestValue.optString("Status", "fail");
                JSONArray productArray = null;
                if ("ok".equalsIgnoreCase(casRequestResult)) {
                    JSONObject productObject = casRequestValue.optJSONObject("Result");
                    if (productObject != null) {
                        productArray = productObject.optJSONArray("Status");
                        mPvrProductManager.parseProducts(productArray);
                    }
                }
                if (productArray != null) {
                    JSONArray finalProductArray = productArray;
                    mThreadHandler.post(() -> mCasProviderManager.putCasSettingsValue(mContext,
                            idPrefix, finalProductArray.toString()));
                }
                break;
            }
            case REQUEST_TYPE_CDSN: {
                String idPrefix = "cas.irdeto.info.cdsn";
                String casRequestResult = casRequestValue.optString("Status", "fail");
                if ("ok".equalsIgnoreCase(casRequestResult)) {
                    mCdSnReceived = true;
                    long cdsn = casRequestValue.optLong("Result", 0);
                    mThreadHandler.post(() -> mCasProviderManager.putCasSettingsValue(mContext,
                            idPrefix, String.valueOf(cdsn)));
                }
                break;
            }
            case REQUEST_TYPE_CLIENT_STATUS: {
                String idPrefix = "cas.irdeto.info.client_status.";
                String casRequestResult = casRequestValue.optString("Status", "fail");
                if ("ok".equalsIgnoreCase(casRequestResult)) {
                    JSONObject clientStatus = casRequestValue.optJSONObject("Result");
                    if (clientStatus != null) {
                        mThreadHandler.post(() -> {
                            for (Iterator<String> it = clientStatus.keys(); it.hasNext(); ) {
                                String key = it.next();
                                Object value = clientStatus.opt(key);
                                if (value != null) {
                                    mCasProviderManager.putCasSettingsValue(mContext,
                                            idPrefix + key,
                                            "" + value);
                                }
                            }
                        });
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    private void startMonitorActions(@NonNull Context context) {
        mCasActionObserver = new CasActionObserver(mThreadHandler);
        context.getContentResolver().registerContentObserver(
                Uri.parse(CasProvider.CONTENT_URI + CasProvider.TABLE_ACTIONS),
                true, mCasActionObserver);
    }

    public void destroy(@NonNull Context context) {
        if (mCasActionObserver != null) {
            context.getContentResolver().unregisterContentObserver(mCasActionObserver);
        }
    }

    public class CasActionObserver extends ContentObserver {
        public CasActionObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri != null) {
                String fragment = uri.getFragment();
                String updatedId = null;
                if (fragment != null) {
                    String[] update = fragment.split(" value=");
                    updatedId = update[0].replace("_id=", "");
                }
                if (ACTIONS_START_MONITORING.equals(updatedId)) {
                    mThreadHandler.postDelayed(() -> sendServiceMonitoringInLooper(true), 50);
                } else if (ACTIONS_STOP_MONITORING.equals(updatedId)) {
                    mThreadHandler.postDelayed(() -> {
                        sendServiceMonitoringInLooper(false);
                        mCasProviderManager.putCasSettingsValue(mContext,
                                "cas.irdeto.info.service_status.emm.monitoring", "");
                        mCasProviderManager.putCasSettingsValue(mContext,
                                "cas.irdeto.info.service_status.ecm.monitoring", "");
                    }, 50);
                } else if (ACTIONS_CANCEL_FSU.equals(updatedId)) {
                    mThreadHandler.post(IrdHandler.this::sendFsuCancelInLooper);
                }
            }
        }
    }

    private void sendServiceMonitoringInLooper(boolean start) {
        int emmHandle = mServiceHandleManager.getEmmHandle();
        int ecmHandle = mServiceHandleManager.getEcmHandle();
        try {
            JSONObject emmArgs = new JSONObject();
            emmArgs.put("handle", emmHandle);
            emmArgs.put("start", start ? 1 : 0);
            casRequest(REQUEST_TYPE_SERVICE_MONITOR, emmArgs);
            JSONObject ecmArgs = new JSONObject();
            ecmArgs.put("handle", ecmHandle);
            ecmArgs.put("start", start ? 1 : 0);
            casRequest(REQUEST_TYPE_SERVICE_MONITOR, ecmArgs);
        } catch (Exception ignored) {}
    }

    private void sendFsuCancelInLooper() {
        try {
            JSONObject args = new JSONObject();
            args.put("ca_system_id", mFsuSystemId);
            casRequest(REQUEST_TYPE_FSU_CANCEL, args);
        } catch (Exception ignored) {}
    }

    private void sendFsuNotifyInLooper() {
        casRequest(REQUEST_TYPE_FSU_NOTIFY_CAT);
    }

    private String getCasRequestCmd(int type, JSONObject args) {
        switch (type) {
            case REQUEST_TYPE_SERVICE_STATUS:
                return CAS_REQUEST_SERVICE_STATUS;
            case REQUEST_TYPE_LOADER_STATUS:
                return CAS_REQUEST_LOADER_STATUS;
            case REQUEST_TYPE_PRODUCT_STATUS:
                return String.format(Locale.getDefault(),
                        CAS_REQUEST_PRODUCT_STATUS, 0, 399);
            case REQUEST_TYPE_CLIENT_STATUS:
                return CAS_REQUEST_CLIENT_STATUS;
            case REQUEST_TYPE_CDSN:
                return CAS_REQUEST_CDSN;
            case REQUEST_TYPE_SERVICE_MONITOR: {
                if (args != null) {
                    int handle = args.optInt("handle", -1);
                    int start = args.optInt("start", -1);
                    if (handle != -1 && start != -1) {
                        return String.format(Locale.getDefault(),
                                CAS_REQUEST_SERVICE_MONITOR, handle, start);
                    }
                }
                return null;
            }
            case REQUEST_TYPE_FSU_CANCEL: {
                if (args != null) {
                    int caSystemId = args.optInt("ca_system_id", 0);
                    if (caSystemId > 0) {
                        return String.format(Locale.getDefault(),
                                CAS_REQUEST_FSU_CANCEL, caSystemId);
                    }
                }
                return null;
            }
            case REQUEST_TYPE_FSU_NOTIFY_CAT: {
                return CAS_REQUEST_FSU_NOTIFY_CAT;
            }
            case REQUEST_TYPE_SET_TUNED_INFO: {
                if (args != null) {
                    int sid = args.optInt("sid", 0xffff);
                    int on_id = args.optInt("on_id", 0xffff);
                    int ts_id = args.optInt("ts_id", 0xffff);
                    if (sid != 0xff && on_id != 0xff && ts_id != 0xff) {
                        return String.format(Locale.getDefault(),
                                CAS_REQUEST_SET_TUNED_INFO, sid, ts_id, on_id);
                    }
                }
                return null;
            }
        }
        return null;
    }

    private JSONObject casRequest(int type) {
        return casRequest(type, null);
    }

    private JSONObject casRequest(int type, JSONObject args) {
        String request = getCasRequestCmd(type, args);
        if (!TextUtils.isEmpty(request)) {
            String casRet = CasUtils.getInstance().casSessionRequest(0, request);
            Log.d(TAG, "Cas request in " + request + ", out " + casRet);
            try {
                if (!TextUtils.isEmpty(casRet)) {
                    JSONObject ret = new JSONObject();
                    JSONObject casRequestResult = new JSONObject(casRet);
                    if (type == REQUEST_TYPE_PRODUCT_STATUS) {
                        casRequestResult = getMultipleProductStatus(casRet);
                    }
                    ret.put("msg_type", type);
                    ret.put("status", casRequestResult);
                    return ret;
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    public void refreshStatusOnPlayingStart() {
        if (mThreadHandler == null)
            return;
        mThreadHandler.removeCallbacks(mServiceStatusUpdater);
        mThreadHandler.postDelayed(mServiceStatusUpdater, DEFAULT_STATUS_DELAY);

        if (!mCdSnReceived) {
            mThreadHandler.postDelayed(mCdSnUpdater, 1000);
        }
        if (!mLoaderStatusReceived) {
            mThreadHandler.postDelayed(mLoaderStatusUpdater, 1000);
        }
    }

    public void getCasInitialStatus() {
        if (mThreadHandler == null)
            return;
        mThreadHandler.removeCallbacks(mCdSnUpdater);
        mThreadHandler.postDelayed(mCdSnUpdater, 1000);
        mThreadHandler.removeCallbacks(mLoaderStatusUpdater);
        mThreadHandler.postDelayed(mLoaderStatusUpdater, 1000);
        mThreadHandler.removeCallbacks(mServiceStatusUpdater);
        mThreadHandler.postDelayed(mServiceStatusUpdater, 1000);
        mThreadHandler.removeCallbacks(mProductStatusUpdater);
        mThreadHandler.postDelayed(mProductStatusUpdater, 1000);
        mThreadHandler.removeCallbacks(mClientStatusUpdater);
        mThreadHandler.postDelayed(mClientStatusUpdater, 1000);
    }

    private boolean fsuNeedChangeStream(int on_id, int ts_id, int sid) {
        String tmpUri = String.format(Locale.getDefault(), "%04x.%04x.%04x", on_id, ts_id, sid);
        String currentUri = CasUtils.getInstance().getCurrentDvbUri();
        Log.i(TAG, "fsuNeedChangeStream: tmp uri= " + tmpUri + ", current uri = " + currentUri);
        return (currentUri == null || !currentUri.contains(tmpUri));
    }

    private final CasStatusRunnable mServiceStatusUpdater =
            new CasStatusRunnable(REQUEST_TYPE_SERVICE_STATUS);
    private final CasStatusRunnable mLoaderStatusUpdater =
            new CasStatusRunnable(REQUEST_TYPE_LOADER_STATUS);
    private final CasStatusRunnable mProductStatusUpdater =
            new CasStatusRunnable(REQUEST_TYPE_PRODUCT_STATUS);
    private final CasStatusRunnable mClientStatusUpdater =
            new CasStatusRunnable(REQUEST_TYPE_CLIENT_STATUS);
    private final CasStatusRunnable mCdSnUpdater =
            new CasStatusRunnable(REQUEST_TYPE_CDSN);
    private class CasStatusRunnable implements Runnable {
        int type;
        CasStatusRunnable(int type) {
            this.type = type;
        }
        @Override
        public void run() {
            JSONObject status = casRequest(type);
            if (status != null) {
                handleCasProvider(status);
            }
        }
    }

    private JSONObject getMultipleProductStatus(@NonNull String p) {
        int start = 0;
        int end = 399;
        try {
            JSONObject ret = new JSONObject();
            JSONArray data = new JSONArray();
            String casRet = p;
            while (true) {
                JSONObject t = new JSONObject(casRet);
                String status = t.optString("Status", "");
                if (!ret.has("Status")) {
                    ret.putOpt("Status", status);
                }
                if ("OK".equalsIgnoreCase(status)) {
                    JSONObject pi = t.optJSONObject("Result");
                    if (pi != null) {
                        JSONArray ps = pi.optJSONArray("Status");
                        if (ps != null) {
                            for (int i = 0;i < ps.length(); i ++) {
                                data.put(ps.getJSONObject(i));
                            }
                            if (ps.length() >= 400 && data.length() < 1000) {
                                Log.d(TAG, "More than 400 products, need try to get more.");
                                start += 400;
                                end = start + 399;
                                casRet = CasUtils.getInstance().casSessionRequest(0,
                                        String.format(Locale.getDefault(),
                                                CAS_REQUEST_PRODUCT_STATUS, start, end));
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            JSONObject result = new JSONObject();
            result.put("Status", data);
            ret.put("Result", result);
            return ret;
        } catch (Exception e) {
            Log.w(TAG, "Got exception in fetch products count(" +
                    start + ":" + end + "), interrupt.");
        }
        return null;
    }
}
