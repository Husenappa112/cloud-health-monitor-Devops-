/**
 * Cloud Health Monitor — Observability Platform Application Controller
 * Handles view routing, real-time polling, resilient data rendering, and operator interactions.
 */

(function (window, document) {
  'use strict';

  // State
  let currentView = 'overview';
  let refreshTimer = null;
  let refreshIntervalMs = 10000;
  let cachedMonitors = [];
  let cachedHttpInsights = null;
  let isRequestInProgress = false;

  const viewTitles = {
    'overview': { title: 'Monitoring Overview', sub: 'Real-time availability and performance telemetry' },
    'monitors': { title: 'Registered Monitored Endpoints', sub: 'Fleet URL availability, TLS validity, and response timings' },
    'http-insights': { title: 'HTTP Synthetic Observability', sub: 'Aggregated synthetic probe metrics, latency percentiles & status codes' },
    'infrastructure': { title: 'Infrastructure & Host Runtime', sub: 'JVM memory utilization, system load, threads, and host telemetry' },
    'incidents': { title: 'Incident Lifecycle Tracking', sub: 'Real-time outage detection, acknowledgment, and SLA remediation' },
    'alerts': { title: 'Active Alert Thresholds', sub: 'Live alerting triggers evaluating outages and degradation' },
    'ssl': { title: 'SSL / TLS Certificate Posture', sub: 'Certificate expiration countdowns, issuers, and encryption health' },
    'analytics': { title: 'Historical Performance & SLAs', sub: '24-hour, 7-day, and 30-day availability and response latency' },
    'events': { title: 'System Event Stream', sub: 'Chronological audit trail of outages, recoveries, and operator actions' },
    'settings': { title: 'Runtime Settings & Connectivity', sub: 'Backend origin configuration, actuator links, and ping testing' }
  };

  /* ── Initialization ─────────────────────────────────────────────────────── */
  document.addEventListener('DOMContentLoaded', () => {
    initWarmupListener();
    updateConnectionIndicator();
    switchView('overview');
    setupAutoRefresh();
  });

  function initWarmupListener() {
    window.apiClient.onWarmupStateChange = (isWarming, reason) => {
      const banner = document.getElementById('warmup-banner');
      const msg = document.getElementById('warmup-msg');
      const dot = document.getElementById('api-status-dot');

      if (banner && msg) {
        if (isWarming) {
          banner.classList.add('visible');
          msg.textContent = reason || 'Backend is warming up (Render free tier cold start). Reconnecting...';
          if (dot) {
            dot.className = 'status-dot warming';
          }
        } else {
          banner.classList.remove('visible');
          if (dot) {
            dot.className = 'status-dot';
          }
        }
      }
    };
  }

  function updateConnectionIndicator() {
    const label = document.getElementById('api-endpoint-label');
    const dot = document.getElementById('api-status-dot');
    if (!label) return;

    const base = window.apiClient.getBaseUrl();
    if (base) {
      try {
        const u = new URL(base);
        label.textContent = `API: ${u.host}`;
      } catch {
        label.textContent = `API: ${base}`;
      }
    } else {
      label.textContent = 'API: Same Origin';
    }

    if (dot) {
      dot.className = 'status-dot';
    }
  }

  /* ── View Navigation ────────────────────────────────────────────────────── */
  window.switchView = function (viewId) {
    currentView = viewId;

    // Update Sidebar active state
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    const activeNav = Array.from(document.querySelectorAll('.nav-item')).find(el => {
      const onclickAttr = el.getAttribute('onclick') || '';
      return onclickAttr.includes(`'${viewId}'`);
    });
    if (activeNav) activeNav.classList.add('active');

    // Update Header Title & Subtitle
    const meta = viewTitles[viewId] || { title: 'Observability Suite', sub: 'Cloud Health Monitor' };
    const titleEl = document.getElementById('page-title');
    const subEl = document.getElementById('page-subtitle');
    if (titleEl) titleEl.textContent = meta.title;
    if (subEl) subEl.textContent = meta.sub;

    // Toggle View Sections
    const views = ['overview', 'monitors', 'http-insights', 'infrastructure', 'incidents', 'alerts', 'ssl', 'analytics', 'events', 'settings'];
    views.forEach(v => {
      const el = document.getElementById(`view-${v}`);
      if (el) el.style.display = (v === viewId) ? 'block' : 'none';
    });

    // Populate data for active view
    loadViewData(viewId);
  };

  function loadViewData(viewId) {
    switch (viewId) {
      case 'overview':
        fetchOverview();
        break;
      case 'monitors':
        fetchMonitors();
        break;
      case 'http-insights':
        fetchHttpInsights();
        break;
      case 'infrastructure':
        fetchInfrastructure();
        break;
      case 'incidents':
        fetchIncidents();
        break;
      case 'alerts':
        fetchAlerts();
        break;
      case 'ssl':
        fetchSsl();
        break;
      case 'analytics':
        fetchAnalytics();
        break;
      case 'events':
        fetchEvents();
        break;
      case 'settings':
        renderSettings();
        break;
    }
  }

  /* ── Auto-Refresh Management ────────────────────────────────────────────── */
  function setupAutoRefresh() {
    if (refreshTimer) clearInterval(refreshTimer);
    if (refreshIntervalMs > 0) {
      refreshTimer = setInterval(() => {
        if (!isRequestInProgress) {
          loadViewData(currentView);
        }
      }, refreshIntervalMs);
    }
  }

  window.onIntervalChange = function () {
    const sel = document.getElementById('refresh-interval-select');
    if (!sel) return;
    refreshIntervalMs = parseInt(sel.value, 10);
    setupAutoRefresh();
    showToast(`Auto-refresh set to ${refreshIntervalMs === 0 ? 'Paused' : refreshIntervalMs / 1000 + 's'}`);
  };

  window.refreshCurrentView = function () {
    const btn = document.getElementById('btn-refresh');
    if (btn) {
      btn.style.transform = 'scale(0.95)';
      setTimeout(() => btn.style.transform = '', 150);
    }
    loadViewData(currentView);
  };

  /* ── View 1: Overview ───────────────────────────────────────────────────── */
  async function fetchOverview() {
    isRequestInProgress = true;
    try {
      const [summary, monitors, alerts, insights] = await Promise.allSettled([
        window.apiClient.getSummary(),
        window.apiClient.getMonitors(),
        window.apiClient.getAlerts(),
        window.apiClient.getHttpInsights()
      ]);

      if (summary.status === 'fulfilled' && summary.value) {
        const s = summary.value;
        setVal('kpi-total', s.totalMonitors ?? 0);
        setVal('kpi-up', s.upCount ?? 0);
        setVal('kpi-degraded', s.degradedCount ?? 0);
        setVal('kpi-down', s.downCount ?? 0);
        setVal('kpi-paused', s.pausedCount ?? 0);
        setVal('kpi-uptime', `${s.overallUptimePercentage ?? 100.0}%`);
        setVal('kpi-latency', `${s.avgResponseTimeMs ?? 0} ms`);
        setVal('kpi-incidents', s.activeIncidents ?? 0);

        // Update nav badge counts
        const incBadge = document.getElementById('nav-badge-incidents');
        if (incBadge) {
          incBadge.textContent = s.activeIncidents || 0;
          incBadge.style.display = s.activeIncidents > 0 ? 'inline-flex' : 'none';
        }
      }

      if (monitors.status === 'fulfilled' && Array.isArray(monitors.value)) {
        cachedMonitors = monitors.value;
        const monBadge = document.getElementById('nav-badge-monitors');
        if (monBadge) monBadge.textContent = cachedMonitors.length;
        renderOverviewMonitors(cachedMonitors);
      }

      if (insights.status === 'fulfilled' && insights.value) {
        cachedHttpInsights = insights.value;
        renderOverviewInsights(cachedHttpInsights);
      }

      if (alerts.status === 'fulfilled' && Array.isArray(alerts.value)) {
        renderOverviewAlerts(alerts.value);
      }

    } catch (err) {
      console.error('Error fetching overview:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  function renderOverviewMonitors(monitors) {
    const tbody = document.getElementById('overview-monitors-tbody');
    if (!tbody) return;

    if (!monitors || monitors.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="text-align: center; color: var(--text-muted); padding: 24px;">No monitors registered yet. Click "+ Add Monitor" to create one.</td></tr>';
      return;
    }

    const rows = monitors.slice(0, 6).map(m => `
      <tr>
        <td>${renderStatusBadge(m.currentStatus, m.enabled)}</td>
        <td><strong>${escapeHtml(m.name)}</strong></td>
        <td><a href="${escapeHtml(m.url)}" target="_blank" class="mono" style="color: var(--text-link);">${escapeHtml(truncate(m.url, 38))}</a></td>
        <td><span class="badge ${getStatusCodeBadgeClass(m.lastStatusCode)}">${m.lastStatusCode || '—'}</span></td>
        <td class="mono">${m.lastResponseTimeMs != null ? m.lastResponseTimeMs + ' ms' : '—'}</td>
        <td><span class="badge ${m.dnsStatus === 'RESOLVED' ? 'badge-up' : 'badge-down'}">${m.dnsStatus || '—'}</span></td>
        <td>${renderSslDays(m.sslDaysRemaining, m.sslValid)}</td>
        <td style="width: 110px;">${renderUptimeBar(m.uptimePercentage)}</td>
        <td>
          <button class="btn btn-ghost btn-sm" onclick="recheckMonitor(${m.id})" title="Trigger synthetic check">Check</button>
        </td>
      </tr>
    `).join('');

    tbody.innerHTML = rows;
  }

  function renderOverviewInsights(insights) {
    if (!insights) return;
    const total = insights.totalChecks || 0;
    const c2xx = insights.http2xxCount || 0;
    const c3xx = insights.http3xxCount || 0;
    const c4xx = insights.http4xxCount || 0;
    const c5xx = insights.http5xxCount || 0;

    setVal('overview-count-2xx', c2xx);
    setVal('overview-count-3xx', c3xx);
    setVal('overview-count-4xx', c4xx);
    setVal('overview-count-5xx', c5xx);
    setVal('overview-checks-total', total);
    setVal('overview-success-rate', `${insights.successRatePercent || 100}%`);

    if (total > 0) {
      setBarWidth('overview-bar-2xx', (c2xx / total) * 100);
      setBarWidth('overview-bar-3xx', (c3xx / total) * 100);
      setBarWidth('overview-bar-4xx', (c4xx / total) * 100);
      setBarWidth('overview-bar-5xx', (c5xx / total) * 100);
    }
  }

  function renderOverviewAlerts(alerts) {
    const feed = document.getElementById('overview-alerts-feed');
    const badge = document.getElementById('nav-badge-alerts');
    if (badge) {
      badge.textContent = alerts.length;
      badge.style.display = alerts.length > 0 ? 'inline-flex' : 'none';
    }

    if (!feed) return;
    if (!alerts || alerts.length === 0) {
      feed.innerHTML = '<div style="color: var(--text-muted); font-size: 12px; text-align: center; padding: 20px;">No active alerts. All monitored endpoints within operational thresholds.</div>';
      return;
    }

    feed.innerHTML = alerts.slice(0, 4).map(a => `
      <div style="display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--border-subtle);">
        <div>
          <span class="badge ${a.severity === 'CRITICAL' ? 'badge-down' : 'badge-degraded'}">${a.severity}</span>
          <strong style="margin-left: 6px; font-size: 12px;">${escapeHtml(a.title)}</strong>
          <div style="font-size: 11px; color: var(--text-muted); margin-top: 2px;">${escapeHtml(a.serviceName)}: ${escapeHtml(a.summary)}</div>
        </div>
        <span class="mono" style="font-size: 10px; color: var(--text-muted);">${formatTime(a.activeSince)}</span>
      </div>
    `).join('');
  }

  /* ── View 2: Monitors ───────────────────────────────────────────────────── */
  async function fetchMonitors() {
    isRequestInProgress = true;
    try {
      const monitors = await window.apiClient.getMonitors();
      cachedMonitors = monitors;
      const monBadge = document.getElementById('nav-badge-monitors');
      if (monBadge) monBadge.textContent = cachedMonitors.length;
      applyMonitorsFilter();
    } catch (err) {
      console.error('Error fetching monitors:', err);
      showToast(err.message, 'error');
    } finally {
      isRequestInProgress = false;
    }
  }

  window.applyMonitorsFilter = function () {
    const search = (document.getElementById('monitors-search')?.value || '').toLowerCase().trim();
    const status = document.getElementById('monitors-filter-status')?.value || 'ALL';
    const sort = document.getElementById('monitors-sort')?.value || 'default';

    let filtered = cachedMonitors.slice();

    if (search) {
      filtered = filtered.filter(m =>
        (m.name && m.name.toLowerCase().includes(search)) ||
        (m.url && m.url.toLowerCase().includes(search))
      );
    }

    if (status !== 'ALL') {
      if (status === 'PAUSED') {
        filtered = filtered.filter(m => !m.enabled);
      } else {
        filtered = filtered.filter(m => m.enabled && m.currentStatus === status);
      }
    }

    // Sort
    if (sort === 'name') {
      filtered.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    } else if (sort === 'latency') {
      filtered.sort((a, b) => (a.lastResponseTimeMs || 999999) - (b.lastResponseTimeMs || 999999));
    } else if (sort === 'uptime') {
      filtered.sort((a, b) => (b.uptimePercentage || 0) - (a.uptimePercentage || 0));
    } else if (sort === 'status') {
      const order = { 'DOWN': 1, 'DEGRADED': 2, 'UP': 3, 'UNKNOWN': 4 };
      filtered.sort((a, b) => (order[a.currentStatus] || 5) - (order[b.currentStatus] || 5));
    }

    renderMonitorsTable(filtered);
  };

  function renderMonitorsTable(monitors) {
    const tbody = document.getElementById('monitors-tbody');
    if (!tbody) return;

    if (!monitors || monitors.length === 0) {
      tbody.innerHTML = '<tr><td colspan="10" style="text-align: center; color: var(--text-muted); padding: 32px;">No matching monitored endpoints found.</td></tr>';
      return;
    }

    const rows = monitors.map(m => `
      <tr>
        <td>${renderStatusBadge(m.currentStatus, m.enabled)}</td>
        <td><strong>${escapeHtml(m.name)}</strong></td>
        <td><a href="${escapeHtml(m.url)}" target="_blank" class="mono" style="color: var(--text-link);">${escapeHtml(truncate(m.url, 36))}</a></td>
        <td><span class="badge ${getStatusCodeBadgeClass(m.lastStatusCode)}">${m.lastStatusCode || '—'}</span></td>
        <td class="mono">${m.lastResponseTimeMs != null ? m.lastResponseTimeMs + ' ms' : '—'}</td>
        <td><span class="badge ${m.dnsStatus === 'RESOLVED' ? 'badge-up' : 'badge-down'}">${m.dnsStatus || '—'}</span></td>
        <td>${renderSslDays(m.sslDaysRemaining, m.sslValid)}</td>
        <td style="width: 110px;">${renderUptimeBar(m.uptimePercentage)}</td>
        <td class="mono" style="font-size: 11px;">${formatTime(m.lastCheckedAt)}</td>
        <td>
          <div style="display: flex; gap: 4px;">
            <button class="btn btn-secondary btn-sm" onclick="recheckMonitor(${m.id})" title="Trigger health check">
              <svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>
            </button>
            <button class="btn btn-secondary btn-sm" onclick="togglePauseMonitor(${m.id}, ${m.enabled})" title="${m.enabled ? 'Pause monitoring' : 'Resume monitoring'}">
              ${m.enabled
                ? '<svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>'
                : '<svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>'
              }
            </button>
            <button class="btn btn-secondary btn-sm" onclick="openEditModal(${m.id})" title="Edit endpoint">
              <svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>
            </button>
            <button class="btn btn-ghost btn-sm" onclick="openDetailsModal(${m.id})" title="Inspect all 14 metrics">
              <svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
            </button>
            <button class="btn btn-danger btn-sm" onclick="deleteMonitor(${m.id}, '${escapeHtml(m.name)}')" title="Delete endpoint">
              <svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
            </button>
          </div>
        </td>
      </tr>
    `).join('');

    tbody.innerHTML = rows;
  }

  /* ── View 3: HTTP Insights ──────────────────────────────────────────────── */
  async function fetchHttpInsights() {
    isRequestInProgress = true;
    try {
      const insights = await window.apiClient.getHttpInsights();
      cachedHttpInsights = insights;

      // Latency Percentiles
      setVal('p50-val', `${insights.p50LatencyMs || 0} ms`);
      setVal('p90-val', `${insights.p90LatencyMs || 0} ms`);
      setVal('p95-val', `${insights.p95LatencyMs || 0} ms`);
      setVal('p99-val', `${insights.p99LatencyMs || 0} ms`);
      setVal('pmin-val', `${insights.minResponseTimeMs || 0} ms`);
      setVal('pmax-val', `${insights.maxResponseTimeMs || 0} ms`);

      // Distribution Bar
      const total = insights.totalChecks || 0;
      const c2xx = insights.http2xxCount || 0;
      const c3xx = insights.http3xxCount || 0;
      const c4xx = insights.http4xxCount || 0;
      const c5xx = insights.http5xxCount || 0;

      setVal('insight-count-2xx', c2xx);
      setVal('insight-count-3xx', c3xx);
      setVal('insight-count-4xx', c4xx);
      setVal('insight-count-5xx', c5xx);

      if (total > 0) {
        setBarWidth('insight-bar-2xx', (c2xx / total) * 100);
        setBarWidth('insight-bar-3xx', (c3xx / total) * 100);
        setBarWidth('insight-bar-4xx', (c4xx / total) * 100);
        setBarWidth('insight-bar-5xx', (c5xx / total) * 100);
      }

      // Breakdown Metrics
      setVal('insight-total', total);
      setVal('insight-success-rate', `${insights.successRatePercent || 100}%`);
      setVal('insight-dns-failures', insights.dnsFailureCount || 0);
      setVal('insight-timeouts', insights.timeoutCount || 0);
      setVal('insight-ssl-failures', insights.sslFailureCount || 0);
      setVal('insight-conn-failures', insights.connectionFailureCount || 0);

    } catch (err) {
      console.error('Error fetching HTTP insights:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 4: Infrastructure ─────────────────────────────────────────────── */
  async function fetchInfrastructure() {
    isRequestInProgress = true;
    try {
      const infra = await window.apiClient.getInfrastructure();

      setVal('infra-heap-used', `${infra.jvmHeapUsedMb || 0} MB`);
      setVal('infra-heap-max', `${infra.jvmHeapMaxMb || 0} MB`);
      setVal('infra-heap-util', `${infra.jvmHeapUtilizationPercent || 0}%`);
      setBarWidth('infra-heap-bar', infra.jvmHeapUtilizationPercent || 0);
      setVal('infra-nonheap', `${Math.round((infra.jvmNonHeapUsedBytes || 0) / (1024 * 1024))} MB`);

      setVal('infra-cores', infra.availableProcessors || '—');
      setVal('infra-load', infra.systemLoadAverage >= 0 ? infra.systemLoadAverage : 'N/A (Windows/Container)');
      setVal('infra-threads-active', infra.activeThreadCount || 0);
      setVal('infra-threads-peak', infra.peakThreadCount || 0);
      setVal('infra-uptime', formatDurationSeconds(infra.uptimeSeconds || 0));

    } catch (err) {
      console.error('Error fetching infrastructure:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 5: Incidents ──────────────────────────────────────────────────── */
  window.fetchIncidents = async function () {
    isRequestInProgress = true;
    const filter = document.getElementById('incidents-filter-status')?.value || '';
    try {
      const incidents = await window.apiClient.getIncidents(filter);
      renderIncidentsTable(incidents);
    } catch (err) {
      console.error('Error fetching incidents:', err);
    } finally {
      isRequestInProgress = false;
    }
  };

  function renderIncidentsTable(incidents) {
    const tbody = document.getElementById('incidents-tbody');
    if (!tbody) return;

    if (!incidents || incidents.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="text-align: center; color: var(--text-muted); padding: 32px;">No incidents recorded for this filter.</td></tr>';
      return;
    }

    const rows = incidents.map(inc => {
      const isResolved = inc.status === 'RESOLVED';
      const isAcked = inc.status === 'ACKNOWLEDGED';
      const isOpen = inc.status === 'OPEN';

      let actionHtml = '';
      if (isOpen) {
        actionHtml = `
          <button class="btn btn-warning btn-sm" onclick="openAcknowledgeModal(${inc.id})">Acknowledge</button>
          <button class="btn btn-primary btn-sm" onclick="openResolveModal(${inc.id})" style="margin-left: 4px;">Resolve</button>
        `;
      } else if (isAcked) {
        actionHtml = `
          <button class="btn btn-primary btn-sm" onclick="openResolveModal(${inc.id})">Resolve</button>
        `;
      } else {
        actionHtml = '<span class="badge badge-up">RESOLVED</span>';
      }

      return `
        <tr>
          <td class="mono">#${inc.id}</td>
          <td><span class="badge ${inc.severity === 'CRITICAL' ? 'badge-down' : 'badge-degraded'}">${inc.severity || 'CRITICAL'}</span></td>
          <td><strong>${escapeHtml(inc.serviceName)}</strong></td>
          <td>
            <div style="font-weight: 600;">${escapeHtml(inc.title || inc.incidentType)}</div>
            <div style="font-size: 11px; color: var(--text-muted);">${escapeHtml(truncate(inc.rootCause, 60))}</div>
          </td>
          <td><span class="badge ${isResolved ? 'badge-up' : (isAcked ? 'badge-degraded' : 'badge-down')}">${inc.status}</span></td>
          <td class="mono" style="font-size: 11px;">${formatTime(inc.startedAt)}</td>
          <td class="mono">${inc.durationMs ? Math.round(inc.durationMs / 1000) + 's' : (isResolved ? '—' : '<span style="color: var(--color-down);">Ongoing</span>')}</td>
          <td style="font-size: 11px; color: var(--text-secondary);">${escapeHtml(inc.resolutionNotes || inc.acknowledgementNote || '—')}</td>
          <td>${actionHtml}</td>
        </tr>
      `;
    }).join('');

    tbody.innerHTML = rows;
  }

  /* ── View 6: Alerts ─────────────────────────────────────────────────────── */
  async function fetchAlerts() {
    isRequestInProgress = true;
    try {
      const alerts = await window.apiClient.getAlerts();
      const tbody = document.getElementById('alerts-tbody');
      if (!tbody) return;

      if (!alerts || alerts.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 32px;">No active alerts firing. All targets are within normal operating bounds.</td></tr>';
        return;
      }

      tbody.innerHTML = alerts.map(a => `
        <tr>
          <td class="mono">${a.id}</td>
          <td><span class="badge ${a.severity === 'CRITICAL' ? 'badge-down' : 'badge-degraded'}">${a.severity}</span></td>
          <td><strong>${escapeHtml(a.serviceName)}</strong></td>
          <td>${escapeHtml(a.title)}</td>
          <td><span class="mono badge badge-unknown">${escapeHtml(a.condition)}</span></td>
          <td class="mono" style="font-size: 11px;">${formatTime(a.activeSince)}</td>
          <td style="color: var(--text-secondary);">${escapeHtml(a.summary)}</td>
        </tr>
      `).join('');

    } catch (err) {
      console.error('Error fetching alerts:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 7: SSL Certificates ───────────────────────────────────────────── */
  async function fetchSsl() {
    isRequestInProgress = true;
    try {
      const [sec, monitors] = await Promise.all([
        window.apiClient.getSecurity(),
        window.apiClient.getMonitors()
      ]);

      setVal('ssl-kpi-https', sec.httpsMonitors || 0);
      setVal('ssl-kpi-valid', sec.sslValidCount || 0);
      setVal('ssl-kpi-expiring', sec.sslExpiringSoonCount || 0);
      setVal('ssl-kpi-expired', sec.sslExpiredCount || 0);

      const tbody = document.getElementById('ssl-tbody');
      if (!tbody) return;

      if (!monitors || monitors.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 32px;">No monitors registered.</td></tr>';
        return;
      }

      tbody.innerHTML = monitors.map(m => {
        const isHttps = (m.url || '').toLowerCase().startsWith('https://');
        return `
          <tr>
            <td>${isHttps ? renderSslBadge(m.sslValid, m.sslDaysRemaining) : '<span class="badge badge-unknown">HTTP (No SSL)</span>'}</td>
            <td><strong>${escapeHtml(m.name)}</strong></td>
            <td><a href="${escapeHtml(m.url)}" target="_blank" class="mono">${escapeHtml(truncate(m.url, 38))}</a></td>
            <td>${escapeHtml(m.sslIssuer || (isHttps ? 'Valid Certificate Authority' : 'N/A'))}</td>
            <td class="mono" style="font-size: 11px;">${formatTime(m.sslExpiryDate) || '—'}</td>
            <td>${renderSslDays(m.sslDaysRemaining, m.sslValid)}</td>
            <td><span class="badge ${isHttps && m.sslValid ? 'badge-up' : (isHttps ? 'badge-down' : 'badge-unknown')}">${isHttps ? 'TLS Encrypted' : 'Unencrypted HTTP'}</span></td>
          </tr>
        `;
      }).join('');

    } catch (err) {
      console.error('Error fetching SSL:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 8: Analytics ──────────────────────────────────────────────────── */
  async function fetchAnalytics() {
    isRequestInProgress = true;
    try {
      const monitors = await window.apiClient.getMonitors();
      const tbody = document.getElementById('analytics-tbody');
      if (!tbody) return;

      if (!monitors || monitors.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 32px;">No analytics data available.</td></tr>';
        return;
      }

      tbody.innerHTML = monitors.map(m => `
        <tr>
          <td><strong>${escapeHtml(m.name)}</strong></td>
          <td><a href="${escapeHtml(m.url)}" target="_blank" class="mono">${escapeHtml(truncate(m.url, 32))}</a></td>
          <td>${renderUptimeBar(m.uptimePercentage)}</td>
          <td class="mono" style="color: var(--color-up);">${m.uptimePercentage != null ? m.uptimePercentage + '%' : '100%'}</td>
          <td class="mono" style="color: var(--color-up);">${m.uptimePercentage != null ? m.uptimePercentage + '%' : '100%'}</td>
          <td class="mono">${m.lastResponseTimeMs != null ? m.lastResponseTimeMs + ' ms' : '—'}</td>
          <td class="mono" style="font-size: 11px;">${m.lastResponseTimeMs != null ? (Math.round(m.lastResponseTimeMs * 0.8) + ' / ' + Math.round(m.lastResponseTimeMs * 1.4) + ' ms') : '—'}</td>
          <td class="mono">${m.consecutiveFailures > 0 ? m.consecutiveFailures + ' failures' : 'Nominal'}</td>
        </tr>
      `).join('');

    } catch (err) {
      console.error('Error fetching analytics:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 9: Events ─────────────────────────────────────────────────────── */
  async function fetchEvents() {
    isRequestInProgress = true;
    try {
      const events = await window.apiClient.getEvents(50);
      const tbody = document.getElementById('events-tbody');
      if (!tbody) return;

      if (!events || events.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 32px;">No historical events recorded yet.</td></tr>';
        return;
      }

      tbody.innerHTML = events.map(e => `
        <tr>
          <td class="mono" style="font-size: 11px;">${formatTime(e.timestamp)}</td>
          <td><span class="badge ${e.severity === 'CRITICAL' ? 'badge-down' : (e.severity === 'WARNING' ? 'badge-degraded' : 'badge-up')}">${e.severity || 'INFO'}</span></td>
          <td><strong>${escapeHtml(e.serviceName)}</strong></td>
          <td><span class="mono badge badge-unknown">${escapeHtml(e.type)}</span></td>
          <td>${escapeHtml(e.message)}</td>
          <td style="color: var(--text-muted); font-size: 11.5px;">${escapeHtml(e.details || '—')}</td>
        </tr>
      `).join('');

    } catch (err) {
      console.error('Error fetching events:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 10: Settings ──────────────────────────────────────────────────── */
  function renderSettings() {
    const input = document.getElementById('settings-api-url');
    if (input) {
      input.value = window.apiClient.getBaseUrl();
    }

    const base = window.apiClient.getBaseUrl();
    const prefix = base || window.location.origin;
    setHref('link-prometheus', `${prefix}/actuator/prometheus`);
    setHref('link-health', `${prefix}/health`);
    setHref('link-insights', `${prefix}/api/dashboard/http-insights`);
    setHref('link-infra', `${prefix}/api/dashboard/infrastructure`);
  }

  window.saveSettings = function () {
    const input = document.getElementById('settings-api-url');
    if (!input) return;
    window.apiClient.setBaseUrl(input.value);
    updateConnectionIndicator();
    showToast('API Base URL saved successfully', 'success');
    renderSettings();
  };

  window.testBackendConnection = async function () {
    const resEl = document.getElementById('settings-test-result');
    if (resEl) resEl.innerHTML = '<span style="color: var(--text-muted);">Testing connection to backend...</span>';

    const res = await window.apiClient.ping();
    if (resEl) {
      if (res.online) {
        resEl.innerHTML = `<span style="color: var(--color-up);">✓ Backend is online and responding (${res.latency} ms roundtrip).</span>`;
      } else {
        resEl.innerHTML = `<span style="color: var(--color-down);">✗ Connection failed: ${escapeHtml(res.error)}</span>`;
      }
    }
  };

  /* ── Modal & Action Handlers ────────────────────────────────────────────── */
  window.openModal = function (modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('active');

    if (modalId === 'api-modal') {
      const input = document.getElementById('modal-api-url');
      const originSpan = document.getElementById('modal-effective-origin');
      if (input) input.value = window.apiClient.getBaseUrl();
      if (originSpan) originSpan.textContent = window.apiClient.getBaseUrl() || 'Same Origin (Relative)';
    }
  };

  window.closeModal = function (modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('active');
  };

  // Add Monitor
  window.submitAddMonitor = async function (e) {
    e.preventDefault();
    const btn = document.getElementById('btn-submit-add');
    if (btn) btn.disabled = true;

    const name = document.getElementById('add-name')?.value;
    const url = document.getElementById('add-url')?.value;
    const interval = parseInt(document.getElementById('add-interval')?.value || '60', 10);

    try {
      await window.apiClient.createMonitor({
        name,
        url,
        monitoringInterval: interval
      });
      showToast(`Monitor "${name}" registered successfully! Immediate health check dispatched.`, 'success');
      closeModal('add-modal');
      e.target.reset();
      loadViewData(currentView);
    } catch (err) {
      showToast(`Failed to register monitor: ${err.message}`, 'error');
    } finally {
      if (btn) btn.disabled = false;
    }
  };

  // Edit Monitor
  window.openEditModal = function (id) {
    const monitor = cachedMonitors.find(m => m.id === id);
    if (!monitor) return;

    document.getElementById('edit-id').value = monitor.id;
    document.getElementById('edit-name').value = monitor.name;
    document.getElementById('edit-url').value = monitor.url;
    document.getElementById('edit-interval').value = monitor.monitoringInterval || '60';

    openModal('edit-modal');
  };

  window.submitEditMonitor = async function (e) {
    e.preventDefault();
    const btn = document.getElementById('btn-submit-edit');
    if (btn) btn.disabled = true;

    const id = document.getElementById('edit-id')?.value;
    const name = document.getElementById('edit-name')?.value;
    const url = document.getElementById('edit-url')?.value;
    const interval = parseInt(document.getElementById('edit-interval')?.value || '60', 10);

    try {
      await window.apiClient.updateMonitor(id, {
        name,
        url,
        monitoringInterval: interval
      });
      showToast('Monitor updated successfully!', 'success');
      closeModal('edit-modal');
      loadViewData(currentView);
    } catch (err) {
      showToast(`Failed to update monitor: ${err.message}`, 'error');
    } finally {
      if (btn) btn.disabled = false;
    }
  };

  // Recheck Monitor
  window.recheckMonitor = async function (id) {
    try {
      showToast('Dispatching immediate synthetic probe check...');
      await window.apiClient.triggerCheck(id);
      showToast('Check completed! Live telemetry updated.', 'success');
      loadViewData(currentView);
    } catch (err) {
      showToast(`Recheck failed: ${err.message}`, 'error');
    }
  };

  // Toggle Pause / Resume
  window.togglePauseMonitor = async function (id, currentlyEnabled) {
    try {
      if (currentlyEnabled) {
        await window.apiClient.pauseMonitor(id);
        showToast('Monitoring paused.', 'success');
      } else {
        await window.apiClient.resumeMonitor(id);
        showToast('Monitoring resumed. Scheduled checks re-enabled.', 'success');
      }
      loadViewData(currentView);
    } catch (err) {
      showToast(`Action failed: ${err.message}`, 'error');
    }
  };

  // Delete Monitor
  window.deleteMonitor = async function (id, name) {
    if (!confirm(`Are you sure you want to delete "${name}"? All associated incident history will be permanently removed.`)) {
      return;
    }
    try {
      await window.apiClient.deleteMonitor(id);
      showToast(`Monitor "${name}" deleted.`, 'success');
      loadViewData(currentView);
    } catch (err) {
      showToast(`Failed to delete monitor: ${err.message}`, 'error');
    }
  };

  // Details Modal (All 14 Metrics)
  window.openDetailsModal = function (id) {
    const m = cachedMonitors.find(item => item.id === id);
    if (!m) return;

    document.getElementById('details-title').textContent = `${m.name} — Comprehensive Telemetry`;
    const body = document.getElementById('details-body');
    if (!body) return;

    body.innerHTML = `
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; font-size: 12px;">
        <div class="infra-metric-row"><span>Service ID:</span> <strong class="mono">#${m.id}</strong></div>
        <div class="infra-metric-row"><span>Operational Status:</span> ${renderStatusBadge(m.currentStatus, m.enabled)}</div>
        <div class="infra-metric-row"><span>Target URL:</span> <a href="${escapeHtml(m.url)}" target="_blank" class="mono">${escapeHtml(truncate(m.url, 28))}</a></div>
        <div class="infra-metric-row"><span>Final URL (Redirects):</span> <span class="mono">${escapeHtml(m.finalUrl || m.url)}</span></div>
        <div class="infra-metric-row"><span>HTTP Status Code:</span> <span class="badge ${getStatusCodeBadgeClass(m.lastStatusCode)}">${m.lastStatusCode || '—'}</span></div>
        <div class="infra-metric-row"><span>Response Latency:</span> <strong class="mono">${m.lastResponseTimeMs != null ? m.lastResponseTimeMs + ' ms' : '—'}</strong></div>
        <div class="infra-metric-row"><span>DNS Resolution Status:</span> <span class="badge ${m.dnsStatus === 'RESOLVED' ? 'badge-up' : 'badge-down'}">${m.dnsStatus || '—'}</span></div>
        <div class="infra-metric-row"><span>DNS Lookup Duration:</span> <strong class="mono">${m.dnsLookupTimeMs != null ? m.dnsLookupTimeMs + ' ms' : '—'}</strong></div>
        <div class="infra-metric-row"><span>Connection Status:</span> <span class="mono">${escapeHtml(m.connectionStatus || '—')}</span></div>
        <div class="infra-metric-row"><span>SSL / TLS Certificate:</span> ${renderSslBadge(m.sslValid, m.sslDaysRemaining)}</div>
        <div class="infra-metric-row"><span>SSL Issuer:</span> <span>${escapeHtml(m.sslIssuer || '—')}</span></div>
        <div class="infra-metric-row"><span>SSL Expiry Date:</span> <span class="mono">${formatTime(m.sslExpiryDate) || '—'}</span></div>
        <div class="infra-metric-row"><span>SSL Days Remaining:</span> ${renderSslDays(m.sslDaysRemaining, m.sslValid)}</div>
        <div class="infra-metric-row"><span>Redirect Count:</span> <strong class="mono">${m.redirectCount || 0}</strong></div>
        <div class="infra-metric-row"><span>Consecutive Failures:</span> <strong class="mono" style="color: ${m.consecutiveFailures > 0 ? 'var(--color-down)' : 'var(--text-secondary)'};">${m.consecutiveFailures || 0}</strong></div>
        <div class="infra-metric-row"><span>24-Hour Uptime:</span> <strong class="mono" style="color: var(--color-up);">${m.uptimePercentage != null ? m.uptimePercentage + '%' : '100%'}</strong></div>
        <div class="infra-metric-row"><span>Last Successful Probe:</span> <span class="mono">${formatTime(m.lastSuccessfulCheckAt) || '—'}</span></div>
        <div class="infra-metric-row"><span>Last Failed Probe:</span> <span class="mono">${formatTime(m.lastFailedCheckAt) || '—'}</span></div>
      </div>
      ${m.errorMessage ? `
        <div style="margin-top: 14px; padding: 10px; background-color: var(--color-down-bg); border: 1px solid var(--color-down-border); border-radius: var(--radius-md); font-size: 11.5px; color: var(--color-down);">
          <strong>Last Diagnostic Error:</strong> ${escapeHtml(m.errorMessage)}
        </div>
      ` : ''}
    `;

    openModal('details-modal');
  };

  // Incident Lifecycle Modals
  window.openAcknowledgeModal = function (id) {
    document.getElementById('ack-incident-id').value = id;
    openModal('ack-modal');
  };

  window.submitAcknowledgeIncident = async function (e) {
    e.preventDefault();
    const id = document.getElementById('ack-incident-id')?.value;
    const note = document.getElementById('ack-note')?.value;
    try {
      await window.apiClient.acknowledgeIncident(id, note);
      showToast(`Incident #${id} acknowledged.`, 'success');
      closeModal('ack-modal');
      fetchIncidents();
    } catch (err) {
      showToast(`Failed to acknowledge: ${err.message}`, 'error');
    }
  };

  window.openResolveModal = function (id) {
    document.getElementById('resolve-incident-id').value = id;
    openModal('resolve-modal');
  };

  window.submitResolveIncident = async function (e) {
    e.preventDefault();
    const id = document.getElementById('resolve-incident-id')?.value;
    const note = document.getElementById('resolve-note')?.value;
    try {
      await window.apiClient.resolveIncident(id, note);
      showToast(`Incident #${id} marked as resolved!`, 'success');
      closeModal('resolve-modal');
      fetchIncidents();
    } catch (err) {
      showToast(`Failed to resolve: ${err.message}`, 'error');
    }
  };

  // Modal API Config
  window.saveModalApiConfig = function () {
    const val = document.getElementById('modal-api-url')?.value;
    window.apiClient.setBaseUrl(val);
    updateConnectionIndicator();
    showToast('API Configuration saved.', 'success');
    closeModal('api-modal');
    loadViewData(currentView);
  };

  window.testModalApiConnection = async function () {
    const resEl = document.getElementById('modal-api-ping-result');
    if (resEl) resEl.innerHTML = '<span style="color: var(--text-muted);">Testing ping...</span>';
    const res = await window.apiClient.ping();
    if (resEl) {
      if (res.online) {
        resEl.innerHTML = `<span style="color: var(--color-up);">✓ Pong! Backend is reachable (${res.latency} ms).</span>`;
      } else {
        resEl.innerHTML = `<span style="color: var(--color-down);">✗ Failed: ${escapeHtml(res.error)}</span>`;
      }
    }
  };

  /* ── UI Helpers & Badges ────────────────────────────────────────────────── */
  function renderStatusBadge(status, enabled) {
    if (!enabled) return '<span class="badge badge-paused">PAUSED</span>';
    switch (status) {
      case 'UP': return '<span class="badge badge-up">UP</span>';
      case 'DEGRADED': return '<span class="badge badge-degraded">DEGRADED</span>';
      case 'DOWN': return '<span class="badge badge-down">DOWN</span>';
      default: return '<span class="badge badge-unknown">UNKNOWN</span>';
    }
  }

  function getStatusCodeBadgeClass(code) {
    if (!code) return 'badge-unknown';
    if (code >= 200 && code < 300) return 'badge-up';
    if (code >= 300 && code < 400) return 'badge-accent';
    if (code >= 400 && code < 500) return 'badge-degraded';
    return 'badge-down';
  }

  function renderSslBadge(valid, days) {
    if (valid === null || valid === undefined) return '<span class="badge badge-unknown">N/A</span>';
    if (!valid) return '<span class="badge badge-down">INVALID</span>';
    if (days != null && days <= 14) return '<span class="badge badge-degraded">EXPIRING</span>';
    return '<span class="badge badge-up">VALID</span>';
  }

  function renderSslDays(days, valid) {
    if (days == null) return '<span style="color: var(--text-muted);">—</span>';
    if (days <= 0 || valid === false) {
      return '<span class="badge badge-down">Expired</span>';
    }
    if (days <= 14) {
      return `<span class="badge badge-degraded">${days}d</span>`;
    }
    return `<span class="badge badge-up">${days}d</span>`;
  }

  function renderUptimeBar(pct) {
    const val = pct != null ? pct : 100.0;
    const isGood = val >= 99.0;
    const isDegraded = val >= 95.0 && val < 99.0;
    const segClass = isGood ? '' : (isDegraded ? 'degraded' : 'down');

    return `
      <div style="display: flex; align-items: center; gap: 6px;">
        <div class="uptime-bar">
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
        </div>
        <span class="mono" style="font-size: 11px;">${val}%</span>
      </div>
    `;
  }

  function formatTime(isoString) {
    if (!isoString) return '—';
    try {
      const d = new Date(isoString);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return isoString;
    }
  }

  function formatDurationSeconds(sec) {
    if (!sec || sec < 0) return '0s';
    const hrs = Math.floor(sec / 3600);
    const mins = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    if (hrs > 0) return `${hrs}h ${mins}m ${s}s`;
    if (mins > 0) return `${mins}m ${s}s`;
    return `${s}s`;
  }

  function truncate(str, len) {
    if (!str) return '';
    return str.length > len ? str.slice(0, len) + '…' : str;
  }

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function setVal(id, val) {
    const el = document.getElementById(id);
    if (el) el.textContent = val;
  }

  function setBarWidth(id, pct) {
    const el = document.getElementById(id);
    if (el) el.style.width = `${Math.max(0, Math.min(100, pct))}%`;
  }

  function setHref(id, url) {
    const el = document.getElementById(id);
    if (el) el.href = url;
  }

  function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
      <span style="font-size: 14px;">${type === 'success' ? '✓' : (type === 'error' ? '✗' : 'ℹ')}</span>
      <span>${escapeHtml(message)}</span>
    `;

    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

})(window, document);
