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

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.event.TickEvent;
import studio.draco.utility.platform.Dim;
import studio.draco.utility.render.RenderContext;
import studio.draco.utility.services.BlockScanner;
import studio.draco.utility.services.EntityService;
import studio.draco.utility.services.InfoService;
import studio.draco.utility.services.Targets;
import studio.draco.utility.world.waypoint.WaypointManager.Waypoint;

public final class Guide extends Module {
    public final Setting.Choice<Targets.Kind> kind = general.choice("kind", "Target", "What the line leads to.", Targets.Kind.ORE);
    public final Setting.Choice<Targets.OreFilter> oreFilter = general.choice("oreFilter", "Ore type", "Which ore to look for.", Targets.OreFilter.ALL);
    public final Setting.Int radius = general.integer("radius", "Ore scan radius", "Blocks around you that are searched for ore.", 24, 8, 64);
    public final Setting.Text waypoint = general.text("waypoint", "Waypoint", "Name of the waypoint to lead to.", "", 32);
    public final Setting.Bool kindColor = general.bool("kindColor", "Use target color", "Color the line like the target's ESP box.", true);
    public final Setting.Color color = general.color("color", "Line color", "", 0xFFFFFF);

    private BlockScanner.Query query;

    public Guide() {
        super("guide", "Guide", "Draws a line to the nearest ore, player, mob, item or to a waypoint.", ModuleCategory.WORLD);
        capabilities(Capability.NAVIGATION, Capability.WORLD_INFORMATION);
        requires(EntityService.class, BlockScanner.class, InfoService.class);
        aliases("tracers", "path");
        oreFilter.visibleWhen(() -> kind.get() == Targets.Kind.ORE);
        radius.visibleWhen(() -> kind.get() == Targets.Kind.ORE);
        waypoint.visibleWhen(() -> kind.get() == Targets.Kind.WAYPOINT);
        color.visibleWhen(() -> !kindColor.get());
    }

    @Override
    protected void onActivate() {
        BlockScanner scanner = Draco.get().services.get(BlockScanner.class);
        query = scanner.create("guide-ores", radius::get, s -> Targets.isOre(s, oreFilter.get()));
        on(TickEvent.Pre.class, e -> updateServices());
        on(Render3DEvent.class, e -> render(e.ctx));
    }

    @Override
    protected void onDeactivate() {
        Draco d = Draco.get();
        d.services.get(EntityService.class).release(this);
        if (query != null) d.services.get(BlockScanner.class).release(query, this);
        d.services.get(InfoService.class).clear("guide");
    }

    /** Only scan what the current target kind needs. */
    private void updateServices() {
        Draco d = Draco.get();
        Targets.Kind k = kind.get();
        boolean entityKind = k == Targets.Kind.PLAYER || k == Targets.Kind.HOSTILE
                || k == Targets.Kind.PASSIVE || k == Targets.Kind.ITEM;
        if (entityKind) d.services.get(EntityService.class).acquire(this);
        else d.services.get(EntityService.class).release(this);
        if (k == Targets.Kind.ORE) d.services.get(BlockScanner.class).acquire(query, this);
        else d.services.get(BlockScanner.class).release(query, this);
    }

    private void render(RenderContext ctx) {
        Draco d = Draco.get();
        InfoService info = d.services.get(InfoService.class);
        Targets.Kind k = kind.get();
        Vec3d eye = ctx.player.getCameraPosVec(ctx.tickDelta);
        Vec3d target = null;
        String name = k.label;

        switch (k) {
            case ORE -> {
                double best = Double.MAX_VALUE;
                BlockPos.Mutable pos = new BlockPos.Mutable();
                for (long packed : query.packed()) {
                    pos.set(packed);
                    double dist = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
                    if (dist >= best || !Targets.isOre(ctx.world.getBlockState(pos), oreFilter.get())) continue;
                    best = dist;
                    target = Vec3d.ofCenter(pos);
                    name = ctx.world.getBlockState(pos).getBlock().getName().getString();
                }
            }
            case WAYPOINT -> {
                Waypoint wp = d.waypoints.find(waypoint.get(), Dim.of(ctx.world));
                if (wp != null) {
                    target = new Vec3d(wp.x() + 0.5, wp.y() + 0.5, wp.z() + 0.5);
                    name = wp.name();
                }
            }
            default -> {
                EntityService.Tracked t = d.services.get(EntityService.class).nearest(k);
                if (t != null) {
                    Entity e = t.entity();
                    target = e.getLerpedPos(ctx.tickDelta).add(0, e.getHeight() / 2.0, 0);
                    name = e.getName().getString();
                }
            }
        }

        if (target == null) {
            info.set("guide", "Guide: " + k.label + " - none found");
            return;
        }
        int rgb = kindColor.get() ? k.rgb : color.get();
        Vec3d look = Vec3d.fromPolar(ctx.camera.getPitch(), ctx.camera.getYaw());
        Vec3d start = ctx.cameraPos.add(look.multiply(1.2)).add(0, -0.25, 0);
        ctx.line(start, target, rgb, true);
        ctx.box(new Box(target.x - 0.35, target.y - 0.35, target.z - 0.35, target.x + 0.35, target.y + 0.35, target.z + 0.35), rgb, true);
        int meters = (int) eye.distanceTo(target);
        ctx.label(target.add(0, 0.8, 0), name + " [" + meters + "m]", rgb);
        info.set("guide", "Guide: " + name + " - " + meters + "m");
    }
}
