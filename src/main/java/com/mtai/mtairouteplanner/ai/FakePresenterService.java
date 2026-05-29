package com.mtai.mtairouteplanner.ai;

import com.mtai.mtairouteplanner.model.AdjustmentResult;
import com.mtai.mtairouteplanner.model.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.model.RouteValidationIssue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FakePresenterService {

    public String presentPlanSuccess(RouteSessionState routeSessionState) {
        return buildRouteExplanation(
                "已为你生成一条" + routeSessionState.currentRoute().scene() + "路线。",
                routeSessionState.currentRoute(),
                routeSessionState.lockedStopOrders()
        );
    }

    public String presentAdjustmentResult(AdjustmentResult adjustmentResult) {
        if (adjustmentResult == null) {
            return "这次调整没有返回结果，当前先保留原路线。";
        }
        if (adjustmentResult.status() == AdjustmentStatus.WAITING_CLARIFICATION) {
            return presentClarificationQuestion(adjustmentResult.sessionState());
        }
        if (adjustmentResult.status() != AdjustmentStatus.SUCCESS) {
            return presentAdjustmentFailure(adjustmentResult);
        }

        RouteSessionState sessionState = adjustmentResult.sessionState();
        GeneratedRoutePlan routePlan = adjustmentResult.adjustedRoute();
        StringBuilder message = new StringBuilder();
        message.append(describeLatestChange(sessionState));
        appendLockedStops(message, sessionState.lockedStopOrders());
        message.append("\n").append(buildRouteMetricsLine(routePlan)).append("\n");
        message.append("行程更新：\n");
        appendStops(message, routePlan.stops());
        appendAdjustmentReason(message, sessionState, routePlan);
        return message.toString();
    }

    public String presentClarificationQuestion(RouteSessionState routeSessionState) {
        PendingClarification pendingClarification = routeSessionState == null ? null : routeSessionState.pendingClarification();
        if (pendingClarification == null) {
            return "还需要你补充一点信息，当前路线先保持不变。";
        }

        StringBuilder message = new StringBuilder();
        message.append("我还需要确认一下：").append(pendingClarification.question()).append("\n");
        if (!pendingClarification.candidateTargets().isEmpty()) {
            message.append("可选目标：").append(formatCandidateTargets(pendingClarification.candidateTargets())).append("\n");
        }
        message.append("当前路线先保持不变，确认后我再继续调整。");
        return message.toString();
    }

    public String presentNoFeasibleRoute(RoutePlanRequest routePlanRequest) {
        StringBuilder message = new StringBuilder("暂时没有找到符合当前条件的路线。");
        if (routePlanRequest != null) {
            message.append(" 当前条件是");
            boolean appended = false;
            if (routePlanRequest.budgetTotal() > 0) {
                message.append("预算约").append(routePlanRequest.budgetTotal()).append("元");
                appended = true;
            }
            if (hasText(routePlanRequest.timeWindow())) {
                if (appended) {
                    message.append("、");
                }
                message.append("时间窗口 ").append(routePlanRequest.timeWindow());
                appended = true;
            }
            if (!routePlanRequest.avoidTags().isEmpty()) {
                if (appended) {
                    message.append("、");
                }
                message.append("避雷标签 ").append(String.join(" / ", routePlanRequest.avoidTags()));
            }
            message.append("。");
        }
        message.append(" 可以尝试放宽预算、延长可玩时间，或减少部分限制条件。");
        return message.toString();
    }

    private String presentAdjustmentFailure(AdjustmentResult adjustmentResult) {
        StringBuilder message = new StringBuilder("这次调整暂时没有成功。");
        if (hasText(adjustmentResult.message())) {
            message.append(" 原因：").append(adjustmentResult.message()).append("。");
        }
        message.append(" 当前先保留原路线。");
        if (adjustmentResult.sessionState() != null && !adjustmentResult.sessionState().lockedStopOrders().isEmpty()) {
            appendLockedStops(message, adjustmentResult.sessionState().lockedStopOrders());
        }
        return message.toString();
    }

    private String buildRouteExplanation(String intro, GeneratedRoutePlan routePlan, Set<Integer> lockedStopOrders) {
        StringBuilder message = new StringBuilder(intro);
        message.append("\n").append(buildRouteSummaryLine(routePlan)).append("\n");
        message.append(buildRouteMetricsLine(routePlan)).append("\n");
        message.append("行程明细：\n");
        appendStops(message, routePlan.stops());
        appendRecommendationReasons(message, routePlan.stops());
        appendAvoidReminders(message, routePlan);
        if (lockedStopOrders != null && !lockedStopOrders.isEmpty()) {
            appendLockedStops(message, lockedStopOrders);
        }
        return message.toString();
    }

    private String buildRouteSummaryLine(GeneratedRoutePlan routePlan) {
        return "路线概览：" + routePlan.startTime() + "-" + routePlan.endTime()
                + "，共" + routePlan.stops().size() + "站，适合" + routePlan.scene() + "。";
    }

    private String buildRouteMetricsLine(GeneratedRoutePlan routePlan) {
        return "预计总预算约" + routePlan.totalBudget() + "元，总时长约"
                + formatDuration(routePlan.totalDurationMinutes())
                + "，总通勤距离约" + roundDistance(routePlan.totalDistanceKm()) + "公里。";
    }

    private void appendStops(StringBuilder message, List<GeneratedRouteStop> stops) {
        for (GeneratedRouteStop stop : stops) {
            message.append(stop.stopOrder())
                    .append(". ")
                    .append(stop.arriveTime())
                    .append("-")
                    .append(stop.leaveTime())
                    .append(" ")
                    .append(stop.poiName())
                    .append("（")
                    .append(stop.slotRole())
                    .append("，")
                    .append(stop.businessArea())
                    .append("）\n");
        }
    }

    private void appendRecommendationReasons(StringBuilder message, List<GeneratedRouteStop> stops) {
        message.append("推荐理由：\n");
        for (GeneratedRouteStop stop : stops.stream().limit(3).toList()) {
            message.append("- 第").append(stop.stopOrder()).append("站 ").append(stop.poiName()).append(" 适合作为").append(stop.slotRole());
            if (!stop.matchedPreferTags().isEmpty()) {
                message.append("，命中偏好：").append(String.join(" / ", stop.matchedPreferTags()));
            } else if ("outdoor".equalsIgnoreCase(stop.indoorOutdoor())) {
                message.append("，更适合散步和放松。");
            } else {
                message.append("，停留节奏比较舒服。");
            }
            message.append("\n");
        }
    }

    private void appendAvoidReminders(StringBuilder message, GeneratedRoutePlan routePlan) {
        Set<String> avoidHits = new LinkedHashSet<>();
        for (GeneratedRouteStop stop : routePlan.stops()) {
            avoidHits.addAll(stop.matchedAvoidTags());
        }
        if (routePlan.validationResult() != null) {
            for (RouteValidationIssue issue : routePlan.validationResult().issues()) {
                avoidHits.add(issue.message());
            }
        }

        message.append("避坑提醒：\n");
        if (avoidHits.isEmpty()) {
            message.append("- 当前路线没有明显避雷标签命中，整体通勤也比较短。\n");
            return;
        }
        for (String avoidHit : avoidHits) {
            message.append("- 需要留意：").append(avoidHit).append("\n");
        }
    }

    private void appendLockedStops(StringBuilder message, Set<Integer> lockedStopOrders) {
        if (lockedStopOrders == null || lockedStopOrders.isEmpty()) {
            return;
        }
        message.append("\n已保留锁定站点：")
                .append(lockedStopOrders.stream()
                        .sorted()
                        .map(order -> "第" + order + "站")
                        .reduce((left, right) -> left + "、" + right)
                        .orElse(""))
                .append("。");
    }

    private void appendAdjustmentReason(StringBuilder message, RouteSessionState sessionState, GeneratedRoutePlan routePlan) {
        List<String> reasons = new ArrayList<>();
        RouteChangeRecord latestChange = latestChange(sessionState);
        if (latestChange != null) {
            reasons.add(mapChangeReason(latestChange));
        }
        if (!routePlan.stops().isEmpty() && !routePlan.stops().getLast().matchedPreferTags().isEmpty()) {
            reasons.add("收尾站点延续了你的偏好：" + String.join(" / ", routePlan.stops().getLast().matchedPreferTags()));
        }
        if (reasons.isEmpty()) {
            reasons.add("路线顺序和预算重新校验后仍然可行。");
        }

        message.append("调整说明：\n");
        for (String reason : reasons) {
            message.append("- ").append(reason).append("\n");
        }
    }

    private String describeLatestChange(RouteSessionState sessionState) {
        RouteChangeRecord latestChange = latestChange(sessionState);
        if (latestChange == null) {
            return "路线已经更新完成。";
        }
        return switch (latestChange.changeType()) {
            case "LOWER_BUDGET" -> "已按你的要求压低预算。";
            case "REPLACE_STOP" -> "已替换相关站点。";
            case "REMOVE_STOP" -> "已移除目标站点。";
            case "ADD_STOP" -> "已为你补充新的停靠点。";
            case "CHANGE_TIME_WINDOW" -> "已按照新的时间窗口重排行程。";
            case "LOCK_STOP" -> "已锁定指定站点，后续调整会优先保留它。";
            case "UNLOCK_STOP" -> "已解除指定站点的锁定。";
            case "SWITCH_TO_INDOOR" -> "已切换为更偏室内的路线。";
            default -> "路线已经根据你的要求完成调整。";
        };
    }

    private String mapChangeReason(RouteChangeRecord latestChange) {
        return switch (latestChange.changeType()) {
            case "LOWER_BUDGET" -> "预算已经下调，优先保留更划算的停留组合。";
            case "REPLACE_STOP" -> "目标站点已替换为更匹配当前要求的候选。";
            case "REMOVE_STOP" -> "路线长度缩短了一些，整体节奏会更轻松。";
            case "ADD_STOP" -> "新增了一站，让路线内容更完整。";
            case "CHANGE_TIME_WINDOW" -> "时间安排已重新压缩，避免超出可玩时段。";
            case "LOCK_STOP" -> "锁定站点已作为后续调整的锚点。";
            case "UNLOCK_STOP" -> "解锁后，后续重排会有更大空间。";
            case "SWITCH_TO_INDOOR" -> "优先保留室内站点，减少天气影响。";
            default -> "路线在约束范围内重新计算完成。";
        };
    }

    private String formatCandidateTargets(List<String> candidateTargets) {
        return candidateTargets.stream()
                .map(this::formatCandidateTarget)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private String formatCandidateTarget(String candidateTarget) {
        String[] parts = candidateTarget.split("\\|", 3);
        if (parts.length == 3) {
            return "第" + parts[0] + "站 " + parts[2] + "（" + parts[1] + "）";
        }
        return candidateTarget;
    }

    private RouteChangeRecord latestChange(RouteSessionState sessionState) {
        if (sessionState == null || sessionState.changeHistory().isEmpty()) {
            return null;
        }
        return sessionState.changeHistory().stream()
                .max(Comparator.comparing(RouteChangeRecord::createdAt))
                .orElse(null);
    }

    private String formatDuration(int totalDurationMinutes) {
        int hours = totalDurationMinutes / 60;
        int minutes = totalDurationMinutes % 60;
        if (hours == 0) {
            return minutes + "分钟";
        }
        if (minutes == 0) {
            return hours + "小时";
        }
        return hours + "小时" + minutes + "分钟";
    }

    private String roundDistance(double totalDistanceKm) {
        return String.format("%.2f", totalDistanceKm);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
