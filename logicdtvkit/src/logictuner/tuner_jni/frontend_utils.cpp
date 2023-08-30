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
#define LOG_TAG "tuner-jni-frontend-utils"

#include "JNI_tuner.h"
#include "frontend_utils.h"
#include "type_change_utils.h"

/*
static bool getIntValue(JNIEnv *env, jobject obj, jfieldID fieldId, int* value) {
    ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == obj)) {
        ALOGE("input parameter error");
        return false;
    }
    jobject intObject = env->GetObjectField(obj, fieldId);
    if (env->IsSameObject(intObject, nullptr)) {
        ALOGE("int Object is nullptr");
        return false;
    }
    jclass integerClazz = env->FindClass("java/lang/Integer");
    if (JNI_TRUE != env->IsInstanceOf(intObject, integerClazz)) {
        ALOGD("parser fail, object instance type is error");
        return false;
    }
    jmethodID intValueId = env->GetMethodID(integerClazz, "intValue", "()I");
    *value = env->CallIntMethod(intObject, intValueId);
    ALOGE("End:%s, value : %d", __FUNCTION__, *value);
    return true;
}

static bool getBoolValue(JNIEnv *env, jobject obj, jfieldID fieldId, bool *value) {
    ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == obj)) {
        ALOGE("input parameter error");
        return false;
    }

    jobject booleanObject = env->GetObjectField(obj, fieldId);
    if (env->IsSameObject(booleanObject, nullptr)) {
        ALOGE("boolean Object is nullptr");
        return false;
    }
    jclass booleanClazz = env->FindClass("java/lang/Boolean");
    if (JNI_TRUE != env->IsInstanceOf(booleanObject, booleanClazz)) {
        ALOGD("parser fail, object instance type is error");
        return false;
    }
    jmethodID booleanValueId = env->GetMethodID(booleanClazz, "booleanValue", "()Z");
    if (JNI_TRUE == env->CallBooleanMethod(booleanObject, booleanValueId)) {
        *value = true;
    } else {
        *value = false;
    }
    ALOGE("End:%s, value : %d", __FUNCTION__, *value);
    return true;
}

static bool getLongValue(JNIEnv *env, jobject obj, jfieldID fieldId, long *value) {
    ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == obj)) {
        ALOGE("input parameter error");
        return false;
    }

    jobject longObject = env->GetObjectField(obj, fieldId);
    if (env->IsSameObject(longObject, nullptr)) {
        ALOGE("long Object is nullptr");
        return false;
    }
    jclass longClazz = env->FindClass("java/lang/Long");
    if (JNI_TRUE != env->IsInstanceOf(longObject, longClazz)) {
        ALOGD("parser fail, object instance type is error");
        return false;
    }
    jmethodID longValueId = env->GetMethodID(longClazz, "longValue", "()J");
    *value = env->CallLongMethod(longObject, longValueId);
    ALOGE("End:%s, value : %ld", __FUNCTION__, *value);
    return true;
}
*/
bool frontend_utils_parseFrontendStatus(JNIEnv *env, jobject jFrontendStatus, Frontend_Status *pFrontendStatus)
{
    ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == jFrontendStatus) || (NULL == pFrontendStatus)) {
        ALOGE("input parameter error");
        return false;
    }

    jclass clazz = env->FindClass(FRONTEND_STATUS_CLASS);

    jfieldID demodLockedField = env->GetFieldID(clazz, "mIsDemodLocked", "Ljava/lang/Boolean;");
    TypeChangeUtils::getValue(env, jFrontendStatus, demodLockedField, &(pFrontendStatus->is_demod_locked));

    jfieldID snrField = env->GetFieldID(clazz, "mSnr", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, snrField, &(pFrontendStatus->snr));

    jfieldID berField = env->GetFieldID(clazz, "mBer", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, berField, &(pFrontendStatus->ber));

    jfieldID pefField = env->GetFieldID(clazz, "mPer", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, pefField, &(pFrontendStatus->per));

    jfieldID perberField = env->GetFieldID(clazz, "mPerBer", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, perberField, &(pFrontendStatus->per_ber));

    jfieldID qualityField = env->GetFieldID(clazz, "mSignalQuality", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, qualityField, &(pFrontendStatus->signal_quality));

    jfieldID strengthField = env->GetFieldID(clazz, "mSignalStrength", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, strengthField, &(pFrontendStatus->signal_strength));

    jfieldID symbolRataField = env->GetFieldID(clazz, "mSymbolRate", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, symbolRataField, &(pFrontendStatus->symbol_rate));

    jfieldID innerFecField = env->GetFieldID(clazz, "mInnerFec", "Ljava/lang/Long;");
    TypeChangeUtils::getValue(env, jFrontendStatus, innerFecField, &(pFrontendStatus->inner_fec));

    jfieldID modulationField = env->GetFieldID(clazz, "mModulation", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, modulationField, &(pFrontendStatus->modulation));

    jfieldID inversionField = env->GetFieldID(clazz, "mInversion", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, inversionField, &(pFrontendStatus->inversion));

    jfieldID lnbVoltageField = env->GetFieldID(clazz, "mLnbVoltage", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, lnbVoltageField, &(pFrontendStatus->lnb_voltage));

    jfieldID plpIdField = env->GetFieldID(clazz, "mPlpId", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, plpIdField, &(pFrontendStatus->plp_id));

    jfieldID isEwbsField = env->GetFieldID(clazz, "mIsEwbs", "Ljava/lang/Boolean;");
    TypeChangeUtils::getValue(env, jFrontendStatus, isEwbsField, &(pFrontendStatus->is_ewbs));

    jfieldID agcField = env->GetFieldID(clazz, "mAgc", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, agcField, &(pFrontendStatus->agc));

    jfieldID isLnaOnField = env->GetFieldID(clazz, "mIsLnaOn", "Ljava/lang/Boolean;");
    TypeChangeUtils::getValue(env, jFrontendStatus, isLnaOnField, &(pFrontendStatus->is_lna_on));

    jfieldID merField = env->GetFieldID(clazz, "mMer", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, merField, &(pFrontendStatus->mer));

    jfieldID freqOffsetField = env->GetFieldID(clazz, "mFreqOffset", "Ljava/lang/Long;");
    TypeChangeUtils::getValue(env, jFrontendStatus, freqOffsetField, &(pFrontendStatus->freq_offset));

    jfieldID hierarchyField = env->GetFieldID(clazz, "mHierarchy", "Ljava/lang/Integer;");
    TypeChangeUtils::getValue(env, jFrontendStatus, hierarchyField, &(pFrontendStatus->hierarchy));

    jfieldID isRfLockedField = env->GetFieldID(clazz, "mIsRfLocked", "Ljava/lang/Boolean;");
    TypeChangeUtils::getValue(env, jFrontendStatus, isRfLockedField, &(pFrontendStatus->is_rf_locked));

    if (ANDROID_SDK_R < Am_tuner_getAndroidVersion()) {
        jfieldID codeRatesField = env->GetFieldID(clazz, "mCodeRates", "[I");
        jintArray codeRatesArray = (jintArray)env->GetObjectField(jFrontendStatus, codeRatesField);
        if (NULL != codeRatesArray) {
            TypeChangeUtils::getIntVector(env, codeRatesArray, &pFrontendStatus->code_rate);
        }

        jfieldID dvbtCellIdsField = env->GetFieldID(clazz, "mDvbtCellIds", "[I");
        jintArray dvbtCellIdsArray = (jintArray)env->GetObjectField(jFrontendStatus, dvbtCellIdsField);
        if (NULL != dvbtCellIdsArray) {
            TypeChangeUtils::getIntVector(env, dvbtCellIdsArray, &pFrontendStatus->dvbt_cell_ids);
        }
    }

    //jfieldID field = env->GetFieldID(clazz, "mIsLayerErrors", "[Z");

    //jfieldID field = env->GetFieldID(clazz, "mPlpInfo", "[Landroid/media/tv/tuner/frontend/FrontendStatus$Atsc3PlpTuningInfo;");
    ALOGE("End:%s", __FUNCTION__);
    return true;
}

bool frontend_utils_parseScanCallbackMessage(JNIEnv *env, int scanMessageType, jobjectArray scanMessage, Scan_Callback_Message *pScanCallbackMessage) {
    ALOGE("Start:%s", __FUNCTION__);
    if ((NULL == env) || (NULL == pScanCallbackMessage)) {
        ALOGE("input parameter error");
        return false;
    }
    pScanCallbackMessage->scanMessageType = scanMessageType;
    if ((NULL != scanMessage)) {
        int length = env->GetArrayLength(scanMessage);
        int value;
        for (int i = 0; i <  length; i++) {
            jobject integerObj = env->GetObjectArrayElement(scanMessage, i);
            if (TypeChangeUtils::getBaseValue(env, integerObj, &value)) {
                pScanCallbackMessage->integer_value.push_back(value);
            }
        }
    }
    return true;
}

