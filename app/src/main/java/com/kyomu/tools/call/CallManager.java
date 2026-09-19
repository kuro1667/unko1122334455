/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.call;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.kyomu.tools.MappingManager;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XposedHelpers;

/**
 * ミュート・キック・Call再参加処理を管理するクラス。
 */
public class CallManager {

    // ===== インスタンス化禁止 =====
    private CallManager() {}

    // ===== スレッドプール =====
    // 修正: new Thread() の多用を ExecutorService に変更
    private static final ExecutorService executor =
            Executors.newCachedThreadPool();

    // ===== 参加者取得 =====

    public static void refreshParticipants() {
        Object cvm = StateHolder.getCachedCallViewModel();
        if (cvm == null) {
            HookManager.log("[ミュート] CallViewModel未取得 - 通話画面を開いてください");
            return;
        }
        executor.execute(() -> {
            try {
                Object vm = StateHolder.getCachedCallViewModel();
                if (vm == null) {
                    HookManager.log("[ミュート] CallViewModel GC済");
                    return;
                }
                Object myCall = XposedHelpers.getObjectField(vm, "myCall");
                if (myCall == null) {
                    HookManager.log("[ミュート] myCall is null - 通話未参加");
                    return;
                }

                HookManager.log("[ミュート] myCall class = "
                        + myCall.getClass().getName());

                // ConferenceCall 取得
                try {
                    Object conf = XposedHelpers.getObjectField(myCall,
                            MappingManager.fld("CallImpl.conferenceCall"));
                    if (conf != null) {
                        StateHolder.currentCallId = (long)
                                XposedHelpers.callMethod(conf, "getId");
                        StateHolder.agoraChannel  = (String)
                                XposedHelpers.callMethod(conf, "getAgoraChannel");
                        StateHolder.myCallUserUuid = (String)
                                XposedHelpers.callMethod(conf, "getConferenceCallUserUuid");
                        HookManager.log("[ミュート] callId=" + StateHolder.currentCallId
                                + " channel=" + StateHolder.agoraChannel);
                        HookManager.log("[ミュート] myUuid=" + StateHolder.myCallUserUuid);
                    }
                } catch (Throwable t) {
                    HookManager.log("[ミュート] ConferenceCall取得失敗: " + t.getMessage());
                }

                // UUID Map 取得
                Map<String, Object> uuidMap = null;
                try {
                    Object jObj = XposedHelpers.getObjectField(myCall,
                            MappingManager.fld("CallImpl.uuidMap"));
                    if (jObj instanceof ConcurrentHashMap) {
                        @SuppressWarnings("unchecked")
                        ConcurrentHashMap<String, Object> tempMap =
                                (ConcurrentHashMap<String, Object>) jObj;
                        uuidMap = tempMap;
                        HookManager.log("[ミュート] UUID map size=" + uuidMap.size());
                    }
                } catch (Throwable t) {
                    HookManager.log("[ミュート] uuidMap取得失敗: " + t.getMessage());
                }

                // 自分のユーザーID取得
                try {
                    Object acc = null;
                    try {
                        Field accField = myCall.getClass().getDeclaredField(
                                MappingManager.fld("CallImpl.account"));
                        accField.setAccessible(true);
                        acc = accField.get(myCall);
                    } catch (Throwable t) {
                        HookManager.log("[ミュート] account取得失敗: " + t.getMessage());
                    }
                    Object myUser = XposedHelpers.callMethod(acc,
                            MappingManager.mtd("AccountManager.getCurrentUser"));
                    if (myUser != null) {
                        long myId = (long) XposedHelpers.callMethod(myUser, "getId");
                        StateHolder.myUserId = String.valueOf(myId);
                        HookManager.log("[ミュート] myUserId=" + StateHolder.myUserId);
                    }
                } catch (Throwable t) {
                    HookManager.log("[ミュート] myUserId取得失敗: " + t.getMessage());
                    if (uuidMap != null && !StateHolder.myCallUserUuid.isEmpty()) {
                        Object myIdObj = uuidMap.get(StateHolder.myCallUserUuid);
                        if (myIdObj != null) {
                            StateHolder.myUserId = String.valueOf(myIdObj);
                            HookManager.log("[ミュート] myUserId(from uuidMap)="
                                    + StateHolder.myUserId);
                        }
                    }
                }

                ensureAgoraCached();

                // userList 取得
                Object uObj = null;
                try {
                    Field uField = myCall.getClass().getDeclaredField(
                            MappingManager.fld("CallImpl.userList"));
                    uField.setAccessible(true);
                    uObj = uField.get(myCall);
                    HookManager.log("[DEBUG] userList取得成功 type="
                            + (uObj != null ? uObj.getClass().getName() : "null"));
                } catch (Throwable t) {
                    HookManager.log("[DEBUG] userList取得失敗: " + t.getMessage());
                }

                List<MuteTarget> newList = new ArrayList<>();
                if (uObj instanceof java.util.Collection) {
                    java.util.Collection<?> users = (java.util.Collection<?>) uObj;
                    HookManager.log("[ミュート] userList size=" + users.size());

                    for (Object user : users) {
                        try {
                            String ts         = user.toString();
                            String appUserId  = extractField(ts, "appUserid");
                            String callUserId = extractField(ts, "callUserId");
                            String nickname   = extractField(ts, "nickname");
                            String avatarUrl  = extractField(ts, "avatarUrl");

                            if (appUserId == null || appUserId.isEmpty()) continue;
                            if (appUserId.equals(StateHolder.myUserId)) continue;

                            boolean wasMuted = false;
                            synchronized (StateHolder.muteTargets) {
                                for (MuteTarget mt : StateHolder.muteTargets) {
                                    if (mt.callUserId.equals(callUserId)) {
                                        wasMuted = mt.muted;
                                        break;
                                    }
                                }
                            }

                            MuteTarget mt = new MuteTarget(
                                    nickname   != null ? nickname   : "???",
                                    appUserId,
                                    callUserId != null ? callUserId : "",
                                    avatarUrl  != null ? avatarUrl  : "");
                            mt.muted = wasMuted;
                            newList.add(mt);

                        } catch (Throwable t) {
                            HookManager.log("[ミュート] CallUser解析エラー: "
                                    + t.getMessage());
                        }
                    }
                } else {
                    HookManager.log("[ミュート] userList type: "
                            + (uObj != null ? uObj.getClass().getName() : "null"));
                }

                synchronized (StateHolder.muteTargets) {
                    StateHolder.muteTargets.clear();
                    StateHolder.muteTargets.addAll(newList);
                }
                StateHolder.inCall = true;
                HookManager.log("[ミュート] 参加者取得完了: " + newList.size() + "人");

                Activity act = StateHolder.getLastActivity();
                if (act != null) act.runOnUiThread(CallManager::notifyMuteUIUpdate);

            } catch (Throwable t) {
                HookManager.log("[ミュート] 参加者取得失敗: " + t.getMessage());
            }
        });
    }

    // ===== ミュート実行 =====

    public static void executeMute(final MuteTarget target, final boolean mute) {
        Object cvm = StateHolder.getCachedCallViewModel();
        if (cvm == null) {
            HookManager.log("[ミュート] CallViewModel未取得");
            return;
        }
        executor.execute(() -> {
            try {
                Object cvmInner = StateHolder.getCachedCallViewModel();
                if (cvmInner == null) {
                    HookManager.log("[ミュート] CallViewModel GC済");
                    return;
                }
                Object myCall = XposedHelpers.getObjectField(cvmInner, "myCall");
                if (myCall == null) {
                    HookManager.log("[ミュート] myCall null");
                    return;
                }

                Object conf = XposedHelpers.getObjectField(myCall,
                        MappingManager.fld("CallImpl.conferenceCall"));
                if (conf == null) {
                    HookManager.log("[ミュート] ConferenceCall null");
                    return;
                }
                long confId = (long) XposedHelpers.callMethod(conf, "getId");

                Object apiProxy = XposedHelpers.getObjectField(myCall,
                        MappingManager.fld("CallImpl.apiProxy"));
                if (apiProxy == null) {
                    HookManager.log("[ミュート] API proxy null");
                    return;
                }

                String targetUuid = resolveUuid(myCall, target);
                if (targetUuid == null || targetUuid.isEmpty()) {
                    HookManager.log("[ミュート] UUID不明");
                    return;
                }

                String apiAction = mute ? "mute" : "unmute";
                java.lang.reflect.InvocationHandler handler =
                        java.lang.reflect.Proxy.getInvocationHandler(apiProxy);

                Class<?> apiInterface = Class.forName(
                        MappingManager.cls("CallApi"),
                        false, apiProxy.getClass().getClassLoader());
                Method oMethod = null;
                String muteMethodName = MappingManager.mtd("CallApi.requestMuteSignature");
                for (Method m : apiInterface.getDeclaredMethods()) {
                    if (m.getName().equals(muteMethodName)
                            && m.getParameterCount() == 3) {
                        oMethod = m;
                        break;
                    }
                }
                if (oMethod == null) {
                    HookManager.log("[ミュート] muteSignatureMethod未発見");
                    return;
                }

                Object result = handler.invoke(apiProxy, oMethod,
                        new Object[]{confId, targetUuid, apiAction});
                if (result == null) {
                    HookManager.log("[ミュート] API結果null");
                    return;
                }

                HookManager.log("[ミュート] 署名取得成功 (" + apiAction + ")");

                final String finalTargetUuid = targetUuid;
                final String agoraCmd = mute ? "muteAudio" : "liftAudioMute";

                java.lang.reflect.InvocationHandler observerHandler = (proxy, method, args) -> {
                    String methodName = method.getName();
                    if (args != null && args.length > 0 && args[0] != null
                            && !"onError".equals(methodName)
                            && !"toString".equals(methodName)
                            && !"hashCode".equals(methodName)
                            && !"equals".equals(methodName)) {
                        Object response = args[0];
                        try {
                            Object sigPayload = extractSignaturePayload(response);
                            if (sigPayload == null) {
                                HookManager.log("[ミュート] SignaturePayload null");
                                return null;
                            }

                            String signature = null, timestamp = null;
                            for (Field f : sigPayload.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                String name = f.getName();
                                Object val  = f.get(sigPayload);
                                if ("signature".equals(name) && val != null)
                                    signature = val.toString();
                                if ("timestamp".equals(name) && val != null)
                                    timestamp = val.toString();
                            }

                            if (signature == null || timestamp == null) {
                                HookManager.log("[ミュート] sig/ts null");
                                return null;
                            }

                            String cmd = agoraCmd + " " + finalTargetUuid
                                    + " " + timestamp + " " + signature;
                            HookManager.log("[ミュート] cmd: "
                                    + cmd.substring(0, Math.min(50, cmd.length())) + "...");

                            if (!ensureAgoraCached()) {
                                HookManager.log("[ミュート] agora.a未取得");
                                return null;
                            }
                            Object agoraA = StateHolder.getAgoraA();
                            if (agoraA == null) {
                                HookManager.log("[ミュート] agora.a GC済");
                                return null;
                            }
                            XposedHelpers.callMethod(agoraA,
                                    MappingManager.mtd("AgoraWrapper.sendCommand"), cmd);

                            target.muted = mute;
                            HookManager.log("[ミュート] "
                                    + (mute ? "MUTE" : "UNMUTE") + " 成功: " + target.name);

                            Activity act = StateHolder.getLastActivity();
                            if (act != null) act.runOnUiThread(CallManager::notifyMuteUIUpdate);

                        } catch (Throwable t) {
                            HookManager.log("[ミュート] onSuccess処理失敗: " + t.getMessage());
                        }
                    } else if ("onError".equals(methodName) && args != null
                            && args.length > 0) {
                        HookManager.log("[ミュート] onError: " + args[0]);
                    }
                    return null;
                };

                autoSubscribe(result, observerHandler, "ミュート");

            } catch (Throwable t) {
                HookManager.log("[ミュート] " + (mute ? "MUTE" : "UNMUTE")
                        + "失敗: " + t.getMessage());
            }
        });
    }

    // ===== キック実行 =====

    public static void executeKick(final MuteTarget target, final boolean isPermanent) {
        Object cvm = StateHolder.getCachedCallViewModel();
        if (cvm == null) {
            HookManager.log("[キック] CallViewModel未取得");
            return;
        }
        executor.execute(() -> {
            try {
                Object cvmInner = StateHolder.getCachedCallViewModel();
                if (cvmInner == null) {
                    HookManager.log("[キック] CallViewModel GC済");
                    return;
                }
                Object myCall = XposedHelpers.getObjectField(cvmInner, "myCall");
                if (myCall == null) {
                    HookManager.log("[キック] myCall null");
                    return;
                }

                Object conf = XposedHelpers.getObjectField(myCall,
                        MappingManager.fld("CallImpl.conferenceCall"));
                if (conf == null) {
                    HookManager.log("[キック] ConferenceCall null");
                    return;
                }

                String confStr = conf.toString();
                long confId    = 0;
                String idStr   = extractField(confStr, "id");
                if (idStr != null) {
                    try { confId = Long.parseLong(idStr); }
                    catch (NumberFormatException ignored) {}
                }
                if (confId == 0) {
                    HookManager.log("[キック] callId取得失敗");
                    return;
                }

                Object apiProxy = XposedHelpers.getObjectField(myCall,
                        MappingManager.fld("CallImpl.apiProxy"));
                if (apiProxy == null) {
                    HookManager.log("[キック] API proxy null");
                    return;
                }

                String targetUuid = resolveUuid(myCall, target);
                if (targetUuid == null || targetUuid.isEmpty()) {
                    HookManager.log("[キック] UUID不明");
                    return;
                }

                final String finalTargetUuid = targetUuid;
                java.lang.reflect.InvocationHandler handler =
                        java.lang.reflect.Proxy.getInvocationHandler(apiProxy);

                Class<?> apiInterface = Class.forName(
                        MappingManager.cls("CallApi"),
                        false, apiProxy.getClass().getClassLoader());
                Method wMethod = null;
                String kickMethodName = MappingManager.mtd("CallApi.requestKickSignature");
                for (Method m : apiInterface.getDeclaredMethods()) {
                    if (m.getName().equals(kickMethodName)
                            && m.getParameterCount() == 3) {
                        wMethod = m;
                        break;
                    }
                }
                if (wMethod == null) {
                    HookManager.log("[キック] kickSignatureMethod未発見");
                    return;
                }

                Object result = handler.invoke(apiProxy, wMethod,
                        new Object[]{confId, finalTargetUuid, isPermanent});
                if (result == null) {
                    HookManager.log("[キック] API結果null");
                    return;
                }

                HookManager.log("[キック] 署名取得中... ("
                        + (isPermanent ? "永久" : "一時") + ") target=" + target.name);

                java.lang.reflect.InvocationHandler observerHandler = (proxy, method, args) -> {
                    String methodName = method.getName();
                    if (args != null && args.length > 0 && args[0] != null
                            && !"onError".equals(methodName)
                            && !"toString".equals(methodName)
                            && !"hashCode".equals(methodName)
                            && !"equals".equals(methodName)) {
                        Object response = args[0];
                        try {
                            Object sigPayload = extractSignaturePayload(response);
                            if (sigPayload == null) {
                                HookManager.log("[キック] SignaturePayload null");
                                return null;
                            }

                            String signature = null, timestamp = null;
                            for (Field f : sigPayload.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                String name = f.getName();
                                Object val  = f.get(sigPayload);
                                if ("signature".equals(name) && val != null)
                                    signature = val.toString();
                                if ("timestamp".equals(name) && val != null)
                                    timestamp = val.toString();
                            }

                            if (signature == null || timestamp == null) {
                                HookManager.log("[キック] sig/ts null");
                                return null;
                            }

                            String cmd = "kick " + finalTargetUuid
                                    + " " + timestamp + " " + signature;
                            HookManager.log("[キック] cmd: "
                                    + cmd.substring(0, Math.min(50, cmd.length())) + "...");

                            if (!ensureAgoraCached()) {
                                HookManager.log("[キック] agora.a未取得");
                                return null;
                            }
                            Object agoraA = StateHolder.getAgoraA();
                            if (agoraA == null) {
                                HookManager.log("[キック] agora.a GC済");
                                return null;
                            }
                            XposedHelpers.callMethod(agoraA,
                                    MappingManager.mtd("AgoraWrapper.sendCommand"), cmd);

                            target.kicked = true;
                            HookManager.log("[キック] "
                                    + (isPermanent ? "永久キック" : "一時キック")
                                    + " 成功: " + target.name);

                            Activity act = StateHolder.getLastActivity();
                            if (act != null) act.runOnUiThread(CallManager::notifyMuteUIUpdate);

                        } catch (Throwable t) {
                            HookManager.log("[キック] onSuccess処理失敗: " + t.getMessage());
                        }
                    } else if ("onError".equals(methodName) && args != null
                            && args.length > 0) {
                        HookManager.log("[キック] onError: " + args[0]);
                    }
                    return null;
                };

                autoSubscribe(result, observerHandler, "キック");

            } catch (Throwable t) {
                HookManager.log("[キック] " + (isPermanent ? "永久" : "一時")
                        + "キック失敗: " + t.getMessage());
            }
        });
    }

    // ===== Call 再参加 =====

    public static void saveCurrentCall() {
        if (StateHolder.tmpChannel == null || StateHolder.tmpChannel.isEmpty()) {
            HookManager.log("[Call] 通話情報なし - 通話に参加してください");
            return;
        }
        String timeStr = new java.text.SimpleDateFormat(
                "HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        synchronized (StateHolder.callHistory) {
            for (int i = 0; i < StateHolder.callHistory.size(); i++) {
                if (StateHolder.callHistory.get(i).callId == StateHolder.tmpCallId) {
                    StateHolder.callHistory.set(i, new CallHistoryEntry(
                            StateHolder.tmpCallId, StateHolder.tmpPostId,
                            StateHolder.tmpHostId,
                            StateHolder.tmpHostNick.isEmpty()
                                    ? "不明" : StateHolder.tmpHostNick,
                            StateHolder.tmpToken, StateHolder.tmpChannel,
                            StateHolder.tmpAccount, timeStr));
                    HookManager.log("[Call] 更新: #" + (i + 1)
                            + " " + StateHolder.tmpHostNick);
                    return;
                }
            }
            if (StateHolder.callHistory.size() >= StateHolder.CALL_HISTORY_MAX) {
                StateHolder.callHistory.remove(0);
            }
            StateHolder.callHistory.add(new CallHistoryEntry(
                    StateHolder.tmpCallId, StateHolder.tmpPostId,
                    StateHolder.tmpHostId,
                    StateHolder.tmpHostNick.isEmpty()
                            ? "不明" : StateHolder.tmpHostNick,
                    StateHolder.tmpToken, StateHolder.tmpChannel,
                    StateHolder.tmpAccount, timeStr));
            HookManager.log("[Call] 保存: callId=" + StateHolder.tmpCallId
                    + " host=" + StateHolder.tmpHostNick);
        }
    }

    public static void doCallRejoin(CallHistoryEntry entry) {
        if (entry.postId == 0) {
            HookManager.log("[Call] postId なし - 再参加不可");
            return;
        }
        executor.execute(() -> {
            try {
                Object vm = StateHolder.retainedGcvm;
                if (vm == null) {
                    Object cvm = StateHolder.getCachedCallViewModel();
                    if (cvm != null) {
                        for (Field f : cvm.getClass().getDeclaredFields()) {
                            f.setAccessible(true);
                            try {
                                Object val = f.get(cvm);
                                if (val != null && val.getClass().getName()
                                        .contains("GlobalCallViewModel")) {
                                    vm = val;
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
                if (vm == null) {
                    HookManager.log("[Call] GlobalCallViewModel 未取得");
                    return;
                }

                HookManager.log("[Call] rejoin postId=" + entry.postId
                        + " host=" + entry.hostNick);
                Method joinMethod = null;
                for (Method m : vm.getClass().getDeclaredMethods()) {
                    if ("joinPostGroupCall".equals(m.getName())
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == long.class) {
                        joinMethod = m;
                        break;
                    }
                }
                if (joinMethod != null) {
                    joinMethod.setAccessible(true);
                    joinMethod.invoke(vm, entry.postId);
                    HookManager.log("[Call] rejoin 成功!");
                } else {
                    HookManager.log("[Call] joinPostGroupCall(long) メソッド未発見");
                }
            } catch (Throwable t) {
                HookManager.log("[Call] rejoin 失敗: " + t.getMessage());
            }
        });
    }

    public static void doCallSdkRejoin(CallHistoryEntry entry) {
        if (entry.token == null || entry.token.isEmpty()) {
            HookManager.log("[Call] トークンなし");
            return;
        }
        if (!ensureAgoraCached()) {
            HookManager.log("[Call] agora.a 未取得");
            return;
        }

        StateHolder.callBlockLeave  = true;
        StateHolder.callIsRejoining = true;

        // 修正: Timer → ScheduledExecutorService で安全タイマー管理
        java.util.concurrent.ScheduledExecutorService safeTimer =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        safeTimer.schedule(() -> {
            if (StateHolder.callBlockLeave || StateHolder.callIsRejoining) {
                HookManager.log("[Call] 安全タイマー: フラグ強制リセット");
                StateHolder.callBlockLeave  = false;
                StateHolder.callIsRejoining = false;
            }
            safeTimer.shutdown();
        }, 30, java.util.concurrent.TimeUnit.SECONDS);

        HookManager.log("[Call] SDK rejoin callId=" + entry.callId
                + " host=" + entry.hostNick);

        executor.execute(() -> {
            try {
                Object agoraAObj = StateHolder.getAgoraA();
                if (agoraAObj == null) {
                    HookManager.log("[Call] agora.a GC済");
                    return;
                }

                boolean rtcFound = false;
                for (Field f : agoraAObj.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(agoraAObj);
                        if (val != null && val.getClass().getName().contains("RtcEngine")) {
                            try {
                                Class<?> rtcEngineClass = StateHolder.appClassLoader
                                        .loadClass(MappingManager.cls("RtcEngine"));
                                Method destroyM = rtcEngineClass.getMethod("destroy");
                                destroyM.invoke(null);
                            } catch (Throwable ignored) {}
                            f.set(agoraAObj, null);
                            HookManager.log("[Call] RtcEngine 破棄完了");
                            rtcFound = true;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }

                if (!rtcFound) {
                    HookManager.log("[Call] RtcEngine フィールド未検出");
                    StateHolder.callBlockLeave  = false;
                    StateHolder.callIsRejoining = false;
                    safeTimer.shutdown();
                    return;
                }

                Thread.sleep((long) (StateHolder.callRejoinDelay * 1000));

                Activity act = StateHolder.getLastActivity();
                if (act != null) {
                    act.runOnUiThread(() -> {
                        try {
                            Object aa = StateHolder.getAgoraA();
                            if (aa == null) {
                                HookManager.log("[Call] agora.a GC済（UI）");
                                return;
                            }
                            XposedHelpers.callMethod(aa,
                                    MappingManager.mtd("AgoraWrapper.joinChannel"),
                                    entry.token, entry.channel);
                            HookManager.log("[Call] SDK再接続成功!");

                            // 修正: Timer → ScheduledExecutorService
                            java.util.concurrent.ScheduledExecutorService resetTimer =
                                    java.util.concurrent.Executors
                                            .newSingleThreadScheduledExecutor();
                            resetTimer.schedule(() -> {
                                StateHolder.callBlockLeave  = false;
                                StateHolder.callIsRejoining = false;
                                resetTimer.shutdown();
                                safeTimer.shutdown();
                            }, 5, java.util.concurrent.TimeUnit.SECONDS);

                        } catch (Throwable t) {
                            HookManager.log("[Call] SDK再接続失敗: " + t.getMessage());
                            StateHolder.callBlockLeave  = false;
                            StateHolder.callIsRejoining = false;
                            safeTimer.shutdown();
                        }
                    });
                } else {
                    StateHolder.callBlockLeave  = false;
                    StateHolder.callIsRejoining = false;
                    safeTimer.shutdown();
                }
            } catch (Throwable t) {
                HookManager.log("[Call] SDK rejoin エラー: " + t.getMessage());
                StateHolder.callBlockLeave  = false;
                StateHolder.callIsRejoining = false;
                safeTimer.shutdown();
            }
        });
    }

    public static void doResetVolume() {
        if (StateHolder.tmpChannel == null || StateHolder.tmpChannel.isEmpty()) {
            HookManager.log("[Call] 現在の通話情報なし");
            return;
        }
        CallHistoryEntry current = new CallHistoryEntry(
                StateHolder.tmpCallId, StateHolder.tmpPostId,
                StateHolder.tmpHostId, StateHolder.tmpHostNick,
                StateHolder.tmpToken, StateHolder.tmpChannel,
                StateHolder.tmpAccount, "now");
        doCallSdkRejoin(current);
    }

    // ===== ミュート状態リセット =====

    public static void resetMuteState() {
        if (StateHolder.muteResetDone) return;
        StateHolder.muteResetDone = true;
        synchronized (StateHolder.muteTargets) {
            StateHolder.muteTargets.clear();
        }
        StateHolder.currentCallId   = 0;
        StateHolder.myCallUserUuid  = "";
        StateHolder.agoraChannel    = "";
        StateHolder.inCall          = false;
        HookManager.log("[ミュート] リセット完了");
        notifyMuteUIUpdate();
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> StateHolder.muteResetDone = false, 1000);
    }

    // ===== UI更新通知 =====

    // MutePage から設定される更新コールバック
    private static Runnable muteUIUpdateCallback = null;

    public static void setMuteUIUpdateCallback(Runnable callback) {
        muteUIUpdateCallback = callback;
    }

    public static void notifyMuteUIUpdate() {
        if (muteUIUpdateCallback != null) {
            muteUIUpdateCallback.run();
        }
    }

    // ===== ユーティリティ =====

    public static boolean ensureAgoraCached() {
        synchronized (StateHolder.agoraLock) {
            if (StateHolder.cachedAgoraARef.get() != null) return true;
            Object cvm = StateHolder.getCachedCallViewModel();
            if (cvm == null) return false;
            try {
                Object myCall = XposedHelpers.getObjectField(cvm, "myCall");
                if (myCall == null) return false;
                Object agoraC = XposedHelpers.getObjectField(myCall,
                        MappingManager.fld("CallImpl.agoraClient"));
                if (agoraC == null) return false;
                Object agoraA = XposedHelpers.getObjectField(agoraC,
                        MappingManager.fld("AgoraClient.engine"));
                if (agoraA != null) {
                    StateHolder.cachedAgoraARef =
                            new java.lang.ref.WeakReference<>(agoraA);
                    HookManager.log("[Agora] agora.a インスタンス取得成功");
                    return true;
                }
            } catch (Throwable t) {
                HookManager.log("[Agora] キャッシュ失敗: " + t.getMessage());
            }
            return false;
        }
    }

    public static String extractField(String toString, String key) {
        String search = key + "=";
        int idx = toString.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end   = toString.indexOf(",", start);
        if (end < 0) end = toString.indexOf(")", start);
        if (end < 0) end = toString.length();
        return toString.substring(start, end).trim();
    }

    private static String resolveUuid(Object myCall, MuteTarget target) {
        String targetUuid = target.callUserId;
        if (targetUuid != null && !targetUuid.isEmpty()) return targetUuid;
        try {
            Field jField = myCall.getClass().getDeclaredField(
                    MappingManager.fld("CallImpl.uuidMap"));
            jField.setAccessible(true);
            Object jObj = jField.get(myCall);
            if (jObj instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) jObj).entrySet()) {
                    long val;
                    Object v = entry.getValue();
                    if (v instanceof Long) val = (Long) v;
                    else if (v instanceof Integer) val = ((Integer) v).longValue();
                    else val = Long.parseLong(v.toString());
                    if (String.valueOf(val).equals(target.appUserId)) {
                        return entry.getKey().toString();
                    }
                }
            }
        } catch (Throwable t) {
            HookManager.log("[UUID] uuidMap失敗: " + t.getMessage());
        }
        return null;
    }

    private static Object extractSignaturePayload(Object response) throws Exception {
        for (Field f : response.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object val = f.get(response);
            if (val != null && val.getClass().getName().contains("Signature")) {
                return val;
            }
        }
        for (Field f : response.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            String name = f.getName();
            if ("signature".equals(name) || "timestamp".equals(name)) {
                return response;
            }
        }
        return null;
    }

    private static boolean autoSubscribe(Object observable,
                                         java.lang.reflect.InvocationHandler handler, String tag) {
        if (observable == null) return false;
        for (Method sm : observable.getClass().getMethods()) {
            if (sm.getParameterCount() == 1) {
                Class<?> pType = sm.getParameterTypes()[0];
                if (pType.isInterface() && !pType.getName().startsWith("java.")) {
                    try {
                        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                                pType.getClassLoader(),
                                new Class<?>[]{pType}, handler);
                        sm.setAccessible(true);
                        sm.invoke(observable, proxy);
                        HookManager.log("[" + tag + "] subscribe: "
                                + sm.getName() + "(" + pType.getSimpleName() + ")");
                        return true;
                    } catch (Throwable t) {
                        HookManager.log("[" + tag + "] " + sm.getName()
                                + " 失敗: " + t.getMessage());
                    }
                }
            }
        }
        for (Method sm : observable.getClass().getMethods()) {
            if ("subscribe".equals(sm.getName()) && sm.getParameterCount() == 0) {
                try {
                    sm.setAccessible(true);
                    sm.invoke(observable);
                    HookManager.log("[" + tag + "] subscribe() 成功");
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        HookManager.log("[" + tag + "] subscribe全失敗 class="
                + observable.getClass().getName());
        return false;
    }

    public static void sendForceUnmute() {
        if (!ensureAgoraCached()) {
            HookManager.log("[Unmute] agora.a未取得");
            return;
        }
        Object agoraA = StateHolder.getAgoraA();
        if (agoraA == null) {
            HookManager.log("[Unmute] agora.a GC済");
            return;
        }
        try {
            XposedHelpers.callMethod(agoraA,
                    MappingManager.mtd("AgoraWrapper.sendCommand"),
                    "requestLiftAudioMute");
            HookManager.log("[Unmute] requestLiftAudioMute 送信成功");
        } catch (Throwable t) {
            HookManager.log("[Unmute] 送信失敗: " + t.getMessage());
        }
    }
}
