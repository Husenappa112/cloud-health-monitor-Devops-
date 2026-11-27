/**
 * Cloud Health Monitor — Resilient API Client
 * 
 * Provides robust error handling, Render cold-start detection, safe non-JSON response parsing,
 * configurable timeout handling, and unified methods for all monitoring endpoints.
 */

(function (window) {
  'use strict';

  class ApiClient {
    constructor() {
      this.timeoutMs = 12000; // 12 seconds request timeout
      this.isWarmingUp = false;
      this.onWarmupStateChange = null;
    }

    /**
     * Resolves the effective backend base URL:
     * 1. localStorage 'CHM_API_BASE_URL' (manual user override in UI)
     * 2. window.__API_BASE_URL__ (from config.js or inline script)
     * 3. Fallback: window.location.origin (for same-origin / embedded mode)
     */
    getBaseUrl() {
      try {
        const stored = localStorage.getItem('CHM_API_BASE_URL');
        if (stored && stored.trim() !== '') {
          return stored.trim().replace(/\/+$/, '');
        }
      } catch (e) {
        // localStorage might be unavailable in restricted environments
      }

      if (typeof window.__API_BASE_URL__ === 'string' && window.__API_BASE_URL__.trim() !== '') {
        return window.__API_BASE_URL__.trim().replace(/\/+$/, '');
      }

      // Default to empty string for relative same-origin requests
      return '';
    }

    setBaseUrl(url) {
      if (url && url.trim() !== '') {
        localStorage.setItem('CHM_API_BASE_URL', url.trim().replace(/\/+$/, ''));
      } else {
        localStorage.removeItem('CHM_API_BASE_URL');
      }
    }

    buildUrl(endpoint) {
      const base = this.getBaseUrl();
      const cleanEndpoint = endpoint.startsWith('/') ? endpoint : '/' + endpoint;
      return base ? `${base}${cleanEndpoint}` : cleanEndpoint;
    }

    /**
     * Resilient fetch wrapper with timeout, cold-start detection, and non-JSON body protection.
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
          this.setWarmingUp(true, 'Request timed out — backend may be spinning up from cold start.');
          throw new Error(`Request to ${endpoint} timed out after ${this.timeoutMs / 1000}s. Backend might be starting up.`);
        }
        this.setWarmingUp(true, 'Network connectivity error — attempting reconnection.');
        throw new Error(`Network error connecting to backend (${url}): ${err.message}`);
      } finally {
        clearTimeout(timeoutId);
      }

      // Check HTTP status and Content-Type
      const contentType = response.headers.get('content-type') || '';
      const isJson = contentType.includes('application/json');

      // Detect Render cold-start / HTML gateway errors (502, 503, 504 or HTML body)
      if (!response.ok) {
        let errorDetails = `HTTP ${response.status} ${response.statusText}`;
        if (isJson) {
          try {
            const jsonErr = await response.json();
            errorDetails = jsonErr.message || jsonErr.error || JSON.stringify(jsonErr);
          } catch (_) {
            // fallback to status text
          }
        } else {
          try {
            const rawText = await response.text();
            // Check for Render spinning up indicators
            if (response.status === 502 || response.status === 503 || response.status === 504 ||
                rawText.includes('starting') || rawText.includes('Render') || rawText.includes('The page could not be found')) {
              this.setWarmingUp(true, 'Backend is waking up (Render free tier cold start). Re-querying...');
              errorDetails = 'Backend is waking up (cold start). Please allow 30-45 seconds.';
            } else {
              errorDetails = rawText.slice(0, 140) || errorDetails;
            }
          } catch (_) {
            // ignore
          }
        }

        const httpError = new Error(errorDetails);
        httpError.status = response.status;
        throw httpError;
      }

      // Successful HTTP response: Verify JSON before calling .json() to avoid syntax crash
      if (!isJson) {
        const textData = await response.text();
        // Check if response accidentally returned HTML
        if (textData.trim().startsWith('<')) {
          this.setWarmingUp(true, 'Received HTML instead of JSON — backend proxy may be initializing.');
          throw new Error('Received unexpected HTML response instead of JSON. Backend might be restarting.');
        }
        try {
          return JSON.parse(textData);
        } catch (e) {
          throw new Error(`Invalid JSON received from ${endpoint}: ${textData.slice(0, 80)}`);
        }
      }

      // Backend is online and returned valid JSON
      this.setWarmingUp(false);
      return await response.json();
    }

    setWarmingUp(isWarming, reason = '') {
      if (this.isWarmingUp !== isWarming) {
        this.isWarmingUp = isWarming;
        if (typeof this.onWarmupStateChange === 'function') {
          this.onWarmupStateChange(isWarming, reason);
        }
      }
    }

    /* ── High-Level API Methods ────────────────────────────────────────── */

    // System Overview & Stats
    async getSummary() {
      return this.request('/api/dashboard/summary');
    }

    // Monitors
    async getMonitors() {
      return this.request('/api/monitors');
    }

    async getMonitor(id) {
      return this.request(`/api/monitors/${id}`);
    }

    async createMonitor(payload) {
      return this.request('/api/monitors', {
        method: 'POST',
        body: payload
      });
    }

    async updateMonitor(id, payload) {
      return this.request(`/api/monitors/${id}`, {
        method: 'PUT',
        body: payload
      });
    }

    async deleteMonitor(id) {
      return this.request(`/api/monitors/${id}`, {
        method: 'DELETE'
      });
    }

    async pauseMonitor(id) {
      return this.request(`/api/monitors/${id}/pause`, {
        method: 'POST'
      });
    }

    async resumeMonitor(id) {
      return this.request(`/api/monitors/${id}/resume`, {
        method: 'POST'
      });
    }

    async triggerCheck(id) {
      return this.request(`/api/monitors/${id}/check`, {
        method: 'POST'
      });
    }

    // Incidents
    async getIncidents(status) {
      const endpoint = status ? `/api/incidents?status=${encodeURIComponent(status)}` : '/api/incidents';
      return this.request(endpoint);
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
        body: { note: note || 'Resolved via operator console' }
      });
    }

    // Dedicated Observability & Telemetry Views
    async getHttpInsights() {
      return this.request('/api/dashboard/http-insights');
    }

    async getInfrastructure() {
      return this.request('/api/dashboard/infrastructure');
    }

    async getSecurity() {
      return this.request('/api/dashboard/security');
    }

    async getEvents(limit = 40) {
      return this.request(`/api/dashboard/events?limit=${limit}`);
    }

    async getAlerts() {
      return this.request('/api/dashboard/alerts');
    }

    // Quick Health Ping
    async ping() {
      try {
        const start = performance.now();
        await this.request('/health', { timeoutMs: 6000 });
        const latency = Math.round(performance.now() - start);
        return { online: true, latency };
      } catch (err) {
        return { online: false, error: err.message };
      }
    }
  }

  // Export singleton to global scope
  window.apiClient = new ApiClient();

})(window);
