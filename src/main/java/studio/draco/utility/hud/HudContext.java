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
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import studio.draco.utility.Draco;

/** What a HUD element needs to draw itself. Coordinates are local: (0, 0) is the element's corner. */
public final class HudContext {
    public final DrawContext draw;
    public final TextRenderer font;
    public final MinecraftClient mc;
    public final float delta;
    public final boolean editor;

    public HudContext(DrawContext draw, MinecraftClient mc, float delta, boolean editor) {
        this.draw = draw;
        this.font = mc.textRenderer;
        this.mc = mc;
        this.delta = delta;
        this.editor = editor;
    }

    public int accent() {
        Draco d = Draco.maybe();
        return d == null || d.settings == null ? 0x4FA3FF : d.settings.accent.get();
    }

    /** Draws shadowed text and returns its width. rgb is 0xRRGGBB. */
    public int text(String s, int x, int y, int rgb) {
        draw.drawTextWithShadow(font, s, x, y, 0xFF000000 | rgb);
        return font.getWidth(s);
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        draw.fill(x1, y1, x2, y2, argb);
    }
}
