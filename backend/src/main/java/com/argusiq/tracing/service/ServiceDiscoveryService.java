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
        String finalServiceName = (serviceName != null && !serviceName.trim().isEmpty()) ? serviceName.trim() : "unknown-service";
        String finalEnv = (environment != null && !environment.trim().isEmpty()) ? environment.trim() : "production";
        String finalVersion = (version != null && !version.trim().isEmpty()) ? version.trim() : "1.0.0";
        String finalLang = (language != null && !language.trim().isEmpty()) ? language.trim() : "unknown";

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        return serviceRepository.findByServiceName(finalServiceName)
                .map(existing -> {
                    existing.setLastSeen(now);
                    if (environment != null && !environment.isEmpty()) {
                        existing.setEnvironment(finalEnv);
                    }
                    if (version != null && !version.isEmpty()) {
                        existing.setVersion(finalVersion);
                    }
                    if (language != null && !language.isEmpty()) {
                        existing.setLanguage(finalLang);
                    }
                    existing.setStatus("ACTIVE");
                    logger.info("Updated existing service: {} [env={}, ver={}, lang={}]", finalServiceName, finalEnv, finalVersion, finalLang);
                    return serviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    MonitoredService newService = new MonitoredService(
                            finalServiceName,
                            finalEnv,
                            finalVersion,
                            finalLang,
                            now,
                            now,
                            "ACTIVE"
                    );
                    logger.info("Discovered new service: {} [env={}, ver={}, lang={}]", finalServiceName, finalEnv, finalVersion, finalLang);
                    return serviceRepository.save(newService);
                });
    }
}
