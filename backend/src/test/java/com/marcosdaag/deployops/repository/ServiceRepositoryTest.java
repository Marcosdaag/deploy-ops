package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.model.enums.UserRole;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ServiceRepositoryTest {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("findAll carga de forma ansiosa la relacion user evitando LazyInitializationException")
    void whenFindAll_shouldEagerlyLoadUserRelation() {
        UserEntity user = new UserEntity();
        user.setEmail("test-eager@deployops.io");
        user.setName("Eager Tester");
        user.setPassword("hashed_secret");
        user.setRole(UserRole.ROLE_USER);
        UserEntity savedUser = userRepository.save(user);

        ServiceEntity service = new ServiceEntity();
        service.setName("Eager Service");
        service.setUrl("https://eager.test.example.com");
        service.setStatus(ServiceStatus.HEALTHY);
        service.setConsecutiveFailures(0);
        service.setConsecutiveSuccesses(1);
        service.setCreatedAt(LocalDateTime.now());
        service.setUser(savedUser);
        serviceRepository.save(service);

        List<ServiceEntity> allServices = serviceRepository.findAll();
        assertFalse(allServices.isEmpty());

        ServiceEntity found = allServices.stream()
                .filter(s -> "Eager Service".equals(s.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(found);
        assertNotNull(found.getUser());
        assertTrue(Hibernate.isInitialized(found.getUser()), "User relation must be initialized");
        assertEquals("test-eager@deployops.io", found.getUser().getEmail());

        List<ServiceEntity> withUser = serviceRepository.findAllWithUser();
        assertFalse(withUser.isEmpty());
        ServiceEntity foundInWithUser = withUser.stream()
                .filter(s -> "Eager Service".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(foundInWithUser);
        assertNotNull(foundInWithUser.getUser());
        assertTrue(Hibernate.isInitialized(foundInWithUser.getUser()), "User relation must be initialized in findAllWithUser");
        assertEquals("test-eager@deployops.io", foundInWithUser.getUser().getEmail());
    }

    @Test
    @DisplayName("Detached service entities accessed in separate thread do not throw LazyInitializationException")
    void whenAccessedInAsyncWorkerThread_shouldNotThrowLazyInitializationException() throws ExecutionException, InterruptedException {
        UserEntity user = new UserEntity();
        user.setEmail("async-detached@deployops.io");
        user.setName("Async Detached User");
        user.setPassword("hashed_secret");
        user.setRole(UserRole.ROLE_USER);
        UserEntity savedUser = userRepository.save(user);

        ServiceEntity service = new ServiceEntity();
        service.setName("Async Detached Service");
        service.setUrl("https://async.test.example.com");
        service.setStatus(ServiceStatus.HEALTHY);
        service.setCreatedAt(LocalDateTime.now());
        service.setUser(savedUser);
        serviceRepository.save(service);

        List<ServiceEntity> endpoints = serviceRepository.findAll();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            ServiceEntity item = endpoints.stream()
                    .filter(s -> "Async Detached Service".equals(s.getName()))
                    .findFirst()
                    .orElseThrow();
            return item.getUser().getEmail();
        });

        String userEmail = future.get();
        assertEquals("async-detached@deployops.io", userEmail);
    }
}
