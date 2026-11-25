# Cloud Health Monitor — Security Architecture & Hardening Report

**Project:** Cloud Health Monitor Platform  
**Target Environment:** Docker, Kubernetes (docker-desktop), Linux (WSL/Production)  
**Security Standard:** Kubernetes Restricted Pod Security Standard & OWASP Top 10

---

## 1. Executive Summary

The Cloud Health Monitor platform implements multi-layer defense-in-depth across the application runtime, HTTP request engine, container packaging, and Kubernetes deployment manifest.

| Security Domain | Protection Mechanism | Status |
|---|---|---|
| **SSRF Prevention** | Protocol restriction, loopback & private IP blocking, cloud metadata blocking | ✅ Implemented & Tested |
| **Container Hardening** | Non-root UID 100, read-only root filesystem, drop ALL capabilities | ✅ Enforced in K8s |
| **Resource Isolation** | CPU/Memory limits, bounded thread pools, timeouts | ✅ Enforced |
| **SSL/TLS Integrity** | Expiration monitoring, hostname verification, TLS handshake validation | ✅ Implemented |
| **Credential Hygiene** | No plain-text secrets in git, secret volume mounts for alert notifications | ✅ Enforced |

---

## 2. Server-Side Request Forgery (SSRF) Protection

Because the platform permits users to register arbitrary URLs for availability testing, strict Server-Side Request Forgery defenses are mandated to prevent internal network scanning, lateral traversal, and cloud metadata credential theft.

### 2.1 Enforced Rules

1. **Protocol Restriction:** Only `http` and `https` schemes are permitted. Schemes like `file://`, `ftp://`, `gopher://`, or `ldap://` are rejected at the input boundary.
2. **Loopback Protection:** Rejects `127.0.0.0/8`, `localhost`, and `::1`.
3. **Private Subnet Blocking:** Rejects RFC 1918 private subnets:
   - `10.0.0.0/8`
   - `172.16.0.0/12`
   - `192.168.0.0/16`
4. **Cloud Metadata Protection:** Explicitly blocks link-local address `169.254.169.254` (the AWS/GCP/Azure instance metadata service), preventing theft of IAM instance profile credentials.
5. **DNS Re-Resolution:** Hostnames are resolved to IP addresses at validation time to verify that internal IPs are not masked by custom DNS domains.

---

## 3. Kubernetes Container Security Context

The deployment manifest `k8s/deployment.yaml` complies with the Kubernetes **Restricted Pod Security Standard**:

```yaml
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 100
    runAsGroup: 101
    fsGroup: 101
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: cloud-health-monitor
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities:
          drop:
            - ALL
      volumeMounts:
        - name: tmp-volume
          mountPath: /tmp
  volumes:
    - name: tmp-volume
      emptyDir: {}
```

### 3.1 Security Verification Results

| Control | Value | Operational Impact |
|---|---|---|
| `runAsUser` | `100` (guest) | Application processes run without root privileges. |
| `readOnlyRootFilesystem` | `true` | Compromised containers cannot modify system binaries or install malware. |
| `allowPrivilegeEscalation` | `false` | Prevents `setuid` binaries from escalating privileges. |
| `capabilities.drop` | `["ALL"]` | Strips all Linux root capabilities (`CAP_NET_RAW`, `CAP_SYS_ADMIN`, etc.). |
| `/tmp` emptyDir | Mounted rw | Spring Boot and Tomcat can create temporary PID/socket files without rootfs writes. |

---

## 4. Threat Modeling & Mitigation Summary

| Threat | Impact | Mitigation in Cloud Health Monitor |
|---|---|---|
| Attacker supplies `http://169.254.169.254/latest/meta-data` | Cloud credential theft | Blocked by `SsrfValidator.isRestrictedAddress()` |
| Attacker supplies `http://localhost:8080/internal-admin` | Internal service exploit | Blocked by `SsrfValidator` loopback filter |
| Slowloris / Hung target URL | Thread exhaustion DoS | `monitor.request-timeout-ms=5000` + bounded `ExecutorService(10)` |
| Malicious container breakout | Node root access | `runAsNonRoot`, `readOnlyRootFilesystem`, `capabilities.drop: [ALL]` |
| SSL certificate expires silently | User service downtime | Proactive SSL days calculation & Prometheus alert rule `< 7d` |

---

## 5. Security Testing Evidence

Automated JUnit tests in `SsrfValidatorTest` and `UrlMonitorIntegrationTest`:
- `SsrfValidatorTest`: 11 test cases verifying loopback, private ranges, cloud metadata, and protocol restrictions.
- `UrlMonitorIntegrationTest`: Verifies HTTP 400 Bad Request return on malicious URL submission.
- All tests pass with zero failures.
