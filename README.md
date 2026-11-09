# Cloud-Based Application Health Monitoring REST API

> **A production-quality Spring Boot 4 REST API** that monitors the health of registered services in real time.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Problem Statement](#problem-statement)
3. [Architecture](#architecture)
4. [Features](#features)
5. [Technology Stack](#technology-stack)
6. [API Documentation](#api-documentation)
7. [Local Setup](#local-setup)
8. [Maven Commands](#maven-commands)
9. [Testing](#testing)
10. [Git Workflow](#git-workflow)
11. [Jenkins Pipeline](#jenkins-pipeline)
12. [Docker](#docker)
13. [Ansible](#ansible)
14. [Kubernetes](#kubernetes)
15. [Screenshot Checklist](#screenshot-checklist)

---

## Project Overview

Cloud Health Monitor is a RESTful API built with Spring Boot 4 that lets you:

- Register services by name and URL
- Perform real HTTP health checks against registered services
- View aggregated monitoring metrics
- Integrate with CI/CD, containerisation, and orchestration tooling

---

## Problem Statement

Modern microservice architectures consist of dozens to hundreds of independent services. Without centralised health monitoring, teams discover failures only after users report them. This project provides a lightweight health-monitoring API that checks every registered service, records response times, and surfaces aggregated metrics — without a database, without external infrastructure, and without fake data.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    REST Clients / curl                       │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP
┌──────────────────────────▼──────────────────────────────────┐
│               Spring Boot 4 Application (port 8081)         │
│                                                             │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │ Health       │  │ Services         │  │ Metrics      │  │
│  │ Controller   │  │ Controller       │  │ Controller   │  │
│  └──────┬───────┘  └────────┬─────────┘  └──────┬───────┘  │
│         │                   │                    │          │
│         │          ┌────────▼─────────┐          │          │
│         │          │ ServiceRegistry  │◄─────────┘          │
│         │          │ (ConcurrentMap)  │                     │
│         │          └────────┬─────────┘                     │
│         │                   │                               │
│         │          ┌────────▼─────────┐                     │
│         │          │ HealthCheck      │                     │
│         │          │ Service          │── HTTP GET ──► external services
│         │          └──────────────────┘                     │
└─────────────────────────────────────────────────────────────┘
```

**Packages:**

| Package      | Contents                                        |
|--------------|-------------------------------------------------|
| `controller` | HealthController, ServicesController, MetricsController |
| `service`    | ServiceRegistry, HealthCheckService, MetricsService |
| `model`      | ServiceEntry, ServiceResponse, HealthCheckResult, MetricsResponse, ApiError |
| `exception`  | GlobalExceptionHandler, custom exception classes |
| `config`     | Reserved for future cross-cutting configuration |

---

## Features

- ✅ Real HTTP health checks (not simulated) with configurable timeouts
- ✅ Thread-safe in-memory service registry (`ConcurrentHashMap`)
- ✅ URL scheme validation (http/https only)
- ✅ Duplicate detection
- ✅ Response time measurement in milliseconds
- ✅ Aggregated metrics: total services, UP/DOWN/UNKNOWN counts, average response time, uptime
- ✅ Centralised exception handling with uniform JSON error envelopes
- ✅ Full CRUD: register, list, get, delete services
- ✅ 15 automated JUnit integration tests (all passing)
- ✅ Jenkins CI pipeline
- ✅ Multi-stage Docker build with non-root user
- ✅ Ansible role for deployment
- ✅ Kubernetes manifests with liveness/readiness probes

---

## Technology Stack

| Component    | Technology              | Version     |
|--------------|-------------------------|-------------|
| Language     | Java                    | 17 (JDK 21) |
| Framework    | Spring Boot             | 4.1.2       |
| Build        | Apache Maven            | 3.9.9       |
| Testing      | JUnit Jupiter           | 6.x         |
| Assertions   | AssertJ                 | 3.27.x      |
| Container    | Docker (multi-stage)    | latest      |
| Orchestration| Kubernetes / Minikube   | ≥ 1.28      |
| CI/CD        | Jenkins                 | ≥ 2.414     |
| Config Mgmt  | Ansible                 | ≥ 2.12      |

---

## API Documentation

### GET /health

Returns the application status.

```bash
curl http://localhost:8081/health
```

```json
{
  "status": "UP",
  "application": "Cloud Health Monitor"
}
```

---

### GET /services

Returns all registered services.

```bash
curl http://localhost:8081/services
```

```json
[
  {
    "name": "Payment API",
    "url": "https://example.com",
    "status": "UP",
    "httpStatus": 200,
    "responseTimeMs": 42,
    "checkedAt": "2026-11-09T11:00:00Z"
  }
]
```

---

### POST /services

Register a new service.

```bash
curl -X POST http://localhost:8081/services \
     -H "Content-Type: application/json" \
     -d '{"name":"Payment API","url":"https://example.com"}'
```

**Success (201 Created):**
```json
{
  "name": "Payment API",
  "url": "https://example.com",
  "status": "UNKNOWN",
  "httpStatus": null,
  "responseTimeMs": null,
  "checkedAt": null
}
```

**Duplicate (409 Conflict):**
```json
{"status":409,"error":"Conflict","message":"Service already registered: Payment API","timestamp":"..."}
```

**Bad Request (400):**
```json
{"status":400,"error":"Bad Request","message":"Service URL must start with http:// or https://","timestamp":"..."}
```

---

### GET /services/{name}

Get a specific service by name.

```bash
curl http://localhost:8081/services/Payment%20API
```

---

### DELETE /services/{name}

Remove a service from the registry.

```bash
curl -X DELETE http://localhost:8081/services/Payment%20API
# 204 No Content
```

---

### GET /services/{name}/health

Perform a live HTTP health check against the service URL.

```bash
curl http://localhost:8081/services/Payment%20API/health
```

```json
{
  "name": "Payment API",
  "url": "https://example.com",
  "status": "UP",
  "httpStatus": 200,
  "responseTimeMs": 35,
  "checkedAt": "2026-11-09T11:00:00Z"
}
```

---

### POST /services/{name}/health

Force a re-check of a service (same response format as GET above).

---

### GET /metrics

Real monitoring statistics.

```bash
curl http://localhost:8081/metrics
```

```json
{
  "totalServices": 3,
  "upServices": 2,
  "downServices": 1,
  "unknownServices": 0,
  "totalChecksPerformed": 15,
  "averageResponseTimeMs": 48.3,
  "applicationUptimeSeconds": 3600
}
```

---

## Local Setup

### Prerequisites

- Java 17+ (JDK 21 installed)
- Maven (or use the included wrapper `./mvnw`)

### Run the application

```powershell
cd cloud-health-monitor\cloud-health-monitor
.\mvnw.cmd spring-boot:run
```

**On Linux/WSL:**
```bash
cd cloud-health-monitor/cloud-health-monitor
./mvnw spring-boot:run
```

Application starts at **http://localhost:8081**

---

## Maven Commands

```bash
# Clean build
./mvnw clean

# Compile only
./mvnw compile

# Run tests
./mvnw test

# Package JAR
./mvnw package -DskipTests

# Full build + test
./mvnw clean verify

# Run the JAR directly
java -jar target/cloud-health-monitor-0.0.1-SNAPSHOT.jar
```

---

## Testing

```bash
./mvnw clean test
```

**Test coverage:**
| Test | Description |
|------|-------------|
| `getHealth_returnsUp` | Verifies /health returns status UP |
| `getServices_emptyRegistry_returnsEmptyArray` | Empty list on startup |
| `postServices_validRequest_returnsCreated` | 201 on valid registration |
| `postServices_duplicateName_returnsConflict` | 409 on duplicate |
| `postServices_missingName_returnsBadRequest` | 400 on blank name |
| `postServices_invalidUrlScheme_returnsBadRequest` | 400 on ftp:// scheme |
| `getServiceByName_exists_returnsService` | 200 with correct data |
| `getServiceByName_notFound_returns404` | 404 on unknown service |
| `deleteService_exists_returnsNoContent` | 204 and service removed |
| `checkHealth_upService_returnsUp` | Real HTTP 200 → UP |
| `checkHealth_downService_returnsDown` | Real HTTP 503 → DOWN |
| `checkHealth_unknownService_returns404` | 404 on unknown service |
| `getMetrics_returnsRealStats` | Real metrics after a check |
| `getServices_multipleServices_returnsAll` | Lists all services |
| `contextLoads` | Spring context starts |

---

## Git Workflow

```bash
# Inspect status
git status
git log --oneline

# Create develop branch
git checkout -b develop

# Create a feature branch
git checkout -b feature/add-metrics

# Make changes, then commit
git add .
git commit -m "feat: implement GET /metrics endpoint"

# Merge back to develop
git checkout develop
git merge --no-ff feature/add-metrics
git branch -d feature/add-metrics

# Push to GitHub
git push origin develop
```

---

## Jenkins Pipeline

### Required Jenkins Plugins

- Pipeline
- Git
- JUnit (for test reports)
- GitHub Integration (for webhooks)

### JDK & Maven Configuration

In **Manage Jenkins → Tools**:

- **JDK**: Add JDK 21 installation (name: `JDK21`)
- **Maven**: The pipeline uses Maven installed at `/tmp/maven`; adjust `MAVEN_HOME` in `Jenkinsfile` as required

### Pipeline Setup

1. **New Item** → Pipeline
2. Definition: **Pipeline script from SCM**
3. SCM: Git, Repository URL: `https://github.com/<user>/cloud-health-monitor.git`
4. Script Path: `Jenkinsfile`
5. Save → **Build Now**

### Webhook (GitHub → Jenkins)

```
GitHub repo → Settings → Webhooks → Add webhook
Payload URL : http://<JENKINS_HOST>:8080/github-webhook/
Content type: application/json
Events      : Just the push event
```

### Verify Build

```bash
# Check archived artifact (replace BUILD_NUMBER)
ls $JENKINS_HOME/jobs/cloud-health-monitor/builds/<BUILD_NUMBER>/archive/
```

---

## Docker

### Build

```bash
# From the project root (where Dockerfile lives)
docker build -t cloud-health-monitor:latest .
```

### Run

```bash
docker run -d \
  --name cloud-health-monitor \
  -p 8081:8081 \
  cloud-health-monitor:latest
```

### Commands

```bash
# Check status
docker ps --filter name=cloud-health-monitor

# View logs
docker logs cloud-health-monitor

# Test health
curl http://localhost:8081/health

# Stop and remove
docker stop cloud-health-monitor
docker rm cloud-health-monitor
```

---

## Ansible

> **Note:** Ansible does not run natively on Windows. Run these commands inside **WSL** (Ubuntu/Debian) or on a Linux control node.

### Install Ansible (WSL)

```bash
sudo apt update && sudo apt install -y ansible
pip3 install docker  # for the community.docker collection
ansible-galaxy collection install community.docker
```

### Run the playbook

```bash
cd ansible/

# Check (dry run)
ansible-playbook -i inventory.ini deploy.yml --check

# Apply
ansible-playbook -i inventory.ini deploy.yml
```

The playbook:
1. Installs Docker
2. Removes any existing container
3. Runs the Docker image
4. Verifies the `/health` endpoint

---

## Kubernetes

### Prerequisites

```bash
minikube start
minikube status
```

### Load the Docker image into Minikube

```bash
# Build the image first
docker build -t cloud-health-monitor:latest .

# Load into Minikube
minikube image load cloud-health-monitor:latest
```

### Deploy

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

### Check status

```bash
kubectl get pods
kubectl get deployments
kubectl get services
kubectl logs deployment/cloud-health-monitor
```

### Access the application

```bash
minikube service cloud-health-monitor --url
# or
kubectl port-forward service/cloud-health-monitor 8081:8081
curl http://localhost:8081/health
```

### Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment cloud-health-monitor --replicas=5
kubectl get pods

# Scale back to 3
kubectl scale deployment cloud-health-monitor --replicas=3
kubectl get pods
```

### Rollback

```bash
kubectl rollout undo deployment/cloud-health-monitor
kubectl rollout status deployment/cloud-health-monitor
```

---

## Screenshot Checklist

| # | Screenshot | Command / Action |
|---|-----------|-----------------|
| 1 | Application running | `curl http://localhost:8081/health` |
| 2 | Health endpoint | Response from above |
| 3 | Service registration | `POST /services` |
| 4 | Service listing | `GET /services` |
| 5 | UP health check | `GET /services/{name}/health` → UP |
| 6 | DOWN health check | `GET /services/{name}/health` → DOWN |
| 7 | Metrics response | `GET /metrics` |
| 8 | Maven tests passing | `./mvnw clean test` |
| 9 | Git branches & merge | `git log --oneline --all --graph` |
| 10 | Jenkins success + artifact | Jenkins UI build #N |
| 11 | Docker container running | `docker ps` |
| 12 | Ansible playbook | `ansible-playbook deploy.yml` |
| 13 | K8s 3 replicas | `kubectl get pods` |
| 14 | K8s 5 replicas | `kubectl get pods` after `--replicas=5` |
| 15 | K8s back to 3 | `kubectl get pods` after `--replicas=3` |
