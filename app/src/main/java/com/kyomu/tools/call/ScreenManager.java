/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.call;

import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;

/**
 * 画面共有の強制開始・停止を管理するクラス。
 */
public class ScreenManager {

    // ===== インスタンス化禁止 =====
    private ScreenManager() {}

    // ===== 強制開始 =====
    public static void forceStart() {
        Object cvm = StateHolder.getCachedCallViewModel();
        if (cvm == null) {
            HookManager.log("[Screen] CVM未取得");
            return;
        }
        try {
            for (java.lang.reflect.Method m : cvm.getClass().getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("screenshare") && name.contains("confirm")) {
                    m.setAccessible(true);
                    m.invoke(cvm);
                    HookManager.log("[Screen] 強制開始成功: " + m.getName());
                    return;
                }
            }
            HookManager.log("[Screen] 強制開始: メソッド未発見");
        } catch (Throwable t) {
            HookManager.log("[Screen] 強制開始失敗: " + t.getMessage());
        }
    }

    // ===== 強制停止 =====
    public static void forceStop() {
        Object cvm = StateHolder.getCachedCallViewModel();
        if (cvm == null) {
            HookManager.log("[Screen] CVM未取得");
            return;
        }
        try {
            for (java.lang.reflect.Method m : cvm.getClass().getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("stop") || name.contains("end"))
                        && name.contains("screen")) {
                    m.setAccessible(true);
                    m.invoke(cvm);
                    HookManager.log("[Screen] 停止成功: " + m.getName());
                    return;
                }
            }
            HookManager.log("[Screen] 停止: メソッド未発見");
        } catch (Throwable t) {
            HookManager.log("[Screen] 停止失敗: " + t.getMessage());
        }
    }
}
