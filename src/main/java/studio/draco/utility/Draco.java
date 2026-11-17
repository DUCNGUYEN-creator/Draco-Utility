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

package studio.draco.utility;

import studio.draco.utility.addon.AddonManager;
import studio.draco.utility.core.command.CommandManager;
import studio.draco.utility.core.config.ConfigManager;
import studio.draco.utility.core.config.ConfigMigrator;
import studio.draco.utility.core.config.ProfileManager;
import studio.draco.utility.core.event.EventBus;
import studio.draco.utility.core.lifecycle.Lifecycle;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleManager;
import studio.draco.utility.core.notification.NotificationManager;
import studio.draco.utility.core.policy.PolicyManager;
import studio.draco.utility.core.profiler.Profiler;
import studio.draco.utility.core.scheduler.WorkScheduler;
import studio.draco.utility.core.service.ServiceRegistry;
import studio.draco.utility.hud.HudManager;
import studio.draco.utility.input.KeybindManager;
import studio.draco.utility.modules.misc.ClientSettings;
import studio.draco.utility.services.CommandHistory;
import studio.draco.utility.world.waypoint.WaypointManager;

import java.nio.file.Path;

/** The one object that owns every core system. Created once by the Bootstrap. */
public final class Draco {
    public static final String VERSION = "2.0.0";
    /** Version of the public addon API (studio.draco.utility.addon). Increases on breaking changes. */
    public static final int API_VERSION = 1;

    private static volatile Draco instance;

    public final EventBus bus = new EventBus();
    public final ServiceRegistry services = new ServiceRegistry();
    public final ModuleManager modules = new ModuleManager(bus, services, System::currentTimeMillis);
    public final WorkScheduler scheduler = new WorkScheduler();
    public final Profiler profiler = new Profiler();
    public final NotificationManager notifications = new NotificationManager(System::currentTimeMillis);
    public final PolicyManager policy = new PolicyManager();
    public final ConfigMigrator migrator = new ConfigMigrator();
    public final ConfigManager config;
    public final ProfileManager profiles;
    public final CommandManager commands;
    public final HudManager hud = new HudManager();
    public final KeybindManager keybinds = new KeybindManager();
    public final WaypointManager waypoints = new WaypointManager();
    public final CommandHistory history = new CommandHistory();
    public final AddonManager addons = new AddonManager();
    public final Lifecycle lifecycle = new Lifecycle();

    /** Set when the ClientSettings module is registered. */
    public volatile ClientSettings settings;

    private Draco(Path configRoot) {
        this.config = new ConfigManager(configRoot, System::currentTimeMillis, migrator);
        this.profiles = new ProfileManager(config);
        this.commands = new CommandManager(() -> {
            ClientSettings s = settings;
            return s == null ? "." : s.prefix.get();
        });
        instance = this;
    }

    /** Creates the singleton. Called once by the Bootstrap. */
    public static Draco create(Path configRoot) {
        return new Draco(configRoot);
    }

    public static Draco get() {
        Draco d = instance;
        if (d == null) throw new IllegalStateException("Draco Utility is not initialized yet");
        return d;
    }

    /** Like get(), but returns null before startup finished. */
    public static Draco maybe() {
        return instance;
    }

    /** Toggles a module from the GUI, a key or a command and tells the user. */
    public void toggle(Module m) {
        boolean wasOn = m.isEnabled();
        if (modules.setEnabled(m, !wasOn) && m.isToggleable()) {
            notifications.info(m.name(), m.isEnabled() ? "Enabled" : "Disabled");
        }
    }
}
