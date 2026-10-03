package com.hireflow.job.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hireflow.job.entity.Job;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByRecruiterId(UUID recruiterId);
}
