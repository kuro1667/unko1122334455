/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * ライセンス管理クラス。
 *
 * NOTE: サーバーが停止中のため現在は常時 free（isLicensed = true）で運用。
 *       サーバー通信メソッドは将来の復活用に維持。
 */
public class LicenseManager {

    // ===== インスタンス化禁止 =====
    private LicenseManager() {}

    // ===== 復号ユーティリティ =====
    private static String decrypt(byte[] data) {
        byte key = 0x72;
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append((char) (b ^ key));
        }
        return sb.toString();
    }

    // ===== 定数 =====
    static final String LICENSE_REGISTER = decrypt(new byte[]{
            (byte)0x1a,(byte)0x06,(byte)0x06,(byte)0x02,(byte)0x01,
            (byte)0x48,(byte)0x5d,(byte)0x5d,(byte)0x19,(byte)0x0b,
            (byte)0x1d,(byte)0x1f,(byte)0x07,(byte)0x5c,(byte)0x1f,
            (byte)0x0b,(byte)0x04,(byte)0x1c,(byte)0x11,(byte)0x5c,
            (byte)0x11,(byte)0x1d,(byte)0x1f,(byte)0x48,(byte)0x47,
            (byte)0x42,(byte)0x4a,(byte)0x40,(byte)0x5d,(byte)0x13,
            (byte)0x02,(byte)0x1b,(byte)0x5d,(byte)0x00,(byte)0x17,
            (byte)0x15,(byte)0x1b,(byte)0x01,(byte)0x06,(byte)0x17,
            (byte)0x00
    });
    static final String LICENSE_VERIFY = decrypt(new byte[]{
            (byte)0x1a,(byte)0x06,(byte)0x06,(byte)0x02,(byte)0x01,
            (byte)0x48,(byte)0x5d,(byte)0x5d,(byte)0x19,(byte)0x0b,
            (byte)0x1d,(byte)0x1f,(byte)0x07,(byte)0x5c,(byte)0x1f,
            (byte)0x0b,(byte)0x04,(byte)0x1c,(byte)0x11,(byte)0x5c,
            (byte)0x11,(byte)0x1d,(byte)0x1f,(byte)0x48,(byte)0x47,
            (byte)0x42,(byte)0x4a,(byte)0x40,(byte)0x5d,(byte)0x13,
            (byte)0x02,(byte)0x1b,(byte)0x5d,(byte)0x04,(byte)0x17,
            (byte)0x00,(byte)0x1b,(byte)0x14,(byte)0x0b
    });
    static final String API_KEY = decrypt(new byte[]{
            (byte)0x44,(byte)0x40,(byte)0x45,(byte)0x10,(byte)0x45,
            (byte)0x13,(byte)0x4a,(byte)0x11,(byte)0x41,(byte)0x44,
            (byte)0x10,(byte)0x16,(byte)0x45,(byte)0x43,(byte)0x17,
            (byte)0x14,(byte)0x47,(byte)0x4a,(byte)0x42,(byte)0x46,
            (byte)0x11,(byte)0x46,(byte)0x44,(byte)0x43,(byte)0x43,
            (byte)0x13,(byte)0x13,(byte)0x14,(byte)0x4b,(byte)0x41,
            (byte)0x4a,(byte)0x47,(byte)0x44,(byte)0x40,(byte)0x42,
            (byte)0x4b,(byte)0x44,(byte)0x4b,(byte)0x16,(byte)0x13,
            (byte)0x10,(byte)0x43,(byte)0x17,(byte)0x46,(byte)0x47,
            (byte)0x47,(byte)0x40,(byte)0x4b,(byte)0x46,(byte)0x40,
            (byte)0x44,(byte)0x45,(byte)0x13,(byte)0x4b,(byte)0x10,
            (byte)0x17,(byte)0x13,(byte)0x4a,(byte)0x11,(byte)0x11,
            (byte)0x4b,(byte)0x13,(byte)0x44,(byte)0x4b
    });
    private static final String LICENSE_HOST = decrypt(new byte[]{
            (byte)0x19,(byte)0x0b,(byte)0x1d,(byte)0x1f,(byte)0x07,
            (byte)0x5c,(byte)0x1f,(byte)0x0b,(byte)0x04,(byte)0x1c,
            (byte)0x11,(byte)0x5c,(byte)0x11,(byte)0x1d,(byte)0x1f
    });

    // ===== HostnameVerifier =====
    static final HostnameVerifier PINNED_HOSTNAME_VERIFIER =
            (hostname, session) -> LICENSE_HOST.equals(hostname);

    // ===== TrustManager（全証明書許可） =====
    // NOTE: サーバー停止中のため現在は使用されない
    //       将来サーバー復活時は適切な証明書ピンニングに置き換えること
    private static final TrustManager[] TRUST_ALL = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    // 修正: Android 14+ では null ではなく空配列を返す
                    return new X509Certificate[0];
                }
                @Override
                public void checkClientTrusted(
                        X509Certificate[] chain, String authType) {}
                @Override
                public void checkServerTrusted(
                        X509Certificate[] chain, String authType) {}
            }
    };

    // ===== SharedPreferences ファイル名 =====
    private static final String PREFS_NAME = "kyomutool_license";

    // ===== 状態変数 =====
    // NOTE: サーバー停止中につき常時 free で運用
    public static volatile boolean isLicensed         = true;
    public static volatile boolean licenseChecked     = true;
    public static volatile boolean licenseServerReached = true;
    public static volatile long    licenseLastCheck   =
            System.currentTimeMillis();
    public static volatile boolean licenseBanned      = false;

    // ===== Context キャッシュ =====
    private static volatile Context cachedContext = null;

    public static void setContext(Context ctx) {
        if (ctx != null) cachedContext = ctx.getApplicationContext();
    }

    // ===== SharedPreferences 書き込み =====
    public static void saveToPrefs(String key, String deviceId,
                                   long timestamp, String signature) {
        Context ctx = cachedContext;
        if (ctx == null) {
            com.kyomu.tools.core.HookManager.log(
                    "[License] saveToPrefs: context null");
            return;
        }
        try {
            // Android 9未満: MODE_WORLD_READABLE を試みる
            // Android 9以降: SecurityException → MODE_PRIVATE にフォールバック
            @SuppressWarnings("deprecation")
            SharedPreferences prefs = ctx.getSharedPreferences(
                    PREFS_NAME, Context.MODE_WORLD_READABLE);
            prefs.edit()
                    .putBoolean("licensed", true)
                    .putString("license_key", key)
                    .putString("device_id", deviceId)
                    .putLong("timestamp", timestamp)
                    .putString("signature", signature)
                    .apply();
            com.kyomu.tools.core.HookManager.log(
                    "[License] saveToPrefs: 保存成功 key="
                            + key.substring(0, 4) + "...");
        } catch (Throwable t) {
            // MODE_WORLD_READABLE 不可 → MODE_PRIVATE + chmod にフォールバック
            try {
                SharedPreferences prefs = ctx.getSharedPreferences(
                        PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean("licensed", true)
                        .putString("license_key", key)
                        .putString("device_id", deviceId)
                        .putLong("timestamp", timestamp)
                        .putString("signature", signature)
                        .apply();
                try {
                    File prefsFile = new File(
                            ctx.getFilesDir().getParent()
                                    + "/shared_prefs/" + PREFS_NAME + ".xml");
                    prefsFile.setReadable(true, false);
                    com.kyomu.tools.core.HookManager.log(
                            "[License] saveToPrefs: MODE_PRIVATE + chmod 成功");
                } catch (Throwable ignored) {}
            } catch (Throwable t2) {
                com.kyomu.tools.core.HookManager.log(
                        "[License] saveToPrefs: 失敗 " + t2.getMessage());
            }
        }
    }

    public static void clearPrefs() {
        Context ctx = cachedContext;
        if (ctx == null) return;
        try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().clear().apply();
            com.kyomu.tools.core.HookManager.log(
                    "[License] clearPrefs: クリア完了");
        } catch (Throwable t) {
            com.kyomu.tools.core.HookManager.log(
                    "[License] clearPrefs: 失敗 " + t.getMessage());
        }
    }

    // ===== ServerCheckResult =====
    static class ServerCheckResult {
        boolean valid;
        boolean serverReached;
        String  error;
        boolean banned;
        long    timestamp;
        String  signature;

        ServerCheckResult(boolean valid, boolean serverReached,
                          String error, boolean banned) {
            this.valid         = valid;
            this.serverReached = serverReached;
            this.error         = error;
            this.banned        = banned;
            this.timestamp     = 0;
            this.signature     = null;
        }
    }

    // ===== メイン認証チェック =====
    // NOTE: サーバー停止中につき常時 true を返す
    //       将来サーバー復活時はこのメソッドを有効化すること
    public static boolean checkLicenseWithServer() {
        com.kyomu.tools.core.HookManager.log(
                "[License] サーバー停止中 → 常時 free");
        return true;
    }

    // ===== HTTP通信（verify） =====
    // NOTE: サーバー停止中につき現在は呼ばれない。将来の復活用に維持。
    @SuppressWarnings("unused")
    private static ServerCheckResult checkWithServer(
            String licenseKey, String deviceId) {
        HttpsURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("licenseKey", licenseKey);
            body.put("deviceId", deviceId);

            conn = buildConnection(LICENSE_VERIFY, body);
            int code = conn.getResponseCode();
            if (code != 200) return parseHttpError(conn, code);

            JSONObject resp = readResponse(conn);
            if (resp.optBoolean("valid", false)) {
                ServerCheckResult ok =
                        new ServerCheckResult(true, true, null, false);
                ok.timestamp = resp.optLong("timestamp", 0);
                ok.signature = resp.optString("signature", "");
                return ok;
            }
            String error   = resp.optString("error", "UNKNOWN");
            boolean banned = "BANNED".equals(error)
                    || "DEVICE_BANNED".equals(error);
            if (banned) licenseBanned = true;
            return new ServerCheckResult(false, true, error, banned);

        } catch (Throwable e) {
            com.kyomu.tools.core.HookManager.log(
                    "[License] verify通信エラー: " + e.getMessage());
            return new ServerCheckResult(
                    false, false, "NETWORK_ERROR", false);
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    // ===== HTTP通信（register） =====
    // NOTE: サーバー停止中につき現在は呼ばれない。将来の復活用に維持。
    @SuppressWarnings("unused")
    static ServerCheckResult callRegisterApi(
            String licenseKey, String deviceId) {
        HttpsURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("licenseKey", licenseKey);
            body.put("deviceId", deviceId);

            conn = buildConnection(LICENSE_REGISTER, body);
            int code = conn.getResponseCode();
            if (code != 200) return parseHttpError(conn, code);

            JSONObject resp = readResponse(conn);
            if (resp.optBoolean("valid", false)) {
                ServerCheckResult ok =
                        new ServerCheckResult(true, true, null, false);
                ok.timestamp = resp.optLong("timestamp", 0);
                ok.signature = resp.optString("signature", "");
                return ok;
            }
            String error   = resp.optString("error", "UNKNOWN");
            boolean banned = "BANNED".equals(error)
                    || "DEVICE_BANNED".equals(error);
            if (banned) licenseBanned = true;
            return new ServerCheckResult(false, true, error, banned);

        } catch (Throwable e) {
            com.kyomu.tools.core.HookManager.log(
                    "[License] register通信エラー: " + e.getMessage());
            return new ServerCheckResult(
                    false, false, "NETWORK_ERROR", false);
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    // ===== 共通HTTPヘルパー =====
    private static HttpsURLConnection buildConnection(
            String urlStr, JSONObject body) throws Exception {
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, TRUST_ALL, new SecureRandom());

        java.net.URL url = new java.net.URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sc.getSocketFactory());
        conn.setHostnameVerifier(PINNED_HOSTNAME_VERIFIER);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-API-Key", API_KEY);
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        byte[] bytes = body.toString().getBytes("UTF-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        return conn;
    }

    private static JSONObject readResponse(
            HttpsURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return new JSONObject(sb.toString());
    }

    private static ServerCheckResult parseHttpError(
            HttpsURLConnection conn, int code) {
        String  errorStr = "HTTP_" + code;
        boolean banned   = false;
        try {
            java.io.InputStream es = conn.getErrorStream();
            if (es == null) {
                try { es = conn.getInputStream(); }
                catch (Throwable ignored) {}
            }
            if (es != null) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(es, "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                if (!sb.toString().isEmpty()) {
                    JSONObject obj = new JSONObject(sb.toString());
                    errorStr = obj.optString("error", errorStr);
                    banned   = "BANNED".equals(errorStr)
                            || "DEVICE_BANNED".equals(errorStr);
                    com.kyomu.tools.core.HookManager.log(
                            "[License] HTTPエラー " + code + ": " + errorStr);
                }
            }
        } catch (Throwable ignored) {}
        if (banned) licenseBanned = true;
        return new ServerCheckResult(false, true, errorStr, banned);
    }

    // ===== ユーティリティ =====
    public static String normalizeLicenseKey(String raw) {
        if (raw == null) return "";
        String stripped = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (stripped.length() == 24) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stripped.length(); i++) {
                if (i > 0 && i % 4 == 0) sb.append('-');
                sb.append(stripped.charAt(i));
            }
            return sb.toString();
        }
        return raw.trim();
    }
}
