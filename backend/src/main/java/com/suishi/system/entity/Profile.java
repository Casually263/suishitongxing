package com.suishi.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_profile")
public class Profile {
    public Long id;
    public Long userId;
    public String displayName;
    public String country;
    public String nativeLang;
    public String learnLang;
    public String langLevel;
    public String timezone;
    public String availSlots;
    public String privacy;
    public String notificationPreferences;
    public Integer version;
}
