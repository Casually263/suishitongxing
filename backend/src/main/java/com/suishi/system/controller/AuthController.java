package com.suishi.system.controller;

import com.suishi.common.BusinessException;
import com.suishi.common.Result;
import com.suishi.security.RedisSessionStore;
import com.suishi.security.SecurityProperties;
import com.suishi.security.UserPrincipal;
import com.suishi.system.dto.LoginRequest;
import com.suishi.system.dto.RegisterRequest;
import com.suishi.system.service.AccountService;
import com.suishi.system.service.AuthService;
import com.suishi.system.vo.UserView;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

@RestController
public class AuthController {
    private final AccountService accounts;
    private final AuthService auth;
    private final RedisSessionStore sessions;
    private final SecurityProperties properties;
    public AuthController(AccountService accounts,AuthService auth,RedisSessionStore sessions,SecurityProperties properties) { this.accounts=accounts;this.auth=auth;this.sessions=sessions;this.properties=properties; }

    @PostMapping("/api/portal/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Map<String,String>> register(@Valid @RequestBody RegisterRequest body,HttpServletRequest request) {
        sessions.limit("register",request.getRemoteAddr(),properties.registerLimit()); return Result.ok(Map.of("userId",accounts.register(body)));
    }
    @PostMapping({"/api/portal/auth/login","/api/admin/auth/login"})
    public Result<AuthService.LoginView> login(@Valid @RequestBody LoginRequest body,HttpServletRequest request,HttpServletResponse response) {
        sessions.limit("login",request.getRemoteAddr(),properties.loginLimit());
        String channel=channel(request); var issued=auth.login(body,channel); setRefresh(response,channel,issued.refreshToken(),properties.refreshSeconds());
        return Result.ok(issued.view());
    }
    @GetMapping({"/api/portal/auth/csrf","/api/admin/auth/csrf"})
    public Result<Map<String,String>> csrf(HttpServletRequest request,HttpServletResponse response) {
        String channel=channel(request); String token=cookie(request,"suishi_csrf_"+channel);
        if(token==null || !token.matches("[A-Za-z0-9_-]{64}")) token=sessions.randomToken();
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("suishi_csrf_"+channel,token).httpOnly(true).secure(properties.cookieSecure())
                .sameSite("Strict").path(path(channel)).maxAge(Duration.ofSeconds(properties.refreshSeconds())).build().toString());
        return Result.ok(Map.of("csrfToken",token));
    }
    @PostMapping({"/api/portal/auth/refresh","/api/admin/auth/refresh"})
    public Result<AuthService.LoginView> refresh(HttpServletRequest request,HttpServletResponse response) {
        String channel=channel(request); verifyBrowserRequest(request,channel);
        sessions.limit("refresh",request.getRemoteAddr(),properties.loginLimit()*2);
        var issued=auth.refresh(cookie(request,"suishi_refresh_"+channel),channel); setRefresh(response,channel,issued.refreshToken(),properties.refreshSeconds());
        return Result.ok(issued.view());
    }
    @PostMapping({"/api/portal/auth/logout","/api/admin/auth/logout"})
    public Result<Void> logout(@AuthenticationPrincipal UserPrincipal principal,HttpServletRequest request,HttpServletResponse response) {
        String channel=channel(request); verifyBrowserRequest(request,channel);
        try { auth.logout(principal,cookie(request,"suishi_refresh_"+channel),channel); }
        finally { setRefresh(response,channel,"",0); }
        return Result.ok(null);
    }
    @GetMapping({"/api/portal/auth/me","/api/app/auth/me","/api/admin/auth/me"})
    public Result<UserView> me(@AuthenticationPrincipal UserPrincipal principal) { return Result.ok(accounts.user(principal.id())); }

    private void verifyBrowserRequest(HttpServletRequest request,String channel) {
        String origin=request.getHeader("Origin"); String header=request.getHeader("X-CSRF-Token"); String token=cookie(request,"suishi_csrf_"+channel);
        if(origin==null || !properties.allowedOrigins().contains(origin) || header==null || token==null || header.length()>128
                || !MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(HttpStatus.FORBIDDEN,"CSRF_REJECTED","请求来源校验失败，请刷新后重试");
    }
    private void setRefresh(HttpServletResponse response,String channel,String token,long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("suishi_refresh_"+channel,token).httpOnly(true).secure(properties.cookieSecure())
                .sameSite("Strict").path(path(channel)).maxAge(Duration.ofSeconds(seconds)).build().toString());
    }
    private String channel(HttpServletRequest request) { return request.getRequestURI().startsWith("/api/admin/")?"admin":"portal"; }
    private String path(String channel) { return "/api/"+channel+"/auth"; }
    private String cookie(HttpServletRequest request,String name) {
        if(request.getCookies()!=null) for(Cookie value:request.getCookies()) if(value.getName().equals(name)) return value.getValue();
        return null;
    }
}
