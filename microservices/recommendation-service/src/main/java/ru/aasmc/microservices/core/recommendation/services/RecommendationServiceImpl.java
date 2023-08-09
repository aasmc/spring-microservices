package ru.aasmc.microservices.core.recommendation.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import ru.aasmc.api.core.recommendation.Recommendation;
import ru.aasmc.api.core.recommendation.RecommendationService;
import ru.aasmc.api.exceptions.InvalidInputException;
import ru.aasmc.util.http.ServiceUtil;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final ServiceUtil serviceUtil;

    @Override
    public List<Recommendation> getRecommendations(int productId) {
        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }

        if (productId == 113) {
            log.debug("No recommendations found for productId: {}", productId);
            return new ArrayList<>();
        }

        List<Recommendation> list = new ArrayList<>();
        list.add(new Recommendation(productId, 1, "Author 1", 1, "Content 1", serviceUtil.getServiceAddress()));
        list.add(new Recommendation(productId, 2, "Author 2", 2, "Content 2", serviceUtil.getServiceAddress()));
        list.add(new Recommendation(productId, 3, "Author 3", 3, "Content 3", serviceUtil.getServiceAddress()));

        log.debug("/recommendation response size: {}", list.size());

        return list;
    }
}
