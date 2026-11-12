package com.animallocket.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {
    /** Bulkhead riêng cho push: hàng đợi đầy thì bỏ bớt thay vì làm chậm cả hệ thống. */
    @Bean(name = "pushExecutor")
    Executor pushExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(5000);
        ex.setThreadNamePrefix("push-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ex.initialize();
        return ex;
    }
}
