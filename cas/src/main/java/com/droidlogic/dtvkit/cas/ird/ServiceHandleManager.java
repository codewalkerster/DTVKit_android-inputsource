package com.droidlogic.dtvkit.cas.ird;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class ServiceHandleManager {
    private ServiceHandle mEmmServiceHandle = null;
    private ServiceHandle mEcmServiceHandle = null;

    private static class MonitorStatus {
        public int handle;
        public int caSystemId;
        public String info;
    }
    private static class ServiceHandle {
        public int handle;
        public ArrayList<MonitorStatus> monitors = new ArrayList<>();
    }

    public void parseFromServiceStatus(@NonNull JSONArray status) {
        if (status.length() > 0) {
            try {
                for (int i = 0; i < status.length(); i++) {
                    JSONObject service = (JSONObject) status.get(i);
                    String serviceType = service.optString("Type");
                    JSONArray streams = service.optJSONArray("StreamStatus");
                    if (streams == null || streams.length() == 0)
                        continue;
                    if ("EMM".equalsIgnoreCase(serviceType)) {
                        if (mEmmServiceHandle == null)
                            mEmmServiceHandle = new ServiceHandle();
                        mEmmServiceHandle.handle = service.optInt("ServiceHandle", 0);
                    } else if ("ECM".equalsIgnoreCase(serviceType)) {
                        if (mEcmServiceHandle == null)
                            mEcmServiceHandle = new ServiceHandle();
                        mEcmServiceHandle.handle = service.optInt("ServiceHandle", 0);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public void parseMonitoringStatus(@NonNull String type, @NonNull JSONObject status) {
        ServiceHandle h = null;
        if ("emm".equalsIgnoreCase(type))
            h = mEmmServiceHandle;
        else if ("ecm".equalsIgnoreCase(type))
            h = mEcmServiceHandle;
        if (h == null)
            return;
        int caSystemId = status.optInt("ca_system_id", 0);
        int handle = status.optInt("service_handle", 0);
        if (handle != h.handle)
            return;
        boolean exist = false;
        for (MonitorStatus m : h.monitors) {
            if (m.caSystemId == caSystemId && m.handle == handle) {
                exist = true;
                m.info = status.optString("info");
                break;
            }
        }
        if (!exist) {
            MonitorStatus m = new MonitorStatus();
            m.caSystemId = caSystemId;
            m.info = status.optString("info");
            m.handle = handle;
            h.monitors.add(m);
        }
    }

    public String createMonitoringProvider(@NonNull String type) {
        String ret = null;
        ServiceHandle h;
        if ("emm".equalsIgnoreCase(type))
            h = mEmmServiceHandle;
        else if ("ecm".equalsIgnoreCase(type))
            h = mEcmServiceHandle;
        else
            return null;
        if (h != null && h.monitors != null
                && h.monitors.size() > 0) {
            try {
                JSONArray a = new JSONArray();
                for (MonitorStatus m : h.monitors) {
                    JSONObject s = new JSONObject();
                    s.put("ServiceHandle", m.handle);
                    s.put("info", m.info);
                    s.put("ca_system_id", m.caSystemId);
                    a.put(s);
                }
                if (a.length() > 0)
                    ret = a.toString();
            } catch (Exception ignored) {}
        }
        return ret;
    }

    public int getEmmHandle() {
        return mEmmServiceHandle != null ? mEmmServiceHandle.handle : -1;
    }

    public int getEcmHandle() {
        return mEcmServiceHandle != null ? mEcmServiceHandle.handle : -1;
    }
}

