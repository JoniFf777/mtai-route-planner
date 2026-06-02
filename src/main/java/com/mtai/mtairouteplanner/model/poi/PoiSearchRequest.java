package com.mtai.mtairouteplanner.model.poi;

import java.util.List;

public record PoiSearchRequest(
        String userId,
        String businessArea,
        String district,
        String categoryLv1,
        String routeRole,
        String suitableScene,
        String timePeriod,
        Integer minAvgPrice,
        Integer maxAvgPrice,
        String indoorOutdoor,
        List<String> avoidTags,
        List<String> preferTags,
        int topN,
        boolean strictAvoidTags
) {
    public PoiSearchRequest {
        avoidTags = avoidTags == null ? List.of() : List.copyOf(avoidTags);
        preferTags = preferTags == null ? List.of() : List.copyOf(preferTags);
        topN = topN <= 0 ? 10 : topN;
    }

    public boolean hasAnyFilter() {
        return hasText(businessArea)
                || hasText(district)
                || hasText(categoryLv1)
                || hasText(routeRole)
                || hasText(suitableScene)
                || hasText(timePeriod)
                || hasText(indoorOutdoor)
                || minAvgPrice != null
                || maxAvgPrice != null
                || !avoidTags.isEmpty()
                || !preferTags.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}


