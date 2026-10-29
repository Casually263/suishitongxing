package com.suishi.ai.service;

import com.suishi.ai.entity.AiEntities.Config;
import com.suishi.ai.mapper.AiQuotaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQuotaService {
    private final AiQuotaMapper mapper;
    public AiQuotaService(AiQuotaMapper mapper){this.mapper=mapper;}
    @Transactional public boolean reserve(Long userId,Long progressId,Config config,boolean firstAttempt){
        mapper.ensureDaily(userId);int calls=mapper.daily(userId);if(calls>=config.dailyLimit)return false;
        if(firstAttempt){mapper.ensureProgress(progressId);if(mapper.rounds(progressId)>=config.maxRounds)return false;mapper.incrementRounds(progressId);}
        mapper.incrementDaily(userId);return true;
    }
}
