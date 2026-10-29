package com.suishi.story;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.suishi.system.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="RUN_INTEGRATION_TESTS",matches="true")
class StoryEditorIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db; @Autowired UserMapper users;
    private static final String PASSWORD="Editor-integration-password-2026";
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv().getOrDefault("IT_DB_URL","jdbc:mysql://127.0.0.1:3307/suishi_it?connectionTimeZone=UTC"));
        r.add("app.security.jwt-key",StoryEditorIT::key);r.add("app.security.email-key",StoryEditorIT::key);r.add("app.security.email-hmac-key",StoryEditorIT::key);
        r.add("app.redis-prefix",()->"suishi:editor-it:"+UUID.randomUUID()+":");r.add("app.bootstrap.enabled",()->false);r.add("app.bootstrap.demo-enabled",()->false);
        r.add("app.security.login-limit",()->1000);r.add("app.security.register-limit",()->1000);
    }
    static String key() { byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b); }
    @BeforeEach void clean() {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("suishi_it");
        for(String table:List.of("story_editor_preview","story_publication","story_editor_draft","ai_chat_log","ai_usage_progress","ai_usage_daily","ai_knowledge_review","story_action","story_submission","story_reward","story_dual_round","story_progress","story_session","sys_oper_log","sys_login_log","user_tag","sys_user_role","user_profile","sys_user"))db.update("DELETE FROM "+table);
        db.update("DELETE FROM story_edge WHERE story_id IN (SELECT id FROM story WHERE chapter_id<>1)");db.update("DELETE FROM story_node WHERE story_id IN (SELECT id FROM story WHERE chapter_id<>1)");db.update("DELETE FROM story WHERE chapter_id<>1");db.update("DELETE FROM knowledge_card WHERE chapter_id<>1");db.update("DELETE FROM chapter WHERE id<>1");
    }
    @AfterEach void cleanup() { clean(); }
    record Account(String id,String bearer) {}
    JsonNode body(MvcResult result)throws Exception { return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data"); }
    JsonNode call(Account account,MockHttpServletRequestBuilder request,Object payload,int status)throws Exception {
        if(account!=null)request.header("Authorization",account.bearer());
        if(payload!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        return body(mvc.perform(request).andExpect(status().is(status)).andReturn());
    }
    Account account(String name,Long role)throws Exception {
        JsonNode registered=call(null,post("/api/portal/auth/register"),Map.of("username",name,"email",name+"@example.test","password",PASSWORD,"cultureRole","GUIDE","adultConfirmed",true,"acceptedTerms",true,"termsVersion","2026-10"),201);
        String id=registered.path("userId").asText();if(role!=null)users.addRole(Long.valueOf(id),role);
        JsonNode login=call(null,post("/api/"+(role==null?"portal":"admin")+"/auth/login"),Map.of("identifier",name,"password",PASSWORD),200);
        return new Account(id,"Bearer "+login.path("accessToken").asText());
    }
    JsonNode fixture(Account admin)throws Exception { return call(admin,post("/api/admin/story/chapters"),Map.of("code","EDITOR_IT","title",Map.of("zh","编辑器集成测试篇章","en","Editor integration chapter"),"intro",Map.of("zh","独立集成测试，不代表正式内容","en","Isolated integration fixture, not reviewed cultural content"),"copyFromChapterId",1),200); }
    String id(JsonNode draft) { return draft.path("chapterId").asText(); }
    JsonNode save(Account admin,JsonNode draft,JsonNode graph,int status)throws Exception { return call(admin,put("/api/admin/story/chapters/"+id(draft)+"/draft"),Map.of("expectedRevision",draft.path("revision").asLong(),"graph",graph),status); }
    JsonNode publish(Account admin,JsonNode draft,int status)throws Exception { return call(admin,post("/api/admin/story/chapters/"+id(draft)+"/publish"),Map.of("expectedRevision",draft.path("revision").asLong(),"culturalChecked",true,"bilingualChecked",true,"note","Automated isolated fixture review; not a claim of human cultural approval"),status); }
    ObjectNode first(JsonNode graph) { return (ObjectNode)graph.path("nodes").get(0); }
    void title(JsonNode graph,String zh,String en) { ObjectNode t=(ObjectNode)first(graph).path("content").path("title");t.put("zh",zh).put("en",en); }
    JsonNode state(Account player,String chapter)throws Exception { return call(player,get("/api/portal/stories/"+chapter+"/state"),null,200); }
    JsonNode start(Account player,String chapter)throws Exception { return call(player,post("/api/portal/stories/"+chapter+"/start"),Map.of("mode","SCRIPT"),200); }
    JsonNode step(Account player,String chapter,JsonNode s,String action,String answer)throws Exception {
        Map<String,Object> request=new LinkedHashMap<>(Map.of("nodeId",s.path("node").path("id").asText(),"expectedVersion",s.path("version").asLong(),"requestId",UUID.randomUUID().toString(),"action",action));if(answer!=null)request.put("answer",answer);
        return call(player,post("/api/portal/stories/"+chapter+"/choose"),request,200);
    }
    JsonNode toDual(Account player,String chapter,JsonNode s)throws Exception { for(String action:List.of("continue","learn","continue","solar"))s=step(player,chapter,s,action,null);return s; }
    @Test void publishIsReadByRealEngineWhileOldSavesKeepContentScoresAndVersion()throws Exception {
        var admin=account("ed_admin",3L);var draft=fixture(admin);String chapter=id(draft);
        JsonNode graph=draft.path("graph").deepCopy();title(graph,"第一封发布春信","The first published letter");draft=save(admin,draft,graph,200);JsonNode v1=publish(admin,draft,200);
        var old=account("ed_old",null);JsonNode oldSave=start(old,chapter);assertThat(oldSave.path("storyVersion").asInt()).isEqualTo(1);
        graph=graph.deepCopy();title(graph,"第二封发布春信","The second published letter");for(JsonNode n:graph.path("nodes"))if(n.path("type").asText().equals("dual"))((ObjectNode)n.path("config")).put("harmonyBase",3).put("harmonyBonus",8).put("knowledgeBonus",4).put("windowHours",12);
        draft=save(admin,draft,graph,200);JsonNode v2=publish(admin,draft,200);assertThat(v2.path("version").asInt()).isEqualTo(2);assertThat(state(old,chapter)).isEqualTo(oldSave);
        var fresh=account("ed_fresh",null);JsonNode newSave=start(fresh,chapter);assertThat(newSave.path("node").path("title").path("zh").asText()).isEqualTo("第二封发布春信");assertThat(newSave.path("storyId").asText()).isNotEqualTo(v1.path("storyId").asText());
        JsonNode oldDual=toDual(old,chapter,oldSave),newDual=toDual(fresh,chapter,newSave);
        assertThat(newDual.toString()).doesNotContain("answerA","answerB","config","optionsB","clueB");
        Instant deadline=Instant.parse(newDual.path("dual").path("deadline").asText());assertThat(Duration.between(Instant.now(),deadline).toMinutes()).isBetween(718L,721L);
        assertThat(step(old,chapter,oldDual,"submit","five_pungent").path("variables").path("harmony").asInt()).isEqualTo(15);
        assertThat(step(fresh,chapter,newDual,"submit","five_pungent").path("variables").path("harmony").asInt()).isEqualTo(11);
        assertThat(publish(admin,draft,200).path("alreadyPublished").asBoolean()).isTrue();assertThat(db.queryForObject("SELECT COUNT(*) FROM story_publication",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_oper_log WHERE action='STORY_PUBLISH'",Integer.class)).isEqualTo(2);
    }
    @Test void incompleteAndUnsafeGraphsSaveAsDraftButCannotPublishAndMissingReviewFails()throws Exception {
        var admin=account("invalid_admin",3L);var draft=fixture(admin);JsonNode graph=draft.path("graph").deepCopy();((ObjectNode)first(graph).path("content").path("body")).put("en","");
        draft=save(admin,draft,graph,200);assertThat(call(admin,post("/api/admin/story/validate"),graph,200).path("valid").asBoolean()).isFalse();publish(admin,draft,400);
        graph=draft.path("graph").deepCopy();((ObjectNode)first(graph).path("content").path("body")).put("en","A test scene");((ObjectNode)graph.path("edges").get(0)).set("condition",json.createObjectNode().put("eval","java.lang.Runtime"));
        assertThat(call(admin,post("/api/admin/story/validate"),graph,200).path("valid").asBoolean()).isFalse();
        call(admin,post("/api/admin/story/chapters/"+id(draft)+"/publish"),Map.of("expectedRevision",draft.path("revision").asLong(),"culturalChecked",false,"bilingualChecked",true,"note","No cultural review performed"),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM story WHERE chapter_id=?",Integer.class,id(draft))).isZero();
    }
    @Test void concurrentSavesRejectStaleRevisionAndPreserveOneWinner()throws Exception {
        var admin=account("race_admin",3L);var draft=fixture(admin);var one=draft.path("graph").deepCopy();var two=draft.path("graph").deepCopy();title(one,"保存分支一","Save one");title(two,"保存分支二","Save two");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{go.await();return saveStatus(admin,draft,one);});var b=pool.submit(()->{go.await();return saveStatus(admin,draft,two);});go.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        JsonNode actual=call(admin,get("/api/admin/story/chapters/"+id(draft)+"/draft"),null,200);assertThat(actual.path("revision").asLong()).isEqualTo(2);
        assertThat(first(actual.path("graph")).path("content").path("title").path("zh").asText()).isIn("保存分支一","保存分支二");
    }
    int saveStatus(Account admin,JsonNode draft,JsonNode graph)throws Exception { return mvc.perform(put("/api/admin/story/chapters/"+id(draft)+"/draft").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",draft.path("revision").asLong(),"graph",graph)))).andReturn().getResponse().getStatus(); }
    @Test void concurrentPublicationCreatesOneImmutableVersion()throws Exception {
        var admin=account("pub_race_admin",3L);var draft=fixture(admin);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try { var a=pool.submit(()->{go.await();return publish(admin,draft,200);});var b=pool.submit(()->{go.await();return publish(admin,draft,200);});go.countDown();assertThat(a.get(15,TimeUnit.SECONDS).path("storyId")).isEqualTo(b.get(15,TimeUnit.SECONDS).path("storyId")); }
        finally { pool.shutdownNow(); }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM story_publication",Integer.class)).isEqualTo(1);
    }
    @Test void previewUsesFrozenServerSnapshotAndHasNoPlayerSideEffectsEvenAtEnding()throws Exception {
        var admin=account("preview_admin",3L);var draft=fixture(admin);String base="/api/admin/story/previews/";
        JsonNode preview=call(admin,post("/api/admin/story/chapters/"+id(draft)+"/preview"),Map.of("expectedRevision",1,"perspective","A"),200);String token=preview.path("id").asText();String originalTitle=preview.path("node").path("title").toString();
        JsonNode changed=draft.path("graph").deepCopy();title(changed,"尚未进入预览的新标题","New draft title outside preview");save(admin,draft,changed,200);
        assertThat(call(admin,get(base+token),null,200).path("node").path("title").toString()).isEqualTo(originalTitle);
        for(String action:List.of("continue","learn","continue","solar"))preview=previewStep(admin,preview,action,Map.of());
        assertThat(preview.path("node").toString()).doesNotContain("answerA","answerB","clueB");
        preview=previewStep(admin,preview,"submit",Map.of("answer","five_pungent","companionAnswer","renewal"));assertThat(preview.path("state").path("variables").path("harmony").asInt()).isEqualTo(15);
        preview=previewStep(admin,preview,"continue",Map.of());preview=previewStep(admin,preview,"submit",Map.of("textZh","仅供预览的文化回礼文本"));preview=previewStep(admin,preview,"submit",Map.of("textEn","A preview-only everyday ritual"));preview=previewStep(admin,preview,"study",Map.of());preview=previewStep(admin,preview,"finish",Map.of());
        assertThat(preview.path("state").path("status").asText()).isEqualTo("COMPLETED");
        for(String table:List.of("story_progress","story_reward","story_submission","story_session"))assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
    }
    JsonNode previewStep(Account admin,JsonNode preview,String action,Map<String,Object> extras)throws Exception { var request=new LinkedHashMap<String,Object>(extras);request.put("expectedRevision",preview.path("revision").asLong());request.put("action",action);return call(admin,post("/api/admin/story/previews/"+preview.path("id").asText()+"/step"),request,200); }
    @Test void perspectiveBPreviewReceivesOnlyItsOwnClueAndJudgesReversedInputs()throws Exception {
        var admin=account("preview_b_admin",3L);var draft=fixture(admin);
        JsonNode p=call(admin,post("/api/admin/story/chapters/"+id(draft)+"/preview"),Map.of("expectedRevision",1,"perspective","B"),200);
        for(String action:List.of("continue","learn","continue","solar"))p=previewStep(admin,p,action,Map.of());
        assertThat(p.path("node").path("clue").path("zh").asText()).contains("笺注").doesNotContain("菜单残页");
        assertThat(p.path("node").path("options").toString()).doesNotContain("five_pungent");
        p=previewStep(admin,p,"submit",Map.of("answer","renewal","companionAnswer","five_pungent"));
        assertThat(p.path("state").path("variables").path("harmony").asInt()).isEqualTo(15);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM story_reward",Integer.class)).isZero();
    }
    @Test void permissionSeparationOwnershipExpiryAndForgedPreviewStateAreEnforced()throws Exception {
        var admin=account("permissions_admin",3L);var draft=fixture(admin);var editor=account("permissions_editor",4L);var reviewer=account("permissions_reviewer",5L);var portal=account("permissions_portal",null);
        call(null,get("/api/admin/story/chapters"),null,401);call(portal,get("/api/admin/story/chapters"),null,403);call(editor,get("/api/admin/story/chapters"),null,200);
        publish(editor,draft,403);save(reviewer,draft,draft.path("graph"),403);
        JsonNode p=call(admin,post("/api/admin/story/chapters/"+id(draft)+"/preview"),Map.of("expectedRevision",1,"perspective","A"),200);String url="/api/admin/story/previews/"+p.path("id").asText();
        call(reviewer,get(url),null,404);previewStep(admin,p,"continue",Map.of());call(admin,post(url+"/step"),Map.of("expectedRevision",0,"action","continue"),409);
        call(admin,post(url+"/step"),Map.of("expectedRevision",1,"action","learn","variables",Map.of("knowledge",9999)),400);
        db.update("UPDATE story_editor_preview SET expires_at=UTC_TIMESTAMP()-INTERVAL 1 MINUTE");call(admin,get(url),null,404);
    }
    @Test void restoreCopiesHistoryIntoNewDraftAndRejectsCrossChapterVersion()throws Exception {
        var admin=account("restore_admin",3L);var draft=fixture(admin);var v1=publish(admin,draft,200);JsonNode changed=draft.path("graph").deepCopy();title(changed,"后来的一封信","A later letter");draft=save(admin,draft,changed,200);publish(admin,draft,200);
        JsonNode restored=call(admin,post("/api/admin/story/chapters/"+id(draft)+"/restore"),Map.of("expectedRevision",draft.path("revision").asLong(),"storyId",v1.path("storyId").asText()),200);
        assertThat(restored.path("revision").asLong()).isEqualTo(3);assertThat(first(restored.path("graph")).path("content").path("title").path("zh").asText()).isEqualTo("一封未写完的春信");
        assertThat(call(admin,get("/api/admin/story/chapters/"+id(draft)+"/versions"),null,200).size()).isEqualTo(2);
        call(admin,post("/api/admin/story/chapters/1/restore"),Map.of("expectedRevision",0,"storyId",v1.path("storyId").asText()),404);
        assertThat(publish(admin,restored,200).path("version").asInt()).isEqualTo(3);
    }
    @Test void draftAndArchivedChaptersDoNotLeakToNewPlayersAndExistingSavesContinue()throws Exception {
        var admin=account("archive_admin",3L);var draft=fixture(admin);String chapter=id(draft);var player=account("archive_player",null);
        call(player,get("/api/portal/stories/"+chapter+"/state"),null,404);publish(admin,draft,200);JsonNode saved=start(player,chapter);
        call(admin,post("/api/admin/story/chapters/"+chapter+"/archive"),null,200);assertThat(state(player,chapter)).isEqualTo(saved);step(player,chapter,saved,"continue",null);
        assertThat(call(player,get("/api/portal/chapters/"+chapter+"/cards"),null,200).size()).isEqualTo(3);
        var fresh=account("archive_fresh",null);call(fresh,post("/api/portal/stories/"+chapter+"/start"),Map.of("mode","SCRIPT"),404);
        assertThat(call(admin,get("/api/admin/story/chapters/"+chapter+"/draft"),null,200).path("chapterId").asText()).isEqualTo(chapter);
    }
}
