package com.mtai.mtairouteplanner.ai;

import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.CompactChangeHistoryItem;
import com.mtai.mtairouteplanner.model.CompactRouteContext;
import com.mtai.mtairouteplanner.model.CompactRouteStop;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class SpringAiIntentAgentService implements IntentAgentService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiIntentAgentService.class);
    private static final Pattern TIME_WINDOW_PATTERN = Pattern.compile("^\\d{2}:\\d{2}-\\d{2}:\\d{2}$");

    private final StructuredIntentParsingGateway parsingGateway;
    private final IntentAgentService fallbackIntentAgentService;
    private final IntentReferenceData intentReferenceData;
    private final String planSystemPrompt;
    private final String adjustSystemPrompt;

    public SpringAiIntentAgentService(
            StructuredIntentParsingGateway parsingGateway,
            IntentAgentService fallbackIntentAgentService,
            IntentReferenceData intentReferenceData,
            String planSystemPrompt,
            String adjustSystemPrompt
    ) {
        this.parsingGateway = Objects.requireNonNull(parsingGateway, "parsingGateway must not be null");
        this.fallbackIntentAgentService = Objects.requireNonNull(fallbackIntentAgentService, "fallbackIntentAgentService must not be null");
        this.intentReferenceData = Objects.requireNonNull(intentReferenceData, "intentReferenceData must not be null");
        this.planSystemPrompt = Objects.requireNonNull(planSystemPrompt, "planSystemPrompt must not be null");
        this.adjustSystemPrompt = Objects.requireNonNull(adjustSystemPrompt, "adjustSystemPrompt must not be null");
    }

    @Override
    public RoutePlanRequest parsePlanRequest(String userId, String message) {
        return parsePlanRequestResult(userId, message).primaryRequest();
    }

    @Override
    public PlanParseResult parsePlanRequestResult(String userId, String message) {
        RoutePlanRequest fallback = fallbackIntentAgentService.parsePlanRequest(userId, message);
        try {
            PlanIntentLlmResponse response = parsingGateway.call(
                    planSystemPrompt,
                    buildPlanUserPrompt(userId, message),
                    PlanIntentLlmResponse.class
            );
            RoutePlanRequest parsed = validateAndNormalizePlanResponse(userId, response, fallback);
            if (parsed != null) {
                String diagnostic = buildPlanDiagnostic(fallback, parsed);
                log.info("Spring AI plan parse comparison for user {}: {}", userId, diagnostic);
                return new PlanParseResult(parsed, fallback, diagnostic);
            }
            log.warn("Spring AI returned invalid plan intent output. Falling back to fake intent parser.");
        } catch (RuntimeException exception) {
            log.warn("Spring AI plan parsing failed. Falling back to fake intent parser.", exception);
        }
        return new PlanParseResult(fallback, fallback, "fake=" + summarizeRoutePlanRequest(fallback) + " | spring_ai=unavailable");
    }

    @Override
    public ParsedAdjustment parseAdjustment(String message, CompactRouteContext routeContext) {
        try {
            AdjustmentIntentLlmResponse response = parsingGateway.call(
                    adjustSystemPrompt,
                    buildAdjustmentUserPrompt(message, routeContext),
                    AdjustmentIntentLlmResponse.class
            );
            ParsedAdjustment parsed = validateAndNormalizeAdjustmentResponse(response, routeContext);
            if (parsed != null) {
                return parsed;
            }
            log.warn("Spring AI returned invalid adjustment output. Falling back to fake intent parser.");
        } catch (RuntimeException exception) {
            log.warn("Spring AI adjustment parsing failed. Falling back to fake intent parser.", exception);
        }
        return fallbackIntentAgentService.parseAdjustment(message, routeContext);
    }

    private RoutePlanRequest validateAndNormalizePlanResponse(
            String userId,
            PlanIntentLlmResponse response,
            RoutePlanRequest fallback
    ) {
        if (response == null) {
            return null;
        }

        String scene = intentReferenceData.canonicalScene(response.scene());
        if (!hasText(scene)) {
            scene = fallback.scene();
        }
        if (!hasText(scene)) {
            return null;
        }

        String businessArea = normalizeBusinessArea(scene, response.businessArea(), fallback);
        String district = intentReferenceData.canonicalDistrict(response.district());
        if (!hasText(district) && hasText(businessArea)) {
            district = intentReferenceData.districtForBusinessArea(businessArea).orElse(null);
        }
        if (!hasText(district)) {
            district = fallback.district();
        }
        if (!hasText(businessArea)) {
            businessArea = fallback.businessArea();
        }

        Integer budgetTotal = positiveOrNull(response.budgetTotal());
        if (budgetTotal == null) {
            budgetTotal = fallback.budgetTotal();
        }

        Integer partySize = positiveOrNull(response.partySize());
        if (partySize == null) {
            partySize = fallback.partySize();
        }

        String timeWindow = normalizeTimeWindow(response.timeWindow(), fallback.timeWindow());
        String pace = normalizePace(response.pace(), fallback.pace());

        return new RoutePlanRequest(
                userId,
                scene,
                businessArea,
                district,
                timeWindow,
                budgetTotal,
                partySize,
                pace,
                normalizeTags(response.preferTags(), fallback.preferTags(), true),
                normalizeTags(response.avoidTags(), fallback.avoidTags(), false)
        );
    }

    private ParsedAdjustment validateAndNormalizeAdjustmentResponse(
            AdjustmentIntentLlmResponse response,
            CompactRouteContext routeContext
    ) {
        if (response == null || !hasText(response.interpretationType())) {
            return null;
        }

        String interpretationType = response.interpretationType().trim().toUpperCase(Locale.ROOT);
        if ("CLARIFICATION_ANSWER".equals(interpretationType)) {
            ClarificationAnswer clarificationAnswer = validateClarificationAnswer(response, routeContext);
            return clarificationAnswer == null ? null : ParsedAdjustment.clarification(clarificationAnswer);
        }
        if (!"CHANGE_REQUEST".equals(interpretationType)) {
            return null;
        }

        ChangeType changeType;
        try {
            changeType = ChangeType.valueOf(nullToEmpty(response.changeType()).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Integer targetStopOrder = response.targetStopOrder();
        if (targetStopOrder != null && !stopOrderExists(routeContext, targetStopOrder)) {
            return null;
        }
        if (changeType == ChangeType.LOWER_BUDGET && positiveOrNull(response.newBudgetTotal()) == null) {
            return null;
        }
        if (changeType == ChangeType.CHANGE_TIME_WINDOW && !validTimeWindow(response.newTimeWindow())) {
            return null;
        }

        return ParsedAdjustment.change(new ChangeRequest(
                changeType,
                targetStopOrder,
                hasText(response.targetSlotRole()) ? response.targetSlotRole().trim() : null,
                positiveOrNull(response.newBudgetTotal()),
                validTimeWindow(response.newTimeWindow()) ? response.newTimeWindow() : null,
                sanitizeTags(response.preferTags(), List.of()),
                sanitizeTags(response.avoidTags(), List.of()),
                routeContext.lockedStopOrders().stream().sorted().toList()
        ));
    }

    private ClarificationAnswer validateClarificationAnswer(
            AdjustmentIntentLlmResponse response,
            CompactRouteContext routeContext
    ) {
        PendingClarification pendingClarification = routeContext.pendingClarification();
        if (pendingClarification == null) {
            return null;
        }

        if (response.clarificationTargetStopOrder() != null) {
            String expectedPrefix = response.clarificationTargetStopOrder() + "|";
            boolean matches = pendingClarification.candidateTargets().stream()
                    .anyMatch(candidate -> candidate.startsWith(expectedPrefix));
            if (matches) {
                return new ClarificationAnswer(response.clarificationTargetStopOrder(), null);
            }
        }

        if (hasText(response.clarificationSelectedCandidateTarget())
                && pendingClarification.candidateTargets().contains(response.clarificationSelectedCandidateTarget())) {
            return new ClarificationAnswer(null, response.clarificationSelectedCandidateTarget());
        }
        return null;
    }

    private String buildPlanUserPrompt(String userId, String message) {
        return """
                Parse this initial route-planning request.
                user_id: %s
                latest_user_message: %s

                Supported scenes: %s
                Supported business areas: %s
                Supported districts: %s
                """.formatted(
                userId,
                message,
                commaSeparated(intentReferenceData.supportedScenes()),
                commaSeparated(intentReferenceData.supportedBusinessAreas()),
                commaSeparated(intentReferenceData.supportedDistricts())
        );
    }

    private String buildAdjustmentUserPrompt(String message, CompactRouteContext routeContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("Parse this route-adjustment request.\n");
        builder.append("latest_user_message: ").append(message).append('\n');
        builder.append("session_version: ").append(routeContext.version()).append('\n');
        builder.append("current_intent_summary: ").append(summarizeIntent(routeContext.currentIntent())).append('\n');
        builder.append("current_route_summary: ").append(nullToEmpty(routeContext.currentRouteSummary())).append('\n');
        builder.append("current_route_stops:\n");
        for (CompactRouteStop stop : routeContext.currentRouteStops()) {
            builder.append("- stop_order=").append(stop.stopOrder())
                    .append(", slot_role=").append(stop.slotRole())
                    .append(", poi_name=").append(stop.poiName())
                    .append(", business_area=").append(stop.businessArea())
                    .append('\n');
        }
        builder.append("locked_stop_orders: ").append(routeContext.lockedStopOrders().stream().sorted().toList()).append('\n');
        builder.append("pending_clarification: ").append(summarizePendingClarification(routeContext.pendingClarification())).append('\n');
        builder.append("latest_change_history:\n");
        for (CompactChangeHistoryItem item : routeContext.latestChangeHistory().stream()
                .sorted(Comparator.comparing(CompactChangeHistoryItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            builder.append("- change_id=").append(item.changeId())
                    .append(", change_type=").append(item.changeType())
                    .append(", target_stop_order=").append(item.targetStopOrder())
                    .append(", before_route_summary=").append(item.beforeRouteSummary())
                    .append(", after_route_summary=").append(item.afterRouteSummary())
                    .append('\n');
        }
        return builder.toString();
    }

    private String summarizeIntent(RouteSessionIntent intent) {
        if (intent == null) {
            return "null";
        }
        return "scene=" + intent.scene()
                + ", business_area=" + intent.businessArea()
                + ", district=" + intent.district()
                + ", time_window=" + intent.timeWindow()
                + ", budget_total=" + intent.budgetTotal()
                + ", party_size=" + intent.partySize()
                + ", pace=" + intent.pace()
                + ", prefer_tags=" + intent.preferTags()
                + ", avoid_tags=" + intent.avoidTags();
    }

    private String summarizePendingClarification(PendingClarification pendingClarification) {
        if (pendingClarification == null) {
            return "null";
        }
        return "question=" + pendingClarification.question()
                + ", missing_fields=" + pendingClarification.missingFields()
                + ", candidate_targets=" + pendingClarification.candidateTargets()
                + ", created_at=" + pendingClarification.createdAt();
    }

    private List<String> sanitizeTags(List<String> tags, List<String> fallback) {
        List<String> source = tags == null || tags.isEmpty() ? fallback : tags;
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String tag : source) {
            if (hasText(tag)) {
                deduplicated.add(tag.trim());
            }
        }
        return List.copyOf(deduplicated);
    }

    private Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private String normalizeBusinessArea(String scene, String candidateBusinessArea, RoutePlanRequest fallback) {
        String businessArea = intentReferenceData.canonicalBusinessArea(candidateBusinessArea);
        if (!hasText(businessArea)) {
            return null;
        }
        if (fallback.businessArea() == null
                && Objects.equals(intentReferenceData.districtForBusinessArea(businessArea).orElse(null), fallback.district())) {
            return null;
        }
        return businessArea;
    }

    private String normalizeTimeWindow(String candidate, String fallback) {
        if (!validTimeWindow(candidate)) {
            return fallback;
        }
        if (!validTimeWindow(fallback)) {
            return candidate.trim();
        }
        String normalizedCandidate = candidate.trim();
        if (timeWindowDurationMinutes(normalizedCandidate) < timeWindowDurationMinutes(fallback)) {
            return fallback;
        }
        return normalizedCandidate;
    }

    private int timeWindowDurationMinutes(String timeWindow) {
        String[] parts = timeWindow.split("-");
        return toMinutes(parts[1]) - toMinutes(parts[0]);
    }

    private int toMinutes(String value) {
        String[] parts = value.split(":");
        return (Integer.parseInt(parts[0]) * 60) + Integer.parseInt(parts[1]);
    }

    private String normalizePace(String candidate, String fallback) {
        if (!hasText(candidate)) {
            return fallback;
        }
        if (!hasText(fallback)) {
            return candidate.trim();
        }
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        if (Set.of("relaxed", "easy", "light", "slow", "leisurely", "轻松").contains(normalized)) {
            return fallback;
        }
        if (Set.of("moderate", "medium", "normal", "适中").contains(normalized)) {
            return fallback;
        }
        if (Set.of("tight", "compact", "fast", "紧凑").contains(normalized)) {
            return fallback;
        }
        return fallback;
    }

    private List<String> normalizeTags(List<String> candidateTags, List<String> fallbackTags, boolean preferTags) {
        List<String> sanitizedFallback = sanitizeTags(fallbackTags, List.of());
        List<String> sanitizedCandidate = sanitizeTags(candidateTags, List.of());
        if (sanitizedCandidate.isEmpty()) {
            return sanitizedFallback;
        }
        if (sanitizedFallback.isEmpty()) {
            return preferTags ? sanitizedCandidate.stream().limit(2).toList() : List.of();
        }

        Set<String> candidateSemanticKeys = sanitizedCandidate.stream()
                .map(this::semanticTagKey)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        List<String> matchedFallbackTags = sanitizedFallback.stream()
                .filter(tag -> candidateSemanticKeys.contains(semanticTagKey(tag)))
                .limit(preferTags ? 2 : sanitizedFallback.size())
                .toList();
        return matchedFallbackTags.isEmpty() ? sanitizedFallback : matchedFallbackTags;
    }

    private String semanticTagKey(String tag) {
        String normalized = nullToEmpty(tag).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "photo", "photos", "picture", "pictures", "拍照" -> "photo";
            case "coffee", "cafe", "咖啡" -> "coffee";
            case "indoor", "室内" -> "indoor";
            case "quiet", "安静" -> "quiet";
            case "vibe", "romantic", "氛围", "氛围好" -> "vibe";
            case "walk", "citywalk", "散步" -> "walk";
            case "budget", "cheap", "便宜", "平价" -> "budget";
            case "queue", "line", "排队" -> "queue";
            case "expensive", "pricey", "贵", "太贵" -> "expensive";
            default -> normalized;
        };
    }

    private String buildPlanDiagnostic(RoutePlanRequest fallback, RoutePlanRequest parsed) {
        return "fake=" + summarizeRoutePlanRequest(fallback) + " | spring_ai=" + summarizeRoutePlanRequest(parsed);
    }

    private String summarizeRoutePlanRequest(RoutePlanRequest request) {
        return "{scene=" + request.scene()
                + ", business_area=" + request.businessArea()
                + ", district=" + request.district()
                + ", time_window=" + request.timeWindow()
                + ", budget_total=" + request.budgetTotal()
                + ", party_size=" + request.partySize()
                + ", pace=" + request.pace()
                + ", prefer_tags=" + request.preferTags()
                + ", avoid_tags=" + request.avoidTags()
                + "}";
    }

    private boolean validTimeWindow(String value) {
        return hasText(value) && TIME_WINDOW_PATTERN.matcher(value.trim()).matches();
    }

    private boolean stopOrderExists(CompactRouteContext routeContext, int stopOrder) {
        return routeContext.currentRouteStops().stream().anyMatch(stop -> stop.stopOrder() == stopOrder);
    }

    private String commaSeparated(Set<String> values) {
        return String.join(", ", values);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PlanIntentLlmResponse(
            String scene,
            String businessArea,
            String district,
            String timeWindow,
            Integer budgetTotal,
            Integer partySize,
            String pace,
            List<String> preferTags,
            List<String> avoidTags
    ) {
        public PlanIntentLlmResponse {
            preferTags = preferTags == null ? List.of() : List.copyOf(preferTags);
            avoidTags = avoidTags == null ? List.of() : List.copyOf(avoidTags);
        }
    }

    public record AdjustmentIntentLlmResponse(
            String interpretationType,
            String changeType,
            Integer targetStopOrder,
            String targetSlotRole,
            Integer newBudgetTotal,
            String newTimeWindow,
            List<String> preferTags,
            List<String> avoidTags,
            Integer clarificationTargetStopOrder,
            String clarificationSelectedCandidateTarget
    ) {
        public AdjustmentIntentLlmResponse {
            preferTags = preferTags == null ? List.of() : List.copyOf(preferTags);
            avoidTags = avoidTags == null ? List.of() : List.copyOf(avoidTags);
        }
    }
}
