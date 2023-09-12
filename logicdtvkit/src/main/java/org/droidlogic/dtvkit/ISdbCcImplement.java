package org.droidlogic.dtvkit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/* for ISdb used */
public class ISdbCcImplement {
    private final String TAG = ISdbCcImplement.class.getSimpleName();
    private final boolean DEBUG;
    private JSONObject mJSONObject;
    private final SpannableStringBuilder spannable = new SpannableStringBuilder();
    private final ArrayList<DisplayItem> mList = new ArrayList<>();
    private int video_width;
    private int video_height;
    private final Rect mDisplayRect;
    private int firstCharX, firstCharY;
    private float xScale = 1.0f;
    private float yScale = 1.0f;
    private int mOrientType = 0;
    private final HashMap<Integer, CharSequence> mReplace = new HashMap<>();
    boolean containFlashing = false;
    boolean bShowFlashing = true;

    public ISdbCcImplement(Rect displayRect, boolean debug) {
        mDisplayRect = displayRect;
        DEBUG = debug;
    }

    public SpannableStringBuilder getSpannable(JSONObject object) {
        mJSONObject = object;
        draw();
        return spannable;
    }

    public int[] getDisplayPosition() {
        return new int[] {firstCharX, firstCharY - (int) (20 * yScale)};
    }

    private void clear() {
        firstCharX = -1;
        firstCharY = -1;
        containFlashing = false;
        bShowFlashing = true;
        mReplace.clear();
        spannable.clear();
        spannable.clearSpans();
        mList.clear();
    }

    private void updateDisplayScale() {
        xScale = (float) mDisplayRect.width() / video_width;
        yScale = (float) mDisplayRect.height() / video_height;
        if (DEBUG) {
            Log.d(TAG, "xScale:" + xScale + ", yScale:" + yScale);
        }
    }

    private void draw() {
        try {
            clear();
            parseContent();
        } catch (JSONException e) {
            Log.e(TAG, "parse failed to draw:" + e.getMessage());
        }
    }

    public boolean containFlashingChar() {
        return containFlashing;
    }

    public SpannableStringBuilder doFlashing() {
        if (!containFlashing || mJSONObject == null || mReplace.size() == 0) {
            return spannable;
        }
        Log.d(TAG, "doFlashing to" + (bShowFlashing ? "Hide" : "Show") + ", " + mReplace);
        for (int i : mReplace.keySet()) {
            if (bShowFlashing) {
                spannable.replace(i, i + 1, " ");
            } else {
                spannable.replace(i, i + 1, mReplace.get(i));
            }
        }
        bShowFlashing = !bShowFlashing;
        return spannable;
    }

    private void parseContent() throws JSONException {
        if (mJSONObject == null) {
            return;
        }
        String mType = "aribCaption";
        if (!TextUtils.equals(mJSONObject.getString("type"), mType)) {
            Log.e(TAG, "invalid header type:" + mJSONObject.getString("type"));
            return;
        }
        video_width = mJSONObject.getInt("plane_width");
        video_height = mJSONObject.getInt("plane_height");
        updateDisplayScale();
        JSONArray array = mJSONObject.getJSONArray("chars");
        int swf = mJSONObject.getInt("swf");
        if (swf < 5) {
            mOrientType = swf % 2;
        } else {
            mOrientType = (swf + 1) % 2;
        }
//        Log.d(TAG, "Total items " + array.length());
        for (int i = 0; i < array.length(); i++) {
            DisplayItem displayItem;
            JSONObject item = (JSONObject) array.get(i);
            if (item.has("new_line")) {
                if (item.getInt("new_line") != 0) {
                    displayItem = new DisplayItem("\n", 10);
                } else {
                    continue;
                }
            } else {
                displayItem = new DisplayItem(" ", 32);
                displayItem
                        .setContent(item.getInt("codepoint"), item.getString("u8str"))
                        .setPosition(item.getInt("x"), item.getInt("y"))
                        .setCharRect(item.getInt("char_width"), item.getInt("char_height"))
                        .setCharType(item.getInt("char_style"), item.getInt("enclosure_style"))
                        .setSpacing(item.getInt("char_horizontal_spacing"), item.getInt("char_vertical_spacing"))
                        .setScale(item.getDouble("char_horizontal_scale"), item.getDouble("char_vertical_scale"))
                        .setRepeatFlash(item.getInt("char_repeat_display_times"), item.getInt("char_flash"))
                        .setColor(parseColor(item.getString("text_color")),
                                parseColor(item.getString("back_color")),
                                parseColor(item.getString("stroke_color")));
                boolean flash = displayItem.char_flash > 0;
                if (flash) {
                    containFlashing = true;
                }
                if (firstCharX < 0 || firstCharY < 0) {
                    firstCharX = displayItem.x;
                    firstCharY = displayItem.y;
                }
            }
            int oldLength = mList.size();
            int repeat = Math.max(displayItem.char_repeat_display_times, 1);
//            if (repeat > 1) {
//                Log.i(TAG, "cc repeat_times:" + repeat + ", code:" + item.getString("u8str"));
//            }
            for (int k = 0; k < repeat; k++) {
                mList.add(displayItem);
                if (displayItem.char_flash > 0) {
                    mReplace.put(oldLength, displayItem.u8str);
                }
            }
        }
        List<DisplayItem> h = mList;
        firstCharX = (int) (firstCharX * xScale);
        firstCharY = (int) (firstCharY * yScale);
        Log.i(TAG, "origin Position:(" + firstCharX + ", " + firstCharY + ")");
        if (mOrientType == 1) {
            // this case , list modified
            h = updateDisplayItem(mList, 1);
//            if (h.size() > 0) {
//                float xRatio = (float) h.get(0).x / (float) video_width;
//                float yRatio = (float) h.get(0).y / (float) video_height;
//                firstCharX = (int) (mDisplayRect.width() * xRatio);
//                firstCharY = (int) (mDisplayRect.height() * yRatio);
//            }
//            Log.i(TAG, "new Position:(" + firstCharX + ", " + firstCharY + ")");
        } else {
            // configurable
            boolean trimSpaceLine = true;
            if (trimSpaceLine) {
                h = updateDisplayItem(mList, 0);
            }
        }
        updateSpan(h);
    }

    private void updateSpan(List<DisplayItem> displayItemList) {
        for (DisplayItem displayItem: displayItemList) {
            int lastIndex = spannable.length();
            int span_mark = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;
            int repeat = displayItem.char_repeat_display_times;
            if (repeat > 1) {
                span_mark = Spannable.SPAN_INCLUSIVE_EXCLUSIVE;
            }
            CharSequence charSequence = displayItem.u8str;
            spannable.append(charSequence);
            if (displayItem.codepoint == 10) {
                continue;
            }
            LinearrBackgroundSpan span = new LinearrBackgroundSpan(displayItem);
            span.setPosition(displayItem.enclosure_style);
            spannable.setSpan(span,
                    lastIndex,
                    spannable.length(),
                    span_mark);

            double size = ((double) displayItem.char_width) * displayItem.char_horizontal_scale;
            if (size < 8.0f) {
                size = 8.0f;
            }
            spannable.setSpan(
                    new AbsoluteSizeSpan((int) (size * xScale)),
                    lastIndex,
                    spannable.length(),
                    span_mark);
            spannable.setSpan(
                    new ForegroundColorSpan(displayItem.text_color),
                    lastIndex,
                    spannable.length(),
                    span_mark
            );
//            spannable.setSpan(
//                    new BackgroundColorSpan(displayItem.back_color),
//                    lastIndex,
//                    spannable.length(),
//                    span_mark
//            );
            if ((displayItem.char_style & 0x03) == 0x03) {
                spannable.setSpan(
                        new StyleSpan(Typeface.BOLD_ITALIC),
                        lastIndex,
                        spannable.length(),
                        span_mark
                );
            } else if ((displayItem.char_style & 0x02) == 0x02) {
                spannable.setSpan(
                        new StyleSpan(Typeface.ITALIC),
                        lastIndex,
                        spannable.length(),
                        span_mark
                );
            } else if ((displayItem.char_style & 0x01) == 0x01) {
                spannable.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        lastIndex,
                        spannable.length(),
                        span_mark
                );
            }

        }
        Log.i(TAG, "spannable -->");
        Log.i(TAG, spannable.toString());
    }

    private List<DisplayItem> updateDisplayItem(final List<DisplayItem> originList, int orientType) {
        List<List<DisplayItem>> everyLineList = new ArrayList<>();
        List<DisplayItem> newList = new ArrayList<>();
        // backward line comes to forward
        int m = originList.size();
        int nMaxLen = 0;
        for (int i = originList.size() - 1; i >= 0; i--) {
            if ((originList.get(i).codepoint == 10)) {
                List<DisplayItem> sub = originList.subList(i+1, m);
                nMaxLen = Math.max(sub.size(), nMaxLen);
                if (DEBUG) {
                    Log.d(TAG, "i=" + i + "," + originList.get(i).codepoint + ", m:" + m
                            + ", nMaxLen " + nMaxLen + ",sub:" + sub);
                }
                everyLineList.add(sub);
                m = i + 1;
            } else if (i == 0) {
                List<DisplayItem> sub = originList.subList(0, m);
                nMaxLen = Math.max(sub.size(), nMaxLen);
                if (DEBUG) {
                    Log.d(TAG, "i=" + i + "," + originList.get(i).codepoint + ", m:" + m
                            + ", nMaxLen " + nMaxLen + ",sub:" + sub);
                }
                everyLineList.add(sub);
                break;
            }
        }

        final int arrCount = everyLineList.size();
        if (DEBUG) {
            Log.i(TAG, "arrCount " + arrCount + ", nMaxLen " + nMaxLen);
        }
        for (int i = arrCount - 1; i >= 0; i--) {
            boolean trim = true;
            for (DisplayItem item : everyLineList.get(i)) {
                if ((item.codepoint == 10)
                    || (item.codepoint == 32 && item.back_color == 0)) {
                    trim = true;
                } else {
                    trim = false;
                    break;
                }
            }
            if (trim) {
                Log.w(TAG, "trim line=" + i);
            } else {
                newList.addAll(everyLineList.get(i));
            }
        }
        if (orientType == 0) {
            return newList;
        }
        DisplayItem[][] backUp = new DisplayItem[arrCount][nMaxLen];
        for (int i = 0; i < arrCount; i++) {
            for (int k = 0; k < everyLineList.get(i).size(); k++) {
                backUp[i][k] = everyLineList.get(i).get(k);
            }
        }
        // [debug]
        if (DEBUG) {
            StringBuilder o = new StringBuilder();
            for (DisplayItem item : originList) {
                o.append(item.toString());
            }
            Log.i(TAG, "old item " + o);
        }
        int pIndex = 0;
        // replace new_line to space holding.
        for (int k = 0; k < nMaxLen; k++) {
            for (int i = 0; i < arrCount; i++) {
                if (DEBUG) {
                    Log.i(TAG, "backup[" + i + "][" + k + "]=" + backUp[i][k]);
                }
                if (backUp[i][k] != null) {
                    if (backUp[i][k].codepoint == 10) {
                        originList.add(pIndex, DisplayItem.createSpace(originList.get(pIndex)));
                    } else {
                        originList.add(pIndex, backUp[i][k]);
                    }
                } else {
                    originList.add(pIndex, DisplayItem.createSpace(originList.get(pIndex)));
                }
                pIndex++;
            }
            // append new_line
            originList.add(pIndex, new DisplayItem("\n", 10));
            pIndex++;
        }
        newList = originList.subList(0, nMaxLen * (arrCount + 1));
        // [debug]
        if (DEBUG) {
            StringBuilder n = new StringBuilder();
            for (DisplayItem item : newList) {
                n.append(item.toString());
            }
            Log.i(TAG, "new item " + n);
        }
        return newList;
    }

    // "text_color":"rgba(0, 0, 0, 255)",
    private int parseColor(String value) {
        if (TextUtils.isEmpty(value)) {
            return Color.BLACK;
        }
        int left = value.indexOf('(');
        String numbers = value.substring(left + 1, value.length() -1);
        String[] number = numbers.split(",");
        // Log.d(TAG, "parseColor: " + Arrays.toString(number));
        int r = 0, g = 0, b = 0, a = 0xff;
        String agrb = value.substring(0, left);
        for (int i = 0; i< agrb.length(); i++) {
            if (agrb.charAt(i) == 'r') {
                r = Integer.parseInt(number[i].trim());
            } else if (agrb.charAt(i) == 'g') {
                g = Integer.parseInt(number[i].trim());
            } else if (agrb.charAt(i) == 'b') {
                b = Integer.parseInt(number[i].trim());
            } else if (agrb.charAt(i) == 'a') {
                a = Integer.parseInt(number[i].trim());
            } else {
                Log.e(TAG, "error type format: " + value);
                return Color.BLACK;
            }
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static class DisplayItem {
        int x;
        int y;
        int char_horizontal_spacing;
        int char_vertical_spacing;
        double char_horizontal_scale;
        double char_vertical_scale;
        int codepoint;
        int pua_codepoint;
        int drcs_code;
        int char_style;
        int enclosure_style;
        CharSequence u8str;
        int text_color;
        int back_color;
        int stroke_color;
        int stroke_width;
        int char_width;
        int char_height;
        int section_width;
        int section_height;

        int char_flash;
        int char_repeat_display_times;

        boolean isFake;
        public DisplayItem(CharSequence c, int codepoint) {
            u8str = c;
            this.codepoint = codepoint;
        }

        public DisplayItem(CharSequence c, int codepoint, boolean fake) {
            u8str = c;
            this.codepoint = codepoint;
            this.isFake = fake;
        }

        public DisplayItem setContent(int codepoint, String u8str) {
            this.codepoint = codepoint;
            this.u8str = u8str;
            return this;
        }

        public DisplayItem setPosition(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public DisplayItem setScale(double xScale, double yScale) {
            char_horizontal_scale = xScale;
            char_vertical_scale = yScale;
            return this;
        }

        public DisplayItem setCharType(int char_style, int enclosure_style) {
            this.char_style = char_style;
            this.enclosure_style = enclosure_style;
            return this;
        }

        public DisplayItem setColor(int text_color, int back_color, int stroke_color) {
            this.text_color = text_color;
            this.back_color = back_color;
            this.stroke_color = stroke_color;
            return this;
        }

        public DisplayItem setCharRect(int width, int height) {
            char_width = width;
            char_height = height;
            return this;
        }

        public DisplayItem setSpacing(int horizontal_spacing, int vertical_spacing) {
            char_horizontal_spacing = horizontal_spacing;
            char_vertical_spacing = vertical_spacing;
            return this;
        }

        public DisplayItem setRepeatFlash(int repeat, int flash) {
            char_repeat_display_times = repeat;
            char_flash = flash;
            return this;
        }

        public static DisplayItem createSpace(DisplayItem item) {
            DisplayItem displayItem = new DisplayItem(" ", 32, true);
            displayItem.char_horizontal_spacing = item.char_horizontal_spacing;
            displayItem.char_vertical_spacing = item.char_vertical_spacing;
            displayItem.char_horizontal_scale = item.char_horizontal_scale;
            displayItem.char_vertical_scale = item.char_vertical_scale;
            displayItem.char_style = item.char_style;
            displayItem.text_color = Color.TRANSPARENT;
            displayItem.back_color = Color.TRANSPARENT;
            displayItem.char_width = item.char_width;
            displayItem.char_height = item.char_height;
            displayItem.section_width = item.section_width;
            displayItem.section_height = item.section_height;
            return displayItem;
        }

        @Override
        public String toString() {
            if (u8str == "\n" || codepoint == 10) {
                return "-";
            } else {
                return String.valueOf(u8str);
            }
        }
    }

    public static class LinearrBackgroundSpan extends ReplacementSpan {

        private int mSize;
        private final int mtextColor;
        private final int mBackgroundColor;
        private final int mStrokColor;
        private final int mStrokeWidth;
        private final boolean isUnderLine;

        private int mPosition;

        public LinearrBackgroundSpan(DisplayItem displayItem) {
            mtextColor = displayItem.text_color;
            mBackgroundColor = displayItem.back_color;
            mStrokColor = displayItem.stroke_color;
            mStrokeWidth = displayItem.stroke_width;
            isUnderLine = (displayItem.char_style & 0x04) == 0x04;
        }

        private void setPosition(int position) {
            mPosition |= position;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            mSize = (int) (paint.measureText(text, start, end));
            return mSize;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Paint bgPaint = new Paint();
            bgPaint.setColor(mBackgroundColor);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setAntiAlias(true);
            Paint.FontMetrics fm = paint.getFontMetrics();
            RectF bgRect = new RectF(x, y + fm.ascent, x + mSize, y + fm.descent);
            canvas.drawRect(bgRect, bgPaint);
            paint.setAntiAlias(true);
            paint.setColor(Color.BLACK);
            // bottom
            if ((mPosition & 0x01) != 0) {
                canvas.drawLine(x, y + fm.descent, x + mSize, y + fm.descent, paint);
            }
            // right
            if ((mPosition & 0x02) != 0) {
                canvas.drawLine(x + mSize, y + fm.ascent, x + mSize, y + fm.descent, paint);
            }
            // up
            if ((mPosition & 0x04) != 0) {
                canvas.drawLine(x, y + fm.ascent, x + mSize, y + fm.ascent, paint);
            }
            // left
            if ((mPosition & 0x08) != 0) {
                canvas.drawLine(x, y + fm.ascent, x, y + fm.descent, paint);
            }
            paint.setColor(mtextColor);
            paint.setUnderlineText(isUnderLine);
            canvas.drawText(text, start, end, x, y, paint);
            if (mStrokColor != 0) {
                paint.setColor(mStrokColor);
                paint.setStrokeWidth(mStrokeWidth);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawText(text, start, end, x, y, paint);
            }
        }
    }
}
