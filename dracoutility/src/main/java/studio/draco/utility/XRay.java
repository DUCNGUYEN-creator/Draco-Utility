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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * X-ray whitelist: only ores, mineral blocks, building blocks, wood, leaves and
 * functional blocks stay visible. Everything else is hidden (see-through).
 */
public final class XRay {
    /** Per-block cache, because chunk building calls this very often (from worker threads). */
    private static final Map<Block, Boolean> VISIBLE_CACHE = new ConcurrentHashMap<>();

    /** Blocks that are always visible, no matter their registry name. */
    private static final Set<Block> ALWAYS_VISIBLE = Set.of(
            // Ores that do not end with "_ore", and mineral blocks
            Blocks.ANCIENT_DEBRIS,
            Blocks.COAL_BLOCK, Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK, Blocks.GOLD_BLOCK,
            Blocks.REDSTONE_BLOCK, Blocks.LAPIS_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK,
            Blocks.NETHERITE_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK,
            Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.QUARTZ_BLOCK,
            // Functional / special blocks
            Blocks.COMPOSTER, Blocks.CAULDRON, Blocks.GRINDSTONE, Blocks.STONECUTTER, Blocks.LOOM,
            Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.OBSERVER, Blocks.REDSTONE_LAMP, Blocks.NOTE_BLOCK,
            Blocks.TNT, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.END_PORTAL_FRAME,
            Blocks.LADDER, Blocks.SCAFFOLDING, Blocks.BOOKSHELF,
            // Decorative / building
            Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT, Blocks.HAY_BLOCK,
            Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE
    );

    /** If a block's registry path contains one of these, it counts as a building / functional block. */
    private static final String[] VISIBLE_NAME_PARTS = {
            "_ore",                                   // every ore, including deepslate and nether ores
            "planks", "mosaic",                       // wood building blocks
            "brick", "tiles", "concrete", "glass",    // bricks, tiles, concrete, glass
            "polished", "chiseled", "cut_", "smooth_",
            "stained", "glazed", "carpet", "wool",
            "purpur", "prismarine", "quartz", "copper",
            "stairs", "slab", "_wall", "fence", "door", // stairs, slabs, walls, fences, doors, trapdoors
            "bars", "chain",
            "table", "anvil", "rail", "lever", "repeater", "comparator" // functional
    };

    private XRay() {}

    /** True if this block should NOT be drawn while X-ray is active. */
    public static boolean isHidden(BlockState state) {
        // Anything with a block entity is functional: chests, furnaces, spawners, signs, beds, beacons, hoppers...
        if (state.hasBlockEntity()) return false;
        return !VISIBLE_CACHE.computeIfAbsent(state.getBlock(), XRay::computeVisible);
    }

    private static boolean computeVisible(Block block) {
        if (ALWAYS_VISIBLE.contains(block)) return true;

        BlockState s = block.getDefaultState();
        // Wood and leaves
        if (s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.PLANKS)) return true;

        String path = Registries.BLOCK.getId(block).getPath();
        // Natural blocks that would otherwise match a name pattern
        if (path.equals("smooth_basalt") || path.equals("moss_carpet")) return false;

        for (String part : VISIBLE_NAME_PARTS) {
            if (path.contains(part)) return true;
        }
        return false;
    }
}
