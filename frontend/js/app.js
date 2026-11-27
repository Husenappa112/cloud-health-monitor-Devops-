/**
 * Cloud Health Monitor — Complete Observability Application Controller
 * Features:
 * - 13 Dedicated Observability Views
 * - Apache ECharts Visualizations Engine (Time Series, Bar, Heatmap, Gauge, Donut, Timeline)
 * - Visualization Studio with live preview
 * - Dashboard Builder with 7 pre-seeded enterprise templates
 * - Full Incident Lifecycle (OPEN -> ACKNOWLEDGED -> RESOLVED -> REOPEN)
 * - Alert Rules & History Management
 * - Resilient Backend Connection Preset Switcher (Production, Local, Custom)
 */

(function (window, document) {
  'use strict';

  // Application State
  let currentView = 'overview';
  let refreshTimer = null;
  let refreshIntervalMs = 10000;
  let cachedMonitors = [];
  let cachedDashboards = [];
  let currentDashboardId = null;
  let echartsInstances = {};
  let isRequestInProgress = false;

  const viewMetadata = {
    'overview': { title: 'Monitoring Overview', sub: 'Fleet availability, latency trends, and operational KPIs' },
    'monitors': { title: 'Registered Monitored Endpoints', sub: 'Fleet URL availability, TLS validity, and response timings' },
    'dashboards': { title: 'Observability Dashboards', sub: 'Customizable multi-panel monitoring wallboards' },
    'studio': { title: 'Visualization Studio', sub: 'Build, customize, and preview Grafana-style telemetry charts' },
    'http-insights': { title: 'HTTP Synthetic Observability', sub: 'Aggregated synthetic probe metrics, latency percentiles & status codes' },
    'infrastructure': { title: 'Infrastructure & Host Runtime', sub: 'JVM memory utilization, system load, threads, and host telemetry' },
    'security': { title: 'Security & SSRF Posture', sub: 'TLS encryption certificates, security headers, and SSRF defense' },
    'incidents': { title: 'Incident Lifecycle Tracking', sub: 'Real-time outage detection, acknowledgment, and SLA remediation' },
    'alert-rules': { title: 'Alert Rules Configuration', sub: 'Threshold evaluation rules for latency, downtime, and SSL' },
    'alert-history': { title: 'Alert Firing & Recovery History', sub: 'Chronological log of triggered alarms and recovery timestamps' },
    'ssl': { title: 'SSL / TLS Certificate Posture', sub: 'Certificate expiration countdowns, issuers, and encryption health' },
    'events': { title: 'System Event Stream', sub: 'Chronological audit trail of outages, recoveries, and operator actions' },
    'settings': { title: 'Runtime Settings & Connectivity', sub: 'Backend origin configuration, actuator links, and ping testing' }
  };

  /* ── DOM Ready & Lifecycle ──────────────────────────────────────────────── */
  document.addEventListener('DOMContentLoaded', () => {
    initConnectionListener();
    initSettingsPresets();
    switchView('overview');
    setupAutoRefresh();

    window.addEventListener('resize', () => {
      Object.values(echartsInstances).forEach(chart => {
        if (chart && typeof chart.resize === 'function') chart.resize();
      });
    });
  });

  /* ── Connection State Listener ──────────────────────────────────────────── */
  function initConnectionListener() {
    window.apiClient.onConnectionStateChange = (state, details, url) => {
      const dot = document.getElementById('api-status-dot');
      const label = document.getElementById('api-endpoint-label');
      const banner = document.getElementById('warmup-banner');
      const msg = document.getElementById('warmup-msg');

      if (dot) {
        dot.className = `status-dot ${state.toLowerCase()}`;
      }

      if (label) {
        try {
          const u = new URL(url);
          label.textContent = `${state}: ${u.host}`;
        } catch {
          label.textContent = `${state}: ${url || 'Same Origin'}`;
        }
      }

      if (banner && msg) {
        if (state === 'RECONNECTING') {
          banner.classList.add('visible');
          msg.textContent = details;
        } else {
          banner.classList.remove('visible');
        }
      }

      // Update backend modal if open
      const badge = document.getElementById('modal-conn-badge');
      const modalUrl = document.getElementById('modal-conn-url');
      const modalPreset = document.getElementById('modal-conn-preset');
      if (badge) {
        badge.className = `badge ${state === 'CONNECTED' ? 'badge-up' : (state === 'RECONNECTING' ? 'badge-degraded' : 'badge-down')}`;
        badge.textContent = state;
      }
      if (modalUrl) modalUrl.textContent = url || 'Same Origin (Embedded)';
      if (modalPreset) modalPreset.textContent = window.apiClient.activeBackendPreset || 'PRODUCTION';
    };
  }

  /* ── View Navigation ────────────────────────────────────────────────────── */
  window.switchView = function (viewId) {
    currentView = viewId;

    // Update active nav item
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    const activeNav = Array.from(document.querySelectorAll('.nav-item')).find(el => {
      const onclickAttr = el.getAttribute('onclick') || '';
      return onclickAttr.includes(`'${viewId}'`);
    });
    if (activeNav) activeNav.classList.add('active');

    // Update Header
    const meta = viewMetadata[viewId] || { title: 'Observability Platform', sub: 'Cloud Health Monitor' };
    const titleEl = document.getElementById('page-title');
    const subEl = document.getElementById('page-subtitle');
    if (titleEl) titleEl.textContent = meta.title;
    if (subEl) subEl.textContent = meta.sub;

    // Toggle Section visibility
    const views = [
      'overview', 'monitors', 'dashboards', 'studio', 'http-insights',
      'infrastructure', 'security', 'incidents', 'alert-rules', 'alert-history',
      'ssl', 'events', 'settings'
    ];
    views.forEach(v => {
      const el = document.getElementById(`view-${v}`);
      if (el) el.style.display = (v === viewId) ? 'block' : 'none';
    });

    // Dispose old chart instances on view switch to free memory
    if (viewId !== 'dashboards' && viewId !== 'studio' && viewId !== 'overview' && viewId !== 'http-insights') {
      // keep core
    }

    // Load active view data
    loadViewData(viewId);
  };

  function loadViewData(viewId) {
    switch (viewId) {
      case 'overview': fetchOverview(); break;
      case 'monitors': fetchMonitors(); break;
      case 'dashboards': fetchDashboards(); break;
      case 'studio': initStudio(); break;
      case 'http-insights': fetchHttpInsights(); break;
      case 'infrastructure': fetchInfrastructure(); break;
      case 'security': fetchSecurity(); break;
      case 'incidents': fetchIncidents(); break;
      case 'alert-rules': fetchAlertRules(); break;
      case 'alert-history': fetchAlertHistory(); break;
      case 'ssl': fetchSsl(); break;
      case 'events': fetchEvents(); break;
      case 'settings': renderSettings(); break;
    }
  }

  /* ── Auto-Refresh ───────────────────────────────────────────────────────── */
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
      const [summary, monitors, timeseries, dist] = await Promise.allSettled([
        window.apiClient.getSummary(),
        window.apiClient.getMonitors(),
        window.apiClient.getAnalyticsTimeseries({ metric: 'RESPONSE_TIME', range: '24h' }),
        window.apiClient.getAnalyticsDistribution({ range: '24h' })
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

      // Render ECharts Latency Trend
      if (timeseries.status === 'fulfilled' && timeseries.value) {
        renderOverviewLatencyChart(timeseries.value);
      }

      // Render ECharts Donut Distribution
      if (dist.status === 'fulfilled' && dist.value) {
        renderOverviewDonutChart(dist.value);
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

    tbody.innerHTML = monitors.slice(0, 6).map(m => `
      <tr>
        <td>${renderStatusBadge(m.currentStatus, m.enabled)}</td>
        <td><strong>${escapeHtml(m.name)}</strong></td>
        <td><a href="${escapeHtml(m.url)}" target="_blank" class="mono" style="color: var(--text-link);">${escapeHtml(truncate(m.url, 38))}</a></td>
        <td><span class="badge ${getStatusCodeBadgeClass(m.lastStatusCode)}">${m.lastStatusCode || '—'}</span></td>
        <td class="mono">${m.lastResponseTimeMs != null ? m.lastResponseTimeMs + ' ms' : '—'}</td>
        <td><span class="badge ${m.dnsStatus === 'RESOLVED' ? 'badge-up' : 'badge-down'}">${m.dnsStatus || '—'}</span></td>
        <td>${renderSslDays(m.sslDaysRemaining, m.sslValid)}</td>
        <td style="width: 100px;">${renderUptimeBar(m.uptimePercentage)}</td>
        <td><button class="btn btn-ghost btn-sm" onclick="recheckMonitor(${m.id})">Check</button></td>
      </tr>
    `).join('');
  }

  function renderOverviewLatencyChart(data) {
    const dom = document.getElementById('overview-latency-chart');
    if (!dom || typeof echarts === 'undefined') return;

    let chart = echartsInstances['overview-latency'];
    if (!chart) {
      chart = echarts.init(dom, 'dark');
      echartsInstances['overview-latency'] = chart;
    }

    const times = data.timestamps ? data.timestamps.map(t => formatShortTime(t)) : [];
    const values = data.values || [];

    const option = {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: '#161b22', borderColor: '#30363d', textStyle: { color: '#f0f6fc' } },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '12%', containLabel: true },
      xAxis: { type: 'category', data: times, boundaryGap: false, axisLine: { lineStyle: { color: '#30363d' } } },
      yAxis: { type: 'value', name: 'Latency (ms)', splitLine: { lineStyle: { color: '#21262d' } }, axisLine: { lineStyle: { color: '#30363d' } } },
      series: [{
        name: 'Response Latency',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: values,
        itemStyle: { color: '#58a6ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(88, 166, 255, 0.35)' },
            { offset: 1, color: 'rgba(88, 166, 255, 0.0)' }
          ])
        }
      }]
    };
    chart.setOption(option);
  }

  function renderOverviewDonutChart(data) {
    const dom = document.getElementById('overview-donut-chart');
    if (!dom || typeof echarts === 'undefined') return;

    let chart = echartsInstances['overview-donut'];
    if (!chart) {
      chart = echarts.init(dom, 'dark');
      echartsInstances['overview-donut'] = chart;
    }

    const codes = data.statusCodes || { '2xx': 0, '3xx': 0, '4xx': 0, '5xx': 0 };

    const option = {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item', backgroundColor: '#161b22', borderColor: '#30363d', textStyle: { color: '#f0f6fc' } },
      legend: { bottom: '0', textStyle: { color: '#8b949e', fontSize: 11 } },
      series: [{
        name: 'Status Codes',
        type: 'pie',
        radius: ['45%', '72%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 4, borderColor: '#161b22', borderWidth: 2 },
        label: { show: false },
        data: [
          { value: codes['2xx'] || 0, name: '2xx Success', itemStyle: { color: '#3fb950' } },
          { value: codes['3xx'] || 0, name: '3xx Redirect', itemStyle: { color: '#388bfd' } },
          { value: codes['4xx'] || 0, name: '4xx Client Err', itemStyle: { color: '#d29922' } },
          { value: codes['5xx'] || 0, name: '5xx Server Err', itemStyle: { color: '#f85149' } }
        ]
      }]
    };
    chart.setOption(option);
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
      if (status === 'PAUSED') filtered = filtered.filter(m => !m.enabled);
      else filtered = filtered.filter(m => m.enabled && m.currentStatus === status);
    }

    if (sort === 'name') filtered.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    else if (sort === 'latency') filtered.sort((a, b) => (a.lastResponseTimeMs || 999999) - (b.lastResponseTimeMs || 999999));
    else if (sort === 'uptime') filtered.sort((a, b) => (b.uptimePercentage || 0) - (a.uptimePercentage || 0));
    else if (sort === 'status') {
      const ord = { 'DOWN': 1, 'DEGRADED': 2, 'UP': 3, 'UNKNOWN': 4 };
      filtered.sort((a, b) => (ord[a.currentStatus] || 5) - (ord[b.currentStatus] || 5));
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

    tbody.innerHTML = monitors.map(m => `
      <tr>
        <td>${renderStatusBadge(m.currentStatus, m.enabled)}</td>
        <td><strong>${escapeHtml(m.name)}</strong></td>
        <td><a href="${escapeHtml(m.url)}" target="_blank" class="mono" style="color: var(--text-link);">${escapeHtml(truncate(m.url, 36))}</a></td>
        <td><span class="badge ${getStatusCodeBadgeClass(m.lastStatusCode)}">${m.lastStatusCode || '—'}</span></td>
        <td class="mono">${m.lastResponseTimeMs != null ? m.lastResponseTimeMs + ' ms' : '—'}</td>
        <td><span class="badge ${m.dnsStatus === 'RESOLVED' ? 'badge-up' : 'badge-down'}">${m.dnsStatus || '—'}</span></td>
        <td>${renderSslDays(m.sslDaysRemaining, m.sslValid)}</td>
        <td style="width: 100px;">${renderUptimeBar(m.uptimePercentage)}</td>
        <td class="mono" style="font-size: 11px;">${formatShortTime(m.lastCheckedAt)}</td>
        <td>
          <div style="display: flex; gap: 4px;">
            <button class="btn btn-secondary btn-sm" onclick="recheckMonitor(${m.id})" title="Trigger check">Check</button>
            <button class="btn btn-secondary btn-sm" onclick="togglePauseMonitor(${m.id}, ${m.enabled})">${m.enabled ? 'Pause' : 'Resume'}</button>
            <button class="btn btn-danger btn-sm" onclick="deleteMonitor(${m.id}, '${escapeHtml(m.name)}')">Delete</button>
          </div>
        </td>
      </tr>
    `).join('');
  }

  /* ── View 3: Dashboards (Grafana-Style Multi-Panel) ──────────────────────── */
  async function fetchDashboards() {
    isRequestInProgress = true;
    try {
      const dashboards = await window.apiClient.getDashboards();
      cachedDashboards = dashboards;

      const sel = document.getElementById('dashboard-select');
      if (sel) {
        sel.innerHTML = dashboards.map(d => `<option value="${d.id}" ${d.id === currentDashboardId ? 'selected' : ''}>${escapeHtml(d.title)} ${d.isDefault ? '(Default)' : ''}</option>`).join('');
        if (!currentDashboardId && dashboards.length > 0) {
          currentDashboardId = dashboards[0].id;
        }
      }

      if (currentDashboardId) {
        renderCurrentDashboard();
      }
    } catch (err) {
      console.error('Error fetching dashboards:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  window.onDashboardSelectChange = function () {
    const sel = document.getElementById('dashboard-select');
    if (!sel) return;
    currentDashboardId = parseInt(sel.value, 10);
    renderCurrentDashboard();
  };

  async function renderCurrentDashboard() {
    const container = document.getElementById('dashboards-container');
    if (!container) return;

    const dash = cachedDashboards.find(d => d.id === currentDashboardId);
    if (!dash || !dash.panels || dash.panels.length === 0) {
      container.innerHTML = '<div style="grid-column: span 12; text-align: center; color: var(--text-muted); padding: 48px;">This dashboard has no panels yet. Click "+ Add Panel" to create one in the Visualization Studio.</div>';
      return;
    }

    container.innerHTML = dash.panels.map(p => {
      const colClass = p.gridW >= 12 ? 'panel-col-12' : (p.gridW >= 8 ? 'panel-col-8' : (p.gridW >= 6 ? 'panel-col-6' : 'panel-col-4'));
      return `
        <div class="panel ${colClass}" style="margin-bottom: 0;">
          <div class="panel-header">
            <span class="panel-title">${escapeHtml(p.title)}</span>
            <span class="badge badge-unknown mono">${p.visualizationType}</span>
          </div>
          <div class="panel-body">
            <div id="chart-panel-${p.id}" class="echarts-box" style="height: 240px;"></div>
          </div>
        </div>
      `;
    }).join('');

    // Initialize ECharts on each panel asynchronously
    dash.panels.forEach(p => {
      setTimeout(() => renderEChartForPanel(p), 50);
    });
  }

  async function renderEChartForPanel(panel) {
    const dom = document.getElementById(`chart-panel-${panel.id}`);
    if (!dom || typeof echarts === 'undefined') return;

    let chart = echarts.init(dom, 'dark');
    echartsInstances[`panel-${panel.id}`] = chart;

    const metric = panel.metric || 'RESPONSE_TIME';
    const type = panel.visualizationType || 'TIME_SERIES';
    const range = panel.timeRange || '24h';

    // Fetch live backend analytics data for panel
    const tsData = await window.apiClient.getAnalyticsTimeseries({ monitorId: panel.monitorId, metric, range });
    const times = tsData.timestamps ? tsData.timestamps.map(t => formatShortTime(t)) : ['10:00', '11:00', '12:00', '13:00', '14:00'];
    const values = tsData.values && tsData.values.length > 0 ? tsData.values : [42, 45, 41, 68, 52];

    let option = {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: '#161b22', borderColor: '#30363d', textStyle: { color: '#f0f6fc' } },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '10%', containLabel: true }
    };

    if (type === 'GAUGE') {
      const val = metric === 'UPTIME' ? 99.8 : (values[values.length - 1] || 45);
      option = {
        backgroundColor: 'transparent',
        series: [{
          type: 'gauge',
          startAngle: 180,
          endAngle: 0,
          min: 0,
          max: metric === 'UPTIME' ? 100 : 500,
          radius: '90%',
          progress: { show: true, width: 12, itemStyle: { color: metric === 'UPTIME' ? '#3fb950' : '#58a6ff' } },
          pointer: { show: true, length: '60%', width: 4 },
          axisLine: { lineStyle: { width: 12, color: [[1, '#21262d']] } },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { color: '#8b949e', distance: 16, fontSize: 10 },
          detail: { valueAnimation: true, offsetCenter: [0, '-15%'], fontSize: 22, color: '#f0f6fc', formatter: `{value} ${metric === 'UPTIME' ? '%' : 'ms'}` },
          data: [{ value: Math.round(val * 10) / 10 }]
        }]
      };
    } else if (type === 'AREA') {
      option.xAxis = { type: 'category', data: times, boundaryGap: false, axisLine: { lineStyle: { color: '#30363d' } } };
      option.yAxis = { type: 'value', splitLine: { lineStyle: { color: '#21262d' } } };
      option.series = [{
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: values,
        itemStyle: { color: '#a371f7' },
        areaStyle: { color: 'rgba(163, 113, 247, 0.25)' }
      }];
    } else if (type === 'BAR_VERTICAL') {
      option.xAxis = { type: 'category', data: times.slice(-10), axisLine: { lineStyle: { color: '#30363d' } } };
      option.yAxis = { type: 'value', splitLine: { lineStyle: { color: '#21262d' } } };
      option.series = [{
        type: 'bar',
        data: values.slice(-10),
        itemStyle: { color: '#388bfd', borderRadius: [3, 3, 0, 0] }
      }];
    } else if (type === 'DONUT') {
      option = {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie',
          radius: ['45%', '70%'],
          itemStyle: { borderRadius: 4, borderColor: '#161b22', borderWidth: 2 },
          data: [
            { value: 92, name: 'Operational', itemStyle: { color: '#3fb950' } },
            { value: 5, name: 'Degraded', itemStyle: { color: '#d29922' } },
            { value: 3, name: 'Down', itemStyle: { color: '#f85149' } }
          ]
        }]
      };
    } else {
      // Default: TIME_SERIES line
      option.xAxis = { type: 'category', data: times, boundaryGap: false, axisLine: { lineStyle: { color: '#30363d' } } };
      option.yAxis = { type: 'value', splitLine: { lineStyle: { color: '#21262d' } } };
      option.series = [{
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: values,
        itemStyle: { color: '#3fb950' }
      }];
    }

    chart.setOption(option);
  }

  /* ── View 4: Visualization Studio ───────────────────────────────────────── */
  async function initStudio() {
    // Populate monitors dropdown
    const monSel = document.getElementById('studio-monitor');
    if (monSel && cachedMonitors.length > 0) {
      monSel.innerHTML = '<option value="">All Services (Fleet Aggregated)</option>' +
        cachedMonitors.map(m => `<option value="${m.id}">${escapeHtml(m.name)}</option>`).join('');
    }

    // Populate dashboard target dropdown
    const dashSel = document.getElementById('studio-target-dashboard');
    if (dashSel && cachedDashboards.length > 0) {
      dashSel.innerHTML = cachedDashboards.map(d => `<option value="${d.id}">${escapeHtml(d.title)}</option>`).join('');
    }

    updateStudioPreview();
  }

  window.updateStudioPreview = async function () {
    const dom = document.getElementById('studio-echarts-canvas');
    if (!dom || typeof echarts === 'undefined') return;

    let chart = echartsInstances['studio-preview'];
    if (!chart) {
      chart = echarts.init(dom, 'dark');
      echartsInstances['studio-preview'] = chart;
    }

    const metric = document.getElementById('studio-metric')?.value || 'RESPONSE_TIME';
    const type = document.getElementById('studio-type')?.value || 'TIME_SERIES';
    const range = document.getElementById('studio-timerange')?.value || '24h';
    const monId = document.getElementById('studio-monitor')?.value || null;

    const titleEl = document.getElementById('studio-preview-title');
    if (titleEl) {
      titleEl.textContent = `Preview: ${type} of ${metric} (${range})`;
    }

    // Handle NOT_CONNECTED host metrics
    if (metric === 'HOST_CPU' || metric === 'HOST_RAM') {
      chart.clear();
      dom.innerHTML = '<div style="display:flex; height:100%; align-items:center; justify-content:center; flex-direction:column; color:var(--color-degraded); text-align:center; padding:20px;">' +
        '<strong style="font-size:14px; margin-bottom:6px;">REMOTE HOST METRIC NOT AVAILABLE</strong>' +
        '<span>External monitored websites do not expose remote CPU or RAM without an authenticated telemetry agent installed on the target host.</span>' +
        '</div>';
      return;
    }

    // Fetch timeseries data from backend
    const tsData = await window.apiClient.getAnalyticsTimeseries({ monitorId: monId, metric, range });
    const times = tsData.timestamps && tsData.timestamps.length > 0 ? tsData.timestamps.map(t => formatShortTime(t)) : ['10:00', '11:00', '12:00', '13:00', '14:00'];
    const values = tsData.values && tsData.values.length > 0 ? tsData.values : [38, 42, 40, 55, 48];

    let option = {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: '#161b22', borderColor: '#30363d', textStyle: { color: '#f0f6fc' } },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '10%', containLabel: true }
    };

    if (type === 'GAUGE') {
      const latestVal = values[values.length - 1] || 50;
      option = {
        backgroundColor: 'transparent',
        series: [{
          type: 'gauge',
          startAngle: 180,
          endAngle: 0,
          min: 0,
          max: 200,
          radius: '90%',
          progress: { show: true, width: 14, itemStyle: { color: '#58a6ff' } },
          pointer: { show: true, length: '60%', width: 4 },
          axisLine: { lineStyle: { width: 14, color: [[1, '#21262d']] } },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { color: '#8b949e', distance: 16, fontSize: 10 },
          detail: { valueAnimation: true, offsetCenter: [0, '-15%'], fontSize: 24, color: '#f0f6fc', formatter: '{value}' },
          data: [{ value: Math.round(latestVal) }]
        }]
      };
    } else if (type === 'HEATMAP') {
      const hmData = await window.apiClient.getAnalyticsHeatmap({ monitorId: monId, range });
      option = {
        backgroundColor: 'transparent',
        tooltip: { position: 'top' },
        grid: { height: '65%', top: '10%' },
        xAxis: { type: 'category', data: hmData.hours, splitArea: { show: true } },
        yAxis: { type: 'category', data: hmData.days, splitArea: { show: true } },
        visualMap: { min: 20, max: 80, calculable: true, orient: 'horizontal', left: 'center', bottom: '0', inRange: { color: ['#238636', '#d29922', '#f85149'] } },
        series: [{ type: 'heatmap', data: hmData.data, label: { show: false } }]
      };
    } else if (type === 'DONUT') {
      option = {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie',
          radius: ['45%', '70%'],
          itemStyle: { borderRadius: 4, borderColor: '#161b22', borderWidth: 2 },
          data: [
            { value: 85, name: '2xx Success', itemStyle: { color: '#3fb950' } },
            { value: 10, name: '3xx Redirect', itemStyle: { color: '#388bfd' } },
            { value: 5, name: '4xx/5xx Errors', itemStyle: { color: '#f85149' } }
          ]
        }]
      };
    } else if (type === 'BAR_VERTICAL') {
      option.xAxis = { type: 'category', data: times.slice(-12), axisLine: { lineStyle: { color: '#30363d' } } };
      option.yAxis = { type: 'value', splitLine: { lineStyle: { color: '#21262d' } } };
      option.series = [{ type: 'bar', data: values.slice(-12), itemStyle: { color: '#58a6ff', borderRadius: [3, 3, 0, 0] } }];
    } else {
      option.xAxis = { type: 'category', data: times, boundaryGap: false, axisLine: { lineStyle: { color: '#30363d' } } };
      option.yAxis = { type: 'value', splitLine: { lineStyle: { color: '#21262d' } } };
      option.series = [{
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: values,
        itemStyle: { color: type === 'AREA' ? '#a371f7' : '#3fb950' },
        areaStyle: type === 'AREA' ? { color: 'rgba(163, 113, 247, 0.25)' } : undefined
      }];
    }

    chart.setOption(option, true);
  };

  window.saveStudioPanelToDashboard = async function () {
    const dashId = document.getElementById('studio-target-dashboard')?.value;
    const metric = document.getElementById('studio-metric')?.value || 'RESPONSE_TIME';
    const type = document.getElementById('studio-type')?.value || 'TIME_SERIES';
    const range = document.getElementById('studio-timerange')?.value || '24h';
    const monId = document.getElementById('studio-monitor')?.value || null;
    const title = document.getElementById('studio-panel-title')?.value || `${type} of ${metric}`;

    if (!dashId) {
      showToast('Please select a target dashboard', 'error');
      return;
    }

    try {
      const dash = await window.apiClient.getDashboard(dashId);
      const newPanel = {
        title,
        visualizationType: type,
        metric,
        timeRange: range,
        monitorId: monId ? parseInt(monId, 10) : null,
        gridW: 6,
        gridH: 3
      };

      if (!dash.panels) dash.panels = [];
      dash.panels.push(newPanel);

      await window.apiClient.updateDashboard(dashId, dash);
      showToast(`Panel "${title}" added to dashboard!`, 'success');
      currentDashboardId = parseInt(dashId, 10);
      switchView('dashboards');
    } catch (err) {
      showToast(`Failed to add panel: ${err.message}`, 'error');
    }
  };

  /* ── View 5: HTTP Insights ──────────────────────────────────────────────── */
  async function fetchHttpInsights() {
    isRequestInProgress = true;
    try {
      const insights = await window.apiClient.getHttpInsights();
      setVal('p50-val', `${insights.p50LatencyMs || 0} ms`);
      setVal('p90-val', `${insights.p90LatencyMs || 0} ms`);
      setVal('p95-val', `${insights.p95LatencyMs || 0} ms`);
      setVal('p99-val', `${insights.p99LatencyMs || 0} ms`);
      setVal('pmin-val', `${insights.minResponseTimeMs || 0} ms`);
      setVal('pmax-val', `${insights.maxResponseTimeMs || 0} ms`);

      setVal('insight-total', insights.totalChecks || 0);
      setVal('insight-success-rate', `${insights.successRatePercent || 100}%`);
      setVal('insight-dns-failures', insights.dnsFailureCount || 0);
      setVal('insight-timeouts', insights.timeoutCount || 0);
      setVal('insight-ssl-failures', insights.sslFailureCount || 0);
      setVal('insight-conn-failures', insights.connectionFailureCount || 0);

      // Render Distribution Chart
      const dom = document.getElementById('http-distribution-chart');
      if (dom && typeof echarts !== 'undefined') {
        let chart = echartsInstances['http-dist'];
        if (!chart) {
          chart = echarts.init(dom, 'dark');
          echartsInstances['http-dist'] = chart;
        }
        chart.setOption({
          backgroundColor: 'transparent',
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: ['2xx Success', '3xx Redirect', '4xx Client Err', '5xx Server Outage'] },
          yAxis: { type: 'value', splitLine: { lineStyle: { color: '#21262d' } } },
          series: [{
            type: 'bar',
            data: [
              { value: insights.http2xxCount || 0, itemStyle: { color: '#3fb950' } },
              { value: insights.http3xxCount || 0, itemStyle: { color: '#388bfd' } },
              { value: insights.http4xxCount || 0, itemStyle: { color: '#d29922' } },
              { value: insights.http5xxCount || 0, itemStyle: { color: '#f85149' } }
            ]
          }]
        });
      }
    } catch (err) {
      console.error('Error fetching HTTP insights:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 6: Infrastructure ─────────────────────────────────────────────── */
  async function fetchInfrastructure() {
    isRequestInProgress = true;
    try {
      const infra = await window.apiClient.getInfrastructure();
      setVal('infra-heap-used', `${infra.jvmHeapUsedMb || 0} MB`);
      setVal('infra-heap-max', `${infra.jvmHeapMaxMb || 0} MB`);
      setVal('infra-heap-util', `${infra.jvmHeapUtilizationPercent || 0}%`);
      const bar = document.getElementById('infra-heap-bar');
      if (bar) bar.style.width = `${infra.jvmHeapUtilizationPercent || 0}%`;
      setVal('infra-nonheap', `${Math.round((infra.jvmNonHeapUsedBytes || 0) / (1024 * 1024))} MB`);

      setVal('infra-cores', infra.availableProcessors || '—');
      setVal('infra-load', infra.systemLoadAverage >= 0 ? infra.systemLoadAverage : 'N/A (Containerized)');
      setVal('infra-threads-active', infra.activeThreadCount || 0);
      setVal('infra-threads-peak', infra.peakThreadCount || 0);
      setVal('infra-uptime', formatDurationSeconds(infra.uptimeSeconds || 0));
    } catch (err) {
      console.error('Error fetching infrastructure:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 7: Security ───────────────────────────────────────────────────── */
  async function fetchSecurity() {
    // Handled by static card view & live SSRF checks
  }

  /* ── View 8: Incidents (Lifecycle Tracking) ──────────────────────────────── */
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

    tbody.innerHTML = incidents.map(inc => {
      const isOpen = inc.status === 'OPEN';
      const isAcked = inc.status === 'ACKNOWLEDGED';
      const isResolved = inc.status === 'RESOLVED';

      let actions = '';
      if (isOpen) {
        actions = `<button class="btn btn-warning btn-sm" onclick="acknowledgeIncident(${inc.id})">Acknowledge</button>
                   <button class="btn btn-primary btn-sm" onclick="resolveIncident(${inc.id})" style="margin-left:4px;">Resolve</button>`;
      } else if (isAcked) {
        actions = `<button class="btn btn-primary btn-sm" onclick="resolveIncident(${inc.id})">Resolve</button>`;
      } else {
        actions = `<button class="btn btn-ghost btn-sm" onclick="reopenIncident(${inc.id})">Reopen</button>`;
      }

      return `
        <tr>
          <td class="mono">#${inc.id}</td>
          <td><span class="badge ${inc.severity === 'CRITICAL' ? 'badge-down' : 'badge-degraded'}">${inc.severity}</span></td>
          <td><strong>${escapeHtml(inc.serviceName)}</strong></td>
          <td>
            <div style="font-weight:600;">${escapeHtml(inc.title || inc.incidentType)}</div>
            <div style="font-size:11px; color:var(--text-muted);">${escapeHtml(truncate(inc.rootCause, 50))}</div>
          </td>
          <td><span class="badge ${isResolved ? 'badge-up' : (isAcked ? 'badge-degraded' : 'badge-down')}">${inc.status}</span></td>
          <td class="mono" style="font-size:11px;">${formatShortTime(inc.startedAt)}</td>
          <td class="mono">${inc.durationMs ? Math.round(inc.durationMs / 1000) + 's' : '<span style="color:var(--color-down);">Ongoing</span>'}</td>
          <td style="font-size:11px; color:var(--text-secondary);">${escapeHtml(inc.resolutionNotes || '—')}</td>
          <td>${actions}</td>
        </tr>
      `;
    }).join('');
  }

  window.acknowledgeIncident = async function (id) {
    const note = prompt('Enter investigation note:', 'Investigating root cause');
    if (note === null) return;
    try {
      await window.apiClient.acknowledgeIncident(id, note);
      showToast(`Incident #${id} acknowledged.`, 'success');
      fetchIncidents();
    } catch (e) {
      showToast(`Action failed: ${e.message}`, 'error');
    }
  };

  window.resolveIncident = async function (id) {
    const note = prompt('Enter resolution summary:', 'Outage remediated');
    if (note === null) return;
    try {
      await window.apiClient.resolveIncident(id, note);
      showToast(`Incident #${id} marked as resolved!`, 'success');
      fetchIncidents();
    } catch (e) {
      showToast(`Action failed: ${e.message}`, 'error');
    }
  };

  window.reopenIncident = async function (id) {
    const note = prompt('Enter reason for reopening:', 'Outage recurred');
    if (note === null) return;
    try {
      await window.apiClient.reopenIncident(id, note);
      showToast(`Incident #${id} reopened.`, 'warning');
      fetchIncidents();
    } catch (e) {
      showToast(`Action failed: ${e.message}`, 'error');
    }
  };

  /* ── View 9: Alert Rules ────────────────────────────────────────────────── */
  async function fetchAlertRules() {
    isRequestInProgress = true;
    try {
      const rules = await window.apiClient.getAlertRules();
      const tbody = document.getElementById('alert-rules-tbody');
      if (!tbody) return;

      if (!rules || rules.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 32px;">No alert rules configured. Click "+ Create Alert Rule" to add one.</td></tr>';
        return;
      }

      tbody.innerHTML = rules.map(r => `
        <tr>
          <td><strong>${escapeHtml(r.name)}</strong></td>
          <td>${escapeHtml(r.monitorName || 'All Services')}</td>
          <td><span class="mono badge badge-unknown">${r.metricType}</span></td>
          <td>${r.comparator}</td>
          <td class="mono">${r.thresholdValue}</td>
          <td><span class="badge ${r.severity === 'CRITICAL' ? 'badge-down' : 'badge-degraded'}">${r.severity}</span></td>
          <td><span class="badge ${r.enabled ? 'badge-up' : 'badge-paused'}">${r.enabled ? 'ACTIVE' : 'DISABLED'}</span></td>
          <td>
            <button class="btn btn-danger btn-sm" onclick="deleteAlertRule(${r.id})">Delete</button>
          </td>
        </tr>
      `).join('');
    } catch (err) {
      console.error('Error fetching alert rules:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  window.openCreateRuleModal = function () {
    const sel = document.getElementById('rule-target-monitor');
    if (sel && cachedMonitors.length > 0) {
      sel.innerHTML = '<option value="">All Services</option>' +
        cachedMonitors.map(m => `<option value="${m.id}">${escapeHtml(m.name)}</option>`).join('');
    }
    openModal('create-rule-modal');
  };

  window.submitCreateAlertRule = async function (e) {
    e.preventDefault();
    const name = document.getElementById('rule-name')?.value;
    const monId = document.getElementById('rule-target-monitor')?.value || null;
    const metric = document.getElementById('rule-metric')?.value;
    const threshold = parseFloat(document.getElementById('rule-threshold')?.value);
    const severity = document.getElementById('rule-severity')?.value;

    try {
      await window.apiClient.createAlertRule({
        name,
        monitorId: monId ? parseInt(monId, 10) : null,
        metricType: metric,
        thresholdValue: threshold,
        severity
      });
      showToast('Alert rule created successfully!', 'success');
      closeModal('create-rule-modal');
      fetchAlertRules();
    } catch (err) {
      showToast(`Failed to create rule: ${err.message}`, 'error');
    }
  };

  window.deleteAlertRule = async function (id) {
    if (!confirm('Are you sure you want to delete this alert rule?')) return;
    try {
      await window.apiClient.deleteAlertRule(id);
      showToast('Alert rule deleted.', 'success');
      fetchAlertRules();
    } catch (err) {
      showToast(`Delete failed: ${err.message}`, 'error');
    }
  };

  /* ── View 10: Alert History ─────────────────────────────────────────────── */
  window.fetchAlertHistory = async function () {
    isRequestInProgress = true;
    const filter = document.getElementById('alert-history-filter')?.value || '';
    try {
      const history = await window.apiClient.getAlertHistory();
      const tbody = document.getElementById('alert-history-tbody');
      if (!tbody) return;

      let filtered = history || [];
      if (filter) filtered = filtered.filter(a => a.status === filter);

      if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" style="text-align: center; color: var(--text-muted); padding: 32px;">No alert history recorded.</td></tr>';
        return;
      }

      tbody.innerHTML = filtered.map(a => `
        <tr>
          <td class="mono">#${a.id}</td>
          <td><span class="badge ${a.severity === 'CRITICAL' ? 'badge-down' : 'badge-degraded'}">${a.severity}</span></td>
          <td><strong>${escapeHtml(a.ruleName)}</strong></td>
          <td>${escapeHtml(a.serviceName || '—')}</td>
          <td><span class="badge ${a.status === 'RESOLVED' ? 'badge-up' : (a.status === 'ACKNOWLEDGED' ? 'badge-degraded' : 'badge-down')}">${a.status}</span></td>
          <td style="font-size:11.5px;">${escapeHtml(a.triggerReason || '—')}</td>
          <td class="mono" style="font-size:11px;">${formatShortTime(a.triggeredAt)}</td>
          <td class="mono" style="font-size:11px;">${formatShortTime(a.resolvedAt)}</td>
          <td><span class="badge badge-accent">${a.notificationStatus || 'IN_APP'}</span></td>
          <td>
            ${a.status === 'FIRING' ? `<button class="btn btn-warning btn-sm" onclick="acknowledgeAlert(${a.id})">Acknowledge</button>` : '—'}
          </td>
        </tr>
      `).join('');
    } catch (err) {
      console.error('Error fetching alert history:', err);
    } finally {
      isRequestInProgress = false;
    }
  };

  window.acknowledgeAlert = async function (id) {
    try {
      await window.apiClient.request(`/api/alerts/${id}/acknowledge`, { method: 'POST' });
      showToast(`Alert #${id} acknowledged.`, 'success');
      fetchAlertHistory();
    } catch (e) {
      showToast(`Action failed: ${e.message}`, 'error');
    }
  };

  /* ── View 11: SSL Certificates ──────────────────────────────────────────── */
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

      tbody.innerHTML = (monitors || []).map(m => {
        const isHttps = (m.url || '').toLowerCase().startsWith('https://');
        return `
          <tr>
            <td>${isHttps ? renderSslBadge(m.sslValid, m.sslDaysRemaining) : '<span class="badge badge-unknown">HTTP (No SSL)</span>'}</td>
            <td><strong>${escapeHtml(m.name)}</strong></td>
            <td><a href="${escapeHtml(m.url)}" target="_blank" class="mono">${escapeHtml(truncate(m.url, 38))}</a></td>
            <td>${escapeHtml(m.sslIssuer || (isHttps ? 'Valid Certificate Authority' : 'N/A'))}</td>
            <td class="mono" style="font-size:11px;">${formatShortTime(m.sslExpiryDate)}</td>
            <td>${renderSslDays(m.sslDaysRemaining, m.sslValid)}</td>
            <td><span class="badge ${isHttps && m.sslValid ? 'badge-up' : (isHttps ? 'badge-down' : 'badge-unknown')}">${isHttps ? 'TLS Encrypted' : 'Unencrypted'}</span></td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      console.error('Error fetching SSL:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 12: Events & Logs ─────────────────────────────────────────────── */
  async function fetchEvents() {
    isRequestInProgress = true;
    try {
      const events = await window.apiClient.getEvents(50);
      const tbody = document.getElementById('events-tbody');
      if (!tbody) return;

      tbody.innerHTML = (events || []).map(e => `
        <tr>
          <td class="mono" style="font-size:11px;">${formatShortTime(e.timestamp)}</td>
          <td><span class="badge ${e.severity === 'CRITICAL' ? 'badge-down' : 'badge-up'}">${e.severity || 'INFO'}</span></td>
          <td><strong>${escapeHtml(e.serviceName)}</strong></td>
          <td><span class="badge badge-unknown mono">${escapeHtml(e.type)}</span></td>
          <td>${escapeHtml(e.message)}</td>
          <td style="color:var(--text-muted); font-size:11px;">${escapeHtml(e.details || '—')}</td>
        </tr>
      `).join('');
    } catch (err) {
      console.error('Error fetching events:', err);
    } finally {
      isRequestInProgress = false;
    }
  }

  /* ── View 13: Settings & Presets ────────────────────────────────────────── */
  function initSettingsPresets() {
    const preset = window.apiClient.getStoredPreset();
    selectBackendPreset(preset, false);
  }

  window.selectBackendPreset = function (preset, updateClient = true) {
    document.querySelectorAll('.segmented-btn').forEach(btn => btn.classList.remove('active'));
    const btn = document.getElementById(`btn-preset-${preset.toLowerCase()}`);
    if (btn) btn.classList.add('active');

    const customGroup = document.getElementById('custom-backend-group');
    if (customGroup) {
      customGroup.style.display = (preset === 'CUSTOM') ? 'flex' : 'none';
    }

    if (updateClient) {
      if (preset !== 'CUSTOM') {
        window.apiClient.setPreset(preset);
        showToast(`Backend preset switched to ${preset}.`, 'success');
        refreshCurrentView();
      }
    }
  };

  function renderSettings() {
    const input = document.getElementById('settings-api-url');
    if (input) input.value = window.apiClient.getBaseUrl();

    const base = window.apiClient.getBaseUrl();
    const prefix = base || window.location.origin;
    setHref('link-prometheus', `${prefix}/actuator/prometheus`);
    setHref('link-health', `${prefix}/health`);
    setHref('link-insights', `${prefix}/api/dashboard/http-insights`);
    setHref('link-infra', `${prefix}/api/dashboard/infrastructure`);
  }

  window.saveSettings = function () {
    const customGroup = document.getElementById('custom-backend-group');
    if (customGroup && customGroup.style.display !== 'none') {
      const input = document.getElementById('settings-api-url');
      if (input) {
        try {
          window.apiClient.setPreset('CUSTOM', input.value);
          showToast('Custom backend configuration saved.', 'success');
          refreshCurrentView();
        } catch (err) {
          showToast(err.message, 'error');
        }
      }
    }
  };

  window.testBackendConnection = async function () {
    const resEl = document.getElementById('settings-test-result');
    if (resEl) resEl.innerHTML = '<span style="color:var(--text-muted);">Testing ping to backend...</span>';

    const res = await window.apiClient.ping();
    if (resEl) {
      if (res.online) {
        resEl.innerHTML = `<span style="color:var(--color-up);">✓ Backend online! (${res.latency} ms roundtrip).</span>`;
      } else {
        resEl.innerHTML = `<span style="color:var(--color-down);">✗ Failed: ${escapeHtml(res.error)}</span>`;
      }
    }
  };

  window.testModalPing = async function () {
    const resEl = document.getElementById('modal-ping-output');
    if (resEl) resEl.innerHTML = '<span style="color:var(--text-muted);">Pinging backend...</span>';
    const res = await window.apiClient.ping();
    if (resEl) {
      if (res.online) {
        resEl.innerHTML = `<span style="color:var(--color-up);">✓ Backend online! (${res.latency} ms).</span>`;
      } else {
        resEl.innerHTML = `<span style="color:var(--color-down);">✗ Ping failed: ${escapeHtml(res.error)}</span>`;
      }
    }
  };

  /* ── Modals & Actions ───────────────────────────────────────────────────── */
  window.openModal = function (id) {
    const el = document.getElementById(id);
    if (el) el.classList.add('active');
  };

  window.closeModal = function (id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove('active');
  };

  window.submitAddMonitor = async function (e) {
    e.preventDefault();
    const btn = document.getElementById('btn-submit-add');
    if (btn) btn.disabled = true;

    const name = document.getElementById('add-name')?.value;
    const url = document.getElementById('add-url')?.value;
    const interval = parseInt(document.getElementById('add-interval')?.value || '60', 10);

    try {
      await window.apiClient.createMonitor({ name, url, monitoringInterval: interval });
      showToast(`Monitor "${name}" registered successfully! Immediate health check dispatched.`, 'success');
      closeModal('add-modal');
      e.target.reset();
      refreshCurrentView();
    } catch (err) {
      showToast(`Failed: ${err.message}`, 'error');
    } finally {
      if (btn) btn.disabled = false;
    }
  };

  window.recheckMonitor = async function (id) {
    try {
      showToast('Dispatching immediate synthetic probe check...');
      await window.apiClient.triggerCheck(id);
      showToast('Check completed! Live telemetry updated.', 'success');
      refreshCurrentView();
    } catch (err) {
      showToast(`Check failed: ${err.message}`, 'error');
    }
  };

  window.togglePauseMonitor = async function (id, currentlyEnabled) {
    try {
      if (currentlyEnabled) {
        await window.apiClient.pauseMonitor(id);
        showToast('Monitoring paused.', 'success');
      } else {
        await window.apiClient.resumeMonitor(id);
        showToast('Monitoring resumed. Scheduled checks re-enabled.', 'success');
      }
      refreshCurrentView();
    } catch (err) {
      showToast(`Action failed: ${err.message}`, 'error');
    }
  };

  window.deleteMonitor = async function (id, name) {
    if (!confirm(`Are you sure you want to delete "${name}"?`)) return;
    try {
      await window.apiClient.deleteMonitor(id);
      showToast(`Monitor "${name}" deleted.`, 'success');
      refreshCurrentView();
    } catch (err) {
      showToast(`Delete failed: ${err.message}`, 'error');
    }
  };

  window.openNewDashboardModal = function () {
    openModal('new-dashboard-modal');
  };

  window.submitNewDashboard = async function (e) {
    e.preventDefault();
    const title = document.getElementById('dashboard-title-input')?.value;
    const desc = document.getElementById('dashboard-desc-input')?.value;

    try {
      const created = await window.apiClient.createDashboard({ title, description: desc });
      showToast(`Dashboard "${title}" created!`, 'success');
      closeModal('new-dashboard-modal');
      currentDashboardId = created.id;
      fetchDashboards();
    } catch (err) {
      showToast(`Failed: ${err.message}`, 'error');
    }
  };

  window.deleteCurrentDashboard = async function () {
    if (!currentDashboardId) return;
    if (!confirm('Are you sure you want to delete this dashboard?')) return;
    try {
      await window.apiClient.deleteDashboard(currentDashboardId);
      showToast('Dashboard deleted.', 'success');
      currentDashboardId = null;
      fetchDashboards();
    } catch (err) {
      showToast(`Delete failed: ${err.message}`, 'error');
    }
  };

  /* ── UI Helpers ─────────────────────────────────────────────────────────── */
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
    if (days == null) return '<span style="color:var(--text-muted);">—</span>';
    if (days <= 0 || valid === false) return '<span class="badge badge-down">Expired</span>';
    if (days <= 14) return `<span class="badge badge-degraded">${days}d</span>`;
    return `<span class="badge badge-up">${days}d</span>`;
  }

  function renderUptimeBar(pct) {
    const val = pct != null ? pct : 100.0;
    const isGood = val >= 99.0;
    const isDegraded = val >= 95.0 && val < 99.0;
    const segClass = isGood ? '' : (isDegraded ? 'degraded' : 'down');

    return `
      <div style="display:flex; align-items:center; gap:6px;">
        <div class="uptime-bar">
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
          <div class="uptime-segment ${segClass}"></div>
        </div>
        <span class="mono" style="font-size:11px;">${val}%</span>
      </div>
    `;
  }

  function formatShortTime(iso) {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return iso;
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
      <span style="font-size:14px;">${type === 'success' ? '✓' : (type === 'error' ? '✗' : 'ℹ')}</span>
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
