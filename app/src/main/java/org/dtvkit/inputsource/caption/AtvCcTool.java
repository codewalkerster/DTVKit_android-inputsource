package org.dtvkit.inputsource.caption;

import android.content.Context;
import android.media.tv.TvTrackInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.droidlogic.app.DataProviderManager;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AtvCcTool {
    private static AtvCcTool mInstance;
    private int mCcIndex = -1;

    private final static int SIG_FMT_NULL = 0;
    private final static int SIG_FMT_N_M = 0x601;
    private final static int SIG_FMT_N_443 = 0x602;
    private final static int SIG_FMT_PAL_M = 0x604;
    private final static int SIG_FMT_PAL_60 = 0x605;
    private final static int SIG_STATUS_STABLE = 4;
    private static final String ATV_CC_TRACK_INDEX = "atv_cc_index";

    public static AtvCcTool getInstance() {
        if (mInstance == null)
            mInstance = new AtvCcTool();
        return mInstance;
    }

    public void startInit(@NonNull Context context) {
        getSavedCcIndexAsync(context);
    }

    public synchronized int getSavedCcChannel() {
        return mCcIndex;
    }

    public String startCC(@NonNull Context context, int index) {
        String trackId = null;
        boolean ret = startCcInMw(index + 1);
        if (ret) {
            if (mCcIndex != index) {
                saveCcIndexAsync(context, index);
                mCcIndex = index;
            }
            trackId = createTrackId(index);
        }
        return trackId;
    }

    public void stopCC() {
        stopCcInMw();
    }

    public List<TvTrackInfo> getAtvCcTracks() {
        List<TvTrackInfo> info = new ArrayList<>();
        int fmt = getAtvVideoFmt();
        if (fmt == SIG_FMT_N_M || fmt == SIG_FMT_N_443
                || fmt == SIG_FMT_PAL_M
                || fmt == SIG_FMT_PAL_60) {
            for (int i = 0;i < 8; i ++) {
                String langPrefix = (i < 4) ? "CC" : "TX";
                int idSuffix = (i >= 4) ? (i - 4) : i;
                info.add(new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE,
                        createTrackId(i)).setLanguage(langPrefix + (idSuffix + 1)).build());
            }
        }
        return info;
    }

    public boolean supportAtv() {
        return "isdb_server".equals(getDtvSystem());
    }

    public void selectCCTrack(@NonNull Context context, final String trackId) {
        int ccIndex = getCcChannelIdFromTrack(trackId);
        if (ccIndex == -1) {
            stopCC();
            saveCcIndexAsync(context, ccIndex);
            mCcIndex = ccIndex;
        } else {
            startCC(context, ccIndex);
        }
    }

    private int getAtvVideoFmt() {
        int fmt = SIG_FMT_NULL;
        try {
            JSONObject o =
                    DtvkitGlueClient.getInstance().request("AtvPlayer.getCurSignalInfo",
                            new JSONArray());
            JSONObject data = o.optJSONObject("data");
            if (data != null) {
                int status = data.optInt("status");
                if (status == SIG_STATUS_STABLE) {
                    fmt = data.optInt("fmt");
                }
            }
        } catch (Exception ignore) {}
        return fmt;
    }

    private boolean startCcInMw(int channel) {
        boolean ret = false;
        try {
            JSONArray args = new JSONArray();
            args.put(channel);
            JSONObject o =
                    DtvkitGlueClient.getInstance().request("AtvPlayer.startCC", args);
            ret = o.optBoolean("data");
        } catch (Exception ignore) {}
        return ret;
    }

    private void stopCcInMw() {
        try {
            DtvkitGlueClient.getInstance().request("AtvPlayer.stopCC", new JSONArray());
        } catch (Exception ignore) {}
    }

    private void getSavedCcIndexAsync(@NonNull Context context) {
        new Thread(() -> {
            int index = DataProviderManager.getIntValue(context, ATV_CC_TRACK_INDEX, -1);
            synchronized (this) {
                mCcIndex = index;
            }
        }).start();
    }

    private void saveCcIndexAsync(@NonNull Context context, int channel) {
        new Thread(() -> DataProviderManager.putIntValue(context, ATV_CC_TRACK_INDEX, channel)).start();
    }

    private String getDtvSystem() {
        String ret = null;
        try {
            JSONObject o =
                    DtvkitGlueClient.getInstance().request("Dvb.getDtvSystem",
                            new JSONArray());
            JSONObject data = o.optJSONObject("data");
            if (data != null) {
                ret = data.optString("system_starting_mode");
            }
        } catch (Exception ignore) {}
        return ret;
    }

    private String createTrackId(int idx) {
        if (idx == -1)
            return null;
        return String.format(Locale.getDefault(),"id=%d&type=5&teletext=0&flag=none", idx);
    }

    private int getCcChannelIdFromTrack(String trackId) {
        try {
            if (TextUtils.isEmpty(trackId)) {
                return -1;
            }
            String[] t = trackId.split("&");
            if (t.length > 0) {
                String id = t[0].replace("id=", "");
                return Integer.parseInt(id);
            }
        } catch (Exception ignore) {}
        return -1;
    }
}
