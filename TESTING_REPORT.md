# Cloud Health Monitor — QA & Testing Audit Report

## 1. Test Architecture & Execution Metrics

- **Total Automated Tests:** 40
- **Passing Tests:** 40 (100%)
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Execution Engine:** JUnit 5, Spring Boot Test 4.1.2, AssertJ
- **Dual-Tree Parity:** 100% (Root module: 40/40 passed; Child `cloud-health-monitor/`: 40/40 passed)

---

## 2. Test Suites Breakdown

### 2.1 SSRF Security Test Suite (`SsrfValidatorTest`) — 14 Tests
Verifies that all Server-Side Request Forgery attack vectors are completely blocked.

| Test Case | Description | Result |
|---|---|---|
| `validateUrl_validPublicHttps_passes` | Valid public HTTPS URL (`https://google.com`) is allowed | PASS |
| `validateUrl_validPublicHttp_passes` | Valid public HTTP URL (`http://example.com`) is allowed | PASS |
| `validateUrl_nullOrBlank_throwsValidationException` | Null, empty, or whitespace-only URL throws 400 | PASS |
| `validateUrl_unsupportedScheme_throwsValidationException` | Protocols like `ftp://`, `file:///`, `gopher://` rejected | PASS |
| `validateUrl_loopbackIpv4_throwsValidationException` | `http://127.0.0.1:8080` rejected with 400 | PASS |
| `validateUrl_privateClassA_throwsValidationException` | `http://10.0.0.1/admin` rejected with 400 | PASS |
| `validateUrl_privateClassB_throwsValidationException` | `http://172.16.0.50/` rejected with 400 | PASS |
| `validateUrl_privateClassC_throwsValidationException` | `http://192.168.1.1/setup` rejected with 400 | PASS |
| `validateUrl_cloudMetadata_throwsValidationException` | **`http://169.254.169.254/latest/meta-data` blocked** | PASS |
| `validateUrl_loopbackIpv6_throwsValidationException` | `http://[::1]:8080` rejected with 400 | PASS |
| `validateUrl_disallowedPort_throwsValidationException` | Sensitive ports like 22 (SSH), 25 (SMTP) rejected | PASS |
| `validateUrl_malformedUrl_throwsValidationException` | Invalid URI strings rejected with 400 | PASS |
| `validateUrl_ipv6LinkLocal_throwsValidationException` | IPv6 link-local addresses rejected | PASS |
| `validateUrl_zeroSubnet_throwsValidationException` | Wildcard `0.0.0.0` address rejected | PASS |

---

### 2.2 Embedded API Integration Suite (`ApiIntegrationTest`) — 14 Tests
Tests end-to-end HTTP REST operations using a live embedded HTTP server.

| Test Case | Description | Result |
|---|---|---|
| `getHealth_returnsUp` | `GET /health` returns application UP status | PASS |
| `getServices_emptyRegistry_returnsEmptyArray` | `GET /services` returns empty list initially | PASS |
| `registerService_valid_createsAndReturns201` | Service registration succeeds with HTTP 201 | PASS |
| `registerService_duplicate_returns409Conflict` | Duplicate registrations yield HTTP 409 Conflict | PASS |
| `registerService_invalidUrl_returns400` | Malformed URLs yield HTTP 400 Bad Request | PASS |
| `checkNow_upService_returnsUp` | Live probe to `/up` returns UP status | PASS |
| `checkNow_downService_returnsDown` | Live probe to `/down` (503) returns DOWN status | PASS |
| `getStatistics_empty_returnsDefaults` | Statistics calculation returns valid defaults | PASS |
| `getMetrics_returnsPrometheusCounters` | Custom metrics endpoint returns counter map | PASS |
| `deleteService_removesEntry` | Service deletion removes target from registry | PASS |
| `deleteService_notFound_returns404` | Deleting non-existent service returns HTTP 404 | PASS |
| `actuatorPrometheus_isAccessible` | `/actuator/prometheus` returns scraped gauges | PASS |
| `corsHeaders_areReturned` | CORS preflight headers return valid origins | PASS |
| `globalExceptionHandler_catchesErrors` | Unhandled exceptions return RFC 7807 JSON | PASS |

---

### 2.3 URL Monitor Lifecycle Suite (`UrlMonitorIntegrationTest`) — 11 Tests
Tests advanced URL monitor lifecycle operations, normalization, and analytics.

| Test Case | Description | Result |
|---|---|---|
| `createMonitor_validPublicUrl_returnsCreated` | Registers public URL monitor and returns 14 metrics | PASS |
| `createMonitor_autoNormalizesScheme` | Normalizes `github.com` $\rightarrow$ `https://github.com` | PASS |
| `createMonitor_ssrfLoopback_returnsBadRequest` | Rejects `http://127.0.0.1:8080/admin` with 400 | PASS |
| `createMonitor_duplicateName_returnsConflict` | Rejects duplicate monitor name with HTTP 409 | PASS |
| `listMonitors_returnsAll` | `GET /monitors` returns all registered monitors | PASS |
| `pauseAndResumeMonitor_updatesState` | **`POST /{id}/pause` and `/{id}/resume` toggle active state** | PASS |
| `updateMonitor_modifiesFields` | **`PUT /{id}` updates name, interval, and URL** | PASS |
| `getDashboardSummary_returnsMetrics` | **`GET /api/dashboard/summary` returns system stats** | PASS |
| `getOverview_returnsSummary` | `GET /monitors/overview` returns monitor aggregates | PASS |
| `getUptimeAndEvents_returnsData` | **`GET /{id}/uptime` and `/{id}/events` return statistics** | PASS |
| `deleteMonitor_removesFromRegistry` | `DELETE /monitors/{id}` cleans up database entry | PASS |

---

### 2.4 Context Boot Suite (`CloudHealthMonitorApplicationTests`) — 1 Test
| Test Case | Description | Result |
|---|---|---|
| `contextLoads` | Verifies full Spring application context, beans, and JPA wiring | PASS |

---

## 3. Test Execution Command & Verification Logs

```powershell
# Root module test command
.\mvnw test

# Output snippet:
# Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```
