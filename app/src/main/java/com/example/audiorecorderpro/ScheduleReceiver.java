package com.example.audiorecorderpro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a scheduled recording is due. */
public class ScheduleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ScheduleStore.ACTION_FIRE.equals(intent.getAction())) {
            return;
        }
        long planId = intent.getLongExtra("planId", 0L);
        ScheduleStore.Plan plan = ScheduleStore.getPlan(context, planId);
        if (plan == null || !plan.active) {
            return;
        }

        ScheduleStore.Run run = ScheduleStore.onAlarm(context, plan);

        if (!RecorderUtils.hasMic(context)) {
            ScheduleStore.failRun(context, run.id, "Microphone permission is not granted.", null);
            return;
        }
        if (AudioRecordingService.running
                && RecorderUtils.prefs(context).getBoolean("isRecording", false)) {
            ScheduleStore.failRun(context, run.id,
                    "Another recording was already in progress at the scheduled time.", null);
            return;
        }

        String name = RecorderUtils.sanitizeName(plan.name);
        if (!name.isEmpty()) {
            RecorderUtils.prefs(context).edit().putString(RecorderUtils.KEY_NEXT_NAME, name).apply();
        }

        Intent start = new Intent(context, AudioRecordingService.class);
        start.setAction(AudioRecordingService.ACTION_START_RECORDING);
        start.putExtra("scheduleRunId", run.id);
        start.putExtra("scheduleMinutes", plan.durationMin);
        try {
            context.startForegroundService(start);
        } catch (RuntimeException e) {
            ScheduleStore.failRun(context, run.id,
                    "Android did not allow the microphone to start in the background.", null);
        }
    }
}
