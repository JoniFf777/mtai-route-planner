import { useEffect, useMemo, useRef, useState } from "react";
import { loadAmap } from "../amap";
import type { GeneratedRouteStop } from "../types";
import {
  extractWalkingPolylinePaths,
  getCoordinateStops,
  getRouteMapStatus,
  getStopCoordinateSignature,
  getWalkingFallbackStatus,
  toAmapPath
} from "../utils/routeMap";

interface RouteMapProps {
  stops: GeneratedRouteStop[];
}

const amapKey = import.meta.env.VITE_AMAP_KEY || "";

export default function RouteMap({ stops }: RouteMapProps) {
  const mapRef = useRef<HTMLDivElement | null>(null);
  const [mapStatus, setMapStatus] = useState("");
  const coordinateStops = useMemo(() => getCoordinateStops(stops), [stops]);
  const routeSignature = useMemo(() => getStopCoordinateSignature(stops), [stops]);

  useEffect(() => {
    if (!amapKey) {
      console.warn("[Amap] Missing VITE_AMAP_KEY. Falling back to text-only route panel.");
      setMapStatus("设置 VITE_AMAP_KEY 后即可显示高德地图。");
      return;
    }
    if (!mapRef.current) {
      return;
    }
    if (coordinateStops.length === 0) {
      console.warn("[Amap] Route stops have no usable coordinates.");
      setMapStatus(getRouteMapStatus("no-coordinates"));
      return;
    }

    let disposed = false;
    let mapInstance: AMapMapInstance | null = null;
    let walkingInstance: AMapWalkingInstance | null = null;

    loadAmap(amapKey)
      .then(async (AMap) => {
        if (disposed || !mapRef.current) {
          return;
        }

        mapInstance = new AMap.Map(mapRef.current, {
          viewMode: "2D",
          zoom: 12,
          center: [coordinateStops[0].lng, coordinateStops[0].lat],
          resizeEnable: true,
          mapStyle: "amap://styles/whitesmoke"
        });

        const markers = coordinateStops.map((stop) => {
          const marker = new AMap.Marker({
            position: [stop.lng, stop.lat],
            title: stop.poi_name,
            label: {
              content: `<div class="map-stop-badge">${stop.stop_order}</div>`,
              direction: "top",
              offset: [0, -8]
            }
          });
          const infoWindow = new AMap.InfoWindow({
            content: `
              <div class="map-info-card">
                <strong>${stop.stop_order}. ${stop.poi_name}</strong>
                <div>${stop.arrive_time} - ${stop.leave_time}</div>
                <div>${stop.category_lv1} · ${stop.business_area} · ${stop.district}</div>
              </div>
            `
          });
          marker.on("click", () => infoWindow.open(mapInstance, [stop.lng, stop.lat]));
          return marker;
        });

        const overlays: unknown[] = [...markers];
        if (coordinateStops.length >= 2) {
          try {
            walkingInstance = await loadWalkingPlanner(AMap);
            const walkingPaths = await planWalkingSegments(walkingInstance, coordinateStops);
            if (disposed) {
              return;
            }
            if (walkingPaths.length > 0) {
              overlays.push(...walkingPaths.map((path) => createRoutePolyline(AMap, path)));
              setMapStatus(getRouteMapStatus("walking"));
            } else {
              console.warn("[Amap] Walking planning returned no usable path. Falling back to direct polyline.");
              overlays.push(createRoutePolyline(AMap, toAmapPath(coordinateStops)));
              setMapStatus(getWalkingFallbackStatus("未返回可用路径"));
            }
          } catch (error) {
            const reason = asErrorReason(error);
            console.warn(`[Amap] Walking planning failed: ${reason}`);
            overlays.push(createRoutePolyline(AMap, toAmapPath(coordinateStops)));
            setMapStatus(getWalkingFallbackStatus(reason));
          }
        } else {
          console.warn("[Amap] Fewer than 2 coordinate stops available. Falling back to direct polyline.");
          setMapStatus(getRouteMapStatus("fallback"));
        }

        if (disposed) {
          return;
        }
        mapInstance.add(overlays);
        mapInstance.setFitView(overlays);
      })
      .catch((error: Error) => {
        if (!disposed) {
          const reason = asErrorReason(error);
          console.warn(`[Amap] Map load failed: ${reason}`);
          setMapStatus(error.message || "地图加载失败。");
        }
      });

    return () => {
      disposed = true;
      walkingInstance?.clear?.();
      mapInstance?.destroy();
    };
  }, [coordinateStops, routeSignature]);

  return (
    <section className="surface-card map-shell">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Route Map</p>
          <h2>高德路线视图</h2>
        </div>
        <span className="status-chip">{coordinateStops.length} 个可定位停靠点</span>
      </div>
      <div ref={mapRef} className="map-canvas" />
      {mapStatus ? <p className="helper-text">{mapStatus}</p> : null}
    </section>
  );
}

function createRoutePolyline(AMap: AMapConstructor, path: Array<[number, number]>) {
  return new AMap.Polyline({
    path,
    strokeColor: "#f97316",
    strokeWeight: 6,
    strokeOpacity: 0.88,
    lineJoin: "round"
  });
}

function loadWalkingPlanner(AMap: AMapConstructor): Promise<AMapWalkingInstance> {
  return new Promise((resolve, reject) => {
    if (typeof AMap.plugin !== "function") {
      reject(new Error("walking plugin missing"));
      return;
    }

    AMap.plugin(["AMap.Walking"], () => {
      if (!AMap.Walking) {
        reject(new Error("walking plugin missing"));
        return;
      }
      resolve(
        new AMap.Walking({
          hideMarkers: true,
          autoFitView: false
        })
      );
    });
  });
}

async function planWalkingSegments(
  walking: AMapWalkingInstance,
  stops: Array<{ lng: number; lat: number }>
): Promise<Array<Array<[number, number]>>> {
  const segments: Array<Array<[number, number]>> = [];

  for (let index = 0; index < stops.length - 1; index += 1) {
    const start: [number, number] = [stops[index].lng, stops[index].lat];
    const end: [number, number] = [stops[index + 1].lng, stops[index + 1].lat];
    const plannedPath = await searchWalkingSegment(walking, start, end);
    if (plannedPath.length < 2) {
      throw new Error(`segment ${index + 1} returned an empty path`);
    }
    segments.push(plannedPath);
  }

  return segments;
}

function searchWalkingSegment(
  walking: AMapWalkingInstance,
  start: [number, number],
  end: [number, number]
): Promise<Array<[number, number]>> {
  return new Promise((resolve, reject) => {
    walking.search(start, end, (status, result) => {
      if (status !== "complete") {
        console.warn("[Amap] walking.search failed", { status, result, start, end });
        reject(new Error(`walking.search status=${status}`));
        return;
      }
      const [firstPath] = extractWalkingPolylinePaths(result);
      if (!firstPath) {
        console.warn("[Amap] walking.search returned no usable steps", { result, start, end });
        reject(new Error("walking.search returned no usable steps"));
        return;
      }
      resolve(firstPath);
    });
  });
}

function asErrorReason(error: unknown): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim();
  }
  return "未知原因";
}
