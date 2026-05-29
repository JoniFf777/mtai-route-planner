package com.mtai.mtairouteplanner.ai;

import com.mtai.mtairouteplanner.model.AdjustmentResult;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionState;

public interface PresenterAgentService {

    String presentInitialRoute(RouteSessionState routeSessionState);

    String presentAdjustmentResult(AdjustmentResult adjustmentResult);

    String presentClarification(RouteSessionState routeSessionState);

    String presentNoFeasibleRoute(RoutePlanRequest routePlanRequest);
}
