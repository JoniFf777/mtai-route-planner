/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_AMAP_KEY?: string;
  readonly VITE_AMAP_SECURITY_CODE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

interface AMapInfoWindow {
  open(map: unknown, position: [number, number]): void;
}

interface AMapMarker {
  on(eventName: string, handler: () => void): void;
}

interface AMapMapInstance {
  add(items: unknown[]): void;
  setFitView(items?: unknown[]): void;
  destroy(): void;
}

interface AMapLngLatLike {
  lng?: number;
  lat?: number;
  getLng?: () => number;
  getLat?: () => number;
}

interface AMapWalkingStep {
  path?: AMapLngLatLike[];
}

interface AMapWalkingRouteResult {
  steps?: AMapWalkingStep[];
}

interface AMapWalkingSearchResult {
  routes?: AMapWalkingRouteResult[];
}

interface AMapWalkingInstance {
  search(
    start: [number, number],
    end: [number, number],
    callback: (status: string, result: AMapWalkingSearchResult) => void
  ): void;
  clear?(): void;
}

interface AMapConstructor {
  plugin(plugins: string | string[], callback: () => void): void;
  Map: new (
    container: HTMLElement,
    options: {
      viewMode?: string;
      zoom?: number;
      center?: [number, number];
      resizeEnable?: boolean;
      mapStyle?: string;
    }
  ) => AMapMapInstance;
  Marker: new (options: {
    position: [number, number];
    title?: string;
    label?: { content: string; direction?: string; offset?: [number, number] };
  }) => AMapMarker;
  Polyline: new (options: {
    path: Array<[number, number]>;
    strokeColor?: string;
    strokeWeight?: number;
    strokeOpacity?: number;
    lineJoin?: string;
  }) => unknown;
  InfoWindow: new (options: { content: string; offset?: [number, number] }) => AMapInfoWindow;
  Walking?: new (options?: {
    map?: AMapMapInstance;
    hideMarkers?: boolean;
    autoFitView?: boolean;
  }) => AMapWalkingInstance;
}

declare global {
  interface Window {
    AMap?: AMapConstructor;
    __amapLoaderPromise__?: Promise<AMapConstructor>;
    _AMapSecurityConfig?: {
      securityJsCode: string;
    };
  }
}

export {};
