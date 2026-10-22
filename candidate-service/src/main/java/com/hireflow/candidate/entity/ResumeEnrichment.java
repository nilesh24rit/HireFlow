package com.hireflow.candidate.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "resume_enrichments")
public class ResumeEnrichment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "processing_id", nullable = false)
    private UUID processingId;

    @Column(name = "proposed_data", nullable = false, columnDefinition = "TEXT")
    private String proposedData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResumeEnrichmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public UUID getProcessingId() {
        return processingId;
    }

    public void setProcessingId(UUID processingId) {
        this.processingId = processingId;
    }

    public String getProposedData() {
        return proposedData;
    }

    public void setProposedData(String proposedData) {
        this.proposedData = proposedData;
    }

    public ResumeEnrichmentStatus getStatus() {
        return status;
    }

    public void setStatus(ResumeEnrichmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public long getVersion() {
        return version;
    }
}
