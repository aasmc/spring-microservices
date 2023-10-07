package ru.aasmc.microservices.composite.product.tracing;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ObservationRegistryConfig implements ObservationRegistryCustomizer<ObservationRegistry> {

    private final BuildProperties buildProperties;

    @Override
    public void customize(ObservationRegistry registry) {
        registry.observationConfig().observationFilter(new BuildInfoObservationFilter(buildProperties));
    }
}
