package com.mtai.mtairouteplanner.service.poi;

import com.mtai.mtairouteplanner.data.model.LoadedPoi;
import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import com.mtai.mtairouteplanner.data.index.MockDataIndexes;
import com.mtai.mtairouteplanner.data.loader.MockDataLoader;
import com.mtai.mtairouteplanner.data.index.PoiIndex;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.UserPreferenceTag;
import com.mtai.mtairouteplanner.model.poi.PoiCandidate;
import com.mtai.mtairouteplanner.model.poi.PoiRetrievalResult;
import com.mtai.mtairouteplanner.model.poi.PoiSearchRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PoiRetrievalService {

    private final MockDataBundle mockDataBundle;
    private final List<LoadedPoi> loadedPois;
    private final MockDataIndexes indexes;

    public PoiRetrievalService() {
        this(new MockDataLoader());
    }

    public PoiRetrievalService(MockDataLoader mockDataLoader) {
        this.mockDataBundle = mockDataLoader.load();
        this.loadedPois = mockDataLoader.assembleLoadedPois(mockDataBundle);
        this.indexes = MockDataIndexes.from(mockDataBundle, loadedPois);
    }

    public PoiRetrievalResult retrieveCandidates(PoiSearchRequest request) {
        if (request == null || !request.hasAnyFilter()) {
            return new PoiRetrievalResult(request, 0, List.of());
        }

        List<UserPreferenceTag> longTermPreferenceTags = request.userId() == null || request.userId().isBlank()
                ? List.of()
                : indexes.userPreferenceIndex().findPreferenceTagsByUserId(request.userId());

        List<LoadedPoi> matchedPois = resolveCandidateBase(request).stream()
                .filter(poi -> matchesDistrict(poi, request))
                .filter(poi -> matchesBusinessArea(poi, request))
                .filter(poi -> matchesCategory(poi, request))
                .filter(poi -> matchesRouteRole(poi, request))
                .filter(poi -> matchesSuitableScene(poi, request))
                .filter(poi -> matchesTimePeriod(poi, request))
                .filter(poi -> matchesBudgetRange(poi, request))
                .filter(poi -> matchesIndoorOutdoor(poi, request))
                .filter(poi -> !shouldExcludeForAvoidTags(poi, request))
                .toList();

        List<PoiCandidate> rankedCandidates = matchedPois.stream()
                .map(poi -> scoreCandidate(poi, request, longTermPreferenceTags))
                .sorted(Comparator
                        .comparingDouble(PoiCandidate::finalScore).reversed()
                        .thenComparing(Comparator.comparingDouble(PoiCandidate::routeScore).reversed())
                        .thenComparing(Comparator.comparingDouble(PoiCandidate::rating).reversed())
                        .thenComparing(PoiCandidate::poiId))
                .limit(request.topN())
                .toList();

        return new PoiRetrievalResult(request, matchedPois.size(), rankedCandidates);
    }

    public PoiIndex poiIndex() {
        return indexes.poiIndex();
    }

    private List<LoadedPoi> resolveCandidateBase(PoiSearchRequest request) {
        if (hasText(request.businessArea())) {
            return indexes.poiIndex().findByBusinessArea(request.businessArea());
        }
        if (hasText(request.routeRole())) {
            return indexes.poiIndex().findByRouteRole(request.routeRole());
        }
        if (hasText(request.categoryLv1())) {
            return indexes.poiIndex().findByCategoryLv1(request.categoryLv1());
        }
        if (hasText(request.suitableScene())) {
            return indexes.poiIndex().findBySuitableScene(request.suitableScene());
        }
        if (hasText(request.district())) {
            return indexes.poiIndex().findByDistrict(request.district());
        }
        return indexes.poiIndex().allPois();
    }

    private boolean matchesDistrict(LoadedPoi poi, PoiSearchRequest request) {
        return !hasText(request.district()) || request.district().equals(poi.poiBasic().district());
    }

    private boolean matchesBusinessArea(LoadedPoi poi, PoiSearchRequest request) {
        return !hasText(request.businessArea()) || request.businessArea().equals(poi.poiBasic().businessArea());
    }

    private boolean matchesCategory(LoadedPoi poi, PoiSearchRequest request) {
        return !hasText(request.categoryLv1()) || request.categoryLv1().equals(poi.poiBasic().categoryLv1());
    }

    private boolean matchesRouteRole(LoadedPoi poi, PoiSearchRequest request) {
        return !hasText(request.routeRole()) || poi.poiRouteProfile().routeRoles().contains(request.routeRole());
    }

    private boolean matchesSuitableScene(LoadedPoi poi, PoiSearchRequest request) {
        return !hasText(request.suitableScene()) || poi.poiRouteProfile().suitableScenes().contains(request.suitableScene());
    }

    private boolean matchesTimePeriod(LoadedPoi poi, PoiSearchRequest request) {
        return !hasText(request.timePeriod()) || poi.poiRouteProfile().suitableTimePeriods().contains(request.timePeriod());
    }

    private boolean matchesBudgetRange(LoadedPoi poi, PoiSearchRequest request) {
        boolean minOkay = request.minAvgPrice() == null || poi.poiBusinessInfo().avgPrice() >= request.minAvgPrice();
        boolean maxOkay = request.maxAvgPrice() == null || poi.poiBusinessInfo().avgPrice() <= request.maxAvgPrice();
        return minOkay && maxOkay;
    }

    private boolean matchesIndoorOutdoor(LoadedPoi poi, PoiSearchRequest request) {
        return !hasText(request.indoorOutdoor()) || request.indoorOutdoor().equalsIgnoreCase(poi.poiRouteProfile().indoorOutdoor());
    }

    private boolean shouldExcludeForAvoidTags(LoadedPoi poi, PoiSearchRequest request) {
        return request.strictAvoidTags() && !findMatchedTags(request.avoidTags(), poi).isEmpty();
    }

    private PoiCandidate scoreCandidate(
            LoadedPoi poi,
            PoiSearchRequest request,
            List<UserPreferenceTag> longTermPreferenceTags
    ) {
        double routeScoreContribution = poi.poiRouteProfile().routeScore() * 0.55;
        double ratingContribution = poi.poiRatingStats().rating() * 7.5;
        double popularityContribution = poi.poiRatingStats().popularityScore() * 0.12;

        List<String> matchedPreferTags = findMatchedTags(request.preferTags(), poi);
        double preferTagBonus = matchedPreferTags.size() * 6.0;

        List<String> matchedUserPreferenceTags = findMatchedUserPreferenceTags(longTermPreferenceTags, poi);
        double longTermPreferenceBonus = longTermPreferenceTags.stream()
                .filter(tag -> !"avoid".equals(tag.tagType()))
                .filter(tag -> matchesTag(tag.tagValue(), poi))
                .mapToDouble(tag -> tag.weight() * 5.0)
                .sum();

        List<String> matchedAvoidTags = findMatchedTags(request.avoidTags(), poi);
        double requestAvoidPenalty = matchedAvoidTags.size() * 9.0;
        double userAvoidPenalty = longTermPreferenceTags.stream()
                .filter(tag -> "avoid".equals(tag.tagType()))
                .filter(tag -> matchesTag(tag.tagValue(), poi))
                .mapToDouble(tag -> tag.weight() * 7.0)
                .sum();
        double avoidTagPenalty = requestAvoidPenalty + userAvoidPenalty;

        double budgetFitBonus = calculateBudgetFitBonus(poi, request);
        double businessAreaMatchBonus = hasText(request.businessArea())
                && request.businessArea().equals(poi.poiBasic().businessArea()) ? 5.0 : 0.0;

        double finalScore = routeScoreContribution
                + ratingContribution
                + popularityContribution
                + preferTagBonus
                + longTermPreferenceBonus
                + budgetFitBonus
                + businessAreaMatchBonus
                - avoidTagPenalty;

        return new PoiCandidate(
                poi.poiId(),
                poi.poiBasic().name(),
                poi.poiBasic().businessArea(),
                poi.poiBasic().district(),
                poi.poiBasic().categoryLv1(),
                poi.poiBasic().categoryLv2(),
                poi.poiRouteProfile().routeRoles(),
                poi.poiRouteProfile().suitableScenes(),
                poi.poiRouteProfile().suitableTimePeriods(),
                poi.poiRouteProfile().indoorOutdoor(),
                poi.poiBusinessInfo().avgPrice(),
                poi.poiRatingStats().rating(),
                poi.poiRatingStats().popularityScore(),
                poi.poiRouteProfile().routeScore(),
                finalScore,
                preferTagBonus,
                longTermPreferenceBonus,
                avoidTagPenalty,
                budgetFitBonus,
                businessAreaMatchBonus,
                matchedPreferTags,
                matchedUserPreferenceTags,
                matchedAvoidTags
        );
    }

    private double calculateBudgetFitBonus(LoadedPoi poi, PoiSearchRequest request) {
        if (request.minAvgPrice() == null && request.maxAvgPrice() == null) {
            return 0.0;
        }

        int min = request.minAvgPrice() == null ? 0 : request.minAvgPrice();
        int max = request.maxAvgPrice() == null ? Math.max(min, poi.poiBusinessInfo().avgPrice()) : request.maxAvgPrice();
        if (max < min) {
            return 0.0;
        }

        double midpoint = (min + max) / 2.0;
        double halfRange = Math.max((max - min) / 2.0, 1.0);
        double distance = Math.abs(poi.poiBusinessInfo().avgPrice() - midpoint);
        double closeness = Math.max(0.0, 1.0 - (distance / halfRange));
        return 5.0 * closeness;
    }

    private List<String> findMatchedTags(List<String> tags, LoadedPoi poi) {
        List<String> matched = new ArrayList<>();
        for (String tag : tags) {
            if (hasText(tag) && matchesTag(tag, poi)) {
                matched.add(tag);
            }
        }
        return List.copyOf(matched);
    }

    private List<String> findMatchedUserPreferenceTags(List<UserPreferenceTag> userPreferenceTags, LoadedPoi poi) {
        Set<String> matched = new LinkedHashSet<>();
        for (UserPreferenceTag userPreferenceTag : userPreferenceTags) {
            if (!"avoid".equals(userPreferenceTag.tagType()) && matchesTag(userPreferenceTag.tagValue(), poi)) {
                matched.add(userPreferenceTag.tagType() + ":" + userPreferenceTag.tagValue());
            }
        }
        return List.copyOf(matched);
    }

    private boolean matchesTag(String tag, LoadedPoi poi) {
        String normalizedTag = normalize(tag);
        if (normalizedTag.isBlank()) {
            return false;
        }

        for (String signal : collectSignals(poi)) {
            String normalizedSignal = normalize(signal);
            if (normalizedSignal.contains(normalizedTag) || normalizedTag.contains(normalizedSignal)) {
                return true;
            }
        }
        return false;
    }

    private List<String> collectSignals(LoadedPoi poi) {
        List<String> signals = new ArrayList<>();
        signals.add(poi.poiBasic().name());
        signals.add(poi.poiBasic().businessArea());
        signals.add(poi.poiBasic().district());
        signals.add(poi.poiBasic().categoryLv1());
        signals.add(poi.poiBasic().categoryLv2());
        signals.add(poi.poiUgcSummary().reviewSummary());
        signals.add(poi.poiUgcSummary().avoidReason());
        signals.add(poi.poiUgcSummary().recommendReason());
        signals.addAll(poi.poiUgcSummary().positiveKeywords());
        signals.addAll(poi.poiUgcSummary().negativeKeywords());
        signals.addAll(poi.poiUgcSummary().crowdKeywords());
        signals.addAll(poi.poiUgcSummary().sceneKeywords());
        signals.addAll(poi.poiRouteProfile().routeRoles());
        signals.addAll(poi.poiRouteProfile().suitableScenes());
        signals.addAll(poi.poiRouteProfile().suitableTimePeriods());
        for (var poiTag : poi.poiTags()) {
            signals.add(poiTag.tagType());
            signals.add(poiTag.tagValue());
        }
        return signals;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}


