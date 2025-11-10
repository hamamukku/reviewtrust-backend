package com.hamas.reviewtrust.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 最小限のスケジューラ設定。setTaskDecorator(...) は使用しない。
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(2);
        ts.setThreadNamePrefix("sched-");
        ts.setRemoveOnCancelPolicy(true);
        ts.setWaitForTasksToCompleteOnShutdown(true);
        ts.initialize();
        return ts;
    }
}
