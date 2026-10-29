package com.suishi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.ai.entity.AiEntities.Config;
import org.apache.ibatis.annotations.*;
import java.util.Map;

public interface AiConfigMapper extends BaseMapper<Config> {
    @Update("UPDATE ai_config SET enabled=#{enabled},base_url=#{baseUrl},model=#{model},temperature=#{temperature},timeout_ms=#{timeoutMs},retries=#{retries},daily_limit=#{dailyLimit},max_rounds=#{maxRounds},minute_limit=#{minuteLimit},max_tokens=#{maxTokens},include_usage=#{includeUsage},version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=1 AND version=#{version}")
    int configure(Config config);
    @Select("SELECT name_i18n name,style_i18n style FROM ai_persona WHERE id=1") Map<String,Object> persona();
}
