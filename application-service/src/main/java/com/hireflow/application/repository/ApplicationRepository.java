package com.hireflow.application.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hireflow.application.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByCandidateId(UUID candidateId);

    List<Application> findByJobId(UUID jobId);

    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);
}
