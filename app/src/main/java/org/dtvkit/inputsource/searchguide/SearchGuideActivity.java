package com.droidlogic.dtvkit.inputsource.searchguide;

import static com.droidlogic.dtvkit.inputsource.searchguide.DtvkitScanSelector.REQUEST_CODE_START_ATSC_ACTIVITY;
import static com.droidlogic.dtvkit.inputsource.searchguide.DtvkitScanSelector.REQUEST_CODE_START_DVBC_ACTIVITY;
import static com.droidlogic.dtvkit.inputsource.searchguide.DtvkitScanSelector.REQUEST_CODE_START_DVBS_ACTIVITY;
import static com.droidlogic.dtvkit.inputsource.searchguide.DtvkitScanSelector.REQUEST_CODE_START_DVBT_ACTIVITY;
import static com.droidlogic.dtvkit.inputsource.searchguide.DtvkitScanSelector.REQUEST_CODE_START_ISDBT_ACTIVITY;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Intent;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.droidlogic.app.DataProviderManager;
import com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils;
import com.droidlogic.dtvkit.inputsource.searchguide.AutoDiseqc;
import com.droidlogic.dtvkit.inputsource.searchguide.DataPresenter;
import com.droidlogic.dtvkit.inputsource.searchguide.DtvkitDvbsSetupFragment;
import com.droidlogic.dtvkit.inputsource.searchguide.OnNextListener;
import com.droidlogic.dtvkit.inputsource.searchguide.SearchStageFragment;
import com.droidlogic.dtvkit.inputsource.searchguide.DtvkitScanSelector;
import com.droidlogic.dtvkit.inputsource.searchguide.SimpleListFragment;
import com.droidlogic.dtvkit.inputsource.searchguide.TKGSMenuSettingFragment;

import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.PvrStatusConfirmManager;
import com.droidlogic.dtvkit.inputsource.R;
import com.droidlogic.fragment.DvbsParameterManager;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.fragment.PasswordCheckUtil;
import com.droidlogic.settings.ConstantManager;
import com.droidlogic.settings.PropSettingManager;
import com.droidlogic.settings.TimezoneSelect;

import org.dtvkit.inputsource.searchguide.TKGSLocatorListFragment;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SearchGuideActivity extends Activity implements OnNextListener {
    private final String TAG = "SearchGuide";
    private DataPresenter mDataPresenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_guide);
        mDataPresenter = new DataPresenter(this);
//        mDataPresenter.startMonitor();
        DataPresenter.setOperateType(DvbsParameterManager.OPERATOR_DEFAULT);
        Intent intent = getIntent();
        String searchType = null;
        if (intent != null) {
            searchType = intent.getStringExtra("search_type");
        }
        Log.d(TAG, "search_type:" + searchType);
        if (searchType == null) {
            showNextFragment(DataPresenter.FRAGMENT_REGION);
        } else {
            int currentDvbSource = ParameterManager.SIGNAL_COFDM;
                switch (searchType) {
                case ("satellite"):
                    currentDvbSource = ParameterManager.SIGNAL_QPSK;
                    List<String> dataList = new ArrayList<>();
                    mDataPresenter.getDataForFragment(DataPresenter.FRAGMENT_SPEC, dataList);
                    if (dataList.size() > 1) {
                        showNextFragment(DataPresenter.FRAGMENT_SPEC);
                        return;
                    }
                    break;
                case ("cable"):
                    currentDvbSource = ParameterManager.SIGNAL_QAM;
                    break;
                case ("isdb"):
                    currentDvbSource = ParameterManager.SIGNAL_ISDBT;
                    break;
                case ("atsc"):
                    String dtvType = mDataPresenter.getParameterManager().getStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM);
                    if (TextUtils.equals("ATSC-C", dtvType)) {
                        currentDvbSource = ParameterManager.SIGNAL_ATSC_C;
                    } else {
                        currentDvbSource = ParameterManager.SIGNAL_ATSC_T;
                    }
                    break;
                case ("terrestrial"):
                default:
                    break;
            }
            //init pvr set flag when inited
            String firstPvrFlag = PvrStatusConfirmManager.read(this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
            if (!PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(firstPvrFlag)) {
                PvrStatusConfirmManager.store(this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST);
            }
            startActivityForSource(currentDvbSource, 0);
        }
    }

    private void showNextFragment(String title) {
        Fragment fragment = showSimpleFragment(title);
        if (null == fragment) {
            fragment = showOtherFragment(title);
        }
        getFragmentManager().beginTransaction()
                .addToBackStack(title)
                .replace(R.id.root, fragment, title)
                .commit();
    }

    private Fragment showSimpleFragment(String title) {
        final SimpleListFragment fragment;
        Log.d(TAG, "showSimpleFragment " + title);
        switch (title) {
            case DataPresenter.FRAGMENT_REGION:
            case DataPresenter.FRAGMENT_SPEC:
                break;
            default:
                return null;
        }
        fragment = SimpleListFragment.newInstance(title);
        fragment.setListener(this);
        return fragment;
    }

    private Fragment showOtherFragment(String title) {
        SearchStageFragment fragment;
        switch (title) {
            case DataPresenter.FRAGMENT_SOURCE_SELECTOR:
                fragment = DtvkitScanSelector.newInstance(title);
                fragment.setListener(this);
                fragment.setParameterManager(mDataPresenter.getParameterManager());
                fragment.setDialogManager(mDataPresenter.getDialogManager());
                break;
            case DataPresenter.FRAGMENT_AUTO_DISEQC:
                fragment = AutoDiseqc.newInstance(title, this);
                break;
            case DataPresenter.FRAGMENT_KTGS:
                fragment = TKGSMenuSettingFragment.newInstance(title, this);
                fragment.setDialogManager(mDataPresenter.getDialogManager());
                fragment.setParameterManager(mDataPresenter.getParameterManager());
                break;
            case DataPresenter.FRAGMENT_KTGS_LOCATOR_LIST:
            case DataPresenter.FRAGMENT_KTGS_HIDDEN_LOCATORS:
                fragment = TKGSLocatorListFragment.newInstance(title, this);
                fragment.setDialogManager(mDataPresenter.getDialogManager());
                fragment.setParameterManager(mDataPresenter.getParameterManager());
                break;
            default:
                return null;
        }
        Log.d(TAG, "showOtherFragment " + title);
        return fragment;
    }

    @Override
    public void onNext(Fragment fragment, String text) {
        onNext(fragment, text, 0);
    }

    public void onNext(Fragment fragment, String text, @Nullable JSONArray array) {
        Log.w(TAG, "not used");
    }

    @Override
    public void onNext(Fragment fragment, String text, int pos) {
        Log.d(TAG, " *** " + fragment + ", => selected " + text + ", pos=" + pos);
        // show next fragment if needed.
        if (fragment instanceof SimpleListFragment) {
            if (fragment.getTag().equals(DataPresenter.FRAGMENT_REGION)) {
                mDataPresenter.getParameterManager().setCountryCodeByIndex(pos);
                updatingHbbtvCountryId();
                String iso = mDataPresenter.getParameterManager().getCurrentCountryIso3Name();
                TimezoneSelect timezone = new TimezoneSelect(this);
                timezone.selectTimeZone(iso);
                // TODO : may be according to condition to show source selector
                // showNextFragment(DataPresenter.FRAGMENT_SOURCE_SELECTOR);
                showNextFragment(DataPresenter.FRAGMENT_SPEC);
            } else if (fragment.getTag().equals(DataPresenter.FRAGMENT_SPEC)) {
                mDataPresenter.getParameterManager().saveStringParameters(ParameterManager.DVBS_OPERATOR_MODE, text);
                DataPresenter.setOperateType(text);
                JSONArray operatorList = mDataPresenter.getParameterManager()
                            .getOperatorsTypeList(ParameterManager.SIGNAL_QPSK);
                try {
                    JSONObject object = (JSONObject) operatorList.get(pos);
                    if (object != null) {
                        mDataPresenter.getParameterManager().setOperatorType(
                                ParameterManager.SIGNAL_QPSK,
                                object.optInt("operators_value",
                                        DvbsParameterManager.OPERATOR_DEFAULT));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_M7) {
                    showNextFragment(DataPresenter.FRAGMENT_AUTO_DISEQC);
                } else if (DataPresenter.getOperateType() == DvbsParameterManager.OPERATOR_TKGS) {
                    showNextFragment(DataPresenter.FRAGMENT_KTGS);
                } else {
                    startActivityForSource(ParameterManager.SIGNAL_QPSK, 0);
                }
            } else if (fragment.getTag().equals(DataPresenter.FRAGMENT_SOURCE_SELECTOR)) {
                int type = TvContractUtils.dvbSourceToInt(text);
                if (type == ParameterManager.SIGNAL_QPSK && mDataPresenter.getParameterManager().checkIsGermanyCountry()) {
                    showNextFragment(DataPresenter.FRAGMENT_SPEC);
                    return;
                }
                startActivityForSource(type, 0);
            }
        } else if (fragment instanceof AutoDiseqc) {
            int select = 0;
            if (text.equals(getResources().getString(R.string.strSkip))) {
                select = 1;
            }
            startActivityForSource(ParameterManager.SIGNAL_QPSK, select);
        } else if (fragment instanceof TKGSMenuSettingFragment) {
            switch (text) {
                case "TKGS Locator List":
                    showNextFragment(DataPresenter.FRAGMENT_KTGS_LOCATOR_LIST);
                    break;
                case "TKGS Update":
                    startActivityForSource(ParameterManager.SIGNAL_QPSK, 2);
                    break;
                case "Continue":
                    startActivityForSource(ParameterManager.SIGNAL_QPSK, 0);
                    break;
                case "TKGS Hidden Locators":
                    showNextFragment(DataPresenter.FRAGMENT_KTGS_HIDDEN_LOCATORS);
                default:
                    break;
            }
        } else {
            Log.i(TAG, "default onNext behaviour, finish");
            finish();
        }
    }

    private void startActivityForSource(int currentDvbSource, int selector) {
        Intent intent = new Intent(getIntent());
        final String pvrStatus = intent.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
        int newDvbSource = currentDvbSource;
        int mwDtvSource = mDataPresenter.getParameterManager().getCurrentDvbSource();
        if (currentDvbSource == ParameterManager.SIGNAL_ATSC_T &&
                mwDtvSource == ParameterManager.SIGNAL_ATSC_C) {
            newDvbSource = mwDtvSource;
        }
        Log.i(TAG, "setCurrentSource:" + newDvbSource);
        mDataPresenter.getParameterManager().setCurrentDvbSource(newDvbSource);
        int requestCode = 0;
        String className = null;
        switch (currentDvbSource) {
            case ParameterManager.SIGNAL_COFDM:
                className = DataManager.KEY_ACTIVITY_DVBT;
                requestCode = REQUEST_CODE_START_DVBT_ACTIVITY;
                break;
            case ParameterManager.SIGNAL_QAM:
                className = DataManager.KEY_ACTIVITY_DVBT;
                requestCode = REQUEST_CODE_START_DVBC_ACTIVITY;
                break;
            case ParameterManager.SIGNAL_QPSK:
                className = DataManager.KEY_ACTIVITY_DVBS;
                requestCode = REQUEST_CODE_START_DVBS_ACTIVITY;
                intent.putExtra("manual", selector);
                break;
            case ParameterManager.SIGNAL_ISDBT:
                className = DataManager.KEY_ACTIVITY_ISDBT;
                requestCode = REQUEST_CODE_START_ISDBT_ACTIVITY;
                break;
            case ParameterManager.SIGNAL_ATSC_T:
            case ParameterManager.SIGNAL_ATSC_C:
                className = DataManager.KEY_ACTIVITY_ATSC;
                requestCode = REQUEST_CODE_START_ATSC_ACTIVITY;
                break;
            default:
                break;
        }
        if (className == null) {
            Log.e(TAG, "Error with dvb source(" + currentDvbSource + "), correct it to dvbt");
            currentDvbSource = ParameterManager.SIGNAL_COFDM;
            className = DataManager.KEY_ACTIVITY_DVBT;
            requestCode = REQUEST_CODE_START_DVBT_ACTIVITY;
        }
        String pvrFlag = PvrStatusConfirmManager.read(this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
        if (pvrStatus != null && PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(pvrFlag)) {
            intent.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, pvrStatus);
        } else {
            intent.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, "");
        }
        intent.setClassName(DataManager.KEY_PACKAGE_NAME, className);
        if (currentDvbSource == ParameterManager.SIGNAL_QAM) {
            intent.putExtra(DataManager.KEY_IS_DVBT, false);
        } else if (currentDvbSource == ParameterManager.SIGNAL_COFDM
                || currentDvbSource == ParameterManager.SIGNAL_ISDBT) {
            intent.putExtra(DataManager.KEY_IS_DVBT, true);
        }
        //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //finish();
        startActivityForResult(intent, requestCode);
    }

    private void updatingHbbtvCountryId() {
        boolean hbbTvFeatherStatus = PropSettingManager.getBoolean("vendor.tv.dtv.hbbtv.enable", false);
        Log.d(TAG, "getFeatureSupportHbbTV: " + hbbTvFeatherStatus);
        if (hbbTvFeatherStatus) {
            Intent intent = new Intent();
            intent.setAction("com.vewd.core.service.COUNTRY_ID_CHANGED");
            intent.putExtra("CountryId",mDataPresenter.getParameterManager().getCurrentCountryIso3Name());
            sendBroadcast(intent);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult requestCode:" + requestCode + ", resultCode:" + resultCode);
        switch (requestCode) {
            case REQUEST_CODE_START_DVBC_ACTIVITY:
            case REQUEST_CODE_START_DVBT_ACTIVITY:
            case REQUEST_CODE_START_DVBS_ACTIVITY:
            case REQUEST_CODE_START_ISDBT_ACTIVITY:
            case REQUEST_CODE_START_ATSC_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK, data);
                } else {
                    setResult(RESULT_CANCELED, data);
                }
                break;
            default:
                Log.d(TAG, "onActivityResult other request");
                break;
        }
        finish();
        syncDatabase(resultCode);
    }

    /* for ac off/on test */
    private void syncDatabase(int resultCode) {
        if (resultCode == RESULT_OK) {
            new Thread(() -> {
                try {
                    Runtime.getRuntime().exec("sync");
                } catch (IOException e) {
                    Log.e(TAG, "error: " + e.getMessage());
                }
            }).start();
        }
    }

    @Override
    public void onFragmentReady(Fragment fragment) {
        List<String> dataList = new ArrayList<>();
        if (fragment instanceof SimpleListFragment) {
            int pos = mDataPresenter.getDataForFragment(fragment.getTag(), dataList);
            if (pos >= 0) {
                ((SimpleListFragment) fragment).updateData(dataList, pos);
            }
        } else if (fragment instanceof TKGSLocatorListFragment) {
            int pos = mDataPresenter.getDataForFragment(fragment.getTag(), dataList);
            if (pos >= 0) {
                ((TKGSLocatorListFragment) fragment).updateData(dataList, pos);
            }
        }
    }

    @Override
    public void onBackPressed() {
        Fragment top = getFragmentManager().findFragmentById(R.id.root);
        Log.i(TAG, "onBackPressed *** " + top);
        Log.i(TAG, "onBackPressed *** " + getFragmentManager().getFragments());
        if (getFragmentManager().getBackStackEntryCount() < 2) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        if (top instanceof SearchStageFragment) {
            if (((SearchStageFragment) top).isCanBackToPrevious()) {
                super.onBackPressed();
            } else {
                setResult(RESULT_CANCELED);
                finish();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
//        mDataPresenter.stopMonitor();
        super.onDestroy();
    }

    private void checkPassWordInfo() {
        String pinCode = mDataPresenter.getParameterManager().getStringParameters(ParameterManager.SECURITY_PASSWORD);
        String countryCode = mDataPresenter.getParameterManager().getCurrentCountryIso3Name();
        if ("fra".equals(countryCode)) {
            if (TextUtils.isEmpty(pinCode) || "0000".equals(pinCode)) {
                PasswordCheckUtil passwordDialog = new PasswordCheckUtil(pinCode);
                passwordDialog.setCurrent_mode(PasswordCheckUtil.PIN_DIALOG_TYPE_ENTER_NEW1_PIN);
                passwordDialog.showPasswordDialog(this, new PasswordCheckUtil.PasswordCallback() {
                    @Override
                    public void passwordRight(String password) {
                        Log.d(TAG, "password is right");
                        mDataPresenter.getParameterManager().saveStringParameters(ParameterManager.SECURITY_PASSWORD, password);
                        getContentResolver().notifyChange(
                                Uri.parse(DataProviderManager.CONTENT_URI + DataProviderManager.TABLE_STRING_NAME),
                                null, ContentResolver.NOTIFY_SYNC_TO_NETWORK);
                    }
                    @Override
                    public void onKeyBack() {
                        Log.d(TAG, "onKeyBack");
                        String newPinCode = mDataPresenter.getParameterManager().getStringParameters(ParameterManager.SECURITY_PASSWORD);
                        if (TextUtils.isEmpty(pinCode) || "0000".equals(pinCode)) {
                            //finish current activity when password hasn't been set right
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    }
                    @Override
                    public void otherKeyHandler(int key_code) {
                    }
                    @Override
                    public boolean checkNewPasswordValid(String value) {
                        Log.d(TAG, "checkNewPasswordValid: " + value);
                        int intValue = 0;
                        try {
                            intValue = Integer.parseInt(value);
                        } catch (Exception e) {
                        }
                        //France cannot use 0000
                        if (intValue > 0 && intValue <= 9999) {
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        for (Fragment fragment : getFragmentManager().getFragments()) {
            if (fragment instanceof SearchStageFragment) {
                ((SearchStageFragment) fragment).onLifecycleChanged(SearchStageFragment.ACTIVITY_LIFECYCLE_ON_START);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (Fragment fragment : getFragmentManager().getFragments()) {
            if (fragment instanceof SearchStageFragment) {
                ((SearchStageFragment) fragment).onLifecycleChanged(SearchStageFragment.ACTIVITY_LIFECYCLE_ON_STOP);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (Fragment fragment : getFragmentManager().getFragments()) {
            if (fragment instanceof SearchStageFragment) {
                ((SearchStageFragment) fragment).onLifecycleChanged(SearchStageFragment.ACTIVITY_LIFECYCLE_ON_PAUSE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPassWordInfo();
        for (Fragment fragment : getFragmentManager().getFragments()) {
            if (fragment instanceof SearchStageFragment) {
                ((SearchStageFragment) fragment).onLifecycleChanged(SearchStageFragment.ACTIVITY_LIFECYCLE_ON_RESUME);
            }
        }
    }

}
