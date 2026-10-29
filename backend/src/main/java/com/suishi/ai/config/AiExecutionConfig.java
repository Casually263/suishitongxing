package com.suishi.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;

@Configuration
public class AiExecutionConfig {
    @Bean(name="aiChatExecutor",destroyMethod="shutdownNow")
    public ExecutorService aiChatExecutor(){
        return new ThreadPoolExecutor(4,4,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"ai-chat");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    @Bean(name="aiTimeoutScheduler",destroyMethod="shutdownNow")
    public ScheduledExecutorService aiTimeoutScheduler(){
        ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(2,r->{Thread t=new Thread(r,"ai-timeout");t.setDaemon(true);return t;});
        executor.setRemoveOnCancelPolicy(true);return executor;
    }
}
