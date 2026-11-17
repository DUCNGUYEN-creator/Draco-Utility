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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import studio.draco.utility.Draco;
import studio.draco.utility.platform.Access;
import studio.draco.utility.platform.L;
import studio.draco.utility.services.CommandHistory;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Action tools that are not modules: teleport, effects / max health, game mode and a command console
 * with editable history. They send real commands, so the server still checks permissions.
 */
public final class ToolsScreen extends Screen {
    private enum Page { TELEPORT, STATS, COMMANDS }

    // {English, Vietnamese, effect id}
    private static final String[][] EFFECTS = {
            {"Speed", "Tốc độ", "speed"}, {"Jump boost", "Nhảy cao", "jump_boost"}, {"Strength", "Sức mạnh", "strength"},
            {"Haste", "Đào nhanh", "haste"}, {"Resistance", "Giáp", "resistance"}, {"Regeneration", "Hồi máu", "regeneration"},
            {"Night vision", "Nhìn đêm", "night_vision"}
    };
    private static final int[] HEALTH_STEPS = {20, 40, 60, 100};
    private static final Pattern COORD = Pattern.compile("[~^]?(-?\\d+(\\.\\d+)?)?");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int HISTORY_ROWS = 4;
    private static final int TOP = 52;
    private static final int STEP = 24;

    private static Page page = Page.TELEPORT;
    private static String tpX = "~", tpY = "~", tpZ = "~", tpPlayer = "";
    private static String cmdText = "";
    private static int selected = -1;
    private static int histOffset = 0;
    private static final int[] EFFECT_LEVELS = new int[EFFECTS.length];
    private static int maxHealth = 20;

    private final Screen parent;
    private TextFieldWidget cmdField;
    private boolean ok;
    private String status = "";

    public ToolsScreen(Screen parent) {
        super(Text.literal("Draco Tools"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        Draco.get().config.flush();
        client.setScreen(parent);
    }

    @Override
    protected void init() {
        cmdField = null;
        ok = Access.hasOperator(client);

        String[] names = {L.t("Teleport", "Dịch chuyển"), L.t("Stats", "Chỉ số"), L.t("Commands", "Lệnh")};
        int tabW = 100;
        int startX = width / 2 - (tabW * 3 + 8) / 2;
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            final Page target = pages[i];
            ButtonWidget tab = ButtonWidget.builder(Text.literal(names[i]), b -> {
                page = target;
                status = "";
                clearAndInit();
            }).dimensions(startX + i * (tabW + 4), 20, tabW, 20).build();
            tab.active = target != page;
            addDrawableChild(tab);
        }
        switch (page) {
            case TELEPORT -> initTeleport();
            case STATS -> initStats();
            case COMMANDS -> initCommands();
        }
    }

    // ------------------------------------------------------------ helpers

    private ButtonWidget addButton(int x, int y, int w, String label, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> action.run()).dimensions(x, y, w, 20).build();
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
        for (int i = 0; i < values.length; i++) if (values[i] == current) idx = i;
        return values[(idx + 1) % values.length];
    }

    private void send(String command) {
        if (!ok || client == null || client.player == null) return;
        client.player.networkHandler.sendChatCommand(command);
    }

    private int lx() { return width / 2 - 155; }
    private int rx() { return width / 2 + 5; }

    // ----------------------------------------------------------- Teleport

    private void initTeleport() {
        int fw = 96, gap = 4;
        int x0 = width / 2 - (fw * 3 + gap * 2) / 2;
        addField(x0, TOP, fw, "X", tpX, s -> tpX = s);
        addField(x0 + fw + gap, TOP, fw, "Y", tpY, s -> tpY = s);
        addField(x0 + 2 * (fw + gap), TOP, fw, "Z", tpZ, s -> tpZ = s);
        addButton(width / 2 - 100, TOP + 26, 200, L.t("Teleport to coordinates", "Dịch chuyển tới tọa độ"), this::teleportToCoords);

        addField(width / 2 - 100, TOP + 62, 200, L.t("Player name", "Tên người chơi"), tpPlayer, s -> tpPlayer = s);
        addButton(width / 2 - 100, TOP + 88, 200, L.t("Teleport to player", "Dịch chuyển tới người chơi"), this::teleportToPlayer);
    }

    private void teleportToCoords() {
        String[] c = {tpX.trim(), tpY.trim(), tpZ.trim()};
        for (String s : c) {
            if (s.isEmpty() || !COORD.matcher(s).matches()) {
                status = L.t("Invalid coordinates", "Tọa độ không hợp lệ");
                return;
            }
        }
        send("tp @s " + String.join(" ", c));
        status = L.t("Teleport command sent", "Đã gửi lệnh tp");
    }

    private void teleportToPlayer() {
        String n = tpPlayer.trim();
        if (!NAME.matcher(n).matches()) {
            status = L.t("Invalid player name", "Tên người chơi không hợp lệ");
            return;
        }
        send("tp @s " + n);
        status = L.t("Teleport command sent", "Đã gửi lệnh tp");
    }

    // -------------------------------------------------------------- Stats

    private static Text effectLabel(int i) {
        int lvl = EFFECT_LEVELS[i];
        String name = L.t(EFFECTS[i][0], EFFECTS[i][1]);
        return Text.literal(name + ": " + (lvl == 0 ? L.t("OFF", "TẮT") : L.t("level ", "cấp ") + lvl));
    }

    private void applyEffect(int i) {
        int lvl = EFFECT_LEVELS[i];
        String id = "minecraft:" + EFFECTS[i][2];
        if (lvl == 0) send("effect clear @s " + id);
        else send("effect give @s " + id + " infinite " + (lvl - 1) + " true");
    }

    private void initStats() {
        int w = 150;
        for (int i = 0; i < EFFECTS.length; i++) {
            final int idx = i;
            int x = (i % 2 == 0) ? lx() : rx();
            ButtonWidget b = ButtonWidget.builder(effectLabel(idx), btn -> {
                EFFECT_LEVELS[idx] = (EFFECT_LEVELS[idx] + 1) % 6;
                applyEffect(idx);
                btn.setMessage(effectLabel(idx));
            }).dimensions(x, TOP + (i / 2) * STEP, w, 20).build();
            b.active = ok;
            addDrawableChild(b);
        }
        int hi = EFFECTS.length;
        ButtonWidget health = ButtonWidget.builder(Text.literal(L.t("Max health: ", "Máu tối đa: ") + maxHealth), btn -> {
            maxHealth = nextInt(HEALTH_STEPS, maxHealth);
            send("attribute @s minecraft:generic.max_health base set " + maxHealth);
            btn.setMessage(Text.literal(L.t("Max health: ", "Máu tối đa: ") + maxHealth));
        }).dimensions((hi % 2 == 0) ? lx() : rx(), TOP + (hi / 2) * STEP, w, 20).build();
        health.active = ok;
        addDrawableChild(health);

        addButton(lx(), TOP + 4 * STEP + 4, w, L.t("Reset all", "Đặt lại tất cả"), () -> {
            for (int i = 0; i < EFFECT_LEVELS.length; i++) EFFECT_LEVELS[i] = 0;
            maxHealth = 20;
            send("effect clear @s");
            send("attribute @s minecraft:generic.max_health base set 20");
            clearAndInit();
        });
        addButton(rx(), TOP + 4 * STEP + 4, w, "Creative / Survival", () -> {
            if (client.interactionManager == null) return;
            boolean creative = client.interactionManager.getCurrentGameMode().isCreative();
            send(creative ? "gamemode survival" : "gamemode creative");
        });
    }

    // ----------------------------------------------------------- Commands

    private void initCommands() {
        CommandHistory history = Draco.get().history;
        int x0 = width / 2 - 155;
        cmdField = addField(x0, TOP, 200, L.t("Command (no / needed)", "Nhập lệnh (không cần /)"), cmdText, s -> cmdText = s);
        addButton(x0 + 204, TOP, 106, L.t("Run", "Chạy"), this::runCommand);

        List<String> h = history.list();
        for (int i = 0; i < HISTORY_ROWS; i++) {
            int idx = h.size() - 1 - (i + histOffset);
            if (idx < 0) break;
            final int fi = idx;
            String text = h.get(idx);
            if (text.length() > 36) text = text.substring(0, 33) + "...";
            String label = (idx == selected ? "> /" : "/") + text;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
                selected = fi;
                cmdText = history.list().get(fi);
                cmdField.setText(cmdText);
                clearAndInit();
            }).dimensions(x0, TOP + 26 + i * 22, 276, 20).build());
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
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Save edit", "Lưu sửa")), btn -> {
            String c = cleanCommand(cmdText);
            if (selected >= 0 && !c.isEmpty()) {
                history.set(selected, c);
                status = L.t("History entry edited", "Đã sửa mục lịch sử");
                clearAndInit();
            }
        }).dimensions(x0, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Delete", "Xóa mục")), btn -> {
            if (selected >= 0) {
                history.remove(selected);
                selected = -1;
                histOffset = 0;
                clearAndInit();
            }
        }).dimensions(x0 + 105, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Clear all", "Xóa hết")), btn -> {
            history.clear();
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
        Draco.get().history.add(c);
        selected = -1;
        histOffset = 0;
        status = L.t("Sent: /", "Đã gửi: /") + c;
        clearAndInit();
    }

    // ------------------------------------------------------- input/render

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (cmdField != null && cmdField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            runCommand();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Draco Tools"), width / 2, 6, 0xFFFFFF);
        if (!ok) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(L.t("Locked: needs singleplayer with cheats or operator permission (level 2+)",
                            "Bị khóa: cần singleplayer bật cheats hoặc quyền OP (cấp 2+)")),
                    width / 2, height - 26, 0xFF5555);
        }
        if (page == Page.TELEPORT) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(L.t("Use ~ for relative coordinates, for example ~ ~10 ~", "Dùng ~ để tính theo vị trí hiện tại, ví dụ ~ ~10 ~")),
                    width / 2, TOP + 120, 0xAAAAAA);
        }
        if (!status.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 14, 0xFFFF55);
        }
    }
}
