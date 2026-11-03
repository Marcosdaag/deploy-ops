package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.PingLogEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PingLogRepository extends JpaRepository<PingLogEntity, Long> {

    List<PingLogEntity> findByServiceAndTimestampAfterOrderByTimestampAsc(ServiceEntity service, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM PingLogEntity p WHERE p.timestamp < :threshold")
    void deleteByTimestampBefore(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query("DELETE FROM PingLogEntity p WHERE p.service.id = :serviceId")
    void deleteByServiceId(@Param("serviceId") Long serviceId);
}
