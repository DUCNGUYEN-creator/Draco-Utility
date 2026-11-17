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

package studio.draco.utility.addon;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import studio.draco.utility.Draco;
import studio.draco.utility.core.Log;

import java.util.ArrayList;
import java.util.List;

/** Finds addons through the "draco-addon" entrypoint and starts each one in isolation. */
public final class AddonManager {
    public static final String ENTRYPOINT = "draco-addon";

    private final List<String> loaded = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    public List<String> loaded() { return List.copyOf(loaded); }
    public List<String> problems() { return List.copyOf(problems); }

    public void loadAll(Draco draco) {
        List<EntrypointContainer<DracoAddon>> found;
        try {
            found = FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, DracoAddon.class);
        } catch (Throwable t) {
            problems.add("Could not read addon entrypoints: " + t);
            Log.error("Addon discovery failed", t);
            return;
        }
        for (EntrypointContainer<DracoAddon> container : found) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                DracoAddon addon = container.getEntrypoint();
                if (addon.apiVersion() != Draco.API_VERSION) {
                    problems.add(modId + ": needs addon API " + addon.apiVersion() + ", Draco provides " + Draco.API_VERSION);
                    continue;
                }
                AddonContext ctx = new AddonContext(modId, draco);
                addon.onInitialize(ctx);
                loaded.add(addon.name() + " (" + modId + ", " + ctx.modules + " modules)");
            } catch (Throwable t) {
                // A broken addon never takes the client down.
                problems.add(modId + ": " + t.getMessage());
                Log.error("Addon " + modId + " failed to start", t);
            }
        }
    }
}
