package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    // JPA no tiene por defecto un findbyname, si tiene un buscador por ID. Por esa razon creamos uno
    ServiceEntity findByName(String name);
}
