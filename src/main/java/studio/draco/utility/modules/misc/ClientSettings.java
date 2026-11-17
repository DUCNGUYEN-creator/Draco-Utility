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

package studio.draco.utility.modules.misc;

import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;

/** Always-on module that holds the client-wide options (theme, prefix, language, notifications, performance). */
public final class ClientSettings extends Module {
    public enum ThemeKind { CLASSIC, MINIMAL, GLASS }
    public enum Language { AUTO, ENGLISH, VIETNAMESE }

    public final Setting.Choice<ThemeKind> theme = general.choice("theme", "Theme", "Look of the Click GUI.", ThemeKind.CLASSIC);
    public final Setting.Color accent = general.color("accent", "Accent color", "Highlight color of the GUI and HUD.", 0x4FA3FF);
    public final Setting.Bool animations = general.bool("animations", "Animations", "Smooth hover effects.", true);
    public final Setting.Choice<Language> language = general.choice("language", "Language", "Text of the tools and HUD.", Language.AUTO);
    public final Setting.Text prefix = general.text("prefix", "Command prefix", "Chat messages that start with this are commands.", ".", 3);

    private final SettingGroup notifications = group("Notifications");
    public final Setting.Bool toasts = notifications.bool("toasts", "Show notifications", "Toast messages for module changes and errors.", true);
    public final Setting.Int seconds = notifications.integer("seconds", "Duration (seconds)", "How long a notification stays.", 4, 1, 15);
    public final Setting.Int maxVisible = notifications.integer("maxVisible", "Max visible", "How many notifications show at once.", 5, 1, 10);

    private final SettingGroup performance = group("Performance");
    public final Setting.Dbl budgetMs = performance.dbl("budgetMs", "Background budget (ms)",
            "Time per tick that scanning and map sampling may use.", 1.5, 0.2, 6.0);

    public ClientSettings() {
        super("client", "Client", "Global options of Draco Utility.", ModuleCategory.MISC);
        aliases("settings", "config");
        toasts.onChanged(v -> apply());
        seconds.onChanged(v -> apply());
        maxVisible.onChanged(v -> apply());
    }

    @Override public boolean isToggleable() { return false; }
    @Override public boolean ignoresGate() { return true; }

    @Override
    protected void onActivate() {
        apply();
    }

    private void apply() {
        Draco d = Draco.maybe();
        if (d != null) d.notifications.configure(toasts.get(), seconds.get() * 1000L, maxVisible.get());
    }
}
