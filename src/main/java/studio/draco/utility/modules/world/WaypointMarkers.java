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

package studio.draco.utility.modules.world;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.platform.Dim;
import studio.draco.utility.render.RenderContext;
import studio.draco.utility.world.waypoint.WaypointManager.Waypoint;

public final class WaypointMarkers extends Module {
    private final Setting.Int maxDistance = general.integer("maxDistance", "Max distance", "Hide markers farther than this.", 2000, 50, 20000);
    private final Setting.Color color = general.color("color", "Color", "", 0x33CCFF);
    private final Setting.Bool labels = general.bool("labels", "Labels", "Show names and distance.", true);
    private final Setting.Bool beam = general.bool("beam", "Beam", "Draw a vertical line above each waypoint.", true);

    public WaypointMarkers() {
        super("waypoints", "Waypoints", "Shows your saved waypoints in the world. Manage them in the map or with .waypoint.",
                ModuleCategory.WORLD);
        capabilities(Capability.NAVIGATION);
    }

    @Override
    protected void onActivate() {
        on(Render3DEvent.class, e -> render(e.ctx));
    }

    private void render(RenderContext ctx) {
        Vec3d eye = ctx.player.getEyePos();
        for (Waypoint wp : Draco.get().waypoints.forDimension(Dim.of(ctx.world))) {
            Vec3d center = new Vec3d(wp.x() + 0.5, wp.y() + 0.5, wp.z() + 0.5);
            double dist = center.distanceTo(eye);
            if (dist > maxDistance.get()) continue;
            ctx.box(new Box(wp.x() + 0.25, wp.y(), wp.z() + 0.25, wp.x() + 0.75, wp.y() + 1.0, wp.z() + 0.75), color.get(), true);
            if (beam.get()) ctx.line(new Vec3d(wp.x() + 0.5, wp.y() + 1, wp.z() + 0.5), new Vec3d(wp.x() + 0.5, wp.y() + 40, wp.z() + 0.5), color.get(), true);
            if (labels.get()) ctx.label(center.add(0, 1.0, 0), wp.name() + " [" + (int) dist + "m]", color.get());
        }
    }
}
