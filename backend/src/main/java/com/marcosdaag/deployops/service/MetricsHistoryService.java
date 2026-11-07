package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingPointDTO;
import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.dto.ServiceMetricsHistoryDTO;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.PingLogEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.repository.PingLogRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricsHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsHistoryService.class);

    private final PingLogRepository pingLogRepository;
    private final ServiceRepository serviceRepository;

    @Transactional
    public void recordPing(ServiceEntity service, PingResult pingResult) {
        if (service == null || service.getId() == null) {
            return;
        }

        PingLogEntity log = new PingLogEntity();
        log.setService(service);
        log.setResponseTimeMs(pingResult.responseTimeMs());
        log.setStatusCode(pingResult.statusCode());
        log.setAlive(pingResult.isAlive());
        log.setTimestamp(LocalDateTime.now());

        pingLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public ServiceMetricsHistoryDTO getMetricsHistory(Long serviceId, String range, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + serviceId));

        if (service.getUser() != null && !service.isPublicStatus()) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para ver las metricas detalladas de este servicio.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = switch (range != null ? range.toLowerCase() : "24h") {
            case "6h" -> now.minusHours(6);
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default -> now.minusHours(24);
        };

        List<PingLogEntity> logs = pingLogRepository.findByServiceAndTimestampAfterOrderByTimestampAsc(service, threshold);

        int totalPings = logs.size();
        long aliveCount = logs.stream().filter(PingLogEntity::isAlive).count();
        double uptimePercentage = totalPings > 0 ? (aliveCount * 100.0) / totalPings : 100.0;

        List<Long> aliveLatencies = logs.stream()
                .filter(PingLogEntity::isAlive)
                .map(PingLogEntity::getResponseTimeMs)
                .sorted()
                .toList();

        long min = aliveLatencies.isEmpty() ? 0 : aliveLatencies.getFirst();
        long max = aliveLatencies.isEmpty() ? 0 : aliveLatencies.getLast();
        double avg = aliveLatencies.isEmpty() ? 0.0 : aliveLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95 = 0;
        if (!aliveLatencies.isEmpty()) {
            int p95Index = (int) Math.ceil(0.95 * aliveLatencies.size()) - 1;
            p95 = aliveLatencies.get(Math.max(0, Math.min(p95Index, aliveLatencies.size() - 1)));
        }

        List<PingPointDTO> points = logs.stream()
                .map(l -> new PingPointDTO(
                        l.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        l.getResponseTimeMs(),
                        l.getStatusCode(),
                        l.isAlive()
                ))
                .toList();

        return ServiceMetricsHistoryDTO.builder()
                .serviceId(service.getId())
                .serviceName(service.getName())
                .range(range != null ? range : "24h")
                .minLatencyMs(min)
                .maxLatencyMs(max)
                .avgLatencyMs(Math.round(avg * 100.0) / 100.0)
                .p95LatencyMs(p95)
                .uptimePercentage(Math.round(uptimePercentage * 100.0) / 100.0)
                .totalPings(totalPings)
                .points(points)
                .build();
    }

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "purgeOldPingLogsLock", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void purgeOldLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        pingLogRepository.deleteByTimestampBefore(threshold);
        logger.info("Purga de registros de ping anteriores a 30 dias ejecutada exitosamente.");
    }
}
