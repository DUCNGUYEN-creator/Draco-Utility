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

import studio.draco.utility.modules.misc.ClientSettings.ThemeKind;

/** Colors of the Click GUI (ARGB). Three presets share the user's accent color. */
public final class Theme {
    public final int panelBg;
    public final int header;
    public final int text;
    public final int textDim;
    public final int hover;
    public final int on;
    public final int off;
    public final int sliderBg;
    public final int tooltipBg;
    public final int accent;

    private Theme(int panelBg, int header, int text, int textDim, int hover, int on, int off, int sliderBg,
                  int tooltipBg, int accent) {
        this.panelBg = panelBg;
        this.header = header;
        this.text = text;
        this.textDim = textDim;
        this.hover = hover;
        this.on = on;
        this.off = off;
        this.sliderBg = sliderBg;
        this.tooltipBg = tooltipBg;
        this.accent = accent;
    }

    public static Theme of(ThemeKind kind, int accentRgb) {
        int accent = 0xFF000000 | accentRgb;
        int onTint = 0xA0000000 | accentRgb;
        return switch (kind) {
            case MINIMAL -> new Theme(0xF00C0C10, 0xFF0C0C10, 0xFFE6E6E6, 0xFF8A8F98, 0x22FFFFFF, onTint,
                    0x00000000, 0xFF1B1D23, 0xF0000000, accent);
            case GLASS -> new Theme(0x8C101822, 0xA01C2836, 0xFFFFFFFF, 0xFFB4BCC8, 0x28FFFFFF, onTint,
                    0x18FFFFFF, 0x60000000, 0xC0101822, accent);
            default -> new Theme(0xE0181A20, 0xF0242830, 0xFFE8E8E8, 0xFF9AA0A8, 0x30FFFFFF, onTint,
                    0xFF22262D, 0xFF2A2E35, 0xF0101418, accent);
        };
    }
}
