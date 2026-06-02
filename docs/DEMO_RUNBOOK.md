# Demo Runbook

## MVP Architecture Overview

This MVP is a Beijing local-lifestyle route planning demo built around a deterministic Java route engine with optional AI understanding and presentation.

Core responsibilities:

- Frontend: React MVP under `frontend/` for route input, result display, and Amap visualization
- Backend API: Spring Boot natural-language and structured route APIs
- Route engine: deterministic Java planning, validation, adjustment, and clarification logic
- Data source: JSON by default, PostgreSQL optional
- Session state: in-memory by default, Redis optional
- AI layer: fake intent/presenter by default, Spring AI optional
- Events: no-op by default, RocketMQ producer optional

Design rule:

```text
LLM understands and explains.
Java retrieves, validates, optimizes, and decides.
```

## Required Dependencies

- Java 21
- Maven wrapper (`.\mvnw.cmd`)
- Node.js and npm
- Docker
- PostgreSQL
- Redis
- RocketMQ optional
- Amap key optional for map rendering
- Spring AI API key optional for real AI mode

## Environment Variables

Backend AI mode:

```powershell
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode"
$env:OPENAI_MODEL="deepseek-v4-flash"
```

Frontend:

```powershell
$env:VITE_API_BASE_URL="http://localhost:8080"
$env:VITE_AMAP_KEY="your-amap-key"
```

Recommended frontend local file:

```text
frontend/.env.local
```

Example contents:

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_AMAP_KEY=your-amap-key
```

Warnings:

- Do not commit real API keys.
- `VITE_AMAP_KEY` is only required for map rendering.
- `OPENAI_*` variables are only required for Spring AI intent/presenter mode.

## Default Local Ports

- Backend: `8080`
- Frontend: `5173`
- PostgreSQL: `5432`
- Redis: `6379`
- RocketMQ NameServer: `9876`
- RocketMQ Broker: `10911`

## Quick Start A: Minimal Local Demo

This is the safest local path and needs no external AI or database credentials.

Mode:

- `route.data.source=json`
- `route.session.store=memory`
- `route.intent.agent=fake`
- `route.presenter.agent=fake`
- `route.events.publisher=noop`
- frontend text panel works
- Amap optional

Backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

Result:

- Open `http://localhost:5173`
- Generate a route with the default sample text
- Route list, budget, duration, distance, and presenter text should work
- If no Amap key is set, textual verification still works

## Quick Start B: Full Local Demo

This mode shows the full current MVP stack.

Mode:

- `route.data.source=postgres`
- `route.session.store=redis`
- `route.intent.agent=spring-ai`
- `route.presenter.agent=spring-ai`
- `route.events.publisher=rocketmq`
- frontend Amap map enabled

### 1. Start Infrastructure

```powershell
docker compose up -d postgres redis rocketmq-nameserver rocketmq-broker
```

### 2. Set Optional AI And Map Variables

```powershell
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode"
$env:OPENAI_MODEL="deepseek-v4-flash"
```

Create `frontend/.env.local`:

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_AMAP_KEY=your-amap-key
```

### 3. Load PostgreSQL Mock Data

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--mock-data.load-to-db=true"
```

### 4. Run Backend In Full Mode

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=spring-ai" "-Dspring-boot.run.arguments=--route.data.source=postgres --route.session.store=redis --route.intent.agent=spring-ai --route.presenter.agent=spring-ai --route.events.publisher=rocketmq"
```

### 5. Run Frontend

```powershell
cd frontend
npm install
npm run dev
```

### 6. Demo End-To-End

- Open `http://localhost:5173`
- Generate a route
- Adjust the route
- Refresh the session
- Confirm map markers and polyline render when coordinates exist
- Confirm Redis, PostgreSQL, and RocketMQ checks below

## API Verification Checklist

### Core API Checks

- `POST /api/routes/plan`
- `GET /api/routes/{sessionId}`
- `POST /api/routes/{sessionId}/adjust`

### Suggested Manual Request

Use:

```json
{
  "user_id": "U10001",
  "message": "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。"
}
```

Expected:

- response contains `session_id`
- `status` is usually `SUCCESS`
- response contains route stops
- presenter message explains the finalized Java route

### Redis Session Check

List session keys:

```powershell
docker compose exec redis redis-cli KEYS "route:session:*"
```

Read one session:

```powershell
docker compose exec redis redis-cli GET "route:session:{sessionId}"
```

Expected:

- session key exists
- stored JSON includes `current_intent`, `current_route`, `locked_stop_orders`, `pending_clarification`, `change_history`, `version`

### PostgreSQL Check

List tables:

```powershell
docker compose exec postgres psql -U mtai -d mtai_route_planner -c "\dt"
```

Sample count checks:

```powershell
docker compose exec postgres psql -U mtai -d mtai_route_planner -c "select count(*) from business_area;"
docker compose exec postgres psql -U mtai -d mtai_route_planner -c "select count(*) from poi_basic;"
docker compose exec postgres psql -U mtai -d mtai_route_planner -c "select count(*) from route_template;"
```

Expected:

- schema exists
- mock-data tables contain rows

### Frontend Map Check

- page loads at `http://localhost:5173`
- result panel shows session id, status, message, stops, budget, duration, distance
- map markers render with stop-order labels
- clicking a marker shows stop name, time, category, and business area
- if coordinates are missing, textual panel still works without crashing

### RocketMQ Check

RocketMQ is optional and async only.

Verify by backend logs after planning or adjustment:

- `ROUTE_PLANNED`
- `ROUTE_ADJUSTED`
- `ROUTE_ADJUSTMENT_FAILED`
- `ROUTE_WAITING_CLARIFICATION`
- `ROUTE_CLARIFICATION_RESOLVED`

Expected:

- when RocketMQ is available, publish success logs appear
- when RocketMQ is unavailable, API still succeeds and warnings are logged safely

## Troubleshooting

### Port 8080 Already In Use

Symptom:

- backend fails to start

Fix:

- stop the existing process on `8080`
- or run temporarily on another port, for example:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

### Redis NOAUTH

Symptom:

- Redis-backed mode fails with authentication errors

Fix:

- confirm your Redis server matches local demo config
- if using a protected Redis instance, add matching Spring Redis password properties before startup

### PostgreSQL Password Mismatch

Symptom:

- datasource connection fails

Fix:

- confirm Docker Compose credentials:
  - database: `mtai_route_planner`
  - username: `mtai`
  - password: `mtai_dev_password`
- or override `POSTGRES_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`

### Flyway Unsupported Database Module

Symptom:

- startup fails during migration with unsupported PostgreSQL version

Fix:

- ensure the project dependency for PostgreSQL Flyway support is present
- current project already includes the PostgreSQL-specific Flyway module; if this returns, re-check dependency resolution

### Amap Key Missing

Symptom:

- frontend text renders but map does not

Fix:

- add `VITE_AMAP_KEY` in `frontend/.env.local`
- restart `npm run dev`

### Spring AI 404 From Wrong Base URL

Symptom:

- Spring AI request returns 404 against OpenAI-compatible providers

Fix:

- use a base URL without the trailing `/v1`
- current recommended compatible default:

```text
https://dashscope.aliyuncs.com/compatible-mode
```

### Spring AI Falls Back To Fake Parser

Symptom:

- route still works, but logs show fallback to fake parser or fake presenter

Fix:

- check Spring AI output validity
- check prompt/provider compatibility
- check whether the model returned invalid structured output
- this fallback is expected safety behavior in MVP

### Frontend CORS

Symptom:

- browser blocks calls from `5173` to backend

Fix:

- ensure backend is using the current local CORS config
- default allowed local origins include `http://localhost:5173` and `http://127.0.0.1:5173`

### RocketMQ Unavailable But API Still Succeeds

Symptom:

- warning logs about event publishing, but route API still returns success

Fix:

- this is expected
- RocketMQ is async only and not part of synchronous route generation

## MVP Status

### Implemented

- Mock data generation
- JSON and PostgreSQL data sources
- Redis session
- Spring AI IntentAgent
- Spring AI PresenterAgent
- Java route engine
- dynamic adjustment
- clarification
- RocketMQ producer-side events
- React frontend
- Amap visualization

### Not Implemented Yet

- real merchant data
- login/auth
- production observability
- RocketMQ consumers
- analytics dashboard
- real payment/order/coupon system
- production deployment

## Related Docs

- `docs/DEMO_RUNBOOK.md`
- `docs/DEV_API_TESTING.md`
- `docs/FRONTEND_MVP.md`
