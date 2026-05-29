package com.mtai.mtairouteplanner.data;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockDataDatabaseLoaderTest {

    @Test
    void classpathMockDataCanBeLoadedIntoRepositoryDeterministically() {
        FakeMockDataDatabaseRepository repository = new FakeMockDataDatabaseRepository();
        MockDataDatabaseLoader loader = new MockDataDatabaseLoader(new ClasspathMockDataSourceReader(), repository);

        MockDataDatabaseLoader.LoadSummary firstLoad = loader.loadToDatabase();
        MockDataDatabaseLoader.LoadSummary secondLoad = loader.loadToDatabase();

        assertThat(repository.replaceAllCalls).isEqualTo(2);
        assertThat(repository.lastBundle).isNotNull();
        assertThat(firstLoad.rowCounts()).isEqualTo(secondLoad.rowCounts());
        assertThat(firstLoad.rowCounts()).containsEntry("business_area", repository.lastBundle.businessAreas().size());
        assertThat(firstLoad.rowCounts()).containsEntry("poi_basic", repository.lastBundle.poiBasics().size());
        assertThat(firstLoad.rowCounts()).containsEntry("route_template", repository.lastBundle.routeTemplates().size());
        assertThat(firstLoad.rowCounts()).containsEntry("demo_user_case", repository.lastBundle.demoUserCases().size());
    }

    private static final class FakeMockDataDatabaseRepository implements MockDataDatabaseRepository {

        private MockDataBundle lastBundle;
        private int replaceAllCalls;

        @Override
        public MockDataBundle loadBundle() {
            return lastBundle;
        }

        @Override
        public void replaceAll(MockDataBundle bundle) {
            this.lastBundle = bundle;
            this.replaceAllCalls++;
        }

        @Override
        public Map<String, Integer> rowCounts() {
            Map<String, Integer> rowCounts = new LinkedHashMap<>();
            rowCounts.put("business_area", lastBundle.businessAreas().size());
            rowCounts.put("traffic_matrix", lastBundle.trafficMatrix().size());
            rowCounts.put("poi_basic", lastBundle.poiBasics().size());
            rowCounts.put("poi_rating_stats", lastBundle.poiRatingStats().size());
            rowCounts.put("poi_business_info", lastBundle.poiBusinessInfo().size());
            rowCounts.put("poi_ugc_summary", lastBundle.poiUgcSummaries().size());
            rowCounts.put("poi_route_profile", lastBundle.poiRouteProfiles().size());
            rowCounts.put("poi_tag", lastBundle.poiTags().size());
            rowCounts.put("poi_embedding_doc", lastBundle.poiEmbeddingDocs().size());
            rowCounts.put("user_profile", lastBundle.userProfiles().size());
            rowCounts.put("user_preference_tag", lastBundle.userPreferenceTags().size());
            rowCounts.put("user_behavior_event", lastBundle.userBehaviorEvents().size());
            rowCounts.put("route_template", lastBundle.routeTemplates().size());
            rowCounts.put("slot_transition_rule", lastBundle.slotTransitionRules().size());
            rowCounts.put("demo_user_case", lastBundle.demoUserCases().size());
            return Map.copyOf(rowCounts);
        }
    }
}
