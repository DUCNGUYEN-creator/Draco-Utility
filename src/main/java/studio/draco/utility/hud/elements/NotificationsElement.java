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

package studio.draco.utility.hud.elements;

import studio.draco.utility.Draco;
import studio.draco.utility.core.notification.NotificationManager;
import studio.draco.utility.hud.HudContext;
import studio.draco.utility.hud.HudElement;

import java.util.List;

/** Draws the toast notifications. */
public final class NotificationsElement extends HudElement {
    public NotificationsElement() {
        super("notifications", "Notifications", Anchor.TOP_RIGHT, 4, 100, true);
    }

    private static int levelColor(NotificationManager.Level l) {
        return switch (l) {
            case SUCCESS -> 0x55DD66;
            case WARNING -> 0xFFCC33;
            case ERROR -> 0xFF5555;
            default -> 0x4FA3FF;
        };
    }

    @Override
    public void render(HudContext c) {
        List<NotificationManager.Notification> list = Draco.get().notifications.visible();
        if (list.isEmpty() && c.editor) {
            list = List.of(new NotificationManager.Notification(NotificationManager.Level.INFO, "Notification",
                    "This is where messages appear", System.currentTimeMillis()));
        }
        width = 170;
        int y = 0;
        for (NotificationManager.Notification n : list) {
            c.fill(0, y, width, y + 22, 0xC0101418);
            c.fill(0, y, 3, y + 22, 0xFF000000 | levelColor(n.level()));
            c.text(c.font.trimToWidth(n.title(), 158), 7, y + 3, 0xFFFFFF);
            c.text(c.font.trimToWidth(n.message(), 158), 7, y + 12, 0xB0B8C0);
            y += 24;
        }
        height = Math.max(10, y - 2);
    }
}
