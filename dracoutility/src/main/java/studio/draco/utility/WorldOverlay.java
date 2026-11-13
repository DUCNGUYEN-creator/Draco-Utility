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

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import studio.draco.utility.Targets.Kind;
import studio.draco.utility.Waypoints.Waypoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * All in-world drawing: X-ray borders, colored ESP boxes, ore boxes, waypoint markers,
 * the guide line and the floating name/distance labels.
 */
public final class WorldOverlay {
    private record ColoredBox(Box box, float r, float g, float b) {}
    private record Line(Vec3d from, Vec3d to, float r, float g, float b) {}
    private record Label(Vec3d pos, String text, int rgb) {}
    private record OreHit(BlockPos pos, double d) {}

    private static final int MAX_XRAY_BOXES = 2500;
    private static final double ESP_RANGE = 96;

    private WorldOverlay() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldOverlay::render);
    }

    private static void render(WorldRenderContext context) {
        if (!Features.allowedNow) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        ClientPlayerEntity player = mc.player;
        MatrixStack matrices = context.matrixStack();
        if (world == null || player == null || matrices == null) return;

        boolean xrayBoxes = Features.xrayActive && Features.xrayOutline;
        boolean espOn = Features.espActive;
        boolean guideOn = Features.guide;
        Kind gk = guideOn ? Features.guideKind() : null;
        boolean wpOn = Features.showWaypoints || gk == Kind.WAYPOINT;
        if (!xrayBoxes && !espOn && !guideOn && !wpOn) {
            Features.guideInfo = null;
            return;
        }

        float td = mc.getRenderTickCounter().getTickDelta(true);
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        Vec3d eye = player.getCameraPosVec(td);
        String dim = Waypoints.dim(world);

        List<ColoredBox> depthBoxes = new ArrayList<>(); // hidden by solid blocks (X-ray borders)
        List<ColoredBox> topBoxes = new ArrayList<>();   // drawn through walls (ESP)
        List<Line> lines = new ArrayList<>();
        List<Label> labels = new ArrayList<>();

        // ---------- X-ray borders around hidden solid blocks ----------
        if (xrayBoxes) {
            int r = Features.outlineRadius;
            BlockPos center = player.getBlockPos();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            scan:
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir() || !XRay.isHidden(state)) continue;
                        VoxelShape shape = state.getCollisionShape(world, pos);
                        if (shape.isEmpty()) continue; // grass, flowers... do not block movement
                        depthBoxes.add(new ColoredBox(shape.getBoundingBox().offset(pos), 1.0f, 0.25f, 0.25f));
                        if (depthBoxes.size() >= MAX_XRAY_BOXES) break scan;
                    }
                }
            }
        }

        // ---------- Entities: ESP boxes + nearest per kind (for the guide line) ----------
        Entity[] nearest = new Entity[Kind.values().length];
        double[] nearestD = new double[Kind.values().length];
        java.util.Arrays.fill(nearestD, Double.MAX_VALUE);

        boolean guideNeedsEntities = gk != null && gk != Kind.ORE && gk != Kind.WAYPOINT;
        boolean espNeedsEntities = espOn && (Features.espEntities || Features.espItems);
        if (guideNeedsEntities || espNeedsEntities) {
            Box area = player.getBoundingBox().expand(128);
            for (Entity e : world.getEntitiesByClass(Entity.class, area, ent -> true)) {
                Kind k = Targets.classify(e, player);
                if (k == null) continue;
                double d = player.squaredDistanceTo(e);
                if (d < nearestD[k.ordinal()]) {
                    nearestD[k.ordinal()] = d;
                    nearest[k.ordinal()] = e;
                }
                if (!espOn || d > ESP_RANGE * ESP_RANGE) continue;
                if (k == Kind.ITEM ? !Features.espItems : !Features.espEntities) continue;

                Box b = e.getBoundingBox().offset(e.getLerpedPos(td).subtract(e.getPos()));
                topBoxes.add(new ColoredBox(b, k.r(), k.g(), k.b()));
                if (Features.espLabels) {
                    String text = entityLabel(e, k, Math.sqrt(d));
                    if (e == Features.nearestPlayer) text = "★ " + text;
                    labels.add(new Label(new Vec3d((b.minX + b.maxX) / 2, b.maxY + 0.45, (b.minZ + b.maxZ) / 2),
                            text, k.rgb));
                }
            }
        }

        // ---------- Ores ----------
        BlockPos nearestOre = null;
        boolean oresWanted = (espOn && Features.espOres) || gk == Kind.ORE;
        if (oresWanted) {
            List<OreHit> hits = new ArrayList<>();
            for (BlockPos op : OreScanner.results()) {
                if (!Targets.isOre(world.getBlockState(op), Features.oreFilter)) continue; // already mined
                hits.add(new OreHit(op, Vec3d.ofCenter(op).squaredDistanceTo(eye)));
            }
            hits.sort(Comparator.comparingDouble(OreHit::d));
            if (!hits.isEmpty()) nearestOre = hits.get(0).pos();

            if (espOn && Features.espOres) {
                int shown = 0;
                for (OreHit h : hits) {
                    topBoxes.add(new ColoredBox(new Box(h.pos()).expand(0.003), 1f, 1f, 1f));
                    if (Features.espLabels && shown < 5) {
                        String name = world.getBlockState(h.pos()).getBlock().getName().getString();
                        labels.add(new Label(Vec3d.ofCenter(h.pos()).add(0, 0.9, 0),
                                name + " [" + (int) Math.sqrt(h.d()) + "m]", Kind.ORE.rgb));
                        shown++;
                    }
                }
            }
        }

        // ---------- Waypoints ----------
        if (wpOn) {
            for (Waypoint wp : Waypoints.all()) {
                if (!wp.dim().equals(dim)) continue;
                Vec3d c = new Vec3d(wp.x() + 0.5, wp.y() + 0.5, wp.z() + 0.5);
                double d = c.distanceTo(eye);
                if (d > 1000) continue;
                Kind wk = Kind.WAYPOINT;
                topBoxes.add(new ColoredBox(new Box(wp.x() + 0.25, wp.y(), wp.z() + 0.25,
                        wp.x() + 0.75, wp.y() + 1.0, wp.z() + 0.75), wk.r(), wk.g(), wk.b()));
                labels.add(new Label(c.add(0, 1.0, 0), wp.name() + " [" + (int) d + "m]", wk.rgb));
            }
        }

        // ---------- Guide line to the nearest target ----------
        Features.guideInfo = null;
        if (gk != null) {
            Vec3d target = null;
            String name = gk.label;
            switch (gk) {
                case ORE -> {
                    if (nearestOre != null) target = Vec3d.ofCenter(nearestOre);
                }
                case WAYPOINT -> {
                    Waypoint sel = Waypoints.find(Features.guideWaypoint, dim);
                    if (sel != null) {
                        target = new Vec3d(sel.x() + 0.5, sel.y() + 0.5, sel.z() + 0.5);
                        name = sel.name();
                    }
                }
                default -> {
                    Entity ne = nearest[gk.ordinal()];
                    if (ne != null) target = ne.getLerpedPos(td).add(0, ne.getHeight() / 2.0, 0);
                }
            }
            if (target != null) {
                Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
                Vec3d start = cam.add(look.multiply(1.2)).add(0, -0.25, 0);
                lines.add(new Line(start, target, gk.r(), gk.g(), gk.b()));
                Features.guideInfo = "Dẫn đường: " + name + " - " + (int) eye.distanceTo(target) + "m";
            } else {
                Features.guideInfo = "Dẫn đường: " + name + " - không tìm thấy";
            }
        }

        // ---------- Draw ----------
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        draw(matrices, depthBoxes, List.of(), true, 2.0f);
        draw(matrices, topBoxes, lines, false, 2.5f);
        matrices.pop();

        drawLabels(mc, matrices, camera, cam, labels);
    }

    private static String entityLabel(Entity e, Kind k, double dist) {
        String name;
        if (e instanceof ItemEntity item) {
            name = item.getStack().getName().getString() + " x" + item.getStack().getCount();
        } else {
            name = e.getName().getString();
        }
        return name + " [" + (int) dist + "m]";
    }

    /** Draws boxes and lines with the vanilla line shader. matrices must already include the camera offset. */
    private static void draw(MatrixStack matrices, List<ColoredBox> boxes, List<Line> lines,
                             boolean depthTest, float width) {
        if (boxes.isEmpty() && lines.isEmpty()) return;

        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(width);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (!depthTest) RenderSystem.disableDepthTest();

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (ColoredBox b : boxes) {
            WorldRenderer.drawBox(matrices, buffer, b.box(), b.r(), b.g(), b.b(), 1.0f);
        }
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        for (Line l : lines) {
            Vec3d dir = l.to().subtract(l.from());
            if (dir.lengthSquared() < 1.0E-4) continue;
            dir = dir.normalize();
            buffer.vertex(matrix, (float) l.from().x, (float) l.from().y, (float) l.from().z)
                    .color(l.r(), l.g(), l.b(), 1.0f)
                    .normal(entry, (float) dir.x, (float) dir.y, (float) dir.z);
            buffer.vertex(matrix, (float) l.to().x, (float) l.to().y, (float) l.to().z)
                    .color(l.r(), l.g(), l.b(), 1.0f)
                    .normal(entry, (float) dir.x, (float) dir.y, (float) dir.z);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        if (!depthTest) RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();
    }

    /** Billboard text that stays readable through walls and keeps a constant size at range. */
    private static void drawLabels(MinecraftClient mc, MatrixStack matrices, Camera camera, Vec3d cam,
                                   List<Label> labels) {
        if (labels.isEmpty()) return;
        TextRenderer tr = mc.textRenderer;
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        for (Label l : labels) {
            double dist = l.pos().distanceTo(cam);
            float scale = 0.025f * (float) Math.max(1.0, dist / 10.0);

            matrices.push();
            matrices.translate(l.pos().x - cam.x, l.pos().y - cam.y, l.pos().z - cam.z);
            matrices.multiply(camera.getRotation());
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
