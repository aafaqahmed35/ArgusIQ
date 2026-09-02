package com.argusiq;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    protected static final String INVESTIGATION_PASSWORD = UUID.randomUUID().toString();
    protected static final String INGESTION_PASSWORD = UUID.randomUUID().toString();

    @Container
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void postgresConfiguration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("argusiq.security.investigation-username", () -> "postgres-investigator");
        registry.add("argusiq.security.investigation-password", () -> INVESTIGATION_PASSWORD);
        registry.add("argusiq.security.ingestion-username", () -> "postgres-collector");
        registry.add("argusiq.security.ingestion-password", () -> INGESTION_PASSWORD);
        registry.add("argusiq.web.allowed-origins", () -> "https://postgres.argusiq.test");
    }
}
