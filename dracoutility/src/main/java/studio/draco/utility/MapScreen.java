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
import studio.draco.utility.Waypoints.Waypoint;

import java.util.List;

/** Large map: zoom, click to mark a coordinate, name and save waypoints, guide or teleport to them. */
public class MapScreen extends Screen {
    private static final int CELL = 3;
    private static final int[] ZOOMS = {1, 2, 4, 8};
    private static final int ROWS = 4;
    private static int zoomIdx = 0;
    private static int listOffset = 0;

    private final MiniMap.Grid grid = new MiniMap.Grid();
    private final int mapX = 8;
    private final int mapY = 22;
    private int cells;
    private int timer;
    private boolean ok;
    private TextFieldWidget nameField;
    private boolean hasPending;
    private int pendingX, pendingY, pendingZ;
    private String status = "";

    public MapScreen() {
        super(Text.literal("Bản đồ"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int mapSize() {
        return cells * CELL;
    }

    @Override
    protected void init() {
        ok = Access.allowed(client);
        cells = Math.max(16, Math.min(height - 40, width - 190) / CELL);
        if (ok) resample();

        int px = mapX + mapSize() + 10;
        int pw = Math.max(110, width - px - 8);
        int y = mapY;

        int half = (pw - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom -"), b -> changeZoom(1))
                .dimensions(px, y, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom +"), b -> changeZoom(-1))
                .dimensions(px + half + 4, y, half, 20).build());

        nameField = new TextFieldWidget(textRenderer, px, y + 26, pw, 20, Text.literal("Tên điểm"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("Tên điểm đánh dấu"));
        nameField.setEditable(ok);
        addDrawableChild(nameField);

        ButtonWidget save = ButtonWidget.builder(Text.literal("Lưu điểm"), b -> saveWaypoint())
                .dimensions(px, y + 50, pw, 20).build();
        save.active = ok;
        addDrawableChild(save);

        List<Waypoint> list = Waypoints.all();
        String dim = ok ? Waypoints.dim(client.world) : "";
        int row = 0;
        int seen = 0;
        for (Waypoint wp : list) {
            if (!wp.dim().equals(dim)) continue;
            if (seen++ < listOffset) continue;
            if (row >= ROWS) break;
            int ry = y + 76 + row * 22;
            final Waypoint fw = wp;
            String label = wp.name().length() > 14 ? wp.name().substring(0, 12) + ".." : wp.name();
            ButtonWidget guideBtn = ButtonWidget.builder(Text.literal(label), b -> guideTo(fw))
                    .dimensions(px, ry, pw - 52, 20).build();
            ButtonWidget tpBtn = ButtonWidget.builder(Text.literal("TP"), b -> teleportTo(fw))
                    .dimensions(px + pw - 50, ry, 24, 20).build();
            ButtonWidget delBtn = ButtonWidget.builder(Text.literal("X"), b -> {
                Waypoints.remove(fw);
                clearAndInit();
            }).dimensions(px + pw - 24, ry, 24, 20).build();
            guideBtn.active = ok;
            tpBtn.active = ok;
            addDrawableChild(guideBtn);
            addDrawableChild(tpBtn);
            addDrawableChild(delBtn);
            row++;
        }

        int sy = y + 76 + ROWS * 22 + 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> {
            listOffset = Math.max(0, listOffset - 1);
            clearAndInit();
        }).dimensions(px, sy, half, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> {
            listOffset++;
            clearAndInit();
        }).dimensions(px + half + 4, sy, half, 18).build());
    }

    private void resample() {
        if (client.world == null || client.player == null) return;
        MiniMap.sample(client.world, client.player, grid, cells, ZOOMS[zoomIdx]);
    }

    private void changeZoom(int delta) {
        zoomIdx = Math.max(0, Math.min(ZOOMS.length - 1, zoomIdx + (delta > 0 ? 1 : -1)));
        if (ok) resample();
    }

    private void saveWaypoint() {
        if (!ok || client.player == null || client.world == null) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "Điểm " + (Waypoints.all().size() + 1);
        int x = hasPending ? pendingX : client.player.getBlockX();
        int y = hasPending ? pendingY : client.player.getBlockY();
        int z = hasPending ? pendingZ : client.player.getBlockZ();
        Waypoints.add(new Waypoint(name, x, y, z, Waypoints.dim(client.world)));
        status = "Đã lưu: " + name + " (" + x + ", " + y + ", " + z + ")";
        hasPending = false;
        nameField.setText("");
        clearAndInit();
    }

    private void guideTo(Waypoint wp) {
        Features.guide = true;
        Features.guideType = Targets.Kind.WAYPOINT.ordinal();
        Features.guideWaypoint = wp.name();
        status = "Đang dẫn đường tới " + wp.name();
    }

    private void teleportTo(Waypoint wp) {
        if (!ok || client.player == null) return;
        client.player.networkHandler.sendChatCommand("tp @s " + wp.x() + " " + wp.y() + " " + wp.z());
        status = "Đã gửi lệnh tp tới " + wp.name();
    }

    @Override
    public void tick() {
        if (ok && ++timer >= 10) {
            timer = 0;
            resample();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        changeZoom(verticalAmount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (ok && grid.colors != null && inMap(mouseX, mouseY)) {
            int i = (int) ((mouseX - mapX) / CELL);
            int j = (int) ((mouseY - mapY) / CELL);
            pendingX = grid.minX + i * grid.bpc + grid.bpc / 2;
            pendingZ = grid.minZ + j * grid.bpc + grid.bpc / 2;
            pendingY = grid.ys[j * grid.cells + i] + 1;
            hasPending = true;
            if (nameField.getText().isEmpty()) {
                nameField.setText("Điểm " + (Waypoints.all().size() + 1));
            }
            status = "Đã chọn: " + pendingX + ", " + pendingY + ", " + pendingZ + " - bấm Lưu điểm";
            return true;
        }
        return false;
    }

    private boolean inMap(double mx, double my) {
        return mx >= mapX && my >= mapY && mx < mapX + mapSize() && my < mapY + mapSize();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(textRenderer, "Bản đồ - lăn chuột để zoom, bấm để chọn vị trí", mapX, 8, 0xFFFFFF);

        if (!ok) {
            ctx.drawTextWithShadow(textRenderer, "Bị khóa: cần singleplayer bật cheats hoặc quyền OP (cấp 2+)",
                    mapX, mapY + 4, 0xFF5555);
            return;
        }

        ctx.fill(mapX - 1, mapY - 1, mapX + mapSize() + 1, mapY + mapSize() + 1, 0xFF000000);
        if (grid.colors != null) {
            MiniMap.drawGrid(ctx, grid, mapX, mapY, CELL);
            MiniMap.drawMarkers(ctx, grid, mapX, mapY, CELL, true);

            if (hasPending) {
                int[] s = MiniMap.toScreen(grid, mapX, mapY, CELL, pendingX + 0.5, pendingZ + 0.5);
                if (s != null) {
                    ctx.fill(s[0] - 4, s[1], s[0] + 5, s[1] + 1, 0xFFFFFF00);
                    ctx.fill(s[0], s[1] - 4, s[0] + 1, s[1] + 5, 0xFFFFFF00);
                }
            }
            if (inMap(mouseX, mouseY)) {
                int i = (int) ((mouseX - mapX) / CELL);
                int j = (int) ((mouseY - mapY) / CELL);
                int wx = grid.minX + i * grid.bpc + grid.bpc / 2;
                int wz = grid.minZ + j * grid.bpc + grid.bpc / 2;
                int wy = grid.ys[j * grid.cells + i];
                ctx.drawTextWithShadow(textRenderer, wx + ", " + wy + ", " + wz + "   (" + grid.bpc + " block/ô)",
                        mapX, mapY + mapSize() + 4, 0xFFFFFF);
            }
        }
        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, status, mapX, height - 12, 0xFFFF55);
        }
    }
}
