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

package studio.draco.utility.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import studio.draco.utility.Draco;
import studio.draco.utility.core.config.Json;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;
import studio.draco.utility.input.KeybindManager;
import studio.draco.utility.input.Keys;
import studio.draco.utility.modules.misc.ClientSettings;
import studio.draco.utility.platform.L;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Click GUI: one draggable panel per category (plus Favorites and search Results). Left click
 * toggles a module, right click opens its settings, middle click favorites it. Settings are drawn
 * from the Setting API, so new modules need no GUI code.
 */
public final class ClickGui extends Screen {
    private static final int PW = 122;
    private static final int HEADER = 16;
    private static final int ROW = 14;
    private static final int SROW = 13;
    private static final int[] PALETTE = {0xFFFFFF, 0xFF3333, 0xFF9900, 0xFFD400, 0x33DD55, 0x33CCFF,
            0x4FA3FF, 0xAA66FF, 0xFF66CC, 0x888888};

    // Layout, persisted in gui.json.
    private static final Map<String, int[]> POSITIONS = new LinkedHashMap<>();
    private static final Set<String> COLLAPSED = new HashSet<>();
    private static final Set<String> EXPANDED = new HashSet<>();
    private static final Map<String, Integer> SCROLL = new HashMap<>();

    private interface Click { void on(int button, double mx, double my); }
    private interface Drag { void on(double mx); }

    private record Hit(int x1, int y1, int x2, int y2, Click click, Drag drag) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }

    private record Panel(String title, List<Module> modules) { }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, int[]> panelRects = new HashMap<>();
    private final Map<Object, Float> hover = new HashMap<>();
    private final StringBuilder editBuffer = new StringBuilder();
    private String search = "";
    private Module bindListening;
    private Setting.Key keyListening;
    private Setting.Text editing;
    private String dragPanel;
    private int dragDx;
    private int dragDy;
    private Drag activeDrag;
    private String tooltip;
    private Theme theme;

    public ClickGui() {
        super(Text.literal("Draco Utility"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Draco.get().config.flush();
    }

    // ------------------------------------------------------------ persistence

    public static Map<String, Object> saveLayout() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> pos = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : POSITIONS.entrySet()) pos.put(e.getKey(), List.of((long) e.getValue()[0], (long) e.getValue()[1]));
        out.put("positions", pos);
        out.put("collapsed", new ArrayList<Object>(COLLAPSED));
        out.put("expanded", new ArrayList<Object>(EXPANDED));
        return out;
    }

    public static void loadLayout(Map<String, Object> data) {
        resetLayout();
        for (Map.Entry<String, Object> e : Json.map(data.get("positions")).entrySet()) {
            List<Object> xy = Json.list(e.getValue());
            if (xy.size() == 2) POSITIONS.put(e.getKey(), new int[]{(int) Json.lng(xy.get(0), 0), (int) Json.lng(xy.get(1), 0)});
        }
        for (Object o : Json.list(data.get("collapsed"))) if (o instanceof String s) COLLAPSED.add(s);
        for (Object o : Json.list(data.get("expanded"))) if (o instanceof String s) EXPANDED.add(s);
    }

    public static void resetLayout() {
        POSITIONS.clear();
        COLLAPSED.clear();
        EXPANDED.clear();
        SCROLL.clear();
    }

    private static void layoutChanged() {
        Draco d = Draco.maybe();
        if (d != null) d.config.markDirty();
    }

    // ---------------------------------------------------------------- helpers

    private void text(DrawContext ctx, String s, int x, int y, int argb) {
        ctx.drawText(textRenderer, s, x, y, argb, false);
    }

    private int tw(String s) {
        return textRenderer.getWidth(s);
    }

    private static String pretty(String s) {
        String t = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private void addHit(int x1, int y1, int x2, int y2, int clipTop, int clipBottom, Click click, Drag drag) {
        int top = Math.max(y1, clipTop);
        int bottom = Math.min(y2, clipBottom);
        if (bottom > top) hits.add(new Hit(x1, top, x2, bottom, click, drag));
    }

    /** Smooth hover amount 0..1 per object. */
    private float hoverAmount(Object key, boolean hovered) {
        ClientSettings cs = Draco.get().settings;
        if (cs == null || !cs.animations.get()) return hovered ? 1f : 0f;
        float cur = hover.getOrDefault(key, 0f);
        cur += ((hovered ? 1f : 0f) - cur) * 0.3f;
        hover.put(key, cur);
        return cur;
    }

    private static int withAlpha(int argb, float factor) {
        int a = Math.min(255, Math.max(0, (int) (((argb >>> 24) & 0xFF) * factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private int moduleHeight(Module m) {
        int h = ROW;
        if (!EXPANDED.contains(m.id())) return h;
        for (SettingGroup g : m.groups()) {
            int n = 0;
            for (Setting<?> s : g) if (s.isVisible()) n++;
            if (n == 0) continue;
            if (g != m.general) h += SROW;
            h += n * SROW;
        }
        return h + (m.isToggleable() ? 2 * SROW : SROW);
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Draco d = Draco.get();
        ClientSettings cs = d.settings;
        theme = Theme.of(cs.theme.get(), cs.accent.get());
        ctx.fill(0, 0, width, height, 0x66000000);
        hits.clear();
        panelRects.clear();
        tooltip = null;

        drawTopBar(ctx, mx, my, d);
        List<Panel> panels = buildPanels(d);
        for (int i = 0; i < panels.size(); i++) drawPanel(ctx, panels.get(i), i, mx, my);

        {
            studio.draco.utility.core.policy.ServerProfile profile = d.policy.active();
            String where = studio.draco.utility.core.policy.PolicyManager.SINGLEPLAYER_KEY.equals(d.policy.current())
                    ? L.t("singleplayer", "chơi đơn") : String.valueOf(d.policy.current());
            String msg = L.t("Profile: ", "Hồ sơ: ") + profile.id() + " (" + where + ")  -  "
                    + L.t("change with .policy", "đổi bằng .policy");
            text(ctx, msg, (width - tw(msg)) / 2, height - 14, 0xFFAAAAAA);
        }
        if (tooltip != null) {
            int w = tw(tooltip) + 8;
            int x = Math.min(mx + 10, width - w - 2);
            int y = Math.min(my + 12, height - 14);
            ctx.fill(x, y, x + w, y + 13, theme.tooltipBg);
            text(ctx, tooltip, x + 4, y + 3, theme.text);
        }
    }

    private int button(DrawContext ctx, int x, int y, String label, int mx, int my, Click click) {
        int w = tw(label) + 12;
        float h = hoverAmount(label + x, inside(mx, my, x, y, x + w, y + 14));
        ctx.fill(x, y, x + w, y + 14, theme.header);
        ctx.fill(x, y, x + w, y + 14, withAlpha(theme.hover, h));
        ctx.fill(x, y + 13, x + w, y + 14, theme.accent);
        text(ctx, label, x + 6, y + 3, theme.text);
        addHit(x, y, x + w, y + 14, 0, height, click, null);
        return w;
    }

    private void drawTopBar(DrawContext ctx, int mx, int my, Draco d) {
        int sw = 170;
        int sx = Math.max(4, width / 2 - sw / 2 - 90);
        int sy = 6;
        ctx.fill(sx - 1, sy - 1, sx + sw + 1, sy + 15, theme.accent);
        ctx.fill(sx, sy, sx + sw, sy + 14, theme.header);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = search.isEmpty() ? L.t("Search modules and settings...", "Tìm module và cài đặt...") : search + (blink ? "_" : "");
        text(ctx, shown, sx + 4, sy + 3, search.isEmpty() ? theme.textDim : theme.text);

        int x = sx + sw + 6;
        x += button(ctx, x, sy, "HUD", mx, my, (b, cx, cy) -> client.setScreen(new HudEditorScreen(this))) + 4;
        x += button(ctx, x, sy, L.t("Tools", "Công cụ"), mx, my, (b, cx, cy) -> client.setScreen(new ToolsScreen(this))) + 4;
        x += button(ctx, x, sy, L.t("Map", "Bản đồ"), mx, my, (b, cx, cy) -> client.setScreen(new MapScreen(this))) + 4;
        button(ctx, x, sy, L.t("Profile: ", "Hồ sơ: ") + d.profiles.active(), mx, my, (b, cx, cy) -> cycleProfile(b == 1 ? -1 : 1));
    }

    private void cycleProfile(int delta) {
        Draco d = Draco.get();
        List<String> names = d.profiles.list();
        if (names.size() < 2) {
            d.notifications.info("Profiles", "Create another profile with .profile create <name>");
            return;
        }
        int i = names.indexOf(d.profiles.active());
        String next = names.get(((i + delta) % names.size() + names.size()) % names.size());
        try {
            d.profiles.switchTo(next);
            d.notifications.success("Profile", "Switched to " + next);
        } catch (RuntimeException e) {
            d.notifications.error("Profile", e.getMessage());
        }
    }

    private List<Panel> buildPanels(Draco d) {
        List<Panel> out = new ArrayList<>();
        if (!search.isEmpty()) {
            out.add(new Panel("Results", d.modules.search(search)));
            return out;
        }
        List<Module> favorites = new ArrayList<>();
        for (Module m : d.modules.all()) if (m.isFavorite()) favorites.add(m);
        if (!favorites.isEmpty()) out.add(new Panel("Favorites", favorites));
        for (ModuleCategory c : ModuleCategory.values()) {
            List<Module> list = d.modules.byCategory(c);
            if (!list.isEmpty()) out.add(new Panel(c.title(), list));
        }
        return out;
    }

    private int[] defaultPosition(int index) {
        int perRow = Math.max(1, (width - 8) / (PW + 6));
        return new int[]{8 + (index % perRow) * (PW + 6), 30 + (index / perRow) * 150};
    }

    private void drawPanel(DrawContext ctx, Panel panel, int index, int mx, int my) {
        final String title = panel.title();
        final int[] pos = POSITIONS.computeIfAbsent(title, k -> defaultPosition(index));
        pos[0] = Math.max(0, Math.min(pos[0], width - PW));
        pos[1] = Math.max(0, Math.min(pos[1], height - HEADER));
        int x = pos[0];
        int y = pos[1];
        boolean collapsed = COLLAPSED.contains(title);

        ctx.fill(x, y, x + PW, y + HEADER, theme.header);
        ctx.fill(x, y + HEADER - 1, x + PW, y + HEADER, theme.accent);
        text(ctx, title, x + 6, y + 4, theme.text);
        text(ctx, collapsed ? "+" : "-", x + PW - 10, y + 4, theme.textDim);
        addHit(x, y, x + PW, y + HEADER, y, y + HEADER, (b, hx, hy) -> {
            if (b == 0) {
                dragPanel = title;
                dragDx = (int) hx - pos[0];
                dragDy = (int) hy - pos[1];
            } else if (b == 1) {
                if (!COLLAPSED.remove(title)) COLLAPSED.add(title);
                layoutChanged();
            }
        }, null);
        if (collapsed) return;

        int top = y + HEADER;
        int total = 0;
        for (Module m : panel.modules()) total += moduleHeight(m);
        if (total == 0) return;
        int visible = Math.min(total, Math.max(40, height - top - 6));
        int scroll = Math.max(0, Math.min(SCROLL.getOrDefault(title, 0), total - visible));
        SCROLL.put(title, scroll);
        panelRects.put(title, new int[]{x, top, visible, total});

        ctx.fill(x, top, x + PW, top + visible, theme.panelBg);
        ctx.enableScissor(x, top, x + PW, top + visible);
        int cy = top - scroll;
        for (Module m : panel.modules()) cy += moduleRow(ctx, m, x, cy, top, top + visible, mx, my);
        ctx.disableScissor();

        if (total > visible) {
            int thumbH = Math.max(10, visible * visible / total);
            int thumbY = top + (visible - thumbH) * scroll / (total - visible);
            ctx.fill(x + PW - 2, thumbY, x + PW, thumbY + thumbH, theme.accent);
        }
    }

    // ----------------------------------------------------------------- modules

    private int moduleRow(DrawContext ctx, Module m, int x, int cy, int clipTop, int clipBottom, int mx, int my) {
        int total = moduleHeight(m);
        boolean expanded = EXPANDED.contains(m.id());
        if (cy + total > clipTop && cy < clipBottom) {
            boolean over = inside(mx, my, x, Math.max(cy, clipTop), x + PW, Math.min(cy + ROW, clipBottom));
            if (m.isEnabled()) ctx.fill(x, cy, x + PW, cy + ROW, m.isRunning() ? theme.on : withAlpha(theme.on, 0.45f));
            else ctx.fill(x, cy, x + PW, cy + ROW, theme.off);
            ctx.fill(x, cy, x + PW, cy + ROW, withAlpha(theme.hover, hoverAmount(m, over)));
            int color = m.blockedReason() != null && !m.isRunning() ? 0xFFFF7777
                    : m.isEnabled() && !m.isRunning() ? 0xFFFFCC66 : theme.text;
            text(ctx, m.name(), x + 6, cy + 3, color);
            String right = (m.isFavorite() ? "*" : "") + (m.keybind() >= 0 ? " [" + KeybindManager.keyName(m.keybind()) + "]" : "")
                    + (expanded ? " v" : " >");
            text(ctx, right, x + PW - 4 - tw(right), cy + 3, theme.textDim);
            if (over) tooltip = m.blockedReason() != null ? m.blockedReason() : m.description();

            addHit(x, cy, x + PW, cy + ROW, clipTop, clipBottom, (b, hx, hy) -> {
                if (b == 0 && m.isToggleable()) Draco.get().toggle(m);
                else if (b == 0 || b == 1) {
                    if (!EXPANDED.remove(m.id())) EXPANDED.add(m.id());
                    layoutChanged();
                } else if (b == 2) m.setFavorite(!m.isFavorite());
            }, null);
        }
        if (expanded) drawSettings(ctx, m, x, cy + ROW, clipTop, clipBottom, mx, my);
        return total;
    }

    private void drawSettings(DrawContext ctx, Module m, int x, int cy, int clipTop, int clipBottom, int mx, int my) {
        for (SettingGroup g : m.groups()) {
            List<Setting<?>> visible = new ArrayList<>();
            for (Setting<?> s : g) if (s.isVisible()) visible.add(s);
            if (visible.isEmpty()) continue;
            if (g != m.general) {
                if (cy + SROW > clipTop && cy < clipBottom) text(ctx, g.name(), x + 6, cy + 2, theme.accent);
                cy += SROW;
            }
            for (Setting<?> s : visible) {
                if (cy + SROW > clipTop && cy < clipBottom) settingRow(ctx, s, x, cy, clipTop, clipBottom, mx, my);
                cy += SROW;
            }
        }
        if (cy + SROW > clipTop && cy < clipBottom) bindRow(ctx, m, x, cy, clipTop, clipBottom, mx, my);
        cy += SROW;
        if (m.isToggleable()) {
            if (cy + SROW > clipTop && cy < clipBottom) {
                boolean over = inside(mx, my, x, Math.max(cy, clipTop), x + PW, Math.min(cy + SROW, clipBottom));
                ctx.fill(x, cy, x + PW, cy + SROW, withAlpha(theme.hover, hoverAmount(m.id() + "reset", over)));
                text(ctx, L.t("Reset settings", "Đặt lại cài đặt"), x + 6, cy + 2, theme.textDim);
                addHit(x, cy, x + PW, cy + SROW, clipTop, clipBottom, (b, hx, hy) -> {
                    for (SettingGroup group : m.groups()) group.resetAll();
                }, null);
            }
        }
    }

    private void bindRow(DrawContext ctx, Module m, int x, int cy, int clipTop, int clipBottom, int mx, int my) {
        boolean over = inside(mx, my, x, Math.max(cy, clipTop), x + PW, Math.min(cy + SROW, clipBottom));
        ctx.fill(x, cy, x + PW, cy + SROW, withAlpha(theme.hover, hoverAmount(m.id() + "bind", over)));
        String value = bindListening == m ? L.t("press a key...", "bấm một phím...") : KeybindManager.keyName(m.keybind())
                + (m.keybind() >= 0 ? " (" + pretty(m.bindMode().name()) + ")" : "");
        text(ctx, "Bind", x + 6, cy + 2, theme.textDim);
        text(ctx, value, x + PW - 4 - tw(value), cy + 2, bindListening == m ? theme.accent : theme.text);
        if (over) tooltip = L.t("Click: set key. Right click: clear. Middle click: toggle / hold.",
                "Click: gán phím. Chuột phải: xóa. Chuột giữa: bật-tắt / giữ.");
        addHit(x, cy, x + PW, cy + SROW, clipTop, clipBottom, (b, hx, hy) -> {
            if (b == 0) {
                bindListening = m;
                keyListening = null;
            } else if (b == 1) {
                m.setKeybind(-1);
            } else if (b == 2) {
                m.setBindMode(m.bindMode() == Module.BindMode.TOGGLE ? Module.BindMode.HOLD : Module.BindMode.TOGGLE);
            }
        }, null);
    }

    // ---------------------------------------------------------------- settings

    private void settingRow(DrawContext ctx, Setting<?> s, int x, int cy, int clipTop, int clipBottom, int mx, int my) {
        boolean over = inside(mx, my, x, Math.max(cy, clipTop), x + PW, Math.min(cy + SROW, clipBottom));
        ctx.fill(x, cy, x + PW, cy + SROW, withAlpha(theme.hover, hoverAmount(s, over)));
        if (over && !s.description().isEmpty()) tooltip = s.description();

        String value = s.valueToString();
        Click click = null;
        Drag drag = null;

        switch (s.type()) {
            case BOOL -> {
                Setting.Bool b = (Setting.Bool) s;
                if (b.get()) ctx.fill(x + 2, cy, x + 4, cy + SROW, theme.accent);
                value = b.get() ? "ON" : "OFF";
                click = (btn, hx, hy) -> { if (btn <= 1) b.set(!b.get()); };
            }
            case INT -> {
                Setting.Int i = (Setting.Int) s;
                double frac = (double) (i.get() - i.min()) / Math.max(1, i.max() - i.min());
                ctx.fill(x + 2, cy + SROW - 3, x + 2 + (int) ((PW - 4) * frac), cy + SROW - 1, theme.accent);
                final int sx = x;
                drag = mouseX -> {
                    double f = Math.max(0, Math.min(1, (mouseX - (sx + 2)) / (PW - 4)));
                    i.set((int) Math.round(i.min() + f * (i.max() - i.min())));
                };
                click = (btn, hx, hy) -> { if (btn == 1) i.reset(); };
            }
            case DOUBLE -> {
                Setting.Dbl dd = (Setting.Dbl) s;
                double frac = (dd.get() - dd.min()) / Math.max(1e-9, dd.max() - dd.min());
                ctx.fill(x + 2, cy + SROW - 3, x + 2 + (int) ((PW - 4) * frac), cy + SROW - 1, theme.accent);
                value = String.format(Locale.ROOT, "%.2f", dd.get());
                final int sx = x;
                drag = mouseX -> {
                    double f = Math.max(0, Math.min(1, (mouseX - (sx + 2)) / (PW - 4)));
                    dd.set(Math.round((dd.min() + f * (dd.max() - dd.min())) * 100.0) / 100.0);
                };
                click = (btn, hx, hy) -> { if (btn == 1) dd.reset(); };
            }
            case ENUM -> {
                Setting.Choice<?> c = (Setting.Choice<?>) s;
                value = pretty(c.get().name());
                click = (btn, hx, hy) -> { if (btn == 1) c.previous(); else c.next(); };
            }
            case STRING -> {
                Setting.Text t = (Setting.Text) s;
                if (editing == t) {
                    boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                    value = editBuffer + (blink ? "_" : "");
                }
                click = (btn, hx, hy) -> {
                    if (btn == 0) {
                        editing = t;
                        editBuffer.setLength(0);
                        editBuffer.append(t.get());
                    }
                };
            }
            case COLOR -> {
                Setting.Color col = (Setting.Color) s;
                ctx.fill(x + PW - 14, cy + 2, x + PW - 4, cy + SROW - 2, 0xFF000000 | col.get());
                value = "";
                click = (btn, hx, hy) -> col.set(nextColor(col.get(), btn == 1 ? -1 : 1));
            }
            case KEY -> {
                Setting.Key k = (Setting.Key) s;
                value = keyListening == k ? L.t("press a key...", "bấm một phím...") : KeybindManager.keyName(k.get());
                click = (btn, hx, hy) -> {
                    if (btn == 0) {
                        keyListening = k;
                        bindListening = null;
                    } else if (btn == 1) {
                        k.set(-1);
                    }
                };
            }
        }

        int valueWidth = tw(value);
        String name = textRenderer.trimToWidth(s.name(), PW - valueWidth - 16);
        text(ctx, name, x + 6, cy + 2, theme.textDim);
        if (!value.isEmpty()) text(ctx, value, x + PW - 4 - valueWidth, cy + 2, theme.text);
        if (click != null) addHit(x, cy, x + PW, cy + SROW, clipTop, clipBottom, click, drag);
    }

    private static int nextColor(int current, int delta) {
        int idx = -1;
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == current) idx = i;
        if (idx < 0) return PALETTE[delta > 0 ? 0 : PALETTE.length - 1];
        return PALETTE[((idx + delta) % PALETTE.length + PALETTE.length) % PALETTE.length];
    }

    // ------------------------------------------------------------------- input

    private void commitEdit() {
        if (editing != null) {
            editing.set(editBuffer.toString());
            editing = null;
        }
    }

    private void setListenedKey(int code) {
        if (keyListening != null) {
            keyListening.set(code);
            keyListening = null;
        } else if (bindListening != null) {
            Module m = bindListening;
            List<Module> clash = KeybindManager.conflicts(Draco.get().modules.all(), code, m);
            m.setKeybind(code);
            if (!clash.isEmpty()) {
                Draco.get().notifications.warning("Keybind", KeybindManager.keyName(code) + " is also used by " + clash.get(0).name());
            }
            bindListening = null;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        commitEdit();
        if (keyListening != null || bindListening != null) {
            if (button >= 2) {
                setListenedKey(KeybindManager.MOUSE_BASE + button);
                return true;
            }
            keyListening = null;
            bindListening = null;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mx, my)) continue;
            h.click().on(button, mx, my);
            if (h.drag() != null && button == 0) {
                activeDrag = h.drag();
                activeDrag.on(mx);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPanel != null) {
            int[] pos = POSITIONS.get(dragPanel);
            if (pos != null) {
                pos[0] = (int) mx - dragDx;
                pos[1] = (int) my - dragDy;
            }
            return true;
        }
        if (activeDrag != null) {
            activeDrag.on(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragPanel != null) layoutChanged();
        dragPanel = null;
        activeDrag = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        for (Map.Entry<String, int[]> e : panelRects.entrySet()) {
            int[] r = e.getValue();
            if (inside(mx, my, r[0], r[1], r[0] + PW, r[1] + r[2])) {
                SCROLL.merge(e.getKey(), (int) (-vertical * 14), Integer::sum);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyListening != null || bindListening != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                keyListening = null;
                bindListening = null;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                setListenedKey(-1);
            } else {
                setListenedKey(keyCode);
            }
            return true;
        }
        if (editing != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) commitEdit();
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) editing = null;
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && editBuffer.length() > 0) editBuffer.setLength(editBuffer.length() - 1);
            else if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown()) {
                String clip = client.keyboard.getClipboard();
                if (clip != null) editBuffer.append(clip, 0, Math.min(clip.length(), Math.max(0, editing.maxLength() - editBuffer.length())));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.isEmpty()) search = "";
            else close();
            return true;
        }
        if (Keys.gui != null && Keys.gui.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr < 32 || chr == 127) return false;
        if (editing != null) {
            if (editBuffer.length() < editing.maxLength()) editBuffer.append(chr);
            return true;
        }
        if (keyListening == null && bindListening == null && search.length() < 24) {
            search += chr;
            return true;
        }
        return false;
    }
}
