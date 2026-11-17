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

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * What a module needs to draw in the world: submit boxes, filled boxes, lines and labels, and the
 * RenderManager draws them all in a few batches. Boxes outside the view frustum are dropped early.
 */
public final class RenderContext {
    public record BoxShape(Box box, float r, float g, float b, float a, boolean throughWalls) { }
    public record LineShape(Vec3d from, Vec3d to, float r, float g, float b, boolean throughWalls) { }
    public record LabelShape(Vec3d pos, String text, int rgb) { }

    public final MatrixStack matrices;
    public final Camera camera;
    public final Vec3d cameraPos;
    public final float tickDelta;
    public final ClientWorld world;
    public final ClientPlayerEntity player;
    private final Frustum frustum;

    final List<BoxShape> outlines = new ArrayList<>();
    final List<BoxShape> fills = new ArrayList<>();
    final List<LineShape> lines = new ArrayList<>();
    final List<LabelShape> labels = new ArrayList<>();

    public RenderContext(MatrixStack matrices, Camera camera, float tickDelta, Frustum frustum,
                         ClientWorld world, ClientPlayerEntity player) {
        this.matrices = matrices;
        this.camera = camera;
        this.cameraPos = camera.getPos();
        this.tickDelta = tickDelta;
        this.frustum = frustum;
        this.world = world;
        this.player = player;
    }

    private boolean visible(Box box) {
        return frustum == null || frustum.isVisible(box);
    }

    private static float r(int rgb) { return ((rgb >> 16) & 0xFF) / 255f; }
    private static float g(int rgb) { return ((rgb >> 8) & 0xFF) / 255f; }
    private static float b(int rgb) { return (rgb & 0xFF) / 255f; }

    /** Box outline. throughWalls = visible behind blocks. */
    public void box(Box box, int rgb, boolean throughWalls) {
        if (visible(box)) outlines.add(new BoxShape(box, r(rgb), g(rgb), b(rgb), 1f, throughWalls));
    }

    /** Translucent filled box. alpha is 0..1. */
    public void fill(Box box, int rgb, float alpha, boolean throughWalls) {
        if (visible(box)) fills.add(new BoxShape(box, r(rgb), g(rgb), b(rgb), alpha, throughWalls));
    }

    public void line(Vec3d from, Vec3d to, int rgb, boolean throughWalls) {
        lines.add(new LineShape(from, to, r(rgb), g(rgb), b(rgb), throughWalls));
    }

    /** Floating text that always faces the camera; stays readable through walls. */
    public void label(Vec3d pos, String text, int rgb) {
        labels.add(new LabelShape(pos, text, rgb));
    }

    public int shapeCount() {
        return outlines.size() + fills.size() + lines.size() + labels.size();
    }
}
