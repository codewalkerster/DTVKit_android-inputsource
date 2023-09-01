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
#define LOG_TAG "dtvkit-jni"

//#include <binder/ProcessState.h>
//#include <binder/IServiceManager.h>
//#include <android_runtime/android_view_Surface.h>
//#include <android/native_window.h>
////#include <gui/Surface.h>
//#include <gui/IGraphicBufferProducer.h>
//#include <ui/GraphicBuffer.h>
//#include <gralloc_usage_ext.h>
//#include <hardware/gralloc1.h>
//#include "amlogic/am_gralloc_ext.h"
#include <dlfcn.h>
#include <sys/prctl.h>
#include <pthread.h>
#include <utils/Looper.h>
#include <memory>

#include <android/hidl/memory/1.0/IMemory.h>
#include <hidlmemory/mapping.h>
#include "org_droidlogic_dtvkit_DtvkitGlueClient.h"

using namespace android;
using ::android::hidl::memory::V1_0::IMemory;
using ::android::sp;
static JavaVM   *gJavaVM = NULL;
sp<DTVKitClientJni> mpDtvkitJni;
static jmethodID notifySubtitleCallback;
static jmethodID notifyDvbCallback;
static jmethodID notifyPidFilterData;

static uint8_t*  gJBuffer = NULL; //java direct buffer
static int       gJBufSize = 0;
static jboolean  gJNIReady = false;
static jboolean  gPidListenerEnabled = false;

// handle subtitle message
static sp<Looper> gLooper;
static sp<SubtitleLooperThread> gLooperThread;


static jobject DtvkitObject;
//sp<Surface> mSurface;
//sp<NativeHandle> mSourceHandle;

static jmethodID notifySubtitleCallbackEx;
static jmethodID notifySubtitleCbCtlEx;
static jmethodID notifyCCSubtitleCallbackEx;
static jmethodID notifyMixVideoEventCallback;
static jmethodID notifyServerStateCallback;

sp<amlogic::SubtitleServerClient> mSubContext;
static jboolean g_bSubStatus = false;
static int subtitle_ca_flag = 0;
static int teletext_region_id = 0;

#define DTVKIT_SUBTITLE_ADD_OFFSET 4
#define SUBTITLE_DEMUX_SOURCE 4
#define SUBTITLE_SCTE27_SOURCE 1

#define SUBTITLE_SUB_TYPE_DVB    1
#define SUBTITLE_SUB_TYPE_TTX_SUB 2
#define SUBTITLE_SUB_TYPE_SCTE   3
#define SUBTITLE_SUB_TYPE_TTX    4

#define START_SUB_TYPE_CLOSED_CAPTION -5
#define CALLBACK_SUB_TYPE_CLOSED_CAPTION 10
#define SUBTITLE_SUB_TYPE_ARIB 16
#define TT_EVENT_INDEXPAGE 14
#define TT_EVENT_GO_TO_PAGE 30
#define TT_EVENT_GO_TO_SUBTITLE 31
#define TT_EVENT_SET_REGION_ID 32

#define FMQ_QUEUE_SIZE 188

static void postSubtitleDataEx(int type, int width, int height, int dst_x, int dst_y, int dst_width, int dst_height, const char *data, int size);
static void clearSubtitleDataEx();
static void setAFDToDVBCore(int dec_id, uint8_t afd);
static void postMixVideoEvent(int event);
static void setSubtitleOn(int pid, uint16_t onid, uint16_t tsid, int type, int magazine, int page, int demuxId);
static void setSubtitleOff();
static void setSubtitlePause();
static void setSubtitleResume();
static void notitySubtitleTeletextEvent(int eventType);
static void subtitleTune(int type, int param1, int param2, int param3);

void SubtitleDataListenerImpl::onSubtitleEvent(const char *data, int size, int parserType,
            int x, int y, int width, int height,
            int videoWidth, int videoHeight, int cmd, int objectSegmentId) {
    if (cmd) {
        postSubtitleDataEx(parserType, width, height, x, y, videoWidth, videoHeight, data, size);
    } else {
        clearSubtitleDataEx();
    }
}

void SubtitleDataListenerImpl::onSubtitleAfdEvent(int dec_id, int afd) {
    setAFDToDVBCore(dec_id, afd);

}

void SubtitleDataListenerImpl::onMixVideoEvent(int val) {
    postMixVideoEvent(val);
}

void SubtitleDataListenerImpl::onServerDied() {
    ALOGE("subtitle client server died");
}

static JNIEnv* getJniEnv(bool *needDetach) {
    int ret = -1;
    JNIEnv *env = NULL;
    ret = gJavaVM->GetEnv((void **) &env, JNI_VERSION_1_4);
    if (ret < 0) {
        ret = gJavaVM->AttachCurrentThread(&env, NULL);
        if (ret < 0) {
            ALOGE("Can't attach thread ret = %d", ret);
            return NULL;
        }
        *needDetach = true;
    }
    return env;
}

static void DetachJniEnv() {
    int result = gJavaVM->DetachCurrentThread();
    if (result != JNI_OK) {
        ALOGE("thread detach failed: %#x", result);
    }
}


static void setSubtitleStatus(bool bOn) {
    g_bSubStatus = bOn;
}

static jboolean getSubtitleStatus() {
    return g_bSubStatus;
}

static void postSubtitleData(int width, int height, int dst_x, int dst_y, int dst_width, int dst_height, uint8_t* data)
{
    ALOGD("callback sendSubtitleData data = %p", data);
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        if (width != 0 || height != 0) {
            //ScopedLocalRef<jbyteArray> array (env, env->NewByteArray(width * height * 4));
            jintArray array = env->NewIntArray(width * height);
            env->SetIntArrayRegion(array, 0, width * height, (jint*)data);
            env->CallVoidMethod(DtvkitObject, notifySubtitleCallback, width, height, dst_x, dst_y, dst_width, dst_height, array);
            env->DeleteLocalRef(array);
        } else {
            env->CallVoidMethod(DtvkitObject, notifySubtitleCallback, width, height, dst_x, dst_y, dst_width, dst_height, NULL);
        }
    }
    if (attached) {
        DetachJniEnv();
    }
}

static void postPidFilterData(int length, uint8_t* data)
{
    //ALOGD("postPidFilterData length = %d", length);
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        if (length <= gJBufSize)
        {
            memset(gJBuffer, 0x0, gJBufSize);
            memcpy(gJBuffer, data, length);
            //ALOGI("Get Pid Filter len %d",length);
        }
        else
        {
            ALOGE("Callback overflow len %d",length);
        }

        env->CallVoidMethod(DtvkitObject, notifyPidFilterData);
    }
    if (attached) {
        DetachJniEnv();
    }
}


static void postSubtitleDataEx(int type, int width, int height, int dst_x, int dst_y,
    int dst_width, int dst_height,const char *data, int size)
{
    //ALOGD("callback sendSubtitleData data = %p", data);
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);
    bool bSubOn = getSubtitleStatus();
    if (!bSubOn) return;

    if (env != NULL) {
        if (type == CALLBACK_SUB_TYPE_CLOSED_CAPTION) {
            jstring jccData = env->NewStringUTF((char *)data);
            env->CallVoidMethod(DtvkitObject, notifyCCSubtitleCallbackEx, true, jccData);
            env->DeleteLocalRef(jccData);
        } else {
            if (width != 0 || height != 0) {
                //ScopedLocalRef<jintArray> array (env, env->NewIntArray(width * height));
                jintArray array = env->NewIntArray(width * height);
                env->SetIntArrayRegion(array, 0, width * height, (jint*)data);
                //env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, type, 0, 0, dst_x, dst_y, 9999, 0, NULL);
                env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, type, width, height, dst_x, dst_y,
                   dst_width, dst_height, array);
                //env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, type, 0, 0, dst_x, dst_y, 0, 0, NULL);
                //env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, type, 0, 0, dst_x, dst_y, 0, 9999, NULL);
                env->DeleteLocalRef(array);
            } else if (size > 0 && type == SUBTITLE_SUB_TYPE_ARIB) {
                jintArray array = env->NewIntArray(size);
                env->SetIntArrayRegion(array, 0, size, (jint*)data);
                env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, type, width, height, dst_x, dst_y,
                   dst_width, dst_height, array);
                env->DeleteLocalRef(array);
            } else {
                env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, type, width, height, dst_x, dst_y,
                   dst_width, dst_height, NULL);
                }
        }
    }
    if (attached) {
        DetachJniEnv();
    }
}


static void clearSubtitleDataEx()
{
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, 0, 0, 0, 0, 0, 9999, 0, NULL);
        //env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, 0, 0, 0, 0, 0, 0, 9999, NULL);
    }
    if (attached) {
        DetachJniEnv();
    }
}

static void clearCCSubtitleData()
{
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);
    if (env != NULL) {
        env->CallVoidMethod(DtvkitObject, notifyCCSubtitleCallbackEx, false, NULL);
    }

    if (attached) {
        DetachJniEnv();
    }

}

static void setAFDToDVBCore(int dec_id, uint8_t afd) {
    ALOGV("AFD value = 0x%xd from subtitleServer", afd);
    if (mpDtvkitJni != NULL)
        mpDtvkitJni->setAfd(dec_id, afd);
}

static void postMixVideoEvent(int event) {
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        env->CallVoidMethod(DtvkitObject, notifyMixVideoEventCallback, event);
    }
    if (attached) {
        DetachJniEnv();
    }

}

static void postDvbParam(const std::string& resource, const std::string json, int id) {
    // ALOGD("-callback postDvbParam resource:%s (%d), json:%s", resource.c_str(), id, json.c_str());
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        //ALOGD("-callback event get ok");
        //ScopedLocalRef<jstring> jResource((env), (env)->NewStringUTF(resource.c_str()));
        //ScopedLocalRef<jstring> jJson((env),  (env)->NewStringUTF(json.c_str()));
        jstring jResource = env->NewStringUTF(resource.c_str());
        jstring jJson     = env->NewStringUTF(json.c_str());
        env->CallVoidMethod(DtvkitObject, notifyDvbCallback, jResource, jJson, id);
        env->DeleteLocalRef(jResource);
        env->DeleteLocalRef(jJson);
    }
    if (attached) {
        DetachJniEnv();
    }

}
pthread_once_t once = PTHREAD_ONCE_INIT;
pthread_t pid_thread;

void  DTVKitClientJni::once_run(void)
{
    if (NULL == mInstance)
         mInstance = new DTVKitClientJni();
}

static uint8_t data[FMQ_QUEUE_SIZE] = {0};
void*  DTVKitClientJni::pid_run(void *arg)
{
    ALOGD("Enter pid_run");
    prctl(PR_SET_NAME, "pid_run");
    if (NULL == mInstance) {
        ALOGE("mInstance null");
        return NULL;
    }

    MessageQueueSync* fmq = mInstance->getQueue();
    if (NULL == fmq) {
        ALOGE("get fmq null");
        return NULL;
    }

    std::atomic<uint32_t>* fwAddr = fmq->getEventFlagWord();
    if (NULL == fwAddr) {
        ALOGE("get fwAddr null");
        return NULL;
    }

    android::hardware::EventFlag* efGroup = nullptr;
    android::hardware::EventFlag::createEventFlag(fwAddr, &efGroup);
    assert(nullptr != efGroup);

    ALOGD("fmq %p, fwAddr %p, efGroup %p\n", fmq, fwAddr, efGroup);

    while (true) {
            if (!gJNIReady) {
                ALOGE("gJNIReady not ready!");
                sleep(1);
                continue;
            }

            if (!gPidListenerEnabled) {
                sleep(1);
                continue;
            }

            /*size_t numMessagesMax = fmq->getQuantumCount();
            size_t size = fmq->getQuantumSize();
            ALOGD("numMessages %lu,size %lu\n", numMessagesMax, size);*/

            size_t availToRead = fmq->availableToRead();
            //ALOGD("availToRead %d\n", availToRead);
            if (availToRead > 0) {
                bool result = fmq->readBlocking(&data[0],
                                 FMQ_QUEUE_SIZE,
                                 static_cast<uint32_t>(kFmqNotFull),
                                 static_cast<uint32_t>(kFmqNotEmpty),
                                 5000000000 /* timeOutNanos */,
                                 efGroup);
                if (!result) {
                    ALOGE("fmq read failed!");
                    continue;
                }

                postPidFilterData(FMQ_QUEUE_SIZE, data);
           } else {
                usleep(100*1000);//100ms
           }
    }

}


DTVKitClientJni::DTVKitClientJni()  {
    mDkSession = DTVKitHidlClient::connect(DTVKitHidlClient::CONNECT_TYPE_HAL);
    mDkSession->setListener(this);
}

DTVKitClientJni *DTVKitClientJni::mInstance = NULL;
DTVKitClientJni *DTVKitClientJni::GetInstance() {
    pthread_once(&once, once_run);
    pthread_create(&pid_thread, NULL, pid_run, NULL);
    return mInstance;
}

DTVKitClientJni::~DTVKitClientJni()  {

}

std::string DTVKitClientJni::request(const std::string& resource, const std::string& json) {
    return mDkSession->request(resource, json);
}

void DTVKitClientJni::setAfd(int player, int afd) {
    mDkSession->setAfd(player, afd);
}

void DTVKitClientJni::setSubtitleFlag(int flag) {
    mDkSession->setSubtitleFlag(flag);
}

MessageQueueSync* DTVKitClientJni::getQueue() {
    ALOGD("Enter getQueue");
    return mDkSession->getQueue();
}

void SubtitleMessageHandler::setParcelData(parcel_t parcel) {
    this->parcel = parcel;
}

void SubtitleMessageHandler::setParam(int param) {
    this->param = param;
}

static void getSubtitleListenerImpl() {
    if (mSubContext == nullptr) {
        mSubContext = new SubtitleServerClient(false, new SubtitleDataListenerImpl(), OpenType::TYPE_APPSDK);
    }
}

void SubtitleMessageHandler::handleMessage(const Message & message) {
    if (message.what != SUBTITLE_CTL_ATTACH)
    {
        if (mSubContext == nullptr)
        {
            ALOGW("mSubContext is null.");
            return;
        }
    }
    switch (message.what) {
        case SUBTITLE_START:
            {
                ALOGD("funname =%d, is_dvb_subtitle = %d, pid = %d,  subt_type = %d, cpage = %d, apage = %d", parcel.funname,
                parcel.is_dvb_subt, parcel.pid, parcel.subt_type, parcel.subt.cpage, parcel.subt.apage);
                if (parcel.is_dvb_subt || parcel.pid == 0) { //pid =0 default cc
                    setSubtitleOn(parcel.pid, 0, 0, parcel.subt_type, parcel.subt.cpage, parcel.subt.apage, parcel.demux_num);
                } else {
                    ALOGD("parcel.ttxt.magazine = %d, parcel.ttxt.page = %d", parcel.ttxt.magazine, parcel.ttxt.page);
                    uint16_t onid = 0;
                    uint16_t tsid = 0;
                    if (parcel.bodyInt.size() >= 2) {
                        onid = parcel.bodyInt[0];
                        tsid = parcel.bodyInt[1];
                    }
                    setSubtitleOn(parcel.pid, onid, tsid, parcel.subt_type, parcel.ttxt.magazine, parcel.ttxt.page,
                    parcel.demux_num);
                }
            }
            break;
        case SUBTITLE_STOP:
            setSubtitleOff();
            break;
        case SUBTITLE_PAUSE:
            setSubtitlePause();
            break;
        case SUBTITLE_RESUME:
            setSubtitleResume();
            break;
        case TELETEXT_EVENT:
            ALOGD("event type = %d", parcel.event_type);
            notitySubtitleTeletextEvent(parcel.event_type);
            break;
        case SUBTITLE_TUNE:
           subtitleTune(parcel.bodyInt[0], parcel.bodyInt[1],
                        parcel.bodyInt[2], parcel.bodyInt[3]);
            break;
        case SUBTITLE_CTL_ATTACH:
            ALOGI("SubtitleServiceCtl:attachSubtitleCtl.");
            getSubtitleListenerImpl();
            if (mpDtvkitJni != NULL)
                mpDtvkitJni->setSubtitleFlag(param);
            break;
        case SUBTITLE_CTL_DETTACH:
            ALOGI("SubtitleServiceCtl:detachSubtitleCtl.");
            if (mpDtvkitJni != NULL)
                 mpDtvkitJni->setSubtitleFlag(0);
            break;
        case SUBTITLE_CTL_DESTROY:
            ALOGI("SubtitleServiceCtl:destroySubtitleCtl.");
            mSubContext = nullptr;
            break;
        case SUBTITLE_CTL_OPEN_USERDATA:
            mSubContext->userDataOpen();
            break;
        case SUBTITLE_CTL_CLOSE_USERDATA:
            mSubContext->userDataClose();
            break;
        case SUBTITLE_CTL_SET_REGION_ID:
            ALOGD("set region Id:%d", param);
            teletext_region_id = param;
            mSubContext->ttControl(TT_EVENT_SET_REGION_ID, -1, -1, param, -1);
            break;
        case SUBTITLE_CTL_RESET_FOR_SEEK:
            mSubContext->resetForSeek();
            break;
        default:
            ALOGD("get funname = %d", parcel.funname);
            break;
    }
}

void DTVKitClientJni::notify(const parcel_t &parcel) {
    AutoMutex _l(mLock);

    ALOGD("notify msgType = %d  this:%p", parcel.msgType, this);
    if (!gJNIReady) {
        ALOGE("notify gJNIReady false");
        return;
    }
    if (parcel.msgType == DTVKIT_DRAW) {
        datablock_t datablock;
        datablock.width      = parcel.bodyInt[0];
        datablock.height     = parcel.bodyInt[1];
        datablock.dst_x      = parcel.bodyInt[2];
        datablock.dst_y      = parcel.bodyInt[3];
        datablock.dst_width  = parcel.bodyInt[4];
        datablock.dst_height = parcel.bodyInt[5];

        if (datablock.width != 0 || datablock.height != 0) {
            sp<IMemory> memory = mapMemory(parcel.mem);
            if (memory == nullptr) {
                ALOGE("[%s] memory map is null", __FUNCTION__);
                return;
            }
            uint8_t *data = static_cast<uint8_t*>(static_cast<void*>(memory->getPointer()));
            memory->read();
            memory->commit();
            //int size = memory->getSize();

            postSubtitleData(datablock.width, datablock.height, datablock.dst_x, datablock.dst_y,
            datablock.dst_width, datablock.dst_height, data);
        } else {
            postSubtitleData(datablock.width, datablock.height, datablock.dst_x, datablock.dst_y,
            datablock.dst_width, datablock.dst_height, NULL);
        }
    }

    if (parcel.msgType == REQUEST) {
        dvb_param_t dvb_param;
        dvb_param.resource = parcel.bodyString[0];
        dvb_param.json     = parcel.bodyString[1];
        dvb_param.id       = parcel.bodyInt[0];
        postDvbParam(dvb_param.resource, dvb_param.json, dvb_param.id);
    }

    if (parcel.msgType == SUB_SERVER_DRAW) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        subtitleHandler->setParcelData(parcel);
        if (gLooper.get() != nullptr) {
            ALOGD("SUB_SERVER_DRAW funname:%d", parcel.funname);
            gLooper->sendMessage(subtitleHandler, Message(parcel.funname));
        } else {
            ALOGE("looper error %d", parcel.funname);
        }
    }
}

void DTVKitClientJni::notifyServerState(int diedOrReconnected) {
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        env->CallVoidMethod(DtvkitObject, notifyServerStateCallback, diedOrReconnected);
    }
    if (attached) {
        DetachJniEnv();
    }
}

static void connectDtvkit(JNIEnv *env, jclass clazz __unused, jobject obj, jobject buffer)
{
    ALOGI("ref dtvkit");
    mpDtvkitJni  =  DTVKitClientJni::GetInstance();
    DtvkitObject = env->NewGlobalRef(obj);
    gJBuffer = (uint8_t*)env->GetDirectBufferAddress(buffer);
    gJBufSize = env->GetDirectBufferCapacity(buffer);
    ALOGE("native buffer info %p,length %d\n", gJBuffer, gJBufSize);
    gLooper = new Looper(true);
    gLooperThread = new SubtitleLooperThread(gLooper);
    gLooperThread->run("subtitleLooper");
    gJNIReady = true;
}

static void disConnectDtvkit(JNIEnv *env, jclass clazz __unused)
{
    ALOGI("disconnect dtvkit");
    gLooperThread->requestExit();
    gLooper.clear();
    env->DeleteGlobalRef(DtvkitObject);
}

static jstring request(JNIEnv *env, jclass clazz __unused, jstring jResource, jstring jJson) {
    const char *resource = env->GetStringUTFChars(jResource, nullptr);
    const char *json = env->GetStringUTFChars(jJson, nullptr);
    if (mpDtvkitJni == nullptr) {
        ALOGE("dtvkitJni is null");
        mpDtvkitJni  =  DTVKitClientJni::GetInstance();
    }
    std::string result   = mpDtvkitJni->request(resource, json);
    env->ReleaseStringUTFChars(jResource, resource);
    env->ReleaseStringUTFChars(jJson, json);
    return env->NewStringUTF(result.c_str());
}

static void setSubtitleOff()
{
    if (mSubContext != nullptr)
    {
        ALOGD("SubtitleServiceCtl:setSubtitleOff to subtitle client.");
        setSubtitleStatus(false);
        mSubContext->close();
    }
    clearSubtitleDataEx();
    clearCCSubtitleData();
}

static void setSubtitleOn(int pid, uint16_t onid, uint16_t tsid, int type, int magazine, int page, int demuxId)
{
    if (mSubContext != nullptr) {
        setSubtitleOff();
        if (type == START_SUB_TYPE_CLOSED_CAPTION) {
            clearCCSubtitleData();
        }
    }
    ALOGD("SubtitleServiceCtl:setSubtitleOn with.pid=(%d,%u,%u), type=%d,magazine=%d, page=%d, demuxId = %d.",
        pid, onid, tsid, type, magazine, page, demuxId);
    setSubtitleStatus(true);
    if (type == SUBTITLE_SUB_TYPE_TTX || type == SUBTITLE_SUB_TYPE_TTX_SUB) {
        mSubContext->setSubType(SUBTITLE_SUB_TYPE_TTX_SUB + DTVKIT_SUBTITLE_ADD_OFFSET);
    } else {
        mSubContext->setSubType(type + DTVKIT_SUBTITLE_ADD_OFFSET);
    }
    if (type == START_SUB_TYPE_CLOSED_CAPTION) {
        int ccId = pid;
        bool isAtv = (demuxId == -1);
        if (!isAtv && ccId < 9) ccId = 9;
        int sourceFlag = isAtv ? 1 : 0;
        mSubContext->selectCcChannel(pid | (sourceFlag << 8));
    } else {
        mSubContext->setSubPid(pid, onid, tsid);
    }
    int iotType = SUBTITLE_DEMUX_SOURCE;
    //if (type == SUBTITLE_SUB_TYPE_SCTE) {
    //    iotType = SUBTITLE_SCTE27_SOURCE;
    //}
    if (demuxId != 0) {
        iotType = (demuxId << 16 | iotType);
    }
    mSubContext->setSecureLevel(subtitle_ca_flag);
    if (mSubContext->open("", iotType))
    {
        if ((type == SUBTITLE_SUB_TYPE_TTX)) {
            mSubContext->ttControl(TT_EVENT_GO_TO_PAGE, magazine, page, teletext_region_id, 0);
        } else if (type == SUBTITLE_SUB_TYPE_TTX_SUB) {
            mSubContext->ttControl(TT_EVENT_GO_TO_SUBTITLE, magazine, page, 0, 0);
        } else if (type == SUBTITLE_SUB_TYPE_DVB) {
            if (magazine != 0 || page != 0) {
                mSubContext->setCompositionPageId(magazine);
                mSubContext->setAncillaryPageId(page);
            }
        }
    }
    subtitle_ca_flag = 0;

}

static void notitySubtitleTeletextEvent(int eventType)
{
    ALOGD("notitySubtitleTeletextEvent event:%d", eventType);

    if (mSubContext != nullptr)
    {
        mSubContext->ttControl(eventType, -1, -1, -1, -1); //now subtitleserver according to event to control
    }
}

static void setSubtitlePause()
{
   bool attached = false;
    if (mSubContext != nullptr)
    {
        ALOGD("SubtitleServiceCtl:setSubtitlePause to subtitle client.");
        JNIEnv *env = getJniEnv(&attached);
        if (env != NULL) {
            env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, 0, 0, 0, 0, 0, 9999, 0, NULL);
            env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, 0, 0, 0, 0, 0, 0, 9999, NULL);
            //when play the pvr file and turn on the subtitle, then seek and switch subtitle track,
            //the dvb core don't deliver the resume.And it cause the subtitle hide.
            //env->CallVoidMethod(DtvkitObject, notifySubtitleCbCtlEx, 1);
        }

        if (attached) {
            DetachJniEnv();
        }
    }
}

static void setSubtitleResume()
{
   bool attached = false;
    if (mSubContext != nullptr)
    {
        ALOGD("SubtitleServiceCtl:setSubtitleResume to subtitle client.");
        JNIEnv *env = getJniEnv(&attached);
        if (env != NULL) {
            env->CallVoidMethod(DtvkitObject, notifySubtitleCbCtlEx, 0);
        }

        if (attached) {
            DetachJniEnv();
        }
    }
}

static void subtitleTune(int type, int param1, int param2, int param3)
{
   ALOGD("SubtitleServiceCtl: subtitleTune(type:%d, params:%d,%d,%d)", type, param1, param2, param3);
   if (mSubContext != nullptr)
   {
      if (type == 2)
         subtitle_ca_flag = param1;
      else
         mSubContext->setPipId(type + 1, param1);
   }
}

static void attachSubtitleCtl(JNIEnv *env, jclass clazz __unused, jint flag)
{
    if (gLooper.get() != nullptr) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        subtitleHandler->setParam(flag);
        gLooper->sendMessage(subtitleHandler, Message(SUBTITLE_CTL_ATTACH));
    }
}

static void detachSubtitleCtl(JNIEnv *env, jclass clazz __unused)
{
    if (gLooper.get() != nullptr) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        gLooper->sendMessage(subtitleHandler, Message(SUBTITLE_CTL_DETTACH));
    }
}

static void destroySubtitleCtl(JNIEnv *env, jclass clazz __unused)
{
    if (gLooper.get() != nullptr) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        gLooper->sendMessage(subtitleHandler, Message(SUBTITLE_CTL_DESTROY));
    }
}

static bool getIsdbtSupport(JNIEnv *env, jclass clazz __unused)
{
#ifdef SUPPORT_ISDBT
    return true;
#else
    return false;
#endif
}

static void nativeUnCrypt(JNIEnv *env, jclass clazz, jstring src, jstring dest) {
    const char *FONT_VENDOR_LIB = "/vendor/lib/libvendorfont.so";
    const char *FONT_PRODUCT_LIB = "/product/lib/libvendorfont.so";

    // TODO: maybe we need some smart method to get the lib.
    void *handle = dlopen(FONT_PRODUCT_LIB, RTLD_NOW);
    if (handle == nullptr) {
        handle = dlopen(FONT_VENDOR_LIB, RTLD_NOW);
    }

    if (handle == nullptr) {
        ALOGE(" nativeUnCrypt error! cannot open uncrypto lib");
        return;
    }

    typedef void (*fnFontRelease)(const char*, const char*);
    fnFontRelease fn = (fnFontRelease)dlsym(handle, "vendor_font_release");
    if (fn == nullptr) {
        ALOGE(" nativeUnCrypt error! cannot locate symbol vendor_font_release in uncrypto lib");
        dlclose(handle);
        return;
    }

    const char *srcstr = (const char *)env->GetStringUTFChars(src, NULL);
    const char *deststr = (const char *)env->GetStringUTFChars(dest, NULL);

    fn(srcstr, deststr);
    dlclose(handle);

    (env)->ReleaseStringUTFChars(src, (const char *)srcstr);
    (env)->ReleaseStringUTFChars(dest, (const char *)deststr);
}

/*
static int updateNative(sp<ANativeWindow> nativeWin) {
    char* vaddr;
    int ret = 0;
    ANativeWindowBuffer* buf;

    if (nativeWin.get() == NULL) {
        return 0;
    }

    int err = nativeWin->dequeueBuffer_DEPRECATED(nativeWin.get(), &buf);

    if (err != 0) {
        ALOGE("dequeueBuffer failed: %s (%d)", strerror(-err), -err);
        return -1;
    }

    return nativeWin->queueBuffer_DEPRECATED(nativeWin.get(), buf);
}

static void SetSurface(JNIEnv *env, jclass thiz, jobject jsurface) {
    sp<IGraphicBufferProducer> new_st = NULL;

    if (jsurface) {
        sp<Surface> surface(android_view_Surface_getSurface(env, jsurface));

        if (surface != NULL) {
            new_st = surface->getIGraphicBufferProducer();

            if (new_st == NULL) {
                jniThrowException(env, "java/lang/IllegalArgumentException",
                                  "The surface does not have a binding SurfaceTexture!");
                return;
            }
        } else {
            jniThrowException(env, "java/lang/IllegalArgumentException",
                              "The surface has been released");
            return;
        }
    }

    sp<ANativeWindow> tmpWindow = NULL;

    if (new_st != NULL) {
        tmpWindow = new Surface(new_st);
        status_t err = native_window_api_connect(tmpWindow.get(),
                       NATIVE_WINDOW_API_MEDIA);
        ALOGI("set native window overlay");
        native_window_set_usage(tmpWindow.get(),
                                am_gralloc_get_video_overlay_producer_usage());
        //native_window_set_usage(tmpWindow.get(), GRALLOC_USAGE_HW_TEXTURE |
        //   GRALLOC_USAGE_EXTERNAL_DISP  | GRALLOC1_PRODUCER_USAGE_VIDEO_DECODER );
        native_window_set_buffers_format(tmpWindow.get(), WINDOW_FORMAT_RGBA_8888);

        updateNative(tmpWindow);
    }
}
*/
static void openUserData() {
    if (gLooper.get() != nullptr) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        gLooper->sendMessage(subtitleHandler, Message(SUBTITLE_CTL_OPEN_USERDATA));
    }
}

static void setRegionId(JNIEnv *env, jclass clazz __unused, jint regionId) {
    if (gLooper.get() != nullptr) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        subtitleHandler->setParam(regionId);
        gLooper->sendMessage(subtitleHandler, Message(SUBTITLE_CTL_SET_REGION_ID));
    }
}

static void closeUserData() {
    if (gLooper.get() != nullptr) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        gLooper->sendMessage(subtitleHandler, Message(SUBTITLE_CTL_CLOSE_USERDATA));
    }
}

static void resetForSeek() {
    if (gLooper.get() != nullptr) {
        sp<SubtitleMessageHandler> subtitleHandler = new SubtitleMessageHandler();
        gLooper->sendMessage(subtitleHandler, Message(SUBTITLE_CTL_RESET_FOR_SEEK));
    }
}

static void enablePidListener(JNIEnv *env, jclass clazz __unused, jboolean enable) {
    ALOGI("enablePidListener tid (%d), enable (%d)", gettid(), enable);
    if (gPidListenerEnabled) {
        return;
    }
    gPidListenerEnabled = enable;
    ALOGI("enablePidListener tid (%d), gPidListenerEnabled (%d)", gettid(), gPidListenerEnabled);
}

static JNINativeMethod gMethods[] = {
{
    "nativeConnectDtvkit", "(Lorg/droidlogic/dtvkit/DtvkitGlueClient;Ljava/nio/ByteBuffer;)V",
    (void *) connectDtvkit
},
{
    "nativeDisconnectDtvkit", "()V",
    (void *) disConnectDtvkit
},
/*
{
    "nativeSetSurface", "(Landroid/view/Surface;)V",
    (void *) SetSurface
},
*/
{
    "nativeRequest", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
    (void*) request
},
{
    "native_attachSubtitleCtl", "(I)V",
    (void*) attachSubtitleCtl
},
{
   "native_detachSubtitleCtl", "()V",
   (void*) detachSubtitleCtl
},
{
   "native_destroySubtitleCtl", "()V",
   (void*) destroySubtitleCtl
},
{
    "native_UnCrypt", "(Ljava/lang/String;Ljava/lang/String;)V",
    (void *)nativeUnCrypt
},
{
    "nativeIsdbtSupport", "()Z",
    (void*) getIsdbtSupport
},
{
   "native_openUserData", "()V",
   (void*) openUserData
},
{
  "native_closeUserData", "()V",
  (void*) closeUserData
},
{
  "native_nativeSubtitleSeekReset", "()V",
  (void*) resetForSeek
},
{
  "native_setRegionId", "(I)V",
  (void*) setRegionId
},
{
  "native_enablePidListener", "(Z)V",
  (void*) enablePidListener
},


};


#define FIND_CLASS(var, className) \
        var = env->FindClass(className); \
        LOG_FATAL_IF(! var, "Unable to find class " className);

#define GET_METHOD_ID(var, clazz, methodName, methodDescriptor) \
        var = env->GetMethodID(clazz, methodName, methodDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find method " methodName);

int register_org_droidlogic_dtvkit_DtvkitGlueClient(JNIEnv *env)
{
    static const char *const kClassPathName = "org/droidlogic/dtvkit/DtvkitGlueClient";
    jclass clazz;
    int rc;
    FIND_CLASS(clazz, kClassPathName);

    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'\n", kClassPathName);
        return -1;
    }

    rc = (env->RegisterNatives(clazz, gMethods, NELEM(gMethods)));
    if (rc < 0) {
        env->DeleteLocalRef(clazz);
        ALOGE("RegisterNatives failed for '%s' %d\n", kClassPathName, rc);
        return -1;
    }

    GET_METHOD_ID(notifyDvbCallback, clazz, "notifyDvbCallback", "(Ljava/lang/String;Ljava/lang/String;I)V");
    GET_METHOD_ID(notifySubtitleCallback, clazz, "notifySubtitleCallback", "(IIIIII[I)V");
    GET_METHOD_ID(notifySubtitleCallbackEx, clazz, "notifySubtitleCallbackEx", "(IIIIIII[I)V");
    GET_METHOD_ID(notifyPidFilterData, clazz, "notifyPidFilterData", "()V");
    GET_METHOD_ID(notifySubtitleCbCtlEx, clazz, "notifySubtitleCbCtlEx", "(I)V");
    GET_METHOD_ID(notifyCCSubtitleCallbackEx, clazz, "notifyCCSubtitleCallbackEx", "(ZLjava/lang/String;)V");
    GET_METHOD_ID(notifyMixVideoEventCallback, clazz, "notifyMixVideoEventCallback", "(I)V");
    GET_METHOD_ID(notifyServerStateCallback, clazz, "notifyServerStateCallback", "(I)V");
    return rc;
}

jint JNI_OnLoad(JavaVM *vm, void *reserved __unused)
{
    JNIEnv *env = NULL;
    jint result = -1;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK)
    {
        ALOGI("ERROR: GetEnv failed\n");
        goto bail;
    }
    assert(env != NULL);
    gJavaVM = vm;
    if (register_org_droidlogic_dtvkit_DtvkitGlueClient(env) < 0)
    {
        ALOGE("Can't register DtvkitGlueClient");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

bail:
    return result;
}


