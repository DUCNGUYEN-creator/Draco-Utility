/*
 * Draco Utility - client-side utility mod for Minecraft (Fabric)
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

package studio.draco.utility;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Named coordinate marks, saved to config/dracoutility_waypoints.txt (tab separated). */
public final class Waypoints {
    public record Waypoint(String name, int x, int y, int z, String dim) {}

    private static final List<Waypoint> LIST = new ArrayList<>();

    private Waypoints() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dracoutility_waypoints.txt");
    }

    public static String dim(ClientWorld w) {
        return w.getRegistryKey().getValue().toString();
    }

    public static List<Waypoint> all() {
        return LIST;
    }

    public static Waypoint find(String name, String dim) {
        if (name == null) return null;
        for (Waypoint w : LIST) {
            if (w.name().equals(name) && w.dim().equals(dim)) return w;
        }
        return null;
    }

    public static void add(Waypoint w) {
        LIST.add(new Waypoint(w.name().replace('\t', ' ').replace('\n', ' ').trim(), w.x(), w.y(), w.z(), w.dim()));
        save();
    }

    public static void remove(Waypoint w) {
        LIST.remove(w);
        save();
    }

    public static void load() {
        LIST.clear();
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String[] t = line.split("\t");
                if (t.length < 5) continue;
                try {
                    LIST.add(new Waypoint(t[0], Integer.parseInt(t[1]), Integer.parseInt(t[2]),
                            Integer.parseInt(t[3]), t[4]));
                } catch (NumberFormatException ignored) {
                    // Skip a broken line.
                }
            }
        } catch (IOException ignored) {
            // Ignore read errors.
        }
    }

    private static void save() {
        List<String> lines = new ArrayList<>();
        for (Waypoint w : LIST) {
            lines.add(w.name() + "\t" + w.x() + "\t" + w.y() + "\t" + w.z() + "\t" + w.dim());
        }
        try {
            Files.write(file(), lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Ignore write errors.
        }
    }
}
