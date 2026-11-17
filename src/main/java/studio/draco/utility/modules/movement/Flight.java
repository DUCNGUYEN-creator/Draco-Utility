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

public final class Flight extends Module {
    private final Setting.Dbl speed = general.dbl("speed", "Speed", "Horizontal speed in blocks per tick.", 0.6, 0.1, 3.0);
    private final Setting.Dbl vertical = general.dbl("vertical", "Vertical speed", "Up and down speed in blocks per tick.", 0.6, 0.1, 3.0);
    private final Setting.Bool resetFall = general.bool("resetFall", "Reset fall distance", "Avoids fall damage when you stop flying.", true);

    public Flight() {
        super("flight", "Flight", "Fly in survival by controlling your velocity. A dedicated server needs allow-flight=true.",
                ModuleCategory.MOVEMENT);
        capabilities(Capability.MOVEMENT);
        aliases("fly");
        conflictsWith("speed");
    }

    @Override
    protected void onActivate() {
        on(TickEvent.Post.class, e -> tick());
    }

    private void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || !p.isAlive() || p.hasVehicle() || p.isFallFlying()) return;

        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy += vertical.get();
        if (mc.options.sneakKey.isPressed()) vy -= vertical.get();

        float forward = p.input.movementForward;
        float sideways = p.input.movementSideways; // positive = left
        double yaw = Math.toRadians(p.getYaw());
        double dx = (-Math.sin(yaw) * forward + Math.cos(yaw) * sideways) * speed.get();
        double dz = (Math.cos(yaw) * forward + Math.sin(yaw) * sideways) * speed.get();

        p.setVelocity(dx, vy, dz);
        if (resetFall.get()) p.fallDistance = 0;
    }
}
