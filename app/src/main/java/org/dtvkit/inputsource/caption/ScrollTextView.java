/*
 * Copyright (c) 2014 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description: JAVA file
 */

package org.dtvkit.inputsource.caption;

import android.content.Context;
import android.widget.Scroller;
import android.widget.TextView;
import android.view.animation.LinearInterpolator;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.graphics.Color;
import android.graphics.Canvas;

public class ScrollTextView extends TextView {
    private static final String TAG = "ScrollTextView";
    private static final int MINSPEED = 64;
    private static final int MAXSPEED = 300;
    private long scrollTime = -1;
    private long lastSetTextTime;
    private Scroller mScroller;
    private Context mContext;
    private Scroller mSetScroll;
    private boolean mHasBkColor;
    private boolean mStartScroll;

    public ScrollTextView(Context context) {
        super(context);
        mContext = context;
        scrollTime = -1;
        mSetScroll = null;
        mScroller = new Scroller(context,new LinearInterpolator());
    }

    public void setBkColor(int CcColor,int trans) {
        super.setBackgroundColor(Color.argb(trans & 0xff, Color.red(CcColor), Color.green(CcColor), Color.blue(CcColor)));
        /*if (trans != 0) {
            mHasBkColor = true;
        } else {
            mHasBkColor = false;
        }*/
        mHasBkColor = true;
    }
    public boolean getHasBkColor() {
        return mHasBkColor;
    }

    public void startPortScroll(float distance, int speed) {
        if (!isAttachedToWindow()||!mHasBkColor) {
            return;
        }
        this.setScroller(mScroller);
        mStartScroll = true;
        int adjustSpeed = -1;
        if (scrollTime < speed && scrollTime > MINSPEED) {
            adjustSpeed = (int) scrollTime;
        } else {
            adjustSpeed = speed;
        }
        mScroller.startScroll(0, 0, 0, (int) (distance/*+mScroller.getCurrY()*/), adjustSpeed);
    }
    @Override
    protected void onDraw(Canvas canvas) {
         ViewParent p = getParent();
        if (p != null && p instanceof ViewGroup && ((ViewGroup)p).getVisibility() == View.VISIBLE) {
            super.onDraw(canvas);
        }
    }
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scrollTime = -1;
        lastSetTextTime = -1;
        scrollTo(0,0);
        mStartScroll = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        mStartScroll = false;
        super.onDetachedFromWindow();
        scrollTime = -1;
        lastSetTextTime = -1;
        mScroller.startScroll(0,0,0,0);

    }

    public void setScroller(Scroller s) {
        super.setScroller(s);
        mSetScroll = s;
    }
    public void stopPortScroll() {
        if (mStartScroll && (mSetScroll != null||!mHasBkColor)) {
            setScroller(null);
            mScroller.startScroll(0,0,0,0,1);
            mScroller.abortAnimation();
            scrollTo(0,0);
        }
    }

    public void timeCount() {
        if (lastSetTextTime < 0 || (System.currentTimeMillis()-lastSetTextTime ) > MAXSPEED) {
            lastSetTextTime = System.currentTimeMillis();
            scrollTime = -1;
        } else {
            scrollTime = System.currentTimeMillis() - lastSetTextTime;
            lastSetTextTime = System.currentTimeMillis();
        }
    }
}