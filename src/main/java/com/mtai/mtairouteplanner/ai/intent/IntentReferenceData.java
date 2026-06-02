package com.mtai.mtairouteplanner.ai.intent;

import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import com.mtai.mtairouteplanner.data.loader.MockDataLoader;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record IntentReferenceData(
        Set<String> supportedScenes,
        Set<String> supportedBusinessAreas,
        Set<String> supportedDistricts,
        Map<String, String> businessAreaToDistrict
) {
    public IntentReferenceData {
        supportedScenes = immutableOrderedSet(supportedScenes);
        supportedBusinessAreas = immutableOrderedSet(supportedBusinessAreas);
        supportedDistricts = immutableOrderedSet(supportedDistricts);
        businessAreaToDistrict = businessAreaToDistrict == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(businessAreaToDistrict));
    }

    public static IntentReferenceData load() {
        return load(new MockDataLoader());
    }

    public static IntentReferenceData load(MockDataLoader mockDataLoader) {
        MockDataBundle bundle = mockDataLoader.load();
        Set<String> scenes = new LinkedHashSet<>();
        bundle.routeTemplates().forEach(template -> scenes.add(template.scene()));

        Map<String, String> areaToDistrict = new LinkedHashMap<>();
        Set<String> businessAreas = new LinkedHashSet<>();
        Set<String> districts = new LinkedHashSet<>();
        bundle.businessAreas().forEach(area -> {
            businessAreas.add(area.areaName());
            districts.add(area.district());
            areaToDistrict.put(area.areaName(), area.district());
        });

        return new IntentReferenceData(scenes, businessAreas, districts, areaToDistrict);
    }

    public String canonicalScene(String candidate) {
        return canonicalize(candidate, supportedScenes);
    }

    public String canonicalBusinessArea(String candidate) {
        return canonicalize(candidate, supportedBusinessAreas);
    }

    public String canonicalDistrict(String candidate) {
        return canonicalize(candidate, supportedDistricts);
    }

    public Optional<String> districtForBusinessArea(String businessArea) {
        String canonicalBusinessArea = canonicalBusinessArea(businessArea);
        if (canonicalBusinessArea == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessAreaToDistrict.get(canonicalBusinessArea));
    }

    private static Set<String> immutableOrderedSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return Set.copyOf(values.stream()
                .filter(IntentReferenceData::hasText)
                .sorted(Comparator.naturalOrder())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll));
    }

    private String canonicalize(String candidate, Set<String> supportedValues) {
        if (!hasText(candidate)) {
            return null;
        }
        return supportedValues.stream()
                .filter(value -> value.equals(candidate) || value.equalsIgnoreCase(candidate))
                .findFirst()
                .orElseGet(() -> aliasFor(candidate, supportedValues).orElse(null));
    }

    private Optional<String> aliasFor(String candidate, Set<String> supportedValues) {
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        return supportedValues.stream()
                .filter(value -> switch (normalized) {
                                        case "dating", "date", "couple" -> value.contains("约会");
                    case "friends", "friend-gathering", "gathering" -> value.contains("聚会");
                    case "rainy", "rainy-day", "indoor-rainy" -> value.contains("雨天");
                    case "student", "budget-student" -> value.contains("学生");
                    case "family", "kids", "parent-child" -> value.contains("亲子");
                    case "solo", "relax", "relaxed-solo" -> value.contains("独处") || value.contains("放松");
                    case "night", "nightlife" -> value.contains("夜");
                    case "citywalk" -> "Citywalk".equalsIgnoreCase(value);
                    default -> false;
                })
                .findFirst();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
