package com.mtai.mtairouteplanner.data.loader;

import com.mtai.mtairouteplanner.data.index.MockDataIndexes;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.GeneratedData;
import com.mtai.mtairouteplanner.data.model.LoadedPoi;
import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockDataLoaderAndIndexesTest {

    private final MockDataLoader mockDataLoader = new MockDataLoader();
    private final Phase2StaticMockDataGenerator generator = new Phase2StaticMockDataGenerator();

    @Test
    void allRequiredMockFilesCanBeLoaded() {
        MockDataBundle bundle = mockDataLoader.load();

        assertThat(bundle.businessAreas()).isNotEmpty();
        assertThat(bundle.trafficMatrix()).isNotEmpty();
        assertThat(bundle.poiBasics()).isNotEmpty();
        assertThat(bundle.poiRatingStats()).isNotEmpty();
        assertThat(bundle.poiBusinessInfo()).isNotEmpty();
        assertThat(bundle.poiUgcSummaries()).isNotEmpty();
        assertThat(bundle.poiRouteProfiles()).isNotEmpty();
        assertThat(bundle.poiTags()).isNotEmpty();
        assertThat(bundle.poiEmbeddingDocs()).isNotEmpty();
        assertThat(bundle.userProfiles()).isNotEmpty();
        assertThat(bundle.userPreferenceTags()).isNotEmpty();
        assertThat(bundle.routeTemplates()).isNotEmpty();
        assertThat(bundle.slotTransitionRules()).isNotEmpty();
        assertThat(bundle.demoUserCases()).isNotEmpty();
    }

    @Test
    void loadedCountsMatchGeneratedData() {
        MockDataBundle bundle = mockDataLoader.load();
        GeneratedData generatedData = generator.generate();

        assertThat(bundle.businessAreas()).hasSameSizeAs(generatedData.businessAreas());
        assertThat(bundle.trafficMatrix()).hasSameSizeAs(generatedData.trafficMatrix());
        assertThat(bundle.poiBasics()).hasSameSizeAs(generatedData.poiBasic());
        assertThat(bundle.poiRatingStats()).hasSameSizeAs(generatedData.poiRatingStats());
        assertThat(bundle.poiBusinessInfo()).hasSameSizeAs(generatedData.poiBusinessInfo());
        assertThat(bundle.poiUgcSummaries()).hasSameSizeAs(generatedData.poiUgcSummaries());
        assertThat(bundle.poiRouteProfiles()).hasSameSizeAs(generatedData.poiRouteProfiles());
        assertThat(bundle.poiTags()).hasSameSizeAs(generatedData.poiTags());
        assertThat(bundle.poiEmbeddingDocs()).hasSameSizeAs(generatedData.poiEmbeddingDocs());
        assertThat(bundle.userProfiles()).hasSameSizeAs(generatedData.userProfiles());
        assertThat(bundle.userPreferenceTags()).hasSameSizeAs(generatedData.userPreferenceTags());
        assertThat(bundle.userBehaviorEvents()).hasSameSizeAs(generatedData.userBehaviorEvents());
        assertThat(bundle.routeTemplates()).hasSameSizeAs(generatedData.routeTemplates());
        assertThat(bundle.slotTransitionRules()).hasSameSizeAs(generatedData.slotTransitionRules());
        assertThat(bundle.demoUserCases()).hasSameSizeAs(generatedData.demoUserCases());
    }

    @Test
    void poiJoinedDataCanBeAssembledByPoiId() {
        MockDataBundle bundle = mockDataLoader.load();

        List<LoadedPoi> loadedPois = mockDataLoader.assembleLoadedPois(bundle);

        assertThat(loadedPois).hasSize(bundle.poiBasics().size());
        assertThat(loadedPois).allSatisfy(loadedPoi -> {
            assertThat(loadedPoi.poiBasic()).isNotNull();
            assertThat(loadedPoi.poiRatingStats()).isNotNull();
            assertThat(loadedPoi.poiBusinessInfo()).isNotNull();
            assertThat(loadedPoi.poiUgcSummary()).isNotNull();
            assertThat(loadedPoi.poiRouteProfile()).isNotNull();
            assertThat(loadedPoi.poiEmbeddingDoc()).isNotNull();
            assertThat(loadedPoi.poiTags()).isNotEmpty();
            assertThat(loadedPoi.poiId()).isEqualTo(loadedPoi.poiBasic().poiId());
        });
    }

    @Test
    void indexesReturnExpectedResults() {
        MockDataBundle bundle = mockDataLoader.load();
        List<LoadedPoi> loadedPois = mockDataLoader.assembleLoadedPois(bundle);
        MockDataIndexes indexes = MockDataIndexes.from(bundle, loadedPois);

        assertThat(indexes.businessAreaIndex().findByAreaName("三里屯")).isPresent();
        assertThat(indexes.businessAreaIndex().findByDistrict("朝阳区")).isNotEmpty();

        assertThat(indexes.poiIndex().findByPoiId("P00001")).isPresent();
        assertThat(indexes.poiIndex().findByBusinessArea("三里屯")).hasSize(10);
        assertThat(indexes.poiIndex().findByDistrict("朝阳区")).isNotEmpty();
        assertThat(indexes.poiIndex().findByCategoryLv1("餐饮")).isNotEmpty();
        assertThat(indexes.poiIndex().findByRouteRole("晚餐主餐")).isNotEmpty();
        assertThat(indexes.poiIndex().findBySuitableScene("情侣约会")).isNotEmpty();
        assertThat(indexes.poiIndex().findByBudgetRange(0, 60))
                .allSatisfy(poi -> assertThat(poi.poiBusinessInfo().avgPrice()).isBetween(0, 60));

        assertThat(indexes.userPreferenceIndex().findProfileByUserId("U10001")).isPresent();
        assertThat(indexes.userPreferenceIndex().findPreferenceTagsByUserId("U10001")).hasSize(5);

        assertThat(indexes.routeTemplateIndex().findByScene("情侣约会")).isNotEmpty();
        assertThat(indexes.routeTemplateIndex().findByTimePeriod("晚间")).isNotEmpty();
        assertThat(indexes.routeTemplateIndex()
                .findCandidateTemplates("情侣约会", "晚间", "中", "轻松", "朝阳区"))
                .extracting(template -> template.templateId())
                .contains("RT001");

        assertThat(indexes.trafficMatrixIndex().findTravelEstimate("三里屯", "国贸", "taxi")).isPresent();
    }

    @Test
    void missingIdsReturnEmptyResultsInsteadOfExceptions() {
        MockDataBundle bundle = mockDataLoader.load();
        List<LoadedPoi> loadedPois = mockDataLoader.assembleLoadedPois(bundle);
        MockDataIndexes indexes = MockDataIndexes.from(bundle, loadedPois);

        assertThat(indexes.businessAreaIndex().findByAreaName("不存在商圈")).isEmpty();
        assertThat(indexes.businessAreaIndex().findByDistrict("不存在区")).isEmpty();
        assertThat(indexes.poiIndex().findByPoiId("P99999")).isEmpty();
        assertThat(indexes.poiIndex().findByBusinessArea("不存在商圈")).isEmpty();
        assertThat(indexes.poiIndex().findByDistrict("不存在区")).isEmpty();
        assertThat(indexes.poiIndex().findByCategoryLv1("不存在分类")).isEmpty();
        assertThat(indexes.poiIndex().findByRouteRole("不存在角色")).isEmpty();
        assertThat(indexes.poiIndex().findBySuitableScene("不存在场景")).isEmpty();
        assertThat(indexes.poiIndex().findByBudgetRange(1000, 1200)).isEmpty();
        assertThat(indexes.userPreferenceIndex().findProfileByUserId("U99999")).isEmpty();
        assertThat(indexes.userPreferenceIndex().findPreferenceTagsByUserId("U99999")).isEmpty();
        assertThat(indexes.routeTemplateIndex().findByScene("不存在场景")).isEmpty();
        assertThat(indexes.routeTemplateIndex().findByTimePeriod("不存在时间")).isEmpty();
        assertThat(indexes.routeTemplateIndex()
                .findCandidateTemplates("不存在场景", "不存在时间", "高", "轻松", "朝阳区"))
                .isEmpty();
        assertThat(indexes.trafficMatrixIndex().findTravelEstimate("不存在", "国贸", "taxi")).isEmpty();
    }
}
