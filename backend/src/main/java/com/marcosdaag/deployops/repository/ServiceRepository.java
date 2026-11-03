package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    @Override
    @EntityGraph(attributePaths = {"user"})
    Optional<ServiceEntity> findById(Long id);

    Optional<ServiceEntity> findByName(String name);

    boolean existsByUrl(String url);

    List<ServiceEntity> findByUserOrderByIdDesc(UserEntity user);

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user WHERE s.user IS NULL OR s.user = :user ORDER BY s.id DESC")
    List<ServiceEntity> findShowcaseAndUserServices(@Param("user") UserEntity user);

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user WHERE s.user IS NULL ORDER BY s.id DESC")
    List<ServiceEntity> findByUserIsNullOrderByIdDesc();

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user WHERE s.user = :user AND s.isPublicStatus = true ORDER BY s.id DESC")
    List<ServiceEntity> findByUserAndIsPublicStatusTrueOrderByIdDesc(@Param("user") UserEntity user);

    List<ServiceEntity> findByIsPublicStatusTrueOrderByIdDesc();
}
