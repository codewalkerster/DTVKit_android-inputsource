/*
 * Copyright (c) 2014 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description: JAVA file
 */

package org.droidlogic.dtvkit;

import android.content.ContentValues;
import android.content.Context;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import com.droidlogic.dtvkit.companionlibrary.model.Channel;
import com.droidlogic.dtvkit.companionlibrary.model.InternalProviderData;

public class TvChannelSetting {
    private static String TAG = "TvChannelSetting";

    /* format:
     * format & 0xff000000: PAL/NTSC/SECAM in atv demod and tuner.
     * format & 0x00ffffff: CVBS format in atv decoder.
     *
     * mode:
     * PAL = 1,
     * NTSC = 2,
     * SECAM = 3,
     */

    public static final int ATV_VIDEO_STD_AUTO          = 0;
    public static final int ATV_VIDEO_STD_PAL           = 1;
    public static final int ATV_VIDEO_STD_NTSC          = 2;
    public static final int ATV_VIDEO_STD_SECAM         = 3;

    public static final int V4L2_COLOR_STD_PAL   = 0x04000000;
    public static final int V4L2_COLOR_STD_NTSC  = 0x08000000;
    public static final int V4L2_COLOR_STD_SECAM = 0x10000000;
    public static final int V4L2_STD_PAL_M       = 0x00000100;
    public static final int V4L2_STD_PAL_N       = 0x00000200;
    public static final int V4L2_STD_PAL_Nc      = 0x00000400;
    public static final int V4L2_STD_PAL_60      = 0x00000800;

    private static int convertAtvCvbsFormat(int format, int mode) {
        switch (mode) {
            case ATV_VIDEO_STD_PAL:
                return ((format & 0x00FFFFFF) | V4L2_COLOR_STD_PAL);
            case ATV_VIDEO_STD_NTSC:
                return ((format & 0x00FFFFFF) | V4L2_COLOR_STD_NTSC);
            case ATV_VIDEO_STD_SECAM:
                return ((format & 0x00FFFFFF) | V4L2_COLOR_STD_SECAM);
            default:
                Log.e(TAG, "convertAtvCvbsFormat error mode == " + mode);
                break;
        }

        return format;
    }

    /* video mode:
     * PAL = 1,
     * NTSC = 2,
     * SECAM = 3,
     */
    public void setAtvChannelVideo(Context context, Channel channel, int mode) {
        if (channel != null) {
            InternalProviderData data = channel.getInternalProviderData();
            if (data == null) {
                Log.e(TAG, "setAtvChannelVideo error data == null");
                return ;
            }
            Log.d(TAG, "setAtvChannelVideo mode: " + mode);

            try {
                data.put("video_std", mode);
                data.put("vfmt", convertAtvCvbsFormat(Integer.parseInt((String) data.get("vfmt")), mode));
                JSONArray args = new JSONArray();
                args.put(Integer.parseInt((String) data.get("unikey"))); //unikey
                args.put(channel.getAntennaType()); //atv_sigtype
                int toTuneFrequency = Integer.parseInt((String) data.get("frequency")) +
                    Integer.parseInt((String) data.get("fine_tune"));
                args.put(toTuneFrequency); //freq
                args.put(mode); //vstd
                args.put(Integer.parseInt((String) data.get("audio_std"))); //astd
                args.put(convertAtvCvbsFormat(Integer.parseInt((String) data.get("vfmt")), mode)); //vfmt
                JSONObject resultObj = DtvkitGlueClient.getInstance().request("AtvPlayer.resetFrontEndPara", args);
            } catch (Exception e) {
               Log.d(TAG, "[debug]channel : " + channel);
               e.printStackTrace();
            }
        } else {
            Log.e(TAG, "setAtvChannelVideo error channel == null");
        }
    }

    /* audio mode:
     * DK = 0,
     * I = 1,
     * BG = 2,
     * M = 3,
     * L = 4,
     */
    public void setAtvChannelAudio(Context context, Channel channel, int mode) {
        if (channel != null) {
            InternalProviderData data = channel.getInternalProviderData();
            if (data == null) {
                Log.e(TAG, "setAtvChannelAudio error data == null");
                return ;
            }
            Log.d(TAG, "setAtvChannelAudio mode: " + mode);

            try {
                data.put("audio_std", mode);
                JSONArray args = new JSONArray();
                args.put(Integer.parseInt((String) data.get("unikey"))); //unikey
                args.put(channel.getAntennaType()); //atv_sigtype
                int toTuneFrequency = Integer.parseInt((String) data.get("frequency")) +
                   Integer.parseInt((String) data.get("fine_tune"));
                args.put(toTuneFrequency); //freq
                args.put(Integer.parseInt((String) data.get("video_std"))); //vstd
                args.put(mode); //astd
                args.put(Integer.parseInt((String) data.get("vfmt"))); //vfmt
                JSONObject resultObj = DtvkitGlueClient.getInstance().request("AtvPlayer.resetFrontEndPara", args);
            } catch (Exception e) {
                Log.d(TAG, "[debug]channel : " + channel);
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "setAtvChannelAudio error channel == null");
        }
    }

    public void setAtvFineTune(Context context, Channel channel, int finetune) {
        if (channel != null) {
            InternalProviderData data = channel.getInternalProviderData();
            if (data == null) {
                Log.e(TAG, "setAtvFineTune error data == null");
                return ;
            }
            Log.d(TAG, "setAtvFineTune finetune: " + finetune);

            try {
                data.put("fine_tune", finetune);
                JSONArray args = new JSONArray();
                args.put(Integer.parseInt((String) data.get("unikey")));
                args.put(channel.getAntennaType());
                int toTuneFrequency = Integer.parseInt((String) data.get("frequency")) + finetune;
                args.put(toTuneFrequency);
                args.put(Integer.parseInt((String) data.get("video_std")));
                args.put(Integer.parseInt((String) data.get("audio_std")));
                args.put(Integer.parseInt((String) data.get("vfmt")));
                JSONObject resultObj = DtvkitGlueClient.getInstance().request("AtvPlayer.resetFrontEndPara", args);
            } catch (Exception e) {
                Log.d(TAG, "[debug]channel : " + channel);
                e.printStackTrace();
            }

        } else {
            Log.e(TAG, "setAtvFineTune error channel == null");
        }
    }
}
