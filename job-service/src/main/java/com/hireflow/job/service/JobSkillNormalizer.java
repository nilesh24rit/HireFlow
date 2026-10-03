package com.hireflow.job.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes raw skill strings before they are persisted as job skills.
 *
 * <p>Values are trimmed, blank values are dropped and case-insensitive
 * duplicates are collapsed to a single entry.
 */
public final class JobSkillNormalizer {

    private JobSkillNormalizer() {
    }

    public static List<String> normalize(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        Map<String, String> uniqueSkills = new LinkedHashMap<>();
        for (String skill : skills) {
            if (skill == null) {
                continue;
            }
            String trimmed = skill.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            uniqueSkills.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return List.copyOf(uniqueSkills.values());
    }
}
