package com.vidyo.vidyoconnector;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Device.Device;
import com.vidyo.VidyoClient.Device.RemoteWindowShare;
import com.vidyo.VidyoClient.Endpoint.Participant;
import com.vidyo.vidyoconnector.event.ControlEvent;
import com.vidyo.vidyoconnector.event.IControlLink;
import com.vidyo.vidyoconnector.share.ShareManager;
import com.vidyo.vidyoconnector.utils.AppUtils;
import com.vidyo.vidyoconnector.utils.Logger;
import com.vidyo.vidyoconnector.view.ControlView;

/**
 * Conference activity holding all connection and callbacks logic.
 */
public class VideoConferenceActivity extends FragmentActivity implements Connector.IConnect, IControlLink,
        View.OnLayoutChangeListener, Connector.IRegisterRemoteWindowShareEventListener, ShareManager.Listener {

    public static final String PORTAL_KEY = "portal.key";
    public static final String ROOM_KEY = "room.key";
    public static final String PIN_KEY = "pin.key";
    public static final String NAME_KEY = "name.key";

    private FrameLayout videoView;

    private ControlView controlView;
    private View progressBar;
    private TextView shareLabel;

    private Connector connector;
    private ShareManager shareManager;

    @Override
    public void onStart() {
        super.onStart();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        if (connector != null) {
            ControlView.State state = controlView.getState();
            connector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground);
            connector.setCameraPrivacy(state.isMuteCamera());

            shareManager.tryUpdateShareOrientation();
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

        shareLabel = findViewById(R.id.share_label);
        shareLabel.setVisibility(View.GONE);
        shareLabel.setOnClickListener(view -> {
            if (shareManager != null && shareManager.isSharing())
                shareManager.requestStopShare();
        });

        /*
         * Connector instance created with NULL passed as video frame. Local & RemoteHolder camera will be assigned later.
         */
        videoView = findViewById(R.id.video_frame);
        connector = new Connector(videoView, Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
                8, "debug@VidyoClient info@VidyoConnector info warning",
                AppUtils.configLogFile(this), 0);
        Logger.i("Connector instance has been created.");

        connector.registerRemoteWindowShareEventListener(this);

        shareManager = new ShareManager(this, connector);
        shareManager.setShareListener(this);

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

            case SHARE:
                if (!shareManager.isSharing()) {
                    shareManager.requestShare();
                } else {
                    shareManager.requestStopShare();
                }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (shareManager != null)
            shareManager.handlePermissionsResponse(requestCode, resultCode, data);
    }

    @Override
    public void onShareStarted() {
        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, "Share started.", Toast.LENGTH_SHORT).show();

            if (controlView != null) controlView.toggleShareState(true);

            final Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);

            shareLabel.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onShareStopped() {
        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, "Share stopped.", Toast.LENGTH_SHORT).show();

            shareLabel.setVisibility(View.GONE);

            if (controlView != null) controlView.toggleShareState(false);
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            shareLabel.setVisibility(View.GONE);

            Toast.makeText(VideoConferenceActivity.this, "Cannot share. Reason: " + message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shareManager != null) shareManager.destroy();
        if (controlView != null) controlView.unregisterListener();

        if (connector != null) {
            connector.hideView(videoView);
            connector.disable();
            connector = null;
        }

        ConnectorPkg.uninitialize();
        ConnectorPkg.setApplicationUIContext(null);

        Logger.i("Connector instance has been released.");
    }

    @Override
    public void onRemoteWindowShareAdded(RemoteWindowShare remoteWindowShare, Participant participant) {
        runOnUiThread(() -> shareManager.requestStopShare());
    }

    @Override
    public void onRemoteWindowShareRemoved(RemoteWindowShare remoteWindowShare, Participant participant) {

    }

    @Override
    public void onRemoteWindowShareStateUpdated(RemoteWindowShare remoteWindowShare, Participant participant, Device.DeviceState deviceState) {

    }
}