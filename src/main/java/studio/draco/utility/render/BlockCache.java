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

package studio.draco.utility.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.services.BlockScanner;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Per-frame cache for block ESPs. The expensive part (checking each found block, measuring its
 * distance, building its Box, sorting by distance) is redone only when
 * <ul>
 *   <li>the scanner published a new result,</li>
 *   <li>the player moved more than half a block since the last rebuild, or</li>
 *   <li>half a second passed (so a block that was mined disappears quickly).</li>
 * </ul>
 * Every other frame just reads the prepared entries, so the render thread does no per-frame searching,
 * no distance maths and no Box allocation.
 */
public final class BlockCache {
    public record Entry(BlockPos pos, Box box, double distSq) { }

    private static final double MOVE_THRESHOLD_SQ = 0.5 * 0.5;
    private static final long MAX_AGE_NANOS = 500_000_000L;
    private static final Entry[] NONE = new Entry[0];

    private Entry[] entries = NONE;
    private int version = -1;
    private ClientWorld world;
    private Vec3d builtAt;
    private long builtNanos;

    public void clear() {
        entries = NONE;
        version = -1;
        world = null;
        builtAt = null;
    }

    /** Entries sorted nearest first. The returned array must not be modified. */
    public Entry[] get(BlockScanner.Query query, ClientWorld w, Vec3d eye, Predicate<BlockState> stillValid) {
        long now = System.nanoTime();
        boolean stale = query.version() != version
                || w != world
                || builtAt == null
                || builtAt.squaredDistanceTo(eye) > MOVE_THRESHOLD_SQ
                || now - builtNanos > MAX_AGE_NANOS;
        if (stale) rebuild(query, w, eye, stillValid, now);
        return entries;
    }

    private void rebuild(BlockScanner.Query query, ClientWorld w, Vec3d eye, Predicate<BlockState> stillValid, long now) {
        long[] packed = query.packed();
        version = query.version();
        world = w;
        builtAt = eye;
        builtNanos = now;
        if (packed.length == 0) {
            entries = NONE;
            return;
        }
        Entry[] out = new Entry[packed.length];
        int n = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (long p : packed) {
            m.set(p);
            if (!stillValid.test(w.getBlockState(m))) continue; // mined or changed since the scan
            BlockPos pos = m.toImmutable();
            out[n++] = new Entry(pos, new Box(pos).expand(0.003), Vec3d.ofCenter(pos).squaredDistanceTo(eye));
        }
        Entry[] result = Arrays.copyOf(out, n);
        Arrays.sort(result, Comparator.comparingDouble(Entry::distSq));
        entries = result;
    }
}
