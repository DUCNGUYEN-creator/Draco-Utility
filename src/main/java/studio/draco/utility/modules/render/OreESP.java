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

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.render.BlockCache;
import studio.draco.utility.render.RenderContext;
import studio.draco.utility.services.BlockScanner;
import studio.draco.utility.services.Targets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OreESP extends Module {
    private final Setting.Choice<Targets.OreFilter> filter = general.choice("filter", "Ore type", "Which ore to show.", Targets.OreFilter.ALL);
    private final Setting.Int radius = general.integer("radius", "Scan radius", "Blocks around you that are searched.", 24, 8, 64);
    private final Setting.Color color = general.color("color", "Color", "", Targets.Kind.ORE.rgb);
    private final Setting.Bool labels = general.bool("labels", "Labels", "Name and distance for the nearest ores.", true);
    private final Setting.Int maxLabels = general.integer("maxLabels", "Max labels", "How many nearest ores get a label.", 5, 1, 20);
    private final Setting.Bool fill = general.bool("fill", "Fill", "Fill the boxes with a translucent color.", false);
    private final Setting.Bool throughWalls = general.bool("throughWalls", "Through walls", "Show ores behind blocks.", true);

    private BlockScanner.Query query;
    private final BlockCache cache = new BlockCache();

    public OreESP() {
        super("oreesp", "Ore ESP", "Highlights ores around you. Scanning is spread over many ticks, so it never lags.", ModuleCategory.RENDER);
        capabilities(Capability.WORLD_INFORMATION);
        requires(BlockScanner.class);
    }

    @Override
    protected void onActivate() {
        BlockScanner scanner = Draco.get().services.get(BlockScanner.class);
        query = scanner.create("ores", radius::get, s -> Targets.isOre(s, filter.get()));
        scanner.acquire(query, this);
        cache.clear();
        on(Render3DEvent.class, e -> render(e.ctx));
    }

    @Override
    protected void onDeactivate() {
        if (query != null) Draco.get().services.get(BlockScanner.class).release(query, this);
    }

    private void render(RenderContext ctx) {
        BlockCache.Entry[] hits = cache.get(query, ctx.world, ctx.player.getEyePos(), st -> Targets.isOre(st, filter.get()));
        int shown = 0;
        for (BlockCache.Entry h : hits) {
            ctx.box(h.box(), color.get(), throughWalls.get());
            if (fill.get()) ctx.fill(h.box(), color.get(), 0.2f, throughWalls.get());
            if (labels.get() && shown < maxLabels.get()) {
                BlockState state = ctx.world.getBlockState(h.pos());
                ctx.label(Vec3d.ofCenter(h.pos()).add(0, 0.9, 0),
                        state.getBlock().getName().getString() + " [" + (int) Math.sqrt(h.distSq()) + "m]", color.get());
                shown++;
            }
        }
    }
}
