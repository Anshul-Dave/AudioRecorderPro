package com.example.audiorecorderpro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudioRecordingService extends Service {

    /** True while this service instance is alive. Used to detect interrupted recordings. */
    static volatile boolean running = false;

    public static final String ACTION_START_RECORDING = "ACTION_START_RECORDING";
    public static final String ACTION_PAUSE_RECORDING = "ACTION_PAUSE_RECORDING";
    public static final String ACTION_RESUME_RECORDING = "ACTION_RESUME_RECORDING";
    public static final String ACTION_RESTART_RECORDING = "ACTION_RESTART_RECORDING";
    public static final String ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING";

    private static final String PREFS_NAME = "AudioRecorderPrefs";
    private static final String CHANNEL_ID = "audio_recording_channel";

    private MediaRecorder mediaRecorder;

    private boolean isRecording = false;
    private boolean isPaused = false;

    private String recordingFilePath;
    private final Handler autoStopHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoStopRunnable = new Runnable() {
        @Override public void run() {
            if (!isRecording) return;
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int minutes = RecorderUtils.activeLimitMinutes(prefs);
            // Keep checking while recording. This also handles a timer setting
            // changed from Off to a duration while a recording is in progress.
            if (minutes <= 0) {
                autoStopHandler.postDelayed(this, 1000L);
                return;
            }
            long start = prefs.getLong("recordingStartTime", 0L);
            long paused = prefs.getLong("pausedDuration", 0L);
            long now = System.currentTimeMillis();
            long elapsed = start > 0 ? now - start - paused : 0L;
            if (isPaused) {
                long pauseStart = prefs.getLong("pauseStartTime", 0L);
                if (pauseStart > 0) elapsed -= (now - pauseStart);
            }
            if (elapsed >= minutes * 60_000L) {
                boolean saved = stopAndSaveCurrentRecording();
                saveRecordingState(false);
                clearRecordingTime();
                sendRecordingStatus();
                sendMessage(saved ? "Auto-stop complete. Recording saved." : "Auto-stop finished, but this recording was too short to save.");
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return;
            }
            autoStopHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {

            case ACTION_START_RECORDING:

                if (isRecording) {
                    sendRecordingStatus();
                    break;
                }

                {
                    SharedPreferences startPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    int limit = intent.hasExtra("scheduleMinutes")
                            ? intent.getIntExtra("scheduleMinutes", 0)
                            : startPrefs.getInt("autoStopMinutes", 0);
                    startPrefs.edit()
                            .putInt("activeLimitMinutes", limit)
                            .putLong("activeRunId", intent.getLongExtra("scheduleRunId", 0L))
                            .apply();
                }

                if (!startRecordingForeground()) {
                    handleStartFailed("Android did not allow the microphone to start in the background.");
                    break;
                }

                if (startAudioRecording()) {
                    sendRecordingStatus();
                    scheduleAutoStopCheck();
                } else {
                    handleStartFailed("The recording could not be started. Check microphone access.");
                }
                break;

            case ACTION_PAUSE_RECORDING:
                pauseAudioRecording();
                break;

            case ACTION_RESUME_RECORDING:
                resumeAudioRecording();
                break;

            case ACTION_RESTART_RECORDING:

                if (isRecording) {
                    stopAndSaveCurrentRecording();
                }

                if (!startRecordingForeground()) {
                    handleStartFailed("Android did not allow the microphone to restart.");
                    break;
                }

                if (startAudioRecording()) {
                    sendRecordingStatus();
                    scheduleAutoStopCheck();
                } else {
                    handleStartFailed("The recording could not be restarted.");
                }
                break;

            case ACTION_STOP_RECORDING:

                boolean saved = stopAndSaveCurrentRecording();

                saveRecordingState(false);
                clearRecordingTime();
                sendRecordingStatus();

                if (!saved) {
                    sendMessage("Recording could not be saved.");
                }

                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                break;
        }

        return START_NOT_STICKY;
    }

    private boolean startRecordingForeground() {

        createNotificationChannel();

        try {
            Notification notification = createRecordingNotification();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                );
            } else {
                startForeground(1, notification);
            }
            return true;
        } catch (RuntimeException e) {
            // Android 12+ can refuse to start a microphone foreground service from the background.
            return false;
        }
    }

    /** Cleans up after a failed start and reports it to the schedule history if needed. */
    private void handleStartFailed(String reason) {
        SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long runId = p.getLong("activeRunId", 0L);
        if (runId > 0) {
            ScheduleStore.failRun(this, runId, reason, null);
        }
        sendMessage(reason);
        saveRecordingState(false);
        clearRecordingTime();
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException ignored) {
            // not in foreground
        }
        stopSelf();
    }

    private PendingIntent servicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, AudioRecordingService.class);
        intent.setAction(action);
        return PendingIntent.getForegroundService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Notification createRecordingNotification() {

        Intent openIntent = new Intent(this, RecordingActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long elapsed = RecorderUtils.elapsedMillis(prefs);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Audio Recorder Pro")
                .setContentText(
                        isPaused
                                ? "Recording paused \u2022 " + RecorderUtils.formatClock(elapsed)
                                : "Recording in progress"
                )
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(!isPaused)
                .setWhen(System.currentTimeMillis() - elapsed)
                .setUsesChronometer(!isPaused);

        builder.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, isPaused ? R.drawable.ic_play : R.drawable.ic_pause),
                isPaused ? "Resume" : "Pause",
                servicePendingIntent(
                        isPaused ? ACTION_RESUME_RECORDING : ACTION_PAUSE_RECORDING,
                        isPaused ? 12 : 11
                )
        ).build());

        builder.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stop),
                "Stop & Save",
                servicePendingIntent(ACTION_STOP_RECORDING, 13)
        ).build());

        return builder.build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audio Recording",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("Audio recording status");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private File createRecordingFile() throws IOException {

        File musicDirectory =
                getExternalFilesDir(Environment.DIRECTORY_MUSIC);

        if (musicDirectory == null) {
            throw new IOException("Music directory unavailable");
        }

        File recordingsDir = new File(musicDirectory, "Recordings");

        if (!recordingsDir.exists() && !recordingsDir.mkdirs()) {
            throw new IOException("Could not create recordings folder");
        }

        SharedPreferences namePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String customName = RecorderUtils.sanitizeName(
                namePrefs.getString(RecorderUtils.KEY_NEXT_NAME, "")
        );
        namePrefs.edit().remove(RecorderUtils.KEY_NEXT_NAME).apply();

        if (!customName.isEmpty()) {
            return RecorderUtils.uniqueFile(recordingsDir, customName);
        }

        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS",
                Locale.getDefault()
        ).format(new Date());

        return new File(
                recordingsDir,
                "Recording_" + timestamp + ".m4a"
        );
    }

    private boolean startAudioRecording() {

        File outputFile = null;

        try {

            outputFile = createRecordingFile();
            recordingFilePath = outputFile.getAbsolutePath();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }

            SharedPreferences preferences =
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            String quality = preferences.getString(
                    "recordingQuality",
                    "MEDIUM"
            );

            int bitRate;

            switch (quality) {
                case "LOW":
                    bitRate = 64000;
                    break;

                case "HIGH":
                    bitRate = 256000;
                    break;

                default:
                    bitRate = 128000;
                    break;
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(bitRate);
            mediaRecorder.setOutputFile(recordingFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            isPaused = false;

            preferences.edit()
                    .putBoolean("isRecording", true)
                    .putBoolean("isPaused", false)
                    .putLong("recordingStartTime", System.currentTimeMillis())
                    .putLong("pausedDuration", 0)
                    .remove("pauseStartTime")
                    .putString("currentRecordingPath", recordingFilePath)
                    .apply();

            updateNotification();
            sendRecordingStatus();

            return true;

        } catch (IOException | RuntimeException e) {

            releaseRecorder();

            if (outputFile != null && outputFile.exists()) {
                // Delete only the incomplete file from this failed start.
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }

            isRecording = false;
            isPaused = false;
            recordingFilePath = null;

            sendMessage("Unable to start recording. Check microphone permission.");

            return false;
        }
    }

    private void scheduleAutoStopCheck() {
        autoStopHandler.removeCallbacks(autoStopRunnable);
        autoStopHandler.postDelayed(autoStopRunnable, 1000L);
    }

    private void pauseAudioRecording() {

        if (!isRecording || isPaused || mediaRecorder == null) {
            return;
        }

        try {

            mediaRecorder.pause();
            isPaused = true;

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean("isPaused", true)
                    .putLong("pauseStartTime", System.currentTimeMillis())
                    .apply();

            sendRecordingStatus();
            updateNotification();
            scheduleAutoStopCheck();

        } catch (RuntimeException e) {
            sendMessage("Could not pause recording.");
        }
    }

    private void resumeAudioRecording() {

        if (!isRecording || !isPaused || mediaRecorder == null) {
            return;
        }

        try {

            mediaRecorder.resume();
            isPaused = false;

            SharedPreferences preferences =
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            long pauseStartTime =
                    preferences.getLong("pauseStartTime", 0);

            long pausedDuration =
                    preferences.getLong("pausedDuration", 0);

            if (pauseStartTime > 0) {
                pausedDuration +=
                        System.currentTimeMillis() - pauseStartTime;
            }

            preferences.edit()
                    .putBoolean("isPaused", false)
                    .putLong("pausedDuration", pausedDuration)
                    .remove("pauseStartTime")
                    .apply();

            sendRecordingStatus();
            updateNotification();

        } catch (RuntimeException e) {
            sendMessage("Could not resume recording.");
        }
    }

    private boolean stopAndSaveCurrentRecording() {
        autoStopHandler.removeCallbacks(autoStopRunnable);

        if (mediaRecorder == null || !isRecording) {
            releaseRecorder();
            isRecording = false;
            isPaused = false;
            return false;
        }

        boolean saved = false;
        String completedPath = recordingFilePath;

        try {
            mediaRecorder.stop();
            saved = true;
        } catch (RuntimeException ignored) {
            // The recording may be too short to finalize.
        } finally {
            releaseRecorder();
            isRecording = false;
            isPaused = false;
            recordingFilePath = null;
        }

        if (saved && completedPath != null) {

            File file = new File(completedPath);

            if (file.exists() && file.length() > 0) {

                Intent savedIntent = new Intent(
                        "com.example.audiorecorderpro.RECORDING_SAVED"
                );

                savedIntent.setPackage(getPackageName());
                savedIntent.putExtra("filePath", completedPath);
                sendBroadcast(savedIntent);

            } else {
                saved = false;
            }
        }

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (saved && completedPath != null) {
            RecorderUtils.setQuality(this, new File(completedPath),
                    sp.getString("recordingQuality", "MEDIUM"));
        }
        long runId = sp.getLong("activeRunId", 0L);
        if (runId > 0) {
            if (saved) {
                ScheduleStore.completeRun(this, runId, completedPath);
            } else {
                ScheduleStore.failRun(this, runId, "The recording was too short to be saved.", null);
            }
            sp.edit().remove("activeRunId").apply();
        }

        return saved;
    }

    private void releaseRecorder() {

        if (mediaRecorder != null) {

            try {
                mediaRecorder.reset();
            } catch (RuntimeException ignored) {
            }

            try {
                mediaRecorder.release();
            } catch (RuntimeException ignored) {
            }

            mediaRecorder = null;
        }
    }

    private void saveRecordingState(boolean recording) {

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean("isRecording", recording)
                .apply();
    }

    private void clearRecordingTime() {

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean("isPaused", false)
                .remove("recordingStartTime")
                .remove("pauseStartTime")
                .remove("currentRecordingPath")
                .remove("activeLimitMinutes")
                .remove("activeRunId")
                .putLong("pausedDuration", 0)
                .apply();
    }

    private void sendRecordingStatus() {

        Intent intent = new Intent(
                "com.example.audiorecorderpro.RECORDING_STATUS"
        );

        intent.setPackage(getPackageName());
        intent.putExtra("isRecording", isRecording);
        intent.putExtra("isPaused", isPaused);

        sendBroadcast(intent);
    }

    private void sendMessage(String message) {

        Intent intent = new Intent(
                "com.example.audiorecorderpro.RECORDING_MESSAGE"
        );

        intent.setPackage(getPackageName());
        intent.putExtra("message", message);

        sendBroadcast(intent);
    }

    private void updateNotification() {

        NotificationManager manager =
                getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.notify(1, createRecordingNotification());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override
    public void onDestroy() {
        autoStopHandler.removeCallbacks(autoStopRunnable);

        if (isRecording) {
            stopAndSaveCurrentRecording();
        } else {
            releaseRecorder();
        }

        saveRecordingState(false);
        clearRecordingTime();
        running = false;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}