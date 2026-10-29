package com.suishi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Base64;
import java.util.List;

@ConfigurationProperties("app.security")
public record SecurityProperties(String jwtKey, String emailKey, String emailHmacKey, String issuer, String audience,
                                 long accessSeconds, long refreshSeconds, boolean cookieSecure, List<String> allowedOrigins,
                                 int loginLimit, int registerLimit) {
    public SecurityProperties {
        for (String key : new String[]{jwtKey, emailKey, emailHmacKey}) {
            if (key == null || Base64.getDecoder().decode(key).length != 32) throw new IllegalArgumentException("Security keys must each be independent base64-encoded 32-byte keys");
        }
        if (jwtKey.equals(emailKey) || jwtKey.equals(emailHmacKey) || emailKey.equals(emailHmacKey)) throw new IllegalArgumentException("Security keys must be distinct");
        if (accessSeconds <= 0 || refreshSeconds <= 0 || allowedOrigins == null || allowedOrigins.isEmpty()) throw new IllegalArgumentException("Invalid security configuration");
    }
}
