package com.example.audiorecorderpro;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.text.DecimalFormat;

/** Feature center for P03-P19 supporting screens. */
public class FeatureHubActivity extends AppCompatActivity {
    private final String prefsName = "AudioRecorderPrefs";
    private LinearLayout root;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        root.setBackgroundColor(0xFFF9F8F6);
        scroll.addView(root);
        setContentView(scroll);
        addText("Audio Recorder Pro", 26, 0xFF14171F, true);
        addText("Feature Center · P03–P19", 14, 0xFF475569, false);
        addButton("🎙  Recording & Library", () -> startActivity(new Intent(this, MainActivity.class)));
        addButton("⏱  Automatic Stop Timer & Quality", () -> startActivity(new Intent(this, SettingsActivity.class)));
        addButton("🗓  Scheduled Recording Setup", this::scheduleSetup);
        addButton("✂  Audio Tools & Playback Speed", this::audioTools);
        addButton("★  Favorites & Bookmarks", this::favoritesAndBookmarks);
        addButton("💾  Storage Manager", this::showStorage);
        addButton("🔐  App Lock & Privacy", this::securitySettings);
        addButton("♻  Recovery & Help", this::recoveryHelp);
        addButton("ⓘ  About Audio Recorder Pro", () -> new AlertDialog.Builder(this).setTitle("About Audio Recorder Pro").setMessage("Offline-first audio recorder. Recordings are stored in the app's Music/Recordings folder. Check permissions and keep backups of important files.").setPositiveButton("OK", null).show());
        addText("Note: Scheduled recording, audio trimming, noise reduction and biometric lock need further native implementation and device testing; these options are not represented as completed features.", 13, 0xFF475569, false);
    }

    private void scheduleSetup() {
        final String[] choices = {"Set up reminder only", "Open recording settings", "Cancel"};
        new AlertDialog.Builder(this).setTitle("Scheduled Recording")
                .setMessage("Choose a setup action. This version saves a reminder preference only; it does not automatically start microphone recording at the scheduled time yet.")
                .setItems(choices, (d, which) -> {
                    if (which == 0) {
                        getSharedPreferences(prefsName, MODE_PRIVATE).edit().putBoolean("scheduleReminderEnabled", true).apply();
                        Toast.makeText(this, "Reminder preference saved", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) startActivity(new Intent(this, SettingsActivity.class));
                }).show();
    }

    private void audioTools() {
        new AlertDialog.Builder(this).setTitle("Audio Tools")
                .setItems(new String[]{"Open My Recordings", "Playback speed preference: 1x", "Trimmer status"}, (d, which) -> {
                    if (which == 0) startActivity(new Intent(this, RecordingLibraryActivity.class));
                    else if (which == 1) {
                        String[] speeds = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
                        new AlertDialog.Builder(this).setTitle("Preferred playback speed").setItems(speeds, (dd, i) -> {
                            getSharedPreferences(prefsName, MODE_PRIVATE).edit().putFloat("playbackSpeed", new float[]{0.5f,0.75f,1f,1.25f,1.5f,2f}[i]).apply();
                            Toast.makeText(this, "Preference saved. Playback integration is still required.", Toast.LENGTH_LONG).show();
                        }).show();
                    } else new AlertDialog.Builder(this).setMessage("Audio trimming/export UI is planned; destructive editing is not enabled in this build.").setPositiveButton("OK", null).show();
                }).show();
    }

    private void favoritesAndBookmarks() {
        SharedPreferences p = getSharedPreferences(prefsName, MODE_PRIVATE);
        boolean enabled = p.getBoolean("favoritesEnabled", false);
        new AlertDialog.Builder(this).setTitle("Favorites & Bookmarks")
                .setMessage("Favorites flag: " + (enabled ? "On" : "Off") + "\nBookmark storage is not yet connected to each recording.")
                .setPositiveButton(enabled ? "Turn Off" : "Turn On", (d, w) -> {
                    p.edit().putBoolean("favoritesEnabled", !enabled).apply();
                    Toast.makeText(this, "Preference saved", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Close", null).show();
    }

    private void showStorage() {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings");
        File[] files = dir.exists() ? dir.listFiles((f, name) -> name.toLowerCase().endsWith(".m4a")) : null;
        long bytes = 0; int count = 0;
        if (files != null) for (File f : files) { if (f.isFile()) { bytes += f.length(); count++; } }
        String size = bytes < 1024 * 1024 ? new DecimalFormat("0.0").format(bytes / 1024.0) + " KB" : new DecimalFormat("0.00").format(bytes / (1024.0 * 1024.0)) + " MB";
        new AlertDialog.Builder(this).setTitle("Storage Manager").setMessage("Recordings found: " + count + "\nTotal size: " + size + "\nFolder: " + dir.getAbsolutePath()).setPositiveButton("Open Library", (d,w) -> startActivity(new Intent(this, RecordingLibraryActivity.class))).setNegativeButton("Close", null).show();
    }

    private void securitySettings() {
        SharedPreferences p = getSharedPreferences(prefsName, MODE_PRIVATE);
        boolean lock = p.getBoolean("appLockEnabled", false);
        new AlertDialog.Builder(this).setTitle("App Lock & Privacy")
                .setMessage("App lock preference: " + (lock ? "Enabled" : "Disabled") + "\nThis toggle is only a saved preference; PIN/biometric authentication and file encryption are not implemented in this build. Recordings remain in app-specific storage.")
                .setPositiveButton(lock ? "Disable preference" : "Enable preference", (d,w) -> {
                    p.edit().putBoolean("appLockEnabled", !lock).apply();
                    Toast.makeText(this, "Preference saved; this does not lock the app yet", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Close", null).show();
    }

    private void recoveryHelp() {
        new AlertDialog.Builder(this).setTitle("Recovery & Help")
                .setMessage("If a recording is missing, check My Recordings and your app storage. Recovery of deleted files and interrupted temporary recordings is not yet implemented. Avoid clearing app data if you need to keep recordings.")
                .setPositiveButton("Open Library", (d,w) -> startActivity(new Intent(this, RecordingLibraryActivity.class)))
                .setNegativeButton("Close", null).show();
    }

    private void addText(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0, dp(8), 0, dp(8));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void addButton(String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setGravity(Gravity.CENTER_VERTICAL); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); lp.topMargin = dp(7); root.addView(b, lp);
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
