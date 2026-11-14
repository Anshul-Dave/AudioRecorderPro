package com.example.audiorecorderpro;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Shared helpers used by every screen. No UI code here. */
final class RecorderUtils {

    static final String PREFS = "AudioRecorderPrefs";
    static final String KEY_FAVORITES = "favoriteRecordings";
    static final String KEY_NEXT_NAME = "nextRecordingName";
    static final long LOW_STORAGE_BYTES = 500L * 1024L * 1024L;

    private RecorderUtils() { }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- permissions ----------

    static boolean hasMic(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static String[] missingRuntimePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasMic(context) && !hasNotificationPermission(context)) {
                return new String[]{Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS};
            }
            if (!hasNotificationPermission(context)) {
                return new String[]{Manifest.permission.POST_NOTIFICATIONS};
            }
        }
        if (!hasMic(context)) {
            return new String[]{Manifest.permission.RECORD_AUDIO};
        }
        return new String[0];
    }

    // ---------- files ----------

    static File recordingsDir(Context context) {
        File music = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (music == null) {
            return null;
        }
        File dir = new File(music, "Recordings");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Newest first. Never returns null. */
    static File[] listRecordings(Context context) {
        File dir = recordingsDir(context);
        if (dir == null) {
            return new File[0];
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".m4a"));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    static String displayName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static long durationMs(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // nothing to do
            }
        }
    }

    // ---------- storage ----------

    static long availableBytes(Context context) {
        StatFs fs = statFs(context);
        return fs == null ? -1L : fs.getAvailableBytes();
    }

    static long totalBytes(Context context) {
        StatFs fs = statFs(context);
        return fs == null ? -1L : fs.getTotalBytes();
    }

    private static StatFs statFs(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) {
                dir = context.getFilesDir();
            }
            return new StatFs(dir.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    static long recordingsTotalSize(Context context) {
        long total = 0;
        for (File f : listRecordings(context)) {
            total += f.length();
        }
        return total;
    }

    // ---------- timer ----------

    /** Elapsed recording time, excluding paused time. Derived from persisted state. */
    static long elapsedMillis(SharedPreferences p) {
        long start = p.getLong("recordingStartTime", 0L);
        if (start <= 0) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - start - p.getLong("pausedDuration", 0L);
        if (p.getBoolean("isPaused", false)) {
            long pauseStart = p.getLong("pauseStartTime", 0L);
            if (pauseStart > 0) {
                elapsed -= (now - pauseStart);
            }
        }
        return Math.max(0L, elapsed);
    }

    // ---------- formatting ----------

    static String formatHms(long millis) {
        long s = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    static String formatClock(long millis) {
        long s = Math.max(0, millis / 1000);
        if (s >= 3600) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60);
    }

    static String formatDurationShort(long millis) {
        long s = Math.max(0, millis / 1000);
        if (s >= 3600) {
            return String.format(Locale.getDefault(), "%dh %02dm", s / 3600, (s % 3600) / 60);
        }
        return String.format(Locale.getDefault(), "%dm %02ds", s / 60, s % 60);
    }

    static String formatSize(long bytes) {
        if (bytes < 0) {
            return "--";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.getDefault(), "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", mb);
        }
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0);
    }

    static String formatDate(long timestamp) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);
        Calendar now = Calendar.getInstance();
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestamp));

        if (sameDay(then, now)) {
            return "Today, " + time;
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(then, now)) {
            return "Yesterday, " + time;
        }
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    static String formatFullDate(long timestamp) {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static String qualityLabel(String quality) {
        if ("LOW".equals(quality)) {
            return "Low";
        }
        if ("HIGH".equals(quality)) {
            return "High";
        }
        return "Medium";
    }

    static String autoStopLabel(int minutes) {
        if (minutes <= 0) {
            return "Manual stop";
        }
        if (minutes % 60 == 0) {
            int h = minutes / 60;
            return h + (h == 1 ? " hour" : " hours");
        }
        if (minutes > 60) {
            return (minutes / 60) + " h " + (minutes % 60) + " min";
        }
        return minutes + " minutes";
    }

    // ---------- favourites ----------

    static Set<String> favorites(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    static boolean isFavorite(Context context, File file) {
        return favorites(context).contains(file.getName());
    }

    static boolean toggleFavorite(Context context, File file) {
        Set<String> set = favorites(context);
        boolean nowFavorite;
        if (set.contains(file.getName())) {
            set.remove(file.getName());
            nowFavorite = false;
        } else {
            set.add(file.getName());
            nowFavorite = true;
        }
        prefs(context).edit().putStringSet(KEY_FAVORITES, set).apply();
        return nowFavorite;
    }

    static void renameFavorite(Context context, String oldName, String newName) {
        Set<String> set = favorites(context);
        if (set.remove(oldName)) {
            set.add(newName);
            prefs(context).edit().putStringSet(KEY_FAVORITES, set).apply();
        }
    }

    static void removeFavorite(Context context, String name) {
        Set<String> set = favorites(context);
        if (set.remove(name)) {
            prefs(context).edit().putStringSet(KEY_FAVORITES, set).apply();
        }
    }


    // ---------- per-recording metadata (quality) ----------

    /** Auto-stop limit of the recording in progress (fixed when the recording starts). */
    static int activeLimitMinutes(SharedPreferences p) {
        return p.getInt("activeLimitMinutes", p.getInt("autoStopMinutes", 0));
    }

    static void setQuality(Context context, File file, String quality) {
        prefs(context).edit().putString("meta.quality." + file.getName(), quality).apply();
    }

    static String getQuality(Context context, File file) {
        return prefs(context).getString("meta.quality." + file.getName(), null);
    }

    static void onRenamed(Context context, String oldName, String newName) {
        renameFavorite(context, oldName, newName);
        SharedPreferences p = prefs(context);
        String q = p.getString("meta.quality." + oldName, null);
        if (q != null) {
            p.edit().remove("meta.quality." + oldName).putString("meta.quality." + newName, q).apply();
        }
    }

    static void onDeleted(Context context, String name) {
        removeFavorite(context, name);
        prefs(context).edit().remove("meta.quality." + name).apply();
    }

    /** Renames a recording. Returns the new file, or null (message in outError[0]). */
    static File renameRecording(Context context, File file, String rawName, String[] outError) {
        String name = sanitizeName(rawName);
        if (name.isEmpty()) {
            outError[0] = "Please enter a valid name.";
            return null;
        }
        File target = new File(file.getParentFile(), name + ".m4a");
        if (target.equals(file)) {
            return file;
        }
        if (target.exists()) {
            outError[0] = "A recording with this name already exists.";
            return null;
        }
        String oldName = file.getName();
        if (file.renameTo(target)) {
            onRenamed(context, oldName, target.getName());
            return target;
        }
        outError[0] = "Could not rename the recording.";
        return null;
    }

    // ---------- file names ----------

    /** Removes characters that are illegal in file names. Returns "" when nothing usable remains. */
    static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.toLowerCase(Locale.ROOT).endsWith(".m4a")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
        }
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80).trim();
        }
        return cleaned;
    }

    static File uniqueFile(File dir, String baseName) {
        File candidate = new File(dir, baseName + ".m4a");
        int n = 2;
        while (candidate.exists()) {
            candidate = new File(dir, baseName + " (" + n + ").m4a");
            n++;
        }
        return candidate;
    }
}
