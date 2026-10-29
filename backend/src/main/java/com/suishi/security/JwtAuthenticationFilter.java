package com.suishi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.common.Result;
import com.suishi.system.entity.User;
import com.suishi.system.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserMapper users;
    private final ObjectMapper json;
    public JwtAuthenticationFilter(JwtService jwt, UserMapper users, ObjectMapper json) { this.jwt = jwt; this.users = users; this.json = json; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ") || header.length() > 4096) { reject(response, 401, "UNAUTHENTICATED", "无效的登录凭证"); return; }
                Claims claims = jwt.verify(header.substring(7));
                User user = users.selectById(Long.valueOf(claims.getSubject()));
                Integer version = claims.get("ver", Integer.class);
                String channel = claims.get("channel", String.class);
                if (user == null || version == null || !version.equals(user.authVersion) || !"ACTIVE".equals(user.status) || !("portal".equals(channel) || "admin".equals(channel))) {
                    reject(response, 401, "UNAUTHENTICATED", "登录已失效，请重新登录"); return;
                }
                UserPrincipal principal = new UserPrincipal(user.id, user.username, user.authVersion, channel, users.roles(user.id), users.permissions(user.id));
                if (request.getRequestURI().startsWith("/api/admin/") && !"admin".equals(channel)) { reject(response, 403, "FORBIDDEN", "请通过管理端重新登录"); return; }
                var authorities = new ArrayList<SimpleGrantedAuthority>();
                principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } catch (JwtException | IllegalArgumentException e) { reject(response, 401, "UNAUTHENTICATED", "登录凭证无效或已过期"); return; }
            catch (DataAccessException e) { reject(response, 503, "DEPENDENCY_UNAVAILABLE", "数据服务暂不可用"); return; }
        }
        chain.doFilter(request, response);
    }
    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        SecurityContextHolder.clearContext(); response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Result.error(code, message));
    }
}
