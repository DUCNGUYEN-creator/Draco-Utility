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

import studio.draco.utility.core.Log;
import studio.draco.utility.core.event.EventBus;
import studio.draco.utility.core.policy.PolicyManager;
import studio.draco.utility.core.service.ServiceRegistry;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Registers modules and drives their state machine. A module runs only while the user has it
 * enabled AND the gate (in a world, with permission) is open. Activation failures roll back,
 * and a module that throws repeatedly is switched off without touching the others.
 */
public final class ModuleManager {
    public interface Listener {
        default void onState(Module module, ModuleState from, ModuleState to) { }
        default void onError(Module module, Throwable error, boolean disabled) { }
        default void onRefused(Module module, String reason) { }
    }

    private static final int ERROR_LIMIT = 3;
    private static final long ERROR_WINDOW_MS = 5000;

    private final EventBus bus;
    private final ServiceRegistry services;
    private final LongSupplier clockMs;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, Module> aliasIndex = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Module, ArrayDeque<Long>> errorTimes = new HashMap<>();
    private final List<Consumer<Module>> releasers = new CopyOnWriteArrayList<>();
    private volatile BooleanSupplier gate = () -> true;
    private volatile PolicyManager policy;
    private volatile Runnable dirtyHook = () -> { };

    public ModuleManager(EventBus bus, ServiceRegistry services, LongSupplier clockMs) {
        this.bus = bus;
        this.services = services;
        this.clockMs = clockMs;
    }

    public EventBus bus() { return bus; }
    public ServiceRegistry services() { return services; }
    public void addListener(Listener l) { listeners.add(l); }

    /** Decides whether modules may run right now (in a world and permitted). */
    public void setGate(BooleanSupplier gate) { this.gate = gate; }

    /** The server policy: a module only runs while the active profile allows all its capabilities. */
    public void setPolicy(PolicyManager policy) { this.policy = policy; }

    /**
     * Registers a cleanup that always runs when a module stops, even if its onDeactivate() throws
     * or its activation failed half way. Used to give back shared services (entity scan, block
     * scans, night vision) so nothing stays acquired by a dead module.
     */
    public void addReleaser(Consumer<Module> releaser) { releasers.add(releaser); }

    /** Null when the policy allows the module, otherwise the reason it is blocked. */
    public String policyBlock(Module m) {
        PolicyManager p = policy;
        return p == null || m.ignoresGate() ? null : p.denyReason(m.capabilities(), m.name());
    }

    /** Called whenever persistent state changes; wired to the config auto-save. */
    public void setDirtyHook(Runnable hook) {
        this.dirtyHook = hook == null ? () -> { } : hook;
        for (Module m : modules.values()) {
            for (SettingGroup g : m.groups()) g.setDirtyHook(this.dirtyHook);
        }
    }

    void markDirty() { dirtyHook.run(); }

    // ------------------------------------------------------------ registration

    public void register(Module m) {
        String key = m.id().toLowerCase(Locale.ROOT);
        if (modules.containsKey(key)) throw new IllegalArgumentException("Duplicate module id: " + m.id());
        m.manager = this;
        m.state = ModuleState.REGISTERED;
        m.enabled = !m.isToggleable();
        modules.put(key, m);
        for (String alias : m.aliases()) aliasIndex.put(alias.toLowerCase(Locale.ROOT), m);
        for (SettingGroup g : m.groups()) g.setDirtyHook(dirtyHook);
        try {
            m.onRegister();
        } catch (Throwable t) {
            Log.error("onRegister failed for " + m.id(), t);
        }
        m.state = ModuleState.DISABLED;
    }

    public Module get(String idOrAlias) {
        if (idOrAlias == null) return null;
        String key = idOrAlias.toLowerCase(Locale.ROOT);
        Module m = modules.get(key);
        return m != null ? m : aliasIndex.get(key);
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules.values()) if (type.isInstance(m)) return type.cast(m);
        return null;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    public List<Module> byCategory(ModuleCategory category) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules.values()) if (m.category() == category) out.add(m);
        return out;
    }

    public List<Module> running() {
        List<Module> out = new ArrayList<>();
        for (Module m : modules.values()) if (m.isRunning()) out.add(m);
        return out;
    }

    public List<Module> withKeybind(int key) {
        List<Module> out = new ArrayList<>();
        if (key < 0) return out;
        for (Module m : modules.values()) if (m.keybind() == key) out.add(m);
        return out;
    }

    /** Matches name, id, alias, description and setting names; best matches first. */
    public List<Module> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all();
        record Scored(Module module, int score) { }
        List<Scored> hits = new ArrayList<>();
        for (Module m : modules.values()) {
            int score = score(m, q);
            if (score >= 0) hits.add(new Scored(m, score));
        }
        hits.sort((a, b) -> a.score != b.score ? Integer.compare(a.score, b.score)
                : a.module.name().compareToIgnoreCase(b.module.name()));
        List<Module> out = new ArrayList<>();
        for (Scored s : hits) out.add(s.module);
        return out;
    }

    private static int score(Module m, String q) {
        String name = m.name().toLowerCase(Locale.ROOT);
        if (name.equals(q) || m.id().equalsIgnoreCase(q)) return 0;
        if (name.startsWith(q)) return 1;
        if (name.contains(q)) return 2;
        for (String a : m.aliases()) if (a.toLowerCase(Locale.ROOT).contains(q)) return 3;
        if (m.description().toLowerCase(Locale.ROOT).contains(q)) return 4;
        for (SettingGroup g : m.groups()) {
            for (Setting<?> s : g) {
                if (s.name().toLowerCase(Locale.ROOT).contains(q) || s.id().toLowerCase(Locale.ROOT).contains(q)) return 5;
            }
        }
        return -1;
    }

    // --------------------------------------------------------------- lifecycle

    /** Turns a module on or off (user intent). Returns false if it was refused. */
    public boolean setEnabled(Module m, boolean on) {
        if (!m.isToggleable() && !on) return false;
        if (on && !m.enabled) {
            for (String otherId : m.conflicts()) {
                Module other = get(otherId);
                if (other != null && other.enabled) {
                    String reason = m.name() + " conflicts with " + other.name();
                    for (Listener l : listeners) l.onRefused(m, reason);
                    return false;
                }
            }
        }
        if (on && !m.enabled) {
            String block = policyBlock(m);
            if (block != null) {
                for (Listener l : listeners) l.onRefused(m, block);
                return false;
            }
        }
        if (m.enabled != on) {
            m.enabled = on;
            markDirty();
        }
        sync(m, gate.getAsBoolean());
        return true;
    }

    /** Applies the gate to every module. Call once per client tick. */
    public void tick() {
        boolean open = gate.getAsBoolean();
        for (Module m : modules.values()) sync(m, open);
    }

    /** Stops every running module but keeps the user's on/off choices (used on world leave). */
    public void deactivateAll() {
        for (Module m : modules.values()) {
            if (m.running) deactivate(m, m.enabled ? ModuleState.SUSPENDED : ModuleState.DISABLED);
        }
    }

    private void sync(Module m, boolean gateOpen) {
        String block = policyBlock(m);
        m.blockedReason = block != null ? block : (!gateOpen && !m.ignoresGate() ? "Not in a world" : null);
        if (block != null && m.enabled && gateOpen) {
            if (!m.refusalReported) {
                m.refusalReported = true;
                for (Listener l : listeners) l.onRefused(m, block);
            }
        } else {
            m.refusalReported = false;
        }
        boolean want = m.enabled && block == null && (gateOpen || m.ignoresGate());
        if (want && !m.running) {
            activate(m);
        } else if (!want && m.running) {
            deactivate(m, m.enabled ? ModuleState.SUSPENDED : ModuleState.DISABLED);
        } else if (!want) {
            ModuleState idle = m.enabled ? ModuleState.SUSPENDED
                    : (m.state == ModuleState.FAILED ? ModuleState.FAILED : ModuleState.DISABLED);
            setState(m, idle);
        }
    }

    private void activate(Module m) {
        for (Class<?> dep : m.requiredServices()) {
            if (!services.has(dep)) {
                fail(m, new IllegalStateException("Missing service " + dep.getSimpleName()));
                return;
            }
        }
        setState(m, ModuleState.ENABLING);
        try {
            m.onActivate();
        } catch (Throwable t) {
            bus.unsubscribeAll(m);
            try {
                m.onDeactivate();
            } catch (Throwable ignored) {
                // Best-effort cleanup after a failed activation.
            } finally {
                release(m);
            }
            fail(m, t);
            return;
        }
        m.running = true;
        setState(m, ModuleState.ENABLED);
    }

    private void deactivate(Module m, ModuleState finalState) {
        setState(m, ModuleState.DISABLING);
        bus.unsubscribeAll(m);
        try {
            m.onDeactivate();
        } catch (Throwable t) {
            Log.error("onDeactivate failed for " + m.id(), t);
            for (Listener l : listeners) l.onError(m, t, false);
        } finally {
            // Whatever the module did (or failed to do), shared resources it held are given back.
            release(m);
        }
        m.running = false;
        setState(m, finalState);
    }

    private void release(Module m) {
        bus.unsubscribeAll(m);
        for (Consumer<Module> r : releasers) {
            try {
                r.accept(m);
            } catch (Throwable t) {
                Log.error("Resource release failed for " + m.id(), t);
            }
        }
    }

    private void fail(Module m, Throwable t) {
        m.running = false;
        m.enabled = false;
        setState(m, ModuleState.FAILED);
        markDirty();
        Log.error("Module failed to start: " + m.id(), t);
        for (Listener l : listeners) l.onError(m, t, true);
    }

    private void setState(Module m, ModuleState to) {
        ModuleState from = m.state;
        if (from == to) return;
        m.state = to;
        for (Listener l : listeners) l.onState(m, from, to);
    }

    /** Called by a module's event wrapper. Three errors within five seconds switch the module off. */
    void reportError(Module m, Throwable t) {
        long now = clockMs.getAsLong();
        ArrayDeque<Long> times = errorTimes.computeIfAbsent(m, k -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > ERROR_WINDOW_MS) times.removeFirst();
        boolean disable = times.size() >= ERROR_LIMIT;
        Log.error("Error in module " + m.id(), t);
        if (disable) {
            times.clear();
            m.enabled = false;
            markDirty();
            if (m.running) deactivate(m, ModuleState.FAILED);
        }
        for (Listener l : listeners) l.onError(m, t, disable);
    }

    // ------------------------------------------------------------ persistence

    public Map<String, Object> saveState() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Module m : modules.values()) out.put(m.id(), m.saveState());
        return out;
    }

    public void loadState(Map<String, Object> data) {
        for (Module m : modules.values()) {
            if (data.get(m.id()) instanceof Map<?, ?> raw) {
                Map<String, Object> state = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) state.put(String.valueOf(e.getKey()), e.getValue());
                m.loadState(state);
            }
        }
        // The gate decides when enabled modules actually start (next tick).
    }

    public void resetState() {
        deactivateAll();
        for (Module m : modules.values()) {
            m.resetState();
            setState(m, m.enabled ? ModuleState.SUSPENDED : ModuleState.DISABLED);
        }
    }
}
