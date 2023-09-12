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
#define LOG_TAG "tuner-jni-filter-utils"

#include "JNI_tuner.h"
#include "include/filter_utils.h"
#include "type_change_utils.h"

/*
static jbyteArray getByteArrayData(JNIEnv *env, char *data) {
    ALOGE("Start:%s", __FUNCTION__);
    int dataLength = 0;
    if (NULL == data) {
        ALOGE("input parameter error");
        return NULL;
    }

    dataLength = strlen(data);
    ALOGD("dataLength :%d", dataLength);
    jbyteArray byteArray = env->NewByteArray(dataLength);
    if (NULL == byteArray) {
        ALOGD("%s : fail, allocate byte array error", __FUNCTION__);
        return NULL;
    }
    env->SetByteArrayRegion(byteArray, 0, dataLength, (jbyte*)(data));
    return byteArray;
}
*/
jobject filter_utils_getSectionTsFilterConfiguration(JNIEnv *env, TS_Filter_Configuration tsFilterConfiguration) {
    ALOGE("Start:%s", __FUNCTION__);

    if (NULL == env) {
        ALOGD("%s : fail, env is null", __FUNCTION__);
        return NULL;
    }
    //get section object
    jclass sectionSettingClass = env->FindClass(SECTION_SETTING_CLASS);
    jmethodID sectionSettingClassInit;

    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        sectionSettingClassInit = env->GetMethodID(sectionSettingClass, "<init>", "(IZZZI[B[B[B)V");
    } else {
        sectionSettingClassInit = env->GetMethodID(sectionSettingClass, "<init>", "(IZZZ[B[B[B)V");
    }

    jbyteArray filterArray = TypeChangeUtils::getJNIArray(env, tsFilterConfiguration.setting.section_setting.filter, tsFilterConfiguration.setting.section_setting.filter_length);
    jbyteArray maskArray = TypeChangeUtils::getJNIArray(env, tsFilterConfiguration.setting.section_setting.mask, tsFilterConfiguration.setting.section_setting.mask_length);
    jbyteArray modeArray = TypeChangeUtils::getJNIArray(env, tsFilterConfiguration.setting.section_setting.mode, tsFilterConfiguration.setting.section_setting.mode_length);
    if ((NULL == filterArray) || (NULL == maskArray) || (NULL == modeArray)) {
        ALOGD("%s : section setting array allocate error", __FUNCTION__);
        return NULL;
    }

    jobject sectionSettingObject;
    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        sectionSettingObject = env->NewObject(sectionSettingClass, sectionSettingClassInit, tsFilterConfiguration.type,
                                                  tsFilterConfiguration.setting.section_setting.crc_enable,
                                                  tsFilterConfiguration.setting.section_setting.is_repeat,
                                                  tsFilterConfiguration.setting.section_setting.is_raw, 0,
                                                  filterArray, maskArray, modeArray);
    } else {
        sectionSettingObject = env->NewObject(sectionSettingClass, sectionSettingClassInit, tsFilterConfiguration.type,
                                                  tsFilterConfiguration.setting.section_setting.crc_enable,
                                                  tsFilterConfiguration.setting.section_setting.is_repeat,
                                                  tsFilterConfiguration.setting.section_setting.is_raw,
                                                  filterArray, maskArray, modeArray);
    }
    //release local ref
    env->DeleteLocalRef(filterArray);
    env->DeleteLocalRef(maskArray);
    env->DeleteLocalRef(modeArray);
    if (NULL == sectionSettingObject) {
        ALOGD("%s : section setting allocate error", __FUNCTION__);
        return NULL;
    }

    //get ts filter configuration object
    jclass tsFilterConfigurationClass = env->FindClass(TS_FILTER_CONFIGURATION_CLASS);
    jmethodID tsFilterConfigurationInit = env->GetMethodID(tsFilterConfigurationClass, "<init>", "(Landroid/media/tv/tuner/filter/Settings;I)V");
    jobject tsFilterConfigurationObject = env->NewObject(tsFilterConfigurationClass, tsFilterConfigurationInit, sectionSettingObject, tsFilterConfiguration.pid);

    ALOGE("End:%s, %p", __FUNCTION__, tsFilterConfigurationObject);
    return tsFilterConfigurationObject;
}

bool filter_utils_getSectionEvent(JNIEnv *env, jobject sectionEventObject, Section_Event *pSectionEvent) {
    //ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == sectionEventObject) || (NULL == pSectionEvent)) {
        ALOGE("input parameter error");
        return false;
    }

    jclass sectionEventClass = env->FindClass(SECTION_EVENT_CLASS);
    if (JNI_TRUE != env->IsInstanceOf(sectionEventObject, sectionEventClass)) {
        ALOGD("%s : is not SectionEvent, doesn't handle", __FUNCTION__);
        return false;
    }
    //2.get data length
    jfieldID fTableId = env->GetFieldID(sectionEventClass, "mTableId", "I");
    jfieldID fVersion = env->GetFieldID(sectionEventClass, "mVersion", "I");
    jfieldID fSectionNum = env->GetFieldID(sectionEventClass, "mSectionNum", "I");
    jfieldID fDataLength;
    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        fDataLength = env->GetFieldID(sectionEventClass, "mDataLength", "J");
    } else {
        fDataLength = env->GetFieldID(sectionEventClass, "mDataLength", "I");
    }
    pSectionEvent->tableId = env->GetIntField(sectionEventObject, fTableId);
    pSectionEvent->version = env->GetIntField(sectionEventObject, fVersion);
    pSectionEvent->sectionNum = env->GetIntField(sectionEventObject, fSectionNum);
    //pSectionEvent->dataLength = env->GetIntField(sectionEventObject, fDataLength);
    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        pSectionEvent->dataLength = (int)env->GetLongField(sectionEventObject, fDataLength);
    } else {
        pSectionEvent->dataLength = env->GetIntField(sectionEventObject, fDataLength);
    }

    //ALOGE("End:%s", __FUNCTION__);
    return true;
}

jobject filter_utils_getAVTsFilterConfiguration(JNIEnv *env, TS_Filter_Configuration tsFilterConfiguration) {
    ALOGE("Start:%s", __FUNCTION__);

    if (NULL == env) {
        ALOGD("%s : fail, env is null", __FUNCTION__);
        return NULL;
    }
    //get AVSetting object
    jclass avSettingClass = env->FindClass(AV_SETTING_CLASS);
    jmethodID avSettingClassInit;
    jobject avSettingObject;
    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        avSettingClassInit = env->GetMethodID(avSettingClass, "<init>", "(IZZIIZ)V");
        avSettingObject = env->NewObject(avSettingClass, avSettingClassInit, tsFilterConfiguration.type,
                                                      tsFilterConfiguration.setting.av_setting.is_audio,
                                                      tsFilterConfiguration.setting.av_setting.is_passthrough,
                                                      tsFilterConfiguration.setting.av_setting.audio_strem_type,
                                                      tsFilterConfiguration.setting.av_setting.video_strem_type,
                                                      tsFilterConfiguration.setting.av_setting.is_use_secure_memory);
    } else {
        avSettingClassInit = env->GetMethodID(avSettingClass, "<init>", "(IZZ)V");
        avSettingObject = env->NewObject(avSettingClass, avSettingClassInit, tsFilterConfiguration.type,
                                                      tsFilterConfiguration.setting.av_setting.is_audio,
                                                      tsFilterConfiguration.setting.av_setting.is_passthrough);
    }

    if (NULL == avSettingObject) {
        ALOGD("%s : av setting allocate error", __FUNCTION__);
        return NULL;
    }

    //get av ts filter configuration object
    jclass tsFilterConfigurationClass = env->FindClass(TS_FILTER_CONFIGURATION_CLASS);
    jmethodID tsFilterConfigurationInit = env->GetMethodID(tsFilterConfigurationClass, "<init>", "(Landroid/media/tv/tuner/filter/Settings;I)V");
    jobject tsFilterConfigurationObject = env->NewObject(tsFilterConfigurationClass, tsFilterConfigurationInit, avSettingObject, tsFilterConfiguration.pid);

    ALOGE("End:%s, %p", __FUNCTION__, tsFilterConfigurationObject);
    return tsFilterConfigurationObject;
}
