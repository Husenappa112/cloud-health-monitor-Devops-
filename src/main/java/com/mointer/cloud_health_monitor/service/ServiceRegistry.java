package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.exception.ServiceAlreadyExistsException;
import com.mointer.cloud_health_monitor.exception.ServiceNotFoundException;
import com.mointer.cloud_health_monitor.model.ServiceEntry;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory store for all registered services.
 */
@Service
public class ServiceRegistry {

    private final ConcurrentMap<String, ServiceEntry> registry = new ConcurrentHashMap<>();

    /** Normalised key (lower-case, trimmed) for a service name. */
    private static String key(String name) {
        return name.trim().toLowerCase();
    }

    /**
     * Register a new service.
     *
     * @throws ServiceAlreadyExistsException if the name is already taken (case-insensitive).
     */
    public ServiceEntry register(String name, String url) {
        ServiceEntry entry = new ServiceEntry(name.trim(), url.trim());
        if (registry.putIfAbsent(key(name), entry) != null) {
            throw new ServiceAlreadyExistsException(name.trim());
        }
        return entry;
    }

    /**
     * Retrieve a service by name.
     *
     * @throws ServiceNotFoundException if the name is not registered.
     */
    public ServiceEntry getByName(String name) {
        ServiceEntry entry = registry.get(key(name));
        if (entry == null) {
            throw new ServiceNotFoundException(name);
        }
        return entry;
    }

    /** Removes a service by name; throws if not found. */
    public void delete(String name) {
        if (registry.remove(key(name)) == null) {
            throw new ServiceNotFoundException(name);
        }
    }

    /** All currently registered services (unmodifiable snapshot). */
    public Collection<ServiceEntry> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** Whether a service with the given name exists. */
    public boolean exists(String name) {
        return registry.containsKey(key(name));
    }
}
