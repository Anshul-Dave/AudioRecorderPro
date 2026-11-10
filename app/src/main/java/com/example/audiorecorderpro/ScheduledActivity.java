package com.example.audiorecorderpro;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.List;

/** P10 - Scheduled Recordings hub (tab): Upcoming, History, Settings. */
public class ScheduledActivity extends AppCompatActivity {

    private LinearLayout content;
    private LinearLayout stats;
    private LinearLayout tabContent;
    private TabLayout tabs;
    private int selectedTab = 0;
    private boolean blocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            blocked = true;
            return;
        }
        content = UiUtil.setupSimpleScreen(this, "Scheduled Recordings",
                "Manage your recording plans", R.id.nav_scheduled);

        MaterialButton add = findViewById(R.id.btnTopAction);
        add.setText("+ New");
        add.setVisibility(View.VISIBLE);
        add.setOnClickListener(v -> startActivity(new Intent(this, ScheduleEditActivity.class)));

        stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(stats);

        tabs = new TabLayout(this);
        tabs.setTabTextColors(ContextCompat.getColor(this, R.color.app_text_secondary),
                ContextCompat.getColor(this, R.color.app_primary));
        tabs.setSelectedTabIndicatorColor(ContextCompat.getColor(this, R.color.app_primary));
        tabs.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));
        tabs.addTab(tabs.newTab().setText("Upcoming"));
        tabs.addTab(tabs.newTab().setText("History"));
        tabs.addTab(tabs.newTab().setText("Settings"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                renderTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
        content.addView(tabs);

        tabContent = new LinearLayout(this);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        tabContent.setPadding(0, UiUtil.dp(this, 12), 0, 0);
        content.addView(tabContent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (blocked) {
            return;
        }
        ScheduleStore.reconcileInterrupted(this);
        ScheduleStore.sweepMissed(this);
        renderStats();
        TabLayout.Tab t = tabs.getTabAt(selectedTab);
        if (t != null && !t.isSelected()) {
            t.select();
        }
        renderTab();
    }

    private void renderStats() {
        stats.removeAllViews();
        addStat(String.valueOf(ScheduleStore.upcoming(this).size()), "Upcoming", false);
        addStat(String.valueOf(ScheduleStore.countStatus(this, ScheduleStore.ST_COMPLETED)),
                "Completed", false);
        addStat(String.valueOf(ScheduleStore.countStatus(this,
                ScheduleStore.ST_FAILED, ScheduleStore.ST_MISSED)), "Failed / Missed", true);
    }

    private void addStat(String number, String label, boolean last) {
        LinearLayout box = UiUtil.card(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(UiUtil.dp(this, 8), UiUtil.dp(this, 12), UiUtil.dp(this, 8), UiUtil.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, last ? 0 : UiUtil.dp(this, 8), UiUtil.dp(this, 12));
        box.setLayoutParams(lp);
        TextView n = UiUtil.text(this, number, 22, true, R.color.app_text);
        n.setGravity(Gravity.CENTER);
        TextView l = UiUtil.text(this, label, 12, false, R.color.app_text_secondary);
        l.setGravity(Gravity.CENTER);
        box.addView(n);
        box.addView(l);
        stats.addView(box);
    }

    private void renderTab() {
        tabContent.removeAllViews();
        if (selectedTab == 0) {
            renderUpcoming();
        } else if (selectedTab == 1) {
            renderHistory();
        } else {
            renderSettings();
        }
    }

    private void renderUpcoming() {
        List<ScheduleStore.Plan> plans = ScheduleStore.upcoming(this);
        if (plans.isEmpty()) {
            LinearLayout empty = UiUtil.card(this);
            empty.addView(UiUtil.text(this, "No upcoming recordings", 16, true, R.color.app_text));
            empty.addView(UiUtil.text(this, "Schedule a recording for a future time.", 14, false,
                    R.color.app_text_secondary));
            MaterialButton create = new MaterialButton(this);
            create.setText("Create Schedule");
            create.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, UiUtil.dp(this, 48));
            lp.setMargins(0, UiUtil.dp(this, 12), 0, 0);
            create.setLayoutParams(lp);
            create.setOnClickListener(v -> startActivity(new Intent(this, ScheduleEditActivity.class)));
            empty.addView(create);
            tabContent.addView(empty);
            return;
        }
        for (ScheduleStore.Plan p : plans) {
            LinearLayout card = UiUtil.card(this);
            card.addView(UiUtil.text(this, p.name, 16, true, R.color.app_text));
            card.addView(UiUtil.text(this, ScheduleStore.formatPlanDate(p.startMillis), 14, false,
                    R.color.app_text_secondary));
            TextView badge = new TextView(this);
            UiUtil.styleBadge(badge, UiUtil.STATUS_SCHEDULED, "Scheduled");
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bl.setMargins(0, UiUtil.dp(this, 8), 0, UiUtil.dp(this, 8));
            badge.setLayoutParams(bl);
            card.addView(badge);
            card.addView(UiUtil.text(this, "Duration: " + ScheduleStore.durationLabel(p.durationMin)
                    + " (Auto-save)", 13, false, R.color.app_text_secondary));
            card.addView(UiUtil.text(this, "View Details \u2192", 14, true, R.color.app_link));
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, ScheduleDetailsActivity.class);
                i.putExtra(ScheduleDetailsActivity.EXTRA_PLAN_ID, p.id);
                startActivity(i);
            });
            tabContent.addView(card);
        }
    }

    private void renderHistory() {
        List<ScheduleStore.Run> runs = ScheduleStore.runs(this);
        if (runs.isEmpty()) {
            LinearLayout empty = UiUtil.card(this);
            empty.addView(UiUtil.text(this, "No history yet", 16, true, R.color.app_text));
            empty.addView(UiUtil.text(this,
                    "Completed, failed, missed and cancelled recordings will appear here.", 14,
                    false, R.color.app_text_secondary));
            tabContent.addView(empty);
            return;
        }
        for (ScheduleStore.Run r : runs) {
            LinearLayout card = UiUtil.card(this);
            card.addView(UiUtil.text(this, r.name, 16, true, R.color.app_text));
            card.addView(UiUtil.text(this, ScheduleStore.formatPlanDate(
                    r.scheduledAt > 0 ? r.scheduledAt : r.createdAt), 14, false,
                    R.color.app_text_secondary));

            String badgeStatus;
            String badgeText;
            String hint;
            switch (r.status) {
                case ScheduleStore.ST_COMPLETED:
                    badgeStatus = UiUtil.STATUS_COMPLETED;
                    badgeText = "Completed";
                    hint = "Tap to play the recording";
                    break;
                case ScheduleStore.ST_MISSED:
                    badgeStatus = UiUtil.STATUS_FAILED;
                    badgeText = "Missed";
                    hint = "Tap to review issue & recovery";
                    break;
                case ScheduleStore.ST_FAILED:
                    badgeStatus = UiUtil.STATUS_FAILED;
                    badgeText = "Failed";
                    hint = "Tap to review issue & recovery";
                    break;
                case ScheduleStore.ST_CANCELLED:
                    badgeStatus = UiUtil.STATUS_CANCELLED;
                    badgeText = "Cancelled";
                    hint = "Cancelled by you";
                    break;
                default:
                    badgeStatus = UiUtil.STATUS_RECORDING;
                    badgeText = "Recording";
                    hint = "Tap to return to the recording";
                    break;
            }
            TextView badge = new TextView(this);
            UiUtil.styleBadge(badge, badgeStatus, badgeText);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bl.setMargins(0, UiUtil.dp(this, 8), 0, UiUtil.dp(this, 8));
            badge.setLayoutParams(bl);
            card.addView(badge);
            card.addView(UiUtil.text(this, hint, 13, false, R.color.app_text_secondary));

            card.setOnClickListener(v -> openRun(r));
            tabContent.addView(card);
        }
    }

    private void openRun(ScheduleStore.Run r) {
        if (ScheduleStore.ST_COMPLETED.equals(r.status)) {
            if (r.path != null && new File(r.path).exists()) {
                Intent i = new Intent(this, NowPlayingActivity.class);
                i.putExtra(NowPlayingActivity.EXTRA_PATH, r.path);
                startActivity(i);
            }
        } else if (ScheduleStore.ST_FAILED.equals(r.status) || ScheduleStore.ST_MISSED.equals(r.status)) {
            Intent i = new Intent(this, RecordingIssueActivity.class);
            i.putExtra(RecordingIssueActivity.EXTRA_RUN_ID, r.id);
            startActivity(i);
        } else if (ScheduleStore.ST_RECORDING.equals(r.status)) {
            startActivity(new Intent(this, RecordingActivity.class));
        }
    }

    private void renderSettings() {
        LinearLayout card = UiUtil.card(this);
        card.addView(UiUtil.text(this, "Scheduling Preferences", 16, true, R.color.app_text));

        int minutes = RecorderUtils.prefs(this).getInt("scheduleDefaultMinutes", 30);
        TextView dur = UiUtil.text(this, "Default Duration:  " + ScheduleStore.durationLabel(minutes)
                + "   (tap to change)", 15, false, R.color.app_text);
        dur.setMinHeight(UiUtil.dp(this, 48));
        dur.setGravity(Gravity.CENTER_VERTICAL);
        dur.setOnClickListener(v -> chooseDefaultDuration());
        card.addView(dur);

        String q = RecorderUtils.qualityLabel(RecorderUtils.prefs(this).getString("recordingQuality", "MEDIUM"));
        TextView qual = UiUtil.text(this, "Default Quality:  " + q + " (M4A)   (tap to change)", 15,
                false, R.color.app_text);
        qual.setMinHeight(UiUtil.dp(this, 48));
        qual.setGravity(Gravity.CENTER_VERTICAL);
        qual.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        card.addView(qual);
        tabContent.addView(card);

        MaterialButton ready = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        ready.setText("Check Device Readiness");
        ready.setAllCaps(false);
        ready.setTextColor(ContextCompat.getColor(this, R.color.app_link));
        ready.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtil.dp(this, 52)));
        ready.setOnClickListener(v -> startActivity(new Intent(this, ReadinessActivity.class)));
        tabContent.addView(ready);
    }

    private void chooseDefaultDuration() {
        String[] names = new String[ScheduleEditActivity.DURATIONS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = ScheduleStore.durationLabel(ScheduleEditActivity.DURATIONS[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Default Duration")
                .setItems(names, (d, which) -> {
                    RecorderUtils.prefs(this).edit()
                            .putInt("scheduleDefaultMinutes", ScheduleEditActivity.DURATIONS[which])
                            .apply();
                    renderTab();
                })
                .show();
    }
}
