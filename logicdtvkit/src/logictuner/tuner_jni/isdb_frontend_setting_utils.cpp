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
#define LOG_TAG "isdb-frontend-setting-utils"

#include "JNI_tuner.h"
#include "include/isdb_frontend_setting_utils.h"


#define ISDBT_FRONTEND_SETTING_CLASS "android/media/tv/tuner/frontend/IsdbtFrontendSettings"
#define ISDBT_LAYER_SETTING_CLASS "android/media/tv/tuner/frontend/IsdbtFrontendSettings$IsdbtLayerSettings"
#define ISDBT_FRONTEND_SETTING_SIGNATURE "(JIIII[Landroid/media/tv/tuner/frontend/IsdbtFrontendSettings$IsdbtLayerSettings;I)V"

jobject isdb_utils_getIsdbtFrontendSettingsObject(JNIEnv *env, Isdbt_Frontend_Settings isdbtFrontendSettings) {
    ALOGD("start:%s", __FUNCTION__);

    if (NULL == env) {
        ALOGE("%s : test fail, env is null", __FUNCTION__);
        return NULL;
    }
    long frequency = isdbtFrontendSettings.frequency;
    int bandwidth = isdbtFrontendSettings.bandwidth;
    int mode = isdbtFrontendSettings.mode;
    int guardInterval = isdbtFrontendSettings.guardInterval;
    int serviceAreaId = isdbtFrontendSettings.serviceAreaId;
    int partialReceptionFlag = isdbtFrontendSettings.partialReceptionFlag;

    jclass isdbtSettingClass = env->FindClass(ISDBT_FRONTEND_SETTING_CLASS);
    jobject isdbtSettingObject;

    if (ANDROID_SDK_T <= Am_tuner_getAndroidVersion()) {
        int size = 1; // for extend
        jclass layerSettingClass = env->FindClass(ISDBT_LAYER_SETTING_CLASS);
        jobjectArray layerSettingArray = env->NewObjectArray(size, layerSettingClass, nullptr);
        for (int i = 0; i < size; i++) {
            int modulation = isdbtFrontendSettings.layerSettings[i].modulation;
            int timeInterleaveMode = isdbtFrontendSettings.layerSettings[i].timeInterleaveMode;
            int codeRate = isdbtFrontendSettings.layerSettings[i].codeRate;
            int numOfSegments = isdbtFrontendSettings.layerSettings[i].numOfSegments;
            jmethodID layerSettingInit = env->GetMethodID(layerSettingClass, "<init>", "(IIII)V");
            jobject layerSettingObject = env->NewObject(layerSettingClass, layerSettingInit, modulation,
                                                        timeInterleaveMode, codeRate, numOfSegments);
            env->SetObjectArrayElement(layerSettingArray, i, layerSettingObject);
        }
        jmethodID isdbtSettingInit = env->GetMethodID(isdbtSettingClass, "<init>", ISDBT_FRONTEND_SETTING_SIGNATURE);
        isdbtSettingObject = env->NewObject(isdbtSettingClass, isdbtSettingInit, (jlong) frequency, bandwidth, mode,
                                           guardInterval, serviceAreaId, layerSettingArray, partialReceptionFlag);
    } else {
        int modulation = isdbtFrontendSettings.layerSettings[0].modulation;
        int codeRate = isdbtFrontendSettings.layerSettings[0].codeRate;
        jmethodID isdbtSettingInit = env->GetMethodID(isdbtSettingClass, "<init>", "(IIIIIII)V");
        isdbtSettingObject = env->NewObject(isdbtSettingClass, isdbtSettingInit, (jint) frequency, modulation, bandwidth,
                                           mode, codeRate, guardInterval, serviceAreaId);
    }

    if (NULL == isdbtSettingObject) {
        ALOGE("%s : test fail, isdbt frontend setting not create", __FUNCTION__);
    }

    ALOGD("end:%s", __FUNCTION__);
    return isdbtSettingObject;
}
