package com.mtai.mtairouteplanner.data.index;

import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.BusinessArea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BusinessAreaIndex {

    private final Map<String, BusinessArea> byAreaName;
    private final Map<String, List<BusinessArea>> byDistrict;

    public BusinessAreaIndex(List<BusinessArea> businessAreas) {
        Map<String, BusinessArea> areaMap = new LinkedHashMap<>();
        Map<String, List<BusinessArea>> districtMap = new LinkedHashMap<>();
        for (BusinessArea businessArea : businessAreas) {
            areaMap.put(businessArea.areaName(), businessArea);
            districtMap.computeIfAbsent(businessArea.district(), ignored -> new java.util.ArrayList<>()).add(businessArea);
        }
        this.byAreaName = Map.copyOf(areaMap);
        this.byDistrict = districtMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    public Optional<BusinessArea> findByAreaName(String areaName) {
        return Optional.ofNullable(byAreaName.get(areaName));
    }

    public List<BusinessArea> findByDistrict(String district) {
        return byDistrict.getOrDefault(district, List.of());
    }
}

