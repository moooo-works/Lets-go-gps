package com.moooo_works.letsgogps.domain.healthcheck

interface SystemHealthCheck {
    /**
     * Synchronously evaluate every [HealthCheckItem]. Cheap — safe to call from
     * the main thread (no IO, no IPC waits beyond a few system service queries).
     */
    fun refresh(): HealthCheckState
}
