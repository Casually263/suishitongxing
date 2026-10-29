package com.suishi.system.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,64}", message="账号须为3–64位字母、数字或下划线") String username,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=72) String password,
        @NotNull CultureRole cultureRole,
        boolean adultConfirmed,
        @AssertTrue(message="请阅读并同意用户协议与隐私说明") boolean acceptedTerms,
        @NotBlank @Pattern(regexp="2026-10", message="请使用当前版本的用户协议") String termsVersion) {
    public enum CultureRole { GUIDE, PARTNER }
}
