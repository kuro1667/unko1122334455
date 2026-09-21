/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kyomu.tools.LicenseManager;
import com.kyomu.tools.MappingManager;
import com.kyomu.tools.core.BrandGuard;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;
import com.kyomu.tools.ui.pages.CallPage;
import com.kyomu.tools.ui.pages.LogPage;
import com.kyomu.tools.ui.pages.MainPage;
import com.kyomu.tools.ui.pages.MutePage;
import com.kyomu.tools.ui.pages.SettingsPage;
import com.kyomu.tools.ui.pages.TestPage;

import java.lang.ref.WeakReference;

public class MenuUI {

    private static final String TAG = "[kyomu]";

    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private static WeakReference<View>     menuOuterRef       = new WeakReference<>(null);
    private static WeakReference<View>     openBtnRef         = new WeakReference<>(null);
    private static WeakReference<ScrollView> menuScrollRef    = new WeakReference<>(null);

    private MenuUI() {}

    private static int dp(int dp, Activity activity) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ==================== setup ====================

    public static void setup(Activity activity) {
        if (activity == null) return;
        if (!BrandGuard.isValid()) {
            HookManager.log(TAG + " BrandGuard検証失敗 - UI無効化");
            return;
        }

        LicenseManager.isLicensed           = true;
        LicenseManager.licenseChecked       = true;
        LicenseManager.licenseServerReached = true;

        if (!android.provider.Settings.canDrawOverlays(activity)) {
            HookManager.log(TAG + " オーバーレイ権限なし - 設定画面を開く");
            try {
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:com.kyomu.tools"));
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Throwable t) {
                HookManager.log(TAG + " 設定画面を開けません: " + t.getMessage());
            }
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                // 前のActivityのUIをクリーンアップ
                Activity prev = currentActivityRef.get();
                if (prev != null && prev != activity && !prev.isFinishing()) {
                    try {
                        ViewGroup oldRoot = (ViewGroup) prev.getWindow().getDecorView();
                        View oldBtn  = oldRoot.findViewWithTag("kyomutool_btn");
                        View oldMenu = oldRoot.findViewWithTag("kyomutool_menu");
                        if (oldBtn  != null) oldRoot.removeView(oldBtn);
                        if (oldMenu != null) oldRoot.removeView(oldMenu);
                    } catch (Throwable ignored) {}
                }

                currentActivityRef = new WeakReference<>(activity);
                StateHolder.lastActivityRef = new WeakReference<>(activity);

                ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();
                View existingBtn = rootView.findViewWithTag("kyomutool_btn");
                if (existingBtn != null) {
                    rootView.removeView(existingBtn);
                    View oldMenu = rootView.findViewWithTag("kyomutool_menu");
                    if (oldMenu != null) rootView.removeView(oldMenu);
                }

                resetPageRefs();

                if (StateHolder.menuWidth == 580 || StateHolder.menuWidth == 0) {
                    StateHolder.menuWidth  = dp(270, activity);
                }
                if (StateHolder.menuHeight == 1000 || StateHolder.menuHeight == 0) {
                    StateHolder.menuHeight = dp(440, activity);
                }

                buildMenu(activity, rootView);

            } catch (Throwable t) {
                HookManager.log(TAG + " setup失敗: " + t.getMessage());
            }
        });
    }

    // ==================== buildMenu ====================

    private static void buildMenu(Activity activity, ViewGroup rootView) {

        // メニュー本体
        final LinearLayout menuOuter = new LinearLayout(activity);
        menuOuter.setTag("kyomutool_menu");
        menuOuter.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.argb(255, 20, 20, 35)); // 背景は常に不透明
        menuBg.setCornerRadius(dp(14, activity));
        menuBg.setStroke(dp(1, activity), Color.argb(180, 46, 204, 113));
        menuOuter.setBackground(menuBg);
        menuOuter.setAlpha(StateHolder.menuAlpha / 255f);
        menuOuter.setElevation(dp(10, activity));
        menuOuter.setClipToOutline(true);
        menuOuter.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14, activity));
            }
        });
        menuOuter.setVisibility(StateHolder.menuVisible ? View.VISIBLE : View.GONE);
        menuOuterRef = new WeakReference<>(menuOuter);

        final ScrollView menuScroll = new ScrollView(activity);
        menuScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, StateHolder.menuHeight));
        menuScroll.setFillViewport(true);
        menuScrollRef = new WeakReference<>(menuScroll);

        LinearLayout menu = new LinearLayout(activity);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(32, 24, 32, 24);
        menuScroll.addView(menu);
        menuOuter.addView(menuScroll);

        // タイトル
        TextView title = new TextView(activity);
        title.setText(BrandGuard.MENU_TITLE);
        title.setTextColor(Color.argb(255, 46, 204, 113));
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 4);
        title.setLetterSpacing(0.1f);
        menu.addView(title);

        TextView versionText = new TextView(activity);
        versionText.setText("v4.6 | mapping: " + MappingManager.getMappingVersion());
        versionText.setTextColor(Color.argb(100, 150, 150, 150));
        versionText.setTextSize(9);
        versionText.setGravity(Gravity.CENTER);
        versionText.setPadding(0, 0, 0, 4);
        menu.addView(versionText);

        TextView licenseStatus = new TextView(activity);

// TODO: LicenseManager復元時はここを有効化し、下のVIP固定表示を削除する
// long hours = (System.currentTimeMillis() - LicenseManager.licenseLastCheck)
//         / (1000L * 60 * 60);
// long remaining = Math.max(0, 24 - hours);
// if (LicenseManager.isLicensed) {
//     licenseStatus.setText("✦ VIP Member ✦");
//     licenseStatus.setTextColor(Color.argb(255, 255, 215, 0));
// } else if (LicenseManager.licenseServerReached) {
//     licenseStatus.setText("License: 無効");
//     licenseStatus.setTextColor(Color.argb(255, 255, 80, 80));
// } else {
//     licenseStatus.setText("License: オフライン（残り" + remaining + "時間）");
//     licenseStatus.setTextColor(Color.argb(255, 255, 200, 50));
// }

// VIP固定表示（LicenseManager無効中）
        licenseStatus.setText("✦ VIP Member ✦");
        licenseStatus.setTextColor(Color.argb(255, 255, 215, 0)); // ゴールド
        licenseStatus.setTextSize(11);
        licenseStatus.setTypeface(null, Typeface.BOLD);
        licenseStatus.setGravity(Gravity.CENTER);
        licenseStatus.setLetterSpacing(0.08f);
        licenseStatus.setPadding(0, 0, 0, 8);
        menu.addView(licenseStatus);

        // タブバー
        LinearLayout tabBar = new LinearLayout(activity);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setPadding(0, 0, 0, 8);

        final TextView tabMain     = UIHelper.makeTabBtn(activity, "メイン",   StateHolder.currentTab == 0);
        final TextView tabLogs     = UIHelper.makeTabBtn(activity, "ログ",     StateHolder.currentTab == 1);
        final TextView tabMute     = UIHelper.makeTabBtn(activity, "ミュート", StateHolder.currentTab == 2);
        final TextView tabCall     = UIHelper.makeTabBtn(activity, "Call",     StateHolder.currentTab == 3);
        final TextView tabSettings = UIHelper.makeTabBtn(activity, "設定",     StateHolder.currentTab == 4);
        final TextView tabTest     = UIHelper.makeTabBtn(activity, "TEST",     StateHolder.currentTab == 5);

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tabLp.setMargins(2, 0, 2, 0);
        tabBar.addView(tabMain,     new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabLogs,     new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabMute,     new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabCall,     new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabSettings, new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabTest,     new LinearLayout.LayoutParams(tabLp));
        menu.addView(tabBar);

        // コンテンツ
        final FrameLayout contentFrame = new FrameLayout(activity);
        contentFrame.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final LinearLayout mainPage     = MainPage.build(activity);
        final LinearLayout logsPage     = LogPage.build(activity);
        final LinearLayout mutePage     = MutePage.build(activity);
        final LinearLayout callPage     = CallPage.build(activity);
        final LinearLayout settingsPage = SettingsPage.build(activity);
        final LinearLayout testPage     = TestPage.build(activity);

        // SettingsPage に参照を渡す
        SettingsPage.setRefs(menuOuter, menuScroll, null);

        mainPage.setVisibility(StateHolder.currentTab == 0 ? View.VISIBLE : View.GONE);
        logsPage.setVisibility(StateHolder.currentTab == 1 ? View.VISIBLE : View.GONE);
        mutePage.setVisibility(StateHolder.currentTab == 2 ? View.VISIBLE : View.GONE);
        callPage.setVisibility(StateHolder.currentTab == 3 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(StateHolder.currentTab == 4 ? View.VISIBLE : View.GONE);
        testPage.setVisibility(StateHolder.currentTab == 5 ? View.VISIBLE : View.GONE);

        contentFrame.addView(mainPage);
        contentFrame.addView(logsPage);
        contentFrame.addView(mutePage);
        contentFrame.addView(callPage);
        contentFrame.addView(settingsPage);
        contentFrame.addView(testPage);
        menu.addView(contentFrame);

        // タブ切り替え
        tabMain.setOnClickListener(v -> switchTab(activity, 0,
                mainPage, logsPage, mutePage, callPage, settingsPage, testPage,
                tabMain, tabLogs, tabMute, tabCall, tabSettings, tabTest));
        tabLogs.setOnClickListener(v -> switchTab(activity, 1,
                mainPage, logsPage, mutePage, callPage, settingsPage, testPage,
                tabMain, tabLogs, tabMute, tabCall, tabSettings, tabTest));
        tabMute.setOnClickListener(v -> switchTab(activity, 2,
                mainPage, logsPage, mutePage, callPage, settingsPage, testPage,
                tabMain, tabLogs, tabMute, tabCall, tabSettings, tabTest));
        tabCall.setOnClickListener(v -> switchTab(activity, 3,
                mainPage, logsPage, mutePage, callPage, settingsPage, testPage,
                tabMain, tabLogs, tabMute, tabCall, tabSettings, tabTest));
        tabSettings.setOnClickListener(v -> switchTab(activity, 4,
                mainPage, logsPage, mutePage, callPage, settingsPage, testPage,
                tabMain, tabLogs, tabMute, tabCall, tabSettings, tabTest));
        tabTest.setOnClickListener(v -> switchTab(activity, 5,
                mainPage, logsPage, mutePage, callPage, settingsPage, testPage,
                tabMain, tabLogs, tabMute, tabCall, tabSettings, tabTest));

        // 閉じるボタン
        TextView close = new TextView(activity);
        close.setText("[ 閉じる ]");
        close.setTextColor(Color.RED);
        close.setTextSize(14);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, 20, 0, 0);
        close.setOnClickListener(v -> {
            menuOuter.setVisibility(View.GONE);
            StateHolder.menuVisible = false;
        });
        menu.addView(close);

        // 開閉ボタン（丸アイコン）
        final ImageView openBtn = new ImageView(activity);
        openBtn.setTag("kyomutool_btn");
        openBtnRef = new WeakReference<>(openBtn);
        final int btnSize = 128;

        loadFloatIcon(activity, openBtn, btnSize);

        openBtn.setScaleType(ImageView.ScaleType.CENTER_CROP);
        openBtn.setBackground(null);
        float[] alphaVals = {1.0f, 0.2f, 0.1f};
        openBtn.setAlpha(alphaVals[StateHolder.btnAlphaMode]);
        openBtn.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        openBtn.setClipToOutline(true);

        // SettingsPage のopenBtn参照を更新
        SettingsPage.setRefs(menuOuter, menuScroll, openBtn);

        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                StateHolder.menuWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
        menuParams.gravity = Gravity.TOP | Gravity.LEFT;

        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(btnSize, btnSize);
        btnParams.gravity = Gravity.TOP | Gravity.LEFT;

        rootView.addView(menuOuter, menuParams);
        rootView.addView(openBtn, btnParams);

        openBtn.post(() -> {
            int screenWidth = rootView.getWidth();
            if (StateHolder.onLeftSide) openBtn.setX(0);
            else openBtn.setX(screenWidth - openBtn.getWidth());
            openBtn.setY(StateHolder.savedBtnY);
            updateMenuPosition(openBtn, menuOuter, rootView);
        });

        openBtn.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY, startRawX, startRawY;
            private boolean dragged;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        startRawX = event.getRawX();
                        startRawY = event.getRawY();
                        dragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - startRawX) > 10
                                || Math.abs(event.getRawY() - startRawY) > 10) {
                            dragged = true;
                            v.setX(event.getRawX() + dX);
                            v.setY(event.getRawY() + dY);
                            if (menuOuter.getVisibility() == View.VISIBLE)
                                updateMenuPosition(openBtn, menuOuter, rootView);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragged) {
                            int screenWidth = rootView.getWidth();
                            int screenH     = rootView.getHeight();
                            float centerX   = v.getX() + v.getWidth() / 2f;
                            if (centerX < screenWidth / 2f) {
                                v.setX(0);
                                StateHolder.onLeftSide = true;
                            } else {
                                v.setX(screenWidth - v.getWidth());
                                StateHolder.onLeftSide = false;
                            }
                            float newY = Math.max(0,
                                    Math.min(v.getY(), screenH - v.getHeight()));
                            v.setY(newY);
                            StateHolder.savedBtnY = newY;
                            updateMenuPosition(openBtn, menuOuter, rootView);
                        } else {
                            if (menuOuter.getVisibility() == View.VISIBLE) {
                                menuOuter.setVisibility(View.GONE);
                                StateHolder.menuVisible = false;
                            } else {
                                menuOuter.setVisibility(View.VISIBLE);
                                StateHolder.menuVisible = true;
                                updateMenuPosition(openBtn, menuOuter, rootView);
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        HookManager.log(TAG + " メニュー表示成功");
    }

    // ==================== タブ切り替え ====================

    private static void switchTab(Activity activity, int tab,
                                  LinearLayout mainPage, LinearLayout logsPage,
                                  LinearLayout mutePage,  LinearLayout callPage,
                                  LinearLayout settingsPage, LinearLayout testPage,
                                  TextView tabMain, TextView tabLogs, TextView tabMute,
                                  TextView tabCall, TextView tabSettings, TextView tabTest) {
        StateHolder.currentTab = tab;
        mainPage.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        logsPage.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        mutePage.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        callPage.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(tab == 4 ? View.VISIBLE : View.GONE);
        testPage.setVisibility(tab == 5 ? View.VISIBLE : View.GONE);
        UIHelper.styleTabBtn(tabMain,     tab == 0, activity);
        UIHelper.styleTabBtn(tabLogs,     tab == 1, activity);
        UIHelper.styleTabBtn(tabMute,     tab == 2, activity);
        UIHelper.styleTabBtn(tabCall,     tab == 3, activity);
        UIHelper.styleTabBtn(tabSettings, tab == 4, activity);
        UIHelper.styleTabBtn(tabTest,     tab == 5, activity);
        if (tab == 3) {
            CallPage.updateCallSpinner(activity);
            CallPage.updateCallInfo(activity);
        }
    }

    // ==================== ユーティリティ ====================

    private static void updateMenuPosition(View btn, View menu, ViewGroup rootView) {
        menu.post(() -> {
            float btnX = btn.getX(), btnY = btn.getY();
            int btnW = btn.getWidth(), mW = menu.getWidth(), mH = menu.getHeight();
            int screenW = rootView.getWidth(), screenH = rootView.getHeight();
            float menuX = StateHolder.onLeftSide ? btnX + btnW + 8 : btnX - mW - 8;
            float menuY = btnY;
            if (menuX + mW > screenW) menuX = screenW - mW;
            if (menuX < 0)            menuX = 0;
            if (menuY + mH > screenH) menuY = screenH - mH;
            if (menuY < 0)            menuY = 0;
            menu.setX(menuX);
            menu.setY(menuY);
        });
    }

    private static void loadFloatIcon(Activity activity, ImageView btn, int size) {
        try {
            // モジュール自身のコンテキストを作成
            android.content.Context moduleCtx = activity.createPackageContext(
                    "com.kyomu.tools",
                    android.content.Context.CONTEXT_IGNORE_SECURITY
            );

            android.content.res.Resources moduleRes = moduleCtx.getResources();
            int resId = moduleRes.getIdentifier(
                    "ic_float_btn", "drawable", "com.kyomu.tools"
            );

            if (resId != 0) {
                android.graphics.drawable.Drawable icon =
                        moduleRes.getDrawable(resId, null);
                btn.setImageDrawable(icon);
                btn.setBackground(null);
                HookManager.log(TAG + " フロートアイコン読み込み成功");
            } else {
                HookManager.log(TAG + " ic_float_btn 未発見 - フォールバック使用");
                drawFallbackIcon(activity, btn, size);
            }

        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            HookManager.log(TAG + " PackageContext作成失敗: " + e.getMessage());
            drawFallbackIcon(activity, btn, size);
        } catch (Throwable t) {
            HookManager.log(TAG + " アイコン読み込み失敗: " + t.getMessage());
            drawFallbackIcon(activity, btn, size);
        }
    }


    private static void drawFallbackIcon(Activity activity, ImageView btn, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF141420);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint);

        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(0xFF2ECC71);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(6);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6, ringPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF2ECC71);
        textPaint.setTextSize(size * 0.35f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText("K", size / 2f, textY, textPaint);

        btn.setImageBitmap(bmp);
    }

    private static void resetPageRefs() {
        MainPage.resetRefs();
        LogPage.resetRefs();
        MutePage.resetRefs();
        CallPage.resetRefs();
        SettingsPage.resetRefs();
        TestPage.resetRefs();
    }
}
