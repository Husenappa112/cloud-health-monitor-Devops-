/**
 * Cloud Health Monitor — Resilient Observability API Client
 * 
 * Features:
 * - Robust preset management (PRODUCTION, LOCAL, CUSTOM)
 * - Self-healing domain sanitization (prevents monitored URLs from being used as backend)
 * - Finite state machine: CONNECTED, CONNECTING, RECONNECTING, DISCONNECTED
 * - Bounded exponential backoff retry for Render cold starts
 * - Safe JSON parsing with MIME-type pre-validation to eliminate SyntaxErrors
 * - Unified endpoints for Monitors, Incidents, Alerts, Visualizations, Dashboards, and Telemetry
 */

(function (window) {
  'use strict';

  const TARGET_BLACKLIST = [
    'google.com', 'google.co.in', 'github.com', 'amazon.com', 'amazon.in',
    'microsoft.com', 'apple.com', 'example.com', 'yahoo.com', 'bing.com'
  ];

  class ApiClient {
    constructor() {
      this.timeoutMs = 12000;
      this.connectionState = 'DISCONNECTED'; // CONNECTED, CONNECTING, RECONNECTING, DISCONNECTED
      this.onConnectionStateChange = null;
      this.retryAttempts = 0;
      this.maxRetryAttempts = 5;
      this.retryTimer = null;
      this.activeBackendPreset = this.getStoredPreset();
    }

    getStoredPreset() {
      try {
        const stored = localStorage.getItem('CHM_BACKEND_PRESET');
        if (stored && ['PRODUCTION', 'LOCAL', 'CUSTOM'].includes(stored)) {
          return stored;
        }
      } catch (e) {}
      return (window.location.hostname.includes('vercel.app')) ? 'PRODUCTION' : 'LOCAL';
    }

    setPreset(preset, customUrl = '') {
      try {
        if (preset === 'PRODUCTION') {
          localStorage.setItem('CHM_BACKEND_PRESET', 'PRODUCTION');
          localStorage.removeItem('CHM_API_BASE_URL');
          this.activeBackendPreset = 'PRODUCTION';
        } else if (preset === 'LOCAL') {
          localStorage.setItem('CHM_BACKEND_PRESET', 'LOCAL');
          localStorage.removeItem('CHM_API_BASE_URL');
          this.activeBackendPreset = 'LOCAL';
        } else if (preset === 'CUSTOM') {
          const validated = this.validateBackendUrl(customUrl);
          localStorage.setItem('CHM_BACKEND_PRESET', 'CUSTOM');
          localStorage.setItem('CHM_API_BASE_URL', validated);
          this.activeBackendPreset = 'CUSTOM';
        }
      } catch (e) {
        console.error('Storage error:', e);
      }
      this.resetRetry();
      this.setConnectionState('CONNECTING', 'Switching backend endpoint...');
    }

    validateBackendUrl(url) {
      if (!url || typeof url !== 'string' || url.trim() === '') {
        throw new Error('Backend URL cannot be empty');
      }
      let cleaned = url.trim().replace(/\/+$/, '');
      if (!cleaned.toLowerCase().startsWith('http://') && !cleaned.toLowerCase().startsWith('https://')) {
        cleaned = 'https://' + cleaned;
      }
      const lower = cleaned.toLowerCase();
      for (const domain of TARGET_BLACKLIST) {
        if (lower.includes(domain)) {
          throw new Error(`"${domain}" is a monitored website target, NOT a Cloud Health Monitor backend API server! To monitor this site, use "+ Add Monitor".`);
        }
      }
      return cleaned;
    }

    getBaseUrl() {
      const preset = this.getStoredPreset();
      if (preset === 'PRODUCTION') {
        return (window.CHM_CONFIG && window.CHM_CONFIG.PRODUCTION_BACKEND) || 'https://cloud-health-monitor-devops.onrender.com';
      }
      if (preset === 'LOCAL') {
        return (window.CHM_CONFIG && window.CHM_CONFIG.LOCAL_BACKEND) || 'http://localhost:8081';
      }

      // CUSTOM preset
      try {
        const custom = localStorage.getItem('CHM_API_BASE_URL');
        if (custom && custom.trim() !== '') {
          // Check for accidental blacklisted target
          const lower = custom.toLowerCase();
          if (TARGET_BLACKLIST.some(d => lower.includes(d))) {
            localStorage.removeItem('CHM_API_BASE_URL');
            localStorage.setItem('CHM_BACKEND_PRESET', 'PRODUCTION');
            return 'https://cloud-health-monitor-devops.onrender.com';
          }
          return custom.trim().replace(/\/+$/, '');
        }
      } catch (e) {}

      return (window.CHM_CONFIG && window.CHM_CONFIG.defaultBackend) || '';
    }

    buildUrl(endpoint) {
      const base = this.getBaseUrl();
      const cleanEndpoint = endpoint.startsWith('/') ? endpoint : '/' + endpoint;
      return base ? `${base}${cleanEndpoint}` : cleanEndpoint;
    }

    setConnectionState(state, details = '') {
      this.connectionState = state;
      if (typeof this.onConnectionStateChange === 'function') {
        this.onConnectionStateChange(state, details, this.getBaseUrl());
      }
    }

    resetRetry() {
      this.retryAttempts = 0;
      if (this.retryTimer) {
        clearTimeout(this.retryTimer);
        this.retryTimer = null;
      }
    }

    /**
     * Resilient fetch wrapper with MIME-type pre-validation and cold-start backoff.
     */
    async request(endpoint, options = {}) {
      const url = this.buildUrl(endpoint);
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), options.timeoutMs || this.timeoutMs);

      const headers = Object.assign({
        'Accept': 'application/json',
      }, options.headers || {});

      if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(options.body);
      }

      const fetchOptions = Object.assign({}, options, {
        headers,
        signal: controller.signal
      });

      let response;
      try {
        response = await fetch(url, fetchOptions);
      } catch (err) {
        clearTimeout(timeoutId);
        if (err.name === 'AbortError') {
          this.handleConnectionFailure(`Request timed out after ${this.timeoutMs / 1000}s. Backend might be starting up from cold start.`);
          throw new Error(`Request timeout: Backend at ${this.getBaseUrl()} took longer than ${this.timeoutMs / 1000}s to respond.`);
        }
        this.handleConnectionFailure(`Network error connecting to backend: ${err.message}`);
        throw new Error(`Network error connecting to backend (${url}): ${err.message}`);
      } finally {
        clearTimeout(timeoutId);
      }

      const contentType = response.headers.get('content-type') || '';
      const isJson = contentType.includes('application/json');

      if (!response.ok) {
        let errorMsg = `HTTP ${response.status} ${response.statusText}`;
        if (isJson) {
          try {
            const errObj = await response.json();
            errorMsg = errObj.message || errObj.error || JSON.stringify(errObj);
          } catch (_) {}
        } else {
          try {
            const raw = await response.text();
            if (response.status === 502 || response.status === 503 || response.status === 504 ||
                raw.includes('starting') || raw.includes('Render') || raw.includes('no-server')) {
              this.handleConnectionFailure('Backend is spinning up (Render free tier cold start).');
              errorMsg = 'Backend is waking up (cold start). Please allow 30-45 seconds for initialization.';
            } else {
              errorMsg = raw.slice(0, 160) || errorMsg;
            }
          } catch (_) {}
        }

        const httpErr = new Error(errorMsg);
        httpErr.status = response.status;
        throw httpErr;
      }

      // Successful HTTP 2xx
      if (!isJson) {
        const text = await response.text();
        if (text.trim().startsWith('<')) {
          this.handleConnectionFailure('Received HTML instead of JSON — proxy gateway might be initializing.');
          throw new Error('Received unexpected HTML response from backend.');
        }
        try {
          const parsed = JSON.parse(text);
          this.handleConnectionSuccess();
          return parsed;
        } catch (e) {
          throw new Error(`Invalid JSON received: ${text.slice(0, 80)}`);
        }
      }

      const data = await response.json();
      this.handleConnectionSuccess();
      return data;
    }

    handleConnectionSuccess() {
      this.resetRetry();
      if (this.connectionState !== 'CONNECTED') {
        this.setConnectionState('CONNECTED', 'Backend online and responding');
      }
    }

    handleConnectionFailure(reason) {
      this.retryAttempts++;
      if (this.retryAttempts <= this.maxRetryAttempts) {
        const delay = Math.min(30000, Math.pow(2, this.retryAttempts) * 1000); // 2s, 4s, 8s, 16s, 30s
        this.setConnectionState('RECONNECTING', `${reason} Retrying in ${delay / 1000}s (Attempt ${this.retryAttempts}/${this.maxRetryAttempts})...`);
      } else {
        this.setConnectionState('DISCONNECTED', `${reason} Max retry attempts reached. Check backend configuration or test ping in Settings.`);
      }
    }

    /* ── High-Level API Methods ────────────────────────────────────────── */

    // Health & System
    async ping() {
      try {
        const start = performance.now();
        const res = await this.request('/health', { timeoutMs: 6000 });
        const latency = Math.round(performance.now() - start);
        return { online: true, latency, data: res };
      } catch (err) {
        return { online: false, error: err.message };
      }
    }

    async getSummary() {
      return this.request('/api/dashboard/summary');
    }

    // Monitors CRUD & Lifecycle
    async getMonitors() {
      return this.request('/api/monitors');
    }

    async getMonitor(id) {
      return this.request(`/api/monitors/${id}`);
    }

    async createMonitor(payload) {
      return this.request('/api/monitors', { method: 'POST', body: payload });
    }

    async updateMonitor(id, payload) {
      return this.request(`/api/monitors/${id}`, { method: 'PUT', body: payload });
    }

    async deleteMonitor(id) {
      return this.request(`/api/monitors/${id}`, { method: 'DELETE' });
    }

    async pauseMonitor(id) {
      return this.request(`/api/monitors/${id}/pause`, { method: 'POST' });
    }

    async resumeMonitor(id) {
      return this.request(`/api/monitors/${id}/resume`, { method: 'POST' });
    }

    async triggerCheck(id) {
      return this.request(`/api/monitors/${id}/check`, { method: 'POST' });
    }

    // Incidents Management
    async getIncidents(status = '') {
      const q = status ? `?status=${encodeURIComponent(status)}` : '';
      return this.request(`/api/incidents${q}`);
    }

    async getIncident(id) {
      return this.request(`/api/incidents/${id}`);
    }

    async acknowledgeIncident(id, note) {
      return this.request(`/api/incidents/${id}/acknowledge`, {
        method: 'POST',
        body: { note: note || 'Acknowledged by operator' }
      });
    }

    async resolveIncident(id, note) {
      return this.request(`/api/incidents/${id}/resolve`, {
        method: 'POST',
        body: { note: note || 'Resolved by operator' }
      });
    }

    async reopenIncident(id, note) {
      return this.request(`/api/incidents/${id}/reopen`, {
        method: 'POST',
        body: { note: note || 'Reopened by operator' }
      });
    }

    // Alerts Management
    async getAlerts() {
      return this.request('/api/alerts');
    }

    async getAlertRules() {
      return this.request('/api/alerts/rules');
    }

    async createAlertRule(rule) {
      return this.request('/api/alerts/rules', { method: 'POST', body: rule });
    }

    async updateAlertRule(id, rule) {
      return this.request(`/api/alerts/rules/${id}`, { method: 'PUT', body: rule });
    }

    async deleteAlertRule(id) {
      return this.request(`/api/alerts/rules/${id}`, { method: 'DELETE' });
    }

    async getAlertHistory() {
      return this.request('/api/alerts/history');
    }

    // Visualization Studio & Dashboards
    async getVisualizationTypes() {
      return this.request('/api/visualizations/types');
    }

    async getVisualizationMetrics() {
      return this.request('/api/visualizations/metrics');
    }

    async getSavedVisualizations() {
      return this.request('/api/visualizations');
    }

    async createVisualization(config) {
      return this.request('/api/visualizations', { method: 'POST', body: config });
    }

    async updateVisualization(id, config) {
      return this.request(`/api/visualizations/${id}`, { method: 'PUT', body: config });
    }

    async deleteVisualization(id) {
      return this.request(`/api/visualizations/${id}`, { method: 'DELETE' });
    }

    async getDashboards() {
      return this.request('/api/dashboards');
    }

    async getDashboard(id) {
      return this.request(`/api/dashboards/${id}`);
    }

    async createDashboard(dashboard) {
      return this.request('/api/dashboards', { method: 'POST', body: dashboard });
    }

    async updateDashboard(id, dashboard) {
      return this.request(`/api/dashboards/${id}`, { method: 'PUT', body: dashboard });
    }

    async deleteDashboard(id) {
      return this.request(`/api/dashboards/${id}`, { method: 'DELETE' });
    }

    // Analytics & Telemetry Queries
    async getAnalyticsTimeseries(params = {}) {
      const qs = new URLSearchParams(params).toString();
      return this.request(`/api/analytics/timeseries${qs ? '?' + qs : ''}`);
    }

    async getAnalyticsDistribution(params = {}) {
      const qs = new URLSearchParams(params).toString();
      return this.request(`/api/analytics/distribution${qs ? '?' + qs : ''}`);
    }

    async getAnalyticsHeatmap(params = {}) {
      const qs = new URLSearchParams(params).toString();
      return this.request(`/api/analytics/heatmap${qs ? '?' + qs : ''}`);
    }

    async getHttpInsights() {
      return this.request('/api/dashboard/http-insights');
    }

    async getInfrastructure() {
      return this.request('/api/dashboard/infrastructure');
    }

    async getSecurity() {
      return this.request('/api/dashboard/security');
    }

    async getEvents(limit = 50) {
      return this.request(`/api/dashboard/events?limit=${limit}`);
    }
  }

  window.apiClient = new ApiClient();

})(window);
