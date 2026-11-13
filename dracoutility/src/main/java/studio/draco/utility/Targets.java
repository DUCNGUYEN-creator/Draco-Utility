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
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Target kinds (with their colors) and helpers to classify entities and ore blocks. */
public final class Targets {
    private Targets() {}

    public enum Kind {
        ORE("Quặng", 0xFFFFFF),
        PLAYER("Người chơi", 0xFF3333),
        HOSTILE("Quái thù địch", 0xFF9900),
        PASSIVE("Mob hiền", 0x33DD55),
        ITEM("Vật phẩm", 0xFFD400),
        WAYPOINT("Điểm đánh dấu", 0x33CCFF);

        public final String label;
        public final int rgb;

        Kind(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }

        public float r() { return ((rgb >> 16) & 0xFF) / 255f; }
        public float g() { return ((rgb >> 8) & 0xFF) / 255f; }
        public float b() { return (rgb & 0xFF) / 255f; }
        public int argb() { return 0xFF000000 | rgb; }
    }

    public static final String[] ORE_FILTER_NAMES = {
            "Tất cả", "Than", "Sắt", "Đồng", "Vàng", "Redstone", "Lapis",
            "Kim cương", "Ngọc lục bảo", "Quartz", "Ancient Debris"
    };
    private static final String[] ORE_FILTER_KEYS = {
            "", "coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore",
            "diamond_ore", "emerald_ore", "quartz_ore", "ancient_debris"
    };

    private static final Map<Block, String> PATHS = new ConcurrentHashMap<>();

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

    /** True if the block is an ore matching the filter index (0 = any ore). */
    public static boolean isOre(BlockState state, int filter) {
        String path = PATHS.computeIfAbsent(state.getBlock(), b -> Registries.BLOCK.getId(b).getPath());
        if (!(path.endsWith("_ore") || path.equals("ancient_debris"))) return false;
        String key = ORE_FILTER_KEYS[filter];
        return key.isEmpty() || path.contains(key);
    }
}
