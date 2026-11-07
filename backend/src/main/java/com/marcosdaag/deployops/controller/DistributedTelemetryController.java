package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.DistributedOverviewDTO;
import com.marcosdaag.deployops.model.enums.OutboxStatus;
import com.marcosdaag.deployops.model.enums.WebhookDeliveryStatus;
import com.marcosdaag.deployops.repository.OutboxEventRepository;
import com.marcosdaag.deployops.repository.WebhookDeliveryFailureRepository;
import com.marcosdaag.deployops.service.StatusPageCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/distributed")
@RequiredArgsConstructor
public class DistributedTelemetryController {

    private final OutboxEventRepository outboxEventRepository;
    private final WebhookDeliveryFailureRepository failureRepository;
    private final StatusPageCacheService statusPageCacheService;

    @Value("${app.rabbitmq.enabled:false}")
    private boolean rabbitmqEnabled;

    @GetMapping("/overview")
    public ResponseEntity<DistributedOverviewDTO> getOverview() {
        long pending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long processing = outboxEventRepository.countByStatus(OutboxStatus.PROCESSING);
        long processed = outboxEventRepository.countByStatus(OutboxStatus.PROCESSED);
        long failed = outboxEventRepository.countByStatus(OutboxStatus.FAILED);
        long dlq = failureRepository.countByStatus(WebhookDeliveryStatus.DEAD_LETTER);

        DistributedOverviewDTO dto = new DistributedOverviewDTO(
                pending,
                processing,
                processed,
                failed,
                dlq,
                statusPageCacheService.getHits(),
                statusPageCacheService.getMisses(),
                statusPageCacheService.getEvictions(),
                statusPageCacheService.isRedisCacheActive(),
                rabbitmqEnabled
        );

        return ResponseEntity.ok(dto);
    }
}
