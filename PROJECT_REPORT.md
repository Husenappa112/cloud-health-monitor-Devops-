# Cloud Health Monitor — Engineering Project Report

**Project Title:** Cloud Health Monitor: Production-Grade URL Availability, Health & Performance Monitoring Platform  
**Author:** Husenappa  
**Repository:** `https://github.com/Husenappa112/cloud-health-monitor-Devops-`  
**Branch:** `develop`  
**Latest Production Commit:** `73293bd` (Updated with full metrics & lifecycle features)  
**Academic/Engineering Submission Date:** November 2026  

---

## 1. Executive Summary

In contemporary cloud-native computing, distributed microservices and external SaaS dependencies make continuous application health monitoring essential. Traditional synthetic ping monitors often only report whether an endpoint responds to ICMP packets, leaving subtle failure modes undetected—such as TLS certificate expiration, silent HTTP 3xx redirect loops, slow database-bound response times, or DNS resolution latency. Furthermore, naive HTTP monitoring agents that allow arbitrary user input are notoriously vulnerable to Server-Side Request Forgery (SSRF) attacks, enabling malicious actors to pivot into internal cloud metadata services and private subnets.

The **Cloud Health Monitor** platform was engineered to solve these challenges. Developed with **Java 21** and **Spring Boot 4**, the system provides real-time automated health checking across 14 multidimensional metrics. It incorporates a military-grade SSRF defense engine, continuous background execution pools, auto-recovery incident management, JPA persistence with PostgreSQL and H2, a responsive HTML5 Canvas dashboard deployed on **Vercel**, and containerized cloud deployment on **Render** and **Kubernetes**.

---

## 2. Problem Statement & Objectives

### 2.1 The Problem
1. **Shallow Health Checks:** Many uptime checkers only perform surface-level ping or status code validation, omitting DNS lookup duration, SSL certificate expiration countdowns, and connection status.
2. **SSRF Vulnerabilities:** Allowing users to submit URLs into an internal crawler exposes cloud metadata endpoints (`169.254.169.254`), container localhosts (`127.0.0.1`), and VPC private subnets (`10.0.0.0/8`, `192.168.0.0/16`).
3. **Operational Overhead:** Monitoring tools often lack automated incident recovery lifecycles, requiring manual triage whenever intermittent glitches occur.
4. **Disjointed DevOps Infrastructure:** Disconnected CI/CD, containerization, and monitoring make deployment validation error-prone.

### 2.2 Core Objectives
- Construct an autonomous URL monitoring engine that evaluates availability, latency, DNS, and TLS certificates without external dependencies.
- Implement strict SSRF prevention that stops loopback, private IPv4/IPv6, link-local, and open redirect attacks.
- Track 14 granular health metrics per target, storing historical records and calculating 24h, 7d, and 30d uptime percentages.
- Deliver full lifecycle management: registration, auto-normalization, duplicate prevention, custom check intervals, pausing, resuming, editing, and immediate on-demand checks.
- Build a lightweight single-page UI featuring real-time response time sparkline charts and public status pages.
- Integrate with Prometheus, Alertmanager, Grafana, Docker, Kubernetes, Jenkins, Vercel, and Render.

---

## 3. System Architecture & Methodology

### 3.1 Layered Architectural Model
The platform is organized into clean architectural tiers adhering to Separation of Concerns:
1. **Presentation Layer:**
   - Standalone Static Web Dashboard deployed to Vercel Global Edge CDN.
   - Built with Vanilla HTML5, Canvas API for hardware-accelerated latency sparklines, CSS3 variables, and modern ES6 asynchronous `fetch` calls.
   - Dual-mode API connectivity: operates seamlessly on same-origin (embedded local) and cross-origin (Vercel to Render) via configurable runtime headers.
2. **Controller & API Gateway Tier:**
   - `MonitorController` (mapped to both `/monitors` and `/api/monitors`).
   - `DashboardController` (mapped to `/dashboard` and `/api/dashboard`).
   - `IncidentController` (`/incidents` lifecycle management).
   - `HealthController` & Actuator (`/health`, `/actuator/prometheus`).
   - `GlobalExceptionHandler` converting domain exceptions into RFC 7807-compliant JSON error responses.
3. **Service & Engine Layer:**
   - `UrlHealthMonitoringEngine`: Coordinates SSRF validation, DNS timing, SSL handshake analysis, and safe redirect traversal.
   - `ScheduledMonitoringService`: Manages a 10-thread fixed executor pool running background check ticks, updating consecutive failures, downtime duration, and incident triggers.
   - `SsrfValidator`: Validates URI schemes, resolves IP addresses, checks CIDR blocks, and prevents internal host targeting.
   - `DnsInspector`: Measures authoritative DNS resolution time via `InetAddress.getByName()`.
   - `SslCertificateInspector`: Directly probes peer X.509 certificate chains via `SSLSocketFactory`, parsing expiration dates and issuer principals.
   - `IncidentManagementService`: State machine tracking outage inception, duration calculation, and auto-resolution upon service recovery.
   - `UptimeAnalyticsService`: Calculates statistical rolling averages for 24-hour, 7-day, and 30-day uptime and latency percentiles.
   - `PrometheusMetricsExportService`: Exports dynamic Micrometer gauges (`monitored_service_up`, `monitored_service_response_time_seconds`, `monitored_service_ssl_expiry_days`).
4. **Data Persistence Tier:**
   - Spring Data JPA with Hibernate ORM 7.x.
   - PostgreSQL (production cloud deployment on Render) and H2 in-memory (testing and local standalone).
   - `MonitoredServiceRepository`, `HealthCheckRecordRepository`, and `IncidentRepository` with optimized database indexing.

---

## 4. Key Engineering Modules

### 4.1 URL Health Monitoring Engine
The engine uses Java 21's modern `java.net.http.HttpClient` configured with `HttpClient.Redirect.NEVER`. When an HTTP 3xx status code is encountered, the engine extracts the `Location` header, constructs the resolved target URI, and re-validates the destination through the `SsrfValidator` before following up to a maximum of 3 hops. This prevents open redirect SSRF attacks where a public server redirects into `http://169.254.169.254/latest/meta-data/`.

### 4.2 SSRF Protection Mechanism
The `SsrfValidator` performs deep input inspection:
- Enforces `http` or `https` schemes; rejects `file://`, `ftp://`, `gopher://`, or raw socket URLs.
- Rejects non-standard ports (allowing only standard web ports 80, 443, 8080, 8443, etc.).
- Resolves hostnames to IP addresses and evaluates them against blacklisted CIDR ranges:
  - `127.0.0.0/8` (Loopback)
  - `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` (RFC 1918 Private networks)
  - `169.254.0.0/16` (Link-local and cloud provider metadata)
  - `0.0.0.0/8` (Local broadcast)
  - `::1`, `fc00::/7`, `fe80::/10` (IPv6 loopback, unique local, link-local)

### 4.3 Incident Lifecycle State Machine
When a monitored URL fails consecutive checks:
- The system transitions status from `UP` to `DOWN` or `DEGRADED`.
- An `Incident` record is created with type `OUTAGE`, `TIMEOUT`, `SSL_ERROR`, or `HTTP_ERROR`.
- If an existing open incident exists, duplicate incidents are avoided.
- Once the service successfully returns a 2xx HTTP response, the incident status transitions to `RESOLVED`, recording total downtime duration in seconds.

---

## 5. Verification & Testing Methodology

### 5.1 Test Architecture
The test suite consists of 40 automated tests executed via JUnit 5, AssertJ, and Spring Boot Test:
- **Unit Tests:** `SsrfValidatorTest` (14 tests) validating private IP detection, loopback rejection, IPv6 parsing, and port whitelist enforcement.
- **Integration Tests:** `ApiIntegrationTest` (14 tests) testing full HTTP request/response pipelines using an embedded test server and `RestClient`.
- **URL Monitor Tests:** `UrlMonitorIntegrationTest` (11 tests) verifying URL registration, auto-normalization (`github.com` $\rightarrow$ `https://github.com`), duplicate conflict handling (HTTP 409), pause/resume state toggling, monitor editing (HTTP PUT), and dashboard analytics aggregation.
- **Context Boot Tests:** `CloudHealthMonitorApplicationTests` (1 test) verifying JPA repository auto-wiring and context startup.

### 5.2 Dual-Tree Synchronization
Because the repository maintains both a root Maven POM and a child Maven module `cloud-health-monitor/` (used by Jenkins and Docker builds), dual-tree parity was verified by compiling and running the full 40-test suite across both project roots, achieving 100% build success without failures or skips.

---

## 6. Deployment Architecture

### 6.1 Vercel Frontend Deployment
- Static web client hosted on Vercel's global CDN: [https://cloud-health-monitor-ui.vercel.app](https://cloud-health-monitor-ui.vercel.app).
- Zero-latency asset caching with sub-second response times worldwide.
- Connects to the backend via configurable runtime storage (`localStorage`) and default auto-detection.

### 6.2 Render Containerized Backend & PostgreSQL
- Multi-stage Docker image packaging Java 21 runtime.
- Render Blueprint (`render.yaml`) specifying environment variables, health check paths (`/health`), and auto-provisioned PostgreSQL database.
- `DatabaseUrlEnvironmentPostProcessor` converts Render's `DATABASE_URL` (`postgres://...`) into valid Spring Boot JDBC URLs (`jdbc:postgresql://...`) at startup.

### 6.3 Local Kubernetes Orchestration
- Deployment configured with 3 replicas and rolling updates.
- Liveness and readiness probes checking `/health` on HTTP port 8081.
- Service published on NodePort `30081`.

---

## 7. Conclusion & Future Enhancements

The Cloud Health Monitor project successfully transitions from a prototype into a production-grade availability platform. By capturing 14 distinct availability and latency metrics, strictly neutralizing SSRF vectors, automating incident recovery, and delivering real-time visual dashboards across modern cloud infrastructure, the system meets all enterprise uptime monitoring standards.

Future roadmap enhancements include multi-region geo-distributed worker probes, SMS/Webhook alert integrations via PagerDuty/Slack, and synthetic user journey multi-step transaction monitoring.
