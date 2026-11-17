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

package studio.draco.utility.core.lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered startup/shutdown. Steps are registered per phase and run in registration order.
 * A failing critical step aborts its phase; optional steps just record the error and continue.
 */
public final class Lifecycle {
    public enum Phase { PRE_INIT, PLATFORM_INIT, CORE_INIT, MODULE_REGISTRATION, CONFIG_LOAD, POST_INIT, SHUTDOWN }

    public record Result(Phase phase, String name, long nanos, Throwable error) {}

    private record Step(Phase phase, String name, Runnable action, boolean critical) {}

    public static final class LifecycleException extends RuntimeException {
        public LifecycleException(String message, Throwable cause) { super(message, cause); }
    }

    private final List<Step> steps = new ArrayList<>();
    private final List<Result> results = new ArrayList<>();

    public void register(Phase phase, String name, Runnable action) {
        steps.add(new Step(phase, name, action, true));
    }

    public void registerOptional(Phase phase, String name, Runnable action) {
        steps.add(new Step(phase, name, action, false));
    }

    /** Runs every phase except SHUTDOWN, in order. */
    public void startAll() {
        for (Phase p : Phase.values()) {
            if (p != Phase.SHUTDOWN) run(p);
        }
    }

    public void shutdown() {
        try {
            run(Phase.SHUTDOWN);
        } catch (LifecycleException ignored) {
            // The failure is already in the report; shutdown must always finish.
        }
    }

    public void run(Phase phase) {
        for (Step s : new ArrayList<>(steps)) {
            if (s.phase != phase) continue;
            long t0 = System.nanoTime();
            Throwable error = null;
            try {
                s.action.run();
            } catch (Throwable t) {
                error = t;
            }
            results.add(new Result(phase, s.name, System.nanoTime() - t0, error));
            if (error != null && s.critical) {
                throw new LifecycleException("Startup step failed: " + phase + " / " + s.name, error);
            }
        }
    }

    public List<Result> results() { return List.copyOf(results); }

    public boolean hasErrors() {
        for (Result r : results) if (r.error != null) return true;
        return false;
    }

    /** Human-readable timing and error report for the log. */
    public String report() {
        StringBuilder sb = new StringBuilder("Draco startup report:\n");
        long total = 0;
        for (Result r : results) {
            total += r.nanos;
            sb.append(String.format("  %-20s %-34s %7.2f ms%s%n", r.phase, r.name, r.nanos / 1e6,
                    r.error == null ? "" : "  FAILED: " + r.error));
        }
        sb.append(String.format("  total %.2f ms%n", total / 1e6));
        return sb.toString();
    }
}
