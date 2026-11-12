package com.example.audiorecorderpro;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.File;

/** P05 - Recording in Progress. Displays real service state; never owns the recording. */
public class RecordingActivity extends AppCompatActivity {

    private static final String ACTION_STATUS = "com.example.audiorecorderpro.RECORDING_STATUS";
    private static final String ACTION_MESSAGE = "com.example.audiorecorderpro.RECORDING_MESSAGE";
    private static final String ACTION_SAVED = "com.example.audiorecorderpro.RECORDING_SAVED";

    private TextView tvStatusBadge;
    private TextView tvTimer;
    private TextView tvFileName;
    private TextView tvFinalizing;
    private TextView tvAutoTitle;
    private TextView tvRemaining;
    private TextView tvAutoNote;
    private TextView tvInfo;
    private MaterialButton btnPauseResume;
    private MaterialButton btnStop;
    private MaterialButton btnRestart;

    private boolean isPaused = false;
    private boolean finalizing = false;
    private boolean navigated = false;
    private boolean visible = false;
    private boolean receiverRegistered = false;
    private String savedPath = null;
    private ObjectAnimator pulse;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshTimes();
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            if (ACTION_SAVED.equals(action)) {
                savedPath = intent.getStringExtra("filePath");
            } else if (ACTION_STATUS.equals(action)) {
                boolean recording = intent.getBooleanExtra("isRecording", false);
                boolean paused = intent.getBooleanExtra("isPaused", false);
                if (recording) {
                    applyState(paused);
                } else {
                    handleStopped();
                }
            } else if (ACTION_MESSAGE.equals(action)) {
                String message = intent.getStringExtra("message");
                if (message != null) {
                    Toast.makeText(RecordingActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);
        UiUtil.setupTopBar(this, "Recording in Progress",
                RecorderUtils.prefs(this).getLong("activeRunId", 0L) > 0 ? "Scheduled Recording" : "Live Session");
        findViewById(R.id.btnBack).setContentDescription("Minimize");

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvTimer = findViewById(R.id.tvTimer);
        tvFileName = findViewById(R.id.tvFileName);
        tvFinalizing = findViewById(R.id.tvFinalizing);
        tvAutoTitle = findViewById(R.id.tvAutoTitle);
        tvRemaining = findViewById(R.id.tvRemaining);
        tvAutoNote = findViewById(R.id.tvAutoNote);
        tvInfo = findViewById(R.id.tvInfo);
        btnPauseResume = findViewById(R.id.btnPauseResume);
        btnStop = findViewById(R.id.btnStop);
        btnRestart = findViewById(R.id.btnRestart);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STATUS);
        filter.addAction(ACTION_MESSAGE);
        filter.addAction(ACTION_SAVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;

        btnPauseResume.setOnClickListener(v -> {
            if (finalizing) {
                return;
            }
            sendToService(isPaused
                    ? AudioRecordingService.ACTION_RESUME_RECORDING
                    : AudioRecordingService.ACTION_PAUSE_RECORDING);
        });

        btnStop.setOnClickListener(v -> {
            if (finalizing) {
                return; // prevent duplicate stop requests
            }
            finalizing = true;
            setFinalizingUi();
            sendToService(AudioRecordingService.ACTION_STOP_RECORDING);
        });

        btnRestart.setOnClickListener(v -> {
            if (finalizing) {
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Restart recording?")
                    .setMessage("The current recording will be saved and a new recording will start.")
                    .setPositiveButton("Restart", (d, w) ->
                            sendToService(AudioRecordingService.ACTION_RESTART_RECORDING))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void sendToService(String action) {
        Intent intent = new Intent(this, AudioRecordingService.class);
        intent.setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException e) {
            finalizing = false;
            Toast.makeText(this, "Could not control recording. Please try again.",
                    Toast.LENGTH_LONG).show();
            refreshButtons();
        }
    }

    // --------------------------------------------------------------- state UI

    private void applyState(boolean paused) {
        isPaused = paused;
        UiUtil.styleBadge(tvStatusBadge,
                paused ? UiUtil.STATUS_CANCELLED : UiUtil.STATUS_RECORDING,
                paused ? "Paused" : "Recording");
        updatePulse();
        stylePauseButton();
        refreshButtons();
        refreshInfo();
        refreshTimes();
    }

    private void refreshButtons() {
        btnPauseResume.setEnabled(!finalizing);
        btnStop.setEnabled(!finalizing);
        btnRestart.setEnabled(!finalizing);
    }

    private void setFinalizingUi() {
        tvFinalizing.setVisibility(View.VISIBLE);
        refreshButtons();
    }

    private void stylePauseButton() {
        int primary = ContextCompat.getColor(this, R.color.app_primary);
        int onPrimary = ContextCompat.getColor(this, R.color.app_on_primary);
        int link = ContextCompat.getColor(this, R.color.app_link);
        int border = ContextCompat.getColor(this, R.color.app_border);
        if (isPaused) {
            btnPauseResume.setText("Resume");
            btnPauseResume.setIconResource(R.drawable.ic_play);
            btnPauseResume.setBackgroundTintList(ColorStateList.valueOf(primary));
            btnPauseResume.setTextColor(onPrimary);
            btnPauseResume.setIconTint(ColorStateList.valueOf(onPrimary));
            btnPauseResume.setStrokeWidth(0);
        } else {
            btnPauseResume.setText("Pause");
            btnPauseResume.setIconResource(R.drawable.ic_pause);
            btnPauseResume.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnPauseResume.setTextColor(link);
            btnPauseResume.setIconTint(ColorStateList.valueOf(link));
            btnPauseResume.setStrokeColor(ColorStateList.valueOf(border));
            btnPauseResume.setStrokeWidth(UiUtil.dp(this, 1));
        }
    }

    private void updatePulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
            tvStatusBadge.setAlpha(1f);
        }
        // Lightweight pulse only while really recording and when animations are enabled.
        if (!isPaused && ValueAnimator.areAnimatorsEnabled()) {
            pulse = ObjectAnimator.ofFloat(tvStatusBadge, View.ALPHA, 1f, 0.55f);
            pulse.setDuration(900);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setInterpolator(new LinearInterpolator());
            pulse.start();
        }
    }

    private void refreshInfo() {
        SharedPreferences p = RecorderUtils.prefs(this);
        String path = p.getString("currentRecordingPath", null);
        tvFileName.setText(path == null ? "" : new File(path).getName());
        tvInfo.setText("Format: M4A (AAC)\nQuality: "
                + RecorderUtils.qualityLabel(p.getString("recordingQuality", "MEDIUM"))
                + "\nSaved locally on this device");
    }

    private void refreshTimes() {
        SharedPreferences p = RecorderUtils.prefs(this);
        long elapsed = RecorderUtils.elapsedMillis(p);
        tvTimer.setText(RecorderUtils.formatHms(elapsed));

        int minutes = RecorderUtils.activeLimitMinutes(p);
        if (minutes > 0) {
            long remaining = minutes * 60_000L - elapsed;
            tvAutoTitle.setText("Auto-Save Timer");
            tvRemaining.setText(RecorderUtils.formatClock(remaining) + " remaining ("
                    + minutes + " min limit)");
            tvAutoNote.setText("Recording will stop and save when the time limit is reached.");
        } else {
            tvAutoTitle.setText("Auto-Save Timer");
            tvRemaining.setText("Manual stop");
            tvAutoNote.setText("Auto-Save is off. Recording continues until you stop it.");
        }
    }

    // ------------------------------------------------------------- stop flow

    private void handleStopped() {
        if (!visible) {
            return; // onResume will finish the flow
        }
        if (navigated) {
            return;
        }
        navigated = true;
        handler.removeCallbacks(tick);
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        if (savedPath != null && new File(savedPath).exists()) {
            Intent intent = new Intent(this, RecordingSavedActivity.class);
            intent.putExtra(RecordingSavedActivity.EXTRA_PATH, savedPath);
            startActivity(intent);
        } else if (finalizing) {
            Toast.makeText(this, "Unable to complete saving. The recording may have been too short.",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No active recording.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        SharedPreferences p = RecorderUtils.prefs(this);
        if (p.getBoolean("isRecording", false)) {
            applyState(p.getBoolean("isPaused", false));
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, 1000);
        } else {
            handleStopped();
        }
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(tick);
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
            tvStatusBadge.setAlpha(1f);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
