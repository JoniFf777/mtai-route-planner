package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.data.LoadedPoi;
import com.mtai.mtairouteplanner.data.MockDataBundle;
import com.mtai.mtairouteplanner.data.MockDataIndexes;
import com.mtai.mtairouteplanner.data.MockDataLoader;
import com.mtai.mtairouteplanner.model.AdjustmentResult;
import com.mtai.mtairouteplanner.model.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.PoiCandidate;
import com.mtai.mtairouteplanner.model.PoiRetrievalResult;
import com.mtai.mtairouteplanner.model.PoiSearchRequest;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.model.RouteValidationResult;
import com.mtai.mtairouteplanner.model.TravelEstimate;

import java.time.LocalDateTime;
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
import java.util.concurrent.atomic.AtomicLong;

public class RouteAdjustmentService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int SLOT_TOP_N = 8;

    private final MockDataBundle mockDataBundle;
    private final MockDataIndexes indexes;
    private final RouteSessionService routeSessionService;
    private final RouteOptimizerService routeOptimizerService;
    private final PoiRetrievalService poiRetrievalService;
    private final TrafficTimeService trafficTimeService;
    private final RouteValidatorService routeValidatorService;
    private final AtomicLong changeSequence = new AtomicLong(20000);

    public RouteAdjustmentService() {
        this(new MockDataLoader(), new RouteSessionService());
    }

    public RouteAdjustmentService(RouteSessionService routeSessionService) {
        this(new MockDataLoader(), routeSessionService);
    }

    public RouteAdjustmentService(MockDataLoader mockDataLoader, RouteSessionService routeSessionService) {
        this.mockDataBundle = mockDataLoader.load();
        this.indexes = MockDataIndexes.from(mockDataBundle, mockDataLoader.assembleLoadedPois(mockDataBundle));
        this.routeSessionService = routeSessionService;
        this.routeOptimizerService = new RouteOptimizerService(mockDataLoader);
        this.poiRetrievalService = new PoiRetrievalService(mockDataLoader);
        this.trafficTimeService = new TrafficTimeService(mockDataLoader);
        this.routeValidatorService = new RouteValidatorService(mockDataLoader);
    }

    public AdjustmentResult applyChange(String sessionId, long expectedVersion, ChangeRequest changeRequest) {
        Optional<RouteSessionState> sessionOptional = routeSessionService.findSession(sessionId);
        if (sessionOptional.isEmpty()) {
            return new AdjustmentResult(sessionId, AdjustmentStatus.NOT_FOUND, "Route session not found.", null, null);
        }

        RouteSessionState session = sessionOptional.get();
        if (session.version() != expectedVersion) {
            return new AdjustmentResult(
                    sessionId,
                    AdjustmentStatus.VERSION_CONFLICT,
                    "Session version conflict.",
                    session,
                    session.currentRoute()
            );
        }
        if (changeRequest == null || changeRequest.changeType() == null) {
            return new AdjustmentResult(sessionId, AdjustmentStatus.REJECTED, "Change request is missing change type.", session, session.currentRoute());
        }
        if (session.currentRoute() == null) {
            return new AdjustmentResult(sessionId, AdjustmentStatus.FAILED, "Current route is missing.", session, null);
        }

        try {
            return switch (changeRequest.changeType()) {
                case LOCK_STOP -> applyLockChange(session, changeRequest, true);
                case UNLOCK_STOP -> applyLockChange(session, changeRequest, false);
                default -> applyRouteAdjustment(session, expectedVersion, changeRequest);
            };
        } catch (RouteSessionNotFoundException exception) {
            return new AdjustmentResult(sessionId, AdjustmentStatus.NOT_FOUND, exception.getMessage(), null, null);
        } catch (RouteSessionVersionConflictException exception) {
            RouteSessionState latestSession = routeSessionService.findSession(sessionId).orElse(null);
            return new AdjustmentResult(sessionId, AdjustmentStatus.VERSION_CONFLICT, exception.getMessage(), latestSession, latestSession == null ? null : latestSession.currentRoute());
        }
    }

    private AdjustmentResult applyLockChange(RouteSessionState session, ChangeRequest changeRequest, boolean lock) {
        if (changeRequest.targetStopOrder() == null) {
            return new AdjustmentResult(session.sessionId(), AdjustmentStatus.REJECTED, "Target stop order is required.", session, session.currentRoute());
        }
        if (!hasStopOrder(session.currentRoute(), changeRequest.targetStopOrder())) {
            return new AdjustmentResult(session.sessionId(), AdjustmentStatus.REJECTED, "Target stop does not exist.", session, session.currentRoute());
        }

        RouteSessionState updated = lock
                ? routeSessionService.lockStop(session.sessionId(), changeRequest.targetStopOrder())
                : routeSessionService.unlockStop(session.sessionId(), changeRequest.targetStopOrder());
        RouteSessionState finalState = routeSessionService.appendChangeHistory(
                updated.sessionId(),
                new RouteChangeRecord(
                        nextChangeId(),
                        changeRequest.changeType().name(),
                        changeRequest.toString(),
                        changeRequest.targetStopOrder(),
                        session.currentRoute(),
                        updated.currentRoute(),
                        LocalDateTime.now()
                )
        );

        return new AdjustmentResult(
                finalState.sessionId(),
                AdjustmentStatus.SUCCESS,
                lock ? "Stop locked." : "Stop unlocked.",
                finalState,
                finalState.currentRoute()
        );
    }

    private AdjustmentResult applyRouteAdjustment(RouteSessionState session, long expectedVersion, ChangeRequest changeRequest) {
        Set<Integer> lockedStopOrders = resolveLockedStopOrders(session, changeRequest);
        String lockedStopViolation = validateLockedStopChange(session.currentRoute(), lockedStopOrders, changeRequest);
        if (lockedStopViolation != null) {
            return new AdjustmentResult(session.sessionId(), AdjustmentStatus.REJECTED, lockedStopViolation, session, session.currentRoute());
        }

        RouteSessionIntent updatedIntent = mergeIntent(session.currentIntent(), changeRequest);
        RoutePlanRequest routePlanRequest = updatedIntent.toRoutePlanRequest(session.userId());
        List<GeneratedRoutePlan> optimizerRoutes = routeOptimizerService.generateRoutes(routePlanRequest);

        Optional<GeneratedRoutePlan> adjustedRoute = selectOptimizerRoute(session, changeRequest, lockedStopOrders, updatedIntent, optimizerRoutes)
                .or(() -> buildLocalFallback(session, changeRequest, lockedStopOrders, updatedIntent));

        if (adjustedRoute.isEmpty()) {
            return new AdjustmentResult(
                    session.sessionId(),
                    AdjustmentStatus.FAILED,
                    "No feasible adjusted route found.",
                    session,
                    session.currentRoute()
            );
        }

        RouteSessionState latestSession = session;
        if (!updatedIntent.equals(session.currentIntent())) {
            latestSession = routeSessionService.updateCurrentIntent(session.sessionId(), expectedVersion, updatedIntent);
        }
        long routeExpectedVersion = latestSession.version();
        latestSession = routeSessionService.updateCurrentRoute(session.sessionId(), routeExpectedVersion, adjustedRoute.get());
        latestSession = routeSessionService.appendChangeHistory(
                latestSession.sessionId(),
                new RouteChangeRecord(
                        nextChangeId(),
                        changeRequest.changeType().name(),
                        changeRequest.toString(),
                        changeRequest.targetStopOrder(),
                        session.currentRoute(),
                        adjustedRoute.get(),
                        LocalDateTime.now()
                )
        );

        return new AdjustmentResult(
                latestSession.sessionId(),
                AdjustmentStatus.SUCCESS,
                "Route adjusted successfully.",
                latestSession,
                adjustedRoute.get()
        );
    }

    private Optional<GeneratedRoutePlan> selectOptimizerRoute(
            RouteSessionState session,
            ChangeRequest changeRequest,
            Set<Integer> lockedStopOrders,
            RouteSessionIntent updatedIntent,
            List<GeneratedRoutePlan> optimizerRoutes
    ) {
        return optimizerRoutes.stream()
                .filter(route -> preservesLockedStops(route, session.currentRoute(), lockedStopOrders))
                .filter(route -> matchesChangeExpectation(route, session.currentRoute(), changeRequest, updatedIntent))
                .findFirst();
    }

    private boolean matchesChangeExpectation(
            GeneratedRoutePlan candidate,
            GeneratedRoutePlan currentRoute,
            ChangeRequest changeRequest,
            RouteSessionIntent updatedIntent
    ) {
        return switch (changeRequest.changeType()) {
            case REPLACE_STOP -> changeRequest.targetStopOrder() != null
                    && hasStopOrder(candidate, changeRequest.targetStopOrder())
                    && !candidate.stops().get(changeRequest.targetStopOrder() - 1).poiId()
                    .equals(currentRoute.stops().get(changeRequest.targetStopOrder() - 1).poiId());
            case REMOVE_STOP -> candidate.stops().size() < currentRoute.stops().size();
            case ADD_STOP -> candidate.stops().size() > currentRoute.stops().size();
            case LOWER_BUDGET -> candidate.totalBudget() <= updatedIntent.budgetTotal()
                    && candidate.totalBudget() < currentRoute.totalBudget();
            case CHANGE_TIME_WINDOW -> candidate.endTime().compareTo(changeRequest.newTimeWindow().split("-")[1]) <= 0;
            case SWITCH_TO_INDOOR -> candidate.stops().stream()
                    .allMatch(stop -> "indoor".equalsIgnoreCase(stop.indoorOutdoor()));
            default -> true;
        };
    }

    private Optional<GeneratedRoutePlan> buildLocalFallback(
            RouteSessionState session,
            ChangeRequest changeRequest,
            Set<Integer> lockedStopOrders,
            RouteSessionIntent updatedIntent
    ) {
        return switch (changeRequest.changeType()) {
            case REPLACE_STOP -> buildLocalReplaceRoute(session, changeRequest, updatedIntent, lockedStopOrders);
            case REMOVE_STOP -> buildLocalRemoveRoute(session, changeRequest, updatedIntent, lockedStopOrders);
            case ADD_STOP -> buildLocalAddRoute(session, changeRequest, updatedIntent, lockedStopOrders);
            default -> Optional.empty();
        };
    }

    private Optional<GeneratedRoutePlan> buildLocalReplaceRoute(
            RouteSessionState session,
            ChangeRequest changeRequest,
            RouteSessionIntent updatedIntent,
            Set<Integer> lockedStopOrders
    ) {
        int targetIndex = changeRequest.targetStopOrder() - 1;
        GeneratedRouteStop targetStop = session.currentRoute().stops().get(targetIndex);
        Set<String> excludedPoiIds = new LinkedHashSet<>();
        session.currentRoute().stops().forEach(stop -> excludedPoiIds.add(stop.poiId()));

        for (PoiCandidate replacementCandidate : findCandidatesForSlot(
                targetStop.slotRole(),
                targetStop.businessArea(),
                targetStop.district(),
                updatedIntent,
                excludedPoiIds,
                false
        )) {
            Optional<LoadedPoi> loadedPoi = indexes.poiIndex().findByPoiId(replacementCandidate.poiId());
            if (loadedPoi.isEmpty()) {
                continue;
            }

            List<RouteStopSeed> routeStopSeeds = new ArrayList<>();
            boolean invalidRoute = false;
            for (int i = 0; i < session.currentRoute().stops().size(); i++) {
                GeneratedRouteStop existingStop = session.currentRoute().stops().get(i);
                if (i == targetIndex) {
                    routeStopSeeds.add(RouteStopSeed.fromCandidate(targetStop.slotRole(), replacementCandidate, loadedPoi.get()));
                } else {
                    Optional<LoadedPoi> existingPoi = indexes.poiIndex().findByPoiId(existingStop.poiId());
                    if (existingPoi.isEmpty()) {
                        invalidRoute = true;
                        break;
                    }
                    routeStopSeeds.add(RouteStopSeed.fromExisting(existingStop, existingPoi.get()));
                }
            }
            if (invalidRoute) {
                continue;
            }

            Optional<GeneratedRoutePlan> rebuiltRoute = buildRoutePlan(session.currentRoute(), routeStopSeeds, updatedIntent)
                    .filter(route -> preservesLockedStops(route, session.currentRoute(), lockedStopOrders));
            if (rebuiltRoute.isPresent()) {
                return rebuiltRoute;
            }
        }
        return Optional.empty();
    }

    private Optional<GeneratedRoutePlan> buildLocalRemoveRoute(
            RouteSessionState session,
            ChangeRequest changeRequest,
            RouteSessionIntent updatedIntent,
            Set<Integer> lockedStopOrders
    ) {
        int targetIndex = changeRequest.targetStopOrder() - 1;
        List<RouteStopSeed> routeStopSeeds = new ArrayList<>();
        for (int i = 0; i < session.currentRoute().stops().size(); i++) {
            if (i == targetIndex) {
                continue;
            }
            GeneratedRouteStop existingStop = session.currentRoute().stops().get(i);
            Optional<LoadedPoi> existingPoi = indexes.poiIndex().findByPoiId(existingStop.poiId());
            if (existingPoi.isEmpty()) {
                return Optional.empty();
            }
            routeStopSeeds.add(RouteStopSeed.fromExisting(existingStop, existingPoi.get()));
        }
        return buildRoutePlan(session.currentRoute(), routeStopSeeds, updatedIntent)
                .filter(route -> preservesLockedStops(route, session.currentRoute(), lockedStopOrders));
    }

    private Optional<GeneratedRoutePlan> buildLocalAddRoute(
            RouteSessionState session,
            ChangeRequest changeRequest,
            RouteSessionIntent updatedIntent,
            Set<Integer> lockedStopOrders
    ) {
        String slotRole = hasText(changeRequest.targetSlotRole())
                ? changeRequest.targetSlotRole()
                : "咖啡休息点";
        int insertAfterIndex = changeRequest.targetStopOrder() == null
                ? session.currentRoute().stops().size() - 1
                : changeRequest.targetStopOrder() - 1;
        GeneratedRouteStop anchorStop = session.currentRoute().stops().get(Math.max(0, Math.min(insertAfterIndex, session.currentRoute().stops().size() - 1)));

        Set<String> excludedPoiIds = new LinkedHashSet<>();
        session.currentRoute().stops().forEach(stop -> excludedPoiIds.add(stop.poiId()));
        for (PoiCandidate addCandidate : findCandidatesForSlot(
                slotRole,
                anchorStop.businessArea(),
                anchorStop.district(),
                updatedIntent,
                excludedPoiIds,
                false
        )) {
            Optional<LoadedPoi> loadedPoi = indexes.poiIndex().findByPoiId(addCandidate.poiId());
            if (loadedPoi.isEmpty()) {
                continue;
            }

            List<RouteStopSeed> routeStopSeeds = new ArrayList<>();
            boolean invalidRoute = false;
            for (int i = 0; i < session.currentRoute().stops().size(); i++) {
                GeneratedRouteStop existingStop = session.currentRoute().stops().get(i);
                Optional<LoadedPoi> existingPoi = indexes.poiIndex().findByPoiId(existingStop.poiId());
                if (existingPoi.isEmpty()) {
                    invalidRoute = true;
                    break;
                }
                routeStopSeeds.add(RouteStopSeed.fromExisting(existingStop, existingPoi.get()));
                if (i == insertAfterIndex) {
                    routeStopSeeds.add(RouteStopSeed.fromCandidate(slotRole, addCandidate, loadedPoi.get()));
                }
            }
            if (invalidRoute) {
                continue;
            }

            Optional<GeneratedRoutePlan> rebuiltRoute = buildRoutePlan(session.currentRoute(), routeStopSeeds, updatedIntent)
                    .filter(route -> preservesLockedStops(route, session.currentRoute(), lockedStopOrders));
            if (rebuiltRoute.isPresent()) {
                return rebuiltRoute;
            }
        }
        return Optional.empty();
    }

    private List<PoiCandidate> findCandidatesForSlot(
            String slotRole,
            String fallbackBusinessArea,
            String fallbackDistrict,
            RouteSessionIntent intent,
            Set<String> excludedPoiIds,
            boolean forceIndoor
    ) {
        SlotSearchProfile profile = slotSearchProfile(slotRole);
        String businessArea = hasText(intent.businessArea()) ? intent.businessArea() : fallbackBusinessArea;
        String district = hasText(intent.district()) ? intent.district() : fallbackDistrict;
        List<String> preferTags = mergeTags(intent.preferTags(), profile.extraPreferTags());
        List<String> scenes = new ArrayList<>();
        if (hasText(intent.scene())) {
            scenes.add(intent.scene());
        }
        scenes.add(null);
        List<String> businessAreas = new ArrayList<>();
        if (hasText(businessArea)) {
            businessAreas.add(businessArea);
        }
        if (!hasText(intent.businessArea())) {
            businessAreas.add(null);
        }
        if (businessAreas.isEmpty()) {
            businessAreas.add(null);
        }
        Map<String, PoiCandidate> allCandidates = new LinkedHashMap<>();

        for (String scene : scenes) {
            for (String scopedBusinessArea : businessAreas) {
                for (String timePeriod : resolveSlotTimePeriods(slotRole, intent.timeWindow())) {
                    List<PoiSearchRequest> searchRequests = new ArrayList<>();
                    searchRequests.add(new PoiSearchRequest(
                            null,
                            scopedBusinessArea,
                            district,
                            profile.categoryLv1(),
                            profile.routeRole(),
                            scene,
                            timePeriod,
                            0,
                            Math.max(intent.budgetTotal() / Math.max(intent.partySize(), 1), 60),
                            forceIndoor ? "indoor" : profile.indoorOutdoor(),
                            intent.avoidTags(),
                            preferTags,
                            SLOT_TOP_N,
                            false
                    ));
                    if (profile.fallbackCategoryLv1() != null || profile.fallbackRouteRole() != null) {
                        searchRequests.add(new PoiSearchRequest(
                                null,
                                scopedBusinessArea,
                                district,
                                profile.fallbackCategoryLv1(),
                                profile.fallbackRouteRole(),
                                scene,
                                timePeriod,
                                0,
                                Math.max(intent.budgetTotal() / Math.max(intent.partySize(), 1), 60),
                                forceIndoor ? "indoor" : profile.indoorOutdoor(),
                                intent.avoidTags(),
                                preferTags,
                                SLOT_TOP_N,
                                false
                        ));
                    }

                    Map<String, PoiCandidate> candidates = new LinkedHashMap<>();
                    for (PoiSearchRequest searchRequest : searchRequests) {
                        PoiRetrievalResult retrievalResult = poiRetrievalService.retrieveCandidates(searchRequest);
                        for (PoiCandidate candidate : retrievalResult.candidates()) {
                            if (excludedPoiIds.contains(candidate.poiId())) {
                                continue;
                            }
                            if (forceIndoor && !"indoor".equalsIgnoreCase(candidate.indoorOutdoor())) {
                                continue;
                            }
                            candidates.putIfAbsent(candidate.poiId(), candidate);
                            allCandidates.putIfAbsent(candidate.poiId(), candidate);
                        }
                    }
                    if (!candidates.isEmpty()) {
                        // Keep searching to gather more valid fallbacks if the first candidate later fails validation.
                    }
                }
            }
        }
        return allCandidates.values().stream()
                .sorted(Comparator
                        .comparingDouble(PoiCandidate::finalScore).reversed()
                        .thenComparing(PoiCandidate::poiId))
                .toList();
    }

    private Optional<GeneratedRoutePlan> buildRoutePlan(
            GeneratedRoutePlan baselineRoute,
            List<RouteStopSeed> routeStopSeeds,
            RouteSessionIntent updatedIntent
    ) {
        if (routeStopSeeds.isEmpty()) {
            return Optional.empty();
        }

        int startMinutes = resolveStartMinutes(baselineRoute, updatedIntent.timeWindow());
        List<GeneratedRouteStop> rebuiltStops = new ArrayList<>();
        int currentMinutes = startMinutes;
        double totalDistanceKm = 0.0;
        double totalStopScore = 0.0;
        int totalBudget = 0;

        for (int i = 0; i < routeStopSeeds.size(); i++) {
            RouteStopSeed seed = routeStopSeeds.get(i);
            double travelMinutes = 0.0;
            double distanceKm = 0.0;
            int arriveMinutes = currentMinutes;

            if (!rebuiltStops.isEmpty()) {
                String previousPoiId = rebuiltStops.getLast().poiId();
                Optional<TravelEstimate> travelEstimate = trafficTimeService.estimateTravelTime(previousPoiId, seed.loadedPoi().poiId());
                if (travelEstimate.isEmpty()) {
                    return Optional.empty();
                }
                travelMinutes = travelEstimate.get().estimatedMinutes();
                distanceKm = travelEstimate.get().distanceKm();
                arriveMinutes += (int) Math.round(travelMinutes);
            }

            int stayMinutes = seed.loadedPoi().poiRouteProfile().avgStayMinutes()
                    + Math.min(seed.loadedPoi().poiBusinessInfo().avgQueueMinutes(), 20);
            int leaveMinutes = arriveMinutes + stayMinutes;
            int estimatedCost = seed.loadedPoi().poiBusinessInfo().avgPrice() * Math.max(updatedIntent.partySize(), 1);
            double stopScore = roundTwo(seed.baseScore() - (travelMinutes * 0.15));

            rebuiltStops.add(new GeneratedRouteStop(
                    rebuiltStops.size() + 1,
                    seed.slotRole(),
                    seed.loadedPoi().poiId(),
                    seed.loadedPoi().poiBasic().name(),
                    seed.loadedPoi().poiBasic().businessArea(),
                    seed.loadedPoi().poiBasic().district(),
                    seed.loadedPoi().poiBasic().categoryLv1(),
                    seed.loadedPoi().poiRouteProfile().indoorOutdoor(),
                    formatMinutes(arriveMinutes),
                    formatMinutes(leaveMinutes),
                    stayMinutes,
                    roundTwo(travelMinutes),
                    roundTwo(distanceKm),
                    estimatedCost,
                    stopScore,
                    seed.matchedPreferTags(),
                    seed.matchedAvoidTags()
            ));

            currentMinutes = leaveMinutes;
            totalDistanceKm += distanceKm;
            totalStopScore += stopScore;
            totalBudget += estimatedCost;
        }

        GeneratedRoutePlan rebuiltRoute = new GeneratedRoutePlan(
                baselineRoute.templateId(),
                updatedIntent.scene(),
                updatedIntent.timeWindow(),
                totalBudget,
                currentMinutes - startMinutes,
                roundTwo(totalDistanceKm),
                roundTwo(totalStopScore + (rebuiltStops.size() * 4.0) - rebuiltStops.stream().mapToDouble(GeneratedRouteStop::travelMinutesFromPrev).sum() * 0.20),
                formatMinutes(startMinutes),
                formatMinutes(currentMinutes),
                rebuiltStops,
                null
        );

        RoutePlanRequest routePlanRequest = updatedIntent.toRoutePlanRequest(null);
        RouteValidationResult validationResult = routeValidatorService.validate(rebuiltRoute, routePlanRequest, rebuiltStops.size());
        if (!validationResult.valid()) {
            return Optional.empty();
        }

        return Optional.of(new GeneratedRoutePlan(
                rebuiltRoute.templateId(),
                rebuiltRoute.scene(),
                rebuiltRoute.timeWindow(),
                rebuiltRoute.totalBudget(),
                rebuiltRoute.totalDurationMinutes(),
                rebuiltRoute.totalDistanceKm(),
                rebuiltRoute.routeScore(),
                rebuiltRoute.startTime(),
                rebuiltRoute.endTime(),
                rebuiltRoute.stops(),
                validationResult
        ));
    }

    private RouteSessionIntent mergeIntent(RouteSessionIntent currentIntent, ChangeRequest changeRequest) {
        String scene = currentIntent.scene();
        String businessArea = currentIntent.businessArea();
        String district = currentIntent.district();
        String timeWindow = currentIntent.timeWindow();
        int budgetTotal = currentIntent.budgetTotal();
        int partySize = currentIntent.partySize();
        String pace = currentIntent.pace();
        List<String> preferTags = new ArrayList<>(currentIntent.preferTags());
        List<String> avoidTags = new ArrayList<>(currentIntent.avoidTags());

        if (!changeRequest.preferTags().isEmpty()) {
            preferTags = mergeTags(preferTags, changeRequest.preferTags());
        }
        if (!changeRequest.avoidTags().isEmpty()) {
            avoidTags = mergeTags(avoidTags, changeRequest.avoidTags());
        }

        switch (changeRequest.changeType()) {
            case LOWER_BUDGET -> {
                if (changeRequest.newBudgetTotal() != null) {
                    budgetTotal = changeRequest.newBudgetTotal();
                }
            }
            case CHANGE_TIME_WINDOW -> {
                if (hasText(changeRequest.newTimeWindow())) {
                    timeWindow = changeRequest.newTimeWindow();
                }
            }
            case SWITCH_TO_INDOOR -> {
                scene = "雨天路线";
                avoidTags = mergeTags(avoidTags, List.of("outdoor"));
                preferTags = mergeTags(preferTags, List.of("室内"));
                businessArea = null;
            }
            default -> {
            }
        }

        return new RouteSessionIntent(
                scene,
                businessArea,
                district,
                timeWindow,
                budgetTotal,
                partySize,
                pace,
                preferTags,
                avoidTags
        );
    }

    private Set<Integer> resolveLockedStopOrders(RouteSessionState session, ChangeRequest changeRequest) {
        Set<Integer> lockedStopOrders = new LinkedHashSet<>(session.lockedStopOrders());
        lockedStopOrders.addAll(changeRequest.lockedStopOrders());
        return lockedStopOrders;
    }

    private String validateLockedStopChange(
            GeneratedRoutePlan currentRoute,
            Set<Integer> lockedStopOrders,
            ChangeRequest changeRequest
    ) {
        if ((changeRequest.changeType() == ChangeType.REPLACE_STOP || changeRequest.changeType() == ChangeType.REMOVE_STOP)
                && changeRequest.targetStopOrder() != null
                && lockedStopOrders.contains(changeRequest.targetStopOrder())) {
            return "Target stop is locked and cannot be changed.";
        }
        if (changeRequest.changeType() == ChangeType.REMOVE_STOP
                && changeRequest.targetStopOrder() != null
                && lockedStopOrders.stream().anyMatch(order -> order > changeRequest.targetStopOrder())) {
            return "Cannot remove a stop before another locked stop.";
        }
        if (changeRequest.changeType() == ChangeType.ADD_STOP
                && changeRequest.targetStopOrder() != null
                && lockedStopOrders.stream().anyMatch(order -> order > changeRequest.targetStopOrder())) {
            return "Cannot insert a stop before another locked stop.";
        }
        if (changeRequest.targetStopOrder() != null && !hasStopOrder(currentRoute, changeRequest.targetStopOrder())
                && (changeRequest.changeType() == ChangeType.REPLACE_STOP
                || changeRequest.changeType() == ChangeType.REMOVE_STOP
                || changeRequest.changeType() == ChangeType.LOCK_STOP
                || changeRequest.changeType() == ChangeType.UNLOCK_STOP)) {
            return "Target stop does not exist.";
        }
        return null;
    }

    private boolean preservesLockedStops(
            GeneratedRoutePlan candidate,
            GeneratedRoutePlan currentRoute,
            Set<Integer> lockedStopOrders
    ) {
        for (Integer lockedStopOrder : lockedStopOrders) {
            if (!hasStopOrder(candidate, lockedStopOrder) || !hasStopOrder(currentRoute, lockedStopOrder)) {
                return false;
            }
            if (!candidate.stops().get(lockedStopOrder - 1).poiId()
                    .equals(currentRoute.stops().get(lockedStopOrder - 1).poiId())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStopOrder(GeneratedRoutePlan routePlan, int stopOrder) {
        return stopOrder >= 1 && stopOrder <= routePlan.stops().size();
    }

    private int resolveStartMinutes(GeneratedRoutePlan baselineRoute, String timeWindow) {
        String requestedStartTime = timeWindow.split("-")[0];
        return Math.max(toMinutes(requestedStartTime), toMinutes(baselineRoute.startTime()));
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
            timePeriods.add("下午");
            timePeriods.add("晚间");
        }
        String derivedTimePeriod = derivePoiTimePeriod(timeWindow);
        if (hasText(derivedTimePeriod)) {
            timePeriods.add(derivedTimePeriod);
        }
        return List.copyOf(timePeriods);
    }

    private SlotSearchProfile slotSearchProfile(String slotRole) {
        return switch (slotRole) {
            case "晚餐主餐" -> new SlotSearchProfile("餐饮", "晚餐主餐", null, null, "indoor", List.of());
            case "午餐主餐" -> new SlotSearchProfile("餐饮", "午餐主餐", null, null, "indoor", List.of());
            case "夜景散步" -> new SlotSearchProfile("景点观光", "夜景散步", "景点观光", "散步点", "outdoor", List.of("夜景"));
            case "甜品收尾" -> new SlotSearchProfile("咖啡甜品", "甜品收尾", "咖啡甜品", "聊天点", "indoor", List.of());
            case "清吧收尾" -> new SlotSearchProfile("夜间消费", "清吧收尾", "夜间消费", "夜生活点", "indoor", List.of());
            case "聊天点" -> new SlotSearchProfile("咖啡甜品", "聊天点", "夜间消费", "夜生活点", null, List.of());
            case "夜间娱乐" -> new SlotSearchProfile("娱乐活动", "夜间娱乐", "夜间消费", "夜生活点", "indoor", List.of());
            case "景点打卡" -> new SlotSearchProfile("景点观光", "景点打卡", null, null, "outdoor", List.of());
            case "胡同散步" -> new SlotSearchProfile("景点观光", "散步点", "景点观光", "夜景散步", "outdoor", List.of("胡同"));
            case "本地小吃" -> new SlotSearchProfile("餐饮", "夜宵点", "餐饮", "午餐主餐", "indoor", List.of("本地小吃"));
            case "简餐点" -> new SlotSearchProfile("餐饮", null, "餐饮", "午餐主餐", "indoor", List.of("简餐"));
            case "室内活动" -> new SlotSearchProfile("文化艺术", "室内活动", "娱乐活动", "强体验活动", "indoor", List.of());
            case "商场餐饮" -> new SlotSearchProfile("餐饮", null, "餐饮", "晚餐主餐", "indoor", List.of("商场"));
            case "室内展览" -> new SlotSearchProfile("文化艺术", "文化体验点", "文化艺术", "文艺点", "indoor", List.of("展览"));
            case "室内娱乐" -> new SlotSearchProfile("娱乐活动", "强体验活动", "娱乐活动", "夜间娱乐", "indoor", List.of());
            case "聚会点" -> new SlotSearchProfile("娱乐活动", "聚会点", "咖啡甜品", "聊天点", "indoor", List.of());
            case "夜生活点" -> new SlotSearchProfile("夜间消费", "夜生活点", "夜间消费", "清吧收尾", "indoor", List.of());
            case "散步点" -> new SlotSearchProfile("景点观光", "散步点", "景点观光", "夜景散步", "outdoor", List.of());
            case "文艺点" -> new SlotSearchProfile("文化艺术", "文艺点", "文化艺术", "文化体验点", "indoor", List.of());
            case "文化体验点" -> new SlotSearchProfile("文化艺术", "文化体验点", "文化艺术", "文艺点", "indoor", List.of());
            case "强体验活动" -> new SlotSearchProfile("娱乐活动", "强体验活动", "娱乐活动", "夜间娱乐", "indoor", List.of());
            case "咖啡休息点", "休息点" -> new SlotSearchProfile("咖啡甜品", "咖啡休息点", "咖啡甜品", "聊天点", "indoor", List.of());
            case "夜宵点" -> new SlotSearchProfile("餐饮", "夜宵点", "夜间消费", "夜宵点", "indoor", List.of());
            default -> new SlotSearchProfile(null, slotRole, null, null, null, List.of());
        };
    }

    private String derivePoiTimePeriod(String timeWindow) {
        int startMinutes = toMinutes(timeWindow.split("-")[0]);
        if (startMinutes < 11 * 60) {
            return "上午";
        }
        if (startMinutes < 14 * 60) {
            return "中午";
        }
        if (startMinutes < 18 * 60) {
            return "下午";
        }
        if (startMinutes < 22 * 60) {
            return "晚间";
        }
        return "深夜前";
    }

    private List<String> mergeTags(List<String> first, List<String> second) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private int toMinutes(String timeText) {
        LocalTime time = LocalTime.parse(timeText, TIME_FORMATTER);
        return time.getHour() * 60 + time.getMinute();
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

    private String nextChangeId() {
        return "C" + changeSequence.incrementAndGet();
    }

    private record SlotSearchProfile(
            String categoryLv1,
            String routeRole,
            String fallbackCategoryLv1,
            String fallbackRouteRole,
            String indoorOutdoor,
            List<String> extraPreferTags
    ) {
    }

    private record RouteStopSeed(
            String slotRole,
            LoadedPoi loadedPoi,
            double baseScore,
            List<String> matchedPreferTags,
            List<String> matchedAvoidTags
    ) {
        static RouteStopSeed fromExisting(GeneratedRouteStop stop, LoadedPoi loadedPoi) {
            return new RouteStopSeed(
                    stop.slotRole(),
                    loadedPoi,
                    stop.stopScore(),
                    stop.matchedPreferTags(),
                    stop.matchedAvoidTags()
            );
        }

        static RouteStopSeed fromCandidate(String slotRole, PoiCandidate candidate, LoadedPoi loadedPoi) {
            return new RouteStopSeed(
                    slotRole,
                    loadedPoi,
                    candidate.finalScore(),
                    candidate.matchedPreferTags(),
                    candidate.matchedAvoidTags()
            );
        }
    }
}
