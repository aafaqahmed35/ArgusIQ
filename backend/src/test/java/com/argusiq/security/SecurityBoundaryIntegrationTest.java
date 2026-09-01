package com.argusiq.security;

import com.argusiq.AbstractArgusIqIntegrationTest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityBoundaryIntegrationTest extends AbstractArgusIqIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Value("${local.server.port}")
    private int serverPort;

    @Test
    void applicationUsesExternallySuppliedDatabaseConfiguration() {
        assertEquals(TEST_DATABASE_URL, environment.getProperty("spring.datasource.url"));
        assertEquals("sa", environment.getProperty("spring.datasource.username"));
    }

    @Test
    void authenticatedHttpResponseIssuesBrowserCsrfCookie() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/v1/traces"))
                .header("Authorization", investigationAuthorization())
                .GET()
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().allValues("Set-Cookie").stream()
                .anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=")));
    }

    @Test
    void healthIsPublicButInvestigationApisRequireInvestigatorAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/traces"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/traces")
                        .with(httpBasic(INGESTION_USERNAME, INGESTION_PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/traces")
                        .with(httpBasic(INVESTIGATION_USERNAME, INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void otlpIngestionRequiresTheCollectorPrincipal() throws Exception {
        byte[] payload = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.getDefaultInstance())
                .build()
                .toByteArray();

        mockMvc.perform(post("/v1/traces")
                        .contentType("application/x-protobuf")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/traces")
                        .with(httpBasic(INVESTIGATION_USERNAME, INVESTIGATION_PASSWORD))
                        .contentType("application/x-protobuf")
                        .content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/traces")
                        .with(httpBasic(INGESTION_USERNAME, INGESTION_PASSWORD))
                        .contentType("application/x-protobuf")
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void investigationMutationsRequireCsrfProtection() throws Exception {
        String request = "{\"title\":\"Security boundary test\"}";

        mockMvc.perform(post("/api/v1/alerts")
                        .with(httpBasic(INVESTIGATION_USERNAME, INVESTIGATION_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        HttpResponse<Void> csrfBootstrap = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + serverPort + "/api/v1/traces"))
                        .header("Authorization", investigationAuthorization())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );
        String csrfCookie = csrfBootstrap.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        String csrfToken = URLDecoder.decode(
                csrfCookie.substring("XSRF-TOKEN=".length()),
                StandardCharsets.UTF_8
        );

        HttpResponse<Void> mutation = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + serverPort + "/api/v1/alerts"))
                        .header("Authorization", investigationAuthorization())
                        .header("Cookie", csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(request))
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );

        assertEquals(200, mutation.statusCode());
    }

    @Test
    void websocketHandshakeRequiresInvestigatorAndConfiguredOrigin() throws Exception {
        mockMvc.perform(get("/ws/info").header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ws/info")
                        .with(httpBasic(INVESTIGATION_USERNAME, INVESTIGATION_PASSWORD))
                        .header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());

        assertNotNull(mockMvc.perform(get("/ws/info")
                        .with(httpBasic(INVESTIGATION_USERNAME, INVESTIGATION_PASSWORD))
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private String investigationAuthorization() {
        String credentials = Base64.getEncoder().encodeToString(
                (INVESTIGATION_USERNAME + ":" + INVESTIGATION_PASSWORD).getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + credentials;
    }
}
