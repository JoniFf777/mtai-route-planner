const AMAP_SCRIPT_URL = "https://webapi.amap.com/maps";
const amapSecurityCode = import.meta.env.VITE_AMAP_SECURITY_CODE || "";

export async function loadAmap(amapKey: string): Promise<AMapConstructor> {
  if (!amapKey) {
    console.warn("[Amap] Missing VITE_AMAP_KEY.");
    throw new Error("Missing VITE_AMAP_KEY.");
  }
  if (window.AMap) {
    return window.AMap;
  }
  if (!window.__amapLoaderPromise__) {
    applyAmapSecurityConfig(amapSecurityCode);
    window.__amapLoaderPromise__ = new Promise<AMapConstructor>((resolve, reject) => {
      const existingScript = document.querySelector<HTMLScriptElement>("script[data-amap-loader='true']");
      if (existingScript) {
        existingScript.addEventListener("load", () => {
          if (window.AMap) {
            resolve(window.AMap);
            return;
          }
          reject(new Error("AMap script loaded but window.AMap is unavailable."));
        });
        existingScript.addEventListener("error", () => reject(new Error("Failed to load Amap script.")));
        return;
      }

      const script = document.createElement("script");
      script.src = `${AMAP_SCRIPT_URL}?v=2.0&key=${encodeURIComponent(amapKey)}&plugin=${encodeURIComponent("AMap.Walking")}`;
      script.async = true;
      script.dataset.amapLoader = "true";
      script.onload = () => {
        if (window.AMap) {
          resolve(window.AMap);
          return;
        }
        reject(new Error("AMap script loaded but window.AMap is unavailable."));
      };
      script.onerror = () => reject(new Error("Failed to load Amap script."));
      document.head.appendChild(script);
    });
  }
  return window.__amapLoaderPromise__;
}

function applyAmapSecurityConfig(securityJsCode: string) {
  if (!securityJsCode) {
    return;
  }
  window._AMapSecurityConfig = {
    securityJsCode
  };
}
