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

package studio.draco.utility.core.module;

import studio.draco.utility.core.event.Event;
import studio.draco.utility.core.event.EventPriority;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Base class of every feature. A module has metadata, setting groups, a keybind and a lifecycle.
 * Event listeners are registered with on() while the module is being activated and are removed
 * automatically when it is deactivated, so nothing can outlive the module.
 */
public abstract class Module {
    public enum BindMode { TOGGLE, HOLD }

    /** Execution statistics of this module's event handlers (for the profiler). */
    public static final class Stats {
        private long calls;
        private long lastNanos;
        private double averageNanos;

        void record(long nanos) {
            calls++;
            lastNanos = nanos;
            averageNanos += (nanos - averageNanos) * 0.05;
        }

        public long calls() { return calls; }
        public double lastMs() { return lastNanos / 1e6; }
        public double averageMs() { return averageNanos / 1e6; }
    }

    private final String id;
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final List<SettingGroup> groups = new ArrayList<>();
    private final List<Class<?>> requiredServices = new ArrayList<>();
    private final Set<String> conflicts = new LinkedHashSet<>();
    private final Set<Capability> capabilities = EnumSet.noneOf(Capability.class);
    private final Stats stats = new Stats();
    private String[] aliases = new String[0];

    /** The default setting group, shown first in the GUI. */
    public final SettingGroup general;

    // Written by ModuleManager (same package).
    ModuleManager manager;
    boolean enabled;
    boolean running;
    ModuleState state = ModuleState.REGISTERED;
    String blockedReason;
    boolean refusalReported;

    private int keybind = -1;
    private BindMode bindMode = BindMode.TOGGLE;
    private boolean favorite;

    protected Module(String id, String name, String description, ModuleCategory category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.general = group("General");
    }

    // -------------------------------------------------------------- metadata

    public final String id() { return id; }
    public final String name() { return name; }
    public final String description() { return description; }
    public final ModuleCategory category() { return category; }
    public final String[] aliases() { return aliases.clone(); }
    public final List<SettingGroup> groups() { return Collections.unmodifiableList(groups); }
    public final List<Class<?>> requiredServices() { return Collections.unmodifiableList(requiredServices); }
    public final Set<String> conflicts() { return Collections.unmodifiableSet(conflicts); }
    public final Stats stats() { return stats; }

    /** What this module does to the game; the active server profile must allow all of it. */
    public final Set<Capability> capabilities() { return Collections.unmodifiableSet(capabilities); }

    /** Why the module is held back right now (no world, profile does not allow it...), or null. */
    public final String blockedReason() { return blockedReason; }

    protected final void aliases(String... names) { this.aliases = names.clone(); }

    /** Declares services that must exist before this module may start. */
    protected final void requires(Class<?>... services) { Collections.addAll(requiredServices, services); }

    /** Declares what the module does. A module with no capabilities is never restricted by a profile. */
    protected final void capabilities(Capability... caps) { Collections.addAll(capabilities, caps); }

    /** Declares module ids that cannot be enabled together with this one. */
    protected final void conflictsWith(String... moduleIds) { Collections.addAll(conflicts, moduleIds); }

    protected final SettingGroup group(String groupName) {
        SettingGroup g = new SettingGroup(groupName);
        groups.add(g);
        if (manager != null) g.setDirtyHook(manager::markDirty);
        return g;
    }

    public final Setting<?> setting(String settingId) {
        for (SettingGroup g : groups) {
            Setting<?> s = g.find(settingId);
            if (s != null) return s;
        }
        return null;
    }

    // ----------------------------------------------------------------- state

    public final boolean isEnabled() { return enabled; }
    public final boolean isRunning() { return running; }
    public final ModuleState state() { return state; }

    /** Modules like the client settings are always on and cannot be toggled. */
    public boolean isToggleable() { return true; }

    /** True if the module must keep running outside a world / without permission. */
    public boolean ignoresGate() { return false; }

    public final void setEnabled(boolean on) {
        if (manager == null) throw new IllegalStateException("Module not registered: " + id);
        manager.setEnabled(this, on);
    }

    public final void toggle() { setEnabled(!enabled); }

    // ---------------------------------------------------------------- keybind

    public final int keybind() { return keybind; }
    public final BindMode bindMode() { return bindMode; }
    public final boolean isFavorite() { return favorite; }

    public final void setKeybind(int key) {
        this.keybind = key < 0 ? -1 : key;
        markDirty();
    }

    public final void setBindMode(BindMode mode) {
        this.bindMode = mode;
        markDirty();
    }

    public final void setFavorite(boolean value) {
        this.favorite = value;
        markDirty();
    }

    private void markDirty() {
        if (manager != null) manager.markDirty();
    }

    // -------------------------------------------------------------- lifecycle

    /** Called once when the module is registered. Create settings here or in the constructor. */
    protected void onRegister() { }

    /** Acquire resources and register listeners. Throwing rolls the activation back. */
    protected void onActivate() throws Exception { }

    /** Release everything acquired in onActivate(). Listeners are already removed. */
    protected void onDeactivate() { }

    /**
     * Registers a listener for the lifetime of the activation. Call it from onActivate().
     * Exceptions are isolated; repeated failures disable the module.
     */
    protected final <T extends Event> void on(Class<T> type, EventPriority priority, Consumer<? super T> handler) {
        if (manager == null) throw new IllegalStateException("Module not registered: " + id);
        manager.bus().subscribe(type, priority, this, null, event -> {
            long t0 = System.nanoTime();
            try {
                handler.accept(event);
            } catch (Throwable t) {
                manager.reportError(this, t);
            } finally {
                stats.record(System.nanoTime() - t0);
            }
        });
    }

    protected final <T extends Event> void on(Class<T> type, Consumer<? super T> handler) {
        on(type, EventPriority.NORMAL, handler);
    }

    // ---------------------------------------------------------- serialization

    public final Map<String, Object> saveState() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("keybind", (long) keybind);
        m.put("bindMode", bindMode.name());
        m.put("favorite", favorite);
        Map<String, Object> values = new LinkedHashMap<>();
        for (SettingGroup g : groups) {
            for (Setting<?> s : g) values.put(s.id(), s.toJson());
        }
        m.put("settings", values);
        return m;
    }

    /** Loads values; unknown or invalid entries are ignored so old configs never break the mod. */
    public final void loadState(Map<String, Object> m) {
        if (m.get("enabled") instanceof Boolean b && isToggleable()) enabled = b;
        if (m.get("keybind") instanceof Number n) keybind = n.intValue() < 0 ? -1 : n.intValue();
        if (m.get("bindMode") instanceof String s) {
            try { bindMode = BindMode.valueOf(s.toUpperCase()); } catch (IllegalArgumentException ignored) { }
        }
        if (m.get("favorite") instanceof Boolean f) favorite = f;
        if (m.get("settings") instanceof Map<?, ?> values) {
            for (SettingGroup g : groups) {
                for (Setting<?> s : g) {
                    Object v = values.get(s.id());
                    if (v != null) s.fromJson(v);
                }
            }
        }
    }

    public final void resetState() {
        for (SettingGroup g : groups) g.resetAll();
        keybind = -1;
        bindMode = BindMode.TOGGLE;
        favorite = false;
        enabled = !isToggleable();
    }

    @Override
    public String toString() {
        return "Module[" + id + "]";
    }
}
