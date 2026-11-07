package com.marcosdaag.deployops.repository;

import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.WebhookDeliveryFailureEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.WebhookDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryFailureRepository extends JpaRepository<WebhookDeliveryFailureEntity, Long> {

    List<WebhookDeliveryFailureEntity> findByUserOrderByFailedAtDesc(UserEntity user);

    List<WebhookDeliveryFailureEntity> findByStatusOrderByFailedAtDesc(WebhookDeliveryStatus status);

    boolean existsByEventIdAndWebhook(String eventId, WebhookEndpointEntity webhook);

    Optional<WebhookDeliveryFailureEntity> findByEventIdAndWebhook(String eventId, WebhookEndpointEntity webhook);

    long countByUserAndStatus(UserEntity user, WebhookDeliveryStatus status);

    long countByStatus(WebhookDeliveryStatus status);
}
