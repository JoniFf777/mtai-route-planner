import { useEffect, useRef, useState } from "react";
import { loadAmap } from "../amap";
import type { GeneratedRouteStop } from "../types";
import { getCoordinateStops, toAmapPath } from "../utils/routeMap";

interface RouteMapProps {
  stops: GeneratedRouteStop[];
}

const amapKey = import.meta.env.VITE_AMAP_KEY || "";

export default function RouteMap({ stops }: RouteMapProps) {
  const mapRef = useRef<HTMLDivElement | null>(null);
  const [mapStatus, setMapStatus] = useState("");
  const coordinateStops = getCoordinateStops(stops);

  useEffect(() => {
    if (!amapKey) {
      setMapStatus("设置 VITE_AMAP_KEY 后即可显示高德地图。");
      return;
    }
    if (!mapRef.current) {
      return;
    }
    if (coordinateStops.length === 0) {
      setMapStatus("当前路线只有文本结果，尚无可绘制的坐标点。");
      return;
    }

    let disposed = false;
    let mapInstance: AMapMapInstance | null = null;

    loadAmap(amapKey)
      .then((AMap) => {
        if (disposed || !mapRef.current) {
          return;
        }
        setMapStatus("");
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
          overlays.push(
            new AMap.Polyline({
              path: toAmapPath(coordinateStops),
              strokeColor: "#f97316",
              strokeWeight: 6,
              strokeOpacity: 0.88,
              lineJoin: "round"
            })
          );
        }

        mapInstance.add(overlays);
        mapInstance.setFitView(overlays);
      })
      .catch((error: Error) => {
        if (!disposed) {
          setMapStatus(error.message || "地图加载失败。");
        }
      });

    return () => {
      disposed = true;
      mapInstance?.destroy();
    };
  }, [coordinateStops]);

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
