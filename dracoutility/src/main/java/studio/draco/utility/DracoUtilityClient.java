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

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class DracoUtilityClient implements ClientModInitializer {
    /** Rebindable in Options > Controls > "Draco Utility". Default: Right Shift. */
    public static KeyBinding menuKey;
    /** Opens the large map. Default: M. */
    public static KeyBinding mapKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        History.load();
        Waypoints.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dracoutility.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.dracoutility"));
        mapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dracoutility.map", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.dracoutility"));

        WorldOverlay.register();
        Hud.register();

        ClientTickEvents.END_CLIENT_TICK.register(DracoUtilityClient::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.save());
    }

    private static void onTick(MinecraftClient mc) {
        while (menuKey.wasPressed()) {
            if (mc.player != null && mc.currentScreen == null) {
                mc.setScreen(new UtilityScreen());
            }
        }
        while (mapKey.wasPressed()) {
            if (mc.player != null && mc.currentScreen == null) {
                mc.setScreen(new MapScreen());
            }
        }
        Features.tick(mc);
        MiniMap.tick(mc);
    }
}
