# Cloud Health Monitor — Production Deployment Guide

## 1. Deployment Topology

The platform uses a split-tier cloud architecture:
- **Frontend:** Vercel Global Edge CDN (Single-Page Application).
- **Backend:** Render Managed Container (Spring Boot 4 / Java 21) or Kubernetes Cluster.
- **Database:** Render Managed PostgreSQL with automated schema updates.

---

## 2. Vercel Frontend Deployment

### 2.1 One-Click / CLI Deployment
The frontend directory (`frontend/`) contains static HTML, CSS, JavaScript, and `vercel.json` routing rules:

```bash
# Navigate to project root
cd /path/to/cloud-health-monitor

# Deploy to Vercel production
npx vercel deploy ./frontend --project cloud-health-monitor-ui --yes --prod
```

### 2.2 Routing Configuration (`frontend/vercel.json`)
```json
{
  "version": 2,
  "cleanUrls": true,
  "routes": [
    { "src": "/css/(.*)", "dest": "/css/$1" },
    { "src": "/js/(.*)", "dest": "/js/$1" },
    { "src": "/(.*)", "dest": "/index.html" }
  ]
}
```

### 2.3 Connecting Frontend to Backend
The frontend includes a runtime backend selector in the top bar:
1. Click the **API: [Endpoint]** button in the header.
2. Enter your deployed backend URL: `https://cloud-health-monitor-devops.onrender.com`.
3. Click **Save & Connect**. The dashboard immediately saves the endpoint to `localStorage` and begins live telemetry.

---

## 3. Render Backend Deployment

### 3.1 Render Blueprint (`render.yaml`)
The project root contains `render.yaml` for declarative Infrastructure as Code deployment:

```yaml
services:
  - type: web
    name: cloud-health-monitor-backend
    runtime: docker
    dockerfilePath: Dockerfile
    plan: free
    region: oregon
    healthCheckPath: /health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: PORT
        value: 8081
      - key: DATABASE_URL
        fromDatabase:
          name: cloud-health-monitor-db
          property: connectionString

databases:
  - name: cloud-health-monitor-db
    plan: free
    region: oregon
    postgresMajorVersion: 16
```

### 3.2 Docker Multi-Stage Build (`Dockerfile`)
```dockerfile
# Stage 1: Build Java 21 application
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

---

## 4. Kubernetes Deployment

### 4.1 Apply Manifests
Deploy the 3-replica cluster with rolling updates and NodePort:

```bash
# Apply deployment & service
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Verify pod status
kubectl get pods -l app=cloud-health-monitor
kubectl get svc cloud-health-monitor-service
```

### 4.2 Probes Configuration
```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8081
  initialDelaySeconds: 30
  periodSeconds: 15
readinessProbe:
  httpGet:
    path: /health
    port: 8081
  initialDelaySeconds: 15
  periodSeconds: 10
```

---

## 5. Prometheus & Grafana Monitoring Deployment

### 5.1 Docker Compose Execution
```bash
docker compose -f docker-compose.monitoring.yml up -d
```
- **Prometheus UI:** `http://localhost:9090`
- **Alertmanager UI:** `http://localhost:9093`
- **Grafana UI:** `http://localhost:3000` (admin/admin)

### 5.2 Prometheus Alert Rules (`monitoring/prometheus/alert.rules.yml`)
- `ServiceDownAlert`: Triggers when `monitored_service_up == 0` for 1 minute.
- `HighLatencyAlert`: Triggers when `monitored_service_response_time_seconds > 2` for 2 minutes.
- `SslExpiringSoonAlert`: Triggers when `monitored_service_ssl_expiry_days < 14`.
- `ConsecutiveFailuresAlert`: Triggers when consecutive failures exceed 3 checks.
