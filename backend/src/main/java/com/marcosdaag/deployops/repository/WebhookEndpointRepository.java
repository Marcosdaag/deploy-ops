package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, Long> {

    List<WebhookEndpointEntity> findByUserOrderByIdDesc(UserEntity user);

    List<WebhookEndpointEntity> findByUserAndEnabledTrue(UserEntity user);

    List<WebhookEndpointEntity> findByEnabledTrue();

    boolean existsByTargetUrlAndUser(String targetUrl, UserEntity user);

    long countByUser(UserEntity user);
}
