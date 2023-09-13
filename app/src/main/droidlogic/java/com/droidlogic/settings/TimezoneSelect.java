package com.droidlogic.settings;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import com.droidlogic.fragment.ParameterManager;
import org.droidlogic.dtvkit.DtvkitGlueClient;


public class TimezoneSelect {
    private Context mContext;
    private AlertDialog alertDialog;
    String [] time;
    private ParameterManager mParameterManager = null;

    private static final Map<String, Integer> mBrazilRegionIdMap = new HashMap<String, Integer>();
    static {
        mBrazilRegionIdMap.put("America/Noronha", 1);
        mBrazilRegionIdMap.put("America/Sao_Paulo", 3);
        mBrazilRegionIdMap.put("America/Araguaina", 3);
        mBrazilRegionIdMap.put("America/Bahia", 3);
        mBrazilRegionIdMap.put("America/Belem", 3);
        mBrazilRegionIdMap.put("America/Recife", 3);
        mBrazilRegionIdMap.put("America/Maceio", 3);
        mBrazilRegionIdMap.put("America/Cuiaba", 5);
        mBrazilRegionIdMap.put("America/Manaus", 5);
        mBrazilRegionIdMap.put("America/Porto_Velho", 5);
        mBrazilRegionIdMap.put("America/Boa_Vista", 5);
        mBrazilRegionIdMap.put("America/Rio_Branco", 6);
        mBrazilRegionIdMap.put("America/Eirunepe", 7);
    }

    public TimezoneSelect(Context context){
        mContext = context;
        mParameterManager = new ParameterManager(mContext, DtvkitGlueClient.getInstance());
    }

    public void selectTimeZone(String name){
        CountryTimeZone zone = new CountryTimeZone();
        time = zone.getTimezone(name);
        if (time != null) {
            if (time.length > 1) {
                showDialog(getItems(time));
            }
            else{
                setTimeZone(time[0]);
            }
        }
    }

    private String [] getItems(String [] tz){
        String [] title = new String[tz.length];
        int i;

        for (i=0; i<tz.length; i++) {
            String gmt = getGmtName(tz[i]);
            int pos = tz[i].indexOf("/");
            title[i] = tz[i].substring(pos+1);
            title[i] += " " + gmt;
        }

        return title;
    }


    private String getGmtName(String time){
        TimeZone tz = TimeZone.getTimeZone(time);
        Calendar now = Calendar.getInstance();
        now.setTimeZone(tz);
        int zoneOffset = now.get(java.util.Calendar.ZONE_OFFSET);
        zoneOffset /= 60000; //min
        int h = zoneOffset / 60;
        int m = zoneOffset % 60;

        String gmt;
        String str="";
        if (h<0) {
            str = "-";
        }
        else if (h>0) {
            str += "+";
        }

        gmt = String.format("GMT%s%02d:%02d", str, h, m);
        return gmt;
    }

    private void setTimeZone(String time) {
        AlarmManager mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        mAlarmManager.setTimeZone(time);
    }

    private void showDialog(String[] items){
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(mContext);
        alertBuilder.setTitle("Select Timezone:");
        alertBuilder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                //Toast.makeText(mContext, items[i], Toast.LENGTH_SHORT).show();
                setTimeZone(time[i]);
                mParameterManager.setCountryRegionId(mBrazilRegionIdMap.get(time[i]) == null ? i + 1 : mBrazilRegionIdMap.get(time[i]));
                alertDialog.dismiss();
            }
        });
        alertBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                setTimeZone(time[0]);
                mParameterManager.setCountryRegionId(1);
            }
        });
        alertDialog = alertBuilder.create();
        alertDialog.show();
    }
}
