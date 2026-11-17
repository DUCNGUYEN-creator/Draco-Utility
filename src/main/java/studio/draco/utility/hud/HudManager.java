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

package studio.draco.utility.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import studio.draco.utility.core.config.Json;
import studio.draco.utility.Draco;
import studio.draco.utility.platform.Access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns the HUD elements, places them from their anchors and persists the layout. */
public final class HudManager {
    public record Bounds(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<HudElement> elements = new ArrayList<>();
    private Runnable dirtyHook = () -> { };

    public void register(HudElement e) {
        elements.add(e);
        e.settings.setDirtyHook(() -> dirtyHook.run());
    }

    public List<HudElement> elements() { return Collections.unmodifiableList(elements); }

    public HudElement find(String id) {
        for (HudElement e : elements) if (e.id().equalsIgnoreCase(id)) return e;
        return null;
    }

    public void setDirtyHook(Runnable hook) {
        this.dirtyHook = hook == null ? () -> { } : hook;
        for (HudElement e : elements) e.settings.setDirtyHook(this.dirtyHook);
    }

    public void markDirty() { dirtyHook.run(); }

    // ----------------------------------------------------------------- layout

    public Bounds bounds(HudElement e, int screenW, int screenH) {
        int w = Math.max(8, (int) Math.ceil(e.width() * e.scale));
        int h = Math.max(8, (int) Math.ceil(e.height() * e.scale));
        boolean left = e.anchor == HudElement.Anchor.TOP_LEFT || e.anchor == HudElement.Anchor.BOTTOM_LEFT;
        boolean top = e.anchor == HudElement.Anchor.TOP_LEFT || e.anchor == HudElement.Anchor.TOP_RIGHT;
        int x = left ? e.offsetX : screenW - w - e.offsetX;
        int y = top ? e.offsetY : screenH - h - e.offsetY;
        return new Bounds(x, y, w, h);
    }

    /** Moves the element so its top-left corner is at (x, y) and picks the nearest corner as anchor. */
    public void moveTo(HudElement e, int x, int y, int screenW, int screenH) {
        Bounds b = bounds(e, screenW, screenH);
        boolean left = x + b.w() / 2 < screenW / 2;
        boolean top = y + b.h() / 2 < screenH / 2;
        e.anchor = left ? (top ? HudElement.Anchor.TOP_LEFT : HudElement.Anchor.BOTTOM_LEFT)
                : (top ? HudElement.Anchor.TOP_RIGHT : HudElement.Anchor.BOTTOM_RIGHT);
        e.offsetX = Math.max(0, left ? x : screenW - x - b.w());
        e.offsetY = Math.max(0, top ? y : screenH - y - b.h());
        dirtyHook.run();
    }

    // -------------------------------------------------------------- rendering

    /** In-game drawing (HudRenderCallback). */
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.getDebugHud().shouldShowDebugHud()) return; // do not cover the F3 screen
        draw(ctx, mc, tickCounter.getTickDelta(true), false);
    }

    /** Layout editor drawing: every element is shown, hidden ones dimmed by the editor itself. */
    public void renderEditor(DrawContext ctx, float delta) {
        draw(ctx, MinecraftClient.getInstance(), delta, true);
    }

    private void draw(DrawContext ctx, MinecraftClient mc, float delta, boolean editor) {
        boolean allowed = Access.inWorld(mc);
        Draco dr = Draco.maybe();
        boolean worldInfo = allowed && (dr == null || dr.policy.active().allows(studio.draco.utility.core.policy.Capability.WORLD_INFORMATION));
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        for (HudElement e : elements) {
            boolean show = e.visible && (!e.requiresPermission() || worldInfo);
            e.setShown(show || editor);
            if (!show && !editor) continue;
            Bounds b = bounds(e, sw, sh);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(b.x(), b.y(), 0);
            m.scale((float) e.scale, (float) e.scale, 1f);
            try {
                e.render(new HudContext(ctx, mc, delta, editor));
            } catch (Throwable t) {
                e.visible = false; // a broken element must never break the whole HUD
                studio.draco.utility.core.Log.error("HUD element failed and was hidden: " + e.id(), t);
            }
            m.pop();
        }
    }

    // ------------------------------------------------------------ persistence

    public Map<String, Object> save() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (HudElement e : elements) out.put(e.id(), e.save());
        return out;
    }

    public void load(Map<String, Object> data) {
        for (HudElement e : elements) {
            Map<String, Object> m = Json.map(data.get(e.id()));
            if (!m.isEmpty()) e.load(m);
        }
    }

    public void reset() {
        for (HudElement e : elements) e.resetAll();
    }
}
