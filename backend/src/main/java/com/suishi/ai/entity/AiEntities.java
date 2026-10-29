package com.suishi.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AiEntities {
    private AiEntities() {}
    @TableName("ai_config") public static class Config {
        public Long id, version;
        public Boolean enabled, includeUsage;
        public String baseUrl, model;
        public BigDecimal temperature;
        public Integer timeoutMs, retries, dailyLimit, maxRounds, minuteLimit, maxTokens;
        public LocalDateTime updatedAt;
    }
    @TableName("ai_chat_log") public static class ChatLog {
        public Long id, userId, progressId, nodeId;
        public String requestId, requestHash, contextHash, language, userTextMasked, replyMasked, sourceIds, mode, status, fallbackReason, model;
        public Integer attempts, latencyMs, promptTokens, completionTokens;
        public Boolean usageComplete;
        public LocalDateTime createdAt, finishedAt;
    }
}
