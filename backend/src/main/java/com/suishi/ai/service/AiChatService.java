package com.suishi.ai.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.ai.config.AiProperties;
import com.suishi.ai.dto.AiRequests.Chat;
import com.suishi.ai.entity.AiEntities.*;
import com.suishi.ai.mapper.AiLogMapper;
import com.suishi.ai.provider.*;
import com.suishi.common.BusinessException;
import com.suishi.security.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AiChatService {
    private static final DefaultRedisScript<Long> RELEASE=new DefaultRedisScript<>("if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end return 0",Long.class);
    private final AiContextService contexts;private final AiConfigService configs;private final AiQuotaService quotas;private final AiSafety safety;
    private final AiProvider provider;private final ScriptedAiProvider script;private final AiProperties properties;private final AiLogMapper logs;
    private final RedisSessionStore limits;private final StringRedisTemplate redis;private final ObjectMapper json;private final ExecutorService executor;private final String prefix;
    private final Semaphore capacity=new Semaphore(4);
    public AiChatService(AiContextService contexts,AiConfigService configs,AiQuotaService quotas,AiSafety safety,@Qualifier("openAiCompatibleProvider") AiProvider provider,ScriptedAiProvider script,AiProperties properties,AiLogMapper logs,RedisSessionStore limits,StringRedisTemplate redis,ObjectMapper json,@Qualifier("aiChatExecutor") ExecutorService executor,@Value("${app.redis-prefix}") String prefix){
        this.contexts=contexts;this.configs=configs;this.quotas=quotas;this.safety=safety;this.provider=provider;this.script=script;this.properties=properties;this.logs=logs;this.limits=limits;this.redis=redis;this.json=json;this.executor=executor;this.prefix=prefix;
    }
    public record History(String id,String nodeId,String userText,String reply,String language,String mode,String status,String reason,int attempts,int latencyMs,boolean usageComplete) {}
    public List<History> history(UserPrincipal user,Long chapterId){
        var context=contexts.load(user.id(),chapterId,"zh");var records=new ArrayList<>(logs.history(user.id(),context.progressId()));Collections.reverse(records);
        return records.stream().map(log->new History(log.id.toString(),log.nodeId.toString(),log.userTextMasked,log.replyMasked,log.language,log.mode,log.status,log.fallbackReason,log.attempts,log.latencyMs,Boolean.TRUE.equals(log.usageComplete))).toList();
    }
    public SseEmitter stream(UserPrincipal user,Long chapterId,Chat request){
        var context=contexts.load(user.id(),chapterId,request.language());
        if(!request.nodeId().equals(context.nodeId()))throw new BusinessException(HttpStatus.CONFLICT,"STORY_CONFLICT","剧情节点已变化，请刷新后再提问");
        String requestHash=AiContextService.digest(chapterId+"\n"+write(request));
        ChatLog previous=logs.request(user.id(),request.requestId());
        if(previous!=null){
            if(!previous.requestHash.equals(requestHash))throw conflict("请求编号已用于其他内容");
            if(!previous.status.equals("COMPLETED"))throw conflict("这条对话尚未完成，请稍后刷新记录或重新提问");
            return replay(previous,context);
        }
        Config config=configs.current();limits.limit("ai-chat",user.id().toString(),config.minuteLimit);
        if(!capacity.tryAcquire())throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"AI_BUSY","小满正在忙，请稍后重试");
        String lease=prefix+"ai-active:"+user.id();boolean acquired=false;
        try{
            acquired=Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lease,request.requestId(),Duration.ofSeconds(90)));
            if(!acquired)throw conflict("上一条对话仍在生成，请等待或停止后重试");
            // Recheck after the per-user lease to handle a concurrent identical POST.
            if(logs.request(user.id(),request.requestId())!=null)throw conflict("这条请求已被接受，请刷新对话记录");
            ChatLog log=new ChatLog();log.userId=user.id();log.progressId=context.progressId();log.nodeId=context.nodeId();log.requestId=request.requestId();log.requestHash=requestHash;log.contextHash=context.hash();log.language=request.language();log.userTextMasked=safety.mask(request.message().strip());log.replyMasked="";log.sourceIds=write(context.cards().stream().map(AiContextService.Knowledge::id).toList());log.mode="SCRIPT";log.status="RUNNING";log.attempts=0;log.latencyMs=0;log.usageComplete=false;logs.insert(log);
            SseEmitter emitter=new SseEmitter(40000L);AiProvider.Cancellation cancellation=new AiProvider.Cancellation();AtomicBoolean finished=new AtomicBoolean();
            emitter.onCompletion(()->{if(!finished.get())cancellation.cancel();});emitter.onTimeout(cancellation::cancel);emitter.onError(error->cancellation.cancel());
            try{executor.execute(()->run(user,context,request,config,log,emitter,cancellation,finished,lease));}
            catch(RejectedExecutionException e){log.status="CANCELLED";log.fallbackReason="BUSY";log.finishedAt=now();logs.updateById(log);throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"AI_BUSY","小满正在忙，请稍后重试");}
            return emitter;
        }catch(RuntimeException e){if(acquired)release(lease,request.requestId());capacity.release();throw e;}
    }
    private void run(UserPrincipal user,AiContextService.Context context,Chat request,Config config,ChatLog log,SseEmitter emitter,AiProvider.Cancellation cancellation,AtomicBoolean finished,String lease){
        long start=System.nanoTime();StringBuilder sent=new StringBuilder();
        try{
            String reason=reason(config,context,log.userTextMasked);
            send(emitter,"meta",Map.of("requestId",request.requestId(),"mode",reason==null?"MODEL":"SCRIPT","reason",reason==null?"":reason,"persona","Xiaoman / 小满","sources",sources(context)),cancellation);
            if(reason==null){
                for(int attempt=0;attempt<=config.retries;attempt++){
                    if(cancellation.cancelled())throw new AiFailure(AiFailure.Kind.CANCELLED,false);
                    if(!quotas.reserve(user.id(),context.progressId(),config,attempt==0)){reason="QUOTA_EXHAUSTED";break;}
                    log.attempts++;log.model=config.model;logs.updateById(log);
                    StringBuilder pending=new StringBuilder();
                    try{
                        var input=new AiProvider.Request(config.baseUrl,config.model,config.temperature.doubleValue(),config.maxTokens,config.timeoutMs,config.includeUsage,contexts.messages(user.id(),context,log.userTextMasked));
                        AiProvider.Reply reply=provider.stream(input,delta->{
                            pending.append(delta);if(sent.length()+pending.length()>600)throw new AiFailure(AiFailure.Kind.UNSAFE_OUTPUT,false);
                            int boundary=boundary(pending);
                            if(boundary>0){String sentence=pending.substring(0,boundary);check(user,context);safety.validate(sentence,context);pending.delete(0,boundary);send(emitter,"delta",Map.of("text",sentence),cancellation);sent.append(sentence);}
                        },cancellation);
                        check(user,context);safety.validate(pending.toString(),context);safety.validate(reply.text(),context);
                        if(!pending.isEmpty()){send(emitter,"delta",Map.of("text",pending.toString()),cancellation);sent.append(pending);}
                        log.mode="MODEL";log.replyMasked=safety.mask(sent.toString());
                        if(reply.usage()!=null){log.promptTokens=reply.usage().promptTokens();log.completionTokens=reply.usage().completionTokens();log.usageComplete=log.attempts==1;}
                        break;
                    }catch(AiFailure failure){
                        if(failure.kind()==AiFailure.Kind.CANCELLED)throw failure;
                        if(failure.retryable() && sent.isEmpty() && attempt<config.retries)continue;
                        reason=switch(failure.kind()){case TIMEOUT->"UPSTREAM_TIMEOUT";case UNSAFE_OUTPUT->"UNSAFE_OUTPUT";case STALE_CONTEXT->"STALE_CONTEXT";case DISABLED->"DISABLED";default->"UPSTREAM_ERROR";};break;
                    }
                }
            }
            if(reason!=null){
                if(cancellation.cancelled())throw new AiFailure(AiFailure.Kind.CANCELLED,false);
                var current=contexts.load(user.id(),context.chapterId(),context.language());
                String response=safety.mask(script.reply(current,log.userTextMasked));
                if(reason.equals("INPUT_BLOCKED"))response=context.language().equals("en")?"I’m Xiaoman, your scripted guide. I can restate permitted clues, but I cannot reveal answers or secrets, or change story progress and rewards.":"我是小满（脚本向导）。我可以转述获准线索，但不能提供标准答案、泄露密钥或修改剧情进度与奖励。";
                send(emitter,"reset",Map.of("mode","SCRIPT","reason",reason,"sources",sources(current)),cancellation);sent.setLength(0);
                for(int offset=0;offset<response.length();){int end=Math.min(offset+24,response.length());if(end<response.length() && Character.isHighSurrogate(response.charAt(end-1)))end--;String part=response.substring(offset,end);send(emitter,"delta",Map.of("text",part),cancellation);offset=end;}
                log.mode="SCRIPT";log.fallbackReason=reason;log.replyMasked=response;
            }
            log.status="COMPLETED";log.latencyMs=elapsed(start);log.finishedAt=now();logs.updateById(log);
            send(emitter,"done",Map.of("id",log.id.toString(),"mode",log.mode,"reason",Objects.requireNonNullElse(log.fallbackReason,""),"attempts",log.attempts,"latencyMs",log.latencyMs,"usageComplete",log.usageComplete),cancellation);
            finished.set(true);emitter.complete();
        }catch(Exception failure){
            log.status="CANCELLED";log.fallbackReason=cancellation.cancelled()?"CLIENT_DISCONNECTED":"SERVICE_ERROR";log.replyMasked=safety.mask(sent.toString());log.latencyMs=elapsed(start);log.finishedAt=now();
            try{logs.updateById(log);}catch(Exception ignored){}
            if(!cancellation.cancelled())try{send(emitter,"error",Map.of("code","AI_STREAM_FAILED","msg","对话未完成，请刷新后重试 / Chat interrupted. Please retry."),cancellation);}catch(Exception ignored){}
            finished.set(true);emitter.complete();
        }finally{cancellation.cancel();release(lease,request.requestId());capacity.release();}
    }
    private void check(UserPrincipal user,AiContextService.Context context){
        if(!contexts.allowed(user,context))throw new AiFailure(AiFailure.Kind.STALE_CONTEXT,false);
        if(!Boolean.TRUE.equals(configs.current().enabled))throw new AiFailure(AiFailure.Kind.DISABLED,false);
        if(!contexts.load(user.id(),context.chapterId(),context.language()).hash().equals(context.hash()))throw new AiFailure(AiFailure.Kind.STALE_CONTEXT,false);
    }
    private String reason(Config config,AiContextService.Context context,String input){
        if(safety.blockedInput(input))return "INPUT_BLOCKED";
        if(!Boolean.TRUE.equals(config.enabled))return "DISABLED";
        if(properties.getApiKey().isBlank())return "NO_KEY";
        if(config.baseUrl.isBlank() || config.model.isBlank())return "CONFIG_MISSING";
        if(context.cards().isEmpty())return "NO_REVIEWED_KNOWLEDGE";
        return null;
    }
    private SseEmitter replay(ChatLog log,AiContextService.Context context){
        SseEmitter emitter=new SseEmitter(10000L);AiProvider.Cancellation cancellation=new AiProvider.Cancellation();
        try{send(emitter,"meta",Map.of("mode",log.mode,"reason",Objects.requireNonNullElse(log.fallbackReason,""),"replayed",true,"sources",sources(context)),cancellation);send(emitter,"delta",Map.of("text",log.replyMasked),cancellation);send(emitter,"done",Map.of("id",log.id.toString(),"mode",log.mode,"reason",Objects.requireNonNullElse(log.fallbackReason,""),"attempts",log.attempts,"latencyMs",log.latencyMs,"usageComplete",log.usageComplete),cancellation);emitter.complete();return emitter;}catch(Exception e){emitter.complete();return emitter;}
    }
    private List<Map<String,String>> sources(AiContextService.Context context){return context.cards().stream().map(card->Map.of("id",card.id(),"title",card.title(),"url",card.sourceUrl())).toList();}
    private void release(String lease,String requestId){try{redis.execute(RELEASE,List.of(lease),requestId);}catch(Exception ignored){/* TTL bounds crash/disconnect recovery. */}}
    private static int boundary(StringBuilder text){for(int i=0;i<text.length();i++)if("。？！!?\n".indexOf(text.charAt(i))>=0 || text.charAt(i)=='.' && i+1<text.length() && Character.isWhitespace(text.charAt(i+1)))return i+1;return -1;}
    private static int elapsed(long started){return (int)Math.min(Integer.MAX_VALUE,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
    private static BusinessException conflict(String message){return new BusinessException(HttpStatus.CONFLICT,"AI_CONFLICT",message);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("AI data encoding failed");}}
    private static void send(SseEmitter emitter,String event,Object value,AiProvider.Cancellation cancellation){
        if(cancellation.cancelled())throw new AiFailure(AiFailure.Kind.CANCELLED,false);
        try{emitter.send(SseEmitter.event().name(event).data(value));}catch(IOException | IllegalStateException e){cancellation.cancel();throw new AiFailure(AiFailure.Kind.CANCELLED,false);}
    }
}
