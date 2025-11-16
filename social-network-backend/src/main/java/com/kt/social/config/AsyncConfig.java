package com.kt.social.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Cấu hình một "luồng" (thread pool) riêng để chạy các tác vụ @Async
     */
    @Bean("asyncTaskExecutor")
    public TaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);   // Số luồng chạy thường trực
        executor.setMaxPoolSize(10);  // Số luồng tối đa
        executor.setQueueCapacity(25); // Hàng đợi
        executor.setThreadNamePrefix("AsyncLog-");
        executor.initialize();
        return executor;
    }

}
