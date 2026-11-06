package com.example.audiorecorderpro;

import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.content.res.ColorStateList;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class RecordingLibraryActivity extends AppCompatActivity {

    private LinearLayout recordingsContainer;
    private TextView tvEmptyState;

    private MediaPlayer mediaPlayer;
    private File currentlyPlayingFile;

    private Button currentlyPlayingButton;
    private SeekBar currentSeekBar;
    private TextView currentDurationView;

    private boolean isUserSeeking = false;
    private int filterMode = 0; // 0 all, 1 favourites, 2 recent (7 days), 3 long (30+ min)

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Update the playback timer and seekbar.
    private final Runnable playbackUpdater = new Runnable() {
        @Override
        public void run() {

            if (mediaPlayer == null) {
                return;
            }

            try {
                int duration = mediaPlayer.getDuration();
                int position = mediaPlayer.getCurrentPosition();

                if (duration > 0) {

                    if (currentDurationView != null) {
                        currentDurationView.setText(
                                formatTime(position)
                                        + " / "
                                        + formatTime(duration)
                        );
                    }

                    if (currentSeekBar != null && !isUserSeeking) {
                        currentSeekBar.setProgress(
                                (int) ((position * 1000L) / duration)
                        );
                    }
                }

                // Continue updating while playing or paused.
                handler.postDelayed(this, 200);

            } catch (IllegalStateException ignored) {
                handler.removeCallbacks(this);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            return;
        }
        setContentView(R.layout.activity_recording_library);

        recordingsContainer = findViewById(R.id.recordingsContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        UiUtil.setupBottomNav(this, R.id.nav_recordings);

        com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.chipGroup);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chipFav) {
                filterMode = 1;
            } else if (id == R.id.chipRecent) {
                filterMode = 2;
            } else if (id == R.id.chipLong) {
                filterMode = 3;
            } else {
                filterMode = 0;
            }
            stopCurrentPlayback();
            loadRecordings();
        });

        loadRecordings();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (recordingsContainer != null) {
            loadRecordings();
        }
    }

    private File getRecordingsDirectory() {

        File musicDirectory =
                getExternalFilesDir(Environment.DIRECTORY_MUSIC);

        if (musicDirectory == null) {
            return null;
        }

        return new File(musicDirectory, "Recordings");
    }

    private void loadRecordings() {

        recordingsContainer.removeAllViews();

        File directory = getRecordingsDirectory();

        if (directory == null) {
            showEmptyState("Storage ઉપલબ્ધ નથી.");
            return;
        }

        if (!directory.exists() && !directory.mkdirs()) {
            showEmptyState("Recording Folder બનાવી શકાયો નથી.");
            return;
        }

        File[] files = directory.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".m4a")
        );

        if (files == null || files.length == 0) {
            showEmptyState(
                    "હજુ કોઈ Recording નથી.\nપહેલાં Audio Record કરો."
            );
            return;
        }

        Arrays.sort(
                files,
                Comparator.comparingLong(File::lastModified).reversed()
        );

        tvEmptyState.setVisibility(android.view.View.GONE);

        int shown = 0;
        long weekAgo = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (filterMode == 1 && !RecorderUtils.isFavorite(this, file)) {
                continue;
            }
            if (filterMode == 2 && file.lastModified() < weekAgo) {
                continue;
            }
            if (filterMode == 3 && RecorderUtils.durationMs(file) < 30L * 60L * 1000L) {
                continue;
            }
            addRecordingItem(file);
            shown++;
        }

        if (shown == 0) {
            showEmptyState(filterMode == 1 ? "No favourite recordings yet."
                    : filterMode == 2 ? "No recordings in the last 7 days."
                    : "No recordings of 30 minutes or longer.");
        }
    }

    private void showEmptyState(String message) {
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(android.view.View.VISIBLE);
    }

    private void addRecordingItem(File file) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.bg_card);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);

        TextView nameView = new TextView(this);
        nameView.setText((RecorderUtils.isFavorite(this, file) ? "\u2605 " : "")
                + RecorderUtils.displayName(file));
        nameView.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface));
        nameView.setTextSize(16);
        nameView.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        TextView detailsView = new TextView(this);
        detailsView.setText(RecorderUtils.formatDate(file.lastModified())
                + " \u2022 " + formatFileSize(file.length()));
        detailsView.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        detailsView.setTextSize(13);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgress(0);

        TextView durationView = new TextView(this);
        durationView.setText("Loading duration...");
        durationView.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        durationView.setTextSize(12);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setWeightSum(4f);

        Button playButton = makeActionButton("Play");
        Button renameButton = makeActionButton("Rename");
        Button shareButton = makeActionButton("Share");
        Button deleteButton = makeActionButton("Delete");

        addActionButton(buttons, playButton);
        addActionButton(buttons, renameButton);
        addActionButton(buttons, shareButton);
        addActionButton(buttons, deleteButton);

        card.addView(nameView);
        card.addView(detailsView);
        card.addView(seekBar);
        card.addView(durationView);
        card.addView(buttons);

        card.setOnClickListener(view -> {
            Intent detailsIntent = new Intent(this, NowPlayingActivity.class);
            detailsIntent.putExtra(NowPlayingActivity.EXTRA_PATH, file.getAbsolutePath());
            startActivity(detailsIntent);
        });

        recordingsContainer.addView(card);

        // Load total duration without playing the audio.
        loadAudioDuration(file, durationView);

        playButton.setOnClickListener(view -> {

            if (file.equals(currentlyPlayingFile)
                    && mediaPlayer != null) {

                togglePlayback();

            } else {

                playRecording(
                        file,
                        playButton,
                        seekBar,
                        durationView
                );
            }
        });

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int progress,
                            boolean fromUser
                    ) {

                        if (fromUser
                                && mediaPlayer != null
                                && file.equals(currentlyPlayingFile)) {

                            try {
                                int duration = mediaPlayer.getDuration();

                                int position = (int) (
                                        progress * (long) duration / 1000
                                );

                                durationView.setText(
                                        formatTime(position)
                                                + " / "
                                                + formatTime(duration)
                                );

                            } catch (IllegalStateException ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        isUserSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {

                        if (mediaPlayer != null
                                && file.equals(currentlyPlayingFile)) {

                            try {
                                int duration = mediaPlayer.getDuration();

                                int position = (int) (
                                        bar.getProgress() * (long) duration
                                                / 1000
                                );

                                mediaPlayer.seekTo(position);

                            } catch (IllegalStateException ignored) {
                            }
                        }

                        isUserSeeking = false;
                    }
                }
        );

        renameButton.setOnClickListener(view -> renameRecording(file));

        shareButton.setOnClickListener(view -> shareRecording(file));

        deleteButton.setOnClickListener(view -> deleteRecording(file));
    }

    private int resolveColor(int attribute) {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(attribute, value, true);
        return value.data;
    }

    private Button makeActionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(1), 0, dp(1), 0);
        button.setMaxLines(1);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return button;
    }

    private void addActionButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        row.addView(button, params);
    }

    // Read duration from the audio file.
    private void loadAudioDuration(File file, TextView durationView) {

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(file.getAbsolutePath());

            String durationString = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
            );

            if (durationString != null) {

                long durationMs = Long.parseLong(durationString);

                durationView.setText(
                        "00:00 / " + formatTime(durationMs)
                );

            } else {
                durationView.setText("Duration unavailable");
            }

        } catch (Exception e) {

            durationView.setText("Duration unavailable");

        } finally {

            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void playRecording(
            File file,
            Button playButton,
            SeekBar seekBar,
            TextView durationView
    ) {

        stopCurrentPlayback();

        try {

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());

            mediaPlayer.setOnPreparedListener(player -> {

                currentlyPlayingFile = file;
                currentlyPlayingButton = playButton;
                currentSeekBar = seekBar;
                currentDurationView = durationView;

                int duration = player.getDuration();

                seekBar.setMax(1000);
                seekBar.setProgress(0);

                durationView.setText(
                        formatTime(0) + " / " + formatTime(duration)
                );

                player.start();

                playButton.setText("PAUSE");

                handler.removeCallbacks(playbackUpdater);
                handler.post(playbackUpdater);
            });

            mediaPlayer.setOnCompletionListener(player -> {

                int duration = player.getDuration();

                durationView.setText(
                        formatTime(duration) + " / " + formatTime(duration)
                );

                seekBar.setProgress(1000);
                playButton.setText("PLAY");

                stopCurrentPlayback();
            });

            mediaPlayer.setOnErrorListener((player, what, extra) -> {

                Toast.makeText(
                        this,
                        "Audio ચલાવી શકાયું નથી.",
                        Toast.LENGTH_SHORT
                ).show();

                stopCurrentPlayback();

                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (IOException | IllegalStateException e) {

            stopCurrentPlayback();

            Toast.makeText(
                    this,
                    "Audio ખોલવામાં Error આવ્યો.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void togglePlayback() {

        if (mediaPlayer == null) {
            return;
        }

        try {

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

                if (currentlyPlayingButton != null) {
                    currentlyPlayingButton.setText("RESUME");
                }

            } else {

                mediaPlayer.start();

                if (currentlyPlayingButton != null) {
                    currentlyPlayingButton.setText("PAUSE");
                }
            }

            handler.removeCallbacks(playbackUpdater);
            handler.post(playbackUpdater);

        } catch (IllegalStateException e) {

            Toast.makeText(
                    this,
                    "Player ફરી શરૂ કરી શકાયો નથી.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void stopCurrentPlayback() {

        handler.removeCallbacks(playbackUpdater);

        if (mediaPlayer != null) {

            try {
                mediaPlayer.setOnPreparedListener(null);
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);

                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }

            } catch (IllegalStateException ignored) {
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (currentlyPlayingButton != null) {
            currentlyPlayingButton.setText("PLAY");
        }

        if (currentSeekBar != null) {
            currentSeekBar.setProgress(0);
        }

        currentlyPlayingFile = null;
        currentlyPlayingButton = null;
        currentSeekBar = null;
        currentDurationView = null;

        isUserSeeking = false;
    }

    private void renameRecording(File file) {

        EditText input = new EditText(this);

        String oldName = file.getName();

        if (oldName.toLowerCase(Locale.ROOT).endsWith(".m4a")) {
            oldName = oldName.substring(0, oldName.length() - 4);
        }

        input.setText(oldName);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Rename Recording")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {

                    String newName = input.getText().toString().trim();

                    if (newName.isEmpty()
                            || newName.contains("/")
                            || newName.contains("\\")
                            || newName.equals(".")
                            || newName.equals("..")) {

                        Toast.makeText(
                                this,
                                "યોગ્ય નામ લખો.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    if (!newName.toLowerCase(Locale.ROOT).endsWith(".m4a")) {
                        newName += ".m4a";
                    }

                    File newFile = new File(file.getParentFile(), newName);

                    if (newFile.exists()) {

                        Toast.makeText(
                                this,
                                "આ નામની ફાઇલ પહેલેથી છે.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    if (file.equals(currentlyPlayingFile)) {
                        stopCurrentPlayback();
                    }

                    if (file.renameTo(newFile)) {

                        RecorderUtils.onRenamed(this, file.getName(), newFile.getName());

                        Toast.makeText(
                                this,
                                "Recording Rename થઈ.",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadRecordings();

                    } else {

                        Toast.makeText(
                                this,
                                "Rename કરવામાં નિષ્ફળતા.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    private void deleteRecording(File file) {

        new AlertDialog.Builder(this)
                .setTitle("Delete Recording?")
                .setMessage(
                        "શું તમે આ Recording કાયમ માટે Delete કરવા માંગો છો?\n\n"
                                + file.getName()
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    if (file.equals(currentlyPlayingFile)) {
                        stopCurrentPlayback();
                    }

                    if (file.delete()) {

                        RecorderUtils.onDeleted(this, file.getName());

                        Toast.makeText(
                                this,
                                "Recording Delete થઈ.",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadRecordings();

                    } else {

                        Toast.makeText(
                                this,
                                "Delete કરવામાં નિષ્ફળતા.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    private void shareRecording(File file) {

        if (!file.exists()) {

            Toast.makeText(
                    this,
                    "Recording File મળી નથી.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/mp4");

            android.net.Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(
                    Intent.createChooser(intent, "Share Recording")
            );

        } catch (IllegalArgumentException e) {

            Toast.makeText(
                    this,
                    "FileProvider configuration ચેક કરો.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String formatFileSize(long bytes) {

        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {
            return String.format(
                    Locale.getDefault(),
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                Locale.getDefault(),
                "%.1f MB",
                bytes / (1024.0 * 1024.0)
        );
    }

    private String formatTime(long milliseconds) {

        long totalSeconds = Math.max(0, milliseconds / 1000);

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
        );
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    @Override
    protected void onDestroy() {

        stopCurrentPlayback();

        super.onDestroy();
    }
}