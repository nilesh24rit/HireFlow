package com.hireflow.application.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hireflow.application.entity.ApplicationOutbox;

public interface ApplicationOutboxRepository extends JpaRepository<ApplicationOutbox, UUID> {

    @Query(value = """
            select * from application_outbox
            where status = 'PENDING'
              and next_attempt_at <= :now
              and not exists (
                  select 1 from application_outbox earlier
                  where earlier.aggregate_id = application_outbox.aggregate_id
                    and (earlier.created_at < application_outbox.created_at
                         or (earlier.created_at = application_outbox.created_at
                             and earlier.id < application_outbox.id))
                    and earlier.status <> 'PUBLISHED'
              )
            order by created_at, id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<ApplicationOutbox> claimEligible(@Param("now") Instant now,
            @Param("batchSize") int batchSize);

    @Query("""
            select o from ApplicationOutbox o
            where o.status = com.hireflow.application.entity.ApplicationOutbox$DeliveryStatus.IN_PROGRESS
              and o.nextAttemptAt < :cutoff
            """)
    List<ApplicationOutbox> findStaleClaims(@Param("cutoff") Instant cutoff);
}
