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

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.render.RenderContext;
import studio.draco.utility.services.EntityService;
import studio.draco.utility.services.Targets;

/** Lines from your crosshair to nearby players, mobs and items, colored by kind. */
public final class Tracers extends Module {
    private final Setting.Bool players = general.bool("players", "Players", "Draw lines to other players.", true);
    private final Setting.Bool hostile = general.bool("hostile", "Hostile mobs", "Draw lines to hostile mobs.", true);
    private final Setting.Bool passive = general.bool("passive", "Passive mobs", "Draw lines to passive mobs and animals.", false);
    private final Setting.Bool items = general.bool("items", "Items", "Draw lines to dropped items.", false);
    private final Setting.Int range = general.integer("range", "Range", "Maximum distance in blocks.", 64, 8, 128);
    private final Setting.Bool throughWalls = general.bool("throughWalls", "Through walls", "Show the lines behind blocks.", true);

    public Tracers() {
        super("tracers", "Tracers", "Draws lines from your crosshair to players, mobs and items.", ModuleCategory.RENDER);
        capabilities(Capability.WORLD_INFORMATION);
        requires(EntityService.class);
    }

    @Override
    protected void onActivate() {
        Draco.get().services.get(EntityService.class).acquire(this);
        on(Render3DEvent.class, e -> render(e.ctx));
    }

    @Override
    protected void onDeactivate() {
        Draco.get().services.get(EntityService.class).release(this);
    }

    private boolean wants(Targets.Kind k) {
        return switch (k) {
            case PLAYER -> players.get();
            case HOSTILE -> hostile.get();
            case PASSIVE -> passive.get();
            case ITEM -> items.get();
            default -> false;
        };
    }

    private void render(RenderContext ctx) {
        // Start a little in front of the camera so the line is visible in first person.
        Vec3d start = ctx.cameraPos.add(ctx.player.getRotationVec(ctx.tickDelta).multiply(0.6));
        double max = (double) range.get() * range.get();
        for (EntityService.Tracked t : Draco.get().services.get(EntityService.class).entities()) {
            if (t.distSq() > max || !wants(t.kind())) continue;
            Entity e = t.entity();
            Box box = e.getBoundingBox().offset(e.getLerpedPos(ctx.tickDelta).subtract(e.getPos()));
            ctx.line(start, box.getCenter(), t.kind().rgb, throughWalls.get());
        }
    }
}
