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

package studio.draco.utility.event;

import net.minecraft.client.gui.DrawContext;
import studio.draco.utility.core.event.Event;

/** Posted once per frame while the in-game HUD is drawn. */
public final class Render2DEvent implements Event {
    public final DrawContext draw;
    public final float tickDelta;

    public Render2DEvent(DrawContext draw, float tickDelta) {
        this.draw = draw;
        this.tickDelta = tickDelta;
    }
}
