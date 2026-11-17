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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which capabilities are allowed in the place you are playing right now.
 *
 * <ul>
 *   <li>Singleplayer uses the "private" profile (everything allowed) unless you changed it.</li>
 *   <li>A server you bound to a profile (".policy trust" or ".policy bind") uses that profile.</li>
 *   <li>Any other server uses the default multiplayer profile, "visual" (client-side drawing only).</li>
 * </ul>
 *
 * Nothing here tries to detect or hide from a server's anti-cheat: you decide what is allowed,
 * Draco simply refuses to run modules that need more than that.
 */
public final class PolicyManager {
    public static final String SINGLEPLAYER_KEY = "singleplayer";

    private final Map<String, ServerProfile> profiles = new LinkedHashMap<>();
    private final Map<String, String> bindings = new LinkedHashMap<>();
    private String singleplayerProfile = ServerProfile.PRIVATE;
    private String defaultProfile = ServerProfile.VISUAL;
    private String currentKey = null;
    private Runnable dirtyHook = () -> { };

    public PolicyManager() {
        reset();
    }

    public void setDirtyHook(Runnable hook) { this.dirtyHook = hook == null ? () -> { } : hook; }

    // ------------------------------------------------------------- current place

    /** Normalizes a server address ("MC.Example.com:25565" -> "mc.example.com"). */
    public static String normalizeAddress(String address) {
        if (address == null) return null;
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
        return a.isEmpty() ? null : a;
    }

    /** Call on join. Pass {@link #SINGLEPLAYER_KEY} for a local world, the address otherwise, null if unknown. */
    public synchronized void setCurrent(String key) {
        this.currentKey = key == null ? null : (SINGLEPLAYER_KEY.equals(key) ? key : normalizeAddress(key));
    }

    public synchronized String current() { return currentKey; }

    public synchronized ServerProfile active() {
        return profileFor(currentKey);
    }

    public synchronized ServerProfile profileFor(String key) {
        String id;
        if (SINGLEPLAYER_KEY.equals(key)) id = singleplayerProfile;
        else if (key != null && bindings.containsKey(key)) id = bindings.get(key);
        else id = defaultProfile;
        ServerProfile p = profiles.get(id);
        if (p == null) p = profiles.get(ServerProfile.VISUAL); // never fall back to something wider
        return p;
    }

    /** Null when the capabilities are allowed, otherwise a human readable reason. */
    public synchronized String denyReason(Collection<Capability> needed, String moduleName) {
        if (needed == null || needed.isEmpty()) return null;
        ServerProfile p = active();
        List<String> missing = new ArrayList<>();
        for (Capability c : needed) if (!p.allows(c)) missing.add(c.title());
        if (missing.isEmpty()) return null;
        return moduleName + " needs " + String.join(", ", missing) + ", which profile '" + p.id() + "' does not allow here";
    }

    // ----------------------------------------------------------------- profiles

    public synchronized List<ServerProfile> profiles() { return new ArrayList<>(profiles.values()); }
    public synchronized ServerProfile profile(String id) { return id == null ? null : profiles.get(id.toLowerCase(Locale.ROOT)); }
    public synchronized Map<String, String> bindings() { return new LinkedHashMap<>(bindings); }
    public synchronized String singleplayerProfileId() { return singleplayerProfile; }
    public synchronized String defaultProfileId() { return defaultProfile; }

    private static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9_-]{1,24}");
    }

    /** Creates a custom profile that starts as a copy of another one. */
    public synchronized boolean create(String id, String copyFrom) {
        id = id == null ? null : id.toLowerCase(Locale.ROOT);
        if (!validId(id) || profiles.containsKey(id)) return false;
        ServerProfile src = profile(copyFrom);
        Set<Capability> caps = src == null ? EnumSet.noneOf(Capability.class) : src.allowed();
        profiles.put(id, new ServerProfile(id, false, caps));
        dirtyHook.run();
        return true;
    }

    public synchronized boolean delete(String id) {
        ServerProfile p = profile(id);
        if (p == null || p.isBuiltin()) return false;
        profiles.remove(p.id());
        bindings.values().removeIf(v -> v.equals(p.id()));
        if (singleplayerProfile.equals(p.id())) singleplayerProfile = ServerProfile.PRIVATE;
        if (defaultProfile.equals(p.id())) defaultProfile = ServerProfile.VISUAL;
        dirtyHook.run();
        return true;
    }

    public synchronized boolean setCapability(String id, Capability c, boolean on) {
        ServerProfile p = profile(id);
        if (p == null || c == null || !p.set(c, on)) return false;
        dirtyHook.run();
        return true;
    }

    /** Binds a server address (or the singleplayer key) to a profile. */
    public synchronized boolean bind(String key, String profileId) {
        ServerProfile p = profile(profileId);
        if (p == null || key == null) return false;
        if (SINGLEPLAYER_KEY.equals(key)) singleplayerProfile = p.id();
        else bindings.put(normalizeAddress(key), p.id());
        dirtyHook.run();
        return true;
    }

    public synchronized boolean unbind(String key) {
        if (key == null) return false;
        if (SINGLEPLAYER_KEY.equals(key)) {
            boolean changed = !ServerProfile.PRIVATE.equals(singleplayerProfile);
            singleplayerProfile = ServerProfile.PRIVATE;
            if (changed) dirtyHook.run();
            return changed;
        }
        boolean removed = bindings.remove(normalizeAddress(key)) != null;
        if (removed) dirtyHook.run();
        return removed;
    }

    public synchronized boolean setDefault(String profileId) {
        ServerProfile p = profile(profileId);
        if (p == null) return false;
        defaultProfile = p.id();
        dirtyHook.run();
        return true;
    }

    // -------------------------------------------------------------- persistence

    public synchronized Map<String, Object> save() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> custom = new LinkedHashMap<>();
        for (ServerProfile p : profiles.values()) {
            if (p.isBuiltin()) continue;
            List<Object> caps = new ArrayList<>();
            for (Capability c : p.allowed()) caps.add(c.key());
            custom.put(p.id(), caps);
        }
        out.put("profiles", custom);
        out.put("bindings", new LinkedHashMap<>(bindings));
        out.put("singleplayer", singleplayerProfile);
        out.put("default", defaultProfile);
        return out;
    }

    public synchronized void load(Map<String, Object> data) {
        reset();
        if (data.get("profiles") instanceof Map<?, ?> custom) {
            for (Map.Entry<?, ?> e : custom.entrySet()) {
                String id = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                if (!validId(id) || profiles.containsKey(id)) continue;
                Set<Capability> caps = EnumSet.noneOf(Capability.class);
                if (e.getValue() instanceof List<?> list) {
                    for (Object o : list) {
                        Capability c = Capability.byKey(String.valueOf(o));
                        if (c != null) caps.add(c);
                    }
                }
                profiles.put(id, new ServerProfile(id, false, caps));
            }
        }
        if (data.get("bindings") instanceof Map<?, ?> b) {
            for (Map.Entry<?, ?> e : b.entrySet()) {
                String key = normalizeAddress(String.valueOf(e.getKey()));
                String id = String.valueOf(e.getValue()).toLowerCase(Locale.ROOT);
                if (key != null && profiles.containsKey(id)) bindings.put(key, id);
            }
        }
        if (data.get("singleplayer") instanceof String s && profiles.containsKey(s.toLowerCase(Locale.ROOT))) {
            singleplayerProfile = s.toLowerCase(Locale.ROOT);
        }
        if (data.get("default") instanceof String s && profiles.containsKey(s.toLowerCase(Locale.ROOT))) {
            defaultProfile = s.toLowerCase(Locale.ROOT);
        }
    }

    public synchronized void reset() {
        profiles.clear();
        bindings.clear();
        profiles.put(ServerProfile.PRIVATE, ServerProfile.privateProfile());
        profiles.put(ServerProfile.INFO, ServerProfile.builtin(ServerProfile.INFO,
                Capability.LOCAL_UI, Capability.LOCAL_VISUAL, Capability.NAVIGATION, Capability.WORLD_INFORMATION));
        profiles.put(ServerProfile.VISUAL, ServerProfile.builtin(ServerProfile.VISUAL,
                Capability.LOCAL_UI, Capability.LOCAL_VISUAL, Capability.NAVIGATION));
        singleplayerProfile = ServerProfile.PRIVATE;
        defaultProfile = ServerProfile.VISUAL;
    }
}
