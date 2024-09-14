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

package com.droidlogic.dtvkit.companionlibrary;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

import android.support.annotation.Nullable;
import android.util.Log;

import com.droidlogic.dtvkit.companionlibrary.model.Channel;
import com.droidlogic.dtvkit.companionlibrary.model.EventPeriod;
import com.droidlogic.dtvkit.companionlibrary.model.Program;

import org.dtvkit.companionlibrary.EpgSyncTask;

import java.util.List;

public abstract class EpgSyncJobService extends Service {
    private static final String TAG = "EpgSyncJob";
    protected static final boolean DEBUG = false;
    public static final String DTVKIT_INPUTID = "com.droidlogic.dtvkit.inputsource/.DtvkitTvInput/HW19";
    /** The action that will be broadcast when the job service's status changes. */
    public static final String ACTION_SYNC_STATUS_CHANGED =
            EpgSyncJobService.class.getPackage().getName() + ".ACTION_SYNC_STATUS_CHANGED";
    /** The key representing the component name for the app's TvInputService. */
    public static final String BUNDLE_KEY_INPUT_ID = EpgSyncJobService.class.getPackage().getName()
            + ".bundle_key_input_id";
    /** The key representing the number of channels that have been scanned and populated in the EPG.
     */
    public static final String BUNDLE_KEY_CHANNELS_SCANNED =
            EpgSyncJobService.class.getPackage().getName() + ".bundle_key_channels_scanned";
    /** The key representing the total number of channels for this input. */
    public static final String BUNDLE_KEY_CHANNEL_COUNT =
            EpgSyncJobService.class.getPackage().getName() + ".bundle_key_channel_count";
    /** The key representing the most recently scanned channel display name. */
    public static final String BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NAME =
            EpgSyncJobService.class.getPackage().getName() +
                    ".bundle_key_scanned_channel_display_name";
    /** The key representing the most recently scanned channel display number. */
    public static final String BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NUMBER =
            EpgSyncJobService.class.getPackage().getName() +
                    ".bundle_key_scanned_channel_display_number";
    /** The key representing the error that occurred during an EPG sync */
    public static final String BUNDLE_KEY_ERROR_REASON =
            EpgSyncJobService.class.getPackage().getName() + ".bundle_key_error_reason";

    /** The name for the {@link android.content.SharedPreferences} file used for storing syncing
     * metadata. */
    public static final String PREFERENCE_EPG_SYNC = EpgSyncJobService.class.getPackage().getName()
            + ".preference_epg_sync";

    /** The status of the job service when syncing has begun. */
    public static final String SYNC_STARTED = "sync_started";
    /** The status of the job service when a channel has been scanned and the EPG for that channel
     * has been populated. */
    public static final String SYNC_SCANNED = "sync_scanned";
    /** The status of the job service when syncing has completed. */
    public static final String SYNC_FINISHED = "sync_finished";
    /** The status of the job when a problem occurs during syncing. A {@link #SYNC_FINISHED}
     *  broadcast will still be sent when the service is done. This status can be used to identify
     *  specific issues in your EPG sync.
     * */
    public static final String SYNC_ERROR = "sync_error";
    /** The key corresponding to the job service's status. */
    public static final String SYNC_STATUS = "sync_status";

    public static final String SYNC_REASON = "sync_reason";
    public static final String SYNC_BY_SCAN = "sync_by_scan";
    public static final String SYNC_BY_OTHER = "sync_by_other";

    /** Indicates that the EPG sync was canceled before being completed. */
    public static final int ERROR_EPG_SYNC_CANCELED = 1;
    /** Indicates that the input id was not defined and the EPG sync cannot complete. */
    public static final int ERROR_INPUT_ID_NULL = 2;
    /** Indicates that no programs were found. */
    public static final int ERROR_NO_PROGRAMS = 3;
    /** Indicates that no channels were found. */
    public static final int ERROR_NO_CHANNELS = 4;
    /** Indicates an error occurred when updating programs in the database */
    public static final int ERROR_DATABASE_INSERT = 5;

    public static final String BUNDLE_KEY_SYNC_NOW_NEXT = "BUNDLE_KEY_SYNC_NOW_NEXT";
    public static final String BUNDLE_KEY_SYNC_CHANNEL_ONLY = "BUNDLE_KEY_SYNC_CHANNEL_ONLY";
    public static final String BUNDLE_KEY_SYNC_SEARCHED_CHANNEL = "BUNDLE_KEY_SYNC_SEARCHED_CHANNEL";
    public static final String BUNDLE_KEY_SYNC_SEARCHED_LCN_CONFLICT = "BUNDLE_KEY_SYNC_SEARCHED_LCN_CONFLICT";
    public static final String BUNDLE_KEY_SYNC_SEARCHED_MODE = "BUNDLE_KEY_SYNC_SEARCHED_MODE";
    public static final String BUNDLE_VALUE_SYNC_SEARCHED_MODE_MANUAL = "MANUAL";
    public static final String BUNDLE_VALUE_SYNC_SEARCHED_MODE_AUTO = "AUTO";
    public static final String BUNDLE_VALUE_SYNC_SEARCHED_MODE_UPDATE = "UPDATE";
    public static final String BUNDLE_KEY_SYNC_HYBRID_MODE = "BUNDLE_KEY_SYNC_HYBRID_MODE";
    public static final String BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE = "BUNDLE_KEY_SYNC_SEARCHED_SIGNAL_TYPE";
    public static final String BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY = "BUNDLE_KEY_SYNC_SEARCHED_FREQUENCY";
    public static final String BUNDLE_KEY_SYNC_REASON = "BUNDLE_KEY_SYNC_REASON";
    public static final String BUNDLE_KEY_SYNC_FROM = "BUNDLE_KEY_SYNC_FROM";
    public static final String BUNDLE_KEY_SYNC_PARAMETERS = "BUNDLE_KEY_SYNC_PARAMETERS";
    public static final String BUNDLE_KEY_SYNC_CANCEL = "BUNDLE_KEY_SYNC_CANCEL";
    public static final String BUNDLE_KEY_SYNC_FREQUENCY = "BUNDLE_KEY_SYNC_FREQUENCY";
    public static final String BUNDLE_KEY_SYNC_NEED_UPDATE_CHANNEL = "BUNDLE_KEY_SYNC_NEED_UPDATE_CANCEL";
    public static final String BUNDLE_KEY_SYNC_CURRENT_PLAY_CHANNEL_ID = "BUNDLE_KEY_SYNC_CURRENT_PLAY_CHANNEL_ID";

    private static final Object mContextLock = new Object();
    protected Context mContext;
    private static String mChannelTypeFilter;

    /**
     * Returns the channels that your app contains.
     *
     * @return The list of channels for your app.
     */
    public abstract List<Channel> getChannels(boolean syncCurrent);
    public abstract boolean checkSignalTypesMatch(String signalType);

    /**
     * Returns the programs that will appear for each channel.
     *
     * @param channelUri The Uri corresponding to the channel.
     * @param channel The channel your programs will appear on.
     * @param startMs The starting time in milliseconds since the epoch to generate programs. If
     * your program starts before this starting time, it should be be included.
     * @param endMs The ending time in milliseconds since the epoch to generate programs. If your
     * program starts before this ending time, it should be be included.
     * @return A list of programs for a given channel.
     */
    public abstract void getProgramsForChannel(List<Program> container, Uri channelUri, Channel channel,
            long startMs, long endMs);

    public abstract int getProgramsForChannelByBatch(List<Program> container, Uri channelUri, Channel channel);

    public abstract List<Program> getAllProgramsForChannel(Uri channelUri, Channel channel);

    public abstract List<Program> getNowNextProgramsForChannel(Uri channelUri, Channel channel);

    public abstract List<EventPeriod> getListOfUpdatedEventPeriods();

    private EpgSyncTask mEpgSyncTask;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, "Created EpgSyncJobService");
        }
        synchronized (mContextLock) {
            if (mContext == null) {
                mContext = getApplicationContext();
            }
        }
        mEpgSyncTask = new EpgSyncTask(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleJob(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Returns {@code true} if the {@code oldProgram} program is the same as the
     * {@code newProgram} program but should update metadata. This updates the database instead
     * of deleting and inserting a new program to keep the user's intent, eg. recording this
     * program.
     */
    public boolean shouldUpdateProgramMetadata(Program oldProgram, Program newProgram) {
        // NOTE: Here, we update the old program if it has the same title and overlaps with the
        // new program. The test logic is just an example and you can modify this. E.g. check
        // whether the both programs have the same program ID if your EPG supports any ID for
        // the programs.
        return /*oldProgram.getTitle() != null*/
                /*&& oldProgram.getTitle().equals(newProgram.getTitle())*/
                oldProgram.getStartTimeUtcMillis() < newProgram.getEndTimeUtcMillis()
                && newProgram.getStartTimeUtcMillis() < oldProgram.getEndTimeUtcMillis();
    }

    private void scheduleJob(Intent intent) {
        if (mEpgSyncTask != null) {
            mEpgSyncTask.run(intent);
        }
    }

    public void cancelAllSyncRequests() {
        if (mEpgSyncTask != null) {
            mEpgSyncTask.run(null);
        }
    }

    public static void setChannelTypeFilter(String type) {
        mChannelTypeFilter = type;
    }

    public static String getChannelTypeFilter() {
        return mChannelTypeFilter;
    }
}
