package com.droidlogic.dtvkit.inputsource.searchguide;

import android.app.Activity;
import android.util.Log;

import com.droidlogic.app.DataProviderManager;

public class DtvkitActivity extends Activity {

    @Override
    protected void onStart() {
        super.onStart();
        String currentInputId = DataProviderManager.getStringValue(this, "tv_current_inputid", "");
        if (currentInputId.contains("com.droidlogic.tvinput")) {
            Log.e("DtvKit", "wrong source:" + currentInputId);
            finish();
        }
    }
}
