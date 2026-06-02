package com.mtai.mtairouteplanner.data.index;

import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.RouteTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouteTemplateIndex {

    private final List<RouteTemplate> allTemplates;
    private final Map<String, List<RouteTemplate>> byScene;
    private final Map<String, List<RouteTemplate>> byTimePeriod;
    private final Map<String, List<RouteTemplate>> byBudgetLevel;
    private final Map<String, List<RouteTemplate>> byPaceLevel;

    public RouteTemplateIndex(List<RouteTemplate> routeTemplates) {
        this.allTemplates = List.copyOf(routeTemplates);
        Map<String, List<RouteTemplate>> sceneMap = new LinkedHashMap<>();
        Map<String, List<RouteTemplate>> timePeriodMap = new LinkedHashMap<>();
        Map<String, List<RouteTemplate>> budgetLevelMap = new LinkedHashMap<>();
        Map<String, List<RouteTemplate>> paceLevelMap = new LinkedHashMap<>();
        for (RouteTemplate routeTemplate : routeTemplates) {
            sceneMap.computeIfAbsent(routeTemplate.scene(), ignored -> new java.util.ArrayList<>()).add(routeTemplate);
            timePeriodMap.computeIfAbsent(routeTemplate.timePeriod(), ignored -> new java.util.ArrayList<>()).add(routeTemplate);
            budgetLevelMap.computeIfAbsent(routeTemplate.budgetLevel(), ignored -> new java.util.ArrayList<>()).add(routeTemplate);
            paceLevelMap.computeIfAbsent(routeTemplate.paceLevel(), ignored -> new java.util.ArrayList<>()).add(routeTemplate);
        }
        this.byScene = sceneMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
        this.byTimePeriod = timePeriodMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
        this.byBudgetLevel = budgetLevelMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
        this.byPaceLevel = paceLevelMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    public List<RouteTemplate> allTemplates() {
        return allTemplates;
    }

    public List<RouteTemplate> findByScene(String scene) {
        return byScene.getOrDefault(scene, List.of());
    }

    public List<RouteTemplate> findByTimePeriod(String timePeriod) {
        return byTimePeriod.getOrDefault(timePeriod, List.of());
    }

    public List<RouteTemplate> findByBudgetLevel(String budgetLevel) {
        return byBudgetLevel.getOrDefault(budgetLevel, List.of());
    }

    public List<RouteTemplate> findByPaceLevel(String paceLevel) {
        return byPaceLevel.getOrDefault(paceLevel, List.of());
    }

    public List<RouteTemplate> findCandidateTemplates(
            String scene,
            String timePeriod,
            String budgetLevel,
            String paceLevel,
            String district
    ) {
        List<RouteTemplate> base = scene == null ? allTemplates : byScene.getOrDefault(scene, List.of());
        return base.stream()
                .filter(template -> timePeriod == null || template.timePeriod().equals(timePeriod))
                .filter(template -> budgetLevel == null || template.budgetLevel().equals(budgetLevel))
                .filter(template -> paceLevel == null || template.paceLevel().equals(paceLevel))
                .filter(template -> district == null || template.suitableDistricts().contains(district))
                .toList();
    }
}

