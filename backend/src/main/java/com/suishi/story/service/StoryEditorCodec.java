package com.suishi.story.service;

import com.fasterxml.jackson.databind.*;
import com.suishi.story.dto.EditorRequests.*;
import com.suishi.story.entity.StoryEntities.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class StoryEditorCodec {
    private final ObjectMapper json;
    public StoryEditorCodec(ObjectMapper json) { this.json=json; }
    public record Issue(String location,String code,String message) {}
    public record Validation(boolean valid,List<Issue> issues) {}
    public record Compiled(Story story,List<Node> nodes,List<Edge> edges,Map<String,Long> ids,StoryGraph runtime) {}
    public String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalArgumentException("Invalid graph JSON",e); } }
    public Graph read(String value) { try { return json.readValue(value,Graph.class); } catch(Exception e) { throw new IllegalArgumentException("Invalid graph JSON",e); } }
    public Validation validate(Graph graph) {
        List<Issue> issues=new ArrayList<>();
        try { checkEditableShape(graph); } catch(IllegalArgumentException e) { return new Validation(false,List.of(new Issue("graph","JSON_SHAPE",e.getMessage()))); }
        if(write(graph).length()>1_000_000) issues.add(new Issue("graph","SIZE_LIMIT","剧情内容不得超过1MB"));
        Set<String> ids=new HashSet<>(),edgeIds=new HashSet<>(),priorities=new HashSet<>();
        for(EditorNode node:graph.nodes()) {
            String location="node:"+node.id();
            if(!ids.add(node.id()))issues.add(new Issue(location,"DUPLICATE_NODE","节点标识重复"));
            if(!Double.isFinite(node.position().x()) || !Double.isFinite(node.position().y()))issues.add(new Issue(location,"POSITION","节点坐标必须有限"));
            try { validateNode(node); } catch(IllegalArgumentException e) { issues.add(new Issue(location,"NODE_CONFIG",e.getMessage())); }
        }
        if(!ids.contains(graph.startNode()))issues.add(new Issue("startNode","MISSING_START","请选择存在的起始节点"));
        for(EditorEdge edge:graph.edges()) {
            String location="edge:"+edge.id();
            if(!edgeIds.add(edge.id()))issues.add(new Issue(location,"DUPLICATE_EDGE","连线标识重复"));
            if(!ids.contains(edge.source()) || !ids.contains(edge.target()))issues.add(new Issue(location,"DANGLING_EDGE","连线两端必须属于当前图"));
            if(!priorities.add(edge.source()+":"+edge.action()+":"+edge.priority()))issues.add(new Issue(location,"AMBIGUOUS_PRIORITY","同一节点和动作的分支优先级不可重复"));
            if(edge.condition().isEmpty() && graph.edges().stream().filter(other->other.source().equals(edge.source()) && other.action().equals(edge.action()) && other.condition().isEmpty()).count()>1)
                issues.add(new Issue(location,"DUPLICATE_FALLBACK","每个动作只能有一条默认分支"));
            try { StoryGraph.matches(edge.condition(),Map.of()); } catch(IllegalArgumentException e) { issues.add(new Issue(location,"CONDITION","条件只支持知识/默契/关照的整数比较")); }
            if(!edge.condition().isEmpty() && graph.edges().stream().anyMatch(other->other.source().equals(edge.source()) && other.action().equals(edge.action()) && other.condition().isEmpty() && other.priority()>=edge.priority()))
                issues.add(new Issue(location,"FALLBACK_PRIORITY","有条件分支的优先级必须高于默认分支"));
        }
        try { StoryGraph.variables(graph.initialVariables()); } catch(IllegalArgumentException e) { issues.add(new Issue("initialVariables","VARIABLES","初始变量只支持0–10000的知识/默契/关照整数")); }
        if(issues.isEmpty())try { compile(graph,1L,1L,1,false); } catch(IllegalArgumentException e) {
            String message=switch(e.getMessage()) {
                case "Cycle" -> "图中存在环路，请移除循环连线";
                case "Unreachable nodes" -> "存在无法从起点到达的节点";
                case "Invalid terminal" -> "结局不能有出边，其他节点必须有出边";
                case "Missing fallback edge" -> "每个可用动作都必须有一条默认分支";
                case "Invalid edge action" -> "连线动作与来源节点的选项/类型不匹配";
                case "Missing translation" -> "标题、正文或线索缺少中英译文";
                default -> "图结构或配置不合法："+e.getMessage();
            };
            issues.add(new Issue("graph","GRAPH_STRUCTURE",message));
        }
        return new Validation(issues.isEmpty(),List.copyOf(issues));
    }
    private void bilingual(JsonNode value,int max) {
        for(String lang:List.of("zh","en")) if(!value.path(lang).isTextual() || value.path(lang).asText().isBlank() || value.path(lang).asText().length()>max)
            throw new IllegalArgumentException("中英文本均必填，且不得超过"+max+"字符");
    }
    public void checkEditableShape(Graph graph) {
        Set<String> nodeIds=new HashSet<>(),edgeIds=new HashSet<>();
        for(EditorEdge edge:graph.edges())if(!edgeIds.add(edge.id()))throw new IllegalArgumentException("连线标识不可重复");
        for(EditorNode node:graph.nodes()) {
            if(!nodeIds.add(node.id()))throw new IllegalArgumentException("节点标识不可重复");
            for(String key:List.of("title","body")) translationShape(node.content().path(key));
            for(String key:List.of("clueA","clueB"))if(node.content().has(key))translationShape(node.content().get(key));
            for(String key:List.of("options","optionsA","optionsB"))if(node.content().has(key)) {
                JsonNode options=node.content().get(key);if(!options.isArray())throw new IllegalArgumentException("选项必须为数组");
                for(JsonNode option:options) { if(!option.isObject() || !option.path("id").isTextual())throw new IllegalArgumentException("选项缺少文本标识");translationShape(option.path("label")); }
            }
            JsonNode config=node.config();
            if(config.has("effects") && !config.get("effects").isObject())throw new IllegalArgumentException("effects必须为对象");
            if(config.has("choices")) {
                if(!config.get("choices").isObject())throw new IllegalArgumentException("choices必须为对象");
                for(JsonNode choice:config.get("choices")) {
                    if(!choice.isObject() || choice.has("effects") && !choice.get("effects").isObject())throw new IllegalArgumentException("选项规则与effects必须为对象");
                    if(choice.has("feedback"))translationShape(choice.get("feedback"));
                }
            }
            if(config.has("explanation"))translationShape(config.get("explanation"));
        }
    }
    private void translationShape(JsonNode value) {
        if(!value.isObject() || !value.path("zh").isTextual() || !value.path("en").isTextual())throw new IllegalArgumentException("双语字段必须包含zh/en文本，未完成内容可留空后保存草稿");
    }
    private void options(JsonNode options) {
        if(!options.isArray() || options.isEmpty() || options.size()>20)throw new IllegalArgumentException("每组需要1–20个选项");
        Set<String> ids=new HashSet<>();
        for(JsonNode option:options) {
            if(!option.path("id").asText().matches("[a-z][a-z0-9_-]{0,63}") || !ids.add(option.path("id").asText()))throw new IllegalArgumentException("选项标识不合法或重复");
            bilingual(option.path("label"),200);
        }
    }
    private void validateNode(EditorNode node) {
        bilingual(node.content().path("title"),200);bilingual(node.content().path("body"),10000);
        Set<String> allowed=switch(node.type()) {
            case "narrative","gift","checkin" -> Set.of("effects");
            case "choice" -> Set.of("choices");
            case "dual" -> Set.of("answerA","answerB","explanation","windowHours","harmonyBase","harmonyBonus","knowledgeBonus");
            case "ending" -> Set.of();
            default -> throw new IllegalArgumentException("未知节点类型");
        };
        node.config().fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw new IllegalArgumentException("不支持的节点参数："+key);});
        if(node.type().equals("choice")) {
            options(node.content().path("options"));
            for(JsonNode choice:node.config().path("choices")) {
                choice.fieldNames().forEachRemaining(key->{if(!Set.of("effects","feedback").contains(key))throw new IllegalArgumentException("不支持的选项参数");});
                if(choice.has("feedback"))bilingual(choice.get("feedback"),2000);
            }
        }
        if(node.type().equals("dual")) { options(node.content().path("optionsA"));options(node.content().path("optionsB"));bilingual(node.content().path("clueA"),4000);bilingual(node.content().path("clueB"),4000);bilingual(node.config().path("explanation"),4000);StoryRules.validateDual(node.config()); }
    }
    public Compiled compile(Graph graph,Long storyId,Long chapterId,int version,boolean generatedIds) {
        Story story=new Story();story.id=storyId;story.chapterId=chapterId;story.version=version;story.status="PUBLISHED";story.initialVariables=write(graph.initialVariables());
        Map<String,Long> ids=new LinkedHashMap<>();long counter=1;
        for(EditorNode node:graph.nodes())ids.put(node.id(),generatedIds?com.baomidou.mybatisplus.core.toolkit.IdWorker.getId():counter++);
        story.startNode=ids.get(graph.startNode());List<Node> nodes=new ArrayList<>();List<Edge> edges=new ArrayList<>();
        for(EditorNode input:graph.nodes()) { Node n=new Node();n.id=ids.get(input.id());n.storyId=storyId;n.nodeKey=input.id();n.nodeType=input.type();n.contentI18n=write(input.content());n.config=write(input.config());nodes.add(n); }
        for(EditorEdge input:graph.edges()) { Edge e=new Edge();e.id=generatedIds?com.baomidou.mybatisplus.core.toolkit.IdWorker.getId():counter++;e.storyId=storyId;e.fromNode=ids.get(input.source());e.toNode=ids.get(input.target());e.actionKey=input.action();e.priority=input.priority();e.conditionJson=write(input.condition());edges.add(e); }
        return new Compiled(story,nodes,edges,ids,new StoryGraph(json,story,nodes,edges));
    }
    public Graph fromPublished(Story story,List<Node> nodeList,List<Edge> edgeList) {
        Map<Long,String> keys=new HashMap<>();nodeList.forEach(n->keys.put(n.id,n.nodeKey));
        Map<Long,Integer> depth=new HashMap<>();depth.put(story.startNode,0);
        for(int i=0;i<nodeList.size();i++) for(Edge edge:edgeList)if(depth.containsKey(edge.fromNode))depth.merge(edge.toNode,depth.get(edge.fromNode)+1,Math::max);
        Map<Integer,Integer> rows=new HashMap<>();List<EditorNode> nodes=new ArrayList<>();List<EditorEdge> edges=new ArrayList<>();
        try {
            for(Node n:nodeList) { int d=depth.getOrDefault(n.id,0),row=rows.merge(d,1,Integer::sum)-1;nodes.add(new EditorNode(n.nodeKey,n.nodeType,(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(n.contentI18n),(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(n.config),new Position(d*300,row*190))); }
            for(Edge e:edgeList)edges.add(new EditorEdge("edge_"+e.id,keys.get(e.fromNode),keys.get(e.toNode),e.actionKey,e.priority,(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(e.conditionJson)));
            return new Graph(keys.get(story.startNode),(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(story.initialVariables),nodes,edges);
        } catch(Exception e) { throw new IllegalArgumentException("Invalid published graph",e); }
    }
}
