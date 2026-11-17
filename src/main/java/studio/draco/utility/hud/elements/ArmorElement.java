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

import net.minecraft.item.ItemStack;
import studio.draco.utility.hud.HudContext;
import studio.draco.utility.hud.HudElement;

/** Your four armor pieces with durability bars. */
public final class ArmorElement extends HudElement {
    public ArmorElement() {
        super("armor", "Armor", Anchor.BOTTOM_RIGHT, 4, 30, false);
    }

    @Override
    public void render(HudContext c) {
        width = 4 * 18;
        height = 18;
        if (c.mc.player == null) return;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = c.mc.player.getInventory().getArmorStack(3 - i); // helmet first
            int x = i * 18;
            if (stack.isEmpty()) {
                if (c.editor) c.fill(x, 0, x + 16, 16, 0x40FFFFFF);
                continue;
            }
            c.draw.drawItem(stack, x, 0);
            c.draw.drawItemInSlot(c.font, stack, x, 0);
        }
    }
}
