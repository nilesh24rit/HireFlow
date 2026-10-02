package com.hireflow.candidate.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hireflow.candidate.entity.Candidate;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
