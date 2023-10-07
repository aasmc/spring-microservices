package ru.aasmc.microservices.composite.product.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class ObservationUtil {

    private final ObservationRegistry registry;

    // creates custom span
    public <T> T observe(String observationName,
                         String contextualName,
                         String highCardinalityKey,
                         String highCardinalityValue,
                         Supplier<T> supplier) {
        return Observation.createNotStarted(observationName, registry)
                .contextualName(contextualName)
                .highCardinalityKeyValue(highCardinalityKey, highCardinalityValue)
                .observe(supplier);
    }

}
