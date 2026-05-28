package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.PoiCandidate;
import com.mtai.mtairouteplanner.model.PoiRetrievalResult;
import com.mtai.mtairouteplanner.model.PoiSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoiRetrievalServiceTest {

    private final PoiRetrievalService poiRetrievalService = new PoiRetrievalService();

    @Test
    void canRetrieveDinnerCandidates() {
        PoiSearchRequest request = new PoiSearchRequest(
                null,
                "三里屯",
                null,
                "餐饮",
                "晚餐主餐",
                "情侣约会",
                "晚间",
                80,
                600,
                "indoor",
                List.of(),
                List.of(),
                5,
                false
        );

        PoiRetrievalResult result = poiRetrievalService.retrieveCandidates(request);

        assertThat(result.totalMatchedCount()).isGreaterThan(0);
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.businessArea()).isEqualTo("三里屯");
            assertThat(candidate.categoryLv1()).isEqualTo("餐饮");
            assertThat(candidate.routeRoles()).contains("晚餐主餐");
            assertThat(candidate.suitableTimePeriods()).contains("晚间");
            assertThat(candidate.avgPrice()).isBetween(80, 600);
            assertThat(candidate.indoorOutdoor()).isEqualTo("indoor");
        });
    }

    @Test
    void canRetrieveCoffeeDessertCandidates() {
        PoiSearchRequest request = new PoiSearchRequest(
                null,
                "五道口",
                null,
                "咖啡甜品",
                "甜品收尾",
                null,
                "下午",
                30,
                120,
                "indoor",
                List.of(),
                List.of(),
                5,
                false
        );

        PoiRetrievalResult result = poiRetrievalService.retrieveCandidates(request);

        assertThat(result.totalMatchedCount()).isGreaterThan(0);
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.categoryLv1()).isEqualTo("咖啡甜品");
            assertThat(candidate.routeRoles()).contains("甜品收尾");
            assertThat(candidate.suitableTimePeriods()).contains("下午");
            assertThat(candidate.avgPrice()).isBetween(30, 120);
        });
    }

    @Test
    void budgetFilterWorks() {
        PoiSearchRequest request = new PoiSearchRequest(
                null,
                "五道口",
                null,
                "餐饮",
                null,
                null,
                null,
                20,
                60,
                null,
                List.of(),
                List.of(),
                20,
                false
        );

        PoiRetrievalResult result = poiRetrievalService.retrieveCandidates(request);

        assertThat(result.totalMatchedCount()).isGreaterThan(0);
        assertThat(result.candidates()).allSatisfy(candidate ->
                assertThat(candidate.avgPrice()).isBetween(20, 60));
    }

    @Test
    void avoidTagsReduceCandidateScore() {
        PoiSearchRequest baselineRequest = new PoiSearchRequest(
                null,
                "三里屯",
                null,
                "咖啡甜品",
                null,
                null,
                null,
                30,
                120,
                null,
                List.of(),
                List.of(),
                10,
                false
        );
        PoiSearchRequest avoidRequest = new PoiSearchRequest(
                null,
                "三里屯",
                null,
                "咖啡甜品",
                null,
                null,
                null,
                30,
                120,
                null,
                List.of("排队"),
                List.of(),
                10,
                false
        );

        PoiRetrievalResult baseline = poiRetrievalService.retrieveCandidates(baselineRequest);
        PoiRetrievalResult withAvoid = poiRetrievalService.retrieveCandidates(avoidRequest);
        PoiCandidate comparedCandidate = withAvoid.candidates().stream()
                .filter(candidate -> !candidate.matchedAvoidTags().isEmpty())
                .findFirst()
                .orElseThrow();
        PoiCandidate baselineCandidate = findCandidateByPoiId(baseline, comparedCandidate.poiId());

        assertThat(comparedCandidate.avoidTagPenalty()).isGreaterThan(0.0);
        assertThat(comparedCandidate.finalScore()).isLessThan(baselineCandidate.finalScore());
    }

    @Test
    void preferTagsImproveRankingScore() {
        PoiSearchRequest baselineRequest = new PoiSearchRequest(
                null,
                "三里屯",
                null,
                "咖啡甜品",
                null,
                null,
                null,
                30,
                120,
                null,
                List.of(),
                List.of(),
                10,
                false
        );
        PoiSearchRequest preferRequest = new PoiSearchRequest(
                null,
                "三里屯",
                null,
                "咖啡甜品",
                null,
                null,
                null,
                30,
                120,
                null,
                List.of(),
                List.of("适合拍照"),
                10,
                false
        );

        PoiRetrievalResult baseline = poiRetrievalService.retrieveCandidates(baselineRequest);
        PoiRetrievalResult withPrefer = poiRetrievalService.retrieveCandidates(preferRequest);
        PoiCandidate comparedCandidate = withPrefer.candidates().stream()
                .filter(candidate -> !candidate.matchedPreferTags().isEmpty())
                .findFirst()
                .orElseThrow();
        PoiCandidate baselineCandidate = findCandidateByPoiId(baseline, comparedCandidate.poiId());

        assertThat(comparedCandidate.preferTagBonus()).isGreaterThan(0.0);
        assertThat(comparedCandidate.finalScore()).isGreaterThan(baselineCandidate.finalScore());
    }

    @Test
    void userPreferenceTagsAffectScore() {
        PoiSearchRequest baselineRequest = new PoiSearchRequest(
                null,
                "三里屯",
                null,
                "餐饮",
                "晚餐主餐",
                "情侣约会",
                "晚间",
                80,
                600,
                null,
                List.of(),
                List.of(),
                10,
                false
        );
        PoiSearchRequest userPreferenceRequest = new PoiSearchRequest(
                "U10001",
                "三里屯",
                null,
                "餐饮",
                "晚餐主餐",
                "情侣约会",
                "晚间",
                80,
                600,
                null,
                List.of(),
                List.of(),
                10,
                false
        );

        PoiRetrievalResult baseline = poiRetrievalService.retrieveCandidates(baselineRequest);
        PoiRetrievalResult withUserPreference = poiRetrievalService.retrieveCandidates(userPreferenceRequest);
        PoiCandidate comparedCandidate = withUserPreference.candidates().stream()
                .filter(candidate -> !candidate.matchedUserPreferenceTags().isEmpty())
                .findFirst()
                .orElseThrow();
        PoiCandidate baselineCandidate = findCandidateByPoiId(baseline, comparedCandidate.poiId());

        assertThat(comparedCandidate.longTermPreferenceBonus()).isGreaterThan(0.0);
        assertThat(comparedCandidate.finalScore()).isGreaterThan(baselineCandidate.finalScore());
    }

    @Test
    void missingFiltersReturnSafeEmptyResults() {
        PoiRetrievalResult emptyRequestResult = poiRetrievalService.retrieveCandidates(new PoiSearchRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                10,
                false
        ));
        PoiRetrievalResult missingAreaResult = poiRetrievalService.retrieveCandidates(new PoiSearchRequest(
                null,
                "不存在商圈",
                null,
                "餐饮",
                "晚餐主餐",
                null,
                null,
                80,
                200,
                null,
                List.of(),
                List.of(),
                10,
                false
        ));

        assertThat(emptyRequestResult.totalMatchedCount()).isZero();
        assertThat(emptyRequestResult.candidates()).isEmpty();
        assertThat(missingAreaResult.totalMatchedCount()).isZero();
        assertThat(missingAreaResult.candidates()).isEmpty();
    }

    private PoiCandidate findCandidateByPoiId(PoiRetrievalResult result, String poiId) {
        return result.candidates().stream()
                .filter(candidate -> candidate.poiId().equals(poiId))
                .findFirst()
                .orElseThrow();
    }
}
