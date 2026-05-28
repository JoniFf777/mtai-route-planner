package com.mtai.mtairouteplanner.data;

import com.mtai.mtairouteplanner.data.Phase2StaticMockDataGenerator.TrafficMatrixEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class TrafficMatrixIndex {

    private final Map<String, TrafficMatrixEntry> entriesByKey;

    public TrafficMatrixIndex(List<TrafficMatrixEntry> trafficMatrixEntries) {
        Map<String, TrafficMatrixEntry> entryMap = new LinkedHashMap<>();
        for (TrafficMatrixEntry trafficMatrixEntry : trafficMatrixEntries) {
            entryMap.put(keyOf(
                    trafficMatrixEntry.fromArea(),
                    trafficMatrixEntry.toArea(),
                    trafficMatrixEntry.transportMode()
            ), trafficMatrixEntry);
        }
        this.entriesByKey = Map.copyOf(entryMap);
    }

    public Optional<TrafficMatrixEntry> findTravelEstimate(String fromArea, String toArea, String transportMode) {
        return Optional.ofNullable(entriesByKey.get(keyOf(fromArea, toArea, transportMode)));
    }

    private String keyOf(String fromArea, String toArea, String transportMode) {
        return normalize(fromArea) + "->" + normalize(toArea) + "|" + normalize(transportMode);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
