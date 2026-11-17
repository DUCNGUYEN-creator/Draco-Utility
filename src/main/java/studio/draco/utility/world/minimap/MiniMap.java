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

package studio.draco.utility.world.minimap;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import studio.draco.utility.Draco;
import studio.draco.utility.core.scheduler.Task;
import studio.draco.utility.platform.Dim;
import studio.draco.utility.services.BlockScanner;
import studio.draco.utility.services.EntityService;
import studio.draco.utility.services.Targets.Kind;
import studio.draco.utility.world.waypoint.WaypointManager.Waypoint;

/**
 * Terrain sampling and drawing shared by the HUD minimap and the large map. The minimap samples
 * a few rows per tick through the scheduler and only while something draws it.
 */
public final class MiniMap implements Task {
    /** A square of map cells; cell (i, j) covers blocks [minX + i*bpc, +bpc) x [minZ + j*bpc, +bpc). */
    public static final class Grid {
        public int cells;
        public int bpc = 1;
        public int minX, minZ;
        public int[] colors;
        public int[] ys;
        boolean sky;
        int py;
    }

    public static final MiniMap TASK = new MiniMap();
    public static final int CELL_PX = 2;
    private static final int UNKNOWN = 0xFF1B1B1B;

    private Grid display = new Grid();
    private Grid building = new Grid();
    private int row = -1;
    private long nextStartNs;
    private volatile long touchedMs;
    private volatile int wantCells = 40;
    private volatile int wantBpc = 1;

    private MiniMap() {}

    public static Grid grid() { return TASK.display; }

    /** The HUD element calls this every frame; sampling pauses when nobody asks for the map. */
    public static void touch(int cells, int bpc) {
        TASK.touchedMs = System.currentTimeMillis();
        TASK.wantCells = Math.max(16, cells);
        TASK.wantBpc = Math.max(1, bpc);
    }

    @Override public String name() { return "minimap"; }

    @Override
    public void step(long deadline) {
        if (System.currentTimeMillis() - touchedMs > 1000) {
            row = -1;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            row = -1;
            return;
        }
        if (row < 0) {
            if (System.nanoTime() < nextStartNs) return;
            begin(building, mc.world, mc.player, wantCells, wantBpc);
            row = 0;
        }
        while (row < building.cells) {
            sampleRow(building, mc.world, row++);
            if (System.nanoTime() >= deadline) break;
        }
        if (row >= building.cells) {
            shade(building);
            Grid done = building;
            building = display;
            display = done;
            row = -1;
            nextStartNs = System.nanoTime() + 150_000_000L;
        }
    }

    // ------------------------------------------------------------- sampling

    public static void begin(Grid g, ClientWorld w, ClientPlayerEntity p, int cells, int bpc) {
        if (g.colors == null || g.colors.length != cells * cells) {
            g.colors = new int[cells * cells];
            g.ys = new int[cells * cells];
        }
        g.cells = cells;
        g.bpc = bpc;
        g.minX = p.getBlockX() - (cells / 2) * bpc;
        g.minZ = p.getBlockZ() - (cells / 2) * bpc;
        g.sky = w.getDimension().hasSkyLight() && w.isSkyVisible(p.getBlockPos());
        g.py = p.getBlockY();
    }

    /** Samples one row (constant z). Uses the surface when the sky is visible, else the floor near the player. */
    public static void sampleRow(Grid g, ClientWorld w, int j) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int i = 0; i < g.cells; i++) {
            int wx = g.minX + i * g.bpc + g.bpc / 2;
            int wz = g.minZ + j * g.bpc + g.bpc / 2;
            int startY = g.sky ? w.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1 : g.py + 3;
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
            g.colors[j * g.cells + i] = color;
            g.ys[j * g.cells + i] = height;
        }
    }

    /** Samples the whole grid at once (used by the large map, which is small enough). */
    public static void sampleAll(Grid g, ClientWorld w, ClientPlayerEntity p, int cells, int bpc) {
        begin(g, w, p, cells, bpc);
        for (int j = 0; j < cells; j++) sampleRow(g, w, j);
        shade(g);
    }

    /** Relief shading: compare each cell with the one to its north. */
    public static void shade(Grid g) {
        for (int j = 1; j < g.cells; j++) {
            for (int i = 0; i < g.cells; i++) {
                int idx = j * g.cells + i;
                int prev = idx - g.cells;
                if (g.colors[idx] == UNKNOWN || g.colors[prev] == UNKNOWN) continue;
                if (g.ys[idx] > g.ys[prev]) g.colors[idx] = scale(g.colors[idx], 1.15f);
                else if (g.ys[idx] < g.ys[prev]) g.colors[idx] = scale(g.colors[idx], 0.82f);
            }
        }
    }

    private static int scale(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int gr = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // -------------------------------------------------------------- drawing

    /** Draws the terrain; consecutive cells of the same color in a row become one rectangle. */
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
        Draco d = Draco.maybe();
        if (w == null || p == null || d == null || g.colors == null) return;

        BlockScanner.Query ores = d.services.get(BlockScanner.class).find("ores");
        if (ores != null && ores.active()) {
            for (long op : ores.packed()) dot(ctx, g, x, y, cellPx, BlockPos.unpackLongX(op) + 0.5, BlockPos.unpackLongZ(op) + 0.5, 0xFFFFFFFF, 2);
        }

        EntityService es = d.services.get(EntityService.class);
        if (es.active()) {
            for (EntityService.Tracked t : es.entities()) {
                Entity e = t.entity();
                dot(ctx, g, x, y, cellPx, e.getX(), e.getZ(), t.kind().argbColor(), 3);
            }
        }

        for (Waypoint wp : d.waypoints.forDimension(Dim.of(w))) {
            int[] s = toScreen(g, x, y, cellPx, wp.x() + 0.5, wp.z() + 0.5);
            if (s == null) continue;
            ctx.fill(s[0] - 2, s[1] - 2, s[0] + 3, s[1] + 3, Kind.WAYPOINT.argbColor());
            if (labels) ctx.drawTextWithShadow(mc.textRenderer, wp.name(), s[0] + 5, s[1] - 4, Kind.WAYPOINT.argbColor());
        }

        dot(ctx, g, x, y, cellPx, p.getX(), p.getZ(), 0xFFFFFFFF, 5);
        dot(ctx, g, x, y, cellPx, p.getX(), p.getZ(), 0xFFFF2020, 3);
    }

    /** World coordinates to screen coordinates inside the map, or null when outside it. */
    public static int[] toScreen(Grid g, int x, int y, int cellPx, double wx, double wz) {
        double fx = (wx - g.minX) / g.bpc * cellPx;
        double fz = (wz - g.minZ) / g.bpc * cellPx;
        int max = g.cells * cellPx;
        if (fx < 0 || fz < 0 || fx >= max || fz >= max) return null;
        return new int[]{x + (int) fx, y + (int) fz};
    }

    private static void dot(DrawContext ctx, Grid g, int x, int y, int cellPx, double wx, double wz, int color, int size) {
        int[] s = toScreen(g, x, y, cellPx, wx, wz);
        if (s == null) return;
        int h = size / 2;
        ctx.fill(s[0] - h, s[1] - h, s[0] - h + size, s[1] - h + size, color);
    }
}
