package ru.aasmc.microservices.composite.product.tracing;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;

@RequiredArgsConstructor
public class BuildInfoObservationFilter implements ObservationFilter {

    private final BuildProperties buildProperties;

    // adds custom tag to span
    @Override
    public Observation.Context map(Observation.Context context) {
        KeyValue buildVersion = KeyValue.of("build.version", buildProperties.getVersion());
        return context.addLowCardinalityKeyValue(buildVersion);
    }
}
