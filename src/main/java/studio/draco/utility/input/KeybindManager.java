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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Polls module keybinds. Codes below 1000 are GLFW keyboard keys; 1000 + n is mouse button n.
 * Binds are ignored while a screen is open so typing in the GUI never toggles modules.
 */
public final class KeybindManager {
    public static final int MOUSE_BASE = 1000;

    private final Map<Module, Boolean> previous = new IdentityHashMap<>();

    public void tick(MinecraftClient mc, List<Module> modules) {
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();
        boolean screenOpen = mc.currentScreen != null;
        for (Module m : modules) {
            int key = m.keybind();
            if (key < 0) {
                previous.remove(m);
                continue;
            }
            boolean down = !screenOpen && isDown(handle, key);
            boolean was = previous.getOrDefault(m, false);
            previous.put(m, down);
            if (down == was) continue;
            if (m.bindMode() == Module.BindMode.HOLD) {
                if (down && !m.isEnabled()) Draco.get().toggle(m);
                else if (!down && m.isEnabled()) Draco.get().toggle(m);
            } else if (down) {
                Draco.get().toggle(m);
            }
        }
    }

    public static boolean isDown(long handle, int key) {
        if (key >= MOUSE_BASE) return GLFW.glfwGetMouseButton(handle, key - MOUSE_BASE) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(handle, key);
    }

    public static String keyName(int code) {
        if (code < 0) return "None";
        if (code >= MOUSE_BASE) return "Mouse " + (code - MOUSE_BASE + 1);
        return InputUtil.fromKeyCode(code, -1).getLocalizedText().getString();
    }

    /** Other modules that already use this key. */
    public static List<Module> conflicts(List<Module> modules, int key, Module except) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m != except && m.keybind() == key && key >= 0) out.add(m);
        return out;
    }
}
