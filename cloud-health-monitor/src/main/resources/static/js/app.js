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
  const dot = document.getElementById('api-status-dot');
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
  if (event && event.currentTarget) {
    event.currentTarget.classList.add('active');
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
      fetch(getApiUrl('/monitors/overview')),
      fetch(getApiUrl('/monitors'))
    ]);

    if (overviewRes.ok) {
      const overview = await overviewRes.json();
      document.getElementById('stat-total').textContent = overview.totalMonitors || 0;
      document.getElementById('stat-up').textContent = overview.upCount || 0;
      document.getElementById('stat-degraded').textContent = overview.degradedCount || 0;
      document.getElementById('stat-down').textContent = overview.downCount || 0;
      document.getElementById('stat-uptime').textContent = (overview.overallUptimePercentage || 100) + '%';
      document.getElementById('stat-incidents').textContent = overview.openIncidents || 0;

      // Status banner update
      const banner = document.getElementById('status-banner');
      const bannerText = document.getElementById('banner-text');
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

    if (monitorsRes.ok) {
      monitorsData = await monitorsRes.json();
      renderMonitorsTable(monitorsData);
    }
  } catch (err) {
    console.error('Failed to load monitoring data:', err);
    const banner = document.getElementById('status-banner');
    const bannerText = document.getElementById('banner-text');
    if (banner && bannerText) {
      banner.className = 'status-banner incident';
      bannerText.textContent = `Backend connection warning: ${err.message}. If backend is hosted on Render free tier, allow 30-50s for wake-up.`;
    }
  }
}

function renderMonitorsTable(data) {
  const tbody = document.getElementById('monitors-tbody');
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: var(--text-muted); padding: 32px;">No monitors registered yet. Click "Add Monitor" to start tracking endpoints!</td></tr>`;
    return;
  }

  tbody.innerHTML = data.map(m => {
    const badgeClass = m.currentStatus === 'UP' ? 'badge-up' :
                       m.currentStatus === 'DEGRADED' ? 'badge-degraded' :
                       m.currentStatus === 'DOWN' ? 'badge-down' : 'badge-unknown';

    const latencyText = m.lastResponseTimeMs ? `${m.lastResponseTimeMs} ms` : '-';
    const httpCodeText = m.lastStatusCode ? `${m.lastStatusCode}` : '-';
    const dnsText = m.dnsLookupTimeMs ? `${m.dnsLookupTimeMs} ms` : '-';
    const sslText = m.sslDaysRemaining !== null && m.sslDaysRemaining !== undefined
                    ? `${m.sslDaysRemaining}d remaining` : '-';
    const lastChecked = m.lastCheckedAt ? new Date(m.lastCheckedAt).toLocaleTimeString() : 'Never';

    return `
      <tr>
        <td><span class="badge ${badgeClass}"><span class="dot"></span>${m.currentStatus}</span></td>
        <td><strong>${escapeHtml(m.name)}</strong></td>
        <td><a href="${escapeHtml(m.url)}" target="_blank" style="color: var(--accent); text-decoration: none;">${escapeHtml(m.url)}</a></td>
        <td>${httpCodeText}</td>
        <td>${latencyText}</td>
        <td>${dnsText}</td>
        <td>${sslText}</td>
        <td>${lastChecked}</td>
        <td>
          <div style="display: flex; gap: 6px;">
            <button class="btn btn-secondary btn-sm" onclick="checkNow(${m.id})">Check</button>
            <button class="btn btn-secondary btn-sm" onclick="openDetails(${m.id})">Details</button>
            <button class="btn btn-danger btn-sm" onclick="deleteMonitor(${m.id})">&times;</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function filterMonitors() {
  const query = document.getElementById('search-input').value.toLowerCase();
  const filter = document.getElementById('status-filter').value;

  const filtered = monitorsData.filter(m => {
    const matchesQuery = m.name.toLowerCase().includes(query) || m.url.toLowerCase().includes(query);
    const matchesStatus = filter === 'ALL' || m.currentStatus === filter;
    return matchesQuery && matchesStatus;
  });

  renderMonitorsTable(filtered);
}

async function checkNow(id) {
  try {
    const res = await fetch(getApiUrl(`/monitors/${id}/check`), { method: 'POST' });
    if (res.ok) {
      await fetchData(false);
    }
  } catch (e) {
    console.error('Check failed:', e);
  }
}

async function deleteMonitor(id) {
  if (!confirm('Are you sure you want to remove this monitor?')) return;
  try {
    const res = await fetch(getApiUrl(`/monitors/${id}`), { method: 'DELETE' });
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
    const res = await fetch(getApiUrl('/monitors'), {
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

async function openDetails(id) {
  const m = monitorsData.find(x => x.id === id);
  if (!m) return;

  document.getElementById('details-title').textContent = `${m.name} — Status & History`;
  document.getElementById('details-ssl').textContent = m.sslDaysRemaining !== null
    ? `${m.sslDaysRemaining} days remaining (${m.sslIssuer || 'Standard SSL'})`
    : 'No SSL (HTTP or invalid)';
  document.getElementById('details-dns').textContent = m.dnsLookupTimeMs ? `${m.dnsLookupTimeMs} ms` : '-';
  document.getElementById('details-latency').textContent = m.lastResponseTimeMs ? `${m.lastResponseTimeMs} ms` : '-';

  try {
    const historyRes = await fetch(getApiUrl(`/monitors/${id}/history`));
    if (historyRes.ok) {
      const history = await historyRes.json();
      const tbody = document.getElementById('details-history-tbody');
      if (history.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 16px;">No history recorded yet.</td></tr>`;
      } else {
        tbody.innerHTML = history.slice(0, 20).map(h => {
          const badgeClass = h.status === 'UP' ? 'badge-up' :
                             h.status === 'DEGRADED' ? 'badge-degraded' : 'badge-down';
          return `
            <tr>
              <td>${new Date(h.checkedAt).toLocaleTimeString()}</td>
              <td><span class="badge ${badgeClass}"><span class="dot"></span>${h.status}</span></td>
              <td>${h.httpStatusCode || '-'}</td>
              <td>${h.responseTimeMs || 0} ms</td>
              <td style="color: ${h.errorMessage ? 'var(--danger)' : 'var(--text-secondary)'}; font-size: 0.8rem;">
                ${escapeHtml(h.errorMessage || 'Health check passed normally')}
              </td>
            </tr>
          `;
        }).join('');
      }
    }
  } catch (e) {
    console.error('History load error:', e);
  }

  document.getElementById('details-modal').classList.add('active');
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
      const res = await fetch(getApiUrl(`/monitors/${m.id}/statistics`));
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
    const badgeClass = m.currentStatus === 'UP' ? 'badge-up' :
                       m.currentStatus === 'DEGRADED' ? 'badge-degraded' :
                       m.currentStatus === 'DOWN' ? 'badge-down' : 'badge-unknown';
    return `
      <div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background-color: var(--bg-primary); border-radius: 8px;">
        <div>
          <strong style="font-size: 1rem;">${escapeHtml(m.name)}</strong>
          <div style="font-size: 0.8rem; color: var(--text-muted);">${escapeHtml(m.url)}</div>
        </div>
        <span class="badge ${badgeClass}"><span class="dot"></span>${m.currentStatus}</span>
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
