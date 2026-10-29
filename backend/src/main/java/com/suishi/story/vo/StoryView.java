package com.suishi.story.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Explicit player projection: never contains server config, graph edges or another clue/answer. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoryView {
    public boolean started;
    public String chapterId, storyId, status, perspective, partnerMode, inviteCode;
    public Integer storyVersion;
    public Long version;
    public Boolean partnerJoined;
    public NodeView node;
    public JsonNode variables, history;
    public DualView dual;
    public List<Map<String,Object>> submissions, rewards;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeView(String id,String key,String type,JsonNode title,JsonNode body,JsonNode options,JsonNode clue) {}
    public record DualView(String state,String deadline,boolean extended,boolean submitted) {}
}
