package com.mtai.mtairouteplanner.data.index;

import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.UserPreferenceTag;
import com.mtai.mtairouteplanner.data.loader.Phase2StaticMockDataGenerator.UserProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserPreferenceIndex {

    private final Map<String, UserProfile> profileByUserId;
    private final Map<String, List<UserPreferenceTag>> preferenceTagsByUserId;

    public UserPreferenceIndex(List<UserProfile> userProfiles, List<UserPreferenceTag> userPreferenceTags) {
        Map<String, UserProfile> profiles = new LinkedHashMap<>();
        for (UserProfile userProfile : userProfiles) {
            profiles.put(userProfile.userId(), userProfile);
        }

        Map<String, List<UserPreferenceTag>> tags = new LinkedHashMap<>();
        for (UserPreferenceTag userPreferenceTag : userPreferenceTags) {
            tags.computeIfAbsent(userPreferenceTag.userId(), ignored -> new java.util.ArrayList<>()).add(userPreferenceTag);
        }

        this.profileByUserId = Map.copyOf(profiles);
        this.preferenceTagsByUserId = tags.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    public Optional<UserProfile> findProfileByUserId(String userId) {
        return Optional.ofNullable(profileByUserId.get(userId));
    }

    public List<UserPreferenceTag> findPreferenceTagsByUserId(String userId) {
        return preferenceTagsByUserId.getOrDefault(userId, List.of());
    }
}

