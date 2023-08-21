package ru.aasmc.microservices.composite.product.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import ru.aasmc.api.core.product.Product;
import ru.aasmc.api.core.product.ProductService;
import ru.aasmc.api.core.recommendation.Recommendation;
import ru.aasmc.api.core.recommendation.RecommendationService;
import ru.aasmc.api.core.review.Review;
import ru.aasmc.api.core.review.ReviewService;
import ru.aasmc.api.event.Event;
import ru.aasmc.api.exceptions.InvalidInputException;
import ru.aasmc.api.exceptions.NotFoundException;
import ru.aasmc.util.http.HttpErrorInfo;

import java.io.IOException;
import java.util.logging.Level;

@Slf4j
@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

    private final ObjectMapper mapper;
    private final String productServiceUrl;
    private final String recommendationServiceUrl;
    private final String reviewServiceUrl;
    private final WebClient webClient;
    private final StreamBridge streamBridge;
    private final Scheduler publishEventScheduler;

    @Autowired
    public ProductCompositeIntegration(
            ObjectMapper mapper,
            WebClient.Builder builder,
            StreamBridge streamBridge,
            @Qualifier("publishEventScheduler") Scheduler publishEventScheduler,
            @Value("${app.product-service.host}") String productServiceHost,
            @Value("${app.product-service.port}") int productServicePort,
            @Value("${app.recommendation-service.host}") String recommendationServiceHost,
            @Value("${app.recommendation-service.port}") int recommendationServicePort,
            @Value("${app.review-service.host}") String reviewServiceHost,
            @Value("${app.review-service.port}") int reviewServicePort) {

        this.mapper = mapper;
        this.webClient = builder.build();
        this.streamBridge = streamBridge;
        this.publishEventScheduler = publishEventScheduler;

        productServiceUrl = "http://" + productServiceHost + ":" + productServicePort;
        recommendationServiceUrl = "http://" + recommendationServiceHost + ":" + recommendationServicePort;
        reviewServiceUrl = "http://" + reviewServiceHost + ":" + reviewServicePort;
    }

    @Override
    public Mono<Product> createProduct(Product body) {
        return Mono.fromCallable(() -> {
            sendMessage("products-out-0",
                    new Event<>(Event.Type.CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Product> getProduct(int productId) {
        String url = productServiceUrl + "/product/" + productId;
        log.debug("Will call the getProduct API on URL: {}", url);

        return webClient.get().uri(url)
                .retrieve()
                .bodyToMono(Product.class)
                .log(log.getName(), Level.FINE)
                .onErrorMap(WebClientResponseException.class, this::handleException);
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        return Mono.fromRunnable(() -> {
            sendMessage("products-out-0",
                    new Event<>(Event.Type.DELETE, productId, null));
        }).subscribeOn(publishEventScheduler).then();
    }

    @Override
    public Mono<Recommendation> createRecommendation(Recommendation body) {
        return Mono.fromCallable(() -> {
            sendMessage("recommendations-out-0",
                    new Event<>(Event.Type.CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Flux<Recommendation> getRecommendations(int productId) {
        String url = recommendationServiceUrl + "/recommendation?productId=" + productId;

        log.debug("Will call the getRecommendations API on URL: {}", url);
        // Return an empty result if something goes wrong to make it possible
        // for the composite service to return partial responses
        return webClient.get().uri(url)
                .retrieve()
                .bodyToFlux(Recommendation.class)
                .log(log.getName(), Level.FINE)
                .onErrorResume(e -> Flux.empty());
    }

    @Override
    public Mono<Void> deleteRecommendations(int productId) {
        return Mono.fromRunnable(() -> {
            sendMessage("recommendations-out-0",
                    new Event<>(Event.Type.DELETE, productId, null));
        }).subscribeOn(publishEventScheduler).then();
    }

    @Override
    public Mono<Review> createReview(Review body) {
        return Mono.fromCallable(() -> {
            sendMessage("reviews-out-0", new Event<>(Event.Type.CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Flux<Review> getReviews(int productId) {
        String url = reviewServiceUrl + "/review?productId=" + productId;

        log.debug("Will call getReviews API on URL: {}", url);
        // Return an empty result if something goes wrong to make it possible
        // for the composite service to return partial responses
        return webClient.get().uri(url)
                .retrieve()
                .bodyToFlux(Review.class)
                .log(log.getName(), Level.FINE)
                .onErrorResume(e -> Flux.empty());
    }

    @Override
    public Mono<Void> deleteReviews(int productId) {
        return Mono.fromRunnable(() -> sendMessage(
                        "reviews-out-0",
                        new Event<>(Event.Type.DELETE, productId, null)
                ))
                .subscribeOn(publishEventScheduler).then();
    }

    public Mono<Health> getProductHealth() {
        return getHealth(productServiceUrl);
    }

    public Mono<Health> getRecommendationHealth() {
        return getHealth(recommendationServiceUrl);
    }

    public Mono<Health> getReviewHealth() {
        return getHealth(reviewServiceUrl);
    }

    private Mono<Health> getHealth(String url) {
        url += "/actuator/health";
        log.debug("Will call the Health API on URL: {}", url);
        return webClient.get().uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(s -> new Health.Builder().up().build())
                .onErrorResume(ex -> Mono.just(new Health.Builder().down(ex).build()))
                .log(log.getName(), Level.FINE);
    }

    private void sendMessage(String bindingName, Event<?, ?> event) {
        log.debug("Sending a {} message to {}", event.getEventType(), bindingName);
        Message<? extends Event<?, ?>> message = MessageBuilder.withPayload(event)
                .setHeader("partitionKey", event.getKey())
                .build();
        streamBridge.send(bindingName, message);
    }

    private Throwable handleException(Throwable ex) {
        if (!(ex instanceof WebClientResponseException)) {
            log.warn("Got an unexpected error: {}, will rethrow it", ex.toString());
            return ex;
        }
        WebClientResponseException wcre = (WebClientResponseException) ex;

        switch (wcre.getStatusCode()) {
            case NOT_FOUND -> {
                return new NotFoundException(getErrorMessage(wcre));
            }
            case UNPROCESSABLE_ENTITY -> {
                return new InvalidInputException(getErrorMessage(wcre));
            }
            default -> {
                log.warn("Got an unexpected HTTP error: {}, will rethrow it", wcre.getStatusCode());
                log.warn("Error body: {}", wcre.getResponseBodyAsString());
                return ex;
            }
        }
    }

    private String getErrorMessage(WebClientResponseException ex) {
        try {
            return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
        } catch (IOException ioex) {
            return ex.getMessage();
        }
    }

}
