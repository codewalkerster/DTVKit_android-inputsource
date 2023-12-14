package com.droidlogic.dtvkit.inputsource.searchguide;

import android.content.Context;
import android.support.annotation.MainThread;
import android.util.Log;

import com.droidlogic.dtvkit.inputsource.R;
import com.droidlogic.fragment.ParameterManager;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils;
import com.droidlogic.dtvkit.inputsource.searchguide.OnMessageHandler;
import com.droidlogic.fragment.dialog.DialogManager;
import com.droidlogic.fragment.DvbsParameterManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class DataPresenter {
    private final String TAG = DataPresenter.class.getSimpleName();

    public static final String FRAGMENT_REGION = "Country";
    public static final String FRAGMENT_SOURCE_SELECTOR = "Select Source";
    public static final String FRAGMENT_SPEC = "Operator";
    public static final String FRAGMENT_AUTO_DISEQC = "Auto DiSEqC";
    public static final String FRAGMENT_MANUAL_DISEQC = "Manual DiSEqC";
    public static final String FRAGMENT_OPERATOR_LIST = "Operator List";
    public static final String FRAGMENT_OPERATOR_SUB_LIST = "Operator Sub List";
    public static final String FRAGMENT_SEARCH_UI = "Search UI";
    public static final String FRAGMENT_REGIONAL_CHANNELS = "Regional Channels";

    public static final String FRAGMENT_KTGS = "TKGS";
    public static final String FRAGMENT_KTGS_LOCATOR_LIST = "TKGS Locator List";
    public static final String FRAGMENT_KTGS_HIDDEN_LOCATORS = "TKGS Hidden Locators";

    public static final String FRAGMENT_SERVICE_LIST = "Service List";

    private final ParameterManager mParameterManager;
    private final DialogManager mDialogManager;
    private final HashMap<String, OnMessageHandler> mHandlers = new HashMap<>();
    private final Context mContext;
    private static int mOperateType = DvbsParameterManager.OPERATOR_DEFAULT;

    private final DtvkitGlueClient.SignalHandler mSignalHandler = (signal, data) -> {
        for (OnMessageHandler handler : mHandlers.values()) {
            handler.handleMessage(signal, data);
        }
    };

    public DataPresenter(Context context) {
        mContext = context;
        mParameterManager = new ParameterManager(context, DtvkitGlueClient.getInstance());
        mDialogManager = new DialogManager(context, mParameterManager);
    }

    public ParameterManager getParameterManager() {
        return mParameterManager;
    }

    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    @MainThread
    public void addHandlers(String id, OnMessageHandler handler) {
        mHandlers.put(id, handler);
    }

    @MainThread
    public void removeHandlers(String id) {
        mHandlers.remove(id);
    }

    public static int getOperateType() {
        return mOperateType;
    }

    public static void setOperateType(int operateType) {
        mOperateType = operateType;
    }

    public static void setOperateType(String operateType) {
        if (operateType.contains("Sky")) {
            mOperateType = DvbsParameterManager.OPERATOR_SKY_D;
        } else if (operateType.contains("HD+")) {
            mOperateType = DvbsParameterManager.OPERATOR_ASTRA_HD_PLUS;
        } else if (operateType.contains("M7")) {
            mOperateType = DvbsParameterManager.OPERATOR_M7;
        } else if (operateType.contains("Airtel")) {
            mOperateType = DvbsParameterManager.OPERATOR_AIRTEL;
        } else if (operateType.contains("TKGS")) {
            mOperateType = DvbsParameterManager.OPERATOR_TKGS;
        } else if (operateType.contains("SunDirect")) {
            mOperateType = DvbsParameterManager.OPERATOR_SUNDIRECT;
        } else if (operateType.contains("Canal+")) {
            mOperateType = DvbsParameterManager.OPERATOR_CANALPLUS;
        } else if (operateType.contains("Tivusat")) {
            mOperateType = DvbsParameterManager.OPERATOR_TIVUSAT;
        } else {
            mOperateType = DvbsParameterManager.OPERATOR_DEFAULT;
        }
    }

    void startMonitor() {
        DtvkitGlueClient.getInstance().registerSignalHandler(mSignalHandler);
    }

    void stopMonitor() {
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mSignalHandler);
    }

    private List<String> getCountryList() {
        return mParameterManager.getCountryDisplayList();
    }

    private int getCurrentCountryIndex() {
        return mParameterManager.getCurrentCountryIndex();
    }

    private List<String> getOperatorsTypeList(int type) {
        final List<String> dataList = new ArrayList<>();
        JSONArray operatorsTypeArray = mParameterManager.getOperatorsTypeList(type);
        if (operatorsTypeArray != null && operatorsTypeArray.length() > 0) {
            JSONObject operaTypeObj = null;
            String name = null;
            for (int i = 0; i < operatorsTypeArray.length(); i++) {
                try {
                    operaTypeObj = operatorsTypeArray.getJSONObject(i);
                    name = operaTypeObj.getString("operators_name");
                    dataList.add(name);
                } catch (Exception e) {
                    Log.d(TAG, "get operator type data Exception = " + e.getMessage());
                }
            }
        } else {
            Log.d(TAG, "showOperatorTypeConfirmDialog no operatorsTypeArray");
        }
        return dataList;
    }

    public List<String> getTKGSLocatorListName() {
        List<String> dataList = new ArrayList<>();
        JSONArray jsonArray = mParameterManager.getTKGSVisibleLocatorsList();
        if (jsonArray != null && jsonArray.length() > 0) {
            JSONObject locatorObj = null;
            String locatorName = null;
            for (int i = 0 ;i < jsonArray.length(); i++) {
                try {
                    locatorObj = jsonArray.getJSONObject(i);
                    locatorName = locatorObj.getString("freq") + (locatorObj.getInt("polar") == 0 ? "H" : "V") + locatorObj.getString("symbol_rate") + "  " + locatorObj.getString("tkgspid");
                    dataList.add(locatorName);
                } catch (Exception e) {
                    Log.d(TAG, "get locator data Exception = " + e.getMessage());
                }

            }
        }
        return dataList;
    }

    public List<String> getTKGSHiddenLocatorsInfo() {
        List<String> dataList = new ArrayList<>();
        JSONArray jsonArray = mParameterManager.getTKGSHiddenLocation();
        if (jsonArray != null && jsonArray.length() > 0) {
            JSONObject locatorObj = null;
            String locatorName = null;
            for (int i = 0 ;i < jsonArray.length(); i++) {
                try {
                    locatorObj = jsonArray.getJSONObject(i);
                    locatorName = locatorObj.getString("freq") + (locatorObj.getInt("polar") == 0 ? "H" : "V") + locatorObj.getString("symbol_rate") + "  " + locatorObj.getString("tkgspid");
                    dataList.add(locatorName);
                } catch (Exception e) {
                    Log.d(TAG, "get locator data Exception = " + e.getMessage());
                }

            }
        }
        return dataList;
    }

    public int getDataForFragment(String idx, List<String> dataList) {
        int pos = -1;
        switch (idx) {
            case FRAGMENT_REGION:
                pos = getCurrentCountryIndex();
                dataList.addAll(getCountryList());
                break;
            case FRAGMENT_SOURCE_SELECTOR:
                String type = TvContractUtils.dvbSourceToChannelTypeString(mParameterManager.getCurrentDvbSource());
                dataList.add(mContext.getString(R.string.strDvbt));
                dataList.add(mContext.getString(R.string.strDvbc));
                dataList.add(mContext.getString(R.string.strDvbs));
                if (DtvkitGlueClient.getInstance().isdbtSupport()) {
                    dataList.add(mContext.getString(R.string.strIsdbt));
                }
                pos = 0;
                for (int i = 0; i < dataList.size(); i++) {
                    String s1 = dataList.get(i).replace("-", "_");
                    Log.d(TAG, " s1: " + s1 + ", type: " + type);
                    if (type.contains(s1)) {
                        pos = i;
                        break;
                    }
                }
                break;
            case FRAGMENT_SPEC:
                pos = 0;
                dataList.addAll(getOperatorsTypeList(ParameterManager.SIGNAL_QPSK));
                break;
            case FRAGMENT_KTGS_LOCATOR_LIST:
                pos = 0;
                dataList.addAll(getTKGSLocatorListName());
                break;
            case FRAGMENT_KTGS_HIDDEN_LOCATORS:
                pos = 0;
                dataList.addAll(getTKGSHiddenLocatorsInfo());
                break;
            default:
                break;
        }
        if (dataList != null) {
            Log.d(TAG, "getDataForFragment pos=" + pos + ", size=" + dataList.size());
        }
        return pos;
    }
}
