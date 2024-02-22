package com.droidlogic.settings;

import android.util.Log;
import android.text.TextUtils;
import android.content.Context;
import android.os.storage.StorageManager;
import android.os.Environment;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

import com.droidlogic.app.SystemControlManager;
import com.droidlogic.app.FileListManager;

import com.droidlogic.dtvkit.inputsource.R;

public class SysSettingManager {

    private static final String TAG = "SysSettingManager";
    private static final boolean DEBUG = true;

    public static final String PVR_DEFAULT_PATH = "/data/vendor/dtvkit";
    public static final String PVR_DEFAULT_FOLDER = "PVR_DIR";

    protected SystemControlManager mSystemControlManager;
    private FileListManager mFileListManager;
    private Context mContext;

    public SysSettingManager(Context context) {
        mContext = context;
        mSystemControlManager = SystemControlManager.getInstance();
        mFileListManager = new FileListManager(context);
    }

    public String readSysFs(String sys) {
        String result = mSystemControlManager.readSysFs(sys);
        if (DEBUG) {
            Log.d(TAG, "readSysFs sys = " + sys + ", result = " + result);
        }
        return result;
    }

    public boolean writeSysFs(String sys, String val) {
        return mSystemControlManager.writeSysFs(sys, val);
    }

    public String getVideoFormatFromSys() {
        String result = "";
        String height = readSysFs(ConstantManager.SYS_HEIGHT_PATH);
        String frameFormat = parseFrameFormatStrFromDi0Path();
        if (!TextUtils.isEmpty(height) && !"NA".equals(height)) {
            result = height + frameFormat;
        }
        if (DEBUG) {
            Log.d(TAG, "getVideoFormatFromSys result = " + result);
        }
        return result;
    }

    public String getVideoDecodeInfo() {
        String result = "";
        result = readSysFs(ConstantManager.SYS_VIDEO_DECODE_PATH);
        if (DEBUG) {
            Log.d(TAG, "getVideoDecodeInfo result = " + result);
        }
        return result;
    }

    public int parseWidthFromVdecStatus(String vdecInfo) {
        int result = 0;
        String temp = parseMatchedInfoFromVdecStatus(ConstantManager.SYS_VIDEO_DECODE_VIDEO_WIDTH_PREFIX, ConstantManager.SYS_VIDEO_DECODE_VIDEO_WIDTH_SUFFIX, vdecInfo);
        if (!TextUtils.isEmpty(temp) && TextUtils.isDigitsOnly(temp)) {
            result = Integer.valueOf(temp);
        }
        if (DEBUG) {
            Log.d(TAG, "parseWidthFromVdecStatus result = " + result);
        }
        return result;
    }

    public int parseHeightFromVdecStatus(String vdecInfo) {
        int result = 0;
        String temp = parseMatchedInfoFromVdecStatus(ConstantManager.SYS_VIDEO_DECODE_VIDEO_HEIGHT_PREFIX, ConstantManager.SYS_VIDEO_DECODE_VIDEO_HEIGHT_SUFFIX, vdecInfo);
        if (!TextUtils.isEmpty(temp) && TextUtils.isDigitsOnly(temp)) {
            result = Integer.valueOf(temp);
        }
        if (DEBUG) {
            Log.d(TAG, "parseHeightFromVdecStatus result = " + result);
        }
        return result;
    }

    public float parseFrameRateStrFromVdecStatus(String vdecInfo) {
        float result = 0f;
        String temp = parseMatchedInfoFromVdecStatus(ConstantManager.SYS_VIDEO_DECODE_VIDEO_FRAME_RATE_PREFIX, ConstantManager.SYS_VIDEO_DECODE_VIDEO_FRAME_RATE_SUFFIX, vdecInfo);
        try {
            result = Float.valueOf(temp);
        } catch (Exception e) {
            Log.e(TAG, "parseFrameRateStrFromVdecStatus Exception = " + e.getMessage());
        }
        if (DEBUG) {
            Log.d(TAG, "parseFrameRateStrFromVdecStatus result = " + result);
        }
        return result;
    }

    private String parseMatchedInfoFromVdecStatus(String startStr, String endStr, String value) {
        /*vdec_status example:
            vdec channel 0 statistics:
              device name : ammvdec_mpeg12
              frame width : 720
             frame height : 576
               frame rate : 25 fps
                 bit rate : 0 kbps
                   status : 6
                frame dur : 3840
               frame data : 12 KB
              frame count : 426441
               drop count : 0
            fra err count : 62
             hw err count : 0
               total data : 2742089 KB
            ratio_control : 9000
        */
        String result = "";
        if (!TextUtils.isEmpty(startStr) && !TextUtils.isEmpty(endStr) && !TextUtils.isEmpty(value)) {
            int start = value.indexOf(startStr);//example:"frame rate : "
            int end = value.indexOf(endStr, start);//example:" fps"
            //deal different next line symbol
            if (start != -1 && end != -1) {
                String sub = value.substring(start, end);
                if (sub != null) {
                    byte[] byteValue = sub.getBytes();
                    if (byteValue != null && byteValue.length >= 2) {
                        byte temp1 = byteValue[byteValue.length - 1];
                        byte temp2 = byteValue[byteValue.length - 2];
                        if ('\r' == temp2 && '\n' == temp1) {
                            end = end -2;
                        } else if ('\r' != temp2 && '\n' == temp1) {
                            end = end -1;
                        }
                    }
                }
            }
            int preLength = startStr.length();//example:"frame rate : "
            if (start != -1 && end != -1 && (start + preLength) < end) {
                String sub = value.substring(start + preLength, end);
                if (sub != null && sub.length() > 0) {
                    sub = sub.trim();
                }
                //Log.d(TAG, "parseMatchedInfo = " + sub);
                result = sub;
            }
        }
        return result;
    }

    public String parseFrameFormatStrFromDi0Path() {
        String result = "";
        String frameFormat = readSysFs(ConstantManager.SYS_PI_PATH);
        if (!TextUtils.isEmpty(frameFormat) && !"null".equals(frameFormat) && !"NA".equals(frameFormat)) {
            if (frameFormat.startsWith(ConstantManager.CONSTANT_FORMAT_INTERLACE)) {
                result = ConstantManager.PI_TO_VIDEO_FORMAT_MAP.get(ConstantManager.CONSTANT_FORMAT_INTERLACE);
            } else if (frameFormat.startsWith(ConstantManager.CONSTANT_FORMAT_PROGRESSIVE)) {
                result = ConstantManager.PI_TO_VIDEO_FORMAT_MAP.get(ConstantManager.CONSTANT_FORMAT_PROGRESSIVE);
            } else if (frameFormat.startsWith(ConstantManager.CONSTANT_FORMAT_COMPRESSED)) {//Compressed may exist with progressive or interlace
                result = ConstantManager.PI_TO_VIDEO_FORMAT_MAP.get(ConstantManager.CONSTANT_FORMAT_PROGRESSIVE);
            } else {
                result = ConstantManager.PI_TO_VIDEO_FORMAT_MAP.get(ConstantManager.CONSTANT_FORMAT_PROGRESSIVE);
            }
        } else {
            result = ConstantManager.PI_TO_VIDEO_FORMAT_MAP.get(ConstantManager.CONSTANT_FORMAT_PROGRESSIVE);
        }
        if (DEBUG) {
            Log.d(TAG, "parseFrameFormatStrFromDi0Path result = " + result);
        }
        return result;
    }

    public List<String> getStorageDeviceNameList() {
        List<String> result = new ArrayList<String>();
        List<Map<String, Object>> mapList = getStorageDevices();
        String name = "";
        if (mapList != null && mapList.size() > 0) {
            for (Map<String, Object> map : mapList) {
                name = getStorageName(map);
                result.add(name);
            }
        }
        return result;
    }

    public List<String> getStorageDevicePathList() {
        List<String> result = new ArrayList<String>();
        List<Map<String, Object>> mapList = getStorageDevices();
        String name = "";
        if (mapList != null && mapList.size() > 0) {
            for (Map<String, Object> map : mapList) {
                name = getStoragePath(map);
                result.add(name);
            }
        }
        return result;
    }

    public String getStorageFsType(String rawPath) {
        String result = null;
        List<Map<String, Object>> mapList = getStorageDevices();
        String path = "";
        if (mapList != null && mapList.size() > 0) {
            for (Map<String, Object> map : mapList) {
                path = getStoragePath(map);
                if (TextUtils.equals(rawPath, path) && isMediaPath(rawPath)) {
                    result = getStorageFsType(map);
                    Log.d(TAG, "getStorageFsType rawPath = " + rawPath + ", fsType = " + result);
                    break;
                }
            }
        }
        return result;
    }

    public String getStorageVolumeId(String rawPath) {
        String result = null;
        List<Map<String, Object>> mapList = getStorageDevices();
        String path = "";
        if (mapList != null && mapList.size() > 0) {
            for (Map<String, Object> map : mapList) {
                path = getStoragePath(map);
                if (TextUtils.equals(rawPath, path) && isMediaPath(rawPath)) {
                    result = getStorageVolumeId(map);
                    Log.d(TAG, "getStorageVolumeId rawPath = " + rawPath + ", volumeId = " + result);
                    break;
                }
            }
        }
        return result;
    }

    public String getStorageDeviceId(String rawPath) {
        String result = null;
        List<Map<String, Object>> mapList = getStorageDevices();
        String path = "";
        if (mapList != null && mapList.size() > 0) {
            for (Map<String, Object> map : mapList) {
                path = getStoragePath(map);
                if (TextUtils.equals(rawPath, path) && isMediaPath(rawPath)) {
                    result = getStorageDeviceId(map);
                    Log.d(TAG, "getStorageDeviceId rawPath = " + rawPath + ", deviceId = " + result);
                    break;
                }
            }
        }
        return result;
    }

    public String getStorageRawPathByVolumeId(String volumeId) {
        String result = null;
        List<Map<String, Object>> mapList = getStorageDevices();
        String volumeIdStr = "";
        if (mapList != null && mapList.size() > 0) {
            for (Map<String, Object> map : mapList) {
                volumeIdStr = getStorageVolumeId(map);
                if (TextUtils.equals(volumeId, volumeIdStr)) {
                    result = getStoragePath(map);
                    Log.d(TAG, "getStorageRawPathByVolumeId volumeId = " + volumeId + ", rawPath = " + result);
                    break;
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> getStorageDevices() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(FileListManager.KEY_NAME, mContext.getString(R.string.strSettingsPvrDefault));
        map.put(FileListManager.KEY_PATH, PVR_DEFAULT_PATH);
        result.add(map);
        result.addAll(getWriteableDevices());
        return result;
    }

    private List<Map<String, Object>> getWriteableDevices() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> readList = mFileListManager.getDevices();
        if (readList != null && readList.size() > 0) {
            for (Map<String, Object> map : readList) {
                String storagePath = (String) map.get(FileListManager.KEY_PATH);
                if (storagePath != null && storagePath.startsWith("/storage/emulated")) {
                    Log.d(TAG, "getWriteableDevices add inner sdcard " + storagePath);
                    result.add(map);
                } else if (storagePath != null && storagePath.startsWith("/storage")) {
                    String uuid = null;
                    int idx = storagePath.lastIndexOf("/");
                    if (idx >= 0) {
                        uuid = storagePath.substring(idx + 1);
                    }
                    if (uuid != null) {
                        String fsType = (String) map.get(FileListManager.KEY_FS_TYPE);
                        String volumeId = (String) map.get(FileListManager.KEY_VOLUME_ID);
                        String deviceId = (String) map.get(FileListManager.KEY_DISK_ID);
                        Log.d(TAG, "getWriteableDevices add storage /mnt/media_rw/" + uuid + ", fsType = " + fsType + ", volumeId = " + volumeId + ", deviceId = " + deviceId);
                        map.put(FileListManager.KEY_PATH, "/mnt/media_rw/" + uuid);
                        result.add(map);
                    } else {
                        Log.d(TAG, "getWriteableDevices empty uuid");
                    }
                } else {
                    Log.d(TAG, "getWriteableDevices unknown device " + storagePath);
                }
            }
        } else {
            Log.d(TAG, "getWriteableDevices device not found");
        }
        return result;
    }

    public boolean isDeviceExist(String devicePath) {
        boolean result = false;
        if (devicePath != null) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                List<Map<String, Object>> deviceList = getWriteableDevices();
                if (deviceList != null && deviceList.size() > 0) {
                    for (Map<String, Object> map : deviceList) {
                        String path = (String) map.get(FileListManager.KEY_PATH);
                        if (path.equals(devicePath)) {
                            result = true;
                            break;
                        }
                    }
                }
            } else {
                try {
                    File file = new File(devicePath);
                    if (file != null && file.exists() && file.isDirectory()) {
                        result = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "isDeviceExist Exception = " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static boolean isMovableDevice(String devicePath) {
        boolean result = false;
        if (devicePath != null && !devicePath.startsWith("/storage/emulated") && devicePath.startsWith("/storage")) {
            Log.d(TAG, "isMovableDevice " + devicePath);
            result = true;
        }
        return result;
    }

    public String getStorageName(Map<String, Object> map) {
        String result = null;
        if (map != null) {
            result = (String) map.get(FileListManager.KEY_NAME);
        }
        return result;
    }

    public String getStoragePath(Map<String, Object> map) {
        String result = null;
        if (map != null) {
            result = (String) map.get(FileListManager.KEY_PATH);
        }
        return result;
    }

    public String getStorageFsType(Map<String, Object> map) {
        String result = null;
        if (map != null) {
            result = (String) map.get(FileListManager.KEY_FS_TYPE);
        }
        return result;
    }

    public String getStorageVolumeId(Map<String, Object> map) {
        String result = null;
        if (map != null) {
            result = (String) map.get(FileListManager.KEY_VOLUME_ID);
        }
        return result;
    }

    public String getStorageDeviceId(Map<String, Object> map) {
        String result = null;
        if (map != null) {
            result = (String) map.get(FileListManager.KEY_DISK_ID);
        }
        return result;
    }

    public String getAppDefaultPath() {
        return PVR_DEFAULT_PATH;
    }

    public void formatStorageByVolumeId(String volumeId) {
        StorageManager storageManager = mFileListManager.getStorageManager();
        if (storageManager != null && volumeId != null) {
            try {
                Method format = StorageManager.class.getMethod("format", String.class);
                format.invoke(storageManager, volumeId);
            } catch (Exception e) {
                Log.d(TAG, "formatStorageByVolumeId format Exception = " + e.getMessage());
            }
        } else {
            Log.d(TAG, "formatStorageByVolumeId null");
        }
    }

    public void formatStorageByDiskId(String diskId) {
        StorageManager storageManager = mFileListManager.getStorageManager();
        if (storageManager != null && diskId != null) {
            try {
                Method partitionPublic = StorageManager.class.getMethod("partitionPublic", String.class);
                partitionPublic.invoke(storageManager, diskId);
            } catch (Exception e) {
                Log.d(TAG, "formatStorageByDiskId format Exception = " + e.getMessage());
            }
        } else {
            Log.d(TAG, "formatStorageByDiskId null");
        }
    }

    public static String convertMediaPathToMountedPath(String mediaPath) {
        String result = mediaPath;
        if (isMediaPath(mediaPath)) {
            String[] split = mediaPath.split("/");
            if (split != null && split.length >= 4) {
                result = "/storage";
                for (int i = 3; i < split.length; i++) {
                    result += "/" + split[i];
                }
            }
        } else {
            Log.d(TAG, "convertMediaPathToMountedPath not ready");
        }
        return result;
    }

    public static String convertStoragePathToMediaPath(String mountPath) {
        String result = mountPath;
        if (isStoragePath(mountPath)) {
            String[] split = mountPath.split("/");
            if (split != null && split.length >= 3) {
                result = "/mnt/media_rw";
                for (int i = 2; i < split.length; i++) {
                    result += "/" + split[i];
                }
            }
        } else {
            Log.d(TAG, "convertStoragePathToMediaPath not ready");
        }
        return result;
    }

    public static boolean isMountedPathAvailable(String path) {
        boolean result = false;
        if (TextUtils.isEmpty(path)) {
            return result;
        }
        String volumeState = Environment.getExternalStorageState(new File(path));
        if (TextUtils.equals(volumeState, Environment.MEDIA_MOUNTED)) {
            result = true;
        }
        Log.d(TAG, "isMountedPathAvailable path = " + path + ", volumeState = " + volumeState + ", result = " + result);
        return result;
    }

    public static boolean isMediaPath(String mediaPath) {
        boolean result = false;
        if (mediaPath != null && mediaPath.startsWith("/mnt/media_rw/")) {
            result = true;
        }
        return result;
    }

    public static boolean isStoragePath(String mediaPath) {
        boolean result = false;
        if (mediaPath != null && mediaPath.startsWith("/storage") && !mediaPath.startsWith("/storage/emulated")) {
            result = true;
        }
        return result;
    }

    public static boolean isStorageFatFormat(String fsType) {
        boolean result = false;
        if (fsType != null) {
            result = fsType.toUpperCase().contains("FAT");
        }
        return result;
    }
}
