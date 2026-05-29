package com.mtai.mtairouteplanner.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PostgresMockDataRepository implements MockDataDatabaseRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final List<String> TABLES = List.of(
            "business_area",
            "traffic_matrix",
            "poi_basic",
            "poi_rating_stats",
            "poi_business_info",
            "poi_ugc_summary",
            "poi_route_profile",
            "poi_tag",
            "poi_embedding_doc",
            "user_profile",
            "user_preference_tag",
            "user_behavior_event",
            "route_template",
            "slot_transition_rule",
            "demo_user_case"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PostgresMockDataRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = MockDataObjectMapperFactory.create();
    }

    @Override
    public MockDataBundle loadBundle() {
        return new MockDataBundle(
                loadBusinessAreas(),
                loadTrafficMatrix(),
                loadPoiBasics(),
                loadPoiRatingStats(),
                loadPoiBusinessInfo(),
                loadPoiUgcSummaries(),
                loadPoiRouteProfiles(),
                loadPoiTags(),
                loadPoiEmbeddingDocs(),
                loadUserProfiles(),
                loadUserPreferenceTags(),
                loadUserBehaviorEvents(),
                loadRouteTemplates(),
                loadSlotTransitionRules(),
                loadDemoUserCases()
        );
    }

    @Override
    public void replaceAll(MockDataBundle bundle) {
        transactionTemplate.executeWithoutResult(ignored -> {
            truncateAll();
            insertAll(bundle);
        });
    }

    @Override
    public Map<String, Integer> rowCounts() {
        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        for (String table : TABLES) {
            Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
            rowCounts.put(table, count == null ? 0 : count);
        }
        return Map.copyOf(rowCounts);
    }

    private List<BusinessArea> loadBusinessAreas() {
        return jdbcTemplate.query("""
                        select area_id, city, district, area_name, center_lat, center_lng, coordinate_system, area_tags, suitable_scenes
                        from business_area
                        order by area_id
                        """,
                (resultSet, rowNum) -> new BusinessArea(
                        resultSet.getString("area_id"),
                        resultSet.getString("city"),
                        resultSet.getString("district"),
                        resultSet.getString("area_name"),
                        resultSet.getDouble("center_lat"),
                        resultSet.getDouble("center_lng"),
                        resultSet.getString("coordinate_system"),
                        readStringList(resultSet, "area_tags"),
                        readStringList(resultSet, "suitable_scenes")
                ));
    }

    private List<TrafficMatrixEntry> loadTrafficMatrix() {
        return jdbcTemplate.query("""
                        select from_area, to_area, transport_mode, distance_km, estimated_minutes
                        from traffic_matrix
                        order by from_area_id, to_area_id, transport_mode
                        """,
                (resultSet, rowNum) -> new TrafficMatrixEntry(
                        resultSet.getString("from_area"),
                        resultSet.getString("to_area"),
                        resultSet.getString("transport_mode"),
                        resultSet.getDouble("distance_km"),
                        resultSet.getDouble("estimated_minutes")
                ));
    }

    private List<PoiBasic> loadPoiBasics() {
        return jdbcTemplate.query("""
                        select poi_id, name, city, district, business_area, address, lat, lng, coordinate_system,
                               category_lv1, category_lv2, brand, branch_name, status
                        from poi_basic
                        order by poi_id
                        """,
                (resultSet, rowNum) -> new PoiBasic(
                        resultSet.getString("poi_id"),
                        resultSet.getString("name"),
                        resultSet.getString("city"),
                        resultSet.getString("district"),
                        resultSet.getString("business_area"),
                        resultSet.getString("address"),
                        resultSet.getDouble("lat"),
                        resultSet.getDouble("lng"),
                        resultSet.getString("coordinate_system"),
                        resultSet.getString("category_lv1"),
                        resultSet.getString("category_lv2"),
                        resultSet.getString("brand"),
                        resultSet.getString("branch_name"),
                        resultSet.getString("status")
                ));
    }

    private List<PoiRatingStats> loadPoiRatingStats() {
        return jdbcTemplate.query("""
                        select poi_id, rating, taste_score, environment_score, service_score, review_count,
                               favorite_count, popularity_score, rank_desc
                        from poi_rating_stats
                        order by poi_id
                        """,
                (resultSet, rowNum) -> new PoiRatingStats(
                        resultSet.getString("poi_id"),
                        resultSet.getDouble("rating"),
                        resultSet.getDouble("taste_score"),
                        resultSet.getDouble("environment_score"),
                        resultSet.getDouble("service_score"),
                        resultSet.getInt("review_count"),
                        resultSet.getInt("favorite_count"),
                        resultSet.getDouble("popularity_score"),
                        resultSet.getString("rank_desc")
                ));
    }

    private List<PoiBusinessInfo> loadPoiBusinessInfo() {
        return jdbcTemplate.query("""
                        select poi_id, avg_price, business_hours, reservation_available, queue_supported,
                               avg_queue_minutes, has_group_buy, coupon_desc
                        from poi_business_info
                        order by poi_id
                        """,
                (resultSet, rowNum) -> new PoiBusinessInfo(
                        resultSet.getString("poi_id"),
                        resultSet.getInt("avg_price"),
                        resultSet.getString("business_hours"),
                        resultSet.getBoolean("reservation_available"),
                        resultSet.getBoolean("queue_supported"),
                        resultSet.getInt("avg_queue_minutes"),
                        resultSet.getBoolean("has_group_buy"),
                        resultSet.getString("coupon_desc")
                ));
    }

    private List<PoiUgcSummary> loadPoiUgcSummaries() {
        return jdbcTemplate.query("""
                        select poi_id, positive_keywords, negative_keywords, crowd_keywords, scene_keywords,
                               review_summary, avoid_reason, recommend_reason
                        from poi_ugc_summary
                        order by poi_id
                        """,
                (resultSet, rowNum) -> new PoiUgcSummary(
                        resultSet.getString("poi_id"),
                        readStringList(resultSet, "positive_keywords"),
                        readStringList(resultSet, "negative_keywords"),
                        readStringList(resultSet, "crowd_keywords"),
                        readStringList(resultSet, "scene_keywords"),
                        resultSet.getString("review_summary"),
                        resultSet.getString("avoid_reason"),
                        resultSet.getString("recommend_reason")
                ));
    }

    private List<PoiRouteProfile> loadPoiRouteProfiles() {
        return jdbcTemplate.query("""
                        select poi_id, route_roles, suitable_scenes, suitable_time_periods, avg_stay_minutes,
                               indoor_outdoor, weather_sensitive, energy_level, noise_level, photo_friendly,
                               family_friendly, route_score
                        from poi_route_profile
                        order by poi_id
                        """,
                (resultSet, rowNum) -> new PoiRouteProfile(
                        resultSet.getString("poi_id"),
                        readStringList(resultSet, "route_roles"),
                        readStringList(resultSet, "suitable_scenes"),
                        readStringList(resultSet, "suitable_time_periods"),
                        resultSet.getInt("avg_stay_minutes"),
                        resultSet.getString("indoor_outdoor"),
                        resultSet.getBoolean("weather_sensitive"),
                        resultSet.getString("energy_level"),
                        resultSet.getString("noise_level"),
                        resultSet.getBoolean("photo_friendly"),
                        resultSet.getBoolean("family_friendly"),
                        resultSet.getDouble("route_score")
                ));
    }

    private List<PoiTag> loadPoiTags() {
        return jdbcTemplate.query("""
                        select poi_id, tag_type, tag_value, confidence, source
                        from poi_tag
                        order by poi_id, tag_type, tag_value, source
                        """,
                (resultSet, rowNum) -> new PoiTag(
                        resultSet.getString("poi_id"),
                        resultSet.getString("tag_type"),
                        resultSet.getString("tag_value"),
                        resultSet.getDouble("confidence"),
                        resultSet.getString("source")
                ));
    }

    private List<PoiEmbeddingDoc> loadPoiEmbeddingDocs() {
        return jdbcTemplate.query("""
                        select poi_id, embedding_text, embedding_vector, updated_at
                        from poi_embedding_doc
                        order by poi_id
                        """,
                (resultSet, rowNum) -> new PoiEmbeddingDoc(
                        resultSet.getString("poi_id"),
                        resultSet.getString("embedding_text"),
                        readJsonObject(resultSet, "embedding_vector"),
                        resultSet.getString("updated_at")
                ));
    }

    private List<UserProfile> loadUserProfiles() {
        return jdbcTemplate.query("""
                        select user_id, nickname, city, default_budget_level, default_pace, default_transport, created_at, updated_at
                        from user_profile
                        order by user_id
                        """,
                (resultSet, rowNum) -> new UserProfile(
                        resultSet.getString("user_id"),
                        resultSet.getString("nickname"),
                        resultSet.getString("city"),
                        resultSet.getString("default_budget_level"),
                        resultSet.getString("default_pace"),
                        resultSet.getString("default_transport"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at")
                ));
    }

    private List<UserPreferenceTag> loadUserPreferenceTags() {
        return jdbcTemplate.query("""
                        select user_id, tag_type, tag_value, weight, source, updated_at
                        from user_preference_tag
                        order by user_id, tag_type, tag_value, source
                        """,
                (resultSet, rowNum) -> new UserPreferenceTag(
                        resultSet.getString("user_id"),
                        resultSet.getString("tag_type"),
                        resultSet.getString("tag_value"),
                        resultSet.getDouble("weight"),
                        resultSet.getString("source"),
                        resultSet.getString("updated_at")
                ));
    }

    private List<UserBehaviorEvent> loadUserBehaviorEvents() {
        return jdbcTemplate.query("""
                        select event_id, user_id, event_type, poi_id, route_id, tag_snapshot, event_time
                        from user_behavior_event
                        order by event_id
                        """,
                (resultSet, rowNum) -> new UserBehaviorEvent(
                        resultSet.getString("event_id"),
                        resultSet.getString("user_id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("poi_id"),
                        resultSet.getString("route_id"),
                        readStringList(resultSet, "tag_snapshot"),
                        resultSet.getString("event_time")
                ));
    }

    private List<RouteTemplate> loadRouteTemplates() {
        return jdbcTemplate.query("""
                        select template_id, scene, time_period, min_duration_minutes, max_duration_minutes,
                               budget_level, pace_level, slot_sequence, suitable_districts
                        from route_template
                        order by template_id
                        """,
                (resultSet, rowNum) -> new RouteTemplate(
                        resultSet.getString("template_id"),
                        resultSet.getString("scene"),
                        resultSet.getString("time_period"),
                        resultSet.getInt("min_duration_minutes"),
                        resultSet.getInt("max_duration_minutes"),
                        resultSet.getString("budget_level"),
                        resultSet.getString("pace_level"),
                        readStringList(resultSet, "slot_sequence"),
                        readStringList(resultSet, "suitable_districts")
                ));
    }

    private List<SlotTransitionRule> loadSlotTransitionRules() {
        return jdbcTemplate.query("""
                        select from_slot, to_slot, weight, reason
                        from slot_transition_rule
                        order by from_slot, to_slot
                        """,
                (resultSet, rowNum) -> new SlotTransitionRule(
                        resultSet.getString("from_slot"),
                        resultSet.getString("to_slot"),
                        resultSet.getDouble("weight"),
                        resultSet.getString("reason")
                ));
    }

    private List<DemoUserCase> loadDemoUserCases() {
        return jdbcTemplate.query("""
                        select case_id, user_id, user_query, city, district, business_area, time_window, party_size,
                               budget, prefer_tags, avoid_tags, expected_scene
                        from demo_user_case
                        order by case_id
                        """,
                (resultSet, rowNum) -> new DemoUserCase(
                        resultSet.getString("case_id"),
                        resultSet.getString("user_id"),
                        resultSet.getString("user_query"),
                        resultSet.getString("city"),
                        resultSet.getString("district"),
                        resultSet.getString("business_area"),
                        resultSet.getString("time_window"),
                        resultSet.getInt("party_size"),
                        resultSet.getInt("budget"),
                        readStringList(resultSet, "prefer_tags"),
                        readStringList(resultSet, "avoid_tags"),
                        resultSet.getString("expected_scene")
                ));
    }

    private void truncateAll() {
        for (int index = TABLES.size() - 1; index >= 0; index--) {
            jdbcTemplate.execute("truncate table " + TABLES.get(index));
        }
    }

    private void insertAll(MockDataBundle bundle) {
        Map<String, String> areaIdByName = new LinkedHashMap<>();
        bundle.businessAreas().forEach(area -> areaIdByName.put(area.areaName(), area.areaId()));

        for (BusinessArea area : bundle.businessAreas()) {
            jdbcTemplate.update("""
                            insert into business_area(area_id, city, district, area_name, center_lat, center_lng, coordinate_system, area_tags, suitable_scenes)
                            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                            """,
                    area.areaId(),
                    area.city(),
                    area.district(),
                    area.areaName(),
                    area.centerLat(),
                    area.centerLng(),
                    area.coordinateSystem(),
                    writeJson(area.areaTags()),
                    writeJson(area.suitableScenes())
            );
        }

        for (TrafficMatrixEntry entry : bundle.trafficMatrix()) {
            jdbcTemplate.update("""
                            insert into traffic_matrix(from_area_id, to_area_id, from_area, to_area, transport_mode, distance_km, estimated_minutes)
                            values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    areaIdByName.get(entry.fromArea()),
                    areaIdByName.get(entry.toArea()),
                    entry.fromArea(),
                    entry.toArea(),
                    entry.transportMode(),
                    entry.distanceKm(),
                    entry.estimatedMinutes()
            );
        }

        for (PoiBasic poiBasic : bundle.poiBasics()) {
            jdbcTemplate.update("""
                            insert into poi_basic(poi_id, business_area_id, name, city, district, business_area, address, lat, lng,
                                                  coordinate_system, category_lv1, category_lv2, brand, branch_name, status)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    poiBasic.poiId(),
                    areaIdByName.get(poiBasic.businessArea()),
                    poiBasic.name(),
                    poiBasic.city(),
                    poiBasic.district(),
                    poiBasic.businessArea(),
                    poiBasic.address(),
                    poiBasic.lat(),
                    poiBasic.lng(),
                    poiBasic.coordinateSystem(),
                    poiBasic.categoryLv1(),
                    poiBasic.categoryLv2(),
                    poiBasic.brand(),
                    poiBasic.branchName(),
                    poiBasic.status()
            );
        }

        for (PoiRatingStats stats : bundle.poiRatingStats()) {
            jdbcTemplate.update("""
                            insert into poi_rating_stats(poi_id, rating, taste_score, environment_score, service_score, review_count,
                                                         favorite_count, popularity_score, rank_desc)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    stats.poiId(),
                    stats.rating(),
                    stats.tasteScore(),
                    stats.environmentScore(),
                    stats.serviceScore(),
                    stats.reviewCount(),
                    stats.favoriteCount(),
                    stats.popularityScore(),
                    stats.rankDesc()
            );
        }

        for (PoiBusinessInfo info : bundle.poiBusinessInfo()) {
            jdbcTemplate.update("""
                            insert into poi_business_info(poi_id, avg_price, business_hours, reservation_available, queue_supported,
                                                          avg_queue_minutes, has_group_buy, coupon_desc)
                            values (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    info.poiId(),
                    info.avgPrice(),
                    info.businessHours(),
                    info.reservationAvailable(),
                    info.queueSupported(),
                    info.avgQueueMinutes(),
                    info.hasGroupBuy(),
                    info.couponDesc()
            );
        }

        for (PoiUgcSummary summary : bundle.poiUgcSummaries()) {
            jdbcTemplate.update("""
                            insert into poi_ugc_summary(poi_id, positive_keywords, negative_keywords, crowd_keywords, scene_keywords,
                                                        review_summary, avoid_reason, recommend_reason)
                            values (?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
                            """,
                    summary.poiId(),
                    writeJson(summary.positiveKeywords()),
                    writeJson(summary.negativeKeywords()),
                    writeJson(summary.crowdKeywords()),
                    writeJson(summary.sceneKeywords()),
                    summary.reviewSummary(),
                    summary.avoidReason(),
                    summary.recommendReason()
            );
        }

        for (PoiRouteProfile profile : bundle.poiRouteProfiles()) {
            jdbcTemplate.update("""
                            insert into poi_route_profile(poi_id, route_roles, suitable_scenes, suitable_time_periods, avg_stay_minutes,
                                                          indoor_outdoor, weather_sensitive, energy_level, noise_level, photo_friendly,
                                                          family_friendly, route_score)
                            values (?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    profile.poiId(),
                    writeJson(profile.routeRoles()),
                    writeJson(profile.suitableScenes()),
                    writeJson(profile.suitableTimePeriods()),
                    profile.avgStayMinutes(),
                    profile.indoorOutdoor(),
                    profile.weatherSensitive(),
                    profile.energyLevel(),
                    profile.noiseLevel(),
                    profile.photoFriendly(),
                    profile.familyFriendly(),
                    profile.routeScore()
            );
        }

        for (PoiTag tag : bundle.poiTags()) {
            jdbcTemplate.update("""
                            insert into poi_tag(poi_id, tag_type, tag_value, confidence, source)
                            values (?, ?, ?, ?, ?)
                            """,
                    tag.poiId(),
                    tag.tagType(),
                    tag.tagValue(),
                    tag.confidence(),
                    tag.source()
            );
        }

        for (PoiEmbeddingDoc doc : bundle.poiEmbeddingDocs()) {
            jdbcTemplate.update("""
                            insert into poi_embedding_doc(poi_id, embedding_text, embedding_vector, updated_at)
                            values (?, ?, cast(? as jsonb), ?)
                            """,
                    doc.poiId(),
                    doc.embeddingText(),
                    writeJson(doc.embeddingVector()),
                    doc.updatedAt()
            );
        }

        for (UserProfile profile : bundle.userProfiles()) {
            jdbcTemplate.update("""
                            insert into user_profile(user_id, nickname, city, default_budget_level, default_pace, default_transport, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    profile.userId(),
                    profile.nickname(),
                    profile.city(),
                    profile.defaultBudgetLevel(),
                    profile.defaultPace(),
                    profile.defaultTransport(),
                    profile.createdAt(),
                    profile.updatedAt()
            );
        }

        for (UserPreferenceTag tag : bundle.userPreferenceTags()) {
            jdbcTemplate.update("""
                            insert into user_preference_tag(user_id, tag_type, tag_value, weight, source, updated_at)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    tag.userId(),
                    tag.tagType(),
                    tag.tagValue(),
                    tag.weight(),
                    tag.source(),
                    tag.updatedAt()
            );
        }

        for (UserBehaviorEvent event : bundle.userBehaviorEvents()) {
            jdbcTemplate.update("""
                            insert into user_behavior_event(event_id, user_id, event_type, poi_id, route_id, tag_snapshot, event_time)
                            values (?, ?, ?, ?, ?, cast(? as jsonb), ?)
                            """,
                    event.eventId(),
                    event.userId(),
                    event.eventType(),
                    event.poiId(),
                    event.routeId(),
                    writeJson(event.tagSnapshot()),
                    event.eventTime()
            );
        }

        for (RouteTemplate template : bundle.routeTemplates()) {
            jdbcTemplate.update("""
                            insert into route_template(template_id, scene, time_period, min_duration_minutes, max_duration_minutes,
                                                       budget_level, pace_level, slot_sequence, suitable_districts)
                            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                            """,
                    template.templateId(),
                    template.scene(),
                    template.timePeriod(),
                    template.minDurationMinutes(),
                    template.maxDurationMinutes(),
                    template.budgetLevel(),
                    template.paceLevel(),
                    writeJson(template.slotSequence()),
                    writeJson(template.suitableDistricts())
            );
        }

        for (SlotTransitionRule rule : bundle.slotTransitionRules()) {
            jdbcTemplate.update("""
                            insert into slot_transition_rule(from_slot, to_slot, weight, reason)
                            values (?, ?, ?, ?)
                            """,
                    rule.fromSlot(),
                    rule.toSlot(),
                    rule.weight(),
                    rule.reason()
            );
        }

        for (DemoUserCase demoUserCase : bundle.demoUserCases()) {
            jdbcTemplate.update("""
                            insert into demo_user_case(case_id, user_id, business_area_id, user_query, city, district, business_area,
                                                       time_window, party_size, budget, prefer_tags, avoid_tags, expected_scene)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
                            """,
                    demoUserCase.caseId(),
                    demoUserCase.userId(),
                    areaIdByName.get(demoUserCase.businessArea()),
                    demoUserCase.userQuery(),
                    demoUserCase.city(),
                    demoUserCase.district(),
                    demoUserCase.businessArea(),
                    demoUserCase.timeWindow(),
                    demoUserCase.partySize(),
                    demoUserCase.budget(),
                    writeJson(demoUserCase.preferTags()),
                    writeJson(demoUserCase.avoidTags()),
                    demoUserCase.expectedScene()
            );
        }
    }

    private List<String> readStringList(ResultSet resultSet, String columnName) throws SQLException {
        String json = resultSet.getString(columnName);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize list column " + columnName, exception);
        }
    }

    private Object readJsonObject(ResultSet resultSet, String columnName) throws SQLException {
        String json = resultSet.getString(columnName);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize JSON column " + columnName, exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON column", exception);
        }
    }
}
