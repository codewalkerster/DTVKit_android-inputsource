/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _ATV_FRONTEND_SETTING_UTILS_
#define _ATV_FRONTEND_SETTING_UTILS_
#include <jni.h>
#include <utils/Log.h>
#include <utils/RefBase.h>
#include <assert.h>
#include <string>

using namespace android;
using ::android::sp;
using ::android::RefBase;

#define ANALOG_FRONTEND_SETTING_CLASS "android/media/tv/tuner/frontend/AnalogFrontendSettings"

enum ANALOG_SIGNAL_TYPE {
    ANALOG_SIGNAL_TYPE_UNDEFINED = 0,
    ANALOG_SIGNAL_TYPE_AUTO      = 1 << 0,
    ANALOG_SIGNAL_TYPE_PAL       = 1 << 1,
    ANALOG_SIGNAL_TYPE_PAL_M     = 1 << 2,
    ANALOG_SIGNAL_TYPE_PAL_N     = 1 << 3,
    ANALOG_SIGNAL_TYPE_PAL_60    = 1 << 4,
    ANALOG_SIGNAL_TYPE_NTSC      = 1 << 5,
    ANALOG_SIGNAL_TYPE_NTSC_443  = 1 << 6,
    ANALOG_SIGNAL_TYPE_SECAM     = 1 << 7,
};

enum ANALOG_SIF {
    ANALOG_SIF_UNDEFINED  = 0,
    ANALOG_SIF_AUTO       = 1 << 0,
    ANALOG_SIF_BG         = 1 << 1,
    ANALOG_SIF_BG_A2      = 1 << 2,
    ANALOG_SIF_BG_NICAM   = 1 << 3,
    ANALOG_SIF_I          = 1 << 4,
    ANALOG_SIF_DK         = 1 << 5,
    ANALOG_SIF_DK1_A2     = 1 << 6,
    ANALOG_SIF_DK2_A2     = 1 << 7,
    ANALOG_SIF_DK3_A2     = 1 << 8,
    ANALOG_SIF_DK_NICAM   = 1 << 9,
    ANALOG_SIF_L          = 1 << 10,
    ANALOG_SIF_M          = 1 << 11,
    ANALOG_SIF_M_BTSC     = 1 << 12,
    ANALOG_SIF_M_A2       = 1 << 13,
    ANALOG_SIF_M_EIAJ     = 1 << 14,
    ANALOG_SIF_I_NICAM    = 1 << 15,
    ANALOG_SIF_L_NICAM    = 1 << 16,
    ANALOG_SIF_L_PRIME    = 1 << 17,
};

enum ANALOG_AFT_FLAG {
    ANALOG_AFT_FLAG_UNDEFINED  = 0,
    ANALOG_AFT_FLAG_TRUE       = 1,
    ANALOG_AFT_FLAG_FALSE      = 2,
};

typedef struct
{
    int atfFlag;           /*AFT_FLAG*/
    int signalType;        /*Analog signal type*/
    int sifStandard;       /*Analog Standard Interchange Format*/
    unsigned long frequency;
}Analog_Frontend_Settings;

/**
 * get Analog frontend settings jobject
 *
 * @param analogFrontendSettings java class AnalogFrontendSettings map structure Analog_Frontend_Settings.
 * @return analog frontend settings jobject or null.
 */

jobject analog_utils_getAnalogFrontendSettingsObject(JNIEnv *env, Analog_Frontend_Settings analogFrontendSettings);

#endif/*_ATV_FRONTEND_SETTING_UTILS_*/

