package ru.aasmc.api.core.product;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

public interface ProductService {

    Mono<Product> createProduct(Product body);


    /**
     * Sample usage: "curl $HOST:$PORT/product/1".
     *
     * @param productId Id of the product
     * @return the product, if found, else null
     */
    @GetMapping(
            value = "/product/{productId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    Mono<Product> getProduct(
            @PathVariable("productId") int productId,
            @RequestParam(value = "delay", required = false, defaultValue = "0") int delay,
            @RequestParam(value = "faultPercent", required = false, defaultValue = "0") int faultPercent
    );

    Mono<Void> deleteProduct(int productId);

}
