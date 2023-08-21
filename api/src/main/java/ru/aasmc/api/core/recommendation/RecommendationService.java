package ru.aasmc.api.core.recommendation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RecommendationService {


    Mono<Recommendation> createRecommendation(@RequestBody Recommendation body);

    /**
     * Sample usage: "curl $HOST:$PORT/recommendation?productId=1".
     *
     * @param productId Id of the product
     * @return the recommendations of the product
     */
    @GetMapping(
            value = "/recommendation",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    Flux<Recommendation> getRecommendations(
            @RequestParam(value = "productId", required = true) int productId
    );

    Mono<Void> deleteRecommendations(int productId);

}
