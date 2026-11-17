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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Cheap per-tick facts about the local player and the nearest other player. */
public final class PlayerService {
    private PlayerEntity nearestPlayer;
    private double nearestDistance;
    private double speed; // blocks per second, smoothed
    private Vec3d lastPos;

    public PlayerEntity nearestPlayer() { return nearestPlayer; }
    public double nearestPlayerDistance() { return nearestDistance; }
    public double speed() { return speed; }

    public void update(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null) {
            nearestPlayer = null;
            lastPos = null;
            speed = 0;
            return;
        }
        Vec3d pos = p.getPos();
        if (lastPos != null) {
            double blocksPerSecond = pos.distanceTo(lastPos) * 20.0;
            speed += (blocksPerSecond - speed) * 0.2;
        }
        lastPos = pos;

        PlayerEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (AbstractClientPlayerEntity other : w.getPlayers()) {
            if (other == p || other.isSpectator()) continue;
            double d = p.squaredDistanceTo(other);
            if (d < bestD) {
                bestD = d;
                best = other;
            }
        }
        nearestPlayer = best;
        nearestDistance = best == null ? 0 : Math.sqrt(bestD);
    }
}
