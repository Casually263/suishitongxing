package com.suishi.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Result<Void>> business(BusinessException e) {
        var response=ResponseEntity.status(e.status());
        if(e.status().value()==429) response.header("Retry-After","60");
        return response.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(Result.error(e.code(), e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("参数不合法");
        return ResponseEntity.badRequest().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(Result.error("VALIDATION_ERROR", message));
    }
    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    ResponseEntity<Result<Void>> invalid(Exception e) { return ResponseEntity.badRequest().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(Result.error("VALIDATION_ERROR", "请求格式或参数不合法")); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Result<Void>> wrongMethod(HttpRequestMethodNotSupportedException e) { return ResponseEntity.status(405).body(Result.error("METHOD_NOT_ALLOWED","请求方法不支持")); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Result<Void>> wrongType(HttpMediaTypeNotSupportedException e) { return ResponseEntity.status(415).body(Result.error("UNSUPPORTED_MEDIA_TYPE","请使用支持的请求格式")); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Result<Void>> duplicate(DuplicateKeyException e) { return ResponseEntity.status(409).body(Result.error("ACCOUNT_EXISTS", "账号或邮箱已被使用")); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Result<Void>> forbidden(AccessDeniedException e) { return ResponseEntity.status(403).body(Result.error("FORBIDDEN", "没有执行此操作的权限")); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Result<Void>> missing(NoResourceFoundException e) { return ResponseEntity.status(404).body(Result.error("RESOURCE_NOT_FOUND", "资源不存在")); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Result<Void>> dependency(DataAccessException e) {
        LOG.warn("Data dependency unavailable, type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(503).body(Result.error("DEPENDENCY_UNAVAILABLE", "数据服务暂不可用，请稍后重试"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Result<Void>> unexpected(Exception e) {
        LOG.error("Unhandled request failure, type={}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Result.error("INTERNAL_ERROR", "服务暂时无法处理请求，请稍后重试"));
    }
}
