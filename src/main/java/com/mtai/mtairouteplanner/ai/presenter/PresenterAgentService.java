package com.mtai.mtairouteplanner.ai.presenter;

import com.mtai.mtairouteplanner.model.adjustment.AdjustmentResult;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;

public interface PresenterAgentService {

    String presentInitialRoute(RouteSessionState routeSessionState);

    String presentAdjustmentResult(AdjustmentResult adjustmentResult);

    String presentClarification(RouteSessionState routeSessionState);

    String presentNoFeasibleRoute(RoutePlanRequest routePlanRequest);
}

