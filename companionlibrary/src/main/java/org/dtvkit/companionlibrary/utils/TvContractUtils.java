/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications copyright (C) 2018 DTVKit
 */

package com.droidlogic.dtvkit.companionlibrary.utils;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.companionlibrary.model.Channel;
import com.droidlogic.dtvkit.companionlibrary.model.InternalProviderData;
import com.droidlogic.dtvkit.companionlibrary.model.Program;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Static helper methods for working with {@link android.media.tv.TvContract}.
 */
public class TvContractUtils {
    private static final String TAG = "TvContractUtils";
    private static final boolean DEBUG = false;
    private static final int BATCH_OPERATION_COUNT = 50;

    private static final int SIGNAL_QPSK  = 1; // digital satellite
    private static final int SIGNAL_COFDM = 2; // digital terrestrial
    private static final int SIGNAL_QAM   = 4; // digital cable
    private static final int SIGNAL_ISDBT = 5;
    private static final int ATV_VIDEO_STD_PAL  = 1;
    private static final int ATV_VIDEO_STD_NTSC = 2;
    private static final int ATV_VIDEO_STD_SECAM = 3;
    private static final Map<String, String> CHANNEL_SETTINGS_DEFAULT = new HashMap<String, String>() {
        {
            put(Channel.KEY_SET_FAVOURITE, "0");
            put(Channel.KEY_SET_HIDDEN, "0");
            put(Channel.KEY_SET_DELETE, "0");
            put(Channel.KEY_IS_FAVOURITE, "0");
            put(Channel.KEY_FAVOURITE_INFO, "");
            put(Channel.KEY_HIDDEN, "false");
            put(Channel.KEY_SET_DISPLAYNAME, "0");
            put(Channel.KEY_NEW_DISPLAYNAME, "");
            put(Channel.KEY_SET_DISPLAYNUMBER, "0");
            put(Channel.KEY_NEW_DISPLAYNUMBER, "0");
            put(Channels.COLUMN_APP_LINK_ICON_URI, "");
            put(Channels.COLUMN_APP_LINK_INTENT_URI, "");
            put(Channel.KEY_SET_MOVE_DISPLAYNUMBER, "0");
        }
    };
    private static final Map<String, String> ATV_EXTRA_SETTINGS_DEFAULT = new HashMap<String, String>() {
        {
            put(Channel.KEY_FREQUENCY, "0");
            put(Channel.KEY_FINE_TUNE, "0");
        }
    };

    /**
     * Updates the list of available channels.
     *
     * @param context The application's context.
     * @param inputId The ID of the TV input service that provides this TV channel.
     * @param channels The updated list of channels.
     */
    public static void updateChannels(Context context, String inputId, boolean isSearched,
            final List<Channel> channels, PersistableBundle extras) {
        // hold a map from Unique String to row ID for existing channels.
        ArrayList<Pair<String, Long>> channelMap = new ArrayList<>();
        // hold a map from Unique String to old InternalProviderData for existing channels.
        ArrayMap<String, ArrayMap<String, String>> channelUseSettingValueMap = new ArrayMap<>();
        // Operations to execute
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        // different source
        ArrayList<String> sources = new ArrayList<>();
        // 0.find sources
        String syncSignalType = extras.getString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE, "full");
        String searchMode = extras.getString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, null);
        boolean syncCurrent = !TextUtils.equals("full", syncSignalType);
        if (syncCurrent) {
            sources.add(syncSignalType);
            if (!syncSignalType.contains("DVB")) {
                sources.add("ATV");
            }
        } else {
            sources.add("DVB-T");
            sources.add("DVB-C");
            sources.add("DVB-S");
            sources.add("ISDB-T");
            sources.add("ATV");
        }
        for (String source : sources) {
            // 1.first get existed channels in tv.db
            cacheRelatedChannel(channelMap, channelUseSettingValueMap, context, inputId,
                    isSearched, source, extras);
            // 2.find new channel and channels that need to be updated
            handleUpdateOrInsert(ops, channelMap, channelUseSettingValueMap, context, inputId,
                    channels.stream()
                            .filter(channel -> source.equals(toSignalType(channel.getType())))
                            .collect(Collectors.toList()), source, extras);
            // 3.delete channels that no need
            handleChannelDelete(channelMap, context);
            // 4.delete old programs
            if (TextUtils.equals(EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO, searchMode)) {
                handleProgramDelete(context, source);
            }
            channelMap.clear();
            channelUseSettingValueMap.clear();
        }
        //5.deal insert or update channels to tv.db
        syncToDb(ops, context);

        //notify immediately as livetv may be in background and android r sends such contentprovider notification after 10s in ContentService
        if (VERSION.SDK_INT > VERSION_CODES.P + 1 && ops.size() > 0) {
            context.getContentResolver().notifyChange(Channels.CONTENT_URI, null, 1 << 15/*ContentResolver.NOTIFY_NO_DELAY*/);
        }
        ops.clear();
    }

    //1.first get existed channels in tv.db
    private static void cacheRelatedChannel(
            ArrayList<Pair<String, Long>> channelMap,
            ArrayMap<String, ArrayMap<String, String>> channelUseSettingValueMap, Context context,
            String inputId, boolean isSearched, String signalType, PersistableBundle extras) {
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {
                Channels._ID, Channels.COLUMN_TYPE, Channels.COLUMN_ORIGINAL_NETWORK_ID,
                Channels.COLUMN_TRANSPORT_STREAM_ID, Channels.COLUMN_SERVICE_ID,
                Channels.COLUMN_DISPLAY_NAME, Channels.COLUMN_DISPLAY_NUMBER,
                Channels.COLUMN_APP_LINK_ICON_URI, Channels.COLUMN_APP_LINK_INTENT_URI,
                Channels.COLUMN_INTERNAL_PROVIDER_DATA};
        String searchMode = extras.getString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, null);
        ContentResolver resolver = context.getContentResolver();

        String sector = " OR " + TvContract.Channels.COLUMN_TYPE + " =? ";
        StringBuilder selection = new StringBuilder(Channels.COLUMN_TYPE + " =? ");
        String[] selectionArgs = TvContractUtils.searchSignalTypeToSelectionArgs(signalType);
        if (selectionArgs != null) {
            int length = selectionArgs.length - 1;
            while (length > 0) {
                selection.append(sector);
                length--;
            }
        }
        try (Cursor cursor = resolver.query(channelsUri, projection, selection.toString(), selectionArgs, null)) {
            InternalProviderData internalProviderData = null;
            String displayName = null;
            String displayNumber = null;
            String rawDisplayName = null;
            String rawDisplayNumber = null;
            String linkIconUri = null;
            String linkIntentUri = null;
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                String channelType = cursor.getString(1);
                int originalNetworkId = cursor.getInt(2);
                int transportStreamId = cursor.getInt(3);
                int serviceId = cursor.getInt(4);
                displayName = cursor.getString(5);
                displayNumber = cursor.getString(6);
                linkIconUri = cursor.getString(7);
                linkIntentUri = cursor.getString(8);
                int type = cursor.getType(9);//InternalProviderData type
                int frequency = 0;
                String ciNumber = null;
                if (type == Cursor.FIELD_TYPE_BLOB) {
                    byte[] internalProviderByteData = cursor.getBlob(9);
                    if (internalProviderByteData != null) {
                        internalProviderData = new InternalProviderData(internalProviderByteData, signalType);
                    }
                } else {
                    if (DEBUG) Log.i(TAG, "COLUMN_INTERNAL_PROVIDER_DATA other type");
                }
                if (internalProviderData != null) {
                    try {
                        frequency = Integer.parseInt((String)internalProviderData.get(Channel.KEY_FREQUENCY));
                    } catch (Exception e) {
                        Log.d(TAG, "cacheRelatedChannel no frequency info Exception = " + e.getMessage());
                    }

                    //frequency = Integer.parseInt((String) internalProviderData.get(Channel.KEY_FREQUENCY));
                    ciNumber = (String) internalProviderData.get(Channel.KEY_CHANNEL_CI_NUMBER);
                    rawDisplayName = (String) internalProviderData.get(Channel.KEY_RAW_DISPLAYNAME);
                    rawDisplayNumber = (String) internalProviderData.get(Channel.KEY_RAW_DISPLAYNUMBER);
                    boolean setRawDisplayNumber = "1".equals(internalProviderData.get(Channel.KEY_SET_DISPLAYNUMBER));
                    boolean setRawDisplayName = "1".equals(internalProviderData.get(Channel.KEY_SET_DISPLAYNAME));
                    if (setRawDisplayNumber) {
                        internalProviderData.put(Channel.KEY_NEW_DISPLAYNUMBER, displayNumber);
                    }
                    if (setRawDisplayName) {
                        internalProviderData.put(Channel.KEY_NEW_DISPLAYNAME, displayName);
                    }
                    if (!TextUtils.isEmpty(linkIconUri)) {
                        internalProviderData.put(Channels.COLUMN_APP_LINK_ICON_URI, linkIconUri);
                    }
                    if (!TextUtils.isEmpty(linkIntentUri)) {
                        internalProviderData.put(Channels.COLUMN_APP_LINK_INTENT_URI, linkIntentUri);
                    }
                }
                String uniqueStr = getUniqueStrForChannel(internalProviderData, channelType,
                        originalNetworkId, transportStreamId, serviceId,
                        frequency, ciNumber);
                channelMap.add(new Pair<>(uniqueStr, rowId));
                if (!isSearched) {
                    saveRawUseSettingValuesToMap(uniqueStr, TextUtils.equals(signalType, "ATV"), channelUseSettingValueMap, internalProviderData);
                    continue;
                }
                if (TvContract.Channels.TYPE_DVB_S.equals(EpgSyncJobService.getChannelTypeFilter())) {
                    // DVB-S transponder search may have several search frequencies
                    if (EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL.equals(searchMode)) {
                        String frequencies = extras.getString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY);
                        if (!TextUtils.isEmpty(frequencies)) {
                            String[] freq_str = frequencies.split(" ");
                            boolean found = false;
                            for (String s : freq_str) {
                                if ((frequency != 0 && TextUtils.isDigitsOnly(s) && Integer.parseInt(s) == frequency)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                saveRawUseSettingValuesToMap(uniqueStr, TextUtils.equals(signalType, "ATV"), channelUseSettingValueMap, internalProviderData);
                            }
                        }
                    }
                } else {
                    int searchFrequency = extras.getInt(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY, 0);
                    if ((frequency != 0 && searchFrequency != frequency
                            && EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL.equals(searchMode))) {
                        saveRawUseSettingValuesToMap(uniqueStr, TextUtils.equals(signalType, "ATV"), channelUseSettingValueMap, internalProviderData);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "updateChannels query Failed = " + e.getMessage());
        }
    }

    //2.find new channel and channels that need to be updated
    private static void handleUpdateOrInsert(
            ArrayList<ContentProviderOperation> ops, ArrayList<Pair<String, Long>> channelMap,
            ArrayMap<String, ArrayMap<String, String>> channelUseSettingValueMap, Context context,
            String inputId, List<Channel> channels, String signalType, PersistableBundle extras) {
        int numberToUpdate = 0;
        int numberToInsert = 0;
        String searchMode = extras.getString(EpgSyncJobService.BUNDLE_KEY_SYNC_SEARCHED_MODE, null);
        // If a channel exists, update it. If not, insert a new one.
        int oldDbCount = channelMap.size();
        int newChannelSeq = 0;
        for (Channel channel : channels) {
            ContentValues values = new ContentValues();
            values.put(Channels.COLUMN_INPUT_ID, inputId);
            values.putAll(channel.toContentValues());
            // If some required fields are not populated, the app may crash, so defaults are used
            if (channel.getPackageName() == null) {
                // If channel does not include package name, it will be added
                values.put(Channels.COLUMN_PACKAGE_NAME, context.getPackageName());
            }
            if (channel.getInputId() == null) {
                // If channel does not include input id, it will be added
                values.put(Channels.COLUMN_INPUT_ID, inputId);
            }
            if (channel.getType() == null) {
                // If channel does not include type it will be added
                values.put(Channels.COLUMN_TYPE, Channels.TYPE_OTHER);
            }

            String channelType = channel.getType();
            int originalNetworkId = channel.getOriginalNetworkId();
            int transportStreamId = channel.getTransportStreamId();
            int serviceId = channel.getServiceId();
            InternalProviderData internalProviderData;
            internalProviderData = channel.getInternalProviderData();
            int frequency = 0;
            String ciNumber = null;
            if (internalProviderData != null) {
                try {
                    frequency = Integer.parseInt((String)internalProviderData.get(Channel.KEY_FREQUENCY));
                } catch (Exception e) {
                    Log.d(TAG, "updateChannels no frequency info Exception = " + e.getMessage());
                }
                try {
                    ciNumber = (String)internalProviderData.get(Channel.KEY_CHANNEL_CI_NUMBER);
                } catch (Exception e) {
                    //Log.d(TAG, "updateChannels no ciNumber info Exception = " + e.getMessage());
                }
            } else {
                Log.e(TAG, "updateChannels no internalProviderData, ignore");
                continue;
            }
            String uniqueStr = getUniqueStrForChannel(internalProviderData, channelType,
                    originalNetworkId, transportStreamId, serviceId,
                    frequency, ciNumber);
            Long rowId = null;
            if (EpgSyncJobService.BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO.equals(searchMode)) {
                // need to remove all old channels
            } else {
                if (newChannelSeq < oldDbCount) {
                    // only get first of channelMap is OK, because it is ordered list
                    String oldUniqueStr = channelMap.get(0).first;
                    if (DEBUG) {
                        Log.i(TAG, "uniqueStr=" + uniqueStr + ", oldUniqueStr=" + oldUniqueStr
                            + ", " + TextUtils.equals(uniqueStr, oldUniqueStr));
                    }
                    if (TextUtils.equals(uniqueStr, oldUniqueStr)) {
                        rowId = channelMap.get(0).second;
                        channelMap.remove(0);
                        newChannelSeq++;
                    } else {
                        // when find first different channel, remove all remaining channels
                        Log.i(TAG, "_ID=" + channelMap.get(0).second + ","
                                + oldUniqueStr + "in db is first different from " + uniqueStr
                                + ", keep number=" + newChannelSeq);
                        newChannelSeq = Integer.MAX_VALUE;
                    }
                }
            }
            ArrayMap<String, String> singleUserSettings = channelUseSettingValueMap.get(uniqueStr);
            if (singleUserSettings != null && singleUserSettings.size() > 0) {
                if (DEBUG) {
                    Log.d(TAG, String.format("Mapping %s to %d", uniqueStr, rowId));
                }
                restoreRawUseSettingValuesToInternalProviderData(uniqueStr, TextUtils.equals(signalType, "ATV"), channelUseSettingValueMap, internalProviderData);
                if ("1".equals(singleUserSettings.get(Channel.KEY_SET_DISPLAYNUMBER))) {
                    values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, singleUserSettings.get(Channel.KEY_NEW_DISPLAYNUMBER));
                }
                if ("1".equals(singleUserSettings.get(Channel.KEY_SET_DISPLAYNAME))) {
                    values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, singleUserSettings.get(Channel.KEY_NEW_DISPLAYNAME));
                }
                if (!TextUtils.isEmpty(singleUserSettings.get(Channels.COLUMN_APP_LINK_ICON_URI))) {
                    values.put(Channels.COLUMN_APP_LINK_ICON_URI, singleUserSettings.get(Channels.COLUMN_APP_LINK_ICON_URI));
                }
                if (!TextUtils.isEmpty(singleUserSettings.get(Channels.COLUMN_APP_LINK_INTENT_URI))) {
                    values.put(Channels.COLUMN_APP_LINK_INTENT_URI, singleUserSettings.get(Channels.COLUMN_APP_LINK_INTENT_URI));
                }
            }

            byte[] dataByte = internalProviderData.toString().getBytes();
            if (dataByte != null && dataByte.length > 0) {
                values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, dataByte);
            } else {
                values.putNull(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
            }
            if (rowId == null) {
                numberToInsert++;
                ops.add(ContentProviderOperation.newInsert(
                                    TvContract.Channels.CONTENT_URI)
                                    .withValues(values)
                                    .build());
                if (DEBUG) {
                    Log.d(TAG, "Adding channel " + channel.getDisplayName());
                }
            } else {
                if (null == searchMode) {
                    if (DEBUG) Log.d(TAG, "not update fvp data if not channel search update");
                    values.remove(TvContract.Channels.COLUMN_APP_LINK_ICON_URI);
                    values.remove(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI);
                }
                numberToUpdate++;
                values.put(Channels._ID, rowId);
                ops.add(ContentProviderOperation.newUpdate(
                                    TvContract.buildChannelUri(rowId))
                                    .withValues(values)
                                    .build());
                if (DEBUG) {
                    Log.d(TAG, "Updating channel " + channel.getDisplayName() + " _ID=" + rowId);
                }
            }
        }
        if (numberToInsert != 0 || numberToUpdate != 0 || channelMap.size() != 0) {
            Log.i(TAG, "signalType: " + signalType
                    + ", numberToInsert:" + numberToInsert
                    + ", numberToUpdate:" + numberToUpdate
                    + ", numberToDelete:" + channelMap.size());
        }
    }

    //3.delete channels that don't exist in DtvKit db anymore
    private static void handleChannelDelete(ArrayList<Pair<String, Long>> channelMap, Context context) {
        ArrayList<Long> ids = new ArrayList<>();
        for (Pair<String, Long> entry : channelMap) {
            Long rowId = entry.second;
            if (DEBUG) {
                Log.d(TAG, " handleChannelDelete add _ID=" + rowId);
            }
            ids.add(rowId);
        }
        handleDelete(ids, context, true);
    }

    // 4.delete old programs
    private static void handleProgramDelete(Context context, String dvbSource) {
        ArrayList<Long> ids = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(TvContract.RecordedPrograms.CONTENT_URI,
                new String[]{TvContract.Programs._ID, TvContract.Programs.COLUMN_INTERNAL_PROVIDER_FLAG4},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long rowId = cursor.getLong(0);
                    if (!cursor.isNull(1)) {
                        int source = cursor.getInt(1);
                        if (TvContractUtils.dvbSourceToInt(dvbSource) == source) {
                            ids.add(rowId);
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "handleProgramDelete Failed = " + e.getMessage());
        }
        handleDelete(ids, context, false);
    }

    //5.deal insert or update channels to tv.db
    private static void syncToDb(ArrayList<ContentProviderOperation> ops, Context context) {
        ContentResolver resolver = context.getContentResolver();
        for (int i = 0; i < ops.size(); i += BATCH_OPERATION_COUNT) {
            int toIndex =
                    Math.min((i + BATCH_OPERATION_COUNT), ops.size());
            ArrayList<ContentProviderOperation> batchOps =
                    new ArrayList<>(ops.subList(i, toIndex));
            if (DEBUG) {
                Log.d(TAG, "syncToDb from fromIndex " + i + " to " + toIndex);
            }
            try {
                resolver.applyBatch(TvContract.AUTHORITY, batchOps);
            } catch (Exception e) {
                Log.e(TAG, "syncToDb Failed = " + e.getMessage());
            } finally {
                Log.d(TAG, "syncToDb size: " + ops.size());
            }
        }
//        if (logos != null && !logos.isEmpty()) {
//            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
//        }
    }

    private static void handleDelete(ArrayList<Long> ids, Context context, boolean channelList) {
        ArrayList<ContentProviderOperation> deleteOps = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        for (Long rowId : ids) {
            if (DEBUG) {
                Log.d(TAG, " handleDelete add _ID=" + rowId);
            }
            if (channelList) {
                deleteOps.add(ContentProviderOperation.newDelete(
                                TvContract.buildChannelUri(rowId))
                        .build());
            } else {
                deleteOps.add(ContentProviderOperation.newDelete(
                                TvContract.buildProgramUri(rowId))
                        .build());
            }
        }
        for (int i = 0; i < deleteOps.size(); i += BATCH_OPERATION_COUNT) {
            int toIndex =
                    Math.min((i + BATCH_OPERATION_COUNT), deleteOps.size());
            ArrayList<ContentProviderOperation> batchOps =
                    new ArrayList<>(deleteOps.subList(i, toIndex));
            if (DEBUG) {
                Log.d(TAG, "handleDelete from fromIndex " + i + " to " + toIndex);
            }
            try {
                resolver.applyBatch(TvContract.AUTHORITY, batchOps);
            } catch (Exception e) {
                Log.e(TAG, "handleDelete Failed = " + e.getMessage());
            }
        }
        deleteOps.clear();
    }

    //cache use settings
    private static void saveRawUseSettingValuesToMap(String uniqueKey, boolean isATv,
            ArrayMap<String, ArrayMap<String, String>> map, InternalProviderData internalProviderData) {
        if (uniqueKey == null || map == null || internalProviderData == null) {
            Log.d(TAG, "saveRawUseSettingValuesToMap USER_SETTING_FLAG and USER_SETTING_FLAG_DEFAULT are not same length or null container");
            return;
        }
        String tempStr = null;
        ArrayMap<String, String> child = new ArrayMap<>();
        for (Map.Entry <String, String> entry: CHANNEL_SETTINGS_DEFAULT.entrySet()) {
            try {
                tempStr = (String) internalProviderData.get(entry.getKey());
            } catch (InternalProviderData.ParseException ignored) {
            }
            if (tempStr == null) {
                tempStr = entry.getValue();
            }
            child.put(entry.getKey(), tempStr);
        }
        if (isATv) {
            for (Map.Entry <String, String> entry: ATV_EXTRA_SETTINGS_DEFAULT.entrySet()) {
                try {
                    tempStr = (String) internalProviderData.get(entry.getKey());
                } catch (InternalProviderData.ParseException ignored) {
                }
                if (tempStr == null) {
                    tempStr = entry.getValue();
                }
                child.put(entry.getKey(), tempStr);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "saveRawUseSettingValuesToMap uniqueKey:" + uniqueKey + "," + child);
        }
        map.put(uniqueKey, child);
    }

    //restore use settings
    private static void restoreRawUseSettingValuesToInternalProviderData(String uniqueKey, boolean isATv,
        ArrayMap<String, ArrayMap<String, String>> map, InternalProviderData internalProviderData) {
        if (uniqueKey == null || map == null || internalProviderData == null) {
            Log.w(TAG, "null data");
            return;
        }
        String tempStr;
        ArrayMap<String, String> child = map.get(uniqueKey);
        if (child == null) {
            return;
        }
        for (Map.Entry <String, String> entry: CHANNEL_SETTINGS_DEFAULT.entrySet()) {
            try {
                tempStr = child.get(entry.getKey());
                if (Channel.KEY_HIDDEN.equals(entry.getKey())) {
                    tempStr = (String)internalProviderData.get(entry.getKey());
                }
                if (tempStr == null) {
                    tempStr = entry.getValue();
                }
                internalProviderData.put(entry.getKey(), tempStr);
                if (DEBUG) {
                    Log.d(TAG, "restoreRawUseSettingValuesToInternalProviderData "
                            + entry.getKey() + "->" + tempStr);
                }
            } catch (InternalProviderData.ParseException ignored) {
            }
        }
        if (isATv) {
            for (Map.Entry <String, String> entry: ATV_EXTRA_SETTINGS_DEFAULT.entrySet()) {
                try {
                    tempStr = child.get(entry.getKey());
                    if (tempStr == null) {
                        tempStr = entry.getValue();
                    }
                    internalProviderData.put(entry.getKey(), tempStr);
                    if (DEBUG) {
                        Log.d(TAG, "restoreRawUseSettingValuesToInternalProviderData "
                                + entry.getKey() + "->" + tempStr);
                    }
                } catch (InternalProviderData.ParseException ignored) {
                }
            }
        }
    }

    public static String getUniqueStrForChannel(InternalProviderData internalProviderData,
        String channelType, int originalNetworkId, int transportStreamId,
        int serviceId, int frequency, String ciNumber) {
        boolean isATv = Channels.TYPE_PAL.equals(channelType)
                || Channels.TYPE_NTSC.equals(channelType)
                || Channels.TYPE_SECAM.equals(channelType);
        StringBuilder result = new StringBuilder(channelType
                + "-" + originalNetworkId
                + "-" + transportStreamId
                + "-" + serviceId
                + "-" + ciNumber);
        int tuneFreq = frequency;
        try {
            if (isATv) {
                String signal = (String) internalProviderData.get("signal_type");
                tuneFreq += internalProviderData.getInt("fine_tune");
                result.append("-").append(signal);
            }
        } catch (Exception ignored) {}
        result = result.append("-").append((tuneFreq / 1000));
        if (DEBUG) {
            Log.d(TAG,  "getUniqueStrForChannel::" + result);
        }
        return result.toString();
    }

    /**
     * Builds a map of available channels.
     *
     * @param resolver Application's ContentResolver.
     * @param inputId The ID of the TV input service that provides this TV channel.
     * @return LongSparseArray mapping each channel's {@link TvContract.Channels#_ID} to the
     * Channel object.
     */
    public static LinkedList<Channel> buildChannelMap(ContentResolver resolver, String inputId,
            int frequency, String selection, String[] selectionArgs, long firstChannelId) {
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        LinkedList<Channel> channelList = new LinkedList<>();
        Channel firstChannel = null;
        try (Cursor cursor = resolver.query(uri, Channel.PROJECTION, selection, selectionArgs, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                if (DEBUG) {
                    Log.d(TAG, "Cursor is null or found no results");
                }
                return null;
            }

            while (cursor.moveToNext()) {
                Channel nextChannel = Channel.fromCursor(cursor);
                if (firstChannelId == nextChannel.getId()) {
                    firstChannel = nextChannel;
                    continue;
                }
                if (frequency != -1 && nextChannel.getFrequency() == frequency) {
                    channelList.addFirst(nextChannel);
                } else {
                    channelList.add(nextChannel);
                }
            }
            if (firstChannel != null) {
                channelList.addFirst(firstChannel);
            }
        } catch (Exception e) {
            Log.d(TAG, "Content provider query: " + Arrays.toString(e.getStackTrace()));
            return null;
        }
        return channelList;
    }

    /**
     * Returns the {@link Channel} with specified channel URI.
     * @param resolver {@link ContentResolver} used to query database.
     * @param dvbUri URI of channel to play.
     * @return An channel object with specified dvbUri.
     */
    public static Channel getChannelWithDvbUri(ContentResolver resolver, String inputId, String dvbUri) {
        Channel channel = null;
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        if (TextUtils.isEmpty(dvbUri)) {
            return null;
        }
        try (Cursor cursor = resolver.query(
                uri, Channel.PROJECTION, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            while (cursor.moveToNext()) {
                Channel nextChannel = Channel.fromCursor(cursor);
                InternalProviderData data = nextChannel.getInternalProviderData();
                if (data == null) {
                    continue;
                }
                String dvb = (String) data.get("dvbUri");
                if (dvbUri.equals(dvb)) {
                    channel = nextChannel;
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getChannelWithDvbUri ", e);
        }
        return channel;
    }

    /**
     * Returns the current list of channels your app provides.
     *
     * @param resolver Application's ContentResolver.
     * @return List of channels.
     */
    public static List<Channel> getChannels(ContentResolver resolver, String inputId) {
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        List<Channel> channels = new ArrayList<>();
        // TvProvider returns programs in chronological order by default.
        try (Cursor cursor = resolver.query(uri, Channel.PROJECTION, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return channels;
            }
            while (cursor.moveToNext()) {
                channels.add(Channel.fromCursor(cursor));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to get channels", e);
        }
        return channels;
    }

    /**
     * Returns the {@link Channel} with specified channel URI.
     * @param resolver {@link ContentResolver} used to query database.
     * @param channelUri URI of channel.
     * @return An channel object with specified channel URI.
     */
    public static Channel getChannel(ContentResolver resolver, Uri channelUri) {
        if (channelUri == null) {
            return null;
        }
        try (Cursor cursor = resolver.query(channelUri, Channel.PROJECTION, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                Log.w(TAG, "No channel matches " + channelUri);
                return null;
            }
            cursor.moveToNext();
            return Channel.fromCursor(cursor);
        } catch (Exception e) {
            Log.w(TAG, "Unable to get the channel with URI " + channelUri, e);
            return null;
        }
    }

    /**
     * Returns the {@link Channel} with specified id.
     * @param resolver {@link ContentResolver} used to query database.
     * @param originalNetworkId of channel.
     * @param transportStreamId of channel.
     * @param serviceId of channel.
     * @return An channel object with specified id.
     */
    public static Channel getChannelByNetworkTransportServiceId(ContentResolver resolver,
        int originalNetworkId, int transportStreamId, int serviceId) {
        Channel result = null;
        try (Cursor cursor = resolver.query(Channels.CONTENT_URI, Channel.PROJECTION, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                Log.w(TAG, "getChannelByNetworkTransportServiceId No channels");
                return null;
            }
            Channel channel;
            int networkId;
            int streamId;
            int service;
            while (cursor.moveToNext()) {
                channel = Channel.fromCursor(cursor);
                networkId = channel.getOriginalNetworkId();
                streamId = channel.getTransportStreamId();
                service = channel.getServiceId();
                if (networkId == originalNetworkId && streamId == transportStreamId && serviceId == service) {
                    result = channel;
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getChannelByNetworkTransportServiceId Exception " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns the current list of programs on a given channel.
     *
     * @param resolver Application's ContentResolver.
     * @param channelUri Channel's Uri.
     * @return List of programs.
     */
    public static List<Program> getPrograms(ContentResolver resolver, Uri channelUri) {
        if (channelUri == null) {
            return null;
        }
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri);
        List<Program> programs = new ArrayList<>();
        // TvProvider returns programs in chronological order by default.
        try (Cursor cursor = resolver.query(uri, Program.PROJECTION, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return programs;
            }
            while (cursor.moveToNext()) {
                programs.add(Program.fromCursor(cursor));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to get programs for " + channelUri, e);
        }
        return programs;
    }

    /**
     * Returns the program that is scheduled to be playing now on a given channel.
     *
     * @param resolver Application's ContentResolver.
     * @param channelUri Channel's Uri.
     * @return The program that is scheduled for now in the EPG.
     */
    public static Program getCurrentProgram(ContentResolver resolver, Uri channelUri) {
        List<Program> programs = getPrograms(resolver, channelUri);
        if (programs == null) {
            return null;
        }
        long nowMs = System.currentTimeMillis();
        for (Program program : programs) {
            if (program.getStartTimeUtcMillis() <= nowMs && program.getEndTimeUtcMillis() > nowMs) {
                return program;
            }
        }
        return null;
    }

    public static Program getCurrentProgramExt(ContentResolver resolver, Uri channelUri, long nowMs) {
        List<Program> programs = getPrograms(resolver, channelUri);
        if (programs == null) {
            return null;
        }
        for (Program program : programs) {
            if (program.getStartTimeUtcMillis() <= nowMs && program.getEndTimeUtcMillis() > nowMs) {
                return program;
            }
        }
        return null;
    }

    /**
     * Returns the program that is scheduled to be playing after a given program on a given channel.
     *
     * @param resolver Application's ContentResolver.
     * @param channelUri Channel's Uri.
     * @param currentProgram Program which plays before the desired program.If null, returns current
     *                       program
     * @return The program that is scheduled after given program in the EPG.
     */
    public static Program getNextProgram(ContentResolver resolver, Uri channelUri,
                                         Program currentProgram) {
        if (currentProgram == null) {
            return getCurrentProgram(resolver, channelUri);
        }
        List<Program> programs = getPrograms(resolver, channelUri);
        if (programs == null) {
            return null;
        }
        int currentProgramIndex = programs.indexOf(currentProgram);
        if (currentProgramIndex + 1 < programs.size()) {
            return programs.get(currentProgramIndex + 1);
        }
        return null;
    }

    private static void insertUrl(ContentResolver resolver, Uri contentUri, URL sourceUrl) {
        if (DEBUG) {
            Log.d(TAG, "Inserting " + sourceUrl + " to " + contentUri);
        }
        try (InputStream is = sourceUrl.openStream(); OutputStream os = resolver.openOutputStream(contentUri)) {
            copy(is, os);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to write " + sourceUrl + "  to " + contentUri, ioe);
        }
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    /**
     * Parses a string of comma-separated ratings into an array of {@link TvContentRating}.
     *
     * @param commaSeparatedRatings String containing various ratings, separated by commas.
     * @return An array of TvContentRatings.
     */
    public static TvContentRating[] stringToContentRatings(String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return null;
        }
        String[] ratings = commaSeparatedRatings.split("\\s*,\\s*");
        TvContentRating[] contentRatings = new TvContentRating[ratings.length];
        for (int i = 0; i < contentRatings.length; ++i) {
            contentRatings[i] = TvContentRating.unflattenFromString(ratings[i]);
        }
        return contentRatings;
    }

    /**
     * Flattens an array of {@link TvContentRating} into a String to be inserted into a database.
     *
     * @param contentRatings An array of TvContentRatings.
     * @return A comma-separated String of ratings.
     */
    public static String contentRatingsToString(TvContentRating[] contentRatings) {
        if (contentRatings == null || contentRatings.length == 0) {
            return null;
        }
        final String DELIMITER = ",";
        StringBuilder ratings = new StringBuilder(contentRatings[0].flattenToString());
        for (int i = 1; i < contentRatings.length; ++i) {
            ratings.append(DELIMITER);
            ratings.append(contentRatings[i].flattenToString());
        }
        return ratings.toString();
    }

    private TvContractUtils() {
    }

    private static class InsertLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
        private final ContentResolver mContentResolver;

        InsertLogosTask(Context context) {
            mContentResolver = context.getContentResolver();
        }

        @Override
        public Void doInBackground(Map<Uri, String>... logosList) {
            for (Map<Uri, String> logos : logosList) {
                for (Uri uri : logos.keySet()) {
                    try {
                        insertUrl(mContentResolver, uri, new URL(logos.get(uri)));
                    } catch (MalformedURLException e) {
                        Log.e(TAG, "Can't load " + logos.get(uri), e);
                    }
                }
            }
            return null;
        }
    }

    public static String getStringFromChannelInternalProviderData(Channel channel, String key, String defaultVal) {
        String result = defaultVal;
        try {
            if (channel != null) {
                InternalProviderData providerData = channel.getInternalProviderData();
                if (providerData != null) {
                    result = (String) providerData.get(key);
                }
            }
        } catch(Exception e) {
            Log.i(TAG, "getStringFromChannelInternalProviderData Exception " + e.getMessage());
        }
        return result;
    }

    public static boolean getBooleanFromChannelInternalProviderData(Channel channel, String key, boolean defaultVal) {
        boolean result = defaultVal;
        try {
            if (channel != null) {
                InternalProviderData providerData = channel.getInternalProviderData();
                if (providerData != null) {
                    result = Boolean.parseBoolean((String) providerData.get(key));
                }
            }
        } catch(InternalProviderData.ParseException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String toSignalType(String type) {
        switch (type) {
            case Channels.TYPE_DVB_T:
            case Channels.TYPE_DVB_T2:
                return "DVB-T";
            case Channels.TYPE_DVB_C:
            case Channels.TYPE_DVB_C2:
                return "DVB-C";
            case Channels.TYPE_DVB_S:
            case Channels.TYPE_DVB_S2:
                return "DVB-S";
            case Channels.TYPE_ISDB_T:
                return "ISDB-T";
            case Channels.TYPE_ISDB_C:
                return "ISDB-C";
            case Channels.TYPE_NTSC:
            case Channels.TYPE_PAL:
            case Channels.TYPE_SECAM:
                return "ATV";
            default:
                return type;
        }
    }

    /* in tis, use unified format with TvContract.Channels.TYPE_ */
    public static String searchSignalTypeToChannelType(String searchSignalType) {
        String result = TvContract.Channels.TYPE_OTHER;

        if (TextUtils.isEmpty(searchSignalType)) {
            return null;
        }
        switch (searchSignalType) {
            case "DVB-T":
            case "DVB_T":
                result = TvContract.Channels.TYPE_DVB_T;
                break;
            case "DVB-T2":
            case "DVB_T2":
                result = TvContract.Channels.TYPE_DVB_T2;
                break;
            case "DVB-C":
            case "DVB_C":
                result = TvContract.Channels.TYPE_DVB_C;
                break;
            case "DVB-C2":
            case "DVB_C2":
                result = TvContract.Channels.TYPE_DVB_C2;
                break;
            case "DVB-S":
            case "DVB_S":
                result = TvContract.Channels.TYPE_DVB_S;
                break;
            case "DVB-S2":
            case "DVB_S2":
                result = TvContract.Channels.TYPE_DVB_S2;
                break;
            case "ISDB-T":
            case "ISDB_T":
                result = TvContract.Channels.TYPE_ISDB_T;
                break;
            case "ISDB-C":
            case "ISDB_C":
                result = TvContract.Channels.TYPE_ISDB_C;
                break;
            default:
                break;
        }
        return result;
    }

    public static String[] searchSignalTypeToSelectionArgs(String searchSignalType) {
        if (TextUtils.isEmpty(searchSignalType)) {
            return null;
        }
        switch (searchSignalType) {
            case "DVB-T":
            case "DVB_T":
                return new String[]{Channels.TYPE_DVB_T, Channels.TYPE_DVB_T2};
            case "DVB-C":
            case "DVB_C":
                return new String[]{Channels.TYPE_DVB_C, Channels.TYPE_DVB_C2};
            case "DVB-S":
            case "DVB_S":
                return new String[]{Channels.TYPE_DVB_S, Channels.TYPE_DVB_S2};
            case "ISDB-T":
            case "ISDB_T":
                return new String[]{Channels.TYPE_ISDB_T};
            case "ISDB-C":
            case "ISDB_C":
                return new String[]{Channels.TYPE_ISDB_C};
            case "ATV":
                return new String[]{Channels.TYPE_NTSC, Channels.TYPE_PAL, Channels.TYPE_SECAM};
            default:
                break;
        }
        return null;
    }

    public static String dvbSourceToChannelTypeString(int source) {
        String result = TvContract.Channels.TYPE_DVB_T;
        switch (source) {
            case SIGNAL_COFDM:
                result = TvContract.Channels.TYPE_DVB_T;
                break;
            case SIGNAL_QAM:
                result = TvContract.Channels.TYPE_DVB_C;
                break;
            case SIGNAL_QPSK:
                result = TvContract.Channels.TYPE_DVB_S;
                break;
            case SIGNAL_ISDBT:
                result = TvContract.Channels.TYPE_ISDB_T;
                break;
            default:
                break;
        }
        return result;
    }

    public static int dvbSourceToInt(String sourceName) {
        int source = 0;
        String handle = sourceName.replaceAll("-", "_");
        if (handle.contains("DVB_C")) {
            source = SIGNAL_QAM;
        } else if (handle.contains("DVB_T")) {
            source = SIGNAL_COFDM;
        } else if (handle.contains("DVB_S")) {
            source = SIGNAL_QPSK;
        } else if (handle.contains("ISDB_T")) {
            source = SIGNAL_ISDBT;
        }
        return source;
    }

    public static String videoStdToType(int vStd) {
        String type;
        switch (vStd) {
            case ATV_VIDEO_STD_NTSC:
                type = TvContract.Channels.TYPE_NTSC;
                break;
            case ATV_VIDEO_STD_SECAM:
                type = TvContract.Channels.TYPE_SECAM;
                break;
            default:
                type = TvContract.Channels.TYPE_PAL;
        }
        return type;
    }


    /* tv_dtvkit_system in database.db is DVB-T format, NOT TYPE_DVB_T */
    public static String dvbSourceToDbString(int source) {
        String result = "DVB-T";

        switch (source) {
            case SIGNAL_COFDM:
                result = "DVB-T";
                break;
            case SIGNAL_QAM:
                result = "DVB-C";
                break;
            case SIGNAL_QPSK:
                result = "DVB-S";
                break;
            case SIGNAL_ISDBT:
                result = "ISDB-T";
                break;
            default:
                break;
        }
        return result;
    }
}
