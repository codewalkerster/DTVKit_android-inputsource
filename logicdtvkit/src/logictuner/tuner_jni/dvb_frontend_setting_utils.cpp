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
#define LOG_TAG "dvb-frontend-setting-utils"

#include "JNI_tuner.h"
#include "include/dvb_frontend_setting_utils.h"

jobject dvb_utils_getDvbtFrontendSettingsObject(JNIEnv *env, Dvbt_Frontend_Settings dvbtFrontendSettings) {
    ALOGD("start:%s", __FUNCTION__);

    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return NULL;
    }

    int frequency = dvbtFrontendSettings.frequency;
    int transmissionMode = dvbtFrontendSettings.transmissionMode;
    int bandwidth = dvbtFrontendSettings.bandwidth;
    int constellation = dvbtFrontendSettings.constellation;
    int hierarchy = dvbtFrontendSettings.hierarchy;
    int hpCodeRate = dvbtFrontendSettings.hpCodeRate;
    int lpCodeRate = dvbtFrontendSettings.lpCodeRate;
    int guardInterval = dvbtFrontendSettings.guardInterval;
    bool isHighPriority = dvbtFrontendSettings.isHighPriority;
    int standard = dvbtFrontendSettings.standard;
    bool isMiso = dvbtFrontendSettings.isMiso;
    int plpMode = dvbtFrontendSettings.plpMode;
    int plpId = dvbtFrontendSettings.plpId;
    int plpGroupId = dvbtFrontendSettings.plpGroupId;

    jclass dvbtSettingClass = env->FindClass(DVBT_FRONTEND_SETTING_CLASS);
    jmethodID dvbtSettingInit;
    jobject dvbtSettingObject;

    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        dvbtSettingInit = env->GetMethodID(dvbtSettingClass, "<init>", "(JIIIIIIIZIZIII)V");
        dvbtSettingObject = env->NewObject(dvbtSettingClass, dvbtSettingInit, (jlong)frequency, transmissionMode, bandwidth, constellation,
                                               hierarchy, hpCodeRate, lpCodeRate, guardInterval, isHighPriority, standard, isMiso,
                                               plpMode, plpId, plpGroupId);

    } else {
        dvbtSettingInit = env->GetMethodID(dvbtSettingClass, "<init>", "(IIIIIIIIZIZIII)V");
        dvbtSettingObject = env->NewObject(dvbtSettingClass, dvbtSettingInit, frequency, transmissionMode, bandwidth, constellation,
                                               hierarchy, hpCodeRate, lpCodeRate, guardInterval, isHighPriority, standard, isMiso,
                                               plpMode, plpId, plpGroupId);
    }

    if (NULL == dvbtSettingObject) {
        ALOGD("%s : test fail, dvbt frontend setting not create", __FUNCTION__);
    }

    ALOGD("end:%s", __FUNCTION__);
    return dvbtSettingObject;
}

jobject dvb_utils_getDvbcFrontendSettingsObject(JNIEnv *env, Dvbc_Frontend_Settings dvbcFrontendSettings) {

    ALOGD("start:%s", __FUNCTION__);

    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return NULL;
    }

    int frequency = dvbcFrontendSettings.frequency;
    int modulation = dvbcFrontendSettings.modulation;
    int symbolRate = dvbcFrontendSettings.symbolRate;
    int outerFec = dvbcFrontendSettings.outerFec;
    int annex = dvbcFrontendSettings.annex;
    int spectralInversion = dvbcFrontendSettings.spectralInversion;
    long innerFec = dvbcFrontendSettings.innerFec;

    jclass dvbcSettingClass = env->FindClass(DVBC_FRONTEND_SETTING_CLASS);
    jmethodID dvbcSettingInit;
    jobject dvbcSettingObject;

    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        dvbcSettingInit = env->GetMethodID(dvbcSettingClass, "<init>", "(JIJIIIIII)V");
        dvbcSettingObject = env->NewObject(dvbcSettingClass, dvbcSettingInit, (jlong)frequency, modulation, (jlong)innerFec, symbolRate,
                                               outerFec, annex, spectralInversion, 0, 0);

    } else {
        dvbcSettingInit = env->GetMethodID(dvbcSettingClass, "<init>", "(IIJIIII)V");
        dvbcSettingObject = env->NewObject(dvbcSettingClass, dvbcSettingInit, frequency, modulation, innerFec, symbolRate,
                                               outerFec, annex, spectralInversion);
    }

    if (NULL == dvbcSettingObject) {
        ALOGD("%s : test fail, dvbt frontend setting not create", __FUNCTION__);
    }

    ALOGD("end:%s", __FUNCTION__);
    return dvbcSettingObject;
}

jobject dvb_utils_getDvbsFrontendSettingsObject(JNIEnv *env, Dvbs_Frontend_Settings dvbsFrontendSettings) {

    ALOGD("start:%s", __FUNCTION__);

    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return NULL;
    }

    //prepare dvbs code rate object
    jclass dvbsCodeRateClass = env->FindClass("android/media/tv/tuner/frontend/DvbsCodeRate");
    jmethodID dvbsCodeRateInit = env->GetMethodID(dvbsCodeRateClass, "<init>", "(JZZI)V");
    jobject dvbsCodeRateObject = env->NewObject(dvbsCodeRateClass, dvbsCodeRateInit, (jlong)dvbsFrontendSettings.code_rate.fec,
                                            dvbsFrontendSettings.code_rate.isLinear, dvbsFrontendSettings.code_rate.isShortFrames,
                                            dvbsFrontendSettings.code_rate.bitsPer1000Symbol);
    //prepare dvbs setting object
    jclass dvbsSettingClass = env->FindClass(DVBS_FRONTEND_SETTING_CLASS);
    jmethodID dvbsSettingInit;
    jobject dvbsSettingObject = NULL;
    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        dvbsSettingInit = env->GetMethodID(dvbsSettingClass, "<init>", "(JILandroid/media/tv/tuner/frontend/DvbsCodeRate;IIIIIIIZ)V");
        dvbsSettingObject = env->NewObject(dvbsSettingClass, dvbsSettingInit,(jlong)dvbsFrontendSettings.frequency, dvbsFrontendSettings.modulation,
                                           dvbsCodeRateObject, dvbsFrontendSettings.symbol_rate, dvbsFrontendSettings.roll_off, dvbsFrontendSettings.pilot,
                                           dvbsFrontendSettings.input_streamId,dvbsFrontendSettings.standard, dvbsFrontendSettings.vcm,
                                           dvbsFrontendSettings.scan_type, dvbsFrontendSettings.isDiseqcRxMessage);
        if (nullptr != dvbsSettingObject) {
            jlong endFrequency = (jlong)dvbsFrontendSettings.end_frequency;
            if (endFrequency > 0) {
                jmethodID setEndFrequencyMethodID = env->GetMethodID(dvbsSettingClass, "setEndFrequencyLong", "(J)V");
                if (nullptr != setEndFrequencyMethodID) {
                    env->CallVoidMethod(dvbsSettingObject, setEndFrequencyMethodID, endFrequency);
                    ALOGD("%s : set end frequency : %lu", __FUNCTION__, dvbsFrontendSettings.end_frequency);
                }
            }
        }
    } else {
        dvbsSettingInit = env->GetMethodID(dvbsSettingClass, "<init>", "(IILandroid/media/tv/tuner/frontend/DvbsCodeRate;IIIIII)V");
        dvbsSettingObject = env->NewObject(dvbsSettingClass, dvbsSettingInit,dvbsFrontendSettings.frequency, dvbsFrontendSettings.modulation,
                                           dvbsCodeRateObject, dvbsFrontendSettings.symbol_rate, dvbsFrontendSettings.roll_off, dvbsFrontendSettings.pilot,
                                           dvbsFrontendSettings.input_streamId,dvbsFrontendSettings.standard, dvbsFrontendSettings.vcm);
    }

    if (NULL == dvbsSettingObject) {
        ALOGD("%s : test fail, dvbt frontend setting not create", __FUNCTION__);
    }

    ALOGD("end:%s", __FUNCTION__);
    return dvbsSettingObject;
}
