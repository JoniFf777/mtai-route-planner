package com.mtai.mtairouteplanner.ai;

import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.CompactRouteContext;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;

public interface IntentAgentService {

    RoutePlanRequest parsePlanRequest(String userId, String message);

    default PlanParseResult parsePlanRequestResult(String userId, String message) {
        RoutePlanRequest request = parsePlanRequest(userId, message);
        return new PlanParseResult(request, request, "single-parser");
    }

    ParsedAdjustment parseAdjustment(String message, CompactRouteContext routeContext);

    record PlanParseResult(
            RoutePlanRequest primaryRequest,
            RoutePlanRequest fallbackRequest,
            String diagnosticSummary
    ) {
        public boolean hasDistinctFallback() {
            return primaryRequest != null
                    && fallbackRequest != null
                    && !primaryRequest.equals(fallbackRequest);
        }
    }

    record ParsedAdjustment(
            ChangeRequest changeRequest,
            ClarificationAnswer clarificationAnswer
    ) {
        public static ParsedAdjustment change(ChangeRequest changeRequest) {
            return new ParsedAdjustment(changeRequest, null);
        }

        public static ParsedAdjustment clarification(ClarificationAnswer clarificationAnswer) {
            return new ParsedAdjustment(null, clarificationAnswer);
        }

        public boolean isClarificationAnswer() {
            return clarificationAnswer != null;
        }
    }
}
