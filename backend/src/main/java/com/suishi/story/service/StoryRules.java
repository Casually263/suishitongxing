package com.suishi.story.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Shared by live play and isolated editor previews. Model output never enters these rules. */
public final class StoryRules {
    private StoryRules() {}
    public record Judgment(boolean success,int harmonyDelta,int knowledgeDelta) {}
    public static int windowHours(JsonNode config) { return config.path("windowHours").asInt(48); }
    public static void validateDual(JsonNode config) {
        for(String key:List.of("harmonyBase","harmonyBonus","knowledgeBonus")) number(config,key,0,100);
        number(config,"windowHours",1,168);
    }
    private static void number(JsonNode config,String key,int min,int max) {
        if(config.has(key) && (!config.get(key).isIntegralNumber() || !config.get(key).canConvertToInt() || config.get(key).intValue()<min || config.get(key).intValue()>max))
            throw new IllegalArgumentException("Invalid dual parameter: "+key);
    }
    public static Judgment judge(JsonNode config,String answerA,String answerB,boolean skipped) {
        validateDual(config);
        boolean success=!skipped && config.path("answerA").asText().equals(answerA) && config.path("answerB").asText().equals(answerB);
        return new Judgment(success,skipped?0:config.path("harmonyBase").asInt(10)+(success?config.path("harmonyBonus").asInt(5):0),success?config.path("knowledgeBonus").asInt(1):0);
    }
}
