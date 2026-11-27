package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.model.CreateMonitorRequest;
import com.mointer.cloud_health_monitor.model.MonitorResponse;
import com.mointer.cloud_health_monitor.model.UpdateMonitorRequest;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UrlMonitorIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    MonitoredServiceRepository repository;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
        repository.deleteAll();
    }

    @Test
    @DisplayName("POST /monitors successfully registers a public URL monitor")
    void createMonitor_validPublicUrl_returnsCreated() {
        CreateMonitorRequest req = new CreateMonitorRequest("Google", "https://google.com", 60);

        ResponseEntity<MonitorResponse> response = client.post()
                .uri("/monitors")
                .body(req)
                .retrieve()
                .toEntity(MonitorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Google");
        assertThat(response.getBody().url()).isEqualTo("https://google.com");
        assertThat(response.getBody().monitoringInterval()).isEqualTo(60);
        assertThat(response.getBody().dnsStatus()).isNotNull();
        assertThat(response.getBody().connectionStatus()).isNotNull();
    }

    @Test
    @DisplayName("POST /monitors auto-normalizes URLs missing https:// scheme")
    void createMonitor_autoNormalizesScheme() {
        CreateMonitorRequest req = new CreateMonitorRequest("GitHub Raw", "github.com", 30);

        ResponseEntity<MonitorResponse> response = client.post()
                .uri("/api/monitors")
                .body(req)
                .retrieve()
                .toEntity(MonitorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().url()).isEqualTo("https://github.com");
    }

    @Test
    @DisplayName("POST /monitors rejects SSRF loopback address with 400 Bad Request")
    void createMonitor_ssrfLoopback_returnsBadRequest() {
        CreateMonitorRequest req = new CreateMonitorRequest("Localhost Admin", "http://127.0.0.1:8080/admin", 60);

        assertThatThrownBy(() -> client.post()
                .uri("/monitors")
                .body(req)
                .retrieve()
                .toEntity(MonitorResponse.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("POST /monitors rejects duplicate monitor name with 409 Conflict")
    void createMonitor_duplicateName_returnsConflict() {
        CreateMonitorRequest req1 = new CreateMonitorRequest("DupService", "https://google.com", 60);
        CreateMonitorRequest req2 = new CreateMonitorRequest("DupService", "https://github.com", 60);

        client.post().uri("/monitors").body(req1).retrieve().toBodilessEntity();

        assertThatThrownBy(() -> client.post()
                .uri("/monitors")
                .body(req2)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    @DisplayName("GET /monitors returns all registered monitors")
    void listMonitors_returnsAll() {
        CreateMonitorRequest req1 = new CreateMonitorRequest("Svc1", "https://google.com", 60);
        CreateMonitorRequest req2 = new CreateMonitorRequest("Svc2", "https://github.com", 120);

        client.post().uri("/monitors").body(req1).retrieve().toBodilessEntity();
        client.post().uri("/monitors").body(req2).retrieve().toBodilessEntity();

        List<?> list = client.get().uri("/monitors").retrieve().body(List.class);
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("POST /api/monitors/{id}/pause and /resume toggles active state")
    void pauseAndResumeMonitor_updatesState() {
        CreateMonitorRequest req = new CreateMonitorRequest("ToggleSvc", "https://google.com", 60);
        MonitorResponse created = client.post().uri("/api/monitors").body(req).retrieve().body(MonitorResponse.class);
        assertThat(created).isNotNull();
        assertThat(created.enabled()).isTrue();

        // Pause
        MonitorResponse paused = client.post().uri("/api/monitors/" + created.id() + "/pause")
                .retrieve().body(MonitorResponse.class);
        assertThat(paused).isNotNull();
        assertThat(paused.enabled()).isFalse();

        // Resume
        MonitorResponse resumed = client.post().uri("/api/monitors/" + created.id() + "/resume")
                .retrieve().body(MonitorResponse.class);
        assertThat(resumed).isNotNull();
        assertThat(resumed.enabled()).isTrue();
    }

    @Test
    @DisplayName("PUT /api/monitors/{id} updates monitor parameters")
    void updateMonitor_modifiesFields() {
        CreateMonitorRequest req = new CreateMonitorRequest("OriginalName", "https://google.com", 60);
        MonitorResponse created = client.post().uri("/api/monitors").body(req).retrieve().body(MonitorResponse.class);
        assertThat(created).isNotNull();

        UpdateMonitorRequest update = new UpdateMonitorRequest("UpdatedName", "https://github.com", 90, true);
        MonitorResponse updated = client.put().uri("/api/monitors/" + created.id())
                .body(update).retrieve().body(MonitorResponse.class);

        assertThat(updated).isNotNull();
        assertThat(updated.name()).isEqualTo("UpdatedName");
        assertThat(updated.url()).isEqualTo("https://github.com");
        assertThat(updated.monitoringInterval()).isEqualTo(90);
    }

    @Test
    @DisplayName("GET /api/dashboard/summary returns full dashboard statistics")
    void getDashboardSummary_returnsMetrics() {
        CreateMonitorRequest req = new CreateMonitorRequest("DashSvc", "https://google.com", 60);
        client.post().uri("/api/monitors").body(req).retrieve().toBodilessEntity();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = client.get().uri("/api/dashboard/summary").retrieve().body(Map.class);
        assertThat(summary).isNotNull();
        assertThat(summary).containsKey("totalMonitors");
        assertThat(summary).containsKey("overallUptimePercentage");
        assertThat(summary).containsKey("enabledCount");
        assertThat(summary).containsKey("pausedCount");
    }

    @Test
    @DisplayName("GET /monitors/overview returns system aggregate metrics")
    void getOverview_returnsSummary() {
        CreateMonitorRequest req = new CreateMonitorRequest("Test Overview", "https://google.com", 60);
        client.post().uri("/monitors").body(req).retrieve().toBodilessEntity();

        @SuppressWarnings("unchecked")
        Map<String, Object> overview = client.get().uri("/monitors/overview").retrieve().body(Map.class);
        assertThat(overview).isNotNull();
        assertThat(overview).containsKey("totalMonitors");
        assertThat(overview).containsKey("overallUptimePercentage");
    }

    @Test
    @DisplayName("GET /api/monitors/{id}/uptime and /events return analytics and incidents")
    void getUptimeAndEvents_returnsData() {
        CreateMonitorRequest req = new CreateMonitorRequest("AnalyticsSvc", "https://google.com", 60);
        MonitorResponse created = client.post().uri("/api/monitors").body(req).retrieve().body(MonitorResponse.class);
        assertThat(created).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = client.get().uri("/api/monitors/" + created.id() + "/uptime")
                .retrieve().body(Map.class);
        assertThat(stats).isNotNull();
        assertThat(stats).containsKey("uptime24h");
        assertThat(stats).containsKey("uptime7d");
        assertThat(stats).containsKey("uptime30d");

        List<?> events = client.get().uri("/api/monitors/" + created.id() + "/events")
                .retrieve().body(List.class);
        assertThat(events).isNotNull();
    }

    @Test
    @DisplayName("DELETE /monitors/{id} removes a monitor")
    void deleteMonitor_removesFromRegistry() {
        CreateMonitorRequest req = new CreateMonitorRequest("ToDelete", "https://google.com", 60);
        MonitorResponse created = client.post().uri("/monitors").body(req).retrieve().body(MonitorResponse.class);

        assertThat(created).isNotNull();
        client.delete().uri("/monitors/" + created.id()).retrieve().toBodilessEntity();

        assertThat(repository.existsById(created.id())).isFalse();
    }
}
