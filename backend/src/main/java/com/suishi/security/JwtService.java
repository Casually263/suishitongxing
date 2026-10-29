package com.suishi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecurityProperties properties;
    private final SecretKey key;
    public JwtService(SecurityProperties properties) {
        this.properties = properties; key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtKey()));
    }
    public String issue(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(properties.issuer()).audience().add(properties.audience()).and()
                .subject(principal.id().toString()).id(UUID.randomUUID().toString())
                .claim("ver", principal.authVersion()).claim("channel", principal.channel())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(properties.accessSeconds())))
                .signWith(key, Jwts.SIG.HS256).compact();
    }
    public Claims verify(String token) {
        var signed = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).requireAudience(properties.audience())
                .build().parseSignedClaims(token);
        if (!"HS256".equals(signed.getHeader().getAlgorithm())) throw new io.jsonwebtoken.UnsupportedJwtException("Only HS256 is accepted");
        return signed.getPayload();
    }
}
