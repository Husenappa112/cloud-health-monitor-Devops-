# Cloud Health Monitor — Security Architecture & Threat Model

## 1. Security Overview

Because **Cloud Health Monitor** allows users to submit external URLs that the backend system subsequently resolves and contacts over HTTP/HTTPS, **Server-Side Request Forgery (SSRF)** represents the primary attack surface. Without rigorous defenses, an attacker could use the monitoring engine as an internal proxy to scan private infrastructure, steal cloud instance credentials, or exploit internal microservices.

---

## 2. Server-Side Request Forgery (SSRF) Defense

### 2.1 Defense-in-Depth Layered Architecture

```
User Input URL
     │
     ▼
[ Stage 1: Protocol & Scheme Check ] ──(Invalid scheme)──► 400 Bad Request
     │ (Only http / https allowed)
     ▼
[ Stage 2: Port Whitelist Enforcement ] ──(Blocked port)──► 400 Bad Request
     │ (Ports 80, 443, 8080, 8443, 8000, 3000, 5000, 9000 allowed)
     ▼
[ Stage 3: DNS Hostname Resolution ] ──(Unresolvable)──► Mark DOWN (DNS Failed)
     │ (Resolves host to all InetAddresses)
     ▼
[ Stage 4: IP Address & Subnet Filter ] ──(Private / Loopback / Metadata)──► 400 Bad Request
     │
     ▼
[ Stage 5: HTTP Client Execution (Redirect.NEVER) ]
     │
     ├── (Status 2xx / 4xx / 5xx) ──► Record Metrics & Exit
     └── (Status 3xx Redirect) ──► Re-enter Stage 1 for Redirect Target URL!
```

---

## 3. Blacklisted IP Subnets & Cloud Metadata Protection

The `SsrfValidator` resolves hostnames to raw `InetAddress` objects and checks them against the following blocked CIDR blocks:

| Subnet Range | Classification | Threat Mitigated |
|---|---|---|
| `127.0.0.0/8` | IPv4 Loopback | Prevents access to local services (`127.0.0.1:8081`, DB ports) |
| `10.0.0.0/8` | RFC 1918 Private Class A | Prevents pivoting into private enterprise / VPC networks |
| `172.16.0.0/12` | RFC 1918 Private Class B | Prevents Docker bridge & Kubernetes pod network attacks |
| `192.168.0.0/16`| RFC 1918 Private Class C | Prevents home/office router & local LAN service scanning |
| `169.254.0.0/16`| Link-Local / Metadata | **Blocks `169.254.169.254` (AWS, GCP, Azure, OpenStack IMDS)** |
| `0.0.0.0/8` | Broadcast / Wildcard | Prevents wildcard binding exploit |
| `::1` | IPv6 Loopback | Blocks localhost via IPv6 |
| `fc00::/7` | IPv6 Unique Local Address | Blocks private IPv6 networks |
| `fe80::/10` | IPv6 Link-Local Address | Blocks IPv6 metadata and link endpoints |

---

## 4. Open Redirect Traversal Protection

Naive HTTP implementations follow redirects automatically (`HttpClient.Redirect.ALWAYS` or `curl -L`). Attackers exploit this via open redirects on public servers:
1. Target: `https://public-site.com/redirect?to=http://169.254.169.254/latest/meta-data/`
2. Naive client validates `public-site.com` (allowed) and sends request.
3. Server returns `302 Found` with `Location: http://169.254.169.254/...`.
4. Client follows redirect internally without re-validating, exposing credentials!

### Cloud Health Monitor Solution:
- Configures `HttpClient.Redirect.NEVER`.
- The engine intercepts the `Location` header in the HTTP 3xx response.
- Resolves the relative/absolute destination URL.
- Submits the new URL into `SsrfValidator.validateUrl(nextUri)` before sending another request.
- If the redirect destination points to a private/metadata IP, the request is immediately aborted with `SSRF_REDIRECT_BLOCKED`.

---

## 5. Cross-Origin Resource Sharing (CORS) Security

Configured in `WebCorsConfig.java`:
- **Allowed Origins:** `https://cloud-health-monitor-ui.vercel.app`, `http://localhost:3000`, `http://localhost:8081`.
- **Allowed Methods:** `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`.
- **Allowed Headers:** `Content-Type`, `Authorization`, `X-Requested-With`, `Accept`.
- **Pre-flight Caching:** 3600 seconds (`maxAge(3600)`).

---

## 6. TLS / SSL Inspection Security

The `SslCertificateInspector` uses standard Java Secure Socket Extensions (`SSLSocketFactory.getDefault()`).
- Direct X.509 peer certificate inspection without skipping certificate validation.
- Flags expired certificates, self-signed certificates, and expiring certificates within a 7-day degraded warning window.
- Records Certificate Authority Common Name (`CN=`) issuer for transparency.
