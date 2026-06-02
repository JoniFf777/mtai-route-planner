package com.mtai.mtairouteplanner.service.route.planning;

import com.mtai.mtairouteplanner.data.model.LoadedPoi;
import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import com.mtai.mtairouteplanner.data.index.MockDataIndexes;
import com.mtai.mtairouteplanner.data.loader.MockDataLoader;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.BusinessArea;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.TrafficMatrixEntry;
import com.mtai.mtairouteplanner.model.route.TravelEstimate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public class TrafficTimeService {

    private static final String WALKING_MODE = "walking";
    private static final String TAXI_MODE = "taxi";
    private static final String SAME_AREA_SOURCE = "SAME_AREA_HAVERSINE_WALKING";
    private static final String TRAFFIC_MATRIX_SOURCE = "TRAFFIC_MATRIX";
    private static final String CROSS_AREA_FALLBACK_SOURCE = "CROSS_AREA_HAVERSINE_FALLBACK";
    private static final double WALKING_SPEED_KMH = 4.5;
    private static final double BEIJING_TRANSPORT_COEFFICIENT = 6.2;
    private static final double CROSS_AREA_BASE_MINUTES = 12.0;

    private final MockDataBundle mockDataBundle;
    private final MockDataIndexes indexes;

    public TrafficTimeService() {
        this(new MockDataLoader());
    }

    public TrafficTimeService(MockDataLoader mockDataLoader) {
        this.mockDataBundle = mockDataLoader.load();
        this.indexes = MockDataIndexes.from(mockDataBundle, mockDataLoader.assembleLoadedPois(mockDataBundle));
    }

    public Optional<TravelEstimate> estimateTravelTime(String fromPoiId, String toPoiId) {
        Optional<LoadedPoi> fromPoi = indexes.poiIndex().findByPoiId(fromPoiId);
        Optional<LoadedPoi> toPoi = indexes.poiIndex().findByPoiId(toPoiId);
        if (fromPoi.isEmpty() || toPoi.isEmpty()) {
            return Optional.empty();
        }

        return estimateTravelTime(fromPoi.get(), toPoi.get());
    }

    private Optional<TravelEstimate> estimateTravelTime(LoadedPoi fromPoi, LoadedPoi toPoi) {
        if (fromPoi.poiBasic().businessArea().equals(toPoi.poiBasic().businessArea())) {
            double distanceKm = haversineKm(
                    fromPoi.poiBasic().lat(),
                    fromPoi.poiBasic().lng(),
                    toPoi.poiBasic().lat(),
                    toPoi.poiBasic().lng()
            );
            double estimatedMinutes = Math.max((distanceKm / WALKING_SPEED_KMH) * 60.0, 1.0);
            return Optional.of(new TravelEstimate(
                    fromPoi.poiId(),
                    toPoi.poiId(),
                    fromPoi.poiBasic().businessArea(),
                    toPoi.poiBasic().businessArea(),
                    rounded(distanceKm, 2),
                    rounded(estimatedMinutes, 2),
                    WALKING_MODE,
                    SAME_AREA_SOURCE
            ));
        }

        Optional<TrafficMatrixEntry> matrixEntry = indexes.trafficMatrixIndex()
                .findTravelEstimate(fromPoi.poiBasic().businessArea(), toPoi.poiBasic().businessArea(), TAXI_MODE);
        if (matrixEntry.isPresent()) {
            TrafficMatrixEntry trafficMatrixEntry = matrixEntry.get();
            return Optional.of(new TravelEstimate(
                    fromPoi.poiId(),
                    toPoi.poiId(),
                    fromPoi.poiBasic().businessArea(),
                    toPoi.poiBasic().businessArea(),
                    trafficMatrixEntry.distanceKm(),
                    trafficMatrixEntry.estimatedMinutes(),
                    trafficMatrixEntry.transportMode(),
                    TRAFFIC_MATRIX_SOURCE
            ));
        }

        Optional<BusinessArea> fromArea = indexes.businessAreaIndex().findByAreaName(fromPoi.poiBasic().businessArea());
        Optional<BusinessArea> toArea = indexes.businessAreaIndex().findByAreaName(toPoi.poiBasic().businessArea());
        if (fromArea.isEmpty() || toArea.isEmpty()) {
            return Optional.empty();
        }

        double distanceKm = haversineKm(
                fromArea.get().centerLat(),
                fromArea.get().centerLng(),
                toArea.get().centerLat(),
                toArea.get().centerLng()
        );
        double estimatedMinutes = Math.max(distanceKm * BEIJING_TRANSPORT_COEFFICIENT + CROSS_AREA_BASE_MINUTES, 18.0);

        return Optional.of(new TravelEstimate(
                fromPoi.poiId(),
                toPoi.poiId(),
                fromPoi.poiBasic().businessArea(),
                toPoi.poiBasic().businessArea(),
                rounded(distanceKm, 2),
                rounded(estimatedMinutes, 2),
                TAXI_MODE,
                CROSS_AREA_FALLBACK_SOURCE
        ));
    }

    private double rounded(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double haversineKm(double startLat, double startLng, double endLat, double endLng) {
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(endLat - startLat);
        double lngDistance = Math.toRadians(endLng - startLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}


