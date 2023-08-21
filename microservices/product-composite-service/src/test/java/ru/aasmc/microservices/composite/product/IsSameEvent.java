package ru.aasmc.microservices.composite.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aasmc.api.event.Event;

import java.io.IOException;
import java.util.Map;

public class IsSameEvent extends TypeSafeMatcher<String> {
    private static final Logger LOG = LoggerFactory.getLogger(IsSameEvent.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Event<?,?> expectedEvent;

    private IsSameEvent(Event<?,?> expectedEvent) {
        this.expectedEvent = expectedEvent;
    }

    @Override
    protected boolean matchesSafely(String eventAsJson) {
        if (expectedEvent == null) {
            return false;
        }
        LOG.trace("Convert the following json string to a map: {}", eventAsJson);
        Map<String, Object> mapEvent = convertJsonStringToMap(eventAsJson);
        mapEvent.remove("eventCreatedAt");

        Map<String, Object> mapExpectedEvent = getMapWithoutCreatedAt(expectedEvent);
        LOG.trace("Got the map: {}", mapEvent);
        LOG.trace("Compare to the expected map: {}", mapExpectedEvent);
        return mapEvent.equals(mapExpectedEvent);
    }

    @Override
    public void describeTo(Description description) {
        String expectedJson = convertObjectToJsonString(expectedEvent);
        description.appendText("expected to look like " + expectedJson);
    }

    public static Matcher<String> sameEventExceptCreatedAt(Event<?,?> expectedEvent) {
        return new IsSameEvent(expectedEvent);
    }

    private Map<String, Object> getMapWithoutCreatedAt(Event<?,?> event) {
        Map<String, Object> mapEvent = convertObjectToMap(event);
        mapEvent.remove("eventCreatedAt");
        return mapEvent;
    }

    private Map<String, Object> convertObjectToMap(Object o) {
        JsonNode node = mapper.convertValue(o, JsonNode.class);
        return mapper.convertValue(node, new TypeReference<>(){});
    }

    private String convertObjectToJsonString(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> convertJsonStringToMap(String eventAsJson) {
        try {
            return mapper.readValue(eventAsJson, new TypeReference<>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
