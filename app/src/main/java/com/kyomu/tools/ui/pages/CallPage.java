/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.ui.pages;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.kyomu.tools.call.CallHistoryEntry;
import com.kyomu.tools.call.CallManager;
import com.kyomu.tools.core.HookManager;
import com.kyomu.tools.core.StateHolder;
import com.kyomu.tools.ui.UIHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class CallPage {

    private static WeakReference<Spinner>  callSpinnerRef  = new WeakReference<>(null);
    private static WeakReference<TextView> callInfoViewRef = new WeakReference<>(null);
    private static WeakReference<TextView> callStatusViewRef = new WeakReference<>(null);

    public static LinearLayout build(Activity activity) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 4, 0, 0);

        TextView callTitle = new TextView(activity);
        callTitle.setText("── 通話履歴・再参加 ──");
        callTitle.setTextColor(Color.argb(200, 80, 200, 255));
        callTitle.setTextSize(12);
        callTitle.setGravity(Gravity.CENTER);
        callTitle.setPadding(0, 0, 0, 8);
        page.addView(callTitle);

        // SAVEボタン
        TextView saveBtn = new TextView(activity);
        saveBtn.setText("SAVE（現在の通話を保存）");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(12);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(16, 10, 16, 10);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(Color.argb(255, 60, 120, 200));
        saveBg.setCornerRadius(UIHelper.dp(8, activity));
        saveBtn.setBackground(saveBg);
        saveBtn.setOnClickListener(v -> {
            CallManager.saveCurrentCall();
            updateCallSpinner(activity);
            updateCallInfo(activity);
        });
        page.addView(saveBtn);

        // スピナー
        Spinner callSpinner = new Spinner(activity);
        callSpinner.setBackgroundColor(Color.argb(255, 50, 50, 50));
        callSpinnerRef = new WeakReference<>(callSpinner);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        spinLp.setMargins(0, 8, 0, 4);
        page.addView(callSpinner, spinLp);
        updateCallSpinner(activity);

        callSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateCallInfo(activity);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 通話情報
        TextView callInfoView = new TextView(activity);
        callInfoView.setTextColor(Color.GRAY);
        callInfoView.setTextSize(10);
        callInfoView.setPadding(4, 4, 4, 8);
        callInfoView.setText("通話を選択してください");
        callInfoViewRef = new WeakReference<>(callInfoView);
        page.addView(callInfoView);
        updateCallInfo(activity);

        // ステータス
        TextView callStatusView = new TextView(activity);
        callStatusView.setTextColor(Color.GRAY);
        callStatusView.setTextSize(10);
        callStatusView.setGravity(Gravity.CENTER);
        callStatusView.setPadding(0, 0, 0, 8);
        callStatusViewRef = new WeakReference<>(callStatusView);
        page.addView(callStatusView);

        // ボタン行
        LinearLayout callBtnRow = new LinearLayout(activity);
        callBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        callBtnRow.setGravity(Gravity.CENTER);
        callBtnRow.setPadding(0, 0, 0, 8);
        LinearLayout.LayoutParams callBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        callBtnLp.setMargins(6, 0, 6, 0);

        TextView rejoinBtn = UIHelper.makeMuteActionBtn(activity, "REJOIN",
                Color.argb(255, 60, 160, 60));
        rejoinBtn.setOnClickListener(v -> {
            Spinner cs = callSpinnerRef.get();
            if (cs == null) return;
            int idx = cs.getSelectedItemPosition();
            synchronized (StateHolder.callHistory) {
                if (idx >= 0 && idx < StateHolder.callHistory.size())
                    CallManager.doCallRejoin(StateHolder.callHistory.get(idx));
                else updateCallStatus(activity, "通話を選択してください", Color.GRAY);
            }
        });

        TextView sdkBtn = UIHelper.makeMuteActionBtn(activity, "SDK",
                Color.argb(255, 200, 140, 60));
        sdkBtn.setOnClickListener(v -> {
            Spinner cs = callSpinnerRef.get();
            if (cs == null) return;
            int idx = cs.getSelectedItemPosition();
            synchronized (StateHolder.callHistory) {
                if (idx >= 0 && idx < StateHolder.callHistory.size())
                    CallManager.doCallSdkRejoin(StateHolder.callHistory.get(idx));
                else updateCallStatus(activity, "通話を選択してください", Color.GRAY);
            }
        });

        TextView deleteBtn = UIHelper.makeMuteActionBtn(activity, "DELETE",
                Color.argb(255, 200, 60, 60));
        deleteBtn.setOnClickListener(v -> {
            Spinner cs = callSpinnerRef.get();
            if (cs == null) return;
            int idx = cs.getSelectedItemPosition();
            synchronized (StateHolder.callHistory) {
                if (idx >= 0 && idx < StateHolder.callHistory.size()) {
                    CallHistoryEntry removed = StateHolder.callHistory.remove(idx);
                    updateCallSpinner(activity);
                    updateCallInfo(activity);
                    updateCallStatus(activity, "削除: " + removed.hostNick, Color.YELLOW);
                }
            }
        });

        callBtnRow.addView(rejoinBtn, callBtnLp);
        callBtnRow.addView(sdkBtn, callBtnLp);
        callBtnRow.addView(deleteBtn, callBtnLp);
        page.addView(callBtnRow);

        page.addView(UIHelper.makeDivider(activity));

        // 音量リセット
        TextView resetLabel = new TextView(activity);
        resetLabel.setText("── 音量リセット（現在の通話） ──");
        resetLabel.setTextColor(Color.argb(200, 200, 200, 200));
        resetLabel.setTextSize(11);
        resetLabel.setGravity(Gravity.CENTER);
        resetLabel.setPadding(0, 0, 0, 4);
        page.addView(resetLabel);

        TextView resetVolumeBtn = new TextView(activity);
        resetVolumeBtn.setText("RESET（SDK再接続）");
        resetVolumeBtn.setTextColor(Color.WHITE);
        resetVolumeBtn.setTextSize(12);
        resetVolumeBtn.setGravity(Gravity.CENTER);
        resetVolumeBtn.setPadding(16, 10, 16, 10);
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setColor(Color.argb(255, 140, 60, 200));
        resetBg.setCornerRadius(UIHelper.dp(8, activity));
        resetVolumeBtn.setBackground(resetBg);
        resetVolumeBtn.setOnClickListener(v -> CallManager.doResetVolume());
        page.addView(resetVolumeBtn);

        // 再接続待機
        final TextView delayLabel = new TextView(activity);
        delayLabel.setText("再接続待機: " + StateHolder.callRejoinDelay + "秒");
        delayLabel.setTextColor(Color.argb(200, 200, 200, 200));
        delayLabel.setTextSize(11);
        delayLabel.setGravity(Gravity.CENTER);
        delayLabel.setPadding(0, 8, 0, 0);
        page.addView(delayLabel);

        SeekBar delayBar = new SeekBar(activity);
        delayBar.setMax(50);
        delayBar.setProgress((int) (StateHolder.callRejoinDelay * 10));
        delayBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                StateHolder.callRejoinDelay = progress / 10f;
                delayLabel.setText("再接続待機: " + StateHolder.callRejoinDelay + "秒");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                HookManager.log("[Call] 再接続待機: " + StateHolder.callRejoinDelay + "秒");
            }
        });
        page.addView(delayBar);

        // 全履歴クリア
        TextView clearAllBtn = new TextView(activity);
        clearAllBtn.setText("[ 全履歴クリア ]");
        clearAllBtn.setTextColor(Color.YELLOW);
        clearAllBtn.setTextSize(11);
        clearAllBtn.setGravity(Gravity.CENTER);
        clearAllBtn.setPadding(0, 16, 0, 0);
        clearAllBtn.setOnClickListener(v -> {
            synchronized (StateHolder.callHistory) { StateHolder.callHistory.clear(); }
            updateCallSpinner(activity);
            updateCallInfo(activity);
            updateCallStatus(activity, "全履歴クリア", Color.YELLOW);
        });
        page.addView(clearAllBtn);

        TextView callNote = new TextView(activity);
        callNote.setText("※ REJOIN: 通話外から実行（別通話に一度参加が必要）\n"
                + "※ SDK: BAN回避（音声のみ・UIは透明）\n"
                + "※ RESET: 現在通話のSDK再接続（音量0対策）");
        callNote.setTextColor(Color.argb(150, 180, 180, 180));
        callNote.setTextSize(9);
        callNote.setPadding(0, 12, 0, 0);
        page.addView(callNote);
        return page;
    }

    public static void updateCallSpinner(Activity activity) {
        Spinner cs = callSpinnerRef.get();
        if (cs == null || activity == null) return;
        activity.runOnUiThread(() -> {
            Spinner spinner = callSpinnerRef.get();
            if (spinner == null) return;
            List<String> labels = new ArrayList<>();
            synchronized (StateHolder.callHistory) {
                for (int i = 0; i < StateHolder.callHistory.size(); i++) {
                    CallHistoryEntry e = StateHolder.callHistory.get(i);
                    labels.add("#" + (i + 1) + " " + e.hostNick + " (" + e.callId + ")");
                }
            }
            if (labels.isEmpty()) labels.add("（履歴なし）");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        });
    }

    public static void updateCallInfo(Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            TextView civ = callInfoViewRef.get();
            Spinner cs   = callSpinnerRef.get();
            if (civ == null) return;
            int idx = cs != null ? cs.getSelectedItemPosition() : -1;
            synchronized (StateHolder.callHistory) {
                if (idx >= 0 && idx < StateHolder.callHistory.size()) {
                    CallHistoryEntry e = StateHolder.callHistory.get(idx);
                    civ.setText("callId: " + e.callId + "\npostId: " + e.postId
                            + "\nhost: " + e.hostNick + " (" + e.hostId + ")"
                            + "\ntime: " + e.time);
                    civ.setTextColor(Color.WHITE);
                } else {
                    civ.setText("通話を選択してください");
                    civ.setTextColor(Color.GRAY);
                }
            }
        });
    }

    public static void updateCallStatus(Activity activity, String msg, int color) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            TextView csv = callStatusViewRef.get();
            if (csv != null) { csv.setText(msg); csv.setTextColor(color); }
        });
    }

    public static void resetRefs() {
        callSpinnerRef    = new WeakReference<>(null);
        callInfoViewRef   = new WeakReference<>(null);
        callStatusViewRef = new WeakReference<>(null);
    }
}
