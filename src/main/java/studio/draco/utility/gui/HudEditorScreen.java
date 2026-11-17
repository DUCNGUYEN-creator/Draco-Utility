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

package studio.draco.utility.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import studio.draco.utility.Draco;
import studio.draco.utility.hud.HudElement;
import studio.draco.utility.hud.HudManager;
import studio.draco.utility.platform.L;

import java.util.List;

/**
 * Layout editor for the HUD. Drag to move (hold Shift for a coarse grid), scroll to scale,
 * right click to show / hide, middle click to reset one element.
 */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudElement selected;
    private HudElement dragging;
    private int grabX;
    private int grabY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        Draco.get().config.flush();
        client.setScreen(parent);
    }

    private HudElement elementAt(double mx, double my) {
        List<HudElement> list = Draco.get().hud.elements();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudElement e = list.get(i);
            if (Draco.get().hud.bounds(e, width, height).contains(mx, my)) return e;
        }
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
        HudManager hud = Draco.get().hud;
        hud.renderEditor(ctx, delta);

        int accent = 0xFF000000 | Draco.get().settings.accent.get();
        for (HudElement e : hud.elements()) {
            HudManager.Bounds b = hud.bounds(e, width, height);
            int color = e == selected ? accent : (e.visible ? 0xAAFFFFFF : 0x66FF6666);
            ctx.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y(), color);
            ctx.fill(b.x() - 1, b.y() + b.h(), b.x() + b.w() + 1, b.y() + b.h() + 1, color);
            ctx.fill(b.x() - 1, b.y(), b.x(), b.y() + b.h(), color);
            ctx.fill(b.x() + b.w(), b.y(), b.x() + b.w() + 1, b.y() + b.h(), color);
            if (!e.visible) ctx.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0x88000000);
            if (e == selected || b.contains(mx, my)) {
                String label = e.title() + (e.visible ? "" : L.t(" (hidden)", " (ẩn)")) + String.format(" x%.1f", e.scale);
                int ly = b.y() > 12 ? b.y() - 11 : b.y() + b.h() + 2;
                ctx.drawTextWithShadow(textRenderer, label, b.x(), ly, 0xFFFFFFFF);
            }
        }
        String help = L.t("Drag: move   Scroll: scale   Right click: show / hide   Middle click: reset   Shift: coarse grid   Esc: back",
                "Kéo: di chuyển   Cuộn: phóng to/nhỏ   Chuột phải: hiện/ẩn   Chuột giữa: đặt lại   Shift: lưới thô   Esc: quay lại");
        ctx.drawCenteredTextWithShadow(textRenderer, help, width / 2, height - 12, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        HudElement e = elementAt(mx, my);
        selected = e;
        if (e == null) return super.mouseClicked(mx, my, button);
        if (button == 0) {
            dragging = e;
            HudManager.Bounds b = Draco.get().hud.bounds(e, width, height);
            grabX = (int) mx - b.x();
            grabY = (int) my - b.y();
        } else if (button == 1) {
            e.visible = !e.visible;
            Draco.get().hud.markDirty();
        } else if (button == 2) {
            e.resetPosition();
            Draco.get().hud.markDirty();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(mx, my, button, dx, dy);
        int grid = Screen.hasShiftDown() ? 8 : 1;
        int x = (int) Math.round((mx - grabX) / grid) * grid;
        int y = (int) Math.round((my - grabY) / grid) * grid;
        Draco.get().hud.moveTo(dragging, x, y, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        HudElement e = elementAt(mx, my);
        if (e == null) return super.mouseScrolled(mx, my, horizontal, vertical);
        e.scale = Math.max(0.5, Math.min(3.0, Math.round((e.scale + (vertical > 0 ? 0.1 : -0.1)) * 10.0) / 10.0));
        Draco.get().hud.markDirty();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DELETE && selected != null) {
            selected.visible = false;
            Draco.get().hud.markDirty();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
