# Cloud Health Monitor 🌐⚡

> **Production-Grade URL Availability, Health & Performance Monitoring Platform**  
> *Engineered with Java 21, Spring Boot 4, PostgreSQL / H2, Prometheus, Grafana, Alertmanager, Docker, Kubernetes, Vercel & Render.*

---

## 📌 Live Deployments & Repository
- **Production Frontend (Vercel):** [https://cloud-health-monitor-ui.vercel.app](https://cloud-health-monitor-ui.vercel.app)
- **Production Backend (Render):** `https://cloud-health-monitor-devops.onrender.com`
- **GitHub Repository:** [Husenappa112/cloud-health-monitor-Devops-](https://github.com/Husenappa112/cloud-health-monitor-Devops-)
- **Active Working Branch:** `develop`

---

## 🚀 Overview

**Cloud Health Monitor** is an enterprise-grade URL availability and infrastructure observability platform. It allows users to register any publicly accessible HTTP/HTTPS URL (e.g., `https://google.com`, `https://github.com`, or custom microservice REST APIs) and continuously monitor availability, latency, DNS resolution, and SSL certificates across global networks.

### Key Capabilities
- **Real-Time Health Engine:** Automated background scheduler checks registered targets every 30 to 300 seconds.
- **14 Core Health Metrics:** Captures HTTP status, latency, DNS lookup, SSL days remaining, certificate issuer, TLS validity, connection state, redirect tracking, consecutive failures, 24h/7d/30d uptime %, and downtime duration.
- **Deep Security & SSRF Protection:** Multi-layer defense-in-depth engine prevents Server-Side Request Forgery by blocking IPv4/IPv6 loopback (`127.0.0.0/8`), RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local/cloud metadata (`169.254.169.254`), and IPv6 ULA/link-local addresses with safe redirect evaluation.
- **Interactive UI Dashboard:** High-performance responsive web dashboard featuring sparkline latency trend charts, pause/resume controls, parameter editing, status filters, search, and public status pages.
- **Enterprise Observability:** Native Prometheus metrics scraping (`/actuator/prometheus`), 6 Prometheus alerting rules, Alertmanager routing, and Grafana visualization dashboards.
- **Automated CI/CD & Orchestration:** Multi-stage Docker containers, Kubernetes 3-replica rolling deployment with liveness/readiness probes, Jenkins Declarative Pipeline, and GitHub Actions CI.

---

## 🏗️ System Architecture

```
[ Web Browser / User ]
        │
        ├── (HTTPS) ──► [ Vercel Production Edge ]
        │                   └─► Single-Page Dashboard (HTML5 / Canvas / CSS3 / ES6)
        │
        └── (REST / JSON) ──► [ Spring Boot 4 Engine (Java 21) ]
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
| 4 | **DNS Status** | Hostname DNS resolution result | `SUCCESS`, `FAILED`, `SKIPPED` |
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
- **Frontend:** Responsive HTML5, Canvas API sparklines, Vanilla ES6 JavaScript, Modern CSS3
- **Observability:** Prometheus, Grafana, Alertmanager, Micrometer Prometheus Exporter
- **Containerization & Orchestration:** Docker multi-stage build, Kubernetes (3 replicas, NodePort 30081)
- **CI/CD Automation:** Jenkins Declarative Pipeline, GitHub Actions CI
- **Cloud Hosting:** Vercel (Frontend CDN), Render (Containerized Backend + Managed Postgres)

---

## ⚡ Quickstart — Local Development

### Prerequisites
- JDK 21 or higher
- Maven 3.9+ (or use included `./mvnw`)
- Docker Desktop (optional, for container execution)

### 1. Clone & Build
```bash
git clone https://github.com/Husenappa112/cloud-health-monitor-Devops-.git
cd cloud-health-monitor-Devops-
git checkout develop
```

### 2. Run Automated Tests
```bash
# Execute full test suite (40 unit, integration, and security tests)
./mvnw test
```

### 3. Run Locally
```bash
./mvnw spring-boot:run
```
The application starts at:
- **Web Dashboard:** [http://localhost:8081](http://localhost:8081)
- **REST API:** `http://localhost:8081/api/monitors`
- **Prometheus Metrics:** `http://localhost:8081/actuator/prometheus`
- **Application Health:** `http://localhost:8081/health`

---

## 🧪 Testing Summary

The project includes 40 comprehensive automated test cases:
- `CloudHealthMonitorApplicationTests`: Application context boot validation.
- `ApiIntegrationTest`: Embedded server REST endpoints, `/health`, registry lifecycle.
- `UrlMonitorIntegrationTest`: URL registration, URL auto-normalization, pause/resume, edit, overview, and statistics.
- `SsrfValidatorTest`: SSRF loopback, 10.x, 172.16.x, 192.168.x, 169.254.x, IPv6, port validation, and protocol restriction.

---

## 📜 Documentation Directory

| Document | Purpose |
|---|---|
| [PROJECT_REPORT.md](PROJECT_REPORT.md) | Comprehensive engineering and academic project report |
| [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) | Deep-dive architectural blueprints and diagrams |
| [API_DOCUMENTATION.md](API_DOCUMENTATION.md) | OpenAPI REST specification with payloads and status codes |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | Entity relational models, indices, and schema definitions |
| [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) | Step-by-step production rollout guide (Vercel, Render, K8s) |
| [SECURITY_DOCUMENTATION.md](SECURITY_DOCUMENTATION.md) | SSRF prevention architecture and threat models |
| [TESTING_REPORT.md](TESTING_REPORT.md) | 40-test execution audit, test results, and coverage metrics |
| [USER_GUIDE.md](USER_GUIDE.md) | Operations manual and user dashboard guide |
| [PHASE_COMPLETION_REPORT.md](PHASE_COMPLETION_REPORT.md) | Project sign-off and milestone delivery handover |

---

## 👤 Author & Maintainer
- **Engineer:** Husenappa
- **Repository:** [Husenappa112/cloud-health-monitor-Devops-](https://github.com/Husenappa112/cloud-health-monitor-Devops-)
- **Branch:** `develop`
