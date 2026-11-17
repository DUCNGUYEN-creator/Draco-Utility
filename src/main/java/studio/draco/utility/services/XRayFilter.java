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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;

import java.util.Set;

/**
 * X-ray whitelist: only ores, mineral blocks, building blocks, wood, leaves and functional blocks
 * stay visible. Everything else is hidden (see-through).
 */
public final class XRayFilter {
    /**
     * Hidden flag per block, indexed by the block's registry id. Built once on the main thread
     * (see {@link #build()}) and never modified afterwards: chunk-compile worker threads only read
     * an immutable array, so a lookup needs no lock, no map and no shared mutable state.
     */
    private static volatile boolean[] hiddenByRawId;

    private static final Set<Block> ALWAYS_VISIBLE = Set.of(
            Blocks.ANCIENT_DEBRIS,
            Blocks.COAL_BLOCK, Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK, Blocks.GOLD_BLOCK,
            Blocks.REDSTONE_BLOCK, Blocks.LAPIS_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK,
            Blocks.NETHERITE_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK,
            Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.QUARTZ_BLOCK,
            Blocks.COMPOSTER, Blocks.CAULDRON, Blocks.GRINDSTONE, Blocks.STONECUTTER, Blocks.LOOM,
            Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.OBSERVER, Blocks.REDSTONE_LAMP, Blocks.NOTE_BLOCK,
            Blocks.TNT, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.END_PORTAL_FRAME,
            Blocks.LADDER, Blocks.SCAFFOLDING, Blocks.BOOKSHELF,
            Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT, Blocks.HAY_BLOCK,
            Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE
    );

    private static final String[] VISIBLE_NAME_PARTS = {
            "_ore", "planks", "mosaic",
            "brick", "tiles", "concrete", "glass",
            "polished", "chiseled", "cut_", "smooth_",
            "stained", "glazed", "carpet", "wool",
            "purpur", "prismarine", "quartz", "copper",
            "stairs", "slab", "_wall", "fence", "door",
            "bars", "chain",
            "table", "anvil", "rail", "lever", "repeater", "comparator"
    };

    private XRayFilter() {}

    /** Precomputes the table for every registered block. Call from the main thread before X-ray turns on. */
    public static void build() {
        boolean[] table = new boolean[Registries.BLOCK.size()];
        for (Block block : Registries.BLOCK) {
            int id = Registries.BLOCK.getRawId(block);
            if (id >= 0 && id < table.length) table[id] = !computeVisible(block);
        }
        hiddenByRawId = table;
    }

    /** True if this block should NOT be drawn while X-ray is active. Safe to call from any thread. */
    public static boolean isHidden(BlockState state) {
        // Anything with a block entity is functional: chests, furnaces, spawners, signs, beds...
        if (state.hasBlockEntity()) return false;
        boolean[] table = hiddenByRawId;
        if (table == null) return false; // not built yet: show everything rather than guess
        int id = Registries.BLOCK.getRawId(state.getBlock());
        return id >= 0 && id < table.length && table[id];
    }

    private static boolean computeVisible(Block block) {
        if (ALWAYS_VISIBLE.contains(block)) return true;
        BlockState s = block.getDefaultState();
        if (s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.PLANKS)) return true;
        String path = Targets.pathOf(block);
        if (path.equals("smooth_basalt") || path.equals("moss_carpet")) return false;
        for (String part : VISIBLE_NAME_PARTS) {
            if (path.contains(part)) return true;
        }
        return false;
    }
}
