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
#define LOG_TAG "analog-frontend-setting-utils"

#include "JNI_tuner.h"
#include "include/analog_frontend_setting_utils.h"

jobject analog_utils_getAnalogFrontendSettingsObject(JNIEnv *env, Analog_Frontend_Settings analogFrontendSettings) {
    ALOGD("start:%s", __FUNCTION__);

    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return NULL;
    }

    jclass analogSettingClass = env->FindClass(ANALOG_FRONTEND_SETTING_CLASS);
    jmethodID analogSettingInit;
    jobject analogSettingObject;

    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        analogSettingInit = env->GetMethodID(analogSettingClass, "<init>", "(JIII)V");
        analogSettingObject = env->NewObject(analogSettingClass, analogSettingInit, (jlong)analogFrontendSettings.frequency, analogFrontendSettings.signalType,
                                                 analogFrontendSettings.sifStandard, analogFrontendSettings.atfFlag);
    } else {
        analogSettingInit = env->GetMethodID(analogSettingClass, "<init>", "(III)V");
        analogSettingObject = env->NewObject(analogSettingClass, analogSettingInit, (int)analogFrontendSettings.frequency,
                                                 analogFrontendSettings.signalType, analogFrontendSettings.sifStandard);
    }

    if (NULL == analogSettingObject) {
        ALOGD("%s : test fail, analog frontend setting not create", __FUNCTION__);
    }

    ALOGD("end:%s", __FUNCTION__);
    return analogSettingObject;
}
