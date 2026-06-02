package com.mtai.mtairouteplanner.service.route.planning;

import com.mtai.mtairouteplanner.data.model.LoadedPoi;
import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import com.mtai.mtairouteplanner.data.index.MockDataIndexes;
import com.mtai.mtairouteplanner.data.loader.MockDataLoader;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.route.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.route.RouteValidationIssue;
import com.mtai.mtairouteplanner.model.route.RouteValidationResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RouteValidatorService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final MockDataBundle mockDataBundle;
    private final MockDataIndexes indexes;

    public RouteValidatorService() {
        this(new MockDataLoader());
    }

    public RouteValidatorService(MockDataLoader mockDataLoader) {
        this.mockDataBundle = mockDataLoader.load();
        this.indexes = MockDataIndexes.from(mockDataBundle, mockDataLoader.assembleLoadedPois(mockDataBundle));
    }

    public RouteValidationResult validate(
            GeneratedRoutePlan routePlan,
            RoutePlanRequest routePlanRequest,
            int expectedSlotCount
    ) {
        List<RouteValidationIssue> issues = new ArrayList<>();

        if (routePlan.totalBudget() > routePlanRequest.budgetTotal()) {
            issues.add(new RouteValidationIssue("BUDGET_EXCEEDED", "Total budget exceeds request budget.", null));
        }

        TimeWindow timeWindow = parseTimeWindow(routePlanRequest.timeWindow());
        if (toMinutes(routePlan.startTime()) < timeWindow.startMinutes()
                || toMinutes(routePlan.endTime()) > timeWindow.endMinutes()) {
            issues.add(new RouteValidationIssue("TIME_WINDOW_EXCEEDED", "Route exceeds requested time window.", null));
        }

        if (routePlan.stops().size() != expectedSlotCount) {
            issues.add(new RouteValidationIssue("MISSING_SLOT", "Required route slots are not fully filled.", null));
        }

        Set<String> poiIds = new HashSet<>();
        int avoidHitCount = 0;
        for (GeneratedRouteStop stop : routePlan.stops()) {
            if (stop.poiId() == null || stop.poiId().isBlank() || stop.slotRole() == null || stop.slotRole().isBlank()) {
                issues.add(new RouteValidationIssue("MISSING_SLOT", "A route stop is missing required slot or POI.", stop.stopOrder()));
            }

            if (!poiIds.add(stop.poiId())) {
                issues.add(new RouteValidationIssue("DUPLICATE_POI", "Duplicate POI detected in route.", stop.stopOrder()));
            }

            if (!isBusinessHoursRespected(stop)) {
                issues.add(new RouteValidationIssue("BUSINESS_HOURS_VIOLATION", "Stop is outside business hours.", stop.stopOrder()));
            }

            if (!stop.matchedAvoidTags().isEmpty()) {
                avoidHitCount += stop.matchedAvoidTags().size();
                if (stop.matchedAvoidTags().size() >= 2) {
                    issues.add(new RouteValidationIssue("AVOID_TAG_HIT", "Stop strongly hits avoid tags.", stop.stopOrder()));
                }
            }
        }

        if (avoidHitCount >= 3) {
            issues.add(new RouteValidationIssue("AVOID_TAG_HIT", "Route strongly hits avoid tags overall.", null));
        }

        double totalTravelMinutes = routePlan.stops().stream()
                .mapToDouble(GeneratedRouteStop::travelMinutesFromPrev)
                .sum();
        double maxTravelMinutes = switch (routePlanRequest.pace()) {
            case "杞绘澗" -> 60.0;
            case "閫備腑" -> 90.0;
            case "绱у噾" -> 120.0;
            default -> 90.0;
        };
        if (totalTravelMinutes > maxTravelMinutes) {
            issues.add(new RouteValidationIssue("PACE_TOO_TIGHT", "Route travel load is too long for selected pace.", null));
        }

        return new RouteValidationResult(issues.isEmpty(), issues);
    }

    private boolean isBusinessHoursRespected(GeneratedRouteStop stop) {
        Optional<LoadedPoi> loadedPoi = indexes.poiIndex().findByPoiId(stop.poiId());
        if (loadedPoi.isEmpty()) {
            return false;
        }

        String businessHours = loadedPoi.get().poiBusinessInfo().businessHours();
        int arriveMinutes = toMinutes(stop.arriveTime());
        int leaveMinutes = toMinutes(stop.leaveTime());

        for (String period : businessHours.split(",")) {
            String trimmedPeriod = period.trim();
            if (trimmedPeriod.isBlank()) {
                continue;
            }
            String[] parts = trimmedPeriod.split("-");
            if (parts.length != 2) {
                continue;
            }
            int openMinutes = toMinutes(parts[0]);
            int closeMinutes = toMinutes(parts[1]);
            if (arriveMinutes >= openMinutes && leaveMinutes <= closeMinutes) {
                return true;
            }
        }
        return false;
    }

    private TimeWindow parseTimeWindow(String timeWindow) {
        String[] parts = timeWindow.split("-");
        return new TimeWindow(toMinutes(parts[0]), toMinutes(parts[1]));
    }

    private int toMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        if (parts.length != 2) {
            LocalTime localTime = LocalTime.parse(hhmm, TIME_FORMATTER);
            return localTime.getHour() * 60 + localTime.getMinute();
        }
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private record TimeWindow(int startMinutes, int endMinutes) {
    }
}


