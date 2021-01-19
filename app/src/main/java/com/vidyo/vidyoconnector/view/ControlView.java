package com.vidyo.vidyoconnector.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.vidyo.vidyoconnector.R;
import com.vidyo.vidyoconnector.event.ControlEvent;
import com.vidyo.vidyoconnector.event.IControlLink;

public class ControlView extends LinearLayout implements View.OnClickListener {

    public enum ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTING, FAILED, DISCONNECTED
    }

    private IControlLink callback;

    private ImageView connectView;
    private ImageView muteCamera;
    private ImageView muteMic;
    private ImageView muteSpeaker;
    private ImageView switchCamera;

    private View controlMoreLayout;
    private ImageView debugOption;
    private ImageView captureToggle;

    private TextView libraryVersion;
    private TextView connectionState;

    private State internalState;

    public ControlView(Context context) {
        super(context);
        init();
    }

    public ControlView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    public ControlView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ControlView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void registerListener(IControlLink callback) {
        this.callback = callback;
    }

    public void unregisterListener() {
        this.callback = null;
    }

    public State getState() {
        return internalState;
    }

    public void showVersion(String version) {
        if (libraryVersion == null) return;

        libraryVersion.setText(getContext().getString(R.string.lib_version, version));
    }

    public void disable(boolean state) {
        connectView.setOnClickListener(state ? null : this);
        muteCamera.setOnClickListener(state ? null : this);
        muteMic.setOnClickListener(state ? null : this);
        muteSpeaker.setOnClickListener(state ? null : this);
        switchCamera.setOnClickListener(state ? null : this);

        debugOption.setOnClickListener(state ? null : this);
        captureToggle.setOnClickListener(state ? null : this);

        findViewById(R.id.more_control).setOnClickListener(state ? null : this);
        findViewById(R.id.more_send_logs).setOnClickListener(state ? null : this);

        setAlpha(state ? 0.3f : 1f);
    }

    public void connectedCall(boolean connected) {
        internalState.setConnected(connected);
        invalidateState();
    }

    public void updateConnectionState(ConnectionState state) {
        this.connectionState.setText(state.name());
    }

    public void toggleCaptureState(boolean state) {
        internalState.setCapture(state);
        invalidateState();
    }

    private void invalidateState() {
        connectView.setImageResource(internalState.isConnected() ? R.drawable.call_end : R.drawable.call_start);
        muteCamera.setImageResource(internalState.isMuteCamera() ? R.drawable.camera_off : R.drawable.camera_on);
        muteMic.setImageResource(internalState.isMuteMic() ? R.drawable.microphone_off : R.drawable.microphone_on);
        muteSpeaker.setImageResource(internalState.isMuteSpeaker() ? R.drawable.speaker_off : R.drawable.speaker_on);
        debugOption.setImageResource(internalState.isDebug() ? R.drawable.ic_debug_on : R.drawable.ic_debug_off);
        captureToggle.setImageResource(internalState.isCapture() ? R.drawable.ic_video_on : R.drawable.ic_video_off);
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.call_toolbar, this, true);

        connectView = findViewById(R.id.call_control);
        muteCamera = findViewById(R.id.camera_control);
        muteMic = findViewById(R.id.mic_control);
        muteSpeaker = findViewById(R.id.speaker_control);
        switchCamera = findViewById(R.id.switch_control);

        controlMoreLayout = findViewById(R.id.control_settings_layout);
        debugOption = findViewById(R.id.more_debug);
        captureToggle = findViewById(R.id.capture_toggle);

        libraryVersion = findViewById(R.id.library_version);
        connectionState = findViewById(R.id.connection_state);

        internalState = State.defaultState();
        invalidateState();

        updateConnectionState(ConnectionState.DISCONNECTED);

        disable(false);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        if (internalState == null) return;

        ControlEvent<?> controlEvent = null;

        switch (v.getId()) {
            case R.id.call_control:
                controlEvent = new ControlEvent<>(ControlEvent.Call.CONNECT_DISCONNECT, !internalState.isConnected());
                break;
            case R.id.camera_control:
                boolean muteCamera = !internalState.isMuteCamera();
                controlEvent = new ControlEvent<>(ControlEvent.Call.MUTE_CAMERA, muteCamera);
                internalState.setMuteCamera(muteCamera);
                invalidateState();
                break;
            case R.id.mic_control:
                boolean muteMic = !internalState.isMuteMic();
                controlEvent = new ControlEvent<>(ControlEvent.Call.MUTE_MIC, muteMic);
                internalState.setMuteMic(muteMic);
                invalidateState();
                break;
            case R.id.speaker_control:
                boolean muteSpeaker = !internalState.isMuteSpeaker();
                controlEvent = new ControlEvent<>(ControlEvent.Call.MUTE_SPEAKER, muteSpeaker);
                internalState.setMuteSpeaker(muteSpeaker);
                invalidateState();
                break;
            case R.id.switch_control:
                break;
            case R.id.more_control:
                controlMoreLayout.setVisibility(controlMoreLayout.getVisibility() == GONE ? VISIBLE : GONE);
                break;
            case R.id.more_debug:
                boolean debug = !internalState.isDebug();
                controlEvent = new ControlEvent<>(ControlEvent.Call.DEBUG_OPTION, debug);
                internalState.setDebug(debug);
                invalidateState();
                break;
            case R.id.more_send_logs:
                controlEvent = new ControlEvent<>(ControlEvent.Call.SEND_LOGS);
                break;
            case R.id.capture_toggle:
                break;
        }

        if (controlEvent != null && this.callback != null) {
            this.callback.onControlEvent(controlEvent);
        }
    }

    public static class State implements Parcelable {

        private boolean connected;
        private boolean muteCamera;
        private boolean muteMic;
        private boolean muteSpeaker;

        private boolean capture;

        private boolean debug;

        State(Parcel in) {
            connected = in.readByte() != 0x00;
            muteCamera = in.readByte() != 0x00;
            muteMic = in.readByte() != 0x00;
            muteSpeaker = in.readByte() != 0x00;
            capture = in.readByte() != 0x00;
            debug = in.readByte() != 0x00;
        }

        private State(boolean connected, boolean muteCamera, boolean muteMic, boolean muteSpeaker) {
            this.connected = connected;
            this.muteCamera = muteCamera;
            this.muteMic = muteMic;
            this.muteSpeaker = muteSpeaker;
        }

        private void setConnected(boolean connected) {
            this.connected = connected;
        }

        public boolean isConnected() {
            return connected;
        }

        void setMuteCamera(boolean muteCamera) {
            this.muteCamera = muteCamera;
        }

        void setMuteMic(boolean muteMic) {
            this.muteMic = muteMic;
        }

        void setMuteSpeaker(boolean muteSpeaker) {
            this.muteSpeaker = muteSpeaker;
        }

        void setDebug(boolean debug) {
            this.debug = debug;
        }

        public boolean isMuteCamera() {
            return muteCamera;
        }

        public boolean isMuteMic() {
            return muteMic;
        }

        public boolean isMuteSpeaker() {
            return muteSpeaker;
        }

        boolean isDebug() {
            return debug;
        }

        public boolean isCapture() {
            return capture;
        }

        public void setCapture(boolean capture) {
            this.capture = capture;
        }

        static State defaultState() {
            return new State(false, false, false, false);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeByte((byte) (connected ? 0x01 : 0x00));
            dest.writeByte((byte) (muteCamera ? 0x01 : 0x00));
            dest.writeByte((byte) (muteMic ? 0x01 : 0x00));
            dest.writeByte((byte) (muteSpeaker ? 0x01 : 0x00));
            dest.writeByte((byte) (capture ? 0x01 : 0x00));
            dest.writeByte((byte) (debug ? 0x01 : 0x00));
        }

        public static final Parcelable.Creator<State> CREATOR = new Parcelable.Creator<State>() {
            @Override
            public State createFromParcel(Parcel in) {
                return new State(in);
            }

            @Override
            public State[] newArray(int size) {
                return new State[size];
            }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.stateToSave = this.internalState;
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        this.internalState = ss.stateToSave;

        invalidateState();
    }

    static class SavedState extends BaseSavedState {
        State stateToSave;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            this.stateToSave = in.readParcelable(State.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeParcelable(stateToSave, 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}