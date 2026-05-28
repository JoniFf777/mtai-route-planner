package com.mtai.mtairouteplanner.data;

import java.util.List;

public record MockDataIndexes(
        BusinessAreaIndex businessAreaIndex,
        PoiIndex poiIndex,
        RouteTemplateIndex routeTemplateIndex,
        UserPreferenceIndex userPreferenceIndex,
        TrafficMatrixIndex trafficMatrixIndex
) {
    public static MockDataIndexes from(MockDataBundle bundle, List<LoadedPoi> loadedPois) {
        return new MockDataIndexes(
                new BusinessAreaIndex(bundle.businessAreas()),
                new PoiIndex(loadedPois),
                new RouteTemplateIndex(bundle.routeTemplates()),
                new UserPreferenceIndex(bundle.userProfiles(), bundle.userPreferenceTags()),
                new TrafficMatrixIndex(bundle.trafficMatrix())
        );
    }
}
