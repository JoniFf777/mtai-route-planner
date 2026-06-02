package com.mtai.mtairouteplanner.model.clarification;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.adjustment.ChangeRequest;

import java.time.LocalDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PendingClarification(
        String question,
        List<String> missingFields,
        List<String> candidateTargets,
        String originalUserMessage,
        LocalDateTime createdAt,
        ChangeRequest originalChangeRequest
) {
    public PendingClarification {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        candidateTargets = candidateTargets == null ? List.of() : List.copyOf(candidateTargets);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public PendingClarification(
            String question,
            List<String> missingFields,
            List<String> candidateTargets,
            String originalUserMessage,
            LocalDateTime createdAt
    ) {
        this(question, missingFields, candidateTargets, originalUserMessage, createdAt, null);
    }
}


