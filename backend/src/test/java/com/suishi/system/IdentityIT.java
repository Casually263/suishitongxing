package com.suishi.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.security.RedisSessionStore;
import com.suishi.security.UserPrincipal;
import com.suishi.system.mapper.UserMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="RUN_INTEGRATION_TESTS",matches="true")
class IdentityIT {
    private static final String PASSWORD="Test-only-password-2026";
    private static final String ORIGIN="http://localhost:5173";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired UserMapper users;
    @Autowired RedisSessionStore sessions;
    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv().getOrDefault("IT_DB_URL","jdbc:mysql://127.0.0.1:3307/suishi_it?connectionTimeZone=UTC"));
        registry.add("app.security.jwt-key",IdentityIT::key); registry.add("app.security.email-key",IdentityIT::key); registry.add("app.security.email-hmac-key",IdentityIT::key);
        registry.add("app.redis-prefix",()->"suishi:it:"+UUID.randomUUID()+":");
        registry.add("app.bootstrap.enabled",()->false); registry.add("app.bootstrap.demo-enabled",()->false);
        registry.add("app.security.login-limit",()->1000); registry.add("app.security.register-limit",()->1000);
    }
    static String key(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
    @BeforeEach void cleanOnlyIsolatedSchema() {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("suishi_it");
        for(String table:List.of("ai_chat_log","ai_usage_progress","ai_usage_daily","ai_knowledge_review","story_action","story_submission","story_reward","story_dual_round","story_progress","story_session","sys_oper_log","sys_login_log","user_tag","sys_user_role","user_profile","sys_user")) db.update("DELETE FROM "+table);
    }
    Map<String,Object> registration(String username) {
        return new LinkedHashMap<>(Map.of("username",username,"email",username+"@example.test","password",PASSWORD,"cultureRole","GUIDE","adultConfirmed",true,"acceptedTerms",true,"termsVersion","2026-10"));
    }
    String register(String username) throws Exception {
        var result=mvc.perform(post("/api/portal/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(registration(username))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("OK")).andReturn();
        return body(result).path("data").path("userId").asText();
    }
    MvcResult login(String identifier,String channel) throws Exception {
        return mvc.perform(post("/api/"+channel+"/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("identifier",identifier,"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn();
    }
    String token(MvcResult result) throws Exception{return body(result).path("data").path("accessToken").asText();}
    JsonNode body(MvcResult result) throws Exception{return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
    Cookie refreshCookie(MvcResult result){return result.getResponse().getCookie("suishi_refresh_portal");}
    MvcResult csrf() throws Exception{return mvc.perform(get("/api/portal/auth/csrf")).andExpect(status().isOk()).andReturn();}
    @Test void registrationPersistsEncryptedIdentityAndVersionedProfileAcrossAliases() throws Exception {
        String id=register("guide_one"); assertThat(id).matches("[0-9]+");
        Map<String,Object> stored=db.queryForMap("SELECT * FROM sys_user WHERE id=?",id);
        assertThat(stored.get("password_hash").toString()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(stored.get("email_enc").toString()).doesNotContain("guide_one@example.test");
        assertThat(stored.get("email_lookup").toString()).hasSize(64);
        var login=login("GUIDE_ONE@EXAMPLE.TEST","portal"); String bearer="Bearer "+token(login);
        assertThat(refreshCookie(login).isHttpOnly()).isTrue();assertThat(refreshCookie(login).getPath()).isEqualTo("/api/portal/auth");
        mvc.perform(get("/api/app/auth/me").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id)).andExpect(jsonPath("$.data.roles[0]").value("GUIDE"));
        Map<String,Object> p=new LinkedHashMap<>(Map.of("displayName","向导一","country","中国","nativeLang","zh","learnLang","en","langLevel",Map.of("zh",5,"en",3),"timezone","Asia/Shanghai","availSlots",List.of(Map.of("dayOfWeek",1,"start","18:00","end","20:00")),"tagIds",List.of("1","2"),"privacy",Map.of("showCountry",false,"showAvatar",false),"notificationPreferences",Map.of("termReminders",true,"partnerReminders",false)));
        p.put("expectedVersion",0);
        mvc.perform(put("/api/portal/profile").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(get("/api/app/profile").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("向导一")).andExpect(jsonPath("$.data.tagIds[1]").value("2"));
        mvc.perform(put("/api/app/profile").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM user_tag WHERE user_id=?",Integer.class,id)).isEqualTo(2);
    }
    @Test void registrationRejectsPrivilegeInjectionDuplicatesAndOversizedUtf8Password() throws Exception {
        register("guide_one");
        var payload=registration("GUIDE_ONE");
        mvc.perform(post("/api/portal/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isConflict());
        payload=registration("guide_two");payload.put("email","GUIDE_ONE@EXAMPLE.TEST");
        mvc.perform(post("/api/portal/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isConflict());
        payload=registration("guide_two");payload.put("cultureRole","ADMIN");
        mvc.perform(post("/api/portal/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        payload=registration("guide_two");payload.put("roles",List.of("ADMIN"));
        mvc.perform(post("/api/portal/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        payload=registration("guide_two");payload.put("password","春".repeat(25));
        mvc.perform(post("/api/portal/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        assertThat(users.countUsers()).isEqualTo(1);
    }
    @Test void adminChannelAndPermissionsAreEnforcedAndDisableRevokesExistingJwt() throws Exception {
        String target=register("guide_one"),adminId=register("admin_one");users.addRole(Long.valueOf(adminId),3L);
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer broken")).andExpect(status().isUnauthorized());
        String memberToken=token(login("guide_one","portal"));
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+memberToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("identifier","guide_one","password",PASSWORD)))).andExpect(status().isForbidden());
        String adminPortal=token(login("admin_one","portal"));
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+adminPortal)).andExpect(status().isForbidden());
        String adminToken=token(login("admin_one","admin"));
        var list=mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain("passwordHash","emailEnc","emailLookup",PASSWORD);
        mvc.perform(put("/api/admin/users/"+target+"/status").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\",\"expectedAuthVersion\":0}")).andExpect(status().isOk());
        mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer "+memberToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("identifier","guide_one","password",PASSWORD)))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/"+target+"/status").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"expectedAuthVersion\":1}")).andExpect(status().isOk());
        login("guide_one","portal");
        mvc.perform(put("/api/admin/users/"+adminId+"/status").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\",\"expectedAuthVersion\":0}")).andExpect(status().isConflict());
    }
    @Test void refreshRequiresCsrfAndOriginRotatesOnceAndDetectsReplay() throws Exception {
        register("guide_one");var login=login("guide_one","portal");var csrf=csrf();
        Cookie refresh=refreshCookie(login),csrfCookie=csrf.getResponse().getCookie("suishi_csrf_portal");String csrfToken=body(csrf).path("data").path("csrfToken").asText();
        mvc.perform(post("/api/portal/auth/refresh").cookie(refresh)).andExpect(status().isForbidden());
        mvc.perform(post("/api/portal/auth/refresh").cookie(refresh,csrfCookie).header("Origin","https://untrusted.invalid").header("X-CSRF-Token",csrfToken)).andExpect(status().isForbidden());
        var rotated=mvc.perform(post("/api/portal/auth/refresh").cookie(refresh,csrfCookie).header("Origin",ORIGIN).header("X-CSRF-Token",csrfToken)).andExpect(status().isOk()).andReturn();
        assertThat(refreshCookie(rotated).getValue()).isNotEqualTo(refresh.getValue());
        mvc.perform(post("/api/portal/auth/refresh").cookie(refresh,csrfCookie).header("Origin",ORIGIN).header("X-CSRF-Token",csrfToken)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer "+token(rotated))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/portal/auth/refresh").cookie(refreshCookie(rotated),csrfCookie).header("Origin",ORIGIN).header("X-CSRF-Token",csrfToken)).andExpect(status().isUnauthorized());
        String relogin=token(login("guide_one","portal"));
        mvc.perform(post("/api/portal/auth/refresh").cookie(refresh,csrfCookie).header("Origin",ORIGIN).header("X-CSRF-Token",csrfToken)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer "+relogin)).andExpect(status().isOk());
    }
    @Test void logoutInvalidatesJwtAndRefreshOnTheServer() throws Exception {
        register("guide_one");var login=login("guide_one","portal");var csrf=csrf();
        String token=token(login),csrfToken=body(csrf).path("data").path("csrfToken").asText();Cookie csrfCookie=csrf.getResponse().getCookie("suishi_csrf_portal");
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+token).cookie(refreshCookie(login),csrfCookie).header("Origin",ORIGIN).header("X-CSRF-Token",csrfToken)).andExpect(status().isOk()).andExpect(cookie().maxAge("suishi_refresh_portal",0));
        mvc.perform(get("/api/app/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/portal/auth/refresh").cookie(refreshCookie(login),csrfCookie).header("Origin",ORIGIN).header("X-CSRF-Token",csrfToken)).andExpect(status().isUnauthorized());
    }
    @Test void redisConsumeIsAtomicAndRateLimitIsEnforced() throws Exception {
        String token=sessions.issue(new UserPrincipal(1L,"test",0,"portal",List.of("GUIDE"),List.of()));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var results=pool.invokeAll(List.<Callable<RedisSessionStore.Consumed>>of(()->sessions.consume(token),()->sessions.consume(token)));
            assertThat(results.stream().map(result->{try{return result.get().reused();}catch(Exception e){throw new RuntimeException(e);}}).toList()).containsExactlyInAnyOrder(false,true);
        } finally {pool.shutdownNow();}
        String scope=UUID.randomUUID().toString();sessions.limit("test",scope,2);sessions.limit("test",scope,2);
        assertThatThrownBy(()->sessions.limit("test",scope,2)).isInstanceOf(com.suishi.common.BusinessException.class).hasMessageContaining("频繁");
    }
    @Test void rejectsMalformedRequestsWithUniformSafeErrors() throws Exception {
        mvc.perform(get("/api/portal/auth/login")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED")).andExpect(jsonPath("$.traceId").isString());
        mvc.perform(post("/api/portal/auth/login").contentType(MediaType.TEXT_PLAIN).content("invalid")).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        var result=mvc.perform(get("/api/common/meta")).andExpect(status().isOk()).andExpect(jsonPath("$.data.interestTags[0].id").isString()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password","jwtKey","emailKey","stackTrace");
    }
}
