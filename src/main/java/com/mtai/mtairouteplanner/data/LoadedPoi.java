package com.mtai.mtairouteplanner.data;

import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiBasic;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiBusinessInfo;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiEmbeddingDoc;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiRatingStats;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiRouteProfile;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiTag;
import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.PoiUgcSummary;

import java.util.List;

public record LoadedPoi(
        PoiBasic poiBasic,
        PoiRatingStats poiRatingStats,
        PoiBusinessInfo poiBusinessInfo,
        PoiUgcSummary poiUgcSummary,
        PoiRouteProfile poiRouteProfile,
        List<PoiTag> poiTags,
        PoiEmbeddingDoc poiEmbeddingDoc
) {
    public String poiId() {
        return poiBasic.poiId();
    }
}
