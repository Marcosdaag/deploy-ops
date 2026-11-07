package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PROCESSING' AND o.lockedAt < :threshold ORDER BY o.lockedAt ASC")
    List<OutboxEventEntity> findStaleProcessingEvents(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Query("DELETE FROM OutboxEventEntity o WHERE o.status = :status AND o.processedAt < :before")
    int deleteOldProcessedEvents(@Param("status") OutboxStatus status, @Param("before") LocalDateTime before);
}
