/*
 * Draco Utility - client framework for Minecraft (Fabric)
 * Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package studio.draco.utility.core.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared services (entity cache, block scanner, waypoints...). Modules depend on services, not on each other. */
public final class ServiceRegistry {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T instance) {
        if (services.putIfAbsent(type, instance) != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
    }

    public <T> T get(Class<T> type) {
        Object s = services.get(type);
        if (s == null) throw new IllegalStateException("Service not available: " + type.getName());
        return type.cast(s);
    }

    public boolean has(Class<?> type) {
        return services.containsKey(type);
    }
}
