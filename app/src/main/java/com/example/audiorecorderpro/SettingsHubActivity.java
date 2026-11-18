package com.example.audiorecorderpro;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** P16 - Settings hub (tab). */
public class SettingsHubActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            return;
        }
        LinearLayout content = UiUtil.setupSimpleScreen(this, "Settings", null, R.id.nav_settings);

        section(content, "Recording");
        LinearLayout rec = UiUtil.card(this);
        rec.setPadding(0, 0, 0, 0);
        UiUtil.addRow(this, rec, R.drawable.ic_mic, "Recording Settings",
                "Format, quality & auto-stop", SettingsActivity.class);
        content.addView(rec);

        section(content, "Scheduled Recording");
        LinearLayout sched = UiUtil.card(this);
        sched.setPadding(0, 0, 0, 0);
        UiUtil.addRow(this, sched, R.drawable.ic_schedule, "Scheduled Recordings",
                "Manage automated recording plans", ScheduledActivity.class);
        content.addView(sched);

        section(content, "Privacy & Security");
        LinearLayout priv = UiUtil.card(this);
        priv.setPadding(0, 0, 0, 0);
        UiUtil.addRow(this, priv, R.drawable.ic_lock, "App Lock & Privacy",
                "Secure with a local PIN", AppLockActivity.class);
        content.addView(priv);

        section(content, "Storage");
        LinearLayout storage = UiUtil.card(this);
        storage.setPadding(0, 0, 0, 0);
        UiUtil.addRow(this, storage, R.drawable.ic_storage, "Storage Manager",
                "Manage saved recordings & space", StorageActivity.class);
        content.addView(storage);

        section(content, "Support");
        LinearLayout support = UiUtil.card(this);
        support.setPadding(0, 0, 0, 0);
        UiUtil.addRow(this, support, R.drawable.ic_help, "Help & Troubleshooting",
                "FAQs and offline support guides", HelpActivity.class);
        content.addView(support);
    }

    private void section(LinearLayout parent, String title) {
        TextView t = UiUtil.text(this, title, 13, true, R.color.app_text_secondary);
        t.setPadding(UiUtil.dp(this, 4), UiUtil.dp(this, 8), 0, UiUtil.dp(this, 4));
        parent.addView(t);
    }
}
