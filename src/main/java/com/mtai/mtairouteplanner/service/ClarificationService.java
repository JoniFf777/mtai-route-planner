package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.ClarificationResolutionResult;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RouteSessionState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ClarificationService {

    private final RouteSessionService routeSessionService;

    public ClarificationService(RouteSessionService routeSessionService) {
        this.routeSessionService = routeSessionService;
    }

    public PreparationResult prepareClarification(RouteSessionState session, ChangeRequest changeRequest) {
        if (!requiresTargetStop(changeRequest.changeType()) || changeRequest.targetStopOrder() != null) {
            return PreparationResult.resolved(changeRequest, session);
        }

        List<GeneratedRouteStop> candidates = findCandidateStops(session, changeRequest);
        if (candidates.isEmpty()) {
            return PreparationResult.rejected("No matching stop could be identified for clarification.", session);
        }
        if (candidates.size() == 1) {
            return PreparationResult.resolved(withTargetStopOrder(changeRequest, candidates.getFirst().stopOrder()), session);
        }

        PendingClarification pendingClarification = new PendingClarification(
                buildQuestion(changeRequest),
                List.of("target_stop_order"),
                candidates.stream().map(this::candidateLabel).toList(),
                null,
                LocalDateTime.now(),
                changeRequest
        );
        RouteSessionState updatedSession = routeSessionService.setPendingClarification(
                session.sessionId(),
                session.version(),
                pendingClarification
        );
        return PreparationResult.waiting(updatedSession, "Clarification is required before applying this change.");
    }

    public ClarificationResolutionResult resolvePendingClarification(String sessionId, ClarificationAnswer clarificationAnswer) {
        RouteSessionState session = routeSessionService.findSession(sessionId)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
        PendingClarification pendingClarification = session.pendingClarification();
        if (pendingClarification == null) {
            throw new IllegalArgumentException("No pending clarification exists for this session.");
        }
        if (pendingClarification.originalChangeRequest() == null) {
            throw new IllegalArgumentException("Pending clarification does not contain an original change request.");
        }

        int resolvedStopOrder = resolveStopOrder(pendingClarification, clarificationAnswer);
        ChangeRequest resolvedChangeRequest = withTargetStopOrder(
                pendingClarification.originalChangeRequest(),
                resolvedStopOrder
        );
        RouteSessionState clearedSession = routeSessionService.clearPendingClarification(sessionId, session.version());

        return new ClarificationResolutionResult(
                sessionId,
                "RESOLVED",
                "Clarification resolved successfully.",
                resolvedChangeRequest,
                clearedSession,
                clearedSession.currentRoute()
        );
    }

    private List<GeneratedRouteStop> findCandidateStops(RouteSessionState session, ChangeRequest changeRequest) {
        if (session.currentRoute() == null) {
            return List.of();
        }
        List<GeneratedRouteStop> candidates = new ArrayList<>();
        for (GeneratedRouteStop stop : session.currentRoute().stops()) {
            if (changeRequest.targetSlotRole() == null || changeRequest.targetSlotRole().isBlank()
                    || Objects.equals(changeRequest.targetSlotRole(), stop.slotRole())) {
                candidates.add(stop);
            }
        }
        return List.copyOf(candidates);
    }

    private String buildQuestion(ChangeRequest changeRequest) {
        if (changeRequest.targetSlotRole() != null && !changeRequest.targetSlotRole().isBlank()) {
            return "Which stop with slot role '" + changeRequest.targetSlotRole() + "' should be changed?";
        }
        return "Which stop should be changed?";
    }

    private String candidateLabel(GeneratedRouteStop stop) {
        return stop.stopOrder() + "|" + stop.slotRole() + "|" + stop.poiName();
    }

    private int resolveStopOrder(PendingClarification pendingClarification, ClarificationAnswer clarificationAnswer) {
        if (clarificationAnswer == null) {
            throw new IllegalArgumentException("Clarification answer is required.");
        }
        if (clarificationAnswer.targetStopOrder() != null) {
            String prefix = clarificationAnswer.targetStopOrder() + "|";
            boolean matched = pendingClarification.candidateTargets().stream()
                    .anyMatch(candidate -> candidate.startsWith(prefix));
            if (!matched) {
                throw new IllegalArgumentException("Clarification answer does not match any pending candidate stop.");
            }
            return clarificationAnswer.targetStopOrder();
        }
        if (clarificationAnswer.selectedCandidateTarget() != null && !clarificationAnswer.selectedCandidateTarget().isBlank()) {
            Optional<String> matchedCandidate = pendingClarification.candidateTargets().stream()
                    .filter(candidate -> candidate.equals(clarificationAnswer.selectedCandidateTarget()))
                    .findFirst();
            if (matchedCandidate.isPresent()) {
                return Integer.parseInt(matchedCandidate.get().split("\\|")[0]);
            }
        }
        throw new IllegalArgumentException("Clarification answer must include a valid target_stop_order or selected_candidate_target.");
    }

    private boolean requiresTargetStop(ChangeType changeType) {
        return changeType == ChangeType.REPLACE_STOP
                || changeType == ChangeType.REMOVE_STOP
                || changeType == ChangeType.LOCK_STOP
                || changeType == ChangeType.UNLOCK_STOP;
    }

    private ChangeRequest withTargetStopOrder(ChangeRequest original, int targetStopOrder) {
        return new ChangeRequest(
                original.changeType(),
                targetStopOrder,
                original.targetSlotRole(),
                original.newBudgetTotal(),
                original.newTimeWindow(),
                original.preferTags(),
                original.avoidTags(),
                original.lockedStopOrders()
        );
    }

    public record PreparationResult(
            String status,
            String message,
            ChangeRequest resolvedChangeRequest,
            RouteSessionState sessionState
    ) {
        static PreparationResult resolved(ChangeRequest changeRequest, RouteSessionState sessionState) {
            return new PreparationResult("RESOLVED", null, changeRequest, sessionState);
        }

        static PreparationResult waiting(RouteSessionState sessionState, String message) {
            return new PreparationResult("WAITING_CLARIFICATION", message, null, sessionState);
        }

        static PreparationResult rejected(String message, RouteSessionState sessionState) {
            return new PreparationResult("REJECTED", message, null, sessionState);
        }
    }
}
