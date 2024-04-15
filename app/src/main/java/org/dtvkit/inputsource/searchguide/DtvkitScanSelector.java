package com.droidlogic.dtvkit.inputsource.searchguide;

import android.content.ContentResolver;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.droidlogic.app.DataProviderManager;
import com.droidlogic.dtvkit.inputsource.PvrStatusConfirmManager;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.fragment.PasswordCheckUtil;
import com.droidlogic.settings.ConstantManager;

import com.droidlogic.dtvkit.inputsource.searchguide.DataPresenter;

import java.util.List;

public class DtvkitScanSelector extends com.droidlogic.dtvkit.inputsource.searchguide.SimpleListFragment {
    private static final String TAG = "DtvkitScanSelector";

    public static final int REQUEST_CODE_START_DVBC_ACTIVITY = 1;
    public static final int REQUEST_CODE_START_DVBT_ACTIVITY = 2;
    public static final int REQUEST_CODE_START_DVBS_ACTIVITY = 3;
    public static final int REQUEST_CODE_START_SETTINGS_ACTIVITY = 4;
    public static final int REQUEST_CODE_START_ISDBT_ACTIVITY = 5;
    public static final int REQUEST_CODE_START_ATSC_ACTIVITY = 6;

    public static final String SEARCH_TYPE_MANUAL_AUTO = "search_manual_auto";
    public static final String SEARCH_TYPE_DVBS_DVBT_DVBC = "search_dvbs_dvbt_dvbc";
    public static final String SEARCH_FOUND_SERVICE_NUMBER = "service_number";
    public static final String SEARCH_FOUND_SERVICE_LIST = "service_list";
    public static final String SEARCH_FOUND_SERVICE_INFO = "service_info";
    public static final String SEARCH_FOUND_FIRST_SERVICE = "first_service";

    private int currentIndex = -1;

    public static DtvkitScanSelector newInstance(String title) {
        DtvkitScanSelector fragment = new DtvkitScanSelector();
        fragment.setTitle(title);
        return fragment;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initData();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPassWordInfo();
    }

    private void checkPassWordInfo() {
        String pinCode = getParameterManager().getStringParameters(ParameterManager.SECURITY_PASSWORD);
        String countryCode = getParameterManager().getCurrentCountryIso3Name();
        if ("fra".equals(countryCode)) {
            if (TextUtils.isEmpty(pinCode) || "0000".equals(pinCode)) {
                PasswordCheckUtil passwordDialog = new PasswordCheckUtil(pinCode);
                passwordDialog.setCurrent_mode(PasswordCheckUtil.PIN_DIALOG_TYPE_ENTER_NEW1_PIN);
                passwordDialog.showPasswordDialog(getActivity(), new PasswordCheckUtil.PasswordCallback() {
                    @Override
                    public void passwordRight(String password) {
                        Log.d(TAG, "password is right");
                        getParameterManager().saveStringParameters(ParameterManager.SECURITY_PASSWORD, password);
                        getContext().getContentResolver().notifyChange(
                                Uri.parse(DataProviderManager.CONTENT_URI + DataProviderManager.TABLE_STRING_NAME),
                                null, ContentResolver.NOTIFY_SYNC_TO_NETWORK);
                    }

                    @Override
                    public void onKeyBack() {
                        Log.d(TAG, "onKeyBack");
                        if (getListener() != null) {
                            getListener().onNext(DtvkitScanSelector.this, "back key");
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
    public void updateData(List<String> data, int position) {
        int user_select = currentIndex >= 0 ? currentIndex : position;
        super.updateData(data, user_select);
    }

    private void initData() {
        int currentDvbSource = getParameterManager().getCurrentDvbSource();
        String sourceParameter = getParameterManager().getStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM);
        String sourceStr = com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils.dvbSourceToDbString(currentDvbSource);
        if (!TextUtils.equals(sourceParameter, sourceStr)) {
            getParameterManager().setCurrentDvbSource(currentDvbSource);
        }

        final Intent intent = new Intent();
        final String inputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        final String pvrStatus = getActivity().getIntent().getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
        String searchType = getActivity().getIntent().getStringExtra("search_type");
        if (searchType != null) {
            switch (searchType) {
                case ("terrestrial"):
                    currentIndex = 0;
                    break;
                case ("cable"):
                    currentIndex = 1;
                    break;
                case ("satellite"):
                    currentIndex = 2;
                    break;
                case ("isdb"):
                    currentIndex = 3;
                    break;
            }
        }
        if (inputId != null) {
            intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId);
        }
        if (pvrStatus != null) {
            intent.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, pvrStatus);
        }
        //init pvr set flag when inited
        String firstPvrFlag = PvrStatusConfirmManager.read(getContext(),
                PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
        if (!PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(firstPvrFlag)) {
            PvrStatusConfirmManager.store(getContext(), PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG,
                    PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST);
        }
    }
}
