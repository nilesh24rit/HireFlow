package com.hireflow.candidate.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hireflow.candidate.entity.CandidateSkill;

public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, UUID> {

    @Query("select s from CandidateSkill s where s.candidateId = :candidateId order by lower(s.skill)")
    List<CandidateSkill> findByCandidateId(@Param("candidateId") UUID candidateId);

    void deleteByCandidateId(UUID candidateId);
}
