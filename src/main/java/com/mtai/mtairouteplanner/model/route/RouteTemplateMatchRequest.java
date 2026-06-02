package com.mtai.mtairouteplanner.model.route;

public record RouteTemplateMatchRequest(
        String scene,
        String timePeriod,
        String budgetLevel,
        String paceLevel,
        String district,
        Integer durationMinutes,
        int topN
) {
    public RouteTemplateMatchRequest {
        topN = topN <= 0 ? 5 : topN;
    }

    public boolean hasAnyFilter() {
        return hasText(scene)
                || hasText(timePeriod)
                || hasText(budgetLevel)
                || hasText(paceLevel)
                || hasText(district)
                || durationMinutes != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}


