package com.mtai.mtairouteplanner.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PoiIndex {

    private final Map<String, LoadedPoi> byPoiId;
    private final Map<String, List<LoadedPoi>> byBusinessArea;
    private final Map<String, List<LoadedPoi>> byDistrict;
    private final Map<String, List<LoadedPoi>> byCategoryLv1;
    private final Map<String, List<LoadedPoi>> byRouteRole;
    private final Map<String, List<LoadedPoi>> bySuitableScene;

    public PoiIndex(List<LoadedPoi> loadedPois) {
        Map<String, LoadedPoi> poiMap = new LinkedHashMap<>();
        Map<String, List<LoadedPoi>> businessAreaMap = new LinkedHashMap<>();
        Map<String, List<LoadedPoi>> districtMap = new LinkedHashMap<>();
        Map<String, List<LoadedPoi>> categoryMap = new LinkedHashMap<>();
        Map<String, List<LoadedPoi>> routeRoleMap = new LinkedHashMap<>();
        Map<String, List<LoadedPoi>> suitableSceneMap = new LinkedHashMap<>();

        for (LoadedPoi loadedPoi : loadedPois) {
            poiMap.put(loadedPoi.poiId(), loadedPoi);
            businessAreaMap.computeIfAbsent(loadedPoi.poiBasic().businessArea(), ignored -> new java.util.ArrayList<>()).add(loadedPoi);
            districtMap.computeIfAbsent(loadedPoi.poiBasic().district(), ignored -> new java.util.ArrayList<>()).add(loadedPoi);
            categoryMap.computeIfAbsent(loadedPoi.poiBasic().categoryLv1(), ignored -> new java.util.ArrayList<>()).add(loadedPoi);

            for (String routeRole : loadedPoi.poiRouteProfile().routeRoles()) {
                routeRoleMap.computeIfAbsent(routeRole, ignored -> new java.util.ArrayList<>()).add(loadedPoi);
            }
            for (String suitableScene : loadedPoi.poiRouteProfile().suitableScenes()) {
                suitableSceneMap.computeIfAbsent(suitableScene, ignored -> new java.util.ArrayList<>()).add(loadedPoi);
            }
        }

        this.byPoiId = Map.copyOf(poiMap);
        this.byBusinessArea = immutableGroupedMap(businessAreaMap);
        this.byDistrict = immutableGroupedMap(districtMap);
        this.byCategoryLv1 = immutableGroupedMap(categoryMap);
        this.byRouteRole = immutableGroupedMap(routeRoleMap);
        this.bySuitableScene = immutableGroupedMap(suitableSceneMap);
    }

    public Optional<LoadedPoi> findByPoiId(String poiId) {
        return Optional.ofNullable(byPoiId.get(poiId));
    }

    public List<LoadedPoi> allPois() {
        return List.copyOf(byPoiId.values());
    }

    public List<LoadedPoi> findByBusinessArea(String businessArea) {
        return byBusinessArea.getOrDefault(businessArea, List.of());
    }

    public List<LoadedPoi> findByDistrict(String district) {
        return byDistrict.getOrDefault(district, List.of());
    }

    public List<LoadedPoi> findByCategoryLv1(String categoryLv1) {
        return byCategoryLv1.getOrDefault(categoryLv1, List.of());
    }

    public List<LoadedPoi> findByRouteRole(String routeRole) {
        return byRouteRole.getOrDefault(routeRole, List.of());
    }

    public List<LoadedPoi> findBySuitableScene(String suitableScene) {
        return bySuitableScene.getOrDefault(suitableScene, List.of());
    }

    public List<LoadedPoi> findByBudgetRange(int minAvgPriceInclusive, int maxAvgPriceInclusive) {
        return byPoiId.values().stream()
                .filter(loadedPoi -> loadedPoi.poiBusinessInfo().avgPrice() >= minAvgPriceInclusive)
                .filter(loadedPoi -> loadedPoi.poiBusinessInfo().avgPrice() <= maxAvgPriceInclusive)
                .toList();
    }

    private Map<String, List<LoadedPoi>> immutableGroupedMap(Map<String, List<LoadedPoi>> mutableMap) {
        return mutableMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }
}
