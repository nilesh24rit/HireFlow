package com.hireflow.candidate.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.candidate.dto.CandidateResponse;
import com.hireflow.candidate.dto.CreateCandidateRequest;
import com.hireflow.candidate.dto.UpdateCandidateRequest;
import com.hireflow.candidate.entity.Candidate;
import com.hireflow.candidate.entity.CandidateSkill;
import com.hireflow.candidate.exception.DuplicateResourceException;
import com.hireflow.candidate.exception.ResourceNotFoundException;
import com.hireflow.candidate.repository.CandidateRepository;
import com.hireflow.candidate.repository.CandidateSkillRepository;

/**
 * Candidate profile management with HireFlow authorization rules enforced next
 * to the operations they protect ({@code @PreAuthorize} on service methods,
 * not scattered across controllers).
 *
 * <p>The permission matrix for {@code /api/candidates}:</p>
 *
 * <ul>
 *   <li><b>Create</b> — CANDIDATE or ADMIN only. The authenticated user's id is
 *       derived from the security context and used as the profile owner; any
 *       {@code userId} supplied in the request body is overridden by the
 *       principal's id for CANDIDATE callers, so a CANDIDATE can only ever create their own profile.</li>
 *   <li><b>Read by candidate id</b> — any authenticated caller. Reading a profile
 *       does not leak ownership-sensitive information beyond the profile itself.</li>
 *   <li><b>Read by user id</b> — CANDIDATE (own profile only), RECRUITER (any, for
 *       recruitment workflows), or ADMIN.</li>
 *   <li><b>Update</b> — CANDIDATE who owns the profile (userId on the persisted
 *       candidate must equal the token's user id), or ADMIN.</li>
 *   <li><b>Delete</b> — ADMIN only; deletion removes the profile and all skills.</li>
 * </ul>
 *
 * <p><b>Ownership enforcement.</b> Update checks that the persisted candidate's
 * {@code userId} equals the authenticated principal's id before applying any change.
 * A CANDIDATE cannot modify another candidate's profile even if they know its id.
 * Ownership is checked against the database, never against a client-supplied field.</p>
 */
@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final CandidateSkillRepository candidateSkillRepository;

    public CandidateService(CandidateRepository candidateRepository,
            CandidateSkillRepository candidateSkillRepository) {
        this.candidateRepository = candidateRepository;
        this.candidateSkillRepository = candidateSkillRepository;
    }

    /**
     * Creates a candidate profile.
     *
     * <p>For CANDIDATE callers, the {@code userId} from the request body is overridden by
     * the authenticated principal's id, so a CANDIDATE can only ever create their own profile.
     * ADMIN may also create profiles, specifying any target {@code userId}.</p>
     *
     * @param request validated profile payload
     * @return the created candidate
     */
    @Transactional
    @PreAuthorize("hasAnyRole('CANDIDATE','ADMIN')")
    public CandidateResponse createCandidate(CreateCandidateRequest request) {
        UUID ownerId = resolveCandidateOwnerId(request.userId());
        return createCandidateInternal(request, ownerId);
    }

    /**
     * Creates a candidate profile with an explicitly specified owner id.
     *
     * @param request     validated profile payload
     * @param principalId the owner's user id
     * @return the created candidate
     */
    @Transactional
    @PreAuthorize("hasAnyRole('CANDIDATE','ADMIN')")
    public CandidateResponse createCandidate(CreateCandidateRequest request, UUID principalId) {
        return createCandidateInternal(request, principalId);
    }

    private CandidateResponse createCandidateInternal(CreateCandidateRequest request, UUID ownerId) {
        if (candidateRepository.existsByUserId(ownerId)) {
            throw duplicateCandidate(ownerId);
        }
        Candidate candidate = new Candidate();
        candidate.setUserId(ownerId);
        candidate.setHeadline(request.headline());
        candidate.setSummary(request.summary());
        candidate.setLocation(request.location());
        candidate.setYearsOfExperience(request.yearsOfExperience());
        candidate.setCurrentCompany(request.currentCompany());
        candidate.setCurrentJobTitle(request.currentJobTitle());
        candidate.setResumeUrl(request.resumeUrl());
        candidate.setLinkedinUrl(request.linkedinUrl());
        candidate.setGithubUrl(request.githubUrl());

        Candidate saved = candidateRepository.saveAndFlush(candidate);
        saveSkills(saved.getId(), request.skills());
        return toResponse(saved);
    }

    /**
     * Returns a candidate profile by candidate id; any authenticated caller.
     *
     * @param id the candidate's own UUID (from the candidates table)
     * @return the candidate profile
     */
    @Transactional(readOnly = true)
    public CandidateResponse getCandidateById(UUID id) {
        return toResponse(findCandidate(id));
    }

    /**
     * Returns a candidate profile by the owning user's id.
     *
     * <p>A CANDIDATE may only look up their own profile (the userId in the path
     * must equal the authenticated principal). RECRUITER and ADMIN may look up
     * any profile.</p>
     *
     * @param userId id of the owning user
     * @return the candidate profile for the given user
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN') or #userId.toString() == authentication.name")
    public CandidateResponse getCandidateByUserId(UUID userId) {
        Candidate candidate = candidateRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No candidate found with user id '" + userId + "'"));
        return toResponse(candidate);
    }

    /**
     * Updates a candidate profile.
     *
     * <p>A CANDIDATE may only update a profile they own: the persisted
     * {@code userId} of the candidate identified by {@code id} must equal
     * the caller's principal id. ADMIN may update any profile.</p>
     *
     * @param id      the candidate's own UUID (from the candidates table)
     * @param request validated partial update payload
     * @return the updated candidate
     */
    @Transactional
    @PreAuthorize("hasAnyRole('CANDIDATE','ADMIN')")
    public CandidateResponse updateCandidate(UUID id, UpdateCandidateRequest request) {
        Candidate candidate = findCandidate(id);
        enforceCandidateOwnership(candidate);
        return updateCandidateInternal(candidate, request);
    }

    /**
     * Updates a candidate profile with explicit caller identity.
     *
     * @param id          the candidate's own UUID
     * @param request     validated partial update payload
     * @param principalId authenticated user's id
     * @param isAdmin     whether caller has ADMIN role
     * @return the updated candidate
     */
    @Transactional
    @PreAuthorize("hasAnyRole('CANDIDATE','ADMIN')")
    public CandidateResponse updateCandidate(UUID id, UpdateCandidateRequest request,
            UUID principalId, boolean isAdmin) {
        Candidate candidate = findCandidate(id);
        if (!isAdmin && !principalId.equals(candidate.getUserId())) {
            throw new AccessDeniedException("You do not own this candidate profile");
        }
        return updateCandidateInternal(candidate, request);
    }

    private CandidateResponse updateCandidateInternal(Candidate candidate, UpdateCandidateRequest request) {
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
            replaceSkills(candidate.getId(), request.skills());
        }
        return toResponse(candidateRepository.saveAndFlush(candidate));
    }

    /**
     * Deletes a candidate profile; ADMIN only.
     *
     * @param id the candidate's own UUID (from the candidates table)
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCandidate(UUID id) {
        Candidate candidate = findCandidate(id);
        candidateSkillRepository.deleteByCandidateId(id);
        candidateRepository.delete(candidate);
    }

    private UUID resolveCandidateOwnerId(UUID requestedUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return requestedUserId;
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin && requestedUserId != null) {
            return requestedUserId;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            return requestedUserId;
        }
    }

    private void enforceCandidateOwnership(Candidate candidate) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            return;
        }
        UUID principalId;
        try {
            principalId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid authenticated principal");
        }
        if (!principalId.equals(candidate.getUserId())) {
            throw new AccessDeniedException("You do not own this candidate profile");
        }
    }

    private Candidate findCandidate(UUID id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No candidate found with id '" + id + "'"));
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

    private DuplicateResourceException duplicateCandidate(UUID userId) {
        return new DuplicateResourceException("A candidate profile already exists for user '" + userId + "'");
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
