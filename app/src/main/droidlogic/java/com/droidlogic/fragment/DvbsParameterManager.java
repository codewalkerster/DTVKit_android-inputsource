package com.droidlogic.fragment;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.droidlogic.fragment.ItemAdapter.ItemDetail;
import com.droidlogic.fragment.dialog.CustomDialog;
import org.droidlogic.dtvkit.DtvkitGlueClient;

public class DvbsParameterManager {

    private static final String TAG = "DvbsParameterManager";
    private Context mContext;
    private DtvkitGlueClient mDtvkitGlueClient;
    private String mCurrentListDirection;
    private String mCurrentListType = ParameterManager.ITEM_LNB;
    private String mCurrentSatellite = "";//not null
    private String mCurrentLnbId = "";//not null
    private String mFocusedLnbId = "";//not null
    private String mCurrentTp = "";//not null
    private static DvbsParameterManager mInstance = null;
    private SatelliteWrap mSateWrap = null;
    private LnbWrap mLnbWrap = null;
    private int mDishStatus = 0;
    private int mDishDirection = 0;
    private int mDishStep = 1;
    private int mDishPos = 0;
    private int mDishLocationPos = 0;
    private List<Integer> mUbFreqs = new ArrayList<>();

    private final String[] lnbKeyList = {"test_satellite",
            "test_transponder", "lnb_type", "unicable_version", "high_lnb_voltage", "high_tone_22k",
            "tone_burst", "diseqc1.0", "diseqc2.0", "diseqc2_abilities", "motor"};

    private int mCurrentOperator = OPERATOR_DEFAULT;
    // sync with DTVKit definition
    public static final int OPERATOR_ASTRA_HD_PLUS = 0;
    public static final int OPERATOR_SKY_D = 1;
    public static final int OPERATOR_M7 = 2;
    public static final int OPERATOR_AIRTEL = 0x400;
    public static final int OPERATOR_SUNDIRECT = 0x401;
    public static final int OPERATOR_TKGS = 0x0301;
    public static final int OPERATOR_CANALPLUS = 0x0500;
    public static final int OPERATOR_TIVUSAT = 0x0600;
    public static final int OPERATOR_FRANSAT = 0x0700;
    public static final int OPERATOR_DEFAULT = 0xFFFF;

    public static final int OPERATOR_AIRTEL_BOUQUETID = 0x6070;
    public static final int OPERATOR_CANALPLUS_BOUQUETID = 0xC023;
    public static final int OPERATOR_FRANSAT_BOUQUETID = 0x71;

    public static final int CMD_OPERATOR_M7 = 0x01 << 16;
    public static final int CMD_OPERATOR_ASTRA = 0x02 << 16;
    public static final int CMD_OPERATOR_TKGS = 0x04 << 16;

    public static final int CMD_ACTION_DISEQC_CONFIRM = 0x01;
    public static final int CMD_ACTION_GET_OPERATOR = 0x02;
    public static final int CMD_ACTION_GET_SUB_OPERATOR = 0x03;
    public static final int CMD_ACTION_SET_OPERATOR = 0x04;
    public static final int CMD_ACTION_SET_SUB_OPERATOR = 0x5;
    public static final int CMD_ACTION_GET_REGION = 0x06;
    public static final int CMD_ACTION_SET_REGION = 0x07;
    public static final int CMD_ACTION_GET_SERVICE_LIST = 0x08;
    public static final int CMD_ACTION_SET_SERVICE_LIST = 0x09;

    public static final int CMD_ACTION_GET_TKGS_OPERATION_MODE = 0x00040001;
    public static final int CMD_ACTION_SET_TKGS_OPERATION_MODE = 0x00040002;
    public static final int CMD_ACTION_GET_TKGS_LOCATION = 0x00040003;
    public static final int CMD_ACTION_SET_TKGS_LOCATION = 0x00040004;
    public static final int CMD_ACTION_GET_TKGS_CATEGORY = 0x00040005;
    public static final int CMD_ACTION_SET_TKGS_VERSION_CHECK_REPLY = 0x00040006;
    public static final int CMD_ACTION_GET_TKGS_USER_MESSAGE = 0x00040007;
    public static final int CMD_ACTION_GET_TKGS_SERVICE_LIST = 0x00040008;
    public static final int CMD_ACTION_SET_TKGS_SERVICE_LIST = 0x00040009;
    public static final int CMD_ACTION_GET_TKGS_VERSION = 0x0004000A;
    public static final int CMD_ACTION_GET_TKGS_HIDDEN_LOCATION = 0x0004000B;
    public static final int CMD_ACTION_SET_TKGS_HIDDEN_TP_LOCATION = 0x0004000C;
    public static final int CMD_ACTION_RESET_TKGS = 0x0004000D;

    private DvbsParameterManager(Context context) {
        this.mContext = context.getApplicationContext();
        this.mDtvkitGlueClient = DtvkitGlueClient.getInstance();
        this.mSateWrap = new SatelliteWrap(mContext, mDtvkitGlueClient);
        this.mLnbWrap = new LnbWrap(mContext, mDtvkitGlueClient);
        for (int i: CustomDialog.DIALOG_SET_EDIT_SWITCH_ITEM_UNICABLE_USER_BAND_FREQUENCY_LIST) {
            mUbFreqs.add(i);
        }
    }

    public static DvbsParameterManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DvbsParameterManager(context);
        }
        return mInstance;
    }

    public void setCurrentOperator(int operator) {
        mCurrentOperator = operator;
        Log.i(TAG, "set operator :" + mCurrentOperator);
    }

    public SatelliteWrap getSatelliteWrap() {
        return this.mSateWrap;
    }

    public LnbWrap getLnbWrap() {
        return this.mLnbWrap;
    }

    public LinkedList<ItemDetail> getCurrentItemList() {
        if (ParameterManager.ITEM_SATELLITE.equals(mCurrentListType)) {
            return getSatelliteNameList();
        } else if (ParameterManager.ITEM_TRANSPONDER.equals(mCurrentListType)) {
            return getTransponderList();
        } else {
            return getLnbNameList();
        }
    }

    public LinkedList<ItemDetail> getSatelliteNameList() {
        return getSatelliteNameList(mCurrentLnbId, mCurrentOperator);
    }

    public List<String> getSatelliteNameListSelected() {
        return getSatelliteNameListSelected(mCurrentLnbId);
    }
    public List<String> getSatelliteNameListSelected(String lnbId) {
        List<String> list = new ArrayList<String>();
        if (TextUtils.isEmpty(lnbId)) {
            return list;
        }
        List<SatelliteWrap.Satellite> sateList = mSateWrap.getSatelliteList(mCurrentOperator);
        for (SatelliteWrap.Satellite sate: sateList) {
            if (sate.isBoundedLnb(lnbId)) {
                list.add(sate.getName());
            }
        }
        return list;
    }

    private LinkedList<ItemDetail> getSatelliteNameList(String lnbKey, int operator_code) {
        LinkedList<ItemDetail> list = new LinkedList<ItemDetail>();
        List<SatelliteWrap.Satellite> sateList = mSateWrap.getSatelliteList(operator_code);
        for (SatelliteWrap.Satellite sate: sateList) {
            int type = ItemDetail.NOT_SELECT_EDIT;
            if (sate.isBoundedLnb(lnbKey))
                type = ItemDetail.SELECT_EDIT;
            list.add(new ItemDetail(type, sate.getName(), null, true));
        }
        return list;
    }

    public LinkedList<ItemDetail> getTransponderList() {
        return getTransponderList(mCurrentSatellite);
    }

    public LinkedList<ItemDetail> getTransponderList(String sateName) {
        LinkedList<ItemDetail> list = new LinkedList<ItemDetail>();
        if (TextUtils.isEmpty(sateName)) {
            return list;
        }
        List<SatelliteWrap.Transponder> tps = mSateWrap.getTransponderList(sateName);

        for (SatelliteWrap.Transponder tp: tps) {
            int type = ItemDetail.NOT_SELECT_EDIT;
            if (tp.isSelected()) {
                type = ItemDetail.SELECT_EDIT;
            }
            list.add(new ItemDetail(type, tp.getDisplayName(), null, false));
        }
        return list;
    }

    public void selectSingleTransponder(String tpName, boolean selected) {
        if (TextUtils.isEmpty(tpName)) {
            return;
        }
        if (tpName.equals(mCurrentTp)) {
            mSateWrap.selectTransponder(mCurrentSatellite, tpName, selected);
            if (!selected) {
                setCurrentTransponder("");
            }
        } else {
            mSateWrap.selectTransponder(mCurrentSatellite, mCurrentTp, false);
            mSateWrap.selectTransponder(mCurrentSatellite, tpName, selected);
            setCurrentTransponder(tpName);
        }
    }

    public LinkedList<ItemDetail> getLnbNameList() {
        LinkedList<ItemDetail> list = getLnbIdList();
        for (ItemDetail item: list) {
            item.setFirstText("LNB" + item.getFirstText());
        }
        return list;
    }

    public LinkedList<ItemDetail> getLnbIdList() {
        LinkedList<ItemDetail> list = new LinkedList<ItemDetail>();
        String currentLnb = getCurrentLnbId();
        List<String> lnbIdList = mLnbWrap.getLnbIdList();
        for (String id: lnbIdList) {
            int type = ItemDetail.NOT_SELECT_EDIT;
            if (currentLnb.equals(id))
                type = ItemDetail.SELECT_EDIT;
            list.add(new ItemDetail(type, id, null, false));
        }
        if (list.size() > 0) {
            if (mCurrentLnbId.isEmpty()) {
                mCurrentLnbId = list.get(0).getFirstText();
                list.get(0).setEditStatus(ItemDetail.SELECT_EDIT);
                setInitialCurrentSateTp(mCurrentLnbId);
            }
            if (mFocusedLnbId.isEmpty()) {
                mFocusedLnbId = list.get(0).getFirstText();
            }
        }
        return list;
    }

    public LinkedList<ItemDetail> getLnbParamsWithId() {
        return getLnbParamsWithId(mCurrentLnbId);
    }

    public LinkedList<ItemDetail> getLnbParamsWithId(String id) {
        LinkedList<ItemDetail> list = new LinkedList<ItemDetail>();
        List<String> typeList = getParameterListType();
        LnbWrap.Lnb lnb = mLnbWrap.getLnbById(id);
        for (int i = 0; i < lnbKeyList.length; i ++) {
            ItemDetail item = parseLnbParaValue(lnbKeyList[i], getParameterTitle(i), lnb);
            list.add(item);
        }
        return list;
    }

    public List<Integer> getLnbParamsIntValue() {
        List<Integer> list = new ArrayList<Integer>();
        List<String> typeList = getParameterListType();
        LnbWrap.Lnb lnb = mLnbWrap.getLnbById(mCurrentLnbId);
        for (int i = 0; i < lnbKeyList.length; i ++) {
            int value = parseLnbParaIntValue(lnbKeyList[i], lnb);
            list.add(value);
        }
        return list;
    }

    private List<String> getParameterListType() {
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < CustomDialog.ID_DIALOG_TITLE_COLLECTOR.length; i++) {
            list.add(mContext.getString(CustomDialog.ID_DIALOG_TITLE_COLLECTOR[i]));
        }
        return list;
    }

    private String getParameterTitle(int index) {
        List<String> paraList = getParameterListType();
        return paraList.get(index);
    }

    private ItemDetail parseLnbParaValue(String paraKey, String keyTitle, LnbWrap.Lnb lnb) {
        String valueStr = "none";
        boolean enable_switch = true;
        int itemType = ItemDetail.SWITCH_EDIT;

        if (lnbKeyList[0].equals(paraKey)) {
            String currentSate = getCurrentSatellite();
            if (!TextUtils.isEmpty(currentSate)) {
                valueStr = currentSate;
            }
        } else if (lnbKeyList[1].equals(paraKey)) {
            String currentTp = getCurrentTransponder();
            if (!TextUtils.isEmpty(currentTp)) {
                valueStr = currentTp;
            }
        } else if (lnbKeyList[2].equals(paraKey)) {
            int lnbType = lnb.getLnbInfo().getType();
            int lnb_low_freq = lnb.getLnbInfo().lowLocalFreq();
            int lnb_high_freq = lnb.getLnbInfo().highLocalFreq();
            if (lnbType == 0 || lnbType == 3) {
                valueStr = mContext.getString(CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_LNB_TYPE_LIST[lnbType]);
            } else if (lnbType == 1) {
                valueStr = mContext.getString(CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_LNB_TYPE_LIST[2]);
            } else if (lnbType == 4) {
                valueStr = mContext.getString(CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_LNB_TYPE_LIST[1]);
            } else {
                valueStr = mContext.getString(CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_LNB_TYPE_LIST[0]);
            }
        } else if (lnbKeyList[3].equals(paraKey)) {
            int  version =  lnb.getUnicableVersion();
            valueStr = CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_UNICABLE_LIST[version];
        } else if (lnbKeyList[4].equals(paraKey)) {
            String lp = lnb.getLnbInfo().getLnbPower();
            int lnbType = lnb.getLnbInfo().getType();
            if (lp.isEmpty()) {
                valueStr = CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_UNICABLE_LIST[1];//on
            } else {
                valueStr = lp;
            }
        } else if (lnbKeyList[5].equals(paraKey)) {
            int lnbType = lnb.getLnbInfo().getType();
            String tone22K = lnb.getLnbInfo().get22Khz();
            boolean isSingle = lnb.getLnbInfo().isSingle();
            if (!isSingle && (lnbType != 4)) {
                enable_switch = false;
            }
            valueStr = tone22K;
        } else if (lnbKeyList[6].equals(paraKey)) {
            String tb = lnb.getToneBurst();
            if (!tb.isEmpty()) {
                valueStr = tb;
            }
        } else if (lnbKeyList[7].equals(paraKey)) {
            int c_switch = lnb.getCSwitch();
            if (c_switch > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_0_LIST.length -1)) {
                c_switch = 0;
            }
            valueStr = CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_0_LIST[c_switch];
        } else if (lnbKeyList[8].equals(paraKey)) {
            int u_switch = lnb.getUSwitch();
            if (u_switch > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_1_LIST.length -1)) {
                u_switch = 0;
            }
            valueStr = CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_1_LIST[u_switch];
        } else if (lnbKeyList[9].equals(paraKey)) {
            int diseqc2_abilities = lnb.getDiseqc2Abilities();
            if (diseqc2_abilities == 1 || diseqc2_abilities == 3) {
                diseqc2_abilities = 1;
            } else {
                diseqc2_abilities = 0;
            }
            valueStr = CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC2_X_LIST[diseqc2_abilities];
        } else if (lnbKeyList[10].equals(paraKey)) {
            int motor = lnb.getMotor();
            if (motor > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_MOTOR_LIST.length -1)) {
                motor = 0;
            }
            valueStr = CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_MOTOR_LIST[motor];
        }
        ItemDetail item = new ItemDetail(itemType, keyTitle, valueStr, false);
        item.setEnable(enable_switch);
        return item;
    }

    public int getCurrentLnbParaIntValue(String paraKey) {
        LnbWrap.Lnb lnb = mLnbWrap.getLnbById(mCurrentLnbId);
        return parseLnbParaIntValue(paraKey, lnb);
    }

    private int parseLnbParaIntValue(String paraKey, LnbWrap.Lnb lnb) {
        int ret = 0;
        if (lnbKeyList[2].equals(paraKey)) {
            ret = lnb.getLnbInfo().getType();
            if (ret == 1) {
                ret = 2;
            } else if (ret == 2) {
                ret = 0;
            } else if (ret == 4) {
                ret = 1;
            } else if (ret > 4) {
                ret = 0;
            }
        } else if (lnbKeyList[3].equals(paraKey)) {
            ret = lnb.getUnicableVersion();
        } else if (lnbKeyList[4].equals(paraKey)) {
            String lp = lnb.getLnbInfo().getLnbPower();
            if (lp.equals("off")) {
                ret = 0;
            } else {
                ret = 1;
            }
        } else if (lnbKeyList[5].equals(paraKey)) {
            String tone22K = lnb.getLnbInfo().get22Khz();
            if (tone22K.equals("off")) {
                ret = 0;
            } else if (tone22K.equals("on")) {
                ret = 1;
            } else {
                ret = 2;
            }
        } else if (lnbKeyList[6].equals(paraKey)) {
            String tb = lnb.getToneBurst();
            if (tb.equals("a")) {
                ret = 1;
            } else if (tb.equals("b")) {
                ret = 2;
            } else {
                ret = 0;
            }
        } else if (lnbKeyList[7].equals(paraKey)) {
            ret = lnb.getCSwitch();
            if (ret > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_0_LIST.length -1)) {
                ret = 0;
            }
        } else if (lnbKeyList[8].equals(paraKey)) {
            ret = lnb.getUSwitch();
            if (ret > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_DISEQC1_1_LIST.length -1)) {
                ret = 0;
            }
        } else if (lnbKeyList[9].equals(paraKey)) {
            ret = lnb.getDiseqc2Abilities();
            if (ret == 1 || ret == 3) {
                ret = 1;
            } else {
                ret = 0;
            }
        } else if (lnbKeyList[10].equals(paraKey)) {
            ret = lnb.getMotor();
            if (ret > (CustomDialog.DIALOG_SET_SELECT_SINGLE_ITEM_MOTOR_LIST.length -1)) {
                ret = 0;
            }
        } else if ("lnb".equals(paraKey)) {
            ret = Integer.parseInt(lnb.getId());
        } else if ("low_min_freq".equals(paraKey)) {
            ret = lnb.getLnbInfo().lowMin();
            if (ret == 65535) ret =0;
        } else if ("low_max_freq".equals(paraKey)) {
            ret = lnb.getLnbInfo().lowMax();
            if (ret == 65535) ret =0;
        } else if ("low_local_oscillator_frequency".equals(paraKey)) {
            ret = lnb.getLnbInfo().lowLocalFreq();
            if (ret == 65535) ret =0;
        } else if ("high_min_freq".equals(paraKey)) {
            ret = lnb.getLnbInfo().highMin();
            if (ret == 65535) ret =0;
        } else if ("high_max_freq".equals(paraKey)) {
            ret = lnb.getLnbInfo().highMax();
            if (ret == 65535) ret =0;
        } else if ("high_local_oscillator_frequency".equals(paraKey)) {
            ret = lnb.getLnbInfo().highLocalFreq();
            if (ret == 65535) ret =0;
        } else if ("unicable_chan".equals(paraKey)) {
            ret = lnb.getUnicable().getChannel();
            if (ret == 65535) ret =0;
        } else if ("unicable_if".equals(paraKey)) {
            ret = lnb.getUnicable().getBandFreq();
            if (ret == 65535) ret =0;
        } else if ("unicable_position_b".equals(paraKey)) {
            boolean lbp = lnb.getUnicable().isPositionB();
            ret = lbp ? 1 : 0;
        }
        return ret;
    }

    public String getCurrentListDirection() {
        return mCurrentListDirection;
    }

    public void setCurrentListDirection(String direction) {
        mCurrentListDirection = direction;
    }

    public String getCurrentListType() {
        return mCurrentListType;
    }

    public void setCurrentListType(String type) {
        mCurrentListType = type;
    }

    public String getCurrentSatellite() {
        return mCurrentSatellite;
    }

    public int getCurrentSatelliteIndex() {
        return getCurrentSatelliteIndex(false);
    }
    public int getCurrentSatelliteIndex(boolean selectedSatellites) {
        int pos = 0;
        boolean found = false;
        if (mCurrentSatellite == null || mCurrentSatellite.isEmpty()) {
            return 0;
        }
        if (selectedSatellites) {
            List<String> sateList = getSatelliteNameListSelected();
            for (String sate : sateList) {
                if (mCurrentSatellite.equals(sate)) {
                    found = true;
                    break;
                }
                pos ++;
            }
        } else {
            List<SatelliteWrap.Satellite> sateList = mSateWrap.getSatelliteList(mCurrentOperator);
            for (SatelliteWrap.Satellite sate: sateList) {
                if (sate.getName().equals(mCurrentSatellite)) {
                    found = true;
                    break;
                }
                pos ++;
            }
        }
        return (found)? pos:0;
    }

    public void setCurrentSatellite(String satellite) {
        if (satellite == null) return;
        if (mCurrentSatellite.equals(satellite)) return;
        mCurrentSatellite = satellite;
        setInitialCurrentTransponder(mCurrentSatellite);
    }

    public String getCurrentLnbId() {
        return mCurrentLnbId;
    }

    public int getCurrentLnbIndex() {
        int pos = 0;
        boolean found = false;
        if (mCurrentLnbId == null || mCurrentLnbId.isEmpty()) {
            return 0;
        }
        List<String> lnbList = mLnbWrap.getLnbIdList();
        for (String lnbId : lnbList) {
            if (lnbId.equals(mCurrentLnbId)) {
                found = true;
                break;
            }
            pos ++;
        }
        return (found)? pos:0;
    }

    public void setCurrentLnbId(String lnb) {
        if (lnb == null) return;
        if (mCurrentLnbId.equals(lnb)) return;
        mCurrentLnbId = lnb;
        setInitialCurrentSateTp(mCurrentLnbId);
    }

    public String getCurrentFocusedLnbId() {
        return mFocusedLnbId;
    }

    public void setCurrentFocusedLnbId(String lnb) {
        if (lnb == null) return;
        if (mFocusedLnbId.equals(lnb)) return;
        mFocusedLnbId = lnb;
    }

    public void setCurrentTransponder(String tp) {
        if (tp == null) return;
        if (mCurrentTp.equals(tp)) return;
        mCurrentTp = tp;
    }

    public String getCurrentTransponder() {
        return mCurrentTp;
    }

    public int getCurrentTransponderIndex() {
        int pos = 0;
        boolean found = false;
        if (TextUtils.isEmpty(mCurrentSatellite) || TextUtils.isEmpty(mCurrentTp)) {
            return 0;
        }
        LinkedList<ItemDetail> tps = getTransponderList();
        for (ItemDetail tp : tps) {
            if (mCurrentTp.equals(tp.getFirstText())) {
                found = true;
                break;
            }
            pos ++;
        }
        return (found)? pos:0;
    }

    public void setInitialCurrentSateTp(String lnbId) {
        List<String> sates = getSatelliteNameListSelected();
        if (sates.size() > 0) {
            setCurrentSatellite(sates.get(0));
            setCurrentTransponder("");
            List<SatelliteWrap.Transponder> tps = mSateWrap.getTransponderList(sates.get(0));
            if (tps.size() > 0) {
                //if support multi-tps
                //setCurrentTransponder(tps.get(0).getFirstText());
                //else support single-tp
                for (SatelliteWrap.Transponder tp : tps) {
                    if (tp.isSelected()) {
                        setCurrentTransponder(tp.getDisplayName());
                        break;
                    }
                }
            }
        } else {
            setCurrentSatellite("");
        }
    }

    public void setInitialCurrentTransponder(String sate) {
        setCurrentTransponder("");
        List<SatelliteWrap.Transponder> tps = mSateWrap.getTransponderList(sate);
        if (tps.size() > 0) {
            //if support multi-tps
            //setCurrentTransponder(tps.get(0).getFirstText());
            //else support single-tp
            for (SatelliteWrap.Transponder tp : tps) {
                if (tp.isSelected()) {
                    setCurrentTransponder(tp.getDisplayName());
                    break;
                }
            }
        }
    }

    public int getDisEqcLocationIndex(String location) {
        int index = 1;//default 1= "off"
        if (location == null || location.isEmpty())
            return index;
        List<String> locationNameList = getLnbWrap().getDiseqcLocationNames();
        for (int i = 0; i < locationNameList.size(); i ++) {
            String name = locationNameList.get(i);
            if (location.equals(name)) {
                index = i;
            }
        }
        return index;
    }

    public int getCurrentDiseqcValue(String key) {
        int value = 0;
        if (key == null)
            return value;
        switch (key) {
            case "dish_limit_state":
                value = mDishStatus;
                break;
            case "dish_dir":
                value = mDishDirection;
                break;
            case "dish_step":
                value = mDishStep;
                break;
            case "dish_pos":
                value = mDishPos;
                break;
            case "diseqc_location":
                value = mDishLocationPos;
                break;
        }
        return value;
    }

    public void setCurrentDiseqcValue(String key, int value) {
        if (key == null || value < 0)
            return;
        if (value < 0) {
            value = 0;
        }
        switch (key) {
            case "dish_limit_state": {
                if (value > 1) value = 0;
                mDishStatus = value;
                break;
            }
            case "dish_dir": {
                if (value > 2) value = 0;
                mDishDirection = value;
                break;
            }
            case "dish_step":
                if (value > 127) value = 0;
                mDishStep = value;
                break;
            case "dish_pos":
                if (value > 255) value = 0;
                mDishPos = value;
                break;
            case "diseqc_location":
                if (value > 1023) value = 0;
                mDishLocationPos = value;
                break;
        }
    }

    public int getUbFrequency(int brand) {
        int ret = 0;
        try {
            ret = mUbFreqs.get(brand);
        } catch (Exception e) {
        }
        return ret;
    }

    public void setUbFrequency(int brand, int value) {
        if (brand >= mUbFreqs.size()) {
            return;
        }
        mUbFreqs.set(brand, value);
    }

    public void resetSelections() {
        mCurrentListType = ParameterManager.ITEM_LNB;
        mCurrentLnbId = "";
        mFocusedLnbId = "";
        mCurrentSatellite = "";
        mCurrentTp = "";
    }
}
