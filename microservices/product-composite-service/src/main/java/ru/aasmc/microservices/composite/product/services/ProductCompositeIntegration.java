package ru.aasmc.microservices.composite.product.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
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
import ru.aasmc.util.http.ServiceUtil;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;


@Slf4j
@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

    private static final String PRODUCT_SERVICE_URL = "http://product";
    private static final String RECOMMENDATION_SERVICE_URL = "http://recommendation";
    private static final String REVIEW_SERVICE_URL = "http://review";
    private final ObjectMapper mapper;
    private final WebClient webClient;
    private final StreamBridge streamBridge;
    private final Scheduler publishEventScheduler;
    private final ServiceUtil serviceUtil;

    @Autowired
    public ProductCompositeIntegration(
            ObjectMapper mapper,
            @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder builder,
            StreamBridge streamBridge,
            @Qualifier("publishEventScheduler") Scheduler publishEventScheduler,
            ServiceUtil serviceUtil) {

        this.mapper = mapper;
        this.webClient = builder.build();
        this.streamBridge = streamBridge;
        this.publishEventScheduler = publishEventScheduler;
        this.serviceUtil = serviceUtil;
    }

    @Override
    public Mono<Product> createProduct(Product body) {
        return Mono.fromCallable(() -> {
            sendMessage("products-out-0",
                    new Event<>(Event.Type.CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Retry(name = "product")
    @TimeLimiter(name = "product")
    @CircuitBreaker(name = "product", fallbackMethod = "getProductFallbackValue")
    @Override
    public Mono<Product> getProduct(int productId, int delay, int faultPercent) {
        URI url = UriComponentsBuilder.fromUriString(PRODUCT_SERVICE_URL +
                        "/product/{productId}?delay={delay}&faultPercent={faultPercent}")
                .build(productId, delay, faultPercent);
        log.debug("Will call the getProduct API on URL: {}", url);
        return webClient.get().uri(url)
                .retrieve()
                .bodyToMono(Product.class)
                .log(log.getName(), Level.FINE)
                .onErrorMap(WebClientResponseException.class, this::handleException);
    }

    private Mono<Product> getProductFallbackValue(int productId,
                                                  int delay,
                                                  int faultPercent,
                                                  CallNotPermittedException ex) {
        log.warn("Creating a fail-fast fallback product for productId = {}, delay = {}, faultPercent = {}, exception = {}",
                productId, delay, faultPercent, ex.toString());
        if (productId == 13) {
            String errMsg = "Product Id: " + productId + " not found in fallback cache!";
            log.warn(errMsg);
            throw new NotFoundException(errMsg);
        }

        return Mono.just(new Product(productId, "Fallback product" + productId, productId, serviceUtil.getServiceAddress()));
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
        String url = RECOMMENDATION_SERVICE_URL + "/recommendation?productId=" + productId;

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
        String url = REVIEW_SERVICE_URL + "/review?productId=" + productId;

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

        switch (HttpStatus.resolve(wcre.getStatusCode().value())) {
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
