package com.droidlogic.dtvkit.inputsource;

import android.os.Handler;
import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.graphics.Typeface;

import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.amlogic.hbbtv.HbbTvManager;
import com.droidlogic.app.CCSubtitleView;

import org.dtvkit.inputsource.EWBSAlarmOverlayView;
import org.json.JSONArray;

import com.droidlogic.dtvkit.inputsource.CiMenuView;
import com.droidlogic.dtvkit.inputsource.HbbTvOverlayView;
import org.droidlogic.dtvkit.SubtitleServerView;
import org.droidlogic.dtvkit.MhegOverlayView;
import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.json.JSONException;
import org.json.JSONObject;

import com.droidlogic.dtvkit.inputsource.utils.Constant;

public class DtvkitOverlayView extends FrameLayout {
    private static final String TAG = "DtvkitOverlayView";
    private static final boolean DEBUG = true;

    private MhegOverlayView nativeOverlayView;
    private CiMenuView ciOverlayView;
    private TextView mText;
    private ImageView mTuningImage;
    private RelativeLayout mRelativeLayout;
    private CCSubtitleView mCCSubView = null;
    private SubtitleServerView mSubServerView;
    private TextView mCasOsm;
    private int w;
    private int h;
    private HbbTvOverlayView mHbbTvFrameLayout = null;
    private EWBSAlarmOverlayView mEwbsAlarmOverlayView = null;

    private boolean mhegTookKey = false;
    private KeyEvent lastMhegKey = null;
    final private static int MHEG_KEY_INTERVAL = 65;
    private int mCasOsmDisplayMode = 0;

    public DtvkitOverlayView(Context context, Handler mainHandler, boolean enableCC) {
        super(context);
        Log.i(TAG, "Created" + this);
        mSubServerView = new SubtitleServerView(getContext(), mainHandler);
        addView(mSubServerView);
        nativeOverlayView = new MhegOverlayView(getContext(), mainHandler);
        addView(nativeOverlayView);

        if (enableCC) {
            mCCSubView = new CCSubtitleView(getContext());
            addView(mCCSubView);
        }
        initRelativeLayout();
        mCasOsm = new TextView(getContext());
        addView(mCasOsm);
        mCasOsm.setVisibility(View.GONE);
        ciOverlayView = new CiMenuView(getContext());
        addView(ciOverlayView);
        mEwbsAlarmOverlayView = new EWBSAlarmOverlayView(getContext());
        addView(mEwbsAlarmOverlayView);
        mHbbTvFrameLayout = new HbbTvOverlayView(getContext());
        addView(mHbbTvFrameLayout);
    }

    public void destroy() {
        Log.i(TAG, "Destroy " + this);
        if (mSubServerView != null) {
            mSubServerView.destroy();
            removeView(mSubServerView);
            mSubServerView = null;
        }
        if (nativeOverlayView != null) {
            nativeOverlayView.destroy();
            removeView(nativeOverlayView);
            nativeOverlayView = null;
        }
        if (ciOverlayView != null) {
            ciOverlayView.destroy();
            removeView(ciOverlayView);
            ciOverlayView = null;
        }
        if (mRelativeLayout != null) {
            removeView(mRelativeLayout);
            mText = null;
            mTuningImage = null;
            mRelativeLayout = null;
        }
        if (mCCSubView != null) {
            removeView(mCCSubView);
            mCCSubView = null;
        }
        if (mCasOsm != null) {
            removeView(mCasOsm);
            mCasOsm = null;
        }
        if (mEwbsAlarmOverlayView != null) {
            removeView(mEwbsAlarmOverlayView);
            mEwbsAlarmOverlayView = null;
        }
        removeView(mHbbTvFrameLayout);
    }

    public void hideOverLay() {
        setVisibility(View.GONE);
        Log.d(TAG, "hideOverLay");
    }

    public void showOverLay() {
        setVisibility(View.VISIBLE);
        Log.d(TAG, "showOverLay");
    }

    private void initRelativeLayout() {
        if (mText == null && mRelativeLayout == null) {
            Log.d(TAG, "initRelativeLayout");
            mRelativeLayout = new RelativeLayout(getContext());
            mText = new TextView(getContext());
            mTuningImage = new ImageView(getContext());
            ViewGroup.LayoutParams linearLayoutParams = new ViewGroup.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            mRelativeLayout.setLayoutParams(linearLayoutParams);
            mRelativeLayout.setBackgroundColor(Color.TRANSPARENT);

            //add scrambled text
            RelativeLayout.LayoutParams textLayoutParams = new RelativeLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            textLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            mText.setGravity(Gravity.CENTER);
            mText.setTypeface(Typeface.DEFAULT_BOLD);
            mText.setTextColor(Color.WHITE);
            //mText.setText("RelativeLayout");
            mText.setVisibility(View.GONE);
            //add tuning view
            RelativeLayout.LayoutParams imageLayoutParams = new RelativeLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            ColorDrawable colorDrawable = new ColorDrawable();
            colorDrawable.setColor(Color.argb(255, 0, 0, 0));
            mTuningImage.setImageDrawable(colorDrawable);
            mTuningImage.setVisibility(View.GONE);

            mRelativeLayout.addView(mTuningImage, imageLayoutParams);
            mRelativeLayout.addView(mText, textLayoutParams);
            this.addView(mRelativeLayout);
        } else {
            Log.d(TAG, "initRelativeLayout already");
        }
    }

    public void setTeletextMix(int status, boolean subFlag) {
        if (DEBUG) Log.d(TAG, "status:" + status + "|subFlag = " + subFlag);

        if (subFlag) {
            mSubServerView.setTeletextTransparent(status == Constant.TTX_MODE_TRANSPARENT);
            if (status == Constant.TTX_MODE_SEPARATE) {
                mSubServerView.setSize(w/2, 0, w, h);
            } else {
                mSubServerView.setSize(0, 0, w, h);
            }
        } else if (status == Constant.TTX_MODE_SEPARATE) {
            nativeOverlayView.setSize(w/2, 0, w, h);
        } else {
            nativeOverlayView.setSize(0, 0, w, h);
        }
    }

    public void showBlockedText(String text, boolean isPip, boolean blackColor) {
        if (DEBUG) Log.d(TAG, "text:" + text + "|isPip = " + isPip + "|blackColor = " + blackColor);

        if (mText != null && mRelativeLayout != null) {
            Log.d(TAG, "showText:" + text);

            mText.setText(text);
            if (mText.getVisibility() != View.VISIBLE) {
                mText.setVisibility(View.VISIBLE);
            }
            //display black background
            ColorDrawable colorDrawable = new ColorDrawable();
            int color = Color.argb(255, 0, 0, 0);

            if (blackColor) {
                color = Color.argb(255, 0, 0, 0); //black
            } else {
                color = Color.argb(255, 3, 0, 247); //blue
            }
            colorDrawable.setColor(color);
            showTuningImage(colorDrawable);
        }
    }

    //Need double check clear subtitle
    public void hideBlockedText() {
        if (DEBUG) Log.d(TAG, "hideBlockedText:");
        try {
            mSubServerView.clearSubtitle();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        if (mText != null && mRelativeLayout != null) {
            Log.d(TAG, "hideText");
            mText.setText("");
            if (mText.getVisibility() != View.GONE) {
                mText.setVisibility(View.GONE);
                //hide black background
                hideTuningImage();
            }
        }
    }

    //Need check HBBTV View
    public void addHbbTvView(View view, HbbTvManager hbbTvManager) {
        mHbbTvFrameLayout.addHbbTvView(view, hbbTvManager);
        if (hbbTvManager != null) {
            HbbTvManager.HbbTvWindowSizeChangeCallback hbbTvWindowSizeChangeCallback= new HbbTvManager.HbbTvWindowSizeChangeCallback() {
                public void onHbbTvWindowSizeChange(boolean isFullScreen) {
                    Log.d(TAG,"the isFullScreen = " + isFullScreen);
                    if (isFullScreen) {
                        mSubServerView.setVisibility(View.VISIBLE);
                    } else {
                        mSubServerView.setVisibility(View.INVISIBLE);
                    }
                }
           };
           hbbTvManager.registerHbbTvWindowSizeChangeCallback(hbbTvWindowSizeChangeCallback);
        }

    }

    public void showTuningImage(Drawable drawable) {
        if (mTuningImage != null && mRelativeLayout != null) {
            if (drawable != null) {
                Log.d(TAG, "showTuningImage");
                mTuningImage.setImageDrawable(drawable);
            }
            if (mTuningImage.getVisibility() != View.VISIBLE) {
                mTuningImage.setVisibility(View.VISIBLE);
            }
        }
        nativeOverlayView.clear();
        mSubServerView.clearSubtitle();
    }

    //Need check hideTuningImage View
    public void hideTuningImage() {
        mSubServerView.clearSubtitle();
        if (mTuningImage != null && mRelativeLayout != null) {
            Log.d(TAG, "hideTuningImage");
            if (mTuningImage.getVisibility() != View.GONE) {
                mTuningImage.setVisibility(View.GONE);
            }
        }
    }

    public void test(String[] args) {
        boolean showSubtitle = false;
        if (args != null && args.length > 0) {
            for (String opt : args) {
                if (opt.equals("testSubtitle")) {
                    showSubtitle = true;
                    break;
                }
            }
        }
        if (showSubtitle) {
            mSubServerView.showTestSubtitle();
        }
    }

    public void setSize(int width, int height) {
        w = width;
        h = height;
        nativeOverlayView.setSize(width, height);
        mSubServerView.setSize(width, height);
        mEwbsAlarmOverlayView.setSize(width, height);
    }

    public void setSize(int x, int y, int width, int height) {
        Log.i(TAG, "setSize for hbbtv in");
        w = width;
        h = height;
        nativeOverlayView.setSize(x, y, width, height);
        mSubServerView.setSize(x, y, width, height);
        Log.i(TAG, "setSize for hbbtv out");
    }

    public void showCCSubtitle(String json) {
        if (mCCSubView != null) {
            mCCSubView.showJsonStr(json);
        }
    }

    public void hideCCSubtitle() {
        if (mCCSubView != null) {
            mCCSubView.clearContent();
        }
    }

    public void prepareCasWindow(String msg, int osdMode, int anchor_x,
                                 int anchor_y, int w, int h, int bg, int alpah, int fg) {
        if (mCasOsm != null) {
            LayoutParams layoutParams = (LayoutParams) mCasOsm.getLayoutParams();
            if (osdMode == 8 || w == 0 || h == 0) {
                layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                layoutParams.width = w;
                layoutParams.height = h;
            }
            layoutParams.gravity = Gravity.NO_GRAVITY;
            float ax = (float) (anchor_x);
            float ay = (float) (anchor_y);
            float tx = (float) (this.w);
            float ty = (float) (this.h);
            if (ax / tx > 0.3 && ax / tx < 0.7) {
                layoutParams.gravity |= Gravity.CENTER_HORIZONTAL;
            } else if (ax / tx <= 0.3) {
                layoutParams.gravity |= Gravity.LEFT;
            } else if (ax / tx >= 0.7) {
                layoutParams.gravity |= Gravity.RIGHT;
            }
            if (ay / ty > 0.3 && ay / ty < 0.7) {
                layoutParams.gravity |= Gravity.CENTER_VERTICAL;
            } else if (ay / ty <= 0.3) {
                layoutParams.gravity |= Gravity.TOP;
            } else if (ay / ty >= 0.7) {
                layoutParams.gravity |= Gravity.BOTTOM;
            }
            mCasOsm.setLayoutParams(layoutParams);

            mCasOsm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            mCasOsm.setTypeface(Typeface.DEFAULT_BOLD);
            mCasOsm.setTextColor(Color.WHITE);

            mCasOsm.setText(msg);
            mCasOsm.setVisibility(View.GONE);
        }
    }

    public void showCasMessage(int displayMode) {
        if (mCasOsm != null) {
            mCasOsm.setVisibility(View.VISIBLE);
            mCasOsmDisplayMode = displayMode;
        }
    }

    public void clearCasView() {
        if (mCasOsm != null) {
            mCasOsm.setText("");
            mCasOsm.setVisibility(View.GONE);
            mCasOsmDisplayMode = 0;
        }
    }

    public int getCasOsmDisplayMode() {
        return mCasOsmDisplayMode;
    }

    public void showEWBSAlarmView(JSONObject EWBSData) {
        if (mEwbsAlarmOverlayView != null) {
            mEwbsAlarmOverlayView.showEWBSJsonMsg(EWBSData);
            mEwbsAlarmOverlayView.setVisibility(View.VISIBLE);
        }
    }

    public void hideEWBSAlarmView() {
        mEwbsAlarmOverlayView.hideEWBSAlarmView();
        mEwbsAlarmOverlayView.setVisibility(View.GONE);
    }

    public boolean checkMhegKeyLimit(KeyEvent event) {
        if (DEBUG) Log.d(TAG, "checkMhegKeyLimit: event + " + event);

        if (lastMhegKey == null)
            return false;
        if (lastMhegKey.getKeyCode() != event.getKeyCode())
            return false;
        if (event.getEventTime() - lastMhegKey.getEventTime() > MHEG_KEY_INTERVAL)
            return false;
        return true;
    }

    //Need double check Key handle
    public boolean handleKeyDown(int keyCode, KeyEvent event, boolean mhegAppStarted) {
        boolean result;
        if (ciOverlayView.handleKeyDown(keyCode, event)) {
            mhegTookKey = false;
            result = true;
        } else if (!(checkMhegKeyLimit(event)) && mhegAppStarted && mhegKeypress(keyCode)) {
            mhegTookKey = true;
            result = true;
            lastMhegKey = event;
        } else if (mCasOsm.getVisibility() == View.VISIBLE && ((mCasOsmDisplayMode & 0x1) == 0)) {
            clearCasView();
            result = true;
        } else if (mHbbTvFrameLayout.handleKeyDown(keyCode, event)) {
            result = true;
            mhegTookKey = false;
            Log.d(TAG, "hbbtv manager the handle key down result = " + result);
        } else {
            mhegTookKey = false;
            result = false;
        }
        if (DEBUG) Log.d(TAG, "handleKeyDown: keyCode + " + keyCode + "|result = " + result);
        return result;
    }

    public boolean handleKeyUp(int keyCode, KeyEvent event) {
        boolean result;
        if (ciOverlayView.handleKeyUp(keyCode, event) || mhegTookKey) {
            result = true;
        } else if (mHbbTvFrameLayout.handleKeyUp(keyCode, event)) {
            result = true;
            Log.d(TAG, "hbbtv manager the handle key up result = " + result);
        } else {
            result = false;
        }
        mhegTookKey = false;
        lastMhegKey = null;

        if (DEBUG) Log.d(TAG, "handleKeyUp: keyCode + " + keyCode + "|result = " + result);
        return result;
    }

    public void setOverlayTarget() {
        if (DEBUG) Log.d(TAG, "setOverlayTarget:");
        if (null != nativeOverlayView) {
            nativeOverlayView.setOverlayTarget(nativeOverlayView.mTarget);
        }
    }

    public void setOverlaySubtitleListener() {
        if (DEBUG) Log.d(TAG, "setOverlaySubtitleListener:");
        if (null != mSubServerView) {
            mSubServerView.setOverlaySubtitleListener(mSubServerView.mSubListener);
        }
    }

    public void clearSubtitle() {
        if (DEBUG) Log.d(TAG, "clearSubtitle:");
        if (null != mSubServerView) {
            mSubServerView.clearSubtitle();
        }
    }

    private boolean mhegKeypress(int keyCode) {
        boolean used = false;
        try {
            JSONArray args = new JSONArray();
            args.put(keyCode);
            used = DtvkitGlueClient.getInstance().request("Mheg.notifyKeypress", args)
                    .getBoolean("data");
            Log.i(TAG, "Mheg keypress, used:" + used);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return used;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (mSubServerView != null) {
            result.append("SubServerView:").append(mSubServerView).append('\n');
        }
        if (nativeOverlayView != null) {
            result.append("nativeOverlayView:").append(nativeOverlayView).append('\n');
        }
        if (ciOverlayView != null) {
            result.append("ciOverlayView:").append(ciOverlayView).append('\n');
        }
        if (mRelativeLayout != null) {
            result.append("RelativeLayout:").append(mRelativeLayout).append('\n');
        }
        if (mCCSubView != null) {
            result.append("CCSubView:").append(mCCSubView).append('\n');
        }
        if (mCasOsm != null) {
            result.append("CasOsm:").append(mCasOsm).append('\n');
        }
        return result.toString();
    }

}
