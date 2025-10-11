// SchedulerConfig.java (placeholder)
package com.hamas.reviewtrust.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * スケジューラ有効化＋MDC引き継ぎ。
 * - RequestIdやユーザー名を含むMDCを @Scheduled タスクにも伝播
 * - daily再取得などの定時実行の土台
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(2);
        ts.setThreadNamePrefix("sched-");
        // (temporarily disabled) ts.setTaskDecorator(mdcTaskDecorator);
        ts.setRemoveOnCancelPolicy(true);
        ts.setWaitForTasksToCompleteOnShutdown(true);
        ts.initialize();
        return ts;
    }
}
