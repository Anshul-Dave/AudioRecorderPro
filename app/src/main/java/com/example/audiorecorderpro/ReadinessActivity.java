package com.example.audiorecorderpro;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

/** P13 - Recording Readiness. Shows real permission / storage / alarm status. */
public class ReadinessActivity extends AppCompatActivity {

    private static final int REQUEST_PERMS = 301;

    private LinearLayout content;
    private boolean blocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            blocked = true;
            return;
        }
        content = UiUtil.setupSimpleScreen(this, "Recording Readiness",
                "Check device before scheduling", 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (blocked) {
            return;
        }
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!RecorderUtils.hasMic(this)) {
            RecorderUtils.prefs(this).edit().putBoolean("deniedOnce", true).apply();
        }
        render();
    }

    private void render() {
        content.removeAllViews();

        boolean mic = RecorderUtils.hasMic(this);
        boolean notif = RecorderUtils.hasNotificationPermission(this);
        long free = RecorderUtils.availableBytes(this);
        boolean storageOk = free < 0 || free >= RecorderUtils.LOW_STORAGE_BYTES;
        boolean exact = ScheduleStore.canScheduleExact(this);
        boolean allRequired = mic && notif && storageOk && exact;

        LinearLayout status = UiUtil.card(this);
        TextView badge = new TextView(this);
        UiUtil.styleBadge(badge, allRequired ? UiUtil.STATUS_COMPLETED : UiUtil.STATUS_FAILED,
                allRequired ? "All Optimal" : "Action Needed");
        status.addView(badge);
        status.addView(UiUtil.text(this, allRequired ? "Ready to Schedule" : "Fix the items below",
                20, true, R.color.app_text));
        status.addView(UiUtil.text(this, allRequired
                        ? "Permissions and alarms are in place. Android can still limit background "
                        + "recording on some phones, so check the result after your first scheduled run."
                        : "Some settings need your attention before scheduled recordings can work reliably.",
                14, false, R.color.app_text_secondary));
        content.addView(status);

        content.addView(UiUtil.text(this, "Readiness Checklist", 18, true, R.color.app_text));

        content.addView(row("Microphone Permission", "Required to capture audio",
                mic, "Granted", "Not granted", mic ? null : "Grant", v -> requestMic()));
        content.addView(row("Notification Permission", "Active foreground service alerts",
                notif, "Granted", "Not granted", notif ? null : "Grant", v -> requestNotif()));
        content.addView(row("Storage Availability",
                free < 0 ? "Storage information unavailable"
                        : RecorderUtils.formatSize(free) + " available locally",
                storageOk, "Passed", "Low storage", storageOk ? null : "Manage",
                v -> startActivity(new Intent(this, StorageActivity.class))));
        content.addView(row("Exact Alarm", "Lets the recording start on time",
                exact, "Allowed", "Not allowed", exact ? null : "Allow", v -> openExactAlarm()));

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean unrestricted = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        content.addView(row("Battery Optimization (recommended)",
                "Unrestricted use helps recordings start on time",
                unrestricted, "Unrestricted", "Optimized", unrestricted ? null : "Review",
                v -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))));

        MaterialButton next = new MaterialButton(this);
        next.setText("Continue to Schedule");
        next.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtil.dp(this, 56));
        lp.setMargins(0, UiUtil.dp(this, 8), 0, 0);
        next.setLayoutParams(lp);
        next.setOnClickListener(v -> startActivity(new Intent(this, ScheduleEditActivity.class)));
        content.addView(next);
    }

    private LinearLayout row(String title, String subtitle, boolean ok, String okText,
                             String badText, String fixLabel, android.view.View.OnClickListener fix) {
        LinearLayout card = UiUtil.card(this);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(UiUtil.text(this, title, 15, true, R.color.app_text));
        col.addView(UiUtil.text(this, subtitle, 13, false, R.color.app_text_secondary));
        top.addView(col);

        TextView badge = new TextView(this);
        boolean recommended = title.contains("recommended");
        UiUtil.styleBadge(badge,
                ok ? UiUtil.STATUS_COMPLETED : (recommended ? UiUtil.STATUS_SCHEDULED : UiUtil.STATUS_FAILED),
                ok ? okText : badText);
        top.addView(badge);
        card.addView(top);

        if (fixLabel != null) {
            MaterialButton b = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(fixLabel);
            b.setAllCaps(false);
            b.setTextColor(ContextCompat.getColor(this, R.color.app_link));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, UiUtil.dp(this, 48));
            lp.setMargins(0, UiUtil.dp(this, 8), 0, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(fix);
            card.addView(b);
        }
        return card;
    }

    private void requestMic() {
        boolean blockedForever = RecorderUtils.prefs(this).getBoolean("deniedOnce", false)
                && !ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.RECORD_AUDIO);
        if (blockedForever) {
            openAppSettings();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_PERMS);
        }
    }

    private void requestNotif() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this, android.Manifest.permission.POST_NOTIFICATIONS)
                    || !RecorderUtils.prefs(this).getBoolean("notifAsked", false)) {
                RecorderUtils.prefs(this).edit().putBoolean("notifAsked", true).apply();
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMS);
            } else {
                openAppSettings();
            }
        }
    }

    private void openExactAlarm() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                i.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(i);
            }
        } catch (RuntimeException e) {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }
}
