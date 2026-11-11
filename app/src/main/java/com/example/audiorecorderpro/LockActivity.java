package com.example.audiorecorderpro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** PIN entry screen shown when App Lock is on. */
public class LockActivity extends AppCompatActivity {

    static final String EXTRA_NEXT = "extra_next";
    private static final int MAX_FAILS = 5;
    private static final long LOCKOUT_MS = 30_000L;

    private EditText etPin;
    private TextView tvError;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsActivity.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        if (!AppLock.isEnabled(this) || AppLock.unlocked) {
            proceed();
            return;
        }
        setContentView(R.layout.activity_lock);
        etPin = findViewById(R.id.etPin);
        tvError = findViewById(R.id.tvPinError);
        findViewById(R.id.btnUnlock).setOnClickListener(v -> tryUnlock());
        etPin.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryUnlock();
                return true;
            }
            return false;
        });
    }

    private void tryUnlock() {
        SharedPreferences p = RecorderUtils.prefs(this);
        long until = p.getLong(AppLock.KEY_UNTIL, 0L);
        long now = System.currentTimeMillis();
        if (until > now) {
            showError("Too many attempts. Try again in " + ((until - now) / 1000 + 1) + " seconds.");
            return;
        }
        String pin = etPin.getText().toString();
        if (pin.length() != 4) {
            showError("Enter your 4-digit PIN.");
            return;
        }
        if (AppLock.verify(this, pin)) {
            p.edit().putInt(AppLock.KEY_FAILS, 0).remove(AppLock.KEY_UNTIL).apply();
            AppLock.unlocked = true;
            proceed();
        } else {
            int fails = p.getInt(AppLock.KEY_FAILS, 0) + 1;
            SharedPreferences.Editor e = p.edit().putInt(AppLock.KEY_FAILS, fails);
            if (fails >= MAX_FAILS) {
                e.putInt(AppLock.KEY_FAILS, 0).putLong(AppLock.KEY_UNTIL, now + LOCKOUT_MS);
                showError("Too many attempts. Try again in 30 seconds.");
            } else {
                showError("Incorrect PIN. " + (MAX_FAILS - fails) + " attempts left.");
            }
            e.apply();
            etPin.setText("");
        }
    }

    private void showError(String message) {
        UiUtil.styleBadge(tvError, UiUtil.STATUS_FAILED, message);
        tvError.setVisibility(android.view.View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void proceed() {
        Intent next = getIntent().getParcelableExtra(EXTRA_NEXT);
        if (next == null) {
            next = new Intent(this, MainActivity.class);
        }
        startActivity(next);
        finish();
    }
}
