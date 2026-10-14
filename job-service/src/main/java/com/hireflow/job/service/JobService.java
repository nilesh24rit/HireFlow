package com.hireflow.job.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.job.dto.CreateJobRequest;
import com.hireflow.job.dto.JobResponse;
import com.hireflow.job.dto.UpdateJobRequest;
import com.hireflow.job.cache.JobCache;
import com.hireflow.job.entity.Job;
import com.hireflow.job.entity.JobSkill;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.exception.InvalidRequestException;
import com.hireflow.job.exception.ResourceNotFoundException;
import com.hireflow.job.repository.JobRepository;
import com.hireflow.job.repository.JobSkillRepository;

/**
 * Job requisition lifecycle management with HireFlow authorization rules
 * enforced next to the operations they protect ({@code @PreAuthorize} on
 * service methods, not scattered across controllers).
 *
 * <p>The permission matrix for {@code /api/jobs}:</p>
 *
 * <ul>
 *   <li><b>Create</b> — RECRUITER or ADMIN only. The authenticated user's id is
 *       derived from the security context and used as the recruiter owner; for RECRUITER
 *       callers, any {@code recruiterId} supplied in the request body is overridden by the
 *       principal's id, preventing recruiter impersonation.</li>
 *   <li><b>Read by job id</b> — any authenticated caller (CANDIDATE, RECRUITER, ADMIN).</li>
 *   <li><b>Read by recruiter id</b> — any authenticated caller (browse jobs).</li>
 *   <li><b>Update</b> — RECRUITER who owns the job (recruiterId on the persisted
 *       job must equal the token's user id), or ADMIN.</li>
 *   <li><b>Delete</b> — RECRUITER who owns the job, or ADMIN.</li>
 * </ul>
 *
 * <p><b>Ownership enforcement.</b> Update and delete check that the persisted job's
 * {@code recruiterId} equals the authenticated principal's id before applying any change.
 * A RECRUITER cannot modify or delete another recruiter's job even if they know its id.
 * Ownership is checked against the database, never against a client-supplied field.</p>
 */
@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobSkillRepository jobSkillRepository;
    private final JobCache jobCache;

    public JobService(JobRepository jobRepository, JobSkillRepository jobSkillRepository) {
        this(jobRepository, jobSkillRepository, JobCache.disabled());
    }

    public JobService(JobRepository jobRepository, JobSkillRepository jobSkillRepository,
            JobCache jobCache) {
        this.jobRepository = jobRepository;
        this.jobSkillRepository = jobSkillRepository;
        this.jobCache = jobCache;
    }

    /**
     * Creates a job requisition.
     *
     * <p>For RECRUITER callers, the {@code recruiterId} is derived from the authenticated
     * principal to prevent impersonation. ADMIN callers may explicitly specify the target
     * {@code recruiterId}.</p>
     *
     * @param request validated job payload
     * @return the created job
     */
    @Transactional
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public JobResponse createJob(CreateJobRequest request) {
        UUID ownerRecruiterId = resolveRecruiterId(request.recruiterId());
        return createJobInternal(request, ownerRecruiterId);
    }

    /**
     * Creates a job requisition with an explicitly specified recruiter owner id.
     *
     * @param request     validated job payload
     * @param recruiterId owning recruiter's user id
     * @return the created job
     */
    @Transactional
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public JobResponse createJob(CreateJobRequest request, UUID recruiterId) {
        return createJobInternal(request, recruiterId);
    }

    private JobResponse createJobInternal(CreateJobRequest request, UUID recruiterId) {
        Job job = new Job();
        job.setRecruiterId(recruiterId);
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setLocation(request.location());
        job.setEmploymentType(request.employmentType());
        job.setExperienceMin(request.experienceMin());
        job.setExperienceMax(request.experienceMax());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setStatus(request.status() != null ? request.status() : JobStatus.DRAFT);
        assertValidJob(job);

        Job saved = jobRepository.saveAndFlush(job);
        saveSkills(saved.getId(), request.skills());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public JobResponse getJobById(UUID id) {
        JobResponse cached = jobCache.getById(id);
        if (cached != null) {
            return cached;
        }
        JobResponse response = toResponse(findJob(id));
        jobCache.cacheAfterCommit(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<JobResponse> getJobsByRecruiterId(UUID recruiterId) {
        return jobRepository.findByRecruiterId(recruiterId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Updates a job requisition.
     *
     * <p>RECRUITER may only update jobs they own. ADMIN may update any job.</p>
     *
     * @param id      UUID of the job to update
     * @param request partial update payload
     * @return the updated job
     */
    @Transactional
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public JobResponse updateJob(UUID id, UpdateJobRequest request) {
        Job job = findJob(id);
        enforceJobOwnership(job);
        return updateJobInternal(job, request);
    }

    /**
     * Updates a job requisition with explicit caller identity.
     *
     * @param id          UUID of the job
     * @param request     partial update payload
     * @param principalId authenticated caller id
     * @param isAdmin     true if caller is ADMIN
     * @return the updated job
     */
    @Transactional
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public JobResponse updateJob(UUID id, UpdateJobRequest request, UUID principalId, boolean isAdmin) {
        Job job = findJob(id);
        if (!isAdmin && !principalId.equals(job.getRecruiterId())) {
            throw new AccessDeniedException("You do not own this job posting");
        }
        return updateJobInternal(job, request);
    }

    private JobResponse updateJobInternal(Job job, UpdateJobRequest request) {
        if (request.title() != null) {
            job.setTitle(request.title());
        }
        if (request.description() != null) {
            job.setDescription(request.description());
        }
        if (request.location() != null) {
            job.setLocation(request.location());
        }
        if (request.employmentType() != null) {
            job.setEmploymentType(request.employmentType());
        }
        if (request.experienceMin() != null) {
            job.setExperienceMin(request.experienceMin());
        }
        if (request.experienceMax() != null) {
            job.setExperienceMax(request.experienceMax());
        }
        if (request.salaryMin() != null) {
            job.setSalaryMin(request.salaryMin());
        }
        if (request.salaryMax() != null) {
            job.setSalaryMax(request.salaryMax());
        }
        if (request.status() != null) {
            job.setStatus(request.status());
        }
        assertValidJob(job);
        if (request.skills() != null) {
            replaceSkills(job.getId(), request.skills());
        }
        return toResponse(jobRepository.saveAndFlush(job));
    }

    /**
     * Deletes a job requisition.
     *
     * <p>RECRUITER may only delete jobs they own. ADMIN may delete any job.</p>
     *
     * @param id UUID of the job to delete
     */
    @Transactional
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public void deleteJob(UUID id) {
        Job job = findJob(id);
        enforceJobOwnership(job);
        jobSkillRepository.deleteByJobId(id);
        jobRepository.delete(job);
    }

    /**
     * Deletes a job requisition with explicit caller identity.
     *
     * @param id          UUID of the job
     * @param principalId authenticated caller id
     * @param isAdmin     true if caller is ADMIN
     */
    @Transactional
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public void deleteJob(UUID id, UUID principalId, boolean isAdmin) {
        Job job = findJob(id);
        if (!isAdmin && !principalId.equals(job.getRecruiterId())) {
            throw new AccessDeniedException("You do not own this job posting");
        }
        jobSkillRepository.deleteByJobId(id);
        jobRepository.delete(job);
    }

    private UUID resolveRecruiterId(UUID requestedRecruiterId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return requestedRecruiterId;
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin && requestedRecruiterId != null) {
            return requestedRecruiterId;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            return requestedRecruiterId;
        }
    }

    private void enforceJobOwnership(Job job) {
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
        if (!principalId.equals(job.getRecruiterId())) {
            throw new AccessDeniedException("You do not own this job posting");
        }
    }

    private Job findJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No job found with id '" + id + "'"));
    }

    private void assertValidJob(Job job) {
        if (job.getTitle() == null || job.getTitle().isBlank()) {
            throw new InvalidRequestException("title must not be blank");
        }
        if (job.getDescription() == null || job.getDescription().isBlank()) {
            throw new InvalidRequestException("description must not be blank");
        }
        if (job.getExperienceMin() != null && job.getExperienceMax() != null
                && job.getExperienceMin() > job.getExperienceMax()) {
            throw new InvalidRequestException("experienceMin must be less than or equal to experienceMax");
        }
        if (job.getSalaryMin() != null && job.getSalaryMax() != null
                && job.getSalaryMin() > job.getSalaryMax()) {
            throw new InvalidRequestException("salaryMin must be less than or equal to salaryMax");
        }
    }

    private void saveSkills(UUID jobId, List<String> skills) {
        List<String> normalized = JobSkillNormalizer.normalize(skills);
        if (normalized.isEmpty()) {
            return;
        }
        List<JobSkill> entities = normalized.stream()
                .map(skill -> newSkill(jobId, skill))
                .toList();
        jobSkillRepository.saveAll(entities);
        jobSkillRepository.flush();
    }

    private void replaceSkills(UUID jobId, List<String> skills) {
        jobSkillRepository.deleteByJobId(jobId);
        jobSkillRepository.flush();
        saveSkills(jobId, skills);
    }

    private JobSkill newSkill(UUID jobId, String skill) {
        JobSkill jobSkill = new JobSkill();
        jobSkill.setJobId(jobId);
        jobSkill.setSkill(skill);
        return jobSkill;
    }

    private JobResponse toResponse(Job job) {
        List<String> skills = jobSkillRepository.findByJobId(job.getId()).stream()
                .map(JobSkill::getSkill)
                .toList();
        return new JobResponse(
                job.getId(),
                job.getRecruiterId(),
                job.getTitle(),
                job.getDescription(),
                job.getLocation(),
                job.getEmploymentType(),
                job.getExperienceMin(),
                job.getExperienceMax(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getStatus(),
                skills,
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
