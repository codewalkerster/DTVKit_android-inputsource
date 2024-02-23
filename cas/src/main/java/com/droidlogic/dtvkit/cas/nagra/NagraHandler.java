package com.droidlogic.dtvkit.cas.nagra;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.droidlogic.dtvkit.cas.CasHandler;
import com.droidlogic.dtvkit.cas.CasProvider;
import com.droidlogic.dtvkit.cas.CasProviderManager;
import com.droidlogic.dtvkit.cas.CasUtils;

import org.json.JSONObject;

import java.util.Locale;

public class NagraHandler extends CasHandler {
    private static final String TAG = "NagraHandler";
    private final Context mContext;
    private final Handler mThreadHandler;
    private ContentObserver mCasActionObserver;
    protected CasProviderManager mCasProviderManager;

    private static final int CAS_MSG_ERROR_BANNER = 0;
    private static final int CAS_REQUEST_INVOKE_SYSTEM_INFO = 3000;
    private static final int CAS_REQUEST_INVOKE_FACTORY_RESET = 4000;

    private static final String NAGRA_FAC_RESET_ACTION = "action.nagra.reset";

    public NagraHandler(@NonNull Context context,
                      @NonNull Handler handler) {
        mContext = context;
        mThreadHandler = handler;
        mCasProviderManager = new CasProviderManager();
        mCasActionObserver = new ContentObserver(mThreadHandler) {
            @Override
            public void onChange(boolean selfChange, @Nullable Uri uri) {
                if (uri != null) {
                    String fragment = uri.getFragment();
                    String updatedId = null;
                    if (fragment != null) {
                        String[] update = fragment.split(" value=");
                        updatedId = update[0].replace("_id=", "");
                    }
                    if (NAGRA_FAC_RESET_ACTION.equals(updatedId)) {
                        mThreadHandler.postDelayed(() -> {
                            JSONObject ret = casRequest(CAS_REQUEST_INVOKE_FACTORY_RESET);
                            if (ret != null) {
                                handleCasProvider(ret);
                            }
                        }, 50);
                    }
                }
            }
        };
        context.getContentResolver().registerContentObserver(
                Uri.parse(CasProvider.CONTENT_URI + CasProvider.TABLE_ACTIONS),
                true, mCasActionObserver);
        getCasInitialStatus();
    }

    @Override
    public void destroy(@NonNull Context context) {
        if (mCasActionObserver != null) {
            context.getContentResolver().unregisterContentObserver(mCasActionObserver);
            mCasActionObserver = null;
        }
    }

    @Override
    public void handleCasProvider(@NonNull JSONObject casEvent) {
        Log.d(TAG, "handleCasProvider: " + casEvent);
        if (mThreadHandler == null || mCasProviderManager == null)
            return;

        int type = casEvent.optInt("msg_type", 0);

        //valid cas info, set ca system id to nagra
        if (mCasSystem != CAS_SYSTEM_NAGRA) {
            mCasSystem = CAS_SYSTEM_NAGRA;
            mThreadHandler.postDelayed(() ->
                            mCasProviderManager.putCasSettingsValue(mContext,
                                    "cas.system.id", "" + CAS_SYSTEM_NAGRA),
                    0);
        }
        switch (type) {
            case CAS_MSG_ERROR_BANNER: {
                String id = "cas.nagra.info.banner_msg";
                String value = casEvent.optString("content", "");
                if (!TextUtils.isEmpty(value)) {
                    mThreadHandler.postDelayed(() ->
                            mCasProviderManager.putCasSettingsValue(mContext, id, value),
                            50);
                }
                break;
            }
            case CAS_REQUEST_INVOKE_SYSTEM_INFO: {
                String id = "cas.nagra.info.system";
                JSONObject value = null;
                //first try if from cas hal
                JSONObject casRequestValue = casEvent.optJSONObject("status");
                if (casRequestValue != null) {
                    String casRequestResult = casRequestValue.optString("Status", "fail");
                    if ("OK".equalsIgnoreCase(casRequestResult)) {
                        value = casRequestValue.optJSONObject("Result");
                    } else if ("Unavailable".equalsIgnoreCase(casRequestResult)) {
                        mThreadHandler.postDelayed(this::getCasInitialStatus, 2000);
                    }
                }
                //then try if from jCas
                if (value == null ) {
                    value = casEvent.optJSONObject("system_info");
                }
                if (value != null) {
                    JSONObject finalValue = value;
                    mThreadHandler.postDelayed(() ->
                                    mCasProviderManager.putCasSettingsValue(mContext,
                                            id, finalValue.toString()),
                            50);
                }
                break;
            }
            case CAS_REQUEST_INVOKE_FACTORY_RESET: {
                String id = "cas.nagra.info.reset_ret";
                int ret = -1;
                //first try if from cas hal
                JSONObject casRequestValue = casEvent.optJSONObject("status");
                if (casRequestValue != null) {
                    String casRequestResult = casRequestValue.optString("Status", "fail");
                    if ("OK".equalsIgnoreCase(casRequestResult)) {
                        String r = casRequestValue.optString("Result");
                        if ("Success".equalsIgnoreCase(r)) {
                            ret = 1;
                        } else {
                            ret = 0;
                        }
                    }
                }
                //then try if from jCas
                if (ret == -1 && casEvent.has("result")) {
                    ret = casEvent.optBoolean("result") ? 1 :0;
                }
                if (ret != -1) {
                    boolean finalValue = ret == 1;
                    mThreadHandler.postDelayed(() ->
                                    mCasProviderManager.putCasSettingsValue(mContext,
                                            id, String.valueOf(finalValue)),
                            50);
                }
                break;
            }
        }
    }

    @Override
    public void onPlayStatusStart(@NonNull String uri) {
    }

    @Override
    public void onPlayingStopped() {
    }

    @Override
    public void onRecordingStatusChanged() {
    }

    @Override
    public void getCasInitialStatus() {
        mThreadHandler.postDelayed(() -> {
            JSONObject ret = casRequest(CAS_REQUEST_INVOKE_SYSTEM_INFO);
            if (ret != null) {
                handleCasProvider(ret);
            }
        }, 200);
    }

    private JSONObject casRequest(int invoke) {
        String request = String.format(Locale.getDefault(),"{\"InvokeID\":%d}", invoke);
        String casRet = CasUtils.getInstance().casSessionRequest(0, request);
        Log.d(TAG, "Cas request in " + invoke + ", out " + casRet);
        try {
            if (!TextUtils.isEmpty(casRet)) {
                JSONObject ret = new JSONObject();
                JSONObject casRequestResult = new JSONObject(casRet);
                String status = casRequestResult.optString("Status");
                if (!"Fail".equalsIgnoreCase(status)) {
                    ret.put("msg_type", invoke);
                    ret.put("status", casRequestResult);
                    return ret;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }
}

