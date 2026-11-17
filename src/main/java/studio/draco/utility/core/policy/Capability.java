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

package studio.draco.utility.core.policy;

import java.util.Locale;

/**
 * What a module does to the game, independent of its name. Every module declares the capabilities
 * it needs and a {@link ServerProfile} decides which of them are allowed where you are playing
 * (singleplayer, your own server, somebody else's server...).
 */
public enum Capability {
    LOCAL_UI("Local UI", "Menus, themes and HUD. Never touches the game."),
    LOCAL_VISUAL("Local visual", "Client-side drawing only (fullbright, minimap...)."),
    NAVIGATION("Navigation", "Saved waypoints and guidance."),
    WORLD_INFORMATION("World information", "Shows information the vanilla client hides (ESP, X-Ray, ore finder)."),
    MOVEMENT("Movement modification", "Changes how you move (flight, speed...)."),
    COMBAT("Combat automation", "Chooses and attacks targets for you."),
    AUTOMATION("Gameplay automation", "Performs actions for you (auto click, auto totem, anti-AFK...)."),
    NETWORK("Network interaction", "Sends or alters protocol traffic.");

    private final String title;
    private final String description;

    Capability(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() { return title; }
    public String description() { return description; }

    /** Lower-case id used in commands and config files, e.g. "world_information". */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static Capability byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (Capability c : values()) {
            if (c.key().equals(k)) return c;
        }
        return null;
    }
}
