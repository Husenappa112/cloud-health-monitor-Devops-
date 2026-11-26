/**
 * Cloud Health Monitor — Runtime API Configuration
 *
 * For Vercel hosting:
 * Set window.__API_BASE_URL__ to your deployed Render backend URL,
 * for example: "https://cloud-health-monitor-backend.onrender.com"
 *
 * When left empty (""), it defaults to the same origin (local development / Spring Boot embedded).
 */
window.__API_BASE_URL__ = window.__API_BASE_URL__ || "";
