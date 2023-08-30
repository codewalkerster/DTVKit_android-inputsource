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
#define LOG_TAG "tuner-jni-test"

//#include "android_runtime/AndroidRuntime.h"
#include <vector>
#include <map>
#include "JNI_tuner.h"
#include "include/dvb_frontend_setting_utils.h"
#include "frontend_utils.h"
#include "filter_utils.h"
#include "type_change_utils.h"

using namespace android;
using ::android::sp;
using ::android::RefBase;

#define INVALID_TUNER_ID 0xFFFF
#define INVALID_FD       -1
#define BUFFER_SIZE_SECTION_DEFAULT 1024 * 4L
#define BUFFER_SIZE_VIDEO_DEFAULT 1024 * 1024 * 4L
#define BUFFER_SIZE_AUDIO_DEFAULT 1024 * 1024 * 2L

bool tuner_test_openPatFilter();

static int gTunerClient = INVALID_TUNER_ID;
static std::vector<int> gFrontendList;
//static jobject gWeakRefPmtFilter = NULL;
static jobject gWeakRefPatFilter = NULL;
static jobject gWeakRefVideoFilter = NULL;
static jobject gWeakRefAudioFilter = NULL;
//static jni_asplayer_handle mHandle;
static auto testFailLeave = [](bool attached){if (attached) Am_tuner_detachJNIEnv();};

void tuner_test_checkTunerClient() {
    ALOGD("start:%s", __FUNCTION__);
    gTunerClient = Am_tuner_getTunerClientId();
    if (INVALID_TUNER_ID == gTunerClient) {
        ALOGD("%s : test fail", __FUNCTION__);
    }
    ALOGD("end:%s, gTunerClient:%d", __FUNCTION__, gTunerClient);
}

void tuner_test_getFrontendIds() {
    ALOGD("start:%s", __FUNCTION__);
    if (INVALID_TUNER_ID == gTunerClient) {
        ALOGD("%s : test fail", __FUNCTION__);
        return;
    }

    jobject list = Am_tuner_getFrontendIds(gTunerClient);
    if (NULL == list) {
        ALOGD("%s : test fail, list is null", __FUNCTION__);
        return;
    }

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }
    //1.Test jobject class
    jclass listClazz = env->FindClass("java/util/List");
    if (JNI_TRUE != env->IsInstanceOf(list, listClazz)) {
        ALOGD("%s : test fail, not list object", __FUNCTION__);
        testFailLeave(attached);
        return;
    }
    //2.show Frontend Id
    jmethodID listSize = env->GetMethodID(listClazz, "size", "()I");
    jmethodID listGet = env->GetMethodID(listClazz, "get", "(I)Ljava/lang/Object;");

    int frontendSize = env->CallIntMethod(list, listSize);
    ALOGD("%s : frontend size : %d", __FUNCTION__, frontendSize);
    for (int i=0; i < frontendSize; i++) {
        jobject id = env->CallObjectMethod(list, listGet, i);
        jclass integerClazz = env->FindClass("java/lang/Integer");
        if (JNI_TRUE != env->IsInstanceOf(id, integerClazz)) {
            ALOGD("%s : test fail, id is not integer", __FUNCTION__);
            testFailLeave(attached);
            return;
        }
        jmethodID intValue = env->GetMethodID(integerClazz, "intValue", "()I");
        int frontendId = env->CallIntMethod(id, intValue);
        ALOGD(":%s, frontendId : %d", __FUNCTION__, frontendId);
        gFrontendList.push_back(frontendId);
    }
    if (attached) {
        Am_tuner_detachJNIEnv();
    }
    ALOGD("end:%s", __FUNCTION__);
}

void tuner_test_getFrontendInfo() {
    ALOGD("start:%s", __FUNCTION__);
    if (INVALID_TUNER_ID == gTunerClient) {
        ALOGD("%s : test fail", __FUNCTION__);
        return;
    }

    jobject frontendInfo = Am_tuner_getFrontendInfo(gTunerClient);
    if (NULL == frontendInfo) {
        ALOGD("%s : test fail, FrontendInfo is null", __FUNCTION__);
        return;
    }

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }
    //1.Test jobject class
    jclass clazz = env->FindClass("android/media/tv/tuner/frontend/FrontendInfo");
    if (JNI_TRUE != env->IsInstanceOf(frontendInfo, clazz)) {
        ALOGD("%s : test fail, not FrontendInfo object", __FUNCTION__);
        testFailLeave(attached);
        return;
    }
    //2.show Frontend info
    jfieldID fId = env->GetFieldID(clazz, "mId", "I");
    jfieldID fType = env->GetFieldID(clazz, "mType", "I");

    int id = env->GetIntField(frontendInfo, fId);
    int type = env->GetIntField(frontendInfo, fType);

    ALOGD("%s : frontend id : %d, type : %d", __FUNCTION__, id, type);

    if (attached) {
        Am_tuner_detachJNIEnv();
    }
    ALOGD("end:%s", __FUNCTION__);
}

void tuner_test_getFrontendInfoById() {
    ALOGD("start:%s", __FUNCTION__);
    if (INVALID_TUNER_ID == gTunerClient) {
        ALOGD("%s : test fail", __FUNCTION__);
        return;
    }

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }

    int frontendSize = gFrontendList.size();
    ALOGD("%s : frontendSize : %d", __FUNCTION__, frontendSize);
    for (int frontendId : gFrontendList) {
        ALOGD("%s : frontendId : %d", __FUNCTION__, frontendId);
        jobject frontendInfo = Am_tuner_getFrontendInfoById(gTunerClient, frontendId);
        if (NULL == frontendInfo) {
            ALOGD("%s : test fail, FrontendInfo is null", __FUNCTION__);
            testFailLeave(attached);
            return;
        }
        //1.Test jobject class
        jclass clazz = env->FindClass("android/media/tv/tuner/frontend/FrontendInfo");
        if (JNI_TRUE != env->IsInstanceOf(frontendInfo, clazz)) {
            ALOGD("%s : test fail, not FrontendInfo object", __FUNCTION__);
            testFailLeave(attached);
            return;
        }

        //2.show Frontend info
        jfieldID fId = env->GetFieldID(clazz, "mId", "I");
        jfieldID fType = env->GetFieldID(clazz, "mType", "I");

        int id = env->GetIntField(frontendInfo, fId);
        int type = env->GetIntField(frontendInfo, fType);
        ALOGD("%s : frontend id : %d, type : %d", __FUNCTION__, id, type);

        env->DeleteWeakGlobalRef(frontendInfo);
    }
    if (attached) {
        Am_tuner_detachJNIEnv();
    }
    ALOGD("end:%s", __FUNCTION__);
}

void tuner_test_getFrontendStatus() {
    ALOGD("start:%s", __FUNCTION__);
    if (INVALID_TUNER_ID == gTunerClient) {
        ALOGD("%s : test fail", __FUNCTION__);
        return;
    }

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }

    int aStatusTypes[3] = {FRONTEND_STATUS_TYPE_DEMOD_LOCK, FRONTEND_STATUS_TYPE_SIGNAL_QUALITY, FRONTEND_STATUS_TYPE_SIGNAL_STRENGTH};
    jintArray jaStatusTypes = TypeChangeUtils::getJNIArray(env, aStatusTypes, 3);
    jobject frontendStatusObject = Am_tuner_getFrontendStatus(gTunerClient, jaStatusTypes);
    Frontend_Status stfrontendStatus;
    memset(&stfrontendStatus, 0, sizeof(Frontend_Status));
    frontend_utils_parseFrontendStatus(env, frontendStatusObject, &stfrontendStatus);

    ALOGD("Demod lock :%d, signal quality :%d, signal strength:%d", stfrontendStatus.is_demod_locked,
        stfrontendStatus.signal_quality, stfrontendStatus.signal_strength);

    if (attached) {
        Am_tuner_detachJNIEnv();
    }
    ALOGD("end:%s", __FUNCTION__);
}

void tuner_test_switchDVBCTune() {
    ALOGD("start:%s", __FUNCTION__);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }
    //1.Cancel dvbt tuning
    Am_tuner_closeFrontend(gTunerClient);
    //2.prepare dvbc frontendSettings
    Dvbc_Frontend_Settings dvbcFrontendSettings;
    memset(&dvbcFrontendSettings, 0, sizeof(Dvbc_Frontend_Settings));
    dvbcFrontendSettings.frequency = 530 * 1000 * 1000;
    dvbcFrontendSettings.modulation = DVBC_MODULATION_AUTO;
    dvbcFrontendSettings.symbolRate = 6900;

    jobject dvbcSettingObject = dvb_utils_getDvbcFrontendSettingsObject(env, dvbcFrontendSettings);
    if (NULL == dvbcSettingObject) {
        ALOGD("%s : test fail, dvbt frontend setting not create", __FUNCTION__);
        testFailLeave(attached);
        return;
    }
    Am_tuner_tune(gTunerClient, dvbcSettingObject);
    ALOGD("end:%s", __FUNCTION__);
}

void LnbCallback(jobject lnb, int eventType, jbyteArray diseqcMessage);

void tuner_test_switchDVBSTune() {
    ALOGD("start:%s", __FUNCTION__);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }
    //1.Cancel dvbt tuning
    Am_tuner_cancelScanning(gTunerClient);
    Am_tuner_closeFrontend(gTunerClient);
    //2.LNB test
    jobject lnb = Am_tuner_openLnb(gTunerClient, (long)LnbCallback);
    ALOGD("%s : Am_tuner_openLnb lnb: %p ", __FUNCTION__, lnb);
    lnb = Am_tuner_openLnbByName(gTunerClient, "test-lnb", (long)LnbCallback);
    ALOGD("%s : Am_tuner_openLnbByName lnb: %p ", __FUNCTION__, lnb);
    Am_lnb_setVoltage(lnb, DVBS_LNB_VOLTAGE_5V);
    Am_lnb_setTone(lnb, DVBS_LNB_TONE_CONTINUOUS);
    Am_lnb_setSatellitePosition(lnb, DVBS_LNB_POSITION_POSITION_A);
    std::vector<char> message = {0x11, 0x22, 0x33, 0x44};
    Am_lnb_sendDiseqcMessage(lnb, message);
    //2.prepare dvbs frontendSettings
    Dvbs_Frontend_Settings dvbsFrontendSettings;
    memset(&dvbsFrontendSettings, 0, sizeof(Dvbs_Frontend_Settings));
    dvbsFrontendSettings.frequency = 530 * 1000 * 1000;
    jobject dvbsSettingObject = dvb_utils_getDvbsFrontendSettingsObject(env, dvbsFrontendSettings);
    if (NULL == dvbsSettingObject) {
        ALOGD("%s : test fail, dvbs frontend setting not create", __FUNCTION__);
        testFailLeave(attached);
        return;
    }
    Am_tuner_tune(gTunerClient, dvbsSettingObject);
    ALOGD("end:%s", __FUNCTION__);
}

void videoFilterCallback(jobject filter, jobjectArray filterEventArray, int filterStatus) {
    ALOGD("start:%s", __FUNCTION__);
}

void audioFilterCallback(jobject filter, jobjectArray filterEventArray, int filterStatus) {
    ALOGD("start:%s", __FUNCTION__);
}

int getAVFilterId(bool isAudio, int pid, int vidoeStreamType, int audioStreamType) {
    ALOGD("start:%s, isAudio : %d, pid :%d, vidoeStreamType : %d, audioStreamType : %d ", __FUNCTION__, isAudio, pid, vidoeStreamType, audioStreamType);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return INVALID_VALUE;
    }
    jobject avFilter;
    if (true == isAudio) {
        Am_filter_callback vidoefilterCallback = videoFilterCallback;
        avFilter = Am_tuner_openFilter(gTunerClient, MAIN_TYPE_TS, SUBTYPE_AUDIO, BUFFER_SIZE_AUDIO_DEFAULT, (long)vidoefilterCallback);
    } else {
        Am_filter_callback audioCallback = videoFilterCallback;
        avFilter = Am_tuner_openFilter(gTunerClient, MAIN_TYPE_TS, SUBTYPE_VIDEO, BUFFER_SIZE_VIDEO_DEFAULT, (long)audioCallback);
    }

    if (NULL == avFilter) {
        ALOGD("%s : test fail, gWeakRefAudioFilter is null", __FUNCTION__);
        testFailLeave(attached);
        return INVALID_VALUE;
    }

    TS_Filter_Configuration tsFilterConfiguration;
    memset(&tsFilterConfiguration, 0, sizeof(TS_Filter_Configuration));
    tsFilterConfiguration.pid = pid;
    tsFilterConfiguration.type = MAIN_TYPE_TS;
    tsFilterConfiguration.setting.av_setting.is_passthrough = true;
    if (true == isAudio) {
        tsFilterConfiguration.setting.av_setting.is_audio = true;
        tsFilterConfiguration.setting.av_setting.audio_strem_type = true;
    } else {
        tsFilterConfiguration.setting.av_setting.is_audio = false;
        tsFilterConfiguration.setting.av_setting.video_strem_type = true;
    }
    jobject tsFilterConfigurationObject = filter_utils_getAVTsFilterConfiguration(env, tsFilterConfiguration);
    if (NULL == tsFilterConfigurationObject) {
        ALOGD("%s : test fail, get AVTsFilterConfigurationObject object error", __FUNCTION__);
        env->DeleteWeakGlobalRef(avFilter);
        testFailLeave(attached);
        return INVALID_VALUE;
    }
    int result = Am_filter_configure(avFilter, tsFilterConfigurationObject);
    ALOGD("%s : filter configure result : %d", __FUNCTION__, result);
    //3.start filter
    result = Am_filter_start(avFilter);
    ALOGD("%s : filter start result : %d", __FUNCTION__, result);
    //4.get filter Id
    int filterId = Am_filter_getId(avFilter);
    ALOGD("%s : filter Id : %d", __FUNCTION__, filterId);
    if (true == isAudio) {
        gWeakRefAudioFilter = avFilter;
    } else {
        gWeakRefVideoFilter = avFilter;
    }

    if (attached) {
        Am_tuner_detachJNIEnv();
    }
    ALOGD("end:%s", __FUNCTION__);
    return filterId;
}

int getAvSyncHwId() {
    ALOGD("start:%s", __FUNCTION__);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return INVALID_VALUE;
    }
    int avSyncHwId = Am_tuner_getAvSyncHwId(gTunerClient, gWeakRefVideoFilter != NULL ? gWeakRefVideoFilter : gWeakRefAudioFilter);
    ALOGD("end:%s, AV SyncHwId : %d", __FUNCTION__, avSyncHwId);
    return avSyncHwId;
}
/*
void tuner_test_AVPlay() {
    ALOGD("start:%s", __FUNCTION__);

    if (INVALID_TUNER_ID == gTunerClient) {
        ALOGD("%s : test fail gTunerClient is INVALID_TUNER_ID", __FUNCTION__);
        return;
    }

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }
    //Set JNI evn, need create by ASPlayer self?
    JniASPlayer_registerJNI(env);
    jobject tuner = Am_tuner_getTunerObjectByClientId(gTunerClient);
    if (NULL == tuner) {
        ALOGD("%s : test fail, get Tuner object is null", __FUNCTION__);
        testFailLeave(attached);
        return;
    }
    //ini asplayer params
    jni_asplayer_init_params params;
    memset(&params, 0, sizeof(jni_asplayer_init_params));
    params.playback_mode = JNI_ASPLAYER_PLAYBACK_MODE_PASSTHROUGH;
    params.source = JNI_ASPLAYER_TS_DEMOD;
    params.drmmode = JNI_ASPLAYER_TS_INPUT_BUFFER_TYPE_NORMAL;
    params.dmx_dev_id = 0;
    params.event_mask = 0;
    int result = JniASPlayer_create(params, (void *)tuner, &mHandle);
    if (JNI_ASPLAYER_OK != result) {
        ALOGD("%s : JniASPlayer_create fail, result ： %d", __FUNCTION__, result);
        testFailLeave(attached);
        return;
    }
    JniASPlayer_prepare(mHandle);
    //prepare AV Filter
    int videoFilterId = getAVFilterId(false, 101, VIDEO_STREAM_TYPE_AVC, AUDIO_STREAM_TYPE_UNDEFINED);
    int audioFilterId = getAVFilterId(true, 102, VIDEO_STREAM_TYPE_UNDEFINED, AUDIO_STREAM_TYPE_MPEG1);
    int avSyncHwId = getAvSyncHwId();
    ALOGD("%s : videoFilterId %d, audioFilterId : %d, avSyncHwId : %d", __FUNCTION__, videoFilterId, audioFilterId, avSyncHwId);
    //set video/audio params
    jni_asplayer_video_params videoParams;
    memset(&videoParams, 0, sizeof(jni_asplayer_video_params));
    videoParams.avSyncHwId = avSyncHwId;
    videoParams.filterId = videoFilterId;
    videoParams.mimeType = "video/avc";
    videoParams.height = 720;
    videoParams.width = 1920;
    videoParams.pid = 101;
    result = JniASPlayer_setVideoParams(mHandle, &videoParams);
    if (JNI_ASPLAYER_OK != result) {
        ALOGD("%s : JniASPlayer_setVideoParams fail, result ： %d", __FUNCTION__, result);
        testFailLeave(attached);
        return;
    }

    jni_asplayer_audio_params audioParams;
    memset(&audioParams, 0, sizeof(jni_asplayer_audio_params));
    audioParams.mimeType = "audio/mpeg";
    audioParams.sampleRate = 8000;
    audioParams.channelCount = 1;
    audioParams.pid = 102;
    audioParams.filterId = audioFilterId;
    audioParams.avSyncHwId = avSyncHwId;
    result = JniASPlayer_setAudioParams(mHandle, &audioParams);
    if (JNI_ASPLAYER_OK != result) {
        ALOGD("%s : JniASPlayer_setAudioParams fail, result ： %d", __FUNCTION__, result);
        testFailLeave(attached);
        return;
    }

    jobject surface = Am_tuner_getSurface();
    result = JniASPlayer_setSurface(mHandle, (void *)surface);
    if (JNI_ASPLAYER_OK != result) {
        ALOGD("%s : JniASPlayer_setSurface fail, result ： %d", __FUNCTION__, result);
        testFailLeave(attached);
        return;
    }
    JniASPlayer_startVideoDecoding(mHandle);
    if (JNI_ASPLAYER_OK != result) {
        ALOGD("%s : JniASPlayer_startVideoDecoding fail, result ： %d", __FUNCTION__, result);
        testFailLeave(attached);
        return;
    }
    JniASPlayer_startAudioDecoding(mHandle);
    if (JNI_ASPLAYER_OK != result) {
        ALOGD("%s : JniASPlayer_startAudioDecoding fail, result ： %d", __FUNCTION__, result);
        testFailLeave(attached);
        return;
    }

    testFailLeave(attached);
    ALOGD("end:%s", __FUNCTION__);
}
*/
void tuner_eventshow(int event) {
    ALOGD("%s : tuner event : %d", __FUNCTION__, event);
    //3.test get tuner status
    tuner_test_getFrontendStatus();
    //4.open filter
    //tuner_test_openPatFilter();
    //5.test cancel tuning
    //Am_tuner_cancelTuning(gTunerClient);
    //5.test clear tune event listener
    //Am_tuner_clearOnTuneEventListener(gTunerClient);
    //6.test switch dvb-c tune
    //tuner_test_switchDVBCTune();
    //7.0 JTSPlayerTest
    if (0 == event) {
//        tuner_test_AVPlay();
    } else {
        ALOGD("%s: tuner not lock", __FUNCTION__);
    }
}

void tuner_test_tune(){
    ALOGD("start:%s", __FUNCTION__);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }

//1.register tuner event listener
    long callbackContext = (long)tuner_eventshow;
    Am_tuner_setOnTuneEventListener(gTunerClient, callbackContext);

//2.prepare frontendSettings
    Dvbt_Frontend_Settings dvbtFrontendSettings;
    memset(&dvbtFrontendSettings, 0, sizeof(Dvbt_Frontend_Settings));
    dvbtFrontendSettings.frequency = 482 * 1000 * 1000;
    dvbtFrontendSettings.transmissionMode = DVBT_TRANSMISSION_MODE_AUTO;
    dvbtFrontendSettings.bandwidth = DVBT_BANDWIDTH_AUTO;
    dvbtFrontendSettings.standard = DVBT_STANDARD_T;

    jobject dvbtSettingObject = dvb_utils_getDvbtFrontendSettingsObject(env, dvbtFrontendSettings);
    if (NULL == dvbtSettingObject) {
        ALOGD("%s : test fail, dvbt frontend setting not create", __FUNCTION__);
        testFailLeave(attached);
        return;
    }
    Am_tuner_tune(gTunerClient, dvbtSettingObject);
    ALOGD("end:%s", __FUNCTION__);
}

void scanCallback(int scanCallbackMessageType, jobjectArray scanCallbackMessage) {
    ALOGD("start:%s, scanCallbackMessageType :%d ", __FUNCTION__, scanCallbackMessageType);
    if (NULL != scanCallbackMessage) {
        bool attached = false;
        JNIEnv *env = Am_tuner_getJNIEnv(&attached);
        if (NULL == env) {
            ALOGD("%s : test fail, env is null", __FUNCTION__);
            return;
        }
        Scan_Callback_Message scanMessage;
        memset(&scanMessage, 0, sizeof(Scan_Callback_Message));
        frontend_utils_parseScanCallbackMessage(env, scanCallbackMessageType, scanCallbackMessage, &scanMessage);
        ALOGD("Scan_Callback_Message scanMessageType : %d", scanMessage.scanMessageType);
        for (int value : scanMessage.integer_value) {
            ALOGD("Scan_Callback_Message message : %d", value);
        }
    }
    ALOGD("end:%s", __FUNCTION__);
}

void LnbCallback(jobject lnb, int eventType, jbyteArray diseqcMessage) {
    ALOGD("start:%s, lnb : %p, eventType :%d", __FUNCTION__, lnb, eventType);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }
    if (NULL != diseqcMessage) {
        std::vector<char> message;
        TypeChangeUtils::getCharVector(env, diseqcMessage, &message);
        for (int i = 0; i < message.size(); i++) {
            ALOGD("0X%x ", message[i]);
        }
    }
    ALOGD("end:%s", __FUNCTION__);
}

void tuner_test_scan() {
    ALOGD("start:%s", __FUNCTION__);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }

//1.register tuner event listener
    long callbackContext = (long)scanCallback;

//2.prepare frontendSettings
    Dvbt_Frontend_Settings dvbtFrontendSettings;
    memset(&dvbtFrontendSettings, 0, sizeof(Dvbt_Frontend_Settings));
    dvbtFrontendSettings.frequency = 482 * 1000 * 1000;
    dvbtFrontendSettings.transmissionMode = DVBT_TRANSMISSION_MODE_AUTO;
    dvbtFrontendSettings.bandwidth = DVBT_BANDWIDTH_AUTO;
    dvbtFrontendSettings.standard = DVBT_STANDARD_T;

    jobject dvbtSettingObject = dvb_utils_getDvbtFrontendSettingsObject(env, dvbtFrontendSettings);
    if (NULL == dvbtSettingObject) {
        ALOGD("%s : test fail, dvbt frontend setting not create", __FUNCTION__);
        testFailLeave(attached);
        return;
    }
    Am_tuner_scan(gTunerClient, dvbtSettingObject, SCAN_TYPE_AUTO, callbackContext);
    ALOGD("end:%s", __FUNCTION__);
}

void patFilterCallback(jobject filter, jobjectArray filterEventArray, int filterStatus) {
    ALOGD("start:%s", __FUNCTION__);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return;
    }
    //show filter status
    ALOGD("filterStatus : %d", filterStatus);

    //handle PAT Filter Event
    if (NULL != filterEventArray) {
        int eventSize = env->GetArrayLength(filterEventArray);
        for (int index = 0; index < eventSize; index++) {
            //1.check section event
            jobject filterEvent = env->GetObjectArrayElement(filterEventArray, index);
            Section_Event stSectionEvent;
            memset(&stSectionEvent, 0, sizeof(Section_Event));
            filter_utils_getSectionEvent(env, filterEvent, &stSectionEvent);
/*
            jclass sectionEventClass = env->FindClass("android/media/tv/tuner/filter/SectionEvent");
            if (JNI_TRUE != env->IsInstanceOf(filterEvent, sectionEventClass)) {
                ALOGD("%s : is not SectionEvent, doesn't handle", __FUNCTION__);
                continue;
            }
            //2.get data length
            jfieldID fTableId = env->GetFieldID(sectionEventClass, "mTableId", "I");
            jfieldID fVersion = env->GetFieldID(sectionEventClass, "mVersion", "I");
            jfieldID fSectionNum = env->GetFieldID(sectionEventClass, "mSectionNum", "I");
            jfieldID fDataLength = env->GetFieldID(sectionEventClass, "mDataLength", "I");

            int tableId = env->GetIntField(filterEvent, fTableId);
            int version = env->GetIntField(filterEvent, fVersion);
            int sectionNum = env->GetIntField(filterEvent, fSectionNum);
            int dataLength = env->GetIntField(filterEvent, fDataLength);
*/
            ALOGD("tableId :%d, version :%d, section num :%d, data length :%d", stSectionEvent.tableId, stSectionEvent.version,
                stSectionEvent.sectionNum, stSectionEvent.dataLength);

            //3.read section data
            char *buffer = new char[stSectionEvent.dataLength];
            int readSize = Am_filter_read(filter, buffer, 0, stSectionEvent.dataLength);
            ALOGD("read Pat data size :%d ", readSize);
            if (readSize > stSectionEvent.dataLength) {
                ALOGD("%s : test fail, read data too long than real data size", __FUNCTION__);
            } else {
                for (int i = 0; i < readSize; i++) {
                    ALOGD("0X%x ", buffer[i]);
                }
            }
            delete[] buffer;
        }
    }
    if (attached) {
        Am_tuner_detachJNIEnv();
    }
    ALOGD("end:%s", __FUNCTION__);
}

bool tuner_test_openPatFilter() {
    ALOGD("start:%s", __FUNCTION__);

    bool attached = false;
    JNIEnv *env = Am_tuner_getJNIEnv(&attached);
    if (NULL == env) {
        ALOGD("%s : test fail, env is null", __FUNCTION__);
        return false;
    }

    if (!env->IsSameObject(gWeakRefPatFilter, nullptr)) {
        ALOGD("%s : has request pat filter", __FUNCTION__);
        testFailLeave(attached);
        return false;
    }

    //1.request PAT filter
    Am_filter_callback filterCallback = patFilterCallback;
    gWeakRefPatFilter = Am_tuner_openFilter(gTunerClient, MAIN_TYPE_TS, SUBTYPE_SECTION, BUFFER_SIZE_SECTION_DEFAULT, (long)filterCallback);

    if (NULL == gWeakRefPatFilter) {
        ALOGD("%s : test fail, localFilter is null", __FUNCTION__);
        testFailLeave(attached);
        return false;
    }
    jclass clazz = env->FindClass(FILTER_CLASS);
    if (JNI_TRUE != env->IsInstanceOf(gWeakRefPatFilter, clazz)) {
        ALOGD("%s : test fail, not Filter object", __FUNCTION__);
        env->DeleteWeakGlobalRef(gWeakRefPatFilter);
        gWeakRefPatFilter = NULL;
        testFailLeave(attached);
        return false;
    }

    //2.config PAT filter
    char patTable[3] = {0, 0, 0};
    char mask[4] = {0xFF, 0, 0, 0};
    char mode[3] = {0, 0, 0};
    TS_Filter_Configuration tsFilterConfiguration;
    memset(&tsFilterConfiguration, 0, sizeof(TS_Filter_Configuration));
    tsFilterConfiguration.pid = 0;
    tsFilterConfiguration.type = MAIN_TYPE_TS;
    tsFilterConfiguration.setting.section_setting.crc_enable = false;
    tsFilterConfiguration.setting.section_setting.is_repeat = false;
    tsFilterConfiguration.setting.section_setting.is_raw = false;
    tsFilterConfiguration.setting.section_setting.filter = patTable;
    tsFilterConfiguration.setting.section_setting.filter_length = 3;
    tsFilterConfiguration.setting.section_setting.mask = mask;
    tsFilterConfiguration.setting.section_setting.mask_length = 4;
    tsFilterConfiguration.setting.section_setting.mode = mode;
    tsFilterConfiguration.setting.section_setting.mode_length = 3;
    jobject tsFilterConfigurationObject = filter_utils_getSectionTsFilterConfiguration(env, tsFilterConfiguration);
    if (NULL == tsFilterConfigurationObject) {
        ALOGD("%s : test fail, get tsFilterConfigurationObject object error", __FUNCTION__);
        env->DeleteWeakGlobalRef(gWeakRefPatFilter);
        gWeakRefPatFilter = NULL;
        testFailLeave(attached);
        return false;
    }

/*
    //2.config PAT filter
    jclass patSettingClass = env->FindClass("android/media/tv/tuner/filter/SectionSettingsWithSectionBits");
    jmethodID patSettingClassInit = env->GetMethodID(patSettingClass, "<init>", "(IZZZ[B[B[B)V");
    char patTable[3] = {0, 0, 0};
    char mask[4] = {0xFF, 0, 0, 0};
    char mode[3] = {0, 0, 0};
    jbyteArray filterArray = env->NewByteArray(3);
    jbyteArray maskArray = env->NewByteArray(4);
    jbyteArray modeArray = env->NewByteArray(3);
    if ((NULL == filterArray) || (NULL == maskArray) || (NULL == modeArray)) {
        ALOGD("%s : test fail, section setting init error", __FUNCTION__);
        env->DeleteWeakGlobalRef(gWeakRefPatFilter);
        gWeakRefPatFilter = NULL;
        testFailLeave(attached);
        return false;
    }
    env->SetByteArrayRegion(filterArray, 0, 3, (jbyte*)(&patTable[0]));
    env->SetByteArrayRegion(maskArray, 0, 4, (jbyte*)(&mask[0]));
    env->SetByteArrayRegion(modeArray, 0, 3, (jbyte*)(&mode[0]));
    jobject patSettingObject = env->NewObject(patSettingClass, patSettingClassInit, MAIN_TYPE_TS, false, false, false, filterArray, maskArray, modeArray);

    jclass tsFilterConfigurationClass = env->FindClass("android/media/tv/tuner/filter/TsFilterConfiguration");
    jmethodID tsFilterConfigurationInit = env->GetMethodID(tsFilterConfigurationClass, "<init>", "(Landroid/media/tv/tuner/filter/Settings;I)V");
    jobject tsFilterConfigurationObject = env->NewObject(tsFilterConfigurationClass, tsFilterConfigurationInit, patSettingObject, 0);
*/
    int result = Am_filter_configure(gWeakRefPatFilter, tsFilterConfigurationObject);
    ALOGD("%s : filter configure result : %d", __FUNCTION__, result);
    //3.start filter
    result = Am_filter_start(gWeakRefPatFilter);
    ALOGD("%s : filter start result : %d", __FUNCTION__, result);
    //4.get filter Id
    int filterId = Am_filter_getId(gWeakRefPatFilter);
    ALOGD("%s : filter Id : %d", __FUNCTION__, filterId);

    if (attached) {
        Am_tuner_detachJNIEnv();
    }
    ALOGD("end:%s", __FUNCTION__);
    return true;
}

void filter_test_setType() {
    ALOGD("start:%s", __FUNCTION__);

    if (NULL != gWeakRefPatFilter) {
        Am_filter_setType(gWeakRefPatFilter, MAIN_TYPE_TS, SUBTYPE_SECTION);
    }
    ALOGD("end:%s", __FUNCTION__);
}

void tunerTest() {
/*
    tuner_test_checkTunerClient();
    tuner_test_getFrontendIds();
    tuner_test_getFrontendInfo();
    tuner_test_getFrontendInfoById();
    tuner_test_tune();
    //tuner_test_scan();
*/
}
