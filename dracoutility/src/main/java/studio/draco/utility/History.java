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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Command history, persisted to config/dracoutility_history.txt (oldest first). */
public final class History {
    private static final int MAX = 50;
    private static final List<String> ENTRIES = new ArrayList<>();

    private History() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dracoutility_history.txt");
    }

    public static void load() {
        ENTRIES.clear();
        try {
            Path p = file();
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) ENTRIES.add(line);
                }
            }
        } catch (IOException ignored) {
            // History is a convenience only; ignore read errors.
        }
        trim();
    }

    private static void save() {
        try {
            Files.write(file(), ENTRIES, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Ignore write errors.
        }
    }

    private static void trim() {
        while (ENTRIES.size() > MAX) ENTRIES.remove(0);
    }

    public static List<String> list() {
        return ENTRIES;
    }

    public static void add(String cmd) {
        ENTRIES.remove(cmd); // avoid duplicates, move to newest
        ENTRIES.add(cmd);
        trim();
        save();
    }

    public static void set(int index, String cmd) {
        if (index >= 0 && index < ENTRIES.size() && !cmd.isBlank()) {
            ENTRIES.set(index, cmd);
            save();
        }
    }

    public static void remove(int index) {
        if (index >= 0 && index < ENTRIES.size()) {
            ENTRIES.remove(index);
            save();
        }
    }

    public static void clear() {
        ENTRIES.clear();
        save();
    }
}
