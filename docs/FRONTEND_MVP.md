# Frontend MVP

## Overview

The frontend MVP is a small Vite + React + TypeScript app under `frontend/`.
It calls the backend natural-language route APIs and renders returned route stops on an Amap/Gaode map when coordinates are available.
When an Amap key is configured, the frontend first tries to use Amap walking route planning between consecutive stops so the displayed path follows roads instead of a straight chord.

## Start The Backend

The frontend expects the backend to be running first.

Default local startup:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend tests:

```powershell
.\mvnw.cmd test
```

## Frontend Environment Variables

Create `frontend/.env.local` from `frontend/.env.example` and set:

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_AMAP_KEY=your-amap-key
VITE_AMAP_SECURITY_CODE=your-security-js-code
```

Notes:

- `VITE_API_BASE_URL` should point to the backend host.
- `VITE_AMAP_KEY` is required for Amap rendering.
- `VITE_AMAP_SECURITY_CODE` may be required for newer Amap keys that enforce `securityJsCode`.
- If `VITE_AMAP_KEY` is missing, the text route panel still works.
- Do not commit real Amap keys or security codes.

## Start The Frontend

Install dependencies:

```powershell
cd frontend
npm install
```

Run the development server:

```powershell
npm run dev
```

Default local URL:

```text
http://localhost:5173
```

Build validation:

```powershell
npm run build
```

Optional lightweight frontend test:

```powershell
npm run test
```

## How To Test Route Generation

1. Start the backend.
2. Start the frontend.
3. Open `http://localhost:5173`.
4. Keep the default `user_id` as `U10001`.
5. Use a sample message such as:

```text
今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。
```

6. Click `Generate Route`.
7. Confirm the page shows:
   - `session_id`
   - route `status`
   - presenter `message`
   - stop list
   - budget / duration / distance
8. If `VITE_AMAP_KEY` is set and stop coordinates are present, confirm:
   - markers are rendered
   - marker labels show stop order
   - the map note says `道路路径由高德步行规划生成` when Amap walking planning succeeds
   - a road-following path is drawn between stops

## How To Test Adjustment

1. Generate a route first.
2. Enter an adjustment request such as:

```text
第二站别动
```

3. Click `Adjust Route`.
4. Confirm the result panel and map update to the latest route.
5. Use `Refresh Session` to fetch the current backend session state with `GET /api/routes/{sessionId}`.

## Coordinate Notes

- Route stop coordinates are included in the API response.
- The backend returns GCJ-02 coordinates.
- The frontend sends coordinates to Amap in `[lng, lat]` order.
- The frontend keeps backend stop order as the source of truth and only asks Amap to plan walking paths between stop `n` and stop `n+1`.

## Map Path Fallbacks

- If the Amap walking route plugin is unavailable, the search fails, or there are fewer than 2 valid coordinate stops, the map falls back to a straight coordinate connection.
- When fallback happens after a walking attempt, the map note shows `高德步行规划失败，已降级为坐标直连`, optionally with a short reason.
- If no route stop has usable coordinates, the text panel still works and the map note shows `当前路线暂无可用坐标`.
- Check the browser console for Amap loader or walking-planning warnings.

## Known Limitations

- No login or auth.
- No merchant detail pages.
- No turn-by-turn navigation UI.
- Road-following path depends on the client-side Amap walking planner and may fall back to a straight coordinate line.
- If coordinates are missing for a stop, the text panel still renders and the map skips that stop safely.
- The frontend only visualizes finalized backend route results. It does not choose or optimize routes by itself.
