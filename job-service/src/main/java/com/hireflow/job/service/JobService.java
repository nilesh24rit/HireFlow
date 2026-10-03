package com.hireflow.job.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.job.dto.CreateJobRequest;
import com.hireflow.job.dto.JobResponse;
import com.hireflow.job.dto.UpdateJobRequest;
import com.hireflow.job.entity.Job;
import com.hireflow.job.entity.JobSkill;
import com.hireflow.job.entity.JobStatus;
import com.hireflow.job.exception.DuplicateJobSkillException;
import com.hireflow.job.exception.JobNotFoundException;
import com.hireflow.job.exception.JobValidationException;
import com.hireflow.job.repository.JobRepository;
import com.hireflow.job.repository.JobSkillRepository;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobSkillRepository jobSkillRepository;

    public JobService(JobRepository jobRepository, JobSkillRepository jobSkillRepository) {
        this.jobRepository = jobRepository;
        this.jobSkillRepository = jobSkillRepository;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        Job job = new Job();
        job.setRecruiterId(request.recruiterId());
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
        return toResponse(findJob(id));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> getJobsByRecruiterId(UUID recruiterId) {
        return jobRepository.findByRecruiterId(recruiterId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobResponse updateJob(UUID id, UpdateJobRequest request) {
        Job job = findJob(id);
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
            replaceSkills(id, request.skills());
        }
        return toResponse(jobRepository.saveAndFlush(job));
    }

    @Transactional
    public void deleteJob(UUID id) {
        jobRepository.delete(findJob(id));
    }

    private Job findJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("No job found with id '" + id + "'"));
    }

    private void assertValidJob(Job job) {
        if (job.getTitle() == null || job.getTitle().isBlank()) {
            throw new JobValidationException("title must not be blank");
        }
        if (job.getDescription() == null || job.getDescription().isBlank()) {
            throw new JobValidationException("description must not be blank");
        }
        if (job.getExperienceMin() != null && job.getExperienceMax() != null
                && job.getExperienceMin() > job.getExperienceMax()) {
            throw new JobValidationException("experienceMin must be less than or equal to experienceMax");
        }
        if (job.getSalaryMin() != null && job.getSalaryMax() != null
                && job.getSalaryMin() > job.getSalaryMax()) {
            throw new JobValidationException("salaryMin must be less than or equal to salaryMax");
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
        try {
            jobSkillRepository.saveAll(entities);
            jobSkillRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateJobSkillException("A duplicate skill was submitted for the job");
        }
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
