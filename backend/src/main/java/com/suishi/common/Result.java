package com.suishi.common;

import org.slf4j.MDC;

public record Result<T>(String code, String msg, T data, String traceId) {
    public static <T> Result<T> ok(T data) { return new Result<>("OK", "成功", data, MDC.get("traceId")); }
    public static Result<Void> error(String code, String message) { return new Result<>(code, message, null, MDC.get("traceId")); }
}
