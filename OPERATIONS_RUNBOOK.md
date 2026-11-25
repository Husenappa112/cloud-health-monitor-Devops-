# Cloud Health Monitor — Operations Runbook

**Environment:** Windows 11 + WSL 2 (Ubuntu) + Docker Desktop (Kubernetes)  
**Cluster Context:** `docker-desktop` | **Namespace:** `default` / `monitoring`  
**Application Port:** NodePort `30081` -> `8081`  

---

## 1. Quick Reference: Service Endpoints

| Service | Host / URL | Credentials / Notes |
| :--- | :--- | :--- |
| **Application Health** | `http://localhost:30081/health` | Public HTTP GET (HTTP 200 OK) |
| **Application Services** | `http://localhost:30081/services` | Service registry status |
| **Application Metrics** | `http://localhost:30081/metrics` | Summary uptime & check counters |
| **Prometheus Actuator** | `http://localhost:30081/actuator/prometheus` | Prometheus metrics stream |
| **Prometheus Server** | `http://localhost:9090` | Web UI, graph explorer, targets & alerts |
| **Alertmanager UI** | `http://localhost:9093` | Alert state & silences |
| **Grafana Dashboards** | `http://localhost:3000` | User: `admin` | Password in `grafana-secret` |
| **Jenkins Dashboard** | `http://localhost:8083` | WSL service (`Cloud-Health-Monitor` job) |

---

## 2. Environment Startup & Verification

### Step 1: Start Docker Desktop (Windows PowerShell)
```powershell
# Launch Docker Desktop
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"

# Wait for Docker Engine and WSL initialization
docker info
wsl --list --running
```

### Step 2: Verify Kubernetes Cluster Availability (PowerShell or Bash)
```bash
# Verify current Kubernetes context is docker-desktop
kubectl config current-context

# Verify control-plane node status is Ready
kubectl get nodes
```

### Step 3: Start Supporting Monitoring Containers (PowerShell or Bash)
```bash
# Start Prometheus and Alertmanager containers
docker start chm-prometheus chm-alertmanager

# Verify containers are running
docker ps --filter "name=chm"
```

### Step 4: Verify or Start Jenkins Service (WSL Ubuntu)
```bash
# In WSL Ubuntu: check status
sudo systemctl status jenkins

# If stopped, start Jenkins
sudo systemctl start jenkins
```

---

## 3. Kubernetes Deployment & Pod Management

### Check Deployment & Replicas (PowerShell or Bash)
```bash
# Check deployment in default namespace
kubectl get deployments -n default

# Check individual pod health, IPs, and restart counts
kubectl get pods -n default -o wide

# Check service and NodePort mapping
kubectl get services -n default
```

### Stream Application Logs
```bash
# Stream logs from all 3 replicas simultaneously
kubectl logs -l app=cloud-health-monitor -n default -f --tail=100

# View logs from a specific pod
kubectl logs <pod-name> -n default --tail=50
```

### Safe Deployment Rollout Restart
```bash
# Trigger a graceful rolling restart (0 downtime)
kubectl rollout restart deployment/cloud-health-monitor -n default

# Monitor rollout progress
kubectl rollout status deployment/cloud-health-monitor -n default --timeout=120s
```

### Rollback Deployment (If an Update Fails)
```bash
# Roll back to the previous deployment revision
kubectl rollout undo deployment/cloud-health-monitor -n default

# Verify rollout status
kubectl rollout status deployment/cloud-health-monitor -n default
```

---

## 4. Monitoring & Grafana Operations

### Check Prometheus Scrape Targets
Open `http://localhost:9090/targets` in a browser, or query via terminal:
```bash
# Windows PowerShell
powershell -Command "(Invoke-RestMethod -Uri 'http://localhost:9090/api/v1/targets').data.activeTargets | Select-Object @{N='Job';E={$_.labels.job}}, health, scrapeUrl"

# Bash / cURL
curl -s http://localhost:9090/api/v1/targets | grep -o '"health":"[^"]*"'
```

### Check Active Alert Rules
Open `http://localhost:9090/alerts` in a browser, or verify loaded rule groups:
```bash
curl -s http://localhost:9090/api/v1/rules | grep -o '"name":"[^"]*"'
```

### Connect to Grafana via Port-Forward (If ClusterIP is Not Exposed)
```bash
# Forward port 3000 from the monitoring namespace
kubectl port-forward svc/grafana 3000:3000 -n monitoring
```
Access Grafana at `http://localhost:3000`.

### Retrieve Grafana Admin Password
```bash
# Windows PowerShell
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((kubectl get secret grafana-secret -n monitoring -o jsonpath='{.data.admin-password}')))

# WSL Ubuntu / Linux
kubectl get secret grafana-secret -n monitoring -o jsonpath='{.data.admin-password}' | base64 --decode; echo
```

---

## 5. Performance & Load Testing (Locust)

### Run Locust Load Test against Application
```bash
# Run headless load test: 20 users, spawn rate 2, duration 1 minute
locust -f locustfile.py --headless -u 20 -r 2 --run-time 1m --host http://localhost:30081

# Or run with interactive web UI at http://localhost:8089
locust -f locustfile.py --host http://localhost:30081
```

---

## 6. Safe Shutdown & Restart Procedures

### Graceful Environment Stop
```powershell
# 1. Stop standalone containers
docker stop chm-prometheus chm-alertmanager

# 2. Stop Jenkins service in WSL
wsl -d Ubuntu -u root systemctl stop jenkins

# 3. Quit Docker Desktop from the system tray or PowerShell
Get-Process "*docker*" | Stop-Process -Force
```

### Complete Environment Startup Sequence
1. Start Docker Desktop (`Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"`).
2. Wait 60s for node `Ready`: `kubectl wait --for=condition=Ready nodes --all --timeout=120s`.
3. Start monitoring: `docker start chm-prometheus chm-alertmanager`.
4. Start Jenkins in WSL: `wsl -d Ubuntu -u root systemctl start jenkins`.
5. Port-forward Grafana: `kubectl port-forward svc/grafana 3000:3000 -n monitoring &`.
6. Verify `/health`: `curl -i http://localhost:30081/health`.

---

## 7. Troubleshooting & Failure Diagnostics

| Symptom | Root Cause | Remediation Command |
| :--- | :--- | :--- |
| **`localhost:30081` connection refused** | Docker Desktop is stopped or Kubernetes pods are not in `Ready` state. | Verify `kubectl get pods -n default`. If pods are starting, wait 20s for readiness probe delay. |
| **`kubectl exec` fails with CRI HTTP error** | Docker Desktop CRI socket proxy bug on Windows v1.36+. | Use `docker exec <container-id> <cmd>` directly against the underlying container for pod inspection. |
| **Prometheus target reports DOWN** | NodePort mapping or network bridging issue. | Confirm NodePort is active via `curl http://host.docker.internal:30081/health`. Check `chm-prometheus` Docker container logs. |
| **Grafana `localhost:3000` unreachable** | Grafana is deployed as `ClusterIP` in namespace `monitoring`. | Run `kubectl port-forward svc/grafana 3000:3000 -n monitoring` in background. |
| **Jenkins `localhost:8083` unreachable** | Jenkins `systemd` service stopped in WSL Ubuntu. | Run `wsl -d Ubuntu -u root systemctl restart jenkins`. |
| **Pod CrashLoopBackOff on `/tmp` write** | Read-only root filesystem prevents Tomcat temporary file creation. | Ensure `emptyDir` scratch volume is mounted at `/tmp` in `k8s/deployment.yaml`. |
| **Tomcat permission denied on `/app/app.jar`** | Container UID mismatch between Dockerfile and manifest. | Confirm `k8s/deployment.yaml` specifies `runAsUser: 100` and `runAsGroup: 101` matching Alpine `appuser`. |
