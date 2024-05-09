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

#ifndef __ORG_DTVKIT_INPUTSOURCE_CLIENT_H__
#define __ORG_DTVKIT_INPUTSOURCE_CLIENT_H__
#include <jni.h>
#include <utils/Log.h>
#ifdef SUPPORT_TUNER_FRAMEWORK
#include "glue_client.h"
#include <utils/RefBase.h>
#else
#include "DTVKitHidlClient.h"
#endif

using namespace android;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using android::Mutex;

enum {
    REQUEST        = 0,
    DTVKIT_DRAW    = 1,
    SUB_SERVER_DRAW = 2,
    HBBTV_DRAW     = 3,
};

typedef struct datablock_s {
    int width;
    int height;
    int dst_x;
    int dst_y;
    int dst_width;
    int dst_height;
    hidl_memory mem;
} datablock_t;

typedef struct dvb_param_s {
    std::string resource;
    std::string json;
    int id;
}dvb_param_t;

class DTVKitClientJni : virtual public RefBase{
public:
    DTVKitClientJni();
    ~DTVKitClientJni();

    virtual std::string request(const std::string& resource, const std::string& json) = 0;
    virtual void setAfd(int player, int afd) = 0;
    virtual void setSubtitleFlag(int flag) = 0;
//    virtual MessageQueueSync* getQueue() = 0;

    static DTVKitClientJni *GetInstance();
    static void once_run(void);
    static void* pid_run(void *arg);
protected:
    //static void  once_run(void);
    //static void* pid_run(void *arg);
    //sp<DTVKitHidlClient> mDkSession;
    //mutable Mutex mLock;
    static DTVKitClientJni *mInstance;
};

#ifdef SUPPORT_TUNER_FRAMEWORK
class DTVKitTunerClientJni : public DTVKitClientJni {
public:
    DTVKitTunerClientJni();
    ~DTVKitTunerClientJni();

    virtual std::string request(const std::string& resource, const std::string& json);
    virtual void setAfd(int player, int afd);
    virtual void setSubtitleFlag(int flag);
private:
    static void signalCallback(const std::string &signal, const std::string &data, int id);
    Glue_client *mGlueClient = NULL;
};
#else
class DTVKitServerClientJni : public DTVKitClientJni, public DTVKitListener{
public:
    DTVKitServerClientJni();
    ~DTVKitServerClientJni();

    virtual std::string request(const std::string& resource, const std::string& json);
    virtual void setAfd(int player, int afd);
    virtual void setSubtitleFlag(int flag);
    virtual MessageQueueSync* getQueue();

    virtual void notify(const parcel_t &parcel);
    virtual void notifyServerState(int diedOrReconnected);
private:
    mutable Mutex mLock;
    sp<DTVKitHidlClient> mDkSession;
};
#endif

#endif/*__ORG_DTVKIT_INPUTSOURCE_CLIENT_H__*/

