package com.mtai.mtairouteplanner.model.clarification;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ClarificationAnswer(
        Integer targetStopOrder,
        String selectedCandidateTarget
) {
}


