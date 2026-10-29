package com.suishi.system.vo;

import com.suishi.system.entity.User;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public record UserView(String id, String username, String cultureRole, String status, boolean adultConfirmed,
                       boolean emailVerified, int authVersion, List<String> roles, List<String> permissions, Instant createdAt) {
    public static UserView of(User user, List<String> roles, List<String> permissions) {
        return new UserView(user.id.toString(), user.username, user.roleType, user.status, Boolean.TRUE.equals(user.adultStatus),
                Boolean.TRUE.equals(user.emailVerified), user.authVersion, roles, permissions, user.createdAt == null ? null : user.createdAt.toInstant(ZoneOffset.UTC));
    }
}
