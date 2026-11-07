package com.example.audiorecorderpro;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** P11 - Create Schedule (also used to edit a schedule). */
public class ScheduleEditActivity extends AppCompatActivity {

    static final String EXTRA_PLAN_ID = "extra_plan_id";
    static final String EXTRA_NAME = "extra_name";
    static final int[] DURATIONS = {5, 15, 30, 45, 60, 90, 120, 180, 240};

    private TextInputLayout tilName;
    private EditText etName;
    private EditText etDate;
    private EditText etTime;
    private Spinner spDuration;
    private Spinner spRepeat;
    private TextView badgeReady;
    private TextView tvReadyLines;
    private final Calendar cal = Calendar.getInstance();
    private long editingId = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            return;
        }
        setContentView(R.layout.activity_schedule_edit);

        editingId = getIntent().getLongExtra(EXTRA_PLAN_ID, 0L);
        ScheduleStore.Plan existing = editingId == 0L ? null : ScheduleStore.getPlan(this, editingId);
        if (editingId != 0L && existing == null) {
            Toast.makeText(this, "This schedule could not be found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        UiUtil.setupTopBar(this, existing == null ? "Create Schedule" : "Edit Schedule", null);

        tilName = findViewById(R.id.tilName);
        etName = findViewById(R.id.etName);
        etDate = findViewById(R.id.etDate);
        etTime = findViewById(R.id.etTime);
        spDuration = findViewById(R.id.spDuration);
        spRepeat = findViewById(R.id.spRepeat);
        badgeReady = findViewById(R.id.badgeReady);
        tvReadyLines = findViewById(R.id.tvReadyLines);

        String[] durationNames = new String[DURATIONS.length];
        for (int i = 0; i < DURATIONS.length; i++) {
            durationNames[i] = ScheduleStore.durationLabel(DURATIONS[i]);
        }
        ArrayAdapter<String> dAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, durationNames);
        dAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDuration.setAdapter(dAdapter);

        ArrayAdapter<String> rAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Does not repeat", "Repeats Daily", "Repeats Weekly"});
        rAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRepeat.setAdapter(rAdapter);

        if (existing != null) {
            etName.setText(existing.name);
            cal.setTimeInMillis(existing.startMillis);
            spDuration.setSelection(indexOfDuration(existing.durationMin));
            spRepeat.setSelection(existing.repeat);
        } else {
            String prefill = getIntent().getStringExtra(EXTRA_NAME);
            if (prefill != null) {
                etName.setText(prefill);
            }
            // Default: one hour from now, rounded up to the next 5 minutes.
            long base = System.currentTimeMillis() + 60L * 60L * 1000L;
            long step = 5L * 60L * 1000L;
            cal.setTimeInMillis(((base + step - 1) / step) * step);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            int def = RecorderUtils.prefs(this).getInt("scheduleDefaultMinutes", 30);
            spDuration.setSelection(indexOfDuration(def));
        }
        showDateTime();

        etDate.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            cal.set(y, m, d);
            showDateTime();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());

        etTime.setOnClickListener(v -> new TimePickerDialog(this, (view, h, min) -> {
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, min);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            showDateTime();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(this)).show());

        MaterialButton top = findViewById(R.id.btnTopAction);
        top.setText("Save");
        top.setVisibility(View.VISIBLE);
        top.setOnClickListener(v -> save());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnOpenReadiness).setOnClickListener(v ->
                startActivity(new Intent(this, ReadinessActivity.class)));
    }

    private int indexOfDuration(int minutes) {
        for (int i = 0; i < DURATIONS.length; i++) {
            if (DURATIONS[i] == minutes) {
                return i;
            }
        }
        return 2;
    }

    private void showDateTime() {
        Date d = cal.getTime();
        etDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d));
        etTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d));
    }

    private void save() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            tilName.setError("Schedule name is required");
            return;
        }
        tilName.setError(null);

        if (cal.getTimeInMillis() < System.currentTimeMillis() + 30_000L) {
            Toast.makeText(this, "Choose a time in the future.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!RecorderUtils.hasMic(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Microphone permission needed")
                    .setMessage("Scheduled recording cannot ask for permission later. Grant microphone access first.")
                    .setPositiveButton("Open Readiness Check", (d, w) ->
                            startActivity(new Intent(this, ReadinessActivity.class)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        ScheduleStore.Plan plan = new ScheduleStore.Plan();
        plan.id = editingId;
        plan.name = name;
        plan.startMillis = cal.getTimeInMillis();
        plan.durationMin = DURATIONS[spDuration.getSelectedItemPosition()];
        plan.repeat = spRepeat.getSelectedItemPosition();
        ScheduleStore.savePlan(this, plan);

        RecorderUtils.prefs(this).edit().putInt("scheduleDefaultMinutes", plan.durationMin).apply();

        if (!ScheduleStore.canScheduleExact(this)) {
            Toast.makeText(this,
                    "Saved. Exact alarms are off, so the recording may start a few minutes late.",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Action completed successfully", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (badgeReady == null) {
            return;
        }
        boolean mic = RecorderUtils.hasMic(this);
        long free = RecorderUtils.availableBytes(this);
        boolean storageOk = free < 0 || free >= RecorderUtils.LOW_STORAGE_BYTES;
        boolean ready = mic && storageOk;
        UiUtil.styleBadge(badgeReady, ready ? UiUtil.STATUS_COMPLETED : UiUtil.STATUS_FAILED,
                ready ? "Ready" : "Action needed");
        tvReadyLines.setText((mic ? "\u2713 Microphone permission granted"
                : "\u2715 Microphone permission is missing")
                + "\n" + (storageOk
                ? "\u2713 Sufficient storage available (" + RecorderUtils.formatSize(free) + ")"
                : "\u2715 Low storage (" + RecorderUtils.formatSize(free) + ")")
                + "\n" + (ScheduleStore.canScheduleExact(this)
                ? "\u2713 Exact alarms allowed"
                : "\u2022 Exact alarms are off (recording may start late)"));
    }
}
