import { describe, expect, it } from "vitest";
import { getCoordinateStops, toAmapPath } from "./routeMap";
import type { GeneratedRouteStop } from "../types";

const baseStop: GeneratedRouteStop = {
  stop_order: 1,
  slot_role: "dinner",
  poi_id: "P10001",
  poi_name: "Dinner A",
  business_area: "sanlitun",
  district: "chaoyang",
  lng: 116.4567,
  lat: 39.9345,
  coordinate_system: "GCJ-02",
  category_lv1: "food",
  indoor_outdoor: "indoor",
  arrive_time: "18:00",
  leave_time: "19:10",
  stay_minutes: 70,
  travel_minutes_from_prev: 0,
  distance_km_from_prev: 0,
  estimated_cost: 220,
  stop_score: 88,
  matched_prefer_tags: ["photo"],
  matched_avoid_tags: []
};

describe("routeMap helpers", () => {
  it("filters out stops with missing coordinates without throwing", () => {
    const path = toAmapPath([
      baseStop,
      {
        ...baseStop,
        stop_order: 2,
        poi_id: "P10002",
        poi_name: "Cafe B",
        lng: null,
        lat: null
      }
    ]);

    expect(path).toEqual([[116.4567, 39.9345]]);
    expect(getCoordinateStops([
      baseStop,
      {
        ...baseStop,
        stop_order: 3,
        poi_id: "P10003",
        poi_name: "Walk C",
        lng: undefined,
        lat: undefined
      }
    ])).toHaveLength(1);
  });
});
