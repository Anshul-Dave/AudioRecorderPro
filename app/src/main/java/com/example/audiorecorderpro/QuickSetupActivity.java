package com.example.audiorecorderpro;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

/** P04 - Quick Recording Setup. */
public class QuickSetupActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 201;
    private static final int MAX_MINUTES = 12 * 60;
    private static final String ACTION_STATUS = "com.example.audiorecorderpro.RECORDING_STATUS";
    private static final String ACTION_MESSAGE = "com.example.audiorecorderpro.RECORDING_MESSAGE";

    private RadioGroup rgQuality;
    private RadioGroup rgDuration;
    private MaterialSwitch swAutoSave;
    private View layoutAutoOptions;
    private View layoutCustom;
    private EditText etName;
    private EditText etHours;
    private EditText etMinutes;
    private TextView tvCustomError;
    private TextView tvAutoSummary;
    private TextView badgeMic;
    private TextView badgeStorage;
    private TextView badgeNotif;
    private TextView tvReadyNote;
    private MaterialButton btnStart;
    private MaterialButton btnSaveDefault;
    private MaterialButton btnGrant;

    private boolean pendingOpen = false;
    private boolean startAfterPermission = false;
    private boolean receiverRegistered = false;
    private boolean recordingActive = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clearPending = () -> {
        pendingOpen = false;
        refreshStartButton();
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (ACTION_STATUS.equals(intent.getAction())) {
                boolean recording = intent.getBooleanExtra("isRecording", false);
                if (recording && pendingOpen) {
                    pendingOpen = false;
                    handler.removeCallbacks(clearPending);
                    Intent open = new Intent(QuickSetupActivity.this, RecordingActivity.class);
                    open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(open);
                    finish();
                }
            } else if (ACTION_MESSAGE.equals(intent.getAction())) {
                if (pendingOpen) {
                    pendingOpen = false;
                    handler.removeCallbacks(clearPending);
                    refreshStartButton();
                }
                String message = intent.getStringExtra("message");
                if (message != null) {
                    Toast.makeText(QuickSetupActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_setup);
        UiUtil.setupTopBar(this, "Quick Recording Setup", "Choose how your recording should work");

        rgQuality = findViewById(R.id.rgQuality);
        rgDuration = findViewById(R.id.rgDuration);
        swAutoSave = findViewById(R.id.swAutoSave);
        layoutAutoOptions = findViewById(R.id.layoutAutoOptions);
        layoutCustom = findViewById(R.id.layoutCustom);
        etName = findViewById(R.id.etName);
        etHours = findViewById(R.id.etHours);
        etMinutes = findViewById(R.id.etMinutes);
        tvCustomError = findViewById(R.id.tvCustomError);
        tvAutoSummary = findViewById(R.id.tvAutoSummary);
        badgeMic = findViewById(R.id.badgeMic);
        badgeStorage = findViewById(R.id.badgeStorage);
        badgeNotif = findViewById(R.id.badgeNotif);
        tvReadyNote = findViewById(R.id.tvReadyNote);
        btnStart = findViewById(R.id.btnStart);
        btnSaveDefault = findViewById(R.id.btnSaveDefault);
        btnGrant = findViewById(R.id.btnGrant);

        loadDefaults();

        swAutoSave.setOnCheckedChangeListener((b, checked) -> updateAutoUi());
        rgDuration.setOnCheckedChangeListener((g, id) -> updateAutoUi());
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { updateAutoUi(); }
        };
        etHours.addTextChangedListener(watcher);
        etMinutes.addTextChangedListener(watcher);

        btnStart.setOnClickListener(v -> onStart());
        btnSaveDefault.setOnClickListener(v -> onSaveDefault());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnGrant.setOnClickListener(v -> onGrant());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STATUS);
        filter.addAction(ACTION_MESSAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;

        updateAutoUi();
    }

    private void loadDefaults() {
        SharedPreferences p = RecorderUtils.prefs(this);
        String q = p.getString("recordingQuality", "MEDIUM");
        rgQuality.check("LOW".equals(q) ? R.id.rbLow : "HIGH".equals(q) ? R.id.rbHigh : R.id.rbMedium);

        int minutes = p.getInt("autoStopMinutes", 0);
        swAutoSave.setChecked(minutes > 0);
        if (minutes == 30 || minutes <= 0) {
            rgDuration.check(R.id.rb30);
        } else if (minutes == 60) {
            rgDuration.check(R.id.rb60);
        } else if (minutes == 120) {
            rgDuration.check(R.id.rb120);
        } else {
            rgDuration.check(R.id.rbCustom);
            etHours.setText(String.valueOf(minutes / 60));
            etMinutes.setText(String.valueOf(minutes % 60));
        }
    }

    /** Returns 0 when Auto-Save is off, a positive minute count, or -1 when the input is invalid. */
    private int selectedMinutes() {
        if (!swAutoSave.isChecked()) {
            return 0;
        }
        int id = rgDuration.getCheckedRadioButtonId();
        if (id == R.id.rb30) {
            return 30;
        }
        if (id == R.id.rb60) {
            return 60;
        }
        if (id == R.id.rb120) {
            return 120;
        }
        int hours;
        int mins;
        try {
            hours = etHours.getText().toString().trim().isEmpty() ? 0
                    : Integer.parseInt(etHours.getText().toString().trim());
            mins = etMinutes.getText().toString().trim().isEmpty() ? 0
                    : Integer.parseInt(etMinutes.getText().toString().trim());
        } catch (NumberFormatException e) {
            showCustomError("Enter whole numbers only.");
            return -1;
        }
        if (mins > 59) {
            showCustomError("Minutes must be between 0 and 59.");
            return -1;
        }
        int total = hours * 60 + mins;
        if (total <= 0) {
            showCustomError("Enter a duration greater than 0.");
            return -1;
        }
        if (total > MAX_MINUTES) {
            showCustomError("Maximum Auto-Save time is 12 hours.");
            return -1;
        }
        hideCustomError();
        return total;
    }

    private void showCustomError(String message) {
        UiUtil.styleBadge(tvCustomError, UiUtil.STATUS_FAILED, message);
        tvCustomError.setVisibility(View.VISIBLE);
    }

    private void hideCustomError() {
        tvCustomError.setVisibility(View.GONE);
    }

    private void updateAutoUi() {
        boolean on = swAutoSave.isChecked();
        layoutAutoOptions.setVisibility(on ? View.VISIBLE : View.GONE);
        boolean custom = rgDuration.getCheckedRadioButtonId() == R.id.rbCustom;
        layoutCustom.setVisibility(on && custom ? View.VISIBLE : View.GONE);
        if (!on || !custom) {
            hideCustomError();
        }
        if (!on) {
            tvAutoSummary.setText("Recording continues until you stop it.");
            return;
        }
        int minutes = selectedMinutes();
        if (minutes > 0) {
            tvAutoSummary.setText("Auto-stop after " + RecorderUtils.autoStopLabel(minutes)
                    + ". The timer runs inside the recording service.");
        } else {
            tvAutoSummary.setText("Enter a valid time limit to continue.");
        }
    }

    private String selectedQuality() {
        int id = rgQuality.getCheckedRadioButtonId();
        if (id == R.id.rbLow) {
            return "LOW";
        }
        if (id == R.id.rbHigh) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    // --------------------------------------------------------------- actions

    private void onSaveDefault() {
        int minutes = selectedMinutes();
        if (minutes < 0) {
            return;
        }
        RecorderUtils.prefs(this).edit()
                .putString("recordingQuality", selectedQuality())
                .putInt("autoStopMinutes", minutes)
                .apply();
        Toast.makeText(this, "Defaults saved. They apply to your next recording.",
                Toast.LENGTH_SHORT).show();
    }

    public void onStart() {
        if (recordingActive) {
            startActivity(new Intent(this, RecordingActivity.class));
            finish();
            return;
        }
        int minutes = selectedMinutes();
        if (minutes < 0) {
            Toast.makeText(this, "Please fix the Auto-Save time first.", Toast.LENGTH_SHORT).show();
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

        String name = RecorderUtils.sanitizeName(etName.getText().toString());
        SharedPreferences.Editor editor = RecorderUtils.prefs(this).edit()
                .putString("recordingQuality", selectedQuality())
                .putInt("autoStopMinutes", minutes);
        if (name.isEmpty()) {
            editor.remove(RecorderUtils.KEY_NEXT_NAME);
        } else {
            editor.putString(RecorderUtils.KEY_NEXT_NAME, name);
        }
        editor.apply();

        pendingOpen = true;
        refreshStartButton();
        handler.removeCallbacks(clearPending);
        handler.postDelayed(clearPending, 6000);

        Intent intent = new Intent(this, AudioRecordingService.class);
        intent.setAction(AudioRecordingService.ACTION_START_RECORDING);
        try {
            startForegroundService(intent);
        } catch (RuntimeException e) {
            pendingOpen = false;
            refreshStartButton();
            Toast.makeText(this, "Could not start recording. Please try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onGrant() {
        String[] missing = RecorderUtils.missingRuntimePermissions(this);
        boolean blocked = missing.length > 0
                && !ActivityCompat.shouldShowRequestPermissionRationale(this, missing[0])
                && RecorderUtils.prefs(this).getBoolean("askedPermissions", false)
                && RecorderUtils.prefs(this).getBoolean("deniedOnce", false);
        if (missing.length == 0) {
            return;
        }
        if (blocked) {
            openAppSettings();
        } else {
            ActivityCompat.requestPermissions(this, missing, REQUEST_PERMISSIONS);
        }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }
        if (!RecorderUtils.hasMic(this)) {
            RecorderUtils.prefs(this).edit().putBoolean("deniedOnce", true).apply();
        }
        refreshReadiness();
        if (RecorderUtils.hasMic(this)) {
            if (startAfterPermission) {
                startAfterPermission = false;
                onStart();
            }
        } else if (startAfterPermission) {
            startAfterPermission = false;
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, android.Manifest.permission.RECORD_AUDIO)) {
                new AlertDialog.Builder(this)
                        .setTitle("Microphone permission needed")
                        .setMessage("Recording needs the microphone. Please allow it in App settings.")
                        .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                Toast.makeText(this, "Microphone permission is required to record.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------- readiness

    private void refreshReadiness() {
        boolean mic = RecorderUtils.hasMic(this);
        UiUtil.styleBadge(badgeMic, mic ? UiUtil.STATUS_COMPLETED : UiUtil.STATUS_FAILED,
                mic ? "Ready" : "Action required");

        long free = RecorderUtils.availableBytes(this);
        if (free < 0) {
            UiUtil.styleBadge(badgeStorage, UiUtil.STATUS_CANCELLED, "Unavailable");
        } else if (free < RecorderUtils.LOW_STORAGE_BYTES) {
            UiUtil.styleBadge(badgeStorage, UiUtil.STATUS_FAILED,
                    "Low: " + RecorderUtils.formatSize(free));
        } else {
            UiUtil.styleBadge(badgeStorage, UiUtil.STATUS_COMPLETED,
                    RecorderUtils.formatSize(free) + " free");
        }

        boolean notif = RecorderUtils.hasNotificationPermission(this);
        UiUtil.styleBadge(badgeNotif, notif ? UiUtil.STATUS_COMPLETED : UiUtil.STATUS_FAILED,
                notif ? "Ready" : "Not allowed");

        StringBuilder note = new StringBuilder();
        if (!mic) {
            note.append("Microphone access is required to record. ");
        }
        if (!notif) {
            note.append("Without notification permission, the recording notification and its controls may be hidden.");
        }
        tvReadyNote.setText(note.toString().trim());
        tvReadyNote.setVisibility(note.length() == 0 ? View.GONE : View.VISIBLE);
        btnGrant.setVisibility(mic && notif ? View.GONE : View.VISIBLE);
    }

    private void refreshStartButton() {
        btnStart.setEnabled(!pendingOpen);
        if (recordingActive) {
            btnStart.setText("Return to Recording");
        } else {
            btnStart.setText(pendingOpen ? "Starting\u2026" : "Start Recording");
        }
        btnSaveDefault.setEnabled(!recordingActive);
    }

    @Override
    protected void onResume() {
        super.onResume();
        recordingActive = RecorderUtils.prefs(this).getBoolean("isRecording", false);
        refreshReadiness();
        refreshStartButton();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clearPending);
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
