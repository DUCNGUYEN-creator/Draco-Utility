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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Client-side night vision shared by Fullbright and X-ray. The effect is kept alive while at
 * least one owner holds it and removed again when the last owner lets go.
 */
public final class NightVision {
    private static final Set<Object> OWNERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean applied;

    private NightVision() {}

    public static void acquire(Object owner) { OWNERS.add(owner); }
    public static void release(Object owner) { OWNERS.remove(owner); }

    /** Call every client tick. */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            applied = false;
            return;
        }
        if (!OWNERS.isEmpty()) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || (!cur.isInfinite() && cur.getDuration() < 600)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
            }
            applied = true;
        } else if (applied) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            applied = false;
        }
    }
}
