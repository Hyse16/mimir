package com.mimir.blog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;

@Configuration
class ImageAnalysisTaskConfiguration {

    @Bean("imageAnalysisTaskExecutor")
    TaskExecutor imageAnalysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("image-analysis-");
        executor.initialize();
        return executor;
    }

    @Bean("imageAnalysisEventScheduler")
    TaskScheduler imageAnalysisEventScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("image-analysis-events-");
        scheduler.initialize();
        return scheduler;
    }
}
