# Cloud Health Monitor — REST API Documentation

This document provides complete, production-grade reference documentation for all REST API endpoints provided by the **Cloud Health Monitor Platform**.

---

## 1. Base URL & Common Conventions

- **Default Local Port:** `8081`
- **Kubernetes NodePort:** `30081` (e.g. `http://localhost:30081`)
- **Content-Type:** `application/json`
- **Error Response Standard Format:** Uniform `ApiError` envelope.

### Uniform Error Response Format
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "URL resolves to a restricted private or internal IP address: 127.0.0.1",
  "timestamp": "2026-11-25T15:20:00.123456Z"
}
```

---

## 2. URL Monitor Endpoints (`/monitors`)

### 2.1 Register a New URL Monitor
Creates and schedules an automated health monitor for a public HTTP/HTTPS URL. Performs immediate initial health check and SSRF validation.

- **Method:** `POST`
- **Path:** `/monitors`
- **Status Codes:**
  - `201 Created`: Monitor created and scheduled.
  - `400 Bad Request`: Validation failure (empty name/URL or SSRF violation).
  - `409 Conflict`: Monitor with this name already exists.

#### Request Body
```json
{
  "name": "Google Public",
  "url": "https://google.com",
  "monitoringInterval": 60
}
```

#### Curl Example
```bash
curl -X POST http://localhost:8081/monitors \
  -H "Content-Type: application/json" \
  -d '{"name":"Google","url":"https://google.com","monitoringInterval":60}'
```

#### Sample Response (`201 Created`)
```json
{
  "id": 1,
  "name": "Google",
  "url": "https://google.com",
  "monitoringInterval": 60,
  "enabled": true,
  "currentStatus": "UP",
  "lastCheckedAt": "2026-11-25T15:20:05.123Z",
  "lastResponseTimeMs": 142,
  "lastStatusCode": 200,
  "sslDaysRemaining": 84,
  "sslIssuer": "CN=GTS CA 1C3",
  "dnsLookupTimeMs": 14,
  "errorMessage": null,
  "createdAt": "2026-11-25T15:20:04.981Z",
  "updatedAt": "2026-11-25T15:20:05.123Z"
}
```

---

### 2.2 List All Monitored Services
Retrieves a list of all configured URL monitors along with their live operational status.

- **Method:** `GET`
- **Path:** `/monitors`
- **Status Codes:** `200 OK`

#### Curl Example
```bash
curl -s http://localhost:8081/monitors
```

---

### 2.3 Get Single Monitor by ID
- **Method:** `GET`
- **Path:** `/monitors/{id}`
- **Status Codes:**
  - `200 OK`: Found.
  - `404 Not Found`: Monitor does not exist.

---

### 2.4 Update Monitor Settings
Updates name, target URL, check interval, or enabled status.

- **Method:** `PUT`
- **Path:** `/monitors/{id}`
- **Status Codes:** `200 OK`, `400 Bad Request`, `404 Not Found`

#### Request Body
```json
{
  "name": "Google Production",
  "url": "https://google.com",
  "monitoringInterval": 30,
  "enabled": true
}
```

---

### 2.5 Delete Monitor
Removes a monitor from scheduling and deletes its record.

- **Method:** `DELETE`
- **Path:** `/monitors/{id}`
- **Status Codes:** `204 No Content`, `404 Not Found`

---

### 2.6 Trigger Immediate Force Recheck
Forces an out-of-band immediate health check for a given monitor.

- **Method:** `POST`
- **Path:** `/monitors/{id}/check`
- **Status Codes:** `200 OK`, `404 Not Found`

---

### 2.7 Historical Health Check Log
Returns the 50 most recent health check logs for a specific monitor.

- **Method:** `GET`
- **Path:** `/monitors/{id}/history`
- **Status Codes:** `200 OK`, `404 Not Found`

#### Sample Response
```json
[
  {
    "id": 12,
    "serviceId": 1,
    "serviceName": "Google",
    "status": "UP",
    "httpStatusCode": 200,
    "responseTimeMs": 138,
    "checkedAt": "2026-11-25T15:21:05.000Z",
    "errorMessage": null,
    "dnsLookupTimeMs": 12,
    "sslDaysRemaining": 84
  }
]
```

---

### 2.8 Monitor Performance & Uptime Statistics
Returns calculated uptime percentages over 24-hour, 7-day, and 30-day windows.

- **Method:** `GET`
- **Path:** `/monitors/{id}/statistics`
- **Status Codes:** `200 OK`

#### Sample Response
```json
{
  "serviceId": 1,
  "uptime24h": 100.0,
  "uptime7d": 99.98,
  "uptime30d": 99.95,
  "avgResponseTime24h": 145.2,
  "minResponseTime24h": 112,
  "maxResponseTime24h": 320,
  "totalChecks24h": 1440,
  "openIncidents": 0,
  "totalIncidents": 1
}
```

---

### 2.9 System Overview Statistics
Returns platform-wide aggregate counts and overall system uptime.

- **Method:** `GET`
- **Path:** `/monitors/overview`
- **Status Codes:** `200 OK`

#### Sample Response
```json
{
  "totalMonitors": 4,
  "upCount": 3,
  "downCount": 1,
  "degradedCount": 0,
  "unknownCount": 0,
  "overallUptimePercentage": 75.0,
  "openIncidents": 1,
  "resolvedIncidents": 3,
  "totalChecks": 1250
}
```

---

## 3. Incident Lifecycle Endpoints (`/incidents`)

### 3.1 List Incidents
- **Method:** `GET`
- **Path:** `/incidents` or `/incidents?status=OPEN`
- **Status Codes:** `200 OK`

#### Sample Response
```json
[
  {
    "id": 4,
    "serviceId": 2,
    "serviceName": "Internal Payment Gateway",
    "incidentType": "TIMEOUT",
    "status": "OPEN",
    "startedAt": "2026-11-25T15:10:00Z",
    "resolvedAt": null,
    "durationMs": null,
    "rootCause": "Connection timed out (> 5000ms)"
  }
]
```

---

### 3.2 Manually Resolve an Incident
- **Method:** `POST`
- **Path:** `/incidents/{id}/resolve`
- **Request Body (Optional):**
  ```json
  {
    "note": "Resolved after upstream CDN failover."
  }
  ```
- **Status Codes:** `200 OK`, `404 Not Found`

---

## 4. Legacy Endpoints (Backward Compatible)

| Endpoint | Method | Description |
|---|---|---|
| `/health` | `GET` | Application health ping: `{"status":"UP","application":"Cloud Health Monitor"}` |
| `/services` | `GET` | Lists registered services from in-memory registry |
| `/services` | `POST` | Registers a service in registry |
| `/services/{name}` | `GET` | Gets single service |
| `/services/{name}` | `DELETE` | Removes service from registry |
| `/services/{name}/health`| `GET`/`POST` | Executes real check on named service |
| `/metrics` | `GET` | Aggregated runtime metrics |

---

## 5. Prometheus Metrics (`/actuator/prometheus`)

Metrics exported by Micrometer for Prometheus:

| Metric Name | Type | Labels | Description |
|---|---|---|---|
| `monitored_services_total` | Gauge | — | Total registered monitors |
| `monitored_services_up_count` | Gauge | — | Total monitors in UP status |
| `monitored_services_down_count` | Gauge | — | Total monitors in DOWN status |
| `monitored_services_degraded_count`| Gauge | — | Total monitors in DEGRADED status |
| `monitored_service_up` | Gauge | `service` | 1.0 = UP, 0.5 = DEGRADED, 0.0 = DOWN |
| `monitored_service_response_time_seconds` | Gauge | `service` | Observed response latency in seconds |
| `monitored_service_ssl_expiry_days` | Gauge | `service` | Days until certificate expires |
| `monitored_service_dns_lookup_seconds` | Gauge | `service` | DNS resolution latency in seconds |
| `monitored_service_checks_total` | Counter | `service`, `status` | Cumulative check count |
