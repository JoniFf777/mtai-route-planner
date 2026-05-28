package com.mtai.mtairouteplanner.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class Phase2StaticMockDataGenerator {

    private static final String CITY = "北京";
    private static final String COORDINATE_SYSTEM = "GCJ-02";
    private static final String TRANSPORT_MODE = "taxi";
    private static final long RANDOM_SEED = 20260528L;
    private static final int POIS_PER_AREA = 10;
    private static final String BUSINESS_AREAS_FILE = "business_areas.json";
    private static final String TRAFFIC_MATRIX_FILE = "traffic_matrix.json";
    private static final String ROUTE_TEMPLATES_FILE = "route_templates.json";
    private static final String SLOT_TRANSITION_RULES_FILE = "slot_transition_rules.json";
    private static final String POI_BASIC_FILE = "poi_basic.jsonl";
    private static final String POI_RATING_STATS_FILE = "poi_rating_stats.jsonl";
    private static final String POI_BUSINESS_INFO_FILE = "poi_business_info.jsonl";
    private static final String POI_UGC_SUMMARY_FILE = "poi_ugc_summary.jsonl";
    private static final String POI_ROUTE_PROFILE_FILE = "poi_route_profile.jsonl";
    private static final String POI_TAGS_FILE = "poi_tags.jsonl";
    private static final String POI_EMBEDDING_DOCS_FILE = "poi_embedding_docs.jsonl";
    private static final String USER_PROFILES_FILE = "user_profiles.jsonl";
    private static final String USER_PREFERENCE_TAGS_FILE = "user_preference_tags.jsonl";
    private static final String USER_BEHAVIOR_EVENTS_FILE = "user_behavior_events.jsonl";
    private static final String DEMO_USER_CASES_FILE = "demo_user_cases.json";
    private static final String POI_JOINED_VIEW_FILE = "poi_joined_view.jsonl";
    private static final String NEWLINE = "\n";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper prettyJsonMapper;
    private final ObjectMapper compactJsonMapper;

    public Phase2StaticMockDataGenerator() {
        this.prettyJsonMapper = createObjectMapper(true);
        this.compactJsonMapper = createObjectMapper(false);
    }

    public GeneratedData generate() {
        List<BusinessArea> businessAreas = buildBusinessAreas();
        List<TrafficMatrixEntry> trafficMatrix = buildTrafficMatrix(businessAreas);
        List<RouteTemplate> routeTemplates = buildRouteTemplates();
        List<SlotTransitionRule> slotTransitionRules = buildSlotTransitionRules();

        Random random = new Random(RANDOM_SEED);
        PoiDataBundle poiDataBundle = buildPoiData(businessAreas, random);
        UserDataBundle userDataBundle = buildUserData(poiDataBundle.poiBasic(), random);
        List<DemoUserCase> demoUserCases = buildDemoUserCases(userDataBundle.userProfiles(), businessAreas, random);
        List<PoiJoinedView> poiJoinedViews = buildPoiJoinedViews(poiDataBundle);

        return new GeneratedData(
                businessAreas,
                trafficMatrix,
                routeTemplates,
                slotTransitionRules,
                poiDataBundle.poiBasic(),
                poiDataBundle.poiRatingStats(),
                poiDataBundle.poiBusinessInfo(),
                poiDataBundle.poiUgcSummaries(),
                poiDataBundle.poiRouteProfiles(),
                poiDataBundle.poiTags(),
                poiDataBundle.poiEmbeddingDocs(),
                userDataBundle.userProfiles(),
                userDataBundle.userPreferenceTags(),
                userDataBundle.userBehaviorEvents(),
                demoUserCases,
                poiJoinedViews
        );
    }

    public void writeTo(Path outputDirectory) throws IOException {
        GeneratedData data = generate();
        Files.createDirectories(outputDirectory);

        writePrettyJson(outputDirectory.resolve(BUSINESS_AREAS_FILE), data.businessAreas());
        writePrettyJson(outputDirectory.resolve(TRAFFIC_MATRIX_FILE), data.trafficMatrix());
        writePrettyJson(outputDirectory.resolve(ROUTE_TEMPLATES_FILE), data.routeTemplates());
        writePrettyJson(outputDirectory.resolve(SLOT_TRANSITION_RULES_FILE), data.slotTransitionRules());

        writeJsonLines(outputDirectory.resolve(POI_BASIC_FILE), data.poiBasic());
        writeJsonLines(outputDirectory.resolve(POI_RATING_STATS_FILE), data.poiRatingStats());
        writeJsonLines(outputDirectory.resolve(POI_BUSINESS_INFO_FILE), data.poiBusinessInfo());
        writeJsonLines(outputDirectory.resolve(POI_UGC_SUMMARY_FILE), data.poiUgcSummaries());
        writeJsonLines(outputDirectory.resolve(POI_ROUTE_PROFILE_FILE), data.poiRouteProfiles());
        writeJsonLines(outputDirectory.resolve(POI_TAGS_FILE), data.poiTags());
        writeJsonLines(outputDirectory.resolve(POI_EMBEDDING_DOCS_FILE), data.poiEmbeddingDocs());
        writeJsonLines(outputDirectory.resolve(USER_PROFILES_FILE), data.userProfiles());
        writeJsonLines(outputDirectory.resolve(USER_PREFERENCE_TAGS_FILE), data.userPreferenceTags());
        writeJsonLines(outputDirectory.resolve(USER_BEHAVIOR_EVENTS_FILE), data.userBehaviorEvents());
        writePrettyJson(outputDirectory.resolve(DEMO_USER_CASES_FILE), data.demoUserCases());
        writeJsonLines(outputDirectory.resolve(POI_JOINED_VIEW_FILE), data.poiJoinedViews());
    }

    public static void main(String[] args) throws IOException {
        Path outputDirectory = args.length > 0
                ? Path.of(args[0])
                : Path.of("src", "main", "resources", "mock-data");
        new Phase2StaticMockDataGenerator().writeTo(outputDirectory);
    }

    private ObjectMapper createObjectMapper(boolean pretty) {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
        if (pretty) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        return mapper;
    }

    private void writePrettyJson(Path targetFile, Object value) throws IOException {
        prettyJsonMapper.writeValue(targetFile.toFile(), value);
    }

    private void writeJsonLines(Path targetFile, List<?> values) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            builder.append(compactJsonMapper.writeValueAsString(values.get(i)));
            if (i < values.size() - 1) {
                builder.append(NEWLINE);
            }
        }
        Files.writeString(targetFile, builder.toString(), StandardCharsets.UTF_8);
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
                        List.of("夜景", "散步", "约会", "松弛感"),
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
                        List.of("老北京", "游客", "景点", "本地小吃"),
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
                        List.of("本地小吃", "烟火气", "清真餐饮", "本地感"),
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
            areasByDistrict.computeIfAbsent(businessArea.district(), ignored -> new ArrayList<>()).add(businessArea);
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
            BusinessArea first = areaByName.get(pair.get(0));
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
                slotTransitionRule("强体验活动", "晚餐主餐", 0.90, "高强度活动后回归主餐补给合理。"),
                slotTransitionRule("室内展览", "商场餐饮", 0.90, "雨天和亲子路线常用的稳定组合。"),
                slotTransitionRule("商场餐饮", "室内娱乐", 0.88, "同场景切换成本低，适合雨天。"),
                slotTransitionRule("文化体验点", "午餐主餐", 0.80, "白天文化活动后接正餐较常见。"),
                slotTransitionRule("午餐主餐", "室内活动", 0.76, "亲子和雨天路线中频繁出现。"),
                slotTransitionRule("散步点", "咖啡休息点", 0.90, "独处放松路线需要节奏缓冲。"),
                slotTransitionRule("文艺点", "晚餐主餐", 0.77, "文艺体验后接餐饮，适合慢节奏晚间路线。"),
                slotTransitionRule("夜生活点", "聊天点", 0.79, "夜生活开始后常切换到可停留交流的场景。")
        );
    }

    private PoiDataBundle buildPoiData(List<BusinessArea> businessAreas, Random random) {
        List<PoiCategoryBlueprint> blueprints = buildPoiCategoryBlueprints();
        Map<String, Integer> categoryCounters = new LinkedHashMap<>();

        List<PoiBasic> poiBasic = new ArrayList<>();
        List<PoiRatingStats> poiRatingStats = new ArrayList<>();
        List<PoiBusinessInfo> poiBusinessInfo = new ArrayList<>();
        List<PoiUgcSummary> poiUgcSummaries = new ArrayList<>();
        List<PoiRouteProfile> poiRouteProfiles = new ArrayList<>();
        List<PoiTag> poiTags = new ArrayList<>();
        List<PoiEmbeddingDoc> poiEmbeddingDocs = new ArrayList<>();

        int poiSequence = 1;
        for (BusinessArea area : businessAreas) {
            List<PoiCategoryBlueprint> areaPlan = buildAreaBlueprintPlan(area, blueprints);
            for (int slotIndex = 0; slotIndex < areaPlan.size(); slotIndex++) {
                PoiCategoryBlueprint blueprint = areaPlan.get(slotIndex);
                String counterKey = area.areaName() + "|" + blueprint.namePrefix();
                int categoryIndex = categoryCounters.merge(counterKey, 1, Integer::sum);
                String poiId = String.format("P%05d", poiSequence++);
                String name = blueprint.namePrefix() + alphabeticIndex(categoryIndex) + "·" + area.areaName();
                Coordinate coordinate = offsetCoordinate(area, random, 0.9);
                int addressNo = 10 + slotIndex + random.nextInt(30);

                PoiBasic basic = new PoiBasic(
                        poiId,
                        name,
                        CITY,
                        area.district(),
                        area.areaName(),
                        "北京市" + area.district() + area.areaName() + "片区" + addressNo + "号",
                        coordinate.lat(),
                        coordinate.lng(),
                        COORDINATE_SYSTEM,
                        blueprint.categoryLv1(),
                        blueprint.categoryLv2(),
                        null,
                        null,
                        "OPEN"
                );
                poiBasic.add(basic);

                double baseRating = 4.1 + random.nextDouble() * 0.8;
                double popularityScore = 70 + random.nextDouble() * 29;
                PoiRatingStats ratingStats = new PoiRatingStats(
                        poiId,
                        rounded(baseRating, 2),
                        rounded(baseRating - 0.05 + random.nextDouble() * 0.25, 2),
                        rounded(baseRating - 0.10 + random.nextDouble() * 0.30, 2),
                        rounded(baseRating - 0.08 + random.nextDouble() * 0.28, 2),
                        120 + random.nextInt(2800),
                        40 + random.nextInt(1100),
                        rounded(popularityScore, 2),
                        area.areaName() + blueprint.categoryLv1() + "热门榜第" + (1 + random.nextInt(10)) + "名（模拟）"
                );
                poiRatingStats.add(ratingStats);

                int avgPrice = randomInt(random, blueprint.minPrice(), blueprint.maxPrice());
                boolean reservationAvailable = blueprint.maxPrice() >= 180 || "夜间消费".equals(blueprint.categoryLv1());
                boolean queueSupported = !"景点观光".equals(blueprint.categoryLv1());
                int avgQueueMinutes = queueSupported ? randomInt(random, 5, 45) : 0;
                boolean hasGroupBuy = "咖啡甜品".equals(blueprint.categoryLv1())
                        || "娱乐活动".equals(blueprint.categoryLv1())
                        || "餐饮".equals(blueprint.categoryLv1());
                PoiBusinessInfo businessInfo = new PoiBusinessInfo(
                        poiId,
                        avgPrice,
                        businessHoursFor(blueprint),
                        reservationAvailable,
                        queueSupported,
                        avgQueueMinutes,
                        hasGroupBuy,
                        hasGroupBuy ? buildCouponDescription(blueprint, avgPrice) : "无"
                );
                poiBusinessInfo.add(businessInfo);

                List<String> positiveKeywords = takeFirstDistinct(
                        area.areaTags().get(0),
                        blueprint.positiveKeywords().get(0),
                        blueprint.positiveKeywords().get(1),
                        area.areaTags().get(area.areaTags().size() - 1)
                );
                List<String> negativeKeywords = takeFirstDistinct(
                        blueprint.negativeKeywords().get(0),
                        blueprint.negativeKeywords().get(1),
                        "周末更热闹"
                );
                List<String> crowdKeywords = takeFirstDistinct(
                        blueprint.crowdKeywords().get(0),
                        blueprint.crowdKeywords().get(1),
                        area.suitableScenes().get(0)
                );
                List<String> sceneKeywords = takeFirstDistinct(
                        area.suitableScenes().get(0),
                        blueprint.suitableScenes().get(0),
                        blueprint.routeRoles().get(0)
                );
                PoiUgcSummary ugcSummary = new PoiUgcSummary(
                        poiId,
                        positiveKeywords,
                        negativeKeywords,
                        crowdKeywords,
                        sceneKeywords,
                        name + "整体偏" + area.areaTags().get(0) + "氛围，适合" + area.suitableScenes().get(0) + "，工作日体验更稳定。",
                        "如果非常在意" + negativeKeywords.get(0) + "，建议错峰前往。",
                        "适合想要" + sceneKeywords.get(0) + "、同时偏好" + positiveKeywords.get(0) + "的人群。"
                );
                poiUgcSummaries.add(ugcSummary);

                int stayMinutes = randomInt(random, blueprint.minStayMinutes(), blueprint.maxStayMinutes());
                List<String> suitableScenes = takeFirstDistinct(
                        area.suitableScenes().get(0),
                        area.suitableScenes().get(Math.min(1, area.suitableScenes().size() - 1)),
                        blueprint.suitableScenes().get(0),
                        blueprint.suitableScenes().get(1)
                );
                PoiRouteProfile routeProfile = new PoiRouteProfile(
                        poiId,
                        blueprint.routeRoles(),
                        suitableScenes,
                        blueprint.suitableTimePeriods(),
                        stayMinutes,
                        blueprint.indoorOutdoor(),
                        blueprint.weatherSensitive(),
                        blueprint.energyLevel(),
                        blueprint.noiseLevel(),
                        blueprint.photoFriendly(),
                        blueprint.familyFriendly(),
                        rounded(72 + random.nextDouble() * 24, 2)
                );
                poiRouteProfiles.add(routeProfile);

                addPoiTags(poiTags, poiId, area, blueprint, positiveKeywords, sceneKeywords, random);

                PoiEmbeddingDoc embeddingDoc = new PoiEmbeddingDoc(
                        poiId,
                        name + "，位于" + area.district() + area.areaName() + "，属于" + blueprint.categoryLv1() + "/"
                                + blueprint.categoryLv2() + "，人均" + avgPrice + "元，适合"
                                + String.join("、", suitableScenes) + "，常见角色有" + String.join("、", blueprint.routeRoles())
                                + "，关键词：" + String.join("、", positiveKeywords) + "、" + String.join("、", area.areaTags()) + "。",
                        null,
                        staticTimestamp(10 + poiSequence)
                );
                poiEmbeddingDocs.add(embeddingDoc);
            }
        }

        return new PoiDataBundle(
                poiBasic,
                poiRatingStats,
                poiBusinessInfo,
                poiUgcSummaries,
                poiRouteProfiles,
                poiTags,
                poiEmbeddingDocs
        );
    }

    private void addPoiTags(
            List<PoiTag> poiTags,
            String poiId,
            BusinessArea area,
            PoiCategoryBlueprint blueprint,
            List<String> positiveKeywords,
            List<String> sceneKeywords,
            Random random
    ) {
        poiTags.add(new PoiTag(poiId, "category", blueprint.categoryLv1(), rounded(0.98, 2), "mock_rule"));
        poiTags.add(new PoiTag(poiId, "sub_category", blueprint.categoryLv2(), rounded(0.95, 2), "mock_rule"));
        poiTags.add(new PoiTag(poiId, "scene", sceneKeywords.get(0), rounded(0.90 + random.nextDouble() * 0.08, 2), "mock_rule"));
        poiTags.add(new PoiTag(poiId, "vibe", area.areaTags().get(0), rounded(0.86 + random.nextDouble() * 0.1, 2), "mock_rule"));
        poiTags.add(new PoiTag(poiId, "feature", positiveKeywords.get(0), rounded(0.82 + random.nextDouble() * 0.12, 2), "mock_rule"));
    }

    private List<PoiCategoryBlueprint> buildPoiCategoryBlueprints() {
        return List.of(
                new PoiCategoryBlueprint(
                        "餐饮", "小吃快餐", "餐饮",
                        20, 60, 40, 75,
                        List.of("午餐主餐", "夜宵点"),
                        List.of("低预算学生路线", "Citywalk", "朋友聚会"),
                        List.of("中午", "下午", "晚间"),
                        "indoor", false, "low", "medium", false, false,
                        List.of("性价比高", "出餐快", "烟火气"),
                        List.of("高峰易满座", "座位紧凑", "热门时段嘈杂"),
                        List.of("学生多", "本地客多", "朋友结伴多")
                ),
                new PoiCategoryBlueprint(
                        "咖啡甜品", "咖啡甜品", "咖啡甜品",
                        30, 100, 30, 90,
                        List.of("咖啡休息点", "甜品收尾", "聊天点"),
                        List.of("独处放松路线", "情侣约会", "Citywalk"),
                        List.of("上午", "下午", "晚间"),
                        "indoor", false, "low", "low", true, false,
                        List.of("适合拍照", "停留舒适", "甜口友好"),
                        List.of("热门时段排队", "甜度偏高", "周末人多"),
                        List.of("情侣多", "朋友小聚多", "独处办公多")
                ),
                new PoiCategoryBlueprint(
                        "餐饮", "普通餐饮", "餐饮",
                        60, 180, 60, 120,
                        List.of("午餐主餐", "晚餐主餐"),
                        List.of("朋友聚会", "情侣约会", "游客半日游"),
                        List.of("中午", "晚间"),
                        "indoor", false, "medium", "medium", false, true,
                        List.of("口味稳定", "适合多人", "下饭"),
                        List.of("高峰期等位", "上菜略慢", "周末更热闹"),
                        List.of("家庭客多", "朋友聚餐多", "上班族多")
                ),
                new PoiCategoryBlueprint(
                        "餐饮", "高端餐饮", "餐饮",
                        200, 600, 75, 150,
                        List.of("晚餐主餐", "聊天点"),
                        List.of("情侣约会", "独处放松路线", "朋友聚会"),
                        List.of("晚间"),
                        "indoor", false, "low", "low", true, false,
                        List.of("氛围精致", "服务细致", "适合约会"),
                        List.of("预算要求高", "需要预约", "用餐节奏慢"),
                        List.of("约会人群多", "商务宴请多", "庆祝场景多")
                ),
                new PoiCategoryBlueprint(
                        "文化艺术", "展览美术馆", "文化艺术",
                        0, 150, 60, 150,
                        List.of("文化体验点", "室内活动", "文艺点"),
                        List.of("Citywalk", "雨天路线", "独处放松路线"),
                        List.of("上午", "下午"),
                        "indoor", false, "low", "low", true, true,
                        List.of("内容完整", "适合慢逛", "空间舒展"),
                        List.of("闭馆较早", "热门展期人多", "讲解位有限"),
                        List.of("游客多", "独处观展多", "亲子同行多")
                ),
                new PoiCategoryBlueprint(
                        "娱乐活动", "密室剧本杀", "娱乐活动",
                        100, 250, 90, 180,
                        List.of("强体验活动", "聚会点", "夜间娱乐"),
                        List.of("朋友聚会", "雨天路线", "低预算学生路线"),
                        List.of("下午", "晚间"),
                        "indoor", false, "high", "high", false, false,
                        List.of("参与感强", "适合组局", "停留时间长"),
                        List.of("需要提前约场", "高峰期爆满", "时间不够会赶"),
                        List.of("朋友结伴多", "学生组局多", "夜间客流多")
                ),
                new PoiCategoryBlueprint(
                        "景点观光", "景点公园", "景点观光",
                        0, 100, 30, 120,
                        List.of("景点打卡", "散步点", "夜景散步"),
                        List.of("Citywalk", "游客半日游", "独处放松路线"),
                        List.of("上午", "下午", "晚间"),
                        "outdoor", true, "medium", "low", true, true,
                        List.of("适合散步", "视野开阔", "拍照出片"),
                        List.of("天气影响大", "节假日拥挤", "步行量偏高"),
                        List.of("游客多", "拍照人群多", "散步人群多")
                ),
                new PoiCategoryBlueprint(
                        "夜间消费", "酒吧清吧", "夜间消费",
                        80, 300, 60, 150,
                        List.of("清吧收尾", "夜生活点", "夜宵点"),
                        List.of("夜游路线", "情侣约会", "朋友聚会"),
                        List.of("晚间", "深夜前"),
                        "indoor", false, "low", "high", true, false,
                        List.of("氛围在线", "适合收尾", "夜间节奏好"),
                        List.of("周末偏吵", "深夜排队", "预算浮动大"),
                        List.of("夜游人群多", "约会人群多", "朋友续摊多")
                )
        );
    }

    private List<PoiCategoryBlueprint> buildAreaBlueprintPlan(BusinessArea area, List<PoiCategoryBlueprint> blueprints) {
        PoiCategoryBlueprint snack = blueprints.get(0);
        PoiCategoryBlueprint cafe = blueprints.get(1);
        PoiCategoryBlueprint regularDining = blueprints.get(2);
        PoiCategoryBlueprint fineDining = blueprints.get(3);
        PoiCategoryBlueprint culture = blueprints.get(4);
        PoiCategoryBlueprint entertainment = blueprints.get(5);
        PoiCategoryBlueprint scenic = blueprints.get(6);
        PoiCategoryBlueprint nightlife = blueprints.get(7);

        List<PoiCategoryBlueprint> plan = new ArrayList<>(List.of(
                regularDining,
                cafe,
                culture,
                scenic,
                entertainment,
                snack,
                fineDining,
                cafe,
                nightlife,
                regularDining
        ));

        if (hasTag(area, "学生") || hasTag(area, "低预算") || hasTag(area, "性价比")) {
            plan.set(6, snack);
            plan.set(8, entertainment);
            plan.set(9, snack);
        }
        if (hasTag(area, "夜景") || hasTag(area, "夜生活") || hasTag(area, "夜宵")) {
            plan.set(3, scenic);
            plan.set(8, nightlife);
        }
        if (hasTag(area, "游客") || hasTag(area, "胡同") || hasTag(area, "景点")) {
            plan.set(2, culture);
            plan.set(3, scenic);
            plan.set(7, scenic);
        }
        if (hasTag(area, "商场") || hasTag(area, "亲子")) {
            plan.set(3, entertainment);
            plan.set(4, culture);
            plan.set(8, cafe);
        }
        if (hasTag(area, "商务") || hasTag(area, "精致")) {
            plan.set(0, fineDining);
            plan.set(6, fineDining);
            plan.set(8, nightlife);
        }
        if (hasTag(area, "公园") || hasTag(area, "户外") || hasTag(area, "散步")) {
            plan.set(3, scenic);
            plan.set(7, scenic);
        }
        return plan;
    }

    private UserDataBundle buildUserData(List<PoiBasic> poiBasics, Random random) {
        List<UserProfile> userProfiles = new ArrayList<>();
        List<UserPreferenceTag> userPreferenceTags = new ArrayList<>();
        List<UserBehaviorEvent> userBehaviorEvents = new ArrayList<>();

        List<String> budgetLevels = List.of("低", "中", "高");
        List<String> paceLevels = List.of("轻松", "适中", "紧凑");
        List<String> transports = List.of("步行+地铁", "步行+打车", "地铁");
        List<String> sceneValues = List.of("情侣约会", "朋友聚会", "Citywalk", "游客半日游", "夜游路线");
        List<String> vibeValues = List.of("安静", "烟火气", "适合拍照", "松弛感", "热闹");
        List<String> foodValues = List.of("火锅", "清淡", "甜品", "本地小吃", "韩餐");
        List<String> avoidValues = List.of("排队久", "太吵", "步行太多", "太网红", "停车不便");
        List<String> paceValues = List.of("少走路", "节奏紧凑", "慢慢逛", "适合聊天", "夜间活动");
        List<String> eventTypes = List.of("CLICK_POI", "SAVE_ROUTE", "VIEW_POI", "LIKE_POI");

        for (int i = 0; i < 20; i++) {
            String userId = String.format("U%05d", 10001 + i);
            UserProfile userProfile = new UserProfile(
                    userId,
                    "路线体验者" + String.format("%02d", i + 1),
                    CITY,
                    budgetLevels.get(i % budgetLevels.size()),
                    paceLevels.get(i % paceLevels.size()),
                    transports.get(i % transports.size()),
                    staticTimestamp(i),
                    staticTimestamp(i + 20)
            );
            userProfiles.add(userProfile);

            userPreferenceTags.add(new UserPreferenceTag(userId, "scene", sceneValues.get(i % sceneValues.size()), rounded(0.60 + random.nextDouble() * 0.25, 2), i % 2 == 0 ? "history" : "explicit", staticTimestamp(30 + i)));
            userPreferenceTags.add(new UserPreferenceTag(userId, "vibe", vibeValues.get(i % vibeValues.size()), rounded(0.58 + random.nextDouble() * 0.27, 2), i % 2 == 0 ? "explicit" : "history", staticTimestamp(60 + i)));
            userPreferenceTags.add(new UserPreferenceTag(userId, "food", foodValues.get(i % foodValues.size()), rounded(0.55 + random.nextDouble() * 0.30, 2), "history", staticTimestamp(90 + i)));
            userPreferenceTags.add(new UserPreferenceTag(userId, "avoid", avoidValues.get(i % avoidValues.size()), rounded(0.70 + random.nextDouble() * 0.20, 2), "explicit", staticTimestamp(120 + i)));
            userPreferenceTags.add(new UserPreferenceTag(userId, "pace", paceValues.get(i % paceValues.size()), rounded(0.56 + random.nextDouble() * 0.24, 2), "history", staticTimestamp(150 + i)));

            for (int eventIndex = 0; eventIndex < 4; eventIndex++) {
                PoiBasic poi = poiBasics.get((i * 7 + eventIndex * 11) % poiBasics.size());
                userBehaviorEvents.add(new UserBehaviorEvent(
                        String.format("E%05d", i * 4 + eventIndex + 1),
                        userId,
                        eventTypes.get((i + eventIndex) % eventTypes.size()),
                        poi.poiId(),
                        String.format("DR%05d", 3000 + i),
                        List.of("scene:" + sceneValues.get(i % sceneValues.size()), "avoid:" + avoidValues.get(i % avoidValues.size())),
                        staticTimestamp(180 + i * 4 + eventIndex)
                ));
            }
        }

        return new UserDataBundle(userProfiles, userPreferenceTags, userBehaviorEvents);
    }

    private List<DemoUserCase> buildDemoUserCases(List<UserProfile> userProfiles, List<BusinessArea> businessAreas, Random random) {
        List<DemoCaseSeed> seeds = List.of(
                demoCaseSeed("情侣约会", "18:00-22:00", 2, 520, List.of("拍照", "氛围好"), List.of("太吵", "排队久"), "今晚想在{area}附近约会，预算{budget}，希望氛围好一点，别太折腾。"),
                demoCaseSeed("朋友聚会", "19:00-23:00", 4, 760, List.of("能聊天", "夜生活"), List.of("步行太多", "太贵"), "和朋友想在{area}聚一下，最好有吃有玩，预算控制在{budget}左右。"),
                demoCaseSeed("Citywalk", "14:00-19:00", 2, 320, List.of("胡同", "拍照"), List.of("商业味太重"), "下午想在{area}做个 citywalk，顺便找地方休息和吃点东西。"),
                demoCaseSeed("游客半日游", "10:00-15:00", 3, 480, List.of("景点", "本地感"), List.of("绕路", "太累"), "有外地朋友来北京，想在{area}附近安排半天路线，兼顾打卡和吃饭。"),
                demoCaseSeed("低预算学生路线", "15:00-21:00", 3, 260, List.of("性价比", "朋友聚会"), List.of("太贵", "预约麻烦"), "学生党想在{area}附近玩一圈，预算别超过{budget}。"),
                demoCaseSeed("亲子室内路线", "11:00-17:00", 3, 560, List.of("室内", "轻松"), List.of("太吵", "步行太多"), "周末带孩子在{area}附近转转，最好主要是室内路线。"),
                demoCaseSeed("雨天路线", "13:00-20:00", 2, 420, List.of("室内活动", "少走路"), List.of("户外", "换乘多"), "下雨天想在{area}附近安排室内一点的路线，预算{budget}以内。"),
                demoCaseSeed("夜游路线", "19:30-23:30", 2, 600, List.of("夜景", "收尾喝点"), List.of("太早关门", "太安静"), "晚上想在{area}附近夜游，最好有夜景和收尾点。"),
                demoCaseSeed("独处放松路线", "15:00-20:00", 1, 300, List.of("安静", "慢节奏"), List.of("太网红", "太挤"), "一个人想在{area}附近放松一下，轻松慢慢来就行。"),
                demoCaseSeed("朋友聚会", "16:00-22:00", 5, 880, List.of("体验感", "聚会"), List.of("太分散", "等待久"), "想在{area}附近安排朋友聚会，最好有一个强体验点。")
        );

        List<DemoUserCase> demoUserCases = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            DemoCaseSeed seed = seeds.get(i % seeds.size());
            BusinessArea area = businessAreas.get((i * 3 + 2) % businessAreas.size());
            UserProfile user = userProfiles.get(i % userProfiles.size());
            int adjustedBudget = seed.budget() + randomInt(random, -40, 60);
            String userQuery = seed.queryTemplate()
                    .replace("{area}", area.areaName())
                    .replace("{budget}", String.valueOf(adjustedBudget));

            demoUserCases.add(new DemoUserCase(
                    String.format("C%04d", i + 1),
                    user.userId(),
                    userQuery,
                    CITY,
                    area.district(),
                    area.areaName(),
                    seed.timeWindow(),
                    seed.partySize(),
                    adjustedBudget,
                    seed.preferTags(),
                    seed.avoidTags(),
                    seed.scene()
            ));
        }
        return demoUserCases;
    }

    private DemoCaseSeed demoCaseSeed(
            String scene,
            String timeWindow,
            int partySize,
            int budget,
            List<String> preferTags,
            List<String> avoidTags,
            String queryTemplate
    ) {
        return new DemoCaseSeed(scene, timeWindow, partySize, budget, List.copyOf(preferTags), List.copyOf(avoidTags), queryTemplate);
    }

    private List<PoiJoinedView> buildPoiJoinedViews(PoiDataBundle bundle) {
        Map<String, PoiRatingStats> ratingByPoiId = indexByPoiId(bundle.poiRatingStats(), PoiRatingStats::poiId);
        Map<String, PoiBusinessInfo> businessInfoByPoiId = indexByPoiId(bundle.poiBusinessInfo(), PoiBusinessInfo::poiId);
        Map<String, PoiUgcSummary> ugcByPoiId = indexByPoiId(bundle.poiUgcSummaries(), PoiUgcSummary::poiId);
        Map<String, PoiRouteProfile> routeProfileByPoiId = indexByPoiId(bundle.poiRouteProfiles(), PoiRouteProfile::poiId);
        Map<String, PoiEmbeddingDoc> embeddingByPoiId = indexByPoiId(bundle.poiEmbeddingDocs(), PoiEmbeddingDoc::poiId);
        Map<String, List<PoiTag>> tagsByPoiId = new LinkedHashMap<>();
        for (PoiTag poiTag : bundle.poiTags()) {
            tagsByPoiId.computeIfAbsent(poiTag.poiId(), ignored -> new ArrayList<>()).add(poiTag);
        }

        List<PoiJoinedView> joinedViews = new ArrayList<>();
        for (PoiBasic basic : bundle.poiBasic()) {
            joinedViews.add(new PoiJoinedView(
                    basic.poiId(),
                    basic,
                    ratingByPoiId.get(basic.poiId()),
                    businessInfoByPoiId.get(basic.poiId()),
                    ugcByPoiId.get(basic.poiId()),
                    routeProfileByPoiId.get(basic.poiId()),
                    tagsByPoiId.getOrDefault(basic.poiId(), List.of()),
                    embeddingByPoiId.get(basic.poiId())
            ));
        }
        return joinedViews;
    }

    private <T> Map<String, T> indexByPoiId(List<T> records, java.util.function.Function<T, String> keyExtractor) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T record : records) {
            result.put(keyExtractor.apply(record), record);
        }
        return result;
    }

    private String businessHoursFor(PoiCategoryBlueprint blueprint) {
        return switch (blueprint.categoryLv1()) {
            case "餐饮" -> "11:00-14:00,17:00-21:30";
            case "咖啡甜品" -> "10:00-22:00";
            case "文化艺术" -> "10:00-19:00";
            case "娱乐活动" -> "13:00-22:30";
            case "景点观光" -> "09:00-21:00";
            case "夜间消费" -> "18:00-02:00";
            default -> "10:00-21:00";
        };
    }

    private String buildCouponDescription(PoiCategoryBlueprint blueprint, int avgPrice) {
        if ("咖啡甜品".equals(blueprint.categoryLv1())) {
            return "工作日双人饮品券，预计节省" + Math.max(10, avgPrice / 5) + "元";
        }
        if ("娱乐活动".equals(blueprint.categoryLv1())) {
            return "四人同行体验券，预计节省" + Math.max(20, avgPrice / 4) + "元";
        }
        return "双人套餐券，预计节省" + Math.max(15, avgPrice / 6) + "元";
    }

    private Coordinate offsetCoordinate(BusinessArea area, Random random, double maxRadiusKm) {
        double radiusKm = random.nextDouble() * maxRadiusKm;
        double angle = random.nextDouble() * Math.PI * 2;
        double latOffset = (radiusKm / 111.0) * Math.cos(angle);
        double lngOffset = (radiusKm / (111.0 * Math.cos(Math.toRadians(area.centerLat())))) * Math.sin(angle);
        return new Coordinate(
                rounded(area.centerLat() + latOffset, 6),
                rounded(area.centerLng() + lngOffset, 6)
        );
    }

    private boolean hasTag(BusinessArea area, String tag) {
        return area.areaTags().stream().anyMatch(item -> item.contains(tag));
    }

    private int randomInt(Random random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private List<String> takeFirstDistinct(String... rawValues) {
        LinkedHashSet<String> uniqueValues = new LinkedHashSet<>();
        for (String value : rawValues) {
            uniqueValues.add(value);
        }
        return uniqueValues.stream().filter(Objects::nonNull).toList();
    }

    private String alphabeticIndex(int index) {
        StringBuilder builder = new StringBuilder();
        int value = index;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            builder.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return builder.toString();
    }

    private String staticTimestamp(int hourOffset) {
        return LocalDateTime.of(2026, 5, 28, 9, 0)
                .plusHours(hourOffset)
                .format(DATE_TIME_FORMATTER);
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
            List<SlotTransitionRule> slotTransitionRules,
            List<PoiBasic> poiBasic,
            List<PoiRatingStats> poiRatingStats,
            List<PoiBusinessInfo> poiBusinessInfo,
            List<PoiUgcSummary> poiUgcSummaries,
            List<PoiRouteProfile> poiRouteProfiles,
            List<PoiTag> poiTags,
            List<PoiEmbeddingDoc> poiEmbeddingDocs,
            List<UserProfile> userProfiles,
            List<UserPreferenceTag> userPreferenceTags,
            List<UserBehaviorEvent> userBehaviorEvents,
            List<DemoUserCase> demoUserCases,
            List<PoiJoinedView> poiJoinedViews
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

    public record PoiBasic(
            String poiId,
            String name,
            String city,
            String district,
            String businessArea,
            String address,
            double lat,
            double lng,
            String coordinateSystem,
            String categoryLv1,
            String categoryLv2,
            String brand,
            String branchName,
            String status
    ) {
    }

    public record PoiRatingStats(
            String poiId,
            double rating,
            double tasteScore,
            double environmentScore,
            double serviceScore,
            int reviewCount,
            int favoriteCount,
            double popularityScore,
            String rankDesc
    ) {
    }

    public record PoiBusinessInfo(
            String poiId,
            int avgPrice,
            String businessHours,
            boolean reservationAvailable,
            boolean queueSupported,
            int avgQueueMinutes,
            boolean hasGroupBuy,
            String couponDesc
    ) {
    }

    public record PoiUgcSummary(
            String poiId,
            List<String> positiveKeywords,
            List<String> negativeKeywords,
            List<String> crowdKeywords,
            List<String> sceneKeywords,
            String reviewSummary,
            String avoidReason,
            String recommendReason
    ) {
    }

    public record PoiRouteProfile(
            String poiId,
            List<String> routeRoles,
            List<String> suitableScenes,
            List<String> suitableTimePeriods,
            int avgStayMinutes,
            String indoorOutdoor,
            boolean weatherSensitive,
            String energyLevel,
            String noiseLevel,
            boolean photoFriendly,
            boolean familyFriendly,
            double routeScore
    ) {
    }

    public record PoiTag(
            String poiId,
            String tagType,
            String tagValue,
            double confidence,
            String source
    ) {
    }

    public record PoiEmbeddingDoc(
            String poiId,
            String embeddingText,
            Object embeddingVector,
            String updatedAt
    ) {
    }

    public record UserProfile(
            String userId,
            String nickname,
            String city,
            String defaultBudgetLevel,
            String defaultPace,
            String defaultTransport,
            String createdAt,
            String updatedAt
    ) {
    }

    public record UserPreferenceTag(
            String userId,
            String tagType,
            String tagValue,
            double weight,
            String source,
            String updatedAt
    ) {
    }

    public record UserBehaviorEvent(
            String eventId,
            String userId,
            String eventType,
            String poiId,
            String routeId,
            List<String> tagSnapshot,
            String eventTime
    ) {
    }

    public record DemoUserCase(
            String caseId,
            String userId,
            String userQuery,
            String city,
            String district,
            String businessArea,
            String timeWindow,
            int partySize,
            int budget,
            List<String> preferTags,
            List<String> avoidTags,
            String expectedScene
    ) {
    }

    public record PoiJoinedView(
            String poiId,
            PoiBasic poiBasic,
            PoiRatingStats poiRatingStats,
            PoiBusinessInfo poiBusinessInfo,
            PoiUgcSummary poiUgcSummary,
            PoiRouteProfile poiRouteProfile,
            List<PoiTag> poiTags,
            PoiEmbeddingDoc poiEmbeddingDoc
    ) {
    }

    private record PoiCategoryBlueprint(
            String categoryLv1,
            String categoryLv2,
            String namePrefix,
            int minPrice,
            int maxPrice,
            int minStayMinutes,
            int maxStayMinutes,
            List<String> routeRoles,
            List<String> suitableScenes,
            List<String> suitableTimePeriods,
            String indoorOutdoor,
            boolean weatherSensitive,
            String energyLevel,
            String noiseLevel,
            boolean photoFriendly,
            boolean familyFriendly,
            List<String> positiveKeywords,
            List<String> negativeKeywords,
            List<String> crowdKeywords
    ) {
    }

    private record PoiDataBundle(
            List<PoiBasic> poiBasic,
            List<PoiRatingStats> poiRatingStats,
            List<PoiBusinessInfo> poiBusinessInfo,
            List<PoiUgcSummary> poiUgcSummaries,
            List<PoiRouteProfile> poiRouteProfiles,
            List<PoiTag> poiTags,
            List<PoiEmbeddingDoc> poiEmbeddingDocs
    ) {
    }

    private record UserDataBundle(
            List<UserProfile> userProfiles,
            List<UserPreferenceTag> userPreferenceTags,
            List<UserBehaviorEvent> userBehaviorEvents
    ) {
    }

    private record DemoCaseSeed(
            String scene,
            String timeWindow,
            int partySize,
            int budget,
            List<String> preferTags,
            List<String> avoidTags,
            String queryTemplate
    ) {
    }

    private record Coordinate(double lat, double lng) {
    }
}
