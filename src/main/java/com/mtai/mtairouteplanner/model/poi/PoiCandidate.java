package com.mtai.mtairouteplanner.model.poi;

import java.util.List;

public record PoiCandidate(
        String poiId,
        String name,
        String businessArea,
        String district,
        String categoryLv1,
        String categoryLv2,
        List<String> routeRoles,
        List<String> suitableScenes,
        List<String> suitableTimePeriods,
        String indoorOutdoor,
        int avgPrice,
        double rating,
        double popularityScore,
        double routeScore,
        double finalScore,
        double preferTagBonus,
        double longTermPreferenceBonus,
        double avoidTagPenalty,
        double budgetFitBonus,
        double businessAreaMatchBonus,
        List<String> matchedPreferTags,
        List<String> matchedUserPreferenceTags,
        List<String> matchedAvoidTags
) {
}


