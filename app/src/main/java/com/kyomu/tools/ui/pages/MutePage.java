/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.ui.pages;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import com.kyomu.tools.call.CallManager;
import com.kyomu.tools.call.MuteTarget;
import com.kyomu.tools.core.StateHolder;
import com.kyomu.tools.ui.UIHelper;

import java.lang.ref.WeakReference;

public class MutePage {

    private static WeakReference<LinearLayout> muteListRef = new WeakReference<>(null);
    private static WeakReference<TextView>     muteStatusRef = new WeakReference<>(null);

    public static LinearLayout build(Activity activity) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 4, 0, 0);

        final TextView muteStatus = new TextView(activity);
        muteStatus.setTextColor(Color.argb(200, 200, 200, 200));
        muteStatus.setTextSize(10);
        muteStatus.setGravity(Gravity.CENTER);
        muteStatus.setPadding(0, 0, 0, 4);
        muteStatus.setText(StateHolder.inCall
                ? "callId: " + StateHolder.currentCallId
                  + " / " + StateHolder.muteTargets.size() + "人"
                : "通話未参加");
        muteStatusRef = new WeakReference<>(muteStatus);
        page.addView(muteStatus);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(4, 0, 4, 0);

        // アクション行1
        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        row1.setPadding(0, 0, 0, 4);

        TextView refreshBtn = UIHelper.makeMuteActionBtn(activity, "更新",
                Color.argb(255, 60, 120, 200));
        refreshBtn.setOnClickListener(v -> {
            CallManager.setMuteUIUpdateCallback(() -> {
                Activity act = StateHolder.getLastActivity();
                if (act == null || act.isFinishing()) return;
                act.runOnUiThread(() -> {
                    TextView ms = muteStatusRef.get();
                    if (ms != null) ms.setText(StateHolder.inCall
                            ? "callId: " + StateHolder.currentCallId
                              + " / " + StateHolder.muteTargets.size() + "人"
                            : "通話未参加");
                    updateMuteUI(act);
                });
            });
            CallManager.refreshParticipants();
        });

        TextView resetBtn = UIHelper.makeMuteActionBtn(activity, "リセット",
                Color.argb(255, 100, 100, 100));
        resetBtn.setOnClickListener(v -> {
            CallManager.resetMuteState();
            TextView ms = muteStatusRef.get();
            if (ms != null) ms.setText("通話未参加");
        });

        row1.addView(refreshBtn, btnLp);
        row1.addView(resetBtn, btnLp);
        page.addView(row1);

        // アクション行2
        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, 0, 0, 8);

        TextView muteAllBtn = UIHelper.makeMuteActionBtn(activity, "全Mute",
                Color.argb(255, 200, 60, 60));
        muteAllBtn.setOnClickListener(v -> {
            synchronized (StateHolder.muteTargets) {
                for (MuteTarget mt : StateHolder.muteTargets)
                    if (!mt.muted && !mt.guarded) CallManager.executeMute(mt, true);
            }
        });

        TextView unmuteAllBtn = UIHelper.makeMuteActionBtn(activity, "全解除",
                Color.argb(255, 60, 160, 60));
        unmuteAllBtn.setOnClickListener(v -> {
            synchronized (StateHolder.muteTargets) {
                for (MuteTarget mt : StateHolder.muteTargets)
                    if (mt.muted && !mt.guarded) CallManager.executeMute(mt, false);
            }
        });

        TextView kickAllBtn = UIHelper.makeMuteActionBtn(activity, "全Kick",
                Color.argb(255, 200, 120, 30));
        kickAllBtn.setOnClickListener(v -> {
            synchronized (StateHolder.muteTargets) {
                for (MuteTarget mt : StateHolder.muteTargets)
                    if (!mt.kicked && !mt.guarded) CallManager.executeKick(mt, false);
            }
        });

        TextView permKickAllBtn = UIHelper.makeMuteActionBtn(activity, "全永久",
                Color.argb(255, 180, 30, 30));
        permKickAllBtn.setOnClickListener(v -> {
            synchronized (StateHolder.muteTargets) {
                for (MuteTarget mt : StateHolder.muteTargets)
                    if (!mt.kicked && !mt.guarded) CallManager.executeKick(mt, true);
            }
        });

        row2.addView(muteAllBtn, btnLp);
        row2.addView(unmuteAllBtn, btnLp);
        row2.addView(kickAllBtn, btnLp);
        row2.addView(permKickAllBtn, btnLp);
        page.addView(row2);

        // アクション行3
        LinearLayout row3 = new LinearLayout(activity);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER);
        row3.setPadding(0, 0, 0, 8);

        TextView roleAllModBtn = UIHelper.makeMuteActionBtn(activity, "全+モデ",
                Color.argb(255, 160, 80, 220));
        roleAllModBtn.setOnClickListener(v -> {
            synchronized (StateHolder.muteTargets) {
                for (MuteTarget mt : StateHolder.muteTargets) {
                    if (mt.guarded) continue;
                    CallManager.executeChangeUserRole(mt, true);
                }
            }
        });

        TextView roleAllUserBtn = UIHelper.makeMuteActionBtn(activity, "全-モデ",
                Color.argb(255, 100, 60, 160));
        roleAllUserBtn.setOnClickListener(v -> {
            synchronized (StateHolder.muteTargets) {
                for (MuteTarget mt : StateHolder.muteTargets) {
                    if (mt.guarded) continue;
                    if (mt.callUserId != null
                            && mt.callUserId.equals(StateHolder.myCallUserUuid)) continue;
                    CallManager.executeChangeUserRole(mt, false);
                }
            }
        });

        row3.addView(roleAllModBtn, btnLp);
        row3.addView(roleAllUserBtn, btnLp);
        page.addView(row3);

        TextView note = new TextView(activity);
        note.setText("※ host/moderator権限が必要（全操作共通）");
        note.setTextColor(Color.argb(150, 255, 200, 100));
        note.setTextSize(9);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 0, 0, 4);
        page.addView(note);

        ScrollView muteScroll = new ScrollView(activity);
        muteScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UIHelper.dp(260, activity)));

        LinearLayout muteList = new LinearLayout(activity);
        muteList.setOrientation(LinearLayout.VERTICAL);
        muteListRef = new WeakReference<>(muteList);
        muteScroll.addView(muteList);
        page.addView(muteScroll);

        CallManager.setMuteUIUpdateCallback(() -> {
            Activity act = StateHolder.getLastActivity();
            if (act == null || act.isFinishing()) return;
            act.runOnUiThread(() -> {
                TextView ms = muteStatusRef.get();
                if (ms != null) ms.setText(StateHolder.inCall
                        ? "callId: " + StateHolder.currentCallId
                          + " / " + StateHolder.muteTargets.size() + "人"
                        : "通話未参加");
                updateMuteUI(act);
            });
        });
        updateMuteUI(activity);
        return page;
    }

    public static void updateMuteUI(Activity activity) {
        LinearLayout container = muteListRef.get();
        if (container == null || activity == null) return;
        activity.runOnUiThread(() -> {
            LinearLayout mlc = muteListRef.get();
            if (mlc == null) return;
            mlc.removeAllViews();

            if (!StateHolder.inCall || StateHolder.muteTargets.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText("通話中ではないか参加者なし\n「更新」ボタンで取得");
                empty.setTextColor(Color.GRAY);
                empty.setTextSize(11);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, 20, 0, 20);
                mlc.addView(empty);
                return;
            }

            synchronized (StateHolder.muteTargets) {
                for (int i = 0; i < StateHolder.muteTargets.size(); i++) {
                    final MuteTarget mt = StateHolder.muteTargets.get(i);
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(0, 6, 0, 6);

                    TextView nameView = new TextView(activity);
                    String dn = mt.name.length() > 12
                            ? mt.name.substring(0, 12) + "…" : mt.name;
                    nameView.setText((i + 1) + ". " + dn
                            + (mt.kicked ? " [KICKED]" : mt.muted ? " [MUTED]" : "")
                            + (mt.guarded ? " 🛡️" : ""));
                    nameView.setTextColor(mt.guarded ? Color.argb(255, 100, 200, 255)
                            : mt.kicked ? Color.argb(255, 255, 60, 60)
                              : mt.muted  ? Color.argb(255, 255, 100, 100)
                                : Color.WHITE);
                    nameView.setTextSize(11);
                    row.addView(nameView);

                    LinearLayout btnRow = new LinearLayout(activity);
                    btnRow.setOrientation(LinearLayout.HORIZONTAL);
                    btnRow.setGravity(Gravity.CENTER_VERTICAL);
                    btnRow.setPadding(0, 4, 0, 0);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(3, 0, 3, 0);

                    TextView guardBtn = UIHelper.makeSmallBtn(activity,
                            mt.guarded ? "🛡️ON" : "🛡️",
                            mt.guarded ? Color.argb(255,30,144,255) : Color.argb(255,80,80,80));
                    guardBtn.setOnClickListener(v -> {
                        mt.guarded = !mt.guarded;
                        updateMuteUI(activity);
                    });

                    TextView muteBtn = UIHelper.makeSmallBtn(activity, "Mute",
                            mt.muted ? Color.argb(255,80,80,80) : Color.argb(255,200,60,60));
                    muteBtn.setOnClickListener(v -> CallManager.executeMute(mt, true));

                    TextView unmuteBtn = UIHelper.makeSmallBtn(activity, "解除",
                            !mt.muted ? Color.argb(255,80,80,80) : Color.argb(255,60,160,60));
                    unmuteBtn.setOnClickListener(v -> CallManager.executeMute(mt, false));

                    TextView kickBtn = UIHelper.makeSmallBtn(activity, "Kick",
                            Color.argb(255, 200, 120, 30));
                    kickBtn.setOnClickListener(v -> CallManager.executeKick(mt, false));

                    TextView permKickBtn = UIHelper.makeSmallBtn(activity, "永久",
                            Color.argb(255, 180, 30, 30));
                    permKickBtn.setOnClickListener(v -> CallManager.executeKick(mt, true));

                    btnRow.addView(guardBtn, lp); btnRow.addView(muteBtn, lp);
                    btnRow.addView(unmuteBtn, lp); btnRow.addView(kickBtn, lp);
                    btnRow.addView(permKickBtn, lp);
                    row.addView(btnRow);

                    LinearLayout btnRow2 = new LinearLayout(activity);
                    btnRow2.setOrientation(LinearLayout.HORIZONTAL);
                    btnRow2.setGravity(Gravity.CENTER_VERTICAL);
                    btnRow2.setPadding(0, 2, 0, 0);

                    TextView roleModBtn = UIHelper.makeSmallBtn(activity, "+モデ",
                            Color.argb(255, 160, 80, 220));
                    roleModBtn.setOnClickListener(v ->
                            CallManager.executeChangeUserRole(mt, true));
                    LinearLayout.LayoutParams roleModLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    roleModLp.setMargins(3, 0, 3, 0);

                    TextView roleUserBtn = UIHelper.makeSmallBtn(activity, "-モデ",
                            Color.argb(255, 100, 60, 160));
                    roleUserBtn.setOnClickListener(v ->
                            CallManager.executeChangeUserRole(mt, false));
                    LinearLayout.LayoutParams roleUserLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    roleUserLp.setMargins(3, 0, 3, 0);

                    btnRow2.addView(roleModBtn, roleModLp);
                    btnRow2.addView(roleUserBtn, roleUserLp);
                    row.addView(btnRow2);

                    View div = new View(activity);
                    div.setBackgroundColor(Color.argb(50, 100, 100, 100));
                    LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1);
                    divLp.setMargins(0, 6, 0, 0);
                    div.setLayoutParams(divLp);
                    row.addView(div);
                    mlc.addView(row);
                }
            }
        });
    }

    public static void resetRefs() {
        muteListRef   = new WeakReference<>(null);
        muteStatusRef = new WeakReference<>(null);
    }
}
