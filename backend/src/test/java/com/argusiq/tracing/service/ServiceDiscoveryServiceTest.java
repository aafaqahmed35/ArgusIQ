package com.argusiq.tracing.service;

import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDiscoveryServiceTest {

    private MonitoredServiceRepository repository;
    private ServiceDiscoveryService serviceDiscoveryService;

    @BeforeEach
    void setUp() {
        repository = mock(MonitoredServiceRepository.class);
        serviceDiscoveryService = new ServiceDiscoveryService(repository);
    }

    @Test
    void discoversNewServiceSuccessfully() {
        when(repository.findByServiceName("AtlasBankService")).thenReturn(Optional.empty());
        when(repository.save(any(MonitoredService.class))).thenAnswer(i -> i.getArgument(0));

        MonitoredService service = serviceDiscoveryService.discoverService(
                "AtlasBankService",
                "production",
                "2.1.0",
                "java"
        );

        assertNotNull(service);
        assertEquals("AtlasBankService", service.getServiceName());
        assertEquals("production", service.getEnvironment());
        assertEquals("2.1.0", service.getVersion());
        assertEquals("java", service.getLanguage());
        assertEquals("ACTIVE", service.getStatus());

        verify(repository).save(any(MonitoredService.class));
    }

    @Test
    void updatesExistingServiceOnRediscovery() {
        MonitoredService existing = new MonitoredService(
                "AtlasBankService",
                "staging",
                "1.0.0",
                "java",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusHours(2),
                "ACTIVE"
        );

        when(repository.findByServiceName("AtlasBankService")).thenReturn(Optional.of(existing));
        when(repository.save(any(MonitoredService.class))).thenAnswer(i -> i.getArgument(0));

        MonitoredService updated = serviceDiscoveryService.discoverService(
                "AtlasBankService",
                "production",
                "2.0.0",
                "java"
        );

        assertEquals("production", updated.getEnvironment());
        assertEquals("2.0.0", updated.getVersion());
        verify(repository).save(existing);
    }
}
