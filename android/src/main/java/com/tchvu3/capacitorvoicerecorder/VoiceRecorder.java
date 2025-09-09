package com.tchvu3.capacitorvoicerecorder;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
        name = "VoiceRecorder",
        permissions = {
                @Permission(
                        alias = VoiceRecorder.RECORD_AUDIO_ALIAS,
                        strings = {Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                )
        }
)
public class VoiceRecorder extends Plugin {
    static final String RECORD_AUDIO_ALIAS = "voice recording";
    private VoiceRecorderService recorderService;
    private boolean bound = false;
    private PluginCall startCall;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            VoiceRecorderService.LocalBinder binder = (VoiceRecorderService.LocalBinder) service;
            recorderService = binder.getService();
            bound = true;

            if (startCall != null) {
                try {
                    recorderService.startRecording();
                    startCall.resolve(ResponseGenerator.successResponse());
                } catch (Exception e) {
                    startCall.reject(Messages.FAILED_TO_RECORD, e);
                }
                startCall = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            recorderService = null;
        }
    };

    private CustomMediaRecorder getMediaRecorder() {
        if (bound) {
            return recorderService.getMediaRecorder();
        }
        return null;
    }

    @Override
    public void load() {
        Context context = getContext();
        Intent intent = new Intent(context, VoiceRecorderService.class);
        context.bindService(intent, connection, 0);
    }

    private void startService(String title, String message, String directory, String subDirectory) {
        Context context = getContext();
        Intent intent = new Intent(context, VoiceRecorderService.class);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("directory", directory);
        intent.putExtra("subDirectory", subDirectory);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        new Handler(Looper.getMainLooper()).post(() ->
                context.bindService(intent, connection, 0)
        );
    }

    @PluginMethod
    public void canDeviceVoiceRecord(PluginCall call) {
        if (CustomMediaRecorder.canPhoneCreateMediaRecorder(getContext())) {
            call.resolve(ResponseGenerator.successResponse());
        } else {
            call.resolve(ResponseGenerator.failResponse());
        }
    }

    @PluginMethod
    public void requestAudioRecordingPermission(PluginCall call) {
        if (doesUserGaveAudioRecordingPermission()) {
            call.resolve(ResponseGenerator.successResponse());
        } else {
            requestPermissionForAlias(RECORD_AUDIO_ALIAS, call, "recordAudioPermissionCallback");
        }
    }

    @PermissionCallback
    private void recordAudioPermissionCallback(PluginCall call) {
        this.hasAudioRecordingPermission(call);
    }

    @PluginMethod
    public void hasAudioRecordingPermission(PluginCall call) {
        call.resolve(ResponseGenerator.fromBoolean(doesUserGaveAudioRecordingPermission()));
    }

    @PluginMethod
    public void startRecording(PluginCall call) {
        if (!CustomMediaRecorder.canPhoneCreateMediaRecorder(getContext())) {
            call.reject(Messages.CANNOT_RECORD_ON_THIS_PHONE);
            return;
        }

        if (!doesUserGaveAudioRecordingPermission()) {
            call.reject(Messages.MISSING_PERMISSION);
            return;
        }

        if (this.isMicrophoneOccupied()) {
            call.reject(Messages.MICROPHONE_BEING_USED);
            return;
        }

        CustomMediaRecorder mediaRecorder = getMediaRecorder();
        if (mediaRecorder != null) {
            call.reject(Messages.ALREADY_RECORDING);
            return;
        }

        try {
            String title = call.getString("title", "Recording in progress");
            String message = call.getString("message", "Recording audio");
            String directory = call.getString("directory");
            String subDirectory = call.getString("subDirectory");
            startCall = call;
            startService(title, message, directory, subDirectory);
        } catch (Exception exp) {
            Context context = getContext();
            Intent intent = new Intent(context, VoiceRecorderService.class);
            context.stopService(intent);
            call.reject(Messages.FAILED_TO_RECORD, exp);
        }
    }

    @PluginMethod
    public void stopRecording(PluginCall call) {
        Context context = getContext();

        if (bound) {
            try {
                JSObject obj = recorderService.stopRecording();
                call.resolve(obj);
            } catch (MessagesException e) {
                call.reject(e.getMessage(), e);
            }
        } else {
            call.reject(Messages.FAILED_TO_FETCH_RECORDING);
        }

        Intent intent = new Intent(context, VoiceRecorderService.class);
        context.stopService(intent);

        if (bound) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException e) {
                Log.d("VoiceRecorder", "Attempted to unbind service, but it was already unbound.", e);
            }
            bound = false;
            recorderService = null;
        }
    }

    @PluginMethod
    public void pauseRecording(PluginCall call) {
        CustomMediaRecorder mediaRecorder = getMediaRecorder();
        if (mediaRecorder == null) {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED);
            return;
        }
        try {
            call.resolve(ResponseGenerator.fromBoolean(mediaRecorder.pauseRecording()));
        } catch (NotSupportedOsVersion exception) {
            call.reject(Messages.NOT_SUPPORTED_OS_VERSION);
        }
    }

    @PluginMethod
    public void resumeRecording(PluginCall call) {
        CustomMediaRecorder mediaRecorder = getMediaRecorder();
        if (mediaRecorder == null) {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED);
            return;
        }
        try {
            call.resolve(ResponseGenerator.fromBoolean(mediaRecorder.resumeRecording()));
        } catch (NotSupportedOsVersion exception) {
            call.reject(Messages.NOT_SUPPORTED_OS_VERSION);
        }
    }

    @PluginMethod
    public void getCurrentStatus(PluginCall call) {
        CustomMediaRecorder mediaRecorder = getMediaRecorder();
        if (mediaRecorder == null) {
            call.resolve(ResponseGenerator.statusResponse(CurrentRecordingStatus.NONE));
        } else {
            call.resolve(ResponseGenerator.statusResponse(mediaRecorder.getCurrentStatus()));
        }
    }

    private boolean doesUserGaveAudioRecordingPermission() {
        return getPermissionState(VoiceRecorder.RECORD_AUDIO_ALIAS).equals(PermissionState.GRANTED);
    }

    private boolean isMicrophoneOccupied() {
        AudioManager audioManager = (AudioManager) this.getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return true;
        return audioManager.getMode() != AudioManager.MODE_NORMAL;
    }
}
