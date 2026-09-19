package com.kyomu.tools.ui.pages;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.ref.WeakReference;

public class LogPage {

    private static WeakReference<TextView> logViewRef = new WeakReference<>(null);
    private static WeakReference<ScrollView> logScrollRef = new WeakReference<>(null);
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable filePoller = null;

    public static LinearLayout build(Activity activity) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);

        ScrollView logScroll = new ScrollView(activity);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400));
        logScrollRef = new WeakReference<>(logScroll);

        TextView logView = new TextView(activity);
        logView.setTextColor(Color.argb(200, 150, 255, 150));
        logView.setTextSize(10);
        logView.setPadding(8, 8, 8, 8);
        logViewRef = new WeakReference<>(logView);

        // まず既存のlogListを表示
        refreshFromMemory(logView);

        logScroll.addView(logView);
        page.addView(logScroll);

        // クリアボタン
        TextView clearBtn = new TextView(activity);
        clearBtn.setText("[ ログクリア ]");
        clearBtn.setTextColor(Color.YELLOW);
        clearBtn.setTextSize(12);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(0, 8, 0, 0);
        clearBtn.setOnClickListener(v -> {
            synchronized (StateHolder.logList) {
                StateHolder.logList.clear();
            }
            try {
                new java.io.FileWriter(
                        "/data/data/jp.nanameue.yay/cache/kyomutool_log.txt", false).close();
            } catch (Throwable ignored) {}
            TextView lv = logViewRef.get();
            if (lv != null) lv.setText("");
        });
        page.addView(clearBtn);

        // ファイルポーリング開始（2秒ごと）
        startFilePolling();

        return page;
    }

    private static void refreshFromMemory(TextView logView) {
        StringBuilder sb = new StringBuilder();
        synchronized (StateHolder.logList) {
            for (String s : StateHolder.logList) sb.append(s).append("\n");
        }
        logView.setText(sb.toString());
    }

    private static void startFilePolling() {
        stopFilePolling();
        filePoller = new Runnable() {
            @Override public void run() {
                TextView lv = logViewRef.get();
                if (lv == null) {
                    stopFilePolling();
                    return;
                }
                // ファイルから読み込み
                try {
                    java.io.File f = new java.io.File(
                            "/data/data/jp.nanameue.yay/cache/kyomutool_log.txt");
                    if (f.exists()) {
                        StringBuilder sb = new StringBuilder();
                        BufferedReader br = new BufferedReader(new FileReader(f));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        br.close();
                        String text = sb.toString();
                        if (!text.equals(lv.getText().toString())) {
                            lv.setText(text);
                            // 末尾にスクロール
                            ScrollView sv = logScrollRef.get();
                            if (sv != null) sv.post(() -> {
                                ScrollView s = logScrollRef.get();
                                if (s != null) s.fullScroll(View.FOCUS_DOWN);
                            });
                        }
                    }
                } catch (Throwable ignored) {}

                handler.postDelayed(this, 2000);
            }
        };
        handler.postDelayed(filePoller, 2000);
    }

    public static void stopFilePolling() {
        if (filePoller != null) {
            handler.removeCallbacks(filePoller);
            filePoller = null;
        }
    }

    public static void resetRefs() {
        stopFilePolling();
        logViewRef   = new WeakReference<>(null);
        logScrollRef = new WeakReference<>(null);
    }
}
