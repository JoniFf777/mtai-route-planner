package com.mtai.mtairouteplanner.service.route.planning;

import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.route.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOptimizerAndValidatorServiceTest {

    private final RouteOptimizerService routeOptimizerService = new RouteOptimizerService();

    @Test
    void canGenerateDatingRoute() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10001",
                "情侣约会",
                null,
                "朝阳区",
                "16:00-23:00",
                1200,
                2,
                "轻松",
                List.of("安静"),
                List.of()
        );

        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(request);

        assertThat(routes).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        GeneratedRoutePlan topRoute = routes.getFirst();
        assertThat(topRoute.scene()).isEqualTo("情侣约会");
        assertThat(topRoute.stops()).hasSizeBetween(3, 5);
        assertThat(topRoute.totalBudget()).isLessThanOrEqualTo(request.budgetTotal());
        assertThat(topRoute.totalDurationMinutes()).isPositive();
        assertThat(topRoute.totalDistanceKm()).isPositive();
        assertThat(topRoute.validationResult()).isNotNull();
        assertThat(topRoute.validationResult().valid()).isTrue();
    }

    @Test
    void canGenerateCitywalkRoute() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10003",
                "Citywalk",
                null,
                "东城区",
                "13:00-22:00",
                800,
                2,
                "适中",
                List.of("适合拍照"),
                List.of()
        );

        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(request);

        assertThat(routes).isNotEmpty();
        assertThat(routes.getFirst().scene()).isEqualTo("Citywalk");
        assertThat(routes.getFirst().stops()).hasSizeBetween(3, 5);
        assertThat(routes.getFirst().validationResult().valid()).isTrue();
    }

    @Test
    void canGenerateRainyDayIndoorRoute() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10004",
                "雨天路线",
                null,
                "朝阳区",
                "13:00-21:00",
                800,
                2,
                "轻松",
                List.of("展览"),
                List.of()
        );

        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(request);

        assertThat(routes).isNotEmpty();
        assertThat(routes.getFirst().scene()).isEqualTo("雨天路线");
        assertThat(routes.getFirst().stops()).allSatisfy(stop ->
                assertThat(stop.indoorOutdoor()).isEqualToIgnoringCase("indoor"));
    }

    @Test
    void budgetConstraintWorks() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10004",
                "雨天路线",
                null,
                "朝阳区",
                "13:00-21:00",
                650,
                2,
                "轻松",
                List.of(),
                List.of()
        );

        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(request);

        assertThat(routes).isNotEmpty();
        assertThat(routes).allSatisfy(route -> {
            assertThat(route.totalBudget()).isLessThanOrEqualTo(request.budgetTotal());
            assertThat(route.validationResult().valid()).isTrue();
        });
    }

    @Test
    void avoidTagsAffectRouteSelection() {
        RoutePlanRequest baselineRequest = new RoutePlanRequest(
                null,
                "情侣约会",
                null,
                "朝阳区",
                "16:00-23:00",
                1200,
                2,
                "轻松",
                List.of(),
                List.of()
        );
        RoutePlanRequest avoidRequest = new RoutePlanRequest(
                null,
                "情侣约会",
                null,
                "朝阳区",
                "16:00-23:00",
                1200,
                2,
                "轻松",
                List.of(),
                List.of("排队")
        );

        List<GeneratedRoutePlan> baselineRoutes = routeOptimizerService.generateRoutes(baselineRequest);
        List<GeneratedRoutePlan> avoidRoutes = routeOptimizerService.generateRoutes(avoidRequest);

        assertThat(baselineRoutes).isNotEmpty();
        assertThat(avoidRoutes).isNotEmpty();

        List<String> baselineSequence = poiSequence(baselineRoutes.getFirst());
        List<String> avoidSequence = poiSequence(avoidRoutes.getFirst());
        int avoidHits = totalAvoidHits(avoidRoutes.getFirst());

        assertThat(
                !baselineSequence.equals(avoidSequence)
                        || avoidHits == 0
                        || avoidRoutes.getFirst().routeScore() < baselineRoutes.getFirst().routeScore()
        ).isTrue();
    }

    @Test
    void generatedRoutesDoNotContainDuplicateStopsAndIncludeTravelEstimates() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10001",
                "情侣约会",
                null,
                "朝阳区",
                "16:00-23:00",
                1200,
                2,
                "轻松",
                List.of("安静"),
                List.of()
        );

        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(request);

        assertThat(routes).isNotEmpty();
        assertThat(routes).allSatisfy(route -> {
            assertThat(route.stops()).extracting(GeneratedRouteStop::poiId).doesNotHaveDuplicates();
            assertThat(route.startTime()).isEqualTo("16:00");
            assertThat(route.endTime()).matches("\\d{2}:\\d{2}");
            assertThat(route.totalDistanceKm()).isPositive();
            assertThat(route.totalDurationMinutes()).isPositive();
            assertThat(route.validationResult().valid()).isTrue();
            assertThat(route.stops()).allSatisfy(stop -> {
                assertThat(stop.arriveTime()).matches("\\d{2}:\\d{2}");
                assertThat(stop.leaveTime()).matches("\\d{2}:\\d{2}");
                assertThat(stop.stayMinutes()).isPositive();
                assertThat(stop.estimatedCost()).isGreaterThanOrEqualTo(0);
            });
            assertThat(route.stops().getFirst().travelMinutesFromPrev()).isZero();
            assertThat(route.stops().getFirst().distanceKmFromPrev()).isZero();
            assertThat(route.stops().stream().skip(1).toList()).allSatisfy(stop -> {
                assertThat(stop.travelMinutesFromPrev()).isPositive();
                assertThat(stop.distanceKmFromPrev()).isPositive();
            });
        });
    }

    @Test
    void returnsSafeEmptyResultIfNoFeasibleRoute() {
        RoutePlanRequest request = new RoutePlanRequest(
                null,
                "情侣约会",
                "三里屯",
                null,
                "18:00-19:00",
                60,
                2,
                "轻松",
                List.of(),
                List.of("排队", "夜景")
        );

        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(request);

        assertThat(routes).isEmpty();
    }

    private List<String> poiSequence(GeneratedRoutePlan routePlan) {
        return routePlan.stops().stream()
                .map(GeneratedRouteStop::poiId)
                .toList();
    }

    private int totalAvoidHits(GeneratedRoutePlan routePlan) {
        return routePlan.stops().stream()
                .mapToInt(stop -> stop.matchedAvoidTags().size())
                .sum();
    }
}
