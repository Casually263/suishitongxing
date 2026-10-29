package com.suishi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.ai.config.AiProperties;
import com.suishi.ai.provider.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class OpenAiCompatibleProviderTest {
    HttpServer server;ScheduledExecutorService scheduler;AiEndpointPolicy policy;OpenAiCompatibleProvider provider;AiProperties properties;
    volatile HttpHandler handler;String base;final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));server.createContext("/v1/chat/completions",exchange->handler.handle(exchange));server.start();
        base="http://127.0.0.1:"+server.getAddress().getPort();properties=new AiProperties();properties.setApiKey("unit-only-api-key");properties.setAllowedOrigins(List.of(base));properties.setAllowLocalEndpoints(true);
        MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles("test");policy=new AiEndpointPolicy(properties,environment);scheduler=Executors.newScheduledThreadPool(2);
        provider=new OpenAiCompatibleProvider(json,properties,policy,scheduler);
    }
    @AfterEach void stop(){server.stop(0);scheduler.shutdownNow();policy.close();}
    AiProvider.Request request(int timeout){return new AiProvider.Request(base+"/v1","fixture-model",0.4,128,timeout,true,List.of(new AiProvider.Message("system","Safe fixture context"),new AiProvider.Message("user","Hello")));}
    static void write(OutputStream out,String text)throws IOException{out.write(text.getBytes(StandardCharsets.UTF_8));out.flush();}
    @Test void streamsUtf8DeltasBeforeTheFinalChunkAndReadsUsage()throws Exception{
        CountDownLatch first=new CountDownLatch(1),finish=new CountDownLatch(1);AtomicReference<String> payload=new AtomicReference<>();AtomicReference<String> auth=new AtomicReference<>();
        handler=exchange->{payload.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));auth.set(exchange.getRequestHeaders().getFirst("Authorization"));exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try(var out=exchange.getResponseBody()){
            byte[] event="data: {\"choices\":[{\"delta\":{\"content\":\"你好，\"}}]}\r\n\r\n".getBytes(StandardCharsets.UTF_8);for(int i=0;i<event.length;i+=3){out.write(event,i,Math.min(3,event.length-i));out.flush();}
            try{finish.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            write(out,"data: {\"choices\":[{\"delta\":{\"content\":\"春天。\"},\"finish_reason\":\"stop\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}\n\ndata: [DONE]\n\n");
        }};
        var result=CompletableFuture.supplyAsync(()->provider.stream(request(5000),delta->first.countDown(),new AiProvider.Cancellation()));
        assertThat(first.await(2,TimeUnit.SECONDS)).isTrue();assertThat(result.isDone()).isFalse();finish.countDown();var reply=result.get(3,TimeUnit.SECONDS);
        assertThat(reply.text()).isEqualTo("你好，春天。");assertThat(reply.usage().promptTokens()).isEqualTo(12);assertThat(auth.get()).isEqualTo("Bearer unit-only-api-key");assertThat(payload.get()).doesNotContain("unit-only-api-key");assertThat(json.readTree(payload.get()).path("stream_options").path("include_usage").asBoolean()).isTrue();
    }
    @Test void nonStreamingChatUsesTheSameProtocolAndUnknownUsageStaysNull(){
        handler=exchange->{byte[] data="{\"choices\":[{\"message\":{\"content\":\"Hello there.\"}}]}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);try(var out=exchange.getResponseBody()){out.write(data);}};
        var reply=provider.chat(request(2000),new AiProvider.Cancellation());assertThat(reply.text()).isEqualTo("Hello there.");assertThat(reply.usage()).isNull();
    }
    @Test void stalledBodyHasABoundedDeadlineEvenAfterHeadersAndADelta(){
        handler=exchange->{exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try(var out=exchange.getResponseBody()){write(out,"data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n");try{Thread.sleep(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}}};
        long start=System.nanoTime();assertThatThrownBy(()->provider.stream(request(180),part->{},new AiProvider.Cancellation())).isInstanceOfSatisfying(AiFailure.class,e->assertThat(e.kind()).isEqualTo(AiFailure.Kind.TIMEOUT));assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(1200);
    }
    @Test void missingDoneAndMalformedEventsAreProtocolFailures(){
        for(String body:List.of("data: {bad}\n\n","data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n")){
            handler=exchange->{exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try(var out=exchange.getResponseBody()){write(out,body);}};
            assertThatThrownBy(()->provider.stream(request(2000),part->{},new AiProvider.Cancellation())).isInstanceOfSatisfying(AiFailure.class,e->assertThat(e.kind()).isEqualTo(AiFailure.Kind.PROTOCOL));
        }
    }
    @Test void authenticationErrorsAreNotRetriedOrExposed(){
        handler=exchange->{exchange.sendResponseHeaders(401,0);try(var out=exchange.getResponseBody()){write(out,"upstream-secret-body unit-only-api-key");}};
        assertThatThrownBy(()->provider.stream(request(2000),part->{},new AiProvider.Cancellation())).isInstanceOfSatisfying(AiFailure.class,e->{assertThat(e.retryable()).isFalse();assertThat(e.toString()).doesNotContain("upstream-secret-body","unit-only-api-key");});
    }
    @Test void redirectIsNeverFollowedWithTheAuthorizationHeader(){
        AtomicInteger followed=new AtomicInteger();server.createContext("/credential-trap",exchange->{followed.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});
        handler=exchange->{exchange.getResponseHeaders().set("Location",base+"/credential-trap");exchange.sendResponseHeaders(302,-1);exchange.close();};
        assertThatThrownBy(()->provider.stream(request(2000),part->{},new AiProvider.Cancellation())).isInstanceOf(AiFailure.class);assertThat(followed.get()).isZero();
    }
    @Test void clientCancellationClosesTheCurrentUpstreamBody(){
        handler=exchange->{exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try(var out=exchange.getResponseBody()){write(out,"data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n");try{Thread.sleep(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}};
        var cancellation=new AiProvider.Cancellation();assertThatThrownBy(()->provider.stream(request(3000),part->cancellation.cancel(),cancellation)).isInstanceOfSatisfying(AiFailure.class,e->assertThat(e.kind()).isEqualTo(AiFailure.Kind.CANCELLED));
    }
}
