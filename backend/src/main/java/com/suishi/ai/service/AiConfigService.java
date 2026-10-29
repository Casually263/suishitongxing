package com.suishi.ai.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.*;
import com.suishi.ai.config.AiProperties;
import com.suishi.ai.dto.AiRequests.*;
import com.suishi.ai.entity.AiEntities.Config;
import com.suishi.ai.mapper.*;
import com.suishi.ai.provider.AiEndpointPolicy;
import com.suishi.common.BusinessException;
import com.suishi.system.mapper.LogMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

@Service
public class AiConfigService {
    private final AiConfigMapper configs;private final AiKnowledgeMapper cards;private final AiLogMapper chats;private final LogMapper audits;
    private final AiProperties properties;private final AiEndpointPolicy endpoints;private final ObjectMapper json;
    public AiConfigService(AiConfigMapper configs,AiKnowledgeMapper cards,AiLogMapper chats,LogMapper audits,AiProperties properties,AiEndpointPolicy endpoints,ObjectMapper json){this.configs=configs;this.cards=cards;this.chats=chats;this.audits=audits;this.properties=properties;this.endpoints=endpoints;this.json=json;}
    public Config current(){Config c=configs.selectById(1L);if(c==null)throw new IllegalStateException("Missing AI configuration");if(c.baseUrl==null || c.baseUrl.isBlank())c.baseUrl=properties.getBaseUrl();if(c.model==null || c.model.isBlank())c.model=properties.getModel();return c;}
    public Map<String,Object> view(){
        Config c=current();Map<String,Object> view=new LinkedHashMap<>();view.put("config",c);view.put("keyConfigured",!properties.getApiKey().isBlank());view.put("allowedOrigins",properties.getAllowedOrigins());view.put("persona",Map.of("zh","小满","en","Xiaoman"));return view;
    }
    @Transactional public Map<String,Object> configure(Long actor,Configure request){
        String base=clean(request.baseUrl()),model=clean(request.model());
        if(!properties.getApiKey().isBlank() && (base.contains(properties.getApiKey()) || model.contains(properties.getApiKey())))throw invalid("密钥只能保存在服务端环境变量中");
        if(!base.isBlank())try{endpoints.endpoint(base);}catch(IllegalArgumentException e){throw invalid("模型地址必须是服务端允许的HTTPS地址，不得含凭据、查询参数或内网地址");}
        Config c=new Config();c.id=1L;c.version=request.expectedVersion();c.enabled=request.enabled();c.baseUrl=base.isEmpty()?null:base;c.model=model.isEmpty()?null:model;
        c.temperature=request.temperature();c.timeoutMs=request.timeoutMs();c.retries=request.retries();c.dailyLimit=request.dailyLimit();c.maxRounds=request.maxRounds();c.minuteLimit=request.minuteLimit();c.maxTokens=request.maxTokens();c.includeUsage=request.includeUsage();
        if(configs.configure(c)!=1)throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","AI配置已更新，请刷新");
        audits.operation(IdWorker.getId(),actor,"AI_CONFIG_UPDATE",1L);return view();
    }
    public List<Map<String,Object>> knowledge(){var result=cards.all();result.forEach(card->card.put("content",read(card.get("content").toString())));return result;}
    @Transactional public void review(Long actor,Long id,Review request){
        Map<String,Object> card=cards.lock(id);if(card==null)throw new BusinessException(HttpStatus.NOT_FOUND,"CARD_NOT_FOUND","知识卡不存在");
        if(!request.expectedHash().equals(card.get("contentHash")))throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","卡片或出处已变化，请重新核对");
        if(request.decision().equals("APPROVED")){
            if(!request.culturalChecked() || !request.bilingualChecked())throw invalid("请完成文化事实与中英内容的核对");
            JsonNode content=read(card.get("content").toString());
            for(String field:List.of("title","body"))for(String language:List.of("zh","en"))if(content.path(field).path(language).asText().isBlank())throw invalid("知识卡缺少中英内容");
            URI source;try{source=URI.create(card.get("sourceUrl").toString());}catch(Exception e){throw invalid("出处地址不合法");}
            if(!"https".equals(source.getScheme()) || source.getHost()==null || source.getUserInfo()!=null)throw invalid("请提供明确的HTTPS知识出处");
        }
        cards.review(id,request.expectedHash(),request.decision(),actor,request.note().strip());cards.status(id,request.decision().equals("APPROVED")?"APPROVED":"SOURCE_CHECKED");
        audits.operation(IdWorker.getId(),actor,"AI_KNOWLEDGE_"+request.decision(),id);
    }
    public Map<String,Object> usage(){return Map.of("today",chats.usage(),"latest",chats.latestMetadata(),"timezone","UTC","source","DATABASE","includesDevelopmentAccounts",true);}
    private static String clean(String value){return value==null?"":value.strip();}
    private static BusinessException invalid(String message){return new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_AI_CONFIG",message);}
    private JsonNode read(String text){try{return json.readTree(text);}catch(Exception e){throw invalid("知识卡内容格式不合法");}}
}
