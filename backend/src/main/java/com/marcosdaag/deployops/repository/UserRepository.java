package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<UserEntity> findByPublicSlug(String publicSlug);
    boolean existsByPublicSlug(String publicSlug);
}
