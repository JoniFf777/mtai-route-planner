package com.mtai.mtairouteplanner.data;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.BusinessArea;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.DemoUserCase;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiBasic;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiBusinessInfo;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiEmbeddingDoc;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiRatingStats;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiRouteProfile;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiTag;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiUgcSummary;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.RouteTemplate;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.SlotTransitionRule;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.TrafficMatrixEntry;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.UserBehaviorEvent;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.UserPreferenceTag;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.UserProfile;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ClasspathMockDataSourceReader implements MockDataSourceReader {

    private static final String RESOURCE_ROOT = "mock-data/";
    private static final String BUSINESS_AREAS_FILE = "business_areas.json";
    private static final String TRAFFIC_MATRIX_FILE = "traffic_matrix.json";
    private static final String POI_BASIC_FILE = "poi_basic.jsonl";
    private static final String POI_RATING_STATS_FILE = "poi_rating_stats.jsonl";
    private static final String POI_BUSINESS_INFO_FILE = "poi_business_info.jsonl";
    private static final String POI_UGC_SUMMARY_FILE = "poi_ugc_summary.jsonl";
    private static final String POI_ROUTE_PROFILE_FILE = "poi_route_profile.jsonl";
    private static final String POI_TAGS_FILE = "poi_tags.jsonl";
    private static final String POI_EMBEDDING_DOCS_FILE = "poi_embedding_docs.jsonl";
    private static final String USER_PROFILES_FILE = "user_profiles.jsonl";
    private static final String USER_PREFERENCE_TAGS_FILE = "user_preference_tags.jsonl";
    private static final String USER_BEHAVIOR_EVENTS_FILE = "user_behavior_events.jsonl";
    private static final String ROUTE_TEMPLATES_FILE = "route_templates.json";
    private static final String SLOT_TRANSITION_RULES_FILE = "slot_transition_rules.json";
    private static final String DEMO_USER_CASES_FILE = "demo_user_cases.json";

    private final ObjectMapper objectMapper;

    public ClasspathMockDataSourceReader() {
        this(MockDataObjectMapperFactory.create());
    }

    public ClasspathMockDataSourceReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MockDataBundle load() {
        try {
            return new MockDataBundle(
                    readJsonArray(BUSINESS_AREAS_FILE, BusinessArea.class),
                    readJsonArray(TRAFFIC_MATRIX_FILE, TrafficMatrixEntry.class),
                    readJsonLines(POI_BASIC_FILE, PoiBasic.class),
                    readJsonLines(POI_RATING_STATS_FILE, PoiRatingStats.class),
                    readJsonLines(POI_BUSINESS_INFO_FILE, PoiBusinessInfo.class),
                    readJsonLines(POI_UGC_SUMMARY_FILE, PoiUgcSummary.class),
                    readJsonLines(POI_ROUTE_PROFILE_FILE, PoiRouteProfile.class),
                    readJsonLines(POI_TAGS_FILE, PoiTag.class),
                    readJsonLines(POI_EMBEDDING_DOCS_FILE, PoiEmbeddingDoc.class),
                    readJsonLines(USER_PROFILES_FILE, UserProfile.class),
                    readJsonLines(USER_PREFERENCE_TAGS_FILE, UserPreferenceTag.class),
                    readJsonLines(USER_BEHAVIOR_EVENTS_FILE, UserBehaviorEvent.class),
                    readJsonArray(ROUTE_TEMPLATES_FILE, RouteTemplate.class),
                    readJsonArray(SLOT_TRANSITION_RULES_FILE, SlotTransitionRule.class),
                    readJsonArray(DEMO_USER_CASES_FILE, DemoUserCase.class)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load mock data from classpath resources", exception);
        }
    }

    private <T> List<T> readJsonArray(String fileName, Class<T> itemType) throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_ROOT + fileName);
        JavaType collectionType = objectMapper.getTypeFactory().constructCollectionType(List.class, itemType);
        try (var inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, collectionType);
        }
    }

    private <T> List<T> readJsonLines(String fileName, Class<T> itemType) throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_ROOT + fileName);
        List<T> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    records.add(objectMapper.readValue(line, itemType));
                }
            }
        }
        return List.copyOf(records);
    }
}
