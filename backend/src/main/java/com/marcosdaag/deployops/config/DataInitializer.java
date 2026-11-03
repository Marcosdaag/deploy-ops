package com.marcosdaag.deployops.config;

import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final ServiceRepository serviceRepository;

    @Override
    public void run(String... args) {
        if (serviceRepository.count() == 0) {
            logger.info("Inicializando endpoints de demostracion protegidos en base de datos...");
            seedEndpoint("Google Search", "https://www.google.com");
            seedEndpoint("Cloudflare Edge DNS", "https://1.1.1.1");
            seedEndpoint("Wikimedia Global Portal", "https://www.wikipedia.org");
        }
    }

    private void seedEndpoint(String name, String url) {
        ServiceEntity entity = new ServiceEntity();
        entity.setName(name);
        entity.setUrl(url);
        entity.setStatus(ServiceStatus.PENDING);
        entity.setProtected(true);
        entity.setCreatedAt(LocalDateTime.now());
        serviceRepository.save(entity);
    }
}
