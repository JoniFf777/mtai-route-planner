# DEV API Testing

This document explains how to run and manually verify the current Java-only demo flow.

## 1. Run Tests

```powershell
.\mvnw.cmd test
```

## 2. Start Infrastructure With Docker Compose

Redis is only required for Redis-backed session storage and the optional Redis integration tests.

PostgreSQL is only required for the optional PostgreSQL-backed mock-data source and the gated PostgreSQL integration tests.

RocketMQ is optional. It is producer-side only in the current phase and is never required for API success.

```powershell
docker compose up -d redis postgres rocketmq-nameserver rocketmq-broker
```

Default local ports:

```text
Redis: localhost:6379
PostgreSQL: localhost:5432
RocketMQ NameServer: localhost:9876
RocketMQ Broker: localhost:10911
```

Default local PostgreSQL demo credentials:

```text
database: mtai_route_planner
username: mtai
password: mtai_dev_password
```

Warnings:

- Do not commit real database passwords or real API keys for non-demo environments.
- RocketMQ is async only in this project. Route planning and route adjustment must still succeed even if RocketMQ is unavailable.

## 3. Start The App In Default Local Mode

This is the default local development mode:

- `route.data.source=json`
- `route.session.store=memory`
- `route.intent.agent=fake`
- `route.presenter.agent=fake`
- `route.events.publisher=noop`
- `spring.ai.model.chat=none`

```powershell
.\mvnw.cmd spring-boot:run
```

Equivalent explicit command:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.data.source=json --route.session.store=memory --route.intent.agent=fake --route.presenter.agent=fake --route.events.publisher=noop --spring.ai.model.chat=none"
```

Important notes:

- JSON remains the default data source.
- No Redis, PostgreSQL, RocketMQ, or OpenAI API key is required in this mode.

## 4. Start The App In Redis Session Mode

Make sure Redis is already running, then start the app in Redis session mode:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.data.source=json --route.session.store=redis --route.intent.agent=fake --route.presenter.agent=fake --route.events.publisher=noop --spring.ai.model.chat=none"
```

## 5. Configure Spring AI Environment Variables

Spring AI intent and presenter modes are optional and are not the default.

PowerShell:

```powershell
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode"
$env:OPENAI_MODEL="deepseek-v4-flash"
```

Notes:

- `OPENAI_BASE_URL` is optional. In the `spring-ai` profile the default is DashScope-compatible `https://dashscope.aliyuncs.com/compatible-mode`.
- `OPENAI_MODEL` is optional. In the `spring-ai` profile the default is `deepseek-v4-flash`.
- Do not commit real API keys into `application.yml`, profile files, or source control.

## 6. Start The App In Spring AI Intent Mode

Shortest profile-based command:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=spring-ai"
```

Equivalent short command without the profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.intent.agent=spring-ai --spring.ai.model.chat=openai"
```

Current limitation:

- Natural-language understanding can use Spring AI in this mode.
- If a Spring AI parsed planning request is valid but produces no feasible route, the app retries once with the fake parsed request as a safe fallback.

## 7. Start The App In Spring AI Presenter Mode

Presenter-only command:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=spring-ai" "-Dspring-boot.run.arguments=--route.intent.agent=fake --route.presenter.agent=spring-ai"
```

## 8. Start The App In Spring AI Intent + Presenter Mode

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=spring-ai" "-Dspring-boot.run.arguments=--route.intent.agent=spring-ai --route.presenter.agent=spring-ai"
```

Warnings:

- Presenter only explains finalized Java routes.
- Presenter must not add, remove, reorder, or reinterpret stops.
- Route selection, timing, and budget decisions still come from Java services.

## 9. Load Mock Data Into PostgreSQL

Keep `route.data.source=json` for the load step. The loader reads the existing JSON and JSONL files under `src/main/resources/mock-data/` and writes them into PostgreSQL with deterministic truncate-and-insert behavior.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--mock-data.load-to-db=true"
```

Recommended flow:

1. Start PostgreSQL with Docker Compose.
2. Run the load command above once.
3. Stop that Spring Boot process after the load finishes.
4. Start the app again in `route.data.source=postgres` mode.

Important notes:

- JSON remains the default local demo source.
- The load step does not remove the existing mock JSON and JSONL resources.

## 10. Run The App In PostgreSQL Data Mode

Make sure PostgreSQL is already running and mock data has been loaded first.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.data.source=postgres"
```

Equivalent explicit JSON mode:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.data.source=json"
```

Relevant PostgreSQL properties:

```text
route.data.source=postgres
spring.datasource.url=${POSTGRES_URL:jdbc:postgresql://localhost:5432/mtai_route_planner}
spring.datasource.username=${POSTGRES_USER:mtai}
spring.datasource.password=${POSTGRES_PASSWORD:mtai_dev_password}
spring.datasource.driver-class-name=org.postgresql.Driver
```

## 11. Run Without RocketMQ

This is the default and recommended local mode while building route logic.

```powershell
.\mvnw.cmd spring-boot:run
```

Equivalent explicit no-op event mode:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.events.publisher=noop"
```

Important note:

- Even if RocketMQ is down or not started, route planning and route adjustment APIs should still work in no-op mode.

## 12. Run With RocketMQ Event Publishing

Start RocketMQ first:

```powershell
docker compose up -d rocketmq-nameserver rocketmq-broker
```

Then run the app with RocketMQ publishing enabled:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.events.publisher=rocketmq"
```

Equivalent combined example with PostgreSQL data mode:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--route.data.source=postgres --route.events.publisher=rocketmq"
```

Relevant RocketMQ properties:

```text
route.events.publisher=rocketmq
route.events.topic=mtai-route-events
rocketmq.name-server=${ROCKETMQ_NAME_SERVER:localhost:9876}
rocketmq.producer.group=${ROCKETMQ_PRODUCER_GROUP:mtai-route-event-producer}
```

Warning:

- RocketMQ is async only and is never part of the synchronous route-generation decision path.

## 13. Published Route Events

Current producer-side event types:

- `ROUTE_PLANNED`
- `ROUTE_PLAN_FAILED`
- `ROUTE_ADJUSTED`
- `ROUTE_ADJUSTMENT_FAILED`
- `ROUTE_WAITING_CLARIFICATION`
- `ROUTE_CLARIFICATION_RESOLVED`

Compact payload fields:

- `event_id`
- `event_type`
- `session_id`
- `user_id`
- `route_scene`
- `route_status`
- `change_type` when applicable
- `route_summary` when applicable
- `issue_reason` when applicable
- `created_at`

The event payload does not include full POI datasets, full UGC summaries, embedding docs, full route history, or full Redis session state.

## 14. Verify RocketMQ Events Locally

The simplest local verification path is log-based:

1. Start RocketMQ with Docker Compose.
2. Start the app with `--route.events.publisher=rocketmq`.
3. Call a planning or adjustment API.
4. Check the application log for lines like:

```text
Published route event to RocketMQ. topic=mtai-route-events eventType=ROUTE_PLANNED eventId=...
```

If RocketMQ is unavailable, the API should still succeed and the app should log a warning instead of failing the request.

## 15. Current API Layers

### Dev Structured API

- `POST /api/dev/routes/plan-structured`
- `GET /api/dev/routes/{sessionId}`
- `POST /api/dev/routes/{sessionId}/adjust-structured`
- `GET /api/dev/routes/{sessionId}/context`
- `POST /api/dev/routes/{sessionId}/clarification/answer`

### User-Facing Natural-Language API

- `POST /api/routes/plan`
- `GET /api/routes/{sessionId}`
- `POST /api/routes/{sessionId}/adjust`

The same user-facing natural-language API is used in fake mode and Spring AI mode. Only the parser and presenter behind it change.

## 16. Manual Verification With curl

Below examples assume the app is already running on `localhost:8080`.

### A. Create Natural-Language Route

```bash
curl -X POST "http://localhost:8080/api/routes/plan" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。\"}"
```

Expected result:

- `status` should be `SUCCESS`
- response should contain `session_id`
- response should contain `route`
- response `message` should include route summary and stop details

### B. Query Session

Replace `{sessionId}` with the value returned from step A.

```bash
curl "http://localhost:8080/api/routes/{sessionId}"
```

### C. Lock Second Stop

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"第二站别动\"}"
```

### D. Lower Budget

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"预算降到300\"}"
```

### E. Add Coffee

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"加一个咖啡\"}"
```

### F. Switch To Indoor

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"今天下雨，改成室内\"}"
```

## 17. Optional Structured API Checks

If you want to verify the Java engine without natural-language parsing, test the dev structured API directly.

```bash
curl -X POST "http://localhost:8080/api/dev/routes/plan-structured" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"scene\":\"情侣约会\",\"business_area\":\"三里屯\",\"district\":\"朝阳区\",\"time_window\":\"18:00-22:00\",\"budget_total\":500,\"party_size\":2,\"pace\":\"轻松\",\"prefer_tags\":[\"拍照\"],\"avoid_tags\":[\"排队\"]}"
```

## 18. Clarification Flow Check

Some adjustment requests are intentionally ambiguous. For example:

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"换掉\"}"
```

Possible expected result:

- `status = WAITING_CLARIFICATION`
- response `message` should contain a clarification question
- session should keep the current route unchanged

Then answer it:

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"第一个\"}"
```

## 19. Verify Session Persistence Across Restart In Redis Mode

This check applies only when `route.session.store=redis`.

1. Start Redis with Docker Compose.
2. Start the app in Redis mode.
3. Create a route with `POST /api/routes/plan` and note the returned `session_id`.
4. Stop the Spring Boot app.
5. Start the Spring Boot app again in Redis mode.
6. Query `GET /api/routes/{sessionId}` using the same `session_id`.

Expected result:

- the session should still exist
- `current_route` should still be present
- `current_intent` should still be present
- `locked_stop_orders`, `pending_clarification`, and `change_history` should remain if they were previously written

## 20. Optional Integration Tests

Redis integration tests are disabled by default so normal `.\mvnw.cmd test` passes without Redis.

```powershell
.\mvnw.cmd test -Droute.redis.tests=true
```

PostgreSQL integration tests are disabled by default so normal `.\mvnw.cmd test` passes without PostgreSQL.

```powershell
.\mvnw.cmd test -Droute.postgres.tests=true
```

No live RocketMQ integration test is enabled by default in the current phase. Current RocketMQ coverage is unit-level only, so normal `.\mvnw.cmd test` does not require a running RocketMQ server.
