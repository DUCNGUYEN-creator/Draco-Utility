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
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Target kinds with their default colors, plus entity and ore classification. */
public final class Targets {
    private Targets() {}

    public enum Kind {
        ORE("Ore", 0xFFFFFF),
        PLAYER("Player", 0xFF3333),
        HOSTILE("Hostile mob", 0xFF9900),
        PASSIVE("Passive mob", 0x33DD55),
        ITEM("Item", 0xFFD400),
        WAYPOINT("Waypoint", 0x33CCFF);

        public final String label;
        public final int rgb;

        Kind(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }

        public int argbColor() {
            return 0xFF000000 | rgb;
        }
    }

    public enum OreFilter {
        ALL("Any ore", ""),
        COAL("Coal", "coal_ore"),
        IRON("Iron", "iron_ore"),
        COPPER("Copper", "copper_ore"),
        GOLD("Gold", "gold_ore"),
        REDSTONE("Redstone", "redstone_ore"),
        LAPIS("Lapis", "lapis_ore"),
        DIAMOND("Diamond", "diamond_ore"),
        EMERALD("Emerald", "emerald_ore"),
        QUARTZ("Quartz", "quartz_ore"),
        DEBRIS("Ancient Debris", "ancient_debris");

        public final String label;
        private final String key;

        OreFilter(String label, String key) {
            this.label = label;
            this.key = key;
        }

        public boolean matches(String path) {
            if (!(path.endsWith("_ore") || path.equals("ancient_debris"))) return false;
            return key.isEmpty() || path.contains(key);
        }
    }

    private static final Map<Block, String> PATHS = new ConcurrentHashMap<>();

    /** Registry path of a block ("diamond_ore"), cached because chunk scans call this a lot. */
    public static String pathOf(Block block) {
        return PATHS.computeIfAbsent(block, b -> Registries.BLOCK.getId(b).getPath());
    }

    public static boolean isOre(BlockState state, OreFilter filter) {
        return filter.matches(pathOf(state.getBlock()));
    }

    /** Returns the kind of an entity, or null if it should be ignored. */
    public static Kind classify(Entity e, Entity self) {
        if (e == self || e.isSpectator()) return null;
        if (e instanceof ItemEntity) return Kind.ITEM;
        if (e instanceof PlayerEntity) return Kind.PLAYER;
        if (e instanceof ArmorStandEntity) return null;
        if (e instanceof Monster) return Kind.HOSTILE;
        if (e instanceof LivingEntity) return Kind.PASSIVE;
        return null;
    }
}
