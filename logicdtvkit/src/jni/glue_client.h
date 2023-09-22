#ifndef GULE_CLIENT_H
#define GULE_CLIENT_H

#include <iostream>
typedef void (*SIGNAL_CB)(const std::string &signal, const std::string &data, int id);
typedef void  (*DISPATCHDRAW_CB)(int32_t src_width, int32_t src_height, int32_t dst_x, int32_t dst_y, int32_t dst_width, int32_t dst_height, const uint8_t *data);

/**
 * @brief   Function pointer for customer subtitle controls.
 * @param   pid
 * @param   type in subtitle, teletext subtitle, scte27
 */
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

// Singleton mode, only used by getInstance
class Glue_client {

private:
    Glue_client();
    static Glue_client *p_client;

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
        return p_client;
    }
    void dispatchDraw(int32_t src_width, int32_t src_height, int32_t dst_x, int32_t dst_y, int32_t dst_width, int32_t dst_height, const uint8_t *data);
    void dispatchSignal(const std::string &signal, const std::string &data, int id);

    void RegisterCusSubCtl(void* ctlfuns, int flag);
    void UnRegisterCusSubCtl(void);
    void setAfd(int id, uint8_t afd);
private:
    SIGNAL_CB signal_callback = NULL;
    DISPATCHDRAW_CB dispatchDraw_callback = NULL;
};
// no need add lock for new client.we new client first
Glue_client* Glue_client::p_client = new Glue_client();

#endif //GULE_CLIENT_H
