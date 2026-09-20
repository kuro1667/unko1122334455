/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.core;

import android.app.Activity;
import android.os.Bundle;
import com.kyomu.tools.login.LoginHookManager;
import com.kyomu.tools.LicenseManager;
import com.kyomu.tools.MappingManager;
import com.kyomu.tools.call.CallManager;
import com.kyomu.tools.persist.PersistManager;
import com.kyomu.tools.ui.MenuUI;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class HookManager {

    private HookManager() {}

    // ===== メインフック処理 =====

    public static void handlePackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!BrandGuard.isValid()) {
            log("[KyomuTools] BrandGuard検証失敗 - 全機能無効化");
            return;
        }
        ClassLoader cl = lpparam.classLoader;
        StateHolder.appClassLoader = cl;

        try {
            new java.io.File("/data/data/jp.nanameue.yay/cache/kyomutool_log.txt").delete();
        } catch (Throwable ignored) {}

        log("[kyomu] Yay検出! フック開始");

        hookPlayIntegrity(cl);

        if (!MappingManager.load()) {
            log("[kyomu] マッピング読み込み失敗 - 未対応バージョン");
            hookActivityForUnsupported(cl);
            return;
        }

        // マッピング全エントリの存在を検証（警告のみ・フック処理は継続）
        MappingManager.validate(cl);
        for (String vlog : MappingManager.drainPendingLogs()) log(vlog);

        hookNetworkSecurity();
        hookVip(cl);
        hookCallViewModel(cl);
        hookCallViewModelLeave(cl);
        hookRtcEngine(cl);
        hookAgoraDelegate(cl);
        hookConnectionState(cl);
        hookAgoraWrapper(cl);
        hookCallImpl(cl);
        hookGlobalCallViewModel(cl);
        hookRtcEngineAccount(cl);
        hookActivity(cl);
        LoginHookManager.hook(cl);
        hookScreenShare(cl);

        LicenseManager.isLicensed           = true;
        LicenseManager.licenseChecked       = true;
        LicenseManager.licenseServerReached = true;
        LicenseManager.licenseLastCheck     = System.currentTimeMillis();

        try {
            XposedHelpers.findAndHookMethod(
                    android.app.AlertDialog.Builder.class,
                    "show",
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                            for (StackTraceElement e : stack) {
                                if (e.getClassName().contains("onesignal")) {
                                    log("[BLOCK] OneSignal 通知ダイアログ ブロック");
                                    param.setResult(null);
                                    return;
                                }
                            }
                        }
                    });
        } catch (Throwable ignored) {}

        log("[kyomu] 全フック完了 (mapping: " + MappingManager.getMappingVersion() + ")");
    }

    // ===== Play Integrity バイパス =====

    private static void hookPlayIntegrity(ClassLoader cl) {
        try {
            Class<?> licenseClientClass = cl.loadClass("com.pairip.licensecheck.LicenseClient");

            hookMethod(licenseClientClass, "checkLicense",
                    new Class<?>[]{android.content.Context.class},
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            log("[PI] checkLicense ブロック");
                            param.setResult(null);
                        }
                    });

            hookMethod(licenseClientClass, "initializeLicenseCheck",
                    new Class<?>[]{},
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            log("[PI] initializeLicenseCheck ブロック");
                            param.setResult(null);
                        }
                    });

            hookMethod(licenseClientClass, "processResponse",
                    new Class<?>[]{int.class, Bundle.class},
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            int original = (int) param.args[0];
                            log("[PI] processResponse: " + original + " → 0 (LICENSED)");
                            param.args[0] = 0;
                        }
                    });

            for (Method m : licenseClientClass.getDeclaredMethods()) {
                if ("handleError".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            log("[PI] handleError ブロック");
                            param.setResult(null);
                        }
                    });
                }
                if ("startErrorDialogActivity".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            log("[PI] startErrorDialogActivity ブロック");
                            param.setResult(null);
                        }
                    });
                }
                if ("performLocalInstallerCheck".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            log("[PI] installerCheck → true");
                            param.setResult(true);
                        }
                    });
                }
                if ("connectToLicensingService".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            log("[PI] connectToLicensingService ブロック");
                            param.setResult(null);
                        }
                    });
                }
                if ("startPaywallActivity".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            log("[PI] startPaywallActivity ブロック");
                            param.setResult(null);
                        }
                    });
                }
            }

            try {
                Class<?> licenseActivity = cl.loadClass("com.pairip.licensecheck.LicenseActivity");
                hookMethod(licenseActivity, "onCreate",
                        new Class<?>[]{Bundle.class},
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                Activity act = (Activity) param.thisObject;
                                act.finish();
                                log("[PI] LicenseActivity 即終了");
                            }
                        });
            } catch (Throwable ignored) {}

            log("[OK] Play Integrity バイパス完了");
        } catch (Throwable t) {
            log("[ERR] Play Integrity バイパス失敗: " + t.getMessage());
        }
    }

    // ===== ネットワークセキュリティ =====

    private static void hookNetworkSecurity() {
        try {
            XposedHelpers.findAndHookMethod(
                    android.security.NetworkSecurityPolicy.class,
                    "isCleartextTrafficPermitted",
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                        }
                    });
        } catch (Throwable ignored) {}
        try {
            XposedHelpers.findAndHookMethod(
                    android.security.NetworkSecurityPolicy.class,
                    "isCleartextTrafficPermitted",
                    String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                        }
                    });
        } catch (Throwable ignored) {}
    }

    // ===== VIP偽装 =====

    private static void hookVip(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("AccountManager"), cl,
                    MappingManager.mtd("AccountManager.getMyUser"),
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getResult() != null) {
                                StateHolder.cachedMyRealmUserRef =
                                        new WeakReference<>(param.getResult());
                            }
                        }
                    });
            log("[VIP] AccountManager フック成功");
        } catch (Throwable t) {
            log("[VIP] AccountManager hook失敗: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RealmUser"), cl, "getVip",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (StateHolder.adBlock && isMyRealmUser(param.thisObject))
                                param.setResult(true);
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RealmUser"), cl, "getVipUntilSeconds",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (StateHolder.adBlock && isMyRealmUser(param.thisObject))
                                param.setResult(1900000000L);
                        }
                    });

            try {
                XposedHelpers.findAndHookMethod(
                        MappingManager.cls("RealmUser"), cl, "realmGet$vip",
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if (StateHolder.adBlock && isMyRealmUser(param.thisObject))
                                    param.setResult(true);
                            }
                        });
            } catch (Throwable ignored) {}

            try {
                XposedHelpers.findAndHookMethod(
                        MappingManager.cls("RealmUser"), cl, "realmGet$vipUntilSeconds",
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if (StateHolder.adBlock && isMyRealmUser(param.thisObject))
                                    param.setResult(1900000000L);
                            }
                        });
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            log("[VIP] RealmUser hook失敗: " + t.getMessage());
        }
    }

    // ===== CallViewModel キャッシュ =====

    private static void hookCallViewModel(ClassLoader cl) {
        try {
            Class<?> cvmClass = cl.loadClass(MappingManager.cls("CallViewModel"));
            for (Constructor<?> ctor : cvmClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        StateHolder.setCachedCallViewModel(param.thisObject);
                        StateHolder.setAgoraA(null);
                        log("[kyomu] CallViewModel キャッシュ完了");

                        // 初期化クラスを自動検出（未検出時のみ）
                        if (StateHolder.detectedCallInitializerClass == null) {
                            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                            for (StackTraceElement e : stack) {
                                String cls = e.getClassName();
                                if (cls.startsWith("jp.nanameue.yay.call.")
                                        && !cls.contains("CallViewModel")
                                        && !cls.contains("GlobalCallViewModel")
                                        && !cls.contains("CallImpl")) {
                                    StateHolder.detectedCallInitializerClass = cls;
                                    log("[kyomu] CallInitializer 自動検出: " + cls);
                                    break;
                                }
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            log("[kyomu] CallViewModel hook失敗: " + t.getMessage());
        }
    }

    // ===== CallViewModel leaveCall / onCallEnd ブロック =====

    private static void hookCallViewModelLeave(ClassLoader cl) {
        try {
            try {
                XposedHelpers.findAndHookMethod(
                        MappingManager.cls("CallViewModel"), cl, "leaveCall",
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                if (StateHolder.callBlockLeave) {
                                    log("[BLOCK] CallViewModel.leaveCall() ブロック");
                                    param.setResult(null);
                                }
                            }
                        });
                log("[OK] CallViewModel.leaveCall() フック成功");
            } catch (Throwable t) {
                log("[ERR] CallViewModel.leaveCall() フック失敗: " + t.getMessage());
            }

            try {
                XposedHelpers.findAndHookMethod(
                        MappingManager.cls("CallViewModel"), cl, "onCallEnd",
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                if (StateHolder.callBlockLeave) {
                                    log("[BLOCK] CallViewModel.onCallEnd() ブロック");
                                    param.setResult(null);
                                }
                            }
                        });
                log("[OK] CallViewModel.onCallEnd() フック成功");
            } catch (Throwable t) {
                log("[ERR] CallViewModel.onCallEnd() フック失敗: " + t.getMessage());
            }
        } catch (Throwable t) {
            log("[ERR] CallViewModel leaveCall/onCallEnd フック失敗: " + t.getMessage());
        }
    }

    // ===== RtcEngine フック =====

    private static void hookRtcEngine(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RtcEngineImpl"), cl,
                    "muteLocalAudioStream", boolean.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            boolean mute = (boolean) param.args[0];
                            if (!mute || !StateHolder.appMuteBlock) return;
                            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                            for (StackTraceElement e : stack) {
                                // ユーザー自身のミュート操作は通す
                                if (e.getClassName().contains("CallViewModel")
                                        && e.getMethodName().contains("onToggleMicrophoneClick"))
                                    return;
                                // AgoraWrapper経由のミュートは通す
                                if (e.getClassName().equals(MappingManager.cls("AgoraWrapper"))
                                        && (e.getMethodName().equals(MappingManager.mtd("AgoraWrapper.muteAudio"))
                                        || e.getMethodName().equals(MappingManager.mtd("AgoraWrapper.muteTargets"))))
                                    return;
                                // 通話参加時の初期化呼び出しは通す
                                String initCls = MappingManager.cls("CallInitializer");
                                if (initCls == null || initCls.isEmpty()) {
                                    initCls = StateHolder.detectedCallInitializerClass;
                                }
                                if (initCls != null && e.getClassName().equals(initCls))
                                    return;
                            }
                            log("[BLOCK] muteLocalAudioStream(true) ブロック");
                            param.setResult(0);
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RtcEngineImpl"), cl,
                    "adjustRecordingSignalVolume", int.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            int vol = (int) param.args[0];
                            if (vol == 0 && StateHolder.sdkMute) {
                                log("[BLOCK] adjustRecordingSignalVolume(0) → 100");
                                param.args[0] = 100;
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RtcEngineImpl"), cl,
                    "enableLocalAudio", boolean.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            boolean enable = (boolean) param.args[0];
                            if (!enable && StateHolder.sdkMute) {
                                log("[BLOCK] enableLocalAudio(false) ブロック");
                                param.args[0] = true;
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RtcEngineImpl"), cl,
                    "muteAllRemoteAudioStreams", boolean.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            boolean mute = (boolean) param.args[0];
                            if (mute && StateHolder.sdkMute) {
                                log("[BLOCK] muteAllRemoteAudioStreams(true) ブロック");
                                param.setResult(0);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RtcEngineImpl"), cl, "leaveChannel",
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                            for (StackTraceElement e : stack) {
                                if (e.getClassName().contains("CallViewModel")
                                        && e.getMethodName().contains("onHangClick")) {
                                    CallManager.resetMuteState(); return;
                                }
                                if (e.getClassName().contains("GlobalCallViewModel")
                                        && e.getMethodName().contains("trySwitchCall")) {
                                    CallManager.resetMuteState(); return;
                                }
                            }
                            if (StateHolder.callIsRejoining) return;
                            if (StateHolder.callBlockLeave) {
                                log("[BLOCK] leaveChannel() ブロック（Call再接続中）");
                                param.setResult(0); return;
                            }
                            if (StateHolder.kickBlock) {
                                log("[BLOCK] leaveChannel() ブロック（キック）");
                                param.setResult(0);
                            }
                        }
                    });

            try {
                Class<?> leaveOpts = cl.loadClass(MappingManager.cls("LeaveChannelOptions"));
                XposedHelpers.findAndHookMethod(
                        MappingManager.cls("RtcEngineImpl"), cl,
                        "leaveChannel", leaveOpts,
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                                for (StackTraceElement e : stack) {
                                    if (e.getClassName().contains("CallViewModel")
                                            && e.getMethodName().contains("onHangClick")) {
                                        CallManager.resetMuteState(); return;
                                    }
                                    if (e.getClassName().contains("GlobalCallViewModel")
                                            && e.getMethodName().contains("trySwitchCall")) {
                                        CallManager.resetMuteState(); return;
                                    }
                                }
                                if (StateHolder.callIsRejoining) return;
                                if (StateHolder.callBlockLeave) {
                                    log("[BLOCK] leaveChannel(opts) ブロック");
                                    param.setResult(0); return;
                                }
                                if (StateHolder.kickBlock) {
                                    log("[BLOCK] leaveChannel(opts) ブロック（キック）");
                                    param.setResult(0);
                                }
                            }
                        });
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            log("[ERR] RtcEngine フック失敗: " + t.getMessage());
        }
    }

    // ===== AgoraDelegate =====

    private static void hookAgoraDelegate(ClassLoader cl) {
        try {
            Class<?> agoraDClass = cl.loadClass(MappingManager.cls("AgoraDelegate"));
            boolean hooked = false;
            for (Method m : agoraDClass.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 4
                        && params[0].isEnum()
                        && params[1] == String.class
                        && params[2] == String.class
                        && params[3] == String.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            Object action = param.args[0];
                            if (action == null) return;
                            int ordinal = (int) XposedHelpers.callMethod(action, "ordinal");
                            // appMuteBlock に関わらず全アクションをログに出す
                            log("[AgoraDelegate] アクション受信 ordinal=" + ordinal
                                    + " block=" + StateHolder.appMuteBlock);
                            if (!StateHolder.appMuteBlock) return;
                            if (ordinal == 0 || ordinal == 1) {
                                log("[BLOCK] agora.d.c() 完全ブロック ordinal=" + ordinal);
                                param.setResult(null);
                            }
                        }
                    });
                    hooked = true;
                    log("[OK] agora.d.c() フック成功: " + m.getName());
                    break;
                }
            }
            if (!hooked) log("[ERR] agora.d.c() メソッド未発見");
        } catch (Throwable t) {
            log("[ERR] agora.d.c() フック失敗: " + t.getMessage());
        }
    }

    // ===== onConnectionStateChanged =====

    private static void hookConnectionState(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RtcEventHandler"), cl,
                    "onConnectionStateChanged", int.class, int.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            int state  = (int) param.args[0];
                            int reason = (int) param.args[1];
                            // 全ての接続状態変化をログに出す
                            log("[ConnectionState] state=" + state + " reason=" + reason
                                    + " blockLeave=" + StateHolder.callBlockLeave
                                    + " serverKick=" + StateHolder.serverKick);
                            if (state == 1 && (StateHolder.callBlockLeave || StateHolder.serverKick)) {
                                log("[BLOCK] DISCONNECTED ブロック");
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable ignored) {}
    }

    // ===== AgoraWrapper =====

    private static void hookAgoraWrapper(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("AgoraWrapper"), cl,
                    MappingManager.mtd("AgoraWrapper.joinChannel"),
                    String.class, String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            StateHolder.tmpToken   = (String) param.args[0];
                            StateHolder.tmpChannel = (String) param.args[1];
                            StateHolder.setAgoraA(param.thisObject);
                            log("[Call] channel=" + StateHolder.tmpChannel);
                        }
                    });
            log("[OK] AgoraWrapper.joinChannel() フック成功");

            Class<?> agoraAClass = cl.loadClass(MappingManager.cls("AgoraWrapper"));
            boolean foundSendCmd = false;
            for (Method m : agoraAClass.getDeclaredMethods()) {
                if (m.getName().equals(MappingManager.mtd("AgoraWrapper.sendCommand"))
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    foundSendCmd = true;
                    break;
                }
            }
            log(foundSendCmd ? "[OK] sendCommand 確認OK" : "[WARN] sendCommand 未発見");

            // 受信コマンドの監視（キック・ミュート検知）
            try {
                String recvMethod = MappingManager.mtd("AgoraWrapper.onMessageReceived");
                XposedHelpers.findAndHookMethod(
                        MappingManager.cls("AgoraWrapper"), cl, recvMethod,
                        String.class,
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                String msg = (String) param.args[0];
                                if (msg == null) return;
                                if (msg.contains("kick")) {
                                    log("[受信] キックコマンド検知: " + msg);
                                    if (StateHolder.kickBlock) {
                                        log("[BLOCK] キックコマンドブロック");
                                        param.setResult(null);
                                    }
                                } else if (msg.contains("muteAudio")) {
                                    log("[受信] ミュートコマンド検知: " + msg);
                                } else if (msg.contains("liftAudioMute")) {
                                    log("[受信] ミュート解除コマンド検知: " + msg);
                                } else {
                                    log("[受信] コマンド: " + msg);
                                }
                            }
                        });
                log("[OK] AgoraWrapper 受信コマンド監視フック成功");
            } catch (Throwable t) {
                log("[WARN] AgoraWrapper 受信コマンド監視フック失敗: " + t.getMessage());
            }

        } catch (Throwable t) {
            log("[ERR] AgoraWrapper.joinChannel フック失敗: " + t.getMessage());
        }
    }

    // ===== CallImpl =====

    private static void hookCallImpl(ClassLoader cl) {
        try {
            Class<?> callImplClass = cl.loadClass(MappingManager.cls("CallImpl"));
            String initMethodName = MappingManager.mtd("CallImpl.init");
            for (Method m : callImplClass.getDeclaredMethods()) {
                if (m.getName().equals(initMethodName) && m.getParameterCount() == 8) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                StateHolder.tmpCallId = (long) param.args[0];
                                StateHolder.tmpHostId = (long) param.args[1];
                                Object post = param.args[5];
                                if (post != null) {
                                    try {
                                        Object user = XposedHelpers.callMethod(post, "getUser");
                                        if (user != null)
                                            StateHolder.tmpHostNick = (String)
                                                    XposedHelpers.callMethod(user, "getNickname");
                                        StateHolder.tmpPostId = (long)
                                                XposedHelpers.callMethod(post, "getId");
                                    } catch (Throwable ignored) {}
                                }
                                log("[Call] callId=" + StateHolder.tmpCallId
                                        + " host=" + StateHolder.tmpHostNick);
                            } catch (Throwable t) {
                                log("[Call] CallImpl.init 解析失敗: " + t.getMessage());
                            }
                        }
                    });
                    log("[OK] CallImpl." + initMethodName + "() フック成功");
                    break;
                }
            }
        } catch (Throwable t) {
            log("[ERR] CallImpl.init フック失敗: " + t.getMessage());
        }
    }

    // ===== GlobalCallViewModel =====

    private static void hookGlobalCallViewModel(ClassLoader cl) {
        try {
            Class<?> jfClass = cl.loadClass(MappingManager.cls("JoinForm"));
            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("GlobalCallViewModel"), cl,
                    "joinCall", jfClass,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            StateHolder.retainedGcvm = param.thisObject;
                            try {
                                Object form = param.args[0];
                                Field aF = form.getClass().getDeclaredField(
                                        MappingManager.fld("JoinForm.callId"));
                                aF.setAccessible(true);
                                StateHolder.tmpCallId = ((Number) aF.get(form)).longValue();

                                Field fF = form.getClass().getDeclaredField(
                                        MappingManager.fld("JoinForm.hostId"));
                                fF.setAccessible(true);
                                StateHolder.tmpHostId = ((Number) fF.get(form)).longValue();

                                Field iF = form.getClass().getDeclaredField(
                                        MappingManager.fld("JoinForm.post"));
                                iF.setAccessible(true);
                                Object post = iF.get(form);
                                if (post != null) {
                                    StateHolder.tmpPostId = (long)
                                            XposedHelpers.callMethod(post, "getId");
                                    try {
                                        Object user = XposedHelpers.callMethod(post, "getUser");
                                        if (user != null)
                                            StateHolder.tmpHostNick = (String)
                                                    XposedHelpers.callMethod(user, "getNickname");
                                    } catch (Throwable ignored) {}
                                }
                            } catch (Throwable t) {
                                log("[Call] JoinForm解析失敗: " + t.getMessage());
                            }
                            log("[Call] joinCall callId=" + StateHolder.tmpCallId
                                    + " postId=" + StateHolder.tmpPostId);
                        }
                    });
            log("[OK] GlobalCallViewModel.joinCall() フック成功");
        } catch (Throwable t) {
            log("[ERR] GlobalCallViewModel.joinCall() フック失敗: " + t.getMessage());
        }
    }

    // ===== RtcEngine joinChannelWithUserAccount =====

    private static void hookRtcEngineAccount(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    MappingManager.cls("RtcEngineImpl"), cl,
                    "joinChannelWithUserAccount",
                    String.class, String.class, String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            StateHolder.tmpAccount = (String) param.args[2];
                        }
                    });
        } catch (Throwable ignored) {}
    }

    // ===== Activity フック =====

    private static void hookActivity(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity", cl, "onResume",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            final Activity activity = (Activity) param.thisObject;
                            if (!activity.getClass().getName().contains("nanameue")) return;
                            LicenseManager.setContext(activity);
                            MappingManager.updateVersionIfNeeded(activity);
                            for (String msg : MappingManager.drainPendingLogs()) log(msg);
                            StateHolder.lastActivityRef = new WeakReference<>(activity);
                            activity.runOnUiThread(() -> MenuUI.setup(activity));
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    "android.app.Activity", cl, "onDestroy",
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            if (activity == StateHolder.getLastActivity()) {
                                StateHolder.lastActivityRef = new WeakReference<>(null);
                                if (StateHolder.persistEnabled) {
                                    StateHolder.persistEnabled = false;
                                    PersistManager.stop();
                                }
                            }
                        }
                    });

        } catch (Throwable t) {
            log("[kyomu] Activity hook失敗: " + t.getMessage());
        }
    }

    // ===== 未対応バージョン用 =====

    private static void hookActivityForUnsupported(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity", cl, "onResume",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            final Activity activity = (Activity) param.thisObject;
                            if (!activity.getClass().getName().contains("nanameue")) return;
                            activity.runOnUiThread(() -> {
                                android.view.ViewGroup rootView =
                                        (android.view.ViewGroup) activity.getWindow().getDecorView();
                                if (rootView.findViewWithTag("kyomutool_unsupported") != null) return;
                                android.widget.TextView tv = new android.widget.TextView(activity);
                                tv.setTag("kyomutool_unsupported");
                                tv.setText("[kyomu] 未対応バージョン\nマッピングJSONを更新してください");
                                tv.setTextColor(android.graphics.Color.RED);
                                tv.setTextSize(12);
                                tv.setBackgroundColor(android.graphics.Color.argb(200, 0, 0, 0));
                                tv.setPadding(20, 20, 20, 20);
                                tv.setGravity(android.view.Gravity.CENTER);
                                android.widget.FrameLayout.LayoutParams lp =
                                        new android.widget.FrameLayout.LayoutParams(
                                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                                lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                                lp.topMargin = 100;
                                rootView.addView(tv, lp);
                            });
                        }
                    });
        } catch (Throwable ignored) {}
    }

    // ===== ヘルパー =====

    private static boolean isMyRealmUser(Object userObj) {
        Object cachedMyRealmUser = StateHolder.cachedMyRealmUserRef.get();
        if (cachedMyRealmUser == null || userObj == null) return false;
        try {
            Method getUuid = null;
            try {
                getUuid = userObj.getClass().getMethod("getUuid");
            } catch (Throwable t) {
                for (Method m : userObj.getClass().getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                        m.setAccessible(true);
                        try {
                            String val = (String) m.invoke(userObj);
                            if (val != null && val.matches(
                                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                                            + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                                            + "[0-9a-fA-F]{12}")) {
                                getUuid = m;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
            if (getUuid == null) return false;
            Object myUuid   = getUuid.invoke(cachedMyRealmUser);
            Object thisUuid = getUuid.invoke(userObj);
            return myUuid != null && myUuid.equals(thisUuid);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void hookMethod(Class<?> cls, String name,
                                   Class<?>[] params, XC_MethodHook hook) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            XposedBridge.hookMethod(m, hook);
        } catch (Throwable t) {
            log("[WARN] hookMethod失敗: " + name + " - " + t.getMessage());
        }
    }

    public static void log(String msg) {
        android.util.Log.d("KyomuTools", msg);
        try { XposedBridge.log("[KyomuTools] " + msg); } catch (Throwable ignored) {}
        String time = new java.text.SimpleDateFormat(
                "HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String line = time + " " + msg;
        synchronized (StateHolder.logList) {
            StateHolder.logList.add(line);
            if (StateHolder.logList.size() > StateHolder.LOG_MAX)
                StateHolder.logList.remove(0);
        }
        try {
            java.io.File logDir = new java.io.File("/data/data/jp.nanameue.yay/cache");
            if (!logDir.exists()) logDir.mkdirs();
            java.io.File f = new java.io.File(logDir, "kyomutool_log.txt");
            if (f.exists() && f.length() > 1024 * 1024)
                new java.io.FileWriter(f, false).close();
            try (java.io.FileWriter fw = new java.io.FileWriter(f, true)) {
                fw.write(line + "\n");
            }
        } catch (Throwable ignored) {}
    }

    // ===== 画面共有フック =====

    private static void hookScreenShare(ClassLoader cl) {
        try {
            Class<?> cvmClass = cl.loadClass(MappingManager.cls("CallViewModel"));
            for (java.lang.reflect.Method m : cvmClass.getDeclaredMethods()) {
                if (m.getReturnType() != boolean.class || m.getParameterCount() != 0) continue;
                String name = m.getName().toLowerCase();
                if (name.contains("screen") && (name.contains("other") || name.contains("sharing"))) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (StateHolder.screenShareAudioForce) param.setResult(false);
                        }
                    });
                }
                if (name.contains("screen") && name.contains("enabled")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (StateHolder.screenShareAudioForce) param.setResult(true);
                        }
                    });
                }
                if (name.contains("can") && name.contains("screen")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (StateHolder.screenShareAudioForce) param.setResult(true);
                        }
                    });
                }
            }
            log("[Screen] CallViewModel フック完了");
        } catch (Throwable t) {
            log("[Screen] CallViewModel フック失敗: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.SharedPreferencesImpl", cl,
                    "getBoolean", String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!StateHolder.screenShareAudioForce) return;
                            String key = (String) param.args[0];
                            if (key != null && (key.contains("screen_share_audio")
                                    || key.contains("ScreenShareAudio")
                                    || key.contains("canShareScreenAudio"))) {
                                param.setResult(true);
                            }
                        }
                    });
            log("[Screen] SharedPreferences フック完了");
        } catch (Throwable t) {
            log("[Screen] SharedPreferences フック失敗: " + t.getMessage());
        }

        try {
            Class<?> rtcClass = cl.loadClass(MappingManager.cls("RtcEngineImpl"));
            for (java.lang.reflect.Method m : rtcClass.getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("screen") && name.contains("track"))
                        || name.contains("screencapture")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            if (!StateHolder.screenShareAudioForce) return;
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof Boolean) param.args[i] = true;
                            }
                        }
                    });
                }
            }
            log("[Screen] RtcEngineImpl フック完了");
        } catch (Throwable t) {
            log("[Screen] RtcEngineImpl フック失敗: " + t.getMessage());
        }
    }

}
