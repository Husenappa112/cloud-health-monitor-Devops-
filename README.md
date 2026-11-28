# Cloud Health Monitor 🌐⚡

> **Production-Grade Observability & Availability Platform (Grafana / Datadog-Style Dark Theme)**  
> *Engineered with Java 21, Spring Boot 4, PostgreSQL / H2, Prometheus, Grafana, Alertmanager, Docker, Kubernetes, Vercel & Render.*

---

## 📌 Live Deployments & Repository
- **Production Frontend (Vercel):** [https://cloud-health-monitor-ui.vercel.app](https://cloud-health-monitor-ui.vercel.app)
- **Production Backend (Render):** `https://cloud-health-monitor-devops.onrender.com`
- **GitHub Repository:** [Husenappa112/cloud-health-monitor-Devops-](https://github.com/Husenappa112/cloud-health-monitor-Devops-)
- **Active Working Branch:** `develop`

---

## 🚀 Overview

**Cloud Health Monitor** is an enterprise-grade synthetic observability and URL availability platform inspired by modern Datadog and Grafana dark interfaces. It empowers SREs, DevOps engineers, and full-stack developers to register publicly accessible HTTP/HTTPS services (e.g. `https://google.com`, `https://github.com`, custom APIs) and continuously monitor availability, latency percentiles (P50, P90, P95, P99), DNS lookup duration, SSL/TLS certificate chains, and incident lifecycles across distributed networks.

### Key Capabilities
- **Grafana-Style Observability UI:** High-density dark charcoal aesthetic (`#080c14`, `#161b22`, subtle `#30363d` panel borders) designed for mission-critical monitoring wallboards and NOC consoles.
- **10 Dedicated Observability Views:**
  1. **Overview:** High-level system KPIs, live monitor telemetry, HTTP status distribution bar, and active alert feeds.
  2. **Monitors:** Complete registered service inventory with live search, status filtering (ALL, UP, DEGRADED, DOWN, PAUSED), custom sorting, and per-monitor actions (Immediate Re-check, Pause/Resume, Parameter Edit, Deletion, and 14-Metric Deep Inspection).
  3. **HTTP Insights:** Aggregated synthetic probe metrics, HTTP status code distribution (2xx/3xx/4xx/5xx), latency percentiles (P50 Median, P90, P95, P99, Min, Max), connection timeouts, socket failures, and DNS/SSL error counts. Explicitly labeled as synthetic probe telemetry rather than visitor traffic.
  4. **Infrastructure:** Real JVM runtime telemetry (Heap used/max/utilization %, Non-Heap, Available Processors, System Load, Active/Peak Threads, Uptime) and explicit `NOT_CONNECTED` host status clarifying that external targets require an authenticated agent to expose host-level CPU/RAM.
  5. **Incidents:** Full incident lifecycle state machine (`OPEN` $\rightarrow$ `ACKNOWLEDGED` $\rightarrow$ `RESOLVED`) with severity classification (`CRITICAL`, `WARNING`, `INFO`), operator investigation notes, and downtime SLA duration tracking.
  6. **Alerts:** Real-time threshold evaluation displaying active alerts (Outages, Latency Degradation > 2000ms, SSL Expiry $\le$ 14 days).
  7. **SSL Certificates:** Security posture dashboard showing certificate validity, Certificate Authority (CA) issuers, expiration dates, days remaining countdowns, and TLS protocol encryption.
  8. **Analytics:** Rolling SLA availability compliance (24-hour, 7-day, 30-day uptime %), average latency, and check totals.
  9. **Event Log:** Chronological audit trail of service state changes, outage detections, operator acknowledgments, and automated recoveries.
  10. **Settings:** Runtime API base URL configuration, live backend ping testing, and Prometheus/Actuator endpoint direct navigation.
- **Resilient Frontend Architecture:** Battle-tested API client (`frontend/js/api.js`) that detects Render free-tier cold-starts, handles 502/503/504 gateway responses gracefully, provides configurable timeout aborting, and prevents raw non-JSON syntax errors (`Unexpected token 'T'`).
- **14 Core Health Metrics:** Captures HTTP status, latency, DNS lookup, SSL days remaining, certificate issuer, TLS validity, connection state, redirect tracking, consecutive failures, 24h/7d/30d uptime %, and downtime duration.
- **SSRF Defense-in-Depth:** Rejects IPv4/IPv6 loopback (`127.0.0.0/8`), RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local/cloud metadata (`169.254.169.254`), and IPv6 ULA/link-local addresses with safe redirect evaluation.
- **Dual-Tree Parity:** 100% synchronization across root module and `cloud-health-monitor/` child module, with all 40 unit and integration tests passing.

---

## 🏗️ System Architecture

```
[ Web Browser / Operator ]
        │
        ├── (HTTPS) ──► [ Vercel Production Global Edge ]
        │                   └─► High-Density Observability Dashboard (HTML5 / CSS3 / ES6)
        │
        └── (REST / JSON) ──► [ Spring Boot 4 Backend Engine (Java 21) ]
                                    │
               ┌────────────────────┼────────────────────┐
               ▼                    ▼                    ▼
     [ SSRF Validator ]    [ DNS Inspector ]    [ SSL Inspector ]
               │                    │                    │
               └────────────────────┬────────────────────┘
                                    │
                                    ▼
                        [ Java 21 HTTP/2 Client ]
                                    │
                                    ▼
                       [ Public Monitored Target ]
                                    │
                                    ▼
       ┌────────────────────────────┴────────────────────────────┐
       ▼                                                         ▼
[ PostgreSQL / H2 Database ]                              [ Prometheus / Grafana ]
  - monitored_services                                      - monitored_service_up
  - health_check_records                                    - monitored_service_response_time_seconds
  - incidents                                               - monitored_service_ssl_expiry_days
```

---

## 📊 Captured Health & Performance Metrics (14 Total)

| # | Metric Name | Description | Output Example |
|---|---|---|---|
| 1 | **Current Status** | Primary health classification | `UP`, `DEGRADED`, `DOWN`, `UNKNOWN` |
| 2 | **HTTP Status Code** | Response status code received | `200`, `301`, `404`, `500` |
| 3 | **Response Time** | Total round-trip latency in milliseconds | `42 ms` |
| 4 | **DNS Status** | Hostname DNS resolution result | `RESOLVED`, `FAILED`, `SKIPPED` |
| 5 | **DNS Lookup Time** | Duration required for DNS resolution | `12 ms` |
| 6 | **Connection Status** | TCP socket & handshake status | `CONNECTED`, `TIMED_OUT`, `SSRF_BLOCKED` |
| 7 | **TLS/SSL Validity** | Whether certificate is valid & trusted | `true` / `false` |
| 8 | **SSL Days Remaining** | Days before TLS certificate expires | `84 days` |
| 9 | **SSL Expiry Date** | Exact timestamp of certificate expiration | `2027-02-19T00:00:00Z` |
| 10 | **SSL Issuer** | Certificate Authority / CN issuer | `GTS CA 1C3` |
| 11 | **Redirect Count** | Number of HTTP redirects followed | `1` (e.g. `http` $\rightarrow$ `https`) |
| 12 | **Final URL** | Destination URL after safe redirects | `https://www.google.com` |
| 13 | **Consecutive Failures** | Consecutive failed check attempts | `0` (resets on `UP`) |
| 14 | **Uptime & Downtime** | Rolling 24h/7d/30d uptime % and downtime seconds | `99.98%`, `0s downtime` |

---

## 🛠️ Technology Stack

- **Backend:** Java 21, Spring Boot 4.1.2, Spring Data JPA, Hibernate ORM 7.x, Micrometer
- **Security:** Custom SSRF Validator with CIDR matching, InetAddress resolution, and Redirect.NEVER enforcement
- **Database:** PostgreSQL (Production / Render) and H2 in-memory (Test & Local Development)
- **Frontend:** Responsive HTML5, High-density CSS3 design system, Resilient Vanilla ES6 API client
- **Observability:** Prometheus, Grafana, Alertmanager, Micrometer Prometheus Exporter
- **Containerization & Orchestration:** Docker multi-stage build, Kubernetes (3 replicas, NodePort 30081)
- **CI/CD Automation:** Jenkins Declarative Pipeline, GitHub Actions CI
- **Cloud Hosting:** Vercel (Frontend CDN), Render (Containerized Backend + Managed Postgres)

---

## ⚡ Quickstart — Local Development

### Prerequisites
- JDK 21 or higher
- Maven 3.9+ (or use included `./mvnw`)
- Git

### Build & Run Locally
```bash
# Clone the repository
git clone https://github.com/Husenappa112/cloud-health-monitor-Devops-.git
cd cloud-health-monitor-Devops-
git checkout develop

# Run the test suite (40 tests)
./mvnw clean test

# Run the application
./mvnw spring-boot:run
```

Access the dashboard locally at `http://localhost:8081` or inspect Prometheus metrics at `http://localhost:8081/actuator/prometheus`.
