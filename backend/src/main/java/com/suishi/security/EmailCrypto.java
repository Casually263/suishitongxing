package com.suishi.security;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class EmailCrypto {
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec lookupKey;
    private final SecureRandom random = new SecureRandom();
    public EmailCrypto(SecurityProperties properties) {
        encryptionKey = new SecretKeySpec(Base64.getDecoder().decode(properties.emailKey()), "AES");
        lookupKey = new SecretKeySpec(Base64.getDecoder().decode(properties.emailHmacKey()), "HmacSHA256");
    }
    public String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public String lookup(String email) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(lookupKey); return HexFormat.of().formatHex(mac.doFinal(normalize(email).getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("Email lookup unavailable"); }
    }
    public String encrypt(String email) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(normalize(email).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Email encryption unavailable"); }
    }
}
