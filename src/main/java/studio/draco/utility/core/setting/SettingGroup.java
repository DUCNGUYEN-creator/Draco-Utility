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

package studio.draco.utility.core.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** A named, ordered set of settings (one collapsible section in the GUI). */
public final class SettingGroup implements Iterable<Setting<?>> {
    private final String name;
    private final List<Setting<?>> settings = new ArrayList<>();
    private Runnable dirtyHook = () -> { };

    public SettingGroup(String name) {
        this.name = name;
    }

    public String name() { return name; }
    public List<Setting<?>> all() { return Collections.unmodifiableList(settings); }
    @Override public Iterator<Setting<?>> iterator() { return all().iterator(); }

    public Setting.Bool bool(String id, String name, String description, boolean def) {
        return add(new Setting.Bool(id, name, description, def));
    }

    public Setting.Int integer(String id, String name, String description, int def, int min, int max) {
        return add(new Setting.Int(id, name, description, def, min, max));
    }

    public Setting.Dbl dbl(String id, String name, String description, double def, double min, double max) {
        return add(new Setting.Dbl(id, name, description, def, min, max));
    }

    public <E extends Enum<E>> Setting.Choice<E> choice(String id, String name, String description, E def) {
        return add(new Setting.Choice<>(id, name, description, def));
    }

    public Setting.Text text(String id, String name, String description, String def, int maxLength) {
        return add(new Setting.Text(id, name, description, def, maxLength));
    }

    public Setting.Color color(String id, String name, String description, int rgb) {
        return add(new Setting.Color(id, name, description, rgb));
    }

    public Setting.Key key(String id, String name, String description, int def) {
        return add(new Setting.Key(id, name, description, def));
    }

    public Setting<?> find(String id) {
        for (Setting<?> s : settings) if (s.id().equalsIgnoreCase(id)) return s;
        return null;
    }

    /** Called when any setting in the group changes; used to schedule an auto-save. */
    public void setDirtyHook(Runnable hook) {
        this.dirtyHook = hook == null ? () -> { } : hook;
        for (Setting<?> s : settings) s.setDirtyHook(this.dirtyHook);
    }

    public void resetAll() {
        for (Setting<?> s : settings) s.reset();
    }

    private <S extends Setting<?>> S add(S setting) {
        if (find(setting.id()) != null) throw new IllegalArgumentException("Duplicate setting id: " + setting.id());
        settings.add(setting);
        setting.setDirtyHook(dirtyHook);
        return setting;
    }
}
