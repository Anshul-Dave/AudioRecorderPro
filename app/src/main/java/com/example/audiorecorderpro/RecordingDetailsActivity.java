package com.example.audiorecorderpro;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.File;

/** P09 - Recording Details. */
public class RecordingDetailsActivity extends AppCompatActivity {

    static final String EXTRA_PATH = "extra_path";

    private File file;
    private TextView tvName;
    private LinearLayout infoContainer;
    private MaterialButton btnFav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            return;
        }
        setContentView(R.layout.activity_recording_details);
        UiUtil.setupTopBar(this, "Recording Details", null);

        file = RecordingActions.fromIntent(this, EXTRA_PATH);
        if (file == null) {
            Toast.makeText(this, "This recording could not be found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvName = findViewById(R.id.tvName);
        infoContainer = findViewById(R.id.infoContainer);
        btnFav = findViewById(R.id.btnFav);
        UiUtil.styleBadge(findViewById(R.id.badgeFormat), UiUtil.STATUS_SCHEDULED, "M4A");
        UiUtil.styleBadge(findViewById(R.id.badgeSaved), UiUtil.STATUS_COMPLETED, "Saved");

        findViewById(R.id.btnPlay).setOnClickListener(v -> {
            Intent i = new Intent(this, NowPlayingActivity.class);
            i.putExtra(NowPlayingActivity.EXTRA_PATH, file.getAbsolutePath());
            startActivity(i);
        });
        findViewById(R.id.btnRename).setOnClickListener(v ->
                RecordingActions.rename(this, file, renamed -> {
                    file = renamed;
                    bind();
                }));
        findViewById(R.id.btnShare).setOnClickListener(v -> RecordingActions.share(this, file));
        btnFav.setOnClickListener(v -> {
            boolean now = RecorderUtils.toggleFavorite(this, file);
            Toast.makeText(this, now ? "Added to favourites" : "Removed from favourites",
                    Toast.LENGTH_SHORT).show();
            refreshFav();
        });
        findViewById(R.id.btnDelete).setOnClickListener(v ->
                RecordingActions.confirmDelete(this, file, this::finish));

        bind();
    }

    private void bind() {
        long duration = RecorderUtils.durationMs(file);
        tvName.setText(file.getName());

        infoContainer.removeAllViews();
        addInfo("Recording Name", RecorderUtils.displayName(file));
        addInfo("Date Recorded", RecorderUtils.formatFullDate(file.lastModified()));
        addInfo("Duration", RecorderUtils.formatHms(duration));
        addInfo("Format", "M4A (AAC)");
        addInfo("File Size", RecorderUtils.formatSize(file.length()));
        String quality = RecorderUtils.getQuality(this, file);
        addInfo("Quality", quality == null ? "Not recorded" : RecorderUtils.qualityLabel(quality));
        refreshFav();
    }

    private void addInfo(String label, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_info_row, infoContainer, false);
        ((TextView) row.findViewById(R.id.infoLabel)).setText(label);
        ((TextView) row.findViewById(R.id.infoValue)).setText(value);
        infoContainer.addView(row);
    }

    private void refreshFav() {
        btnFav.setText(RecorderUtils.isFavorite(this, file) ? "Favourited" : "Favourite");
    }
}
