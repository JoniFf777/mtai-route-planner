# DEV API Testing

This document explains how to run and manually verify the current Java-only demo flow.

## 1. Run Tests

```powershell
.\mvnw.cmd test
```

## 2. Start Redis With Docker Compose

Redis is only required for Redis-backed session storage and the optional Redis integration tests.

```powershell
docker compose up -d redis
```

Redis will listen on:

```text
localhost:6379
```

## 3. Start The App In Memory Mode

Memory mode is the default local fallback.

```powershell
.\mvnw.cmd spring-boot:run
```

Equivalent explicit command:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--route.session.store=memory"
```

## 4. Start The App In Redis Mode

Make sure Redis is already running, then start the app in Redis mode:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--route.session.store=redis"
```

Relevant local properties:

```text
route.session.store=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Default local address:

```text
http://localhost:8080
```

## 5. Current API Layers

### Dev Structured API

- `POST /api/dev/routes/plan-structured`
- `GET /api/dev/routes/{sessionId}`
- `POST /api/dev/routes/{sessionId}/adjust-structured`
- `GET /api/dev/routes/{sessionId}/context`
- `POST /api/dev/routes/{sessionId}/clarification/answer`

### User-Facing Fake Natural-Language API

- `POST /api/routes/plan`
- `GET /api/routes/{sessionId}`
- `POST /api/routes/{sessionId}/adjust`

## 6. Manual Verification With curl

Below examples assume the app is already running on `localhost:8080`.

### A. Create Natural-Language Route

```bash
curl -X POST "http://localhost:8080/api/routes/plan" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"浠婃櫄鎯冲拰濂虫湅鍙嬪湪涓夐噷灞害浼氾紝棰勭畻500锛屼笉鎯冲お绱紝鏈€濂借兘鎷嶇収銆俓"}"
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

Expected result:

- current session state
- current route
- current intent

### C. Lock Second Stop

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"绗簩绔欏埆鍔╘"}"
```

Expected result:

- `status` should usually be `SUCCESS`
- `session.locked_stop_orders` should include `2`
- response `message` should explain that the stop is locked

### D. Lower Budget

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"棰勭畻闄嶅埌300\"}"
```

Expected result:

- `status` should usually be `SUCCESS`
- `session.current_intent.budget_total` should become `300`
- response `message` should explain budget change and new totals

### E. Add Coffee

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"鍔犱竴涓挅鍟"}"
```

Expected result:

- `status` should usually be `SUCCESS`
- route stop count should increase if feasible
- response `message` should describe the updated route

### F. Switch To Indoor

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"浠婂ぉ涓嬮洦锛屾敼鎴愬鍐匼"}"
```

Expected result:

- `status` should usually be `SUCCESS`
- adjusted route should prefer indoor stops
- response `message` should explain that the route was switched toward indoor options

## 7. Optional Structured API Checks

If you want to verify the Java engine without natural-language parsing, test the dev structured API directly.

Example create request:

```bash
curl -X POST "http://localhost:8080/api/dev/routes/plan-structured" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"scene\":\"鎯呬荆绾︿細\",\"business_area\":\"涓夐噷灞痋",\"district\":\"鏈濋槼鍖篭",\"time_window\":\"18:00-22:00\",\"budget_total\":500,\"party_size\":2,\"pace\":\"杞绘澗\",\"prefer_tags\":[\"鎷嶇収\",\"姘涘洿濂絓"],\"avoid_tags\":[\"鎺掗槦涔匼"]}"
```

## 8. Clarification Flow Check

Some adjustment requests are intentionally ambiguous. For example:

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"鎹㈡帀\"}"
```

Possible expected result:

- `status = WAITING_CLARIFICATION`
- response `message` should contain a clarification question
- session should keep the current route unchanged

Then answer it:

```bash
curl -X POST "http://localhost:8080/api/routes/{sessionId}/adjust" ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":\"U10001\",\"message\":\"绗竴涓猏"}"
```

## 9. Verify Session Persistence Across Restart In Redis Mode

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

## 10. Optional Redis Integration Tests

Redis integration tests are disabled by default so normal `.\mvnw.cmd test` passes without Redis.

Run them only when Redis is already running:

```powershell
.\mvnw.cmd test -Droute.redis.tests=true
```

## 11. Current Limitations

- `FakeIntentAgentService` is rule-based and deterministic, not a real LLM Intent Agent.
- `FakePresenterService` is deterministic formatting logic, not a real Presenter Agent.
- `RouteSessionService` supports memory mode and Redis mode, but the Redis implementation is still MVP-level.
- Data comes from mock JSON and JSONL files under `src/main/resources/mock-data/`, not PostgreSQL yet.
- RocketMQ is not connected yet.
- There is no frontend yet.

## 12. Next Phases

- Real Spring AI `IntentAgent`
- Real Spring AI `PresenterAgent`
- PostgreSQL persistence
- RocketMQ async events
- Frontend / Amap visualization
