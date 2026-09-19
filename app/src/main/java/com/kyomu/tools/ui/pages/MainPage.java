/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.ui.pages;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.kyomu.tools.call.CallManager;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;
import com.kyomu.tools.persist.PersistManager;
import com.kyomu.tools.ui.UIHelper;

import java.lang.ref.WeakReference;

public class MainPage {

    private static WeakReference<CheckBox> cbKickRef    = new WeakReference<>(null);
    private static WeakReference<CheckBox> cbSdkRef     = new WeakReference<>(null);
    private static WeakReference<CheckBox> cbAppRef     = new WeakReference<>(null);
    private static WeakReference<CheckBox> cbServerRef  = new WeakReference<>(null);
    private static WeakReference<CheckBox> cbAdBlockRef = new WeakReference<>(null);
    private static WeakReference<TextView> persistStatusRef = new WeakReference<>(null);

    @SuppressWarnings("unchecked")
    private static final WeakReference<TextView>[] intervalBtnRefs = new WeakReference[5];
    static {
        for (int i = 0; i < intervalBtnRefs.length; i++)
            intervalBtnRefs[i] = new WeakReference<>(null);
    }

    public static LinearLayout build(Activity activity) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        // 広告ブロック
        CheckBox cbAdBlock = UIHelper.addToggle(page, activity,
                "広告ブロック（VIP偽装）", StateHolder.adBlock,
                v -> StateHolder.adBlock = v);
        cbAdBlockRef = new WeakReference<>(cbAdBlock);

        page.addView(UIHelper.makeDivider(activity));

        // ブロック系トグル
        CheckBox cbKick = UIHelper.addToggle(page, activity,
                "キック（退出）ブロック", StateHolder.kickBlock,
                v -> StateHolder.kickBlock = v);
        cbKickRef = new WeakReference<>(cbKick);

        CheckBox cbSdk = UIHelper.addToggle(page, activity,
                "SDK ミュートブロック", StateHolder.sdkMute,
                v -> StateHolder.sdkMute = v);
        cbSdkRef = new WeakReference<>(cbSdk);

        CheckBox cbApp = UIHelper.addToggle(page, activity,
                "アプリ内ミュートブロック", StateHolder.appMuteBlock,
                v -> StateHolder.appMuteBlock = v);
        cbAppRef = new WeakReference<>(cbApp);

        CheckBox cbServer = UIHelper.addToggle(page, activity,
                "サーバー切断ブロック", StateHolder.serverKick,
                v -> StateHolder.serverKick = v);
        cbServerRef = new WeakReference<>(cbServer);

        // ボタン行
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 16, 0, 0);

        // 推奨ボタン
        final TextView recommend = new TextView(activity);
        boolean isRec = StateHolder.kickBlock && !StateHolder.sdkMute
                && StateHolder.appMuteBlock && StateHolder.serverKick;
        recommend.setText(isRec ? "推奨OFF" : "推奨ON");
        recommend.setTextColor(Color.WHITE);
        recommend.setTextSize(14);
        recommend.setGravity(Gravity.CENTER);
        recommend.setPadding(24, 12, 24, 12);
        GradientDrawable recBg = new GradientDrawable();
        recBg.setCornerRadius(UIHelper.dp(8, activity));
        recBg.setColor(isRec ? Color.argb(255,255,100,100) : Color.argb(255,80,200,255));
        recommend.setBackground(recBg);
        recommend.setOnClickListener(new View.OnClickListener() {
            private boolean isOn = StateHolder.kickBlock && !StateHolder.sdkMute
                    && StateHolder.appMuteBlock && StateHolder.serverKick;
            @Override public void onClick(View v) {
                Activity act = StateHolder.getLastActivity();
                if (act == null) return;
                if (isOn) {
                    StateHolder.kickBlock = false; StateHolder.sdkMute = false;
                    StateHolder.appMuteBlock = false; StateHolder.serverKick = false;
                    setCheckboxes(false, false, false, false);
                    recommend.setText("推奨ON");
                    recBg.setColor(Color.argb(255, 80, 200, 255));
                    isOn = false;
                } else {
                    StateHolder.kickBlock = true; StateHolder.sdkMute = false;
                    StateHolder.appMuteBlock = true; StateHolder.serverKick = true;
                    setCheckboxes(true, false, true, true);
                    recommend.setText("推奨OFF");
                    recBg.setColor(Color.argb(255, 255, 100, 100));
                    isOn = true;
                }
            }
        });

        // 全ON/OFFボタン
        final boolean[] allState = {StateHolder.kickBlock && StateHolder.sdkMute
                && StateHolder.appMuteBlock && StateHolder.serverKick};
        final TextView toggleAll = new TextView(activity);
        toggleAll.setText(allState[0] ? "全OFF" : "全ON");
        toggleAll.setTextColor(allState[0] ? Color.WHITE : Color.BLACK);
        toggleAll.setTextSize(14);
        toggleAll.setGravity(Gravity.CENTER);
        toggleAll.setPadding(24, 12, 24, 12);
        GradientDrawable togBg = new GradientDrawable();
        togBg.setCornerRadius(UIHelper.dp(8, activity));
        togBg.setColor(allState[0] ? Color.argb(255,255,60,60) : Color.argb(255,80,255,80));
        toggleAll.setBackground(togBg);
        toggleAll.setOnClickListener(v -> {
            Activity act = StateHolder.getLastActivity();
            if (act == null) return;
            allState[0] = !allState[0];
            StateHolder.kickBlock = allState[0]; StateHolder.sdkMute = allState[0];
            StateHolder.appMuteBlock = allState[0]; StateHolder.serverKick = allState[0];
            setCheckboxes(allState[0], allState[0], allState[0], allState[0]);
            toggleAll.setText(allState[0] ? "全OFF" : "全ON");
            toggleAll.setTextColor(allState[0] ? Color.WHITE : Color.BLACK);
            togBg.setColor(allState[0] ? Color.argb(255,255,60,60) : Color.argb(255,80,255,80));
        });

        // 解除送信ボタン
        TextView manualUnmute = new TextView(activity);
        manualUnmute.setText("解除送信");
        manualUnmute.setTextColor(Color.WHITE);
        manualUnmute.setTextSize(14);
        manualUnmute.setGravity(Gravity.CENTER);
        manualUnmute.setPadding(24, 12, 24, 12);
        GradientDrawable unmuteBg = new GradientDrawable();
        unmuteBg.setCornerRadius(UIHelper.dp(8, activity));
        unmuteBg.setColor(Color.argb(255, 60, 140, 200));
        manualUnmute.setBackground(unmuteBg);
        manualUnmute.setOnClickListener(v -> CallManager.sendForceUnmute());

        TextView sp1 = new TextView(activity); sp1.setText(" ");
        TextView sp2 = new TextView(activity); sp2.setText(" ");
        btnRow.addView(recommend);
        btnRow.addView(sp1);
        btnRow.addView(toggleAll);
        btnRow.addView(sp2);
        btnRow.addView(manualUnmute);
        page.addView(btnRow);

        // Persist セクション
        TextView persistLabel = new TextView(activity);
        persistLabel.setText("── 強制ミュート解除送信（自動） ──");
        persistLabel.setTextColor(Color.GRAY);
        persistLabel.setTextSize(12);
        persistLabel.setGravity(Gravity.CENTER);
        persistLabel.setPadding(0, 20, 0, 4);
        page.addView(persistLabel);

        TextView persistStatusView = new TextView(activity);
        persistStatusView.setTextSize(11);
        persistStatusView.setGravity(Gravity.CENTER);
        persistStatusView.setPadding(0, 0, 0, 8);
        persistStatusRef = new WeakReference<>(persistStatusView);
        updatePersistStatus();
        page.addView(persistStatusView);

        final Switch persistSwitch = new Switch(activity);
        persistSwitch.setText("自動ミュート解除送信");
        persistSwitch.setTextColor(Color.WHITE);
        persistSwitch.setTextSize(13);
        persistSwitch.setChecked(StateHolder.persistEnabled);
        persistSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            StateHolder.persistEnabled = isChecked;
            if (isChecked) {
                PersistManager.start();
                if (!StateHolder.persistEnabled) btn.setChecked(false);
            } else {
                PersistManager.stop();
            }
            updatePersistStatus();
        });
        page.addView(persistSwitch);

        PersistManager.setStatusUpdateCallback(() -> {
            Activity act = StateHolder.getLastActivity();
            if (act != null) act.runOnUiThread(MainPage::updatePersistStatus);
        });

        // インターバル
        final TextView intervalInfo = new TextView(activity);
        intervalInfo.setText("送信間隔: " + StateHolder.currentInterval + "s");
        intervalInfo.setTextColor(Color.argb(200, 200, 200, 200));
        intervalInfo.setTextSize(11);
        intervalInfo.setPadding(0, 8, 0, 4);
        page.addView(intervalInfo);

        LinearLayout intervalRow = new LinearLayout(activity);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        intervalRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < StateHolder.INTERVAL_OPTIONS.length; i++) {
            final int idx = i;
            final TextView ibtn = new TextView(activity);
            ibtn.setText(StateHolder.INTERVAL_OPTIONS[i] + "s");
            ibtn.setTextSize(12);
            ibtn.setGravity(Gravity.CENTER);
            ibtn.setPadding(0, 8, 0, 8);
            intervalBtnRefs[i] = new WeakReference<>(ibtn);
            UIHelper.styleIntervalBtn(ibtn, i == StateHolder.selectedIntervalIndex, activity);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            ilp.setMargins(4, 0, 4, 0);
            intervalRow.addView(ibtn, ilp);
            ibtn.setOnClickListener(v -> {
                Activity act = StateHolder.getLastActivity();
                if (act == null) return;
                StateHolder.selectedIntervalIndex = idx;
                StateHolder.currentInterval = StateHolder.INTERVAL_OPTIONS[idx];
                intervalInfo.setText("送信間隔: " + StateHolder.currentInterval + "s");
                for (int j = 0; j < StateHolder.INTERVAL_OPTIONS.length; j++) {
                    TextView ref = intervalBtnRefs[j].get();
                    if (ref != null) UIHelper.styleIntervalBtn(ref, j == idx, act);
                }
                if (StateHolder.persistEnabled) {
                    PersistManager.stop();
                    PersistManager.start();
                }
            });
        }
        page.addView(intervalRow);

        page.addView(UIHelper.makeDivider(activity));

        TextView loginSection = new TextView(activity);
        loginSection.setText("── ログイン ──");
        loginSection.setTextColor(Color.argb(200, 80, 200, 255));
        loginSection.setTextSize(12);
        loginSection.setGravity(Gravity.CENTER);
        loginSection.setPadding(0, 4, 0, 4);
        page.addView(loginSection);

        UIHelper.addToggle(page, activity,
                "ログインフック（v2 API 経由）", StateHolder.loginHookEnabled,
                v -> {
                    StateHolder.loginHookEnabled = v;
                    HookManager.log("[Login] フック " + (v ? "ON" : "OFF"));
                });

        TextView loginNote = new TextView(activity);
        loginNote.setText("※ ONにしてからログインボタンを押してください\n※ 連続使用は429エラーの原因になります");
        loginNote.setTextColor(Color.argb(150, 255, 200, 100));
        loginNote.setTextSize(9);
        loginNote.setPadding(0, 2, 0, 8);
        page.addView(loginNote);

        // ── トークン再取得ボタン ──
        TextView btnRefresh = new TextView(activity);
        btnRefresh.setText("トークン再取得");
        btnRefresh.setTextColor(Color.WHITE);
        btnRefresh.setTextSize(13);
        btnRefresh.setGravity(Gravity.CENTER);
        btnRefresh.setPadding(24, 12, 24, 12);
        GradientDrawable refreshBg = new GradientDrawable();
        refreshBg.setCornerRadius(UIHelper.dp(8, activity));
        refreshBg.setColor(Color.argb(255, 80, 180, 120));
        btnRefresh.setBackground(refreshBg);
        btnRefresh.setOnClickListener(v ->
                com.kyomu.tools.login.LoginHookManager.manualRefresh());
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        refreshLp.setMargins(32, 8, 32, 16);
        page.addView(btnRefresh, refreshLp);

        return page;
    }

    public static void updatePersistStatus() {
        TextView psv = persistStatusRef.get();
        if (psv == null) return;
        psv.post(() -> {
            if (StateHolder.persistEnabled) {
                psv.setText("送信中: " + StateHolder.currentInterval
                        + "s間隔 | " + StateHolder.sentCount + "回送信");
                psv.setTextColor(Color.argb(255, 100, 255, 100));
            } else {
                psv.setText("停止中");
                psv.setTextColor(Color.GRAY);
            }
        });
    }

    private static void setCheckboxes(boolean kick, boolean sdk,
                                      boolean app, boolean server) {
        CheckBox ck = cbKickRef.get();   if (ck != null) ck.setChecked(kick);
        CheckBox cs = cbSdkRef.get();    if (cs != null) cs.setChecked(sdk);
        CheckBox ca = cbAppRef.get();    if (ca != null) ca.setChecked(app);
        CheckBox cv = cbServerRef.get(); if (cv != null) cv.setChecked(server);
    }

    public static void resetRefs() {
        cbKickRef    = new WeakReference<>(null);
        cbSdkRef     = new WeakReference<>(null);
        cbAppRef     = new WeakReference<>(null);
        cbServerRef  = new WeakReference<>(null);
        cbAdBlockRef = new WeakReference<>(null);
        persistStatusRef = new WeakReference<>(null);
        for (int i = 0; i < intervalBtnRefs.length; i++)
            intervalBtnRefs[i] = new WeakReference<>(null);
    }
}
