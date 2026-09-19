/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools;

import android.app.Activity;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * マッピングJSONを assets/mappings/latest.json から読み込み、
 * クラス名・メソッド名・フィールド名のキーベース解決を提供するクラス。
 *
 * スレッドセーフ設計：loaded / appVersion / mappingVersion は volatile。
 * pendingLogs は synchronized ブロックで保護。
 */
public class MappingManager {

    // ---------- 定数 ----------

    private static final String TAG = "KyomuTools";
    private static final String MAPPING_PATH = "mappings/latest.json";

    // ---------- フィールド ----------

    private static JSONObject classMap = null;
    private static JSONObject methodMap = null;
    private static JSONObject fieldMap = null;

    private static volatile boolean loaded = false;
    private static volatile String appVersion = "unknown";
    private static volatile String mappingVersion = "?";

    private static final List<String> pendingLogs = new ArrayList<>();

    // ---------- コンストラクタ ----------

    private MappingManager() {}

    // ---------- ロード ----------

    /**
     * モジュールの assets から latest.json を読み込む。
     *
     * @return 成功した場合 true
     */
    public static boolean load() {
        synchronized (pendingLogs) {
            pendingLogs.clear();
        }

        JSONObject root = loadFromModuleAssets(MAPPING_PATH);
        if (root == null) {
            addLog("[Mapping] latest.json 読み込み失敗");
            return false;
        }

        JSONObject tmpClass  = root.optJSONObject("classes");
        JSONObject tmpMethod = root.optJSONObject("methods");
        JSONObject tmpField  = root.optJSONObject("fields");

        if (tmpClass == null || tmpMethod == null || tmpField == null) {
            addLog("[Mapping] JSONフォーマット不正: classes/methods/fields キーが存在しません");
            return false;
        }

        classMap       = tmpClass;
        methodMap      = tmpMethod;
        fieldMap       = tmpField;
        loaded         = true;
        mappingVersion = root.optString("version", "?");

        addLog("[Mapping] ロード成功: v" + mappingVersion);
        return true;
    }

    /**
     * モジュールの ClassLoader から assets ファイルを読み込む。
     * try-with-resources で BufferedReader を安全にクローズ。
     *
     * @param path assets/ 以下のパス
     * @return パース済みの JSONObject、失敗時は null
     */
    private static JSONObject loadFromModuleAssets(String path) {
        InputStream is = null;
        try {
            ClassLoader cl = MappingManager.class.getClassLoader();
            if (cl == null) {
                addLog("[Mapping] ClassLoader が null");
                return null;
            }

            is = cl.getResourceAsStream("assets/" + path);
            if (is == null) {
                addLog("[Mapping] 未発見: assets/" + path);
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            return new JSONObject(sb.toString());

        } catch (Throwable t) {
            addLog("[Mapping] 読み込みエラー: " + t.getMessage());
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {}
            }
        }
    }

    // ---------- ログ ----------

    /**
     * Android ログ と pendingLogs 両方に記録する。
     * XposedBridge は HookManager 経由でのみ使用するため、
     * ここでは android.util.Log を使用する。
     */
    private static void addLog(String msg) {
        Log.d(TAG, msg);
        synchronized (pendingLogs) {
            pendingLogs.add(msg);
        }
    }

    /**
     * 蓄積されたログを取得してクリアする。
     *
     * @return ログのコピーリスト
     */
    public static List<String> drainPendingLogs() {
        synchronized (pendingLogs) {
            List<String> copy = new ArrayList<>(pendingLogs);
            pendingLogs.clear();
            return copy;
        }
    }

    // ---------- キー解決 ----------

    /**
     * クラス名を解決する。
     *
     * @param key マッピングキー
     * @return 対応するクラス名、見つからない場合は空文字
     */
    public static String cls(String key) {
        return classMap != null ? classMap.optString(key, "") : "";
    }

    /**
     * メソッド名を解決する。
     *
     * @param key マッピングキー
     * @return 対応するメソッド名、見つからない場合は空文字
     */
    public static String mtd(String key) {
        return methodMap != null ? methodMap.optString(key, "") : "";
    }

    /**
     * フィールド名を解決する。
     *
     * @param key マッピングキー
     * @return 対応するフィールド名、見つからない場合は空文字
     */
    public static String fld(String key) {
        return fieldMap != null ? fieldMap.optString(key, "") : "";
    }

    // ---------- ゲッター ----------

    /** マッピングがロード済みかどうか */
    public static boolean isLoaded() {
        return loaded;
    }

    /** マッピングバージョン文字列 */
    public static String getMappingVersion() {
        return mappingVersion;
    }

    // ---------- バージョン更新 ----------

    /**
     * Activity からアプリのバージョン名を取得して appVersion に反映する。
     * すでに取得済みの場合はスキップ。
     *
     * @param activity 取得に使用する Activity
     */
    public static void updateVersionIfNeeded(Activity activity) {
        if (!"unknown".equals(appVersion)) return;

        try {
            String v = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0)
                    .versionName;
            if (v != null && !v.isEmpty()) {
                appVersion = v;
                addLog("[Mapping] アプリバージョン取得: " + v);
            }
        } catch (Throwable ignored) {}
    }
}
