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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One configurable value. Handles default, validation, visibility, change callbacks and JSON
 * (de)serialization, so the GUI and the config layer never need module-specific code.
 */
public abstract class Setting<T> {
    public enum Type { BOOL, INT, DOUBLE, ENUM, STRING, COLOR, KEY }

    private final String id;
    private final String name;
    private final String description;
    protected T value;
    protected T defaultValue;
    private BooleanSupplier visibility = () -> true;
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private Runnable dirtyHook = () -> { };

    protected Setting(String id, String name, String description, T defaultValue) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    /** Subclasses call this at the end of their constructor, once their bounds are assigned. */
    protected final void init() {
        defaultValue = coerce(defaultValue);
        value = defaultValue;
    }

    public final String id() { return id; }
    public final String name() { return name; }
    public final String description() { return description; }
    public final T get() { return value; }
    public final T defaultValue() { return defaultValue; }
    public abstract Type type();

    /** Clamps / normalizes a candidate value. */
    protected abstract T coerce(T candidate);
    public abstract Object toJson();
    public abstract boolean fromJson(Object json);
    public abstract boolean parse(String text);

    public String valueToString() { return String.valueOf(value); }

    /** Returns true if the stored value changed. */
    public final boolean set(T candidate) {
        T coerced = coerce(candidate);
        if (Objects.equals(coerced, value)) return false;
        value = coerced;
        for (Consumer<T> l : listeners) l.accept(value);
        dirtyHook.run();
        return true;
    }

    public final void reset() { set(defaultValue); }
    public final boolean isDefault() { return Objects.equals(value, defaultValue); }

    public final Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visibility = Objects.requireNonNull(condition);
        return this;
    }

    public final boolean isVisible() { return visibility.getAsBoolean(); }

    public final Setting<T> onChanged(Consumer<T> listener) {
        listeners.add(listener);
        return this;
    }

    final void setDirtyHook(Runnable hook) { this.dirtyHook = hook == null ? () -> { } : hook; }

    // ------------------------------------------------------------ implementations

    public static final class Bool extends Setting<Boolean> {
        public Bool(String id, String name, String description, boolean def) {
            super(id, name, description, def);
            init();
        }

        @Override public Type type() { return Type.BOOL; }
        @Override protected Boolean coerce(Boolean v) { return v != null && v; }
        @Override public Object toJson() { return value; }

        @Override public boolean fromJson(Object json) {
            if (json instanceof Boolean b) { set(b); return true; }
            return json instanceof String s && parse(s);
        }

        @Override public boolean parse(String text) {
            String t = text.trim().toLowerCase(Locale.ROOT);
            if (t.equals("true") || t.equals("on") || t.equals("1") || t.equals("yes")) { set(true); return true; }
            if (t.equals("false") || t.equals("off") || t.equals("0") || t.equals("no")) { set(false); return true; }
            return false;
        }

        public boolean on() { return value; }
    }

    public static final class Int extends Setting<Integer> {
        private final int min;
        private final int max;

        public Int(String id, String name, String description, int def, int min, int max) {
            super(id, name, description, def);
            this.min = min;
            this.max = max;
            init();
        }

        public int min() { return min; }
        public int max() { return max; }
        @Override public Type type() { return Type.INT; }
        @Override protected Integer coerce(Integer v) { return Math.max(min, Math.min(max, v == null ? min : v)); }
        @Override public Object toJson() { return (long) value; }

        @Override public boolean fromJson(Object json) {
            if (json instanceof Number n) { set(n.intValue()); return true; }
            return json instanceof String s && parse(s);
        }

        @Override public boolean parse(String text) {
            try { set(Integer.parseInt(text.trim())); return true; } catch (NumberFormatException e) { return false; }
        }
    }

    public static final class Dbl extends Setting<Double> {
        private final double min;
        private final double max;

        public Dbl(String id, String name, String description, double def, double min, double max) {
            super(id, name, description, def);
            this.min = min;
            this.max = max;
            init();
        }

        public double min() { return min; }
        public double max() { return max; }
        @Override public Type type() { return Type.DOUBLE; }

        @Override protected Double coerce(Double v) {
            double d = v == null || v.isNaN() ? min : v;
            return Math.max(min, Math.min(max, d));
        }

        @Override public Object toJson() { return value; }

        @Override public boolean fromJson(Object json) {
            if (json instanceof Number n) { set(n.doubleValue()); return true; }
            return json instanceof String s && parse(s);
        }

        @Override public boolean parse(String text) {
            try { set(Double.parseDouble(text.trim())); return true; } catch (NumberFormatException e) { return false; }
        }
    }

    public static final class Choice<E extends Enum<E>> extends Setting<E> {
        private final Class<E> type;

        public Choice(String id, String name, String description, E def) {
            super(id, name, description, def);
            this.type = def.getDeclaringClass();
            init();
        }

        public E[] values() { return type.getEnumConstants(); }
        @Override public Type type() { return Type.ENUM; }
        @Override protected E coerce(E v) { return v == null ? defaultValue : v; }
        @Override public Object toJson() { return value.name(); }

        @Override public boolean fromJson(Object json) {
            return json instanceof String s && parse(s);
        }

        @Override public boolean parse(String text) {
            for (E e : values()) {
                if (e.name().equalsIgnoreCase(text.trim())) { set(e); return true; }
            }
            return false;
        }

        public E next() { return shift(1); }
        public E previous() { return shift(-1); }

        private E shift(int delta) {
            E[] all = values();
            int i = (value.ordinal() + delta + all.length) % all.length;
            set(all[i]);
            return value;
        }
    }

    public static final class Text extends Setting<String> {
        private final int maxLength;

        public Text(String id, String name, String description, String def, int maxLength) {
            super(id, name, description, def);
            this.maxLength = maxLength;
            init();
        }

        public int maxLength() { return maxLength; }
        @Override public Type type() { return Type.STRING; }

        @Override protected String coerce(String v) {
            String s = v == null ? "" : v;
            return s.length() > maxLength ? s.substring(0, maxLength) : s;
        }

        @Override public Object toJson() { return value; }
        @Override public boolean fromJson(Object json) { if (json instanceof String s) { set(s); return true; } return false; }
        @Override public boolean parse(String text) { set(text); return true; }
    }

    /** A 0xRRGGBB color. */
    public static final class Color extends Setting<Integer> {
        public Color(String id, String name, String description, int rgb) {
            super(id, name, description, rgb);
            init();
        }

        @Override public Type type() { return Type.COLOR; }
        @Override protected Integer coerce(Integer v) { return (v == null ? 0 : v) & 0xFFFFFF; }
        @Override public Object toJson() { return String.format("#%06X", value); }

        @Override public boolean fromJson(Object json) {
            if (json instanceof Number n) { set(n.intValue()); return true; }
            return json instanceof String s && parse(s);
        }

        @Override public boolean parse(String text) {
            String t = text.trim();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.length() != 6) return false;
            try { set(Integer.parseInt(t, 16)); return true; } catch (NumberFormatException e) { return false; }
        }

        @Override public String valueToString() { return String.format("#%06X", value); }
    }

    /** A key code (GLFW), -1 means unbound. */
    public static final class Key extends Setting<Integer> {
        public Key(String id, String name, String description, int def) {
            super(id, name, description, def);
            init();
        }

        @Override public Type type() { return Type.KEY; }
        @Override protected Integer coerce(Integer v) { return v == null || v < 0 ? -1 : v; }
        @Override public Object toJson() { return (long) value; }

        @Override public boolean fromJson(Object json) {
            if (json instanceof Number n) { set(n.intValue()); return true; }
            return json instanceof String s && parse(s);
        }

        @Override public boolean parse(String text) {
            String t = text.trim().toLowerCase(Locale.ROOT);
            if (t.equals("none") || t.equals("unbound")) { set(-1); return true; }
            try { set(Integer.parseInt(t)); return true; } catch (NumberFormatException e) { return false; }
        }
    }
}
