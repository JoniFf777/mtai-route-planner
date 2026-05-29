/// <reference types="vite/client" />

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

interface AMapConstructor {
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
}

declare global {
  interface Window {
    AMap?: AMapConstructor;
    __amapLoaderPromise__?: Promise<AMapConstructor>;
  }
}

export {};
