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

    @Override
    @EntityGraph(attributePaths = {"user"})
    List<ServiceEntity> findAll();

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user ORDER BY s.id DESC")
    List<ServiceEntity> findAllWithUser();

    Optional<ServiceEntity> findByName(String name);

    boolean existsByUrl(String url);

    boolean existsByUrlAndUser(String url, UserEntity user);

    boolean existsByUrlAndUserIsNull(String url);

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user WHERE s.user = :user ORDER BY s.id DESC")
    List<ServiceEntity> findByUserOrderByIdDesc(@Param("user") UserEntity user);

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user WHERE s.user IS NULL OR s.user = :user ORDER BY s.id DESC")
    List<ServiceEntity> findShowcaseAndUserServices(@Param("user") UserEntity user);

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user WHERE s.user IS NULL ORDER BY s.id DESC")
    List<ServiceEntity> findByUserIsNullOrderByIdDesc();

    @Query("SELECT s FROM ServiceEntity s LEFT JOIN FETCH s.user WHERE s.user = :user AND s.isPublicStatus = true ORDER BY s.id DESC")
    List<ServiceEntity> findByUserAndIsPublicStatusTrueOrderByIdDesc(@Param("user") UserEntity user);

    List<ServiceEntity> findByIsPublicStatusTrueOrderByIdDesc();
}
