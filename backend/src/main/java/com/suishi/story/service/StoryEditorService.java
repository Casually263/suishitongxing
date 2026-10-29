package com.suishi.story.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.suishi.common.BusinessException;
import com.suishi.story.dto.EditorRequests.*;
import com.suishi.story.entity.StoryEntities.*;
import com.suishi.story.mapper.*;
import com.suishi.story.vo.StoryView;
import com.suishi.system.mapper.LogMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StoryEditorService {
    private final StoryEditorMapper editor; private final StoryMapper stories; private final StoryNodeMapper nodes;
    private final StoryEditorCodec codec; private final ObjectMapper json; private final LogMapper logs;
    public StoryEditorService(StoryEditorMapper editor,StoryMapper stories,StoryNodeMapper nodes,StoryEditorCodec codec,ObjectMapper json,LogMapper logs) {
        this.editor=editor;this.stories=stories;this.nodes=nodes;this.codec=codec;this.json=json;this.logs=logs;
    }
    private static BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"EDITOR_NOT_FOUND","篇章、版本或预览不存在/已过期"); }
    private static BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT,"DRAFT_CONFLICT","草稿已被修改，请先重新加载；你的本地修改不会被自动覆盖"); }
    private static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_EDITOR_GRAPH",message); }
    private ObjectNode object(String value) { try { return (ObjectNode)json.readTree(value); } catch(Exception e) { throw new IllegalArgumentException("Invalid editor state",e); } }
    private void chapter(Long id) { if(editor.chapters().stream().noneMatch(c->c.get("id").toString().equals(id.toString())))throw missing(); }
    private void lock(Long id) { if(editor.lockChapter(id)==null)throw missing(); }
    public List<Map<String,Object>> chapters() {
        var result=editor.chapters();for(var c:result) { c.put("title",object(c.get("title").toString()));c.put("intro",object(c.get("intro").toString())); }return result;
    }
    private Graph publishedGraph(Story story) {
        String snapshot=editor.publishedGraph(story.id);
        return snapshot==null?codec.fromPublished(story,nodes.nodes(story.id),nodes.edges(story.id)):codec.read(snapshot);
    }
    public Map<String,Object> draft(Long chapterId) {
        chapter(chapterId);var saved=editor.draft(chapterId);Map<String,Object> view=new LinkedHashMap<>();
        view.put("chapterId",chapterId.toString());view.put("revision",saved==null?0L:((Number)saved.get("revision")).longValue());
        if(saved==null) { Story latest=editor.latestStory(chapterId);if(latest==null)throw missing();view.put("graph",publishedGraph(latest)); }
        else { view.put("graph",codec.read(saved.get("graph_json").toString()));view.put("updatedBy",saved.get("updated_by").toString());view.put("updatedAt",saved.get("updated_at")); }
        return view;
    }
    private Map<String,Object> revision(Long id,long expected) {
        var saved=editor.draft(id);long actual=saved==null?0:((Number)saved.get("revision")).longValue();if(expected!=actual)throw conflict();return saved;
    }
    @Transactional public Map<String,Object> save(Long actor,Long chapterId,Save request) {
        lock(chapterId);var saved=revision(chapterId,request.expectedRevision());String content=codec.write(request.graph());
        if(content.length()>1_000_000)throw invalid("剧情内容不得超过1MB");codec.checkEditableShape(request.graph());
        if(saved==null)editor.insertDraft(chapterId,content,actor);
        else if(editor.updateDraft(chapterId,request.expectedRevision(),content,actor)!=1)throw conflict();
        logs.operation(IdWorker.getId(),actor,"STORY_DRAFT_SAVE",chapterId);return draft(chapterId);
    }
    public StoryEditorCodec.Validation validate(Graph graph) { return codec.validate(graph); }
    private void requireValid(Graph graph) { var result=codec.validate(graph);if(!result.valid())throw invalid(result.issues().get(0).message()); }
    @Transactional public Map<String,Object> publish(Long actor,Long chapterId,Publish request) {
        if(!request.culturalChecked() || !request.bilingualChecked() || request.note().strip().length()<5)throw invalid("发布前需要文化/双语核对和至少5个字符的真实说明");
        lock(chapterId);var saved=revision(chapterId,request.expectedRevision());if(saved==null)throw invalid("请先保存草稿");
        String existing=editor.alreadyPublished(chapterId,request.expectedRevision());
        if(existing!=null)return Map.of("storyId",existing,"version",stories.selectById(Long.valueOf(existing)).version,"alreadyPublished",true);
        Graph graph=codec.read(saved.get("graph_json").toString());requireValid(graph);
        long id=IdWorker.getId();int version=editor.maxVersion(chapterId)+1;
        var compiled=codec.compile(graph,id,chapterId,version,true);
        stories.insert(compiled.story());compiled.nodes().forEach(nodes::insert);compiled.edges().forEach(editor::edge);
        editor.publication(id,chapterId,request.expectedRevision(),codec.write(graph),actor,request.note().strip());
        editor.visibility(chapterId,"PUBLISHED");logs.operation(IdWorker.getId(),actor,"STORY_PUBLISH",id);
        return Map.of("storyId",Long.toString(id),"version",version,"alreadyPublished",false);
    }
    public List<Map<String,Object>> versions(Long chapterId) { chapter(chapterId);return editor.versions(chapterId); }
    public Graph versionGraph(Long chapterId,Long storyId) {
        Story story=stories.selectById(storyId);if(story==null || !chapterId.equals(story.chapterId))throw missing();return publishedGraph(story);
    }
    @Transactional public Map<String,Object> restore(Long actor,Long chapterId,Restore request) {
        lock(chapterId);var graph=versionGraph(chapterId,request.storyId());
        var result=save(actor,chapterId,new Save(request.expectedRevision(),graph));logs.operation(IdWorker.getId(),actor,"STORY_RESTORE",request.storyId());return result;
    }
    @Transactional public Map<String,Object> createChapter(Long actor,CreateChapter request) {
        Story source=editor.latestStory(request.copyFromChapterId());if(source==null)throw missing();
        Long id=IdWorker.getId();
        try { editor.createChapter(id,request.code(),codec.write(request.title()),codec.write(request.intro())); }
        catch(DuplicateKeyException e) { throw new BusinessException(HttpStatus.CONFLICT,"CHAPTER_CODE_EXISTS","篇章代码已存在"); }
        editor.insertDraft(id,codec.write(publishedGraph(source)),actor);
        for(var card:stories.cards(source.chapterId))editor.copyCard(IdWorker.getId(),id,card.get("content").toString(),card.get("sourceTitle").toString(),card.get("sourceUrl").toString());
        logs.operation(IdWorker.getId(),actor,"STORY_CREATE",id);return draft(id);
    }
    @Transactional public void archive(Long actor,Long chapterId) {
        lock(chapterId);editor.visibility(chapterId,"ARCHIVED");logs.operation(IdWorker.getId(),actor,"STORY_ARCHIVE",chapterId);
    }
    @Transactional public Map<String,Object> startPreview(Long actor,Long chapterId,Preview request) {
        lock(chapterId);var saved=revision(chapterId,request.expectedRevision());if(saved==null)throw invalid("请先保存草稿");
        Graph graph=codec.read(saved.get("graph_json").toString());requireValid(graph);editor.prunePreviews();
        ObjectNode state=json.createObjectNode().put("nodeKey",graph.startNode()).put("perspective",request.perspective()).put("status","PLAYING");
        state.set("variables",json.valueToTree(StoryGraph.variables(graph.initialVariables())));state.set("history",json.createArrayNode());
        String id=UUID.randomUUID().toString();editor.preview(id,actor,chapterId,codec.write(graph),codec.write(state));return preview(actor,id);
    }
    @Transactional public Map<String,Object> preview(Long actor,String id) {
        var saved=editor.lockPreview(id,actor);if(saved==null)throw missing();return previewView(saved);
    }
    private Map<String,Object> previewView(Map<String,Object> saved) {
        Graph graph=codec.read(saved.get("graph_json").toString());var compiled=codec.compile(graph,1L,1L,1,false);
        ObjectNode state=object(saved.get("state_json").toString());Node node=compiled.runtime().node(compiled.ids().get(state.path("nodeKey").asText()));
        JsonNode content=object(node.contentI18n);String perspective=state.path("perspective").asText();boolean dual="dual".equals(node.nodeType);
        return Map.of("id",saved.get("id"),"revision",saved.get("revision"),"state",state,"expiresAt",saved.get("expires_at"),"companionOptions",dual?content.path("options"+(perspective.equals("A")?"B":"A")):json.createArrayNode(),
            "node",new StoryView.NodeView(node.nodeKey,node.nodeKey,node.nodeType,content.path("title"),content.path("body"),dual?content.path("options"+perspective):node.nodeType.equals("choice")?content.path("options"):null,dual?content.path("clue"+perspective):null));
    }
    private static void action(String actual,String expected) { if(!expected.equals(actual))throw invalid("当前预览节点不支持此动作"); }
    private static boolean option(JsonNode options,String answer) { for(JsonNode o:options)if(o.path("id").asText().equals(answer))return true;return false; }
    @Transactional public Map<String,Object> stepPreview(Long actor,String id,PreviewAction request) {
        var saved=editor.lockPreview(id,actor);if(saved==null)throw missing();
        if(((Number)saved.get("revision")).longValue()!=request.expectedRevision())throw new BusinessException(HttpStatus.CONFLICT,"PREVIEW_CONFLICT","预览进度已变化，请重新读取");
        ObjectNode state=object(saved.get("state_json").toString());if(!state.path("status").asText().equals("PLAYING"))throw invalid("预览已结束，请重新开始");
        Graph graph=codec.read(saved.get("graph_json").toString());var compiled=codec.compile(graph,1L,1L,1,false);StoryGraph runtime=compiled.runtime();
        Node node=runtime.node(compiled.ids().get(state.path("nodeKey").asText()));JsonNode config=runtime.read(node.config),content=runtime.read(node.contentI18n);
        var vars=StoryGraph.variables(state.path("variables"));ObjectNode feedback=json.createObjectNode();String transition=request.action();
        switch(node.nodeType) {
            case "narrative" -> { action(request.action(),"continue");StoryGraph.apply(vars,config.path("effects")); }
            case "choice" -> {
                JsonNode choice=config.path("choices").path(request.action());if(choice.isMissingNode())throw invalid("预览选项不存在");StoryGraph.apply(vars,choice.path("effects"));
                if(choice.path("feedback").isObject())feedback.setAll((ObjectNode)choice.path("feedback"));
            }
            case "dual" -> {
                action(request.action(),"submit");String side=state.path("perspective").asText(),other=side.equals("A")?"B":"A";
                if(!option(content.path("options"+side),request.answer()) || !option(content.path("options"+other),request.companionAnswer()))throw invalid("请分别填写双方预览答案的选项标识");
                var judged=StoryRules.judge(config,side.equals("A")?request.answer():request.companionAnswer(),side.equals("B")?request.answer():request.companionAnswer(),false);
                vars.put("harmony",Math.min(10000,vars.get("harmony")+judged.harmonyDelta()));vars.put("knowledge",Math.min(10000,vars.get("knowledge")+judged.knowledgeDelta()));
                feedback.setAll((ObjectNode)config.path("explanation"));feedback.put("success",judged.success());feedback.put("harmonyDelta",judged.harmonyDelta());
            }
            case "gift","checkin" -> {
                action(request.action(),"submit");String zh=Objects.toString(request.textZh(),"").strip(),en=Objects.toString(request.textEn(),"").strip();
                if(zh.isEmpty() && en.isEmpty() || !zh.isEmpty() && zh.length()<5 || !en.isEmpty() && en.length()<5)throw invalid("预览文字至少用一种语言填写5个字符");
                StoryGraph.apply(vars,config.path("effects"));feedback.put("zh","仅用于预览，不会保存用户回礼或打卡。").put("en","Preview only. No user submission is created.");
            }
            case "ending" -> { action(request.action(),"finish");state.put("status","COMPLETED");feedback.put("zh","预览结算完成，不发放真实印章。").put("en","Preview complete. No real stamp is awarded."); }
            default -> throw invalid("未知节点");
        }
        if(!node.nodeType.equals("ending"))state.put("nodeKey",runtime.node(runtime.next(node.id,transition,vars)).nodeKey);
        state.set("variables",json.valueToTree(vars));state.set("feedback",feedback);
        state.withArray("history").add(json.createObjectNode().put("nodeKey",node.nodeKey).put("action",request.action()));
        editor.updatePreview(id,codec.write(state));return preview(actor,id);
    }
}
