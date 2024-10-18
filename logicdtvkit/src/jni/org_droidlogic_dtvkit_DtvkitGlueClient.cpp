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
#include <signal.h>
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

static jobject DtvkitObject;
//sp<Surface> mSurface;
//sp<NativeHandle> mSourceHandle;

static jmethodID notifySubtitleCallbackEx;
static jmethodID notifySubtitleCbCtlEx;
static jmethodID notifyCCSubtitleCallbackEx;
static jmethodID notifyMixVideoEventCallback;
static jmethodID notifyServerStateCallback;

#define CALLBACK_SUB_TYPE_CLOSED_CAPTION 10
#define SUBTITLE_SUB_TYPE_ARIB 12

#define FMQ_QUEUE_SIZE 188

static void postMixVideoEvent(int event);

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

static void postSubtitleData(int width, int height, int dst_x, int dst_y, int dst_width, int dst_height, uint8_t* data)
{
    //ALOGD("callback sendSubtitleData data = %p", data);

    bool attached = false;
    int dst_h = dst_height & 0xfffff;

    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        bool is_ext = (dst_height & 0x8000000) > 0;

        if (is_ext) {
            //from subtitle server
            bool show = (dst_height & 0x4000000) > 0;
            int drawType = ((dst_height >> 20) & 0x3f);
            if (drawType == CALLBACK_SUB_TYPE_CLOSED_CAPTION || drawType == SUBTITLE_SUB_TYPE_ARIB) {
                if (show && data != NULL) {
                    jstring jccData = env->NewStringUTF((char *)data);
                    env->CallVoidMethod(DtvkitObject, notifyCCSubtitleCallbackEx, true, jccData, drawType);
                    env->DeleteLocalRef(jccData);
                } else {
                    env->CallVoidMethod(DtvkitObject, notifyCCSubtitleCallbackEx, false, NULL, drawType);
                }
            } else {
                if (show && data != NULL && width != 0 && height != 0) {
                    jintArray array = env->NewIntArray(width * height);
                    env->SetIntArrayRegion(array, 0, width * height, (jint*) data);
                    env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, drawType, width, height,
                        dst_x, dst_y, dst_width, dst_h, array);
                    env->DeleteLocalRef(array);
                } else {
                    if (drawType == 0 && width == 0) {
                        postMixVideoEvent(height);
                    } else {
                        env->CallVoidMethod(DtvkitObject, notifySubtitleCallbackEx, drawType, 0, 0, 0, 0,
                            9999, dst_h, NULL);
                    }
                }
            }
        } else {
            //from dtvkit osd
            if (width != 0 && height != 0) {
                //ScopedLocalRef<jbyteArray> array (env, env->NewByteArray(width * height * 4));
                jintArray array = env->NewIntArray(width * height);
                    env->SetIntArrayRegion(array, 0, width * height, (jint*)data);
                env->CallVoidMethod(DtvkitObject, notifySubtitleCallback, width, height, dst_x, dst_y,
                    dst_width, dst_h, array);
                env->DeleteLocalRef(array);
            } else {
                env->CallVoidMethod(DtvkitObject, notifySubtitleCallback, width, height, dst_x, dst_y,
                    dst_width, dst_h, NULL);
            }
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
static uint8_t data[FMQ_QUEUE_SIZE] = {0};
DTVKitClientJni *DTVKitClientJni::mInstance = NULL;

DTVKitClientJni *DTVKitClientJni::GetInstance() {
    pthread_once(&once, once_run);
    pthread_create(&pid_thread, NULL, pid_run, NULL);
    return mInstance;
}

void  DTVKitClientJni::once_run(void)
{
    if (NULL == mInstance) {
#ifdef SUPPORT_TUNER_FRAMEWORK
        ALOGD("Support tuner framework");
        mInstance = new DTVKitTunerClientJni();
#else
        ALOGD("Support dtvkit server");
        mInstance = new DTVKitServerClientJni();
#endif
    }
}

#ifdef SUPPORT_TUNER_FRAMEWORK
void*  DTVKitClientJni::pid_run(void *arg) {
    return NULL;
}
#else
void*  DTVKitClientJni::pid_run(void *arg)
{
    ALOGD("Enter pid_run");
    prctl(PR_SET_NAME, "pid_run");
    if (NULL == mInstance) {
        ALOGE("mInstance null");
        return NULL;
    }

    DTVKitServerClientJni* client = static_cast<DTVKitServerClientJni *>(mInstance);
    MessageQueueSync* fmq = client->getQueue();
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
#endif

DTVKitClientJni::DTVKitClientJni()  {

}

DTVKitClientJni::~DTVKitClientJni()  {

}

#ifdef SUPPORT_TUNER_FRAMEWORK
void signalHandler(int signal) {

    ALOGD("signalHandler signal : %d", signal);
    if (signal ==  SIGINT) {
        ALOGD("Caught SIGINT. Performing cleanup operations...");
    }
}

void registerSignalHandler() {
    struct sigaction action;
    action.sa_handler = &signalHandler;
    sigemptyset(&action.sa_mask);
    action.sa_flags = 0;
    if (sigaction(SIGINT, &action, NULL) < 0) {
        ALOGD("Failed to register signal handler for SIGINT");
    } else {
        ALOGD("Success to register signal handler for SIGINT");
    }
}

DTVKitTunerClientJni::DTVKitTunerClientJni() {
    mGlueClient = Glue_client::getInstance();
    mGlueClient->addInterface();
    mGlueClient->setSignalCallback(signalCallback);
    mGlueClient->setDisPatchDrawCallback((DISPATCHDRAW_CB)postSubtitleData);
    registerSignalHandler();
}

DTVKitTunerClientJni::~DTVKitTunerClientJni()  {
   Glue_client::getInstance()->setDisPatchDrawCallback(NULL);
}

std::string DTVKitTunerClientJni::request(const std::string& resource, const std::string& json) {
    return mGlueClient->request(resource, json);
}

void DTVKitTunerClientJni::setAfd(int player, int afd) {
    //Deprecated
    //mGlueClient->setAfd(player, afd);
}

void DTVKitTunerClientJni::setSubtitleFlag(int flag) {
    //Deprecated
}

void DTVKitTunerClientJni::signalCallback(const std::string &signal, const std::string &data, int id) {
    ALOGD("-signalCallback signal:%s (%d), data:%s", signal.c_str(), id, data.c_str());
    postDvbParam(signal, data, id);
}

#else
DTVKitServerClientJni::DTVKitServerClientJni()  {
    mDkSession = DTVKitHidlClient::connect(DTVKitHidlClient::CONNECT_TYPE_HAL);
    mDkSession->setListener(this);
}

DTVKitServerClientJni::~DTVKitServerClientJni()  {

}

std::string DTVKitServerClientJni::request(const std::string& resource, const std::string& json) {
    return mDkSession->request(resource, json);
}

void DTVKitServerClientJni::setAfd(int player, int afd) {
    //Deprecated
    //mDkSession->setAfd(player, afd);
}

void DTVKitServerClientJni::setSubtitleFlag(int flag) {
    //Deprecated
    //mDkSession->setSubtitleFlag(flag);
}

MessageQueueSync* DTVKitServerClientJni::getQueue() {
    ALOGD("Enter getQueue");
    return mDkSession->getQueue();
}

void DTVKitServerClientJni::notify(const parcel_t &parcel) {
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

        if (datablock.width != 0 && datablock.height != 0) {
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
}

void DTVKitServerClientJni::notifyServerState(int diedOrReconnected) {
    bool attached = false;
    JNIEnv *env = getJniEnv(&attached);

    if (env != NULL) {
        env->CallVoidMethod(DtvkitObject, notifyServerStateCallback, diedOrReconnected);
    }
    if (attached) {
        DetachJniEnv();
    }
}
#endif

static void connectDtvkit(JNIEnv *env, jclass clazz __unused, jobject obj, jobject buffer)
{
    ALOGI("ref dtvkit");
    mpDtvkitJni  =  DTVKitClientJni::GetInstance();
    DtvkitObject = env->NewGlobalRef(obj);
    gJBuffer = (uint8_t*)env->GetDirectBufferAddress(buffer);
    gJBufSize = env->GetDirectBufferCapacity(buffer);
    ALOGE("native buffer info %p,length %d\n", gJBuffer, gJBufSize);
    gJNIReady = true;
}

static void disConnectDtvkit(JNIEnv *env, jclass clazz __unused)
{
    ALOGI("disconnect dtvkit");
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

static void attachSubtitleCtl(JNIEnv *env, jclass clazz __unused, jint flag)
{
    //Deprecated
}

static void detachSubtitleCtl(JNIEnv *env, jclass clazz __unused)
{
    //Deprecated
}

static void destroySubtitleCtl(JNIEnv *env, jclass clazz __unused)
{
    //Deprecated
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
    //Deprecated
}

static void setRegionId(JNIEnv *env, jclass clazz __unused, jint regionId) {
    char args[16] = {0};

    if (mpDtvkitJni != nullptr) {
        snprintf(args, sizeof(args), "[%d]", regionId);
        mpDtvkitJni->request("Dvb.setTeletextRegionId", args);
    }
}

static void closeUserData() {
    //Deprecated
}

static void resetForSeek() {
    //Deprecated
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
    GET_METHOD_ID(notifyCCSubtitleCallbackEx, clazz, "notifyCCSubtitleCallbackEx", "(ZLjava/lang/String;I)V");
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


