package com.suishi.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class RedisSessionStore {
    public record Session(Long userId, int authVersion, String channel) {}
    public record Consumed(Session session, boolean reused) {}
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
        local value = redis.call('GET', KEYS[1])
        if value then
          redis.call('DEL', KEYS[1])
          redis.call('SET', KEYS[2], value, 'EX', ARGV[1])
          return 'OK:' .. value
        end
        local used = redis.call('GET', KEYS[2])
        if used then return 'REUSED:' .. used end
        return ''
        """, String.class);
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return count
        """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final SecurityProperties properties;
    private final String prefix;
    private final SecureRandom random = new SecureRandom();
    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper json, SecurityProperties properties, @Value("${app.redis-prefix}") String prefix) {
        this.redis = redis; this.json = json; this.properties = properties; this.prefix = prefix;
    }
    public String issue(UserPrincipal principal) {
        String token = randomToken();
        try { redis.opsForValue().set(prefix + "refresh:" + digest(token), json.writeValueAsString(new Session(principal.id(), principal.authVersion(), principal.channel())), Duration.ofSeconds(properties.refreshSeconds())); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Session encoding failed"); }
        return token;
    }
    public Consumed consume(String token) {
        if (token == null || token.length() > 128 || token.length() < 32) throw unauthorized();
        String hash = digest(token);
        String value = redis.execute(CONSUME, List.of(prefix + "refresh:" + hash, prefix + "refresh-used:" + hash), String.valueOf(properties.refreshSeconds()));
        if (value == null || value.isEmpty()) throw unauthorized();
        boolean reused = value.startsWith("REUSED:");
        try { return new Consumed(json.readValue(value.substring(reused ? 7 : 3), Session.class), reused); }
        catch (JsonProcessingException e) { throw unauthorized(); }
    }
    public void limit(String action, String address, int maximum) {
        Long count = redis.execute(RATE, List.of(prefix + "rate:" + action + ":" + digest(address)), "60");
        if (count == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "认证服务暂不可用");
        if (count > maximum) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请一分钟后重试");
    }
    public String randomToken() { byte[] value = new byte[48]; random.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private BusinessException unauthorized() { return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录已失效，请重新登录"); }
}
