/*
 * Draco Utility - client-side utility mod for Minecraft (Fabric)
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

package studio.draco.utility;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/** Top-left HUD: minimap, coordinates, active features, nearest player and guide target. */
public final class Hud {
    private Hud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(Hud::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden || !Features.allowedNow) return;
        if (mc.getDebugHud().shouldShowDebugHud()) return; // do not overlap the F3 screen

        int y = 4;
        if (Features.minimap && MiniMap.MINI.colors != null) {
            MiniMap.drawMini(ctx, 4, 4);
            y = 4 + MiniMap.miniPixelSize() + 4;
        }

        List<String> lines = new ArrayList<>();
        lines.add("XYZ: " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());

        List<String> on = new ArrayList<>();
        if (Features.fly) on.add("Bay");
        if (Features.espActive) on.add("ESP");
        if (Features.xrayActive) on.add("X-ray");
        if (Features.autoClick) on.add(Features.clickPlace ? "Đặt khối" : "Click");
        if (Features.autoPvp) on.add("PvP");
        if (!on.isEmpty()) lines.add("Bật: " + String.join(", ", on));

        if (Features.espActive && Features.espEntities && Features.nearestPlayer != null) {
            lines.add("Người chơi gần nhất: " + Features.nearestPlayer.getName().getString()
                    + " (" + (int) Features.nearestPlayerDist + "m)");
        }
        if (Features.guide && Features.guideInfo != null) lines.add(Features.guideInfo);

        for (String line : lines) {
            ctx.drawTextWithShadow(mc.textRenderer, line, 4, y, 0xFFFFFF);
            y += 10;
        }
    }
}
