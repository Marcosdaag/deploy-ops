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

    // Busca el incidente de un servicio especifico que tenga un estado especifico (ej: ONGOING)
    Optional<IncidentEntity> findByServiceAndStatus(ServiceEntity service, IncidentStatus status);

    // Devuelve la lista completa de incidentes de un servicio
    List<IncidentEntity> findByService(ServiceEntity service);

    List<IncidentEntity> findByServiceAndStartedAtAfter(ServiceEntity service, LocalDateTime startedAt);

    // Trae los incidentes que estuvieron activos dentro de la ventana de tiempo para un solo servicio
    @Query("SELECT i FROM IncidentEntity i WHERE i.service = :service AND (i.resolvedAt IS NULL OR i.resolvedAt > :windowStart)")
    List<IncidentEntity> findOverlappingIncidents(@Param("service") ServiceEntity service, @Param("windowStart") LocalDateTime windowStart);

    // Consulta en lote para evitar el problema de N+1 queries al listar el dashboard
    @Query("SELECT i FROM IncidentEntity i WHERE i.service IN :services AND (i.resolvedAt IS NULL OR i.resolvedAt > :windowStart)")
    List<IncidentEntity> findOverlappingIncidentsForServices(@Param("services") List<ServiceEntity> services, @Param("windowStart") LocalDateTime windowStart);

    // Borra los incidentes asociados a un servicio antes de borrar el servicio
    void deleteByServiceId(Long serviceId);
}
