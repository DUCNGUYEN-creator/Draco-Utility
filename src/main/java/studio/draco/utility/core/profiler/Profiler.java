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

package studio.draco.utility.core.profiler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cheap exponential-moving-average timers and counters for the performance HUD. */
public final class Profiler {
    private static final double ALPHA = 0.1;

    private final Map<String, double[]> timers = new LinkedHashMap<>(); // {ema, last}
    private final Map<String, long[]> counters = new LinkedHashMap<>();
    private final Map<String, Long> running = new LinkedHashMap<>();

    public synchronized void start(String name) {
        running.put(name, System.nanoTime());
    }

    public synchronized void end(String name) {
        Long t0 = running.remove(name);
        if (t0 != null) record(name, System.nanoTime() - t0);
    }

    public synchronized void record(String name, long nanos) {
        double[] t = timers.computeIfAbsent(name, k -> new double[]{nanos, nanos});
        t[0] += (nanos - t[0]) * ALPHA;
        t[1] = nanos;
    }

    public synchronized double averageMs(String name) {
        double[] t = timers.get(name);
        return t == null ? 0 : t[0] / 1e6;
    }

    public synchronized double lastMs(String name) {
        double[] t = timers.get(name);
        return t == null ? 0 : t[1] / 1e6;
    }

    public synchronized void count(String name, long amount) {
        counters.computeIfAbsent(name, k -> new long[1])[0] += amount;
    }

    public synchronized long counter(String name) {
        long[] c = counters.get(name);
        return c == null ? 0 : c[0];
    }

    /** Cache hit rate in percent from "<name>.hit" and "<name>.miss" counters. */
    public synchronized double hitRate(String name) {
        long hit = counter(name + ".hit");
        long miss = counter(name + ".miss");
        return hit + miss == 0 ? 0 : 100.0 * hit / (hit + miss);
    }
}
