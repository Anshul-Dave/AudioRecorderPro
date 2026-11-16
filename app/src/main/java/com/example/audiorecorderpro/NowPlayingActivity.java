package com.example.audiorecorderpro;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

/** P08 - Now Playing. */
public class NowPlayingActivity extends AppCompatActivity {

    static final String EXTRA_PATH = "extra_path";

    private File file;
    private long durationMs = 0;
    private TextView tvPosition;
    private TextView tvDuration;
    private SeekBar seekBar;
    private ImageButton btnPlay;
    private MediaPlayer player;
    private boolean userSeeking = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (player == null) {
                return;
            }
            try {
                if (!userSeeking && durationMs > 0) {
                    int pos = player.getCurrentPosition();
                    seekBar.setProgress((int) (pos * 1000L / durationMs));
                    tvPosition.setText(RecorderUtils.formatClock(pos));
                }
                if (player.isPlaying()) {
                    handler.postDelayed(this, 250);
                }
            } catch (IllegalStateException ignored) {
                // released
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            return;
        }
        setContentView(R.layout.activity_now_playing);
        UiUtil.setupTopBar(this, "Now Playing", null);

        file = RecordingActions.fromIntent(this, EXTRA_PATH);
        if (file == null) {
            Toast.makeText(this, "This recording could not be found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvPosition = findViewById(R.id.tvPosition);
        tvDuration = findViewById(R.id.tvDuration);
        seekBar = findViewById(R.id.seekBar);
        btnPlay = findViewById(R.id.btnPlay);

        ((TextView) findViewById(R.id.tvName)).setText(file.getName());
        ((TextView) findViewById(R.id.tvMeta)).setText("Recorded "
                + RecorderUtils.formatDate(file.lastModified()) + " \u2022 "
                + RecorderUtils.formatSize(file.length()));
        durationMs = RecorderUtils.durationMs(file);
        tvDuration.setText(RecorderUtils.formatClock(durationMs));

        btnPlay.setOnClickListener(v -> toggle());
        findViewById(R.id.btnBack10).setOnClickListener(v -> skip(-10_000));
        findViewById(R.id.btnFwd10).setOnClickListener(v -> skip(10_000));
        findViewById(R.id.btnViewDetails).setOnClickListener(v -> {
            Intent i = new Intent(this, RecordingDetailsActivity.class);
            i.putExtra(RecordingDetailsActivity.EXTRA_PATH, file.getAbsolutePath());
            startActivity(i);
        });
        findViewById(R.id.btnShare).setOnClickListener(v -> RecordingActions.share(this, file));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && durationMs > 0) {
                    tvPosition.setText(RecorderUtils.formatClock(progress * durationMs / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (durationMs > 0 && prepare()) {
                    try {
                        player.seekTo((int) (bar.getProgress() * durationMs / 1000));
                    } catch (IllegalStateException ignored) {
                        // ignore
                    }
                }
            }
        });
    }

    private boolean prepare() {
        if (player != null) {
            return true;
        }
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(file.getAbsolutePath());
            mp.prepare();
            mp.setOnCompletionListener(m -> {
                setPlayIcon(true);
                seekBar.setProgress(0);
                tvPosition.setText("00:00");
                handler.removeCallbacks(ticker);
            });
            player = mp;
            if (mp.getDuration() > 0) {
                durationMs = mp.getDuration();
                tvDuration.setText(RecorderUtils.formatClock(durationMs));
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, "This recording cannot be played.", Toast.LENGTH_LONG).show();
            release();
            return false;
        }
    }

    private void toggle() {
        if (!prepare()) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                setPlayIcon(true);
                handler.removeCallbacks(ticker);
            } else {
                player.start();
                setPlayIcon(false);
                handler.removeCallbacks(ticker);
                handler.post(ticker);
            }
        } catch (IllegalStateException e) {
            release();
        }
    }

    private void skip(int deltaMs) {
        if (!prepare()) {
            return;
        }
        try {
            int target = Math.max(0, Math.min((int) durationMs, player.getCurrentPosition() + deltaMs));
            player.seekTo(target);
            if (durationMs > 0) {
                seekBar.setProgress((int) (target * 1000L / durationMs));
            }
            tvPosition.setText(RecorderUtils.formatClock(target));
        } catch (IllegalStateException ignored) {
            // ignore
        }
    }

    private void setPlayIcon(boolean play) {
        btnPlay.setImageResource(play ? R.drawable.ic_play : R.drawable.ic_pause);
        btnPlay.setContentDescription(play ? "Play" : "Pause");
    }

    private void release() {
        handler.removeCallbacks(ticker);
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
                // ignore
            }
            player = null;
        }
        if (btnPlay != null) {
            setPlayIcon(true);
        }
    }

    @Override
    protected void onStop() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    setPlayIcon(true);
                    handler.removeCallbacks(ticker);
                }
            } catch (IllegalStateException ignored) {
                // ignore
            }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        release();
        super.onDestroy();
    }
}
