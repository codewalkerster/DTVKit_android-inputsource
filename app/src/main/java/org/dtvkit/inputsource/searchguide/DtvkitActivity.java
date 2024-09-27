package com.droidlogic.dtvkit.inputsource.searchguide;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;

import com.droidlogic.app.DataProviderManager;

public class DtvkitActivity extends Activity {
    private final String TAG = "DtvKitActivity";
    @Override
    protected void onStart() {
        super.onStart();
        String currentInputId = DataProviderManager.getStringValue(this, "tv_current_inputid", "");
        if (currentInputId.contains("com.droidlogic.tvinput")) {
            Log.e(TAG, "wrong source:" + currentInputId);
            finish();
        }
    }

    protected void showScanSelectDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("You already scanned for channels")
        .setCancelable(false)
        .setMessage("Choose 'Scan again' to keep channel settings and channel list.\n" +
                "Alternatively, choose 'Start again' to clear your channel settings and channel list.")
        .setPositiveButton("Start again", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onPositiveSelect();
                dialog.dismiss();
            }
        })
        .setNegativeButton("Scan again", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onNegativeSelect();
                dialog.dismiss();
            }
        }).create();
        Log.i(TAG, "showScanSelectDialog");
        dialog.show();
    }

    public void onPositiveSelect() {
    }

    public void onNegativeSelect() {
    }
}
