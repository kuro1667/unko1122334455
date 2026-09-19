/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools;

import com.kyomu.tools.core.BrandGuard;
import com.kyomu.tools.core.HookManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.contains("yay")) return;
        XposedBridge.log("[KyomuTools] handleLoadPackage: " + lpparam.packageName);
        if (!BrandGuard.isValid()) {
            XposedBridge.log("[KyomuTools] BrandGuard検証失敗 - モジュール無効化");
            return;
        }
        HookManager.handlePackage(lpparam);
    }
}
