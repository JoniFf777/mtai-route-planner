package com.mtai.mtairouteplanner.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompactRouteStop(
        int stopOrder,
        String slotRole,
        String poiId,
        String poiName,
        String businessArea,
        String arriveTime,
        String leaveTime
) {
    public static CompactRouteStop from(GeneratedRouteStop stop) {
        return new CompactRouteStop(
                stop.stopOrder(),
                stop.slotRole(),
                stop.poiId(),
                stop.poiName(),
                stop.businessArea(),
                stop.arriveTime(),
                stop.leaveTime()
        );
    }
}
