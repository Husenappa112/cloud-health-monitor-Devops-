# Cloud Health Monitor — User & Operator Guide

## 1. Introduction

Welcome to **Cloud Health Monitor**! This guide explains how to use the web dashboard and REST APIs to register endpoints, monitor availability, diagnose performance anomalies, analyze response times, and manage incidents.

---

## 2. Navigating the Dashboard

Open the live web dashboard:
👉 **[https://cloud-health-monitor-ui.vercel.app](https://cloud-health-monitor-ui.vercel.app)**

### 2.1 Navigation Tabs (Sidebar)
- **Overview:** Main dashboard showing total monitors, operational UP count, degraded count, outages, overall uptime %, and monitored endpoint table.
- **Monitors:** Filtered view focusing on registered endpoints, diagnostic metrics, and control actions.
- **Incidents:** Real-time lifecycle log showing ongoing outages (`OPEN`), root causes, and automatically resolved incidents.
- **Analytics:** 24h, 7d, and 30d uptime percentages, min/max/average latencies, and check totals.
- **Public Status:** High-level status banner and service health cards suitable for sharing with end-users.

---

## 3. Registering a New URL Monitor

1. In the top-right corner, click **Add Monitor**.
2. Fill in the fields:
   - **Service Name:** Enter a recognizable name (e.g. `Google Search` or `Payment Microservice`).
   - **Target URL:** Enter the public HTTP/HTTPS URL (e.g. `google.com` or `https://api.github.com`).
     > *Note:* If you omit `https://`, the platform automatically prepends it. Private IPs (e.g. `192.168.1.1` or `127.0.0.1`) are blocked for security.
   - **Monitoring Interval:** Choose 30s, 60s (default), 2 minutes, or 5 minutes.
3. Click **Start Monitoring**.
4. The system executes an immediate probe and displays the live status.

---

## 4. Understanding Health Metrics & Statuses

### Status Badges
- 🟢 **UP:** Endpoint responded with HTTP 200–299 in under 2000ms with a valid SSL certificate.
- 🟡 **DEGRADED:** Endpoint returned HTTP 3xx redirect, latency exceeded 2000ms, or SSL certificate expires in under 7 days.
- 🔴 **DOWN:** Endpoint returned HTTP 4xx/5xx, connection timed out, DNS resolution failed, or SSL certificate expired.
- 🟣 **PAUSED:** Monitoring is paused by administrator; scheduled checks are suspended.

---

## 5. Monitor Controls & Actions

In the Monitored Endpoints table:
- **Check (Proactive Recheck):** Triggers an immediate health check on demand without waiting for the interval.
- **Pause / Resume:**
  - Click **Pause** to suspend checks during maintenance windows.
  - Click **Resume** to reactivate monitoring and immediately run a health check.
- **Edit:** Opens the Edit modal to update the service name, target URL, monitoring interval, or active status.
- **Details:** Opens the comprehensive diagnostic modal with the interactive latency sparkline chart.
- **Delete (&times;):** Removes the monitor and its check history from the platform.

---

## 6. Inspecting Performance Trends & Sparkline Charts

Click **Details** on any monitor to view:
- **Latency Sparkline Chart:** Visual response time history over recent checks with Min, Avg, and Max markers.
- **24h / 7d / 30d Uptime Statistics:** Calculated availability percentages.
- **DNS Lookup Time:** Exact milliseconds taken for authoritative DNS resolution.
- **SSL Certificate Details:** Days remaining, exact expiration timestamp, and certificate authority.
- **Redirects:** Number of HTTP redirect hops and final landing URL.
- **Recent Health Check Table:** Chronological log of recent checks with status codes and diagnostic notes.

---

## 7. Connecting Frontend to a Custom Backend

When hosting on Vercel, you can connect to your own backend:
1. Click the **API: [Endpoint]** button in the top navigation bar.
2. In the modal, enter your backend base URL (e.g., `https://your-custom-backend.onrender.com`).
3. Click **Save & Connect**. The dashboard will persist the setting and reload live data immediately.
