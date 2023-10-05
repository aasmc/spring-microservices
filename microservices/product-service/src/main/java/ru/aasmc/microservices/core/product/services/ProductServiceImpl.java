package ru.aasmc.microservices.core.product.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.aasmc.api.core.product.Product;
import ru.aasmc.api.core.product.ProductService;
import ru.aasmc.api.exceptions.InvalidInputException;
import ru.aasmc.api.exceptions.NotFoundException;
import ru.aasmc.microservices.core.product.persistence.ProductEntity;
import ru.aasmc.microservices.core.product.persistence.ProductRepository;
import ru.aasmc.util.http.ServiceUtil;

import java.time.Duration;
import java.util.Random;
import java.util.logging.Level;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ServiceUtil serviceUtil;
    private final ProductRepository repository;
    private final ProductMapper mapper;

    @Override
    public Mono<Product> createProduct(Product body) {
        if (body.getProductId() < 1) {
            throw new InvalidInputException("Invalid productId: " + body.getProductId());
        }
        ProductEntity entity = mapper.apiToEntity(body);
        return repository.save(entity)
                .log(log.getName(), Level.FINE)
                .onErrorMap(
                        DuplicateKeyException.class,
                        ex -> new InvalidInputException("Duplicate key, Product Id: " + body.getProductId())
                ).map(mapper::entityToApi);
    }

    @Override
    public Mono<Product> getProduct(int productId, int delay, int faultPercent) {
        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }
        log.info("Will get product info for id={}", productId);
        return repository.findByProductId(productId)
                .map(e -> throwErrorIfBadLuck(e, faultPercent))
                .delayElement(Duration.ofSeconds(delay))
                .switchIfEmpty(Mono.error(new NotFoundException("No product found for productId: " + productId)))
                .log(log.getName(), Level.FINE)
                .map(mapper::entityToApi)
                .map(this::setServiceAddress);
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }
        log.debug("deleteProduct: tries to delete an entity with productId: {}", productId);
        return repository.findByProductId(productId)
                .log(log.getName(), Level.FINE)
                .map(repository::delete)
                .flatMap(e -> e);
    }

    private Product setServiceAddress(Product e) {
        e.setServiceAddress(serviceUtil.getServiceAddress());
        return e;
    }

    private ProductEntity throwErrorIfBadLuck(ProductEntity entity, int faultPercent) {

        if (faultPercent == 0) {
            return entity;
        }

        int randomThreshold = getRandomNumber(1, 100);

        if (faultPercent < randomThreshold) {
            log.debug("We got lucky, no error occurred, {} < {}", faultPercent, randomThreshold);
        } else {
            log.info("Bad luck, an error occurred, {} >= {}", faultPercent, randomThreshold);
            throw new RuntimeException("Something went wrong...");
        }

        return entity;
    }

    private final Random randomNumberGenerator = new Random();

    private int getRandomNumber(int min, int max) {

        if (max < min) {
            throw new IllegalArgumentException("Max must be greater than min");
        }

        return randomNumberGenerator.nextInt((max - min) + 1) + min;
    }
}
