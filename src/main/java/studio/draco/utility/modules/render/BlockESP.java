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
import java.util.List;
import java.util.Locale;

public final class BlockESP extends Module {
    private final Setting.Text blocks = general.text("blocks", "Blocks",
            "Comma separated block names (part of the id is enough), for example: chest, barrel, spawner.",
            "chest,barrel,shulker_box", 200);
    private final Setting.Int radius = general.integer("radius", "Scan radius", "Blocks around you that are searched.", 24, 8, 64);
    private final Setting.Color color = general.color("color", "Color", "", 0xFFAA00);
    private final Setting.Bool labels = general.bool("labels", "Labels", "Name and distance.", true);
    private final Setting.Bool fill = general.bool("fill", "Fill", "Fill the boxes with a translucent color.", true);

    private volatile String[] tokens;
    private BlockScanner.Query query;
    private final BlockCache cache = new BlockCache();

    public BlockESP() {
        super("blockesp", "Block ESP", "Highlights blocks you choose by name: storage, spawners, anything.", ModuleCategory.RENDER);
        capabilities(Capability.WORLD_INFORMATION);
        requires(BlockScanner.class);
        aliases("storageesp", "search");
        tokens = parse(blocks.get());
        blocks.onChanged(v -> tokens = parse(v));
    }

    private static String[] parse(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.toLowerCase(Locale.ROOT).split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.toArray(new String[0]);
    }

    private boolean matches(BlockState state) {
        String path = Targets.pathOf(state.getBlock());
        for (String t : tokens) if (path.contains(t)) return true;
        return false;
    }

    @Override
    protected void onActivate() {
        BlockScanner scanner = Draco.get().services.get(BlockScanner.class);
        query = scanner.create("blocks", radius::get, this::matches);
        scanner.acquire(query, this);
        cache.clear();
        on(Render3DEvent.class, e -> render(e.ctx));
    }

    @Override
    protected void onDeactivate() {
        if (query != null) Draco.get().services.get(BlockScanner.class).release(query, this);
    }

    private void render(RenderContext ctx) {
        BlockCache.Entry[] hits = cache.get(query, ctx.world, ctx.player.getEyePos(), this::matches);
        int count = 0;
        for (BlockCache.Entry h : hits) {
            ctx.box(h.box(), color.get(), true);
            if (fill.get()) ctx.fill(h.box(), color.get(), 0.25f, true);
            if (labels.get() && count++ < 12) {
                ctx.label(Vec3d.ofCenter(h.pos()).add(0, 0.9, 0),
                        ctx.world.getBlockState(h.pos()).getBlock().getName().getString() + " [" + (int) Math.sqrt(h.distSq()) + "m]",
                        color.get());
            }
        }
    }
}
