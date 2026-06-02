package com.mtai.mtairouteplanner.data.model;

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

import java.util.List;

public record MockDataBundle(
        List<BusinessArea> businessAreas,
        List<TrafficMatrixEntry> trafficMatrix,
        List<PoiBasic> poiBasics,
        List<PoiRatingStats> poiRatingStats,
        List<PoiBusinessInfo> poiBusinessInfo,
        List<PoiUgcSummary> poiUgcSummaries,
        List<PoiRouteProfile> poiRouteProfiles,
        List<PoiTag> poiTags,
        List<PoiEmbeddingDoc> poiEmbeddingDocs,
        List<UserProfile> userProfiles,
        List<UserPreferenceTag> userPreferenceTags,
        List<UserBehaviorEvent> userBehaviorEvents,
        List<RouteTemplate> routeTemplates,
        List<SlotTransitionRule> slotTransitionRules,
        List<DemoUserCase> demoUserCases
) {
}

