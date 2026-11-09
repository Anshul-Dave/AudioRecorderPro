package com.example.audiorecorderpro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/** P06 - Recording Saved Successfully. */
public class RecordingSavedActivity extends AppCompatActivity {

    static final String EXTRA_PATH = "extra_path";

    private File file;
    private TextView tvName;
    private TextView tvMeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            return;
        }
        setContentView(R.layout.activity_recording_saved);
        UiUtil.setupTopBar(this, "Recording Details", null);

        file = RecordingActions.fromIntent(this, EXTRA_PATH);
        if (file == null) {
            Toast.makeText(this, "This recording could not be found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        tvName = findViewById(R.id.tvName);
        tvMeta = findViewById(R.id.tvMeta);
        UiUtil.styleBadge(findViewById(R.id.badgeFormat), UiUtil.STATUS_SCHEDULED, "M4A");
        bind();

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
        findViewById(R.id.btnLibrary).setOnClickListener(v -> {
            Intent i = new Intent(this, RecordingLibraryActivity.class);
            startActivity(i);
            finish();
        });
    }

    private void bind() {
        tvName.setText(file.getName());
        tvMeta.setText("Recorded " + RecorderUtils.formatDate(file.lastModified()) + " \u2022 "
                + RecorderUtils.formatDurationShort(RecorderUtils.durationMs(file)) + " \u2022 "
                + RecorderUtils.formatSize(file.length()));
    }
}
