package com.tchvu3.capacitorvoicerecorder;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import com.getcapacitor.JSObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class VoiceRecorderService extends Service {

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {

        public VoiceRecorderService getService() {
            return VoiceRecorderService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent != null ? intent.getStringExtra("title") : null;
        String message = intent != null ? intent.getStringExtra("message") : null;
        startForeground(1, VoiceRecorderNotification.createNotification(this, title, message));
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopRecording();
        } catch (Exception e) {
            Log.e("VoiceRecorderService", "Exception while stopping recording in onDestroy", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private CustomMediaRecorder mediaRecorder;

    public CustomMediaRecorder getMediaRecorder() {
        return mediaRecorder;
    }

    public void startRecording(String directory, String subDirectory) throws MessagesException {
        try {
            Log.i("VoiceRecorderService", "startRecording");
            RecordOptions options = new RecordOptions(directory, subDirectory);
            mediaRecorder = new CustomMediaRecorder(getApplicationContext(), options);
            mediaRecorder.startRecording();
        } catch (Exception exp) {
            Log.e("VoiceRecorderService", "startRecording", exp);
            mediaRecorder = null;
            throw new MessagesException(Messages.FAILED_TO_RECORD, exp);
        }
    }

    public JSObject stopRecording() throws MessagesException {
        if (mediaRecorder == null) {
            Log.w("VoiceRecorderService", "stopRecording - RECORDING_HAS_NOT_STARTED");
            throw new MessagesException(Messages.RECORDING_HAS_NOT_STARTED);
        }

        try {
            if (mediaRecorder.getErrorInfo() != null) {
                try {
                    mediaRecorder.stopRecording();
                } catch (Exception exp) {
                    Log.e("VoiceRecorderService", "mediaRecorder.stopRecording():", exp);
                }
                Log.e("VoiceRecorderService", "stopRecording - errorInfo: " + mediaRecorder.getErrorInfo().toString());
                throw new MessagesException(Messages.RUNTIME_FAILED + " error info: " + mediaRecorder.getErrorInfo());
            }

            mediaRecorder.stopRecording();
            File recordedFile = mediaRecorder.getOutputFile();
            RecordOptions options = mediaRecorder.getRecordOptions();

            String path = null;
            String recordDataBase64 = null;
            if (options.getDirectory() != null) {
                path = recordedFile.getName();
                if (options.getSubDirectory() != null) {
                    path = options.getSubDirectory() + "/" + path;
                }
            } else {
                recordDataBase64 = readRecordedFileAsBase64(recordedFile);
            }

            RecordData recordData = new RecordData(
                recordDataBase64,
                getMsDurationOfAudioFile(recordedFile.getAbsolutePath()),
                "audio/aac",
                path
            );
            if ((recordDataBase64 == null && path == null) || recordData.getMsDuration() < 0) {
                throw new MessagesException(Messages.EMPTY_RECORDING);
            } else {
                return ResponseGenerator.dataResponse(recordData.toJSObject());
            }
        } catch (Exception exp) {
            Log.e("VoiceRecorderService", "stopRecording - FAILED_TO_FETCH_RECORDING", exp);
            throw new MessagesException(Messages.FAILED_TO_FETCH_RECORDING, exp);
        } finally {
            RecordOptions options = mediaRecorder.getRecordOptions();
            if (options.getDirectory() == null) {
                mediaRecorder.deleteOutputFile();
            }

            mediaRecorder = null;
            Log.i("VoiceRecorderService", "stopRecording - stopped");
        }
    }

    private String readRecordedFileAsBase64(File recordedFile) {
        BufferedInputStream bufferedInputStream;
        byte[] bArray = new byte[(int) recordedFile.length()];
        try {
            bufferedInputStream = new BufferedInputStream(new FileInputStream(recordedFile));
            bufferedInputStream.read(bArray);
            bufferedInputStream.close();
        } catch (IOException exp) {
            return null;
        }
        return Base64.encodeToString(bArray, Base64.DEFAULT);
    }

    private int getMsDurationOfAudioFile(String recordedFilePath) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(recordedFilePath);
            mediaPlayer.prepare();
            int duration = mediaPlayer.getDuration();
            mediaPlayer.release();
            return duration;
        } catch (Exception ignore) {
            return -1;
        }
    }
}
