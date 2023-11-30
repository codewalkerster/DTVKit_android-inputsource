package com.droidlogic.dtvkit.cas.ird;

import org.json.JSONArray;
import org.json.JSONObject;

public class PvrProductManager {
    private final static int PVR_PRODUCT_ID = 0xffe0;
    private final static int PVR_PRODUCT_TYPE = 1;
    private final static int TEST_SYSTEM_ID = 1573;
    private boolean hasEntitledPvrProduct = false;
    private boolean hasPvrProduct = false;

    public synchronized void parseProducts(JSONArray array) {
        parseProducts(array, false);
    }

    public synchronized void parseProducts(JSONArray array, boolean enablePvrTest) {
        long nowStartDate = 0L;
        hasPvrProduct = false;
        hasEntitledPvrProduct = false;
        if (enablePvrTest)
            nowStartDate = getNowStartDate();
        try {
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject product = array.getJSONObject(i);
                    int id = product.optInt("product_id");
                    int type = product.optInt("product_type");
                    int entitled = product.optInt("entitled");
                    if (id == PVR_PRODUCT_ID && type == PVR_PRODUCT_TYPE) {
                        if (enablePvrTest) {
                            long start = product.optLong("start_date");
                            long duration = product.optLong("duration");
                            if ((start + duration) < nowStartDate) {
                                product.put("start_date", nowStartDate);
                                product.put("duration", 60);
                            }
                        }
                        hasPvrProduct = true;
                        hasEntitledPvrProduct = (entitled > 0);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public JSONObject createTestPvrProduct() {
        try {
            if (!hasPvrProduct) {
                JSONObject p = new JSONObject();
                p.put("ca_system_id", TEST_SYSTEM_ID);
                p.put("duration", 60);
                p.put("entitled", 1);
                p.put("product_id", PVR_PRODUCT_ID);
                p.put("product_type", PVR_PRODUCT_TYPE);
                p.put("sector_number", 0);
                p.put("source", 0);
                p.put("start_date", getNowStartDate());
                hasPvrProduct = true;
                hasEntitledPvrProduct = true;
                return p;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static long getNowStartDate(){
        //2000/1/1 = 946684800000
        return ((System.currentTimeMillis() - 946684800000L)/86400000);
    }

    public synchronized boolean hasPvrProduct() {
        return hasPvrProduct;
    }

    public synchronized boolean hasEntitledProduct() {
        return hasEntitledPvrProduct;
    }
}

