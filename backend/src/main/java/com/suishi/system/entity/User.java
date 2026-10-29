package com.suishi.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_user")
public class User {
    public Long id;
    public String username;
    public String usernameNormalized;
    public String emailEnc;
    public String emailLookup;
    public String passwordHash;
    public String roleType;
    public String status;
    public Integer authVersion;
    public Boolean adultStatus;
    public Boolean emailVerified;
    public String termsVersion;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
