#ifndef __DTV_GULE_H__
#define __DTV_GULE_H__

#include <iostream>

typedef void (*SIGNAL_CB)(const std::string &signal, const std::string &data, int id);
typedef void  (*DISPATCHDRAW_CB)(int32_t src_width, int32_t src_height, int32_t dst_x, int32_t dst_y, int32_t dst_width, int32_t dst_height, const uint8_t *data);

typedef void (*F_SubtitleCtrlStart)(int pid, int onid, int type, int magazine, int page, int demux_id);
typedef void (*F_SubtitleCtrlVoid)(void);
typedef void (*F_NotifyTeletextEvent)(int eventType);
typedef void (*F_SubtitleTune)(int type, int param1, int param2, int param3);

typedef struct
{
   F_SubtitleCtrlStart start;
   F_SubtitleCtrlVoid stop;
   F_SubtitleCtrlVoid pause;
   F_SubtitleCtrlVoid resume;
   F_NotifyTeletextEvent notifyTeletextEvent;
   F_SubtitleTune     tune;
}S_CUS_SUB_CTRL_T;


class DtvGlueBase {

public:
    // 虚析构函数
    virtual ~DtvGlueBase() {}

    virtual int addInterface(void);

    virtual std::string request(const std::string &resource, const std::string &json);

    virtual void RegisterCusSubCtl(void* ctlfuns, int flag);

    virtual void UnRegisterCusSubCtl(void);

    virtual void dispatchSignal(const std::string &signal, const std::string &data, int id);

    virtual int setSignalCallback(SIGNAL_CB cb);

    virtual int setDisPatchDrawCallback(DISPATCHDRAW_CB cb);

    virtual int SetSurface(int path, void *surface);

    virtual void setAfd(int id, uint8_t afd);

    virtual int RegisterRWSysfsCallback(void *readCb, void *writeCb);

    virtual int UnRegisterRWSysfsCallback(void);

    virtual int RegisterRWPropCallback(void *readCb, void *writeCb);

    virtual int UnRegisterRWPropCallback(void);

    virtual void dispatchDraw(int32_t src_width, int32_t src_height, int32_t dst_x, int32_t dst_y, int32_t dst_width, int32_t dst_height, const uint8_t *data);

protected:
    virtual bool ParseResource(const std::string &resource, std::string *interface, std::string *invokable);

    SIGNAL_CB signal_callback = NULL;
    DISPATCHDRAW_CB dispatchDraw_callback = NULL;
    S_CUS_SUB_CTRL_T s_cus_subtitleContrl_t = {NULL, NULL, NULL, NULL, NULL, NULL};
};

// ExampleLibrary.h 文件
#if defined(_WIN32) || defined(__CYGWIN__)
  #define DLL_PUBLIC __declspec(dllexport)
#else
  #define DLL_PUBLIC __attribute__((visibility("default")))
#endif

extern "C" {

// !!! This Function Must be implemented by the Concrete class
typedef DtvGlueBase* (* CreateDtvGlueInstance_Func)();
DLL_PUBLIC DtvGlueBase* dl_CreateDtvGlueInstance();

// !!! This Function Must be implemented by the Concrete class
typedef void (* ReleaseDtvGlueInstance_Func)(DtvGlueBase*);
DLL_PUBLIC void dl_ReleaseDtvGlueInstance(DtvGlueBase* instance);

typedef int (* addInterface_Func)(DtvGlueBase*);
DLL_PUBLIC int dl_addInterface(DtvGlueBase* instance);

typedef const char* (* request_Func)(DtvGlueBase*, const std::string &, const std::string &);
DLL_PUBLIC const char* dl_request(DtvGlueBase* instance, const std::string &resource, const std::string &json);

typedef void (* RegisterCusSubCtl_Func)(DtvGlueBase*, void*, int);
DLL_PUBLIC void dl_RegisterCusSubCtl(DtvGlueBase* instance, void* ctlfuns, int flag);

typedef void (* UnRegisterCusSubCtl_Func)(DtvGlueBase*);
DLL_PUBLIC void dl_UnRegisterCusSubCtl(DtvGlueBase* instance);

typedef void (* dispatchSignal_Func)(DtvGlueBase*, const std::string &, const std::string &, int);
DLL_PUBLIC void dl_dispatchSignal(DtvGlueBase* instance, const std::string &signal, const std::string &data, int id);

typedef int (* setSignalCallback_Func)(DtvGlueBase*, SIGNAL_CB);
DLL_PUBLIC int dl_setSignalCallback(DtvGlueBase* instance, SIGNAL_CB cb);

typedef int (* setDisPatchDrawCallback_Func)(DtvGlueBase*, DISPATCHDRAW_CB);
DLL_PUBLIC int dl_setDisPatchDrawCallback(DtvGlueBase* instance, DISPATCHDRAW_CB cb);

typedef int (* SetSurface_Func)(DtvGlueBase*, int, void *);
DLL_PUBLIC int dl_SetSurface(DtvGlueBase* instance, int path, void *surface);

typedef void (* setAfd_Func)(DtvGlueBase*, int, uint8_t);
DLL_PUBLIC void dl_setAfd(DtvGlueBase* instance, int id, uint8_t afd);

typedef int (* RegisterRWSysfsCallback_Func)(DtvGlueBase*, void *, void *);
DLL_PUBLIC int dl_RegisterRWSysfsCallback(DtvGlueBase* instance, void *readCb, void *writeCb);

typedef int (* UnRegisterRWSysfsCallback_Func)(DtvGlueBase*);
DLL_PUBLIC int dl_UnRegisterRWSysfsCallback(DtvGlueBase* instance);

typedef int (* RegisterRWPropCallback_Func)(DtvGlueBase*, void *, void *);
DLL_PUBLIC int dl_RegisterRWPropCallback(DtvGlueBase* instance, void *readCb, void *writeCb);

typedef int (* UnRegisterRWPropCallback_Func)(DtvGlueBase*);
DLL_PUBLIC int dl_UnRegisterRWPropCallback(DtvGlueBase* instance);

typedef void (* dispatchDraw_Func)(DtvGlueBase*, int32_t, int32_t,
                                         int32_t, int32_t,
                                         int32_t, int32_t,
                                         const uint8_t *);
DLL_PUBLIC void dl_dispatchDraw(DtvGlueBase* instance, int32_t src_width, int32_t src_height,
                                                        int32_t dst_x, int32_t dst_y,
                                                        int32_t dst_width, int32_t dst_height,
                                                        const uint8_t *data);

}


#endif // __DTV_GULE_H__
