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

/**
 * Entry point of an addon. Declare it in the addon's fabric.mod.json:
 * <pre>"entrypoints": { "draco-addon": ["com.example.MyAddon"] }</pre>
 * An addon only gets what {@link AddonContext} offers, never Draco's internals.
 */
public interface DracoAddon {
    /** The Draco addon API version this addon was written for (see {@code Draco.API_VERSION}). */
    default int apiVersion() { return 1; }

    /** Human readable name shown in logs and messages. */
    String name();

    /** Register modules, commands and HUD elements here. */
    void onInitialize(AddonContext context);
}
