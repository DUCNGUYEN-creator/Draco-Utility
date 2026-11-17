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

import studio.draco.utility.core.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Upgrades old config files one schema version at a time, so renaming a setting in a later
 * release does not wipe the user's configuration.
 */
public final class ConfigMigrator {
    public interface Step {
        Map<String, Object> apply(Map<String, Object> data);
    }

    private final Map<String, TreeMap<Integer, Step>> steps = new HashMap<>();

    /** Registers the step that upgrades file {@code fileId} from {@code fromVersion} to fromVersion + 1. */
    public void register(String fileId, int fromVersion, Step step) {
        steps.computeIfAbsent(fileId, k -> new TreeMap<>()).put(fromVersion, step);
    }

    public Map<String, Object> migrate(String fileId, int fromVersion, int toVersion, Map<String, Object> data) {
        Map<String, Object> current = data;
        TreeMap<Integer, Step> forFile = steps.get(fileId);
        for (int v = fromVersion; v < toVersion; v++) {
            Step step = forFile == null ? null : forFile.get(v);
            if (step == null) {
                Log.warn("No migration for " + fileId + " v" + v + " -> v" + (v + 1) + "; assuming compatible");
                continue;
            }
            current = step.apply(current);
        }
        return current;
    }
}
