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

package studio.draco.utility.modules.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.event.TickEvent;
import studio.draco.utility.platform.Dim;
import studio.draco.utility.render.RenderContext;

import java.util.ArrayList;
import java.util.List;

public final class Breadcrumbs extends Module {
    private final Setting.Int maxPoints = general.integer("maxPoints", "Max points", "Length of the trail.", 300, 50, 2000);
    private final Setting.Int interval = general.integer("interval", "Interval (ticks)", "Ticks between trail points.", 2, 1, 20);
    private final Setting.Color color = general.color("color", "Color", "", 0x55AAFF);
    private final Setting.Bool throughWalls = general.bool("throughWalls", "Through walls", "Show the trail behind blocks.", false);

    private final List<Vec3d> points = new ArrayList<>();
    private String dimension = "";
    private int ticks;

    public Breadcrumbs() {
        super("breadcrumbs", "Breadcrumbs", "Draws a trail where you have walked.", ModuleCategory.RENDER);
        capabilities(Capability.LOCAL_VISUAL);
    }

    @Override
    protected void onActivate() {
        points.clear();
        on(TickEvent.Post.class, e -> tick());
        on(Render3DEvent.class, e -> render(e.ctx));
    }

    private void tick() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null || MinecraftClient.getInstance().world == null) return;
        String dim = Dim.of(MinecraftClient.getInstance().world);
        if (!dim.equals(dimension)) {
            points.clear();
            dimension = dim;
        }
        if (++ticks % interval.get() != 0) return;
        Vec3d pos = p.getPos();
        if (points.isEmpty() || points.get(points.size() - 1).squaredDistanceTo(pos) > 0.25) {
            points.add(pos);
            while (points.size() > maxPoints.get()) points.remove(0);
        }
    }

    private void render(RenderContext ctx) {
        for (int i = 1; i < points.size(); i++) {
            ctx.line(points.get(i - 1), points.get(i), color.get(), throughWalls.get());
        }
    }
}
