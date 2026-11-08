package com.example.audiorecorderpro;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** P12 - Schedule Details. */
public class ScheduleDetailsActivity extends AppCompatActivity {

    static final String EXTRA_PLAN_ID = "extra_plan_id";

    private LinearLayout content;
    private long planId;
    private boolean blocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            blocked = true;
            return;
        }
        planId = getIntent().getLongExtra(EXTRA_PLAN_ID, 0L);
        content = UiUtil.setupSimpleScreen(this, "Schedule Details", null, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (blocked) {
            return;
        }
        render();
    }

    private void render() {
        content.removeAllViews();
        ScheduleStore.Plan plan = ScheduleStore.getPlan(this, planId);
        if (plan == null || !plan.active) {
            Toast.makeText(this, "This schedule is no longer active.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout header = UiUtil.card(this);
        header.addView(UiUtil.text(this, plan.name, 20, true, R.color.app_text));
        header.addView(UiUtil.text(this, ScheduleStore.formatLongDate(plan.startMillis), 14, false,
                R.color.app_text_secondary));
        TextView badge = new TextView(this);
        UiUtil.styleBadge(badge, UiUtil.STATUS_SCHEDULED, "Scheduled");
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.setMargins(0, UiUtil.dp(this, 10), 0, UiUtil.dp(this, 10));
        badge.setLayoutParams(bl);
        header.addView(badge);
        header.addView(UiUtil.text(this, "Scheduled for " + ScheduleStore.formatTime(plan.startMillis)
                + " \u2022 " + ScheduleStore.repeatLabel(plan.repeat), 14, false, R.color.app_text));
        header.addView(UiUtil.text(this, "Records for " + ScheduleStore.durationLabel(plan.durationMin)
                + " and saves automatically", 14, false, R.color.app_text_secondary));
        content.addView(header);

        LinearLayout timeline = UiUtil.card(this);
        timeline.addView(UiUtil.text(this, "Schedule Timeline", 16, true, R.color.app_text));
        timeline.addView(timelineRow("Schedule Created",
                new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .format(new Date(plan.createdAt)), R.color.badge_completed_text));
        timeline.addView(timelineRow("Pending Execution",
                "Expected: " + new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .format(new Date(plan.startMillis)), R.color.badge_scheduled_text));
        content.addView(timeline);

        if (!ScheduleStore.canScheduleExact(this)) {
            LinearLayout warn = UiUtil.card(this);
            TextView b = new TextView(this);
            UiUtil.styleBadge(b, UiUtil.STATUS_FAILED, "Exact alarms are off");
            warn.addView(b);
            warn.addView(UiUtil.text(this,
                    "Android may start this recording a few minutes late. Open the readiness check to allow exact alarms.",
                    13, false, R.color.app_text_secondary));
            content.addView(warn);
        }

        MaterialButton edit = new MaterialButton(this);
        edit.setText("Edit Schedule");
        edit.setAllCaps(false);
        edit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtil.dp(this, 56)));
        edit.setOnClickListener(v -> {
            Intent i = new Intent(this, ScheduleEditActivity.class);
            i.putExtra(ScheduleEditActivity.EXTRA_PLAN_ID, plan.id);
            startActivity(i);
        });
        content.addView(edit);

        MaterialButton cancel = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancel.setText("Cancel Schedule");
        cancel.setAllCaps(false);
        cancel.setTextColor(ContextCompat.getColor(this, R.color.app_record));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtil.dp(this, 52));
        cl.setMargins(0, UiUtil.dp(this, 8), 0, 0);
        cancel.setLayoutParams(cl);
        cancel.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Cancel this schedule?")
                .setMessage("\"" + plan.name + "\" will not record. You can create a new schedule any time.")
                .setNegativeButton("Keep Schedule", null)
                .setPositiveButton("Cancel Schedule", (d, w) -> {
                    ScheduleStore.cancelPlan(this, plan.id);
                    Toast.makeText(this, "Schedule cancelled.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show());
        content.addView(cancel);
    }

    private LinearLayout timelineRow(String title, String subtitle, int dotColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiUtil.dp(this, 10), 0, 0);

        ImageView dot = new ImageView(this);
        dot.setImageResource(R.drawable.ic_dot);
        dot.setColorFilter(ContextCompat.getColor(this, dotColor));
        dot.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(dot, new LinearLayout.LayoutParams(UiUtil.dp(this, 14), UiUtil.dp(this, 14)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(UiUtil.dp(this, 12), 0, 0, 0);
        col.setLayoutParams(cp);
        col.addView(UiUtil.text(this, title, 15, true, R.color.app_text));
        col.addView(UiUtil.text(this, subtitle, 13, false, R.color.app_text_secondary));
        row.addView(col);
        return row;
    }
}
