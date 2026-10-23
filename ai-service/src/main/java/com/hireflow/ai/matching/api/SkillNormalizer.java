package com.hireflow.ai.matching.api;

import java.util.Locale;

public final class SkillNormalizer {

    private SkillNormalizer() {
    }

    public static String normalize(String skill) {
        if (skill == null) {
            return "";
        }
        return skill.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
