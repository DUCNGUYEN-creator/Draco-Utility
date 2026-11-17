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
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.event.TickEvent;
import studio.draco.utility.render.RenderContext;
import studio.draco.utility.services.NightVision;
import studio.draco.utility.services.XRayFilter;
import studio.draco.utility.services.XRayState;

public final class XRay extends Module {
    private static final int MAX_BOXES = 2500;

    private final Setting.Bool outline = general.bool("outline", "Borders", "Outline hidden blocks that would block your movement.", true);
    private final Setting.Int outlineRadius = general.integer("outlineRadius", "Border radius", "Blocks around you that get borders.", 3, 1, 8);
    private final Setting.Color outlineColor = general.color("outlineColor", "Border color", "", 0xFF4040);
    private final Setting.Bool fullbright = general.bool("fullbright", "Fullbright", "Night vision so caves are not dark.", true);

    public XRay() {
        super("xray", "X-Ray", "Only ores, minerals, building blocks, wood, leaves and functional blocks stay visible.",
                ModuleCategory.RENDER);
        capabilities(Capability.WORLD_INFORMATION);
        aliases("xray");
    }

    @Override
    protected void onActivate() {
        XRayFilter.build();
        XRayState.active = true;
        reloadChunks();
        on(TickEvent.Post.class, e -> {
            if (fullbright.get()) NightVision.acquire(this);
            else NightVision.release(this);
        });
        on(Render3DEvent.class, e -> {
            if (outline.get()) renderBorders(e.ctx);
        });
    }

    @Override
    protected void onDeactivate() {
        XRayState.active = false;
        NightVision.release(this);
        reloadChunks();
    }

    private static void reloadChunks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer != null && mc.world != null) mc.worldRenderer.reload();
    }

    private void renderBorders(RenderContext ctx) {
        int r = outlineRadius.get();
        BlockPos center = ctx.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int boxes = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = ctx.world.getBlockState(pos);
                    if (state.isAir() || !XRayFilter.isHidden(state)) continue;
                    VoxelShape shape = state.getCollisionShape(ctx.world, pos);
                    if (shape.isEmpty()) continue; // grass, flowers... do not block movement
                    ctx.box(shape.getBoundingBox().offset(pos), outlineColor.get(), false);
                    if (++boxes >= MAX_BOXES) return;
                }
            }
        }
    }
}
