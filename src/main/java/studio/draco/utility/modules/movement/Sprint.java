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

package studio.draco.utility.modules.movement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.TickEvent;

public final class Sprint extends Module {
    private final Setting.Bool whenHungry = general.bool("whenHungry", "Sprint when hungry",
            "Keep sprinting even when the hunger bar is too low to sprint normally.", false);

    public Sprint() {
        super("sprint", "Sprint", "Sprints automatically while you walk forward.", ModuleCategory.MOVEMENT);
        capabilities(Capability.MOVEMENT);
    }

    @Override
    protected void onActivate() {
        on(TickEvent.Post.class, e -> {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) return;
            boolean moving = p.input.movementForward > 0;
            boolean fed = whenHungry.get() || p.getHungerManager().getFoodLevel() > 6;
            if (moving && fed && !p.isSneaking() && !p.horizontalCollision) p.setSprinting(true);
        });
    }
}
