package com.example.audiorecorderpro;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter; 
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;

/** P03 - Home Dashboard. */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final String ACTION_STATUS = "com.example.audiorecorderpro.RECORDING_STATUS";
    private static final String ACTION_MESSAGE = "com.example.audiorecorderpro.RECORDING_MESSAGE";

    private View cardActive;
    private TextView tvActiveStatus;
    private TextView tvRecordingTimer;
    private TextView tvRecordLabel;
    private LinearLayout recentContainer;
    private TextView tvRecentEmpty;
    private TextView tvStorageAvail;
    private TextView tvStorageWarning;
    private LinearProgressIndicator progressStorage;

    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean receiverRegistered = false;
    private boolean pendingOpenRecording = false;
    private boolean startAfterPermission = false;
    private boolean blocked = false;
    private View cardIssue;
    private TextView tvIssueText;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable clearPendingRunnable = () -> pendingOpenRecording = false;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecording) {
                return;
            }
            tvRecordingTimer.setText(RecorderUtils.formatHms(
                    RecorderUtils.elapsedMillis(RecorderUtils.prefs(MainActivity.this))));
            if (!isPaused) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (ACTION_STATUS.equals(intent.getAction())) {
                boolean recording = intent.getBooleanExtra("isRecording", false);
                boolean paused = intent.getBooleanExtra("isPaused", false);
                updateRecordingUi(recording, paused);
                // Open the recording screen only after the service reports a real active recording.
                if (recording && pendingOpenRecording) {
                    pendingOpenRecording = false;
                    openRecordingScreen();
                }
            } else if (ACTION_MESSAGE.equals(intent.getAction())) {
                pendingOpenRecording = false;
                String message = intent.getStringExtra("message");
                if (message != null) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsActivity.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            blocked = true;
            return;
        }
        setContentView(R.layout.activity_main);

        cardActive = findViewById(R.id.cardActive);
        tvActiveStatus = findViewById(R.id.tvActiveStatus);
        tvRecordingTimer = findViewById(R.id.tvRecordingTimer);
        tvRecordLabel = findViewById(R.id.tvRecordLabel);
        recentContainer = findViewById(R.id.recentContainer);
        tvRecentEmpty = findViewById(R.id.tvRecentEmpty);
        tvStorageAvail = findViewById(R.id.tvStorageAvail);
        tvStorageWarning = findViewById(R.id.tvStorageWarning);
        progressStorage = findViewById(R.id.progressStorage);
        cardIssue = findViewById(R.id.cardIssue);
        tvIssueText = findViewById(R.id.tvIssueText);

        registerStatusReceiver();

        findViewById(R.id.btnQuickRecord).setOnClickListener(v -> onQuickRecord());
        findViewById(R.id.btnQuickSetup).setOnClickListener(v ->
                startActivity(new Intent(this, QuickSetupActivity.class)));
        findViewById(R.id.btnReturnToRecording).setOnClickListener(v -> openRecordingScreen());
        findViewById(R.id.btnHeaderSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsHubActivity.class)));
        findViewById(R.id.btnEditSchedule).setOnClickListener(v -> {
            ScheduleStore.Plan plan = ScheduleStore.nextPlan(this);
            if (plan != null) {
                Intent i = new Intent(this, ScheduleEditActivity.class);
                i.putExtra(ScheduleEditActivity.EXTRA_PLAN_ID, plan.id);
                startActivity(i);
            }
        });
        findViewById(R.id.btnViewSchedule).setOnClickListener(v -> {
            ScheduleStore.Plan plan = ScheduleStore.nextPlan(this);
            if (plan == null) {
                startActivity(new Intent(this, ScheduleEditActivity.class));
            } else {
                Intent i = new Intent(this, ScheduleDetailsActivity.class);
                i.putExtra(ScheduleDetailsActivity.EXTRA_PLAN_ID, plan.id);
                startActivity(i);
            }
        });
        findViewById(R.id.btnReviewIssue).setOnClickListener(v -> {
            java.util.List<ScheduleStore.Run> issues = ScheduleStore.unresolvedIssues(this);
            if (!issues.isEmpty()) {
                Intent i = new Intent(this, RecordingIssueActivity.class);
                i.putExtra(RecordingIssueActivity.EXTRA_RUN_ID, issues.get(0).id);
                startActivity(i);
            }
        });
        findViewById(R.id.btnViewAll).setOnClickListener(v ->
                startActivity(new Intent(this, RecordingLibraryActivity.class)));
        findViewById(R.id.btnManageStorage).setOnClickListener(v ->
                startActivity(new Intent(this, StorageActivity.class)));

        UiUtil.setupBottomNav(this, R.id.nav_home);

        // Ask once for microphone (+ notification on Android 13+) on first launch.
        if (!RecorderUtils.prefs(this).getBoolean("askedPermissions", false)) {
            RecorderUtils.prefs(this).edit().putBoolean("askedPermissions", true).apply();
            String[] missing = RecorderUtils.missingRuntimePermissions(this);
            if (missing.length > 0) {
                ActivityCompat.requestPermissions(this, missing, REQUEST_PERMISSIONS);
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStatusReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STATUS);
        filter.addAction(ACTION_MESSAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        receiverRegistered = true;
    }

    // ---------------------------------------------------------------- record

    private void onQuickRecord() {
        if (isRecording) {
            // Never start a second recording: reopen the existing session.
            openRecordingScreen();
            return;
        }
        if (!RecorderUtils.hasMic(this)) {
            startAfterPermission = true;
            ActivityCompat.requestPermissions(this,
                    RecorderUtils.missingRuntimePermissions(this), REQUEST_PERMISSIONS);
            return;
        }
        long free = RecorderUtils.availableBytes(this);
        if (free >= 0 && free < 20L * 1024L * 1024L) {
            Toast.makeText(this, "Not enough storage to start recording.", Toast.LENGTH_LONG).show();
            return;
        }
        startRecording();
    }

    private void startRecording() {
        pendingOpenRecording = true;
        handler.removeCallbacks(clearPendingRunnable);
        handler.postDelayed(clearPendingRunnable, 6000);

        Intent intent = new Intent(this, AudioRecordingService.class);
        intent.setAction(AudioRecordingService.ACTION_START_RECORDING);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException e) {
            pendingOpenRecording = false;
            Toast.makeText(this, "Could not start recording. Please try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openRecordingScreen() {
        Intent intent = new Intent(this, RecordingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }
        if (RecorderUtils.hasMic(this)) {
            if (startAfterPermission) {
                startAfterPermission = false;
                startRecording();
            }
        } else if (startAfterPermission) {
            startAfterPermission = false;
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, android.Manifest.permission.RECORD_AUDIO)) {
                new AlertDialog.Builder(this)
                        .setTitle("Microphone permission needed")
                        .setMessage("Recording needs the microphone. Please allow it in App settings.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.setData(Uri.fromParts("package", getPackageName(), null));
                            startActivity(i);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                Toast.makeText(this, "Microphone permission is required to record.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // -------------------------------------------------------------------- UI

    private void updateRecordingUi(boolean recording, boolean paused) {
        isRecording = recording;
        isPaused = recording && paused;
        handler.removeCallbacks(timerRunnable);

        if (isRecording) {
            cardActive.setVisibility(View.VISIBLE);
            tvActiveStatus.setText(isPaused ? "Recording paused" : "Recording in progress");
            tvRecordLabel.setText("Return to recording");
            findViewById(R.id.btnQuickRecord).setContentDescription("Return to recording");
            tvRecordingTimer.setText(RecorderUtils.formatHms(
                    RecorderUtils.elapsedMillis(RecorderUtils.prefs(this))));
            if (!isPaused) {
                handler.postDelayed(timerRunnable, 1000);
            }
        } else {
            cardActive.setVisibility(View.GONE);
            tvRecordLabel.setText("Quick Record");
            findViewById(R.id.btnQuickRecord).setContentDescription("Start recording");
        }
    }

    private void loadRecent() {
        recentContainer.removeAllViews();
        File[] files = RecorderUtils.listRecordings(this);
        if (files.length == 0) {
            tvRecentEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvRecentEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        int count = Math.min(3, files.length);
        for (int i = 0; i < count; i++) {
            File file = files[i];
            View row = inflater.inflate(R.layout.item_recording_row, recentContainer, false);
            ((TextView) row.findViewById(R.id.rowName)).setText(RecorderUtils.displayName(file));
            ((TextView) row.findViewById(R.id.rowMeta)).setText(
                    RecorderUtils.formatDate(file.lastModified())
                            + " \u2022 " + RecorderUtils.formatDurationShort(RecorderUtils.durationMs(file))
                            + " \u2022 " + RecorderUtils.formatSize(file.length()));
            ImageView fav = row.findViewById(R.id.rowFav);
            fav.setVisibility(RecorderUtils.isFavorite(this, file) ? View.VISIBLE : View.GONE);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, NowPlayingActivity.class);
                intent.putExtra(NowPlayingActivity.EXTRA_PATH, file.getAbsolutePath());
                startActivity(intent);
            });
            recentContainer.addView(row);
        }
    }

    private void loadStorage() {
        long free = RecorderUtils.availableBytes(this);
        long total = RecorderUtils.totalBytes(this);
        if (free < 0 || total <= 0) {
            tvStorageAvail.setText("Storage information is unavailable.");
            progressStorage.setVisibility(View.GONE);
            tvStorageWarning.setVisibility(View.GONE);
            return;
        }
        tvStorageAvail.setText(RecorderUtils.formatSize(free) + " available \u2022 "
                + RecorderUtils.formatSize(RecorderUtils.recordingsTotalSize(this)) + " used by recordings");
        progressStorage.setVisibility(View.VISIBLE);
        progressStorage.setProgress((int) (((total - free) * 100L) / total));

        if (free < RecorderUtils.LOW_STORAGE_BYTES) {
            tvStorageWarning.setVisibility(View.VISIBLE);
            UiUtil.styleBadge(tvStorageWarning, UiUtil.STATUS_FAILED, "Low storage");
        } else {
            tvStorageWarning.setVisibility(View.GONE);
        }
    }

    private void loadSchedule() {
        TextView name = findViewById(R.id.tvScheduleName);
        TextView info = findViewById(R.id.tvScheduleInfo);
        TextView badge = findViewById(R.id.badgeSchedule);
        View edit = findViewById(R.id.btnEditSchedule);
        com.google.android.material.button.MaterialButton view = findViewById(R.id.btnViewSchedule);
        ScheduleStore.Plan plan = ScheduleStore.nextPlan(this);
        if (plan == null) {
            name.setVisibility(View.GONE);
            badge.setVisibility(View.GONE);
            edit.setVisibility(View.GONE);
            info.setText("No upcoming recordings. Schedule a recording for a future time.");
            view.setText("Create Schedule");
        } else {
            name.setVisibility(View.VISIBLE);
            name.setText(plan.name);
            info.setText(ScheduleStore.formatPlanDate(plan.startMillis) + " \u2022 "
                    + ScheduleStore.durationLabel(plan.durationMin));
            badge.setVisibility(View.VISIBLE);
            UiUtil.styleBadge(badge, UiUtil.STATUS_SCHEDULED, "Scheduled");
            edit.setVisibility(View.VISIBLE);
            view.setText("View Schedule");
        }

        java.util.List<ScheduleStore.Run> issues = ScheduleStore.unresolvedIssues(this);
        if (issues.isEmpty()) {
            cardIssue.setVisibility(View.GONE);
        } else {
            cardIssue.setVisibility(View.VISIBLE);
            tvIssueText.setText(issues.size() == 1
                    ? "1 recording issue needs your attention"
                    : issues.size() + " recording issues need your attention");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (blocked) {
            return;
        }
        ScheduleStore.reconcileInterrupted(this);
        ScheduleStore.sweepMissed(this);
        loadSchedule();
        updateRecordingUi(
                RecorderUtils.prefs(this).getBoolean("isRecording", false),
                RecorderUtils.prefs(this).getBoolean("isPaused", false));
        loadRecent();
        loadStorage();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerRunnable);
        handler.removeCallbacks(clearPendingRunnable);
        if (receiverRegistered) {
            try {
                unregisterReceiver(statusReceiver);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
