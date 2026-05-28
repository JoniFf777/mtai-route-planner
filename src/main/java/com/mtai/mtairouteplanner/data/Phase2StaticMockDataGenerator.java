package com.mtai.mtairouteplanner.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Phase2StaticMockDataGenerator {

    private static final String CITY = "北京";
    private static final String COORDINATE_SYSTEM = "GCJ-02";
    private static final String TRANSPORT_MODE = "taxi";
    private static final String BUSINESS_AREAS_FILE = "business_areas.json";
    private static final String TRAFFIC_MATRIX_FILE = "traffic_matrix.json";
    private static final String ROUTE_TEMPLATES_FILE = "route_templates.json";
    private static final String SLOT_TRANSITION_RULES_FILE = "slot_transition_rules.json";

    private final ObjectMapper objectMapper;

    public Phase2StaticMockDataGenerator() {
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public GeneratedData generate() {
        List<BusinessArea> businessAreas = buildBusinessAreas();
        List<TrafficMatrixEntry> trafficMatrix = buildTrafficMatrix(businessAreas);
        List<RouteTemplate> routeTemplates = buildRouteTemplates();
        List<SlotTransitionRule> slotTransitionRules = buildSlotTransitionRules();
        return new GeneratedData(businessAreas, trafficMatrix, routeTemplates, slotTransitionRules);
    }

    public void writeTo(Path outputDirectory) throws IOException {
        GeneratedData data = generate();
        Files.createDirectories(outputDirectory);
        writePrettyJson(outputDirectory.resolve(BUSINESS_AREAS_FILE), data.businessAreas());
        writePrettyJson(outputDirectory.resolve(TRAFFIC_MATRIX_FILE), data.trafficMatrix());
        writePrettyJson(outputDirectory.resolve(ROUTE_TEMPLATES_FILE), data.routeTemplates());
        writePrettyJson(outputDirectory.resolve(SLOT_TRANSITION_RULES_FILE), data.slotTransitionRules());
    }

    public static void main(String[] args) throws IOException {
        Path outputDirectory = args.length > 0
                ? Path.of(args[0])
                : Path.of("src", "main", "resources", "mock-data");
        new Phase2StaticMockDataGenerator().writeTo(outputDirectory);
    }

    private void writePrettyJson(Path targetFile, Object value) throws IOException {
        objectMapper.writeValue(targetFile.toFile(), value);
    }

    private List<BusinessArea> buildBusinessAreas() {
        return List.of(
                businessArea("BA001", "朝阳区", "三里屯", 39.9370, 116.4543,
                        List.of("潮流", "夜生活", "约会", "拍照"),
                        List.of("情侣约会", "夜游路线", "朋友聚会")),
                businessArea("BA002", "朝阳区", "国贸", 39.9084, 116.4611,
                        List.of("商务", "高端餐饮", "精致约会"),
                        List.of("情侣约会", "朋友聚会", "游客半日游")),
                businessArea("BA003", "朝阳区", "望京", 40.0049, 116.4705,
                        List.of("韩餐", "年轻人", "聚会", "咖啡"),
                        List.of("朋友聚会", "低预算学生路线", "独处放松路线")),
                businessArea("BA004", "朝阳区", "亮马河", 39.9498, 116.4749,
                        List.of("夜景", "散步", "松弛感", "约会"),
                        List.of("情侣约会", "夜游路线", "独处放松路线")),
                businessArea("BA005", "朝阳区", "蓝色港湾", 39.9333, 116.4735,
                        List.of("商场", "休闲", "拍照", "亲子"),
                        List.of("亲子室内路线", "情侣约会", "雨天路线")),
                businessArea("BA006", "朝阳区", "朝阳公园", 39.9331, 116.4788,
                        List.of("公园", "户外", "散步", "放松"),
                        List.of("独处放松路线", "情侣约会", "游客半日游")),
                businessArea("BA007", "东城区", "王府井", 39.9151, 116.4119,
                        List.of("商圈", "游客", "购物", "餐饮"),
                        List.of("游客半日游", "朋友聚会", "雨天路线")),
                businessArea("BA008", "东城区", "前门", 39.8954, 116.4046,
                        List.of("老北京", "游客", "景点", "小吃"),
                        List.of("游客半日游", "Citywalk", "夜游路线")),
                businessArea("BA009", "东城区", "南锣鼓巷", 39.9409, 116.4037,
                        List.of("胡同", "citywalk", "小吃", "文艺"),
                        List.of("Citywalk", "游客半日游", "独处放松路线")),
                businessArea("BA010", "东城区", "东直门", 39.9462, 116.4344,
                        List.of("交通枢纽", "餐饮", "夜生活", "便利"),
                        List.of("朋友聚会", "夜游路线", "雨天路线")),
                businessArea("BA011", "东城区", "雍和宫", 39.9485, 116.4170,
                        List.of("文化", "寺院", "胡同", "安静"),
                        List.of("Citywalk", "独处放松路线", "游客半日游")),
                businessArea("BA012", "东城区", "簋街", 39.9399, 116.4353,
                        List.of("夜宵", "烟火气", "夜生活", "聚餐"),
                        List.of("朋友聚会", "夜游路线", "情侣约会")),
                businessArea("BA013", "西城区", "西单", 39.9085, 116.3737,
                        List.of("商场", "潮流", "娱乐", "餐饮"),
                        List.of("朋友聚会", "雨天路线", "低预算学生路线")),
                businessArea("BA014", "西城区", "什刹海", 39.9424, 116.3853,
                        List.of("夜景", "胡同", "清吧", "游客"),
                        List.of("夜游路线", "Citywalk", "情侣约会")),
                businessArea("BA015", "西城区", "金融街", 39.9173, 116.3660,
                        List.of("商务", "安静", "品质餐饮", "精致"),
                        List.of("情侣约会", "独处放松路线", "雨天路线")),
                businessArea("BA016", "西城区", "牛街", 39.8916, 116.3703,
                        List.of("小吃", "烟火气", "清真餐饮", "本地感"),
                        List.of("Citywalk", "低预算学生路线", "游客半日游")),
                businessArea("BA017", "海淀区", "五道口", 39.9929, 116.3388,
                        List.of("学生", "低预算", "聚会", "夜生活"),
                        List.of("低预算学生路线", "朋友聚会", "夜游路线")),
                businessArea("BA018", "海淀区", "中关村", 39.9836, 116.3155,
                        List.of("科技", "书店", "简餐", "学生"),
                        List.of("低预算学生路线", "独处放松路线", "雨天路线")),
                businessArea("BA019", "海淀区", "颐和园/圆明园", 39.9999, 116.2755,
                        List.of("景点", "公园", "游客", "散步"),
                        List.of("游客半日游", "Citywalk", "独处放松路线")),
                businessArea("BA020", "海淀区", "魏公村", 39.9603, 116.3232,
                        List.of("高校", "餐饮", "性价比", "便利"),
                        List.of("低预算学生路线", "朋友聚会", "雨天路线"))
        );
    }

    private BusinessArea businessArea(
            String areaId,
            String district,
            String areaName,
            double centerLat,
            double centerLng,
            List<String> areaTags,
            List<String> suitableScenes
    ) {
        return new BusinessArea(
                areaId,
                CITY,
                district,
                areaName,
                rounded(centerLat, 4),
                rounded(centerLng, 4),
                COORDINATE_SYSTEM,
                List.copyOf(areaTags),
                List.copyOf(suitableScenes)
        );
    }

    private List<TrafficMatrixEntry> buildTrafficMatrix(List<BusinessArea> businessAreas) {
        Map<String, BusinessArea> areaByName = new LinkedHashMap<>();
        Map<String, List<BusinessArea>> areasByDistrict = new LinkedHashMap<>();
        for (BusinessArea businessArea : businessAreas) {
            areaByName.put(businessArea.areaName(), businessArea);
            areasByDistrict
                    .computeIfAbsent(businessArea.district(), ignored -> new ArrayList<>())
                    .add(businessArea);
        }

        List<TrafficMatrixEntry> entries = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();

        for (List<BusinessArea> districtAreas : areasByDistrict.values()) {
            for (BusinessArea fromArea : districtAreas) {
                for (BusinessArea toArea : districtAreas) {
                    if (!fromArea.areaName().equals(toArea.areaName())) {
                        addTrafficEntry(entries, dedupe, fromArea, toArea);
                    }
                }
            }
        }

        List<List<String>> crossDistrictPairs = List.of(
                List.of("三里屯", "东直门"),
                List.of("三里屯", "雍和宫"),
                List.of("亮马河", "东直门"),
                List.of("蓝色港湾", "什刹海"),
                List.of("朝阳公园", "亮马河"),
                List.of("王府井", "西单"),
                List.of("前门", "牛街"),
                List.of("南锣鼓巷", "什刹海"),
                List.of("东直门", "簋街"),
                List.of("金融街", "王府井"),
                List.of("西单", "魏公村"),
                List.of("五道口", "中关村"),
                List.of("五道口", "魏公村"),
                List.of("中关村", "金融街"),
                List.of("颐和园/圆明园", "什刹海")
        );

        for (List<String> pair : crossDistrictPairs) {
            BusinessArea first = areaByName.get(pair.getFirst());
            BusinessArea second = areaByName.get(pair.get(1));
            addTrafficEntry(entries, dedupe, first, second);
            addTrafficEntry(entries, dedupe, second, first);
        }

        return entries.stream()
                .sorted(Comparator.comparing(TrafficMatrixEntry::fromArea).thenComparing(TrafficMatrixEntry::toArea))
                .toList();
    }

    private void addTrafficEntry(
            List<TrafficMatrixEntry> entries,
            Set<String> dedupe,
            BusinessArea fromArea,
            BusinessArea toArea
    ) {
        String key = fromArea.areaName() + "->" + toArea.areaName();
        if (!dedupe.add(key)) {
            return;
        }

        double distanceKm = haversineKm(
                fromArea.centerLat(),
                fromArea.centerLng(),
                toArea.centerLat(),
                toArea.centerLng()
        );
        double travelMinutes = estimateMinutes(fromArea, toArea, distanceKm);

        entries.add(new TrafficMatrixEntry(
                fromArea.areaName(),
                toArea.areaName(),
                TRANSPORT_MODE,
                rounded(distanceKm, 2),
                rounded(travelMinutes, 2)
        ));
    }

    private double estimateMinutes(BusinessArea fromArea, BusinessArea toArea, double distanceKm) {
        boolean sameDistrict = fromArea.district().equals(toArea.district());
        double baseMinutes = sameDistrict ? 8.0 : 14.0;
        double perKmMinutes = sameDistrict ? 4.2 : 5.6;
        return Math.max(baseMinutes + (distanceKm * perKmMinutes), sameDistrict ? 10.0 : 18.0);
    }

    private List<RouteTemplate> buildRouteTemplates() {
        return List.of(
                routeTemplate("RT001", "情侣约会", "晚间", 180, 240, "中", "轻松",
                        List.of("晚餐主餐", "夜景散步", "甜品收尾"),
                        List.of("朝阳区", "东城区", "西城区")),
                routeTemplate("RT002", "情侣约会", "下午到晚间", 240, 360, "高", "适中",
                        List.of("咖啡休息点", "晚餐主餐", "夜景散步", "清吧收尾"),
                        List.of("朝阳区", "东城区", "西城区")),
                routeTemplate("RT003", "朋友聚会", "晚间", 180, 300, "中", "适中",
                        List.of("晚餐主餐", "聊天点", "夜间娱乐"),
                        List.of("朝阳区", "东城区", "西城区", "海淀区")),
                routeTemplate("RT004", "朋友聚会", "全天", 240, 420, "中", "紧凑",
                        List.of("午餐主餐", "强体验活动", "晚餐主餐", "夜宵点"),
                        List.of("朝阳区", "东城区", "海淀区")),
                routeTemplate("RT005", "Citywalk", "白天", 180, 300, "低", "适中",
                        List.of("景点打卡", "胡同散步", "咖啡休息点", "本地小吃"),
                        List.of("东城区", "西城区", "海淀区")),
                routeTemplate("RT006", "Citywalk", "下午到晚间", 240, 360, "中", "轻松",
                        List.of("景点打卡", "散步点", "文艺点", "晚餐主餐"),
                        List.of("东城区", "西城区", "海淀区")),
                routeTemplate("RT007", "游客半日游", "上午", 180, 240, "中", "适中",
                        List.of("景点打卡", "文化体验点", "本地小吃"),
                        List.of("东城区", "西城区", "海淀区")),
                routeTemplate("RT008", "游客半日游", "下午", 180, 300, "中", "轻松",
                        List.of("景点打卡", "散步点", "晚餐主餐"),
                        List.of("朝阳区", "东城区", "西城区", "海淀区")),
                routeTemplate("RT009", "低预算学生路线", "午后", 150, 240, "低", "轻松",
                        List.of("简餐点", "咖啡休息点", "聊天点"),
                        List.of("海淀区", "朝阳区", "西城区")),
                routeTemplate("RT010", "低预算学生路线", "晚间", 180, 300, "低", "适中",
                        List.of("晚餐主餐", "聚会点", "夜宵点"),
                        List.of("海淀区", "朝阳区", "西城区")),
                routeTemplate("RT011", "亲子室内路线", "白天", 180, 300, "中", "轻松",
                        List.of("室内活动", "商场餐饮", "休息点"),
                        List.of("朝阳区", "东城区", "西城区")),
                routeTemplate("RT012", "亲子室内路线", "全天", 240, 360, "中", "轻松",
                        List.of("文化体验点", "午餐主餐", "室内活动", "甜品收尾"),
                        List.of("朝阳区", "东城区", "海淀区")),
                routeTemplate("RT013", "雨天路线", "白天", 180, 300, "中", "轻松",
                        List.of("室内展览", "商场餐饮", "室内娱乐"),
                        List.of("朝阳区", "东城区", "西城区", "海淀区")),
                routeTemplate("RT014", "雨天路线", "下午到晚间", 240, 360, "中", "适中",
                        List.of("咖啡休息点", "室内活动", "晚餐主餐"),
                        List.of("朝阳区", "东城区", "西城区", "海淀区")),
                routeTemplate("RT015", "夜游路线", "晚间", 180, 300, "中", "轻松",
                        List.of("晚餐主餐", "夜景散步", "清吧收尾"),
                        List.of("朝阳区", "东城区", "西城区")),
                routeTemplate("RT016", "夜游路线", "深夜前", 180, 300, "中", "适中",
                        List.of("夜生活点", "聊天点", "夜宵点"),
                        List.of("朝阳区", "东城区", "海淀区")),
                routeTemplate("RT017", "独处放松路线", "午后", 150, 240, "低", "轻松",
                        List.of("散步点", "咖啡休息点", "文艺点"),
                        List.of("朝阳区", "东城区", "西城区", "海淀区")),
                routeTemplate("RT018", "独处放松路线", "晚间", 180, 240, "中", "轻松",
                        List.of("晚餐主餐", "散步点", "甜品收尾"),
                        List.of("朝阳区", "东城区", "西城区")),
                routeTemplate("RT019", "朋友聚会", "雨天晚间", 180, 300, "中", "适中",
                        List.of("晚餐主餐", "室内娱乐", "甜品收尾"),
                        List.of("朝阳区", "东城区", "西城区", "海淀区")),
                routeTemplate("RT020", "情侣约会", "雨天晚间", 180, 300, "高", "轻松",
                        List.of("室内展览", "晚餐主餐", "甜品收尾"),
                        List.of("朝阳区", "东城区", "西城区"))
        );
    }

    private RouteTemplate routeTemplate(
            String templateId,
            String scene,
            String timePeriod,
            int minDurationMinutes,
            int maxDurationMinutes,
            String budgetLevel,
            String paceLevel,
            List<String> slotSequence,
            List<String> suitableDistricts
    ) {
        return new RouteTemplate(
                templateId,
                scene,
                timePeriod,
                minDurationMinutes,
                maxDurationMinutes,
                budgetLevel,
                paceLevel,
                List.copyOf(slotSequence),
                List.copyOf(suitableDistricts)
        );
    }

    private List<SlotTransitionRule> buildSlotTransitionRules() {
        return List.of(
                slotTransitionRule("晚餐主餐", "夜景散步", 0.95, "晚餐后接短距离散步，体验更完整。"),
                slotTransitionRule("夜景散步", "甜品收尾", 0.92, "散步后进入轻收尾环节，节奏自然。"),
                slotTransitionRule("晚餐主餐", "清吧收尾", 0.88, "适合夜间约会和朋友聚会的延展。"),
                slotTransitionRule("咖啡休息点", "晚餐主餐", 0.86, "从轻社交热身过渡到主餐较顺。"),
                slotTransitionRule("景点打卡", "胡同散步", 0.91, "同类步行场景衔接自然，适合 Citywalk。"),
                slotTransitionRule("胡同散步", "咖啡休息点", 0.89, "步行后安排休息点能降低疲劳。"),
                slotTransitionRule("咖啡休息点", "本地小吃", 0.87, "休息后进入轻餐饮，预算友好。"),
                slotTransitionRule("景点打卡", "文化体验点", 0.83, "游客动线从景点延伸到文化体验较稳定。"),
                slotTransitionRule("文化体验点", "本地小吃", 0.84, "文化体验后接本地餐饮有目的地特色。"),
                slotTransitionRule("简餐点", "咖啡休息点", 0.82, "学生和轻量路线常见搭配。"),
                slotTransitionRule("聊天点", "夜宵点", 0.85, "社交停留后接夜宵适合延长聚会。"),
                slotTransitionRule("午餐主餐", "强体验活动", 0.78, "先补充体力，再进入高参与活动。"),
                slotTransitionRule("强体验活动", "晚餐主餐", 0.9, "高强度活动后回归主餐补给合理。"),
                slotTransitionRule("室内展览", "商场餐饮", 0.9, "雨天和亲子路线常用的稳定组合。"),
                slotTransitionRule("商场餐饮", "室内娱乐", 0.88, "同场景切换成本低，适合雨天。"),
                slotTransitionRule("文化体验点", "午餐主餐", 0.8, "白天文化活动后接正餐较常见。"),
                slotTransitionRule("午餐主餐", "室内活动", 0.76, "亲子和雨天路线中频繁出现。"),
                slotTransitionRule("散步点", "咖啡休息点", 0.9, "独处放松路线需要节奏缓冲。"),
                slotTransitionRule("文艺点", "晚餐主餐", 0.77, "文艺体验后接餐饮，适合慢节奏晚间路线。"),
                slotTransitionRule("夜生活点", "聊天点", 0.79, "夜生活开始后常切换到可停留交流的场景。")
        );
    }

    private SlotTransitionRule slotTransitionRule(String fromSlot, String toSlot, double weight, String reason) {
        return new SlotTransitionRule(fromSlot, toSlot, rounded(weight, 2), reason);
    }

    private double rounded(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double haversineKm(double startLat, double startLng, double endLat, double endLng) {
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(endLat - startLat);
        double lngDistance = Math.toRadians(endLng - startLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    public record GeneratedData(
            List<BusinessArea> businessAreas,
            List<TrafficMatrixEntry> trafficMatrix,
            List<RouteTemplate> routeTemplates,
            List<SlotTransitionRule> slotTransitionRules
    ) {
    }

    public record BusinessArea(
            String areaId,
            String city,
            String district,
            String areaName,
            double centerLat,
            double centerLng,
            String coordinateSystem,
            List<String> areaTags,
            List<String> suitableScenes
    ) {
    }

    public record TrafficMatrixEntry(
            String fromArea,
            String toArea,
            String transportMode,
            double distanceKm,
            double estimatedMinutes
    ) {
    }

    public record RouteTemplate(
            String templateId,
            String scene,
            String timePeriod,
            int minDurationMinutes,
            int maxDurationMinutes,
            String budgetLevel,
            String paceLevel,
            List<String> slotSequence,
            List<String> suitableDistricts
    ) {
    }

    public record SlotTransitionRule(
            String fromSlot,
            String toSlot,
            double weight,
            String reason
    ) {
    }
}
