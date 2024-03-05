//IATFGlueWrapper.aidl
package org.droidlogic.dtvkit;
import org.droidlogic.dtvkit.IATFGlueOverlayTarget;
import org.droidlogic.dtvkit.IATFGluePidFilterListener;
import org.droidlogic.dtvkit.IATFGlueSignalHandler;
import org.droidlogic.dtvkit.IATFGlueSubtitleListener;

interface IATFGlueWrapper {
    String request(String resource, String args);
    void registerSignalHandler(int id, IATFGlueSignalHandler handler);
    void unregisterSignalHandler(int id, IATFGlueSignalHandler handler);
    void setPidFilterListener(IATFGluePidFilterListener listener);
    void enablePidListener(boolean enable);
    void setOverlayTarget(IATFGlueOverlayTarget target);
    void removeOverlayTarget(IATFGlueOverlayTarget target);
    void setSubtitleListener(IATFGlueSubtitleListener listener);
    void removeSubtitleListener(IATFGlueSubtitleListener listener);
    void attachSubtitleCtl(int flag);
    void destroySubtitleCtl();
}
