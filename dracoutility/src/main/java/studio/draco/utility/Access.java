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

import net.minecraft.client.MinecraftClient;

/**
 * Central permission gate. Every feature is locked unless the player already
 * has permission level 2+ (singleplayer with cheats, or OP on the server).
 * If the level cannot be determined (e.g. through a version-translation layer),
 * it is treated as 0 and everything stays locked.
 */
public final class Access {
    private Access() {}

    public static boolean allowed(MinecraftClient mc) {
        return mc != null && mc.player != null && mc.player.hasPermissionLevel(2);
    }
}
