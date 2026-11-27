# Cloud Health Monitor — REST API Documentation

## Base URLs
- **Production (Render Backend):** `https://cloud-health-monitor-devops.onrender.com`
- **Local Development:** `http://localhost:8081`

All endpoints are dual-mapped to both `/api/monitors` and `/monitors` for universal API client compatibility.

---

## 1. Monitor Management Endpoints

### 1.1 List All Monitors
- **Method:** `GET`
- **Path:** `/api/monitors` (or `/monitors`)
- **Description:** Returns all registered URL monitors with their current health metrics.
- **Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Google Search",
    "url": "https://google.com",
    "monitoringInterval": 60,
    "enabled": true,
    "currentStatus": "UP",
    "lastCheckedAt": "2026-11-27T12:35:10Z",
    "lastResponseTimeMs": 48,
    "lastStatusCode": 200,
    "dnsStatus": "SUCCESS",
    "dnsLookupTimeMs": 8,
    "connectionStatus": "CONNECTED",
    "sslValid": true,
    "sslDaysRemaining": 78,
    "sslExpiryDate": "2027-02-14T00:00:00Z",
    "sslIssuer": "GTS CA 1C3",
    "redirectCount": 0,
    "finalUrl": "https://google.com",
    "lastSuccessfulCheckAt": "2026-11-27T12:35:10Z",
    "lastFailedCheckAt": null,
    "consecutiveFailures": 0,
    "uptimePercentage": 100.0,
    "downtimeDurationSeconds": 0,
    "errorMessage": null,
    "createdAt": "2026-11-27T10:00:00Z",
    "updatedAt": "2026-11-27T12:35:10Z"
  }
]
```

---

### 1.2 Create New Monitor
- **Method:** `POST`
- **Path:** `/api/monitors` (or `/monitors`)
- **Description:** Registers a new URL monitor and triggers an immediate health check. Missing protocol schemes (e.g. `google.com`) are automatically normalized to `https://google.com`.
- **Request Body:**
```json
{
  "name": "GitHub Status",
  "url": "https://www.githubstatus.com",
  "monitoringInterval": 60
}
```
- **Response (201 Created):** Returns the registered monitor entity.
- **Error Responses:**
  - `400 Bad Request`: SSRF violation, invalid URL syntax, or missing fields.
  - `409 Conflict`: Monitor name or normalized URL already exists.

---

### 1.3 Get Single Monitor
- **Method:** `GET`
- **Path:** `/api/monitors/{id}` (or `/monitors/{id}`)
- **Response (200 OK):** Returns single monitor details.
- **Error (404 Not Found):** If ID does not exist.

---

### 1.4 Update Monitor
- **Method:** `PUT`
- **Path:** `/api/monitors/{id}` (or `/monitors/{id}`)
- **Request Body:**
```json
{
  "name": "Updated Service Name",
  "url": "https://example.com/api",
  "monitoringInterval": 120,
  "enabled": true
}
```
- **Response (200 OK):** Returns the updated monitor entity.

---

### 1.5 Pause Monitor
- **Method:** `POST`
- **Path:** `/api/monitors/{id}/pause` (or `/monitors/{id}/pause`)
- **Description:** Pauses scheduled health checks by setting `enabled = false`.
- **Response (200 OK):** Returns monitor entity with `"enabled": false`.

---

### 1.6 Resume Monitor
- **Method:** `POST`
- **Path:** `/api/monitors/{id}/resume` (or `/monitors/{id}/resume`)
- **Description:** Resumes scheduled health checks by setting `enabled = true` and triggers an immediate on-demand check.
- **Response (200 OK):** Returns monitor entity with `"enabled": true`.

---

### 1.7 Execute Immediate Check (Force Recheck)
- **Method:** `POST`
- **Path:** `/api/monitors/{id}/check` (or `/monitors/{id}/check`)
- **Description:** Triggers an immediate health check regardless of the scheduler interval.
- **Response (200 OK):**
```json
{
  "id": 105,
  "serviceId": 1,
  "serviceName": "Google Search",
  "status": "UP",
  "httpStatusCode": 200,
  "responseTimeMs": 52,
  "checkedAt": "2026-11-27T12:40:00Z",
  "errorMessage": null,
  "dnsStatus": "SUCCESS",
  "dnsLookupTimeMs": 6,
  "connectionStatus": "CONNECTED",
  "sslValid": true,
  "sslDaysRemaining": 78,
  "sslExpiryDate": "2027-02-14T00:00:00Z",
  "redirectCount": 0,
  "finalUrl": "https://google.com"
}
```

---

### 1.8 Delete Monitor
- **Method:** `DELETE`
- **Path:** `/api/monitors/{id}` (or `/monitors/{id}`)
- **Response (204 No Content):** Successfully deleted.

---

## 2. Analytics & Reporting Endpoints

### 2.1 Monitor Historical Checks
- **Method:** `GET`
- **Path:** `/api/monitors/{id}/history` (or `/monitors/{id}/history`)
- **Description:** Returns the last 50 historical check records for the monitor.

---

### 2.2 Uptime Statistics
- **Method:** `GET`
- **Path:** `/api/monitors/{id}/uptime` (or `/api/monitors/{id}/statistics`)
- **Description:** Returns rolling uptime percentages and latency percentiles.
- **Response (200 OK):**
```json
{
  "serviceId": 1,
  "uptime24h": 100.0,
  "uptime7d": 99.98,
  "uptime30d": 99.95,
  "avgResponseTime24h": 54.2,
  "minResponseTime24h": 38,
  "maxResponseTime24h": 112,
  "totalChecks24h": 1440,
  "openIncidents": 0,
  "totalIncidents": 1
}
```

---

### 2.3 Monitor Incidents & Failure Events
- **Method:** `GET`
- **Path:** `/api/monitors/{id}/events` (or `/api/monitors/{id}/incidents`)
- **Description:** Returns outage events and incident records for the monitor.

---

### 2.4 Dashboard Overview Summary
- **Method:** `GET`
- **Path:** `/api/dashboard/summary` (or `/monitors/overview`)
- **Response (200 OK):**
```json
{
  "totalMonitors": 4,
  "upCount": 3,
  "degradedCount": 1,
  "downCount": 0,
  "unknownCount": 0,
  "enabledCount": 4,
  "pausedCount": 0,
  "overallUptimePercentage": 100.0,
  "totalChecks": 2840,
  "openIncidents": 0,
  "resolvedIncidents": 2
}
```

---

## 3. Telemetry & Actuator Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/health` | `GET` | Core application liveness status (`{"status":"UP"}`) |
| `/actuator/health` | `GET` | Spring Boot Actuator component health status |
| `/actuator/prometheus` | `GET` | Prometheus text scrape format with all gauges |
| `/incidents` | `GET` | System-wide incident history |
| `/incidents/{id}/resolve` | `POST` | Manually mark an incident as resolved |
