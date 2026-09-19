/*
 * Copyright (c) 2025 KyomuTools
 * All rights reserved.
 * Unauthorized modification, redistribution, or commercial use is prohibited.
 */
package com.kyomu.tools.call;

/**
 * ミュート・キック対象の参加者データクラス。
 */
public class MuteTarget {

    public String  name;
    public String  appUserId;
    public String  callUserId;
    public String  avatarUrl;
    public boolean muted;
    public boolean kicked;
    public boolean guarded;

    public MuteTarget(String name, String appUserId,
                      String callUserId, String avatarUrl) {
        this.name       = name;
        this.appUserId  = appUserId;
        this.callUserId = callUserId;
        this.avatarUrl  = avatarUrl;
        this.muted      = false;
        this.kicked     = false;
        this.guarded    = false;
    }
}
