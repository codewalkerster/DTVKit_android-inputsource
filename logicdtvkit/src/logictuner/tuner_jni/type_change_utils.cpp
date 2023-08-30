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
#define LOG_TAG "tuner-jni-type-change"

#include "JNI_tuner.h"
#include "include/type_change_utils.h"

bool TypeChangeUtils::getValue(JNIEnv *env, jobject obj, jfieldID fieldId, bool* value){
    //ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == obj)) {
       // ALOGE("input parameter error");
        return false;
    }

    jobject booleanObject = env->GetObjectField(obj, fieldId);
    jclass booleanClazz = getValueClass(env, booleanObject, BOOLEAN_CLASS);
    if (NULL == booleanClazz) {
        return false;
    }
    jmethodID booleanValueId = env->GetMethodID(booleanClazz, "booleanValue", "()Z");
    if (JNI_TRUE == env->CallBooleanMethod(booleanObject, booleanValueId)) {
        *value = true;
    } else {
        *value = false;
    }
    ALOGE("%s, value : %d", __FUNCTION__, *value);
    return true;
}

bool TypeChangeUtils::getValue(JNIEnv *env, jobject obj, jfieldID fieldId, int* value){
    //ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == obj)) {
       // ALOGE("input parameter error");
        return false;
    }

    jobject intObject = env->GetObjectField(obj, fieldId);
    jclass integerClazz = getValueClass(env, intObject, INTEGER_CLASS);
    if (NULL == integerClazz) {
        return false;
    }
    jmethodID intValueId = env->GetMethodID(integerClazz, "intValue", "()I");
    *value = env->CallIntMethod(intObject, intValueId);
    ALOGE("%s, value : %d", __FUNCTION__, *value);
    return true;
}

bool TypeChangeUtils::getValue(JNIEnv *env, jobject obj, jfieldID fieldId, long* value) {
    //ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == env) || (NULL == obj)) {
       // ALOGE("input parameter error");
        return false;
    }

    jobject longObject = env->GetObjectField(obj, fieldId);
    jclass longClazz = getValueClass(env, longObject, LONG_CLASS);
    if (NULL == longClazz) {
        return false;
    }
    jmethodID longValueId = env->GetMethodID(longClazz, "longValue", "()J");
    *value = env->CallLongMethod(longObject, longValueId);
    ALOGE("%s, value : %ld", __FUNCTION__, *value);
    return true;
}

bool TypeChangeUtils::getBaseValue(JNIEnv *env, jobject integerObj, int* value) {
    jclass integerClazz = getValueClass(env, integerObj, INTEGER_CLASS);
    if (NULL == integerClazz) {
        return false;
    }
    jmethodID intValueId = env->GetMethodID(integerClazz, "intValue", "()I");
    *value = env->CallIntMethod(integerObj, intValueId);
    ALOGE("End:%s, value : %d", __FUNCTION__, *value);
    return true;
}

jbyteArray TypeChangeUtils::getJNIArray(JNIEnv *env, char *data, int size){
    //ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == data) || (0 == size)) {
       // ALOGE("input parameter error");
        return NULL;
    }

    ALOGD("size :%d", size);
    jbyteArray byteArray = env->NewByteArray(size);
    if (NULL == byteArray) {
        ALOGD("%s : fail, allocate byte array error", __FUNCTION__);
        return NULL;
    }
    env->SetByteArrayRegion(byteArray, 0, size, (jbyte*)(data));
    return byteArray;
}

jintArray TypeChangeUtils::getJNIArray(JNIEnv *env, int *data, int size){
    //ALOGE("Start:%s", __FUNCTION__);

    if ((NULL == data) || (0 == size)) {
        //ALOGE("input parameter error");
        return NULL;
    }

    ALOGD("size :%d", size);
    jintArray intArray = env->NewIntArray(size);
    if (NULL == intArray) {
        ALOGD("%s : fail, allocate int array error", __FUNCTION__);
        return NULL;
    }
    env->SetIntArrayRegion(intArray, 0, size, (jint*)(data));
    return intArray;
}

bool TypeChangeUtils::getIntVector(JNIEnv *env, jintArray intArray, std::vector<int> *int_vector) {
    if ((NULL == env) || (NULL == intArray) || (NULL == int_vector)) {
        //ALOGE("input parameter error");
        return false;
    }
    int length = env->GetArrayLength(intArray);
    ALOGE("getIntVector length :%d", length);
    jboolean isCopy;
    jint *value = env->GetIntArrayElements(intArray, &isCopy);
    if (NULL == value) {
        ALOGE("GetIntArrayElements error");
        return false;
    }
    for (int i = 0; i < length; i++) {
        int_vector->push_back(value[i]);
    }
    return true;
}

bool TypeChangeUtils::getCharVector(JNIEnv *env, jbyteArray byteArray, std::vector<char> *char_vector) {
    if ((NULL == env) || (NULL == byteArray) || (NULL == char_vector)) {
        //ALOGE("input parameter error");
        return false;
    }
    int length = env->GetArrayLength(byteArray);
    ALOGE("getCharVector length :%d", length );
    jboolean isCopy;
    jbyte *value = env->GetByteArrayElements(byteArray, &isCopy);
    if (NULL == value) {
        ALOGE("GetByteArrayElements error");
        return false;
    }
    for (int i = 0; i < length; i++) {
        char_vector->push_back(value[i]);
    }
    return true;
}

jclass TypeChangeUtils::getValueClass(JNIEnv *env, jobject valueObject, const char *name){
   if ((NULL == env) || (NULL == valueObject)) {
        //ALOGE("%s:input parameter error", __FUNCTION__);
        return NULL;
    }

    if (env->IsSameObject(valueObject, nullptr)) {
        ALOGE("%s:value Object is nullptr", __FUNCTION__);
        return NULL;
    }
    jclass valueClazz = env->FindClass(name);
    if (JNI_TRUE != env->IsInstanceOf(valueObject, valueClazz)) {
        ALOGD("value object not check value class");
        return NULL;
    }
    return valueClazz;
}