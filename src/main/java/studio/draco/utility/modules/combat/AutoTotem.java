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

package studio.draco.utility.modules.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.TickEvent;

public final class AutoTotem extends Module {
    private final Setting.Int delay = general.integer("delay", "Delay (ticks)", "Wait between swaps.", 5, 0, 40);
    private int cooldown;

    public AutoTotem() {
        super("autototem", "Auto Totem", "Keeps a Totem of Undying in your off hand.", ModuleCategory.COMBAT);
        capabilities(Capability.AUTOMATION);
    }

    @Override
    protected void onActivate() {
        cooldown = 0;
        on(TickEvent.Post.class, e -> tick());
    }

    private void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        // Slots 9-35 are the inventory, 36-44 the hotbar.
        for (int slot = 9; slot <= 44; slot++) {
            if (p.playerScreenHandler.getSlot(slot).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                cooldown = delay.get();
                return;
            }
        }
    }
}
