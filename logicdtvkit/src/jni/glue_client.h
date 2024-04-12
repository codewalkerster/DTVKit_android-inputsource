#ifndef __ATF_GULE_CLIENT_H__
#define __ATF_GULE_CLIENT_H__

#include <iostream>

#include "dtv_glue.h"

// Singleton mode, only used by getInstance
class Glue_client {

public:
    int setSignalCallback(SIGNAL_CB cb);
    int setDisPatchDrawCallback(DISPATCHDRAW_CB cb);
    int RegisterRWSysfsCallback(void *readCb, void *writeCb);
    int UnRegisterRWSysfsCallback(void);
    int RegisterRWPropCallback(void *readCb, void *writeCb);
    int UnRegisterRWPropCallback(void);
    int addInterface(void);
    int SetSurface(int path, void *surface);
    std::string request(const std::string &resource, const std::string &json);

    static Glue_client* getInstance()
    {
        if (nullptr == instance)
        {
            instance = new Glue_client();
        }

        return instance;
    }

    void dispatchDraw(int32_t src_width, int32_t src_height, int32_t dst_x, int32_t dst_y, int32_t dst_width, int32_t dst_height, const uint8_t *data);
    void dispatchSignal(const std::string &signal, const std::string &data, int id);

    void RegisterCusSubCtl(void* ctlfuns, int flag);
    void UnRegisterCusSubCtl(void);
    void setAfd(int id, uint8_t afd);

    ~Glue_client();

private:
    Glue_client();
    static Glue_client* instance;

    CreateDtvGlueInstance_Func     m_CreateDtvGlueInstance_Func = nullptr;
    ReleaseDtvGlueInstance_Func    m_ReleaseDtvGlueInstance_Func = nullptr;
    addInterface_Func              m_addInterface_Func = nullptr;
    request_Func                   m_request_Func = nullptr;
    RegisterCusSubCtl_Func         m_RegisterCusSubCtl_Func = nullptr;
    UnRegisterCusSubCtl_Func       m_UnRegisterCusSubCtl_Func = nullptr;
    dispatchSignal_Func            m_dispatchSignal_Func = nullptr;
    setSignalCallback_Func         m_setSignalCallback_Func = nullptr;
    setDisPatchDrawCallback_Func   m_setDisPatchDrawCallback_Func = nullptr;
    SetSurface_Func                m_SetSurface_Func = nullptr;
    setAfd_Func                    m_setAfd_Func = nullptr;
    RegisterRWSysfsCallback_Func   m_RegisterRWSysfsCallback_Func = nullptr;
    UnRegisterRWSysfsCallback_Func m_UnRegisterRWSysfsCallback_Func = nullptr;
    RegisterRWPropCallback_Func    m_RegisterRWPropCallback_Func = nullptr;
    UnRegisterRWPropCallback_Func  m_UnRegisterRWPropCallback_Func = nullptr;
    dispatchDraw_Func              m_dispatchDraw_Func = nullptr;

    void *dllHandle = nullptr;
    DtvGlueBase *m_DtvGlue_ptr = nullptr;
};


#endif //__ATF_GULE_CLIENT_H__
