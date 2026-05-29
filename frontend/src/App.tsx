import { startTransition, useMemo, useState } from "react";
import { adjustRoute, apiBaseUrl, fetchRouteSession, planRoute } from "./api";
import RouteMap from "./components/RouteMap";
import type {
  GeneratedRoutePlan,
  GeneratedRouteStop,
  NaturalLanguageRouteResponse,
  RouteSessionResponse
} from "./types";

interface RouteViewState {
  sessionId: string | null;
  status: string;
  message: string;
  route: GeneratedRoutePlan | null;
  session: RouteSessionResponse | null;
}

const initialPlanMessage = "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。";
const initialAdjustMessage = "第二站别动";

function routeFromState(result: RouteViewState | null): GeneratedRoutePlan | null {
  return result?.route ?? result?.session?.current_route ?? null;
}

export default function App() {
  const [userId, setUserId] = useState("U10001");
  const [planMessage, setPlanMessage] = useState(initialPlanMessage);
  const [adjustMessage, setAdjustMessage] = useState(initialAdjustMessage);
  const [routeResult, setRouteResult] = useState<RouteViewState | null>(null);
  const [loading, setLoading] = useState<"plan" | "adjust" | "refresh" | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  const activeRoute = useMemo(() => routeFromState(routeResult), [routeResult]);
  const stops = activeRoute?.stops ?? [];

  async function handlePlanSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading("plan");
    setErrorMessage("");
    try {
      const response = await planRoute(userId, planMessage);
      startTransition(() => {
        setRouteResult(toRouteViewState(response));
      });
    } catch (error) {
      setErrorMessage(asErrorMessage(error));
    } finally {
      setLoading(null);
    }
  }

  async function handleAdjust() {
    if (!routeResult?.sessionId) {
      setErrorMessage("请先生成一条路线。");
      return;
    }
    setLoading("adjust");
    setErrorMessage("");
    try {
      const response = await adjustRoute(routeResult.sessionId, userId, adjustMessage);
      startTransition(() => {
        setRouteResult(toRouteViewState(response));
      });
    } catch (error) {
      setErrorMessage(asErrorMessage(error));
    } finally {
      setLoading(null);
    }
  }

  async function handleRefresh() {
    if (!routeResult?.sessionId) {
      setErrorMessage("当前没有可刷新的 session。");
      return;
    }
    setLoading("refresh");
    setErrorMessage("");
    try {
      const session = await fetchRouteSession(routeResult.sessionId);
      startTransition(() => {
        setRouteResult((previous) => ({
          sessionId: session.session_id,
          status: previous?.status ?? "SYNCED",
          message: "已从后端刷新最新会话状态。",
          route: session.current_route,
          session
        }));
      });
    } catch (error) {
      setErrorMessage(asErrorMessage(error));
    } finally {
      setLoading(null);
    }
  }

  return (
    <div className="app-shell">
      <div className="ambient ambient-left" />
      <div className="ambient ambient-right" />
      <main className="page-grid">
        <section className="hero-panel surface-card">
          <p className="eyebrow">MTAI Route Planner MVP</p>
          <h1>北京本地生活路线可视化</h1>
          <p className="hero-copy">
            直接调用现有后端自然语言 API，生成路线、调整停靠点，并把返回的 GCJ-02 坐标渲染到高德地图。
          </p>
          <div className="meta-strip">
            <span className="status-chip">API: {apiBaseUrl()}</span>
            <span className="status-chip">
              地图: {import.meta.env.VITE_AMAP_KEY ? "Amap 已配置" : "缺少 Amap Key"}
            </span>
          </div>
        </section>

        <section className="surface-card control-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Plan</p>
              <h2>生成路线</h2>
            </div>
          </div>

          <form className="form-stack" onSubmit={handlePlanSubmit}>
            <label className="field">
              <span>User ID</span>
              <input
                value={userId}
                onChange={(event) => setUserId(event.target.value)}
                placeholder="U10001"
              />
            </label>
            <label className="field">
              <span>自然语言需求</span>
              <textarea
                rows={5}
                value={planMessage}
                onChange={(event) => setPlanMessage(event.target.value)}
                placeholder={initialPlanMessage}
              />
            </label>
            <div className="button-row">
              <button className="primary-button" type="submit" disabled={loading !== null}>
                {loading === "plan" ? "生成中..." : "Generate Route"}
              </button>
              <button
                className="ghost-button"
                type="button"
                onClick={handleRefresh}
                disabled={!routeResult?.sessionId || loading !== null}
              >
                {loading === "refresh" ? "刷新中..." : "Refresh Session"}
              </button>
            </div>
          </form>

          <div className="adjust-shell">
            <label className="field">
              <span>调整指令</span>
              <textarea
                rows={3}
                value={adjustMessage}
                onChange={(event) => setAdjustMessage(event.target.value)}
                placeholder={initialAdjustMessage}
              />
            </label>
            <button
              className="secondary-button"
              type="button"
              onClick={handleAdjust}
              disabled={!routeResult?.sessionId || loading !== null}
            >
              {loading === "adjust" ? "调整中..." : "Adjust Route"}
            </button>
          </div>

          {errorMessage ? <p className="error-banner">{errorMessage}</p> : null}
        </section>

        <section className="surface-card result-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Result</p>
              <h2>路线结果</h2>
            </div>
            <span className={`status-pill status-${(routeResult?.status || "idle").toLowerCase()}`}>
              {routeResult?.status || "IDLE"}
            </span>
          </div>

          <dl className="session-meta">
            <div>
              <dt>Session</dt>
              <dd>{routeResult?.sessionId || "-"}</dd>
            </div>
            <div>
              <dt>预算</dt>
              <dd>{activeRoute ? `${activeRoute.total_budget} 元` : "-"}</dd>
            </div>
            <div>
              <dt>时长</dt>
              <dd>{activeRoute ? `${activeRoute.total_duration_minutes} 分钟` : "-"}</dd>
            </div>
            <div>
              <dt>距离</dt>
              <dd>{activeRoute ? `${activeRoute.total_distance_km.toFixed(2)} km` : "-"}</dd>
            </div>
          </dl>

          <p className="result-message">
            {routeResult?.message || "生成路线后，这里会显示后端返回的说明文本。"}
          </p>

          <div className="stop-list">
            {stops.length === 0 ? (
              <div className="empty-state">当前还没有可展示的路线停靠点。</div>
            ) : (
              stops.map((stop) => <StopCard key={`${stop.stop_order}-${stop.poi_id}`} stop={stop} />)
            )}
          </div>
        </section>

        <RouteMap stops={stops} />
      </main>
    </div>
  );
}

function StopCard({ stop }: { stop: GeneratedRouteStop }) {
  return (
    <article className="stop-card">
      <div className="stop-index">{stop.stop_order}</div>
      <div className="stop-content">
        <div className="stop-title-row">
          <h3>{stop.poi_name}</h3>
          <span>{stop.slot_role}</span>
        </div>
        <p className="stop-meta">
          {stop.arrive_time} - {stop.leave_time} · {stop.category_lv1} · {stop.business_area} · {stop.district}
        </p>
        <p className="stop-meta">
          预计花费 {stop.estimated_cost} 元 · 前序路程 {stop.distance_km_from_prev.toFixed(2)} km /{" "}
          {stop.travel_minutes_from_prev.toFixed(0)} 分钟
        </p>
        {typeof stop.lng === "number" && typeof stop.lat === "number" ? (
          <p className="stop-coordinates">
            坐标 {stop.coordinate_system || "GCJ-02"} · [{stop.lng.toFixed(4)}, {stop.lat.toFixed(4)}]
          </p>
        ) : (
          <p className="stop-coordinates stop-coordinates-muted">
            当前停靠点没有坐标，地图会自动跳过。
          </p>
        )}
      </div>
    </article>
  );
}

function toRouteViewState(response: NaturalLanguageRouteResponse): RouteViewState {
  return {
    sessionId: response.session_id ?? response.session?.session_id ?? null,
    status: response.status,
    message: response.message,
    route: response.route ?? response.session?.current_route ?? null,
    session: response.session ?? null
  };
}

function asErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return "请求失败，请稍后重试。";
}
