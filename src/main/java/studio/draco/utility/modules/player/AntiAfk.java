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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.TickEvent;

public final class AntiAfk extends Module {
    private final Setting.Int interval = general.integer("interval", "Interval (seconds)", "Time between actions.", 20, 5, 120);
    private final Setting.Bool swing = general.bool("swing", "Swing arm", "Swing your hand.", true);
    private final Setting.Bool rotate = general.bool("rotate", "Rotate", "Turn slightly.", true);
    private int ticks;
    private boolean flip;

    public AntiAfk() {
        super("antiafk", "Anti AFK", "Does a small action now and then so you are not marked as away.", ModuleCategory.PLAYER);
        capabilities(Capability.AUTOMATION);
    }

    @Override
    protected void onActivate() {
        ticks = 0;
        on(TickEvent.Post.class, e -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.currentScreen != null) return;
            if (++ticks < interval.get() * 20) return;
            ticks = 0;
            if (swing.get()) p.swingHand(Hand.MAIN_HAND);
            if (rotate.get()) {
                p.setYaw(p.getYaw() + (flip ? -3f : 3f));
                flip = !flip;
            }
        });
    }
}
