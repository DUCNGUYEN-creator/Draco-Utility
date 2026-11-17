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

import studio.draco.utility.core.config.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Commands run from the Tools screen, newest last. */
public final class CommandHistory {
    private static final int MAX = 50;

    private final List<String> entries = new ArrayList<>();
    private Runnable dirtyHook = () -> { };

    public void setDirtyHook(Runnable hook) { this.dirtyHook = hook == null ? () -> { } : hook; }
    public List<String> list() { return entries; }

    public void add(String cmd) {
        entries.remove(cmd);
        entries.add(cmd);
        while (entries.size() > MAX) entries.remove(0);
        dirtyHook.run();
    }

    public void set(int index, String cmd) {
        if (index >= 0 && index < entries.size() && !cmd.isBlank()) {
            entries.set(index, cmd);
            dirtyHook.run();
        }
    }

    public void remove(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            dirtyHook.run();
        }
    }

    public void clear() {
        entries.clear();
        dirtyHook.run();
    }

    public Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entries", new ArrayList<Object>(entries));
        return m;
    }

    public void load(Map<String, Object> data) {
        entries.clear();
        for (Object o : Json.list(data.get("entries"))) {
            if (o instanceof String s && !s.isBlank()) entries.add(s);
        }
        while (entries.size() > MAX) entries.remove(0);
    }
}
