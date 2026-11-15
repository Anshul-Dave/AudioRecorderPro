package com.example.audiorecorderpro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** Local PIN lock. The PIN is never stored; only a salted SHA-256 hash is kept on the device. */
final class AppLock {

    /** True after a correct PIN in this app process. */
    static volatile boolean unlocked = false;

    private static final String KEY_ENABLED = "lockEnabled";
    private static final String KEY_SALT = "lockSalt";
    private static final String KEY_HASH = "lockHash";
    static final String KEY_FAILS = "lockFails";
    static final String KEY_UNTIL = "lockUntil";

    private AppLock() { }

    static boolean isEnabled(Context c) {
        SharedPreferences p = RecorderUtils.prefs(c);
        return p.getBoolean(KEY_ENABLED, false) && p.getString(KEY_HASH, null) != null;
    }

    static void setPin(Context c, String pin) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
        RecorderUtils.prefs(c).edit()
                .putString(KEY_SALT, salt)
                .putString(KEY_HASH, hash(salt, pin))
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_FAILS, 0)
                .remove(KEY_UNTIL)
                .apply();
        unlocked = true;
    }

    static boolean verify(Context c, String pin) {
        SharedPreferences p = RecorderUtils.prefs(c);
        String salt = p.getString(KEY_SALT, null);
        String stored = p.getString(KEY_HASH, null);
        if (salt == null || stored == null) {
            return false;
        }
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                hash(salt, pin).getBytes(StandardCharsets.UTF_8));
    }

    static void disable(Context c) {
        RecorderUtils.prefs(c).edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_SALT)
                .remove(KEY_HASH)
                .putInt(KEY_FAILS, 0)
                .remove(KEY_UNTIL)
                .apply();
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Call at the start of onCreate of every screen that shows recordings.
     * Returns false (and redirects to the PIN screen) when the app is locked.
     */
    static boolean guard(Activity activity) {
        if (!isEnabled(activity) || unlocked) {
            return true;
        }
        Intent next = new Intent(activity.getIntent());
        Intent lock = new Intent(activity, LockActivity.class);
        lock.putExtra(LockActivity.EXTRA_NEXT, next);
        activity.startActivity(lock);
        activity.finish();
        return false;
    }
}
