package com.droidlogic.dtvkit.inputsource.service;

import static org.droidlogic.dtvkit.DtvkitGlueClient.INDEX_FOR_MAIN;
import static org.droidlogic.dtvkit.DtvkitGlueClient.INDEX_FOR_PIP;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.droidlogic.dtvkit.IATFGlueOverlayTarget;
import org.droidlogic.dtvkit.IATFGluePidFilterListener;
import org.droidlogic.dtvkit.IATFGlueSignalHandler;
import org.droidlogic.dtvkit.IATFGlueSubtitleListener;
import org.droidlogic.dtvkit.IATFGlueWrapper;
import org.droidlogic.dtvkit.ParceledListSlice;
import org.droidlogic.dtvkit.IntegerBlock;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class GlueWrapperService extends Service {
    private static final String TAG = "GlueWrapperService";
    private final RemoteCallbackList<IATFGlueSignalHandler> mMainListenerList = new RemoteCallbackList<>();
    private final RemoteCallbackList<IATFGlueSignalHandler> mPipListenerList = new RemoteCallbackList<>();
    private final DtvkitGlueClient.SignalHandler mMainSignalHandler = (signal, data) -> {
        //Log.d(TAG,"signal:"+signal+",json:"+data.toString());
        int count = mMainListenerList.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                mMainListenerList.getBroadcastItem(i).onRespond(INDEX_FOR_MAIN, signal, data.toString());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "onRespond:" + e.getMessage());
        }
        mMainListenerList.finishBroadcast();
    };
    private final DtvkitGlueClient.SignalHandler mPipSignalHandler = (signal, data) -> {
//        Log.d(TAG, "pip signal:" + signal + ",json:" + data.toString());
        int count = mPipListenerList.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                mPipListenerList.getBroadcastItem(i).onRespond(INDEX_FOR_PIP, signal, data.toString());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "onRespond:" + e.getMessage());
        }
        mPipListenerList.finishBroadcast();
    };
    private IATFGluePidFilterListener mIPidFilterListener;
    private final DtvkitGlueClient.PidFilterListener mPidFilterListener = new DtvkitGlueClient.PidFilterListener() {
        @Override
        public void onPidFilterData(ByteBuffer data) {
            if (mIPidFilterListener != null) {
                try {
                    mIPidFilterListener.onPidFilterResponse(data.array());
                } catch (RemoteException e) {
                    Log.e(TAG, "onPidFilterData:" + e.getMessage());
                }
            }
        }
    };
    private IATFGlueOverlayTarget mITarget;
    private final DtvkitGlueClient.OverlayTarget mTarget = new DtvkitGlueClient.OverlayTarget() {
        @Override
        public void draw(int src_width, int src_height, int dst_x, int dst_y,
                         int dst_width, int dst_height, int[] data) {
            if (mITarget != null) {
                try {
                    mITarget.draw(src_width, src_height, dst_x, dst_y, dst_width, dst_height,
                            getParceledListSlice(src_width * src_height, data));
                } catch (RemoteException e) {
                    Log.e(TAG, "draw:" + e.getMessage());
                }
            }
        }
    };

    final int MAX_INT_SIZE = 100 * 1000;
    private ParceledListSlice<IntegerBlock> getParceledListSlice(int length, int[] data) {
        List<IntegerBlock> list = new ArrayList<>();
        int transfer;
        int left = length;
        while (left > 0) {
            transfer = Math.min(left, MAX_INT_SIZE);
            int[] array = new int[transfer];
            System.arraycopy(data, length - left, array, 0, transfer);
            IntegerBlock block = new IntegerBlock();
            block.c = array;
            list.add(block);
            left -= transfer;
        }
        return new ParceledListSlice<>(list);
    }

    private IATFGlueSubtitleListener mISubListener;
    private final DtvkitGlueClient.SubtitleListener mListener = new DtvkitGlueClient.SubtitleListener() {
        @Override
        public void drawEx(int parserType, int src_width, int src_height,
                           int dst_x, int dst_y, int dst_width, int dst_height, int[] data) {
            if (mISubListener != null) {
                try {
                    mISubListener.drawEx(parserType, src_width, src_height,
                            dst_x, dst_y, dst_width, dst_height, getParceledListSlice(src_width * src_height, data));
                } catch (RemoteException e) {
                    Log.e(TAG, "drawEx:" + e.getMessage());
                }
            }
        }

        @Override
        public void pauseEx(int pause) {
            if (mISubListener != null) {
                try {
                    mISubListener.pauseEx(pause);
                } catch (RemoteException e) {
                    Log.e(TAG, "pauseEx:" + e.getMessage());
                }
            }
        }

        @Override
        public void drawCC(boolean bShow, String json, int type) {
            if (mISubListener != null) {
                try {
                    mISubListener.drawCC(bShow, json, type);
                } catch (RemoteException e) {
                    Log.e(TAG, "drawCC:" + e.getMessage());
                }

            }
        }

        @Override
        public void mixVideoEvent(int event) {
            if (mISubListener != null) {
                try {
                    mISubListener.mixVideoEvent(event);
                } catch (RemoteException e) {
                    Log.e(TAG, "mixVideoEvent:" + e.getMessage());
                }

            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        DtvkitGlueClient.getInstance().registerSignalHandler(mMainSignalHandler, INDEX_FOR_MAIN);
        DtvkitGlueClient.getInstance().registerSignalHandler(mPipSignalHandler, INDEX_FOR_PIP);
        return new GlueWrapperSetting();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mMainSignalHandler);
        DtvkitGlueClient.getInstance().unregisterSignalHandler(mPipSignalHandler);
        DtvkitGlueClient.getInstance().removeSubtileListener(mListener);
        DtvkitGlueClient.getInstance().removeOverlayTarget(mTarget);
        DtvkitGlueClient.getInstance().setPidFilterListener(null);
        return false;
    }

    private class GlueWrapperSetting extends IATFGlueWrapper.Stub {
        @Override
        public String request(String resource, String arguments) {
            JSONObject obj = null;
            try {
                JSONArray args = new JSONArray(arguments);
                obj = DtvkitGlueClient.getInstance().request(resource, args);
            } catch (Exception e) {
                Log.e(TAG, "request:" + e.getMessage());
            }
            if (obj != null) {
                return obj.toString();
            } else {
                return "";
            }
        }

        @Override
        public void registerSignalHandler(int id, IATFGlueSignalHandler handler) {
            if (id == 0) {
                mMainListenerList.register(handler);
            } else {
                mPipListenerList.register(handler);
            }
        }

        @Override
        public void unregisterSignalHandler(int id, IATFGlueSignalHandler handler) {
            if (id == 0) {
                mMainListenerList.unregister(handler);
            } else {
                mPipListenerList.unregister(handler);
            }
        }

        @Override
        public void setPidFilterListener(IATFGluePidFilterListener listener) {
            DtvkitGlueClient.getInstance().setPidFilterListener(mPidFilterListener);
            Log.d(TAG, "setPidFilterListener " + mPidFilterListener);
            mIPidFilterListener = listener;
        }

        @Override
        public void enablePidListener(boolean enable) {
			Log.d(TAG, "enablePidListener " + enable);
            DtvkitGlueClient.getInstance().enablePidListener(enable);
        }

        @Override
        public void setOverlayTarget(IATFGlueOverlayTarget target) {
            DtvkitGlueClient.getInstance().setOverlayTarget(mTarget);
            Log.d(TAG, "setOverlayTarget " + mTarget);
            mITarget = target;
        }

        @Override
        public void removeOverlayTarget(IATFGlueOverlayTarget target) {
            Log.d(TAG, "removeOverlayTarget " + mTarget);
            DtvkitGlueClient.getInstance().removeOverlayTarget(mTarget);
            mITarget = null;
        }

        @Override
        public void setSubtitleListener(IATFGlueSubtitleListener listener) {
            Log.d(TAG, "setSubtitleListener " + mListener);
            DtvkitGlueClient.getInstance().setSubtileListener(mListener);
            mISubListener = listener;
        }

        @Override
        public void removeSubtitleListener(IATFGlueSubtitleListener listener) {
            Log.d(TAG, "removeSubtitleListener " + mListener);
            DtvkitGlueClient.getInstance().removeSubtileListener(mListener);
            mISubListener = null;
        }

        @Override
        public void attachSubtitleCtl(int flag) {
            Log.d(TAG, "attachSubtitleCtl " + flag);
            DtvkitGlueClient.getInstance().attachSubtitleCtl(flag);
        }

        @Override
        public void destroySubtitleCtl() {
            Log.d(TAG, "destroySubtitleCtl");
            DtvkitGlueClient.getInstance().destroySubtitleCtl();
        }
    }
}
