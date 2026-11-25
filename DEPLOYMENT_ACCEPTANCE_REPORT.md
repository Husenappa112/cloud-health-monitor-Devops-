# Cloud Health Monitor — Final Deployment Acceptance & Limitations Report

**Project:** Cloud Health Monitor  
**Repository:** `Husenappa112/cloud-health-monitor-Devops-`  
**Branch:** `develop`  
**Commit Verified:** `ed3d109509a0d2daaaeadcf4b979b60b66607c00`  
**Evaluator:** Senior DevOps Engineer, Release Engineer, and Technical Auditor  
**Date:** November 25, 2026  

---

## 1. Acceptance Checklist & Verification Matrix

| Subsystem / Requirement | Criteria | Verified Live Result | Status |
| :--- | :--- | :--- | :--- |
| **Spring Boot Application** | Java 21, Spring Boot 4.1.2, `/health`, `/monitors`, `/incidents`, `/actuator/prometheus` | HTTP 200 OK on Port 8081 & NodePort 30081 (`{"status":"UP","application":"Cloud Health Monitor"}`) | **ACCEPTED** |
| **Unit & Integration Tests**| Comprehensive test suite | 35/35 tests passing cleanly across root and module test runners | **ACCEPTED** |
| **Docker Container Security**| Non-root user, multi-stage build | `appuser:100:101`, read-only root enforced, emptyDir `/tmp` scratch space | **ACCEPTED** |
| **Kubernetes Deployment** | 3 replicas, zero-downtime rolling update, health probes | 3/3 pods `Running (1/1)` on `docker-desktop`, image `cloud-health-monitor:v2` | **ACCEPTED** |
| **Kubernetes Service** | NodePort 30081 exposed, session affinity | NodePort 30081 active, `sessionAffinity: ClientIP` (3 hours) | **ACCEPTED** |
| **Prometheus Monitoring** | Active scrapes every 15s | 2 targets UP (`cloud-health-monitor` and `cloud-health-monitor-kubernetes`) | **ACCEPTED** |
| **Alert Rules** | Custom alert rules loaded and evaluating | 6 production URL rules active (`MonitoredServiceDown`, `MonitoredServiceLatencyHigh`, `MonitoredServiceDegraded`, `SslCertificateExpiringSoon`, `SslCertificateExpired`, `DnsLookupLatencyHigh`) | **ACCEPTED** |
| **Alertmanager Notifications**| Routing and SMTP configuration | Gmail receiver configured (`smtp.gmail.com:587`), version 0.35.0 active | **ACCEPTED** |
| **Grafana Observability** | Accessible dashboards receiving data | Dashboards active and connected to Prometheus datasource | **ACCEPTED** |
| **Jenkins Pipeline** | 8-stage automated build & deploy | Build #12 completed with `SUCCESS` on branch `develop` with auto-rollback | **ACCEPTED** |
| **GitHub Actions CI** | Cloud verification on push | Run #45591464696 on commit `ed3d109` completed with `SUCCESS` across all 3 jobs | **ACCEPTED** |
| **Ansible Automation** | Deployment playbook and role | `ansible/deploy.yml` with role `cloud_health_monitor` and health retries | **ACCEPTED** |
| **Locust Load Testing** | User traffic simulation script | `locustfile.py` with weighted distribution across `/health` and `/actuator/prometheus` | **ACCEPTED** |
| **Database Persistence** | Multi-profile persistence | H2 persistent disk file default + PostgreSQL profile via `application-postgres.properties` | **ACCEPTED** |
| **SSRF & Web Security** | Loopback, RFC 1918, cloud metadata, IPv6 ULA/link-local, 3xx redirect re-validation | All private/loopback/cloud metadata/redirect vectors blocked with HTTP 400 | **ACCEPTED** |

---

## 2. Pipeline Execution Reality & Proven Boundaries

To ensure complete engineering transparency during evaluations and technical interviews, the following distinction must be recognized:

### Actually Executed & Verified Pipelines
1. **Jenkins Local Pipeline:** Stages 1 through 8 were fully executed on the local cluster, generating Build #12 (`cloud-health-monitor:jenkins-12`), rolling out to Kubernetes, validating pod readiness, and passing health checks.
2. **GitHub Actions CI Workflow (`ci.yml`):** Fully executed in the cloud on commit `ed3d109` (Run #45591464696), passing build, unit tests, Checkstyle, and Docker build dry-run.

### Designed but Intentionally Un-dispatched Pipelines
1. **GitHub Actions CD (`cd.yml`):** Configured to trigger only on push to `main` or manual trigger. Because development adheres strictly to GitFlow on branch `develop`, CD was not triggered.
2. **GitHub Actions Release (`release.yml`):** Configured to trigger only on version tags (`v*.*.*`). In accordance with non-destructive release boundaries, no tag was pushed.
3. **GitHub Actions PR Checks (`pr-checks.yml`):** Configured to trigger on Pull Request events. No open pull request exists for `develop`.

---

## 3. Genuine Architectural & Environment Limitations

A senior engineer must be able to honestly address the operational limitations of this setup:

### 1. Single-Node Local Host Boundary
- **Limitation:** The Kubernetes cluster runs inside Docker Desktop on a single developer workstation (Windows 11 with WSL 2).
- **Implication:** While 3 replicas exist, all 3 run on the same physical host and Docker daemon (`docker-desktop`). A host hardware failure or host reboot halts all replicas simultaneously.
- **Production Path:** Migrate the manifest to managed multi-zone cloud Kubernetes (EKS, GKE, or AKS) across multiple worker nodes and availability zones.

### 2. In-Memory Service Registry
- **Limitation:** The microservices registry in the application stores registered service metadata in memory.
- **Implication:** Because there are 3 separate replica pods, each pod maintains its own in-memory state. Under round-robin load balancing, a service registered against Pod 1 would not be immediately visible on Pod 2 or Pod 3 unless requests maintain session affinity (`sessionAffinity: ClientIP` was configured on `service.yaml` as an interim mitigation).
- **Production Path:** Introduce an external shared persistence layer (e.g., Redis cluster, PostgreSQL, or Consul/Eureka service mesh) so all replicas share an identical registry state.

### 3. Storage Persistence Stratification
- **Grafana:** Persisted via PVC (`grafana-storage`, 2Gi) backed by the local host storage-provisioner.
- **Application Pods:** Stateless by design, utilizing ephemeral `emptyDir` scratch space at `/tmp`.
- **Prometheus & Alertmanager:** Running in standalone Docker containers using local Docker volumes. A container recreation without volume preservation would reset Prometheus historical metrics.
- **Production Path:** Deploy Prometheus and Alertmanager inside Kubernetes using the Prometheus Operator (`kube-prometheus-stack`) with StatefulSets and cloud PersistentVolumes (e.g., AWS EBS `gp3` or GKE Persistent Disk).

### 4. Networking & External Access
- **Limitation:** External ingress relies on Kubernetes `NodePort` (`30081`).
- **Implication:** `NodePort` opens a high port directly on the host node, which is suitable for local development and demonstration, but not recommended for public internet production traffic.
- **Production Path:** Replace `NodePort` with an `Ingress` controller (e.g., Ingress-NGINX or Traefik) fronted by a cloud LoadBalancer (AWS ALB or Google Cloud HTTPS Load Balancer) with automated TLS certificates (Cert-Manager).

### 5. Docker Desktop CRI Exec Transport Limitation
- **Limitation:** Running `kubectl exec` directly on Docker Desktop Windows v1.36+ CRI shim encounters a known protocol transport mismatch (`http: server gave HTTP response to HTTPS client`).
- **Implication:** `kubectl exec` into pods fails in this specific local environment.
- **Remediation & Proof:** Authoritative inspection was verified via `docker exec` against the underlying container IDs, proving runtime UID `100`, read-only root, and emptyDir write permissions. This limitation does not exist on production Linux nodes (EKS/GKE).

---

## 4. Final Acceptance Verdict

- **Overall Deployment Acceptance:** **APPROVED & FULLY OPERATIONAL**
- **Readiness for College Demonstration:** **100% READY**
- **Readiness for Technical Interview Explanation:** **100% READY**
- **Operational Documentation:** Delivered (`DEPLOYMENT_ARCHITECTURE.md`, `OPERATIONS_RUNBOOK.md`, `PROJECT_DEMO_GUIDE.md`).
