package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.data.LoadedPoi;
import com.mtai.mtairouteplanner.data.MockDataBundle;
import com.mtai.mtairouteplanner.data.MockDataIndexes;
import com.mtai.mtairouteplanner.data.MockDataLoader;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.PoiCandidate;
import com.mtai.mtairouteplanner.model.PoiRetrievalResult;
import com.mtai.mtairouteplanner.model.PoiSearchRequest;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteTemplateCandidate;
import com.mtai.mtairouteplanner.model.RouteTemplateMatchRequest;
import com.mtai.mtairouteplanner.model.RouteValidationResult;
import com.mtai.mtairouteplanner.model.TravelEstimate;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RouteOptimizerService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int BEAM_WIDTH = 8;
    private static final int SLOT_TOP_N = 8;
    private static final int MAX_TEMPLATE_CANDIDATES = 5;
    private static final int MAX_ROUTE_RESULTS = 3;

    private final MockDataBundle mockDataBundle;
    private final MockDataIndexes indexes;
    private final RouteTemplateService routeTemplateService;
    private final PoiRetrievalService poiRetrievalService;
    private final TrafficTimeService trafficTimeService;
    private final RouteValidatorService routeValidatorService;

    public RouteOptimizerService() {
        this(new MockDataLoader());
    }

    public RouteOptimizerService(MockDataLoader mockDataLoader) {
        this.mockDataBundle = mockDataLoader.load();
        List<LoadedPoi> loadedPois = mockDataLoader.assembleLoadedPois(mockDataBundle);
        this.indexes = MockDataIndexes.from(mockDataBundle, loadedPois);
        this.routeTemplateService = new RouteTemplateService(mockDataLoader);
        this.poiRetrievalService = new PoiRetrievalService(mockDataLoader);
        this.trafficTimeService = new TrafficTimeService(mockDataLoader);
        this.routeValidatorService = new RouteValidatorService(mockDataLoader);
    }

    public List<GeneratedRoutePlan> generateRoutes(RoutePlanRequest request) {
        if (request == null || request.scene() == null || request.scene().isBlank()) {
            return List.of();
        }

        String resolvedDistrict = hasText(request.district())
                ? request.district()
                : resolveDistrictFromBusinessArea(request.businessArea()).orElse(null);
        TimeWindow timeWindow = parseTimeWindow(request.timeWindow());
        String templateBudgetLevel = inferBudgetLevel(request.budgetTotal(), request.partySize());

        List<RouteTemplateCandidate> templateCandidates = matchTemplates(request, resolvedDistrict, timeWindow, templateBudgetLevel);
        List<GeneratedRoutePlan> feasiblePlans = new ArrayList<>();

        for (RouteTemplateCandidate templateCandidate : templateCandidates) {
            feasiblePlans.addAll(generateRoutesForTemplate(request, templateCandidate, resolvedDistrict, timeWindow));
        }

        Map<String, GeneratedRoutePlan> uniquePlans = new LinkedHashMap<>();
        for (GeneratedRoutePlan feasiblePlan : feasiblePlans) {
            String key = feasiblePlan.templateId() + "|" + feasiblePlan.stops().stream().map(GeneratedRouteStop::poiId).reduce((a, b) -> a + "->" + b).orElse("");
            uniquePlans.putIfAbsent(key, feasiblePlan);
        }

        return uniquePlans.values().stream()
                .sorted(Comparator
                        .comparingDouble(GeneratedRoutePlan::routeScore).reversed()
                        .thenComparingInt(GeneratedRoutePlan::totalBudget)
                        .thenComparing(GeneratedRoutePlan::templateId))
                .limit(MAX_ROUTE_RESULTS)
                .toList();
    }

    private List<RouteTemplateCandidate> matchTemplates(
            RoutePlanRequest request,
            String resolvedDistrict,
            TimeWindow timeWindow,
            String templateBudgetLevel
    ) {
        RouteTemplateMatchRequest primaryRequest = new RouteTemplateMatchRequest(
                request.scene(),
                deriveTemplateTimePeriod(timeWindow),
                templateBudgetLevel,
                request.pace(),
                resolvedDistrict,
                timeWindow.endMinutes() - timeWindow.startMinutes(),
                MAX_TEMPLATE_CANDIDATES
        );

        List<RouteTemplateCandidate> candidates = new ArrayList<>(routeTemplateService.findCandidateTemplates(primaryRequest));
        if (candidates.isEmpty()) {
            RouteTemplateMatchRequest fallbackRequest = new RouteTemplateMatchRequest(
                    request.scene(),
                    null,
                    templateBudgetLevel,
                    request.pace(),
                    resolvedDistrict,
                    timeWindow.endMinutes() - timeWindow.startMinutes(),
                    MAX_TEMPLATE_CANDIDATES
            );
            candidates.addAll(routeTemplateService.findCandidateTemplates(fallbackRequest));
        }
        return candidates.stream()
                .limit(MAX_TEMPLATE_CANDIDATES)
                .toList();
    }

    private List<GeneratedRoutePlan> generateRoutesForTemplate(
            RoutePlanRequest request,
            RouteTemplateCandidate templateCandidate,
            String resolvedDistrict,
            TimeWindow timeWindow
    ) {
        List<List<PoiCandidate>> slotCandidates = new ArrayList<>();
        for (String slotRole : templateCandidate.slotSequence()) {
            List<PoiCandidate> candidates = retrieveSlotCandidates(request, slotRole, resolvedDistrict);
            if (candidates.isEmpty()) {
                return List.of();
            }
            slotCandidates.add(candidates);
        }

        List<PartialRoute> beam = List.of(new PartialRoute(
                templateCandidate,
                new ArrayList<>(),
                timeWindow.startMinutes(),
                0,
                0.0,
                0.0
        ));

        for (int slotIndex = 0; slotIndex < templateCandidate.slotSequence().size(); slotIndex++) {
            String slotRole = templateCandidate.slotSequence().get(slotIndex);
            List<PartialRoute> nextBeam = new ArrayList<>();
            for (PartialRoute partialRoute : beam) {
                for (PoiCandidate poiCandidate : slotCandidates.get(slotIndex)) {
                    if (partialRoute.containsPoi(poiCandidate.poiId())) {
                        continue;
                    }

                    Optional<GeneratedRouteStop> nextStop = buildRouteStop(partialRoute, poiCandidate, slotRole, request);
                    if (nextStop.isEmpty()) {
                        continue;
                    }

                    List<GeneratedRouteStop> newStops = new ArrayList<>(partialRoute.stops());
                    newStops.add(nextStop.get());

                    int newBudget = partialRoute.totalBudget() + nextStop.get().estimatedCost();
                    double newDistanceKm = partialRoute.totalDistanceKm() + nextStop.get().distanceKmFromPrev();
                    double newScore = partialRoute.totalScore() + nextStop.get().stopScore();
                    int newCurrentMinutes = toMinutes(nextStop.get().leaveTime());

                    GeneratedRoutePlan partialPlan = buildGeneratedPlan(
                            templateCandidate,
                            request.timeWindow(),
                            timeWindow.startMinutes(),
                            newCurrentMinutes,
                            newBudget,
                            newDistanceKm,
                            newScore,
                            newStops,
                            null
                    );
                    RouteValidationResult partialValidation = routeValidatorService.validate(partialPlan, request, newStops.size());
                    if (!partialValidation.valid()) {
                        continue;
                    }

                    nextBeam.add(new PartialRoute(
                            templateCandidate,
                            newStops,
                            newCurrentMinutes,
                            newBudget,
                            newDistanceKm,
                            newScore
                    ));
                }
            }

            beam = nextBeam.stream()
                    .sorted(Comparator
                            .comparingDouble((PartialRoute partialRoute) ->
                                    partialSelectionScore(partialRoute, request, templateCandidate.slotSequence().size()))
                            .reversed()
                            .thenComparingInt(PartialRoute::totalBudget))
                    .limit(BEAM_WIDTH)
                    .toList();

            if (beam.isEmpty()) {
                return List.of();
            }
        }

        List<GeneratedRoutePlan> results = new ArrayList<>();
        for (PartialRoute partialRoute : beam) {
            GeneratedRoutePlan planWithoutValidation = buildGeneratedPlan(
                    templateCandidate,
                    request.timeWindow(),
                    timeWindow.startMinutes(),
                    partialRoute.currentMinutes(),
                    partialRoute.totalBudget(),
                    partialRoute.totalDistanceKm(),
                    partialRoute.totalScore(),
                    partialRoute.stops(),
                    null
            );
            RouteValidationResult validationResult = routeValidatorService.validate(
                    planWithoutValidation,
                    request,
                    templateCandidate.slotSequence().size()
            );
            if (validationResult.valid()) {
                results.add(buildGeneratedPlan(
                        templateCandidate,
                        request.timeWindow(),
                        timeWindow.startMinutes(),
                        partialRoute.currentMinutes(),
                        partialRoute.totalBudget(),
                        partialRoute.totalDistanceKm(),
                        partialRoute.totalScore(),
                        partialRoute.stops(),
                        validationResult
                ));
            }
        }
        return results;
    }

    private Optional<GeneratedRouteStop> buildRouteStop(
            PartialRoute partialRoute,
            PoiCandidate poiCandidate,
            String slotRole,
            RoutePlanRequest request
    ) {
        Optional<LoadedPoi> loadedPoi = poiRetrievalService.poiIndex().findByPoiId(poiCandidate.poiId());
        if (loadedPoi.isEmpty()) {
            return Optional.empty();
        }

        double travelMinutes = 0.0;
        double distanceKm = 0.0;
        int arriveMinutes = partialRoute.currentMinutes();

        if (!partialRoute.stops().isEmpty()) {
            String previousPoiId = partialRoute.stops().getLast().poiId();
            Optional<TravelEstimate> travelEstimate = trafficTimeService.estimateTravelTime(previousPoiId, poiCandidate.poiId());
            if (travelEstimate.isEmpty()) {
                return Optional.empty();
            }
            travelMinutes = travelEstimate.get().estimatedMinutes();
            distanceKm = travelEstimate.get().distanceKm();
            arriveMinutes += (int) Math.round(travelMinutes);
        }

        int stayMinutes = loadedPoi.get().poiRouteProfile().avgStayMinutes()
                + Math.min(loadedPoi.get().poiBusinessInfo().avgQueueMinutes(), 20);
        int leaveMinutes = arriveMinutes + stayMinutes;
        int estimatedCost = loadedPoi.get().poiBusinessInfo().avgPrice() * request.partySize();
        double stopScore = poiCandidate.finalScore() - travelMinutes * 0.15;

        return Optional.of(new GeneratedRouteStop(
                partialRoute.stops().size() + 1,
                slotRole,
                poiCandidate.poiId(),
                poiCandidate.name(),
                poiCandidate.businessArea(),
                poiCandidate.district(),
                loadedPoi.get().poiBasic().lng(),
                loadedPoi.get().poiBasic().lat(),
                loadedPoi.get().poiBasic().coordinateSystem(),
                poiCandidate.categoryLv1(),
                poiCandidate.indoorOutdoor(),
                formatMinutes(arriveMinutes),
                formatMinutes(leaveMinutes),
                stayMinutes,
                roundTwo(travelMinutes),
                roundTwo(distanceKm),
                estimatedCost,
                roundTwo(stopScore),
                poiCandidate.matchedPreferTags(),
                poiCandidate.matchedAvoidTags()
        ));
    }

    private GeneratedRoutePlan buildGeneratedPlan(
            RouteTemplateCandidate templateCandidate,
            String timeWindow,
            int startMinutes,
            int endMinutes,
            int totalBudget,
            double totalDistanceKm,
            double accumulatedStopScore,
            List<GeneratedRouteStop> stops,
            RouteValidationResult validationResult
    ) {
        int totalDurationMinutes = endMinutes - startMinutes;
        double totalTravelMinutes = stops.stream().mapToDouble(GeneratedRouteStop::travelMinutesFromPrev).sum();
        double routeScore = roundTwo(
                templateCandidate.matchScore()
                        + accumulatedStopScore
                        + (stops.size() * 4.0)
                        - (totalTravelMinutes * 0.20)
        );

        return new GeneratedRoutePlan(
                templateCandidate.templateId(),
                templateCandidate.scene(),
                timeWindow,
                totalBudget,
                totalDurationMinutes,
                roundTwo(totalDistanceKm),
                routeScore,
                formatMinutes(startMinutes),
                formatMinutes(endMinutes),
                List.copyOf(stops),
                validationResult
        );
    }

    private List<PoiCandidate> retrieveSlotCandidates(
            RoutePlanRequest request,
            String slotRole,
            String resolvedDistrict
    ) {
        SlotQueryProfile profile = slotProfile(slotRole);
        Map<String, PoiCandidate> uniqueCandidates = collectSlotCandidates(
                buildSlotSearchRequests(request, resolvedDistrict, profile, request.scene(), slotRole)
        );

        if (uniqueCandidates.isEmpty() && hasText(request.scene())) {
            uniqueCandidates.putAll(collectSlotCandidates(
                    buildSlotSearchRequests(request, resolvedDistrict, profile, null, slotRole)
            ));
        }

        List<PoiCandidate> topScoredCandidates = uniqueCandidates.values().stream()
                .sorted(Comparator
                        .comparingDouble(PoiCandidate::finalScore).reversed()
                        .thenComparingInt(PoiCandidate::avgPrice)
                        .thenComparing(PoiCandidate::poiId))
                .limit(SLOT_TOP_N)
                .toList();
        List<PoiCandidate> lowestPriceCandidates = uniqueCandidates.values().stream()
                .sorted(Comparator
                        .comparingInt(PoiCandidate::avgPrice)
                        .thenComparing(Comparator.comparingDouble(PoiCandidate::finalScore).reversed())
                        .thenComparing(PoiCandidate::poiId))
                .limit(Math.max(3, SLOT_TOP_N / 2))
                .toList();

        Map<String, PoiCandidate> selectedCandidates = new LinkedHashMap<>();
        for (PoiCandidate candidate : topScoredCandidates) {
            selectedCandidates.putIfAbsent(candidate.poiId(), candidate);
        }
        for (PoiCandidate candidate : lowestPriceCandidates) {
            selectedCandidates.putIfAbsent(candidate.poiId(), candidate);
        }

        return selectedCandidates.values().stream()
                .sorted(Comparator
                        .comparingDouble(PoiCandidate::finalScore).reversed()
                        .thenComparingInt(PoiCandidate::avgPrice)
                        .thenComparing(PoiCandidate::poiId))
                .limit(SLOT_TOP_N + 2)
                .toList();
    }

    private Map<String, PoiCandidate> collectSlotCandidates(List<PoiSearchRequest> searchRequests) {
        Map<String, PoiCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (PoiSearchRequest searchRequest : searchRequests) {
            PoiRetrievalResult retrievalResult = poiRetrievalService.retrieveCandidates(searchRequest);
            for (PoiCandidate candidate : retrievalResult.candidates()) {
                uniqueCandidates.merge(candidate.poiId(), candidate, (current, incoming) ->
                        incoming.finalScore() > current.finalScore() ? incoming : current);
            }
        }
        return uniqueCandidates;
    }

    private List<PoiSearchRequest> buildSlotSearchRequests(
            RoutePlanRequest request,
            String resolvedDistrict,
            SlotQueryProfile profile,
            String suitableScene,
            String slotRole
    ) {
        List<PoiSearchRequest> searchRequests = new ArrayList<>();
        List<String> slotTimePeriods = resolveSlotTimePeriods(slotRole, request.timeWindow());
        for (String slotTimePeriod : slotTimePeriods) {
            searchRequests.add(new PoiSearchRequest(
                    request.userId(),
                    request.businessArea(),
                    resolvedDistrict,
                    profile.categoryLv1(),
                    profile.routeRole(),
                    suitableScene,
                    slotTimePeriod,
                    0,
                    Math.max(request.budgetTotal() / request.partySize(), 60),
                    profile.indoorOutdoor(),
                    request.avoidTags(),
                    mergeTags(request.preferTags(), profile.extraPreferTags()),
                    SLOT_TOP_N,
                    false
            ));

            if (profile.fallbackRouteRole() != null || profile.fallbackCategoryLv1() != null) {
                searchRequests.add(new PoiSearchRequest(
                        request.userId(),
                        request.businessArea(),
                        resolvedDistrict,
                        profile.fallbackCategoryLv1(),
                        profile.fallbackRouteRole(),
                        suitableScene,
                        slotTimePeriod,
                        0,
                        Math.max(request.budgetTotal() / request.partySize(), 60),
                        profile.indoorOutdoor(),
                        request.avoidTags(),
                        mergeTags(request.preferTags(), profile.extraPreferTags()),
                        SLOT_TOP_N,
                        false
                ));
            }
        }
        return searchRequests;
    }

    private List<String> resolveSlotTimePeriods(String slotRole, String timeWindow) {
        Set<String> timePeriods = new LinkedHashSet<>();

        if (slotRole.contains("午餐") || slotRole.contains("简餐") || slotRole.contains("本地小吃")) {
            timePeriods.add("中午");
            timePeriods.add("下午");
        }
        if (slotRole.contains("晚餐") || slotRole.contains("甜品收尾")) {
            timePeriods.add("晚间");
        }
        if (slotRole.contains("夜景") || slotRole.contains("清吧") || slotRole.contains("夜间")
                || slotRole.contains("夜生活") || slotRole.contains("夜宵")) {
            timePeriods.add("晚间");
            timePeriods.add("深夜前");
        }
        if (slotRole.contains("咖啡") || slotRole.contains("聊天") || slotRole.contains("休息")) {
            timePeriods.add("下午");
            timePeriods.add("晚间");
            timePeriods.add("上午");
        }
        if (slotRole.contains("景点") || slotRole.contains("散步") || slotRole.contains("胡同")
                || slotRole.contains("文艺") || slotRole.contains("文化") || slotRole.contains("展览")
                || slotRole.contains("活动") || slotRole.contains("娱乐") || slotRole.contains("聚会")) {
            timePeriods.add("下午");
            timePeriods.add("上午");
            timePeriods.add("晚间");
        }
        if (slotRole.contains("商场餐饮")) {
            timePeriods.add("中午");
            timePeriods.add("晚间");
            timePeriods.add("下午");
        }

        String derivedTimePeriod = derivePoiTimePeriod(timeWindow);
        if (hasText(derivedTimePeriod)) {
            timePeriods.add(derivedTimePeriod);
        }
        if (timePeriods.isEmpty()) {
            return List.of();
        }
        return List.copyOf(timePeriods);
    }

    private SlotQueryProfile slotProfile(String slotRole) {
        return switch (slotRole) {
            case "晚餐主餐" -> new SlotQueryProfile("餐饮", "晚餐主餐", null, null, "indoor", List.of());
            case "午餐主餐" -> new SlotQueryProfile("餐饮", "午餐主餐", null, null, "indoor", List.of());
            case "夜景散步" -> new SlotQueryProfile("景点观光", "夜景散步", "景点观光", "散步点", "outdoor", List.of("夜景"));
            case "甜品收尾" -> new SlotQueryProfile("咖啡甜品", "甜品收尾", "咖啡甜品", "聊天点", "indoor", List.of());
            case "清吧收尾" -> new SlotQueryProfile("夜间消费", "清吧收尾", "夜间消费", "夜生活点", "indoor", List.of());
            case "聊天点" -> new SlotQueryProfile("咖啡甜品", "聊天点", "夜间消费", "夜生活点", null, List.of());
            case "夜间娱乐" -> new SlotQueryProfile("娱乐活动", "夜间娱乐", "夜间消费", "夜生活点", "indoor", List.of());
            case "景点打卡" -> new SlotQueryProfile("景点观光", "景点打卡", null, null, "outdoor", List.of());
            case "胡同散步" -> new SlotQueryProfile("景点观光", "散步点", "景点观光", "夜景散步", "outdoor", List.of("胡同"));
            case "本地小吃" -> new SlotQueryProfile("餐饮", "夜宵点", "餐饮", "午餐主餐", "indoor", List.of("本地小吃"));
            case "简餐点" -> new SlotQueryProfile("餐饮", null, "餐饮", "午餐主餐", "indoor", List.of("简餐"));
            case "室内活动" -> new SlotQueryProfile("文化艺术", "室内活动", "娱乐活动", "强体验活动", "indoor", List.of());
            case "商场餐饮" -> new SlotQueryProfile("餐饮", null, "餐饮", "晚餐主餐", "indoor", List.of("商场"));
            case "室内展览" -> new SlotQueryProfile("文化艺术", "文化体验点", "文化艺术", "文艺点", "indoor", List.of("展览"));
            case "室内娱乐" -> new SlotQueryProfile("娱乐活动", "强体验活动", "娱乐活动", "夜间娱乐", "indoor", List.of());
            case "聚会点" -> new SlotQueryProfile("娱乐活动", "聚会点", "咖啡甜品", "聊天点", "indoor", List.of());
            case "夜生活点" -> new SlotQueryProfile("夜间消费", "夜生活点", "夜间消费", "清吧收尾", "indoor", List.of());
            case "散步点" -> new SlotQueryProfile("景点观光", "散步点", "景点观光", "夜景散步", "outdoor", List.of());
            case "文艺点" -> new SlotQueryProfile("文化艺术", "文艺点", "文化艺术", "文化体验点", "indoor", List.of());
            case "文化体验点" -> new SlotQueryProfile("文化艺术", "文化体验点", "文化艺术", "文艺点", "indoor", List.of());
            case "强体验活动" -> new SlotQueryProfile("娱乐活动", "强体验活动", "娱乐活动", "夜间娱乐", "indoor", List.of());
            case "咖啡休息点" -> new SlotQueryProfile("咖啡甜品", "咖啡休息点", "咖啡甜品", "聊天点", "indoor", List.of());
            case "休息点" -> new SlotQueryProfile("咖啡甜品", "咖啡休息点", "咖啡甜品", "聊天点", "indoor", List.of());
            case "夜宵点" -> new SlotQueryProfile("餐饮", "夜宵点", "夜间消费", "夜宵点", "indoor", List.of());
            default -> new SlotQueryProfile(null, slotRole, null, null, null, List.of());
        };
    }

    private List<String> mergeTags(List<String> first, List<String> second) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private Optional<String> resolveDistrictFromBusinessArea(String businessArea) {
        if (!hasText(businessArea)) {
            return Optional.empty();
        }
        return indexes.businessAreaIndex().findByAreaName(businessArea).map(area -> area.district());
    }

    private String inferBudgetLevel(int budgetTotal, int partySize) {
        int perPersonBudget = budgetTotal / Math.max(partySize, 1);
        if (perPersonBudget <= 120) {
            return "低";
        }
        if (perPersonBudget <= 260) {
            return "中";
        }
        return "高";
    }

    private String deriveTemplateTimePeriod(TimeWindow timeWindow) {
        int start = timeWindow.startMinutes();
        int end = timeWindow.endMinutes();
        if (start >= 18 * 60 && end <= 24 * 60) {
            return "晚间";
        }
        if (start >= 13 * 60 && end >= 18 * 60) {
            return "下午到晚间";
        }
        if (start < 12 * 60 && end <= 15 * 60) {
            return "上午";
        }
        if (start < 18 * 60 && end <= 18 * 60) {
            return "白天";
        }
        if (start >= 13 * 60 && end <= 18 * 60) {
            return "午后";
        }
        return null;
    }

    private String derivePoiTimePeriod(String timeWindow) {
        TimeWindow parsed = parseTimeWindow(timeWindow);
        int start = parsed.startMinutes();
        if (start < 11 * 60) {
            return "上午";
        }
        if (start < 14 * 60) {
            return "中午";
        }
        if (start < 18 * 60) {
            return "下午";
        }
        if (start < 22 * 60) {
            return "晚间";
        }
        return "深夜前";
    }

    private TimeWindow parseTimeWindow(String timeWindow) {
        String[] parts = timeWindow.split("-");
        return new TimeWindow(toMinutes(parts[0]), toMinutes(parts[1]));
    }

    private double partialSelectionScore(PartialRoute partialRoute, RoutePlanRequest request, int totalSlotCount) {
        if (partialRoute.stops().isEmpty() || totalSlotCount <= 0) {
            return partialRoute.totalScore();
        }
        double budgetPerSlot = (double) request.budgetTotal() / totalSlotCount;
        double averageSpentPerStop = (double) partialRoute.totalBudget() / partialRoute.stops().size();
        double budgetPressurePenalty = Math.max(0.0, averageSpentPerStop - budgetPerSlot) * 0.35;
        return partialRoute.totalScore() - budgetPressurePenalty;
    }

    private int toMinutes(String timeText) {
        String[] parts = timeText.split(":");
        if (parts.length != 2) {
            LocalTime time = LocalTime.parse(timeText, TIME_FORMATTER);
            return time.getHour() * 60 + time.getMinute();
        }
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private String formatMinutes(int minutes) {
        int normalized = Math.max(minutes, 0);
        int hour = normalized / 60;
        int minute = normalized % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    private double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TimeWindow(int startMinutes, int endMinutes) {
    }

    private record SlotQueryProfile(
            String categoryLv1,
            String routeRole,
            String fallbackCategoryLv1,
            String fallbackRouteRole,
            String indoorOutdoor,
            List<String> extraPreferTags
    ) {
    }

    private record PartialRoute(
            RouteTemplateCandidate templateCandidate,
            List<GeneratedRouteStop> stops,
            int currentMinutes,
            int totalBudget,
            double totalDistanceKm,
            double totalScore
    ) {
        boolean containsPoi(String poiId) {
            return stops.stream().anyMatch(stop -> stop.poiId().equals(poiId));
        }
    }
}
