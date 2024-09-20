package com.droidlogic.dtvkit.inputsource.searchguide;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.media.tv.TvInputInfo;
import android.content.Context;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.text.TextUtils;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ContentResolver;
import android.util.TypedValue;
import android.widget.TextView;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONObject;

import com.droidlogic.app.DataProviderManager;
import com.droidlogic.dtvkit.inputsource.DataManager;
import com.droidlogic.dtvkit.inputsource.PvrStatusConfirmManager;
import com.droidlogic.dtvkit.inputsource.R;
import com.droidlogic.settings.ConstantManager;
import org.droidlogic.dtvkit.DtvkitGlueClient;
import com.droidlogic.fragment.ParameterManager;
import com.droidlogic.fragment.PasswordCheckUtil;
import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils;

public class DtvkitDvbScanSelect extends Activity {
    private static final String TAG = "DtvkitDvbScanSelect";

    private static final int[] RES = {R.id.select_dvbc, R.id.select_dvbt, R.id.select_dvbs};

    public static final int REQUEST_CODE_START_DVBC_ACTIVITY = 1;
    public static final int REQUEST_CODE_START_DVBT_ACTIVITY = 2;
    public static final int REQUEST_CODE_START_DVBS_ACTIVITY = 3;
    public static final int REQUEST_CODE_START_SETTINGS_ACTIVITY = 4;
    public static final int REQUEST_CODE_START_ISDBT_ACTIVITY = 5;

    public static final int SEARCH_TYPE_MANUAL = 0;
    public static final int SEARCH_TYPE_AUTO = 1;
    public static final int SEARCH_TYPE_DVBC = 0;
    public static final int SEARCH_TYPE_DVBT = 1;
    public static final int SEARCH_TYPE_DVBS = 2;
    public static final int SEARCH_TYPE_ISDBT = 3;
    public static final String SEARCH_TYPE_MANUAL_AUTO = "search_manual_auto";
    public static final String SEARCH_TYPE_DVBS_DVBT_DVBC = "search_dvbs_dvbt_dvbc";
    public static final String SEARCH_FOUND_SERVICE_NUMBER = "service_number";
    public static final String SEARCH_FOUND_SERVICE_LIST = "service_list";
    public static final String SEARCH_FOUND_SERVICE_INFO = "service_info";
    public static final String SEARCH_FOUND_FIRST_SERVICE = "first_service";
    public static final String SEARCH_FOUND_LCN_STATE = "lcn_state";

    private DataManager mDataManager;
    private Intent mIntent = null;
    private ParameterManager mParameterManager;
    private Context mContext;
    private int currentIndex = 0;
    private int currentDvbSource = 0;
    private boolean autoChannelScan = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_search_activity);
        mContext = this;
        mIntent = getIntent();
        mDataManager = new DataManager(this);
        mParameterManager = new ParameterManager(this, DtvkitGlueClient.getInstance());
        initLayout();
    }


    @Override
    protected void onResume() {
        super.onResume();
        checkPassWordInfo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult requestCode:" + requestCode + ", resultCode:" + resultCode);
        switch (requestCode) {
            case REQUEST_CODE_START_DVBC_ACTIVITY:
            case REQUEST_CODE_START_DVBT_ACTIVITY:
            case REQUEST_CODE_START_DVBS_ACTIVITY:
            case REQUEST_CODE_START_ISDBT_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK, data);
                    finish();
                } else {
                    setResult(RESULT_CANCELED);
                    if (autoChannelScan) {
                        finish();
                    }
                }
                autoChannelScan = false;
                break;
            case REQUEST_CODE_START_SETTINGS_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
                break;
            default:
                // do nothing
                Log.d(TAG, "onActivityResult other request");
        }
    }

    private void checkPassWordInfo() {
        String pinCode = mParameterManager.getStringParameters(ParameterManager.SECURITY_PASSWORD);
        String countryCode = mParameterManager.getCurrentCountryIso3Name();
        if ("fra".equals(countryCode)) {
            if (TextUtils.isEmpty(pinCode) || "0000".equals(pinCode)) {
                PasswordCheckUtil passwordDialog = new PasswordCheckUtil(pinCode);
                passwordDialog.setCurrent_mode(PasswordCheckUtil.PIN_DIALOG_TYPE_ENTER_NEW1_PIN);
                passwordDialog.showPasswordDialog(this, new PasswordCheckUtil.PasswordCallback() {
                    @Override
                    public void passwordRight(String password) {
                        Log.d(TAG, "password is right");
                        mParameterManager.saveStringParameters(mParameterManager.SECURITY_PASSWORD, password);
                        getContentResolver().notifyChange(
                            Uri.parse(DataProviderManager.CONTENT_URI + DataProviderManager.TABLE_STRING_NAME),
                            null, ContentResolver.NOTIFY_SYNC_TO_NETWORK);
                    }
                    @Override
                    public void onKeyBack() {
                        Log.d(TAG, "onKeyBack");
                        String newPinCode = mParameterManager.getStringParameters(mParameterManager.SECURITY_PASSWORD);
                        if (TextUtils.isEmpty(pinCode) || "0000".equals(pinCode)) {
                            //finish current activity when password hasn't been set right
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

    private void initLayout() {
        currentDvbSource = getCurrentDvbSource();
        final Intent intent = new Intent();
        final String inputId = mIntent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        final String pvrStatus = mIntent.getStringExtra(ConstantManager.KEY_LIVETV_PVR_STATUS);
        String searchType = mIntent.getStringExtra("search_type");
        if (inputId != null) {
            intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId);
        }
        if (pvrStatus != null) {
            intent.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, pvrStatus);
        }
        //init pvr set flag when inited
        String firstPvrFlag = PvrStatusConfirmManager.read(this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
        if (!PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(firstPvrFlag)) {
            PvrStatusConfirmManager.store(this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST);
        }

        Spinner spinnerDvbSource = (Spinner)findViewById(R.id.spinner_dvb_system);
        List<String> systems = new ArrayList<>();
        systems.add(getResources().getString(R.string.strDvbc));
        systems.add(getResources().getString(R.string.strDvbt));
        systems.add(getResources().getString(R.string.strDvbs));
        if (DtvkitGlueClient.getInstance().isdbtSupport()) {
            systems.add(getResources().getString(R.string.strIsdbt));
        }
        ArrayAdapter<String> dvbSystemAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, systems);
        dvbSystemAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spinnerDvbSource.setAdapter(dvbSystemAdapter);
        spinnerDvbSource.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                choiceSource(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        Button scan = (Button)findViewById(R.id.button_scan_menu);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDvbSource == ParameterManager.SIGNAL_QPSK && mParameterManager.checkIsGermanyCountry()) {
                    JSONArray operTypeList = mParameterManager.getOperatorsTypeList(ParameterManager.SIGNAL_QPSK);
                    showOperatorTypeConfirmDialog(ParameterManager.SIGNAL_QPSK, mContext, operTypeList);
                }
                String className = null;
                int requestCode = 0;
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
                        break;
                    case ParameterManager.SIGNAL_ISDBT:
                        className = DataManager.KEY_ACTIVITY_ISDBT;
                        requestCode = REQUEST_CODE_START_ISDBT_ACTIVITY;
                        break;
                }
                if (className == null) {
                    Log.e(TAG, "Error with dvb source(" + currentDvbSource + "), correct it to dvbt");
                    currentDvbSource = ParameterManager.SIGNAL_COFDM;
                    setCurrentDvbSource(currentDvbSource);
                    className = DataManager.KEY_ACTIVITY_DVBT;
                    requestCode = REQUEST_CODE_START_DVBT_ACTIVITY;
                }
                String pvrFlag = PvrStatusConfirmManager.read(DtvkitDvbScanSelect.this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
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
                startActivityForResult(intent, requestCode);
            }
        });
        Button settings = (Button)findViewById(R.id.select_setting);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pvrFlag = PvrStatusConfirmManager.read(DtvkitDvbScanSelect.this, PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG);
                if (pvrStatus != null && PvrStatusConfirmManager.KEY_PVR_CLEAR_FLAG_FIRST.equals(pvrFlag)) {
                    intent.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, pvrStatus);
                } else {
                    intent.putExtra(ConstantManager.KEY_LIVETV_PVR_STATUS, "");
                }
                intent.setClassName(DataManager.KEY_PACKAGE_NAME, DataManager.KEY_ACTIVITY_SETTINGS);
                startActivityForResult(intent, REQUEST_CODE_START_SETTINGS_ACTIVITY);
                mDataManager.saveIntParameters(DataManager.KEY_SELECT_SEARCH_ACTIVITY, DataManager.SELECT_SETTINGS);
                Log.d(TAG, "start to set related language");
            }
        });

        Log.i("searchType", "" + searchType);
        if (searchType != null) {
            switch (searchType) {
                case "cable":
                    searchType = getString(R.string.strDvbc);
                    break;
                case "terrestrial":
                    searchType = getString(R.string.strDvbt);
                    break;
                case "satellite":
                    searchType = getString(R.string.strDvbs);
                    break;
                case "isdb":
                    searchType = getString(R.string.strIsdbt);
                    break;
            }
            for (int position = 0; position < systems.size(); position++) {
                if (TextUtils.equals(systems.get(position), searchType)) {
                    choiceSource(position);
                    scan.performClick();
                    autoChannelScan = true;
                    break;
                }
            }
        }
        spinnerDvbSource.setSelection(dvbSourceToSpinnerPos(currentDvbSource));
    }

    private void choiceSource(int position) {
        int source = 0;
        switch (position) {
            case 0:
                source = ParameterManager.SIGNAL_QAM;
                break;
            case 1:
                source = ParameterManager.SIGNAL_COFDM;
                break;
            case 2:
                source = ParameterManager.SIGNAL_QPSK;
                break;
            case 3:
                source = ParameterManager.SIGNAL_ISDBT;
                break;
        }
        if (currentDvbSource != source) {
            currentDvbSource = source;
            setCurrentDvbSource(source);
        }
    }

    private void showOperatorTypeConfirmDialog(final int tunerType, final Context context, final JSONArray operatorsTypeArray) {
        Log.d(TAG, "showOperatorTypeConfirmDialog");
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog alert = builder.create();
        final View dialogView = View.inflate(context, R.layout.operator_type_setting, null);
        final TextView title = (TextView) dialogView.findViewById(R.id.dialog_title);
        final ListView listView = (ListView) dialogView.findViewById(R.id.lv_dialog);
        final List<HashMap<String, Object>> dataList = new ArrayList<HashMap<String, Object>>();
        if (operatorsTypeArray != null && operatorsTypeArray.length() > 0) {
            JSONObject operTypeObj = null;
            String name = null;
            for (int i = 0; i < operatorsTypeArray.length(); i++) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                try {
                    operTypeObj = operatorsTypeArray.getJSONObject(i);
                    name = operTypeObj.getString("operators_name");
                    map.put("name", name);
                    dataList.add(map);
                } catch (Exception e) {
                    Log.d(TAG, "get operator type data Exception = " + e.getMessage());
                }
            }
        } else {
            Log.d(TAG, "showOperatorTypeConfirmDialog no operatorsTypeArray");
            return;
        }

        SimpleAdapter adapter = new SimpleAdapter(context, dataList,
                R.layout.operator_type_list,
                new String[] {"name"},
                new int[] {R.id.name});

        listView.setAdapter(adapter);
        listView.setSelection(currentIndex);
        title.setText(R.string.operator_type_setting);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int position, long id) {
                Log.d(TAG, "showOperatorTypeConfirmDialog onItemClick position = " + position);
                int index = mParameterManager.getOperatorTypeIndex(tunerType);
                if (index == position) {
                    Log.d(TAG, "showOperatorTypeConfirmDialog same position = " + position);
                    alert.dismiss();
                    return;
                }
                Log.d(TAG, "showOperatorTypeConfirmDialog onItemClick position = " + position);
                mParameterManager.setOperatorType(tunerType, position);
                currentIndex = position;
                alert.dismiss();
            }
        });
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(TAG, "showOperatorTypeConfirmDialog onDismiss");
            }
        });

        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        alert.setView(dialogView);
        alert.show();
        WindowManager.LayoutParams params = alert.getWindow().getAttributes();
        params.width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500, context.getResources().getDisplayMetrics());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        alert.getWindow().setAttributes(params);

    }

    private int dvbSourceToSpinnerPos(int source) {
        int position = 0;
        switch (source) {
            case ParameterManager.SIGNAL_COFDM:
                position = 1;
                break;
            case ParameterManager.SIGNAL_QAM:
                position = 0;
                break;
            case ParameterManager.SIGNAL_QPSK:
                position = 2;
                break;
            case ParameterManager.SIGNAL_ISDBT:
                position = 3;
                break;
            case ParameterManager.SIGNAL_ANALOG:
                //not supported now, will correct to dvbt
                position = 1;
                break;
        }
        return position;
    }

    private void setCurrentDvbSource(int source) {
        JSONArray array = new JSONArray();
        try {
            array.put(source);
            DtvkitGlueClient.getInstance().request("Dvb.SetDvbSource", array);
            mParameterManager.saveStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM,
                    TvContractUtils.dvbSourceToDbString(source));
            EpgSyncJobService.setChannelTypeFilter(TvContractUtils.dvbSourceToChannelTypeString(source));
        } catch (Exception e) {
        }
    }

    private int getCurrentDvbSource() {
        int source = ParameterManager.SIGNAL_COFDM;
        try {
            JSONObject sourceReq = DtvkitGlueClient.getInstance().request("Dvb.GetDvbSource", new JSONArray());
            if (sourceReq != null) {
                source = sourceReq.optInt("data");
            }
            String sourceParameter = mParameterManager.getStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM);
            String sourceStr = TvContractUtils.dvbSourceToDbString(source);
            if (!Objects.equals(sourceParameter, sourceStr)) {
                mParameterManager.saveStringParameters(ParameterManager.TV_KEY_DTVKIT_SYSTEM, sourceStr);
            }
        } catch (Exception e) {
        }
        return source;
    }
}
