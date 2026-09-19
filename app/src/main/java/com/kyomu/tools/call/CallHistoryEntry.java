/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.call;

/**
 * 通話履歴の1件分のデータクラス。
 */
public class CallHistoryEntry {

    public long   callId;
    public long   postId;
    public long   hostId;
    public String hostNick;
    public String token;
    public String channel;
    public String account;
    public String time;

    public CallHistoryEntry(long callId, long postId, long hostId,
                            String hostNick, String token,
                            String channel, String account, String time) {
        this.callId   = callId;
        this.postId   = postId;
        this.hostId   = hostId;
        this.hostNick = hostNick;
        this.token    = token;
        this.channel  = channel;
        this.account  = account;
        this.time     = time;
    }

    /** Spinner表示用ラベル */
    public String label() {
        return hostNick + " (" + callId + ")";
    }
}
