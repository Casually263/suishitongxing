package com.suishi.story.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.story.entity.StoryEntities.*;
import java.util.*;

/** Small declarative DSL: no SQL fragments, JavaScript, SpEL or arbitrary property access. */
public final class StoryGraph {
    public static final Set<String> VARIABLES=Set.of("knowledge","harmony","care");
    private static final Set<String> TYPES=Set.of("narrative","choice","dual","gift","checkin","ending");
    private final ObjectMapper json;
    private final Map<Long,Node> nodes=new LinkedHashMap<>();
    private final List<Edge> edges;
    public StoryGraph(ObjectMapper json,Story story,List<Node> nodeList,List<Edge> edges) {
        this.json=json; this.edges=edges;
        if(nodeList.isEmpty() || nodeList.size()>200 || edges.size()>800) throw new IllegalArgumentException("Graph size");
        for(Node node:nodeList) {
            if(!Objects.equals(node.storyId,story.id) || !TYPES.contains(node.nodeType) || nodes.put(node.id,node)!=null) throw new IllegalArgumentException("Invalid node");
            JsonNode content=read(node.contentI18n), config=read(node.config);
            bilingual(content.path("title")); bilingual(content.path("body"));
            validateEffects(config.path("effects"));
            if(node.nodeType.equals("choice")) {
                if(!content.path("options").isArray() || content.path("options").isEmpty()) throw new IllegalArgumentException("Missing choices");
                Set<String> ids=new HashSet<>();
                for(JsonNode option:content.path("options")) {
                    String id=option.path("id").asText();
                    if(!id.matches("[a-z][a-z0-9_-]{0,63}") || !ids.add(id) || !config.path("choices").has(id)) throw new IllegalArgumentException("Invalid choice");
                    bilingual(option.path("label")); validateEffects(config.path("choices").path(id).path("effects"));
                }
                if(config.path("choices").size()!=ids.size())throw new IllegalArgumentException("Hidden choices");
            }
            if(node.nodeType.equals("dual")) {
                bilingual(content.path("clueA")); bilingual(content.path("clueB"));
                for(String side:List.of("A","B")) {
                    Set<String> ids=new HashSet<>();
                    for(JsonNode option:content.path("options"+side)) { ids.add(option.path("id").asText()); bilingual(option.path("label")); }
                    if(ids.isEmpty() || !ids.contains(config.path("answer"+side).asText())) throw new IllegalArgumentException("Invalid dual answers");
                }
                bilingual(config.path("explanation")); StoryRules.validateDual(config);
            }
        }
        node(story.startNode); variables(read(story.initialVariables));
        for(Edge edge:edges) {
            if(!Objects.equals(edge.storyId,story.id)) throw new IllegalArgumentException("Cross-version edge");
            node(edge.fromNode); node(edge.toNode); validateCondition(read(edge.conditionJson));
            Node from=node(edge.fromNode);
            boolean validAction=switch(from.nodeType) {
                case "narrative" -> edge.actionKey.equals("continue");
                case "choice" -> read(from.config).path("choices").has(edge.actionKey);
                case "dual","gift","checkin" -> edge.actionKey.equals("submit");
                default -> false;
            };
            if(!validAction) throw new IllegalArgumentException("Invalid edge action");
        }
        Map<Long,Integer> colors=new HashMap<>(); visit(story.startNode,colors);
        if(colors.size()!=nodes.size()) throw new IllegalArgumentException("Unreachable nodes");
        for(Node node:nodes.values()) {
            List<Edge> out=edges.stream().filter(e->e.fromNode.equals(node.id)).toList();
            if(node.nodeType.equals("ending")!=out.isEmpty()) throw new IllegalArgumentException("Invalid terminal");
            if(!node.nodeType.equals("ending")) {
                Set<String> actions=new HashSet<>();
                if(node.nodeType.equals("choice")) read(node.config).path("choices").fieldNames().forEachRemaining(actions::add);
                else actions.add(node.nodeType.equals("narrative")?"continue":"submit");
                for(String action:actions) if(out.stream().noneMatch(e->e.actionKey.equals(action) && read(e.conditionJson).isEmpty())) throw new IllegalArgumentException("Missing fallback edge");
            }
        }
    }
    private void visit(Long id,Map<Long,Integer> colors) {
        if(colors.getOrDefault(id,0)==1) throw new IllegalArgumentException("Cycle");
        if(colors.getOrDefault(id,0)==2) return;
        colors.put(id,1); for(Edge edge:edges) if(edge.fromNode.equals(id)) visit(edge.toNode,colors); colors.put(id,2);
    }
    public Node node(Long id) { Node node=nodes.get(id); if(node==null) throw new IllegalArgumentException("Unknown node"); return node; }
    public JsonNode read(String value) {
        try { JsonNode result=json.readTree(value); if(result==null || !result.isObject()) throw new IllegalArgumentException("Expected object"); return result; }
        catch(java.io.IOException e) { throw new IllegalArgumentException("Invalid graph JSON",e); }
    }
    private static void bilingual(JsonNode value) {
        if(!value.path("zh").isTextual() || value.path("zh").asText().isBlank() || !value.path("en").isTextual() || value.path("en").asText().isBlank()) throw new IllegalArgumentException("Missing translation");
    }
    private static void validateEffects(JsonNode effects) {
        if(effects.isMissingNode()) return;
        if(!effects.isObject()) throw new IllegalArgumentException("Invalid effects");
        effects.fields().forEachRemaining(e->{if(!VARIABLES.contains(e.getKey()) || !e.getValue().isIntegralNumber() || !e.getValue().canConvertToInt() || Math.abs((long)e.getValue().intValue())>100) throw new IllegalArgumentException("Invalid variable effect");});
    }
    private static void validateCondition(JsonNode condition) {
        if(!condition.isObject()) throw new IllegalArgumentException("Invalid condition");
        if(condition.isEmpty()) return;
        if(condition.size()!=3 || !VARIABLES.contains(condition.path("var").asText()) || !Set.of("eq","ne","gte","gt","lte","lt").contains(condition.path("op").asText()) || !condition.path("value").isIntegralNumber() || !condition.path("value").canConvertToInt()) throw new IllegalArgumentException("Invalid condition DSL");
    }
    public static Map<String,Integer> variables(JsonNode value) {
        Map<String,Integer> result=new LinkedHashMap<>();
        value.fields().forEachRemaining(e->{if(!VARIABLES.contains(e.getKey()) || !e.getValue().isIntegralNumber() || !e.getValue().canConvertToInt() || e.getValue().intValue()<0 || e.getValue().intValue()>10000) throw new IllegalArgumentException("Invalid variables"); result.put(e.getKey(),e.getValue().intValue());});
        VARIABLES.forEach(key->result.putIfAbsent(key,0)); return result;
    }
    public static void apply(Map<String,Integer> variables,JsonNode effects) {
        validateEffects(effects);
        effects.fields().forEachRemaining(e->variables.put(e.getKey(),Math.max(0,Math.min(10000,variables.getOrDefault(e.getKey(),0)+e.getValue().intValue()))));
    }
    public static boolean matches(JsonNode condition,Map<String,Integer> variables) {
        validateCondition(condition); if(condition.isEmpty()) return true;
        int actual=variables.getOrDefault(condition.path("var").asText(),0), expected=condition.path("value").intValue();
        return switch(condition.path("op").asText()) {case "eq"->actual==expected;case "ne"->actual!=expected;case "gte"->actual>=expected;case "gt"->actual>expected;case "lte"->actual<=expected;case "lt"->actual<expected;default->throw new IllegalArgumentException("Invalid operator");};
    }
    public Long next(Long current,String action,Map<String,Integer> variables) {
        List<Edge> candidates=edges.stream().filter(e->e.fromNode.equals(current) && e.actionKey.equals(action) && matches(read(e.conditionJson),variables)).sorted(Comparator.comparingInt((Edge e)->e.priority).reversed()).toList();
        if(candidates.isEmpty() || candidates.size()>1 && candidates.get(0).priority.equals(candidates.get(1).priority)) throw new IllegalArgumentException("Ambiguous or missing transition");
        return candidates.get(0).toNode;
    }
}
