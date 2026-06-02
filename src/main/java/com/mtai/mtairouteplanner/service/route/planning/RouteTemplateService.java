package com.mtai.mtairouteplanner.service.route.planning;

import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import com.mtai.mtairouteplanner.data.index.MockDataIndexes;
import com.mtai.mtairouteplanner.data.loader.MockDataLoader;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.RouteTemplate;
import com.mtai.mtairouteplanner.model.route.RouteTemplateCandidate;
import com.mtai.mtairouteplanner.model.route.RouteTemplateMatchRequest;

import java.util.Comparator;
import java.util.List;

public class RouteTemplateService {

    private final MockDataBundle mockDataBundle;
    private final MockDataIndexes indexes;

    public RouteTemplateService() {
        this(new MockDataLoader());
    }

    public RouteTemplateService(MockDataLoader mockDataLoader) {
        this.mockDataBundle = mockDataLoader.load();
        this.indexes = MockDataIndexes.from(mockDataBundle, mockDataLoader.assembleLoadedPois(mockDataBundle));
    }

    public List<RouteTemplate> findByScene(String scene) {
        return indexes.routeTemplateIndex().findByScene(scene);
    }

    public List<RouteTemplate> findByTimePeriod(String timePeriod) {
        return indexes.routeTemplateIndex().findByTimePeriod(timePeriod);
    }

    public List<RouteTemplate> findByBudgetLevel(String budgetLevel) {
        return indexes.routeTemplateIndex().findByBudgetLevel(budgetLevel);
    }

    public List<RouteTemplate> findByPaceLevel(String paceLevel) {
        return indexes.routeTemplateIndex().findByPaceLevel(paceLevel);
    }

    public List<RouteTemplateCandidate> findCandidateTemplates(RouteTemplateMatchRequest request) {
        if (request == null || !request.hasAnyFilter()) {
            return List.of();
        }

        List<RouteTemplate> matches = indexes.routeTemplateIndex().findCandidateTemplates(
                request.scene(),
                null,
                null,
                null,
                null
        );

        return matches.stream()
                .map(template -> scoreTemplate(template, request))
                .filter(candidate -> candidate.matchScore() > 0.0)
                .sorted(Comparator
                        .comparingDouble(RouteTemplateCandidate::matchScore).reversed()
                        .thenComparing(Comparator.comparingDouble(RouteTemplateCandidate::durationFitScore).reversed())
                        .thenComparing(RouteTemplateCandidate::templateId))
                .limit(request.topN())
                .toList();
    }

    private RouteTemplateCandidate scoreTemplate(RouteTemplate template, RouteTemplateMatchRequest request) {
        double sceneScore = hasText(request.scene()) && request.scene().equals(template.scene()) ? 60.0 : (!hasText(request.scene()) ? 10.0 : 0.0);
        double timeMatchScore = hasText(request.timePeriod()) && request.timePeriod().equals(template.timePeriod()) ? 18.0 : 0.0;
        double budgetMatchScore = hasText(request.budgetLevel()) && request.budgetLevel().equals(template.budgetLevel()) ? 12.0 : 0.0;
        double paceMatchScore = hasText(request.paceLevel()) && request.paceLevel().equals(template.paceLevel()) ? 8.0 : 0.0;
        double districtMatchScore = hasText(request.district()) && template.suitableDistricts().contains(request.district()) ? 10.0 : 0.0;
        double durationFitScore = calculateDurationFitScore(template, request.durationMinutes());

        double totalScore = sceneScore + timeMatchScore + budgetMatchScore + paceMatchScore + districtMatchScore + durationFitScore;

        return new RouteTemplateCandidate(
                template.templateId(),
                template.scene(),
                template.timePeriod(),
                template.minDurationMinutes(),
                template.maxDurationMinutes(),
                template.budgetLevel(),
                template.paceLevel(),
                template.slotSequence(),
                template.suitableDistricts(),
                totalScore,
                timeMatchScore,
                budgetMatchScore,
                paceMatchScore,
                districtMatchScore,
                durationFitScore
        );
    }

    private double calculateDurationFitScore(RouteTemplate template, Integer durationMinutes) {
        if (durationMinutes == null) {
            return 0.0;
        }
        if (durationMinutes >= template.minDurationMinutes() && durationMinutes <= template.maxDurationMinutes()) {
            return 14.0;
        }

        int distanceToRange = durationMinutes < template.minDurationMinutes()
                ? template.minDurationMinutes() - durationMinutes
                : durationMinutes - template.maxDurationMinutes();
        return Math.max(0.0, 14.0 - (distanceToRange / 15.0));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}


