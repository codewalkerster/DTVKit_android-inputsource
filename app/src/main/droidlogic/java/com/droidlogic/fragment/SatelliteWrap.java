package com.droidlogic.fragment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.droidlogic.fragment.ItemAdapter.ItemDetail;
import com.droidlogic.fragment.dialog.CustomDialog;
import org.droidlogic.dtvkit.DtvkitGlueClient;

public class SatelliteWrap {

    private static final String TAG = "SatelliteWrap";
    private Context mContext;
    private DtvkitGlueClient mDtvkitGlueClient;

    public SatelliteWrap(Context context, DtvkitGlueClient glueClient) {
        mContext = context;
        mDtvkitGlueClient = glueClient;
    }

    public List<Satellite> getSatelliteList(int operator) {
        List<Satellite> satelliteList =  new ArrayList<Satellite>();
        try {
            JSONArray array = new JSONArray();
            array.put(operator);
            array.put(new JSONArray());
            JSONObject jsonObject = mDtvkitGlueClient.request("Dvbs.getSatellites", array);
            if (jsonObject != null) {
                JSONArray sate_array = (JSONArray)(jsonObject.get("data"));
                for (int i = 0; i < sate_array.length(); i ++) {
                    JSONObject sate = (JSONObject)(sate_array.get(i));
                    Satellite satellite = new Satellite();
                    satellite.parseFromJson(sate);
                    satelliteList.add(satellite);
                }
            }
        } catch (Exception e) {
        }
        return satelliteList;
    }

    public Satellite getSatelliteByName(String name) {
        Satellite satellite = new Satellite();
        try {
            JSONArray array = new JSONArray();
            array.put(name);
            JSONObject obj = mDtvkitGlueClient.request("Dvbs.getSatelliteInfoByName", array);
            if (obj != null) {
                JSONObject sate = (JSONObject)(obj.get("data"));
                satellite.parseFromJson(sate);
            }
        } catch (Exception e) {
        }
        return satellite;
    }

    public List<Transponder> getTransponderList(String sateName) {
        List<Transponder> tps = new ArrayList<Transponder>();
        try {
            JSONArray array = new JSONArray();
            array.put(sateName);
            JSONObject obj = mDtvkitGlueClient.request("Dvbs.getTransponders", array);
            if (obj != null) {
                JSONArray tps_array = (JSONArray)(obj.get("data"));
                if (tps_array != null) {
                    for (int i = 0; i < tps_array.length(); i ++) {
                        JSONObject jtp = (JSONObject)(tps_array.get(i));
                        Transponder tp = new Transponder();
                        tp.parseFromJson(jtp);
                        tps.add(tp);
                    }
                }
            }
        } catch (Exception e) {
        }
        return tps;
    }

    public Transponder getTransponderByName(String sateName, String tpName) {
        Transponder tp = new Transponder();
        try {
            JSONArray array = new JSONArray();
            array.put(sateName);
            array.put(tpName);
            JSONObject obj = mDtvkitGlueClient.request("Dvbs.getTransponderbyName", array);
            if (obj != null) {
                JSONObject jtp = (JSONObject)(obj.get("data"));
                if (jtp != null) {
                    tp.parseFromJson(jtp);
                }
            }
        } catch (Exception e) {
        }
        return tp;
    }

    public void editDishPos(String sateName, int dishPos) {
        Satellite sate = getSatelliteByName(sateName);
        try {
            JSONArray array = new JSONArray();
            array.put(sate.getName());
            array.put(sate.getName());
            array.put(sate.getDirection());
            array.put(sate.getLongitude());
            array.put(dishPos);
            array.put("" + sate.getLinkedLnb());
            mDtvkitGlueClient.request("Dvbs.editSatellite", array);
        } catch (Exception e) {
        }
    }

    public void addSatellite(String name, boolean isEast, float position) {
        try {
            JSONArray array = new JSONArray();
            array.put(name);
            array.put(isEast);
            array.put(position);
            array.put(65535);
            array.put("0");
            mDtvkitGlueClient.request("Dvbs.addSatellite", array);
        } catch (Exception e) {
        }
    }

    public void editSatellite(String old_name_edit, String new_name_edit, boolean isEast_edit, float position_edit) {
        Satellite sate = getSatelliteByName(old_name_edit);
        try {
            JSONArray array = new JSONArray();
            array.put(old_name_edit);
            array.put(new_name_edit);
            array.put(isEast_edit);
            array.put(position_edit);
            array.put(sate.dishPos);
            array.put("" + sate.linkedLnb);
            mDtvkitGlueClient.request("Dvbs.editSatellite", array);
        } catch (Exception e) {
        }
    }

    public void addTransponder(String sateName, int freq,
                                String polarity, int symbole, boolean isDvbs2,
                                String modulation, String fec, String inversion) {
        try {
            JSONArray array = new JSONArray();
            array.put(sateName);
            array.put(freq);
            array.put(polarity);
            array.put(symbole);
            array.put(isDvbs2 ? "DVBS2" : "DVBS");
            array.put(TextUtils.isEmpty(modulation) ? "auto" : modulation.toLowerCase());
            array.put(TextUtils.isEmpty(fec) ? "auto" : fec.toLowerCase());
            array.put(TextUtils.isEmpty(inversion) ? "auto" : inversion.toLowerCase());
            mDtvkitGlueClient.request("Dvbs.addTransponder", array);
        } catch (Exception e) {
        }
    }

    public void editTransponder(String sateName, String oldTpName, int freq,
                                String polarity, int symbole, boolean isDvbs2,
                                String modulation, String fec, String inversion) {
        try {
            JSONArray array = new JSONArray();
            array.put(sateName);
            array.put(oldTpName);
            array.put(freq);
            array.put(polarity);
            array.put(symbole);
            array.put(isDvbs2 ? "DVBS2" : "DVBS");
            array.put(TextUtils.isEmpty(modulation) ? "auto" : modulation.toLowerCase());
            array.put(TextUtils.isEmpty(fec) ? "auto" : fec.toLowerCase());
            array.put(TextUtils.isEmpty(inversion) ? "auto" : inversion.toLowerCase());
            mDtvkitGlueClient.request("Dvbs.editTransponder", array);
        } catch (Exception e) {
        }
    }

    public void removeSatellite(String sateName) {
        try {
            JSONArray array = new JSONArray();
            array.put(sateName);
            mDtvkitGlueClient.request("Dvbs.deleteSatellite", array);
        } catch (Exception e) {
        }
    }

    public void removeTransponder(String sateName, String tpName) {
        Transponder tp = getTransponderByName(sateName, tpName);
        try {
            JSONArray array = new JSONArray();
            array.put(sateName);
            array.put(tp.getFreq());
            array.put(tp.getPolarity());
            array.put(tp.getSymbol());
            mDtvkitGlueClient.request("Dvbs.deleteTransponder", array);
        } catch (Exception e) {
        }
    }

    public void selectTransponder(String sateName, String tpName, boolean selected) {
        try {
            JSONArray array = new JSONArray();
            array.put(sateName);
            array.put(tpName);
            array.put(selected);
            mDtvkitGlueClient.request("Dvbs.selectTransponder", array);
        } catch (Exception e) {
        }
    }

    public boolean autoDiSEqcRecognize() {
        boolean ret = false;
        try {
            JSONArray array = new JSONArray();
            mDtvkitGlueClient.request("Dvbs.autoDiseqcRecognize", array);
            ret = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public boolean autoDiseqcConfirm(boolean startScan) {
        boolean ret = false;
        try {
            JSONArray array = new JSONArray();
            array.put(startScan);
            mDtvkitGlueClient.request("Dvbs.autoDiseqcConfirm", array);
            ret = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public boolean autoDiseqcStop() {
        boolean ret = false;
        try {
            JSONArray array = new JSONArray();
            mDtvkitGlueClient.request("dvbs.autoDiseqcStop", array);
            ret = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public class Transponder {
        private int frequency = 0;
        private String polarity = "H";
        private int symbolRate = 0;
        private String system = "DVBS";
        private String modulation = "auto";
        private String innerFec = "auto";
        private String inversion = "auto";
        private boolean isSelected = false;

        Transponder() {
        }

        public void parseFromJson(JSONObject json) {
            try {
                if (json != null) {
                    frequency = (int)(json.get("frequency"));
                    polarity = (String)(json.get("polarity"));
                    symbolRate = (int)(json.get("symbol_rate"));
                    system = (String)(json.get("system"));
                    modulation = ((String)(json.get("modulation"))).toLowerCase();
                    innerFec = ((String)(json.get("inner_fec"))).toLowerCase();
                    inversion = ((String)(json.get("inversion"))).toLowerCase();
                    isSelected = (boolean)(json.get("select"));
                }
            } catch (Exception e) {
            }
        }

        public JSONArray toJsonArray() {
            JSONArray array = new JSONArray();
            try {
                array.put(frequency);
                array.put(polarity);
                array.put(symbolRate);
                array.put(system);
                array.put(modulation);
                array.put(innerFec);
                array.put(inversion);
            } catch (Exception e) {
            }
            return array;
        }

        public boolean isSelected() {
            return isSelected;
        }

        public String getDisplayName() {
            return ("" + frequency) +  polarity  + ("" + symbolRate);
        }

        public String getSystem() {
            return system;
        }

        public int getFreq() {
            return frequency;
        }

        public String getPolarity() {
            return polarity;
        }

        public int getSymbol() {
            return symbolRate;
        }

        public String getFecMode() {
            return innerFec;
        }

        public String getModulation() {
            return modulation;
        }
    }

    public class Satellite {
        private String name = "";
        private boolean isEast = false;
        private int longitude = 0;
        private int linkedLnb = 1;
        private int dishPos = 0;

        Satellite() {
        }

        public void parseFromJson(JSONObject json) {
            try {
                if (json != null) {
                    name = (String)(json.get("name"));
                    isEast = (boolean)(json.get("east"));
                    longitude = (int)(json.get("long_pos"));
                    dishPos = (int)(json.get("dish_pos"));
                    linkedLnb = (int)(json.get("bound_lnb"));
                }
            } catch (Exception e) {
                Log.d(TAG, "Satellite parseFromJson failed.");
            }
        }

        public String getName() {
            return this.name;
        }

        public boolean getDirection() {
            return isEast;
        }

        public int getLongitude() {
            return longitude;
        }

        public int getLinkedLnb() {
            return linkedLnb;
        }

        public int getDishPos() {
            return dishPos;
        }

        public boolean isBoundedLnb(String lnbKey) {
            try {
                int lnb_key = Integer.parseInt(lnbKey);
                if (linkedLnb == lnb_key)
                    return true;
            } catch (Exception e) {
            }
            return false;
        }

        public void boundLnb(String key, boolean link) {
            try {
                int lnb_key = Integer.parseInt(key);
                if (linkedLnb == lnb_key) {
                    if (!link) linkedLnb = 0;
                } else {
                    if (link)
                        linkedLnb = lnb_key;
                }
                editSatellite(this.name, this.isEast, this.longitude, this.linkedLnb);
            } catch (Exception e) {
            }
        }

        public void editSatellite(String name, boolean isEast, int position, int lnb) {
            try {
                JSONArray array = new JSONArray();
                array.put(this.name);
                array.put(name);
                array.put(isEast);
                array.put(position);
                array.put(dishPos);
                array.put("" + lnb);
                mDtvkitGlueClient.request("Dvbs.editSatellite", array);
            } catch (Exception e) {
            }
        }
    }
}
