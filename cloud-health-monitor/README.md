# Cloud Health Monitor — Application Availability & Performance Monitoring Platform

> **An Enterprise-Grade URL Availability, Latency, SSL & DNS Monitoring Platform** built with **Java 21**, **Spring Boot**, **Spring Data JPA**, **Prometheus**, **Alertmanager**, **Grafana**, **Docker**, and **Kubernetes**.

[![CI Build & Test](https://github.com/Husenappa112/cloud-health-monitor-Devops-/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Husenappa112/cloud-health-monitor-Devops-/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-green.svg)](https://spring.io/projects/spring-boot)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Hardened%20(UID%20100)-blue.svg)](https://kubernetes.io/)

---

## 1. Executive Overview

**Cloud Health Monitor** is a production-oriented observability and uptime monitoring platform designed to continuously verify the availability, response time, SSL validity, and DNS resolution of critical web services and APIs.

Users can register publicly accessible endpoints (e.g., `https://google.com`, `https://github.com`, microservice APIs) and monitor them through:
1. **Interactive SaaS Web Dashboard:** Single-page responsive interface served directly from the Spring Boot application (`/index.html`).
2. **RESTful APIs:** Complete CRUD endpoints for monitors, check history, uptime analytics, and incident lifecycle management (`/monitors`, `/incidents`).
3. **Enterprise DevOps & SRE Integration:** Native Prometheus scrape metrics (`/actuator/prometheus`), Alertmanager Gmail notifications, and pre-provisioned Grafana dashboards.
4. **Hardened Kubernetes Infrastructure:** Zero-trust container security (non-root UID 100, read-only root filesystem, dropped capabilities) deployed on Kubernetes.

---

## 2. Key Platform Features

- **Multi-Protocol URL Health Engine:** Uses Java 21 `HttpClient` with bounded connect and request timeouts (3s/5s) to test target endpoints without thread leaks.
- **SSL Certificate Expiry Tracking:** Inspects peer TLS certificates, calculates exact days until expiration, and triggers proactive warnings when `< 7 days`.
- **DNS Resolution & Latency Inspection:** Resolves hostnames and measures DNS query duration in milliseconds, detecting `UnknownHostException` early.
- **Enterprise SSRF Protection:** Prevents Server-Side Request Forgery by rejecting loopback (`127.0.0.1`), private RFC 1918 subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), cloud metadata endpoints (`169.254.169.254`), and forbidden schemes.
- **Automated Background Scheduler:** Multi-threaded bounded thread pool (10 workers) that polls services according to configurable intervals (30s, 60s, 120s, 300s).
- **Incident Lifecycle Management:** Automatically opens incidents on transition to `DOWN` and auto-resolves with duration tracking upon recovery to `UP`.
- **Uptime & Latency Analytics:** Calculates real-time 24-hour, 7-day, and 30-day uptime percentages and min/max/avg latency from persisted JPA check logs.
- **Prometheus Metrics & Alert Rules:** Custom Micrometer gauges (`monitored_service_up`, `monitored_service_response_time_seconds`, `monitored_service_ssl_expiry_days`) with 6 Alertmanager rules.
- **Interactive Web Dashboard:** Modern dark-themed dashboard with live status cards, endpoint tables, search filters, modal detail views, and a public status page.

---

## 3. Architecture

```
                                  ┌────────────────────────┐
                                  │   Public Endpoints     │
                                  │ Google, GitHub, APIs   │
                                  └──────────▲─────────────┘
                                             │ HTTP/HTTPS GET, SSL, DNS
┌────────────────────────────────────────────┼────────────────────────────────────────┐
│                      Cloud Health Monitor Platform                                  │
│                                            │                                        │
│  ┌───────────────────────┐    ┌────────────┴──────────┐    ┌─────────────────────┐  │
│  │   Responsive Web UI   │◄───┤ Scheduled Monitoring  ├───►│ Spring Data JPA     │  │
│  │   (Static Dashboard)  │    │ Engine (10 Threads)   │    │ H2 / PostgreSQL     │  │
│  └───────────▲───────────┘    └────────────┬──────────┘    └─────────────────────┘  │
│              │                             │                                        │
│  ┌───────────┴───────────┐                 │               ┌─────────────────────┐  │
│  │ REST APIs (/monitors) │                 ├──────────────►│ Incident Manager    │  │
│  └───────────────────────┘                 │               │ (Auto-Create/Resolve│  │
│                                            ▼               └─────────────────────┘  │
│                               ┌──────────────────────┐                              │
│                               │ Micrometer Export    │                              │
│                               │ /actuator/prometheus │                              │
│                               └──────────┬───────────┘                              │
└──────────────────────────────────────────┼──────────────────────────────────────────┘
                                           │ Scrape (15s)
                         ┌─────────────────┴─────────────────┐
                         │       Prometheus (Port 9090)      │
                         └───────┬───────────────────┬───────┘
                                 │ Alerts            │ Dashboards
                         ┌───────▼─────────┐ ┌───────▼─────────┐
                         │ Alertmanager    │ │ Grafana         │
                         │ (Gmail Alerts)  │ │ (Port 3000)     │
                         └─────────────────┘ └─────────────────┘
```

---

## 4. REST API Quick Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/monitors` | Register a new monitored URL (with SSRF validation) |
| `GET` | `/monitors` | List all monitors with live status and latency |
| `GET` | `/monitors/{id}` | Get details of a single monitor |
| `PUT` | `/monitors/{id}` | Update monitor name, URL, or check interval |
| `DELETE` | `/monitors/{id}` | Delete a monitor |
| `POST` | `/monitors/{id}/check` | Force an immediate out-of-band health check |
| `GET` | `/monitors/{id}/history` | Retrieve the 50 most recent check logs |
| `GET` | `/monitors/{id}/statistics`| Retrieve 24h/7d/30d uptime & latency stats |
| `GET` | `/monitors/overview` | Platform-wide aggregate metrics (UP/DOWN counts, uptime %) |
| `GET` | `/incidents` | List outage & degradation incidents |
| `POST` | `/incidents/{id}/resolve`| Manually resolve an open incident |
| `GET` | `/health` | Application health ping (backward compatible) |
| `GET` | `/actuator/prometheus` | Prometheus metrics scrape endpoint |

For complete request bodies, error schemas, and curl examples, see [API_DOCUMENTATION.md](file:///c:/Users/hhuse/Downloads/cloud-health-monitor/API_DOCUMENTATION.md).

---

## 5. Getting Started & Local Setup

### Prerequisites
- **JDK 21** or **25**
- **Maven 3.9+** (or use included `./mvnw`)
- **Docker Desktop** (with Kubernetes enabled)

### Running Locally with Maven
```bash
# Clone the repository
git clone -b develop https://github.com/Husenappa112/cloud-health-monitor-Devops-.git
cd cloud-health-monitor

# Run tests
./mvnw clean test

# Start the Spring Boot application
./mvnw spring-boot:run
```

Once started:
- **Web Dashboard:** Open [http://localhost:8081](http://localhost:8081) in your browser.
- **REST API:** `curl http://localhost:8081/monitors`
- **Prometheus Metrics:** [http://localhost:8081/actuator/prometheus](http://localhost:8081/actuator/prometheus)
- **H2 Console:** [http://localhost:8081/h2-console](http://localhost:8081/h2-console) (JDBC URL: `jdbc:h2:mem:cloudhealth`, User: `sa`)

---

## 6. Kubernetes Security & Deployment

The application is deployed on Kubernetes using a hardened pod security context complying with the Kubernetes Restricted Pod Security Standard:

```bash
# Apply Kubernetes Deployment & NodePort Service
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Verify pods (3 replicas running as non-root UID 100)
kubectl get pods -l app=cloud-health-monitor
kubectl get svc cloud-health-monitor
```

- **NodePort Access:** [http://localhost:30081](http://localhost:30081)
- **Security Audit:** Run non-root check:
  ```bash
  kubectl exec -it deployment/cloud-health-monitor -- id
  # Output: uid=100(guest) gid=101(guest) groups=101(guest)
  ```

---

## 7. Prometheus, Alertmanager & Grafana Monitoring

1. **Prometheus:** Running at [http://localhost:9090](http://localhost:9090) scraping `/actuator/prometheus` every 15 seconds.
2. **Alertmanager:** Running at [http://localhost:9093](http://localhost:9093) with 6 active rules:
   - `MonitoredServiceDown` (critical)
   - `MonitoredServiceLatencyHigh` (warning)
   - `MonitoredServiceDegraded` (warning)
   - `SslCertificateExpiringSoon` (warning)
   - `SslCertificateExpired` (critical)
   - `DnsLookupLatencyHigh` (warning)
3. **Grafana:** Running at [http://localhost:3000](http://localhost:3000) (admin / admin).

---

## 8. Complete Project Documentation

| Document | Purpose |
|---|---|
| [API_DOCUMENTATION.md](file:///c:/Users/hhuse/Downloads/cloud-health-monitor/API_DOCUMENTATION.md) | Full REST API reference with request/response schemas |
| [SECURITY_REPORT.md](file:///c:/Users/hhuse/Downloads/cloud-health-monitor/SECURITY_REPORT.md) | SSRF security analysis & Kubernetes container hardening audit |
| [DEPLOYMENT_ARCHITECTURE.md](file:///c:/Users/hhuse/Downloads/cloud-health-monitor/DEPLOYMENT_ARCHITECTURE.md) | Multi-tier deployment architecture and network topologies |
| [OPERATIONS_RUNBOOK.md](file:///c:/Users/hhuse/Downloads/cloud-health-monitor/OPERATIONS_RUNBOOK.md) | Step-by-step SRE operations, disaster recovery, and rollback procedures |
| [PROJECT_DEMO_GUIDE.md](file:///c:/Users/hhuse/Downloads/cloud-health-monitor/PROJECT_DEMO_GUIDE.md) | End-to-end interview presentation script and walkthrough guide |
| [DEPLOYMENT_ACCEPTANCE_REPORT.md](file:///c:/Users/hhuse/Downloads/cloud-health-monitor/DEPLOYMENT_ACCEPTANCE_REPORT.md) | Formal verification audit and acceptance signoff |
