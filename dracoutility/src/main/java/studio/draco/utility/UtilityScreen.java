/*
 * Draco Utility - client-side utility mod for Minecraft (Fabric)
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

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import studio.draco.utility.Targets.Kind;
import studio.draco.utility.Waypoints.Waypoint;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Settings menu. Two rows of tabs; each tab is one page of buttons. */
public class UtilityScreen extends Screen {
    private enum Page { GENERAL, VISUAL, GUIDE, CLICK, MAP, TELEPORT, STATS, COMMANDS }

    private static final String[] PAGE_NAMES = {
            "Chung", "Hiển thị", "Dẫn đường", "Click", "Bản đồ", "Dịch chuyển", "Chỉ số", "Lệnh"
    };
    private static final double[] FLY_SPEEDS = {0.2, 0.4, 0.6, 0.8, 1.0};
    private static final int[] HEALTH_STEPS = {20, 40, 60, 100};
    private static final int[] OUTLINE_RADII = {2, 3, 4, 6};
    private static final int[] ORE_RADII = {16, 24, 32, 48};
    private static final int[] CLICK_DELAYS = {20, 10, 5, 3, 2, 1}; // ticks between clicks
    private static final int[] MAP_SIZES = {64, 80, 96, 128};
    private static final int[] MAP_ZOOMS = {1, 2, 4};
    // {display name, effect id}
    private static final String[][] EFFECTS = {
            {"Tốc độ", "speed"}, {"Nhảy cao", "jump_boost"}, {"Sức mạnh", "strength"},
            {"Đào nhanh", "haste"}, {"Giáp", "resistance"}, {"Hồi máu", "regeneration"},
            {"Nhìn đêm", "night_vision"}
    };
    private static final Pattern COORD = Pattern.compile("[~^]?(-?\\d+(\\.\\d+)?)?");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int HISTORY_ROWS = 4;
    private static final int TOP = 68;   // first content row, below the two tab rows
    private static final int STEP = 24;

    // Remembered between openings of the menu.
    private static Page page = Page.GENERAL;
    private static String tpX = "~", tpY = "~", tpZ = "~", tpPlayer = "";
    private static String cmdText = "";
    private static int selected = -1;   // index into History.list(), -1 = none
    private static int histOffset = 0;  // scroll offset (newest first)

    private TextFieldWidget cmdField;
    private boolean ok;
    private String status = "";

    public UtilityScreen() {
        super(Text.literal("Draco Utility"));
    }

    @Override
    public boolean shouldPause() {
        return false; // keep the game running so singleplayer is not paused
    }

    @Override
    public void removed() {
        Config.save(); // persist every toggle when the menu closes
    }

    @Override
    protected void init() {
        cmdField = null;
        ok = Access.allowed(client);

        int tabW = 68, gap = 4;
        int startX = width / 2 - (tabW * 4 + gap * 3) / 2;
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            final Page target = pages[i];
            int col = i % 4;
            int rowY = i < 4 ? 18 : 40;
            ButtonWidget tab = ButtonWidget.builder(Text.literal(PAGE_NAMES[i]), b -> {
                page = target;
                status = "";
                clearAndInit();
            }).dimensions(startX + col * (tabW + gap), rowY, tabW, 20).build();
            tab.active = target != page; // current tab is shown as disabled
            addDrawableChild(tab);
        }

        switch (page) {
            case GENERAL -> initGeneral();
            case VISUAL -> initVisual();
            case GUIDE -> initGuide();
            case CLICK -> initClick();
            case MAP -> initMap();
            case TELEPORT -> initTeleport();
            case STATS -> initStats();
            case COMMANDS -> initCommands();
        }
    }

    // ------------------------------------------------------------ helpers

    private static Text toggleLabel(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "BẬT" : "TẮT")).formatted(on ? Formatting.GREEN : Formatting.RED);
    }

    private void addToggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        ButtonWidget b = ButtonWidget.builder(toggleLabel(name, get.getAsBoolean()), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(toggleLabel(name, get.getAsBoolean()));
        }).dimensions(x, y, w, 20).build();
        b.active = ok;
        addDrawableChild(b);
    }

    /** A button whose label is recomputed after each click (for cycling through values). */
    private void addCycle(int x, int y, int w, Supplier<String> label, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label.get()), btn -> {
            action.run();
            btn.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build();
        b.active = ok;
        addDrawableChild(b);
    }

    private ButtonWidget addButton(int x, int y, int w, String label, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> action.run())
                .dimensions(x, y, w, 20).build();
        b.active = ok;
        return addDrawableChild(b);
    }

    private TextFieldWidget addField(int x, int y, int w, String hint, String initial, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(hint));
        f.setMaxLength(256);
        f.setPlaceholder(Text.literal(hint));
        f.setText(initial);
        f.setChangedListener(onChange);
        f.setEditable(ok);
        return addDrawableChild(f);
    }

    private static int nextInt(int[] values, int current) {
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) idx = i;
        }
        return values[(idx + 1) % values.length];
    }

    private static double nextDouble(double[] values, double current) {
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 1e-6) idx = i;
        }
        return values[(idx + 1) % values.length];
    }

    /** Sends a command (without the leading slash). The server still enforces its own permissions. */
    private void send(String command) {
        if (!ok || client == null || client.player == null) return;
        client.player.networkHandler.sendChatCommand(command);
    }

    private int lx() { return width / 2 - 155; }
    private int rx() { return width / 2 + 5; }

    // ------------------------------------------------------------ General

    private void initGeneral() {
        int w = 150, y = TOP;
        addToggle(lx(), y, w, "Bay", () -> Features.fly, v -> Features.fly = v);
        addCycle(rx(), y, w, () -> "Tốc độ bay: " + Features.flySpeed,
                () -> Features.flySpeed = nextDouble(FLY_SPEEDS, Features.flySpeed));

        addToggle(lx(), y + STEP, w, "Auto PvP", () -> Features.autoPvp, v -> Features.autoPvp = v);
        addToggle(rx(), y + STEP, w, "PvP cả người chơi", () -> Features.pvpPlayers, v -> Features.pvpPlayers = v);

        addButton(lx(), y + 2 * STEP, w, "Creative / Survival", this::toggleCreative);
    }

    private void toggleCreative() {
        if (client == null || client.interactionManager == null) return;
        boolean creative = client.interactionManager.getCurrentGameMode().isCreative();
        send(creative ? "gamemode survival" : "gamemode creative");
    }

    // ------------------------------------------------------------ Visual

    private void initVisual() {
        int w = 150, y = TOP;
        addToggle(lx(), y, w, "ESP", () -> Features.esp, v -> Features.esp = v);
        addToggle(rx(), y, w, "X-ray", () -> Features.xray, v -> Features.xray = v);

        addToggle(lx(), y + STEP, w, "ESP thực thể", () -> Features.espEntities, v -> Features.espEntities = v);
        addToggle(rx(), y + STEP, w, "ESP vật phẩm", () -> Features.espItems, v -> Features.espItems = v);

        addToggle(lx(), y + 2 * STEP, w, "ESP quặng", () -> Features.espOres, v -> Features.espOres = v);
        addToggle(rx(), y + 2 * STEP, w, "Nhãn tên + khoảng cách", () -> Features.espLabels, v -> Features.espLabels = v);

        addToggle(lx(), y + 3 * STEP, w, "Viền X-ray", () -> Features.xrayOutline, v -> Features.xrayOutline = v);
        addCycle(rx(), y + 3 * STEP, w, () -> "Bán kính viền: " + Features.outlineRadius,
                () -> Features.outlineRadius = nextInt(OUTLINE_RADII, Features.outlineRadius));

        addToggle(lx(), y + 4 * STEP, w, "Sáng X-ray", () -> Features.xrayFullbright, v -> Features.xrayFullbright = v);
        addCycle(rx(), y + 4 * STEP, w, () -> "Lọc quặng: " + Targets.ORE_FILTER_NAMES[Features.oreFilter],
                () -> Features.oreFilter = (Features.oreFilter + 1) % Targets.ORE_FILTER_NAMES.length);
    }

    // -------------------------------------------------------------- Guide

    private void initGuide() {
        int w = 150, y = TOP;
        addToggle(lx(), y, w, "Dẫn đường", () -> Features.guide, v -> Features.guide = v);
        addCycle(rx(), y, w, () -> "Đích: " + Features.guideKind().label,
                () -> Features.guideType = (Features.guideType + 1) % Kind.values().length);

        addCycle(lx(), y + STEP, w, () -> "Lọc quặng: " + Targets.ORE_FILTER_NAMES[Features.oreFilter],
                () -> Features.oreFilter = (Features.oreFilter + 1) % Targets.ORE_FILTER_NAMES.length);
        addCycle(rx(), y + STEP, w, () -> "Bán kính quét quặng: " + Features.oreRadius,
                () -> Features.oreRadius = nextInt(ORE_RADII, Features.oreRadius));

        addToggle(lx(), y + 2 * STEP, w, "Hiện điểm đánh dấu", () -> Features.showWaypoints,
                v -> Features.showWaypoints = v);
    }

    // -------------------------------------------------------------- Click

    private void initClick() {
        int w = 150, y = TOP;
        addToggle(lx(), y, w, "Auto click", () -> Features.autoClick, v -> Features.autoClick = v);
        addCycle(rx(), y, w, () -> "Chế độ: " + (Features.clickPlace ? "Đặt khối" : "Đập / tấn công"),
                () -> Features.clickPlace = !Features.clickPlace);

        addCycle(lx(), y + STEP, w,
                () -> "Tốc độ: " + String.format("%.1f", 20.0 / Features.clickDelayTicks) + " lần/giây",
                () -> Features.clickDelayTicks = nextInt(CLICK_DELAYS, Features.clickDelayTicks));
    }

    // ---------------------------------------------------------------- Map

    private void initMap() {
        int w = 150, y = TOP;
        addToggle(lx(), y, w, "Mini map", () -> Features.minimap, v -> Features.minimap = v);
        addCycle(rx(), y, w, () -> "Kích thước: " + Features.minimapSize,
                () -> Features.minimapSize = nextInt(MAP_SIZES, Features.minimapSize));

        addCycle(lx(), y + STEP, w, () -> "Zoom: " + Features.minimapZoom + " block/ô",
                () -> Features.minimapZoom = nextInt(MAP_ZOOMS, Features.minimapZoom));
        ButtonWidget open = addButton(rx(), y + STEP, w, "Mở bản đồ lớn",
                () -> client.setScreen(new MapScreen()));
        open.active = true; // the map screen shows its own lock message

        addButton(lx(), y + 2 * STEP, w, "Đánh dấu vị trí hiện tại", this::markHere);
    }

    private void markHere() {
        if (!ok || client.player == null || client.world == null) return;
        String name = "Điểm " + (Waypoints.all().size() + 1);
        Waypoints.add(new Waypoint(name, client.player.getBlockX(), client.player.getBlockY(),
                client.player.getBlockZ(), Waypoints.dim(client.world)));
        status = "Đã lưu " + name + " - đổi tên trong bản đồ lớn";
    }

    // ----------------------------------------------------------- Teleport

    private void initTeleport() {
        int fw = 96, gap = 4;
        int x0 = width / 2 - (fw * 3 + gap * 2) / 2;
        addField(x0, TOP, fw, "X", tpX, s -> tpX = s);
        addField(x0 + fw + gap, TOP, fw, "Y", tpY, s -> tpY = s);
        addField(x0 + 2 * (fw + gap), TOP, fw, "Z", tpZ, s -> tpZ = s);
        addButton(width / 2 - 100, TOP + 26, 200, "Dịch chuyển tới tọa độ", this::teleportToCoords);

        addField(width / 2 - 100, TOP + 62, 200, "Tên người chơi", tpPlayer, s -> tpPlayer = s);
        addButton(width / 2 - 100, TOP + 88, 200, "Dịch chuyển tới người chơi", this::teleportToPlayer);
    }

    private void teleportToCoords() {
        String[] c = {tpX.trim(), tpY.trim(), tpZ.trim()};
        for (String s : c) {
            if (s.isEmpty() || !COORD.matcher(s).matches()) {
                status = "Tọa độ không hợp lệ";
                return;
            }
        }
        send("tp @s " + String.join(" ", c));
        status = "Đã gửi lệnh tp";
    }

    private void teleportToPlayer() {
        String n = tpPlayer.trim();
        if (!NAME.matcher(n).matches()) {
            status = "Tên người chơi không hợp lệ";
            return;
        }
        send("tp @s " + n);
        status = "Đã gửi lệnh tp";
    }

    // -------------------------------------------------------------- Stats

    private static Text effectLabel(int i) {
        int lvl = Features.effectLevels[i];
        return Text.literal(EFFECTS[i][0] + ": " + (lvl == 0 ? "TẮT" : "cấp " + lvl));
    }

    private void applyEffect(int i) {
        int lvl = Features.effectLevels[i];
        String id = "minecraft:" + EFFECTS[i][1];
        if (lvl == 0) {
            send("effect clear @s " + id);
        } else {
            send("effect give @s " + id + " infinite " + (lvl - 1) + " true");
        }
    }

    private void initStats() {
        int w = 150, y = TOP;

        for (int i = 0; i < EFFECTS.length; i++) {
            final int idx = i;
            int x = (i % 2 == 0) ? lx() : rx();
            int yy = y + (i / 2) * STEP;
            ButtonWidget b = ButtonWidget.builder(effectLabel(idx), btn -> {
                Features.effectLevels[idx] = (Features.effectLevels[idx] + 1) % 6;
                applyEffect(idx);
                btn.setMessage(effectLabel(idx));
            }).dimensions(x, yy, w, 20).build();
            b.active = ok;
            addDrawableChild(b);
        }

        // Max health (attribute), placed after the effects.
        int hi = EFFECTS.length;
        ButtonWidget health = ButtonWidget.builder(Text.literal("Máu tối đa: " + Features.maxHealth), btn -> {
            Features.maxHealth = nextInt(HEALTH_STEPS, Features.maxHealth);
            send("attribute @s minecraft:generic.max_health base set " + Features.maxHealth);
            btn.setMessage(Text.literal("Máu tối đa: " + Features.maxHealth));
        }).dimensions((hi % 2 == 0) ? lx() : rx(), y + (hi / 2) * STEP, w, 20).build();
        health.active = ok;
        addDrawableChild(health);

        addButton(width / 2 - 100, y + 4 * STEP + 4, 200, "Đặt lại tất cả", () -> {
            for (int i = 0; i < Features.effectLevels.length; i++) Features.effectLevels[i] = 0;
            Features.maxHealth = 20;
            send("effect clear @s");
            send("attribute @s minecraft:generic.max_health base set 20");
            clearAndInit();
        });
    }

    // ----------------------------------------------------------- Commands

    private void initCommands() {
        int x0 = width / 2 - 155;
        cmdField = addField(x0, TOP, 200, "Nhập lệnh (không cần /)", cmdText, s -> cmdText = s);
        addButton(x0 + 204, TOP, 106, "Chạy", this::runCommand);

        List<String> h = History.list();
        for (int i = 0; i < HISTORY_ROWS; i++) {
            int idx = h.size() - 1 - (i + histOffset);
            if (idx < 0) break;
            final int fi = idx;
            String text = h.get(idx);
            if (text.length() > 36) text = text.substring(0, 33) + "...";
            String label = (idx == selected ? "> /" : "/") + text;
            ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
                selected = fi;
                cmdText = History.list().get(fi);
                cmdField.setText(cmdText);
                clearAndInit();
            }).dimensions(x0, TOP + 26 + i * 22, 276, 20).build();
            addDrawableChild(b); // selecting history only fills the field; always usable
        }

        int maxOffset = Math.max(0, h.size() - HISTORY_ROWS);
        addDrawableChild(ButtonWidget.builder(Text.literal("^"), btn -> {
            histOffset = Math.max(0, histOffset - 1);
            clearAndInit();
        }).dimensions(x0 + 280, TOP + 26, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("v"), btn -> {
            histOffset = Math.min(maxOffset, histOffset + 1);
            clearAndInit();
        }).dimensions(x0 + 280, TOP + 48, 30, 20).build());

        int by = TOP + 26 + HISTORY_ROWS * 22 + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Lưu sửa"), btn -> {
            String c = cleanCommand(cmdText);
            if (selected >= 0 && !c.isEmpty()) {
                History.set(selected, c);
                status = "Đã sửa mục lịch sử";
                clearAndInit();
            }
        }).dimensions(x0, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Xóa mục"), btn -> {
            if (selected >= 0) {
                History.remove(selected);
                selected = -1;
                histOffset = 0;
                clearAndInit();
            }
        }).dimensions(x0 + 105, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Xóa hết"), btn -> {
            History.clear();
            selected = -1;
            histOffset = 0;
            clearAndInit();
        }).dimensions(x0 + 210, by, 100, 20).build());
    }

    private static String cleanCommand(String raw) {
        String c = raw.trim();
        if (c.startsWith("/")) c = c.substring(1).trim();
        return c;
    }

    private void runCommand() {
        String c = cleanCommand(cmdText);
        if (c.isEmpty() || !ok) return;
        send(c);
        History.add(c);
        selected = -1;
        histOffset = 0;
        status = "Đã gửi: /" + c;
        clearAndInit();
    }

    // ------------------------------------------------------- input/render

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (cmdField != null && cmdField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            runCommand();
            return true;
        }
        boolean typing = getFocused() instanceof TextFieldWidget tf && tf.isFocused();
        if (!typing && DracoUtilityClient.menuKey.matchesKey(keyCode, scanCode)) {
            close(); // same key closes the menu
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Clicking the minimap (when no button is under the cursor) opens the large map. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (ok && Features.minimap && MiniMap.MINI.colors != null) {
            int size = MiniMap.miniPixelSize();
            if (mouseX >= 4 && mouseX < 4 + size && mouseY >= 4 && mouseY < 4 + size) {
                client.setScreen(new MapScreen());
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Draco Utility"), width / 2, 6, 0xFFFFFF);

        if (!ok) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Bị khóa: cần singleplayer bật cheats hoặc quyền OP (cấp 2+)"),
                    width / 2, height - 26, 0xFF5555);
        }

        switch (page) {
            case TELEPORT -> context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Dùng ~ để tính theo vị trí hiện tại, ví dụ ~ ~10 ~"),
                    width / 2, TOP + 120, 0xAAAAAA);
            case GUIDE -> {
                String info = Features.guideInfo == null ? "Bật Dẫn đường và chọn đích: dây dẫn có màu của loại đích"
                        : Features.guideInfo;
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(info), width / 2, TOP + 3 * STEP + 6,
                        0xAAAAAA);
            }
            case CLICK -> context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Tốc độ áp dụng cho đánh mob và đặt khối. Tốc độ đào do game quyết định."),
                    width / 2, TOP + 2 * STEP + 6, 0xAAAAAA);
            default -> { }
        }

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 14, 0xFFFF55);
        }
    }
}
