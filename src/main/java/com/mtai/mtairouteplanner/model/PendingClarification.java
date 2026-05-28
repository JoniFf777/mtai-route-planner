package com.mtai.mtairouteplanner.model;

import java.time.LocalDateTime;
import java.util.List;

public record PendingClarification(
        String question,
        List<String> missingFields,
        List<String> candidateTargets,
        String originalUserMessage,
        LocalDateTime createdAt
) {
    public PendingClarification {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        candidateTargets = candidateTargets == null ? List.of() : List.copyOf(candidateTargets);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
