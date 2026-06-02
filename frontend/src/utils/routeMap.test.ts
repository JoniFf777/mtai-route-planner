import { describe, expect, it } from "vitest";
import {
  extractWalkingPolylinePaths,
  getCoordinateStops,
  getRouteMapStatus,
  getStopCoordinateSignature,
  getWalkingFallbackStatus,
  toAmapPath
} from "./routeMap";
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
    expect(
      getCoordinateStops([
        baseStop,
        {
          ...baseStop,
          stop_order: 3,
          poi_id: "P10003",
          poi_name: "Walk C",
          lng: undefined,
          lat: undefined
        }
      ])
    ).toHaveLength(1);
  });

  it("keeps route stop order when extracting coordinate stops", () => {
    const stops = getCoordinateStops([
      {
        ...baseStop,
        stop_order: 3,
        poi_id: "P10003",
        poi_name: "Dessert C",
        lng: 116.49,
        lat: 39.93
      },
      {
        ...baseStop,
        stop_order: 1,
        poi_id: "P10001",
        poi_name: "Dinner A"
      },
      {
        ...baseStop,
        stop_order: 2,
        poi_id: "P10002",
        poi_name: "Cafe B",
        lng: 116.47,
        lat: 39.94
      }
    ]);

    expect(stops.map((stop) => stop.stop_order)).toEqual([3, 1, 2]);
  });

  it("derives user-facing status text for walking, fallback, and no-coordinates", () => {
    expect(getRouteMapStatus("walking")).toBe("道路路径由高德步行规划生成");
    expect(getRouteMapStatus("fallback")).toBe("当前为坐标直连，仅供参考");
    expect(getRouteMapStatus("no-coordinates")).toBe("当前路线暂无可用坐标");
  });

  it("formats a fallback status with a short reason when provided", () => {
    expect(getWalkingFallbackStatus()).toBe("高德步行规划失败，已降级为坐标直连");
    expect(getWalkingFallbackStatus("plugin missing")).toBe(
      "高德步行规划失败，已降级为坐标直连（plugin missing）"
    );
  });

  it("extracts walking route paths from Amap-style step results", () => {
    const paths = extractWalkingPolylinePaths({
      routes: [
        {
          steps: [
            {
              path: [
                { lng: 116.4567, lat: 39.9345 },
                { lng: 116.457, lat: 39.935 }
              ]
            },
            {
              path: [
                {
                  getLng: () => 116.457,
                  getLat: () => 39.935
                },
                {
                  getLng: () => 116.4581,
                  getLat: () => 39.9358
                }
              ]
            }
          ]
        }
      ]
    });

    expect(paths).toEqual([[[116.4567, 39.9345], [116.457, 39.935], [116.4581, 39.9358]]]);
  });

  it("builds a stable coordinate signature for route-stop changes", () => {
    expect(
      getStopCoordinateSignature([
        baseStop,
        {
          ...baseStop,
          stop_order: 2,
          poi_id: "P10002",
          poi_name: "Cafe B",
          lng: null,
          lat: null
        }
      ])
    ).toContain("1:P10001:Dinner A:sanlitun:chaoyang:food:116.4567:39.9345:18:00:19:10");
  });
});
