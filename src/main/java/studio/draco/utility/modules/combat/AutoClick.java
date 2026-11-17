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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.TickEvent;

public final class AutoClick extends Module {
    public enum Mode { ATTACK_BREAK, PLACE }

    private final Setting.Choice<Mode> mode = general.choice("mode", "Mode", "Attack / break, or place blocks.", Mode.ATTACK_BREAK);
    private final Setting.Dbl cps = general.dbl("cps", "Clicks per second",
            "Applies to attacking and placing. Mining speed is decided by the game.", 4.0, 0.5, 20.0);

    private int cooldown;
    private boolean held;

    public AutoClick() {
        super("autoclick", "Auto Click", "Clicks for you: attacks what you look at, mines, or places blocks.", ModuleCategory.COMBAT);
        capabilities(Capability.AUTOMATION);
        aliases("clicker");
    }

    @Override
    protected void onActivate() {
        cooldown = 0;
        on(TickEvent.Post.class, e -> tick());
    }

    @Override
    protected void onDeactivate() {
        release(MinecraftClient.getInstance());
    }

    private void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (cooldown > 0) cooldown--;
        if (mc.currentScreen != null) {
            release(mc);
            return;
        }
        int delay = Math.max(1, (int) Math.round(20.0 / cps.get()));

        if (mode.get() == Mode.PLACE) {
            release(mc);
            if (cooldown == 0 && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                ActionResult result = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                if (result.shouldSwingHand()) p.swingHand(Hand.MAIN_HAND);
                cooldown = delay;
            }
        } else if (mc.crosshairTarget instanceof EntityHitResult hit) {
            release(mc);
            if (cooldown == 0 && p.getAttackCooldownProgress(0.5f) >= 1.0f) {
                mc.interactionManager.attackEntity(p, hit.getEntity());
                p.swingHand(Hand.MAIN_HAND);
                cooldown = delay;
            }
        } else {
            mc.options.attackKey.setPressed(true); // hold the key so vanilla keeps mining
            held = true;
        }
    }

    private void release(MinecraftClient mc) {
        if (held) {
            mc.options.attackKey.setPressed(false);
            held = false;
        }
    }
}
