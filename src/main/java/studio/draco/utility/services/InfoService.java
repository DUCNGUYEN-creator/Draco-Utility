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

package studio.draco.utility.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Short status lines modules publish for the HUD (for example the guide target). */
public final class InfoService {
    private final Map<String, String> lines = new ConcurrentHashMap<>();

    public void set(String key, String text) {
        if (text == null) lines.remove(key);
        else lines.put(key, text);
    }

    public void clear(String key) { lines.remove(key); }
    public String get(String key) { return lines.get(key); }
}
