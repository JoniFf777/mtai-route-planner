package com.mtai.mtairouteplanner.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class Phase2StaticMockDataGeneratorTest {

    private static final Set<String> SUPPORTED_DISTRICTS = Set.of("朝阳区", "东城区", "西城区", "海淀区");
    private static final Set<String> SUPPORTED_SCENES = Set.of(
            "情侣约会",
            "朋友聚会",
            "Citywalk",
            "游客半日游",
            "低预算学生路线",
            "亲子室内路线",
            "雨天路线",
            "夜游路线",
            "独处放松路线"
    );
    private static final Set<String> VALID_NAME_PREFIXES = Set.of("餐饮", "咖啡甜品", "文化艺术", "娱乐活动", "景点观光", "夜间消费");
    private static final List<String> BRAND_BLACKLIST = List.of("海底捞", "星巴克", "瑞幸", "喜茶", "奈雪", "麦当劳", "肯德基", "必胜客", "盒马", "优衣库");
    private static final Pattern SYNTHETIC_NAME_PATTERN = Pattern.compile("^(餐饮|咖啡甜品|文化艺术|娱乐活动|景点观光|夜间消费)[A-Z]{1,2}·.+$");
    private static final List<String> ALL_RESOURCE_FILES = List.of(
            "business_areas.json",
            "traffic_matrix.json",
            "route_templates.json",
            "slot_transition_rules.json",
            "poi_basic.jsonl",
            "poi_rating_stats.jsonl",
            "poi_business_info.jsonl",
            "poi_ugc_summary.jsonl",
            "poi_route_profile.jsonl",
            "poi_tags.jsonl",
            "poi_embedding_docs.jsonl",
            "user_profiles.jsonl",
            "user_preference_tags.jsonl",
            "user_behavior_events.jsonl",
            "demo_user_cases.json",
            "poi_joined_view.jsonl"
    );

    private final Phase2StaticMockDataGenerator generator = new Phase2StaticMockDataGenerator();

    @TempDir
    Path tempDir;

    @Test
    void generatedFilesMatchCheckedInResourcesExactly() throws IOException {
        generator.writeTo(tempDir);

        for (String fileName : ALL_RESOURCE_FILES) {
            assertThat(Files.readString(tempDir.resolve(fileName), StandardCharsets.UTF_8))
                    .isEqualTo(readResource("mock-data/" + fileName));
        }
    }

    @Test
    void generatedDataFollowsPhaseTwoConstraints() {
        Phase2StaticMockDataGenerator.GeneratedData data = generator.generate();

        assertThat(data.businessAreas()).hasSize(20);
        assertThat(data.trafficMatrix()).hasSizeBetween(100, 200);
        assertThat(data.routeTemplates()).hasSize(20);
        assertThat(data.slotTransitionRules()).hasSize(20);
        assertThat(data.poiBasic()).hasSize(200);
        assertThat(data.poiRatingStats()).hasSize(200);
        assertThat(data.poiBusinessInfo()).hasSize(200);
        assertThat(data.poiUgcSummaries()).hasSize(200);
        assertThat(data.poiRouteProfiles()).hasSize(200);
        assertThat(data.poiEmbeddingDocs()).hasSize(200);
        assertThat(data.userProfiles()).hasSize(20);
        assertThat(data.userPreferenceTags()).hasSize(100);
        assertThat(data.demoUserCases()).hasSize(30);
        assertThat(data.poiJoinedViews()).hasSize(200);

        assertThat(data.businessAreas())
                .extracting(Phase2StaticMockDataGenerator.BusinessArea::city)
                .containsOnly("北京");
        assertThat(data.businessAreas())
                .extracting(Phase2StaticMockDataGenerator.BusinessArea::district)
                .allMatch(SUPPORTED_DISTRICTS::contains);
        assertThat(data.businessAreas())
                .extracting(Phase2StaticMockDataGenerator.BusinessArea::coordinateSystem)
                .containsOnly("GCJ-02");

        Map<String, Phase2StaticMockDataGenerator.BusinessArea> areasByName = data.businessAreas().stream()
                .collect(Collectors.toMap(
                        Phase2StaticMockDataGenerator.BusinessArea::areaName,
                        Function.identity()
                ));

        Set<String> poiIds = data.poiBasic().stream()
                .map(Phase2StaticMockDataGenerator.PoiBasic::poiId)
                .collect(Collectors.toSet());
        assertThat(poiIds).hasSize(data.poiBasic().size());

        assertThat(data.poiBasic()).allSatisfy(poi -> {
            assertThat(poi.city()).isEqualTo("北京");
            assertThat(poi.coordinateSystem()).isEqualTo("GCJ-02");
            assertThat(poi.businessArea()).isIn(areasByName.keySet());
            assertThat(poi.district()).isEqualTo(areasByName.get(poi.businessArea()).district());
            assertThat(SYNTHETIC_NAME_PATTERN.matcher(poi.name()).matches()).isTrue();
            assertThat(VALID_NAME_PREFIXES).anyMatch(poi.name()::startsWith);
            assertThat(BRAND_BLACKLIST).noneMatch(poi.name()::contains);

            Phase2StaticMockDataGenerator.BusinessArea area = areasByName.get(poi.businessArea());
            assertThat(haversineKm(poi.lat(), poi.lng(), area.centerLat(), area.centerLng())).isLessThanOrEqualTo(1.2);
        });

        assertMatchingPoiIdSet(data.poiRatingStats().stream().map(Phase2StaticMockDataGenerator.PoiRatingStats::poiId).collect(Collectors.toSet()), poiIds);
        assertMatchingPoiIdSet(data.poiBusinessInfo().stream().map(Phase2StaticMockDataGenerator.PoiBusinessInfo::poiId).collect(Collectors.toSet()), poiIds);
        assertMatchingPoiIdSet(data.poiUgcSummaries().stream().map(Phase2StaticMockDataGenerator.PoiUgcSummary::poiId).collect(Collectors.toSet()), poiIds);
        assertMatchingPoiIdSet(data.poiRouteProfiles().stream().map(Phase2StaticMockDataGenerator.PoiRouteProfile::poiId).collect(Collectors.toSet()), poiIds);
        assertMatchingPoiIdSet(data.poiEmbeddingDocs().stream().map(Phase2StaticMockDataGenerator.PoiEmbeddingDoc::poiId).collect(Collectors.toSet()), poiIds);
        assertMatchingPoiIdSet(data.poiJoinedViews().stream().map(Phase2StaticMockDataGenerator.PoiJoinedView::poiId).collect(Collectors.toSet()), poiIds);

        Map<String, List<Phase2StaticMockDataGenerator.PoiTag>> tagsByPoiId = data.poiTags().stream()
                .collect(Collectors.groupingBy(
                        Phase2StaticMockDataGenerator.PoiTag::poiId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        assertThat(tagsByPoiId.keySet()).isEqualTo(poiIds);
        assertThat(tagsByPoiId.values()).allSatisfy(tags -> assertThat(tags).isNotEmpty());

        assertThat(data.poiJoinedViews()).allSatisfy(joinedView -> {
            assertThat(joinedView.poiBasic()).isNotNull();
            assertThat(joinedView.poiRatingStats()).isNotNull();
            assertThat(joinedView.poiBusinessInfo()).isNotNull();
            assertThat(joinedView.poiUgcSummary()).isNotNull();
            assertThat(joinedView.poiRouteProfile()).isNotNull();
            assertThat(joinedView.poiEmbeddingDoc()).isNotNull();
            assertThat(joinedView.poiTags()).isNotEmpty();
        });

        assertThat(data.routeTemplates()).allSatisfy(template -> {
            assertThat(template.scene()).isIn(SUPPORTED_SCENES);
            assertThat(template.minDurationMinutes()).isPositive();
            assertThat(template.maxDurationMinutes()).isGreaterThan(template.minDurationMinutes());
            assertThat(template.suitableDistricts()).allMatch(SUPPORTED_DISTRICTS::contains);
        });

        assertThat(data.userProfiles())
                .extracting(Phase2StaticMockDataGenerator.UserProfile::userId)
                .doesNotHaveDuplicates();
        assertThat(data.userPreferenceTags())
                .extracting(Phase2StaticMockDataGenerator.UserPreferenceTag::userId)
                .allMatch(userId -> data.userProfiles().stream().anyMatch(user -> user.userId().equals(userId)));
        assertThat(data.userBehaviorEvents())
                .allSatisfy(event -> {
                    assertThat(event.userId()).isIn(data.userProfiles().stream().map(Phase2StaticMockDataGenerator.UserProfile::userId).toList());
                    assertThat(event.poiId()).isIn(poiIds);
                });
        assertThat(data.demoUserCases()).allSatisfy(userCase -> {
            assertThat(userCase.city()).isEqualTo("北京");
            assertThat(userCase.userId()).isIn(data.userProfiles().stream().map(Phase2StaticMockDataGenerator.UserProfile::userId).toList());
            assertThat(userCase.businessArea()).isIn(areasByName.keySet());
            assertThat(userCase.district()).isEqualTo(areasByName.get(userCase.businessArea()).district());
            assertThat(userCase.expectedScene()).isIn(SUPPORTED_SCENES);
            assertThat(userCase.preferTags()).isNotEmpty();
            assertThat(userCase.avoidTags()).isNotEmpty();
        });
    }

    private void assertMatchingPoiIdSet(Set<String> actual, Set<String> expected) {
        assertThat(actual).isEqualTo(expected);
    }

    private String readResource(String resourcePath) throws IOException {
        return new String(new ClassPathResource(resourcePath).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private double haversineKm(double startLat, double startLng, double endLat, double endLng) {
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(endLat - startLat);
        double lngDistance = Math.toRadians(endLng - startLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
