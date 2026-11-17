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

package studio.draco.utility.platform;

import net.minecraft.client.MinecraftClient;

/**
 * Central permission gate. Modules only run while the player already has permission level 2+
 * (singleplayer with cheats, or OP on the server). If the level cannot be determined (for
 * example through a version-translation layer) it counts as 0 and everything stays locked.
 */
/**
 * Questions about the current game state. Which modules may run is NOT decided here any more:
 * that is the job of the server policy (core/policy), per capability.
 */
public final class Access {
    private Access() {}

    /** A world is loaded and the player exists. */
    public static boolean inWorld(MinecraftClient mc) {
        return mc != null && mc.player != null && mc.world != null;
    }

    /**
     * The player is allowed to run server commands (permission level 2+). Only the screens that
     * work by sending /tp, /gamemode, /effect... need this; the server checks it again anyway.
     */
    public static boolean hasOperator(MinecraftClient mc) {
        return inWorld(mc) && mc.player.hasPermissionLevel(2);
    }
}
