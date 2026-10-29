package com.suishi.story;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.story.entity.StoryEntities.*;
import com.suishi.story.service.StoryGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StoryGraphTest {
    private final ObjectMapper json=new ObjectMapper();
    private com.fasterxml.jackson.databind.JsonNode parse(String value)throws Exception{return json.readTree(value);}
    @ParameterizedTest @CsvSource({"eq,3,true","ne,3,false","gte,3,true","gt,3,false","lte,3,true","lt,3,false","gte,4,false","lt,4,true"})
    void conditionsUseOnlyIntegerWhitelist(String op,int expected,boolean result)throws Exception {
        assertThat(StoryGraph.matches(parse("{\"var\":\"knowledge\",\"op\":\""+op+"\",\"value\":"+expected+"}"),Map.of("knowledge",3))).isEqualTo(result);
    }
    @Test void rejectsExecutableAndUnknownConditions()throws Exception {
        for(String condition:List.of("{\"expr\":\"T(java.lang.Runtime).getRuntime()\"}","{\"var\":\"password\",\"op\":\"eq\",\"value\":3}","{\"var\":\"knowledge\",\"op\":\"eval\",\"value\":3}"))assertThatThrownBy(()->StoryGraph.matches(parse(condition),Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void effectsRejectUnknownVariablesAndClampWithoutOverflow()throws Exception {
        Map<String,Integer> vars=new HashMap<>(Map.of("knowledge",9999,"harmony",1));StoryGraph.apply(vars,parse("{\"knowledge\":3,\"harmony\":-3}"));assertThat(vars).containsEntry("knowledge",10000).containsEntry("harmony",0);
        assertThatThrownBy(()->StoryGraph.apply(vars,parse("{\"admin\":1}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->StoryGraph.apply(vars,parse("{\"knowledge\":2147483647}"))).isInstanceOf(IllegalArgumentException.class);
    }
    private Story story(){Story s=new Story();s.id=1L;s.startNode=1L;s.initialVariables="{}";return s;}
    private Node node(long id,String type){Node n=new Node();n.id=id;n.storyId=1L;n.nodeType=type;n.contentI18n="{\"title\":{\"zh\":\"春\",\"en\":\"Spring\"},\"body\":{\"zh\":\"故事\",\"en\":\"Story\"}}";n.config="{}";return n;}
    private Edge edge(long id,long from,long to,int priority,String condition){Edge e=new Edge();e.id=id;e.storyId=1L;e.fromNode=from;e.toNode=to;e.actionKey="continue";e.priority=priority;e.conditionJson=condition;return e;}
    @Test void priorityBranchUsesUpdatedVariablesAndFallback() {
        var g=new StoryGraph(json,story(),List.of(node(1,"narrative"),node(2,"ending"),node(3,"ending")),List.of(edge(1,1,2,20,"{\"var\":\"knowledge\",\"op\":\"gte\",\"value\":3}"),edge(2,1,3,0,"{}")));
        assertThat(g.next(1L,"continue",Map.of("knowledge",3))).isEqualTo(2L);assertThat(g.next(1L,"continue",Map.of("knowledge",2))).isEqualTo(3L);
    }
    @Test void graphRejectsCyclesDanglingCrossVersionUnreachableAndMissingTranslation() {
        assertThatThrownBy(()->new StoryGraph(json,story(),List.of(node(1,"narrative")),List.of(edge(1,1,1,0,"{}")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new StoryGraph(json,story(),List.of(node(1,"narrative")),List.of(edge(1,1,9,0,"{}")))).isInstanceOf(IllegalArgumentException.class);
        Edge foreign=edge(1,1,2,0,"{}");foreign.storyId=9L;
        assertThatThrownBy(()->new StoryGraph(json,story(),List.of(node(1,"narrative"),node(2,"ending")),List.of(foreign))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new StoryGraph(json,story(),List.of(node(1,"ending"),node(2,"ending")),List.of())).isInstanceOf(IllegalArgumentException.class);
        Node untranslated=node(1,"ending");untranslated.contentI18n="{}";assertThatThrownBy(()->new StoryGraph(json,story(),List.of(untranslated),List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
