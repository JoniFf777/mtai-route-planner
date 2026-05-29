# Frontend MVP

## Overview

This MVP frontend is a small Vite + React + TypeScript app under `frontend/`.
It calls the existing backend natural-language route APIs and renders returned route stops on an Amap/Gaode map when coordinates are available.

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

```bash
VITE_API_BASE_URL=http://localhost:8080
VITE_AMAP_KEY=your-amap-key
```

Notes:

- `VITE_API_BASE_URL` should point to the backend host.
- `VITE_AMAP_KEY` is required only for map rendering.
- If `VITE_AMAP_KEY` is missing, the text route panel still works.
- Do not commit real Amap keys.

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
   - a route polyline is drawn between stops

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

- Route stop coordinates are now included in the API response.
- The backend returns GCJ-02 coordinates.
- The frontend sends coordinates to Amap in `[lng, lat]` order.

## Known Limitations

- No login or auth.
- No merchant detail pages.
- No turn-by-turn navigation.
- Polyline rendering is a simple straight-line connection between stops.
- If coordinates are missing for a stop, the text panel still renders and the map skips that stop safely.
- The frontend only visualizes finalized backend route results. It does not choose or optimize routes by itself.
