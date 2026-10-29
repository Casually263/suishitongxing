package com.suishi.story;

import com.fasterxml.jackson.databind.*;
import com.suishi.story.service.StoryGraph;
import com.suishi.story.mapper.*;
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
import org.springframework.test.web.servlet.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="RUN_INTEGRATION_TESTS",matches="true")
class StoryIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
    @Autowired StoryMapper stories; @Autowired StoryNodeMapper nodes;
    private static final String PASSWORD="Story-test-password-2026";
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->System.getenv().getOrDefault("IT_DB_URL","jdbc:mysql://127.0.0.1:3307/suishi_it?connectionTimeZone=UTC"));
        r.add("app.security.jwt-key",StoryIT::key);r.add("app.security.email-key",StoryIT::key);r.add("app.security.email-hmac-key",StoryIT::key);
        r.add("app.redis-prefix",()->"suishi:story-it:"+UUID.randomUUID()+":");r.add("app.bootstrap.enabled",()->false);r.add("app.bootstrap.demo-enabled",()->false);
        r.add("app.security.login-limit",()->1000);r.add("app.security.register-limit",()->1000);
    }
    static String key(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
    @BeforeEach void clean(){
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("suishi_it");
        for(String table:List.of("ai_chat_log","ai_usage_progress","ai_usage_daily","ai_knowledge_review","story_action","story_submission","story_reward","story_dual_round","story_progress","story_session","sys_oper_log","sys_login_log","user_tag","sys_user_role","user_profile","sys_user"))db.update("DELETE FROM "+table);
        db.update("DELETE FROM story_edge WHERE story_id=2");db.update("DELETE FROM story_node WHERE story_id=2");db.update("DELETE FROM story WHERE id=2");
    }
    @AfterEach void cleanup(){clean();}
    record Player(String bearer,String id){}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
    Player player(String name,String role)throws Exception{
        var registered=mvc.perform(post("/api/portal/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"email",name+"@example.test","password",PASSWORD,"cultureRole",role,"adultConfirmed",true,"acceptedTerms",true,"termsVersion","2026-10")))).andExpect(status().isCreated()).andReturn();
        String id=body(registered).path("userId").asText();
        var login=mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("identifier",name,"password",PASSWORD)))).andExpect(status().isOk()).andReturn();return new Player("Bearer "+body(login).path("accessToken").asText(),id);
    }
    JsonNode getState(Player p)throws Exception{return body(mvc.perform(get("/api/app/stories/1/state").header("Authorization",p.bearer)).andExpect(status().isOk()).andReturn());}
    JsonNode start(Player p,String mode)throws Exception{return body(mvc.perform(post("/api/portal/stories/1/start").header("Authorization",p.bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("mode",mode)))).andExpect(status().isOk()).andReturn());}
    Map<String,Object> action(JsonNode state,String action){return new LinkedHashMap<>(Map.of("nodeId",state.path("node").path("id").asText(),"expectedVersion",state.path("version").asLong(),"requestId",UUID.randomUUID().toString(),"action",action));}
    MvcResult submit(Player p,Map<String,Object> action,int expected)throws Exception{return mvc.perform(post("/api/portal/stories/1/choose").header("Authorization",p.bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(action))).andExpect(status().is(expected)).andReturn();}
    JsonNode step(Player p,JsonNode s,String action)throws Exception{return body(submit(p,action(s,action),200));}
    JsonNode toDual(Player p,JsonNode s,String route,String calendar)throws Exception{s=step(p,s,"continue");s=step(p,s,route);s=step(p,s,"continue");return step(p,s,calendar);}
    JsonNode answer(Player p,JsonNode s,String value)throws Exception{var a=action(s,"submit");a.put("answer",value);return body(submit(p,a,200));}
    JsonNode complete(Player p,JsonNode s,String endChoice)throws Exception{
        s=step(p,s,"continue");
        for(String text:List.of("在我家，我们会给朋友写一封迎春的信。","我在窗边观察了一枚新芽，记录下今天的天气。")){var a=action(s,"submit");a.put("textZh",text);a.put("textEn","In my family, we observe the changing season together.");s=body(submit(p,a,200));}
        s=step(p,s,endChoice);return step(p,s,"finish");
    }
    @Test void publishedGraphCardsAndAllThreeEndingsAreDatabaseDriven()throws Exception{
        new StoryGraph(json,stories.selectById(1L),nodes.nodes(1L),nodes.edges(1L));
        String[][] paths={{"learn","solar","five_pungent","study","spring_returns"},{"observe","lunar","moon_cake","care","across_windows"},{"taste","lunar","moon_cake","quiet","first_bud"}};
        for(int i=0;i<paths.length;i++){
            var p=player("story_path_"+i,"GUIDE");var cards=body(mvc.perform(get("/api/app/chapters/1/cards").header("Authorization",p.bearer)).andExpect(status().isOk()).andReturn());assertThat(cards.size()).isEqualTo(3);assertThat(cards.get(0).path("content").path("body").path("en").asText()).isNotBlank();
            var s=toDual(p,start(p,"SCRIPT"),paths[i][0],paths[i][1]);s=answer(p,s,paths[i][2]);s=complete(p,s,paths[i][3]);
            assertThat(s.path("status").asText()).isEqualTo("COMPLETED");assertThat(s.path("node").path("key").asText()).isEqualTo(paths[i][4]);assertThat(s.path("rewards").size()).isEqualTo(1);assertThat(s.path("submissions").size()).isEqualTo(2);
            assertThat(getState(p)).isEqualTo(s);assertThat(start(p,"SCRIPT").path("status").asText()).isEqualTo("COMPLETED");
        }
    }
    @Test void duplicateRequestsStaleVersionsJumpingAndInjectedVariablesCannotChangeState()throws Exception{
        var p=player("story_guard","GUIDE");JsonNode initial=start(p,"SCRIPT");var a=action(initial,"continue");JsonNode next=body(submit(p,a,200));assertThat(body(submit(p,a,200))).isEqualTo(next);
        a.put("action","learn");submit(p,a,409);
        var stale=action(initial,"continue");submit(p,stale,409);
        var jump=action(next,"learn");jump.put("nodeId","1012");submit(p,jump,409);
        var forged=action(next,"learn");forged.put("variables",Map.of("knowledge",1000));submit(p,forged,400);
        submit(p,action(next,"unknown-choice"),400);assertThat(getState(p)).isEqualTo(next);
        mvc.perform(get("/api/portal/stories/1/state")).andExpect(status().isUnauthorized());
        var stranger=player("story_stranger","PARTNER");assertThat(getState(stranger).path("started").asBoolean()).isFalse();
        assertThat(getState(stranger).toString()).doesNotContain(p.id,"history","submissions");
    }
    private JsonNode join(Player p,String code)throws Exception{return body(mvc.perform(post("/api/portal/stories/1/join").header("Authorization",p.bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("inviteCode",code)))).andExpect(status().isOk()).andReturn());}
    @Test void humanCluesAreIsolatedWaitingIsPersistentAndSecondAnswerAdvancesBoth()throws Exception{
        var a=player("story_guide","GUIDE");var b=player("story_partner","PARTNER");var sa=start(a,"HUMAN");String invitation=sa.path("inviteCode").asText();var sb=join(b,invitation);
        sa=toDual(a,sa,"learn","solar");sb=toDual(b,sb,"observe","solar");
        assertThat(sa.toString()).doesNotContain("clueA","clueB","answerA","answerB","config","你的笺注");
        assertThat(sa.path("node").path("clue").path("zh").asText()).contains("菜单残页");assertThat(sb.path("node").path("clue").path("zh").asText()).contains("笺注");
        assertThat(sb.path("node").path("options").toString()).doesNotContain("five_pungent");assertThat(sa.path("node").path("options").toString()).doesNotContain("renewal");
        sa=answer(a,sa,"five_pungent");assertThat(sa.path("dual").path("state").asText()).isEqualTo("WAITING_PARTNER");assertThat(getState(a)).isEqualTo(sa);assertThat(sa.toString()).doesNotContain("辛、新谐音","renewal");
        var duplicate=action(sa,"submit");duplicate.put("answer","five_pungent");submit(a,duplicate,409);
        sb=answer(b,sb,"renewal");assertThat(sb.path("node").path("key").asText()).isEqualTo("letter_table");assertThat(getState(a).path("variables").path("harmony").asInt()).isEqualTo(15);
        assertThat(db.queryForObject("SELECT state FROM story_dual_round",String.class)).isEqualTo("JUDGED");
        var out=player("story_outsider","PARTNER");mvc.perform(post("/api/app/stories/1/join").header("Authorization",out.bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("inviteCode",invitation)))).andExpect(status().isConflict());
    }
    @Test void concurrentSubmissionsAndEndingReplayHaveExactlyOneSettlement()throws Exception{
        var a=player("concurrent_a","GUIDE");var b=player("concurrent_b","PARTNER");var sa=start(a,"HUMAN");var sb=join(b,sa.path("inviteCode").asText());
        sa=toDual(a,sa,"learn","solar");sb=toDual(b,sb,"taste","solar");
        var aa=action(sa,"submit");aa.put("answer","five_pungent");var ab=action(sb,"submit");ab.put("answer","renewal");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);var f1=pool.submit(()->{ready.countDown();go.await();return submit(a,aa,200);});var f2=pool.submit(()->{ready.countDown();go.await();return submit(b,ab,200);});assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();f1.get(15,TimeUnit.SECONDS);f2.get(15,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        assertThat(getState(a).path("variables").path("harmony").asInt()).isEqualTo(15);assertThat(getState(b).path("variables").path("harmony").asInt()).isEqualTo(15);
        var s=complete(a,getState(a),"study");var again=action(s,"finish");submit(a,again,409);assertThat(db.queryForObject("SELECT COUNT(*) FROM story_reward",Integer.class)).isEqualTo(1);
        assertThat(getState(b).path("status").asText()).isEqualTo("PLAYING");
    }
    @Test void timeoutSupportsExtensionLateSubmissionScriptFallbackAndSkipWithoutAdvancingAbsentUser()throws Exception{
        var a=player("timeout_a","GUIDE");var b=player("timeout_b","PARTNER");var sa=start(a,"HUMAN");var sb=join(b,sa.path("inviteCode").asText());sa=toDual(a,sa,"observe","lunar");sb=toDual(b,sb,"taste","solar");
        sa=answer(a,sa,"five_pungent");db.update("UPDATE story_dual_round SET deadline=UTC_TIMESTAMP(3)-INTERVAL 1 HOUR");sa=getState(a);assertThat(sa.path("dual").path("state").asText()).isEqualTo("TIMEOUT");sa=step(a,sa,"extend");assertThat(sa.path("dual").path("extended").asBoolean()).isTrue();
        db.update("UPDATE story_dual_round SET deadline=UTC_TIMESTAMP(3)-INTERVAL 1 HOUR");sa=getState(a);submit(a,action(sa,"extend"),409);sa=step(a,sa,"script");assertThat(sa.path("partnerMode").asText()).isEqualTo("SCRIPT");assertThat(sa.path("node").path("key").asText()).isEqualTo("letter_table");
        sb=getState(b);assertThat(sb.path("node").path("type").asText()).isEqualTo("dual");assertThat(sb.path("variables").path("harmony").asInt()).isZero();sb=step(b,sb,"skip");assertThat(sb.path("variables").path("harmony").asInt()).isZero();assertThat(sb.path("node").path("key").asText()).isEqualTo("letter_table");
    }
    @Test void lateAnswersStillJudgeAndOwnerOnlyTextIsNotExposedToPartner()throws Exception{
        var a=player("late_a","GUIDE");var b=player("late_b","PARTNER");var sa=start(a,"HUMAN");var sb=join(b,sa.path("inviteCode").asText());sa=toDual(a,sa,"taste","solar");sb=toDual(b,sb,"observe","lunar");sa=answer(a,sa,"moon_cake");
        db.update("UPDATE story_dual_round SET deadline=UTC_TIMESTAMP(3)-INTERVAL 1 HOUR");sb=answer(b,getState(b),"renewal");assertThat(sb.path("variables").path("harmony").asInt()).isEqualTo(10);
        sa=step(a,getState(a),"continue");var bad=action(sa,"submit");bad.put("textZh","短");submit(a,bad,400);
        var gift=action(sa,"submit");gift.put("textZh","PRIVATE_ONLY_THIS_USER_春天的故事");sa=body(submit(a,gift,200));assertThat(sa.path("submissions").get(0).path("visibility").asText()).isEqualTo("PRIVATE");assertThat(getState(b).toString()).doesNotContain("PRIVATE_ONLY_THIS_USER");
    }
    @Test void concurrentCompetingChoicesOnlyAdvanceOnceAndExactRetryIsIdempotent()throws Exception{
        var p=player("choice_race","GUIDE");var s=step(p,start(p,"SCRIPT"),"continue");var a=action(s,"learn");var b=action(s,"taste");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            CountDownLatch go=new CountDownLatch(1);
            var f1=pool.submit(()->{go.await();return mvc.perform(post("/api/portal/stories/1/choose").header("Authorization",p.bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(a))).andReturn().getResponse().getStatus();});
            var f2=pool.submit(()->{go.await();return mvc.perform(post("/api/portal/stories/1/choose").header("Authorization",p.bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b))).andReturn().getResponse().getStatus();});
            go.countDown();int first=f1.get(15,TimeUnit.SECONDS),second=f2.get(15,TimeUnit.SECONDS);assertThat(List.of(first,second)).containsExactlyInAnyOrder(200,409);
            var winner=first==200?a:b;var saved=getState(p);assertThat(saved.path("version").asLong()).isEqualTo(2);assertThat(body(submit(p,winner,200))).isEqualTo(saved);
        }finally{pool.shutdownNow();}
    }
    @Test void existingSaveStaysOnPublishedVersionWhileNewUsersReceiveNewVersion()throws Exception{
        var old=player("version_old","GUIDE");var saved=step(old,start(old,"SCRIPT"),"continue");
        db.update("INSERT INTO story(id,chapter_id,version,status,start_node,initial_variables) SELECT 2,chapter_id,2,status,start_node+10000,initial_variables FROM story WHERE id=1");
        db.update("INSERT INTO story_node(id,story_id,node_key,node_type,content_i18n,config) SELECT id+10000,2,node_key,node_type,content_i18n,config FROM story_node WHERE story_id=1");
        db.update("INSERT INTO story_edge(id,story_id,from_node,to_node,action_key,priority,condition_json) SELECT id+10000,2,from_node+10000,to_node+10000,action_key,priority,condition_json FROM story_edge WHERE story_id=1");
        assertThat(getState(old)).isEqualTo(saved);var resumed=step(old,saved,"learn");assertThat(resumed.path("storyVersion").asInt()).isEqualTo(1);
        var fresh=player("version_new","GUIDE");assertThat(start(fresh,"SCRIPT").path("storyVersion").asInt()).isEqualTo(2);
    }
}
