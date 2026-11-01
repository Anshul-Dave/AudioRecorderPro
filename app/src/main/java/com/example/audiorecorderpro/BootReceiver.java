package com.example.audiorecorderpro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms scheduled recordings after the phone restarts or the app is updated. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            ScheduleStore.sweepMissed(context);
            ScheduleStore.rescheduleAll(context);
        }
    }
}
