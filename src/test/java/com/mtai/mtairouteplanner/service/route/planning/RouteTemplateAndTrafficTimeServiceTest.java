package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.RouteTemplateCandidate;
import com.mtai.mtairouteplanner.model.RouteTemplateMatchRequest;
import com.mtai.mtairouteplanner.model.TravelEstimate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTemplateAndTrafficTimeServiceTest {

    private final RouteTemplateService routeTemplateService = new RouteTemplateService();
    private final TrafficTimeService trafficTimeService = new TrafficTimeService();

    @Test
    void canMatchDatingRouteTemplates() {
        RouteTemplateMatchRequest request = new RouteTemplateMatchRequest(
                "情侣约会",
                "晚间",
                "中",
                "轻松",
                "朝阳区",
                210,
                5
        );

        List<RouteTemplateCandidate> candidates = routeTemplateService.findCandidateTemplates(request);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.getFirst().templateId()).isEqualTo("RT001");
        assertThat(candidates.getFirst().scene()).isEqualTo("情侣约会");
    }

    @Test
    void canMatchCitywalkRouteTemplates() {
        RouteTemplateMatchRequest request = new RouteTemplateMatchRequest(
                "Citywalk",
                "白天",
                "低",
                "适中",
                "东城区",
                240,
                5
        );

        List<RouteTemplateCandidate> candidates = routeTemplateService.findCandidateTemplates(request);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.getFirst().templateId()).isEqualTo("RT005");
        assertThat(candidates.getFirst().scene()).isEqualTo("Citywalk");
    }

    @Test
    void budgetAndTimeFiltersAffectTemplateRanking() {
        RouteTemplateMatchRequest budgetFocusedRequest = new RouteTemplateMatchRequest(
                "情侣约会",
                "晚间",
                "中",
                "轻松",
                "朝阳区",
                210,
                5
        );
        RouteTemplateMatchRequest premiumRequest = new RouteTemplateMatchRequest(
                "情侣约会",
                "下午到晚间",
                "高",
                "适中",
                "朝阳区",
                300,
                5
        );

        List<RouteTemplateCandidate> budgetFocused = routeTemplateService.findCandidateTemplates(budgetFocusedRequest);
        List<RouteTemplateCandidate> premium = routeTemplateService.findCandidateTemplates(premiumRequest);

        assertThat(budgetFocused.getFirst().templateId()).isEqualTo("RT001");
        assertThat(premium.getFirst().templateId()).isEqualTo("RT002");
        assertThat(premium.getFirst().budgetMatchScore()).isGreaterThan(0.0);
        assertThat(premium.getFirst().timeMatchScore()).isGreaterThan(0.0);
    }

    @Test
    void sameAreaTravelUsesHaversineWalkingEstimate() {
        Optional<TravelEstimate> travelEstimate = trafficTimeService.estimateTravelTime("P00001", "P00002");

        assertThat(travelEstimate).isPresent();
        assertThat(travelEstimate.get().fromBusinessArea()).isEqualTo("三里屯");
        assertThat(travelEstimate.get().toBusinessArea()).isEqualTo("三里屯");
        assertThat(travelEstimate.get().estimateSource()).isEqualTo("SAME_AREA_HAVERSINE_WALKING");
        assertThat(travelEstimate.get().transportMode()).isEqualTo("walking");
        assertThat(travelEstimate.get().distanceKm()).isPositive();
        assertThat(travelEstimate.get().estimatedMinutes()).isPositive();
    }

    @Test
    void crossAreaTravelUsesTrafficMatrixWhenAvailable() {
        Optional<TravelEstimate> travelEstimate = trafficTimeService.estimateTravelTime("P00001", "P00011");

        assertThat(travelEstimate).isPresent();
        assertThat(travelEstimate.get().fromBusinessArea()).isEqualTo("三里屯");
        assertThat(travelEstimate.get().toBusinessArea()).isEqualTo("国贸");
        assertThat(travelEstimate.get().estimateSource()).isEqualTo("TRAFFIC_MATRIX");
        assertThat(travelEstimate.get().transportMode()).isEqualTo("taxi");
        assertThat(travelEstimate.get().distanceKm()).isPositive();
        assertThat(travelEstimate.get().estimatedMinutes()).isPositive();
    }

    @Test
    void missingMatrixEntryUsesFallback() {
        Optional<TravelEstimate> travelEstimate = trafficTimeService.estimateTravelTime("P00001", "P00061");

        assertThat(travelEstimate).isPresent();
        assertThat(travelEstimate.get().fromBusinessArea()).isEqualTo("三里屯");
        assertThat(travelEstimate.get().toBusinessArea()).isEqualTo("王府井");
        assertThat(travelEstimate.get().estimateSource()).isEqualTo("CROSS_AREA_HAVERSINE_FALLBACK");
        assertThat(travelEstimate.get().transportMode()).isEqualTo("taxi");
        assertThat(travelEstimate.get().distanceKm()).isPositive();
        assertThat(travelEstimate.get().estimatedMinutes()).isPositive();
    }

    @Test
    void missingPoiReturnsSafeEmptyResult() {
        Optional<TravelEstimate> travelEstimate = trafficTimeService.estimateTravelTime("P99999", "P00001");

        assertThat(travelEstimate).isEmpty();
    }
}
