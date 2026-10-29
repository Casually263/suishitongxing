package com.suishi.ai.provider;

import com.fasterxml.jackson.databind.*;
import com.suishi.ai.config.AiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static com.suishi.ai.provider.AiFailure.Kind.*;

@Component
public class OpenAiCompatibleProvider implements AiProvider {
    private final ObjectMapper json;
    private final AiProperties properties;
    private final AiEndpointPolicy endpoints;
    private final ScheduledExecutorService scheduler;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    public OpenAiCompatibleProvider(ObjectMapper json,AiProperties properties,AiEndpointPolicy endpoints,@Qualifier("aiTimeoutScheduler") ScheduledExecutorService scheduler){this.json=json;this.properties=properties;this.endpoints=endpoints;this.scheduler=scheduler;}
    @Override public Reply chat(Request request,Cancellation cancellation){return execute(request,false,part->{},cancellation);}
    @Override public Reply stream(Request request,Consumer<String> delta,Cancellation cancellation){return execute(request,true,delta,cancellation);}
    private Reply execute(Request request,boolean stream,Consumer<String> delta,Cancellation cancellation){
        if(cancellation.cancelled())throw new AiFailure(CANCELLED,false);
        if(properties.getApiKey().isBlank())throw new AiFailure(AUTHENTICATION,false);
        AtomicBoolean timedOut=new AtomicBoolean();AtomicReference<InputStream> current=new AtomicReference<>();
        CompletableFuture<HttpResponse<InputStream>> pending=null;ScheduledFuture<?> timeout=null;
        try {
            Map<String,Object> payload=new LinkedHashMap<>();payload.put("model",request.model());payload.put("messages",request.messages());payload.put("temperature",request.temperature());payload.put("max_tokens",request.maxTokens());payload.put("stream",stream);
            if(stream && request.includeUsage())payload.put("stream_options",Map.of("include_usage",true));
            HttpRequest upstream=HttpRequest.newBuilder(endpoints.endpoint(request.baseUrl())).timeout(Duration.ofMillis(request.timeoutMs()))
                    .header("Authorization","Bearer "+properties.getApiKey()).header("Content-Type","application/json").header("Accept",stream?"text/event-stream":"application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload),StandardCharsets.UTF_8)).build();
            pending=http.sendAsync(upstream,HttpResponse.BodyHandlers.ofInputStream());
            var headers=pending;
            timeout=scheduler.schedule(()->{timedOut.set(true);headers.cancel(true);InputStream body=current.get();if(body!=null)try{body.close();}catch(IOException ignored){}},request.timeoutMs(),TimeUnit.MILLISECONDS);
            HttpResponse<InputStream> response=pending.get(request.timeoutMs(),TimeUnit.MILLISECONDS);
            current.set(response.body());cancellation.attach(response.body());
            try(InputStream body=response.body()){
                if(timedOut.get())throw new AiFailure(TIMEOUT,true);
                int status=response.statusCode();
                if(status!=200){if(status==401 || status==403)throw new AiFailure(AUTHENTICATION,false);throw new AiFailure(UNAVAILABLE,status==408 || status==429 || status>=500);}
                if(cancellation.cancelled())throw new AiFailure(CANCELLED,false);
                if(stream){
                    if(!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).contains("text/event-stream"))throw new AiFailure(PROTOCOL,false);
                    return parseStream(body,delta,cancellation,timedOut);
                }
                String raw=readBody(body,65536,cancellation);JsonNode root=json.readTree(raw);
                if(root.has("error") || !root.path("choices").path(0).path("message").path("content").isTextual())throw new AiFailure(PROTOCOL,false);
                String text=root.path("choices").path(0).path("message").path("content").asText();if(text.length()>4096 || text.isBlank())throw new AiFailure(PROTOCOL,false);
                return new Reply(text,usage(root.path("usage")));
            }
        } catch(AiFailure failure){throw failure;}
        catch(TimeoutException | CancellationException e){throw new AiFailure(cancellation.cancelled()?CANCELLED:TIMEOUT,!cancellation.cancelled());}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiFailure(CANCELLED,false);}
        catch(Exception e){if(cancellation.cancelled())throw new AiFailure(CANCELLED,false);if(timedOut.get())throw new AiFailure(TIMEOUT,true);throw new AiFailure(UNAVAILABLE,e instanceof IOException || e instanceof ExecutionException);}
        finally {if(timeout!=null)timeout.cancel(false);if(pending!=null && !pending.isDone())pending.cancel(true);InputStream body=current.get();if(body!=null){cancellation.detach(body);try{body.close();}catch(IOException ignored){}}}
    }
    private Reply parseStream(InputStream body,Consumer<String> consumer,Cancellation cancellation,AtomicBoolean timedOut)throws IOException{
        Reader reader=new InputStreamReader(body,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT));
        StringBuilder data=new StringBuilder(),text=new StringBuilder();Usage usage=null;boolean done=false;
        for(String line;(line=limitedLine(reader))!=null;){
            if(cancellation.cancelled())throw new AiFailure(CANCELLED,false);if(timedOut.get())throw new AiFailure(TIMEOUT,true);
            if(line.isEmpty()){
                if(data.isEmpty())continue;String event=data.toString().stripTrailing();data.setLength(0);
                if(event.equals("[DONE]")){done=true;break;}
                JsonNode chunk;
                try{chunk=json.readTree(event);}catch(Exception e){throw new AiFailure(PROTOCOL,false);}
                if(chunk==null || chunk.has("error"))throw new AiFailure(PROTOCOL,false);
                Usage supplied=usage(chunk.path("usage"));if(supplied!=null)usage=supplied;
                JsonNode choice=chunk.path("choices").path(0),value=choice.path("delta").path("content");
                if(choice.path("finish_reason").asText().equals("length") || choice.path("finish_reason").asText().equals("content_filter") || choice.path("delta").has("tool_calls"))throw new AiFailure(PROTOCOL,false);
                if(!value.isMissingNode() && !value.isNull() && !value.isTextual())throw new AiFailure(PROTOCOL,false);
                if(value.isTextual() && !value.asText().isEmpty()){String part=value.asText();text.append(part);if(text.length()>4096)throw new AiFailure(PROTOCOL,false);consumer.accept(part);}
            }else if(line.startsWith("data:")){data.append(line.substring(5).stripLeading()).append('\n');if(data.length()>65536)throw new AiFailure(PROTOCOL,false);}
        }
        if(!done || text.isEmpty())throw new AiFailure(PROTOCOL,false);
        return new Reply(text.toString(),usage);
    }
    private static String limitedLine(Reader reader)throws IOException{
        StringBuilder result=new StringBuilder();int ch;
        while((ch=reader.read())!=-1){if(ch=='\n')break;if(result.length()>=65536)throw new AiFailure(PROTOCOL,false);result.append((char)ch);}
        if(ch==-1 && result.isEmpty())return null;
        if(!result.isEmpty() && result.charAt(result.length()-1)=='\r')result.setLength(result.length()-1);
        return result.toString();
    }
    private static String readBody(InputStream body,int limit,Cancellation cancellation)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];for(int n;(n=body.read(buffer))!=-1;){if(cancellation.cancelled())throw new AiFailure(CANCELLED,false);if(bytes.size()+n>limit)throw new AiFailure(PROTOCOL,false);bytes.write(buffer,0,n);}return bytes.toString(StandardCharsets.UTF_8);
    }
    private static Usage usage(JsonNode node){
        if(!node.path("prompt_tokens").canConvertToInt() || !node.path("completion_tokens").canConvertToInt())return null;
        int in=node.path("prompt_tokens").asInt(),out=node.path("completion_tokens").asInt();return in<0 || out<0?null:new Usage(in,out);
    }
}
