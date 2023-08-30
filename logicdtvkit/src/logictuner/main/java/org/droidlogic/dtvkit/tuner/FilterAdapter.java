package droidlogic.dtvkit.tuner;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterConfiguration;
import android.os.Handler;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.lang.Long;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dtvkit tool class.
 */
public class FilterAdapter {
    private static final String TAG = "FilterAdapter";
    private static final boolean DEBUG = true;

    private Filter mFilter;
    private Executor mCallbackExecutor;
    private long mFilterCallbackContext;

    private native void nativeFilterCallback(FilterEvent[] events, int status);

    public FilterAdapter(Filter filter, Executor callbackExecutor, long filterCallbackContext) {
        mFilter = filter;
        mCallbackExecutor = callbackExecutor;
        mFilterCallbackContext = filterCallbackContext;
    }

    public Filter getFilter() {
        return mFilter;
    }

    private void setType(int mainType, int subtype) {
        FilterHelp.setType(mFilter, mainType, subtype);
        if (DEBUG) Log.d(TAG, "setType filter id :" + mFilter.getId() + " mainType : " + mainType + " subtype : " + subtype);
    }

    private void setCallback(long callbackContext) {
        if (DEBUG) Log.d(TAG, "setCallback filter id :" + mFilter.getId() + " callbackContext : 0x" + Long.toHexString(callbackContext));
        if (0 == callbackContext) {
            FilterHelp.setCallback(mFilter, null, null);
            mFilterCallbackContext = 0;
        } else {
            FilterAdapterCallback callback = new FilterAdapterCallback();
            FilterHelp.setCallback(mFilter, callback, mCallbackExecutor);
            mFilterCallbackContext = callbackContext;
            callback.setCallbackFilterAdapter(this);
        }
    }

    private int configure(FilterConfiguration config) {
        int result = 0;
        if ((null != config) && (config instanceof FilterConfiguration)) {
            result = mFilter.configure(config);
            if (DEBUG) Log.d(TAG, "configure filter id :" + mFilter.getId() + "config : " + config + " result : " + result);
        } else {
            Log.e(TAG, "configure error");
        }
        return result;
    }

    private int getId() {
        if (DEBUG) Log.d(TAG, "getId filter id :" + mFilter.getId());
        return mFilter.getId();
    }

    private int setDataSource(Filter source) {
        int result = 0;
        if ((null != source) && (source instanceof Filter)) {
            result = mFilter.setDataSource(source);
            mFilter = source;
            if (DEBUG) Log.d(TAG, "setDataSource filter id :" + mFilter.getId() + "source filter id: " + source.getId() + " result : " + result);
        } else {
            Log.e(TAG, "setDataSource error");
        }
        return result;
    }

    private int start() {
        int result = mFilter.start();
        if (DEBUG) Log.d(TAG, "start filter id :" + mFilter.getId() + " result : " + result);
        return result;
    }

    private int stop() {
        int result = mFilter.stop();
        if (DEBUG) Log.d(TAG, "stop filter id :" + mFilter.getId() + " result : " + result);
        return result;
    }

    private int flush() {
        int result = mFilter.flush();
        if (DEBUG) Log.d(TAG, "flush filter id :" + mFilter.getId() + " result : " + result);
        return result;
    }

    private void close() {
        if (DEBUG) Log.d(TAG, "close filter id :" + mFilter.getId());
        mFilter.close();
        mFilter = null;
        mCallbackExecutor = null;
		mFilterCallbackContext = 0;
    }

    private int read(byte[] buffer, long offset, long size) {
        //TBD:need check array length
        int result = mFilter.read(buffer, offset, size);
        if (DEBUG) Log.d(TAG, "read filter id :" + mFilter.getId() + " offset : " + offset + " size : " + size
            + " buffer : " + buffer + " result : " + result);
        return result;
    }

    private static class FilterHelp{
        static Method sSetTypeIds;
        static Method sSetCallback;

        static {
            try {
                Class<?> Filter = Class.forName("android.media.tv.tuner.filter.Filter");
                sSetTypeIds = Filter.getDeclaredMethod("setType", int.class, int.class);
                sSetCallback = Filter.getDeclaredMethod("setCallback", FilterCallback.class, Executor.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static void setType(android.media.tv.tuner.filter.Filter filter, int mainType, int subType) {
            try {
                sSetTypeIds.invoke(filter, mainType, subType);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static void setCallback(android.media.tv.tuner.filter.Filter filter, FilterCallback callback, Executor executor) {
            try {
                sSetCallback.invoke(filter, callback, executor);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class FilterAdapterCallback implements FilterCallback{
        private FilterAdapter mFilterAdapter;

        public FilterAdapterCallback() {}

        public void setCallbackFilterAdapter(FilterAdapter filterAdapter) {
            mFilterAdapter = filterAdapter;
        }

        @Override
        public void onFilterEvent(Filter filter, FilterEvent[] events) {
            nativeCallbackHandle(filter, events, 0);
        }

        @Override
        public void onFilterStatusChanged(Filter filter, int status) {
            nativeCallbackHandle(filter, null, status);
        }

        private void nativeCallbackHandle(Filter filter, FilterEvent[] events, int status) {
            try {
            if (null != mFilterAdapter) {
                if (DEBUG) Log.d(TAG, "onFilterEvent filter id :" + filter.getId() + " mFilterAdapter id : " + mFilterAdapter.getId()
                    + " events :" + events + " status : " + events);
                if (0 != mFilterAdapter.mFilterCallbackContext) {
                    mFilterAdapter.nativeFilterCallback(events, status);
                } else {
                    //TBD use data for TIAF
                }
            } else {
                Log.e(TAG, "Error not register callback FilterAdapter");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error callback FilterAdapter : " + e.getMessage());
            }
        }
    }
}