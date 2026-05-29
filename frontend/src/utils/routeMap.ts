import type { GeneratedRouteStop } from "../types";

export interface CoordinateStop extends GeneratedRouteStop {
  lng: number;
  lat: number;
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
