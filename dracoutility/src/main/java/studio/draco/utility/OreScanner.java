/*
 * Draco Utility - client-side utility mod for Minecraft (Fabric)
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

package studio.draco.utility;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds ores around the player, one horizontal layer per tick, so it never causes a lag spike.
 * A full pass takes (2 * radius + 1) ticks, then the result list is swapped in.
 */
public final class OreScanner {
    private static List<BlockPos> result = List.of();
    private static final List<BlockPos> building = new ArrayList<>();
    private static int layer = -1;
    private static int radius = -1;
    private static int filter = -1;
    private static BlockPos origin = BlockPos.ORIGIN;

    private OreScanner() {}

    public static List<BlockPos> results() {
        return result;
    }

    public static void tick(MinecraftClient mc, boolean want) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (!want || w == null || p == null) {
            if (!result.isEmpty()) result = List.of();
            building.clear();
            layer = -1;
            return;
        }

        // (Re)start when the settings changed.
        if (layer < 0 || radius != Features.oreRadius || filter != Features.oreFilter) {
            building.clear();
            result = List.of();
            layer = 0;
            radius = Features.oreRadius;
            filter = Features.oreFilter;
            origin = p.getBlockPos();
        }

        int y = origin.getY() - radius + layer;
        if (!w.isOutOfHeightLimit(y)) {
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    pos.set(origin.getX() + dx, y, origin.getZ() + dz);
                    BlockState s = w.getBlockState(pos);
                    if (!s.isAir() && Targets.isOre(s, filter)) building.add(pos.toImmutable());
                }
            }
        }

        layer++;
        if (layer > 2 * radius) {
            result = new ArrayList<>(building);
            building.clear();
            layer = 0;
            origin = p.getBlockPos();
        }
    }
}
