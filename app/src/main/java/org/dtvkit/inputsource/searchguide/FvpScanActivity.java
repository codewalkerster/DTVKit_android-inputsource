package com.droidlogic.dtvkit.inputsource.searchguide;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.droidlogic.dtvkit.inputsource.searchguide.DtvKitDVBTCScanPresenter;
import com.droidlogic.dtvkit.inputsource.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;


public class FvpScanActivity extends Activity implements com.droidlogic.dtvkit.inputsource.searchguide.UpdateScanView {
    private static final String TAG = "FvpScanActivity";

    private static final int MSG_UPDATE_CHANNEL_NUMBER = 0;
    private static final int MSG_UPDATE_SCAN_PROGRESS = 1;
    private static final int MSG_UPDATE_SCAN_STATUS = 2;
    private static final int MSG_FINISH_SCAN_VIEW = 3;

    DtvKitDVBTCScanPresenter mScanPresenter = null;
    ExoPlayer mExoPlayer;
    TextView mScanStatus;
    TextView mDigitalChannelNumber;
    TextView mIpChannelNumber;
    ProgressBar mScanProgress;
    TextView mScanProgressNumber;
    Handler mMainThreadHandler = new Handler((Message msg)->{
        switch (msg.what) {
            case MSG_UPDATE_SCAN_PROGRESS:{
                mScanProgressNumber.setText(String.valueOf(msg.arg1) + "%");
                mScanProgress.setProgress(msg.arg1, true);
                break;
            }
            case MSG_UPDATE_CHANNEL_NUMBER:{
                mDigitalChannelNumber.setText(String.valueOf(msg.arg1));
                mIpChannelNumber.setText(String.valueOf(msg.arg2));
                break;
            }
            case MSG_UPDATE_SCAN_STATUS:{
                mScanStatus.setText((String)msg.obj);
                break;
            }
            case MSG_FINISH_SCAN_VIEW:{
                finish();
            }
        }
        return true;
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fvp_scan);
        initPlayer();
        initView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        triggerAutomaticScan();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlayback();
        if (null != mScanPresenter) {
            if (mScanPresenter.isStartScan()) {
                mScanPresenter.stopScan(true);
            }
            Log.d(TAG, "onStop isStartScan = " + mScanPresenter.isStartScan() + "|isSyncTvProvider = " + mScanPresenter.isSyncTvProvider());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (null != mScanPresenter) {
            mScanPresenter.releaseScanResource();
            mScanPresenter = null;
        }
    }

    @Override
    public void finish() {
        stopPlayback();
        //Send Message to mds client
        //sendFvpChannelScanMessage();
        sendScanFinishBroadcast();
        Log.d(TAG, "need do some thing when scan view finish");
        super.finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (null == mScanPresenter) {
            finish();
            return true;
        }

        if (mScanPresenter.isSyncTvProvider()) {
            Toast.makeText(this, R.string.sync_tv_provider, Toast.LENGTH_SHORT).show();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mScanPresenter.stopScan(false);
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void updateScanProgress(int progress) {
        Log.d(TAG, "updateScanProgress progress = " + progress);
        if (100 <= progress) {
            mScanPresenter.stopScan(true);
        }
        mMainThreadHandler.removeMessages(MSG_UPDATE_SCAN_PROGRESS);
        Message msg = mMainThreadHandler.obtainMessage(MSG_UPDATE_SCAN_PROGRESS, progress, 0);
        boolean info = mMainThreadHandler.sendMessage(msg);
        Log.d(TAG, "updateScanProgress " + info);
    }

    @Override
    public void updateScanChannelNumber(int digitalChannelNumber, int ipChannelNumber) {
        Log.d(TAG, "updateScanProgress digitalChannelNumber = " + digitalChannelNumber +
                "| ipChannelNumber = " + ipChannelNumber);
        mMainThreadHandler.removeMessages(MSG_UPDATE_CHANNEL_NUMBER);
        Message msg = mMainThreadHandler.obtainMessage(MSG_UPDATE_CHANNEL_NUMBER, digitalChannelNumber, ipChannelNumber);
        boolean info = mMainThreadHandler.sendMessage(msg);
        Log.d(TAG, "updateScanChannelNumber " + info);
    }

    @Override
    public void updateScanStatus(String status) {
        Log.d(TAG, "updateScanStatus status = " + status);
        mMainThreadHandler.removeMessages(MSG_UPDATE_SCAN_STATUS);
        Message msg = mMainThreadHandler.obtainMessage(MSG_UPDATE_SCAN_STATUS, status);
        boolean info = mMainThreadHandler.sendMessage(msg);
        Log.d(TAG, "updateScanStatus " + info);
    }

    @Override
    public void finishScanView() {
        Log.d(TAG, "finishScanView");
        mMainThreadHandler.removeMessages(MSG_FINISH_SCAN_VIEW);
        Message msg = mMainThreadHandler.obtainMessage(MSG_FINISH_SCAN_VIEW);
        boolean info = mMainThreadHandler.sendMessage(msg);
        Log.d(TAG, "MSG_FINISH_SCAN_VIEW " + info);
    }

    private void initPlayer(){
        mExoPlayer = new ExoPlayer.Builder(this).build();
        mExoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        mExoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                Log.i(TAG,"onPlayerError:"+error);
            }
        });
    }

    private void stopPlayback() {
        if (mExoPlayer != null) {
            mExoPlayer.stop();
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }

    private void initView() {
        SurfaceView surfaceView = findViewById(R.id.fvp_surface_view);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (mExoPlayer != null) {
                    mExoPlayer.setVideoSurfaceHolder(holder);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mExoPlayer != null) {
                    mExoPlayer.setVideoSurfaceHolder(null);
                }
            }
        });
        mScanStatus = findViewById(R.id.fvp_scan_status);
        mDigitalChannelNumber = findViewById(R.id.fvp_digital_channel_number);
        mIpChannelNumber = findViewById(R.id.fvp_ip_channel_number);
        mScanProgress = findViewById(R.id.fvp_scan_progress);
        mScanProgressNumber = findViewById(R.id.fvp_scan_progress_number);
    }

    private void triggerAutomaticScan() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra("Automatic_scan", false)) {
            String uri = intent.getStringExtra("FvpVideoUrl");
            String dvbType = intent.getStringExtra("DvbType");
            String inputId = intent.getStringExtra("InputId");
            mScanPresenter = new DtvKitDVBTCScanPresenter(this, DtvKitDVBTCScanPresenter.DVB_T, inputId, this);
            mScanPresenter.startAutoScan();
            if (mExoPlayer == null) {
                initPlayer();
            }
            if (null != uri) {
                MediaItem mediaItem = MediaItem.fromUri(uri);
                mExoPlayer.setMediaItem(mediaItem);
                mExoPlayer.prepare();
                mExoPlayer.play();
            }
            Log.d(TAG, "triggerAutomaticScan:video uri = " + uri + "| dvbType = " + dvbType + "|inputId = " + inputId);
        } else {
            Log.d(TAG, "not need automatic scan, finish ");
            finish();
        }
    }

    private void sendScanFinishBroadcast() {
        FvpSearchBroadcast.sendBroadcast(this);
    }
}
