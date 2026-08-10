package com.argusiq.tracing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "services")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, unique = true)
    private String serviceName;

    @Column(name = "environment")
    private String environment;

    @Column(name = "version")
    private String version;

    @Column(name = "language")
    private String language;

    @Column(name = "first_seen", nullable = false)
    private LocalDateTime firstSeen;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    protected MonitoredService() {
    }

    public MonitoredService(String serviceName, String environment, String version, String language, LocalDateTime firstSeen, LocalDateTime lastSeen, String status) {
        this.serviceName = serviceName;
        this.environment = environment;
        this.version = version;
        this.language = language;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.status = status != null ? status : "ACTIVE";
    }

    public Long getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
