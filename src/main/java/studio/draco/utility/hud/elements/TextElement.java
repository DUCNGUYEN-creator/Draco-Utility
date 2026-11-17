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

package studio.draco.utility.hud.elements;

import studio.draco.utility.hud.HudContext;
import studio.draco.utility.hud.HudElement;

import java.util.List;

/** Base for elements that are just a few lines of text. */
public abstract class TextElement extends HudElement {
    protected TextElement(String id, String title, Anchor anchor, int x, int y, boolean visible) {
        super(id, title, anchor, x, y, visible);
    }

    protected abstract List<String> lines(HudContext c);

    protected int color(HudContext c) { return 0xFFFFFF; }

    @Override
    public void render(HudContext c) {
        List<String> lines = lines(c);
        if (lines.isEmpty()) {
            if (!c.editor) {
                width = 10;
                height = 10;
                return;
            }
            lines = List.of(title());
        }
        int w = 0;
        int y = 0;
        for (String line : lines) {
            w = Math.max(w, c.text(line, 0, y, color(c)));
            y += 10;
        }
        width = w;
        height = y - 1;
    }
}
