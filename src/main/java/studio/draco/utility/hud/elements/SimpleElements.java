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

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.MathHelper;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.hud.HudContext;
import studio.draco.utility.platform.Dim;
import studio.draco.utility.services.InfoService;
import studio.draco.utility.services.PlayerService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The small text-only HUD elements. */
public final class SimpleElements {
    private SimpleElements() {}

    public static final class Watermark extends TextElement {
        public Watermark() { super("watermark", "Watermark", Anchor.TOP_LEFT, 4, 4, true); }

        @Override protected int color(HudContext c) { return c.accent(); }

        @Override protected List<String> lines(HudContext c) {
            return List.of("Draco Utility " + Draco.VERSION + " | " + Draco.get().profiles.active());
        }
    }

    public static final class Coordinates extends TextElement {
        private final Setting.Bool convert = settings.bool("convert", "Dimension conversion",
                "Also show the matching Overworld / Nether coordinates.", true);

        public Coordinates() { super("coordinates", "Coordinates", Anchor.TOP_LEFT, 4, 104, true); }

        @Override protected List<String> lines(HudContext c) {
            ClientPlayerEntity p = c.mc.player;
            if (p == null) return List.of();
            List<String> out = new ArrayList<>();
            out.add("XYZ: " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());
            if (convert.get() && c.mc.world != null) {
                String dim = Dim.of(c.mc.world);
                if (dim.equals("minecraft:overworld")) out.add("Nether: " + p.getBlockX() / 8 + " " + p.getBlockZ() / 8);
                else if (dim.equals("minecraft:the_nether")) out.add("Overworld: " + p.getBlockX() * 8 + " " + p.getBlockZ() * 8);
            }
            return out;
        }
    }

    public static final class Fps extends TextElement {
        public Fps() { super("fps", "FPS", Anchor.BOTTOM_RIGHT, 4, 4, false); }

        @Override protected List<String> lines(HudContext c) { return List.of("FPS: " + c.mc.getCurrentFps()); }
    }

    public static final class Ping extends TextElement {
        public Ping() { super("ping", "Ping", Anchor.BOTTOM_RIGHT, 4, 14, false); }

        @Override protected List<String> lines(HudContext c) {
            ClientPlayNetworkHandler h = c.mc.getNetworkHandler();
            ClientPlayerEntity p = c.mc.player;
            PlayerListEntry entry = h == null || p == null ? null : h.getPlayerListEntry(p.getUuid());
            return List.of("Ping: " + (entry == null ? "-" : entry.getLatency() + " ms"));
        }
    }

    public static final class Direction extends TextElement {
        public Direction() { super("direction", "Direction", Anchor.TOP_LEFT, 4, 128, false); }

        @Override protected List<String> lines(HudContext c) {
            ClientPlayerEntity p = c.mc.player;
            if (p == null) return List.of();
            return List.of("Facing: " + p.getHorizontalFacing().asString() + " (" + (int) MathHelper.wrapDegrees(p.getYaw()) + ")");
        }
    }

    public static final class Speed extends TextElement {
        public Speed() { super("speed", "Speed", Anchor.TOP_LEFT, 4, 140, false); }

        @Override protected List<String> lines(HudContext c) {
            return List.of(String.format("Speed: %.2f b/s", Draco.get().services.get(PlayerService.class).speed()));
        }
    }

    /** Nearest player and the guide target; these reveal world information, so they are locked. */
    public static final class Info extends TextElement {
        public Info() { super("info", "Info", Anchor.TOP_LEFT, 4, 116, true); }

        @Override public boolean requiresPermission() { return true; }

        @Override protected List<String> lines(HudContext c) {
            Draco d = Draco.get();
            List<String> out = new ArrayList<>();
            PlayerService ps = d.services.get(PlayerService.class);
            if (ps.nearestPlayer() != null && d.modules.get("esp") != null && d.modules.get("esp").isRunning()) {
                out.add("Nearest player: " + ps.nearestPlayer().getName().getString() + " (" + (int) ps.nearestPlayerDistance() + "m)");
            }
            String guide = d.services.get(InfoService.class).get("guide");
            Module g = d.modules.get("guide");
            if (guide != null && g != null && g.isRunning()) out.add(guide);
            return out;
        }
    }

    public static final class Performance extends TextElement {
        public Performance() { super("performance", "Performance", Anchor.BOTTOM_LEFT, 4, 4, false); }

        @Override protected List<String> lines(HudContext c) {
            Draco d = Draco.get();
            List<String> out = new ArrayList<>();
            out.add(String.format("Tick: %.2f ms", d.profiler.averageMs("tick")));
            out.add(String.format("3D render: %.2f ms", d.profiler.averageMs("render3d")));
            out.add(String.format("Entities: %.2f ms", d.profiler.averageMs("entities")));
            out.add(String.format("Scheduler: %.2f ms, %d tasks", d.scheduler.lastTickNanos() / 1e6, d.scheduler.taskCount()));
            out.add("Modules: " + d.modules.running().size() + "/" + d.modules.all().size() + ", listeners: " + d.bus.listenerCount());
            List<Module> running = new ArrayList<>(d.modules.running());
            running.sort(Comparator.comparingDouble((Module m) -> m.stats().averageMs()).reversed());
            for (int i = 0; i < Math.min(3, running.size()); i++) {
                Module m = running.get(i);
                if (m.stats().averageMs() > 0.005) out.add(String.format("  %s: %.3f ms", m.name(), m.stats().averageMs()));
            }
            return out;
        }
    }
}
