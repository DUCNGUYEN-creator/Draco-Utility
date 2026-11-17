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

package studio.draco.utility.services;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import studio.draco.utility.Draco;
import studio.draco.utility.core.scheduler.Task;
import studio.draco.utility.core.scheduler.WorkScheduler;
import studio.draco.utility.core.util.LongList;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Finds blocks around the player (ores, chests...) one horizontal layer at a time through the
 * WorkScheduler, so scanning never causes a lag spike. A query only runs while it has an owner.
 *
 * Results are kept as packed longs ({@link BlockPos#asLong}), not as BlockPos objects: a big scan
 * allocates one small array per cycle instead of thousands of short-lived objects, which is what
 * used to cause garbage-collector micro-stutters.
 */
public final class BlockScanner {
    private static final long[] NONE = new long[0];

    /** A named search: radius + predicate + the latest results. */
    public static final class Query {
        private final String id;
        private final IntSupplier radius;
        private final Predicate<BlockState> predicate;
        private final Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());
        private volatile long[] results = NONE;
        private volatile int version;
        private WorkScheduler.Handle handle;

        private Query(String id, IntSupplier radius, Predicate<BlockState> predicate) {
            this.id = id;
            this.radius = radius;
            this.predicate = predicate;
        }

        public String id() { return id; }
        public boolean active() { return !owners.isEmpty(); }

        /** Packed positions of the latest finished scan. The array is never modified after it is published. */
        public long[] packed() { return results; }

        /** Number of positions in the latest finished scan. */
        public int size() { return results.length; }

        /** Increases every time a new scan result is published; lets callers cache work between scans. */
        public int version() { return version; }

        private void publish(long[] r) {
            results = r;
            version++;
        }
    }

    private final WorkScheduler scheduler;
    private final Map<String, Query> byId = new HashMap<>();

    public BlockScanner(WorkScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Creates (or replaces) the query with this id. It does nothing until acquired. */
    public Query create(String id, IntSupplier radius, Predicate<BlockState> predicate) {
        Query old = byId.put(id, new Query(id, radius, predicate));
        if (old != null) stop(old);
        return byId.get(id);
    }

    public Query find(String id) { return byId.get(id); }

    public void acquire(Query q, Object owner) {
        q.owners.add(owner);
        if (q.handle == null) q.handle = scheduler.submit(new ScanTask(q), 5);
    }

    public void release(Query q, Object owner) {
        q.owners.remove(owner);
        if (q.owners.isEmpty()) stop(q);
    }

    /** Gives back every query this owner holds. Safe to call for an owner that holds nothing. */
    public void releaseAll(Object owner) {
        for (Query q : byId.values()) {
            if (q.owners.contains(owner)) release(q, owner);
        }
    }

    private static void stop(Query q) {
        if (q.handle != null) q.handle.cancel();
        q.handle = null;
        q.publish(NONE);
    }

    private static final class ScanTask implements Task {
        private final Query q;
        private final LongList building = new LongList(1024);
        private int layer = -1;
        private int radius = -1;
        private BlockPos origin = BlockPos.ORIGIN;
        private ClientWorld world;

        ScanTask(Query q) { this.q = q; }

        @Override public String name() { return "scan:" + q.id; }

        @Override
        public void step(long deadline) {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld w = mc.world;
            ClientPlayerEntity p = mc.player;
            if (w == null || p == null) {
                layer = -1;
                building.clear();
                if (q.size() != 0) q.publish(NONE);
                return;
            }
            int r = q.radius.getAsInt();
            if (layer < 0 || r != radius || w != world) {
                building.clear();
                if (q.size() != 0) q.publish(NONE);
                layer = 0;
                radius = r;
                world = w;
                origin = p.getBlockPos();
            }
            BlockPos.Mutable pos = new BlockPos.Mutable();
            do {
                int y = origin.getY() - radius + layer;
                if (!w.isOutOfHeightLimit(y)) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            pos.set(origin.getX() + dx, y, origin.getZ() + dz);
                            BlockState s = w.getBlockState(pos);
                            if (!s.isAir() && q.predicate.test(s)) building.add(pos.asLong());
                        }
                    }
                }
                layer++;
                if (layer > 2 * radius) {
                    q.publish(building.toArray());
                    building.clear();
                    layer = 0;
                    origin = p.getBlockPos();
                    Draco d = Draco.maybe();
                    if (d != null) d.profiler.count("scan.cycles", 1);
                    break;
                }
            } while (System.nanoTime() < deadline);
        }
    }
}
