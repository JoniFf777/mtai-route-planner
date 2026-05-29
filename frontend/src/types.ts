export interface GeneratedRouteStop {
  stop_order: number;
  slot_role: string;
  poi_id: string;
  poi_name: string;
  business_area: string;
  district: string;
  lng?: number | null;
  lat?: number | null;
  coordinate_system?: string | null;
  category_lv1: string;
  indoor_outdoor: string;
  arrive_time: string;
  leave_time: string;
  stay_minutes: number;
  travel_minutes_from_prev: number;
  distance_km_from_prev: number;
  estimated_cost: number;
  stop_score: number;
  matched_prefer_tags?: string[];
  matched_avoid_tags?: string[];
}

export interface GeneratedRoutePlan {
  template_id: string;
  scene: string;
  time_window: string;
  total_budget: number;
  total_duration_minutes: number;
  total_distance_km: number;
  route_score: number;
  start_time: string;
  end_time: string;
  stops: GeneratedRouteStop[];
}

export interface RouteSessionIntent {
  scene: string;
  business_area?: string | null;
  district?: string | null;
  time_window: string;
  budget_total: number;
  party_size: number;
  pace: string;
  prefer_tags: string[];
  avoid_tags: string[];
}

export interface PendingClarification {
  question: string;
  candidate_targets: string[];
}

export interface RouteSessionResponse {
  session_id: string;
  user_id: string;
  status: string;
  current_intent: RouteSessionIntent | null;
  current_route: GeneratedRoutePlan | null;
  locked_stop_orders: number[];
  pending_clarification?: PendingClarification | null;
  version: number;
}

export interface NaturalLanguageRouteResponse {
  session_id?: string | null;
  status: string;
  route?: GeneratedRoutePlan | null;
  message: string;
  session?: RouteSessionResponse | null;
}
