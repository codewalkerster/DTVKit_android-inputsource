package org.droidlogic.dtvkit;

import org.droidlogic.dtvkit.IntegerBlock;
import org.droidlogic.dtvkit.ParceledListSlice;

interface IATFGlueSubtitleListener {
    void drawEx(int parserType, int src_width, int src_height, int dst_x, int dst_y, int dst_width, int dst_height, in ParceledListSlice<IntegerBlock> data);
    void pauseEx(int pause);
    void drawCC(boolean bShow, String json, int type);
    void mixVideoEvent(int event);
}
