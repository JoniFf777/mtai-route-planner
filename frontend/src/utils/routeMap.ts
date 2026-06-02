import type { GeneratedRouteStop } from "../types";

export interface CoordinateStop extends GeneratedRouteStop {
  lng: number;
  lat: number;
}

export type RouteMapRenderMode = "walking" | "fallback" | "no-coordinates";

export const ROUTE_MAP_STATUS_TEXT: Record<RouteMapRenderMode, string> = {
  walking: "道路路径由高德步行规划生成",
  fallback: "当前为坐标直连，仅供参考",
  "no-coordinates": "当前路线暂无可用坐标"
};

interface AmapPointLike {
  lng?: number;
  lat?: number;
  getLng?: () => number;
  getLat?: () => number;
}

interface AmapWalkingStepLike {
  path?: AmapPointLike[];
}

interface AmapWalkingRouteLike {
  steps?: AmapWalkingStepLike[];
}

interface AmapWalkingResultLike {
  routes?: AmapWalkingRouteLike[];
}

export function getCoordinateStops(stops: GeneratedRouteStop[]): CoordinateStop[] {
  return stops.filter(hasCoordinates).map((stop) => ({
    ...stop,
    lng: stop.lng as number,
    lat: stop.lat as number
  }));
}

export function hasCoordinates(stop: GeneratedRouteStop): stop is CoordinateStop {
  return Number.isFinite(stop.lng) && Number.isFinite(stop.lat);
}

export function toAmapPath(stops: GeneratedRouteStop[]): Array<[number, number]> {
  return getCoordinateStops(stops).map((stop) => [stop.lng, stop.lat]);
}

export function getRouteMapStatus(mode: RouteMapRenderMode): string {
  return ROUTE_MAP_STATUS_TEXT[mode];
}

export function getWalkingFallbackStatus(reason?: string): string {
  const normalizedReason = normalizeStatusReason(reason);
  if (!normalizedReason) {
    return "高德步行规划失败，已降级为坐标直连";
  }
  return `高德步行规划失败，已降级为坐标直连（${normalizedReason}）`;
}

export function getStopCoordinateSignature(stops: GeneratedRouteStop[]): string {
  return stops
    .map((stop) =>
      [
        stop.stop_order,
        stop.poi_id,
        stop.poi_name,
        stop.business_area,
        stop.district,
        stop.category_lv1,
        stop.lng ?? "null",
        stop.lat ?? "null",
        stop.arrive_time,
        stop.leave_time
      ].join(":")
    )
    .join("|");
}

export function extractWalkingPolylinePaths(
  result: AmapWalkingResultLike
): Array<Array<[number, number]>> {
  return (result.routes ?? [])
    .map((route) => flattenWalkingSteps(route.steps ?? []))
    .filter((path) => path.length >= 2);
}

function flattenWalkingSteps(steps: AmapWalkingStepLike[]): Array<[number, number]> {
  const flattened: Array<[number, number]> = [];

  for (const step of steps) {
    for (const point of step.path ?? []) {
      const normalized = normalizeAmapPoint(point);
      if (!normalized) {
        continue;
      }
      const previous = flattened[flattened.length - 1];
      if (previous && previous[0] === normalized[0] && previous[1] === normalized[1]) {
        continue;
      }
      flattened.push(normalized);
    }
  }

  return flattened;
}

function normalizeAmapPoint(point: AmapPointLike): [number, number] | null {
  const lng = typeof point.getLng === "function" ? point.getLng() : point.lng;
  const lat = typeof point.getLat === "function" ? point.getLat() : point.lat;
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    return null;
  }
  return [lng as number, lat as number];
}

function normalizeStatusReason(reason?: string): string | null {
  if (!reason) {
    return null;
  }
  const trimmed = reason.trim();
  if (!trimmed) {
    return null;
  }
  return trimmed.length > 36 ? `${trimmed.slice(0, 36)}...` : trimmed;
}
