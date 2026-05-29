package com.mtai.mtairouteplanner.ai;

import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.CompactRouteContext;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FakeIntentAgentService {

    private static final Map<String, String> BUSINESS_AREA_TO_DISTRICT = new LinkedHashMap<>();
    private static final Map<String, Integer> CHINESE_NUMBER_MAP = Map.ofEntries(
            Map.entry("一", 1),
            Map.entry("二", 2),
            Map.entry("两", 2),
            Map.entry("三", 3),
            Map.entry("四", 4),
            Map.entry("五", 5)
    );

    static {
        BUSINESS_AREA_TO_DISTRICT.put("三里屯", "朝阳区");
        BUSINESS_AREA_TO_DISTRICT.put("国贸", "朝阳区");
        BUSINESS_AREA_TO_DISTRICT.put("望京", "朝阳区");
        BUSINESS_AREA_TO_DISTRICT.put("亮马河", "朝阳区");
        BUSINESS_AREA_TO_DISTRICT.put("蓝色港湾", "朝阳区");
        BUSINESS_AREA_TO_DISTRICT.put("朝阳公园", "朝阳区");
        BUSINESS_AREA_TO_DISTRICT.put("王府井", "东城区");
        BUSINESS_AREA_TO_DISTRICT.put("前门", "东城区");
        BUSINESS_AREA_TO_DISTRICT.put("南锣鼓巷", "东城区");
        BUSINESS_AREA_TO_DISTRICT.put("东直门", "东城区");
        BUSINESS_AREA_TO_DISTRICT.put("雍和宫", "东城区");
        BUSINESS_AREA_TO_DISTRICT.put("簋街", "东城区");
        BUSINESS_AREA_TO_DISTRICT.put("西单", "西城区");
        BUSINESS_AREA_TO_DISTRICT.put("什刹海", "西城区");
        BUSINESS_AREA_TO_DISTRICT.put("金融街", "西城区");
        BUSINESS_AREA_TO_DISTRICT.put("牛街", "西城区");
        BUSINESS_AREA_TO_DISTRICT.put("五道口", "海淀区");
        BUSINESS_AREA_TO_DISTRICT.put("中关村", "海淀区");
        BUSINESS_AREA_TO_DISTRICT.put("颐和园", "海淀区");
        BUSINESS_AREA_TO_DISTRICT.put("圆明园", "海淀区");
        BUSINESS_AREA_TO_DISTRICT.put("魏公村", "海淀区");
    }

    public RoutePlanRequest parsePlanRequest(String userId, String message) {
        requireUserMessage(userId, message);
        String normalizedMessage = normalize(message);
        String detectedBusinessArea = detectBusinessArea(normalizedMessage);
        String scene = detectScene(normalizedMessage);
        String businessArea = shouldUseBusinessArea(scene) ? detectedBusinessArea : null;
        String district = detectDistrict(normalizedMessage);
        if (!hasText(district) && hasText(businessArea)) {
            district = BUSINESS_AREA_TO_DISTRICT.get(businessArea);
        }
        if (!hasText(district) && hasText(detectedBusinessArea)) {
            district = BUSINESS_AREA_TO_DISTRICT.get(detectedBusinessArea);
        }
        if ("低预算学生路线".equals(scene)) {
            district = "西城区";
        }

        int partySize = detectPartySize(normalizedMessage, scene);
        int budgetTotal = detectBudgetTotal(normalizedMessage, scene, partySize);
        String timeWindow = detectTimeWindow(normalizedMessage, scene);
        String pace = detectPace(normalizedMessage, scene);
        List<String> preferTags = detectPreferTags(normalizedMessage, scene);
        List<String> avoidTags = detectAvoidTags(normalizedMessage);

        return new RoutePlanRequest(
                userId,
                scene,
                businessArea,
                district,
                timeWindow,
                budgetTotal,
                partySize,
                pace,
                preferTags,
                avoidTags
        );
    }

    public ParsedAdjustment parseAdjustment(String message, CompactRouteContext routeContext) {
        if (routeContext == null) {
            throw new IllegalArgumentException("Route context is required.");
        }
        if (!hasText(message)) {
            throw new IllegalArgumentException("message is required.");
        }

        String normalizedMessage = normalize(message);
        if (routeContext.pendingClarification() != null) {
            ClarificationAnswer clarificationAnswer = tryParseClarificationAnswer(normalizedMessage);
            if (clarificationAnswer != null) {
                return ParsedAdjustment.clarification(clarificationAnswer);
            }
        }

        Integer lockStopOrder = containsLockKeywords(normalizedMessage) ? extractStopOrder(normalizedMessage) : null;
        Set<Integer> lockedStopOrders = new LinkedHashSet<>();
        if (lockStopOrder != null) {
            lockedStopOrders.add(lockStopOrder);
        }

        if (containsUnlockKeywords(normalizedMessage)) {
            Integer targetStopOrder = extractStopOrder(normalizedMessage);
            if (targetStopOrder == null) {
                throw new IllegalArgumentException("Could not determine which stop to unlock.");
            }
            return ParsedAdjustment.change(new ChangeRequest(
                    ChangeType.UNLOCK_STOP,
                    targetStopOrder,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }

        if (containsIndoorSwitchKeywords(normalizedMessage)) {
            return ParsedAdjustment.change(new ChangeRequest(
                    ChangeType.SWITCH_TO_INDOOR,
                    null,
                    null,
                    null,
                    null,
                    List.of("室内"),
                    List.of("户外"),
                    List.copyOf(lockedStopOrders)
            ));
        }

        Integer loweredBudget = extractBudgetAfterKeywords(normalizedMessage, "预算降到", "降到", "预算改到");
        if (loweredBudget != null) {
            return ParsedAdjustment.change(new ChangeRequest(
                    ChangeType.LOWER_BUDGET,
                    null,
                    null,
                    loweredBudget,
                    null,
                    List.of(),
                    List.of("太贵"),
                    List.copyOf(lockedStopOrders)
            ));
        }

        if (containsAddCoffeeKeywords(normalizedMessage)) {
            return ParsedAdjustment.change(new ChangeRequest(
                    ChangeType.ADD_STOP,
                    null,
                    "咖啡休息点",
                    null,
                    null,
                    List.of("咖啡"),
                    List.of(),
                    List.copyOf(lockedStopOrders)
            ));
        }

        if (containsRemoveKeywords(normalizedMessage)) {
            return ParsedAdjustment.change(new ChangeRequest(
                    ChangeType.REMOVE_STOP,
                    extractStopOrder(normalizedMessage),
                    detectTargetSlotRole(normalizedMessage),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.copyOf(lockedStopOrders)
            ));
        }

        if (containsReplaceKeywords(normalizedMessage)) {
            String targetSlotRole = detectTargetSlotRole(normalizedMessage);
            Integer targetStopOrder = targetSlotRole == null ? extractStopOrder(normalizedMessage) : null;
            return ParsedAdjustment.change(new ChangeRequest(
                    ChangeType.REPLACE_STOP,
                    targetStopOrder,
                    targetSlotRole,
                    null,
                    null,
                    detectAdjustmentPreferTags(normalizedMessage),
                    detectAdjustmentAvoidTags(normalizedMessage),
                    List.copyOf(lockedStopOrders)
            ));
        }

        if (containsLockKeywords(normalizedMessage)) {
            Integer targetStopOrder = extractStopOrder(normalizedMessage);
            if (targetStopOrder == null) {
                throw new IllegalArgumentException("Could not determine which stop to lock.");
            }
            return ParsedAdjustment.change(new ChangeRequest(
                    ChangeType.LOCK_STOP,
                    targetStopOrder,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }

        throw new IllegalArgumentException("FakeIntentAgent could not parse the adjustment request.");
    }

    private void requireUserMessage(String userId, String message) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("user_id is required.");
        }
        if (!hasText(message)) {
            throw new IllegalArgumentException("message is required.");
        }
    }

    private String detectScene(String message) {
        if (containsAny(message, "citywalk", "Citywalk")) {
            return "Citywalk";
        }
        if (containsAny(message, "带孩子", "孩子", "亲子")) {
            return "亲子室内路线";
        }
        if (containsAny(message, "下雨", "雨天")) {
            return "雨天路线";
        }
        if (containsAny(message, "女朋友", "男朋友", "约会", "情侣")) {
            return "情侣约会";
        }
        if (containsAny(message, "学生党", "学生")) {
            return "低预算学生路线";
        }
        if (containsAny(message, "朋友聚会", "朋友一起", "聚会")) {
            return "朋友聚会";
        }
        if (containsAny(message, "夜游", "夜生活")) {
            return "夜游路线";
        }
        return "独处放松路线";
    }

    private String detectBusinessArea(String message) {
        for (String businessArea : BUSINESS_AREA_TO_DISTRICT.keySet()) {
            if (message.contains(businessArea)) {
                return businessArea;
            }
        }
        return null;
    }

    private String detectDistrict(String message) {
        if (message.contains("朝阳区")) {
            return "朝阳区";
        }
        if (message.contains("东城区")) {
            return "东城区";
        }
        if (message.contains("西城区")) {
            return "西城区";
        }
        if (message.contains("海淀区")) {
            return "海淀区";
        }
        return null;
    }

    private int detectPartySize(String message, String scene) {
        Integer explicitPartySize = extractPartySize(message);
        if (explicitPartySize != null) {
            return explicitPartySize;
        }
        if ("情侣约会".equals(scene)) {
            return 2;
        }
        if ("亲子室内路线".equals(scene)) {
            return 3;
        }
        if ("朋友聚会".equals(scene)) {
            return 3;
        }
        if ("低预算学生路线".equals(scene)) {
            return 2;
        }
        return 1;
    }

    private Integer extractPartySize(String message) {
        Matcher arabicMatcher = Pattern.compile("(\\d+)\\s*个?人").matcher(message);
        if (arabicMatcher.find()) {
            return Integer.parseInt(arabicMatcher.group(1));
        }
        for (Map.Entry<String, Integer> entry : CHINESE_NUMBER_MAP.entrySet()) {
            if (message.contains(entry.getKey() + "个人")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private int detectBudgetTotal(String message, String scene, int partySize) {
        Integer perPersonBudget = extractBudgetAfterKeywords(message, "人均");
        if (perPersonBudget != null) {
            int totalBudget = perPersonBudget * Math.max(partySize, 1);
            return "低预算学生路线".equals(scene) ? Math.max(totalBudget, 300) : totalBudget;
        }
        Integer totalBudget = extractBudgetAfterKeywords(message, "预算", "预算大概", "预算差不多");
        if (totalBudget != null) {
            return "低预算学生路线".equals(scene) ? Math.max(totalBudget, 300) : totalBudget;
        }
        return switch (scene) {
            case "情侣约会" -> 500;
            case "Citywalk" -> 200;
            case "亲子室内路线" -> 450;
            case "雨天路线" -> 400;
            case "低预算学生路线" -> 300;
            case "朋友聚会" -> 600;
            default -> 260;
        };
    }

    private Integer extractBudgetAfterKeywords(String message, String... keywords) {
        for (String keyword : keywords) {
            Matcher matcher = Pattern.compile(Pattern.quote(keyword) + "\\s*(\\d{2,4})").matcher(message);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }

    private String detectTimeWindow(String message, String scene) {
        if (containsAny(message, "今晚", "晚上", "夜里")) {
            return "18:00-22:00";
        }
        if ("Citywalk".equals(scene)) {
            return "13:00-22:00";
        }
        if ("低预算学生路线".equals(scene)) {
            return "18:00-22:00";
        }
        if ("亲子室内路线".equals(scene)) {
            return "10:00-16:00";
        }
        if (containsAny(message, "周末", "白天")) {
            return switch (scene) {
                case "亲子室内路线", "雨天路线" -> "10:00-16:00";
                default -> "13:00-19:00";
            };
        }
        return switch (scene) {
            case "情侣约会" -> "16:00-23:00";
            case "朋友聚会", "夜游路线" -> "18:00-22:00";
            case "Citywalk" -> "13:00-22:00";
            case "亲子室内路线", "雨天路线" -> "10:00-16:00";
            case "低预算学生路线" -> "18:00-22:00";
            default -> "14:00-18:00";
        };
    }

    private String detectPace(String message, String scene) {
        if (containsAny(message, "不想太累", "轻松", "少走路", "别太赶")) {
            return "轻松";
        }
        if (containsAny(message, "赶一点", "紧凑", "特种兵")) {
            return "紧凑";
        }
        if ("情侣约会".equals(scene) || "亲子室内路线".equals(scene) || "雨天路线".equals(scene)) {
            return "轻松";
        }
        return "适中";
    }

    private List<String> detectPreferTags(String message, String scene) {
        Set<String> preferTags = new LinkedHashSet<>();
        if (message.contains("拍照")) {
            preferTags.add("拍照");
        }
        if (message.contains("小吃")) {
            preferTags.add("小吃");
        }
        if (message.contains("室内")) {
            preferTags.add("室内");
        }
        if (message.contains("咖啡")) {
            preferTags.add("咖啡");
        }
        if (message.contains("氛围")) {
            preferTags.add("氛围好");
        }
        if (message.contains("安静")) {
            preferTags.add("安静");
        }
        if ("情侣约会".equals(scene) && !preferTags.contains("拍照")) {
            preferTags.add("氛围好");
        }
        if ("Citywalk".equals(scene) && !preferTags.contains("小吃")) {
            preferTags.add("散步");
        }
        if ("亲子室内路线".equals(scene)) {
            preferTags.add("亲子");
            preferTags.add("室内");
        }
        if ("雨天路线".equals(scene)) {
            preferTags.add("室内");
        }
        return List.copyOf(preferTags);
    }

    private List<String> detectAvoidTags(String message) {
        Set<String> avoidTags = new LinkedHashSet<>();
        if (message.contains("排队")) {
            avoidTags.add("排队久");
        }
        if (message.contains("太吵")) {
            avoidTags.add("太吵");
        }
        if (message.contains("太贵")) {
            avoidTags.add("太贵");
        }
        if (message.contains("不想走太多")) {
            avoidTags.add("走路多");
        }
        return List.copyOf(avoidTags);
    }

    private ClarificationAnswer tryParseClarificationAnswer(String message) {
        Integer targetStopOrder = extractStopOrder(message);
        if (targetStopOrder == null) {
            return null;
        }
        return new ClarificationAnswer(targetStopOrder, null);
    }

    private Integer extractStopOrder(String message) {
        Matcher numericMatcher = Pattern.compile("第\\s*(\\d+)\\s*(站|个)?").matcher(message);
        if (numericMatcher.find()) {
            return Integer.parseInt(numericMatcher.group(1));
        }
        for (Map.Entry<String, Integer> entry : CHINESE_NUMBER_MAP.entrySet()) {
            if (message.contains("第" + entry.getKey() + "站") || message.contains("第" + entry.getKey() + "个")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean containsUnlockKeywords(String message) {
        return containsAny(message, "解锁", "可以动");
    }

    private boolean containsLockKeywords(String message) {
        return containsAny(message, "别动", "锁定", "保留");
    }

    private boolean containsIndoorSwitchKeywords(String message) {
        return containsAny(message, "改成室内", "切到室内", "最好室内", "下雨") && containsAny(message, "室内", "下雨", "雨天");
    }

    private boolean containsAddCoffeeKeywords(String message) {
        return containsAny(message, "加一个咖啡", "加个咖啡", "加一站咖啡", "加个咖啡店");
    }

    private boolean containsRemoveKeywords(String message) {
        return containsAny(message, "删掉", "去掉", "移除");
    }

    private boolean containsReplaceKeywords(String message) {
        return containsAny(message, "换掉", "换一个", "换便宜点", "换成");
    }

    private String detectTargetSlotRole(String message) {
        if (containsAny(message, "晚餐", "主餐", "吃饭")) {
            return "晚餐主餐";
        }
        if (containsAny(message, "咖啡", "甜品")) {
            return "咖啡休息点";
        }
        return null;
    }

    private List<String> detectAdjustmentPreferTags(String message) {
        Set<String> preferTags = new LinkedHashSet<>();
        if (message.contains("便宜")) {
            preferTags.add("平价");
        }
        if (message.contains("咖啡")) {
            preferTags.add("咖啡");
        }
        if (message.contains("室内")) {
            preferTags.add("室内");
        }
        return List.copyOf(preferTags);
    }

    private List<String> detectAdjustmentAvoidTags(String message) {
        Set<String> avoidTags = new LinkedHashSet<>();
        if (message.contains("便宜") || message.contains("太贵")) {
            avoidTags.add("太贵");
        }
        return List.copyOf(avoidTags);
    }

    private boolean containsAny(String message, String... values) {
        for (String value : values) {
            if (message.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        return message.replace('，', ',')
                .replace('。', ' ')
                .replace('！', ' ')
                .replace('？', ' ')
                .trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean shouldUseBusinessArea(String scene) {
        return !"情侣约会".equals(scene)
                && !"Citywalk".equals(scene)
                && !"亲子室内路线".equals(scene)
                && !"低预算学生路线".equals(scene);
    }

    public record ParsedAdjustment(
            ChangeRequest changeRequest,
            ClarificationAnswer clarificationAnswer
    ) {
        public static ParsedAdjustment change(ChangeRequest changeRequest) {
            return new ParsedAdjustment(changeRequest, null);
        }

        public static ParsedAdjustment clarification(ClarificationAnswer clarificationAnswer) {
            return new ParsedAdjustment(null, clarificationAnswer);
        }

        public boolean isClarificationAnswer() {
            return clarificationAnswer != null;
        }
    }
}
