/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 */
package com.kyomu.tools.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kyomu.tools.core.HookManager;


public class UIHelper {

    private UIHelper() {}

    public interface OnToggle {
        void onChanged(boolean value);
    }

    public static int dp(int dp, Activity activity) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static TextView makeTabBtn(Activity activity, String text, boolean active) {
        TextView tab = new TextView(activity);
        tab.setText(text);
        tab.setTextSize(11);
        tab.setGravity(android.view.Gravity.CENTER);
        tab.setPadding(0, 10, 0, 10);
        styleTabBtn(tab, active, activity);
        return tab;
    }

    public static void styleTabBtn(TextView tab, boolean active, Activity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8, activity));
        if (active) {
            bg.setColor(Color.argb(255, 46, 204, 113));
            tab.setTextColor(Color.argb(255, 10, 10, 25));
            tab.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(Color.argb(255, 40, 40, 55));
            tab.setTextColor(Color.argb(200, 150, 150, 150));
            tab.setTypeface(null, Typeface.NORMAL);
        }
        tab.setBackground(bg);
    }

    public static void styleIntervalBtn(TextView btn, boolean selected, Activity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6, activity));
        if (selected) {
            bg.setColor(Color.argb(255, 80, 200, 255));
            btn.setTextColor(Color.argb(255, 10, 10, 25));
            btn.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(Color.argb(255, 40, 40, 55));
            btn.setTextColor(Color.argb(180, 150, 150, 150));
            btn.setTypeface(null, Typeface.NORMAL);
        }
        btn.setBackground(bg);
    }

    public static TextView makeMuteActionBtn(Activity activity, String text, int bgColor) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setPadding(dp(10, activity), dp(6, activity), dp(10, activity), dp(6, activity));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(8, activity));
        btn.setBackground(bg);
        return btn;
    }

    public static TextView makeSmallBtn(Activity activity, String text, int bgColor) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(9);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setPadding(dp(8, activity), dp(4, activity), dp(8, activity), dp(4, activity));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(5, activity));
        btn.setBackground(bg);
        return btn;
    }

    public static TextView makePmBtn(Activity activity, String text) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setPadding(dp(12, activity), dp(6, activity), dp(12, activity), dp(6, activity));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(255, 55, 55, 70));
        bg.setCornerRadius(dp(8, activity));
        btn.setBackground(bg);
        return btn;
    }

    public static TextView makeActionBtn(Activity activity, String text, int bgColor) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setPadding(dp(16, activity), dp(10, activity), dp(16, activity), dp(10, activity));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(8, activity));
        btn.setBackground(bg);
        return btn;
    }

    public static CheckBox addToggle(LinearLayout parent, Activity activity,
                                     String label, boolean defaultVal, OnToggle callback) {
        CheckBox cb = new CheckBox(activity);
        cb.setText(label);
        cb.setTextColor(Color.WHITE);
        cb.setChecked(defaultVal);
        cb.setPadding(0, 8, 0, 8);
        cb.setOnCheckedChangeListener((btn, isChecked) -> {
            callback.onChanged(isChecked);
            HookManager.log("[kyomu] " + label + " → " + (isChecked ? "ON" : "OFF"));
        });
        parent.addView(cb);
        return cb;
    }

    public static View makeDivider(Activity activity) {
        View div = new View(activity);
        div.setBackgroundColor(Color.argb(100, 100, 100, 100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        lp.setMargins(0, 8, 0, 8);
        div.setLayoutParams(lp);
        return div;
    }
}
