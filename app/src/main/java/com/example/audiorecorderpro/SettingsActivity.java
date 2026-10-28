package com.example.audiorecorderpro;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.Arrays;

/** P17 - Recording Settings (quality, auto-stop default, appearance). */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AudioRecorderPrefs";

    private RadioGroup rgQuality;
    private RadioGroup rgTheme;
    private Spinner spinnerAutoStop;
    private String[] autoStopNames = {"Off - record until stopped", "5 minutes", "10 minutes",
            "15 minutes", "30 minutes", "1 hour", "2 hours"};
    private int[] autoStopValues = {0, 5, 10, 15, 30, 60, 120};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            return;
        }
        setContentView(R.layout.activity_settings);
        UiUtil.setupTopBar(this, "Recording Settings", "Configure your audio preferences");

        rgQuality = findViewById(R.id.rgQuality);
        rgTheme = findViewById(R.id.rgTheme);
        spinnerAutoStop = findViewById(R.id.spinnerAutoStop);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String q = prefs.getString("recordingQuality", "MEDIUM");
        rgQuality.check("LOW".equals(q) ? R.id.rbLow : "HIGH".equals(q) ? R.id.rbHigh : R.id.rbStandard);

        int theme = Math.max(0, Math.min(2, prefs.getInt("themeMode", 0)));
        rgTheme.check(theme == 1 ? R.id.rbLight : theme == 2 ? R.id.rbDark : R.id.rbSystem);

        // Keep a custom value (set from Quick Setup) instead of silently overwriting it.
        int existing = prefs.getInt("autoStopMinutes", 0);
        boolean known = false;
        for (int v : autoStopValues) {
            if (v == existing) {
                known = true;
            }
        }
        if (!known && existing > 0) {
            String[] names = Arrays.copyOf(autoStopNames, autoStopNames.length + 1);
            int[] values = Arrays.copyOf(autoStopValues, autoStopValues.length + 1);
            names[names.length - 1] = "Custom - " + RecorderUtils.autoStopLabel(existing);
            values[values.length - 1] = existing;
            autoStopNames = names;
            autoStopValues = values;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, autoStopNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAutoStop.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < autoStopValues.length; i++) {
            if (autoStopValues[i] == existing) {
                selected = i;
            }
        }
        spinnerAutoStop.setSelection(selected);

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            int id = rgQuality.getCheckedRadioButtonId();
            String quality = id == R.id.rbLow ? "LOW" : id == R.id.rbHigh ? "HIGH" : "MEDIUM";
            int tid = rgTheme.getCheckedRadioButtonId();
            int themeMode = tid == R.id.rbLight ? 1 : tid == R.id.rbDark ? 2 : 0;
            int stopMinutes = autoStopValues[spinnerAutoStop.getSelectedItemPosition()];

            // Do not change defaults for a recording that is already running.
            SharedPreferences.Editor editor = prefs.edit()
                    .putString("recordingQuality", quality)
                    .putInt("themeMode", themeMode)
                    .putInt("autoStopMinutes", stopMinutes);
            editor.apply();
            applyTheme(themeMode);
            Toast.makeText(this, "Action completed successfully", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    static void applySavedTheme(AppCompatActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyTheme(prefs.getInt("themeMode", 0));
    }

    private static void applyTheme(int mode) {
        if (mode == 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (mode == 2) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
