/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.persist;

import com.kyomu.tools.MappingManager;
import com.kyomu.tools.call.CallManager;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedHelpers;

/**
 * 自動ミュート解除送信（Persist）処理を管理するクラス。
 * 修正: Timer → ScheduledExecutorService に変更。
 * スレッド例外で止まるリスクを排除し安定性を向上。
 */
public class PersistManager {

    // ===== インスタンス化禁止 =====
    private PersistManager() {}

    // ===== スケジューラー =====
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "KyomuTools-Persist");
                t.setDaemon(true);
                return t;
            });

    private static volatile ScheduledFuture<?> currentTask = null;

    // ===== UI更新コールバック =====
    private static volatile Runnable statusUpdateCallback = null;

    public static void setStatusUpdateCallback(Runnable callback) {
        statusUpdateCallback = callback;
    }

    private static void notifyStatusUpdate() {
        if (statusUpdateCallback != null) {
            statusUpdateCallback.run();
        }
    }

    // ===== 開始 =====

    public static void start() {
        synchronized (StateHolder.persistLock) {
            stopInternal();
            StateHolder.sentCount = 0;

            if (!CallManager.ensureAgoraCached()) {
                HookManager.log("[Persist] agora.a未取得 — 通話参加後にONにしてください");
                StateHolder.persistEnabled = false;
                notifyStatusUpdate();
                return;
            }

            long intervalMs = (long) (StateHolder.currentInterval * 1000);

            currentTask = scheduler.scheduleWithFixedDelay(() -> {
                try {
                    sendLiftAudioMute();
                } catch (Throwable t) {
                    HookManager.log("[Persist] 例外: " + t.getMessage());
                    StateHolder.persistEnabled = false;
                    stopInternal();
                    notifyStatusUpdate();
                }
            }, 0, intervalMs, TimeUnit.MILLISECONDS);

            HookManager.log("[Persist] ON (" + StateHolder.currentInterval + "s)");
            notifyStatusUpdate();
        }
    }

    // ===== 停止 =====

    public static void stop() {
        synchronized (StateHolder.persistLock) {
            stopInternal();
        }
    }

    private static void stopInternal() {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        if (StateHolder.sentCount > 0) {
            HookManager.log("[Persist] OFF (" + StateHolder.sentCount + "回送信済)");
        }
        notifyStatusUpdate();
    }

    // ===== 送信処理 =====

    private static void sendLiftAudioMute() {
        Object agoraA = StateHolder.getAgoraA();
        if (agoraA == null) {
            if (!CallManager.ensureAgoraCached()) return;
            agoraA = StateHolder.getAgoraA();
            if (agoraA == null) return;
        }
        try {
            XposedHelpers.callMethod(
                    agoraA,
                    MappingManager.mtd("AgoraWrapper.sendCommand"),
                    "requestLiftAudioMute");
            StateHolder.sentCount++;
            if (StateHolder.sentCount % 50 == 0) {
                HookManager.log("[Persist] 送信 #" + StateHolder.sentCount);
            }
            notifyStatusUpdate();
        } catch (Throwable t) {
            HookManager.log("[Persist] 送信失敗: " + t.getMessage());
            StateHolder.setAgoraA(null);
        }
    }

    // ===== 状態取得 =====

    public static boolean isRunning() {
        return currentTask != null && !currentTask.isCancelled();
    }
}
