package com.suishi.story.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.suishi.common.BusinessException;
import com.suishi.story.dto.StoryRequests.*;
import com.suishi.story.entity.StoryEntities.*;
import com.suishi.story.mapper.*;
import com.suishi.story.vo.StoryView;
import com.suishi.system.entity.User;
import com.suishi.system.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class StoryService {
    private final StoryMapper stories;
    private final StoryNodeMapper nodes;
    private final StoryProgressMapper progresses;
    private final StorySessionMapper sessions;
    private final StoryRoundMapper rounds;
    private final UserMapper users;
    private final ObjectMapper json;
    public StoryService(StoryMapper stories,StoryNodeMapper nodes,StoryProgressMapper progresses,StorySessionMapper sessions,StoryRoundMapper rounds,UserMapper users,ObjectMapper json) {
        this.stories=stories;this.nodes=nodes;this.progresses=progresses;this.sessions=sessions;this.rounds=rounds;this.users=users;this.json=json;
    }
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
    private static BusinessException error(HttpStatus status,String code,String message){return new BusinessException(status,code,message);}
    private static BusinessException invalid(String message){return error(HttpStatus.BAD_REQUEST,"INVALID_STORY_ACTION",message);}
    private static BusinessException conflict(String message){return error(HttpStatus.CONFLICT,"STORY_CONFLICT",message);}
    private static BusinessException missing(){return error(HttpStatus.NOT_FOUND,"STORY_NOT_FOUND","剧情或存档不存在");}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private StoryGraph graph(Story story){return new StoryGraph(json,story,nodes.nodes(story.id),nodes.edges(story.id));}
    private Story published(Long chapterId){Story story=stories.published(chapterId);if(story==null)throw missing();return story;}
    private Progress owned(Long userId,Long chapterId){Progress p=progresses.find(userId,chapterId);if(p==null)throw missing();return p;}
    private void member(Session session,Long userId){if(session==null || !userId.equals(session.userA) && !userId.equals(session.userB))throw missing();}
    public List<Map<String,Object>> chapters(Long userId) {
        var list=stories.chapters();
        for(var chapter:list){chapter.put("title",read(chapter.get("title").toString()));chapter.put("intro",read(chapter.get("intro").toString()));var p=progresses.find(userId,Long.valueOf(chapter.get("id").toString()));chapter.put("progressStatus",p==null?"NOT_STARTED":p.status);}
        return list;
    }
    public List<Map<String,Object>> cards(Long userId,Long chapterId){if(progresses.find(userId,chapterId)==null)published(chapterId);var cards=stories.cards(chapterId);cards.forEach(c->c.put("content",read(c.get("content").toString())));return cards;}
    public List<Map<String,Object>> rewards(Long userId){return stories.rewards(userId);}
    @Transactional public StoryView start(Long userId,Long chapterId,Start request) {
        // Serialize first-start/join for the same account before checking the unique chapter slot.
        lockUser(userId);
        Progress existing=progresses.find(userId,chapterId);
        if(existing!=null)return stateView(existing);
        Story story=published(chapterId);StoryGraph graph=graph(story);User user=users.selectById(userId);
        if(request.mode().equals("HUMAN") && (!Boolean.TRUE.equals(user.adultStatus) || !"GUIDE".equals(user.roleType)))throw invalid("真人邀请需由已确认成年的文化向导发起");
        Session session=newSession(story.id,userId,request.mode());
        Progress p=newProgress(userId,story,session,request.mode().equals("HUMAN") || "GUIDE".equals(user.roleType)?"A":"B");
        enterDual(p,graph,session);return stateView(p);
    }
    private void lockUser(Long userId){users.lockStoryUser(userId);}
    private Session newSession(Long storyId,Long userId,String mode){Session s=new Session();s.storyId=storyId;s.userA=userId;s.mode=mode;s.inviteCode=UUID.randomUUID().toString().replace("-","");s.inviteDeadline=now().plusHours(48);sessions.insert(s);return s;}
    private Progress newProgress(Long userId,Story story,Session session,String side){Progress p=new Progress();p.userId=userId;p.chapterId=story.chapterId;p.storyId=story.id;p.sessionId=session.id;p.perspective=side;p.currentNode=story.startNode;p.variables=story.initialVariables;p.history="[]";p.status="PLAYING";p.version=0L;progresses.insert(p);return p;}
    @Transactional public StoryView join(Long userId,Long chapterId,Join request) {
        lockUser(userId);Session session=sessions.invite(request.inviteCode());
        if(session==null || !session.mode.equals("HUMAN") || session.inviteDeadline.isBefore(now()))throw missing();
        Story story=stories.selectById(session.storyId);
        if(!story.chapterId.equals(chapterId))throw missing();
        Progress existing=progresses.find(userId,chapterId);
        if(existing!=null){if(existing.sessionId.equals(session.id))return stateView(existing);throw conflict("本篇章已有存档，不能覆盖");}
        User user=users.selectById(userId);
        if(userId.equals(session.userA) || !Boolean.TRUE.equals(user.adultStatus) || !"PARTNER".equals(user.roleType))throw invalid("请由另一位已确认成年的文化伙伴接受邀请");
        if(session.userB!=null)throw conflict("邀请已被接受");
        Progress host=progresses.find(session.userA,chapterId);
        if(host==null || !host.sessionId.equals(session.id) || !host.status.equals("PLAYING"))throw conflict("邀请已失效");
        session.userB=userId;sessions.updateById(session);
        Progress p=newProgress(userId,story,session,"B");enterDual(p,graph(story),session);return stateView(p);
    }
    @Transactional public StoryView state(Long userId,Long chapterId) {
        Progress p=progresses.find(userId,chapterId);
        if(p==null){published(chapterId);return new StoryView();}
        Session s=sessions.lock(p.sessionId);member(s,userId);p=progresses.lock(p.id);
        if(!p.sessionId.equals(s.id))throw conflict("协作状态已变化，请刷新");
        expire(sessions.round(s.id,p.currentNode));return stateView(p);
    }
    @Transactional public StoryView choose(Long userId,Long chapterId,Action request) {
        Progress p=owned(userId,chapterId);Session session=sessions.lock(p.sessionId);member(session,userId);p=progresses.lock(p.id);
        // Lock order is always session then progress. A fallback may have moved this progress.
        if(!p.sessionId.equals(session.id))throw conflict("协作状态已变化，请刷新");
        String hash=hash(write(request)),previous=progresses.actionHash(p.id,request.requestId());
        if(previous!=null){if(!previous.equals(hash))throw conflict("请求编号已用于其他内容");return stateView(p);}
        if(!p.version.equals(request.expectedVersion()) || !p.currentNode.equals(request.nodeId()))throw conflict("存档已更新或节点不匹配，请刷新后继续");
        if(!p.status.equals("PLAYING"))throw conflict("篇章已经结算");
        Story story=stories.selectById(p.storyId);StoryGraph graph=graph(story);Node node=graph.node(p.currentNode);Long from=p.currentNode;
        if(!node.nodeType.equals("dual") && request.answer()!=null)throw invalid("此节点不接受谜题答案");
        if(!Set.of("gift","checkin").contains(node.nodeType) && (request.textZh()!=null || request.textEn()!=null))throw invalid("此节点不接受文字记录");
        Map<String,Integer> vars=StoryGraph.variables(read(p.variables));JsonNode config=read(node.config);JsonNode feedback=json.createObjectNode();
        switch(node.nodeType){
            case "narrative" -> {requireAction(request,"continue");StoryGraph.apply(vars,config.path("effects"));advance(p,graph,"continue",vars,feedback);}
            case "choice" -> {
                JsonNode choice=config.path("choices").path(request.action());if(choice.isMissingNode())throw invalid("选项不属于当前节点");
                StoryGraph.apply(vars,choice.path("effects"));feedback=choice.path("feedback");advance(p,graph,request.action(),vars,feedback);
            }
            case "gift","checkin" -> {
                requireAction(request,"submit");String zh=clean(request.textZh()),en=clean(request.textEn());if(zh.isEmpty() && en.isEmpty())throw invalid("请至少使用一种语言填写体验（5–2000个字符）");
                if(!zh.isEmpty() && zh.length()<5 || !en.isEmpty() && en.length()<5)throw invalid("体验至少填写5个字符");
                progresses.submission(IdWorker.getId(),p.id,node.id,node.nodeType,write(Map.of("zh",zh,"en",en)));
                StoryGraph.apply(vars,config.path("effects"));advance(p,graph,"submit",vars,json.createObjectNode().put("zh","已保存为你的私有记录，尚未公开发布。").put("en","Saved privately. This entry has not been published."));
            }
            case "dual" -> dual(p,session,graph,node,request);
            case "ending" -> {
                requireAction(request,"finish");stories.reward(IdWorker.getId(),userId,chapterId,p.id,node.nodeKey,session.mode);p.status="COMPLETED";
                save(p,node,"finish",vars,read(node.contentI18n).path("body"));
            }
            default -> throw invalid("未知节点类型");
        }
        progresses.action(IdWorker.getId(),p.id,request.requestId(),hash,from,p.currentNode);
        if(!p.status.equals("COMPLETED"))enterDual(p,graph,sessions.selectById(p.sessionId));
        return stateView(p);
    }
    private static void requireAction(Action request,String expected){if(!expected.equals(request.action()))throw invalid("当前节点不支持此操作");}
    private static String clean(String value){return value==null?"":value.strip();}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void advance(Progress p,StoryGraph graph,String action,Map<String,Integer> vars,JsonNode feedback){Node from=graph.node(p.currentNode);p.currentNode=graph.next(from.id,action,vars);save(p,from,action,vars,feedback);}
    private void save(Progress p,Node from,String action,Map<String,Integer> vars,JsonNode feedback){
        ArrayNode history=(ArrayNode)read(p.history);var event=json.createObjectNode();event.put("nodeId",from.id.toString());event.put("nodeKey",from.nodeKey);event.put("action",action);event.put("at",now().toString());
        if(feedback!=null && feedback.isObject())event.set("feedback",feedback);history.add(event);
        p.history=write(history);p.variables=write(vars);p.version++;p.updatedAt=now();progresses.updateById(p);
    }
    private void enterDual(Progress p,StoryGraph graph,Session session){
        if(!graph.node(p.currentNode).nodeType.equals("dual") || sessions.round(session.id,p.currentNode)!=null)return;
        Round round=new Round();round.sessionId=session.id;round.nodeId=p.currentNode;round.state="OPEN";round.deadline=now().plusHours(StoryRules.windowHours(read(graph.node(p.currentNode).config)));round.extended=false;round.harmonyDelta=0;rounds.insert(round);
    }
    private void expire(Round round){if(round!=null && Set.of("OPEN","WAITING_PARTNER").contains(round.state) && !round.deadline.isAfter(now())){round.state="TIMEOUT";rounds.updateById(round);}}
    private void dual(Progress p,Session session,StoryGraph graph,Node node,Action request){
        Round round=sessions.round(session.id,node.id);if(round==null)throw conflict("协作节点尚未初始化");expire(round);
        if(!Set.of("OPEN","WAITING_PARTNER","TIMEOUT").contains(round.state))throw conflict("协作已经判定");
        if(Set.of("extend","script","skip").contains(request.action())){
            if(!round.state.equals("TIMEOUT"))throw invalid("协作窗口结束后才可使用超时选项");
            if(request.answer()!=null)throw invalid("超时操作不接受答案");
            if(request.action().equals("extend")){
                if(Boolean.TRUE.equals(round.extended))throw conflict("每个协作节点只能延长一次");round.extended=true;round.deadline=now().plusHours(48);round.state=round.answerA==null && round.answerB==null?"OPEN":"WAITING_PARTNER";rounds.updateById(round);
                save(p,node,"extend",StoryGraph.variables(read(p.variables)),json.createObjectNode());return;
            }
            // Detach only the requesting user; the absent human is never advanced or rewarded.
            Session replacement=newSession(p.storyId,p.userId,"SCRIPT");p.sessionId=replacement.id;
            Round own=new Round();own.sessionId=replacement.id;own.nodeId=node.id;own.deadline=now().plusHours(48);own.extended=false;own.harmonyDelta=0;own.state="OPEN";
            JsonNode config=read(node.config);
            if(p.perspective.equals("A")){own.answerA=round.answerA;own.answerB=config.path("answerB").asText();}
            else {own.answerB=round.answerB;own.answerA=config.path("answerA").asText();}
            rounds.insert(own);progresses.updateById(p);
            if(request.action().equals("skip") || own.answerA!=null && own.answerB!=null)resolve(p,replacement,own,graph,node,request.action().equals("skip"),true);
            else save(p,node,"script",StoryGraph.variables(read(p.variables)),json.createObjectNode());
            return;
        }
        requireAction(request,"submit");
        String answer=request.answer();boolean valid=false;
        for(JsonNode option:read(node.contentI18n).path("options"+p.perspective))if(option.path("id").asText().equals(answer))valid=true;
        if(!valid)throw invalid("答案不属于你的当前线索选项");
        if((p.perspective.equals("A")?round.answerA:round.answerB)!=null)throw conflict("你已提交，正在等待伙伴");
        if(p.perspective.equals("A"))round.answerA=answer;else round.answerB=answer;
        if(session.mode.equals("SCRIPT")){
            JsonNode config=read(node.config);if(p.perspective.equals("A"))round.answerB=config.path("answerB").asText();else round.answerA=config.path("answerA").asText();
        }
        if(round.answerA!=null && round.answerB!=null)resolve(p,session,round,graph,node,false,true);
        else {round.state=round.deadline.isAfter(now())?"WAITING_PARTNER":"TIMEOUT";rounds.updateById(round);save(p,node,"submit",StoryGraph.variables(read(p.variables)),json.createObjectNode());}
    }
    private void resolve(Progress actor,Session session,Round round,StoryGraph graph,Node node,boolean skipped,boolean hasAnswers){
        JsonNode config=read(node.config);
        var judgment=StoryRules.judge(config,round.answerA,round.answerB,skipped); boolean success=judgment.success();
        round.success=success;round.state=skipped?"SKIPPED":"JUDGED";round.harmonyDelta=judgment.harmonyDelta();round.resolutionMode=session.mode;rounds.updateById(round);
        List<Progress> participants=progresses.members(session.id);
        for(Progress p:participants){
            if(!p.currentNode.equals(node.id) || !p.status.equals("PLAYING"))throw conflict("双方进度尚未到达同一协作节点");
            Map<String,Integer> vars=StoryGraph.variables(read(p.variables));vars.put("harmony",Math.min(10000,vars.get("harmony")+round.harmonyDelta));vars.put("knowledge",Math.min(10000,vars.get("knowledge")+judgment.knowledgeDelta()));
            var feedback=json.createObjectNode();feedback.setAll((com.fasterxml.jackson.databind.node.ObjectNode)config.path("explanation"));feedback.put("success",success);feedback.put("harmonyDelta",round.harmonyDelta);feedback.put("partnerMode",session.mode);feedback.put("skipped",skipped);
            advance(p,graph,"submit",vars,feedback);
            if(p.id.equals(actor.id)){actor.currentNode=p.currentNode;actor.version=p.version;actor.variables=p.variables;actor.history=p.history;actor.updatedAt=p.updatedAt;}
        }
    }
    private StoryView stateView(Progress p){
        Story story=stories.selectById(p.storyId);StoryGraph graph=graph(story);Node node=graph.node(p.currentNode);Session session=sessions.selectById(p.sessionId);member(session,p.userId);
        JsonNode raw=read(node.contentI18n);
        boolean dual=node.nodeType.equals("dual");
        StoryView state=new StoryView();state.started=true;state.chapterId=p.chapterId.toString();state.storyId=p.storyId.toString();state.storyVersion=story.version;state.version=p.version;state.status=p.status;
        state.node=new StoryView.NodeView(node.id.toString(),node.nodeKey,node.nodeType,raw.path("title"),raw.path("body"),dual?raw.path("options"+p.perspective):node.nodeType.equals("choice")?raw.path("options"):null,dual?raw.path("clue"+p.perspective):null);
        state.variables=read(p.variables);state.history=read(p.history);state.perspective=p.perspective;state.partnerMode=session.mode;state.partnerJoined=session.userB!=null;
        if(session.mode.equals("HUMAN") && session.userA.equals(p.userId) && session.userB==null && session.inviteDeadline.isAfter(now()))state.inviteCode=session.inviteCode;
        if(node.nodeType.equals("dual")){
            Round round=sessions.round(session.id,node.id);
            if(round!=null)state.dual=new StoryView.DualView(round.state,round.deadline.toString()+"Z",Boolean.TRUE.equals(round.extended),(p.perspective.equals("A")?round.answerA:round.answerB)!=null);
        }
        var submissions=progresses.submissions(p.id);submissions.forEach(s->s.put("content",read(s.get("content").toString())));state.submissions=submissions;state.rewards=stories.rewards(p.userId);return state;
    }
}
