package com.droidlogic.dtvkit.inputsource;

import android.media.tv.TvContract;
import android.media.tv.TvContentRating;
import android.net.Uri;
import android.util.Log;
import android.text.TextUtils;

import com.droidlogic.dtvkit.companionlibrary.EpgSyncJobService;
import com.droidlogic.dtvkit.companionlibrary.model.Channel;
import com.droidlogic.dtvkit.companionlibrary.model.EventPeriod;
import com.droidlogic.dtvkit.companionlibrary.model.InternalProviderData;
import com.droidlogic.dtvkit.companionlibrary.model.Program;
import com.droidlogic.dtvkit.companionlibrary.utils.TvContractUtils;
import org.droidlogic.dtvkit.DtvkitGlueClient;

import com.droidlogic.dtvkit.inputsource.parental.ContentRatingsParser;
import com.droidlogic.dtvkit.inputsource.util.FeatureUtil;
import com.droidlogic.settings.PropSettingManager;
import com.droidlogic.fragment.ParameterManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DtvkitEpgSync extends EpgSyncJobService {
    private static final String TAG = "EpgSyncJobService";
    private static final Object mLock = new Object();
    private static JSONArray mDTvServices = null;
    private static JSONArray mATvServices = null;

    public static final int SIGNAL_QPSK = 1; // digital satellite
    public static final int SIGNAL_COFDM = 2; // digital terrestrial
    public static final int SIGNAL_QAM = 4; // digital cable
    public static final int SIGNAL_ISDBT = 5;
    // public static final int SIGNAL_ANALOG = 8;
    boolean mIsUK = false;

    /* called after channel search */
    public static JSONArray getServicesList() throws Exception {
        return getDvbServicesList("cur", "all");
    }

    private static JSONArray getDvbServicesList(String signal_type, String tv_type) throws Exception {
        JSONArray param;
        JSONArray services = new JSONArray();
        int index = 0;
        final int maxTransChannelsSize = 512;
        while (true) {
            param = new JSONArray();
            param.put(signal_type); // signal_type
            param.put(tv_type); // tv_radio type
            param.put(index);
            param.put(maxTransChannelsSize);
            JSONObject obj = DtvkitGlueClient.getInstance().request("Dvb.getListOfServicesByIndex", param);
            JSONArray tmpServices = obj.optJSONArray("data");
            if (tmpServices == null) {
                break;
            } else {
                for (int i = 0; i < tmpServices.length(); i++) {
                    services.put(tmpServices.get(i));
                }
                index += tmpServices.length();
                if (tmpServices.length() < maxTransChannelsSize) {
                    break;
                }
            }
        }
        return services;
    }

    public static JSONArray getAtvServicesList() throws Exception {
        JSONArray param;
        JSONArray services = new JSONArray();
        int index = 0;
        final int maxTransChannelsSize = 512;
        while (true) {
            param = new JSONArray();
            param.put(index);
            param.put(maxTransChannelsSize);
            JSONObject obj = DtvkitGlueClient.getInstance().request("Atv.getListOfServicesByIndex", param);
            JSONArray tmpServices = obj.optJSONArray("data");
            if (tmpServices == null) {
                break;
            } else {
                for (int i = 0; i < tmpServices.length(); i++) {
                    services.put(tmpServices.get(i));
                }
                index += tmpServices.length();
                if (tmpServices.length() < maxTransChannelsSize) {
                    break;
                }
            }
        }
        Log.i(TAG, "Get Atv channels : " + services.length());
        return services;
    }

    public static void setServicesToSync(JSONArray services) {
        synchronized (mLock) {
            mDTvServices = services;
        }
    }

    public static void setATvServicesToSync(JSONArray services) {
        synchronized (mLock) {
            mATvServices = services;
        }
    }

    @Override
    public List<Channel> getChannels(boolean syncCurrent) {
        if (syncCurrent && TextUtils.isEmpty(getChannelTypeFilter())) {
            setChannelTypeFilter(TvContractUtils.dvbSourceToChannelTypeString(getCurrentDvbSource()));
        }
        List<Channel> channels = new ArrayList<>(getDvbChannels(syncCurrent));
        if (TvContractUtils.dvbSourceToInt(getChannelTypeFilter()) == SIGNAL_ISDBT) {
            channels.addAll(getAtvChannels());
        }
        return channels;
    }

    public List<Channel> getAtvChannels() {
        List<Channel> channels = new ArrayList<>();
        Log.i(TAG, "Get atvChannels for db sync");
        try {
            JSONObject serviceNumberObj = DtvkitGlueClient.getInstance().request("Atv.getNumberOfServices", new JSONArray());
            int channelNumber = serviceNumberObj.optInt("data", 0);
            Log.i(TAG, "Total " + channelNumber + " atv channels to sync");
            if (channelNumber <= 0) {
                return channels;
            }
            JSONArray services = new JSONArray();
            synchronized (mLock) {
                if (mATvServices != null && channelNumber == mATvServices.length()) {
                    for (int i = 0; i < channelNumber; i++) {
                        services.put(mATvServices.get(i));
                    }
                }
                mATvServices = null;
            }
            if (services.length() == 0) {
                services = getAtvServicesList();
            }
            for (int i = 0; i < services.length(); i++) {
                JSONObject service = services.getJSONObject(i);
                String ATVName = service.getString("Name");
                String ATVDisplayNumber = null;
                int vstd = service.getInt("VStd");
                if (ATVName.length() == 0)
                    ATVName = "xxxATV Program";
                if (ATVName.startsWith("xxxATV Program"))
                    ATVName = ATVName;
                if (service.getInt("Lcn") != -1)
                    ATVDisplayNumber=""+service.getInt("Lcn")+"-"+0;

                InternalProviderData data = new InternalProviderData("ATV");
                data.put("vfmt", service.getInt("VFmt"));
                data.put("frequency", service.getInt("Freq"));
                data.put("video_std", service.getInt("VStd"));
                data.put("audio_std", service.getInt("AStd"));
                data.put("is_auto_std", service.optInt("isAutoStd"));
                data.put("fine_tune", 0);
                data.put("audio_compensation", 0);
                data.put("is_favourite", 0);
                data.put("multi_name", ATVName);
                JSONObject feMap = new JSONObject();
                feMap.put("vtd", service.getInt("VStd"));
                feMap.put("atd", service.getInt("AStd"));
                feMap.put("vfmt", service.getInt("VFmt"));
                feMap.put("freq", service.getInt("Freq"));
                data.put("unikey", service.getInt("Unikey"));
                feMap.put("mode", 0);
                feMap.put("soundsys", -1);
                data.put("fe", feMap.toString());
                data.put("majorNum", service.getInt("Lcn"));
                data.put("minorNum", 0);
                data.put("srcId", -1);
                data.put("access", 0);
                data.put("hidden", false);
                data.put("set_hidden", 0);
                data.put("hideGuide", 0);
                data.put("audio_pids", null);
                data.put("AUDIO_FORMATS", null);
                data.put("audio_exts", null);
                data.put("audio_langs", null);
                data.put("audio_track_index", 0);
                data.put("audio_out_mode", -1);
                data.put("audio_channel", 0);
                data.put("subt_types", 0);
                data.put("subt_pids", null);
                data.put("subt_stypes", null);
                data.put("subt_id1s", null);
                data.put("subt_id2s", null);
                data.put("subt_langs", null);
                data.put("subt_track_index", -1);
                data.put("content_ratings", "");
                data.put("signal_type", service.getInt("SigType") == 0 ? TvContract.Channels.TYPE_ATSC_T :TvContract.Channels.TYPE_ATSC_C);

                channels.add(new Channel.Builder()
                        .setDisplayName(ATVName)
                        .setType(TvContractUtils.videoStdToType(vstd))
                        .setDisplayNumber(ATVDisplayNumber)
                        .setServiceType(TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
                        .setOriginalNetworkId(0)
                        .setTransportStreamId(0)
                        .setServiceId(0)
                        .setInternalProviderData(data)
                        .setLocked(service.getBoolean("Block") == true ? 1: 0)
                        .setChannelAntennaType(service.getInt("SigType"))
                        .build());
            }
        } catch (Exception e) {
            Log.e(TAG, "allowed getAtvChannels Exception = " + e.getMessage());
        }
        return channels;
    }

    public List<Channel> getDvbChannels(boolean syncCurrent) {
        mIsUK = "gbr".equals(ParameterManager.getCurrentCountryIso3Name());
        List<Channel> channels = new ArrayList<>();

        Log.i(TAG, "Get channels for epg sync, current: " + syncCurrent);

        try {
            JSONArray param = new JSONArray();
            param.put(false);
            param.put(syncCurrent ? "cur" : "all"); // signal_type
            param.put("all"); // tv/radio all
            JSONObject serviceNumberObj = DtvkitGlueClient.getInstance().request("Dvb.getNumberOfServices", param);
            int channelNumber = serviceNumberObj.optInt("data", 0);
            Log.i(TAG, "Total " + channelNumber + " Dtv channels to sync");
            if (channelNumber <= 0) {
                List<Channel> ipChannelList = getIpChannel();
                if ((null != ipChannelList) && (0 < ipChannelList.size())) {
                    channels.addAll(ipChannelList);
                }
                return channels;
            }
            JSONArray services = new JSONArray();
            synchronized (mLock) {
                if (mDTvServices != null && channelNumber == mDTvServices.length()) {
                    for (int i = 0; i < channelNumber; i++) {
                        services.put(mDTvServices.get(i));
                    }
                }
                mDTvServices = null;
            }
            if (services.length() == 0) {
                services = getDvbServicesList(syncCurrent ? "cur" : "all", "all");
            }
            Log.i(TAG, "Finally getChannels size=" + services.length());
            boolean ciTest = PropSettingManager.getBoolean(PropSettingManager.CI_PROFILE_ADD_TEST, false);

            for (int i = 0; i < services.length(); i++) {
                JSONObject service = services.getJSONObject(i);
                String uri = service.getString("uri");

                InternalProviderData data = new InternalProviderData();
                data.put("dvbUri", uri);
                data.put("hidden", service.getBoolean("hidden"));
                data.put("network_id", service.getInt("network_id"));
                data.put("frequency", service.getInt("freq"));
                JSONObject satellite = new JSONObject();
                satellite.put("satellite_info_name", service.getString("sate_name"));
                data.put("satellite_info", satellite.toString());
                JSONObject transponder = new JSONObject();
                String transponderDisplay = service.getString("transponder");
                transponder.put("transponder_info_display_name", transponderDisplay);
                String[] splitTransponder = transponderDisplay.split("/");
                if (splitTransponder.length == 3) {
                    transponder.put("transponder_info_satellite_name", service.getString("sate_name"));
                    transponder.put("transponder_info_frequency", splitTransponder[0]);
                    transponder.put("transponder_info_polarity", splitTransponder[1]);
                    transponder.put("transponder_info_symbol", splitTransponder[2]);
                }
                data.put("transponder_info", transponder.toString());
                data.put("video_pid", service.getInt("video_pid"));
                data.put("video_codec", service.getString("video_codec"));
                data.put("is_data", service.getBoolean("is_data"));
                data.put("channel_signal_type", service.getString("sig_name"));
                data.put("scrambled", service.getBoolean("scrambled")?1:0);//to match with droidlogic_tv.jar
                if (service.has("is_hdtv")) {
                    data.put("is_hdtv", service.getBoolean("is_hdtv")?1:0);
                } else {
                    data.put("is_hdtv", 0);
                }
                if (service.has("mod")) {
                    data.put("modulation", parseModulationInt(service.getString("mod")));
                } else {
                    data.put("modulation", parseModulationInt("auto"));
                }
                if (service.has("fec")) {
                    data.put("fec", service.getString("fec"));
                } else {
                    data.put("fec", "auto");
                }
                if (ciTest && (i % 4 != 0)) {
                    int countFlag = i % 4;
                    data.put("ci_number", countFlag);
                    data.put("profile_name", "profile_name" + countFlag);
                    data.put("slot_id", "slot_id" + countFlag);
                    data.put("tune_quietly", 0);
                    if (countFlag == 1) {
                        if (i < 4) {
                            data.put("is_virtual_channel", true);
                        } else {
                            data.put("is_virtual_channel", false);
                        }
                        data.put("profile_selectable", "true");
                        data.put("service_type_label", "op test");
                    } else if (countFlag == 2) {
                        data.put("profile_ver", "v1");
                        if (i < 4) {
                            data.put("profile_selectable", "true");
                        } else {
                            data.put("profile_selectable", "false");
                        }
                    } else if (countFlag == 3) {
                        data.put("profile_ver", "v2");
                        if (i < 4) {
                            data.put("profile_selectable", "true");
                        } else {
                            data.put("profile_selectable", "false");
                        }
                    }
                } else {
                    tryToPutStringToInternalProviderData(data, "ci_number", service, "ci_number");
                    tryToPutStringToInternalProviderData(data, "profile_name", service, "profile_name");
                    tryToPutStringToInternalProviderData(data, "profile_selectable", service, "profile_selectable");
                    tryToPutStringToInternalProviderData(data, "slot_id", service, "slot_id");
                    tryToPutIntToInternalProviderData(data, "tune_quietly", service, "tune_quietly");
                    tryToPutStringToInternalProviderData(data, "profile_ver", service, "profile_ver");
                    tryToPutBooleanToInternalProviderData(data, "is_virtual_channel", service, "is_virtual_channel");
                    tryToPutStringToInternalProviderData(data, "service_type_label", service, "service_type_label");
                }
                if (service.has("raw_name")) {
                    data.put("raw_displayname", service.getString("raw_name"));
                } else {
                    data.put("raw_displayname", service.getString("name"));
                }
                if (service.has("raw_lcn")) {
                    data.put("raw_displaynumber", String.format(Locale.ENGLISH, "%d", service.getInt("raw_lcn")));
                } else {
                    data.put("raw_displaynumber", String.format(Locale.ENGLISH, "%d", service.getInt("lcn")));
                }
                if (service.has("category_id")) {
                    data.put("category_id", service.optJSONArray("category_id"));
                }
                String signal_type = service.optString("sig_name", TvContract.Channels.TYPE_OTHER);
                String channelType = TvContractUtils.searchSignalTypeToChannelType(signal_type);
                channels.add(new Channel.Builder()
                        .setDisplayName(service.getString("name"))
                        .setType(channelType)
                        .setDisplayNumber(String.format(Locale.ENGLISH, "%d", service.getInt("lcn")))
                        .setServiceType(service.getBoolean("is_data") ? TvContract.Channels.SERVICE_TYPE_OTHER : (service.getBoolean("radio") ? TvContract.Channels.SERVICE_TYPE_AUDIO :
                                TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO))
                        .setOriginalNetworkId(Integer.parseInt(uri.substring(6, 10), 16))
                        .setTransportStreamId(Integer.parseInt(uri.substring(11, 15), 16))
                        .setServiceId(Integer.parseInt(uri.substring(16, 20), 16))
                        .setInternalProviderData(data)
                        .setLocked(service.optBoolean("blocked")?1:0)
                        .build());
            }
            if (channels.size() > 0) {
                int numberToLog = Math.min(channels.size(), 30);
                Log.d(TAG, "-----> (DEBUG) Last " + numberToLog + " Channels <-----");
                for (int i = Math.max(channels.size() - 30, 0); i < channels.size(); i++) {
                    Log.d(TAG, "** " + channels.get(i).getDisplayNumber()
                            + " " + channels.get(i).getDisplayName()
                            + ", " + channels.get(i).getServiceType());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getChannels Exception = " + e.getMessage());
            throw new UnsupportedOperationException("getChannels Failed");
        }

        List<Channel> ipChannelList = getIpChannel();
        if ((null != ipChannelList) && (0 < ipChannelList.size())) {
            channels.addAll(ipChannelList);
        }
        return channels;
    }

    /*param signalType should not null*/
    @Override
    public boolean checkSignalTypesMatch(String signalType) {
        String currentSignalType = TvContractUtils.dvbSourceToChannelTypeString(getCurrentDvbSource());
        if (signalType.contains("TYPE_")) {
            return currentSignalType.equals(signalType);
        } else {
            return currentSignalType.equals(TvContractUtils.searchSignalTypeToChannelType(signalType));
        }
    }

    private Program parseSingleEvent(JSONObject event, Channel channel, long startMs, long endMs) {
        long startTime, endTime;
        int parental_rating;
        int content_value;
        String content_level_1;
        String content_level_2;
        Program pro = null;
        try {
            InternalProviderData data = new InternalProviderData();
            data.put("dvbUri", getDtvkitChannelUri(channel));
            startTime = event.getLong("startutc") * 1000;
            endTime = event.getLong("endutc") * 1000;
            parental_rating = event.getInt("rating");
            content_value = event.optInt("content_value");
            //for parse fvp event info
            int eventId = parseEventId(event.optString("uri"));
            data.put("ad", event.optBoolean("ad", false));
            data.put("subtitles", event.optBoolean("subtitles", false));

/*
            //parse fvp forward event info
            if (null != forwardEvents) {
                JSONObject forwardEvent = forwardEvents.optJSONObject(i);
                if (null != forwardEvent) {
                    if (eventId == parseEventId(forwardEvent.optString("uri"))) {
                        data.put("caption_language", forwardEvent.optBoolean("caption_language", false));
                        data.put("sign_language", forwardEvent.optBoolean("sign_language", false));
                        data.put("audio_description", forwardEvent.optBoolean("audio_description", false));
                    }
                }
            }
*/
            if (startTime >= endMs || endTime <= startMs) {
                Log.i(TAG, "Skip##  startMs:endMs=["+startMs+":"+endMs+"]  event:startT:endT=["+startTime+":"+endTime+"]");
            } else {
                content_level_1 = event.getString("content_level_1");
                content_level_2 = event.getString("content_level_2");
                String[] genres = getGenres(event.getString("genre"), content_value);
                if (channel.getType().startsWith("TYPE_ISDB")) {
                    if (!TextUtils.isEmpty(content_level_1) && !TextUtils.isEmpty(content_level_2)) {
                        data.put("genre", content_level_1 + " - " + content_level_2);
                    } else if (!TextUtils.isEmpty(content_level_1) || !TextUtils.isEmpty(content_level_2)) {
                        data.put("genre", !TextUtils.isEmpty(content_level_1) ? content_level_1 : content_level_2);
                    }
                } else {
                    String genre_str;
                    if (genres.length == 0) {
                        genre_str = (content_value <= 0xff) ? content_level_1 : content_level_2;
                        if (!TextUtils.isEmpty(genre_str)) {
                            data.put("genre", genre_str);
                        }
                    }
                }

                if (!TextUtils.isEmpty(event.getString("guidance"))) {
                    data.put("guidance", event.getString("guidance"));
                }

                pro = new Program.Builder()
                        .setChannelId(channel.getId())
                        .setSignalType(TvContractUtils.dvbSourceToInt(channel.getType()))
                        .setTitle(event.optString("name"))
                        .setStartTimeUtcMillis(startTime)
                        .setEndTimeUtcMillis(endTime)
                        .setDescription(event.optString("description"))
                        .setLongDescription(event.optString("description_extern"))
                        .setCanonicalGenres(genres)
                        .setInternalProviderData(data)
                        .setContentRatings(parental_rating == 0 ? null : parseParentalRatings(parental_rating, event.getString("name"), channel.getType().startsWith("TYPE_ISDB")))
                        .setEventId(eventId)
                        .build();
            }
        } catch (JSONException ignored) {
            return null;
        }
        return pro;
    }

    @Override
    public void getProgramsForChannel(List<Program> container, Uri channelUri, Channel channel, long startMs, long endMs) {
        try {
            String dvbUri = getDtvkitChannelUri(channel);
            if (DEBUG) {
                Log.i(TAG, String.format("Get channel programs for epg sync. Uri %s, startMs %d, endMs %d",
                        dvbUri, startMs, endMs));
            }
            JSONArray args = new JSONArray();
            args.put(dvbUri); // uri
            args.put(startMs/1000);
            args.put(endMs/1000);
            JSONArray events = DtvkitGlueClient.getInstance()
                    .request("Dvb.getListOfEvents", args)
                    .getJSONArray("data");
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                Program pro = parseSingleEvent(event, channel, startMs, endMs);
                if (pro != null) {
                    container.add(pro);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getProgramsForChannel:" + e.getMessage());
        }
    }

    @Override
    public int getProgramsForChannelByBatch(List<Program> container, Uri channelUri, Channel channel) {
        /* Get 8-days programs */
        /* Split 2-3-3 to prevent binder alloc fail when too many programs */
        long start = PropSettingManager.getCurrentStreamTime(true);
        long end = start + TimeUnit.DAYS.toMillis(2);
        int before = container.size();
        for (int j = 0; j < 3; j++) {
            // Log.d(TAG, "test " + channelUri + " epg from " + (start/1000) + " to " + (end/1000));
            getProgramsForChannel(container, channelUri, channel, start, end);
            // plus 1s to next recycle
            start = end + 1000;
            end = start + TimeUnit.DAYS.toMillis(3);
        }
        int after = container.size();
        return after - before;
    }

    @Override
    public List<Program> getAllProgramsForChannel(Uri channelUri, Channel channel) {
        List<Program> programs = new ArrayList<>();
        String dvbUri = getDtvkitChannelUri(channel);

        try {
            JSONArray args = new JSONArray();
            args.put(dvbUri); // uri
            JSONArray events = DtvkitGlueClient.getInstance()
                    .request("Dvb.getListOfEvents", args)
                    .getJSONArray("data");

            //JSONArray forwardEvents = getFvpForwardProgramInfo(events); //get fvp forward event and parse
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                Program pro = parseSingleEvent(event, channel, 0, Long.MAX_VALUE);
                if (pro != null) {
                    programs.add(pro);
                }
            }
        } catch (Exception e) {
            int size = getProgramsForChannelByBatch(programs, channelUri, channel);
            Log.i(TAG, "get " + channelUri +" epg number: "+ size);
        }
        if (DEBUG && programs.size() > 0) {
            Log.i(TAG, "%% channel[" + channel.getDisplayName()
                    + "], ## programs[" + programs.size() + "] ##, Uri:" + dvbUri);
        }
        return programs;
    }

    public List<Program> getNowNextProgramsForChannel(Uri channelUri, Channel channel) {
        List<Program> programs = new ArrayList<>();

        try {
            String dvbUri = getDtvkitChannelUri(channel);

            Log.i(TAG, "Get channel now next programs for epg sync. Uri " + dvbUri);

            JSONArray args = new JSONArray();
            args.put(dvbUri); // uri
            JSONObject events = DtvkitGlueClient.getInstance().request("Dvb.getNowNextEvents", args).getJSONObject("data");

            JSONObject now = events.optJSONObject("now");
            if (now != null) {
                InternalProviderData data = new InternalProviderData();
                data.put("dvbUri", dvbUri);
                int content_value = now.optInt("content_value");
                String content_level_1 = now.getString("content_level_1");
                String content_level_2 = now.getString("content_level_2");
                String[] genres = getGenres(now.getString("genre"), content_value);
                if (channel.getType().startsWith("TYPE_ISDB")) {
                    if (!TextUtils.isEmpty(content_level_1) && !TextUtils.isEmpty(content_level_2)) {
                        data.put("genre", content_level_1 + " - " + content_level_2);
                    } else if (!TextUtils.isEmpty(content_level_1) || !TextUtils.isEmpty(content_level_2)) {
                        data.put("genre", !TextUtils.isEmpty(content_level_1) ? content_level_1 : content_level_2);
                    }
                } else {
                    String genre_str;
                    if (genres.length == 0) {
                        genre_str = (content_value <= 0xff) ? content_level_1 : content_level_2;
                        if (!TextUtils.isEmpty(genre_str)) {
                            data.put("genre", genre_str);
                        }
                    }
                }

                if (!TextUtils.isEmpty(now.getString("guidance"))) {
                    data.put("guidance",now.getString("guidance"));
                }

                programs.add(new Program.Builder()
                        .setChannelId(channel.getId())
                        .setSignalType(TvContractUtils.dvbSourceToInt(channel.getType()))
                        .setTitle(now.getString("name"))
                        .setStartTimeUtcMillis(now.getLong("startutc") * 1000)
                        .setEndTimeUtcMillis(now.getLong("endutc") * 1000)
                        .setDescription(now.getString("description"))
                        .setCanonicalGenres(genres)
                        .setInternalProviderData(data)
                        .build());
            }

            JSONObject next = events.optJSONObject("next");
            if (next != null) {
                InternalProviderData data = new InternalProviderData();
                data.put("dvbUri", dvbUri);
                int content_value = next.optInt("content_value");
                String content_level_1 = next.getString("content_level_1");
                String content_level_2 = next.getString("content_level_2");
                String[] genres = getGenres(next.getString("genre"), content_value);
                if (channel.getType().startsWith("TYPE_ISDB")) {
                    if (!TextUtils.isEmpty(content_level_1) && !TextUtils.isEmpty(content_level_2)) {
                        data.put("genre", content_level_1 + " - " + content_level_2);
                    } else if (!TextUtils.isEmpty(content_level_1) || !TextUtils.isEmpty(content_level_2)) {
                        data.put("genre", !TextUtils.isEmpty(content_level_1) ? content_level_1 : content_level_2);
                    }
                } else {
                    String genre_str;
                    if (genres.length == 0) {
                        genre_str = (content_value <= 0xff) ? content_level_1 : content_level_2;
                        if (!TextUtils.isEmpty(genre_str)) {
                            data.put("genre", genre_str);
                        }
                    }
                }

                if (!TextUtils.isEmpty(next.getString("guidance"))) {
                    data.put("guidance",next.getString("guidance"));
                }

                programs.add(new Program.Builder()
                        .setChannelId(channel.getId())
                        .setSignalType(TvContractUtils.dvbSourceToInt(channel.getType()))
                        .setTitle(next.getString("name"))
                        .setStartTimeUtcMillis(next.getLong("startutc") * 1000)
                        .setEndTimeUtcMillis(next.getLong("endutc") * 1000)
                        .setDescription(next.getString("description"))
                        .setCanonicalGenres(genres)
                        .setInternalProviderData(data)
                        .build());
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return programs;
    }

    @Override
    public List<EventPeriod> getListOfUpdatedEventPeriods()
    {
        List<EventPeriod> eventPeriods = new ArrayList<>();

        Log.i(TAG, "getListOfUpdatedEventPeriods");

        try {
            JSONArray periods = DtvkitGlueClient.getInstance().request("Dvb.getListOfUpdatedEventPeriods", new JSONArray()).getJSONArray("data");

            Log.i(TAG, periods.toString());

            for (int i = 0; i < periods.length(); i++)
            {
                JSONObject period = periods.getJSONObject(i);

                eventPeriods.add(new EventPeriod(period.getString("uri"),period.getLong("startutc"),period.getLong("endutc")));
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return eventPeriods;
    }

    /* getGenres is hotSpot function */
    private String[] getGenres(String genre, int content_value) {
        if (content_value > 0xff) {
            //tv provider don't support level 2
            return new String[]{};
        } else {
            switch (genre)
            {
                case "movies":
                    return new String[]{TvContract.Programs.Genres.MOVIES};
                case "news":
                    return new String[]{TvContract.Programs.Genres.NEWS};
                case "entertainment":
                    return new String[]{TvContract.Programs.Genres.ENTERTAINMENT};
                case "sport":
                    return new String[]{TvContract.Programs.Genres.SPORTS};
                case "children's":
                    return new String[]{TvContract.Programs.Genres.FAMILY_KIDS};
                case "music":
                    if (mIsUK) {
                        return new String[]{TvContract.Programs.Genres.ENTERTAINMENT};
                    } else {
                        return new String[]{TvContract.Programs.Genres.MUSIC};
                    }
                case "arts":
                    return new String[]{TvContract.Programs.Genres.ARTS};
                case "social":
                    if (mIsUK) {
                        return new String[]{TvContract.Programs.Genres.NEWS};
                    } else {
                        return new String[]{TvContract.Programs.Genres.LIFE_STYLE};
                    }
                case "education":
                    return new String[]{TvContract.Programs.Genres.EDUCATION};
                case "leisure":
                    return new String[]{TvContract.Programs.Genres.LIFE_STYLE};
                default:
                    return new String[]{};
            }
        }
    }

    private TvContentRating[] parseParentalRatings(int parentalRating, String title, boolean isISDBType)
    {
        TvContentRating[] ratings_array = new TvContentRating[1];
        String ratingDomain = ContentRatingsParser.DOMAIN_SYSTEM_RATINGS;
        if (isISDBType) {
            String ratingSystemDefinition = "ISDB";
            int parentalRatingContentIndex = parentalRating / 16;
            int parentalRatingAge = parentalRating % 16;
            String[] ISDB_RatingContent = {"Drugs", "Violence", "Violence and Drugs", "Sex", "Sex and Drugs", "Violence and Sex", "Violence, Sex and Drugs"};
            String[] ISDB_RatingAge = {"ISDB_L", "ISDB_10", "ISDB_12", "ISDB_14", "ISDB_16", "ISDB_18"};
            TvContentRating rating;
            if (parentalRatingContentIndex > 0) {
                if (parentalRatingAge > 0 && parentalRatingAge <= 6) {
                    rating = TvContentRating.createRating(ratingDomain, ratingSystemDefinition, ISDB_RatingContent[parentalRatingContentIndex - 1] + "/" + ISDB_RatingAge[parentalRatingAge - 1] , (String) null);
                } else {
                    rating = TvContentRating.createRating(ratingDomain, ratingSystemDefinition, ISDB_RatingContent[parentalRatingContentIndex - 1], (String) null);
                }
            } else if (parentalRatingAge > 0 && parentalRatingAge <= 6) {
                rating = TvContentRating.createRating(ratingDomain, ratingSystemDefinition, ISDB_RatingAge[parentalRatingAge - 1], (String) null);
            } else {
                rating = null;
            }
            if (rating != null) {
                ratings_array[0] = rating;
                Log.d(TAG, "parse ratings add rating:" + rating.flattenToString() + ", title = " + title + " ,parentalRating = " + parentalRating);
            } else {
                ratings_array = null;
            }
        } else {
            String ratingSystemDefinition = "DVB";
            String[] DVB_ContentRating = {"DVB_4", "DVB_5", "DVB_6", "DVB_7", "DVB_8", "DVB_9", "DVB_10",
                    "DVB_11", "DVB_12", "DVB_13", "DVB_14", "DVB_15", "DVB_16", "DVB_17", "DVB_18", "DVB_19"};

            parentalRating += 3; //minimum age = rating + 3 years
//        Log.d(TAG, "parseParentalRatings parentalRating:"+ parentalRating + ", title = " + title);
            if (parentalRating >= 4 && parentalRating <= 19) {
                TvContentRating r = TvContentRating.createRating(ratingDomain, ratingSystemDefinition, DVB_ContentRating[parentalRating - 4], (String) null);
                if (r != null) {
                    ratings_array[0] = r;
                    Log.d(TAG, "parse ratings add rating:" + r.flattenToString() + ", title = " + title);
                }
            } else {
                ratings_array = null;
            }
        }

        return ratings_array;
    }

    private void tryToPutStringToInternalProviderData(InternalProviderData data, String key1, JSONObject obj, String key2) {
        boolean result = false;
        if (data != null && obj != null && key1 != null && key2 != null) {
            try {
                data.put(key1, obj.getString(key2));
                result = true;
            } catch (Exception e) {
                //Log.e(TAG, "tryToPutStringToInternalProviderData key2 = " + key2 + "Exception = " + e.getMessage());
            }
        }
    }

    private void tryToPutIntToInternalProviderData(InternalProviderData data, String key1, JSONObject obj, String key2) {
        boolean result = false;
        if (data != null && obj != null && key1 != null && key2 != null) {
            try {
                data.put(key1, obj.getInt(key2));
                result = true;
            } catch (Exception e) {
                //Log.e(TAG, "tryToPutIntToInternalProviderData key2 = " + key2 + "Exception = " + e.getMessage());
            }
        }
    }

    private void tryToPutBooleanToInternalProviderData(InternalProviderData data, String key1, JSONObject obj, String key2) {
        boolean result = false;
        if (data != null && obj != null && key1 != null && key2 != null) {
            try {
                data.put(key1, obj.getBoolean(key2));
                result = true;
            } catch (Exception e) {
                //Log.e(TAG, "tryToPutBooleanToInternalProviderData key2 = " + key2 + "Exception = " + e.getMessage());
            }
        }
    }

    private String getDtvkitChannelUri(Channel channel) {
        String result = null;
        if (channel != null) {
            try {
                InternalProviderData data = channel.getInternalProviderData();
                if (data != null) {
                    result = (String)data.get(Channel.KEY_DTVKIT_URI);
                } else {
                    result = String.format("dvb://%04x.%04x.%04x", channel.getOriginalNetworkId(), channel.getTransportStreamId(), channel.getServiceId());
                }
            } catch (Exception e) {
                Log.e(TAG, "getDtvkitChannelUri Exception = " + e.getMessage());
            }
        }
        return result;
    }

    private int getCurrentDvbSource() {
        int source = SIGNAL_COFDM;
        try {
            JSONObject sourceReq = DtvkitGlueClient.getInstance().request("Dvb.GetDvbSource", new JSONArray());
            if (sourceReq != null) {
                source = sourceReq.optInt("data");
            }
        } catch (Exception e) {
        }
        return source;
    }

    private int parseModulationInt(String modulation) {
        /*modulation int values refs to: droidlogic_tv.jar: TvChannelParams.java*/
        int mod = 6;//MODULATION_QAM_AUTO
        if (TextUtils.isEmpty(modulation)) {
            return mod;
        }
        switch (modulation) {
            case "qpsk":
                mod = 0;//MODULATION_QPSK
                break;
            case "16qam":
                mod = 1;//MODULATION_QAM_16
                break;
            case "8psk":
                mod = 9;//MODULATION_PSK_8
                break;
            case "16apsk":
                mod = 10;//MODULATION_APSK_16
                break;
            case "32apsk":
                mod = 11;//MODULATION_APSK_32
                break;
            case "dqpsk":
                mod = 12;//MODULATION_DQPSK
                break;
            case "32qam":
                mod = 2;//MODULATION_QAM_32
                break;
            case "64qam":
                mod = 3;//MODULATION_QAM_64
                break;
            case "128qam":
                mod = 4;//MODULATION_QAM_128
                break;
            case "256qam":
                mod = 5;//MODULATION_QAM_256
                break;
            default:
                mod = 6;//MODULATION_QAM_AUTO
                break;
        }
        return mod;
    }

    private List<Channel> getIpChannel() {
        if (!mIsUK || !FeatureUtil.getFeatureSupportFvp()) {
            Log.e(TAG, "not support get Ip channel ");
            return null;
        }
        try {
            JSONObject obj = DtvkitGlueClient.getInstance().request("Fvp.FvpGetClmIpServicesInfo", new JSONArray()).getJSONObject("data");
            if (null == obj) {
                Log.e(TAG, "getIpChannel error no ip channel data ");
                return null;
            }
            int vnid = obj.getInt("ondemand_vnid");
            int virtualNetworkId = obj.getInt("virtual_network_id");
            JSONArray ipChannelArray = obj.getJSONArray("ip_service_details");
            if ((null == ipChannelArray) || (0 == ipChannelArray.length())) {
                Log.e(TAG, "getIpChannel no ip channel array ");
                return null;
            }
            List<Channel> ipChannelList = new ArrayList<>();
            Log.d(TAG, "IP Channel number : " + ipChannelArray.length());
            for (int i = 0; i < ipChannelArray.length(); i ++) {
                JSONObject ipChannelInfo = ipChannelArray.getJSONObject(i);
                if (null == ipChannelInfo) {
                    continue;
                }
                Log.d(TAG, "Index : " + i + " IP Channel info : " + ipChannelInfo);
                //prepare ip channel private data
                InternalProviderData data = new InternalProviderData();
                data.put("ondemand_vnid", vnid);
                data.put("virtual_network_id", virtualNetworkId);
                data.put("mds_service_name", ipChannelInfo.getString("mds_service_name"));
                data.put("service_type", ipChannelInfo.getString("service_type"));
                data.put("service_attributes", ipChannelInfo.getString("service_attributes"));
                if (TextUtils.equals("Hidden and not selectable", ipChannelInfo.getString("service_attributes"))) {
                    Log.d(TAG, "This ip channel not save");
                    continue;
                } else if (TextUtils.equals("Hidden but selectable", ipChannelInfo.getString("service_attributes"))) {
                    data.put("hidden", "true");
                }
                //Save Ip channel to List
                ipChannelList.add(new Channel.Builder()
                        .setDisplayName(ipChannelInfo.getString("short_service_name"))
                        .setDisplayNumber(String.format(Locale.ENGLISH, "%d", ipChannelInfo.getInt("Lcn")))
                        .setOriginalNetworkId(ipChannelInfo.getInt("Onid"))
                        .setTransportStreamId(ipChannelInfo.getInt("Tsid"))
                        .setServiceId(ipChannelInfo.getInt("Sid"))
                        .setSearchable(TextUtils.equals(ipChannelInfo.getString("service_attributes"), "Visible and selectable"))
                        .setInternalProviderData(data)
                        .setServiceType(TvContract.Channels.SERVICE_TYPE_OTHER)
                        .setType(TvContract.Channels.TYPE_DVB_T)
                        .build());
            }
            Log.d(TAG, "ipChannelList size : " + ipChannelList.size());
            return ipChannelList;
        } catch (Exception e) {
            Log.d(TAG, "getIpChannel error : " + e.getMessage());
        }
        return null;
    }

    private JSONArray getFvpForwardProgramInfo(JSONArray events) {
        JSONArray forwardEvents = null;

        if (TextUtils.equals(EpgSyncJobService.getChannelTypeFilter(), "TYPE_DVB_T") && FeatureUtil.getFeatureSupportFvp()) {
            try {
                JSONArray args = new JSONArray();
                for (int i = 0; i < events.length(); i++) {
                    JSONObject event = events.getJSONObject(i);
                    String eventUri = event.getString("uri");
                    args.put(i, eventUri);
                }
                if (0 != args.length()) {
                    forwardEvents = DtvkitGlueClient.getInstance().request("Fvp.FvpGetForwardProgramInfo", args).getJSONArray("data");
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        if (null != forwardEvents) {
            Log.d(TAG, "getFvpForwardProgramInfo |events length = " + events.length() + "|forwardEvents = " + forwardEvents.length());
        } else {
            Log.d(TAG, "getFvpForwardProgramInfo not have fvp info");
        }
        return forwardEvents;
    }

    private int parseEventId(String eventUri) {
        int eventId = 0;
        if (null != eventUri) {
            String temp[];
            temp = eventUri.split(";");
            try{
                if ((null != temp[1])) {
                    eventId = Integer.parseInt(temp[1], 16);
                }
            } catch(Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
        //Log.d(TAG, "|eventUri = " + eventUri + "|eventId = " + eventId);
        return eventId;
    }
}
