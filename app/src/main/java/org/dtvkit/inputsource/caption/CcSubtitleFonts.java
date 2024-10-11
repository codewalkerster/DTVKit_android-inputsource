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
import android.graphics.Typeface;
import android.util.Log;
import java.io.File;
/**
 * Created by daniel on 06/11/2017.
 */

public class CcSubtitleFonts {
    final String TAG = "CcSubtitleFonts";
    Typeface mono_serif_tf;
    Typeface mono_serif_it_tf;
    Typeface casual_tf;
    Typeface casual_it_tf;
    Typeface prop_sans_tf;
    Typeface prop_sans_it_tf;
    Typeface small_capital_tf;
    Typeface small_capital_it_tf;
    Typeface cursive_tf;
    Typeface cursive_it_tf;
    Typeface serif_tf;
    Typeface serif_it_tf;

    CcSubtitleFonts(Context context)
    {
        try {
            mono_serif_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_mono.ttf"));
            mono_serif_it_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_mono_it.ttf"));
            casual_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_casual.ttf"));
            casual_it_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_casual_it.ttf"));
            prop_sans_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_sans.ttf"));
            prop_sans_it_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_sans_it.ttf"));
            small_capital_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_sc.ttf"));
            small_capital_it_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_sc_it.ttf"));
            cursive_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_script.ttf"));
            cursive_it_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_script_it.ttf"));
            serif_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_serif.ttf"));
            serif_it_tf = Typeface.createFromFile(new File(context.getDataDir(), "font/cinecavD_serif_it.ttf"));
        } catch (Exception e) {
            Log.e(TAG, "error " + e.toString());
        } finally {
            if (mono_serif_tf == null) mono_serif_tf = Typeface.DEFAULT;
            if (mono_serif_it_tf == null) mono_serif_it_tf = Typeface.DEFAULT;
            if (casual_tf == null) casual_tf = Typeface.DEFAULT;
            if (casual_it_tf == null) casual_it_tf = Typeface.DEFAULT;
            if (prop_sans_tf == null) prop_sans_tf = Typeface.DEFAULT;
            if (prop_sans_it_tf == null) prop_sans_it_tf = Typeface.DEFAULT;
            if (small_capital_tf == null) small_capital_tf = Typeface.DEFAULT;
            if (small_capital_it_tf == null) small_capital_it_tf = Typeface.DEFAULT;
            if (serif_tf == null) serif_tf = Typeface.DEFAULT;
            if (serif_it_tf == null) serif_it_tf = Typeface.DEFAULT;
        }
    }
}
