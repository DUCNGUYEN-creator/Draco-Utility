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

import studio.draco.utility.core.event.Event;
import studio.draco.utility.render.RenderContext;

/** Posted once per frame; modules submit boxes, lines and labels to the context. */
public final class Render3DEvent implements Event {
    public final RenderContext ctx;

    public Render3DEvent(RenderContext ctx) {
        this.ctx = ctx;
    }
}
