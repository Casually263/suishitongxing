package com.suishi.story.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

public final class StoryEntities {
    private StoryEntities() {}
    @TableName("story") public static class Story {
        public Long id, chapterId, startNode;
        public Integer version;
        public String status, initialVariables;
    }
    @TableName("story_node") public static class Node {
        public Long id, storyId;
        public String nodeKey, nodeType, contentI18n, config;
    }
    @TableName("story_edge") public static class Edge {
        public Long id, storyId, fromNode, toNode;
        public String actionKey, conditionJson;
        public Integer priority;
    }
    @TableName("story_progress") public static class Progress {
        public Long id, userId, chapterId, storyId, sessionId, currentNode, version;
        public String perspective, variables, history, status;
        public LocalDateTime updatedAt;
    }
    @TableName("story_session") public static class Session {
        public Long id, storyId, userA, userB;
        public String mode, inviteCode;
        public LocalDateTime inviteDeadline, createdAt;
    }
    @TableName("story_dual_round") public static class Round {
        public Long id, sessionId, nodeId;
        public String answerA, answerB, state, resolutionMode;
        public LocalDateTime deadline;
        public Boolean extended, success;
        public Integer harmonyDelta;
    }
}
