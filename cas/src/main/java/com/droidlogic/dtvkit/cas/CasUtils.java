package com.droidlogic.dtvkit.cas;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;

import com.droidlogic.dtvkit.cas.ird.IrdHandler;
import com.droidlogic.dtvkit.cas.nagra.NagraHandler;

import org.json.JSONObject;

public class CasUtils {
    private static CasUtils mInstance;
    private Handler mThreadHandler;
    private IrdHandler mIrdHandler;
    private NagraHandler mNagraHandler;
    private CasCallback mCallback;
    private String mCurrentUri = null;

    public static final int MSG_IRD_CAS_START = 1000;

    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("tisCasThr");
        handlerThread.start();
        mThreadHandler = new Handler(handlerThread.getLooper(),
                msg -> {
                    if (msg.what >= MSG_IRD_CAS_START) {
                        mIrdHandler.threadHandleCasMessages(msg);
                        return true;
                    }
                    return false;
                });
    }

    private CasUtils() {
        initHandler();
    }

    public static CasUtils getInstance() {
        if (mInstance == null)
            mInstance = new CasUtils();
        return mInstance;
    }

    public void init(@NonNull Context context, @NonNull CasCallback callback) {
        mIrdHandler = new IrdHandler(context, mThreadHandler);
        mNagraHandler = new NagraHandler(context, mThreadHandler);
        mCallback = callback;
    }

    public void destroy(@NonNull Context context) {
        mCallback = null;
        if (mIrdHandler != null) {
            mIrdHandler.destroy(context);
        }
        if (mNagraHandler != null) {
            mNagraHandler.destroy(context);
        }
    }

    public void onCasSignal(@NonNull JSONObject casEvent) {
        String casSystem = casEvent.optString("cas_system", "");
        if ("irdeto".equalsIgnoreCase(casSystem)) {
            mIrdHandler.handleCasProvider(casEvent);
        } else if ("Nagra".equalsIgnoreCase(casSystem)) {
            mNagraHandler.handleCasProvider(casEvent);
        }
    }

    public void onPlayingStart(@NonNull String uri) {
        synchronized (this) {
            mCurrentUri = uri;
        }
        if (mIrdHandler != null) {
            mIrdHandler.onPlayStatusStart(uri);
        }
    }

    public void onPlayingStopped() {
        synchronized (this) {
            mCurrentUri = null;
        }
        if (mIrdHandler != null) {
            mIrdHandler.onPlayingStopped();
        }
    }

    public synchronized String getCurrentDvbUri() {
        return mCurrentUri;
    }

    public byte[] createIrdFpOptions(int x,
                                            int y,
                                            int bga,
                                            int bg,
                                            int fga,
                                            int fg,
                                            int font) {
        if (fga == 0 || bg == fg) {
            //invalid options, use default bg and fg
            bga = 0x0;
            bg = 0x0;
            fga = 0xff;
            fg = 0xffffff;
        }
        return new byte[]{0x00, 0x0c, (byte)x, (byte)y, (byte)bga,
                (byte)((bg & 0xffffff) >> 16), (byte)((bg & 0xffff) >> 8), (byte)(bg & 0xff),
                (byte)fga,
                (byte)((fg & 0xffffff) >> 16), (byte)((fg & 0xffff) >> 8), (byte)(fg & 0xff),
                (byte)font, (byte)0x00};
    }

    public byte[] hexStrToBytes(@NonNull String hexStr) {
        try {
            char[] tmp = hexStr.toCharArray();
            byte[] data = new byte[tmp.length / 2];
            for (int i = 0; i < tmp.length; i += 2) {
                String hex = String.valueOf(tmp[i]) + tmp[i + 1];
                data[(i)/2] = (byte) Integer.parseInt(hex, 16);
            }
            return data;
        } catch (Exception ignored) {}
        return new byte[1];
    }

    public String casSessionRequest(long session, String request) {
        if (mCallback != null) {
            return mCallback.onCasRequest(session, request);
        }
        return null;
    }

    public interface CasCallback {
        String onCasRequest(long session, String request);
    }
}