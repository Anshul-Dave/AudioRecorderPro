package com.example.audiorecorderpro;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Local storage and alarm handling for scheduled recordings.
 * Plans = what the user scheduled. Runs = what happened (history).
 * Data lives in SharedPreferences as JSON; everything stays on the device.
 */
final class ScheduleStore {

    static final int REPEAT_NONE = 0;
    static final int REPEAT_DAILY = 1;
    static final int REPEAT_WEEKLY = 2;

    static final String ST_RECORDING = "recording";
    static final String ST_COMPLETED = "completed";
    static final String ST_FAILED = "failed";
    static final String ST_MISSED = "missed";
    static final String ST_CANCELLED = "cancelled";

    static final String ACTION_FIRE = "com.example.audiorecorderpro.SCHEDULE_FIRE";
    private static final String KEY_PLANS = "schedulePlans";
    private static final String KEY_RUNS = "scheduleRuns";
    private static final String KEY_COUNTER = "scheduleIdCounter";
    private static final String ALERT_CHANNEL = "schedule_alerts";
    private static final long MISSED_GRACE_MS = 2 * 60 * 1000L;

    private ScheduleStore() { }

    static final class Plan {
        long id;
        String name;
        long startMillis;
        int durationMin;
        int repeat;
        boolean active;
        long createdAt;
    }

    static final class Run {
        long id;
        long planId;
        String name;
        long scheduledAt;
        String status;
        String reason;
        String path;
        boolean resolved;
        long createdAt;
    }

    // ------------------------------------------------------------ json io

    private static SharedPreferences prefs(Context c) {
        return RecorderUtils.prefs(c);
    }

    private static synchronized long nextId(Context c) {
        SharedPreferences p = prefs(c);
        long id = p.getLong(KEY_COUNTER, 0L) + 1L;
        p.edit().putLong(KEY_COUNTER, id).apply();
        return id;
    }

    static synchronized List<Plan> plans(Context c) {
        List<Plan> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_PLANS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Plan p = new Plan();
                p.id = o.getLong("id");
                p.name = o.optString("name", "Scheduled recording");
                p.startMillis = o.getLong("start");
                p.durationMin = o.optInt("duration", 30);
                p.repeat = o.optInt("repeat", REPEAT_NONE);
                p.active = o.optBoolean("active", true);
                p.createdAt = o.optLong("created", 0L);
                list.add(p);
            }
        } catch (JSONException ignored) {
            // corrupted data: treat as empty
        }
        return list;
    }

    private static void writePlans(Context c, List<Plan> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Plan p : list) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("start", p.startMillis);
                o.put("duration", p.durationMin);
                o.put("repeat", p.repeat);
                o.put("active", p.active);
                o.put("created", p.createdAt);
                arr.put(o);
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs(c).edit().putString(KEY_PLANS, arr.toString()).apply();
    }

    static synchronized List<Run> runs(Context c) {
        List<Run> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_RUNS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Run r = new Run();
                r.id = o.getLong("id");
                r.planId = o.optLong("planId", 0L);
                r.name = o.optString("name", "Recording");
                r.scheduledAt = o.optLong("scheduledAt", 0L);
                r.status = o.optString("status", ST_FAILED);
                r.reason = o.optString("reason", "");
                r.path = o.isNull("path") ? null : o.optString("path", null);
                r.resolved = o.optBoolean("resolved", false);
                r.createdAt = o.optLong("created", 0L);
                list.add(r);
            }
        } catch (JSONException ignored) {
            // corrupted data: treat as empty
        }
        Collections.sort(list, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        return list;
    }

    private static void writeRuns(Context c, List<Run> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Run r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("planId", r.planId);
                o.put("name", r.name);
                o.put("scheduledAt", r.scheduledAt);
                o.put("status", r.status);
                o.put("reason", r.reason == null ? "" : r.reason);
                o.put("path", r.path == null ? JSONObject.NULL : r.path);
                o.put("resolved", r.resolved);
                o.put("created", r.createdAt);
                arr.put(o);
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs(c).edit().putString(KEY_RUNS, arr.toString()).apply();
    }

    // -------------------------------------------------------------- plans

    static Plan getPlan(Context c, long id) {
        for (Plan p : plans(c)) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    /** Upcoming plans, soonest first. */
    static List<Plan> upcoming(Context c) {
        List<Plan> out = new ArrayList<>();
        for (Plan p : plans(c)) {
            if (p.active) {
                out.add(p);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.startMillis, b.startMillis));
        return out;
    }

    static Plan nextPlan(Context c) {
        List<Plan> list = upcoming(c);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Creates (id == 0) or updates a plan and (re)arms its alarm. */
    static synchronized Plan savePlan(Context c, Plan plan) {
        List<Plan> list = plans(c);
        if (plan.id == 0L) {
            plan.id = nextId(c);
            plan.createdAt = System.currentTimeMillis();
            plan.active = true;
            list.add(plan);
        } else {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == plan.id) {
                    plan.createdAt = list.get(i).createdAt;
                    plan.active = true;
                    list.set(i, plan);
                }
            }
        }
        writePlans(c, list);
        arm(c, plan);
        return plan;
    }

    static synchronized void cancelPlan(Context c, long planId) {
        List<Plan> list = plans(c);
        for (Plan p : list) {
            if (p.id == planId && p.active) {
                p.active = false;
                disarm(c, p.id);
                addRun(c, p, ST_CANCELLED, "Cancelled by you.", null, true);
            }
        }
        writePlans(c, list);
    }

    static long advance(Plan p, long after) {
        if (p.repeat == REPEAT_NONE) {
            return p.startMillis;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(p.startMillis);
        int guard = 0;
        while (cal.getTimeInMillis() <= after && guard++ < 4000) {
            cal.add(Calendar.DAY_OF_YEAR, p.repeat == REPEAT_DAILY ? 1 : 7);
        }
        return cal.getTimeInMillis();
    }

    // --------------------------------------------------------------- runs

    private static Run addRun(Context c, Plan plan, String status, String reason, String path,
                              boolean resolved) {
        List<Run> list = runs(c);
        Run r = new Run();
        r.id = nextId(c);
        r.planId = plan == null ? 0L : plan.id;
        r.name = plan == null ? "Recording" : plan.name;
        r.scheduledAt = plan == null ? System.currentTimeMillis() : plan.startMillis;
        r.status = status;
        r.reason = reason;
        r.path = path;
        r.resolved = resolved;
        r.createdAt = System.currentTimeMillis();
        list.add(r);
        writeRuns(c, list);
        return r;
    }

    static Run getRun(Context c, long id) {
        for (Run r : runs(c)) {
            if (r.id == id) {
                return r;
            }
        }
        return null;
    }

    private static synchronized void updateRun(Context c, long runId, String status, String reason,
                                               String path) {
        List<Run> list = runs(c);
        for (Run r : list) {
            if (r.id == runId) {
                r.status = status;
                r.reason = reason;
                if (path != null) {
                    r.path = path;
                }
            }
        }
        writeRuns(c, list);
    }

    static synchronized void completeRun(Context c, long runId, String path) {
        updateRun(c, runId, ST_COMPLETED, "", path);
    }

    static synchronized void failRun(Context c, long runId, String reason, String partialPath) {
        updateRun(c, runId, ST_FAILED, reason, partialPath);
        Run r = getRun(c, runId);
        notifyIssue(c, r);
    }

    static synchronized void markResolved(Context c, long runId) {
        List<Run> list = runs(c);
        for (Run r : list) {
            if (r.id == runId) {
                r.resolved = true;
            }
        }
        writeRuns(c, list);
    }

    static List<Run> unresolvedIssues(Context c) {
        List<Run> out = new ArrayList<>();
        for (Run r : runs(c)) {
            if ((ST_FAILED.equals(r.status) || ST_MISSED.equals(r.status)) && !r.resolved) {
                out.add(r);
            }
        }
        return out;
    }

    static int countStatus(Context c, String... statuses) {
        int n = 0;
        for (Run r : runs(c)) {
            for (String s : statuses) {
                if (s.equals(r.status)) {
                    n++;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------- alarm entry points

    /** Called by the alarm receiver. Creates a run, advances the plan, returns the run. */
    static synchronized Run onAlarm(Context c, Plan plan) {
        Run run = addRun(c, plan, ST_RECORDING, "", null, false);
        advanceOrDeactivate(c, plan.id);
        return run;
    }

    private static void advanceOrDeactivate(Context c, long planId) {
        List<Plan> list = plans(c);
        for (Plan p : list) {
            if (p.id == planId) {
                if (p.repeat == REPEAT_NONE) {
                    p.active = false;
                    disarm(c, p.id);
                } else {
                    p.startMillis = advance(p, System.currentTimeMillis() + 1000L);
                    arm(c, p);
                }
            }
        }
        writePlans(c, list);
    }

    /** Marks plans whose time has passed without a start as missed. */
    static synchronized void sweepMissed(Context c) {
        long now = System.currentTimeMillis();
        // Inexact alarms can be delayed by Doze, so give them much longer before calling it missed.
        long grace = canScheduleExact(c) ? MISSED_GRACE_MS : 15 * 60 * 1000L;
        for (Plan p : plans(c)) {
            if (p.active && p.startMillis < now - grace) {
                Run r = addRun(c, p, ST_MISSED,
                        "The recording did not start at the scheduled time. The phone may have been "
                                + "off or Android may have delayed the alarm.", null, false);
                advanceOrDeactivate(c, p.id);
                notifyIssue(c, r);
            }
        }
    }

    static void rescheduleAll(Context c) {
        for (Plan p : plans(c)) {
            if (p.active) {
                arm(c, p);
            }
        }
    }

    /**
     * If the app thinks a recording is running but the service is gone, the recording was
     * interrupted. Clears the stale state and records an issue.
     */
    static synchronized void reconcileInterrupted(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("isRecording", false) || AudioRecordingService.running) {
            return;
        }
        String path = p.getString("currentRecordingPath", null);
        long runId = p.getLong("activeRunId", 0L);
        p.edit()
                .putBoolean("isRecording", false)
                .putBoolean("isPaused", false)
                .remove("recordingStartTime")
                .remove("pauseStartTime")
                .remove("currentRecordingPath")
                .remove("activeLimitMinutes")
                .remove("activeRunId")
                .putLong("pausedDuration", 0)
                .apply();
        String reason = "The recording was interrupted before it could be saved "
                + "(the app or the phone stopped it).";
        if (runId > 0 && getRun(c, runId) != null) {
            updateRun(c, runId, ST_FAILED, reason, path);
            notifyIssue(c, getRun(c, runId));
        } else {
            String name = path == null ? "Interrupted recording"
                    : RecorderUtils.displayName(new File(path));
            Plan fake = new Plan();
            fake.name = name;
            fake.startMillis = System.currentTimeMillis();
            Run r = addRun(c, fake, ST_FAILED, reason, path, false);
            notifyIssue(c, r);
        }
    }

    // -------------------------------------------------------------- alarm

    private static PendingIntent alarmIntent(Context c, long planId) {
        Intent i = new Intent(c, ScheduleReceiver.class);
        i.setAction(ACTION_FIRE);
        i.putExtra("planId", planId);
        return PendingIntent.getBroadcast(c, (int) planId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canScheduleExact(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
    }

    private static void arm(Context c, Plan plan) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = alarmIntent(c, plan.id);
        try {
            if (canScheduleExact(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.startMillis, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.startMillis, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.startMillis, pi);
        }
    }

    private static void disarm(Context c, long planId) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(alarmIntent(c, planId));
        }
    }

    // ------------------------------------------------------ notifications

    private static void notifyIssue(Context c, Run run) {
        if (run == null || !RecorderUtils.hasNotificationPermission(c)) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(ALERT_CHANNEL,
                    "Scheduled recording alerts", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(c, RecordingIssueActivity.class);
        open.putExtra(RecordingIssueActivity.EXTRA_RUN_ID, run.id);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, (int) run.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("Recording issue: " + run.name)
                .setContentText("Tap to review what happened and your options.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (10000 + run.id), n);
    }

    // ----------------------------------------------------------- display

    static String formatPlanDate(long millis) {
        return new SimpleDateFormat("EEE, dd MMM yyyy \u2022 hh:mm a", Locale.getDefault())
                .format(new Date(millis));
    }

    static String formatLongDate(long millis) {
        return new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(new Date(millis));
    }

    static String formatTime(long millis) {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    static String repeatLabel(int repeat) {
        if (repeat == REPEAT_DAILY) {
            return "Repeats Daily";
        }
        if (repeat == REPEAT_WEEKLY) {
            return "Repeats Weekly";
        }
        return "Does not repeat";
    }

    static String durationLabel(int minutes) {
        return RecorderUtils.autoStopLabel(minutes);
    }
}
