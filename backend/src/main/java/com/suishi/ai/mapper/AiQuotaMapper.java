package com.suishi.ai.mapper;

import org.apache.ibatis.annotations.*;

public interface AiQuotaMapper {
    @Insert("INSERT IGNORE INTO ai_usage_daily(user_id,usage_date,calls) VALUES(#{userId},UTC_DATE(),0)") void ensureDaily(Long userId);
    @Select("SELECT calls FROM ai_usage_daily WHERE user_id=#{userId} AND usage_date=UTC_DATE() FOR UPDATE") int daily(Long userId);
    @Update("UPDATE ai_usage_daily SET calls=calls+1 WHERE user_id=#{userId} AND usage_date=UTC_DATE()") void incrementDaily(Long userId);
    @Insert("INSERT IGNORE INTO ai_usage_progress(progress_id,rounds) VALUES(#{progressId},0)") void ensureProgress(Long progressId);
    @Select("SELECT rounds FROM ai_usage_progress WHERE progress_id=#{progressId} FOR UPDATE") int rounds(Long progressId);
    @Update("UPDATE ai_usage_progress SET rounds=rounds+1 WHERE progress_id=#{progressId}") void incrementRounds(Long progressId);
}
