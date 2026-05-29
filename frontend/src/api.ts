import type { NaturalLanguageRouteResponse, RouteSessionResponse } from "./types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

async function parseJsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const fallbackText = await response.text();
    throw new Error(fallbackText || `Request failed with ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function planRoute(userId: string, message: string): Promise<NaturalLanguageRouteResponse> {
  const response = await fetch(`${API_BASE_URL}/api/routes/plan`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      user_id: userId,
      message
    })
  });
  return parseJsonResponse<NaturalLanguageRouteResponse>(response);
}

export async function adjustRoute(sessionId: string, userId: string, message: string): Promise<NaturalLanguageRouteResponse> {
  const response = await fetch(`${API_BASE_URL}/api/routes/${sessionId}/adjust`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      user_id: userId,
      message
    })
  });
  return parseJsonResponse<NaturalLanguageRouteResponse>(response);
}

export async function fetchRouteSession(sessionId: string): Promise<RouteSessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/routes/${sessionId}`);
  return parseJsonResponse<RouteSessionResponse>(response);
}

export function apiBaseUrl(): string {
  return API_BASE_URL;
}
