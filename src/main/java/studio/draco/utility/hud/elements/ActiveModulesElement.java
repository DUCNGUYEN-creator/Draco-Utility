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

package studio.draco.utility.hud.elements;

import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.hud.HudContext;
import studio.draco.utility.hud.HudElement;
import studio.draco.utility.input.KeybindManager;

import java.util.ArrayList;
import java.util.List;

/** Lists the modules that are running, longest name first, right aligned. */
public final class ActiveModulesElement extends HudElement {
    private final Setting.Bool showKey = settings.bool("showKey", "Show keybind", "Append the keybind to each name.", false);

    public ActiveModulesElement() {
        super("activemodules", "Active Modules", Anchor.TOP_RIGHT, 4, 4, true);
    }

    @Override
    public void render(HudContext c) {
        List<String> names = new ArrayList<>();
        for (Module m : Draco.get().modules.running()) {
            if (!m.isToggleable()) continue;
            names.add(m.name() + (showKey.get() && m.keybind() >= 0 ? " [" + KeybindManager.keyName(m.keybind()) + "]" : ""));
        }
        if (names.isEmpty() && c.editor) names.add(title());
        names.sort((a, b) -> Integer.compare(c.font.getWidth(b), c.font.getWidth(a)));

        int w = 10;
        for (String n : names) w = Math.max(w, c.font.getWidth(n));
        int y = 0;
        for (String n : names) {
            c.text(n, w - c.font.getWidth(n), y, c.accent());
            y += 10;
        }
        width = w;
        height = Math.max(10, y - 1);
    }
}
