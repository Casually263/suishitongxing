package com.suishi.story;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.suishi.story.dto.EditorRequests.*;
import com.suishi.story.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StoryEditorCodecTest {
    private final ObjectMapper json=new ObjectMapper();
    private final StoryEditorCodec codec=new StoryEditorCodec(json);
    private ObjectNode obj(String text) { try { return (ObjectNode)json.readTree(text); } catch(Exception e) { throw new IllegalArgumentException(e); } }
    private EditorNode node(String id,String type) { return new EditorNode(id,type,obj("{\"title\":{\"zh\":\"春信\",\"en\":\"A spring letter\"},\"body\":{\"zh\":\"原创测试故事\",\"en\":\"An original test story\"}}"),obj("{}"),new Position(12,34)); }
    private EditorEdge edge(String id,String source,String target,String action,int priority,String condition) { return new EditorEdge(id,source,target,action,priority,obj(condition)); }
    private Graph simple() { return new Graph("opening",obj("{\"knowledge\":0}"),List.of(node("opening","narrative"),node("ending","ending")),List.of(edge("e1","opening","ending","continue",0,"{}"))); }
    private ObjectNode tree(Graph graph) { return (ObjectNode)json.valueToTree(graph); }
    private Graph graph(ObjectNode node) { return codec.read(node.toString()); }
    @Test void validSnapshotCompilesWithNewIdsAndRetainsBilingualContentAndLayout() {
        Graph graph=simple();assertThat(codec.validate(graph).valid()).isTrue();var runtime=codec.compile(graph,77L,9L,3,true);
        assertThat(runtime.story().id).isEqualTo(77L);assertThat(runtime.story().version).isEqualTo(3);
        assertThat(runtime.nodes()).allMatch(n->n.storyId.equals(77L));assertThat(runtime.runtime().node(runtime.story().startNode).nodeKey).isEqualTo("opening");
        assertThat(codec.read(codec.write(graph))).isEqualTo(graph);assertThat(graph.nodes().get(0).position().x()).isEqualTo(12);
        assertThat(runtime.runtime().next(runtime.story().startNode,"continue",Map.of())).isEqualTo(runtime.ids().get("ending"));
    }
    @Test void duplicatePrioritiesAndTwoFallbacksAreRejectedBeforeDatabaseInsert() {
        var tree=tree(simple());ArrayNode edges=(ArrayNode)tree.get("edges");edges.add(edges.get(0).deepCopy());((ObjectNode)edges.get(1)).put("id","second_edge");
        var result=codec.validate(graph(tree));assertThat(result.valid()).isFalse();assertThat(result.issues()).anyMatch(i->i.code().equals("AMBIGUOUS_PRIORITY"));assertThat(result.issues()).anyMatch(i->i.code().equals("DUPLICATE_FALLBACK"));
    }
    @Test void conditionalEdgesNeedALowerPriorityFallback() {
        var t=tree(simple());ObjectNode fallback=(ObjectNode)t.withArray("edges").get(0);fallback.put("priority",20);
        ObjectNode conditional=fallback.deepCopy().put("id","conditional").put("priority",10);conditional.set("condition",obj("{\"var\":\"knowledge\",\"op\":\"gte\",\"value\":3}"));t.withArray("edges").add(conditional);
        assertThat(codec.validate(graph(t)).issues()).anyMatch(i->i.code().equals("FALLBACK_PRIORITY"));
    }
    @Test void danglingUnreachableAndCyclicGraphsCannotBePublished() {
        var t=tree(simple());((ObjectNode)t.withArray("edges").get(0)).put("target","missing");assertThat(codec.validate(graph(t)).issues()).anyMatch(i->i.code().equals("DANGLING_EDGE"));
        t=tree(simple());t.withArray("nodes").add(json.valueToTree(node("unreachable","ending")));assertThat(codec.validate(graph(t)).valid()).isFalse();
        t=tree(simple());((ObjectNode)t.withArray("edges").get(0)).put("target","opening");assertThat(codec.validate(graph(t)).issues()).anyMatch(i->i.message().contains("环路"));
    }
    @Test void incompleteTranslationsMayBeSavedButCannotBePublished() {
        var t=tree(simple());((ObjectNode)t.withArray("nodes").get(0).get("content").get("body")).put("en","");Graph g=graph(t);
        assertThatCode(()->codec.checkEditableShape(g)).doesNotThrowAnyException();assertThat(codec.validate(g).valid()).isFalse();
    }
    @Test void malformedNestedJsonCannotCrashPropertyEditors() {
        var t=tree(simple());((ObjectNode)t.withArray("nodes").get(0).get("config")).put("choices","not-an-object");
        assertThatThrownBy(()->codec.checkEditableShape(graph(t))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unknownExecutableParametersAndDslAreRejected() {
        var t=tree(simple());((ObjectNode)t.withArray("nodes").get(0).get("config")).put("eval","Runtime.exec");assertThat(codec.validate(graph(t)).valid()).isFalse();
        t=tree(simple());((ObjectNode)t.withArray("edges").get(0)).set("condition",obj("{\"expr\":\"knowledge > 1\"}"));assertThat(codec.validate(graph(t)).issues()).anyMatch(i->i.code().equals("CONDITION"));
    }
    @Test void runtimeRulesKeepOldDefaultsAndHonorConfiguredScoresWithoutModelInput() {
        JsonNode defaults=obj("{\"answerA\":\"first\",\"answerB\":\"renewal\"}");var old=StoryRules.judge(defaults,"first","renewal",false);
        assertThat(old.harmonyDelta()).isEqualTo(15);assertThat(old.knowledgeDelta()).isEqualTo(1);assertThat(StoryRules.windowHours(defaults)).isEqualTo(48);
        JsonNode configured=obj("{\"answerA\":\"first\",\"answerB\":\"renewal\",\"harmonyBase\":3,\"harmonyBonus\":8,\"knowledgeBonus\":4,\"windowHours\":12}");
        assertThat(StoryRules.judge(configured,"first","renewal",false)).isEqualTo(new StoryRules.Judgment(true,11,4));
        assertThat(StoryRules.judge(configured,"wrong","renewal",false)).isEqualTo(new StoryRules.Judgment(false,3,0));
        assertThat(StoryRules.judge(configured,null,null,true)).isEqualTo(new StoryRules.Judgment(false,0,0));assertThat(StoryRules.windowHours(configured)).isEqualTo(12);
    }
    @Test void dualParametersCannotBeNegativeFractionalOrUnbounded() {
        for(String config:List.of("{\"harmonyBonus\":-1}","{\"knowledgeBonus\":101}","{\"harmonyBase\":0.5}","{\"windowHours\":0}","{\"windowHours\":169}"))
            assertThatThrownBy(()->StoryRules.validateDual(obj(config))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void legacyPublishedGraphsCanBeCopiedWithoutReusingRuntimeNodeIds() {
        var c=codec.compile(simple(),55L,1L,1,true);Graph copied=codec.fromPublished(c.story(),c.nodes(),c.edges());var next=codec.compile(copied,66L,1L,2,true);
        assertThat(copied.startNode()).isEqualTo("opening");assertThat(codec.validate(copied).valid()).isTrue();assertThat(next.nodes().stream().map(n->n.id).toList()).doesNotContainAnyElementsOf(c.nodes().stream().map(n->n.id).toList());
    }
}
