package com.vidyo.vidyoconnector;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.vidyoconnector.capture.CaptureManager;
import com.vidyo.vidyoconnector.event.ControlEvent;
import com.vidyo.vidyoconnector.event.IControlLink;
import com.vidyo.vidyoconnector.utils.AppUtils;
import com.vidyo.vidyoconnector.utils.Logger;
import com.vidyo.vidyoconnector.view.ControlView;

/**
 * Conference activity holding all connection and callbacks logic.
 */
public class VideoConferenceActivity extends FragmentActivity implements Connector.IConnect,
        IControlLink,
        View.OnLayoutChangeListener {

    public static final String PORTAL_KEY = "portal.key";
    public static final String ROOM_KEY = "room.key";
    public static final String PIN_KEY = "pin.key";
    public static final String NAME_KEY = "name.key";

    private FrameLayout videoView;

    private ControlView controlView;
    private View progressBar;

    private Connector connector;

    @Override
    public void onStart() {
        super.onStart();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        if (connector != null) {
            ControlView.State state = controlView.getState();
            connector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground);
            connector.setCameraPrivacy(state.isMuteCamera());
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (connector != null) {
            connector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Background);
            connector.setCameraPrivacy(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference);

        ConnectorPkg.initialize();
        ConnectorPkg.setApplicationUIContext(this);

        progressBar = findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);

        controlView = findViewById(R.id.control_view);
        controlView.registerListener(this);

        /*
         * Connector instance created with NULL passed as video frame. Local & RemoteHolder camera will be assigned later.
         */
        videoView = findViewById(R.id.video_frame);
        connector = new Connector(videoView, Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
                8, "*@VidyoClient info@VidyoConnector info warning",
                AppUtils.configLogFile(this), 0);
        Logger.i("Connector instance has been created.");
        connector.selectLocalCamera(null);

        CaptureManager captureManager = new CaptureManager(this, this, connector);
        if (!connector.registerVirtualVideoSourceEventListener(captureManager)) {
            Logger.e("registerRemoteMicrophoneEventListener failed");
        } else {
            Logger.i("registerRemoteMicrophoneEventListener success");
        }

        videoView.addOnLayoutChangeListener(this);
        controlView.showVersion(connector.getVersion());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Logger.i("Config change requested.");

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        FrameLayout.LayoutParams videoViewParams = new FrameLayout.LayoutParams(width, height);
        videoView.setLayoutParams(videoViewParams);

        videoView.addOnLayoutChangeListener(this);
        videoView.requestLayout();
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        v.removeOnLayoutChangeListener(this);

        int w = v.getWidth();
        int h = v.getHeight();
        Logger.i("Show View at Called: " + w + ", " + h);

        connector.showViewAt(v, 0, 0, w, h);
        connector.setViewAnimationSpeed(v, 0);
    }

    @Override
    public void onSuccess() {
        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, R.string.connected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(true);
            controlView.updateConnectionState(ControlView.ConnectionState.CONNECTED);
            controlView.disable(false);
        });
    }

    @Override
    public void onFailure(final Connector.ConnectorFailReason connectorFailReason) {
        if (connector != null) connector.unregisterResourceManagerEventListener();

        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, connectorFailReason.name(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.updateConnectionState(ControlView.ConnectionState.FAILED);
            controlView.disable(false);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @Override
    public void onDisconnected(Connector.ConnectorDisconnectReason connectorDisconnectReason) {
        if (connector != null) connector.unregisterResourceManagerEventListener();

        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, R.string.disconnected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.updateConnectionState(ControlView.ConnectionState.DISCONNECTED);
            controlView.disable(false);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @Override
    public void onControlEvent(ControlEvent<?> event) {
        if (connector == null) return;

        switch (event.getCall()) {
            case CONNECT_DISCONNECT:
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                progressBar.setVisibility(View.VISIBLE);
                controlView.disable(true);
                boolean state = (Boolean) event.getValue();

                controlView.updateConnectionState(state ? ControlView.ConnectionState.CONNECTING : ControlView.ConnectionState.DISCONNECTING);

                if (state) {
                    Intent intent = getIntent();

                    String portal = intent.getStringExtra(PORTAL_KEY);
                    String room = intent.getStringExtra(ROOM_KEY);
                    String pin = intent.getStringExtra(PIN_KEY);
                    String name = intent.getStringExtra(NAME_KEY);

                    Logger.i("Start connection: %s, %s, %s, %s", portal, room, pin, name);
                    connector.connectToRoomAsGuest(portal, name, room, pin, this);
                } else {
                    if (connector != null) connector.disconnect();
                }
                break;
            case MUTE_CAMERA:
                boolean cameraPrivacy = (Boolean) event.getValue();
                connector.setCameraPrivacy(cameraPrivacy);
                break;
            case MUTE_MIC:
                connector.setMicrophonePrivacy((Boolean) event.getValue());
                break;
            case MUTE_SPEAKER:
                connector.setSpeakerPrivacy((Boolean) event.getValue());
                break;
            case CYCLE_CAMERA:
                connector.cycleCamera();
                break;
            case DEBUG_OPTION:
                boolean value = (Boolean) event.getValue();
                if (value) {
                    connector.enableDebug(7776, "");
                } else {
                    connector.disableDebug();
                }

                Toast.makeText(VideoConferenceActivity.this, getString(R.string.debug_option) + value, Toast.LENGTH_SHORT).show();
                break;
            case SEND_LOGS:
                AppUtils.sendLogs(this);
                break;
            case SHOW_PREVIEW:
                boolean preview = (Boolean) event.getValue();
                connector.showPreview(preview);
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (connector != null && (connector.getState() == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Idle
                || connector.getState() == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Ready)) {
            super.onBackPressed();
        } else {
            /* You are still connecting or connected */
            Toast.makeText(this, "You have to disconnect or await connection first", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controlView != null) controlView.unregisterListener();

        if (connector != null) {
            connector.unregisterVirtualVideoSourceEventListener();
            connector.selectVirtualCamera(null);

            connector.hideView(videoView);
            connector.disable();
            connector = null;
        }

        ConnectorPkg.uninitialize();
        ConnectorPkg.setApplicationUIContext(null);

        Logger.i("Connector instance has been released.");
    }
}