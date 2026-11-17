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

package studio.draco.utility.platform;

import net.minecraft.client.MinecraftClient;
import studio.draco.utility.Draco;
import studio.draco.utility.modules.misc.ClientSettings;

/** Minimal localization: pick the English or the Vietnamese text. */
public final class L {
    private L() {}

    public static boolean vietnamese() {
        Draco d = Draco.maybe();
        ClientSettings cs = d == null ? null : d.modules.get(ClientSettings.class);
        ClientSettings.Language lang = cs == null ? ClientSettings.Language.AUTO : cs.language.get();
        if (lang == ClientSettings.Language.VIETNAMESE) return true;
        if (lang == ClientSettings.Language.ENGLISH) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.options != null && mc.options.language != null && mc.options.language.startsWith("vi");
    }

    public static String t(String english, String vietnamese) {
        return vietnamese() ? vietnamese : english;
    }
}
