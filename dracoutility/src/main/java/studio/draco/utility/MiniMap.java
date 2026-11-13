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

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import studio.draco.utility.Targets.Kind;
import studio.draco.utility.Waypoints.Waypoint;

/** Samples the terrain around the player into a color grid and draws it (HUD minimap and full map). */
public final class MiniMap {
    /** A square of map cells; cell (i, j) covers blocks [minX + i*bpc, +bpc) x [minZ + j*bpc, +bpc). */
    public static final class Grid {
        public int cells;
        public int bpc = 1;      // blocks per cell
        public int minX, minZ;   // world coordinates of the grid's north-west corner
        public int[] colors;     // ARGB, row-major (j * cells + i)
        public int[] ys;         // surface height of every cell
    }

    public static final int CELL_PX = 2;
    private static final int UNKNOWN = 0xFF1B1B1B;

    public static final Grid MINI = new Grid();
    private static int timer;

    private MiniMap() {}

    public static int miniPixelSize() {
        return MINI.cells * CELL_PX;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || !Features.allowedNow || !Features.minimap) return;
        int cells = Math.max(16, Features.minimapSize / CELL_PX);
        if (--timer > 0 && MINI.cells == cells && MINI.bpc == Features.minimapZoom && MINI.colors != null) return;
        timer = 10;
        sample(mc.world, mc.player, MINI, cells, Features.minimapZoom);
    }

    /** Fills the grid around the player: surface when the sky is visible, otherwise the floor near the player. */
    public static void sample(ClientWorld w, ClientPlayerEntity p, Grid g, int cells, int bpc) {
        if (g.colors == null || g.colors.length != cells * cells) {
            g.colors = new int[cells * cells];
            g.ys = new int[cells * cells];
        }
        g.cells = cells;
        g.bpc = bpc;
        int half = cells / 2;
        g.minX = p.getBlockX() - half * bpc;
        g.minZ = p.getBlockZ() - half * bpc;

        boolean sky = w.getDimension().hasSkyLight() && w.isSkyVisible(p.getBlockPos());
        int py = p.getBlockY();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int wx = g.minX + i * bpc + bpc / 2;
                int wz = g.minZ + j * bpc + bpc / 2;
                int startY = sky ? w.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1 : py + 3;
                startY = Math.min(startY, w.getTopY() - 1);
                int minY = Math.max(w.getBottomY(), startY - 48);

                int color = UNKNOWN;
                int height = w.getBottomY();
                for (int y = startY; y >= minY; y--) {
                    pos.set(wx, y, wz);
                    BlockState s = w.getBlockState(pos);
                    if (s.isAir()) continue;
                    MapColor mapColor = s.getMapColor(w, pos);
                    if (mapColor == MapColor.CLEAR) continue;
                    color = 0xFF000000 | mapColor.color;
                    height = y;
                    break;
                }
                g.colors[j * cells + i] = color;
                g.ys[j * cells + i] = height;
            }
        }

        // Relief shading: compare each cell with the one to its north.
        for (int j = 1; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int idx = j * cells + i;
                int prev = idx - cells;
                if (g.colors[idx] == UNKNOWN || g.colors[prev] == UNKNOWN) continue;
                if (g.ys[idx] > g.ys[prev]) g.colors[idx] = shade(g.colors[idx], 1.15f);
                else if (g.ys[idx] < g.ys[prev]) g.colors[idx] = shade(g.colors[idx], 0.82f);
            }
        }
    }

    private static int shade(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int gr = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // -------------------------------------------------------------- drawing

    public static void drawMini(DrawContext ctx, int x, int y) {
        Grid g = MINI;
        if (g.colors == null) return;
        int size = g.cells * CELL_PX;
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        drawGrid(ctx, g, x, y, CELL_PX);
        drawMarkers(ctx, g, x, y, CELL_PX, false);
    }

    /** Draws the terrain; consecutive cells of the same color in a row are merged into one rectangle. */
    public static void drawGrid(DrawContext ctx, Grid g, int x, int y, int cellPx) {
        for (int j = 0; j < g.cells; j++) {
            int i = 0;
            while (i < g.cells) {
                int color = g.colors[j * g.cells + i];
                int end = i + 1;
                while (end < g.cells && g.colors[j * g.cells + end] == color) end++;
                ctx.fill(x + i * cellPx, y + j * cellPx, x + end * cellPx, y + (j + 1) * cellPx, color);
                i = end;
            }
        }
    }

    public static void drawMarkers(DrawContext ctx, Grid g, int x, int y, int cellPx, boolean labels) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null || g.colors == null) return;

        if (Features.espOres) {
            for (BlockPos op : OreScanner.results()) {
                dot(ctx, g, x, y, cellPx, op.getX() + 0.5, op.getZ() + 0.5, 0xFFFFFFFF, 2);
            }
        }

        if (Features.espEntities || Features.espItems) {
            int span = g.cells * g.bpc;
            Box area = new Box(g.minX, w.getBottomY(), g.minZ, g.minX + span, w.getTopY(), g.minZ + span);
            for (Entity e : w.getEntitiesByClass(Entity.class, area, ent -> true)) {
                Kind k = Targets.classify(e, p);
                if (k == null) continue;
                if (k == Kind.ITEM ? !Features.espItems : !Features.espEntities) continue;
                dot(ctx, g, x, y, cellPx, e.getX(), e.getZ(), k.argb(), 3);
            }
        }

        String dim = Waypoints.dim(w);
        for (Waypoint wp : Waypoints.all()) {
            if (!wp.dim().equals(dim)) continue;
            int[] s = toScreen(g, x, y, cellPx, wp.x() + 0.5, wp.z() + 0.5);
            if (s == null) continue;
            ctx.fill(s[0] - 2, s[1] - 2, s[0] + 3, s[1] + 3, Kind.WAYPOINT.argb());
            if (labels) {
                ctx.drawTextWithShadow(mc.textRenderer, wp.name(), s[0] + 5, s[1] - 4, Kind.WAYPOINT.argb());
            }
        }

        // The player: white square with a red center.
        dot(ctx, g, x, y, cellPx, p.getX(), p.getZ(), 0xFFFFFFFF, 5);
        dot(ctx, g, x, y, cellPx, p.getX(), p.getZ(), 0xFFFF2020, 3);
    }

    /** Converts world coordinates to screen coordinates inside the map, or null when outside it. */
    public static int[] toScreen(Grid g, int x, int y, int cellPx, double wx, double wz) {
        double fx = (wx - g.minX) / g.bpc * cellPx;
        double fz = (wz - g.minZ) / g.bpc * cellPx;
        int max = g.cells * cellPx;
        if (fx < 0 || fz < 0 || fx >= max || fz >= max) return null;
        return new int[]{x + (int) fx, y + (int) fz};
    }

    private static void dot(DrawContext ctx, Grid g, int x, int y, int cellPx, double wx, double wz,
                            int color, int size) {
        int[] s = toScreen(g, x, y, cellPx, wx, wz);
        if (s == null) return;
        int h = size / 2;
        ctx.fill(s[0] - h, s[1] - h, s[0] - h + size, s[1] - h + size, color);
    }
}
