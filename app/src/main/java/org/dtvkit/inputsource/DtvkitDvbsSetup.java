package com.droidlogic.dtvkit.inputsource;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.media.tv.TvInputService;
import android.media.tv.tuner.Tuner;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.droidlogic.dtvkit.inputsource.searchguide.DataPresenter;
import com.droidlogic.dtvkit.inputsource.searchguide.DtvkitDvbsSetupFragment;
import com.droidlogic.dtvkit.inputsource.searchguide.OnNextListener;
import com.droidlogic.dtvkit.inputsource.searchguide.SearchStageFragment;
import com.droidlogic.dtvkit.inputsource.searchguide.SimpleListFragment;
import com.droidlogic.fragment.DvbsParameterManager;
import com.droidlogic.fragment.ScanDishSetupFragment;
import com.droidlogic.dtvkit.inputsource.util.FeatureUtil;
import droidlogic.dtvkit.tuner.TunerAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class DtvkitDvbsSetup extends Activity {
    private static final String TAG = DtvkitDvbsSetup.class.getSimpleName();
    private SearchStageFragment fragment = null;
    private DataPresenter mDataPresenter = null;
    private boolean manualDiseqc = false;
    private boolean manualTKGS = false;
    private TunerAdapter mTunerAdapter = null;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mDataPresenter = new DataPresenter(this);
        String tag = DataPresenter.FRAGMENT_SEARCH_UI;
        if (intent != null) {
            manualTKGS = intent.getIntExtra("manual", -1) == 2;
            manualDiseqc = intent.getIntExtra("manual", -1) == 1;
            if (manualDiseqc) {
                showFragment(DataPresenter.FRAGMENT_MANUAL_DISEQC);
            }
        }
        if (fragment == null) {
            showFragment(DataPresenter.FRAGMENT_SEARCH_UI);
        }
        /************For tuner framework***************/
        if (FeatureUtil.getFeatureSupportTunerFramework()) {
            Tuner tuner = new Tuner(this, null, TvInputService.PRIORITY_HINT_USE_CASE_TYPE_SCAN);
            mTunerAdapter = new TunerAdapter(tuner, TunerAdapter.TUNER_TYPE_SCAN);
        }
    }

    @Override
    public void finish() {
        Log.d(TAG, "finish");
        if (null != mTunerAdapter) {
            mTunerAdapter.release();
            mTunerAdapter = null;
        }
        super.finish();
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (null != mTunerAdapter) {
            mTunerAdapter.release();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (fragment != null) {
            if (fragment.handleKeyDown(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showFragment(String title) {
        showFragment(title, null);
    }

    private void showFragment(String title, LinkedHashMap<String, List<String>> data) {
        Log.d(TAG, "showFragment " + title);
        switch (title) {
            case DataPresenter.FRAGMENT_OPERATOR_LIST:
            case DataPresenter.FRAGMENT_OPERATOR_SUB_LIST:
            case DataPresenter.FRAGMENT_SERVICE_LIST:
                fragment = SimpleListFragment.newInstance(title);
                break;
            case DataPresenter.FRAGMENT_SEARCH_UI:
                fragment = DtvkitDvbsSetupFragment.newInstance(title);
                fragment.setParameterManager(mDataPresenter.getParameterManager());
                fragment.setDialogManager(mDataPresenter.getDialogManager());
                Bundle bundle = new Bundle();
                bundle.putBoolean("manualDiseqc", manualDiseqc);
                bundle.putBoolean("manualTKGS", manualTKGS);
                fragment.setArguments(bundle);
                break;
            case DataPresenter.FRAGMENT_MANUAL_DISEQC:
                fragment = new ScanDishSetupFragment();
                Bundle b = new Bundle();
                b.putInt("operator_code", DataPresenter.getOperateType());
                fragment.setArguments(b);
                fragment.setTitle(title);
                fragment.setParameterManager(mDataPresenter.getParameterManager());
                fragment.setDialogManager(mDataPresenter.getDialogManager());
                break;
            case DataPresenter.FRAGMENT_REGIONAL_CHANNELS:
                fragment = SimpleListFragment.newInstance(title, true);
                break;
            default:
                return;
        }
        if (fragment instanceof SimpleListFragment && data != null) {
            ((SimpleListFragment) fragment).updateDataWithExtra(data);
        }
        fragment.setListener(mOnNextListener);
        Log.d(TAG, "showFragment set params");
        mHandler.post(() -> {
            if (title.equals(DataPresenter.FRAGMENT_SEARCH_UI)) {
                if (!showSearchFragment(true)) {
                    if (getFragmentManager().isDestroyed()) {
                        return;
                    }
                    getFragmentManager().beginTransaction()
                            .addToBackStack(title)
                            .add(android.R.id.content, fragment)
                            .commit();
                }
            } else {
                showSearchFragment(false);
                if (getFragmentManager().isDestroyed()) {
                    return;
                }
                getFragmentManager().beginTransaction()
                        .addToBackStack(title)
                        .add(android.R.id.content, fragment)
                        .commit();
            }
        });
    }

    private boolean showSearchFragment(boolean show) {
        if (getFragmentManager().isDestroyed()) {
            return true;
        }
        List<Fragment> fragments = getFragmentManager().getFragments();
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Log.i(TAG, "showSearchFragment " + fragments);
        Fragment searchFragment = null;
        for (Fragment fragment : fragments) {
            if (fragment instanceof DtvkitDvbsSetupFragment) {
                searchFragment = fragment;
                if (show) {
                    ft.show(searchFragment);
                } else {
                    ft.hide(searchFragment);
                }
            } else {
                // do not need any other fragments
                if (fragment instanceof SearchStageFragment && show) {

                    Log.i(TAG, "remove " + fragment );
                    getFragmentManager().popBackStack(
                                    ((SearchStageFragment) fragment).getTitle(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
            }
        }
        if (searchFragment == null && !show) {
            return false;
        }
        if (searchFragment != null) {
            Log.i(TAG, "search ui exists, " + show);
            ft.commitAllowingStateLoss();
            return true;
        }
        return false;
    }

    private final OnNextListener mOnNextListener = new OnNextListener() {
        @Override
        public void onNext(Fragment fragment, String text) {
            onNext(fragment, text, 0);
        }

        public void onNext(Fragment fragment, String text, @Nullable JSONArray array) {
            if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_ASTRA_HD_PLUS) {
                String title = ((SearchStageFragment) fragment).getTitle();
                if (fragment instanceof DtvkitDvbsSetupFragment) {
                    if (TextUtils.equals(text, DataPresenter.FRAGMENT_SERVICE_LIST)) {
                        if (array == null || array.length() == 0) {
                            Log.w(TAG, " array is null");
                        } else {
                            LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
                            try {
                                for (int i = 0; i < array.length(); i++) {
                                    String id = ((JSONObject) array.get(i)).getString("id");
                                    String name = ((JSONObject) array.get(i)).getString("name");
                                    List<String> idList = new ArrayList<>();
                                    idList.add(id);
                                    result.put(id + " " + name, idList);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            } finally {
                                showFragment(text, result);
                            }

                        }
                    }
                }
            }
        }

        @Override
        public void onNext(Fragment fragment, String text, int pos) {
            Log.i(TAG, "from fragment:" + fragment + ", " + text);
            if (fragment instanceof ScanDishSetupFragment) {
                if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7) {
                    JSONArray arg = new JSONArray();
                    arg.put(DvbsParameterManager.CMD_OPERATOR_M7
                            + DvbsParameterManager.CMD_ACTION_DISEQC_CONFIRM);
                    mDataPresenter.getParameterManager().dvbsScanControl(arg);
                }
                showFragment(DataPresenter.FRAGMENT_SEARCH_UI);
            } else if (fragment instanceof DtvkitDvbsSetupFragment) {
                // show next fragment
                String nextFragment = null;
                if (TextUtils.equals(text, DataPresenter.FRAGMENT_MANUAL_DISEQC)
                        || TextUtils.equals(text, DataPresenter.FRAGMENT_OPERATOR_LIST)
                        || TextUtils.equals(text, DataPresenter.FRAGMENT_OPERATOR_SUB_LIST)
                        || TextUtils.equals(text, DataPresenter.FRAGMENT_REGIONAL_CHANNELS)) {
                    nextFragment = text;
                }
                String finalNextFragment = nextFragment;
                if (finalNextFragment != null) {
                    showFragment(finalNextFragment);
                } else if ("finish".equals(text)) {
                    finish();
                }else {
                    Log.e(TAG, "wrong text:" + text);
                }
            } else if (fragment instanceof SearchStageFragment) {
                if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7) {
                    String title = ((SearchStageFragment) fragment).getTitle();
                    // item selected in a SearchStageFragment
                    if (TextUtils.equals(title, DataPresenter.FRAGMENT_OPERATOR_LIST)) {
                        JSONArray arg = new JSONArray();
                        arg.put(DvbsParameterManager.CMD_OPERATOR_M7
                                + DvbsParameterManager.CMD_ACTION_SET_OPERATOR);
                        arg.put(text);
                        mDataPresenter.getParameterManager().dvbsScanControl(arg);
                        showFragment(DataPresenter.FRAGMENT_OPERATOR_SUB_LIST);
                        return;
                    } else if (TextUtils.equals(title, DataPresenter.FRAGMENT_OPERATOR_SUB_LIST)) {
                        JSONArray arg = new JSONArray();
                        arg.put(DvbsParameterManager.CMD_OPERATOR_M7
                                + DvbsParameterManager.CMD_ACTION_SET_SUB_OPERATOR);
                        arg.put(text);
                        mDataPresenter.getParameterManager().dvbsScanControl(arg);
                    } else if (TextUtils.equals(title, DataPresenter.FRAGMENT_REGIONAL_CHANNELS)) {
                        // text is a HashMap toString
                        int length = text.length();
                        String next = text.substring(1, length - 1); //remove '{', '}'
                        String[] arr = next.split(",");
                        if (arr.length > 0) {
                            JSONArray out = new JSONArray();
                            out.put(DvbsParameterManager.CMD_OPERATOR_M7
                                    + DvbsParameterManager.CMD_ACTION_SET_REGION);
                            try {
                                JSONArray in = new JSONArray();
                                for (String value : arr) {
                                    JSONObject object = new JSONObject();
                                    String[] s = value.trim().split("=");
                                    object.put("RegionName", s[0]);
                                    object.put("SetRegion", s[1]);
                                    in.put(object);
                                }
                                out.put(in);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            } finally {
                                Log.d(TAG, "target:" + out);
                            }
                            mDataPresenter.getParameterManager().dvbsScanControl(out);
                        } else {
                            Log.e(TAG, "Error parse");
                        }
                    } else {
                        Log.e(TAG, "cannot handle it from:" + title);
                    }
                } else if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_ASTRA_HD_PLUS) {
                    List<String> list = ((SimpleListFragment) fragment).getExtraData(text);
                    if (list == null) {
                        Log.e(TAG, "getExtraData null");
                        return;
                    }
                    String[] id_name = text.split(" ", 2);
                    int id = Integer.parseInt(id_name[0]);
                    mDataPresenter.getParameterManager().dvbsSelectServiceList(id, id_name[1]);
                }  else if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_SKY_D) {

                } else {
                    Log.e(TAG, "Error info: " + ((SearchStageFragment) fragment).getTitle());
                }
                mHandler.post(() -> {
                    showSearchFragment(true);
                });
            }
        }

        @Override
        public void onFragmentReady(Fragment fragment) {
            String title = "";
            if (fragment instanceof SimpleListFragment) {
                title = ((SearchStageFragment) fragment).getTitle();
                if (TextUtils.equals(title, DataPresenter.FRAGMENT_OPERATOR_LIST)
                        || TextUtils.equals(title, DataPresenter.FRAGMENT_OPERATOR_SUB_LIST)) {
                    int pos = 0;
                    JSONArray arg = new JSONArray();
                    if (title.equals(DataPresenter.FRAGMENT_OPERATOR_LIST)) {
                        arg.put(DvbsParameterManager.CMD_OPERATOR_M7
                                + DvbsParameterManager.CMD_ACTION_GET_OPERATOR);
                    } else {
                        arg.put(DvbsParameterManager.CMD_OPERATOR_M7
                                + DvbsParameterManager.CMD_ACTION_GET_SUB_OPERATOR);
                    }
                    List<String> dataList = parseJsonObject(mDataPresenter.getParameterManager().dvbsScanControl(arg));
                    ((SimpleListFragment) fragment).updateData(dataList, pos);
                } else if (TextUtils.equals(title, DataPresenter.FRAGMENT_REGIONAL_CHANNELS)) {
                    ((SimpleListFragment) fragment).updateDataWithExtra(getRegionList());
                }
            } else {
                Log.e(TAG, "To Be Done!");
            }
        }
    };

    private List<String> parseJsonObject(JSONObject jsonObject) {
//        Log.i(TAG, "parseJsonObject " + jsonObject);
        List<String> dataList = new ArrayList<>();
        if (jsonObject != null) {
            try {
                JSONArray data = (JSONArray) jsonObject.get("data");
                if (data.length() == 0) {
                    return null;
                }
                for (int i = 0; i < data.length(); i++) {
                    String item = (String) data.get(i);
                    dataList.add(item);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return dataList;
    }

    //    "data":[{"RegionName":"CT2","RegionSrvList":["CT 2 HD","O C KO"]},
    //    {"RegionName":"CT1","RegionSrvList":["CT 1 HD","CT 1 JM HD","CT 1 SM HD"]}]
    private HashMap<String, List<String>> getRegionList() {
        HashMap<String, List<String>> result = new HashMap<>();
        JSONArray arg = new JSONArray();
        arg.put(DvbsParameterManager.CMD_OPERATOR_M7
                + DvbsParameterManager.CMD_ACTION_GET_REGION);
        JSONObject resultObj = mDataPresenter.getParameterManager().dvbsScanControl(arg);
        try {
            JSONArray data = (JSONArray) resultObj.get("data");
            if (data.length() == 0) {
                return result;
            }
            for (int i = 0; i < data.length(); i++) {
                String regionName = (String) (((JSONObject) (data.get(i))).get("RegionName"));
                JSONArray list = (JSONArray) (((JSONObject) (data.get(i))).get("RegionSrvList"));
                List<String> regionSrvList = new ArrayList<>();
                if (list.length() > 0) {
                    for (int j = 0; j < list.length(); j++) {
                        regionSrvList.add(list.getString(j));
                    }
                }
                result.put(regionName, regionSrvList);
            }
        } catch (JSONException e) {
            Log.e(TAG, "getRegionList failed:" + e.getMessage());
        }
        return result;
    }
}

