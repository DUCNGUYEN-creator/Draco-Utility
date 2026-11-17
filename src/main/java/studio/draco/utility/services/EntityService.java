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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import studio.draco.utility.Draco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * One shared scan of the entities around the player per tick. Modules call acquire() while they
 * need it; with no consumers nothing is scanned at all.
 */
public final class EntityService {
    public record Tracked(Entity entity, Targets.Kind kind, double distSq) { }

    public static final double RADIUS = 128;

    private final Set<Object> consumers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Tracked[] nearest = new Tracked[Targets.Kind.values().length];
    private List<Tracked> tracked = List.of();

    public void acquire(Object owner) { consumers.add(owner); }
    public void release(Object owner) { consumers.remove(owner); }
    public boolean active() { return !consumers.isEmpty(); }

    public List<Tracked> entities() { return tracked; }

    public Tracked nearest(Targets.Kind kind) { return nearest[kind.ordinal()]; }

    /** Refreshes the cache. Called once at the start of every client tick. */
    public void update(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || consumers.isEmpty()) {
            tracked = List.of();
            Arrays.fill(nearest, null);
            return;
        }
        long t0 = System.nanoTime();
        Box area = p.getBoundingBox().expand(RADIUS);
        List<Tracked> out = new ArrayList<>();
        Arrays.fill(nearest, null);
        for (Entity e : w.getEntitiesByClass(Entity.class, area, ent -> true)) {
            Targets.Kind kind = Targets.classify(e, p);
            if (kind == null) continue;
            Tracked t = new Tracked(e, kind, p.squaredDistanceTo(e));
            out.add(t);
            Tracked best = nearest[kind.ordinal()];
            if (best == null || t.distSq() < best.distSq()) nearest[kind.ordinal()] = t;
        }
        tracked = out;
        Draco d = Draco.maybe();
        if (d != null) d.profiler.record("entities", System.nanoTime() - t0);
    }
}
