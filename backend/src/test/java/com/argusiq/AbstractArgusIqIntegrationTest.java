package com.argusiq;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

public abstract class AbstractArgusIqIntegrationTest {

    protected static final String INVESTIGATION_USERNAME = "integration-investigator";
    protected static final String INGESTION_USERNAME = "integration-collector";
    protected static final String INVESTIGATION_PASSWORD = UUID.randomUUID().toString();
    protected static final String INGESTION_PASSWORD = UUID.randomUUID().toString();
    protected static final String ALLOWED_ORIGIN = "https://frontend.argusiq.test";
    protected static final String TEST_DATABASE_URL = "jdbc:h2:mem:argusiq-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    @DynamicPropertySource
    static void externalConfiguration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_DATABASE_URL);
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("argusiq.security.investigation-username", () -> INVESTIGATION_USERNAME);
        registry.add("argusiq.security.investigation-password", () -> INVESTIGATION_PASSWORD);
        registry.add("argusiq.security.ingestion-username", () -> INGESTION_USERNAME);
        registry.add("argusiq.security.ingestion-password", () -> INGESTION_PASSWORD);
        registry.add("argusiq.web.allowed-origins", () -> ALLOWED_ORIGIN);
    }
}
