package com.mtai.mtairouteplanner.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Phase2StaticMockDataGenerator generator = new Phase2StaticMockDataGenerator();

    @TempDir
    Path tempDir;

    @Test
    void generatedFilesMatchCheckedInResources() throws IOException {
        generator.writeTo(tempDir);

        assertJsonMatchesResource("business_areas.json");
        assertJsonMatchesResource("traffic_matrix.json");
        assertJsonMatchesResource("route_templates.json");
        assertJsonMatchesResource("slot_transition_rules.json");
    }

    @Test
    void generatedDataFollowsPhaseTwoStepOneConstraints() {
        Phase2StaticMockDataGenerator.GeneratedData data = generator.generate();

        assertThat(data.businessAreas()).hasSize(20);
        assertThat(data.trafficMatrix()).hasSizeBetween(100, 200);
        assertThat(data.routeTemplates()).hasSize(20);
        assertThat(data.slotTransitionRules()).hasSize(20);

        assertThat(data.businessAreas())
                .extracting(Phase2StaticMockDataGenerator.BusinessArea::city)
                .containsOnly("北京");
        assertThat(data.businessAreas())
                .extracting(Phase2StaticMockDataGenerator.BusinessArea::district)
                .allMatch(SUPPORTED_DISTRICTS::contains);
        assertThat(data.businessAreas())
                .extracting(Phase2StaticMockDataGenerator.BusinessArea::coordinateSystem)
                .containsOnly("GCJ-02");
        assertThat(data.businessAreas())
                .extracting(Phase2StaticMockDataGenerator.BusinessArea::areaName)
                .doesNotHaveDuplicates();

        Map<String, Phase2StaticMockDataGenerator.BusinessArea> areasByName = data.businessAreas().stream()
                .collect(Collectors.toMap(
                        Phase2StaticMockDataGenerator.BusinessArea::areaName,
                        Function.identity()
                ));

        assertThat(data.trafficMatrix()).allSatisfy(entry -> {
            assertThat(entry.fromArea()).isIn(areasByName.keySet());
            assertThat(entry.toArea()).isIn(areasByName.keySet());
            assertThat(entry.fromArea()).isNotEqualTo(entry.toArea());
            assertThat(entry.transportMode()).isEqualTo("taxi");
            assertThat(entry.distanceKm()).isPositive();
            assertThat(entry.estimatedMinutes()).isGreaterThanOrEqualTo(10.0);
        });

        Set<String> trafficKeys = data.trafficMatrix().stream()
                .map(entry -> entry.fromArea() + "->" + entry.toArea())
                .collect(Collectors.toSet());
        assertThat(trafficKeys).hasSize(data.trafficMatrix().size());
        assertThat(data.trafficMatrix()).allSatisfy(entry ->
                assertThat(trafficKeys).contains(entry.toArea() + "->" + entry.fromArea()));

        Set<String> allSlotNames = data.routeTemplates().stream()
                .flatMap(template -> template.slotSequence().stream())
                .collect(Collectors.toSet());

        assertThat(data.routeTemplates()).allSatisfy(template -> {
            assertThat(template.minDurationMinutes()).isPositive();
            assertThat(template.maxDurationMinutes()).isGreaterThan(template.minDurationMinutes());
            assertThat(template.scene()).isIn(SUPPORTED_SCENES);
            assertThat(template.slotSequence()).isNotEmpty();
            assertThat(template.suitableDistricts()).isNotEmpty();
            assertThat(template.suitableDistricts()).allMatch(SUPPORTED_DISTRICTS::contains);
        });

        Set<String> templateIds = data.routeTemplates().stream()
                .map(Phase2StaticMockDataGenerator.RouteTemplate::templateId)
                .collect(Collectors.toSet());
        assertThat(templateIds).hasSize(data.routeTemplates().size());

        Set<String> transitionPairs = data.slotTransitionRules().stream()
                .map(rule -> rule.fromSlot() + "->" + rule.toSlot())
                .collect(Collectors.toSet());
        assertThat(transitionPairs).hasSize(data.slotTransitionRules().size());
        assertThat(data.slotTransitionRules()).allSatisfy(rule -> {
            assertThat(rule.fromSlot()).isIn(allSlotNames);
            assertThat(rule.toSlot()).isIn(allSlotNames);
            assertThat(rule.weight()).isBetween(0.0, 1.0);
            assertThat(rule.reason()).isNotBlank();
        });
    }

    private void assertJsonMatchesResource(String fileName) throws IOException {
        JsonNode generated = objectMapper.readTree(Files.readString(tempDir.resolve(fileName)));
        JsonNode resource = objectMapper.readTree(new ClassPathResource("mock-data/" + fileName).getInputStream());
        assertThat(generated).isEqualTo(resource);
    }
}
