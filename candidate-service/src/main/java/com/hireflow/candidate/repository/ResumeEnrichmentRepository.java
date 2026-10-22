package com.hireflow.candidate.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hireflow.candidate.entity.ResumeEnrichment;

public interface ResumeEnrichmentRepository extends JpaRepository<ResumeEnrichment, UUID> {

    Optional<ResumeEnrichment> findByIdAndCandidateId(UUID id, UUID candidateId);
}
