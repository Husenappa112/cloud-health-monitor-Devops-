package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.model.ServiceRegistrationRequest;
import com.mointer.cloud_health_monitor.service.ServiceRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Full-stack integration tests using an embedded server on a random port.
 * RestClient (Spring 6+, included in Spring Boot 4) replaces TestRestTemplate.
 *
 * <p>Service-health tests use a lightweight local HTTP server so no external
 * network is needed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {

    @LocalServerPort int port;
    @Autowired ServiceRegistry serviceRegistry;

    private RestClient client;

    // Local HTTP server used by health-check tests
    private static HttpServer localServer;
    private static int        localPort;

    @BeforeAll
    static void startLocalServer() throws Exception {
        localServer = HttpServer.create(new InetSocketAddress(0), 0);
        localPort   = localServer.getAddress().getPort();

        // /up → 200 OK
        localServer.createContext("/up", exchange -> {
            byte[] body = "OK".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });

        // /down → 503
        localServer.createContext("/down", exchange -> {
            byte[] body = "Service Unavailable".getBytes();
            exchange.sendResponseHeaders(503, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });

        localServer.start();
    }

    @AfterAll
    static void stopLocalServer() {
        if (localServer != null) localServer.stop(0);
    }

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
        // Clear registry between tests
        for (var entry : serviceRegistry.all()) {
            serviceRegistry.delete(entry.getName());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /health
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getHealth_returnsUp() {
        @SuppressWarnings("unchecked")
        Map<String, String> body = client.get().uri("/health")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("status", "UP")
                        .containsEntry("application", "Cloud Health Monitor");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /services (empty)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getServices_emptyRegistry_returnsEmptyArray() {
        @SuppressWarnings("unchecked")
        List<?> body = client.get().uri("/services")
                .retrieve().body(List.class);
        assertThat(body).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /services – happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void postServices_validRequest_returnsCreated() {
        var req = new ServiceRegistrationRequest("Auth API", "https://example.com/auth");
        ResponseEntity<Map> resp = client.post().uri("/services")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .toEntity(Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsEntry("name", "Auth API")
                                 .containsEntry("status", "UNKNOWN");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /services – duplicate name
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void postServices_duplicateName_returnsConflict() {
        var req = new ServiceRegistrationRequest("Payment API", "https://example.com");
        // First registration succeeds
        client.post().uri("/services")
              .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
              .body(req).retrieve().toBodilessEntity();

        // Second should fail with 409
        assertThatThrownBy(() ->
                client.post().uri("/services")
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .body(req).retrieve().toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /services – missing name
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void postServices_missingName_returnsBadRequest() {
        var req = new ServiceRegistrationRequest("", "https://example.com");
        assertThatThrownBy(() ->
                client.post().uri("/services")
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .body(req).retrieve().toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /services – invalid URL scheme
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void postServices_invalidUrlScheme_returnsBadRequest() {
        var req = new ServiceRegistrationRequest("FTP Service", "ftp://example.com");
        assertThatThrownBy(() ->
                client.post().uri("/services")
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .body(req).retrieve().toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /services/{name}
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getServiceByName_exists_returnsService() {
        serviceRegistry.register("My API", "https://example.com");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("/services/My API")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("name", "My API");
    }

    @Test
    void getServiceByName_notFound_returns404() {
        assertThatThrownBy(() ->
                client.get().uri("/services/NonExistent")
                      .retrieve().toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELETE /services/{name}
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void deleteService_exists_returnsNoContent() {
        serviceRegistry.register("Temp API", "https://example.com");

        ResponseEntity<Void> del = client.delete().uri("/services/Temp API")
                .retrieve().toBodilessEntity();
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThatThrownBy(() ->
                client.get().uri("/services/Temp API")
                      .retrieve().toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /services/{name}/health – UP
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void checkHealth_upService_returnsUp() {
        String url = "http://localhost:" + localPort + "/up";
        serviceRegistry.register("UpService", url);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("/services/UpService/health")
                .retrieve().body(Map.class);

        assertThat(body).containsEntry("name", "UpService")
                        .containsEntry("status", "UP");
        assertThat((Integer) body.get("httpStatus")).isEqualTo(200);
        assertThat(body.get("responseTimeMs")).isNotNull();
        assertThat(body.get("checkedAt")).isNotNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /services/{name}/health – DOWN (503)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void checkHealth_downService_returnsDown() {
        String url = "http://localhost:" + localPort + "/down";
        serviceRegistry.register("DownService", url);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("/services/DownService/health")
                .retrieve().body(Map.class);

        assertThat(body).containsEntry("status", "DOWN");
        assertThat((Integer) body.get("httpStatus")).isEqualTo(503);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /services/{name}/health – unknown service
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void checkHealth_unknownService_returns404() {
        assertThatThrownBy(() ->
                client.get().uri("/services/Ghost/health")
                      .retrieve().toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /metrics
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getMetrics_returnsRealStats() {
        String url = "http://localhost:" + localPort + "/up";
        serviceRegistry.register("MetricsTest", url);

        // Trigger a health check so totalChecksPerformed is > 0
        client.get().uri("/services/MetricsTest/health").retrieve().toBodilessEntity();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("/metrics")
                .retrieve().body(Map.class);

        assertThat((Integer) body.get("totalServices")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) body.get("totalChecksPerformed")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) body.get("applicationUptimeSeconds")).isGreaterThan(0);
        assertThat((Integer) body.get("upServices")).isGreaterThanOrEqualTo(1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Multiple services
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getServices_multipleServices_returnsAll() {
        serviceRegistry.register("Alpha", "https://alpha.example.com");
        serviceRegistry.register("Beta",  "https://beta.example.com");

        @SuppressWarnings("unchecked")
        List<?> body = client.get().uri("/services")
                .retrieve().body(List.class);
        assertThat(body).hasSize(2);
    }
}
