package droidlogic.dtvkit.tuner;

import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.Descrambler;
import android.media.tv.tuner.filter.Filter;
import android.util.Log;

import java.lang.Long;
import java.util.concurrent.Executor;

public class DescramblerAdapter {
    private static final String TAG = "DescramblerAdapter";
    private static final boolean DEBUG = true;

    private Descrambler mDescrambler;

    public DescramblerAdapter(Descrambler descrambler) {
        mDescrambler = descrambler;
        Log.d(TAG, "DescramblerAdapter descrambler : " + descrambler);
    }

    private int addPid(int pidType, int pid, FilterAdapter filterAdapter) {
        int result = 0;
        if (DEBUG) Log.d(TAG, "addPid pidType : " + pidType + "pid : " + pid + "FilterAdapter : " + filterAdapter);
        if (null != filterAdapter) {
            Filter filter = filterAdapter.getFilter();
            if (null != filter) {
                Log.d(TAG, "filter id : " + filter.getId());
                result = mDescrambler.addPid(pidType, pid, filter);
            }
        } else {
            result = mDescrambler.addPid(pidType, pid, null);
        }
        return result;
    }

    private int removePid(int pidType, int pid, FilterAdapter filterAdapter) {
        int result = 0;
        if (DEBUG) Log.d(TAG, "removePid pidType : " + pidType + "pid : " + pid + "FilterAdapter : " + filterAdapter);
        if (null != filterAdapter) {
            Filter filter = filterAdapter.getFilter();
            if (null != filter) {
                Log.d(TAG, "filter id : " + filter.getId());
                result = mDescrambler.removePid(pidType, pid, filter);
            }
        } else {
            result = mDescrambler.removePid(pidType, pid, null);
        }
        return result;
    }

    private int setKeyToken(byte[] keyToken) {
        if (DEBUG) Log.d(TAG, "setKeyToken");
        if (null == keyToken) {
            return Tuner.RESULT_INVALID_ARGUMENT;
        }
        if (Descrambler.isValidKeyToken(keyToken)) {
            int result = mDescrambler.setKeyToken(keyToken);
            return result;
        }
        Log.e(TAG, "keyToken is invalid");
        return Tuner.RESULT_INVALID_ARGUMENT;
    }

    private void close() {
        if (DEBUG) Log.d(TAG, "close");
        mDescrambler.close();
        mDescrambler = null;
        return;
    }
}
