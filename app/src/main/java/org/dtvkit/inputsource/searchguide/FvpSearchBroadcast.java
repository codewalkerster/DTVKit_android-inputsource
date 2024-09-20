package com.droidlogic.dtvkit.inputsource.searchguide;

import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;

import org.droidlogic.dtvkit.DtvkitGlueClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.stream.Stream;

import com.droidlogic.dtvkit.companionlibrary.model.Channel;
import com.droidlogic.dtvkit.companionlibrary.model.InternalProviderData;

public class FvpSearchBroadcast {
    private static final String TAG = "FvpSearchBroadcast";
    private static final String DTVKIT_INPUTID = "com.droidlogic.dtvkit.inputsource/.DtvkitTvInput/HW19";


    public static void sendBroadcast(Context context) {
        Intent intent = new Intent();
        Bundle data = new Bundle();

        Log.d(TAG, "sendScanFinishBroadcast");

        data.putString("auth_url", prepareAuthUrl());
        data.putIntegerArrayList("nid_list", getNetworkIdGroup(context));
        intent.setAction("com.android.tv.fvp.INTENT_ACTION");
        intent.setComponent(new ComponentName("com.droidlogic.android.tv", "com.android.tv.fvp.FvpIntentReceiver"));
        intent.putExtra("FVP_TYPE", "scan_action");
        intent.putExtra("FVP_CONFIG", data);
        context.sendBroadcast(intent);
    }

    private static String prepareAuthUrl() {
        String authUrl = null;
        try {
            //JSONObject obj = DtvkitGlueClient.getInstance().request("Hbbtv.HBBGetAuthUrl", new JSONArray());
            JSONObject obj = DtvkitGlueClient.getInstance().request("Fvp.FVPGetAuthUrl", new JSONArray());
            if (obj.getBoolean("accepted")) {
                JSONArray data = obj.getJSONArray("data");
                if (data != null) {
                    //get last one
                    if (data.length() > 0) {
                        JSONObject auth = data.getJSONObject(data.length()-1);
                        authUrl = auth.getString("AuthURL");
                    }
                }
                Log.i(TAG, "prepareAuthUrl authUrl = " + authUrl);
            }
        } catch (Exception e) {
            Log.i(TAG, "prepareAuthUrl Exception = " + e.getMessage());
        }
        return authUrl;
    }

    private static ArrayList<Integer> getNetworkIdGroup(Context context) {
        ArrayList<Integer> networkIdList = new ArrayList();
        Uri channelsUri = TvContract.buildChannelsUriForInput(DTVKIT_INPUTID);
        String[] projection = {Channels.COLUMN_TYPE, Channels.COLUMN_ORIGINAL_NETWORK_ID, Channels.COLUMN_INTERNAL_PROVIDER_DATA};
        String selection = TvContract.Channels.COLUMN_TYPE + " =? OR " + TvContract.Channels.COLUMN_TYPE + " =? ";
        String [] selectionArgs = {"TYPE_DVB_T", "TYPE_DVB_T2"};

        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            InternalProviderData internalProviderData;
            cursor = resolver.query(channelsUri, projection, selection, selectionArgs, null);
            while (cursor != null && cursor.moveToNext()) {
                //Integer networkId = Integer.valueOf(cursor.getInt(1));
                int type = cursor.getType(2);//InternalProviderData type
                if (type == Cursor.FIELD_TYPE_BLOB) {
                    byte[] internalProviderByteData = cursor.getBlob(2);
                    if (internalProviderByteData != null) {
                        internalProviderData = new InternalProviderData(internalProviderByteData);
                        if (internalProviderData != null) {
                            int networkId = Integer.parseInt((String)internalProviderData.get(Channel.KEY_NETWORK_ID));
                            if (!networkIdList.contains(networkId)) {
                                networkIdList.add(networkId);
                            }
                        }
                    }
                } else {
                    Log.i(TAG, "COLUMN_INTERNAL_PROVIDER_DATA other type");
                }
            }
            Stream.of(networkIdList).forEach(r -> Log.d(TAG, "save networkId = " + r));
        } catch (Exception e) {
            Log.e(TAG, "cacheProviderChannel Exception = " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return networkIdList;
    }
}

