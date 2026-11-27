/**
 * Cloud Health Monitor — Runtime API Configuration
 * 
 * Auto-detects production Vercel hosting and routes requests to the deployed Render backend:
 * Backend: https://cloud-health-monitor-devops.onrender.com
 * Frontend: https://cloud-health-monitor-ui.vercel.app
 * 
 * In local development (localhost) or when served directly from Spring Boot,
 * it uses relative endpoints ("").
 */

(function () {
  'use strict';

  const isVercel = window.location.hostname.includes('vercel.app');
  const defaultRenderBackend = 'https://cloud-health-monitor-devops.onrender.com';

  if (!window.__API_BASE_URL__) {
    if (isVercel) {
      window.__API_BASE_URL__ = defaultRenderBackend;
    } else {
      window.__API_BASE_URL__ = '';
    }
  }
})();
