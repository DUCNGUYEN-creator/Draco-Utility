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

package studio.draco.utility.hud.elements;

import net.minecraft.entity.LivingEntity;
import studio.draco.utility.hud.HudContext;
import studio.draco.utility.hud.HudElement;
import studio.draco.utility.platform.Access;

/** Name, health bar and distance of the creature you are looking at (kept for 3 seconds). */
public final class TargetHudElement extends HudElement {
    private LivingEntity target;
    private long seenAt;

    public TargetHudElement() {
        super("targethud", "Target HUD", Anchor.BOTTOM_RIGHT, 4, 50, true);
    }

    @Override public boolean requiresPermission() { return true; }

    @Override
    public void render(HudContext c) {
        long now = System.currentTimeMillis();
        if (c.mc.targetedEntity instanceof LivingEntity le) {
            target = le;
            seenAt = now;
        }
        if (target != null && (now - seenAt > 3000 || !target.isAlive())) target = null;

        String name;
        float health;
        float max;
        double dist;
        if (target != null && c.mc.player != null) {
            name = target.getName().getString();
            health = target.getHealth();
            max = Math.max(1f, target.getMaxHealth());
            dist = c.mc.player.distanceTo(target);
        } else if (c.editor) {
            name = title();
            health = 14;
            max = 20;
            dist = 3;
        } else {
            width = 10;
            height = 10;
            return;
        }
        width = 120;
        height = 30;
        c.fill(0, 0, width, height, 0xA0101418);
        c.text(name, 4, 3, 0xFFFFFF);
        c.fill(4, 15, width - 4, 21, 0xFF2A2E35);
        float frac = Math.max(0f, Math.min(1f, health / max));
        int color = frac > 0.5f ? 0x55FF55 : frac > 0.25f ? 0xFFCC33 : 0xFF4444;
        c.fill(4, 15, 4 + (int) ((width - 8) * frac), 21, 0xFF000000 | color);
        c.text(String.format("%.1f / %.1f   %.1fm", health, max, dist), 4, 21, 0xC0C8D0);
    }
}
