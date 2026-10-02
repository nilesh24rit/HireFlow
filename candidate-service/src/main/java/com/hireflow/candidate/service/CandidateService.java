package com.hireflow.candidate.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.candidate.dto.CandidateResponse;
import com.hireflow.candidate.dto.CreateCandidateRequest;
import com.hireflow.candidate.dto.UpdateCandidateRequest;
import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.entity.CandidateSkill;
import com.hireflow.candidate.exception.CandidateNotFoundException;
import com.hireflow.candidate.exception.DuplicateCandidateException;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.repository.CandidateSkillRepository;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final CandidateSkillRepository candidateSkillRepository;

    public CandidateService(CandidateRepository candidateRepository,
            CandidateSkillRepository candidateSkillRepository) {
        this.candidateRepository = candidateRepository;
        this.candidateSkillRepository = candidateSkillRepository;
    }

    @Transactional
    public CandidateResponse createCandidate(CreateCandidateRequest request) {
        if (candidateRepository.existsByUserId(request.userId())) {
            throw duplicateCandidate(request.userId());
        }
        Candidate candidate = new Candidate();
        candidate.setUserId(request.userId());
        candidate.setHeadline(request.headline());
        candidate.setSummary(request.summary());
        candidate.setLocation(request.location());
        candidate.setYearsOfExperience(request.yearsOfExperience());
        candidate.setCurrentCompany(request.currentCompany());
        candidate.setCurrentJobTitle(request.currentJobTitle());
        candidate.setResumeUrl(request.resumeUrl());
        candidate.setLinkedinUrl(request.linkedinUrl());
        candidate.setGithubUrl(request.githubUrl());

        Candidate saved;
        try {
            saved = candidateRepository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCandidate(request.userId());
        }
        saveSkills(saved.getId(), request.skills());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CandidateResponse getCandidateById(UUID id) {
        return toResponse(findCandidate(id));
    }

    @Transactional(readOnly = true)
    public CandidateResponse getCandidateByUserId(UUID userId) {
        Candidate candidate = candidateRepository.findByUserId(userId)
                .orElseThrow(() -> new CandidateNotFoundException(
                        "No candidate found for user '" + userId + "'"));
        return toResponse(candidate);
    }

    @Transactional
    public CandidateResponse updateCandidate(UUID id, UpdateCandidateRequest request) {
        Candidate candidate = findCandidate(id);
        if (request.headline() != null) {
            candidate.setHeadline(request.headline());
        }
        if (request.summary() != null) {
            candidate.setSummary(request.summary());
        }
        if (request.location() != null) {
            candidate.setLocation(request.location());
        }
        if (request.yearsOfExperience() != null) {
            candidate.setYearsOfExperience(request.yearsOfExperience());
        }
        if (request.currentCompany() != null) {
            candidate.setCurrentCompany(request.currentCompany());
        }
        if (request.currentJobTitle() != null) {
            candidate.setCurrentJobTitle(request.currentJobTitle());
        }
        if (request.resumeUrl() != null) {
            candidate.setResumeUrl(request.resumeUrl());
        }
        if (request.linkedinUrl() != null) {
            candidate.setLinkedinUrl(request.linkedinUrl());
        }
        if (request.githubUrl() != null) {
            candidate.setGithubUrl(request.githubUrl());
        }
        if (request.skills() != null) {
            replaceSkills(id, request.skills());
        }
        return toResponse(candidateRepository.saveAndFlush(candidate));
    }

    @Transactional
    public void deleteCandidate(UUID id) {
        Candidate candidate = findCandidate(id);
        candidateRepository.delete(candidate);
    }

    private Candidate findCandidate(UUID id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new CandidateNotFoundException("No candidate found with id '" + id + "'"));
    }

    private void saveSkills(UUID candidateId, List<String> skills) {
        List<String> normalized = normalizeSkills(skills);
        if (normalized.isEmpty()) {
            return;
        }
        List<CandidateSkill> entities = normalized.stream()
                .map(skill -> newSkill(candidateId, skill))
                .toList();
        candidateSkillRepository.saveAll(entities);
        candidateSkillRepository.flush();
    }

    private void replaceSkills(UUID candidateId, List<String> skills) {
        candidateSkillRepository.deleteByCandidateId(candidateId);
        candidateSkillRepository.flush();
        saveSkills(candidateId, skills);
    }

    private List<String> normalizeSkills(List<String> skills) {
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

    private CandidateSkill newSkill(UUID candidateId, String skill) {
        CandidateSkill candidateSkill = new CandidateSkill();
        candidateSkill.setCandidateId(candidateId);
        candidateSkill.setSkill(skill);
        return candidateSkill;
    }

    private DuplicateCandidateException duplicateCandidate(UUID userId) {
        return new DuplicateCandidateException("A candidate profile already exists for user '" + userId + "'");
    }

    private CandidateResponse toResponse(Candidate candidate) {
        List<String> skills = candidateSkillRepository.findByCandidateId(candidate.getId()).stream()
                .map(CandidateSkill::getSkill)
                .toList();
        return new CandidateResponse(
                candidate.getId(),
                candidate.getUserId(),
                candidate.getHeadline(),
                candidate.getSummary(),
                candidate.getLocation(),
                candidate.getYearsOfExperience(),
                candidate.getCurrentCompany(),
                candidate.getCurrentJobTitle(),
                candidate.getResumeUrl(),
                candidate.getLinkedinUrl(),
                candidate.getGithubUrl(),
                skills,
                candidate.getCreatedAt(),
                candidate.getUpdatedAt());
    }
}
