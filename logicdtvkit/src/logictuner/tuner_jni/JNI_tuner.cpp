/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#define LOG_TAG "tuner-jni"

//#include "android_runtime/AndroidRuntime.h"
#include <map>
#include <algorithm>
#include <mutex>
#include "JNI_tuner.h"
//#include "JNIASPlayer.h"
#include "type_change_utils.h"

using namespace android;
using ::android::sp;
using ::android::RefBase;

struct tuner_fields {
    jmethodID getDemuxCapabilities;
    jmethodID getFrontendIds;
    jmethodID getFrontendInfo;
    jmethodID getFrontendInfoById;
    jmethodID tune;
    jmethodID cancelTuning;
    jmethodID scan;
    jmethodID cancelScanning;
    jmethodID closeFrontend;
    jmethodID close;
    jmethodID getFrontendStatus;
    jmethodID setOnTuneEventListener;
    jmethodID clearOnTuneEventListener;
    jmethodID openFilter;
    jmethodID getAvSyncHwId;
    jmethodID getAvSyncTime;
    jmethodID openLnb;
    jmethodID openLnbByName;
    jmethodID connectCiCam;
    jmethodID disconnectCiCam;
    jmethodID connectFrontendToCiCam;
    jmethodID disconnectFrontendToCiCam;
    jmethodID openDescrambler;
    jmethodID getTuenr;
    jfieldID nativeTunerContext;
    jfieldID tunerType;
    jfieldID surface;
};

struct filter_fields {
    jmethodID setType;
    jmethodID setCallback;
    jmethodID configure;
    jmethodID getId;
    jmethodID setDataSource;
    jmethodID start;
    jmethodID stop;
    jmethodID flush;
    jmethodID read;
    jmethodID close;
};

struct lnb_fields {
    jmethodID setVoltage;
    jmethodID setTone;
    jmethodID setSatellitePosition;
    jmethodID sendDiseqcMessage;
    jmethodID close;
};

struct descrambler_fields {
    jmethodID addPid;
    jmethodID removePid;
    jmethodID setKeyToken;
    jmethodID close;
};

static jobject gSurface;//For JTsPlayer Test
static int gAndroidSDKVersion = 30;
static tuner_fields gTunerFields;
static filter_fields gFilterFields;
static lnb_fields gLnbFields;
static descrambler_fields gDescramblerFields;
static JavaVM *gJavaVM = NULL;
static std::map<jint, jobject> gTunerMap;
static std::vector<long> gTunerStatusListener;
static std::mutex gTunerLock;

#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))

#define FIND_CLASS(var, className) \
        var = env->FindClass(className); \
        LOG_FATAL_IF(! var, "Unable to find class " className);

#define GET_METHOD_ID(var, clazz, methodName, methodDescriptor) \
        var = env->GetMethodID(clazz, methodName, methodDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find method " methodName);

#define GET_FIELD_ID(var, clazz, fieldName, fieldDescriptor) \
        var = env->GetFieldID(clazz, fieldName, fieldDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find field " fieldName);

/****************Internal method**********************/
static jobject getTuner(int tunerClientId) {
    ALOGD("start:%s, tunerClientId:%d", __FUNCTION__, tunerClientId);
    std::map<jint, jobject>::iterator iter = gTunerMap.find(tunerClientId);
    if (iter != gTunerMap.end()) {
        ALOGD("end:%s, tuner jobject : %p", __FUNCTION__, iter->second);
        return iter->second;
    }
    ALOGE(":%s, can't find tuner object by client Id", __FUNCTION__);
    return NULL;
}

static void nativeClearException(JNIEnv *env) {
    if (NULL != env) {
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    } else {
        ALOGE(":%s, JNIEnv has release", __FUNCTION__);
    }
}

static void notifyTunerStatusChange(int tunerClientId, TUNER_LIFECYCLE_STATUS status) {
    ALOGD("%s : tunerClientId :%d, change status: %d", __FUNCTION__, tunerClientId, status);

    long value = 0;

    for (int i = 0; i < gTunerStatusListener.size(); i++) {
        value = gTunerStatusListener[i];
        ALOGD("notify statusListener : 0x%lx", value);

        if (0 != value) {
            Am_tuner_status_listener statusListener = (Am_tuner_status_listener)value;
            statusListener(tunerClientId, status);
        } else {
            ALOGD("not register callback");
        }
    }
}

/*
static void saveNativeTuner(JNIEnv *env, jobject thiz, const sp<AMTuner> &tuner) {
    sp<AMTuner> old = (AMTuner *)env->GetLongField(thiz, gTunerFields.nativeTunerContext);

    if (NULL != tuner) {
        tuner->incStrong(thiz);
    }
    if (NULL != old) {
        old->decStrong(thiz);
    }

    if (NULL != tuner) {
        env->SetLongField(thiz, gTunerFields.nativeTunerContext, (jlong)tuner.get());
    }
}
*/

/****************DTVKIT method**********************/
//jobject Am_tuner_getDemuxCapabilities(jobject tuner) {
    //JNIEnv *env = AndroidRuntime::getJNIEnv();
//}
int Am_tuner_getAndroidVersion() {
    //ALOGD("%s : %d", __FUNCTION__, gAndroidSDKVersion);
    return gAndroidSDKVersion;
}

JNIEnv* Am_tuner_getJNIEnv(bool* needsDetach) {
    *needsDetach = false;
    JNIEnv *env = NULL;
    gJavaVM->GetEnv((void **) &env, JNI_VERSION_1_4);
    if (NULL == env) {
        JavaVMAttachArgs args = {JNI_VERSION_1_4, NULL, NULL};
        int result = gJavaVM->AttachCurrentThread(&env, (void*)&args);
        if (JNI_OK != result) {
            ALOGE("thread attach failed: %#x", result);
            return NULL;
        }
        *needsDetach = true;
    }
    return env;
}

void Am_tuner_detachJNIEnv() {
    int result = gJavaVM->DetachCurrentThread();
    if (JNI_OK != result) {
        ALOGE("thread detach failed: %#x", result);
    }
}

int Am_tuner_getTunerClientId() {
    ALOGD("start:%s", __FUNCTION__);
    int tunerClientId = TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID;
    int size = gTunerMap.size();
    ALOGD("create tuner size:%d", size);
    if (0 < size) {
        std::map<jint, jobject>::iterator iter = gTunerMap.begin();
        tunerClientId = iter->first;
    }
    ALOGD("end:%s, tunerClientId:%d", __FUNCTION__, tunerClientId);
    return tunerClientId;
}

int Am_tuner_getTunerClientIdByType(int tunerType) {
    ALOGD("start:%s, tunerType : %d", __FUNCTION__, tunerType);
    bool attached = false;
    int tunerClientId = TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID;
    int tunerScanClientId = TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID;//For Scan switch LivePlay maybe have two tuner client
    int tunerBackgroundClientId = TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID;//Background tuner for standby
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);

    if (NULL == env) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return tunerClientId;
    }
    std::lock_guard<std::mutex> tunerlock(gTunerLock);
    ALOGD("tuner size:%d", gTunerMap.size());
    for (std::map<jint, jobject>::iterator iter = gTunerMap.begin(); iter != gTunerMap.end(); iter++) {
        jobject tuner = iter->second;
        if (NULL != tuner) {
            ALOGD("clientId :%d, type : %d, tuner :%p,", iter->first, (int)env->GetIntField(tuner, gTunerFields.tunerType), tuner);
            if (TUNER_TYPE_LIVE_0 == tunerType) {
                if (TUNER_TYPE_LIVE_0 == (int)env->GetIntField(tuner, gTunerFields.tunerType)) {
                    tunerClientId = iter->first;
                } else if (TUNER_TYPE_SCAN == (int)env->GetIntField(tuner, gTunerFields.tunerType)) {
                    tunerScanClientId = iter->first;
                } else if (TUNER_TYPE_BACKGROUND == (int)env->GetIntField(tuner, gTunerFields.tunerType)) {
                    tunerBackgroundClientId = iter->first;
                }
            } else {
                if (tunerType == (int)env->GetIntField(tuner, gTunerFields.tunerType)) {
                    tunerClientId = iter->first;
                    break;
                }
            }
        }
    }
    ReleaseEnv(attached);
    ALOGD("tunerClientId:%d, tunerScanClientId : %d, tunerBackgroundClientId : %d", tunerClientId, tunerScanClientId,
        tunerBackgroundClientId);
    if ((TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID == tunerClientId) && (TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID != tunerScanClientId)) {
        tunerClientId = tunerScanClientId;//under scan status only have scan type tuner.
    }
    if ((TUNER_TYPE_LIVE_0 == tunerType) && (TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID == tunerClientId)) {
        tunerClientId = tunerBackgroundClientId;//for background function,when session release, need use background tuner to do work
    }
    ALOGD("end:%s, tunerClientId:%d", __FUNCTION__, tunerClientId);
    return tunerClientId;
}
jobject Am_tuner_getOriginalTuner(int tunerClientId) {
    ALOGE("Start:%s, Tuner ClientId : %d", __FUNCTION__, tunerClientId);

    bool attached = false;
    if (TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID == tunerClientId) {
        ALOGE("end:%s, not have tuner", __FUNCTION__);
        return NULL;
    }

    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject originalTuner = env->CallObjectMethod(tuner, gTunerFields.getTuenr);
    ReleaseEnv(attached);
    ALOGD("end:%s, Original Tuner object:%p", __FUNCTION__, originalTuner);
    return originalTuner;
}

jobject Am_tuner_getRecordTuner() {
    ALOGE("Start:%s", __FUNCTION__);

    bool attached = false;
    int tunerClientId = Am_tuner_getTunerClientIdByType(TUNER_TYPE_LIVE_0);
    if (TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID == tunerClientId) {
        ALOGE("end:%s, not have tuner", __FUNCTION__);
        return NULL;
    }

    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject recordTuner = env->CallObjectMethod(tuner, gTunerFields.getTuenr);
    ReleaseEnv(attached);
    ALOGD("end:%s, recordTuner object:%p", __FUNCTION__, recordTuner);
    return recordTuner;
}

jobject Am_tuner_getDvrTunerByType(int tunerType) {
    ALOGD("Start:%s, tuner type : %d", __FUNCTION__, tunerType);

    if ((TUNER_TYPE_DVR_RECORD > tunerType) || (TUNER_TYPE_DVR_PLAY < tunerType)) {
        ALOGE("Error:%s, input tuner type error, tuner Type : %d", __FUNCTION__, tunerType);
        return NULL;
    }

    bool attached = false;
    int tunerClientId = Am_tuner_getTunerClientIdByType(tunerType);
    if (TUNER_CONSTANT_INVALID_TUNER_CLIENT_ID == tunerClientId) {
        ALOGE("end:%s, not have tuner", __FUNCTION__);
        return NULL;
    }

    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject dvrTuner = env->CallObjectMethod(tuner, gTunerFields.getTuenr);
    ReleaseEnv(attached);
    ALOGD("end:%s, dvrTuner object:%p", __FUNCTION__, dvrTuner);
    return dvrTuner;
}

void Am_tuner_addTunerLifeCycleListener(long listenerContext) {
    ALOGE("Start:%s, listenerContext : 0x%lx", __FUNCTION__, listenerContext);
    gTunerStatusListener.push_back(listenerContext);
    ALOGE("end:%s", __FUNCTION__);
}

void Am_tuner_removeTunerLifeCycleListener(long listenerContext) {
    ALOGE("Start:%s, listenerContext : 0x%lx", __FUNCTION__, listenerContext);

    std::vector<long>::iterator it = find(gTunerStatusListener.begin(), gTunerStatusListener.end(),listenerContext);

    if (it == gTunerStatusListener.end()) {
        ALOGE("%s:can't find this ", __FUNCTION__);
        return;
    }

    std::swap(*it, gTunerStatusListener.back());
    gTunerStatusListener.pop_back();

    ALOGE("end:%s", __FUNCTION__);
}
/*********************Tuner Class map native method*****************************/
jobject Am_tuner_getTunerObjectByClientId(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }

    ALOGD("end:%s, tuner object:%p", __FUNCTION__, tuner);
    return tuner;
}

jobject Am_tuner_getFrontendIds(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }

    jobject list = env->CallObjectMethod(tuner, gTunerFields.getFrontendIds);
    jweak wList = NULL;
    if (NULL != list) {
        wList = env->NewWeakGlobalRef(list);
    }
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return wList;
}

jobject Am_tuner_getFrontendInfo(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject frontendInfo = env->CallObjectMethod(tuner, gTunerFields.getFrontendInfo);
    //TBD need covert to weak global reference
    jweak wFrontendInfo = NULL;
    if (NULL != frontendInfo) {
        wFrontendInfo = env->NewWeakGlobalRef(frontendInfo);
    }
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return wFrontendInfo;
}

jobject Am_tuner_getFrontendInfoById(int tunerClientId, int id) {
    ALOGE("Start:%s:tuner id:%d, frontend id:%d", __FUNCTION__, tunerClientId, id);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject frontendInfo = env->CallObjectMethod(tuner, gTunerFields.getFrontendInfoById, id);
    //TBD need covert to weak global reference
    jweak wFrontendInfo = NULL;
    if (NULL != frontendInfo) {
        wFrontendInfo = env->NewWeakGlobalRef(frontendInfo);
    }
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return wFrontendInfo;
}

jint Am_tuner_tune(int tunerClientId, jobject frontendSettings) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner) || (NULL == frontendSettings)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jint result = env->CallIntMethod(tuner, gTunerFields.tune, frontendSettings);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

jint Am_tuner_cancelTuning(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jint result = env->CallIntMethod(tuner, gTunerFields.cancelTuning);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

jint Am_tuner_scan(int tunerClientId, jobject frontendSettings, int scanType, long callbackContext) {
    ALOGE("Start:%s:tuner id : %d, scanType : %d, callbackContext : 0x%lx", __FUNCTION__, tunerClientId, scanType, callbackContext);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner) || (NULL == frontendSettings)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jint result = env->CallIntMethod(tuner, gTunerFields.scan, frontendSettings, scanType, (jlong)callbackContext);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

jint Am_tuner_cancelScanning(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jint result = env->CallIntMethod(tuner, gTunerFields.cancelScanning);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

void Am_tuner_closeFrontend(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    env->CallVoidMethod(tuner, gTunerFields.closeFrontend);
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return;
}

void Am_tuner_close(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    env->CallVoidMethod(tuner, gTunerFields.close);
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return;
}

jobject Am_tuner_getFrontendStatus(int tunerClientId, jintArray statusTypes) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject frontendStatus = env->CallObjectMethod(tuner, gTunerFields.getFrontendStatus, statusTypes);
    //TBD need covert to weak global reference
    jweak wFrontendStatus = NULL;
    if (NULL != frontendStatus) {
        wFrontendStatus = env->NewWeakGlobalRef(frontendStatus);
    }
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return wFrontendStatus;
}

void Am_tuner_setOnTuneEventListener(int tunerClientId, long callbackContext) {
    ALOGE("Start:%s:tuner id:%d, callbackContext: 0x%lx ", __FUNCTION__, tunerClientId, callbackContext);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    env->CallVoidMethod(tuner, gTunerFields.setOnTuneEventListener, (jlong)callbackContext);
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return;
}

void Am_tuner_clearOnTuneEventListener(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    env->CallVoidMethod(tuner, gTunerFields.clearOnTuneEventListener);
    ReleaseEnv(attached);

    ALOGE("End:%s", __FUNCTION__);
    return;
}

jobject Am_tuner_openFilter(int tunerClientId,
                            int mainType,
                            int subType,
                            long bufferSize,
                            long callbackContext,
                            int privateCallback) {
    ALOGE("Start:%s:tuner id:%d, mainType : %d, subType: %d, bufferSize : %ld,"
          " callbackContext : 0x%lx, privateCallback : %d", __FUNCTION__, tunerClientId,
        mainType, subType, bufferSize, callbackContext, privateCallback);
    jobject globalFilter = NULL;
    bool attached = false;

    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);
    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject filter = env->CallObjectMethod(tuner,
                                           gTunerFields.openFilter,
                                           mainType,
                                           subType,
                                           (jlong)bufferSize,
                                           (jlong)callbackContext,
                                           privateCallback);
    if (NULL != filter) {
        globalFilter = env->NewGlobalRef(filter);
    }
    ReleaseEnv(attached);

    ALOGE("End:%s:globalFilter:%p", __FUNCTION__, globalFilter);
    return globalFilter;
}

jint Am_tuner_getAvSyncHwId(int tunerClientId, jobject filter) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }
    jint avSyncHwId = env->CallIntMethod(tuner, gTunerFields.getAvSyncHwId, filter);

    env->DeleteLocalRef(localFilter);
    ReleaseEnv(attached);

    ALOGE("End:%s, avSyncHwId:%d", __FUNCTION__, avSyncHwId);
    return avSyncHwId;
}

jlong Am_tuner_getAvSyncTime(int tunerClientId, jint avSyncHwId) {
    ALOGE("Start:%s:tuner id:%d, avSyncHwId:%d", __FUNCTION__, tunerClientId, avSyncHwId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jlong syncTime = env->CallLongMethod(tuner, gTunerFields.getAvSyncTime, avSyncHwId);
    ReleaseEnv(attached);

    ALOGE("End:%s, syncTime:%ld", __FUNCTION__, syncTime);
    return syncTime;
}

jobject Am_tuner_openLnb(int tunerClientId, long callbackContext) {
    ALOGE("Start:%s:tuner id:%d, callbackContext : 0x%lx", __FUNCTION__, tunerClientId, callbackContext);
    jobject globalLnb = NULL;
    bool attached = false;

    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);
    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject lnb = env->CallObjectMethod(tuner, gTunerFields.openLnb, (jlong)callbackContext);
    if (NULL != lnb) {
        globalLnb = env->NewGlobalRef(lnb);
    }
    env->DeleteLocalRef(lnb);
    ReleaseEnv(attached);

    ALOGE("End:%s:globalLnb:%p", __FUNCTION__, globalLnb);
    return globalLnb;
}

jobject Am_tuner_openLnbByName(int tunerClientId, const std::string &name, long callbackContext) {
    ALOGE("Start:%s:tuner id:%d, name %s, callbackContext : 0x%lx", __FUNCTION__, tunerClientId, name.c_str(), callbackContext);
    jobject globalLnb = NULL;
    bool attached = false;

    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);
    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jbyteArray nameArray = env->NewByteArray(name.length());
    if (NULL == nameArray) {
        ReleaseEnv(attached);
        ALOGE("%s: create name array error", __FUNCTION__);
        return NULL;
    }

    env->SetByteArrayRegion(nameArray, 0, name.length(), (jbyte*)name.c_str());
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID stringInit = env->GetMethodID(stringClass, "<init>" , "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("utf-8");
    jstring lnbName = (jstring)env->NewObject(stringClass, stringInit, nameArray, encoding);

    if (NULL != lnbName) {
        jobject lnb = env->CallObjectMethod(tuner, gTunerFields.openLnb, lnbName, (jlong)callbackContext);
        if (NULL != lnb) {
            globalLnb = env->NewGlobalRef(lnb);
            env->DeleteLocalRef(lnb);
        }
    } else {
        ALOGE("%s: create lnb Name error", __FUNCTION__);
    }
    env->DeleteLocalRef(nameArray);
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(lnbName);
    ReleaseEnv(attached);

    ALOGE("End:%s:globalLnb:%p", __FUNCTION__, globalLnb);
    return globalLnb;
}

int Am_tuner_connectCiCam(int tunerClientId, int ciCamId) {
    ALOGE("Start:%s:tuner id:%d, ciCamId:%d", __FUNCTION__, tunerClientId, ciCamId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    int result = env->CallIntMethod(tuner, gTunerFields.connectCiCam, ciCamId);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

int Am_tuner_disconnectCiCam(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    int result = env->CallIntMethod(tuner, gTunerFields.disconnectCiCam);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

int Am_tuner_connectFrontendToCiCam(int tunerClientId, int ciCamId) {
    ALOGE("Start:%s:tuner id:%d, ciCamId:%d", __FUNCTION__, tunerClientId, ciCamId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    int result = env->CallIntMethod(tuner, gTunerFields.connectFrontendToCiCam, ciCamId);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

int Am_tuner_disconnectFrontendToCiCam(int tunerClientId, int ciCamId) {
    ALOGE("Start:%s:tuner id:%d, ciCamId:%d", __FUNCTION__, tunerClientId, ciCamId);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    int result = env->CallIntMethod(tuner, gTunerFields.disconnectFrontendToCiCam, ciCamId);
    ReleaseEnv(attached);

    ALOGE("End:%s, result:%d", __FUNCTION__, result);
    return result;
}

jobject Am_tuner_openDescrambler(int tunerClientId) {
    ALOGE("Start:%s:tuner id:%d", __FUNCTION__, tunerClientId);
    jobject globalDescrambler = NULL;
    bool attached = false;

    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);
    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject descrambler = env->CallObjectMethod(tuner, gTunerFields.openDescrambler);
    if (NULL != descrambler) {
        globalDescrambler = env->NewGlobalRef(descrambler);
    }
    env->DeleteLocalRef(descrambler);
    ReleaseEnv(attached);

    ALOGE("End:%s:globalDescrambler:%p", __FUNCTION__, globalDescrambler);
    return globalDescrambler;
}

jobject Am_tuner_getSurfaceByTunerClient(int tunerClientId) {
    ALOGE("Start %s:, tunerClientId : %d", __FUNCTION__, tunerClientId);
    bool attached = false;
    jobject globalSurface = NULL;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    jobject tuner = getTuner(tunerClientId);

    if ((NULL == env) || (NULL == tuner)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return NULL;
    }
    jobject surface = env->GetObjectField(tuner, gTunerFields.surface);//need ASPlayer to delete globalSurface
    if (NULL != surface) {
        globalSurface = env->NewGlobalRef(surface);
    }
    ReleaseEnv(attached);
    ALOGE("End %s surface : %p", __FUNCTION__, globalSurface);
    return globalSurface;
}

void Am_tuner_DeleteSurfaceRef(jobject surface) {
    ALOGE("Start %s:, surface : %p", __FUNCTION__, surface);
    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);

    if (NULL == env) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    env->DeleteGlobalRef(surface);

    ReleaseEnv(attached);
    ALOGE("End %s", __FUNCTION__);
    return;
}
/*********************Filter Class map native method*****************************/
void Am_filter_setType(jobject filter, int mainType, int subType) {
    ALOGE("Start %s: filter:%p, mainType:%d, subType:%d", __FUNCTION__, filter, mainType, subType);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return;
    }
    env->CallVoidMethod(localFilter, gFilterFields.setType, mainType, subType);

    env->DeleteLocalRef(localFilter);
    ReleaseEnv(attached);

    ALOGE("End %s :", __FUNCTION__);
}

void Am_filter_setCallback(jobject filter, long callbackContext) {
    ALOGE("Start %s: filter:%p, callbackContext : %ld", __FUNCTION__, filter, callbackContext);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return;
    }
    env->CallVoidMethod(localFilter, gFilterFields.setCallback, (jlong)callbackContext);

    env->DeleteLocalRef(localFilter);
    ReleaseEnv(attached);

    ALOGE("End %s :", __FUNCTION__);
}

jint Am_filter_configure(jobject filter, jobject config) {
    ALOGE("Start %s:, filter : %p", __FUNCTION__, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    jobject localConfig(env->NewLocalRef(config));
    if ((env->IsSameObject(localFilter, nullptr)) || (env->IsSameObject(localConfig, nullptr))) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter or config nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }
    jint result = env->CallIntMethod(localFilter, gFilterFields.configure, localConfig);

    env->DeleteLocalRef(localFilter);
    env->DeleteLocalRef(localConfig);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

jint Am_filter_getId(jobject filter) {
    //ALOGE("Start %s:, filter : %p", __FUNCTION__, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint filterId = env->CallIntMethod(localFilter, gFilterFields.getId);

    env->DeleteLocalRef(localFilter);
    ReleaseEnv(attached);

    ALOGE("%s : filter : %p, filterId: 0x%x ", __FUNCTION__, filter, filterId);
    return filterId;
}

jint Am_filter_setDataSource(jobject filter, jobject source) {
    ALOGE("Start %s: filter : %p", __FUNCTION__, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    jobject localSource(env->NewLocalRef(source));
    if ((env->IsSameObject(localFilter, nullptr)) || (env->IsSameObject(localSource, nullptr))) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter or config nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localFilter, gFilterFields.setDataSource, localSource);

    env->DeleteLocalRef(localFilter);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

jint Am_filter_start(jobject filter) {
    ALOGE("Start %s:, filter : %p", __FUNCTION__, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localFilter, gFilterFields.start);

    env->DeleteLocalRef(localFilter);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

jint Am_filter_stop(jobject filter) {
    ALOGE("Start %s:, filter : %p", __FUNCTION__, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localFilter, gFilterFields.stop);

    env->DeleteLocalRef(localFilter);
    nativeClearException(env);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

jint Am_filter_flush(jobject filter) {
    ALOGE("Start %s:, filter : %p", __FUNCTION__, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localFilter, gFilterFields.flush);

    env->DeleteLocalRef(localFilter);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

jint Am_filter_read(jobject filter, char * buffer, long offset, long size) {
    //ALOGE("Start %s:, filter :%p, offset : %ld, size : %ld", __FUNCTION__, filter, offset, size);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if ((NULL == env) || (NULL == buffer)) {
        ReleaseEnv(attached);
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jbyteArray readBuffer = env->NewByteArray(size);
    if (NULL == readBuffer) {
        ReleaseEnv(attached);
        ALOGE("%s: create read buffer error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jint readLength = env->CallIntMethod(localFilter, gFilterFields.read, readBuffer, (jlong)offset, (jlong)size);

    if (size != (long)readLength) {
        ALOGE("%s : readLength: %d ", __FUNCTION__, readLength);
    }

    if (0 != readLength) {
        env->GetByteArrayRegion(readBuffer, 0, readLength, (jbyte *)buffer);
    }

    env->DeleteLocalRef(localFilter);
    env->DeleteLocalRef(readBuffer);
    ReleaseEnv(attached);

    ALOGE("%s:, filter :%p, offset : %ld, size : %ld", __FUNCTION__, filter, offset, size);
    return readLength;
}

void Am_filter_close(jobject filter) {
    ALOGE("Start %s: filter :%p", __FUNCTION__, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    jobject localFilter(env->NewLocalRef(filter));
    if (env->IsSameObject(localFilter, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input filter is nullptr", __FUNCTION__);
        return;
    }

    env->CallVoidMethod(localFilter, gFilterFields.close);

    env->DeleteLocalRef(localFilter);
    env->DeleteGlobalRef(filter);
    nativeClearException(env);
    ReleaseEnv(attached);

    ALOGE("End %s : ", __FUNCTION__);
}

/*********************Lnb Class map native method*****************************/
int Am_lnb_setVoltage(jobject lnb, int voltage) {
    ALOGE("Start %s:, lnb : %p, voltage : %d", __FUNCTION__, lnb, voltage);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localLnb(env->NewLocalRef(lnb));
    if (env->IsSameObject(localLnb, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input lnb is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localLnb, gLnbFields.setVoltage, voltage);

    env->DeleteLocalRef(localLnb);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

int Am_lnb_setTone(jobject lnb, int tone) {
    ALOGE("Start %s:, lnb : %p, tone : %d", __FUNCTION__, lnb, tone);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localLnb(env->NewLocalRef(lnb));
    if (env->IsSameObject(localLnb, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input lnb is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localLnb, gLnbFields.setTone, tone);

    env->DeleteLocalRef(localLnb);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

int Am_lnb_setSatellitePosition(jobject lnb, int position) {
    ALOGE("Start %s:, lnb : %p, position : %d", __FUNCTION__, lnb, position);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localLnb(env->NewLocalRef(lnb));
    if (env->IsSameObject(localLnb, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input lnb is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localLnb, gLnbFields.setSatellitePosition, position);

    env->DeleteLocalRef(localLnb);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

int Am_lnb_sendDiseqcMessage(jobject lnb, const std::vector<char> diseqcMessage) {
    ALOGE("Start %s:, lnb : %p", __FUNCTION__, lnb);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localLnb(env->NewLocalRef(lnb));
    if (env->IsSameObject(localLnb, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input lnb is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    char* messagaArray = new char[diseqcMessage.size()];
    std::copy(diseqcMessage.begin(), diseqcMessage.end(), messagaArray);
    jbyteArray byteMessage = TypeChangeUtils::getJNIArray(env, messagaArray, diseqcMessage.size());
    jint result = env->CallIntMethod(localLnb, gLnbFields.sendDiseqcMessage, byteMessage);

    env->DeleteLocalRef(localLnb);
    env->DeleteLocalRef(byteMessage);
    delete[] messagaArray;
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

void Am_lnb_close(jobject lnb) {
    ALOGE("Start %s:, lnb : %p", __FUNCTION__, lnb);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    jobject localLnb(env->NewLocalRef(lnb));
    if (env->IsSameObject(localLnb, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input lnb is nullptr", __FUNCTION__);
        return;
    }

    env->CallVoidMethod(localLnb, gLnbFields.close);

    env->DeleteLocalRef(localLnb);
    env->DeleteGlobalRef(lnb);
    ReleaseEnv(attached);

    ALOGE("End %s", __FUNCTION__);
    return;
}

/*********************Descrambler Class map native method*****************************/
int Am_descrambler_addPid(jobject descrambler, int pidType, int pid, jobject filter) {
    ALOGE("Start %s:, descrambler : %p, pidType : %d, pid : %d, filter : %p", __FUNCTION__, descrambler,
        pidType, pid, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localDescrambler(env->NewLocalRef(descrambler));
    if (env->IsSameObject(localDescrambler, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input descrambler is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localDescrambler, gDescramblerFields.addPid, pidType, pid, filter);

    env->DeleteLocalRef(localDescrambler);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

int Am_descrambler_removePid(jobject descrambler, int pidType, int pid, jobject filter) {
    ALOGE("Start %s:, descrambler : %p, pidType : %d, pid : %d, filter : %p", __FUNCTION__, descrambler,
        pidType, pid, filter);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localDescrambler(env->NewLocalRef(descrambler));
    if (env->IsSameObject(localDescrambler, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input descrambler is nullptr", __FUNCTION__);
        return INVALID_VALUE;
    }

    jint result = env->CallIntMethod(localDescrambler, gDescramblerFields.removePid, pidType, pid, filter);

    env->DeleteLocalRef(localDescrambler);
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

int Am_descrambler_setKeyToken(jobject descrambler, const std::vector<char> keyToken) {
    ALOGE("Start %s:, descrambler : %p", __FUNCTION__, descrambler);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject localDescrambler(env->NewLocalRef(descrambler));
    if ((env->IsSameObject(localDescrambler, nullptr)) || (0 == keyToken.size())) {
        ReleaseEnv(attached);
        ALOGE("%s: input descrambler is nullptr or keyToken size is 0", __FUNCTION__);
        return INVALID_VALUE;
    }

    char* keyArray = new char[keyToken.size()];
    std::copy(keyToken.begin(), keyToken.end(), keyArray);
    jbyteArray keyjbyteArray = TypeChangeUtils::getJNIArray(env, keyArray, keyToken.size());
    jint result = env->CallIntMethod(localDescrambler, gDescramblerFields.setKeyToken, keyjbyteArray);

    env->DeleteLocalRef(localDescrambler);
    env->DeleteLocalRef(keyjbyteArray);
    delete[] keyArray;
    ReleaseEnv(attached);

    ALOGE("End %s : result: %d ", __FUNCTION__, result);
    return result;
}

void Am_descrambler_close(jobject descrambler) {
    ALOGE("Start %s:, descrambler : %p", __FUNCTION__, descrambler);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGE("%s: input parameter error", __FUNCTION__);
        return;
    }
    jobject localDescrambler(env->NewLocalRef(descrambler));
    if (env->IsSameObject(localDescrambler, nullptr)) {
        ReleaseEnv(attached);
        ALOGE("%s: input descrambler is nullptr", __FUNCTION__);
        return;
    }

    env->CallVoidMethod(localDescrambler, gDescramblerFields.close);

    env->DeleteLocalRef(localDescrambler);
    env->DeleteGlobalRef(descrambler);
    ReleaseEnv(attached);

    ALOGE("End %s", __FUNCTION__);
    return;
}

//For JTsPlayer test case
jobject Am_tuner_getSurface() {
    return gSurface;
}
/*********************native method*****************************/
static void dtvkit_tuner_android_version_setting() {
    char sdk[128] = "0";
    __system_property_get("ro.build.version.sdk", sdk);
    gAndroidSDKVersion = atoi(sdk);
    ALOGD(":%s, Android Version: %d", __FUNCTION__, gAndroidSDKVersion);
}

static void dtvkit_tuner_native_init (JNIEnv *env) {
    jclass tunerClazz;
    FIND_CLASS(tunerClazz, TUNER_CLASS);
    if (NULL != tunerClazz) {
        GET_METHOD_ID(gTunerFields.getDemuxCapabilities, tunerClazz, "getDemuxCapabilities", "()Landroid/media/tv/tuner/DemuxCapabilities;");
        GET_METHOD_ID(gTunerFields.getFrontendIds, tunerClazz, "getFrontendIds", "()Ljava/util/List;");
        GET_METHOD_ID(gTunerFields.getFrontendInfo, tunerClazz, "getFrontendInfo", "()Landroid/media/tv/tuner/frontend/FrontendInfo;");
        GET_METHOD_ID(gTunerFields.getFrontendInfoById, tunerClazz, "getFrontendInfoById", "(I)Landroid/media/tv/tuner/frontend/FrontendInfo;");
        GET_METHOD_ID(gTunerFields.tune, tunerClazz, "tune", "(Landroid/media/tv/tuner/frontend/FrontendSettings;)I");
        GET_METHOD_ID(gTunerFields.cancelTuning, tunerClazz, "cancelTuning", "()I");
        GET_METHOD_ID(gTunerFields.scan, tunerClazz, "scan", "(Landroid/media/tv/tuner/frontend/FrontendSettings;IJ)I");
        GET_METHOD_ID(gTunerFields.cancelScanning, tunerClazz, "cancelScanning", "()I");
        GET_METHOD_ID(gTunerFields.closeFrontend, tunerClazz, "closeFrontend", "()V");
        GET_METHOD_ID(gTunerFields.close, tunerClazz, "close", "()V");
        GET_METHOD_ID(gTunerFields.getFrontendStatus, tunerClazz, "getFrontendStatus", "([I)Landroid/media/tv/tuner/frontend/FrontendStatus;");
        GET_METHOD_ID(gTunerFields.setOnTuneEventListener, tunerClazz, "setOnTuneEventListener", "(J)V");
        GET_METHOD_ID(gTunerFields.clearOnTuneEventListener, tunerClazz, "clearOnTuneEventListener", "()V");
        GET_METHOD_ID(gTunerFields.openFilter, tunerClazz, "openFilter", "(IIJJI)Ldroidlogic/dtvkit/tuner/FilterAdapter;");
        GET_METHOD_ID(gTunerFields.getAvSyncHwId, tunerClazz, "getAvSyncHwId", "(Ldroidlogic/dtvkit/tuner/FilterAdapter;)I"); //TBD callback need modify
        GET_METHOD_ID(gTunerFields.getAvSyncTime, tunerClazz, "getAvSyncTime", "(I)J");
        GET_METHOD_ID(gTunerFields.openLnb, tunerClazz, "openLnb", "(J)Ldroidlogic/dtvkit/tuner/LnbAdapter;");
        GET_METHOD_ID(gTunerFields.openLnbByName, tunerClazz, "openLnbByName", "(Ljava/lang/String;J)Ldroidlogic/dtvkit/tuner/LnbAdapter;");
        GET_METHOD_ID(gTunerFields.connectCiCam, tunerClazz, "connectCiCam", "(I)I");
        GET_METHOD_ID(gTunerFields.disconnectCiCam, tunerClazz, "disconnectCiCam", "()I");
        GET_METHOD_ID(gTunerFields.connectFrontendToCiCam, tunerClazz, "connectFrontendToCiCam", "(I)I");
        GET_METHOD_ID(gTunerFields.disconnectFrontendToCiCam, tunerClazz, "disconnectFrontendToCiCam", "(I)I");
        GET_METHOD_ID(gTunerFields.openDescrambler, tunerClazz, "openDescrambler", "()Ldroidlogic/dtvkit/tuner/DescramblerAdapter;");
        //GET_FIELD_ID(gTunerFields.nativeTunerContext, tunerClazz, "mNativeTunerContext", "J");
        GET_METHOD_ID(gTunerFields.getTuenr, tunerClazz, "getTuenr", "()Landroid/media/tv/tuner/Tuner;");
        GET_FIELD_ID(gTunerFields.tunerType, tunerClazz, "mTunerType", "I");
        GET_FIELD_ID(gTunerFields.surface, tunerClazz, "mSurface", "Landroid/view/Surface;");
    } else {
        ALOGE("ERROR: TunerAdapter find class error\n");
    }

    jclass filterClazz;
    FIND_CLASS(filterClazz, FILTER_CLASS);
    if (NULL != filterClazz) {
        GET_METHOD_ID(gFilterFields.setType, filterClazz, "setType", "(II)V");
        GET_METHOD_ID(gFilterFields.setCallback, filterClazz, "setCallback", "(J)V");
        GET_METHOD_ID(gFilterFields.configure, filterClazz, "configure", "(Landroid/media/tv/tuner/filter/FilterConfiguration;)I");
        GET_METHOD_ID(gFilterFields.getId, filterClazz, "getId", "()I");
        GET_METHOD_ID(gFilterFields.setDataSource, filterClazz, "setDataSource", "(Landroid/media/tv/tuner/filter/Filter;)I");
        GET_METHOD_ID(gFilterFields.start, filterClazz, "start", "()I");
        GET_METHOD_ID(gFilterFields.stop, filterClazz, "stop", "()I");
        GET_METHOD_ID(gFilterFields.flush, filterClazz, "flush", "()I");
        GET_METHOD_ID(gFilterFields.read, filterClazz, "read", "([BJJ)I");
        GET_METHOD_ID(gFilterFields.close, filterClazz, "close", "()V");
    } else {
        ALOGE("ERROR: FilterAdapter find class error\n");
    }

    jclass lnbClazz;
    FIND_CLASS(lnbClazz, LNB_CLASS);
    if (NULL != lnbClazz) {
        GET_METHOD_ID(gLnbFields.setVoltage, lnbClazz, "setVoltage", "(I)I");
        GET_METHOD_ID(gLnbFields.setTone, lnbClazz, "setTone", "(I)I");
        GET_METHOD_ID(gLnbFields.setSatellitePosition, lnbClazz, "setSatellitePosition", "(I)I");
        GET_METHOD_ID(gLnbFields.sendDiseqcMessage, lnbClazz, "sendDiseqcMessage", "([B)I");
        GET_METHOD_ID(gLnbFields.close, lnbClazz, "close", "()V");
    } else {
        ALOGE("ERROR: LnbAdapter find class error\n");
    }

    jclass descramblerClazz;
    FIND_CLASS(descramblerClazz, DESCRAMBLER_CLASS);
    if (NULL != descramblerClazz) {
        GET_METHOD_ID(gDescramblerFields.addPid, descramblerClazz, "addPid", "(IILdroidlogic/dtvkit/tuner/FilterAdapter;)I");
        GET_METHOD_ID(gDescramblerFields.removePid, descramblerClazz, "removePid", "(IILdroidlogic/dtvkit/tuner/FilterAdapter;)I");
        GET_METHOD_ID(gDescramblerFields.setKeyToken, descramblerClazz, "setKeyToken", "([B)I");
        GET_METHOD_ID(gDescramblerFields.close, descramblerClazz, "close", "()V");
    } else {
        ALOGE("ERROR: DescramblerAdapter find class error\n");
    }

//    ALOGD(":%s, test for JniASPlayer_registerJNI START", __FUNCTION__);
//    JniASPlayer_registerJNI(env);
//    ALOGD(":%s, test for JniASPlayer_registerJNI END", __FUNCTION__);
    dtvkit_tuner_android_version_setting();
}

/*********TunerAdapter Native***************/
static void dtvkit_tuner_native_setup(JNIEnv *env, jobject thiz, jint tunerClientId) {
    ALOGD("start:%s, tunerClientId:%d", __FUNCTION__, tunerClientId);
    std::map<jint, jobject>::iterator iter = gTunerMap.find(tunerClientId);
    if (iter != gTunerMap.end()) {
        jobject oldTuner = iter->second;
        if (NULL != oldTuner) {
            env->DeleteGlobalRef(oldTuner);
        }
        gTunerMap.erase(iter);
        ALOGD(":%s, delete old tuner", __FUNCTION__);
    }
    jobject tuner = env->NewGlobalRef(thiz);
    gTunerMap.insert(std::pair<jint, jobject>(tunerClientId, tuner));
    //dtvkit_tuner_android_version_setting();
    notifyTunerStatusChange(tunerClientId, TUNER_CREATE);
    ALOGD("end:%s", __FUNCTION__);
}

static void dtvkit_tuner_native_release(JNIEnv *env, jobject thiz, jint tunerClientId) {
    ALOGD("enter:%s, tunerClientId:%d", __FUNCTION__, tunerClientId);
    std::lock_guard<std::mutex> tunerlock(gTunerLock);
    std::map<jint, jobject>::iterator iter = gTunerMap.find(tunerClientId);
    if (iter != gTunerMap.end()) {
        jobject tuner = iter->second;
        if (NULL != tuner) {
            env->DeleteGlobalRef(tuner);
        }
        gTunerMap.erase(iter);
        notifyTunerStatusChange(tunerClientId, TUNER_RELEASE);
    } else {
        ALOGE("tuner release error, not have tuner Client");
    }
    ALOGD("end:%s", __FUNCTION__);
}

static void dtvkit_tuner_native_tune_callback(JNIEnv *env, jobject tuner, jint tunerClientId, jint tuneEvent) {
    ALOGD("%s start: callback tunerClientId : %d tuneEvent : %d", __FUNCTION__, tunerClientId, tuneEvent);

    jclass tunerClass;
    FIND_CLASS(tunerClass, TUNER_CLASS);
    jfieldID callbackField = env->GetFieldID(tunerClass, "mTuneEventListenerContext", "J");
    long callbackContext = (long)env->GetLongField(tuner, callbackField);
    if (0 != callbackContext) {
        Am_tuner_notifyTunerEvent tunerCallback = (Am_tuner_notifyTunerEvent)callbackContext;
        tunerCallback(tunerClientId, tuneEvent);
    } else {
        ALOGD("not register callback");
    }
    ALOGD("end:%s", __FUNCTION__);
}

//For JTsPlayer test case
static void dtvkit_tuner_native_set_surface(JNIEnv *env, jobject tuner, jobject surface) {
    ALOGD("%s surface: %p", __FUNCTION__, surface);

    if (NULL != surface) {
        gSurface = env->NewGlobalRef(surface);
    } else {
        if (NULL != gSurface) {
            env->DeleteGlobalRef(gSurface);
            gSurface = NULL;
        }
    }

    ALOGD("end:%s", __FUNCTION__);
}

static void dtvkit_tuner_native_scan_callback(JNIEnv *env, jobject tuner, jint tunerClientId, jint scanMessageType, jobjectArray scanMessage) {
    ALOGD("%s tunerClientId : %d scan message type: %d", __FUNCTION__, tunerClientId, scanMessageType);
    jclass tunerClass;
    FIND_CLASS(tunerClass, TUNER_CLASS);
    jfieldID callbackField = env->GetFieldID(tunerClass, "mScanCallbackConext", "J");
    long callbackContext = (long)env->GetLongField(tuner, callbackField);
    if (0 != callbackContext) {
        Am_tuner_notifyScanCallbackEvent scanCallback = (Am_tuner_notifyScanCallbackEvent)callbackContext;
        scanCallback(tunerClientId, scanMessageType, scanMessage);
    } else {
        ALOGD("not register callback");
    }
    ALOGD("end:%s", __FUNCTION__);
}

/*********FilterAdapter Native***************/
static void dtvkit_filter_native_callback(JNIEnv *env, jobject filter, jobjectArray events, jint status) {
    ALOGD("%s start: callback", __FUNCTION__);

    jclass filterClass;
    FIND_CLASS(filterClass, FILTER_CLASS);
    jfieldID callbackField = env->GetFieldID(filterClass, "mFilterCallbackContext", "J");
    long callbackContext = (long)env->GetLongField(filter, callbackField);
    if (0 != callbackContext) {
        Am_filter_callback filterCallback = (Am_filter_callback)callbackContext;
        filterCallback(filter, events, status);
    } else {
        ALOGD("not register callback");
    }
    nativeClearException(env);
    ALOGD("end:%s", __FUNCTION__);
}

/*********LnbAdapter Native***************/
static void dtvkit_lnb_native_callback(JNIEnv *env, jobject lnb, jint tunerClientId, jint eventType, jbyteArray diseqcMessage) {
    ALOGD("%s start: callback tunerClientId:%d, eventType: %d", __FUNCTION__, tunerClientId, eventType);

    jclass lnbClass;
    FIND_CLASS(lnbClass, LNB_CLASS);
    jfieldID callbackField = env->GetFieldID(lnbClass, "mLnbCallbackContext", "J");
    long callbackContext = (long)env->GetLongField(lnb, callbackField);
    if (0 != callbackContext) {
        Am_lnb_callback lnbCallback = (Am_lnb_callback)callbackContext;
        lnbCallback(lnb, tunerClientId, eventType, diseqcMessage);
    } else {
        ALOGD("not register callback");
    }
    ALOGD("end:%s", __FUNCTION__);
}

extern void tunerTest();
static void dtvkit_tuner_native_testcase(JNIEnv *env) {
    tunerTest();
}

static JNINativeMethod gTunerMethods[] = {
    {"nativeInit", "()V",  (void *)dtvkit_tuner_native_init},
    {"nativeSetup", "(I)V",  (void *)dtvkit_tuner_native_setup},
    {"nativeRelease", "(I)V",  (void *)dtvkit_tuner_native_release},
    {"nativeTunerEventCallback", "(II)V",  (void *)dtvkit_tuner_native_tune_callback},
    {"nativeTunerSetSurface", "(Landroid/view/Surface;)V",  (void *)dtvkit_tuner_native_set_surface},
    {"nativeTunerTestCase", "()V",  (void *)dtvkit_tuner_native_testcase},
    {"nativeScanCallback", "(II[Ljava/lang/Object;)V",  (void *)dtvkit_tuner_native_scan_callback},
};

static JNINativeMethod gFilterMethods[] = {
    {"nativeFilterCallback", "([Landroid/media/tv/tuner/filter/FilterEvent;I)V",  (void *)dtvkit_filter_native_callback},
};

static JNINativeMethod gLnbMethods[] = {
    {"nativeLnbCallback", "(II[B)V",  (void *)dtvkit_lnb_native_callback},
};

static bool register_droidlogic_dtvkit_tuner(JNIEnv *env)
{
    jclass tunerClazz;
    FIND_CLASS(tunerClazz, TUNER_CLASS);

    if (NULL == tunerClazz) {
        ALOGE("Native registration unable to find tuner class '%s'\n", tunerClazz);
        return false;
    }

    if (JNI_OK != env->RegisterNatives(tunerClazz, gTunerMethods, NELEM(gTunerMethods))) {
        ALOGE("RegisterNatives tuner method failed for %s\n", TUNER_CLASS);
        return false;
    }

    jclass filtClazz;
    FIND_CLASS(filtClazz, FILTER_CLASS);

    if (NULL == filtClazz) {
        ALOGE("Native registration unable to find filter class '%s'\n", filtClazz);
        return false;
    }

    if (JNI_OK != env->RegisterNatives(filtClazz, gFilterMethods, NELEM(gFilterMethods))) {
        ALOGE("RegisterNatives filter method failed for %s\n", FILTER_CLASS);
        return false;
    }

    jclass lnbClass;
    FIND_CLASS(lnbClass, LNB_CLASS);

    if (NULL == lnbClass) {
        ALOGE("Native registration unable to find lnb class '%s'\n", lnbClass);
        return false;
    }

    if (JNI_OK != env->RegisterNatives(lnbClass, gLnbMethods, NELEM(gLnbMethods))) {
        ALOGE("RegisterNatives filter method failed for %s\n", LNB_CLASS);
        return false;
    }
    return true;
}

jint JNI_OnLoad(JavaVM* vm, void* /* reserved */)
{
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed\n");
        return result;
    }
    assert(env != NULL);
    gJavaVM = vm;
    if (true != register_droidlogic_dtvkit_tuner(env)) {
        ALOGE("ERROR: Tuner native registration failed\n");
        return result;
    }
    return JNI_VERSION_1_4;
}
/****************For DTV Static**********************/
/*
AMTuner::AMTuner(JNIEnv *env, jobject thiz) {
    mTuner = env->NewWeakGlobalRef(thiz);
}

AMTuner::~AMTuner() {
    bool needsDetach = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&needsDetach);
    if (NULL != env) {
        env->DeleteWeakGlobalRef(mTuner);
    }
    mTuner = NULL;
    if (needsDetach) {
        detachJNIEnv();
    }
}

jobject AMTuner::getTuner() {
    return NULL;
}
*/
