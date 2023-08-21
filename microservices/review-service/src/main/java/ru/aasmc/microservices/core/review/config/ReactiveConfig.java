package ru.aasmc.microservices.core.review.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Configuration
public class ReactiveConfig {

    @Value("${app.threadPoolSize:10}")
    private Integer threadPoolSize;
    @Value("${app.taskQueueSize:100}")
    private Integer taskQueueSize;

    @Bean
    public Scheduler jdbcScheduler() {
        log.info("Creates a JDBC Scheduler with thread pool size = {}", threadPoolSize);
        return Schedulers.newBoundedElastic(threadPoolSize, taskQueueSize, "jdbc-pool");
    }

}
