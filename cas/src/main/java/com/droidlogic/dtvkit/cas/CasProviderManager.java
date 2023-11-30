package com.droidlogic.dtvkit.cas;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

public class CasProviderManager {
    private static final String TAG = "DtvCasProvider";

    public CasProviderManager() {
    }

    public void putCasSettingsValue(@NonNull Context context, String id, String value) {
        Log.v(TAG, "putCasSettingsValue, id = " + id + ", value = " + value);

        String table = CasProvider.TABLE_SETTINGS;
        Uri uri = Uri.parse(CasProvider.CONTENT_URI + table);
        ContentValues values = new ContentValues();
        values.put("_id", id);
        values.put("value", value);
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{"_id", "value"},
                "_id = ?", new String[]{id},
                null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String originalValue = cursor.getString(1);
                if (skipCheckDuplicatedId(id) || !Objects.equals(originalValue, value)) {
                    int row = context.getContentResolver().update(uri, values, "_id = ?",
                            new String[]{id});
                    if (row != -1) {
                        Log.v(TAG, "putCasSettingsValue update row = " + row);
                    }
                } else {
                    Log.v(TAG, "Has same value for " + id + ", skip this update");
                }
            } else {
                Uri insertUri = context.getContentResolver().insert(uri, values);
                if (insertUri != null) {
                    Log.v(TAG, "putCasSettingsValue insert uri = " + insertUri);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "putCasSettingsValue Exception  = " + e.getMessage());
        }
    }

    public void putScreenMessage(@NonNull Context context, int msgType, int attributed,
                                 int enhanced, int duration, int flashing, int banner,
                                 int coverageCode, int fingerPrintType, int scroll,
                                 byte[] message, byte[] option, int flag1) {
        Log.v(TAG, "putScreenMessage, type = " + msgType +
                ", isAdm= " + attributed +
                ", enhanced= " + enhanced +
                ", value = " + Arrays.toString(message));

        String table = CasProvider.TABLE_SCREEN_MESSAGES;
        Uri uri = Uri.parse(CasProvider.CONTENT_URI + table);
        ContentValues values = new ContentValues();
        values.put("message_type", msgType);
        values.put("attributed", attributed);
        values.put("enhanced", enhanced);
        values.put("duration", duration);
        values.put("flashing", flashing);
        values.put("banner", banner);
        values.put("coverage_code", coverageCode);
        values.put("fingerprint_type", fingerPrintType);
        values.put("scroll", scroll);
        values.put("text", message);
        values.put("option", option);
        values.put("flag1", flag1);
        if (msgType <= 1) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri,
                    new String[]{"_id, text"},
                    "message_type = ?",
                    new String[]{String.valueOf(msgType)},
                    null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    byte[] data = cursor.getBlob(1);
                    if (Arrays.equals(data, message)) {
                        if (msgType == 1) {
                            //remove same force messages
                            context.getContentResolver().delete(
                                    uri, "_id = ?", new String[]{String.valueOf(id)});
                        } else {
                            //has same normal message, skip it
                            return;
                        }
                    }
                }
            }
        }
        context.getContentResolver().insert(uri, values);
    }

    public void putSoUserMessage(@NonNull Context context, byte[] message, int flag1) {
        Log.v(TAG, "putSoUserMessage, message = " + Arrays.toString(message));

        String table = CasProvider.TABLE_SO_USER_MESSAGE;
        Uri uri = Uri.parse(CasProvider.CONTENT_URI + table);
        ContentValues values = new ContentValues();
        values.put("message", message);
        values.put("flag1", flag1);
        context.getContentResolver().insert(uri, values);
    }

    public void cleanForceMessagesOnStart(@NonNull Context context) {
        Uri attributedUri = Uri.parse(CasProvider.CONTENT_URI
                + CasProvider.TABLE_SCREEN_MESSAGES);
        context.getContentResolver().delete(attributedUri,
                "message_type >= 1", null);
    }

    private boolean skipCheckDuplicatedId(@NonNull String id) {
        return "cas.irdeto.info.parental_pin".equals(id) ||
                "cas.irdeto.info.live_status".equals(id) ||
                "cas.irdeto.info.record_status".equals(id) ||
                "cas.irdeto.info.playback_status".equals(id) ||
                "cas.irdeto.info.timeshiftRecord_status".equals(id) ||
                "cas.irdeto.info.fsu".equals(id);
    }
}
