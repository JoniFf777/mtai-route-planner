package com.mtai.mtairouteplanner.model;

import java.util.List;

public record RouteTemplateCandidate(
        String templateId,
        String scene,
        String timePeriod,
        int minDurationMinutes,
        int maxDurationMinutes,
        String budgetLevel,
        String paceLevel,
        List<String> slotSequence,
        List<String> suitableDistricts,
        double matchScore,
        double timeMatchScore,
        double budgetMatchScore,
        double paceMatchScore,
        double districtMatchScore,
        double durationFitScore
) {
}
