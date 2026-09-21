/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.core;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.kyomu.tools.call.CallHistoryEntry;
import com.kyomu.tools.call.MuteTarget;

/**
 * アプリ全体で共有する static 状態変数の一元管理クラス。
 */
public class StateHolder {

    // ===== インスタンス化禁止 =====
    private StateHolder() {}

    // ===== Agora ロック =====
    public static final Object agoraLock = new Object();

    // ===== メイントグル =====
    public static volatile boolean kickBlock    = false;
    public static volatile boolean sdkMute      = false;
    public static volatile boolean appMuteBlock = false;
    public static volatile boolean serverKick   = false;
    public static volatile boolean adBlock      = true;
    public static volatile boolean screenShareAudioForce = false;


    // ===== UI表示状態 =====
    public static volatile boolean onLeftSide  = true;
    public static volatile float   savedBtnY   = 200f;
    public static volatile boolean menuVisible = false;
    public static volatile int     currentTab  = 0;

    // ===== 設定 =====
    public static volatile int menuAlpha   = 230;
    public static volatile int btnAlphaMode = 0;
    public static volatile int menuWidth   = 580;
    public static volatile int menuHeight  = 1000;

    // ===== Persist =====
    public static volatile boolean persistEnabled        = false;
    public static volatile int     sentCount             = 0;
    public static volatile int     selectedIntervalIndex = 1;
    public static volatile float   currentInterval       = 0.5f;
    public static final   float[]  INTERVAL_OPTIONS      = {0.1f, 0.3f, 0.5f, 1f, 5f};
    public static final   Object   persistLock           = new Object();

    // ===== ClassLoader =====
    public static volatile ClassLoader appClassLoader = null;

    // ===== Activity（WeakReference） =====
    public static volatile WeakReference<Activity> lastActivityRef =
            new WeakReference<>(null);

    public static Activity getLastActivity() {
        Activity a = lastActivityRef.get();
        if (a != null && !a.isFinishing() && !a.isDestroyed()) return a;
        return null;
    }

    // ===== Agora（WeakReference） =====
    public static volatile WeakReference<Object> cachedAgoraARef =
            new WeakReference<>(null);

    public static Object getAgoraA() {
        synchronized (agoraLock) {
            return cachedAgoraARef.get();
        }
    }

    public static void setAgoraA(Object obj) {
        synchronized (agoraLock) {
            cachedAgoraARef = new WeakReference<>(obj);
        }
    }

    // ===== CallViewModel（WeakReference） =====
    // 修正: 元は Object のまま保持していたためメモリリークの原因になっていた
    public static volatile WeakReference<Object> cachedCallViewModelRef =
            new WeakReference<>(null);

    public static Object getCachedCallViewModel() {
        return cachedCallViewModelRef.get();
    }

    public static void setCachedCallViewModel(Object obj) {
        cachedCallViewModelRef = new WeakReference<>(obj);
    }

    // ===== VIP偽装（WeakReference） =====
    public static volatile WeakReference<Object> cachedMyRealmUserRef =
            new WeakReference<>(null);

    // ===== ミュート管理 =====
    public static final List<MuteTarget> muteTargets =
            Collections.synchronizedList(new ArrayList<>());
    public static volatile long    currentCallId  = 0;
    public static volatile String  myUserId       = "";
    public static volatile String  myCallUserUuid = "";
    public static volatile String  agoraChannel   = "";
    public static volatile boolean inCall         = false;
    public static volatile boolean muteResetDone  = false;

    // ===== Call タブ =====
    public static final List<CallHistoryEntry> callHistory =
            Collections.synchronizedList(new ArrayList<>());
    public static final int CALL_HISTORY_MAX = 10;

    public static volatile long   tmpCallId   = 0;
    public static volatile long   tmpHostId   = 0;
    public static volatile long   tmpPostId   = 0;
    public static volatile String tmpHostNick = "";
    public static volatile String tmpToken    = "";
    public static volatile String tmpChannel  = "";
    public static volatile String tmpAccount  = "";
    public static volatile Object retainedGcvm = null;

    public static volatile boolean callBlockLeave  = false;
    public static volatile boolean callIsRejoining = false;
    public static volatile float   callRejoinDelay = 1.5f;
    public static volatile String  detectedCallInitializerClass = null;


    // ===== ログ =====
    public static final List<String> logList =
            Collections.synchronizedList(new ArrayList<>());
    public static final int LOG_MAX = 200;

    // ===== ログイン =====
    public static volatile boolean loginHookEnabled = false;

    // ===== テストページ =====
    public static final List<String> testResultLog =
            Collections.synchronizedList(new ArrayList<>());
    public static final int TEST_LOG_MAX = 50;

    // ===== AgoraDelegate kick注入（HookManagerで使用） =====

    /**
     * RTM受信フック内で kick/kickout コマンドの uuid を差し替える。
     * 空文字 = 無効。1回実行後に自動クリア。
     */
    public static volatile String injectKickUuid        = "";

    /**
     * AgoraDelegate.c() フック内で kick action の uuid を強制書き換える。
     */
    public static volatile boolean forceKickViaDelegate = false;
    public static volatile String  forcedKickUuid       = "";

    /** AgoraDelegate の kick ordinal（ログから自動検出・保存） */
    public static volatile int     kickActionOrdinal    = -1;

    // ===== ホストUUID（uuidMap逆引きで取得） =====

    /**
     * uuidMap を tmpHostId で逆引きして得たホストの callUserUuid。
     * TestPage の「ホストUUID取得」ボタンで更新される。
     */
    public static volatile String  detectedHostUuid    = "";

    // ===== RTM publisherId スプーフィング =====

    /**
     * true のとき、次の RtcEngine.joinChannelWithUserAccount() 呼び出しで
     * userAccount 引数（= RTMのpublisherId になる）を spoofPublisherUuid に差し替える。
     * 差し替え後は自動で false に戻す（1回限り）。
     *
     * 目的: RTMチャンネルにホストのUUIDで参加することで、
     *       kick コマンド受信側の publisherId == hostUuid チェックを通過させる。
     */
    public static volatile boolean spoofPublisherId   = false;
    public static volatile String  spoofPublisherUuid = "";
}