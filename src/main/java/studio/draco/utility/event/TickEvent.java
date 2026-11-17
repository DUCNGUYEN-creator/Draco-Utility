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

/** Client tick events. Pre runs after services refreshed their data, Post after the game's own tick logic. */
public final class TickEvent {
    private TickEvent() {}

    public static final class Pre implements Event {
        public static final Pre INSTANCE = new Pre();
        private Pre() {}
    }

    public static final class Post implements Event {
        public static final Post INSTANCE = new Post();
        private Post() {}
    }
}
