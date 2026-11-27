/**
 * Cloud Health Monitor — Runtime Backend Configuration & Presets
 * 
 * Supports three primary backend configurations:
 * 1. PRODUCTION: https://cloud-health-monitor-devops.onrender.com
 * 2. LOCAL:      http://localhost:8081
 * 3. CUSTOM:     User-specified Spring Boot endpoint
 * 
 * Includes self-healing sanitization to prevent monitored target URLs
 * (e.g. google.com, github.com) from being mistakenly configured as the API base URL.
 */

(function () {
  'use strict';

  const PRODUCTION_BACKEND = 'https://cloud-health-monitor-devops.onrender.com';
  const LOCAL_BACKEND = 'http://localhost:8081';

  // Known target domains that must NEVER be treated as backend API servers
  const TARGET_DOMAINS_BLACKLIST = [
    'google.com', 'www.google.com', 'google.co.in',
    'github.com', 'www.github.com',
    'amazon.com', 'amazon.in', 'www.amazon.com', 'www.amazon.in',
    'microsoft.com', 'apple.com', 'example.com', 'yahoo.com', 'bing.com'
  ];

  function sanitizeStoredBackendUrl() {
    try {
      const stored = localStorage.getItem('CHM_API_BASE_URL');
      if (stored) {
        const lower = stored.toLowerCase();
        const isBlacklisted = TARGET_DOMAINS_BLACKLIST.some(domain => lower.includes(domain));
        if (isBlacklisted) {
          console.warn('[CHM] Detected monitored website domain configured as backend API URL. Self-healing: removing invalid URL:', stored);
          localStorage.removeItem('CHM_API_BASE_URL');
          localStorage.removeItem('CHM_BACKEND_PRESET');
        }
      }
    } catch (e) {
      // Storage unavailable
    }
  }

  // Run cleanup before initialization
  sanitizeStoredBackendUrl();

  const isVercel = window.location.hostname.includes('vercel.app');
  const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

  window.CHM_CONFIG = {
    PRODUCTION_BACKEND,
    LOCAL_BACKEND,
    defaultBackend: isVercel ? PRODUCTION_BACKEND : (isLocalhost ? LOCAL_BACKEND : '')
  };

  if (!window.__API_BASE_URL__) {
    window.__API_BASE_URL__ = window.CHM_CONFIG.defaultBackend;
  }
})();
