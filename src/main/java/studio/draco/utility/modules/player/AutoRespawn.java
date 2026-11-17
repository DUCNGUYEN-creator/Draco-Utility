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

package studio.draco.utility.modules.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.TickEvent;

public final class AutoRespawn extends Module {
    private final Setting.Int delay = general.integer("delay", "Delay (ticks)", "Wait before respawning.", 20, 0, 100);
    private int waited;

    public AutoRespawn() {
        super("autorespawn", "Auto Respawn", "Respawns automatically when you die.", ModuleCategory.PLAYER);
        capabilities(Capability.AUTOMATION);
    }

    @Override
    protected void onActivate() {
        waited = 0;
        on(TickEvent.Post.class, e -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.currentScreen instanceof DeathScreen) {
                if (++waited >= delay.get()) {
                    mc.player.requestRespawn();
                    waited = 0;
                }
            } else {
                waited = 0;
            }
        });
    }
}
