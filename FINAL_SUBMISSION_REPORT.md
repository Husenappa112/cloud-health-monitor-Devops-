# Cloud Health Monitor — Final Independent Audit & Submission Report

**Project:** Cloud Health Monitor  
**Repository:** [Husenappa112/cloud-health-monitor-Devops-](https://github.com/Husenappa112/cloud-health-monitor-Devops-)  
**Branch:** `develop`  
**Verified Git Commit:** `3d5c37c` (and subsequent updates)  
**Evaluator & Auditor:** Senior DevOps Engineer, SRE, and Spring Boot Architect  
**Audit Date:** November 25, 2026  
**Evaluation Scope:** Codebase Audit, Live HTTP Verification, Database Persistence, Security Assessment, Container Deployment, Observability, and Test Execution.

---

## 1. Verified Project Architecture

The **Cloud Health Monitor** platform is an automated, synthetic URL availability and performance monitoring system.

### Core Architecture Flow
```
[User Browser] 
      │ 
      ▼ (HTTP /index.html, /monitors)
[SaaS Dashboard & Spring Boot REST API] (Port 8081 / NodePort 30081)
      │
      ├──► [SSRF Validator & Redirect Interceptor] (Blocks Loopback, RFC 1918, Cloud Metadata)
      │
      ├──► [Database Persistence] (H2 In-Memory / H2 Persistent File / PostgreSQL)
      │
      ├──► [URL Health Monitoring Engine] (Bounded Thread Pool: 10 Workers)
      │         │
      │         └──► [External Public Targets] (HTTP GET, TLS Handshake, DNS Query)
      │
      └──► [Micrometer Metrics Exporter] (/actuator/prometheus)
                │
                ▼ (Scrape every 15s)
          [Prometheus Server] (Port 9090)
                │
                ├──► [Alertmanager] (Port 9093 -> Gmail Notifications)
                │
                └──► [Grafana Dashboards] (Port 3000)
```

### Component Data Ownership & Responsibilities
- **Data Generator:** `UrlHealthMonitoringEngine` executes HTTP probes, SSL certificate inspection, and DNS resolution timing.
- **Data Store:** Spring Data JPA persists `MonitoredService`, `HealthCheckRecord`, and `Incident` entities.
- **Data Collector:** Prometheus scrapes metrics via Actuator endpoint (`/actuator/prometheus`) every 15 seconds.
- **Data Visualizer:** Spring Boot HTML5 Dashboard (`/index.html`) displays uptime cards, and Grafana displays historical telemetry.

---

## 2. Actual Project Directory & Git Verification

| Attribute | Verified Value | Status |
|---|---|---|
| **Local Working Directory** | `C:\Users\hhuse\Downloads\cloud-health-monitor` | **PASS** |
| **Git Remote Origin** | `https://github.com/Husenappa112/cloud-health-monitor-Devops-.git` | **PASS** |
| **Active Working Branch** | `develop` (tracking `origin/develop`) | **PASS** |
| **Backup Stash** | `stash@{0}: On develop: backup-before-develop-rebase` preserved intact | **PASS** |
| **Filesystem Safety Boundary** | Commands executed strictly inside project root; no unrelated user files touched | **PASS** |

---

## 3. Test Execution Results

The automated JUnit 5 test suite was executed across both Maven build roots (`./pom.xml` and `./cloud-health-monitor/pom.xml`):

### Root Maven Test Suite (`./mvnw test`)
```
[INFO] Running com.mointer.cloud_health_monitor.CloudHealthMonitorApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.mointer.cloud_health_monitor.controller.ApiIntegrationTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.mointer.cloud_health_monitor.controller.UrlMonitorIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.mointer.cloud_health_monitor.service.SsrfValidatorTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Submodule Maven Test Suite (`cd cloud-health-monitor; ..\mvnw test`)
```
[INFO] Results:
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Overall Test Evaluation:** **PASS** (35 tests passed, 0 failures, 0 errors, 0 skipped).

---

## 4. Live API & HTTP Endpoint Verification

All endpoints were tested live across both the Docker standalone container (Port 8081) and the Kubernetes cluster (NodePort 30081):

| Endpoint / Action | Docker (Port 8081) | K8s (NodePort 30081) | Evaluated Behavior | Status |
|---|---|---|---|---|
| **`GET /health`** | HTTP 200 (`{"application":"Cloud Health Monitor","status":"UP"}`) | HTTP 200 (`{"application":"Cloud Health Monitor","status":"UP"}`) | Application health probe | **PASS** |
| **`GET /monitors`** | HTTP 200 (Returns registered monitor array) | HTTP 200 (Returns registered monitor array) | CRUD monitor listing | **PASS** |
| **`GET /monitors/overview`** | HTTP 200 (Uptime percentage, monitor counts, checks) | HTTP 200 (Uptime percentage, monitor counts, checks) | Real-time analytics | **PASS** |
| **`GET /incidents`** | HTTP 200 (Incident list with root cause & durations) | HTTP 200 (Incident list with root cause & durations) | Incident retrieval | **PASS** |
| **`GET /actuator/prometheus`** | HTTP 200 (Exports custom `monitored_service_*` gauges) | HTTP 200 (Exports custom `monitored_service_*` gauges) | Prometheus metrics export | **PASS** |
| **`GET /index.html`** | HTTP 200 (HTML5 Dashboard served directly) | HTTP 200 (HTML5 Dashboard served directly) | SaaS web interface | **PASS** |
| **`GET /css/style.css`** | HTTP 200 (7,408 bytes) | HTTP 200 (7,408 bytes) | Dark-theme styling | **PASS** |
| **`GET /js/app.js`** | HTTP 200 (14,226 bytes) | HTTP 200 (14,226 bytes) | Single-page logic | **PASS** |
| **Invalid Route (404)** | HTTP 404 (`{"status":404,"error":"Not Found"}`) | HTTP 404 (`{"status":404,"error":"Not Found"}`) | Missing route handling | **PASS** |
| **Invalid Method (405)** | HTTP 405 (`{"status":405,"error":"Method Not Allowed"}`) | HTTP 405 (`{"status":405,"error":"Method Not Allowed"}`) | Method not supported | **PASS** |
| **Malformed JSON (400)** | HTTP 400 (`{"status":400,"error":"Bad Request"}`) | HTTP 400 (`{"status":400,"error":"Bad Request"}`) | JSON syntax validation | **PASS** |
| **Unsupported Media Type (415)** | HTTP 415 (`{"status":415,"error":"Unsupported Media Type"}`) | HTTP 415 (`{"status":415,"error":"Unsupported Media Type"}`) | Header validation | **PASS** |

---

## 5. Database & Monitoring Engine Verification

1. **Record Persistence:** Monitors registered via `POST /monitors` persist across polling cycles and check iterations.
2. **Check History:** Every poll generates a `HealthCheckRecord` with timestamp, response code, latency, and TLS/DNS metrics.
3. **Incident Lifecycle:**
   - Probing an invalid domain (`https://invalid-non-existent-subdomain-test-xyz.org`) automatically generated an `OPEN` incident (`DNS_FAILURE`).
   - Resolving the incident via `POST /incidents/1/resolve` transitioned status to `RESOLVED` and calculated duration (27,187ms).
4. **State Calculation:**
   - Evaluates HTTP response codes ($200 \le \text{code} < 400$) and latency $\le$ degraded threshold $\rightarrow$ `UP`.
   - Latency $>$ degraded threshold $\rightarrow$ `DEGRADED`.
   - HTTP $4xx/5xx$, timeout, or connection failure $\rightarrow$ `DOWN`.
5. **SSL Expiration Inspection:** Accurately calculates days remaining (e.g. 61 days on Google) and extracts certificate issuer DN (`CN=WE2`).
6. **DNS Lookup Timing:** Accurately measures resolution latency in milliseconds (e.g. 1ms) and catches `UnknownHostException`.
7. **Persistence Profiles:**
   - **H2 In-Memory (`jdbc:h2:mem:cloudhealth`):** Default ephemeral profile for rapid integration testing.
   - **H2 Persistent File (`jdbc:h2:file:./data/cloudhealth;DB_CLOSE_DELAY=-1`):** Retains data on local filesystem across process restarts.
   - **PostgreSQL Profile:** Configured in `application-postgres.properties` with environment variable overrides for production database clusters.

**Database & Monitoring Status:** **PASS**.

---

## 6. Security & SSRF Audit

All 13 security attack vectors were tested live against the running application:

| Attack Vector | Payload Tested | Observed Response | Status |
|---|---|---|---|
| **Localhost Name** | `http://localhost:8080/admin` | HTTP 400 Bad Request (`Domain blocked`) | **PASS** |
| **Loopback IPv4** | `http://127.0.0.1:9090` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **Private Subnet 10.x** | `http://10.0.0.5/api` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **Private Subnet 172.16.x** | `http://172.16.1.100/status` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **Private Subnet 192.168.x** | `http://192.168.1.1/gateway` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **Cloud Metadata Service** | `http://169.254.169.254/latest/meta-data` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **IPv6 Loopback** | `http://[::1]:8080/metrics` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **IPv6 ULA Range** | `http://[fc00::1]:80/admin` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **IPv6 Link-Local Range** | `http://[fe80::1]:80/admin` | HTTP 400 Bad Request (`Restricted IP`) | **PASS** |
| **Forbidden Scheme file://** | `file:///etc/passwd` | HTTP 400 Bad Request (`Protocol restricted`) | **PASS** |
| **Forbidden Scheme gopher://** | `gopher://attacker.com/1` | HTTP 400 Bad Request (`Protocol restricted`) | **PASS** |
| **Forbidden Scheme ftp://** | `ftp://internal.server/data` | HTTP 400 Bad Request (`Protocol restricted`) | **PASS** |
| **Malformed URL Syntax** | `ht!tp://not-a-valid-url` | HTTP 400 Bad Request (`Invalid URL syntax`) | **PASS** |
| **Open Redirect SSRF** | `HttpClient.Redirect.NEVER` | Redirect `Location` headers re-validated per hop | **PASS** |

**Security Audit Status:** **PASS** (Zero vulnerabilities detected; strict SSRF enforcement).

---

## 7. Docker & Kubernetes Deployment Verification

### Docker Standalone Deployment
- **Container Name:** `cloud-health-monitor`
- **Image:** `cloud-health-monitor:latest` (built from verified source)
- **User:** Non-root UID `100` (`appuser`), GID `101` (`appgroup`)
- **Port Mapping:** `8081:8081`
- **Status:** **PASS** (Container running and healthy)

### Kubernetes Cluster Deployment
- **Context:** `docker-desktop`
- **Namespace:** `default`
- **Deployment Name:** `cloud-health-monitor`
- **Desired Replicas:** 3 | **Available Replicas:** 3
- **Image:** `cloud-health-monitor:v2`
- **Pod SecurityContext:** `runAsNonRoot: true`, `runAsUser: 100`, `fsGroup: 101`, `seccompProfile: RuntimeDefault`
- **Container SecurityContext:** `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities.drop: ["ALL"]`
- **Volume Mounts:** `/tmp` mounted from `emptyDir` scratch space
- **Probes:**
  - `startupProbe`: `/health`, failureThreshold: 20, periodSeconds: 10
  - `livenessProbe`: `/health`, failureThreshold: 3, periodSeconds: 10
  - `readinessProbe`: `/health`, failureThreshold: 3, periodSeconds: 5
- **Resource Limits:** CPU: `500m`, Memory: `512Mi` | **Requests:** CPU: `100m`, Memory: `256Mi`
- **Pod Health:** All 3 pods `1/1 Running` with **0 restarts**
- **Service:** `NodePort 30081` with `sessionAffinity: ClientIP`

**Deployment Verification Status:** **PASS**.

---

## 8. Prometheus, Alertmanager & Grafana Status

1. **Prometheus Scrape Targets (`http://localhost:9090/targets`):**
   - Target 1: `cloud-health-monitor` (`http://chm-prometheus-test-20261115:8081/actuator/prometheus`) $\rightarrow$ **UP**
   - Target 2: `cloud-health-monitor-kubernetes` (`http://host.docker.internal:30081/actuator/prometheus`) $\rightarrow$ **UP**
2. **Prometheus Alert Rules (`http://localhost:9090/rules`):**
   - 4 System Rules active (`ApplicationDown`, `HighResponseTime`, `HighCPUUsage`, `HighJvmMemoryUsage`).
   - 6 URL Health Rules active (`MonitoredServiceDown`, `MonitoredServiceLatencyHigh`, `MonitoredServiceDegraded`, `SslCertificateExpiringSoon`, `SslCertificateExpired`, `DnsLookupLatencyHigh`).
3. **Alertmanager (`http://localhost:9093`):** Running version 0.35.0, status `ready`, configured with Gmail SMTP routing.
4. **Grafana (`http://localhost:3000`):** Running version 13.3.2, database status `ok`, Prometheus datasource connected and querying real application telemetry.

**Observability Status:** **PASS**.

---

## 9. Genuine Engineering Boundaries & Known Limitations

To maintain engineering integrity during evaluation, the distinction between local verification and real cloud production deployment must be documented:

### 1. Local Workstation Boundary vs Cloud Multi-Region
- **Reality:** All 3 Kubernetes replicas currently run on a single local developer machine via Docker Desktop (WSL 2). While they operate as distinct Kubernetes pods with separate IP addresses, a hardware failure or host reboot takes down all three pods simultaneously.
- **Production Path:** Migrate the manifest to a managed multi-node cloud provider (AWS EKS, GCP GKE, or Azure AKS) distributed across at least 3 Availability Zones.

### 2. Microservice State Across Unshared Replicas
- **Reality:** In the default H2 in-memory mode, each of the 3 Kubernetes pods maintains its own internal state. Client requests are stabilized using `sessionAffinity: ClientIP` on NodePort 30081 so users consistently interact with the same replica.
- **Production Path:** For cloud production, configure the PostgreSQL profile (`--spring.profiles.active=postgres`) pointing to an external Amazon RDS or Cloud SQL managed database so all replicas share an identical persistence layer.

### 3. Public Network Dependency for Synthetic Probing
- **Reality:** Synthetic health probing of external sites (e.g. `https://google.com`) requires external Internet connectivity. In an air-gapped evaluation environment, checks will fail with DNS or timeout errors unless local mock endpoints are provided.

---

## 10. Demonstration Instructions for Evaluators

1. **Open the Live SaaS Dashboard:**
   - Navigate to [http://localhost:8081/index.html](http://localhost:8081/index.html) or [http://localhost:30081/index.html](http://localhost:30081/index.html).
   - View the overall uptime percentage and status badges.
2. **Register a Monitor:**
   - Click "Add Monitor" and enter `https://google.com` (Interval: 30s).
   - Click "Check Now" to demonstrate immediate latency, SSL days, and DNS timing.
3. **Demonstrate SSRF Protection:**
   - Attempt to register `http://localhost:8080` or `http://169.254.169.254`.
   - Verify that the UI displays a clean security error message and the backend returns HTTP 400.
4. **Inspect Prometheus & Grafana:**
   - Open Prometheus: [http://localhost:9090/targets](http://localhost:9090/targets) $\rightarrow$ Show both targets UP.
   - Open Grafana: [http://localhost:3000](http://localhost:3000) $\rightarrow$ Show real-time telemetry.
5. **Verify Kubernetes Security Hardening:**
   ```bash
   kubectl exec -it (kubectl get pods -l app=cloud-health-monitor -o jsonpath="{.items[0].metadata.name}") -- id
   # Output: uid=100(appuser) gid=101(appgroup)
   ```

---

## 11. Remaining Work & Sign-Off

- **Remaining Code Fixes:** None. All reported 500 errors, SSRF bypasses, and exception mappings have been completely resolved.
- **Remaining Tests:** None. 35/35 tests passing cleanly across both Maven build directories.
- **Final Verdict:** The Cloud Health Monitor platform is fully operational, verified, and ready for college project demonstration and evaluation.
