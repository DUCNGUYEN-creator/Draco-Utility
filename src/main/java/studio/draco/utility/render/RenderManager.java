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

package studio.draco.utility.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import studio.draco.utility.Draco;
import studio.draco.utility.event.Render3DEvent;
import studio.draco.utility.render.RenderContext.BoxShape;
import studio.draco.utility.render.RenderContext.LabelShape;
import studio.draco.utility.render.RenderContext.LineShape;

import java.util.List;

/**
 * The one place that draws world-space shapes (ESP boxes, ore boxes, X-ray borders, tracers, guide lines).
 *
 * <p>Outlines are drawn as flat ribbons (quads that always face the camera), not with GL lines. That
 * makes every one of the 12 edges of a box come out with the same thickness on every GPU/driver,
 * instead of depending on line-width support, the line shader or leftover graphics state.
 *
 * <p>Every pass sets the state it needs (blend, cull, depth test, depth function, depth mask) and puts
 * the defaults back afterwards, so neither the result nor other parts of Minecraft depend on what was
 * set before.
 */
public final class RenderManager {
    /** GL_LEQUAL: what vanilla uses, and what lets outlines sit exactly on a block surface. */
    private static final int DEPTH_LEQUAL = 515;
    /** Boxes in the depth-tested pass grow by this much so their edges do not z-fight with the faces they touch. */
    private static final double DEPTH_BIAS = 0.003;
    /** Approximate size of one screen pixel at 1 block distance (about 70 degree FOV, 1080p). */
    private static final double PIXEL_AT_ONE_BLOCK = 0.0013;
    private static final double MIN_HALF_WIDTH = 0.0015;
    private static final double OUTLINE_PX = 2.0;
    private static final double OUTLINE_PX_THROUGH = 2.5;

    private RenderManager() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(RenderManager::render);
    }

    private static void render(WorldRenderContext wrc) {
        Draco d = Draco.maybe();
        if (d == null || !d.bus.hasListeners(Render3DEvent.class)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrices = wrc.matrixStack();
        if (mc.world == null || mc.player == null || matrices == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);
        RenderContext ctx = new RenderContext(matrices, wrc.camera(), tickDelta, wrc.frustum(), mc.world, mc.player);

        d.profiler.start("render3d");
        d.bus.post(new Render3DEvent(ctx));
        flush(mc, ctx);
        d.profiler.end("render3d");
        d.profiler.count("render.shapes", ctx.shapeCount());
    }

    // ------------------------------------------------------------------ passes

    private static void flush(MinecraftClient mc, RenderContext ctx) {
        if (ctx.shapeCount() == 0) return;
        MatrixStack matrices = ctx.matrices;
        Vec3d cam = ctx.cameraPos;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = matrices.peek().getPositionMatrix();
        try {
            // First what is hidden by blocks as it should be, then what shows through walls.
            for (int pass = 0; pass < 2; pass++) {
                boolean through = pass == 1;
                if (!hasShapes(ctx, through)) continue;
                beginState(!through);
                drawFills(m, ctx.fills, through);
                drawOutlines(m, cam, ctx.outlines, ctx.lines, through);
            }
        } finally {
            endState();
            matrices.pop();
        }
        drawLabels(mc, ctx);
    }

    private static boolean hasShapes(RenderContext ctx, boolean through) {
        for (BoxShape s : ctx.fills) if (s.throughWalls() == through) return true;
        for (BoxShape s : ctx.outlines) if (s.throughWalls() == through) return true;
        for (LineShape s : ctx.lines) if (s.throughWalls() == through) return true;
        return false;
    }

    /** Sets every piece of state the shapes depend on. */
    private static void beginState(boolean depthTest) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(DEPTH_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false); // translucent overlays must not hide what is drawn after them
    }

    /** Puts the state back to what the rest of the game expects. */
    private static void endState() {
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(DEPTH_LEQUAL);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    // ------------------------------------------------------------------- fills

    private static void drawFills(Matrix4f m, List<BoxShape> boxes, boolean through) {
        BufferBuilder buffer = null;
        for (BoxShape s : boxes) {
            if (s.throughWalls() != through) continue;
            if (buffer == null) {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            }
            Box b = s.box();
            float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
            float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
            quad(buffer, m, s, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2); // bottom
            quad(buffer, m, s, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1); // top
            quad(buffer, m, s, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1); // north
            quad(buffer, m, s, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2); // south
            quad(buffer, m, s, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1); // west
            quad(buffer, m, s, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2); // east
        }
        if (buffer != null) BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void quad(BufferBuilder buf, Matrix4f m, BoxShape s,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        buf.vertex(m, ax, ay, az).color(s.r(), s.g(), s.b(), s.a());
        buf.vertex(m, bx, by, bz).color(s.r(), s.g(), s.b(), s.a());
        buf.vertex(m, cx, cy, cz).color(s.r(), s.g(), s.b(), s.a());
        buf.vertex(m, dx, dy, dz).color(s.r(), s.g(), s.b(), s.a());
    }

    // ---------------------------------------------------------------- outlines

    private static void drawOutlines(Matrix4f m, Vec3d cam, List<BoxShape> boxes, List<LineShape> lines, boolean through) {
        BufferBuilder buffer = null;
        double px = through ? OUTLINE_PX_THROUGH : OUTLINE_PX;
        double bias = through ? 0.0 : DEPTH_BIAS;

        for (BoxShape s : boxes) {
            if (s.throughWalls() != through) continue;
            if (buffer == null) buffer = beginRibbons();
            boxEdges(buffer, m, cam, s, bias, px);
        }
        for (LineShape l : lines) {
            if (l.throughWalls() != through) continue;
            if (buffer == null) buffer = beginRibbons();
            Vec3d a = l.from(), b = l.to();
            ribbon(buffer, m, cam, a.x, a.y, a.z, b.x, b.y, b.z, l.r(), l.g(), l.b(), 1.0f, px);
        }
        if (buffer != null) BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static BufferBuilder beginRibbons() {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
    }

    /** All 12 edges of the box: 4 on the bottom, 4 on the top, 4 vertical. */
    private static void boxEdges(BufferBuilder buf, Matrix4f m, Vec3d cam, BoxShape s, double bias, double px) {
        Box b = s.box();
        double x1 = b.minX - bias, y1 = b.minY - bias, z1 = b.minZ - bias;
        double x2 = b.maxX + bias, y2 = b.maxY + bias, z2 = b.maxZ + bias;
        float r = s.r(), g = s.g(), bl = s.b(), a = s.a();

        // bottom ring
        ribbon(buf, m, cam, x1, y1, z1, x2, y1, z1, r, g, bl, a, px);
        ribbon(buf, m, cam, x2, y1, z1, x2, y1, z2, r, g, bl, a, px);
        ribbon(buf, m, cam, x2, y1, z2, x1, y1, z2, r, g, bl, a, px);
        ribbon(buf, m, cam, x1, y1, z2, x1, y1, z1, r, g, bl, a, px);
        // top ring
        ribbon(buf, m, cam, x1, y2, z1, x2, y2, z1, r, g, bl, a, px);
        ribbon(buf, m, cam, x2, y2, z1, x2, y2, z2, r, g, bl, a, px);
        ribbon(buf, m, cam, x2, y2, z2, x1, y2, z2, r, g, bl, a, px);
        ribbon(buf, m, cam, x1, y2, z2, x1, y2, z1, r, g, bl, a, px);
        // verticals
        ribbon(buf, m, cam, x1, y1, z1, x1, y2, z1, r, g, bl, a, px);
        ribbon(buf, m, cam, x2, y1, z1, x2, y2, z1, r, g, bl, a, px);
        ribbon(buf, m, cam, x2, y1, z2, x2, y2, z2, r, g, bl, a, px);
        ribbon(buf, m, cam, x1, y1, z2, x1, y2, z2, r, g, bl, a, px);
    }

    /**
     * One line segment as a quad facing the camera, about {@code widthPx} screen pixels wide at any
     * distance. The ends are extended by half the width so that edges meeting in a corner leave no gap.
     */
    private static void ribbon(BufferBuilder buf, Matrix4f m, Vec3d cam,
                               double x0, double y0, double z0, double x1, double y1, double z1,
                               float r, float g, float b, float a, double widthPx) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double dLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dLen < 1.0E-6) return;
        double mx = (x0 + x1) * 0.5 - cam.x, my = (y0 + y1) * 0.5 - cam.y, mz = (z0 + z1) * 0.5 - cam.z;

        // Width is computed per end: a long line that starts next to the camera must be thin there
        // and wider far away, so it keeps a constant width ON SCREEN (a single world width made a
        // tracer from the crosshair fan out into a huge wedge).
        double distA = Math.sqrt(sq(x0 - cam.x) + sq(y0 - cam.y) + sq(z0 - cam.z));
        double distB = Math.sqrt(sq(x1 - cam.x) + sq(y1 - cam.y) + sq(z1 - cam.z));
        double halfA = Math.max(0.5 * widthPx * PIXEL_AT_ONE_BLOCK * distA, MIN_HALF_WIDTH);
        double halfB = Math.max(0.5 * widthPx * PIXEL_AT_ONE_BLOCK * distB, MIN_HALF_WIDTH);

        // Sideways direction: perpendicular to both the edge and the view ray to its middle.
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double sLen = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen < 1.0E-9) return; // the edge points exactly at the camera: it has no visible width
        sx /= sLen; sy /= sLen; sz /= sLen;

        // Extend the ends slightly so edges meeting in a corner leave no gap.
        double ax = x0 - dx / dLen * halfA, ay = y0 - dy / dLen * halfA, az = z0 - dz / dLen * halfA;
        double bx = x1 + dx / dLen * halfB, by = y1 + dy / dLen * halfB, bz = z1 + dz / dLen * halfB;

        buf.vertex(m, (float) (ax - sx * halfA), (float) (ay - sy * halfA), (float) (az - sz * halfA)).color(r, g, b, a);
        buf.vertex(m, (float) (ax + sx * halfA), (float) (ay + sy * halfA), (float) (az + sz * halfA)).color(r, g, b, a);
        buf.vertex(m, (float) (bx + sx * halfB), (float) (by + sy * halfB), (float) (bz + sz * halfB)).color(r, g, b, a);
        buf.vertex(m, (float) (bx - sx * halfB), (float) (by - sy * halfB), (float) (bz - sz * halfB)).color(r, g, b, a);
    }

    private static double sq(double v) { return v * v; }

    // ------------------------------------------------------------------ labels

    private static void drawLabels(MinecraftClient mc, RenderContext ctx) {
        if (ctx.labels.isEmpty()) return;
        TextRenderer tr = mc.textRenderer;
        MatrixStack matrices = ctx.matrices;
        Vec3d cam = ctx.cameraPos;
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        for (LabelShape l : ctx.labels) {
            double dist = l.pos().distanceTo(cam);
            float scale = 0.025f * (float) Math.max(1.0, dist / 10.0); // constant on-screen size at range

            matrices.push();
            matrices.translate(l.pos().x - cam.x, l.pos().y - cam.y, l.pos().z - cam.z);
            matrices.multiply(ctx.camera.getRotation());
            matrices.scale(-scale, -scale, scale);
            Matrix4f m = matrices.peek().getPositionMatrix();
            float x = -tr.getWidth(l.text()) / 2.0f;
            tr.draw(l.text(), x, 0, 0xFF000000 | l.rgb(), false, m, consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, 0xF000F0);
            matrices.pop();
        }
        consumers.draw();
    }
}
