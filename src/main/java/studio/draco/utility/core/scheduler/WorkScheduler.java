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

package studio.draco.utility.core.scheduler;

import studio.draco.utility.core.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Gives heavy jobs (world scans, map sampling) a per-tick time budget. Tasks that were starved
 * in one tick run first in the next, so every task makes progress.
 */
public final class WorkScheduler {
    private static final class Entry {
        final Task task;
        final int priority;
        boolean cancelled;
        boolean starved; // was skipped last tick because the budget ran out
        long lastRunTick = -1;
        long lastNanos;

        Entry(Task task, int priority) {
            this.task = task;
            this.priority = priority;
        }
    }

    public static final class Handle {
        private final Entry entry;

        private Handle(Entry entry) { this.entry = entry; }

        public void cancel() { entry.cancelled = true; }
        public boolean isCancelled() { return entry.cancelled; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final LongSupplier nanoClock;
    private long tickCounter;
    private long lastTickNanos;
    private int lastTasksRun;
    private int lastTasksSkipped;

    public WorkScheduler() {
        this(System::nanoTime);
    }

    public WorkScheduler(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /** Higher priority runs earlier within a tick. */
    public Handle submit(Task task, int priority) {
        Entry e = new Entry(task, priority);
        entries.add(e);
        return new Handle(e);
    }

    public void tick(long budgetNanos) {
        tickCounter++;
        long start = nanoClock.getAsLong();
        long deadline = start + budgetNanos;

        List<Entry> order = new ArrayList<>(entries);
        // Starved tasks first (aging), then by priority, then least recently run.
        order.sort(Comparator.<Entry>comparingInt(e -> e.starved ? 0 : 1)
                .thenComparingInt(e -> -e.priority)
                .thenComparingLong(e -> e.lastRunTick));

        int ran = 0;
        int skipped = 0;
        for (Entry e : order) {
            if (e.cancelled) continue;
            if (ran > 0 && nanoClock.getAsLong() >= deadline) {
                skipped++;
                e.starved = true;
                continue;
            }
            long t0 = nanoClock.getAsLong();
            try {
                e.task.step(deadline);
            } catch (Throwable t) {
                Log.error("Task '" + e.task.name() + "' failed and was removed", t);
                e.cancelled = true;
            }
            e.lastNanos = nanoClock.getAsLong() - t0;
            e.lastRunTick = tickCounter;
            e.starved = false;
            ran++;
            if (e.task.isDone()) e.cancelled = true;
        }
        entries.removeIf(e -> e.cancelled);
        lastTickNanos = nanoClock.getAsLong() - start;
        lastTasksRun = ran;
        lastTasksSkipped = skipped;
    }

    public long lastTickNanos() { return lastTickNanos; }
    public int lastTasksRun() { return lastTasksRun; }
    public int lastTasksSkipped() { return lastTasksSkipped; }
    public int taskCount() { return entries.size(); }
}
