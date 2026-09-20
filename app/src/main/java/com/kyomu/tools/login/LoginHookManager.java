/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 */
package com.kyomu.tools.login;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.kyomu.tools.MappingManager;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;

public class LoginHookManager {

    private LoginHookManager() {}

    private static final String WEB_API_KEY =
            "e9f1ae4c4470f29a92c0168dc42b13637cc332692103f23e626bc2b016f66603";
    private static final String WEB_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";
    private static final String APP_VERSION = "4.27.0";

    private static volatile Object  pendingFakeResponse   = null;
    private static volatile Object  cachedAccountInstance = null;
    private static volatile Object  cachedRealmDsInstance = null;
    private static volatile boolean isRefreshing          = false;

    // 後から manualRefresh() で使うために ClassLoader を保持
    private static volatile ClassLoader cachedClassLoader = null;

    // ------------------------------------------------------------------ //
    //  エントリポイント                                                    //
    // ------------------------------------------------------------------ //
    public static void hook(ClassLoader cl) {
        cachedClassLoader = cl;
        hookAccountConstructor(cl);
        hookLoginMethod(cl);
        hookOnMailLoginSuccess(cl);
        HookManager.log("[Login] フック完了");
    }

    // ------------------------------------------------------------------ //
    //  手動トークン再取得（ボタンから呼ぶ）                               //
    // ------------------------------------------------------------------ //
    public static void manualRefresh() {
        if (cachedClassLoader == null) {
            HookManager.log("[Login] ClassLoader未初期化");
            return;
        }
        if (isRefreshing) {
            HookManager.log("[Login] リフレッシュ中...");
            return;
        }
        isRefreshing = true;

        new Thread(() -> {
            try {
                Object ds = findRealmDataStore(cachedClassLoader);
                if (ds == null) {
                    HookManager.log("[Login] refresh: RealmDataStore未取得");
                    return;
                }

                Object authList = ds.getClass().getMethod("getUserAuths").invoke(ds);
                if (authList == null) return;

                int size = (int) authList.getClass().getMethod("size").invoke(authList);
                if (size == 0) {
                    HookManager.log("[Login] refresh: UserAuth なし → ファイルから試みる");
                    refreshFromFile();
                    return;
                }

                // 全アカウント分リフレッシュ
                for (int i = 0; i < size; i++) {
                    Object auth = authList.getClass()
                            .getMethod("get", int.class).invoke(authList, i);

                    Field rfField = auth.getClass().getDeclaredField("refreshToken");
                    rfField.setAccessible(true);
                    String refreshToken = rfField.get(auth).toString();

                    Field userIdField = auth.getClass().getDeclaredField("userId");
                    userIdField.setAccessible(true);
                    long userId = (long) userIdField.get(auth);

                    HookManager.log("[Login] refresh試行 [" + i + "] userId=" + userId
                            + " token=" + refreshToken.substring(0, Math.min(10, refreshToken.length())) + "...");

                    LoginResult result = refreshTokenV2(refreshToken);

                    if (result != null) {
                        result.userId = userId;
                        writeToRealm(cachedClassLoader, result, "unknown",
                                java.util.UUID.randomUUID().toString());
                        HookManager.log("[Login] refresh完了 [" + i + "] userId=" + userId);
                    } else {
                        HookManager.log("[Login] refresh失敗 [" + i + "] → 手動再ログインが必要です");
                    }
                }
            } catch (Throwable t) {
                HookManager.log("[Login] manualRefresh例外: " + t.getMessage());
            } finally {
                isRefreshing = false;
            }
        }).start();
    }

    private static void refreshFromFile() {
        try {
            java.io.File file = new java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS),
                    "kyomutool_token.txt");
            if (!file.exists()) {
                HookManager.log("[Login] ファイルなし → 手動再ログインが必要です");
                return;
            }
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader(file));
            String line1 = br.readLine();
            String line2 = br.readLine();
            br.close();
            if (line1 == null || line2 == null) {
                HookManager.log("[Login] ファイル読み込み失敗 → 手動再ログインが必要です");
                return;
            }
            long userId = Long.parseLong(line1.trim());
            String refreshToken = line2.trim();
            HookManager.log("[Login] ファイルから refresh試行 userId=" + userId);
            LoginResult result = refreshTokenV2(refreshToken);
            if (result != null) {
                result.userId = userId;
                writeToRealm(cachedClassLoader, result, "unknown",
                        java.util.UUID.randomUUID().toString());
                HookManager.log("[Login] ファイルから refresh完了 userId=" + userId);
            } else {
                HookManager.log("[Login] ファイルから refresh失敗 → 手動再ログインが必要です");
                file.delete();
                HookManager.log("[Login] 無効なトークンファイルを削除しました");
            }
        } catch (Throwable t) {
            HookManager.log("[Login] refreshFromFile例外: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Account コンストラクタフック（インスタンスキャッシュ）             //
    // ------------------------------------------------------------------ //
    private static void hookAccountConstructor(ClassLoader cl) {
        try {
            String accountClassName = !MappingManager.cls("AccountManager").isEmpty()
                    ? MappingManager.cls("AccountManager")
                    : "jp.nanameue.yay.account.a";

            Class<?> accountClass = cl.loadClass(accountClassName);

            for (java.lang.reflect.Constructor<?> ctor : accountClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        cachedAccountInstance = param.thisObject;
                        HookManager.log("[Login] Account インスタンスキャッシュ: "
                                + param.thisObject.getClass().getName());

                        try {
                            String realmDsClassName = !MappingManager.cls("RealmDataStore").isEmpty()
                                    ? MappingManager.cls("RealmDataStore")
                                    : "jp.nanameue.yay.data.RealmDataStore";
                            for (Field f : param.thisObject.getClass().getDeclaredFields()) {
                                if (f.getType().getName().equals(realmDsClassName)) {
                                    f.setAccessible(true);
                                    cachedRealmDsInstance = f.get(param.thisObject);
                                    HookManager.log("[Login] RealmDataStore キャッシュ: " + f.getName());
                                    break;
                                }
                            }
                        } catch (Throwable t) {
                            HookManager.log("[Login] RealmDataStore キャッシュ失敗: " + t.getMessage());
                        }
                    }
                });
            }
            HookManager.log("[Login] Account コンストラクタフック成功");
        } catch (Throwable t) {
            HookManager.log("[Login] Account コンストラクタフック失敗: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  UserInteractor ログインメソッドの自動検索＆フック                  //
    // ------------------------------------------------------------------ //
    private static void hookLoginMethod(ClassLoader cl) {
        try {
            String className = !MappingManager.cls("UserInteractor").isEmpty()
                    ? MappingManager.cls("UserInteractor")
                    : "jp.nanameue.yay.interactor.UserInteractor";

            Class<?> userInteractorClass = cl.loadClass(className);
            Method loginMethod = null;

            String mappedMethod = MappingManager.mtd("UserInteractor.login");
            if (!mappedMethod.isEmpty()) {
                for (Method m : userInteractorClass.getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (m.getName().equals(mappedMethod)
                            && params.length == 4
                            && params[0] == String.class
                            && params[1] == String.class
                            && params[2] == String.class) {
                        loginMethod = m;
                        HookManager.log("[Login] Mapping からメソッド取得: " + mappedMethod);
                        break;
                    }
                }
            }

            if (loginMethod == null) {
                for (Method m : userInteractorClass.getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 4
                            && params[0] == String.class
                            && params[1] == String.class
                            && params[2] == String.class) {
                        loginMethod = m;
                        HookManager.log("[Login] 自動検索でメソッド発見: " + m.getName());
                        break;
                    }
                }
            }

            if (loginMethod == null) {
                HookManager.log("[Login] ログインメソッド未発見");
                return;
            }

            XposedBridge.hookMethod(loginMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!StateHolder.loginHookEnabled) return;

                    String email    = (String) param.args[0];
                    String password = (String) param.args[1];
                    String uuid     = (String) param.args[2];
                    if (email == null) return;

                    HookManager.log("[Login] ログイン試行: " + email);

                    final String finalEmail    = email;
                    final String finalPassword = password;
                    final String finalUuid     = uuid;

                    new Thread(() -> {
                        LoginResult result = loginV2(finalEmail, finalPassword, finalUuid);
                        if (result == null) {
                            HookManager.log("[Login] v2 API 失敗");
                            return;
                        }
                        HookManager.log("[Login] v2 成功 userId=" + result.userId);
                        writeToRealm(cl, result, finalEmail, finalUuid);
                    }).start();
                }
            });

            HookManager.log("[Login] UserInteractor フック成功");
        } catch (Throwable t) {
            HookManager.log("[Login] UserInteractor フック失敗: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  onMailLoginSuccess の null ガード                                  //
    // ------------------------------------------------------------------ //
    private static void hookOnMailLoginSuccess(ClassLoader cl) {
        try {
            String vmClassName = !MappingManager.cls("LoginMenuViewModel").isEmpty()
                    ? MappingManager.cls("LoginMenuViewModel")
                    : "jp.nanameue.yay.account.LoginMenuViewModel";
            String respClassName = !MappingManager.cls("LoginUserResponse").isEmpty()
                    ? MappingManager.cls("LoginUserResponse")
                    : "jp.nanameue.yay.api.LoginUserResponse";

            XposedHelpers.findAndHookMethod(
                    vmClassName, cl,
                    "onMailLoginSuccess",
                    respClassName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!StateHolder.loginHookEnabled) return;

                            if (param.args[0] == null && pendingFakeResponse != null) {
                                HookManager.log("[Login] null → fakeResponse 差替");
                                param.args[0] = pendingFakeResponse;
                                pendingFakeResponse = null;
                            }
                        }
                    });
            HookManager.log("[Login] onMailLoginSuccess フック成功");
        } catch (Throwable t) {
            HookManager.log("[Login] onMailLoginSuccess フック失敗: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  リフレッシュトークンで再取得                                       //
    // ------------------------------------------------------------------ //
    private static LoginResult refreshTokenV2(String refreshToken) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.yay.space/api/v1/oauth/token");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");

            // Basic認証: base64(api_key:)
            String basic = android.util.Base64.encodeToString(
                    (WEB_API_KEY + ":").getBytes("UTF-8"),
                    android.util.Base64.NO_WRAP
            );
            conn.setRequestProperty("Authorization", "Basic " + basic);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            String body = "grant_type=refresh_token&refresh_token="
                    + java.net.URLEncoder.encode(refreshToken, "UTF-8");
            conn.getOutputStream().write(body.getBytes("UTF-8"));
            conn.getOutputStream().flush();
            conn.getOutputStream().close();

            int code = conn.getResponseCode();
            HookManager.log("[Login] refresh HTTP " + code);

            InputStream stream = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String res = sb.toString();
            HookManager.log("[Login] refresh response: "
                    + res.substring(0, Math.min(200, res.length())));

            if (code >= 200 && code < 300) {
                JSONObject json = new JSONObject(res);
                LoginResult r   = new LoginResult();
                r.userId        = 0; // manualRefresh 側で上書き
                r.accessToken   = json.getString("access_token");
                r.refreshToken  = json.getString("refresh_token");
                return r;
            }
        } catch (Throwable t) {
            HookManager.log("[Login] refresh 例外: " + t.getMessage());
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  v2 API（同期）                                                     //
    // ------------------------------------------------------------------ //
    private static LoginResult loginV2(String email, String password, String uuid) {
        HttpURLConnection conn = null;
        try {
            if (uuid == null || uuid.isEmpty()) {
                uuid = java.util.UUID.randomUUID().toString();
                HookManager.log("[Login] uuid 自動生成: " + uuid);
            }

            URL url = new URL("https://api.yay.space/v2/users/login_with_email");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",  "application/json;charset=UTF-8");
            conn.setRequestProperty("User-Agent",    WEB_UA);
            conn.setRequestProperty("Agent",         "YayWeb " + APP_VERSION);
            conn.setRequestProperty("X-Device-Info",
                    "Yay " + APP_VERSION + " Web (" + WEB_UA + ")");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("email",    email);
            body.put("password", password);
            body.put("api_key",  WEB_API_KEY);
            body.put("uuid",     uuid);
            byte[] bytes = body.toString().getBytes("UTF-8");
            conn.getOutputStream().write(bytes);
            conn.getOutputStream().close();

            int code = conn.getResponseCode();
            HookManager.log("[Login] v2 HTTP " + code);

            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String res = sb.toString();
            HookManager.log("[Login] v2 Response: "
                    + res.substring(0, Math.min(200, res.length())));

            if (code >= 200 && code < 300) {
                JSONObject json = new JSONObject(res);
                LoginResult r   = new LoginResult();
                r.userId        = json.getLong("user_id");
                r.accessToken   = json.getString("access_token");
                r.refreshToken  = json.getString("refresh_token");
                return r;
            }
        } catch (Throwable t) {
            HookManager.log("[Login] v2 例外: " + t.getMessage());
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Realm 書き込み（新規・上書き両対応）＋ Account 注入               //
    // ------------------------------------------------------------------ //
    private static void writeToRealm(ClassLoader cl,
                                     LoginResult result,
                                     String email,
                                     String uuid) {
        try {
            String userAuthClassName = !MappingManager.cls("UserAuth").isEmpty()
                    ? MappingManager.cls("UserAuth")
                    : "jp.nanameue.yay.data.realm.UserAuth";
            Class<?> userAuthClass = cl.loadClass(userAuthClassName);
            Object userAuth = userAuthClass.newInstance();
            XposedHelpers.setLongField  (userAuth, "userId",       result.userId);
            XposedHelpers.setObjectField(userAuth, "accessToken",  result.accessToken);
            XposedHelpers.setObjectField(userAuth, "refreshToken", result.refreshToken);
            XposedHelpers.setObjectField(userAuth, "expiresIn",    9999999999L);

            String userClassName = !MappingManager.cls("RealmUser").isEmpty()
                    ? MappingManager.cls("RealmUser")
                    : "jp.nanameue.yay.data.realm.User";
            Class<?> userClass = cl.loadClass(userClassName);
            Object user = userClass.newInstance();
            XposedHelpers.setLongField   (user, "id",       result.userId);
            XposedHelpers.setObjectField (user, "uuid",     uuid);
            XposedHelpers.setObjectField (user, "nickname", email);
            XposedHelpers.setObjectField (user, "username", email);
            XposedHelpers.setBooleanField(user, "newUser",  false);

            String realmDsClassName = !MappingManager.cls("RealmDataStore").isEmpty()
                    ? MappingManager.cls("RealmDataStore")
                    : "jp.nanameue.yay.data.RealmDataStore";
            Class<?> realmDsClass = cl.loadClass(realmDsClassName);

            Object realmDs = findRealmDataStore(cl);
            if (realmDs != null) {
                try {
                    List<Object> authList = new ArrayList<>();
                    authList.add(userAuth);
                    realmDsClass.getMethod("createOrUpdateUserAuths", List.class)
                            .invoke(realmDs, authList);
                    HookManager.log("[Login] UserAuth 書き込み完了 userId=" + result.userId);
                } catch (Throwable t) {
                    HookManager.log("[Login] createOrUpdateUserAuths 失敗: " + t.getMessage());
                }
                try {
                    List<Object> userList = new ArrayList<>();
                    userList.add(user);
                    realmDsClass.getMethod("createOrUpdateUsers", List.class)
                            .invoke(realmDs, userList);
                    HookManager.log("[Login] User 書き込み完了");
                } catch (Throwable t) {
                    HookManager.log("[Login] createOrUpdateUsers 失敗: " + t.getMessage());
                }
            } else {
                HookManager.log("[Login] RealmDataStore インスタンス未取得");
            }

            injectToAccount(cl, user, userAuth, result, uuid);

            // refreshToken をファイルに保存
            try {
                java.io.File file = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS),
                        "kyomutool_token.txt");
                java.io.FileWriter fw = new java.io.FileWriter(file, false);
                fw.write(result.userId + "\n" + result.refreshToken);
                fw.close();
                HookManager.log("[Login] refreshToken 保存完了: " + file.getAbsolutePath());
            } catch (Throwable t) {
                HookManager.log("[Login] refreshToken 保存失敗: " + t.getMessage());
            }

        } catch (Throwable t) {
            HookManager.log("[Login] writeToRealm 失敗: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Account インスタンスへの注入                                       //
    // ------------------------------------------------------------------ //
    private static void injectToAccount(ClassLoader cl,
                                        Object user,
                                        Object userAuth,
                                        LoginResult result,
                                        String uuid) {
        try {
            String accountClassName = !MappingManager.cls("AccountManager").isEmpty()
                    ? MappingManager.cls("AccountManager")
                    : "jp.nanameue.yay.account.a";

            Class<?> accountClass  = cl.loadClass(accountClassName);
            Object accountInstance = findSingleton(accountClass);
            if (accountInstance == null) {
                HookManager.log("[Login] Account インスタンス未取得");
                return;
            }

            // User 型フィールド：Mapping 優先→自動検索
            String userFieldName = MappingManager.fld("AccountManager.userField");
            if (!userFieldName.isEmpty()) {
                try {
                    Field f = accountClass.getDeclaredField(userFieldName);
                    f.setAccessible(true);
                    f.set(accountInstance, user);
                    HookManager.log("[Login] User field set (Mapping): " + userFieldName);
                    userFieldName = "done";
                } catch (Throwable t) {
                    HookManager.log("[Login] Mapping field 失敗、自動検索へ: " + t.getMessage());
                    userFieldName = "";
                }
            }
            if (!userFieldName.equals("done")) {
                String userClassName = !MappingManager.cls("RealmUser").isEmpty()
                        ? MappingManager.cls("RealmUser")
                        : "jp.nanameue.yay.data.realm.User";
                for (Field f : accountClass.getDeclaredFields()) {
                    if (f.getType().getName().equals(userClassName)) {
                        f.setAccessible(true);
                        f.set(accountInstance, user);
                        HookManager.log("[Login] User field set (auto): " + f.getName());
                        break;
                    }
                }
            }

            // UserAuth メソッド：Mapping 優先→自動検索
            String saveAuthMethod = MappingManager.mtd("AccountManager.saveAuth");
            if (!saveAuthMethod.isEmpty()) {
                try {
                    String userAuthClassName = !MappingManager.cls("UserAuth").isEmpty()
                            ? MappingManager.cls("UserAuth")
                            : "jp.nanameue.yay.data.realm.UserAuth";
                    Method m = accountClass.getDeclaredMethod(
                            saveAuthMethod, cl.loadClass(userAuthClassName));
                    m.setAccessible(true);
                    m.invoke(accountInstance, userAuth);
                    HookManager.log("[Login] Account." + saveAuthMethod + "() (Mapping) 成功");
                    saveAuthMethod = "done";
                } catch (Throwable t) {
                    HookManager.log("[Login] Mapping method 失敗、自動検索へ: " + t.getMessage());
                    saveAuthMethod = "";
                }
            }
            if (!saveAuthMethod.equals("done")) {
                String userAuthClassName = !MappingManager.cls("UserAuth").isEmpty()
                        ? MappingManager.cls("UserAuth")
                        : "jp.nanameue.yay.data.realm.UserAuth";
                for (Method m : accountClass.getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1
                            && params[0].getName().equals(userAuthClassName)) {
                        m.setAccessible(true);
                        m.invoke(accountInstance, userAuth);
                        HookManager.log("[Login] Account." + m.getName() + "() (auto) 成功");
                        break;
                    }
                }
            }

            pendingFakeResponse = buildFakeResponse(cl, result, uuid);

        } catch (Throwable t) {
            HookManager.log("[Login] injectToAccount 失敗: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  LoginUserResponse 生成                                             //
    // ------------------------------------------------------------------ //
    private static Object buildFakeResponse(ClassLoader cl,
                                            LoginResult result,
                                            String uuid) {
        try {
            String respClassName = !MappingManager.cls("LoginUserResponse").isEmpty()
                    ? MappingManager.cls("LoginUserResponse")
                    : "jp.nanameue.yay.api.LoginUserResponse";
            return XposedHelpers.newInstance(
                    cl.loadClass(respClassName),
                    result.userId,
                    result.accessToken,
                    false,
                    null,
                    uuid,
                    WEB_API_KEY,
                    9999999999L
            );
        } catch (Throwable t) {
            HookManager.log("[Login] fakeResponse 生成失敗: " + t.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ //
    //  ヘルパー：RealmDataStore インスタンス取得                          //
    // ------------------------------------------------------------------ //
    private static Object findRealmDataStore(ClassLoader cl) {
        if (cachedRealmDsInstance != null) {
            HookManager.log("[Login] RealmDataStore (cache)");
            return cachedRealmDsInstance;
        }
        try {
            String accountClassName = !MappingManager.cls("AccountManager").isEmpty()
                    ? MappingManager.cls("AccountManager")
                    : "jp.nanameue.yay.account.a";

            Class<?> accountClass = cl.loadClass(accountClassName);
            Object instance = findSingleton(accountClass);
            if (instance == null) return null;

            String fieldName = MappingManager.fld("AccountManager.realmDataStore");
            if (!fieldName.isEmpty()) {
                try {
                    Field f = accountClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object val = f.get(instance);
                    if (val != null) {
                        HookManager.log("[Login] RealmDataStore (Mapping): " + fieldName);
                        return val;
                    }
                } catch (Throwable ignored) {}
            }

            String realmDsClassName = !MappingManager.cls("RealmDataStore").isEmpty()
                    ? MappingManager.cls("RealmDataStore")
                    : "jp.nanameue.yay.data.RealmDataStore";
            for (Field f : accountClass.getDeclaredFields()) {
                if (f.getType().getName().equals(realmDsClassName)) {
                    f.setAccessible(true);
                    HookManager.log("[Login] RealmDataStore (auto): " + f.getName());
                    return f.get(instance);
                }
            }
        } catch (Throwable t) {
            HookManager.log("[Login] findRealmDataStore 失敗: " + t.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  ヘルパー：static フィールドからシングルトンを探す                  //
    // ------------------------------------------------------------------ //
    private static Object findSingleton(Class<?> clazz) {
        if (cachedAccountInstance != null && clazz.isInstance(cachedAccountInstance)) {
            HookManager.log("[Login] Account (cache)");
            return cachedAccountInstance;
        }
        try {
            for (Field f : clazz.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(null);
                    if (v != null && clazz.isInstance(v)) return v;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentApp = atClass.getMethod("currentApplication");
            Object app = currentApp.invoke(null);
            if (app == null) return null;
            return searchInstance(app, clazz, 0);
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object searchInstance(Object root, Class<?> targetClass, int depth) {
        if (depth > 3 || root == null) return null;
        try {
            for (Field f : root.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(root);
                    if (val == null) continue;
                    if (targetClass.isInstance(val)) return val;
                    if (val.getClass().getName().startsWith("jp.nanameue.yay")) {
                        Object found = searchInstance(val, targetClass, depth + 1);
                        if (found != null) return found;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------------ //
    //  データクラス                                                        //
    // ------------------------------------------------------------------ //
    private static class LoginResult {
        long   userId;
        String accessToken;
        String refreshToken;
    }
}