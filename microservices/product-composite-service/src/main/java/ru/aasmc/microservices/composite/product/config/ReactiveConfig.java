package ru.aasmc.microservices.composite.product.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class ReactiveConfig {
    /**
     * Number of threads in the thread pool used for handling blocking calls.
     */
    @Value("${app.threadPoolSize:10}")
    private Integer threadPoolSize;
    /**
     * Number of tasks to be queued if all treads in the thread pool used
     * for blocking calls are busy.
     */
    @Value("${app.taskQueueSize:100}")
    private Integer taskQueueSize;

    @Bean
    public Scheduler publishEventScheduler() {
        log.info("Creates a messagingScheduler with connectionPoolSize = {}", threadPoolSize);
        return Schedulers.newBoundedElastic(threadPoolSize, taskQueueSize, "publish-pool");
    }
}
