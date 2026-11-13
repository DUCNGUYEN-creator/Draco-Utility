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
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Saves and loads all toggles/settings to config/dracoutility.properties. */
public final class Config {
    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dracoutility.properties");
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int integer(Properties p, String key, int def, int min, int max) {
        try {
            int v = Integer.parseInt(p.getProperty(key, "").trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double decimal(Properties p, String key, double def, double min, double max) {
        try {
            double v = Double.parseDouble(p.getProperty(key, "").trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static void load() {
        Properties p = new Properties();
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
                // Fall back to defaults.
            }
        }
        Features.fly = bool(p, "fly", false);
        Features.flySpeed = decimal(p, "flySpeed", 0.6, 0.1, 2.0);
        Features.autoPvp = bool(p, "autoPvp", false);
        Features.pvpPlayers = bool(p, "pvpPlayers", false);

        Features.esp = bool(p, "esp", false);
        Features.espEntities = bool(p, "espEntities", true);
        Features.espItems = bool(p, "espItems", true);
        Features.espOres = bool(p, "espOres", true);
        Features.espLabels = bool(p, "espLabels", true);

        Features.xray = bool(p, "xray", false);
        Features.xrayOutline = bool(p, "xrayOutline", true);
        Features.xrayFullbright = bool(p, "xrayFullbright", true);
        Features.outlineRadius = integer(p, "outlineRadius", 3, 1, 8);

        Features.guide = bool(p, "guide", false);
        Features.guideType = integer(p, "guideType", 0, 0, Targets.Kind.values().length - 1);
        Features.oreFilter = integer(p, "oreFilter", 0, 0, Targets.ORE_FILTER_NAMES.length - 1);
        Features.oreRadius = integer(p, "oreRadius", 24, 8, 64);
        Features.guideWaypoint = p.getProperty("guideWaypoint", "");
        Features.showWaypoints = bool(p, "showWaypoints", true);

        Features.autoClick = bool(p, "autoClick", false);
        Features.clickPlace = bool(p, "clickPlace", false);
        Features.clickDelayTicks = integer(p, "clickDelayTicks", 5, 1, 40);

        Features.minimap = bool(p, "minimap", true);
        Features.minimapSize = integer(p, "minimapSize", 80, 48, 160);
        Features.minimapZoom = integer(p, "minimapZoom", 1, 1, 8);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("fly", String.valueOf(Features.fly));
        p.setProperty("flySpeed", String.valueOf(Features.flySpeed));
        p.setProperty("autoPvp", String.valueOf(Features.autoPvp));
        p.setProperty("pvpPlayers", String.valueOf(Features.pvpPlayers));

        p.setProperty("esp", String.valueOf(Features.esp));
        p.setProperty("espEntities", String.valueOf(Features.espEntities));
        p.setProperty("espItems", String.valueOf(Features.espItems));
        p.setProperty("espOres", String.valueOf(Features.espOres));
        p.setProperty("espLabels", String.valueOf(Features.espLabels));

        p.setProperty("xray", String.valueOf(Features.xray));
        p.setProperty("xrayOutline", String.valueOf(Features.xrayOutline));
        p.setProperty("xrayFullbright", String.valueOf(Features.xrayFullbright));
        p.setProperty("outlineRadius", String.valueOf(Features.outlineRadius));

        p.setProperty("guide", String.valueOf(Features.guide));
        p.setProperty("guideType", String.valueOf(Features.guideType));
        p.setProperty("oreFilter", String.valueOf(Features.oreFilter));
        p.setProperty("oreRadius", String.valueOf(Features.oreRadius));
        p.setProperty("guideWaypoint", Features.guideWaypoint == null ? "" : Features.guideWaypoint);
        p.setProperty("showWaypoints", String.valueOf(Features.showWaypoints));

        p.setProperty("autoClick", String.valueOf(Features.autoClick));
        p.setProperty("clickPlace", String.valueOf(Features.clickPlace));
        p.setProperty("clickDelayTicks", String.valueOf(Features.clickDelayTicks));

        p.setProperty("minimap", String.valueOf(Features.minimap));
        p.setProperty("minimapSize", String.valueOf(Features.minimapSize));
        p.setProperty("minimapZoom", String.valueOf(Features.minimapZoom));

        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "Draco Utility settings");
        } catch (IOException ignored) {
            // Settings are a convenience only.
        }
    }
}
