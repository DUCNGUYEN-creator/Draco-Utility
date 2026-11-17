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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A named set of allowed capabilities. Built-in profiles are fixed; custom ones can be edited.
 * Unknown situations get the restrictive default, so a feature that changes gameplay is never
 * switched on by accident.
 */
public final class ServerProfile {
    public static final String PRIVATE = "private";
    public static final String INFO = "info";
    public static final String VISUAL = "visual";

    private final String id;
    private final boolean builtin;
    private final Set<Capability> allowed;

    ServerProfile(String id, boolean builtin, Set<Capability> allowed) {
        this.id = id;
        this.builtin = builtin;
        this.allowed = allowed.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(allowed);
    }

    public String id() { return id; }
    public boolean isBuiltin() { return builtin; }
    public Set<Capability> allowed() { return Collections.unmodifiableSet(allowed); }
    public boolean allows(Capability c) { return allowed.contains(c); }

    /** Returns false (and changes nothing) for a built-in profile. */
    boolean set(Capability c, boolean on) {
        if (builtin) return false;
        if (on) allowed.add(c);
        else allowed.remove(c);
        return true;
    }

    static ServerProfile builtin(String id, Capability... caps) {
        Set<Capability> s = EnumSet.noneOf(Capability.class);
        Collections.addAll(s, caps);
        return new ServerProfile(id, true, s);
    }

    static ServerProfile privateProfile() {
        return new ServerProfile(PRIVATE, true, EnumSet.allOf(Capability.class));
    }
}
