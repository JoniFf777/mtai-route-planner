package com.mtai.mtairouteplanner.data;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresMockDataRepositoryIntegrationTest {

    @Test
    void loaderInsertsExpectedCountsAndPostgresSourceBuildsEquivalentIndexes() throws Exception {
        assumeTrue(Boolean.getBoolean("route.postgres.tests"), "Set -Droute.postgres.tests=true to enable PostgreSQL integration tests.");

        DataSource dataSource = postgresDataSource();
        assumeTrue(canConnect(dataSource), "PostgreSQL is not reachable with the configured connection settings.");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        PostgresMockDataRepository repository = new PostgresMockDataRepository(
                new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource))
        );
        MockDataDatabaseLoader databaseLoader = new MockDataDatabaseLoader(repository);
        MockDataBundle jsonBundle = new MockDataLoader().load();

        MockDataDatabaseLoader.LoadSummary loadSummary = databaseLoader.loadToDatabase();

        assertThat(loadSummary.rowCounts()).isEqualTo(expectedRowCounts(jsonBundle));

        MockDataLoader postgresLoader = new MockDataLoader(new PostgresMockDataSourceReader(repository));
        MockDataBundle postgresBundle = postgresLoader.load();
        assertThat(postgresBundle.businessAreas()).hasSameSizeAs(jsonBundle.businessAreas());
        assertThat(postgresBundle.trafficMatrix()).hasSameSizeAs(jsonBundle.trafficMatrix());
        assertThat(postgresBundle.poiBasics()).hasSameSizeAs(jsonBundle.poiBasics());
        assertThat(postgresBundle.routeTemplates()).hasSameSizeAs(jsonBundle.routeTemplates());

        MockDataIndexes jsonIndexes = MockDataIndexes.from(jsonBundle, new MockDataLoader().assembleLoadedPois(jsonBundle));
        MockDataIndexes postgresIndexes = MockDataIndexes.from(postgresBundle, postgresLoader.assembleLoadedPois(postgresBundle));

        String sampleBusinessArea = jsonBundle.businessAreas().getFirst().areaName();
        String sampleDistrict = jsonBundle.businessAreas().getFirst().district();
        String samplePoiId = jsonBundle.poiBasics().getFirst().poiId();
        String sampleScene = jsonBundle.routeTemplates().getFirst().scene();
        String sampleTimePeriod = jsonBundle.routeTemplates().getFirst().timePeriod();
        String sampleFromArea = jsonBundle.trafficMatrix().getFirst().fromArea();
        String sampleToArea = jsonBundle.trafficMatrix().getFirst().toArea();
        String sampleTransportMode = jsonBundle.trafficMatrix().getFirst().transportMode();

        assertThat(postgresIndexes.businessAreaIndex().findByAreaName(sampleBusinessArea))
                .isEqualTo(jsonIndexes.businessAreaIndex().findByAreaName(sampleBusinessArea));
        assertThat(postgresIndexes.businessAreaIndex().findByDistrict(sampleDistrict))
                .hasSameSizeAs(jsonIndexes.businessAreaIndex().findByDistrict(sampleDistrict));
        assertThat(postgresIndexes.poiIndex().findByPoiId(samplePoiId))
                .isEqualTo(jsonIndexes.poiIndex().findByPoiId(samplePoiId));
        assertThat(postgresIndexes.routeTemplateIndex().findByScene(sampleScene))
                .extracting(template -> template.templateId())
                .containsExactlyElementsOf(jsonIndexes.routeTemplateIndex().findByScene(sampleScene).stream().map(template -> template.templateId()).toList());
        assertThat(postgresIndexes.routeTemplateIndex().findByTimePeriod(sampleTimePeriod))
                .extracting(template -> template.templateId())
                .containsExactlyElementsOf(jsonIndexes.routeTemplateIndex().findByTimePeriod(sampleTimePeriod).stream().map(template -> template.templateId()).toList());
        assertThat(postgresIndexes.trafficMatrixIndex().findTravelEstimate(sampleFromArea, sampleToArea, sampleTransportMode))
                .isEqualTo(jsonIndexes.trafficMatrixIndex().findTravelEstimate(sampleFromArea, sampleToArea, sampleTransportMode));
    }

    private Map<String, Integer> expectedRowCounts(MockDataBundle bundle) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("business_area", bundle.businessAreas().size());
        counts.put("traffic_matrix", bundle.trafficMatrix().size());
        counts.put("poi_basic", bundle.poiBasics().size());
        counts.put("poi_rating_stats", bundle.poiRatingStats().size());
        counts.put("poi_business_info", bundle.poiBusinessInfo().size());
        counts.put("poi_ugc_summary", bundle.poiUgcSummaries().size());
        counts.put("poi_route_profile", bundle.poiRouteProfiles().size());
        counts.put("poi_tag", bundle.poiTags().size());
        counts.put("poi_embedding_doc", bundle.poiEmbeddingDocs().size());
        counts.put("user_profile", bundle.userProfiles().size());
        counts.put("user_preference_tag", bundle.userPreferenceTags().size());
        counts.put("user_behavior_event", bundle.userBehaviorEvents().size());
        counts.put("route_template", bundle.routeTemplates().size());
        counts.put("slot_transition_rule", bundle.slotTransitionRules().size());
        counts.put("demo_user_case", bundle.demoUserCases().size());
        return Map.copyOf(counts);
    }

    private DataSource postgresDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(System.getenv().getOrDefault("POSTGRES_URL", "jdbc:postgresql://localhost:5432/mtai_route_planner"));
        dataSource.setUsername(System.getenv().getOrDefault("POSTGRES_USER", "mtai"));
        dataSource.setPassword(System.getenv().getOrDefault("POSTGRES_PASSWORD", "mtai_dev_password"));
        return dataSource;
    }

    private boolean canConnect(DataSource dataSource) throws Exception {
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
