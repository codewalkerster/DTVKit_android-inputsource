package com.droidlogic.dtvkit.inputsource;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TargetRegionManager {
    private final static String TAG = "TargetRegionManager";
    public static final int TARGET_REGION_COUNTRY = 0;
    public static final int TARGET_REGION_PRIMARY = 1;
    public static final int TARGET_REGION_SECONDARY = 2;
    public static final int TARGET_REGION_TERTIARY = 3;

    private Context mContext;
    private TargetRegionsCallbacks mCallback;
    private Spinner mSpinnerCountry;
    private Spinner mSpinnerPrimary;
    private Spinner mSpinnerSecondary;
    private Spinner mSpinnerTertiary;
    private Map<String, Integer> mCountryRegions;
    private Map<String, Integer> mPrimaryRegions;
    private Map<String, Integer> mSecondaryRegions;
    private Map<String, Integer> mTertiaryRegions;

    /**
     * @param context Need ActivityContext
     */
    TargetRegionManager(Context context) {
        mContext = context;
    }

    void setRegionCallback(TargetRegionsCallbacks cb) {
        mCallback = cb;
    }

    void start(boolean isScreenOn) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(mContext, R.layout.region_list_spin, null);
        if (isScreenOn) {
            Objects.requireNonNull(alert.getWindow()).
                    setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        } else {
            Objects.requireNonNull(alert.getWindow()).
                    setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        alert.setView(dialogView);
        mSpinnerCountry = dialogView.findViewById(R.id.spinner_region_country);
        mSpinnerPrimary = dialogView.findViewById(R.id.spinner_region_primary);
        mSpinnerSecondary = dialogView.findViewById(R.id.spinner_region_secondary);
        mSpinnerTertiary = dialogView.findViewById(R.id.spinner_region_tertiary);
        if (mSpinnerCountry == null
            || mSpinnerPrimary == null
            || mSpinnerSecondary == null
            || mSpinnerTertiary == null) {
            return;
        }
        Log.d(TAG, "Show Dialog");
        mSpinnerCountry.setOnItemSelectedListener(new InnerItemSelectedListener(TARGET_REGION_COUNTRY));
        mSpinnerPrimary.setOnItemSelectedListener(new InnerItemSelectedListener(TARGET_REGION_PRIMARY));
        mSpinnerSecondary.setOnItemSelectedListener(new InnerItemSelectedListener(TARGET_REGION_SECONDARY));
        mSpinnerTertiary.setOnItemSelectedListener(new InnerItemSelectedListener(TARGET_REGION_TERTIARY));
        mSpinnerCountry.requestFocus();
        if (mCallback != null) {
            Map<String, Integer> regionList = mCallback.requestRegionList(TARGET_REGION_COUNTRY);
            updateRegions(TARGET_REGION_COUNTRY, regionList);
        }
        Button btn = dialogView.findViewById(R.id.button_region_confirm);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alert.dismiss();
            }
        });
        alert.show();
        WindowManager.LayoutParams params = alert.getWindow().getAttributes();
        params.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500, mContext.getResources().getDisplayMetrics());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        alert.getWindow().setAttributes(params);
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (mCallback != null) {
                    mCallback.onFinishWithSelections(
                            getRegionCode(TARGET_REGION_COUNTRY),
                            getRegionCode(TARGET_REGION_PRIMARY),
                            getRegionCode(TARGET_REGION_SECONDARY),
                            getRegionCode(TARGET_REGION_TERTIARY));
                }
            }
        });
    }

    public int getRegionCode(int target_id) {
        int ret = -1;
        switch (target_id) {
            case TARGET_REGION_COUNTRY:
                if (mSpinnerCountry != null) {
                    String item = (String)mSpinnerCountry.getSelectedItem();
                    if (item != null) {
                        if (mCountryRegions != null) {
                            Integer tmp = mCountryRegions.get(item);
                            ret = (tmp != null) ? tmp : -1;
                        } else {
                            ret = mSpinnerCountry.getSelectedItemPosition();
                        }
                    }
                }
                break;
            case TARGET_REGION_PRIMARY:
                if (mSpinnerPrimary != null) {
                    String item = (String)mSpinnerPrimary.getSelectedItem();
                    if (item != null) {
                        if (mPrimaryRegions != null) {
                            Integer tmp = mPrimaryRegions.get(item);
                            ret = (tmp != null) ? tmp : -1;
                        } else {
                            ret = mSpinnerPrimary.getSelectedItemPosition();
                        }
                    }
                }
                break;
            case TARGET_REGION_SECONDARY:
                if (mSpinnerSecondary != null) {
                    String item = (String)mSpinnerSecondary.getSelectedItem();
                    if (item != null) {
                        if (mSecondaryRegions != null) {
                            Integer tmp = mSecondaryRegions.get(item);
                            ret = (tmp != null) ? tmp : -1;
                        } else {
                            ret = mSpinnerSecondary.getSelectedItemPosition();
                        }
                    }
                }
                break;
            case TARGET_REGION_TERTIARY:
                if (mSpinnerTertiary != null) {
                    String item = (String)mSpinnerTertiary.getSelectedItem();
                    if (item != null) {
                        if (mTertiaryRegions != null) {
                            Integer tmp = mTertiaryRegions.get(item);
                            ret = (tmp != null) ? tmp : -1;
                        } else {
                            ret = mSpinnerTertiary.getSelectedItemPosition();
                        }
                    }
                }
                break;
            default:
                break;
        }
        return ret;
    }

    public int getRegionPosition(int target_id) {
        int ret = -1;
        switch (target_id) {
            case TARGET_REGION_COUNTRY:
                ret = mSpinnerCountry.getSelectedItemPosition();
                break;
            case TARGET_REGION_PRIMARY:
                ret = mSpinnerPrimary.getSelectedItemPosition();
                break;
            case TARGET_REGION_SECONDARY:
                ret = mSpinnerSecondary.getSelectedItemPosition();
                break;
            case TARGET_REGION_TERTIARY:
                ret = mSpinnerTertiary.getSelectedItemPosition();
                break;
            default:
                break;
        }
        return ret;
    }

    private void updateRegions(int target_id, Map<String, Integer> regions) {
        boolean updateRegions = false;
        switch (target_id) {
            case TARGET_REGION_COUNTRY:
                if (mSpinnerCountry != null) {
                    mCountryRegions = regions;
                    updateRegions = true;
                }
                break;
            case TARGET_REGION_PRIMARY:
                if (mSpinnerPrimary != null) {
                    mPrimaryRegions = regions;
                    updateRegions = true;
                }
                break;
            case TARGET_REGION_SECONDARY:
                if (mSpinnerSecondary != null) {
                    mSecondaryRegions = regions;
                    updateRegions = true;
                }
                break;
            case TARGET_REGION_TERTIARY:
                if (mSpinnerTertiary != null) {
                    mTertiaryRegions = regions;
                    updateRegions = true;
                }
                break;
            default:
                break;
        }
        if (updateRegions) {
            if (regions != null && !regions.isEmpty()) {
                List<String> regionList = new ArrayList<>(regions.keySet());
                updateRegions(target_id, regionList.toArray(new String[0]));
            } else {
                updateRegions(target_id, new String[0]);
            }
        }
    }

    private void updateRegions(int target_id, String[] regions) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(mContext, R.layout.simple_spinner_item, regions);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        switch (target_id) {
            case TARGET_REGION_COUNTRY:
                if (mSpinnerCountry != null) {
                    mSpinnerCountry.setAdapter(adapter);
                    if (adapter.getCount() == 0) {
                        if (mSpinnerPrimary != null) {
                            mSpinnerPrimary.setAdapter(adapter);
                        }
                        if (mSpinnerSecondary != null) {
                            mSpinnerSecondary.setAdapter(adapter);
                        }
                        if (mSpinnerTertiary != null) {
                            mSpinnerTertiary.setAdapter(adapter);
                        }
                    }
                }
                break;
            case TARGET_REGION_PRIMARY:
                if (mSpinnerPrimary != null) {
                    mSpinnerPrimary.setAdapter(adapter);
                    if (adapter.getCount() == 0) {
                        if (mSpinnerSecondary != null) {
                            mSpinnerSecondary.setAdapter(adapter);
                        }
                        if (mSpinnerTertiary != null) {
                            mSpinnerTertiary.setAdapter(adapter);
                        }
                    }
                }
                break;
            case TARGET_REGION_SECONDARY:
                if (mSpinnerSecondary != null) {
                    mSpinnerSecondary.setAdapter(adapter);
                    if (adapter.getCount() == 0) {
                        if (mSpinnerTertiary != null) {
                            mSpinnerTertiary.setAdapter(adapter);
                        }
                    }
                }
                break;
            case TARGET_REGION_TERTIARY:
                if (mSpinnerTertiary != null) {
                    mSpinnerTertiary.setAdapter(adapter);
                }
                break;
            default:
                break;
        }
    }

    public interface TargetRegionsCallbacks {
        Map<String,Integer> requestRegionList(int target_id);
        boolean onRegionSelected(int target_id, int selection_id);
        void onFinishWithSelections(int country, int primary, int secondary, int tertiary);
    }

    private class InnerItemSelectedListener implements AdapterView.OnItemSelectedListener {
        private final int mTargetId;

        InnerItemSelectedListener(int target_id) {
            mTargetId = target_id;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (mCallback != null) {
                int selection_id = getRegionCode(mTargetId);
                if (mCallback.onRegionSelected(mTargetId, selection_id)) {
                    if (mTargetId < TARGET_REGION_TERTIARY) {
                        Map<String, Integer> regions = mCallback.requestRegionList(mTargetId + 1);
                        updateRegions(mTargetId + 1, regions);
                    }
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}