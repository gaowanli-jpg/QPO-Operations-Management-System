package com.machining.quote.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SystemTaskExecutorConfig {
    @Bean(name = "systemTaskExecutor")
    public ThreadPoolTaskExecutor systemTaskExecutor(
            @Value("${erp.task.core-pool-size:2}") int corePoolSize,
            @Value("${erp.task.max-pool-size:4}") int maxPoolSize,
            @Value("${erp.task.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("system-task-");
        executor.initialize();
        return executor;
    }
}
