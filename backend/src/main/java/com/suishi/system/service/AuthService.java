package com.suishi.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.suishi.common.BusinessException;
import com.suishi.security.*;
import com.suishi.system.dto.LoginRequest;
import com.suishi.system.entity.User;
import com.suishi.system.mapper.LogMapper;
import com.suishi.system.mapper.UserMapper;
import com.suishi.system.vo.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class AuthService {
    public record LoginView(String accessToken,long expiresIn,UserView user) {}
    public record Issued(LoginView view,String refreshToken) {}
    private final UserMapper users;
    private final LogMapper logs;
    private final AccountService accounts;
    private final EmailCrypto email;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RedisSessionStore sessions;
    private final SecurityProperties properties;
    private final String dummyHash;
    public AuthService(UserMapper users,LogMapper logs,AccountService accounts,EmailCrypto email,PasswordEncoder passwords,JwtService jwt,RedisSessionStore sessions,SecurityProperties properties) {
        this.users=users;this.logs=logs;this.accounts=accounts;this.email=email;this.passwords=passwords;this.jwt=jwt;this.sessions=sessions;this.properties=properties;
        dummyHash=passwords.encode(sessions.randomToken());
    }
    public Issued login(LoginRequest request,String channel) {
        User user=users.findByIdentifier(request.identifier().strip().toLowerCase(Locale.ROOT),email.lookup(request.identifier()));
        boolean valid=passwords.matches(request.password(),user==null?dummyHash:user.passwordHash);
        if(user==null || !valid) { logs.login(IdWorker.getId(),null,channel,false,"BAD_CREDENTIALS"); throw unauthorized("账号或密码错误"); }
        if(!"ACTIVE".equals(user.status)) { logs.login(IdWorker.getId(),user.id,channel,false,"ACCOUNT_DISABLED"); throw new BusinessException(HttpStatus.FORBIDDEN,"ACCOUNT_DISABLED","账号已停用"); }
        var principal=accounts.principal(user,channel);
        if(channel.equals("admin") && !principal.permissions().contains("admin:access")) { logs.login(IdWorker.getId(),user.id,channel,false,"FORBIDDEN"); throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","当前账号没有管理端权限"); }
        Issued issued=issue(principal); logs.login(IdWorker.getId(),user.id,channel,true,"OK"); return issued;
    }
    public Issued refresh(String token,String channel) {
        var consumed=sessions.consume(token); var session=consumed.session();
        if(consumed.reused()) { users.revokeIfVersion(session.userId(),session.authVersion()); throw unauthorized("刷新凭证已被重复使用，请重新登录"); }
        User user=users.selectById(session.userId());
        if(user==null || !"ACTIVE".equals(user.status) || user.authVersion!=session.authVersion() || !channel.equals(session.channel())) throw unauthorized("登录已失效，请重新登录");
        var principal=accounts.principal(user,channel);
        if(channel.equals("admin") && !principal.permissions().contains("admin:access")) throw unauthorized("管理权限已变更，请重新登录");
        return issue(principal);
    }
    public void logout(UserPrincipal principal,String refreshToken,String channel) {
        if(principal!=null) { users.revokeIfVersion(principal.id(),principal.authVersion()); return; }
        if(refreshToken!=null) {
            var session=sessions.consume(refreshToken).session();
            if(!channel.equals(session.channel())) throw unauthorized("登录凭证不匹配");
            users.revokeIfVersion(session.userId(),session.authVersion());
        }
    }
    private Issued issue(UserPrincipal principal) {
        String refresh=sessions.issue(principal);
        return new Issued(new LoginView(jwt.issue(principal),properties.accessSeconds(),accounts.user(principal.id())),refresh);
    }
    private BusinessException unauthorized(String message) { return new BusinessException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED",message); }
}
