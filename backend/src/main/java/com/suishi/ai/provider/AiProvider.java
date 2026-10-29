package com.suishi.ai.provider;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public interface AiProvider {
    record Message(String role,String content) {}
    record Request(String baseUrl,String model,double temperature,int maxTokens,int timeoutMs,boolean includeUsage,List<Message> messages) {
        public Request { messages=List.copyOf(messages); }
        @Override public String toString(){return "AiRequest[content=REDACTED]";}
    }
    record Usage(Integer promptTokens,Integer completionTokens) {}
    record Reply(String text,Usage usage) {}
    Reply chat(Request request,Cancellation cancellation);
    Reply stream(Request request,Consumer<String> delta,Cancellation cancellation);

    final class Cancellation {
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private final AtomicReference<Closeable> resource=new AtomicReference<>();
        public boolean cancelled(){return cancelled.get();}
        public void attach(Closeable value){resource.set(value);if(cancelled())close(value);}
        public void detach(Closeable value){resource.compareAndSet(value,null);}
        public void cancel(){cancelled.set(true);close(resource.getAndSet(null));}
        private static void close(Closeable value){if(value!=null)try{value.close();}catch(IOException ignored){}}
    }
}
