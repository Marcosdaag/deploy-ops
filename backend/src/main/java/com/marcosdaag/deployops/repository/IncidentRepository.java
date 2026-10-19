package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {
    // Busca el incidente de un servicio específico que tenga un estado específico (ej: ONGOING)
    Optional<IncidentEntity> findByServiceAndStatus(ServiceEntity service, IncidentStatus status);

    // Devuelve la lista completa de incidentes de un servicio
    List<IncidentEntity> findByService(ServiceEntity service);

    // Devuelve los incidentes iniciados a partir de una fecha determinada (optimización para métricas)
    List<IncidentEntity> findByServiceAndStartedAtAfter(ServiceEntity service, LocalDateTime startedAt);
}