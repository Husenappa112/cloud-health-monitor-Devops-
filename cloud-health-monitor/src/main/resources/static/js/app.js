let monitorsData = [];
let activeTab = 'dashboard';

/**
 * Returns the effective API Base URL:
 * 1. window.__API_BASE_URL__ (from config.js or inline script)
 * 2. localStorage value 'CHM_API_BASE_URL' (custom user override in UI)
 * 3. Default: empty string (relative paths for local dev / embedded Spring Boot)
 */
function getApiBaseUrl() {
  const custom = localStorage.getItem('CHM_API_BASE_URL');
  if (custom && custom.trim() !== '') {
    return custom.trim().replace(/\/$/, '');
  }
  if (typeof window.__API_BASE_URL__ === 'string' && window.__API_BASE_URL__.trim() !== '') {
    return window.__API_BASE_URL__.trim().replace(/\/$/, '');
  }
  return '';
}

function getApiUrl(path) {
  const base = getApiBaseUrl();
  const cleanPath = path.startsWith('/') ? path : '/' + path;
  return base ? `${base}${cleanPath}` : cleanPath;
}

function updateApiStatusIndicator() {
  const label = document.getElementById('api-endpoint-label');
  if (!label) return;

  const base = getApiBaseUrl();
  if (base) {
    try {
      const u = new URL(base);
      label.textContent = `API: ${u.host}`;
    } catch {
      label.textContent = `API: ${base}`;
    }
  } else {
    label.textContent = 'API: Same Origin (Local/Render)';
  }
}

// DOM ready
document.addEventListener('DOMContentLoaded', () => {
  updateApiStatusIndicator();
  fetchData();
  setInterval(() => {
    if (activeTab === 'dashboard' || activeTab === 'monitors') {
      fetchData(false);
    } else if (activeTab === 'incidents') {
      fetchIncidents();
    } else if (activeTab === 'status-page') {
      renderStatusPage();
    }
  }, 15000);
});

function switchTab(tabId) {
  activeTab = tabId;
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  if (window.event && window.event.currentTarget) {
    window.event.currentTarget.classList.add('active');
  }

  const titles = {
    'dashboard': 'Monitoring Overview',
    'monitors': 'All Monitored Endpoints',
    'incidents': 'Incident Lifecycle Tracking',
    'analytics': 'System & Performance Analytics',
    'status-page': 'Public Service Status Page'
  };
  document.getElementById('page-title').textContent = titles[tabId] || 'Cloud Health Monitor';

  document.getElementById('view-dashboard').style.display = (tabId === 'dashboard' || tabId === 'monitors') ? 'block' : 'none';
  document.getElementById('view-incidents').style.display = (tabId === 'incidents') ? 'block' : 'none';
  document.getElementById('view-analytics').style.display = (tabId === 'analytics') ? 'block' : 'none';
  document.getElementById('view-status-page').style.display = (tabId === 'status-page') ? 'block' : 'none';

  if (tabId === 'incidents') fetchIncidents();
  if (tabId === 'analytics') fetchAnalytics();
  if (tabId === 'status-page') renderStatusPage();
}

async function fetchData(showLoading = true) {
  try {
    const [overviewRes, monitorsRes] = await Promise.all([
      fetch(getApiUrl('/api/dashboard/summary')),
      fetch(getApiUrl('/api/monitors'))
    ]);

    if (overviewRes.ok) {
      const overview = await overviewRes.json();
      document.getElementById('stat-total').textContent = overview.totalMonitors || 0;
      document.getElementById('stat-up').textContent = overview.upCount || 0;
      document.getElementById('stat-degraded').textContent = overview.degradedCount || 0;
      document.getElementById('stat-down').textContent = overview.downCount || 0;
      if (document.getElementById('stat-paused')) {
        document.getElementById('stat-paused').textContent = overview.pausedCount || 0;
      }
      document.getElementById('stat-uptime').textContent = (overview.overallUptimePercentage !== undefined ? overview.overallUptimePercentage : 100) + '%';
      document.getElementById('stat-incidents').textContent = overview.openIncidents || 0;

      // Status banner update
      const banner = document.getElementById('status-banner');
      const bannerText = document.getElementById('banner-text');
      if (banner && bannerText) {
        if (overview.downCount > 0) {
          banner.className = 'status-banner incident';
          bannerText.textContent = `Major Outage: ${overview.downCount} service(s) currently down!`;
        } else if (overview.degradedCount > 0) {
          banner.className = 'status-banner incident';
          bannerText.textContent = `Partial Outage: ${overview.degradedCount} service(s) degraded.`;
        } else {
          banner.className = 'status-banner operational';
          bannerText.textContent = 'All Systems Operational';
        }
      }
    }

    if (monitorsRes.ok) {
      monitorsData = await monitorsRes.json();
      filterMonitors();
    }
  } catch (err) {
    console.error('Failed to load monitoring data:', err);
    const banner = document.getElementById('status-banner');
    const bannerText = document.getElementById('banner-text');
    if (banner && bannerText) {
      banner.className = 'status-banner incident';
      bannerText.textContent = `Backend connection warning: ${err.message}. If backend is waking up, please wait a moment.`;
    }
  }
}

function renderMonitorsTable(data) {
  const tbody = document.getElementById('monitors-tbody');
  if (!tbody) return;
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: var(--text-muted); padding: 32px;">No monitors found matching your criteria. Click "Add Monitor" to start tracking endpoints!</td></tr>`;
    return;
  }

  tbody.innerHTML = data.map(m => {
    let badgeClass = 'badge-unknown';
    let statusLabel = m.currentStatus;

    if (!m.enabled) {
      badgeClass = 'badge-paused';
      statusLabel = 'PAUSED';
    } else if (m.currentStatus === 'UP') {
      badgeClass = 'badge-up';
    } else if (m.currentStatus === 'DEGRADED') {
      badgeClass = 'badge-degraded';
    } else if (m.currentStatus === 'DOWN') {
      badgeClass = 'badge-down';
    }

    const latencyText = m.lastResponseTimeMs ? `${m.lastResponseTimeMs} ms` : '-';
    const httpCodeText = m.lastStatusCode ? `${m.lastStatusCode}` : '-';
    const dnsText = m.dnsLookupTimeMs ? `${m.dnsLookupTimeMs} ms` : (m.dnsStatus || '-');
    const sslText = m.sslDaysRemaining !== null && m.sslDaysRemaining !== undefined
                    ? `${m.sslDaysRemaining}d remaining` : (m.sslValid === false ? 'Expired' : '-');
    const uptimeText = m.uptimePercentage !== null && m.uptimePercentage !== undefined
                    ? `${m.uptimePercentage}%` : '100.0%';
    const lastChecked = m.lastCheckedAt ? new Date(m.lastCheckedAt).toLocaleTimeString() : 'Never';

    const toggleActionBtn = m.enabled
      ? `<button class="btn btn-warning btn-sm" onclick="pauseMonitor(${m.id})" title="Pause health checks">Pause</button>`
      : `<button class="btn btn-success btn-sm" onclick="resumeMonitor(${m.id})" title="Resume health checks">Resume</button>`;

    return `
      <tr>
        <td><span class="badge ${badgeClass}"><span class="dot"></span>${statusLabel}</span></td>
        <td><strong>${escapeHtml(m.name)}</strong></td>
        <td><a href="${escapeHtml(m.url)}" target="_blank" rel="noopener noreferrer" style="color: var(--accent); text-decoration: none;">${escapeHtml(m.url)}</a></td>
        <td>${httpCodeText}</td>
        <td>${latencyText}</td>
        <td>${dnsText}</td>
        <td>${sslText}</td>
        <td><strong style="color: ${m.uptimePercentage >= 99 ? 'var(--success)' : 'var(--warning)'}">${uptimeText}</strong></td>
        <td>
          <div style="display: flex; gap: 4px; flex-wrap: nowrap;">
            <button class="btn btn-secondary btn-sm" onclick="checkNow(${m.id})" title="Execute check now">Check</button>
            ${toggleActionBtn}
            <button class="btn btn-secondary btn-sm" onclick="openEditModal(${m.id})" title="Edit parameters">Edit</button>
            <button class="btn btn-secondary btn-sm" onclick="openDetails(${m.id})" title="View metrics & history">Details</button>
            <button class="btn btn-danger btn-sm" onclick="deleteMonitor(${m.id})" title="Delete monitor">&times;</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function filterMonitors() {
  const query = (document.getElementById('search-input')?.value || '').toLowerCase().trim();
  const filter = document.getElementById('status-filter')?.value || 'ALL';
  const sort = document.getElementById('sort-filter')?.value || 'default';

  let filtered = monitorsData.filter(m => {
    const matchesQuery = m.name.toLowerCase().includes(query) || m.url.toLowerCase().includes(query);
    let matchesStatus = true;
    if (filter === 'PAUSED') {
      matchesStatus = !m.enabled;
    } else if (filter === 'UP' || filter === 'DEGRADED' || filter === 'DOWN') {
      matchesStatus = m.enabled && m.currentStatus === filter;
    }
    return matchesQuery && matchesStatus;
  });

  if (sort === 'name') {
    filtered.sort((a, b) => a.name.localeCompare(b.name));
  } else if (sort === 'latency') {
    filtered.sort((a, b) => (a.lastResponseTimeMs || 999999) - (b.lastResponseTimeMs || 999999));
  } else if (sort === 'uptime') {
    filtered.sort((a, b) => (b.uptimePercentage || 0) - (a.uptimePercentage || 0));
  } else if (sort === 'status') {
    const order = { 'DOWN': 1, 'DEGRADED': 2, 'UP': 3, 'UNKNOWN': 4 };
    filtered.sort((a, b) => (order[a.currentStatus] || 5) - (order[b.currentStatus] || 5));
  }

  renderMonitorsTable(filtered);
}

async function checkNow(id) {
  try {
    const res = await fetch(getApiUrl(`/api/monitors/${id}/check`), { method: 'POST' });
    if (res.ok) {
      await fetchData(false);
    }
  } catch (e) {
    console.error('Check failed:', e);
  }
}

async function pauseMonitor(id) {
  try {
    const res = await fetch(getApiUrl(`/api/monitors/${id}/pause`), { method: 'POST' });
    if (res.ok) {
      await fetchData(false);
    }
  } catch (e) {
    console.error('Pause failed:', e);
  }
}

async function resumeMonitor(id) {
  try {
    const res = await fetch(getApiUrl(`/api/monitors/${id}/resume`), { method: 'POST' });
    if (res.ok) {
      await fetchData(false);
    }
  } catch (e) {
    console.error('Resume failed:', e);
  }
}

async function deleteMonitor(id) {
  if (!confirm('Are you sure you want to remove this monitor?')) return;
  try {
    const res = await fetch(getApiUrl(`/api/monitors/${id}`), { method: 'DELETE' });
    if (res.ok) {
      await fetchData(false);
    }
  } catch (e) {
    console.error('Delete failed:', e);
  }
}

function openAddModal() {
  document.getElementById('modal-error').style.display = 'none';
  document.getElementById('add-monitor-form').reset();
  document.getElementById('add-modal').classList.add('active');
}

function closeAddModal() {
  document.getElementById('add-modal').classList.remove('active');
}

async function submitAddMonitor(e) {
  e.preventDefault();
  const errorEl = document.getElementById('modal-error');
  errorEl.style.display = 'none';

  const name = document.getElementById('mon-name').value.trim();
  const url = document.getElementById('mon-url').value.trim();
  const monitoringInterval = parseInt(document.getElementById('mon-interval').value);

  try {
    const res = await fetch(getApiUrl('/api/monitors'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, url, monitoringInterval })
    });

    if (res.ok) {
      closeAddModal();
      await fetchData(false);
    } else {
      const errData = await res.json();
      errorEl.textContent = errData.message || 'Failed to create monitor';
      errorEl.style.display = 'block';
    }
  } catch (err) {
    errorEl.textContent = 'Network error: ' + err.message;
    errorEl.style.display = 'block';
  }
}

function openEditModal(id) {
  const m = monitorsData.find(x => x.id === id);
  if (!m) return;

  document.getElementById('edit-modal-error').style.display = 'none';
  document.getElementById('edit-mon-id').value = m.id;
  document.getElementById('edit-mon-name').value = m.name;
  document.getElementById('edit-mon-url').value = m.url;
  document.getElementById('edit-mon-interval').value = m.monitoringInterval || 60;
  document.getElementById('edit-mon-enabled').checked = m.enabled;
  document.getElementById('edit-modal').classList.add('active');
}

function closeEditModal() {
  document.getElementById('edit-modal').classList.remove('active');
}

async function submitEditMonitor(e) {
  e.preventDefault();
  const errorEl = document.getElementById('edit-modal-error');
  errorEl.style.display = 'none';

  const id = document.getElementById('edit-mon-id').value;
  const name = document.getElementById('edit-mon-name').value.trim();
  const url = document.getElementById('edit-mon-url').value.trim();
  const monitoringInterval = parseInt(document.getElementById('edit-mon-interval').value);
  const enabled = document.getElementById('edit-mon-enabled').checked;

  try {
    const res = await fetch(getApiUrl(`/api/monitors/${id}`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, url, monitoringInterval, enabled })
    });

    if (res.ok) {
      closeEditModal();
      await fetchData(false);
    } else {
      const errData = await res.json();
      errorEl.textContent = errData.message || 'Failed to update monitor';
      errorEl.style.display = 'block';
    }
  } catch (err) {
    errorEl.textContent = 'Network error: ' + err.message;
    errorEl.style.display = 'block';
  }
}

async function openDetails(id) {
  const m = monitorsData.find(x => x.id === id);
  if (!m) return;

  document.getElementById('details-title').textContent = m.name;
  document.getElementById('details-subtitle').textContent = `${m.url} (Interval: ${m.monitoringInterval}s)`;

  // Badge
  const badgeClass = m.currentStatus === 'UP' ? 'badge-up' :
                     m.currentStatus === 'DEGRADED' ? 'badge-degraded' :
                     m.currentStatus === 'DOWN' ? 'badge-down' : 'badge-unknown';
  document.getElementById('details-status-badge').innerHTML = `<span class="badge ${badgeClass}"><span class="dot"></span>${m.currentStatus}</span>`;
  document.getElementById('details-http-code').textContent = m.lastStatusCode || '-';

  // SSL
  let sslInfo = 'HTTP / Not applicable';
  if (m.sslDaysRemaining !== null && m.sslDaysRemaining !== undefined) {
    sslInfo = `${m.sslDaysRemaining} days remaining (${m.sslIssuer || 'Valid TLS'})`;
  } else if (m.sslValid === false) {
    sslInfo = 'Invalid / Expired Certificate';
  }
  document.getElementById('details-ssl').textContent = sslInfo;

  // DNS
  document.getElementById('details-dns').textContent = m.dnsLookupTimeMs
    ? `${m.dnsLookupTimeMs} ms (${m.dnsStatus || 'SUCCESS'})`
    : (m.dnsStatus || '-');

  // Connection & Redirects
  const redirectInfo = m.redirectCount ? `${m.redirectCount} redirect(s)` : 'Direct (0 redirects)';
  document.getElementById('details-connection').textContent = `${m.connectionStatus || 'CONNECTED'} — ${redirectInfo}`;

  // Fetch uptime analytics (24h, 7d, 30d)
  try {
    const statsRes = await fetch(getApiUrl(`/api/monitors/${id}/uptime`));
    if (statsRes.ok) {
      const stats = await statsRes.json();
      document.getElementById('details-uptime-24h').textContent = `${stats.uptime24h}%`;
      document.getElementById('details-uptime-7d').textContent = `${stats.uptime7d}%`;
      document.getElementById('details-uptime-30d').textContent = `${stats.uptime30d}%`;
    }
  } catch (e) {
    console.error('Failed to load uptime analytics:', e);
  }

  // Fetch history & render sparkline
  try {
    const historyRes = await fetch(getApiUrl(`/api/monitors/${id}/history`));
    if (historyRes.ok) {
      const history = await historyRes.json();
      renderDetailsHistory(history);
      renderSparklineChart(history);
    }
  } catch (e) {
    console.error('History load error:', e);
  }

  document.getElementById('details-modal').classList.add('active');
}

function renderDetailsHistory(history) {
  const tbody = document.getElementById('details-history-tbody');
  if (!tbody) return;

  if (!history || history.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 16px;">No check records found yet.</td></tr>`;
    return;
  }

  tbody.innerHTML = history.slice(0, 20).map(h => {
    const badgeClass = h.status === 'UP' ? 'badge-up' :
                       h.status === 'DEGRADED' ? 'badge-degraded' : 'badge-down';
    const errorMsg = h.errorMessage ? escapeHtml(h.errorMessage) : 'Health check verified successfully';
    const errorColor = h.errorMessage ? 'var(--danger)' : 'var(--text-secondary)';

    return `
      <tr>
        <td>${new Date(h.checkedAt).toLocaleTimeString()}</td>
        <td><span class="badge ${badgeClass}"><span class="dot"></span>${h.status}</span></td>
        <td>${h.httpStatusCode || '-'}</td>
        <td>${h.responseTimeMs || 0} ms</td>
        <td>${h.dnsLookupTimeMs ? h.dnsLookupTimeMs + ' ms' : (h.dnsStatus || '-')}</td>
        <td style="color: ${errorColor}; font-size: 0.8rem;">${errorMsg}</td>
      </tr>
    `;
  }).join('');
}

function renderSparklineChart(history) {
  const canvas = document.getElementById('details-sparkline');
  const statsLabel = document.getElementById('chart-latency-stats');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  const width = canvas.width;
  const height = canvas.height;
  ctx.clearRect(0, 0, width, height);

  if (!history || history.length === 0) {
    if (statsLabel) statsLabel.textContent = 'No latency points recorded yet.';
    return;
  }

  // Extract chronologically sorted points (history comes newest first)
  const points = history.slice(0, 30).reverse().map(h => h.responseTimeMs || 0);

  const minLat = Math.min(...points);
  const maxLat = Math.max(...points);
  const avgLat = Math.round(points.reduce((a, b) => a + b, 0) / points.length);

  if (statsLabel) {
    statsLabel.textContent = `Min: ${minLat} ms | Avg: ${avgLat} ms | Max: ${maxLat} ms`;
  }

  if (points.length < 2) {
    // Single bar fallback
    ctx.fillStyle = '#38bdf8';
    ctx.fillRect(width / 2 - 15, height - 30, 30, 25);
    return;
  }

  const padding = 15;
  const graphWidth = width - padding * 2;
  const graphHeight = height - padding * 2;
  const range = (maxLat - minLat) || 1;

  // Background gradient
  const gradient = ctx.createLinearGradient(0, padding, 0, height - padding);
  gradient.addColorStop(0, 'rgba(56, 189, 248, 0.35)');
  gradient.addColorStop(1, 'rgba(56, 189, 248, 0.0)');

  // Begin path
  ctx.beginPath();
  const step = graphWidth / (points.length - 1);

  points.forEach((val, idx) => {
    const x = padding + idx * step;
    const y = height - padding - ((val - minLat) / range) * graphHeight;
    if (idx === 0) {
      ctx.moveTo(x, y);
    } else {
      ctx.lineTo(x, y);
    }
  });

  // Stroke line
  ctx.strokeStyle = '#38bdf8';
  ctx.lineWidth = 2.5;
  ctx.lineJoin = 'round';
  ctx.stroke();

  // Fill area under line
  ctx.lineTo(padding + (points.length - 1) * step, height - padding);
  ctx.lineTo(padding, height - padding);
  ctx.closePath();
  ctx.fillStyle = gradient;
  ctx.fill();

  // Draw point dots
  points.forEach((val, idx) => {
    const x = padding + idx * step;
    const y = height - padding - ((val - minLat) / range) * graphHeight;
    ctx.beginPath();
    ctx.arc(x, y, 3, 0, Math.PI * 2);
    ctx.fillStyle = val > 2000 ? '#ef4444' : '#38bdf8';
    ctx.fill();
  });
}

function closeDetailsModal() {
  document.getElementById('details-modal').classList.remove('active');
}

async function fetchIncidents() {
  const tbody = document.getElementById('incidents-tbody');
  try {
    const res = await fetch(getApiUrl('/incidents'));
    if (res.ok) {
      const incidents = await res.json();
      if (incidents.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 32px;">No incidents recorded. All services healthy!</td></tr>`;
        return;
      }
      tbody.innerHTML = incidents.map(inc => {
        const isOpen = inc.status === 'OPEN';
        const statusBadge = isOpen ? '<span class="badge badge-down"><span class="dot"></span>OPEN</span>' :
                                    '<span class="badge badge-up"><span class="dot"></span>RESOLVED</span>';
        const duration = inc.durationMs ? `${Math.round(inc.durationMs / 1000)}s` : (isOpen ? 'Ongoing' : '-');
        const resolveBtn = isOpen ? `<button class="btn btn-secondary btn-sm" onclick="resolveIncident(${inc.id})">Resolve</button>` : '-';

        return `
          <tr>
            <td>#${inc.id}</td>
            <td><strong>${escapeHtml(inc.serviceName)}</strong></td>
            <td>${statusBadge}</td>
            <td><code>${inc.incidentType}</code></td>
            <td style="font-size: 0.85rem; color: var(--text-secondary);">${escapeHtml(inc.rootCause || '-')}</td>
            <td>${new Date(inc.startedAt).toLocaleString()}</td>
            <td>${duration}</td>
            <td>${resolveBtn}</td>
          </tr>
        `;
      }).join('');
    }
  } catch (e) {
    console.error('Incidents load error:', e);
  }
}

async function resolveIncident(id) {
  try {
    const res = await fetch(getApiUrl(`/incidents/${id}/resolve`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note: 'Manually resolved by administrator' })
    });
    if (res.ok) {
      fetchIncidents();
      fetchData(false);
    }
  } catch (e) {
    console.error('Resolve error:', e);
  }
}

async function fetchAnalytics() {
  const tbody = document.getElementById('analytics-tbody');
  if (monitorsData.length === 0) {
    tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 32px;">No monitors to analyze.</td></tr>`;
    return;
  }

  tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 32px;">Calculating uptime statistics...</td></tr>`;

  try {
    const statsList = await Promise.all(monitorsData.map(async m => {
      const res = await fetch(getApiUrl(`/api/monitors/${m.id}/uptime`));
      if (res.ok) {
        const stats = await res.json();
        return { monitor: m, stats };
      }
      return { monitor: m, stats: null };
    }));

    tbody.innerHTML = statsList.map(({ monitor, stats }) => {
      if (!stats) return '';
      return `
        <tr>
          <td><strong>${escapeHtml(monitor.name)}</strong></td>
          <td><strong style="color: ${stats.uptime24h > 99 ? 'var(--success)' : 'var(--warning)'};">${stats.uptime24h}%</strong></td>
          <td>${stats.uptime7d}%</td>
          <td>${stats.uptime30d}%</td>
          <td>${stats.avgResponseTime24h} ms</td>
          <td>${stats.minResponseTime24h} ms / ${stats.maxResponseTime24h} ms</td>
          <td>${stats.totalChecks24h}</td>
          <td>${stats.totalIncidents}</td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Analytics load error:', e);
  }
}

function renderStatusPage() {
  const list = document.getElementById('status-page-list');
  if (monitorsData.length === 0) {
    list.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 24px;">No services configured.</div>`;
    return;
  }

  list.innerHTML = monitorsData.map(m => {
    let badgeClass = 'badge-unknown';
    let statusLabel = m.currentStatus;
    if (!m.enabled) {
      badgeClass = 'badge-paused';
      statusLabel = 'PAUSED';
    } else if (m.currentStatus === 'UP') {
      badgeClass = 'badge-up';
    } else if (m.currentStatus === 'DEGRADED') {
      badgeClass = 'badge-degraded';
    } else if (m.currentStatus === 'DOWN') {
      badgeClass = 'badge-down';
    }

    return `
      <div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background-color: var(--bg-primary); border-radius: 8px;">
        <div>
          <strong style="font-size: 1rem;">${escapeHtml(m.name)}</strong>
          <div style="font-size: 0.8rem; color: var(--text-muted);">${escapeHtml(m.url)}</div>
        </div>
        <span class="badge ${badgeClass}"><span class="dot"></span>${statusLabel}</span>
      </div>
    `;
  }).join('');
}

function openApiConfigModal() {
  const current = getApiBaseUrl();
  const input = document.getElementById('api-config-input');
  if (input) input.value = current;
  const modal = document.getElementById('api-config-modal');
  if (modal) modal.classList.add('active');
}

function closeApiConfigModal() {
  const modal = document.getElementById('api-config-modal');
  if (modal) modal.classList.remove('active');
}

function saveApiConfig(e) {
  if (e) e.preventDefault();
  const input = document.getElementById('api-config-input');
  if (input) {
    const val = input.value.trim();
    if (val) {
      localStorage.setItem('CHM_API_BASE_URL', val);
    } else {
      localStorage.removeItem('CHM_API_BASE_URL');
    }
  }
  closeApiConfigModal();
  updateApiStatusIndicator();
  fetchData();
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}
