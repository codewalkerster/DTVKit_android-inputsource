package com.droidlogic.dtvkit.cas.ird;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.droidlogic.dtvkit.cas.CasProviderManager;
import com.droidlogic.dtvkit.cas.CasUtils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

public class FingerPrintManager {
    private final static String TAG = "FingerPrint";
    private final Context mContext;
    private final ArrayList<FingerPrint> mFpList;
    private int mCurrentFpListType;
    private final Handler mThreadHandler;
    private final CasProviderManager mCasProviderManager;
    private boolean mNeedClearEnhancedFp;

    FingerPrintManager(@NonNull Context context,
                       @NonNull Handler handler,
                       @NonNull CasProviderManager casProviderManager) {
        mFpList = new ArrayList<>();
        mContext = context;
        mThreadHandler = handler;
        mCasProviderManager = casProviderManager;
        mCurrentFpListType = -1;
    }

    private static class FingerPrint {
        int fpType;
        long startTime;
        long endTime;
        int flash;
        int banner;
        int coverage;
        byte[] content;
        int x;
        int y;
        int bga;
        int bg;
        int fga;
        int fg;
        int font;
        int random_display_interval;
        boolean isEnhanced;
        int scroll;

        boolean isRandomPositionType() {
            return (x == 255 && y == 255 && random_display_interval != 0);
        }

        byte[] createFpOptions() {
            return CasUtils.getInstance().createIrdFpOptions(x ,y, bga, bg, fga, fg, font);
        }
    }

    private FingerPrint createFpFromJson(@NonNull JSONObject fpJson) {
        FingerPrint fp = new FingerPrint();
        fp.fpType = fpJson.optInt("fp_type", 0);
        int duration = fpJson.optInt("duration", 0);
        if (duration == 0 || duration > 65535) duration = 65535; // 0 = indefinitely, use default
        fp.startTime = SystemClock.uptimeMillis();
        fp.endTime = fp.startTime + duration * 1000L;
        fp.flash = fpJson.optBoolean("flash", false) ? 0 : 1;
        fp.banner = fpJson.optInt("banner", 0);
        fp.coverage = fpJson.optInt("coverage_percent", 0);
        fp.scroll = fpJson.optInt("scrolling", 0);
        fp.content = CasUtils.getInstance().hexStrToBytes(
                fpJson.optString("content", ""));
        fp.x = fpJson.optInt("location_x", 0);
        fp.y = fpJson.optInt("location_y", 0);
        fp.bga = fpJson.optInt("bg_transparency", 0);
        fp.bg = fpJson.optInt("bg_colour", 0);
        fp.fga = fpJson.optInt("font_transparency", 0);
        fp.fg = fpJson.optInt("font_colour", 0);
        fp.font = fpJson.optInt("font_type", 0);
        fp.isEnhanced = fpJson.optBoolean("enhanced", false);
        fp.random_display_interval = fpJson.optInt("random_display_interval", 0);
        return fp;
    }

    public synchronized void onFpAdded(@NonNull JSONObject fpJson) {
        boolean stopEnhanced = !fpJson.optBoolean("start", false)
                && fpJson.optBoolean("enhanced", false);
        if (!stopEnhanced) {
            FingerPrint fp = createFpFromJson(fpJson);
            if (mCurrentFpListType != fp.fpType)
                //only include one type
                mFpList.clear();
            if (fp.isEnhanced) {
                //only include one enhanced fp
                mFpList.removeIf(f -> f.isEnhanced);
            }
            mCurrentFpListType = fp.fpType;
            mFpList.add(fp);
            mNeedClearEnhancedFp = false;
        } else {
            if (mFpList.size() > 0) {
                FingerPrint lastFP = mFpList.get(mFpList.size() - 1);
                if (lastFP.isEnhanced && (lastFP.endTime - SystemClock.uptimeMillis()) > 1000) {
                    Log.v(TAG, "receive stop message and has enhanced showing.");
                    mNeedClearEnhancedFp = true;
                }
                mFpList.removeIf(fp -> fp.isEnhanced);
            }
        }
        if (mNeedClearEnhancedFp && mFpList.size() == 0) {
            scheduleOneFp(1000);
        } else {
            scheduleOneFp(0);
        }
    }

    private void scheduleOneFp(long delayMs) {
        mThreadHandler.removeCallbacks(mFpRunnable);
        mThreadHandler.postDelayed(mFpRunnable, delayMs);
    }

    private final Runnable mFpRunnable = new Runnable() {
        @Override
        public synchronized void run() {
            //clear timeout fps
            mFpList.removeIf(fp -> fp.endTime < SystemClock.uptimeMillis());
            if (mFpList.size() == 0) {
                Log.v(TAG, "No fp to show now.");
                if (mNeedClearEnhancedFp) {
                    Log.v(TAG, "Clear enhanced fingerprint for received stop message");
                    mThreadHandler.post(() -> mCasProviderManager.putScreenMessage(
                            mContext, 2,  1, 1, 1, 1,
                            0, 0, 0, 0,
                            "".getBytes(), null, 0));
                    mNeedClearEnhancedFp = false;
                }
            } else {
                FingerPrint queueFp = mFpList.get(mFpList.size() - 1);
                long current = SystemClock.uptimeMillis();
                int duration = (int) ((queueFp.endTime - current) / 1000);
                if (queueFp.isRandomPositionType()) {
                    int interval = queueFp.random_display_interval * 10;
                    duration = Math.min(interval, duration);
                }
                if (duration > 1) {
                    Log.v(TAG, "QUEUE fp: [Type:" + queueFp.fpType
                            + ", startTime:" + queueFp.startTime + ", endTime:" + queueFp.endTime
                            + ", Dur:" + duration + ", cov:" + queueFp.coverage
                            + ", cont:" + Arrays.toString(queueFp.content) + ", Enh:" + queueFp.isEnhanced + "]");
                    Log.v(TAG, "Will check after " + duration + " seconds later.");
                    int finalDuration = duration;
                    mThreadHandler.post(() -> mCasProviderManager.putScreenMessage(
                            mContext, 2, 1, queueFp.isEnhanced ? 1 : 0,
                            finalDuration, queueFp.flash, queueFp.banner, queueFp.coverage,
                            queueFp.fpType, queueFp.scroll,
                            queueFp.content, queueFp.createFpOptions(), 0));
                    mThreadHandler.removeCallbacks(mFpRunnable);
                    mThreadHandler.postDelayed(mFpRunnable, duration * 1000L);
                } else {
                    mThreadHandler.removeCallbacks(mFpRunnable);
                    mThreadHandler.postDelayed(mFpRunnable, 1000L);
                }
            }
        }
    };
}

