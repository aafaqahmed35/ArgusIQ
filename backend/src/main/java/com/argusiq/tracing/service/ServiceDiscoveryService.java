package com.argusiq.tracing.service;

import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ServiceDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryService.class);

    private final MonitoredServiceRepository serviceRepository;

    public ServiceDiscoveryService(MonitoredServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Transactional
    public MonitoredService discoverService(String serviceName, String environment, String version, String language) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return discoverService(serviceName, environment, version, language, now, now);
    }

    @Transactional
    public MonitoredService discoverService(
            String serviceName,
            String environment,
            String version,
            String language,
            LocalDateTime observedFirstSeen,
            LocalDateTime observedLastSeen
    ) {
        String finalServiceName = (serviceName != null && !serviceName.trim().isEmpty()) ? serviceName.trim() : "unknown-service";
        String finalEnv = observedValue(environment);
        String finalVersion = observedValue(version);
        String finalLang = observedValue(language);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime firstSeen = observedFirstSeen != null ? observedFirstSeen : now;
        LocalDateTime lastSeen = observedLastSeen != null ? observedLastSeen : firstSeen;
        if (lastSeen.isBefore(firstSeen)) {
            lastSeen = firstSeen;
        }
        LocalDateTime finalFirstSeen = firstSeen;
        LocalDateTime finalLastSeen = lastSeen;

        return serviceRepository.findByServiceNameForUpdate(finalServiceName)
                .map(existing -> {
                    LocalDateTime previousLastSeen = existing.getLastSeen();
                    boolean newerObservation = previousLastSeen == null || finalLastSeen.isAfter(previousLastSeen);
                    if (existing.getFirstSeen() == null || finalFirstSeen.isBefore(existing.getFirstSeen())) {
                        existing.setFirstSeen(finalFirstSeen);
                    }
                    if (previousLastSeen == null || finalLastSeen.isAfter(previousLastSeen)) {
                        existing.setLastSeen(finalLastSeen);
                    }
                    if (finalEnv != null && (!hasObservedValue(existing.getEnvironment()) || newerObservation)) {
                        existing.setEnvironment(finalEnv);
                    }
                    if (finalVersion != null && (!hasObservedValue(existing.getVersion()) || newerObservation)) {
                        existing.setVersion(finalVersion);
                    }
                    if (finalLang != null && (!hasObservedValue(existing.getLanguage()) || newerObservation)) {
                        existing.setLanguage(finalLang);
                    }
                    existing.setStatus("ACTIVE");
                    logger.info(
                            "Updated existing service: {} [env={}, ver={}, lang={}]",
                            finalServiceName,
                            existing.getEnvironment(),
                            existing.getVersion(),
                            existing.getLanguage()
                    );
                    return serviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    MonitoredService newService = new MonitoredService(
                            finalServiceName,
                            finalEnv,
                            finalVersion,
                            finalLang,
                            finalFirstSeen,
                            finalLastSeen,
                            "ACTIVE"
                    );
                    logger.info("Discovered new service: {} [env={}, ver={}, lang={}]", finalServiceName, finalEnv, finalVersion, finalLang);
                    return serviceRepository.save(newService);
                });
    }

    private String observedValue(String value) {
        return hasObservedValue(value) ? value.trim() : null;
    }

    private boolean hasObservedValue(String value) {
        return value != null && !value.isBlank();
    }
}
