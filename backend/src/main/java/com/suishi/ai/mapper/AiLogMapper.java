package com.suishi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.ai.entity.AiEntities.ChatLog;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface AiLogMapper extends BaseMapper<ChatLog> {
    @Select("SELECT * FROM ai_chat_log WHERE user_id=#{userId} AND request_id=#{requestId}")
    ChatLog request(@Param("userId") Long userId,@Param("requestId") String requestId);
    @Select("SELECT * FROM ai_chat_log WHERE user_id=#{userId} AND progress_id=#{progressId} ORDER BY created_at DESC,id DESC LIMIT 20")
    List<ChatLog> history(@Param("userId") Long userId,@Param("progressId") Long progressId);
    @Select("SELECT * FROM ai_chat_log WHERE user_id=#{userId} AND progress_id=#{progressId} AND context_hash=#{hash} AND status='COMPLETED' ORDER BY created_at DESC,id DESC LIMIT 2")
    List<ChatLog> recent(@Param("userId") Long userId,@Param("progressId") Long progressId,@Param("hash") String hash);
    @Select("SELECT COUNT(*) requests,COALESCE(SUM(attempts),0) attempts,COALESCE(SUM(mode='MODEL' AND status='COMPLETED'),0) modelReplies,COALESCE(SUM(mode='SCRIPT' AND status='COMPLETED'),0) scriptReplies,COALESCE(AVG(latency_ms),0) averageLatencyMs,SUM(prompt_tokens) knownPromptTokens,SUM(completion_tokens) knownCompletionTokens,COALESCE(SUM(attempts>0 AND usage_complete=FALSE),0) unknownUsageRequests FROM ai_chat_log WHERE created_at >= UTC_DATE()")
    Map<String,Object> usage();
    @Select("SELECT CAST(id AS CHAR) id,CAST(user_id AS CHAR) userId,mode,status,fallback_reason fallbackReason,attempts,latency_ms latencyMs,prompt_tokens promptTokens,completion_tokens completionTokens,usage_complete usageComplete,created_at createdAt FROM ai_chat_log ORDER BY created_at DESC,id DESC LIMIT 20")
    List<Map<String,Object>> latestMetadata();
}
