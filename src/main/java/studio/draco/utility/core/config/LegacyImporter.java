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

package studio.draco.utility.core.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Converts Draco Utility 1.x files (properties, waypoints, history) into the 2.0 formats. */
public final class LegacyImporter {
    private static final String[] KINDS = {"ORE", "PLAYER", "HOSTILE", "PASSIVE", "ITEM", "WAYPOINT"};
    private static final String[] ORES = {"ALL", "COAL", "IRON", "COPPER", "GOLD", "REDSTONE", "LAPIS",
            "DIAMOND", "EMERALD", "QUARTZ", "DEBRIS"};

    private LegacyImporter() {}

    /** Returns module states keyed by module id, in the same shape ModuleManager.loadState() reads. */
    public static Map<String, Object> importModules(Path propertiesFile) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();

        module(out, "flight", bool(p, "fly"), "speed", dbl(p, "flySpeed"));
        module(out, "autopvp", bool(p, "autoPvp"), "players", bool(p, "pvpPlayers"));

        boolean esp = Boolean.TRUE.equals(bool(p, "esp"));
        Boolean entities = bool(p, "espEntities");
        Map<String, Object> espModule = module(out, "esp", esp, null, null);
        set(espModule, "players", entities);
        set(espModule, "hostile", entities);
        set(espModule, "passive", entities);
        set(espModule, "items", bool(p, "espItems"));
        set(espModule, "labels", bool(p, "espLabels"));

        Map<String, Object> ore = module(out, "oreesp", esp && !Boolean.FALSE.equals(bool(p, "espOres")), null, null);
        set(ore, "labels", bool(p, "espLabels"));
        set(ore, "radius", integer(p, "oreRadius"));
        Integer filter = integer(p, "oreFilter");
        if (filter != null && filter >= 0 && filter < ORES.length) set(ore, "filter", ORES[filter]);

        Map<String, Object> xray = module(out, "xray", bool(p, "xray"), null, null);
        set(xray, "outline", bool(p, "xrayOutline"));
        set(xray, "fullbright", bool(p, "xrayFullbright"));
        set(xray, "outlineRadius", integer(p, "outlineRadius"));

        Map<String, Object> guide = module(out, "guide", bool(p, "guide"), null, null);
        Integer kind = integer(p, "guideType");
        if (kind != null && kind >= 0 && kind < KINDS.length) set(guide, "kind", KINDS[kind]);
        if (filter != null && filter >= 0 && filter < ORES.length) set(guide, "oreFilter", ORES[filter]);
        set(guide, "radius", integer(p, "oreRadius"));
        String waypoint = p.getProperty("guideWaypoint");
        if (waypoint != null && !waypoint.isEmpty()) set(guide, "waypoint", waypoint);

        module(out, "waypoints", bool(p, "showWaypoints"), null, null);

        Map<String, Object> click = module(out, "autoclick", bool(p, "autoClick"), null, null);
        Boolean place = bool(p, "clickPlace");
        if (place != null) set(click, "mode", place ? "PLACE" : "ATTACK_BREAK");
        Integer delay = integer(p, "clickDelayTicks");
        if (delay != null && delay > 0) set(click, "cps", Math.min(20.0, 20.0 / delay));
        return out;
    }

    /** Waypoints as maps with name, x, y, z, dim. */
    public static List<Object> importWaypoints(Path file) throws IOException {
        List<Object> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] t = line.split("\t");
            if (t.length < 5) continue;
            try {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("name", t[0]);
                w.put("x", Long.parseLong(t[1]));
                w.put("y", Long.parseLong(t[2]));
                w.put("z", Long.parseLong(t[3]));
                w.put("dim", t[4]);
                out.add(w);
            } catch (NumberFormatException ignored) {
                // Skip a broken line.
            }
        }
        return out;
    }

    public static List<Object> importHistory(Path file) throws IOException {
        List<Object> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) out.add(line);
        }
        return out;
    }

    // ----------------------------------------------------------------- helpers

    private static Map<String, Object> module(Map<String, Object> out, String id, Object enabled, String settingId, Object settingValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> settings = new LinkedHashMap<>();
        if (enabled != null) m.put("enabled", enabled);
        if (settingId != null && settingValue != null) settings.put(settingId, settingValue);
        m.put("settings", settings);
        out.put(id, m);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static void set(Map<String, Object> module, String settingId, Object value) {
        if (value != null) ((Map<String, Object>) module.get("settings")).put(settingId, value);
    }

    private static Boolean bool(Properties p, String key) {
        String v = p.getProperty(key);
        return v == null ? null : Boolean.valueOf(v.trim());
    }

    private static Integer integer(Properties p, String key) {
        try { return Integer.valueOf(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double dbl(Properties p, String key) {
        try { return Double.valueOf(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return null; }
    }
}
