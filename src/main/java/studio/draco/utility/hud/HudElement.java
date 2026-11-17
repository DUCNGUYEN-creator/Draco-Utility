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

package studio.draco.utility.hud;

import studio.draco.utility.core.config.Json;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One piece of HUD. It draws itself in local coordinates and reports its size; the HudManager places
 * it from an anchor corner plus an offset, so layouts survive a change of resolution.
 */
public abstract class HudElement {
    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private final String id;
    private final String title;
    private final Anchor defaultAnchor;
    private final int defaultX;
    private final int defaultY;
    private final boolean defaultVisible;
    private boolean shown;

    public final SettingGroup settings = new SettingGroup("Settings");
    public Anchor anchor;
    public int offsetX;
    public int offsetY;
    public double scale = 1.0;
    public boolean visible;
    protected int width = 10;
    protected int height = 10;

    protected HudElement(String id, String title, Anchor anchor, int x, int y, boolean visibleByDefault) {
        this.id = id;
        this.title = title;
        this.defaultAnchor = anchor;
        this.defaultX = x;
        this.defaultY = y;
        this.defaultVisible = visibleByDefault;
        resetAll();
    }

    public final String id() { return id; }
    public final String title() { return title; }
    public final int width() { return width; }
    public final int height() { return height; }

    /** Draw in local coordinates and set width / height. */
    public abstract void render(HudContext c);

    /** Elements that reveal information about the world are locked like the modules. */
    public boolean requiresPermission() { return false; }

    protected void onShown() { }
    protected void onHidden() { }

    final void setShown(boolean value) {
        if (value == shown) return;
        shown = value;
        if (value) onShown();
        else onHidden();
    }

    public final void resetPosition() {
        anchor = defaultAnchor;
        offsetX = defaultX;
        offsetY = defaultY;
        scale = 1.0;
    }

    public final void resetAll() {
        resetPosition();
        visible = defaultVisible;
        settings.resetAll();
    }

    public final Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("anchor", anchor.name());
        m.put("offsetX", (long) offsetX);
        m.put("offsetY", (long) offsetY);
        m.put("scale", scale);
        m.put("visible", visible);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Setting<?> s : settings) values.put(s.id(), s.toJson());
        m.put("settings", values);
        return m;
    }

    public final void load(Map<String, Object> m) {
        try {
            anchor = Anchor.valueOf(Json.str(m.get("anchor"), anchor.name()));
        } catch (IllegalArgumentException ignored) {
            anchor = defaultAnchor;
        }
        offsetX = (int) Json.lng(m.get("offsetX"), offsetX);
        offsetY = (int) Json.lng(m.get("offsetY"), offsetY);
        scale = Math.max(0.5, Math.min(3.0, Json.num(m.get("scale"), scale)));
        visible = Json.bool(m.get("visible"), visible);
        Map<String, Object> values = Json.map(m.get("settings"));
        for (Setting<?> s : settings) {
            Object v = values.get(s.id());
            if (v != null) s.fromJson(v);
        }
    }
}
