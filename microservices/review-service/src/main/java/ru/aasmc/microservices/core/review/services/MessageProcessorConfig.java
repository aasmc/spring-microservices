package ru.aasmc.microservices.core.review.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.aasmc.api.core.review.Review;
import ru.aasmc.api.core.review.ReviewService;
import ru.aasmc.api.event.Event;
import ru.aasmc.api.exceptions.EventProcessingException;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MessageProcessorConfig {

    private final ReviewService reviewService;

    @Bean
    public Consumer<Event<Integer, Review>> messageProcessor() {
        return event -> {
            log.info("Process message created at {}...", event.getEventCreatedAt());
            switch (event.getEventType()) {

                case CREATE -> {
                    Review review = event.getData();
                    log.info("Create review with ID: {}/{}", review.getProductId(), review.getReviewId());
                    reviewService.createReview(review).block();
                }
                case DELETE -> {
                    int productId = event.getKey();
                    log.info("Delete reviews with ProductID: {}", productId);
                    reviewService.deleteReviews(productId).block();
                }
                default -> {
                    String errorMessage = "Incorrect event type: " + event.getEventType() + ", expected a CREATE or DELETE event";
                    log.warn(errorMessage);
                    throw new EventProcessingException(errorMessage);
                }
            }
            log.info("Message processing done!");
        };
    }

}
