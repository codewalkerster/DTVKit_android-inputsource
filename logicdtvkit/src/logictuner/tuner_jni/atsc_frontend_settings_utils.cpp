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
#define LOG_TAG "atsc_frontend_jni"

#include "JNI_tuner.h"
#include "include/atsc_frontend_settings_utils.h"

#define ATSC_FRONTEND_SETTING_CLASS "android/media/tv/tuner/frontend/AtscFrontendSettings"

jobject atsc_utils_getAtscFrontendSettingsObject(JNIEnv *env,
                                                 Atsc_Frontend_Settings atscFrontendSettings) {

    jmethodID atscSettingInit;
    jobject atscSettingObject = NULL;
    jclass atscSettingClass = NULL;

    if (NULL == env) {
        ALOGD("%s : failed for env null", __FUNCTION__);
        return NULL;
    }

    atscSettingClass = env->FindClass(ATSC_FRONTEND_SETTING_CLASS);

    if (atscSettingClass == NULL) return NULL;

    atscSettingInit = env->GetMethodID(atscSettingClass, "<init>", "(JI)V");
    atscSettingObject = env->NewObject(atscSettingClass,
                                       atscSettingInit,
                                       (jlong)atscFrontendSettings.frequency,
                                       atscFrontendSettings.modulation);
    if (NULL == atscSettingObject) {
        ALOGD("%s : failed to create atsc frontend settings ", __FUNCTION__);
    } else {
        ALOGV("%s : created atsc frontend settings: %p", atscSettingObject);
    }

    return atscSettingObject;
}