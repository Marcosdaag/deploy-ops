package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    Optional<IncidentEntity> findByServiceAndStatus(ServiceEntity service, IncidentStatus status);

    List<IncidentEntity> findByService(ServiceEntity service);

    List<IncidentEntity> findByServiceAndStartedAtAfter(ServiceEntity service, LocalDateTime startedAt);

    @Query("SELECT i FROM IncidentEntity i WHERE i.service = :service AND (i.resolvedAt IS NULL OR i.resolvedAt > :windowStart)")
    List<IncidentEntity> findOverlappingIncidents(@Param("service") ServiceEntity service, @Param("windowStart") LocalDateTime windowStart);

    // Consulta en lote para evitar N+1 queries al calcular metricas
    @Query("SELECT i FROM IncidentEntity i WHERE i.service IN :services AND (i.resolvedAt IS NULL OR i.resolvedAt > :windowStart)")
    List<IncidentEntity> findOverlappingIncidentsForServices(@Param("services") List<ServiceEntity> services, @Param("windowStart") LocalDateTime windowStart);

    void deleteByServiceId(Long serviceId);
}
