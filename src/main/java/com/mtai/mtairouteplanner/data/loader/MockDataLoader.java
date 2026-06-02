package com.mtai.mtairouteplanner.data.loader;

import com.mtai.mtairouteplanner.data.model.LoadedPoi;
import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import com.mtai.mtairouteplanner.data.source.ClasspathMockDataSourceReader;
import com.mtai.mtairouteplanner.data.source.MockDataSourceReader;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.BusinessArea;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.DemoUserCase;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.PoiBasic;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.PoiBusinessInfo;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.PoiEmbeddingDoc;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.PoiRatingStats;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.PoiRouteProfile;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.PoiTag;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.PoiUgcSummary;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.RouteTemplate;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.SlotTransitionRule;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.TrafficMatrixEntry;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.UserBehaviorEvent;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.UserPreferenceTag;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.UserProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockDataLoader {
    private final MockDataSourceReader sourceReader;

    public MockDataLoader() {
        this(new ClasspathMockDataSourceReader());
    }

    public MockDataLoader(MockDataSourceReader sourceReader) {
        this.sourceReader = sourceReader;
    }

    public MockDataBundle load() {
        return sourceReader.load();
    }

    public List<LoadedPoi> assembleLoadedPois(MockDataBundle bundle) {
        Map<String, PoiRatingStats> ratingByPoiId = toMap(bundle.poiRatingStats(), PoiRatingStats::poiId);
        Map<String, PoiBusinessInfo> businessInfoByPoiId = toMap(bundle.poiBusinessInfo(), PoiBusinessInfo::poiId);
        Map<String, PoiUgcSummary> ugcByPoiId = toMap(bundle.poiUgcSummaries(), PoiUgcSummary::poiId);
        Map<String, PoiRouteProfile> routeProfileByPoiId = toMap(bundle.poiRouteProfiles(), PoiRouteProfile::poiId);
        Map<String, PoiEmbeddingDoc> embeddingByPoiId = toMap(bundle.poiEmbeddingDocs(), PoiEmbeddingDoc::poiId);
        Map<String, List<PoiTag>> tagsByPoiId = new LinkedHashMap<>();
        for (PoiTag poiTag : bundle.poiTags()) {
            tagsByPoiId.computeIfAbsent(poiTag.poiId(), ignored -> new ArrayList<>()).add(poiTag);
        }

        List<LoadedPoi> loadedPois = new ArrayList<>();
        for (PoiBasic poiBasic : bundle.poiBasics()) {
            loadedPois.add(new LoadedPoi(
                    poiBasic,
                    requireComponent(ratingByPoiId.get(poiBasic.poiId()), poiBasic.poiId(), "poi_rating_stats"),
                    requireComponent(businessInfoByPoiId.get(poiBasic.poiId()), poiBasic.poiId(), "poi_business_info"),
                    requireComponent(ugcByPoiId.get(poiBasic.poiId()), poiBasic.poiId(), "poi_ugc_summary"),
                    requireComponent(routeProfileByPoiId.get(poiBasic.poiId()), poiBasic.poiId(), "poi_route_profile"),
                    tagsByPoiId.getOrDefault(poiBasic.poiId(), List.of()),
                    requireComponent(embeddingByPoiId.get(poiBasic.poiId()), poiBasic.poiId(), "poi_embedding_docs")
            ));
        }
        return List.copyOf(loadedPois);
    }

    private <T> T requireComponent(T component, String poiId, String datasetName) {
        if (component == null) {
            throw new IllegalStateException("Missing " + datasetName + " record for poi_id=" + poiId);
        }
        return component;
    }

    private <T> Map<String, T> toMap(List<T> values, java.util.function.Function<T, String> keyExtractor) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T value : values) {
            map.put(keyExtractor.apply(value), value);
        }
        return map;
    }

}

