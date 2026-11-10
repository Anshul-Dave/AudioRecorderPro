package com.example.audiorecorderpro;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** P18 - Help & FAQ. */
public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout content = UiUtil.setupSimpleScreen(this, "Help & Troubleshooting",
                "Find answers and solve common problems", 0);

        addFaq(content, "Where are my recordings saved?",
                "Recordings are saved as M4A files in this app's private storage on your phone. "
                        + "Nothing is uploaded anywhere. Uninstalling the app removes them, so share "
                        + "important recordings first.");
        addFaq(content, "How do I record quickly?",
                "Tap the large red Quick Record button on Home. It uses your saved quality and "
                        + "Auto-Save settings. Use Quick Setup to change them first.");
        addFaq(content, "What is Auto-Save?",
                "Auto-Save stops and saves a recording after the time you choose (30 minutes, "
                        + "1 hour, 2 hours or a custom time up to 12 hours). The timer runs inside the "
                        + "recording service, not on the screen.");
        addFaq(content, "Can I leave the app while recording?",
                "Yes. Recording continues in a foreground service and shows a notification with "
                        + "Pause and Stop & Save. Some phones stop background apps aggressively, so "
                        + "check battery settings if recordings end early.");
        addFaq(content, "Why does the app need permissions?",
                "Microphone: to record audio. Notifications (Android 13+): to show the recording "
                        + "notification and its controls. The app has no internet permission.");
        addFaq(content, "My scheduled recording did not start. Why?",
                "Open Scheduled > History and tap the item. Common reasons: microphone permission was "
                        + "removed, the phone was off, exact alarms are not allowed, or Android blocked "
                        + "starting the microphone in the background. Use the readiness check "
                        + "(Scheduled > Settings) and keep battery use unrestricted for this app.");
        addFaq(content, "What is a recording issue and can it be recovered?",
                "If a recording is interrupted, the app records an issue. A segment can be recovered "
                        + "only if the saved file is still playable. Unfinished files are often damaged "
                        + "and cannot be repaired.");
        addFaq(content, "How does App Lock work?",
                "Set a 4-digit PIN in Settings > App Lock & Privacy. The PIN is asked each time the "
                        + "app starts. If you forget it, clear the app's storage in Android settings, "
                        + "which also deletes app data.");
        addFaq(content, "How do I rename, share or delete a recording?",
                "Open the recording from Home or Recordings, then tap View Details. You can Rename, "
                        + "Share, Favourite or Delete it.");
        addAbout(content);
    }

    private void addAbout(LinearLayout parent) {
        String version = "1.0";
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (v != null) {
                version = v;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // keep default
        }
        LinearLayout card = UiUtil.card(this);
        card.addView(UiUtil.text(this, "About Audio Recorder Pro", 16, true, R.color.app_text));
        card.addView(UiUtil.text(this, "Version " + version
                + "\nWorks completely offline. No account, no cloud, no internet permission.",
                14, false, R.color.app_text_secondary));
        parent.addView(card);
    }

    private void addFaq(LinearLayout parent, String question, String answer) {
        LinearLayout card = UiUtil.card(this);
        TextView q = UiUtil.text(this, question, 16, true, R.color.app_text);
        TextView a = UiUtil.text(this, answer, 14, false, R.color.app_text_secondary);
        a.setVisibility(View.GONE);
        a.setPadding(0, UiUtil.dp(this, 6), 0, 0);
        card.addView(q);
        card.addView(a);
        card.setOnClickListener(v ->
                a.setVisibility(a.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        parent.addView(card);
    }
}
