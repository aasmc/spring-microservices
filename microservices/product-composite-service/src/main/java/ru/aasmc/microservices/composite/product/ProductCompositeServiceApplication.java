package ru.aasmc.microservices.composite.product;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.CompositeReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import ru.aasmc.microservices.composite.product.services.ProductCompositeIntegration;

import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
@ComponentScan("ru.aasmc")
@RequiredArgsConstructor
public class ProductCompositeServiceApplication {

	private final ProductCompositeIntegration integration;

	@Bean
	public ReactiveHealthContributor coreServices() {
		final Map<String, ReactiveHealthIndicator> registry = new LinkedHashMap<>();
		registry.put("product", integration::getProductHealth);
		registry.put("recommendation", integration::getRecommendationHealth);
		registry.put("review", integration::getReviewHealth);
		return CompositeReactiveHealthContributor.fromMap(registry);
	}

	public static void main(String[] args) {
		SpringApplication.run(ProductCompositeServiceApplication.class, args);
	}

}
