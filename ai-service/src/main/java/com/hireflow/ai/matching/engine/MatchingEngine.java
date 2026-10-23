package com.hireflow.ai.matching.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.hireflow.ai.matching.api.CandidateMatchProfile;
import com.hireflow.ai.matching.api.DeterministicMatch;
import com.hireflow.ai.matching.api.JobMatchProfile;
import com.hireflow.ai.matching.api.SkillNormalizer;

@Component
public class MatchingEngine {

    private static final double REQUIRED_WEIGHT = 50.0;
    private static final double PREFERRED_WEIGHT = 20.0;
    private static final double EXPERIENCE_WEIGHT = 20.0;

    public DeterministicMatch calculate(CandidateMatchProfile candidate, JobMatchProfile job) {
        Set<String> candidateSkills = normalized(candidate.skills());
        List<String> matchedRequired = matching(job.requiredSkills(), candidateSkills);
        List<String> missingRequired = missing(job.requiredSkills(), candidateSkills);
        List<String> matchedPreferred = matching(job.preferredSkills(), candidateSkills);
        List<String> unavailable = new ArrayList<>();

        double total = 0;
        double availableWeight = 0;

        if (!job.requiredSkills().isEmpty()) {
            total += REQUIRED_WEIGHT * matchedRequired.size() / (double) job.requiredSkills().size();
            availableWeight += REQUIRED_WEIGHT;
        } else {
            unavailable.add("requiredSkills");
        }
        if (!job.preferredSkills().isEmpty()) {
            total += PREFERRED_WEIGHT * matchedPreferred.size() / (double) job.preferredSkills().size();
            availableWeight += PREFERRED_WEIGHT;
        } else {
            unavailable.add("preferredSkills");
        }

        String experienceAssessment;
        if (candidate.yearsOfExperience() == null
                || (job.minimumExperience() == null && job.maximumExperience() == null)) {
            experienceAssessment = "Unavailable: experience data is incomplete";
            unavailable.add("experience");
        } else {
            boolean meetsMinimum = job.minimumExperience() == null
                    || candidate.yearsOfExperience() >= job.minimumExperience();
            boolean withinMaximum = job.maximumExperience() == null
                    || candidate.yearsOfExperience() <= job.maximumExperience();
            total += EXPERIENCE_WEIGHT * ((meetsMinimum && withinMaximum) ? 1 : 0);
            availableWeight += EXPERIENCE_WEIGHT;
            experienceAssessment = meetsMinimum && withinMaximum
                    ? "Documented experience is within the stated range"
                    : "Documented experience is outside the stated range";
        }

        String educationAssessment = "Unavailable: no education requirement is supported";
        unavailable.add("education");
        int score = availableWeight == 0 ? 0 : (int) Math.round(total * 100 / availableWeight);
        return new DeterministicMatch(Math.max(0, Math.min(100, score)), matchedRequired,
                missingRequired, matchedPreferred, experienceAssessment, educationAssessment, unavailable);
    }

    private Set<String> normalized(List<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String skill = SkillNormalizer.normalize(value);
            if (!skill.isBlank()) {
                normalized.add(skill);
            }
        }
        return normalized;
    }

    private List<String> matching(List<String> requested, Set<String> candidateSkills) {
        return requested.stream()
                .filter(value -> candidateSkills.contains(SkillNormalizer.normalize(value)))
                .distinct()
                .toList();
    }

    private List<String> missing(List<String> requested, Set<String> candidateSkills) {
        return requested.stream()
                .filter(value -> !candidateSkills.contains(SkillNormalizer.normalize(value)))
                .distinct()
                .toList();
    }
}
