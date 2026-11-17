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

package studio.draco.utility.core.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Toast-style messages shared by every system; a HUD element draws them. */
public final class NotificationManager {
    public enum Level { INFO, SUCCESS, WARNING, ERROR }

    public record Notification(Level level, String title, String message, long createdAtMs) {}

    private final List<Notification> active = new ArrayList<>();
    private final LongSupplier clockMs;
    private volatile long durationMs = 4000;
    private volatile int maxVisible = 5;
    private volatile boolean enabled = true;

    public NotificationManager(LongSupplier clockMs) {
        this.clockMs = clockMs;
    }

    public void configure(boolean enabled, long durationMs, int maxVisible) {
        this.enabled = enabled;
        this.durationMs = Math.max(500, durationMs);
        this.maxVisible = Math.max(1, maxVisible);
    }

    public synchronized void push(Level level, String title, String message) {
        if (!enabled) return;
        active.add(new Notification(level, title, message, clockMs.getAsLong()));
        while (active.size() > maxVisible) active.remove(0);
    }

    public void info(String title, String message) { push(Level.INFO, title, message); }
    public void success(String title, String message) { push(Level.SUCCESS, title, message); }
    public void warning(String title, String message) { push(Level.WARNING, title, message); }
    public void error(String title, String message) { push(Level.ERROR, title, message); }

    /** Notifications still within their display time; expired ones are dropped. */
    public synchronized List<Notification> visible() {
        long now = clockMs.getAsLong();
        active.removeIf(n -> now - n.createdAtMs() > durationMs);
        return new ArrayList<>(active);
    }

    public long durationMs() { return durationMs; }
}
