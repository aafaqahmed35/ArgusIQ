package com.argusiq.tracing.service;

import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        LocalDateTime firstSeen = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime lastSeen = firstSeen.plusSeconds(2);
        when(repository.findByServiceNameForUpdate("AtlasBankService")).thenReturn(Optional.empty());
        when(repository.save(any(MonitoredService.class))).thenAnswer(i -> i.getArgument(0));

        MonitoredService service = serviceDiscoveryService.discoverService(
                "AtlasBankService",
                "production",
                "2.1.0",
                "java",
                firstSeen,
                lastSeen
        );

        assertNotNull(service);
        assertEquals("AtlasBankService", service.getServiceName());
        assertEquals("production", service.getEnvironment());
        assertEquals("2.1.0", service.getVersion());
        assertEquals("java", service.getLanguage());
        assertEquals(firstSeen, service.getFirstSeen());
        assertEquals(lastSeen, service.getLastSeen());
        assertEquals("ACTIVE", service.getStatus());

        verify(repository).save(any(MonitoredService.class));
    }

    @Test
    void updatesExistingServiceOnRediscovery() {
        LocalDateTime firstSeen = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime previousLastSeen = firstSeen.plusHours(1);
        MonitoredService existing = new MonitoredService(
                "AtlasBankService",
                "staging",
                "1.0.0",
                "java",
                firstSeen,
                previousLastSeen,
                "ACTIVE"
        );

        when(repository.findByServiceNameForUpdate("AtlasBankService")).thenReturn(Optional.of(existing));
        when(repository.save(any(MonitoredService.class))).thenAnswer(i -> i.getArgument(0));

        MonitoredService updated = serviceDiscoveryService.discoverService(
                "AtlasBankService",
                "production",
                "2.0.0",
                "java",
                firstSeen.plusMinutes(30),
                previousLastSeen.plusHours(1)
        );

        assertEquals("production", updated.getEnvironment());
        assertEquals("2.0.0", updated.getVersion());
        assertEquals(firstSeen, updated.getFirstSeen());
        assertEquals(previousLastSeen.plusHours(1), updated.getLastSeen());
        verify(repository).save(existing);
    }

    @Test
    void missingAndOlderMetadataCannotEraseOrRegressKnownServiceState() {
        LocalDateTime firstSeen = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime lastSeen = firstSeen.plusHours(2);
        MonitoredService existing = new MonitoredService(
                "AtlasBankService",
                "production",
                "2.0.0",
                "java",
                firstSeen,
                lastSeen,
                "ACTIVE"
        );
        when(repository.findByServiceNameForUpdate("AtlasBankService")).thenReturn(Optional.of(existing));
        when(repository.save(any(MonitoredService.class))).thenAnswer(i -> i.getArgument(0));

        MonitoredService updated = serviceDiscoveryService.discoverService(
                "AtlasBankService",
                "staging",
                null,
                "go",
                firstSeen.minusHours(1),
                lastSeen.minusMinutes(1)
        );

        assertEquals(firstSeen.minusHours(1), updated.getFirstSeen());
        assertEquals(lastSeen, updated.getLastSeen());
        assertEquals("production", updated.getEnvironment());
        assertEquals("2.0.0", updated.getVersion());
        assertEquals("java", updated.getLanguage());
    }

    @Test
    void missingMetadataRemainsNullForANewService() {
        LocalDateTime observed = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(repository.findByServiceNameForUpdate("metadata-free-service")).thenReturn(Optional.empty());
        when(repository.save(any(MonitoredService.class))).thenAnswer(i -> i.getArgument(0));

        MonitoredService service = serviceDiscoveryService.discoverService(
                "metadata-free-service",
                " ",
                null,
                "",
                observed,
                observed
        );

        assertNull(service.getEnvironment());
        assertNull(service.getVersion());
        assertNull(service.getLanguage());
    }
}
