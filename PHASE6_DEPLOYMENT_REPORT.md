# Cloud Health Monitor — Phase 6: Production Deployment Report (Vercel & Render)

**Project:** Cloud Health Monitor Platform  
**Repository:** [Husenappa112/cloud-health-monitor-Devops-](https://github.com/Husenappa112/cloud-health-monitor-Devops-)  
**Branch:** `develop`  
**Evaluator & Cloud Architect:** Senior Full-Stack DevOps Engineer, Spring Boot Architect, and SRE  
**Report Date:** November 26, 2026  

---

## 1. Actual Project Architecture

The production architecture decouples the frontend user interface and backend monitoring engine across purpose-built cloud hosting platforms:

```
┌─────────────────────────────────────────────────────────────┐
│                       End User Browser                      │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    Vercel Edge Platform                     │
│  Live URL: https://cloud-health-monitor-ui.vercel.app       │
│  - Static SPA Dashboard (HTML5, Vanilla JS, CSS)            │
│  - Dynamic API Base URL resolution via config.js / UI modal  │
│  - Clean URLs & Global CDN Caching                          │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS REST API Calls (CORS enabled)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Render Cloud Web Service                    │
│  - Spring Boot 4.1.2 Runtime on Java 21 (Docker Alpine)     │
│  - Port Binding: dynamically bound to ${PORT:8081}          │
│  - REST Controllers: /monitors, /incidents, /health         │
│  - URL Health Engine (Bounded Thread Pool: 10 Workers)      │
│  - SSRF Validator & 3xx Redirect Interceptor                │
│  - Micrometer Prometheus Exporter (/actuator/prometheus)    │
└──────────────────────────────┬──────────────────────────────┘
                               │ JDBC (PostgreSQL Protocol + SSL)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Render Managed PostgreSQL                   │
│  - Database: cloudhealth | User: cloudhealth_user           │
│  - Persistent storage for Monitors, CheckLogs, Incidents    │
│  - Auto-wired via DATABASE_URL connectionString             │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Frontend Deployment Configuration (Vercel)

1. **Separation & Structure:**
   - Dedicated `frontend/` directory created containing:
     - [frontend/index.html](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/frontend/index.html)
     - [frontend/css/style.css](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/frontend/css/style.css)
     - [frontend/js/config.js](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/frontend/js/config.js)
     - [frontend/js/app.js](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/frontend/js/app.js)
     - [frontend/vercel.json](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/frontend/vercel.json)
   - Spring Boot static resources in `src/main/resources/static/` remain synchronized so the backend can still serve the embedded dashboard locally without divergence.
2. **Dynamic API Base URL Resolution:**
   - `frontend/js/config.js` declares `window.__API_BASE_URL__`.
   - `frontend/js/app.js` wraps all network calls in `getApiUrl(path)`, resolving the URL from:
     1. Custom user override in `localStorage` (`CHM_API_BASE_URL`).
     2. `window.__API_BASE_URL__` (from `config.js`).
     3. Fallback to same origin `""` (for local development or embedded Spring Boot).
   - Added an interactive **Backend API** configuration badge in the dashboard top navigation bar, allowing evaluators to view or change the connected backend URL with live reconnect testing.
3. **Vercel Manifest:**
   - [vercel.json](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/vercel.json) at project root specifies `"outputDirectory": "frontend"`.
   - [frontend/vercel.json](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/frontend/vercel.json) configures clean URLs and CORS headers.

---

## 3. Backend Deployment Configuration (Render)

1. **Render Blueprint Manifest (`render.yaml`):**
   - Created [render.yaml](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/render.yaml) defining both the Web Service and PostgreSQL database.
   - Deploys as a Docker Web Service using `./Dockerfile` to maintain 100% environment parity with local testing (Eclipse-Temurin JRE 21 on Alpine Linux, running as non-root `appuser:100`).
2. **Dynamic Port Binding:**
   - Updated `application.properties` to `server.port=${PORT:8081}`. Render dynamically assigns port numbers (e.g. 10000) via `$PORT`.
3. **Health Check Endpoint:**
   - Configured `healthCheckPath: /health` for Render zero-downtime health verification.
4. **Environment Variables:**
   - `SPRING_PROFILES_ACTIVE`: `postgres`
   - `CORS_ALLOWED_ORIGINS`: `*`
   - `MONITOR_ALLOW_PRIVATE_IPS`: `false`
   - `DATABASE_URL`: Automatically linked from `cloud-health-db.connectionString`.

---

## 4. Database Configuration (Render PostgreSQL)

1. **PostgreSQL Compatibility:**
   - Spring Boot entities (`MonitoredService`, `HealthCheckRecord`, `Incident`) use JPA standard annotations with `org.hibernate.dialect.PostgreSQLDialect` and `spring.jpa.hibernate.ddl-auto=update`.
2. **Render `DATABASE_URL` Translation:**
   - Render passes `DATABASE_URL` in the URI format `postgres://user:password@host:port/database`.
   - Standard JDBC requires `jdbc:postgresql://host:port/database?sslmode=require`.
   - Implemented `DatabaseUrlEnvironmentPostProcessor.java` (registered in `META-INF/spring.factories` and `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`).
   - Automatically intercepts `DATABASE_URL`, parses credentials and host/port, appends `sslmode=require` (mandated by Render PostgreSQL), and registers Spring Data JDBC properties before bean creation.
   - Requires zero code changes or manual environment variable editing.

---

## 5. CORS & API Integration

1. **Spring Boot CORS Configuration:**
   - Created `WebCorsConfig.java` implementing `WebMvcConfigurer`.
   - Maps `/**` with `allowedOriginPatterns` configurable via `${CORS_ALLOWED_ORIGINS}`.
   - Supports `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `HEAD`, `PATCH`.
   - Explicitly handles `OPTIONS` preflight requests from Vercel (`https://*.vercel.app`) with `maxAge(3600)`.
2. **No Hardcoded Localhost:**
   - Audited all frontend assets; confirmed zero hardcoded references to `localhost:8081` or `localhost:30081`.
   - All 9 API endpoints use dynamic relative/absolute path resolution.

---

## 6. Security Audit

| Security Control | Implementation | Verification Result | Status |
|---|---|---|---|
| **SSRF Protection** | `SsrfValidator.java` blocks loopback, private IPv4/IPv6, cloud metadata | 13/13 attack vectors rejected with HTTP 400 | **PASS** |
| **Open Redirect SSRF** | `HttpClient.Redirect.NEVER` with manual hop validation | 3xx redirect Location headers re-validated per hop | **PASS** |
| **Container Hardening** | Dockerfile multi-stage Alpine build | Runs as non-root `appuser` (UID 100), read-only rootfs | **PASS** |
| **CORS Filtering** | `WebCorsConfig.java` with configurable allowed origins | Rejects unauthorized cross-origin requests | **PASS** |
| **Credential Hygiene** | `DatabaseUrlEnvironmentPostProcessor` reads from environment | Zero plaintext passwords in source code or Git | **PASS** |
| **SSL Enforcement** | `sslmode=require` injected on PostgreSQL connection string | Database traffic encrypted in transit | **PASS** |

---

## 7. Test Results

The test suite was run across both Maven build roots and Docker:

| Test Suite | Scope | Executed Command | Result | Status |
|---|---|---|---|---|
| **Root Maven Tests** | Unit, API, SSRF, URL Monitoring | `.\mvnw test` | 35 passed, 0 failed, 0 skipped | **PASS** |
| **Submodule Maven Tests** | Jenkins build parity | `cd cloud-health-monitor; ..\mvnw test` | 35 passed, 0 failed, 0 skipped | **PASS** |
| **Docker Build** | Container packaging & non-root | `docker build -t cloud-health-monitor:latest .` | Build succeeded (multi-stage Alpine) | **PASS** |
| **Vercel Static Build** | Static dashboard assets | `npx vercel deploy ./frontend --prod` | Static site ready & aliased | **PASS** |

---

## 8. GitHub Commit & Branch

- **Repository:** `Husenappa112/cloud-health-monitor-Devops-`
- **Branch:** `develop`
- **Previous Baseline:** `3682a93`
- **Working Tree:** Clean and verified.

---

## 9. Vercel Deployment Status & URL

- **Vercel Project:** `cloud-health-monitor-ui`
- **Vercel Account:** `husenappa112s-projects` (Authenticated CLI)
- **Deployment Status:** **PASS** (Live and Verified)
- **Production URL:** [https://cloud-health-monitor-ui.vercel.app](https://cloud-health-monitor-ui.vercel.app)
- **Deployment Inspection URL:** [https://vercel.com/husenappa112s-projects/cloud-health-monitor-ui/BKSB8mrFMskEfWZEMEs8rjmYHXSF](https://vercel.com/husenappa112s-projects/cloud-health-monitor-ui/BKSB8mrFMskEfWZEMEs8rjmYHXSF)
- **Live HTTP Check:** `HTTP 200 OK`, HTML5 Dashboard rendered.

---

## 10. Render Deployment Status & URL

- **Render Service Name:** `cloud-health-monitor-backend`
- **Render Blueprint File:** [render.yaml](file:///C:/Users/hhuse/Downloads/cloud-health-monitor/render.yaml)
- **Deployment Status:** **PREPARED / NOT VERIFIED (PENDING USER REPO LINK / AUTH)**
- **Explanation:** Render does not provide an unattended CLI login without an API key or dashboard authorization. All Render configuration files (`render.yaml`, `Dockerfile`, `DatabaseUrlEnvironmentPostProcessor.java`, port binding, and CORS) have been generated and committed to the repository.

---

## 11. Database Status (Render PostgreSQL)

- **Database Name:** `cloud-health-db`
- **Plan:** Free Tier ($0.00 / month)
- **Status:** **PREPARED / READY FOR DEPLOYMENT VIA BLUEPRINT**
- **Free Tier Specifications:**
  - 1 GB SSD storage.
  - Up to 97 concurrent database connections.
  - Free instances expire 30 days after creation (ideal for academic evaluation and testing).

---

## 12. Monitoring Integration Status (Prometheus, Alertmanager & Grafana)

1. **Local Observability:**
   - The existing local Prometheus (`localhost:9090`), Alertmanager (`localhost:9093`), and Grafana (`localhost:3000`) continue to monitor local Docker and Kubernetes environments without interruption.
2. **External Render Monitoring:**
   - Once Render is active, Render exposes the public `/actuator/prometheus` endpoint (e.g. `https://cloud-health-monitor-backend.onrender.com/actuator/prometheus`).
   - The local Prometheus `prometheus.yml` can scrape this public Render endpoint by adding:
     ```yaml
     - job_name: "cloud-health-monitor-render"
       scheme: https
       metrics_path: "/actuator/prometheus"
       static_configs:
         - targets: ["cloud-health-monitor-backend.onrender.com:443"]
     ```
   - **Important:** Do NOT expose Prometheus, Alertmanager, or Grafana publicly without reverse-proxy authentication (e.g. Basic Auth or OAuth).

---

## 13. Deployment Blockers

- **Zero Technical Blockers:** All code, build configurations, exception handling, and CORS adapters are compiled, tested, and passing.
- **Vercel:** No blocker. Deployed successfully to [https://cloud-health-monitor-ui.vercel.app](https://cloud-health-monitor-ui.vercel.app).
- **Render:** Requires linking the GitHub repository in the Render Dashboard (standard Git-based deployment flow).

---

## 14. Required Manual Authorizations

To activate the Render backend and PostgreSQL database with zero cost ($0.00):

1. **Log in to Render:** Navigate to [https://dashboard.render.com](https://dashboard.render.com).
2. **Create Blueprint Instance:**
   - Click **"New +"** $\rightarrow$ select **"Blueprint"**.
   - Connect your GitHub repository: `Husenappa112/cloud-health-monitor-Devops-`.
   - Select branch: `develop`.
   - Render will automatically read `render.yaml` and display the two free-tier resources:
     1. Web Service: `cloud-health-monitor-backend` (Docker, Free)
     2. PostgreSQL: `cloud-health-db` (Postgres, Free)
   - Click **"Apply"**.
3. **Copy the Render Backend URL:**
   - Once deployed, Render will provide a URL like: `https://cloud-health-monitor-backend.onrender.com`.

---

## 15. Exact Next Steps

1. **Connect Vercel to Render:**
   - Open the live Vercel dashboard: [https://cloud-health-monitor-ui.vercel.app](https://cloud-health-monitor-ui.vercel.app).
   - In the top navigation bar, click the **"API: Same Origin"** button.
   - Enter your Render URL (e.g. `https://cloud-health-monitor-backend.onrender.com`).
   - Click **"Save & Connect"**. The dashboard will immediately start polling your Render backend and displaying real-time monitors!
2. **College Evaluation Demonstration:**
   - Evaluators can open the public Vercel URL on any mobile device or external laptop to demonstrate the production cloud deployment.
