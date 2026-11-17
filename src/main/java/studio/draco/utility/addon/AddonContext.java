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

import studio.draco.utility.Draco;
import studio.draco.utility.core.command.Command;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.hud.HudElement;

/**
 * What an addon may do. Modules from addons are subject to the same server policy as built-in
 * ones, so each must declare its capabilities; a module that declares none is refused.
 */
public final class AddonContext {
    private final String addonId;
    private final Draco draco;
    int modules;

    AddonContext(String addonId, Draco draco) {
        this.addonId = addonId;
        this.draco = draco;
    }

    public String addonId() { return addonId; }

    public void registerModule(Module module) {
        if (module.capabilities().isEmpty()) {
            throw new IllegalArgumentException("Module '" + module.id() + "' of addon " + addonId
                    + " must declare at least one capability (call capabilities(...) in its constructor)");
        }
        draco.modules.register(module);
        modules++;
    }

    public void registerCommand(Command command) { draco.commands.register(command); }

    public void registerHudElement(HudElement element) { draco.hud.register(element); }
}
