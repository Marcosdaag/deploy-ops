package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {
    // Busca el incidente de un servicio específico que tenga un estado específico (ej: "UP")
    IncidentEntity findByServiceAndStatus(ServiceEntity service, String status);
}