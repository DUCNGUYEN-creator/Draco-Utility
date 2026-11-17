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

import studio.draco.utility.Draco;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.hud.HudContext;
import studio.draco.utility.hud.HudElement;
import studio.draco.utility.services.EntityService;
import studio.draco.utility.world.minimap.MiniMap;

/** The corner map. Locked like the modules, because it can show mobs, players and ores. */
public final class MinimapElement extends HudElement {
    private final Setting.Int size = settings.integer("size", "Size", "Map size in GUI pixels.", 80, 48, 160);
    private final Setting.Int zoom = settings.integer("zoom", "Blocks per cell", "Higher shows a larger area.", 1, 1, 8);

    public MinimapElement() {
        super("minimap", "Minimap", Anchor.TOP_LEFT, 4, 16, true);
    }

    @Override public boolean requiresPermission() { return true; }

    @Override
    protected void onShown() {
        Draco.get().services.get(EntityService.class).acquire(this);
    }

    @Override
    protected void onHidden() {
        Draco.get().services.get(EntityService.class).release(this);
    }

    @Override
    public void render(HudContext c) {
        int cells = Math.max(16, size.get() / MiniMap.CELL_PX);
        MiniMap.touch(cells, zoom.get());
        width = cells * MiniMap.CELL_PX + 2;
        height = width;
        c.fill(0, 0, width, height, 0xFF000000);
        MiniMap.Grid g = MiniMap.grid();
        if (g.colors != null && g.cells > 0) {
            MiniMap.drawGrid(c.draw, g, 1, 1, MiniMap.CELL_PX);
            MiniMap.drawMarkers(c.draw, g, 1, 1, MiniMap.CELL_PX, false);
        }
    }
}
