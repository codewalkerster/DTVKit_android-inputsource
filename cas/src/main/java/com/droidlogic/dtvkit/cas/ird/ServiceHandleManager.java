package com.droidlogic.dtvkit.cas.ird;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ServiceHandleManager {
    private final List<ServiceHandle> mServiceHandles = new ArrayList<>();

    public static final int SERVICE_TYPE_EMM = 0;
    public static final int SERVICE_TYPE_ECM = 1;
    public static final int SERVICE_TYPE_PVR_RECORD = 2; //record is type of ecm
    public static final int SERVICE_TYPE_PVR_PLAYBACK = 3; //record is type of ecm

    private static class MonitorStatus {
        public int handle;
        public int caSystemId;
        public String info;
    }
    private static class ServiceHandle {
        public int type;
        public int handle;
        public ArrayList<MonitorStatus> monitors = new ArrayList<>();
    }

    public void parseFromServiceStatus(@NonNull JSONArray status) {
        mServiceHandles.clear();
        if (status.length() > 0) {
            try {
                for (int i = 0; i < status.length(); i++) {
                    JSONObject service = (JSONObject) status.get(i);
                    String serviceType = service.optString("Type");
                    JSONArray streams = service.optJSONArray("StreamStatus");
                    int handle = service.optInt("ServiceHandle", 0);
                    if (streams == null || streams.length() == 0)
                        continue;
                    ServiceHandle serviceHandle = new ServiceHandle();
                    serviceHandle.handle = handle;
                    if ("EMM".equalsIgnoreCase(serviceType)) {
                        serviceHandle.type = SERVICE_TYPE_EMM;
                    } else if ("ECM".equalsIgnoreCase(serviceType)) {
                        serviceHandle.type = SERVICE_TYPE_ECM;
                    } else if ("RECORD".equalsIgnoreCase(serviceType)) {
                        serviceHandle.type = SERVICE_TYPE_PVR_RECORD;
                    } else if ("PLAYBACK".equalsIgnoreCase(serviceType)) {
                        serviceHandle.type = SERVICE_TYPE_PVR_PLAYBACK;
                    }
                    mServiceHandles.add(serviceHandle);
                }
            } catch (Exception ignored) {}
        }
    }

    private ServiceHandle getServiceHandle(int handle) {
        for (ServiceHandle h : mServiceHandles) {
            if (h.handle == handle)
                return h;
        }
        return null;
    }

    private boolean isMatchType(ServiceHandle service, int type) {
        if (service == null)
            return false;

        if (type == SERVICE_TYPE_ECM) {
            return service.type == SERVICE_TYPE_ECM || service.type == SERVICE_TYPE_PVR_RECORD;
        }
        return service.type == type;
    }

    public void parseMonitoringStatus(@NonNull JSONObject status) {
        int caSystemId = status.optInt("ca_system_id", 0);
        int handle = status.optInt("service_handle", 0);

        ServiceHandle h = getServiceHandle(handle);
        if (h == null)
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
        int sType = -1;
        if ("emm".equalsIgnoreCase(type))
            sType = SERVICE_TYPE_EMM;
        else if ("ecm".equalsIgnoreCase(type))
            sType = SERVICE_TYPE_ECM;
        else
            return "";

        JSONArray a = new JSONArray();
        for (ServiceHandle h : mServiceHandles) {
            if (sType == SERVICE_TYPE_EMM && h.type != sType)
                continue;
            if (!isMatchType(h, sType))
                continue;
            if (h.monitors != null
                    && h.monitors.size() > 0) {
                try {
                    for (MonitorStatus m : h.monitors) {
                        JSONObject s = new JSONObject();
                        s.put("ServiceHandle", m.handle);
                        s.put("info", m.info);
                        s.put("ca_system_id", m.caSystemId);
                        a.put(s);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (a.length() > 0)
            ret = a.toString();

        return ret;
    }

    public int[] getServiceHandles() {
        int i = 0;
        int[] ret = new int[mServiceHandles.size()];

        for (ServiceHandle h : mServiceHandles) {
            ret[i] = h.handle;
            i ++;
        }

        return ret;
    }
}

