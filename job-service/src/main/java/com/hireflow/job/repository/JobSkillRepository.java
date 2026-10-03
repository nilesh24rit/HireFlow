package com.hireflow.job.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hireflow.job.entity.JobSkill;

public interface JobSkillRepository extends JpaRepository<JobSkill, UUID> {

    @Query("select s from JobSkill s where s.jobId = :jobId order by lower(s.skill)")
    List<JobSkill> findByJobId(@Param("jobId") UUID jobId);

    void deleteByJobId(UUID jobId);
}
