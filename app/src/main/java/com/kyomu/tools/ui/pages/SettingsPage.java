/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.ui.pages;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.kyomu.tools.MappingManager;
import com.kyomu.tools.core.StateHolder;
import com.kyomu.tools.ui.UIHelper;

import java.lang.ref.WeakReference;

public class SettingsPage {

    private static WeakReference<View>    menuOuterRef  = new WeakReference<>(null);
    private static WeakReference<View>    openBtnRef    = new WeakReference<>(null);
    private static WeakReference<android.widget.ScrollView> menuScrollRef
            = new WeakReference<>(null);

    public static void setRefs(View menuOuter,
                               android.widget.ScrollView menuScroll,
                               View openBtn) {
        menuOuterRef  = new WeakReference<>(menuOuter);
        menuScrollRef = new WeakReference<>(menuScroll);
        openBtnRef    = new WeakReference<>(openBtn);
    }

    public static LinearLayout build(Activity activity) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 8, 0, 0);

        // メニュー透明度
        final TextView menuAlphaLabel = new TextView(activity);
        menuAlphaLabel.setText("メニュー透明度: " + (StateHolder.menuAlpha * 100 / 255) + "%");
        menuAlphaLabel.setTextColor(Color.WHITE);
        menuAlphaLabel.setTextSize(12);
        page.addView(menuAlphaLabel);

        SeekBar menuAlphaBar = new SeekBar(activity);
        menuAlphaBar.setMax(255);
        menuAlphaBar.setProgress(StateHolder.menuAlpha);
        menuAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                Activity act = StateHolder.getLastActivity();
                if (act == null) return;
                StateHolder.menuAlpha = Math.max(progress, 30);
                menuAlphaLabel.setText("メニュー透明度: " + (StateHolder.menuAlpha * 100 / 255) + "%");
                View mo = menuOuterRef.get();
                if (mo != null) {
                    mo.setAlpha(StateHolder.menuAlpha / 255f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        page.addView(menuAlphaBar);

        // ボタン透明度
        TextView btnAlphaLabel = new TextView(activity);
        btnAlphaLabel.setText("ボタン透明度");
        btnAlphaLabel.setTextColor(Color.WHITE);
        btnAlphaLabel.setTextSize(12);
        btnAlphaLabel.setPadding(0, 16, 0, 4);
        page.addView(btnAlphaLabel);

        LinearLayout btnAlphaRow = new LinearLayout(activity);
        btnAlphaRow.setOrientation(LinearLayout.HORIZONTAL);
        btnAlphaRow.setGravity(Gravity.CENTER);
        final String[] alphaLabels = {"100%", "20%", "10%"};
        final float[]  alphaValues = {1.0f, 0.2f, 0.1f};
        final TextView[] alphaBtns = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView ab = new TextView(activity);
            ab.setText(alphaLabels[i]);
            ab.setTextSize(12);
            ab.setGravity(Gravity.CENTER);
            ab.setPadding(0, 8, 0, 8);
            alphaBtns[i] = ab;
            UIHelper.styleIntervalBtn(ab, i == StateHolder.btnAlphaMode, activity);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            alp.setMargins(4, 0, 4, 0);
            btnAlphaRow.addView(ab, alp);
            ab.setOnClickListener(v -> {
                Activity act = StateHolder.getLastActivity();
                if (act == null) return;
                StateHolder.btnAlphaMode = idx;
                for (int j = 0; j < 3; j++)
                    UIHelper.styleIntervalBtn(alphaBtns[j], j == idx, act);
                View ob = openBtnRef.get();
                if (ob != null) ob.setAlpha(alphaValues[idx]);
            });
        }
        page.addView(btnAlphaRow);

        // メニュー幅
        final TextView widthLabel = new TextView(activity);
        widthLabel.setText("メニュー幅: " + StateHolder.menuWidth + "px");
        widthLabel.setTextColor(Color.WHITE);
        widthLabel.setTextSize(12);
        widthLabel.setPadding(0, 16, 0, 4);
        page.addView(widthLabel);

        LinearLayout widthRow = new LinearLayout(activity);
        widthRow.setOrientation(LinearLayout.HORIZONTAL);
        widthRow.setGravity(Gravity.CENTER);
        TextView wMinus = UIHelper.makePmBtn(activity, "  −  ");
        TextView wPlus  = UIHelper.makePmBtn(activity, "  ＋  ");
        TextView wSp = new TextView(activity); wSp.setText("    ");
        widthRow.addView(wMinus); widthRow.addView(wSp); widthRow.addView(wPlus);
        page.addView(widthRow);

        wMinus.setOnClickListener(v -> {
            Activity act = StateHolder.getLastActivity();
            if (act == null) return;
            StateHolder.menuWidth = Math.max(
                    StateHolder.menuWidth - UIHelper.dp(20, act), UIHelper.dp(200, act));
            widthLabel.setText("メニュー幅: " + StateHolder.menuWidth + "px");
            View mo = menuOuterRef.get();
            if (mo != null) {
                android.view.ViewGroup.LayoutParams lp = mo.getLayoutParams();
                lp.width = StateHolder.menuWidth;
                mo.setLayoutParams(lp);
            }
        });
        wPlus.setOnClickListener(v -> {
            Activity act = StateHolder.getLastActivity();
            if (act == null) return;
            StateHolder.menuWidth = Math.min(
                    StateHolder.menuWidth + UIHelper.dp(20, act), UIHelper.dp(450, act));
            widthLabel.setText("メニュー幅: " + StateHolder.menuWidth + "px");
            View mo = menuOuterRef.get();
            if (mo != null) {
                android.view.ViewGroup.LayoutParams lp = mo.getLayoutParams();
                lp.width = StateHolder.menuWidth;
                mo.setLayoutParams(lp);
            }
        });

        // メニュー高さ
        final TextView heightLabel = new TextView(activity);
        heightLabel.setText("メニュー高さ: " + StateHolder.menuHeight + "px");
        heightLabel.setTextColor(Color.WHITE);
        heightLabel.setTextSize(12);
        heightLabel.setPadding(0, 16, 0, 4);
        page.addView(heightLabel);

        LinearLayout heightRow = new LinearLayout(activity);
        heightRow.setOrientation(LinearLayout.HORIZONTAL);
        heightRow.setGravity(Gravity.CENTER);
        TextView hMinus = UIHelper.makePmBtn(activity, "  −  ");
        TextView hPlus  = UIHelper.makePmBtn(activity, "  ＋  ");
        TextView hSp = new TextView(activity); hSp.setText("    ");
        heightRow.addView(hMinus); heightRow.addView(hSp); heightRow.addView(hPlus);
        page.addView(heightRow);

        hMinus.setOnClickListener(v -> {
            Activity act = StateHolder.getLastActivity();
            if (act == null) return;
            StateHolder.menuHeight = Math.max(
                    StateHolder.menuHeight - UIHelper.dp(20, act), UIHelper.dp(200, act));
            heightLabel.setText("メニュー高さ: " + StateHolder.menuHeight + "px");
            android.widget.ScrollView ms = menuScrollRef.get();
            if (ms != null) {
                android.view.ViewGroup.LayoutParams lp = ms.getLayoutParams();
                lp.height = StateHolder.menuHeight;
                ms.setLayoutParams(lp);
            }
        });
        hPlus.setOnClickListener(v -> {
            Activity act = StateHolder.getLastActivity();
            if (act == null) return;
            StateHolder.menuHeight = Math.min(
                    StateHolder.menuHeight + UIHelper.dp(20, act), UIHelper.dp(700, act));
            heightLabel.setText("メニュー高さ: " + StateHolder.menuHeight + "px");
            android.widget.ScrollView ms = menuScrollRef.get();
            if (ms != null) {
                android.view.ViewGroup.LayoutParams lp = ms.getLayoutParams();
                lp.height = StateHolder.menuHeight;
                ms.setLayoutParams(lp);
            }
        });

        // フック状態
        TextView hookStatus = new TextView(activity);
        hookStatus.setTextColor(Color.argb(180, 180, 180, 180));
        hookStatus.setTextSize(9);
        hookStatus.setPadding(0, 16, 0, 0);
        hookStatus.setText(buildHookStatusText());
        page.addView(hookStatus);

        return page;
    }

    private static String buildHookStatusText() {
        return "--- フック状態 ---\n"
                + "Mapping: " + MappingManager.getMappingVersion() + "\n"
                + "RtcEngine: " + MappingManager.cls("RtcEngineImpl") + "\n"
                + "SendCmd: " + MappingManager.cls("AgoraWrapper")
                + "." + MappingManager.mtd("AgoraWrapper.sendCommand") + "()\n"
                + "Participants: " + MappingManager.cls("CallImpl")
                + "." + MappingManager.fld("CallImpl.userList") + "\n"
                + "CVM cached: "
                + (StateHolder.getCachedCallViewModel() != null ? "YES" : "NO") + "\n"
                + "agora.a cached: "
                + (StateHolder.getAgoraA() != null ? "YES" : "NO");
    }

    public static void resetRefs() {
        menuOuterRef  = new WeakReference<>(null);
        menuScrollRef = new WeakReference<>(null);
        openBtnRef    = new WeakReference<>(null);
    }
}
