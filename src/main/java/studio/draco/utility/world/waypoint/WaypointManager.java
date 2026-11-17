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

package studio.draco.utility.world.waypoint;

import studio.draco.utility.core.config.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Named coordinates, per dimension. Pure data; the Persistable adapter lives in the bootstrap. */
public final class WaypointManager {
    public record Waypoint(String name, int x, int y, int z, String dim) { }

    private final List<Waypoint> list = new ArrayList<>();
    private Runnable dirtyHook = () -> { };

    public void setDirtyHook(Runnable hook) { this.dirtyHook = hook == null ? () -> { } : hook; }

    public List<Waypoint> all() { return Collections.unmodifiableList(list); }

    public List<Waypoint> forDimension(String dim) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : list) if (w.dim().equals(dim)) out.add(w);
        return out;
    }

    public Waypoint find(String name, String dim) {
        if (name == null) return null;
        for (Waypoint w : list) if (w.name().equalsIgnoreCase(name) && w.dim().equals(dim)) return w;
        return null;
    }

    /** Adds a waypoint; one with the same name in the same dimension is replaced. */
    public void add(Waypoint w) {
        String clean = w.name().replace('\t', ' ').replace('\n', ' ').trim();
        if (clean.isEmpty()) return;
        Waypoint fixed = new Waypoint(clean, w.x(), w.y(), w.z(), w.dim());
        list.removeIf(o -> o.name().equalsIgnoreCase(clean) && o.dim().equals(w.dim()));
        list.add(fixed);
        dirtyHook.run();
    }

    public void remove(Waypoint w) {
        if (list.remove(w)) dirtyHook.run();
    }

    public void reset() { list.clear(); }

    public Map<String, Object> save() {
        List<Object> items = new ArrayList<>();
        for (Waypoint w : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", w.name());
            m.put("x", (long) w.x());
            m.put("y", (long) w.y());
            m.put("z", (long) w.z());
            m.put("dim", w.dim());
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("waypoints", items);
        return out;
    }

    public void load(Map<String, Object> data) {
        list.clear();
        for (Object o : Json.list(data.get("waypoints"))) {
            Map<String, Object> m = Json.map(o);
            String name = Json.str(m.get("name"), "");
            if (name.isEmpty()) continue;
            list.add(new Waypoint(name, (int) Json.lng(m.get("x"), 0), (int) Json.lng(m.get("y"), 0),
                    (int) Json.lng(m.get("z"), 0), Json.str(m.get("dim"), "minecraft:overworld")));
        }
    }
}
