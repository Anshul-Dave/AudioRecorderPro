package com.example.audiorecorderpro;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

/** P18 - App Lock & Privacy. */
public class AppLockActivity extends AppCompatActivity {

    private LinearLayout content;
    private boolean blocked = false;

    interface PinCallback {
        void onPin(String pin);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppLock.guard(this)) {
            blocked = true;
            return;
        }
        content = UiUtil.setupSimpleScreen(this, "App Lock & Privacy",
                "Control access to your recordings", 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (blocked) {
            return;
        }
        render();
    }

    private void render() {
        content.removeAllViews();
        boolean on = AppLock.isEnabled(this);

        LinearLayout status = UiUtil.card(this);
        TextView badge = new TextView(this);
        UiUtil.styleBadge(badge, on ? UiUtil.STATUS_COMPLETED : UiUtil.STATUS_CANCELLED,
                on ? "On" : "Off");
        status.addView(badge);
        status.addView(UiUtil.text(this, on ? "App Lock is On" : "App Lock is Off", 20, true,
                R.color.app_text));
        status.addView(UiUtil.text(this, on
                        ? "A PIN is required each time the app starts."
                        : "Protect access to the app with a local PIN.", 14, false,
                R.color.app_text_secondary));
        if (on) {
            status.addView(button("Change PIN", false, v -> changePin()));
            status.addView(button("Turn Off App Lock", false, v -> turnOff()));
        } else {
            status.addView(button("Set Up App Lock", true, v -> setUp()));
        }
        content.addView(status);

        LinearLayout data = UiUtil.card(this);
        data.addView(UiUtil.text(this, "Your data stays on your device", 16, true, R.color.app_text));
        data.addView(UiUtil.text(this,
                "\u2022 Recordings are stored locally as M4A files.\n"
                        + "\u2022 No account, no cloud, no analytics.\n"
                        + "\u2022 The app has no internet permission.\n"
                        + "\u2022 Your PIN is never stored, only a salted hash.\n"
                        + "\u2022 Android shows its own microphone indicator while recording.",
                14, false, R.color.app_text_secondary));
        content.addView(data);

        LinearLayout perms = UiUtil.card(this);
        perms.addView(UiUtil.text(this, "Permissions", 16, true, R.color.app_text));
        perms.addView(permissionRow("Microphone", RecorderUtils.hasMic(this)));
        perms.addView(permissionRow("Notifications", RecorderUtils.hasNotificationPermission(this)));
        MaterialButton open = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        open.setText("Open App Settings");
        open.setAllCaps(false);
        open.setTextColor(ContextCompat.getColor(this, R.color.app_link));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, UiUtil.dp(this, 48));
        lp.setMargins(0, UiUtil.dp(this, 12), 0, 0);
        open.setLayoutParams(lp);
        open.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(i);
        });
        perms.addView(open);
        content.addView(perms);
    }

    private LinearLayout permissionRow(String label, boolean granted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiUtil.dp(this, 44));
        TextView name = UiUtil.text(this, label, 14, false, R.color.app_text);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);
        TextView badge = new TextView(this);
        UiUtil.styleBadge(badge, granted ? UiUtil.STATUS_COMPLETED : UiUtil.STATUS_FAILED,
                granted ? "Allowed" : "Not allowed");
        row.addView(badge);
        return row;
    }

    private MaterialButton button(String text, boolean primary, android.view.View.OnClickListener l) {
        MaterialButton b = primary ? new MaterialButton(this)
                : new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        if (!primary) {
            b.setTextColor(ContextCompat.getColor(this, R.color.app_link));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtil.dp(this, 52));
        lp.setMargins(0, UiUtil.dp(this, 12), 0, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    // ----------------------------------------------------------- PIN flows

    private void setUp() {
        askPin("Set Up Local PIN", "Enter a 4-digit PIN to secure access to your local recordings.",
                "Save & Enable", pin -> {
                    AppLock.setPin(this, pin);
                    Toast.makeText(this, "Action completed successfully", Toast.LENGTH_SHORT).show();
                    render();
                });
    }

    private void changePin() {
        askPin("Enter current PIN", "Confirm your current 4-digit PIN.", "Continue", current -> {
            if (!AppLock.verify(this, current)) {
                Toast.makeText(this, "Incorrect PIN.", Toast.LENGTH_LONG).show();
                return;
            }
            askPin("Set New PIN", "Enter a new 4-digit PIN.", "Save", pin -> {
                AppLock.setPin(this, pin);
                Toast.makeText(this, "Action completed successfully", Toast.LENGTH_SHORT).show();
                render();
            });
        });
    }

    private void turnOff() {
        askPin("Turn Off App Lock", "Enter your current PIN to turn off App Lock.", "Turn Off", pin -> {
            if (!AppLock.verify(this, pin)) {
                Toast.makeText(this, "Incorrect PIN.", Toast.LENGTH_LONG).show();
                return;
            }
            AppLock.disable(this);
            Toast.makeText(this, "App Lock turned off.", Toast.LENGTH_SHORT).show();
            render();
        });
    }

    private void askPin(String title, String message, String positive, PinCallback callback) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        input.setHint("\u2022\u2022\u2022\u2022");
        input.setGravity(android.view.Gravity.CENTER);
        int pad = UiUtil.dp(this, 24);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, UiUtil.dp(this, 8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(positive, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = input.getText().toString();
            if (pin.length() != 4) {
                Toast.makeText(this, "PIN must be exactly 4 digits.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            callback.onPin(pin);
        });
    }
}
