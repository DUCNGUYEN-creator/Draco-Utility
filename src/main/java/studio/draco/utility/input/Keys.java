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

package studio.draco.utility.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** The few keys registered with the game (rebindable under Options > Controls). Module binds live in the GUI. */
public final class Keys {
    public static KeyBinding gui;
    public static KeyBinding map;
    public static KeyBinding tools;
    public static KeyBinding hud;

    private Keys() {}

    public static void register() {
        gui = reg("key.dracoutility.gui", GLFW.GLFW_KEY_RIGHT_SHIFT);
        map = reg("key.dracoutility.map", GLFW.GLFW_KEY_M);
        tools = reg("key.dracoutility.tools", GLFW.GLFW_KEY_UNKNOWN);
        hud = reg("key.dracoutility.hud", GLFW.GLFW_KEY_UNKNOWN);
    }

    private static KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, key, "category.dracoutility"));
    }
}
