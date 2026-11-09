package com.mointer.cloud_health_monitor.model;

import java.time.Instant;

/**
 * Represents a registered service and its last-known health state.
 */
public class ServiceEntry {

    private final String name;
    private final String url;

    // mutable health fields – updated by HealthCheckService
    private volatile String status = "UNKNOWN";
    private volatile Integer httpStatus;
    private volatile Long responseTimeMs;
    private volatile Instant checkedAt;

    public ServiceEntry(String name, String url) {
        this.name = name;
        this.url  = url;
    }

    // ── Immutable getters ──────────────────────────────────────────────────────

    public String getName()  { return name; }
    public String getUrl()   { return url;  }

    // ── Mutable health getters ─────────────────────────────────────────────────

    public String  getStatus()        { return status;        }
    public Integer getHttpStatus()    { return httpStatus;    }
    public Long    getResponseTimeMs(){ return responseTimeMs; }
    public Instant getCheckedAt()     { return checkedAt;     }

    // ── Mutable health setters ─────────────────────────────────────────────────

    public synchronized void recordHealthCheck(String status, Integer httpStatus,
                                               long responseTimeMs) {
        this.status         = status;
        this.httpStatus     = httpStatus;
        this.responseTimeMs = responseTimeMs;
        this.checkedAt      = Instant.now();
    }
}
