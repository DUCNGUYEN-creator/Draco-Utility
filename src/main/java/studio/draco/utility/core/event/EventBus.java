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

package studio.draco.utility.core.event;

import studio.draco.utility.core.Log;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Typed event bus. Listeners are matched on the exact event class, ordered by priority
 * (stable inside one priority), isolated from each other's exceptions, and grouped by owner so
 * a whole module can be unsubscribed in one call.
 */
public final class EventBus {
    public interface ErrorHandler {
        void onError(Object owner, Event event, Throwable error);
    }

    private static final class Entry {
        final EventPriority priority;
        final Object owner;
        final String key;
        final Consumer<Object> handler;

        Entry(EventPriority priority, Object owner, String key, Consumer<Object> handler) {
            this.priority = priority;
            this.owner = owner;
            this.key = key;
            this.handler = handler;
        }
    }

    /** Handle returned by subscribe(); cancel() removes just that listener. */
    public static final class Subscription {
        private final EventBus bus;
        private final Class<?> type;
        private final Entry entry;

        private Subscription(EventBus bus, Class<?> type, Entry entry) {
            this.bus = bus;
            this.type = type;
            this.entry = entry;
        }

        public void cancel() {
            CopyOnWriteArrayList<Entry> list = bus.listeners.get(type);
            if (list != null) list.remove(entry);
        }
    }

    private final Map<Class<?>, CopyOnWriteArrayList<Entry>> listeners = new ConcurrentHashMap<>();
    private volatile Thread owningThread;
    private volatile ErrorHandler errorHandler =
            (owner, event, error) -> Log.error("Listener of " + owner + " failed on " + event.getClass().getSimpleName(), error);
    private volatile int threadViolations;

    /** Events may only be posted from this thread; posts from other threads are dropped and counted. */
    public void bindThread(Thread thread) {
        this.owningThread = thread;
    }

    public int threadViolations() {
        return threadViolations;
    }

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = Objects.requireNonNull(handler);
    }

    public <T extends Event> Subscription subscribe(Class<T> type, Object owner, Consumer<? super T> handler) {
        return subscribe(type, EventPriority.NORMAL, owner, null, handler);
    }

    /**
     * @param key optional id; subscribing twice with the same (type, owner, key) is ignored,
     *            which prevents duplicate listeners.
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> Subscription subscribe(Class<T> type, EventPriority priority, Object owner,
                                                    String key, Consumer<? super T> handler) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(priority);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(handler);
        CopyOnWriteArrayList<Entry> list = listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            if (key != null) {
                for (Entry e : list) {
                    if (e.owner == owner && key.equals(e.key)) return new Subscription(this, type, e);
                }
            }
            Entry entry = new Entry(priority, owner, key, (Consumer<Object>) (Consumer<?>) handler);
            int index = list.size();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).priority.ordinal() > priority.ordinal()) {
                    index = i;
                    break;
                }
            }
            list.add(index, entry);
            return new Subscription(this, type, entry);
        }
    }

    /** Removes every listener registered by this owner. Returns how many were removed. */
    public int unsubscribeAll(Object owner) {
        int removed = 0;
        for (CopyOnWriteArrayList<Entry> list : listeners.values()) {
            for (Entry e : list) {
                if (e.owner == owner && list.remove(e)) removed++;
            }
        }
        return removed;
    }

    public <T extends Event> T post(T event) {
        Thread bound = owningThread;
        if (bound != null && bound != Thread.currentThread()) {
            threadViolations++;
            return event;
        }
        CopyOnWriteArrayList<Entry> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) return event;
        for (Entry e : list) {
            if (event instanceof CancellableEvent c && c.isCancelled()) break;
            try {
                e.handler.accept(event);
            } catch (Throwable t) {
                errorHandler.onError(e.owner, event, t);
            }
        }
        return event;
    }

    /** True if at least one listener is registered for this exact event class (cheap guard before building an event). */
    public boolean hasListeners(Class<? extends Event> type) {
        CopyOnWriteArrayList<Entry> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    public int listenerCount() {
        int n = 0;
        for (CopyOnWriteArrayList<Entry> list : listeners.values()) n += list.size();
        return n;
    }

    public int listenerCount(Object owner) {
        int n = 0;
        for (CopyOnWriteArrayList<Entry> list : listeners.values()) {
            for (Entry e : list) if (e.owner == owner) n++;
        }
        return n;
    }
}
