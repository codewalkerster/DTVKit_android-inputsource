package com.droidlogic.settings;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.companionlibrary.model.Channel;
import com.droidlogic.dtvkit.companionlibrary.model.InternalProviderData;
import com.droidlogic.dtvkit.inputsource.DtvkitEpgSync;
import com.droidlogic.dtvkit.inputsource.R;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.dtvkit.inputsource.util.FeatureUtil;

public class FactorySettings {
    private static final String TAG = "FactorySettings";
    private Context mContext;
    private ParameterManager mParameterManager = null;
    private String mInput = null;
    private FactorySettingsCallback mCallback;
    private TextView textView_Message;
    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null, mThreadHandler = null;
    private boolean mStartSync = false;
    private String NotifyAppendText = "";
    private String mImportEditInfo = null;

    private final static int MSG_DO_EXPORT_CHANNEL  = 0;
    private final static int MSG_DO_IMPORT_CHANNEL  = 1;
    private final static int MSG_DO_EXPORT_SATELLITES     = 2;
    private final static int MSG_DO_IMPORT_SATELLITES     = 3;
    private final static int MSG_DO_REST_TO_DEFAULT = 4;
    private final static int MSG_SYNC               = 5;
    private final static int MSG_FINISH             = 6;

    /**
     * @param context Need ActivityContext
     */
    public FactorySettings(final Context context, ParameterManager parameter,
                               String input_id, FactorySettingsCallback callback) {
        mContext = context;
        mParameterManager = parameter;
        mInput = input_id;
        mCallback = callback;
        mHandlerThread = new HandlerThread("FactorySettingsThread");
        mHandlerThread.start();
        mHandler = new Handler();
        mThreadHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                boolean ret = false;
                updateNotifyMessage("working");
                switch (msg.what) {
                    case MSG_DO_EXPORT_CHANNEL: {
                        ret = doExportChannels();
                        Message message = mThreadHandler.obtainMessage(MSG_FINISH);
                        message.arg1 = ret?1:0;
                        mThreadHandler.sendMessageDelayed(message, 100);
                        break;
                    }
                    case MSG_DO_IMPORT_CHANNEL: {
                        ret = doImportChannels();
                        if (ret) {
                            Message message = mThreadHandler.obtainMessage(MSG_SYNC);
                            message.arg1 = 0;//is restore
                            mThreadHandler.sendMessageDelayed(message, 100);
                        } else {
                            Message message = mThreadHandler.obtainMessage(MSG_FINISH);
                            message.arg1 = 0;
                            mThreadHandler.sendMessageDelayed(message, 100);
                        }
                        break;
                    }
                    case MSG_DO_EXPORT_SATELLITES: {
                        ret = doExportSatellites();
                        Message message = mThreadHandler.obtainMessage(MSG_FINISH);
                        message.arg1 = ret?1:0;
                        mThreadHandler.sendMessageDelayed(message, 100);
                        break;
                    }
                    case MSG_DO_IMPORT_SATELLITES: {
                        ret = doImportSatellites();
                        Message message = mThreadHandler.obtainMessage(MSG_FINISH);
                        message.arg1 = ret?1:0;
                        mThreadHandler.sendMessageDelayed(message, 100);
                        break;
                    }
                    case MSG_DO_REST_TO_DEFAULT: {
                        ret = doRestoreDefaults();
                        if (ret) {
                            Message message = mThreadHandler.obtainMessage(MSG_SYNC);
                            message.arg1 = 1;//is restore
                            mThreadHandler.sendMessageDelayed(message, 100);
                        } else {
                            Message message = mThreadHandler.obtainMessage(MSG_FINISH);
                            message.arg1 = 0;
                            mThreadHandler.sendMessageDelayed(message, 100);
                        }
                        break;
                    }
                    case MSG_SYNC: {
                        boolean isDvbs = (msg.arg1 == 1);
                        startSync(isDvbs);
                        break;
                    }
                    case MSG_FINISH: {
                        stopSync();
                        if (!TextUtils.isEmpty(mImportEditInfo)) {
                            restoreEditedChannels(mImportEditInfo);
                        }
                        if (msg.arg1 > 0)
                            updateNotifyMessage("done" + NotifyAppendText);
                        else
                            updateNotifyMessage("failed" + NotifyAppendText);
                        synchronized (this) {
                            NotifyAppendText = "";
                        }
                    }
                    default:
                        break;
                }
            }
        };
    }

    public interface FactorySettingsCallback {
        public void onDismiss();
    }

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            String status = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            if (status.equals(EpgSyncJobService.SYNC_FINISHED)) {
                mThreadHandler.removeMessages(MSG_FINISH);
                Message message = mThreadHandler.obtainMessage(MSG_FINISH);
                message.arg1 = 1;
                mThreadHandler.sendMessage(message);
            }
        }
    };

    private void startSync(boolean isDvbs) {
        synchronized (this) {
            if (!mStartSync) {
                mStartSync = true;
                String signalType = isDvbs ? "DVB-S" : "full";
                LocalBroadcastManager.getInstance(mContext).registerReceiver(syncReceiver,
                        new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));

                Bundle parameters = new Bundle();
                parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO);
                parameters.putString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, signalType);

                Intent intent = new Intent(mContext, com.droidlogic.dtvkit.inputsource.DtvkitEpgSync.class);
                intent.putExtra("inputId", mInput);
                intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_FROM, TAG);
                intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SYNC_PARAMETERS, parameters);
                mContext.startService(intent);
           }
        }
    }

    private void stopSync() {
        synchronized (this) {
            if (mStartSync) {
                mStartSync = false;
                LocalBroadcastManager.getInstance(mContext).unregisterReceiver(syncReceiver);
            }
        }
    }

    private View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_export_db: {
                    mThreadHandler.removeMessages(MSG_DO_EXPORT_CHANNEL);
                    mThreadHandler.sendEmptyMessageDelayed(MSG_DO_EXPORT_CHANNEL, 200);
                    break;
                }
                case R.id.btn_import_db: {
                    mThreadHandler.removeMessages(MSG_DO_IMPORT_CHANNEL);
                    mThreadHandler.sendEmptyMessageDelayed(MSG_DO_IMPORT_CHANNEL, 200);
                    break;
                }
                case R.id.btn_export_sat: {
                    mThreadHandler.removeMessages(MSG_DO_EXPORT_SATELLITES);
                    mThreadHandler.sendEmptyMessageDelayed(MSG_DO_EXPORT_SATELLITES, 200);
                    break;
                }
                case R.id.btn_import_sat: {
                    mThreadHandler.removeMessages(MSG_DO_IMPORT_SATELLITES);
                    mThreadHandler.sendEmptyMessageDelayed(MSG_DO_IMPORT_SATELLITES, 200);
                    break;
                }
                case R.id.btn_rest_default: {
                    mThreadHandler.removeMessages(MSG_DO_REST_TO_DEFAULT);
                    mThreadHandler.sendEmptyMessageDelayed(MSG_DO_REST_TO_DEFAULT, 200);
                    break;
                }
                default:
                    break;
            }
        }
    };

    public void start() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(mContext, R.layout.factory_settings, null);
        Button exportChannels = dialogView.findViewById(R.id.btn_export_db);
        Button importChannels = dialogView.findViewById(R.id.btn_import_db);
        Button exportSatellites = dialogView.findViewById(R.id.btn_export_sat);
        Button importSatellites = dialogView.findViewById(R.id.btn_import_sat);
        Button restoreToDefault = dialogView.findViewById(R.id.btn_rest_default);
        textView_Message = dialogView.findViewById(R.id.tv_message);
        exportChannels.setOnClickListener(listener);
        importChannels.setOnClickListener(listener);
        exportSatellites.setOnClickListener(listener);
        importSatellites.setOnClickListener(listener);
        restoreToDefault.setOnClickListener(listener);
        exportChannels.requestFocus();
        if (mParameterManager.getCurrentDvbSource() != ParameterManager.SIGNAL_QPSK) {
            restoreToDefault.setVisibility(View.GONE);
        }

        alert.setView(dialogView);
        WindowManager.LayoutParams params = alert.getWindow().getAttributes();
        params.width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500, mContext.getResources().getDisplayMetrics());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        alert.getWindow().setAttributes(params);
        alert.show();
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                synchronized (this) {
                    mThreadHandler.removeCallbacksAndMessages(null);
                    mHandlerThread.getLooper().quitSafely();
                    mThreadHandler = null;
                    mHandlerThread = null;
                    mHandler.removeCallbacksAndMessages(null);
                    mHandler = null;
                    if (mCallback != null) {
                        mCallback.onDismiss();
                    }
                }
            }
        });
    }

    private void updateNotifyMessage(final String msg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                textView_Message.setText(msg);
            }
        });
    }

    private String getValidExternalStoragePath() {
        SysSettingManager sysSettingManager = new SysSettingManager(mContext);
        List<String> storageList = sysSettingManager.getStorageDevicePathList();
        String removeablePath = null;
        for (String path: storageList) {
            if (sysSettingManager.isMediaPath(path)) {
                String mountedPath = sysSettingManager.convertMediaPathToMountedPath(path);
                if (sysSettingManager.isMountedPathAvailable(mountedPath)) {
                    if (true == FeatureUtil.getFeatureSupportTunerFramework())
                        removeablePath = mountedPath;
                    else
                        removeablePath = path;
                    break;
                } else {
                    removeablePath = null;
                }
            }
        }
        sysSettingManager = null;
        return removeablePath;
    }

    private boolean doRestoreDefaults() {
        //dvbs restore only, reset satellites
        boolean ret = false;
        synchronized (this) {
            Log.d(TAG, "doRestoreDefaults");
            if (mParameterManager != null) {
                ret = mParameterManager.restSatellites();
            }
        }
        return ret;
    }

    private boolean doImportSatellites() {
        boolean ret = false;
        synchronized (this) {
            Log.d(TAG, "doImportSatellites");
            String path = getValidExternalStoragePath();
            if (path == null) {
                Log.e(TAG, "No external storage exists, faild do export.");
                NotifyAppendText = ", No storage exists!";
            } else {
                if (mParameterManager != null) {
                    ret = mParameterManager.importSatellites(path + "/dtvkit_satellites.json");
                    if (ret) {
                        NotifyAppendText = ", successfully import satellites from " + path + "/dtvkit_satellites.json";
                    }
                }
            }
        }
        return ret;
    }

    private boolean doExportSatellites() {
        boolean ret = false;
        synchronized (this) {
            Log.d(TAG, "doExportSatellites");
            String path = getValidExternalStoragePath();
            if (path == null) {
                Log.e(TAG, "No external storage exists, faild do export.");
                NotifyAppendText = ", No storage exists!";
            } else {
                if (mParameterManager != null) {
                    ret = mParameterManager.exportSatellites(path + "/dtvkit_satellites.json");
                    if (ret) {
                        NotifyAppendText = ", Satellites have been exported to " + path + "/dtvkit_satellites.json";
                    }
                }
            }
        }
        return ret;
    }

    private boolean doImportChannels() {
        boolean ret = false;
        synchronized (this) {
            Log.d(TAG, "doImportChannels");
            String path = getValidExternalStoragePath();
            if (path == null) {
                Log.e(TAG, "No external storage exists, faild do import.");
                NotifyAppendText = ", No storage exists!";
            } else {
                if (mParameterManager != null) {
                    JSONObject importRet = mParameterManager.importChannels(path + "/dtvkit_channels.xml");
                    if (importRet == null)
                        ret = false;
                    else {
                        ret = importRet.optBoolean("ret", false);
                        int editLength = importRet.optInt("editLength", 0);
                        if (ret && (editLength > 0)) {
                            mImportEditInfo = decompress(decode(importRet.optString("editData", null)),
                                editLength);
                            Log.v(TAG, "import got edit info, length: " + editLength +
                                " data: " +mImportEditInfo);
                        }
                    }
                    if (ret) {
                        NotifyAppendText = ", successfully import channels from " + path + "/dtvkit_channels.xml";
                    }
                }
            }
        }
        return ret;
    }

    private boolean doExportChannels() {
        boolean ret = false;
        synchronized (this) {
            Log.d(TAG, "doExportChannels");
            String path = getValidExternalStoragePath();
            if (path == null) {
                Log.e(TAG, "No external storage exists, faild do export.");
                NotifyAppendText = ", No storage exists!";
            } else {
                String editInfo = getTvChannelEditInfo();
                String encodedEditInfo = null;
                int editLen = 0;
                if (!TextUtils.isEmpty(editInfo)) {
                    encodedEditInfo = encode(compress(editInfo));
                    editLen = editInfo.length();
                    Log.v(TAG, "find eidted info length: " + editLen);
                }
                if (mParameterManager != null) {
                    ret = mParameterManager.exportChannels(path + "/dtvkit_channels.xml",
                        encodedEditInfo, editLen);
                    if (ret) {
                        NotifyAppendText = ", Channels have been exported to " + path + "/dtvkit_channels.xml";
                    }
                }
            }
        }
        return ret;
    }

    private String getTvChannelEditInfo() {
        String ret = null;
        Uri uri = TvContract.buildChannelsUriForInput(mInput);
        Cursor cursor = null;
        String selection =
            "\"internal_provider_data\" LIKE \'%\"set_displaynumber\":\"1\"%\' ESCAPE \'\\'";
        String[] projection = {
            TvContract.Channels.COLUMN_TYPE,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER,
            TvContract.Channels.COLUMN_SERVICE_TYPE,
            TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
        };
        try {
            if (uri != null) {
                cursor = mContext.getContentResolver().query(uri, projection, selection, null, null);
            }
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            Log.v(TAG, "find edited channel count: " + cursor.getCount());
            JSONArray editInfoArray = new JSONArray();
            while (cursor != null && cursor.moveToNext()) {
                String channelType = cursor.getString(0);
                String displayNumber = cursor.getString(1);
                String serviceType = cursor.getString(2);
                int type = cursor.getType(3);
                if (type != Cursor.FIELD_TYPE_BLOB) {
                    continue;
                } else {
                    byte[] internalProviderByteData = cursor.getBlob(3);
                    InternalProviderData internalProviderData =
                        new InternalProviderData(internalProviderByteData);
                    String rawDisplayNumber =
                        (String) internalProviderData.get(Channel.KEY_RAW_DISPLAYNUMBER);
                    String editInfo = createEditInfo(channelType, serviceType,
                        rawDisplayNumber, displayNumber);
                    if (TextUtils.isEmpty(editInfo)) {
                        continue;
                    }
                    editInfoArray.put(editInfo);
                }
            }
            ret = editInfoArray.toString();
        }  catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return ret;
    }

    private String createEditInfo(String channelType, String serviceType,
                                  String lcn, String displayNumber) {
        String keyType;
        String keyServiceType;

        if (TextUtils.isEmpty(channelType)
            || TextUtils.isEmpty(serviceType)
            || TextUtils.isEmpty(lcn)
            || TextUtils.isEmpty(displayNumber)) {
            return null;
        }
        if (TvContract.Channels.TYPE_DVB_T.equals(channelType)
            || TvContract.Channels.TYPE_DVB_T2.equals(channelType)) {
            keyType = "T";
        } else if (TvContract.Channels.TYPE_DVB_S.equals(channelType)
            || TvContract.Channels.TYPE_DVB_S2.equals(channelType)) {
            keyType = "S";
        } else if (TvContract.Channels.TYPE_DVB_C.equals(channelType)) {
            keyType = "C";
        } else if (TvContract.Channels.TYPE_ISDB_T.equals(channelType)) {
            keyType = "I";
        } else {
            return null;
        }

        if (TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO.equals(serviceType)) {
            keyServiceType = "V";
        } else if (TvContract.Channels.SERVICE_TYPE_AUDIO.equals(serviceType)) {
            keyServiceType = "A";
        } else {
            keyServiceType = "O";
        }

        return keyType + "&" + keyServiceType + "&" + lcn + "&" + displayNumber;
    }

    private void restoreEditedChannels(String editInfo) {
        String ret = null;
        Cursor cursor = null;
        Uri uri = TvContract.buildChannelsUriForInput(mInput);
        String[] projection = {
            TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
        };

        try {
            JSONArray editedChannels = new JSONArray(editInfo);
            for (int i = 0; i < editedChannels.length(); i++) {
                String edit = (String)(editedChannels.get(i));
                String[] restoreInfo = getSelectionFromEditInfo(edit);
                if (restoreInfo != null) {
                    cursor = mContext.getContentResolver().query(uri,
                        projection, restoreInfo[0], null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        byte[] internalProviderByteData = cursor.getBlob(0);
                        InternalProviderData internalProviderData =
                            new InternalProviderData(internalProviderByteData);
                        internalProviderData.put("set_displaynumber", "1");
                        internalProviderData.put("new_displaynumber", "" + restoreInfo[1]);
                        ContentValues values = new ContentValues();
                        values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, restoreInfo[1]);
                        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
                            internalProviderData.toString().getBytes());
                        mContext.getContentResolver().update(uri, values, restoreInfo[0], null);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String[] getSelectionFromEditInfo(String editInfo) {
        String typeSelection = null;
        String serviceTypeSelection = null;
        String lcnSelection = null;
        String rawDisplayNumberSelection = null;

        if (TextUtils.isEmpty(editInfo))
            return null;

        String[] infoSplit = editInfo.split("&");
        if (infoSplit == null || infoSplit.length != 4)
            return null;

        String signalType = infoSplit[0];
        String serviceType = infoSplit[1];
        String rawDisplayNumber = infoSplit[2];
        String newDisplayNumer = infoSplit[3];
        if ("T".equals(signalType)) {
            typeSelection = "(type == \"TYPE_DVB_T\" or type == \"TYPE_DVB_T2\")";
        } else if ("C".equals(signalType)) {
            typeSelection = "type == \"TYPE_DVB_C\"";
        } else if ("S".equals(signalType)) {
            typeSelection = "(type == \"TYPE_DVB_S\" or type == \"TYPE_DVB_S2\")";
        } else if ("I".equals(signalType)) {
            typeSelection = "type == \"TYPE_ISDB_T\"";
        } else {
            return null;
        }
        if ("V".equals(serviceType)) {
            serviceTypeSelection = "service_type == \"SERVICE_TYPE_AUDIO_VIDEO\"";
        } else if ("A".equals(serviceType)) {
            serviceTypeSelection = "service_type == \"SERVICE_TYPE_AUDIO\"";
        } else if ("O".equals(serviceType)) {
            serviceTypeSelection = "service_type == \"SERVICE_TYPE_OTHER\"";
        } else {
            return null;
        }

        lcnSelection = "display_number == " + rawDisplayNumber;
        rawDisplayNumberSelection = "(\"internal_provider_data\" LIKE \'%\"raw_displaynumber\":\""
            + rawDisplayNumber + "\"%\')";
        String selection = typeSelection + " and " +
            serviceTypeSelection + " and " + lcnSelection + " and " + rawDisplayNumberSelection;
        return new String[] {selection, newDisplayNumer};
    }

    private String compress(final String data) {
        try (final ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
               final GZIPOutputStream zipper = new GZIPOutputStream(resultStream)) {
            zipper.write(data.getBytes());
            zipper.finish();
            return resultStream.toString("ISO-8859-1");
        } catch(Exception ignored) {
        }
        return null;
    }

    private String decompress(final String     data, final int expectedSize) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes("ISO-8859-1"));
               final GZIPInputStream unzipper = new GZIPInputStream(inputStream)) {
           final byte [] result = new byte[expectedSize];
           int totalReadBytes = 0;
           while (totalReadBytes < result.length) {
               final int restBytes = result.length - totalReadBytes;
               final int readBytes = unzipper.read(result, totalReadBytes, restBytes);
               if (readBytes < 0) {
                   break;
               }
               totalReadBytes += readBytes;
           }
           if (expectedSize != totalReadBytes) {
               return null;
           }
           return new String(result);
        } catch(Exception ignored) {
        }
        return null;
    }

    private String encode(final String data) {
        if (TextUtils.isEmpty(data))
            return null;
        StringBuilder sbu = new StringBuilder();
        char[] chars = data.toCharArray();
        for (char aChar : chars) {
            sbu.append(String.format("%02x", (int) aChar));
        }
        return sbu.toString();
    }

    private String decode(final String data) {
        if (TextUtils.isEmpty(data))
            return null;
        StringBuilder sbu = new StringBuilder();
        char[] chars = data.toCharArray();
        for (int i = 0;i < chars.length; i +=2) {
            String hexStr = String.valueOf(chars[i]) + chars[i + 1];
            sbu.append((char)(Integer.parseInt(hexStr, 16)));
        }
        return sbu.toString();
    }
}

