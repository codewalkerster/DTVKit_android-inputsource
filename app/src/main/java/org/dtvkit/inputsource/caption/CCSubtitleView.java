package org.dtvkit.inputsource.caption;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.graphics.Canvas;
import android.view.accessibility.CaptioningManager;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * CCSubtitleView for caption close display
 */
public class CCSubtitleView extends ViewGroup {
    private static final String TAG = "CCSubtitleView";
    private static final String CC_ONTENT = "\"content\":";
    private static int mInitCount = 0;//init_count = 0;
    private static CaptioningManager mCaptioningManager = null;
    private static CcSubtitleFonts mCcSubtilteFont = null;//cf = null;
    private static CcImplement mCcImplement = null; //mCcImplement = null;
    private static CcImplement.CaptionWindow mCaptionWindow = null;
    private static Object lock = new Object();
    private Context mContext;
    private static String mJsonStr;
    private static boolean mUpdateViewByJson;
    public static final int JSON_MSG_NORMAL = 0;
    public static final int SUB_VIEW_SHOW = 1;
    public static final int SUB_VIEW_CLEAN = 2;

    private void init(Context context) {
        synchronized (lock) {
            if (mInitCount == 0) {
                mCcSubtilteFont = new CcSubtitleFonts(context);
                mCcImplement    = new CcImplement(context, mCcSubtilteFont);
                mCaptionWindow  = mCcImplement.new CaptionWindow(context, CCSubtitleView.this);
                mCaptioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
                mCaptioningManager.addCaptioningChangeListener(new CaptioningManager.CaptioningChangeListener() {
                    @Override
                    public void onEnabledChanged(boolean enabled) {
                        super.onEnabledChanged(enabled);
                        Log.e(TAG, "onenableChange from " + mCcImplement.cc_setting.is_enabled + " to " + mCaptioningManager.isEnabled());
                        mCcImplement.cc_setting.is_enabled = mCaptioningManager.isEnabled();
                        if (enabled) {
                            mCcImplement.use_default = mCcImplement.cc_setting.isDefaultSetting();
                        }
                    }

                    @Override
                    public void onFontScaleChanged(float fontScale) {
                        super.onFontScaleChanged(fontScale);
                        Log.e(TAG, "onfontscaleChange");
                        mCcImplement.cc_setting.font_scale = mCaptioningManager.getFontScale();
                        if (mCcImplement.cc_setting.is_enabled) {
                            mCcImplement.use_default = mCcImplement.cc_setting.isDefaultSetting();
                        }
                    }

                    @Override
                    public void onLocaleChanged(Locale locale) {
                        super.onLocaleChanged(locale);
                        Log.e(TAG, "onlocaleChange");
                        mCcImplement.cc_setting.cc_locale = mCaptioningManager.getLocale();
                        if (mCcImplement.cc_setting.is_enabled) {
                            mCcImplement.use_default = mCcImplement.cc_setting.isDefaultSetting();
                        }
                    }

                    @Override
                    public void onUserStyleChanged(CaptioningManager.CaptionStyle userStyle) {
                        super.onUserStyleChanged(userStyle);
                        Log.e(TAG, "onUserStyleChange");
                        mCcImplement.cc_setting.has_foreground_color = userStyle.hasForegroundColor();
                        mCcImplement.cc_setting.has_background_color = userStyle.hasBackgroundColor();
                        mCcImplement.cc_setting.has_window_color = userStyle.hasWindowColor();
                        mCcImplement.cc_setting.has_edge_color = userStyle.hasEdgeColor();
                        mCcImplement.cc_setting.has_edge_type = userStyle.hasEdgeType();
                        mCcImplement.cc_setting.edge_type = userStyle.edgeType;
                        mCcImplement.cc_setting.edge_color = userStyle.edgeColor;
                        mCcImplement.cc_setting.foreground_color = userStyle.foregroundColor;
                        mCcImplement.cc_setting.foreground_opacity = userStyle.foregroundColor >>> 24;
                        mCcImplement.cc_setting.background_color = userStyle.backgroundColor;
                        mCcImplement.cc_setting.background_opacity = userStyle.backgroundColor >>> 24;
                        mCcImplement.cc_setting.window_color = userStyle.windowColor;
                        mCcImplement.cc_setting.window_opacity = userStyle.windowColor >>> 24;
                        /* Typeface is obsolete, we use local font */
                        mCcImplement.cc_setting.type_face = userStyle.getTypeface();
                        if (mCcImplement.cc_setting.is_enabled) {
                            mCcImplement.use_default = mCcImplement.cc_setting.isDefaultSetting();
                        }

                    }
                });
                mCcImplement.cc_setting.UpdateCcSetting(mCaptioningManager);
                mCcImplement.use_default = mCcImplement.cc_setting.isDefaultSetting();
            }
            Log.d(TAG, "subtitle view init");
            mInitCount += 1;
        }
    }

    /**
     * creat TVSubtitle
     */
    public CCSubtitleView(Context context) {
        super(context);
        mContext = context;
        init(context);
    }

    /**
     * creat TVSubtitle
     */
    public CCSubtitleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init(context);
    }

    /**
     * creat TVSubtitle
     */
    public CCSubtitleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        init(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        synchronized(lock) {
            Log.d(TAG, "dispatchDraw mUpdateViewByJson = " + mUpdateViewByJson);
            if (mUpdateViewByJson & !TextUtils.isEmpty(mJsonStr)) {
                mCcImplement.caption_screen.updateCaptionScreen(canvas.getWidth(), canvas.getHeight());
                mCaptionWindow.style_use_broadcast = mCcImplement.isStyle_use_broadcast();
                if (!mCaptionWindow.getViewParent().equals(CCSubtitleView.this)) {
                    Log.d(TAG, "View Changed");
                    mCaptionWindow.updateParent(CCSubtitleView.this);
                }
                mCaptionWindow.updateCaptionWindow(mJsonStr);
                //mCaptionWindow.draw(canvas);
                mUpdateViewByJson = false;
            }

        }
        super.dispatchDraw(canvas);
    }

    Handler mHandler = new Handler() {
        public void handleMessage(@NonNull Message msg) {
            //Log.d(TAG, "msg.what =" + msg.what);
            switch (msg.what) {
                case JSON_MSG_NORMAL:
                    mJsonStr = (String)msg.obj;
                    mUpdateViewByJson = true;
                    //postInvalidate();
                    break;
                case SUB_VIEW_SHOW:
                    boolean visible = (Boolean) msg.obj;
                    Log.d(TAG, "Visible =" + visible);
                    mCaptionWindow.updateVisible(visible, CCSubtitleView.this);
                    if (!visible) {
                        mJsonStr = null;
                    }
                    postInvalidate();
                    break;
                case SUB_VIEW_CLEAN:
                    mJsonStr = null;
                    postInvalidate();
                    break;
            }
        }
    };

    public void showJsonStr(String str) {
        Log.d(TAG, "[showJsonStr]");
        if (!TextUtils.isEmpty(str) && (str.equals(mJsonStr) == false)) {
            mHandler.removeMessages(JSON_MSG_NORMAL);
            mHandler.obtainMessage(JSON_MSG_NORMAL, str).sendToTarget();
            mHandler.removeMessages(SUB_VIEW_SHOW);
            mHandler.obtainMessage(SUB_VIEW_SHOW, true).sendToTarget();
        }
    }

    public void clearContent() {
        Log.d(TAG, "clearContent");
        mHandler.removeMessages(JSON_MSG_NORMAL);
        mHandler.removeMessages(SUB_VIEW_SHOW);
        mHandler.obtainMessage(SUB_VIEW_SHOW, false).sendToTarget();
    }
}