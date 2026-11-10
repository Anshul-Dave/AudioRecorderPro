package com.example.audiorecorderpro;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** P15 - Storage Manager. */
public class StorageActivity extends AppCompatActivity {

    private LinearLayout content;
    private boolean blocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            blocked = true;
            return;
        }
        content = UiUtil.setupSimpleScreen(this, "Storage Manager",
                "Manage saved recordings & space", R.id.nav_settings, true);
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

        long free = RecorderUtils.availableBytes(this);
        long total = RecorderUtils.totalBytes(this);
        File[] files = RecorderUtils.listRecordings(this);
        long used = RecorderUtils.recordingsTotalSize(this);

        LinearLayout summary = UiUtil.card(this);
        summary.addView(UiUtil.text(this, "Device Storage", 16, true, R.color.app_text));
        if (free < 0 || total <= 0) {
            summary.addView(UiUtil.text(this, "Storage information is unavailable.", 14, false,
                    R.color.app_text_secondary));
        } else {
            summary.addView(UiUtil.text(this, RecorderUtils.formatSize(free) + " available of "
                    + RecorderUtils.formatSize(total), 14, false, R.color.app_text_secondary));
            LinearProgressIndicator bar = new LinearProgressIndicator(this);
            bar.setMax(100);
            bar.setProgress((int) (((total - free) * 100L) / total));
            bar.setIndicatorColor(ContextCompat.getColor(this, R.color.app_primary));
            bar.setTrackColor(ContextCompat.getColor(this, R.color.app_border));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiUtil.dp(this, 10), 0, UiUtil.dp(this, 6));
            bar.setLayoutParams(lp);
            summary.addView(bar);
            if (free < RecorderUtils.LOW_STORAGE_BYTES) {
                TextView warn = new TextView(this);
                UiUtil.styleBadge(warn, UiUtil.STATUS_FAILED, "Low storage");
                summary.addView(warn);
            }
        }
        summary.addView(UiUtil.text(this, files.length + " Recordings (" + RecorderUtils.formatSize(used) + ")",
                14, true, R.color.app_text));
        content.addView(summary);

        content.addView(UiUtil.text(this, "Your Recordings", 18, true, R.color.app_text));

        if (files.length == 0) {
            LinearLayout empty = UiUtil.card(this);
            empty.addView(UiUtil.text(this, "No recordings yet. Start your first recording \uD83C\uDF99\uFE0F",
                    14, false, R.color.app_text_secondary));
            content.addView(empty);
            return;
        }

        File[] bySize = Arrays.copyOf(files, files.length);
        Arrays.sort(bySize, Comparator.comparingLong(File::length).reversed());
        for (File file : bySize) {
            content.addView(buildRow(file));
        }
    }

    private View buildRow(File file) {
        LinearLayout row = UiUtil.card(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiUtil.dp(this, 16), UiUtil.dp(this, 8), UiUtil.dp(this, 8), UiUtil.dp(this, 8));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = UiUtil.text(this, RecorderUtils.displayName(file), 15, true, R.color.app_text);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(name);
        column.addView(UiUtil.text(this, RecorderUtils.formatSize(file.length()) + " \u2022 "
                + RecorderUtils.formatDate(file.lastModified()), 13, false,
                R.color.app_text_secondary));
        row.addView(column);

        ImageButton delete = new ImageButton(this);
        delete.setImageResource(R.drawable.ic_delete);
        delete.setColorFilter(ContextCompat.getColor(this, R.color.app_record));
        delete.setBackgroundResource(android.R.color.transparent);
        delete.setContentDescription("Delete " + RecorderUtils.displayName(file));
        delete.setLayoutParams(new LinearLayout.LayoutParams(UiUtil.dp(this, 48), UiUtil.dp(this, 48)));
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete Recording?")
                .setMessage("\"" + RecorderUtils.displayName(file)
                        + "\" will be removed from your device permanently.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    String n = file.getName();
                    if (file.delete()) {
                        RecorderUtils.onDeleted(this, n);
                        Toast.makeText(this, "Recording deleted.", Toast.LENGTH_SHORT).show();
                        render();
                    } else {
                        Toast.makeText(this, "Could not delete the recording.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show());
        row.addView(delete);
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, RecordingDetailsActivity.class);
            i.putExtra(RecordingDetailsActivity.EXTRA_PATH, file.getAbsolutePath());
            startActivity(i);
        });
        return row;
    }
}
