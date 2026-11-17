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
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.render.RenderContext;
import studio.draco.utility.services.EntityService;
import studio.draco.utility.services.PlayerService;
import studio.draco.utility.services.Targets;

public final class ESP extends Module {
    private final Setting.Bool players = general.bool("players", "Players", "Highlight other players.", true);
    private final Setting.Bool hostile = general.bool("hostile", "Hostile mobs", "Highlight hostile mobs.", true);
    private final Setting.Bool passive = general.bool("passive", "Passive mobs", "Highlight passive mobs and animals.", true);
    private final Setting.Bool items = general.bool("items", "Items", "Highlight dropped items.", true);
    private final Setting.Int range = general.integer("range", "Range", "Maximum distance in blocks.", 96, 16, 128);
    private final Setting.Bool labels = general.bool("labels", "Labels", "Show name and distance.", true);
    private final Setting.Bool fill = general.bool("fill", "Fill", "Fill the boxes with a translucent color.", false);
    private final Setting.Int fillAlpha = general.integer("fillAlpha", "Fill opacity (%)", "How strong the fill is.", 20, 5, 80);

    private final SettingGroup colors = group("Colors");
    private final Setting.Color playerColor = colors.color("playerColor", "Players", "", Targets.Kind.PLAYER.rgb);
    private final Setting.Color hostileColor = colors.color("hostileColor", "Hostile mobs", "", Targets.Kind.HOSTILE.rgb);
    private final Setting.Color passiveColor = colors.color("passiveColor", "Passive mobs", "", Targets.Kind.PASSIVE.rgb);
    private final Setting.Color itemColor = colors.color("itemColor", "Items", "", Targets.Kind.ITEM.rgb);

    public ESP() {
        super("esp", "ESP", "Colored boxes and name labels through walls for players, mobs and items.", ModuleCategory.RENDER);
        capabilities(Capability.WORLD_INFORMATION);
        requires(EntityService.class, PlayerService.class);
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

    private int colorOf(Targets.Kind k) {
        return switch (k) {
            case PLAYER -> playerColor.get();
            case HOSTILE -> hostileColor.get();
            case PASSIVE -> passiveColor.get();
            default -> itemColor.get();
        };
    }

    private void render(RenderContext ctx) {
        PlayerEntity nearestPlayer = Draco.get().services.get(PlayerService.class).nearestPlayer();
        double max = (double) range.get() * range.get();
        for (EntityService.Tracked t : Draco.get().services.get(EntityService.class).entities()) {
            if (t.distSq() > max || !wants(t.kind())) continue;
            Entity e = t.entity();
            Box box = e.getBoundingBox().offset(e.getLerpedPos(ctx.tickDelta).subtract(e.getPos()));
            int rgb = colorOf(t.kind());
            ctx.box(box, rgb, true);
            if (fill.get()) ctx.fill(box, rgb, fillAlpha.get() / 100f, true);
            if (labels.get()) {
                String name = e instanceof ItemEntity item
                        ? item.getStack().getName().getString() + " x" + item.getStack().getCount()
                        : e.getName().getString();
                String text = (e == nearestPlayer ? "* " : "") + name + " [" + (int) Math.sqrt(t.distSq()) + "m]";
                ctx.label(new Vec3d((box.minX + box.maxX) / 2, box.maxY + 0.45, (box.minZ + box.maxZ) / 2), text, rgb);
            }
        }
    }
}
