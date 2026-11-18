package com.example.audiorecorderpro;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Small UI helpers shared by all screens: top bar, bottom navigation, status badges. */
final class UiUtil {

    static final String STATUS_SCHEDULED = "scheduled";
    static final String STATUS_RECORDING = "recording";
    static final String STATUS_COMPLETED = "completed";
    static final String STATUS_FAILED = "failed";
    static final String STATUS_CANCELLED = "cancelled";

    private UiUtil() { }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Fills the included view_top_bar layout and wires the back button. */
    static void setupTopBar(AppCompatActivity activity, String title, String subtitle) {
        TextView t = activity.findViewById(R.id.tvScreenTitle);
        TextView s = activity.findViewById(R.id.tvScreenSubtitle);
        if (t != null) {
            t.setText(title);
        }
        if (s != null) {
            if (subtitle == null || subtitle.isEmpty()) {
                s.setVisibility(View.GONE);
            } else {
                s.setText(subtitle);
            }
        }
        View back = activity.findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(v -> activity.finish());
        }
    }

    /** Bottom navigation: Home, Recordings, Scheduled, Settings. */
    static void setupBottomNav(AppCompatActivity activity, int selectedId) {
        BottomNavigationView nav = activity.findViewById(R.id.bottomNav);
        if (nav == null) {
            return;
        }
        nav.setSelectedItemId(selectedId);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedId) {
                return true;
            }
            Intent intent;
            if (id == R.id.nav_home) {
                intent = new Intent(activity, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
            } else {
                if (id == R.id.nav_recordings) {
                    intent = new Intent(activity, RecordingLibraryActivity.class);
                } else if (id == R.id.nav_scheduled) {
                    intent = new Intent(activity, ScheduledActivity.class);
                } else {
                    intent = new Intent(activity, SettingsHubActivity.class);
                }
                activity.startActivity(intent);
                if (selectedId != R.id.nav_home) {
                    activity.finish();
                }
            }
            activity.overridePendingTransition(0, 0);
            return true;
        });
    }

    /** Styles a TextView as a status badge: icon + text + colours (never colour alone). */
    static void styleBadge(TextView view, String status, String label) {
        Context c = view.getContext();
        int bg;
        int textColor;
        int icon;
        switch (status) {
            case STATUS_RECORDING:
                bg = R.drawable.bg_badge_recording;
                textColor = R.color.badge_recording_text;
                icon = R.drawable.ic_dot;
                break;
            case STATUS_COMPLETED:
                bg = R.drawable.bg_badge_completed;
                textColor = R.color.badge_completed_text;
                icon = R.drawable.ic_check;
                break;
            case STATUS_FAILED:
                bg = R.drawable.bg_badge_failed;
                textColor = R.color.badge_failed_text;
                icon = R.drawable.ic_warning;
                break;
            case STATUS_CANCELLED:
                bg = R.drawable.bg_badge_cancelled;
                textColor = R.color.badge_cancelled_text;
                icon = R.drawable.ic_close;
                break;
            default:
                bg = R.drawable.bg_badge_scheduled;
                textColor = R.color.badge_scheduled_text;
                icon = R.drawable.ic_schedule;
                break;
        }
        int color = ContextCompat.getColor(c, textColor);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(12);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setBackgroundResource(bg);
        view.setPadding(dp(c, 10), dp(c, 4), dp(c, 12), dp(c, 4));
        view.setCompoundDrawablePadding(dp(c, 4));
        view.setContentDescription(label);
        Drawable d = ContextCompat.getDrawable(c, icon);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, color);
            int size = dp(c, 14);
            d.setBounds(0, 0, size, size);
            view.setCompoundDrawablesRelative(d, null, null, null);
        }
    }


    /** Standard card container used by programmatically built screens. */
    static android.widget.LinearLayout card(Context c) {
        android.widget.LinearLayout card = new android.widget.LinearLayout(c);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 12));
        card.setLayoutParams(lp);
        return card;
    }

    static TextView text(Context c, String value, int sp, boolean bold, int colorRes) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(ContextCompat.getColor(c, colorRes));
        if (bold) {
            t.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 2), 0, dp(c, 2));
        t.setLayoutParams(lp);
        return t;
    }

    /** Uses activity_simple_list as the screen shell. Returns the content container. */
    static android.widget.LinearLayout setupSimpleScreen(AppCompatActivity a, String title,
                                                          String subtitle, int navId) {
        return setupSimpleScreen(a, title, subtitle, navId, false);
    }

    static android.widget.LinearLayout setupSimpleScreen(AppCompatActivity a, String title,
                                                          String subtitle, int navId,
                                                          boolean showBack) {
        a.setContentView(R.layout.activity_simple_list);
        setupTopBar(a, title, subtitle);
        View nav = a.findViewById(R.id.bottomNav);
        if (navId == 0) {
            nav.setVisibility(View.GONE);
        } else {
            if (!showBack) {
                a.findViewById(R.id.btnBack).setVisibility(View.GONE);
            }
            setupBottomNav(a, navId);
        }
        return a.findViewById(R.id.contentContainer);
    }

    /** Adds a tappable row (icon, title, subtitle, chevron) to a container. */
    static void addRow(AppCompatActivity a, android.view.ViewGroup parent, int iconRes,
                       String title, String subtitle, Class<?> target) {
        View row = android.view.LayoutInflater.from(a)
                .inflate(R.layout.item_settings_row, parent, false);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(subtitle);
        row.setOnClickListener(v -> a.startActivity(new Intent(a, target)));
        parent.addView(row);
    }

    static void tint(ImageView view, int colorRes) {
        view.setColorFilter(ContextCompat.getColor(view.getContext(), colorRes));
    }
}
