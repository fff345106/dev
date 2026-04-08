package com.example.hello.config;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiBatchAsyncConfig {

    @Bean(name = "aiBatchExecutor")
    public Executor aiBatchExecutor(AiProperties aiProperties) {
        int concurrency = Math.max(1, aiProperties.getBatchConcurrency());
        int queueCapacity = Math.max(1, aiProperties.getBatchQueueCapacity());
        return new ThreadPoolExecutor(
                concurrency,
                concurrency,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("ai-batch-exec-" + thread.getId());
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
