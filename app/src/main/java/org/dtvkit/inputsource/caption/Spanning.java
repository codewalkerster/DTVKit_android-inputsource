/*
 * Copyright (C) 2017 Amlogic Corporation.
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
 */
package org.dtvkit.inputsource.caption;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.text.style.ReplacementSpan;
import android.util.Log;
import android.text.TextUtils;
public class Spanning extends ReplacementSpan {
    private static String TAG = "Spanning";
    private int mWidth;
    private WindowInfo mWindow;
    private int mRowId;
    private int mRStrId;
    private RowStrInfo mRowStr;
    private RowInfo mRow;
    private CcImplement.CaptionScreen mCapScreen;
    private boolean mStyleUseBroadcast;
    private String mCCVersion;

    public Spanning() {

    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,Paint.FontMetricsInt fm) {
        mWidth = (int) paint.measureText(text, start, end);
        return mWidth;
    }

    public void setCurrentRowStrID(CcImplement.CaptionWindow.Window w, int rowId, int StrId, CcImplement.CaptionScreen screen, boolean style_use_broadcast, String ccVersion/*, CcImplement.CaptionWindow capWindow*/) {
        mRowId = rowId;
        mRStrId = StrId;
        mCapScreen = screen;
        mStyleUseBroadcast = style_use_broadcast;
        mCCVersion = ccVersion;
        mWindow = new WindowInfo(w);
        CcImplement.CaptionWindow.Window.Rows row = w.rows[mRowId];
        if (row != null) {
            mRowStr = new RowStrInfo(row.rowStrs[mRStrId], StrId == 0);
            mRow = new RowInfo(row);
        }
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        if (mRowStr != null) {
            RowStrDraw(canvas, text, start, end, x, top, y, bottom, paint);
        } else {
            canvas.drawText(text, start, end, x, y, paint);
        }
    }

    /* Draw font and background
     * 1. Make sure background was drew first
     * */
    void RowStrDraw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint fontpaint) {
        Paint background_paint = new Paint();
        int canvasWidth = canvas.getWidth();
        Log.d("BBB","mRowStr"+mRowStr.pen_size+"fontscale"+mWindow.fontScale);
        double str_top = top;
        double str_bottom = bottom;
        if (mWindow.fontScale != 100 && mRowStr.pen_size.equalsIgnoreCase("small")) {
            str_bottom = (int) Math.ceil(top + mCapScreen.max_font_height *7/10);
            canvasWidth = (int) Math.ceil(canvasWidth*7/10);
        }else if(mWindow.fontScale != 100 &&  mRowStr.pen_size.equalsIgnoreCase("standard")) {
            str_bottom = (int) Math.ceil(top + mCapScreen.max_font_height *85/100);
            canvasWidth =(int) Math.ceil(canvasWidth*85/100);
        }else {
            str_bottom = (int) Math.ceil(top + mCapScreen.max_font_height);
            canvasWidth =(int) Math.ceil(canvasWidth);
        }
        //top + mCapScreen.max_font_height;
        double str_left, str_right;
        mRow.character_gap = canvasWidth/mWindow.col_count;

        if (mWindow.justify.equalsIgnoreCase("right")) {
            str_right = canvasWidth;
            str_left = (str_right - mRowStr.string_length_on_paint);
        } else if (mWindow.justify.equalsIgnoreCase("full")) {
            str_left = 0;
            str_right = canvasWidth;
        } else if (mWindow.justify.equalsIgnoreCase("center")) {
            str_left = (canvasWidth - mRow.row_length_on_paint) / 2;
            str_right = str_left + mRowStr.string_length_on_paint;
        } else {
            if (mRowStr.isStartStr) {
                str_left = mRow.character_gap* mRowStr.str_start_x;
            }else {
                str_left = mRowStr.pre_str_length + mRow.character_gap* mRow.row_start_x;
            }
            str_right = str_left + mRowStr.string_length_on_paint;
            if (mRowStr.isStartStr && mRow.row_str_count == 1
                && (str_right <  mRow.character_gap*mRow.row_start_x + mRow.row_length_on_paint)) {
                str_right = mRow.character_gap * mRowStr.str_characters_count;
            }
        }
        if (mRowStr.bg_opacity_int == 0) {
            //empty string
            if (text.toString().trim().isEmpty() && mRowStr.fg_opacity_int != 0) {
                background_paint.setColor(mRowStr.fg_color);
                background_paint.setAlpha(mRowStr.fg_opacity_int);
                if (TextUtils.equals(android.os.Build.PRODUCT, "hazel")) {
                    canvas.drawRect((float) str_left, (float) str_top, (float) canvas.getWidth(), (float) str_bottom, background_paint);
                }else {
                    canvas.drawRect((float) str_left, (float) str_top, (float) str_right, (float) str_bottom, background_paint);
                }
                return;
            }
        }else {
            background_paint.setColor(mRowStr.bg_color);
            background_paint.setAlpha(mRowStr.bg_opacity_int);
            if (mWindow.fill_opacity_int != 0xff) {
                background_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
            }
            canvas.drawRect((float) str_left, (float) str_top, (float) str_right, (float) str_bottom, background_paint);
        }
        if (!mWindow.justify.equalsIgnoreCase("full")) {
            draw_text(canvas, text, start, end, mRowStr.font_face, mRowStr.font_size * mRowStr.font_scale,
                    (float) str_left, (float) (str_bottom),
                    mRowStr.fg_color, mRowStr.fg_opacity, mRowStr.fg_opacity_int,
                    mRowStr.underline,
                    mRowStr.edge_color, (float) mRowStr.edge_width, mRowStr.edge_type, fontpaint);
        } else {
            double prior_character_position = str_left;
            //  int size = end - start;
            int width = canvasWidth;
            mRow.character_gap = width / mRow.row_characters_count;
            for (int i = start; i < end; i++) {
                draw_text(canvas, "" + text.charAt(i), 0, 1, mRowStr.font_face, mRowStr.font_size * mRowStr.font_scale,
                        (float) prior_character_position, (float) (str_bottom),
                        mRowStr.fg_color, mRowStr.fg_opacity, mRowStr.fg_opacity_int,
                        mRowStr.underline,
                        mRowStr.edge_color, (float) mRowStr.edge_width, mRowStr.edge_type, fontpaint);
                prior_character_position += mRow.character_gap;
            }
        }

    }

    void draw_str(Canvas canvas, CharSequence text, int start, int end, float left, float bottom, Paint paint) {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        canvas.drawText(text, start, end, left, bottom, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        canvas.drawText(text, start, end, left, bottom, paint);
    }

    void draw_text(Canvas canvas, CharSequence text, int start, int end,
                   Typeface face, double font_size,
                   float left, float bottom, int fg_color, String opacity, int opacity_int,
                   boolean underline,
                   int edge_color, float edge_width, String edge_type, Paint paint) {

        paint.reset();
        // paint.setTextAlign(Paint.Align.LEFT);
        if (mStyleUseBroadcast) {
            if (opacity.equalsIgnoreCase("flash")) {
                if (mWindow.heart_beat == 0)
                    return;
                else
                    opacity_int = 0xff;
            }
        }
        paint.setSubpixelText(true);
        paint.setTypeface(face);
        if (opacity_int != 0xff)
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

        paint.setAntiAlias(true);
        paint.setTextSize((float) font_size);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float distance = bottom - fontMetrics.descent;
        if (edge_type == null || edge_type.equalsIgnoreCase("none")) {
            paint.setColor(fg_color);
            paint.setAlpha(opacity_int);
            draw_str(canvas, text, start, end, left, distance, paint);
        } else if (edge_type.equalsIgnoreCase("uniform")) {
            //paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth((float) (edge_width * 1.5));
            paint.setColor(edge_color);
            paint.setStyle(Paint.Style.STROKE);
            draw_str(canvas, text, start, end, left, distance, paint);
            paint.setColor(fg_color);
            paint.setAlpha(opacity_int);
            paint.setStyle(Paint.Style.FILL);
            draw_str(canvas, text, start, end, left, distance, paint);
        } else if (edge_type.equalsIgnoreCase("shadow_right")) {
            paint.setShadowLayer(edge_width, edge_width, edge_width, edge_color);
            paint.setColor(fg_color);
            paint.setAlpha(opacity_int);
            draw_str(canvas, text, start, end, left, distance, paint);
        } else if (edge_type.equalsIgnoreCase("shadow_left")) {
            paint.setShadowLayer(edge_width, -edge_width, -edge_width, edge_color);
            paint.setColor(fg_color);
            paint.setAlpha(opacity_int);
            draw_str(canvas, text, start, end, left, distance, paint);
        } else if (edge_type.equalsIgnoreCase("raised") ||
                edge_type.equalsIgnoreCase("depressed")) {

            boolean raised;
            raised = !edge_type.equalsIgnoreCase("depressed");
            int colorUp = raised ? fg_color : edge_color;
            int colorDown = raised ? edge_color : fg_color;
            float offset = edge_width;
            paint.setColor(fg_color);
            paint.setStyle(Paint.Style.FILL);
            paint.setShadowLayer(edge_width, -offset, -offset, colorUp);
            draw_str(canvas, text, start, end, left, distance, paint);
            paint.setShadowLayer(edge_width, offset, offset, colorDown);
            draw_str(canvas, text, start, end, left, distance, paint);
        } else if (edge_type.equalsIgnoreCase("none")) {
            paint.setColor(fg_color);
            paint.setAlpha(opacity_int);
            draw_str(canvas, text, start, end, left, distance, paint);
        }
        if (underline) {
            paint.setStrokeWidth((float) (2.0));
            paint.setColor(fg_color);
            canvas.drawLine(left, distance + edge_width * 2,
                    (float) (left + mRowStr.string_length_on_paint),
                    distance + edge_width * 2, paint);
        }
    }

    static class RowStrInfo {
        /* For parse json use */
        boolean italics;
        boolean underline;
        int edge_color;
        int fg_color;
        int bg_color;
        String pen_size = "standard";
        String font_style = "default";
        String offset = "normal";
        String edge_type = "none";
        String fg_opacity = "";
        String bg_opacity = "";
        double string_length_on_paint;
        double str_start_x;
        double str_left;
        double str_top;
        double str_right;
        double str_bottom;
        double font_size;
        double pre_str_length;
        int str_characters_count;
        Paint.FontMetricsInt fontMetrics;

        /* below is the actual parameters we used */
        int fg_opacity_int = 0xff;
        int bg_opacity_int = 0xff;
        double font_scale;
        Typeface font_face;
        double edge_width;
        boolean use_caption_manager_style;
        boolean isStartStr = false;

        RowStrInfo(CcImplement.CaptionWindow.Window.Rows.RowStr str, boolean isStart) {
            isStartStr = isStart;
            italics = str.italics;
            underline = str.underline;
            edge_color = str.edge_color;
            fg_color = str.fg_color;
            bg_color = str.bg_color;
            pen_size = str.pen_size;
            font_style = str.font_style;
            offset = str.offset;
            edge_type = str.edge_type;
            fg_opacity = str.fg_opacity;
            bg_opacity = str.bg_opacity;
            string_length_on_paint = str.string_length_on_paint;
            str_start_x = str.str_start_x;
            str_left = str.str_left;
            str_top = str.str_top;
            str_right = str.str_right;
            str_bottom = str.str_bottom;
            font_size = str.font_size;
            str_characters_count = str.str_characters_count;
            fontMetrics = str.fontMetrics;
            fg_opacity_int = str.fg_opacity_int;
            bg_opacity_int = str.bg_opacity_int;
            font_scale = str.font_scale;
            font_face = str.font_face;
            edge_width = str.edge_width;
            use_caption_manager_style = str.use_caption_manager_style;
            pre_str_length = str.pre_str_length;
        }
    }

    class RowInfo {
        int row_str_count;
        double character_gap;
        double row_length_on_paint;
        int row_characters_count;
        double row_start_x;

        public RowInfo(CcImplement.CaptionWindow.Window.Rows rows) {
            row_str_count = rows.str_count;
            character_gap = rows.character_gap;
            row_length_on_paint = rows.row_length_on_paint;
            row_characters_count = rows.row_characters_count;
            row_start_x = rows.row_start_x;
        }
    }

    class WindowInfo {
        int heart_beat;
        int fill_opacity_int;
        String justify;
        int col_count;
        int fontScale;

        public WindowInfo(CcImplement.CaptionWindow.Window w) {
            this.heart_beat = w.heart_beat;
            this.justify = w.justify;
            this.fill_opacity_int = w.fill_opacity_int;
            this.col_count = w.col_count;
            this.fontScale = w.fontScale;
        }
    }
}
