package com.example.audiorecorderpro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.File;

/** P14 - Recording Issue (missed / interrupted) with honest recovery options. */
public class RecordingIssueActivity extends AppCompatActivity {

    static final String EXTRA_RUN_ID = "extra_run_id";

    private LinearLayout content;
    private long runId;
    private boolean blocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            blocked = true;
            return;
        }
        runId = getIntent().getLongExtra(EXTRA_RUN_ID, 0L);
        content = UiUtil.setupSimpleScreen(this, "Recording Issue",
                "Missed / Interrupted Schedule", 0);
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
        ScheduleStore.Run run = ScheduleStore.getRun(this, runId);
        if (run == null) {
            Toast.makeText(this, "This issue could not be found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        boolean missed = ScheduleStore.ST_MISSED.equals(run.status);
        LinearLayout header = UiUtil.card(this);
        TextView badge = new TextView(this);
        UiUtil.styleBadge(badge, UiUtil.STATUS_FAILED, missed ? "Missed" : "Interrupted / Failed");
        header.addView(badge);
        header.addView(UiUtil.text(this, run.name, 20, true, R.color.app_text));
        header.addView(UiUtil.text(this, ScheduleStore.formatPlanDate(
                run.scheduledAt > 0 ? run.scheduledAt : run.createdAt), 14, false,
                R.color.app_text_secondary));
        header.addView(UiUtil.text(this, run.reason == null || run.reason.isEmpty()
                ? "The recording did not complete." : run.reason, 14, false, R.color.app_text));
        content.addView(header);

        // Recovery status: only claim what we can verify.
        File partial = run.path == null ? null : new File(run.path);
        boolean exists = partial != null && partial.exists() && partial.length() > 0;
        long duration = exists ? RecorderUtils.durationMs(partial) : 0L;
        boolean playable = exists && duration > 0;

        LinearLayout recovery = UiUtil.card(this);
        recovery.addView(UiUtil.text(this, "Recovery Status", 16, true, R.color.app_text));
        if (playable) {
            recovery.addView(UiUtil.text(this, "Partial Recording Available", 15, true,
                    R.color.badge_completed_text));
            recovery.addView(UiUtil.text(this, "A segment (" + RecorderUtils.formatSize(partial.length())
                    + ", " + RecorderUtils.formatDurationShort(duration)
                    + ") was captured before the interruption and can be played.", 14, false,
                    R.color.app_text_secondary));
        } else if (exists) {
            recovery.addView(UiUtil.text(this, "Damaged Segment Found", 15, true,
                    R.color.badge_failed_text));
            recovery.addView(UiUtil.text(this, "A temporary file (" + RecorderUtils.formatSize(partial.length())
                    + ") was found, but it cannot be played because the recording was not finished "
                    + "properly. It cannot be repaired.", 14, false, R.color.app_text_secondary));
        } else {
            recovery.addView(UiUtil.text(this, "No Audio Captured", 15, true, R.color.app_text));
            recovery.addView(UiUtil.text(this, "No saved audio exists for this attempt.", 14, false,
                    R.color.app_text_secondary));
        }
        content.addView(recovery);

        if (playable) {
            content.addView(button("Recover Recording Segment", true, v -> {
                ScheduleStore.markResolved(this, run.id);
                Intent i = new Intent(this, NowPlayingActivity.class);
                i.putExtra(NowPlayingActivity.EXTRA_PATH, partial.getAbsolutePath());
                startActivity(i);
                finish();
            }));
        } else if (exists) {
            content.addView(button("Delete Damaged File", false, v -> {
                String name = partial.getName();
                if (partial.delete()) {
                    RecorderUtils.onDeleted(this, name);
                    Toast.makeText(this, "Damaged file deleted.", Toast.LENGTH_SHORT).show();
                    render();
                }
            }));
        }

        content.addView(button("Reschedule", !playable && !exists, v -> {
            Intent i = new Intent(this, ScheduleEditActivity.class);
            i.putExtra(ScheduleEditActivity.EXTRA_NAME, run.name);
            startActivity(i);
        }));
        content.addView(button("Mark as Reviewed", false, v -> {
            ScheduleStore.markResolved(this, run.id);
            Toast.makeText(this, "Action completed successfully", Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private MaterialButton button(String text, boolean primary, android.view.View.OnClickListener l) {
        MaterialButton b = primary ? new MaterialButton(this)
                : new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        if (!primary) {
            b.setTextColor(ContextCompat.getColor(this, R.color.app_link));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtil.dp(this, 52));
        lp.setMargins(0, 0, 0, UiUtil.dp(this, 8));
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }
}
