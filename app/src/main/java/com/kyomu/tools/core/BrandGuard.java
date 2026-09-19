package com.kyomu.tools.core;

import com.kyomu.tools.BuildConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class BrandGuard {

    private BrandGuard() {}

    private static final int K = 0x37;

    public static final String MENU_TITLE = decode(new byte[]{
            0x7C, 0x4E, 0x58, 0x5A, 0x42, 0x63, 0x58, 0x58, 0x5B, 0x44
    }, K);

    public static final String LOG_TAG = decode(new byte[]{
            0x6C, 0x5C, 0x4E, 0x58, 0x5A, 0x42, 0x6A
    }, K);

    public static final String PACKAGE_NAME = decode(new byte[]{
            0x54, 0x58, 0x5A, 0x19, 0x5C, 0x4E, 0x58, 0x5A, 0x42,
            0x19, 0x43, 0x58, 0x58, 0x5B, 0x44
    }, K);

    private static volatile Boolean cachedResult = null;

    public static boolean isValid() {
        if (cachedResult != null) return cachedResult;
        cachedResult = verify();
        return cachedResult;
    }

    public static boolean reVerify() {
        cachedResult = null;
        return isValid();
    }

    private static boolean verify() {
        if (!verifyPackage()) {
            log("BG:P");
            return false;
        }
        if (!verifyTitle()) {
            log("BG:T");
            return false;
        }
        if (!verifyLogTag()) {
            log("BG:L");
            return false;
        }
        return true;
    }

    private static boolean verifyPackage() {
        return PACKAGE_NAME.equals(BuildConfig.EXPECTED_PACKAGE)
                && PACKAGE_NAME.equals(BuildConfig.APPLICATION_ID);
    }

    private static boolean verifyTitle() {
        return sha256(MENU_TITLE).equals(BuildConfig.BRAND_TITLE_HASH);
    }

    private static boolean verifyLogTag() {
        return sha256(LOG_TAG).equals(BuildConfig.BRAND_LOG_TAG_HASH);
    }

    private static String decode(byte[] encoded, int key) {
        byte[] result = new byte[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            result[i] = (byte) (encoded[i] ^ key);
        }
        return new String(result, StandardCharsets.UTF_8);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void log(String msg) {
        android.util.Log.w("KyomuTools", msg);
        try {
            de.robv.android.xposed.XposedBridge.log(msg);
        } catch (Throwable ignored) {}
    }
}
