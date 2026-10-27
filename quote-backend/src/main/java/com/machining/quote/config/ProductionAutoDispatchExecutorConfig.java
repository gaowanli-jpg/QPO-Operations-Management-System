package com.machining.quote.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 待产零件定时自动下发专用线程池。
 * 单线程：保证同一时刻只有一个批次在跑，不并发压 MES，也不挤占 systemTaskExecutor。
 */
@Configuration
public class ProductionAutoDispatchExecutorConfig {
    @Bean(name = "productionAutoDispatchExecutor")
    public ThreadPoolTaskExecutor productionAutoDispatchExecutor(
            @Value("${erp.production-auto-dispatch.executor.queue-capacity:4}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("production-auto-dispatch-");
        executor.initialize();
        return executor;
    }
}
