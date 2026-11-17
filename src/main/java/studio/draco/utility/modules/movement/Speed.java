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
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.TickEvent;

/** Moves faster in the direction you are walking. Needs a profile that allows movement modification. */
public final class Speed extends Module {
    private final Setting.Dbl speed = general.dbl("speed", "Speed", "Horizontal speed in blocks per tick. Walking is about 0.22, sprinting about 0.28.", 0.4, 0.1, 2.0);
    private final Setting.Bool onlyGround = general.bool("onlyGround", "Only on ground", "Do not change your speed while in the air.", true);

    public Speed() {
        super("speed", "Speed", "Moves faster in the direction you are walking.", ModuleCategory.MOVEMENT);
        capabilities(Capability.MOVEMENT);
        conflictsWith("flight");
    }

    @Override
    protected void onActivate() {
        on(TickEvent.Post.class, e -> tick());
    }

    private void tick() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null || !p.isAlive() || p.hasVehicle() || p.isFallFlying() || p.isTouchingWater() || p.isClimbing()) return;
        if (onlyGround.get() && !p.isOnGround()) return;

        float forward = p.input.movementForward;
        float sideways = p.input.movementSideways; // positive = left
        if (forward == 0 && sideways == 0) return;
        double yaw = Math.toRadians(p.getYaw());
        double dx = -Math.sin(yaw) * forward + Math.cos(yaw) * sideways;
        double dz = Math.cos(yaw) * forward + Math.sin(yaw) * sideways;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return;
        Vec3d v = p.getVelocity();
        p.setVelocity(dx / len * speed.get(), v.y, dz / len * speed.get());
    }
}
