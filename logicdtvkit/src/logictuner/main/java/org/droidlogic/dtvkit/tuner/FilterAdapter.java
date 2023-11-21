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
import java.lang.reflect.Field;
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
    private int mTunerClientId;

    private native void nativeFilterCallback(FilterEvent[] events, int status);

    public FilterAdapter(Filter filter, Executor callbackExecutor, long filterCallbackContext, int tunerClientId) {
        mFilter = filter;
        mCallbackExecutor = callbackExecutor;
        mFilterCallbackContext = filterCallbackContext;
        mTunerClientId = tunerClientId;
    }

    public Filter getFilter() {
        return mFilter;
    }

    /****Filter weak ref if GC,need release resource****/
    @Override
    protected void finalize() {
        Log.e(TAG, "FilterAdapter is release FilterAdapter : " + this + " mFilter : " + mFilter);
        if (null != mFilter) {
            getId();
            close();
        }
    }

    private void setType(int mainType, int subtype) {
        if (null != mFilter) {
            FilterHelp.setType(mFilter, mainType, subtype);
            if (DEBUG) Log.d(TAG, "setType filter id :" + mFilter.getId() + " mainType : " + mainType + " subtype : " + subtype);
        } else {
            Log.e(TAG, "filter hase close");
        }
    }

    private void setCallback(long callbackContext) {
        if (null != mFilter) {
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
        } else {
            Log.e(TAG, "filter hase close");
        }
    }

    private int configure(FilterConfiguration config) {
        int result = Tuner.INVALID_FILTER_ID;
        if (null != mFilter) {
            if ((null != config) && (config instanceof FilterConfiguration)) {
                result = mFilter.configure(config);
                if (DEBUG) Log.d(TAG, "configure filter id :" + mFilter.getId() + "config : " + config + " result : " + result);
            } else {
                Log.e(TAG, "configure error");
            }
        } else {
            Log.e(TAG, "filter hase close");
        }
        return result;
    }

    private int getId() {
        if (null != mFilter) {
            return mFilter.getId();
        } else {
            Log.e(TAG, "filter hase close");
            return Tuner.INVALID_FILTER_ID;
        }
    }

    private int setDataSource(Filter source) {
        int result = Tuner.INVALID_FILTER_ID;
        if (null != mFilter) {
            if ((null != source) && (source instanceof Filter)) {
                result = mFilter.setDataSource(source);
                mFilter = source;
                if (DEBUG) Log.d(TAG, "setDataSource filter id :" + mFilter.getId() + "source filter id: " + source.getId() + " result : " + result);
            } else {
                Log.e(TAG, "setDataSource error");
            }
        } else {
            Log.e(TAG, "filter hase close");
        }
        return result;
    }

    private int start() {
        int result = Tuner.INVALID_FILTER_ID;
        if (null != mFilter) {
            result = mFilter.start();
            if (DEBUG) Log.d(TAG, "start filter id :" + mFilter.getId() + " result : " + result);
        } else {
            Log.e(TAG, "filter hase close");
        }
        return result;
    }

    private int stop() {
        int result = Tuner.INVALID_FILTER_ID;
        if (null != mFilter) {
            result = mFilter.stop();
            if (DEBUG) Log.d(TAG, "stop filter id :" + mFilter.getId() + " result : " + result);
        } else {
            Log.e(TAG, "filter hase close");
        }
        return result;
    }

    private int flush() {
        int result = Tuner.INVALID_FILTER_ID;
        if (null != mFilter) {
            result = mFilter.flush();
            if (DEBUG) Log.d(TAG, "flush filter id :" + mFilter.getId() + " result : " + result);
        } else {
            Log.e(TAG, "filter hase close");
        }
        return result;
    }

    private void close() {
        if (null != mFilter) {
            if (DEBUG) Log.d(TAG, "close filter id :" + mFilter.getId());
            setCallback(0);
            mFilter.close();
            mFilter = null;
            mCallbackExecutor = null;
            mFilterCallbackContext = 0;
            mTunerClientId = 0;
        } else {
            Log.e(TAG, "filter hase close");
        }
    }

    private int read(byte[] buffer, long offset, long size) {
        //TBD:need check array length
        int result = Tuner.INVALID_FILTER_ID;
        if (null != mFilter) {
            result = mFilter.read(buffer, offset, size);
            //Log.d(TAG, "read filter id :" + mFilter.getId() + " offset : " + offset + " size : " + size
            //+ " buffer : " + buffer + " result : " + result);
        } else {
            Log.e(TAG, "filter hase close");
        }
        return result;
    }

    private static class FilterHelp{
        static Method sSetTypeIds;
        static Method sSetCallback;
        static Field sIsClose;

        static {
            try {
                Class<?> Filter = Class.forName("android.media.tv.tuner.filter.Filter");
                sSetTypeIds = Filter.getDeclaredMethod("setType", int.class, int.class);
                sSetCallback = Filter.getDeclaredMethod("setCallback", FilterCallback.class, Executor.class);
                sIsClose = Filter.getDeclaredField("mIsClosed");
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

        private static boolean isFilterClose(android.media.tv.tuner.filter.Filter filter) {
            try {
                sIsClose.setAccessible(true);
                return (boolean) sIsClose.get(filter);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
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
            if (Filter.STATUS_OVERFLOW == status) {
                Log.e(TAG, "onFilterStatusChanged filter id : " + + filter.getId() + " status : " + status );
            }
            //nativeCallbackHandle(filter, null, status);
        }

        private void nativeCallbackHandle(Filter filter, FilterEvent[] events, int status) {
            try {
                if (null != mFilterAdapter) {
                    Log.d(TAG, "onFilterEvent filter id :" + filter.getId());
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