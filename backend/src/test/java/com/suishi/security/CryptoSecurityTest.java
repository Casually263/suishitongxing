package com.suishi.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CryptoSecurityTest {
    static String key() { byte[] key=new byte[32];new SecureRandom().nextBytes(key);return Base64.getEncoder().encodeToString(key); }
    static SecurityProperties properties() { return new SecurityProperties(key(),key(),key(),"suishi","suishi-web",900,604800,false,List.of("http://localhost:5173"),30,10); }
    @Test void encryptsEmailWithAuthenticatedRandomNonceAndStablePrivateLookup() throws Exception {
        var properties=properties(); var crypto=new EmailCrypto(properties);
        String first=crypto.encrypt(" Person@Example.COM "),second=crypto.encrypt("person@example.com");
        assertThat(first).isNotEqualTo(second).doesNotContain("person");
        assertThat(crypto.lookup(" Person@Example.COM ")).isEqualTo(crypto.lookup("person@example.com")).hasSize(64);
        byte[] packet=Base64.getDecoder().decode(first);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(properties.emailKey()),"AES"),new GCMParameterSpec(128,Arrays.copyOf(packet,12)));
        assertThat(new String(cipher.doFinal(Arrays.copyOfRange(packet,12,packet.length)),StandardCharsets.UTF_8)).isEqualTo("person@example.com");
        packet[packet.length-1]^=1;
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(properties.emailKey()),"AES"),new GCMParameterSpec(128,Arrays.copyOf(packet,12)));
        assertThatThrownBy(()->cipher.doFinal(Arrays.copyOfRange(packet,12,packet.length))).isInstanceOf(javax.crypto.AEADBadTagException.class);
    }
    @Test void verifiesIssuerAudienceSignatureAndVersionWithoutEmbeddingPrivateData() {
        var properties=properties();var jwt=new JwtService(properties);
        var principal=new UserPrincipal(100L,"tester",4,"portal",List.of("GUIDE"),List.of("profile:write"));
        String token=jwt.issue(principal);var claims=jwt.verify(token);
        assertThat(claims.getSubject()).isEqualTo("100"); assertThat(claims.get("ver",Integer.class)).isEqualTo(4);
        assertThat(claims).doesNotContainKeys("email","password","roles","permissions");
        String forged=Jwts.builder().issuer("other").audience().add("suishi-web").and().subject("100")
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtKey())),Jwts.SIG.HS256).compact();
        assertThatThrownBy(()->jwt.verify(forged)).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->jwt.verify(token.substring(0,token.lastIndexOf('.')+1)+"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).isInstanceOf(JwtException.class);
    }
    @Test void refusesMissingWeakOrReusedServerKeys() {
        var p=properties();
        assertThatThrownBy(()->new SecurityProperties(p.jwtKey(),p.jwtKey(),p.emailHmacKey(),"a","b",900,900,false,List.of("http://localhost"),30,10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SecurityProperties("d2Vhaw==",p.emailKey(),p.emailHmacKey(),"a","b",900,900,false,List.of("http://localhost"),30,10)).isInstanceOf(IllegalArgumentException.class);
    }
}
