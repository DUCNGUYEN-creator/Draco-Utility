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

package studio.draco.utility.bootstrap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.draco.utility.Draco;
import studio.draco.utility.commands.BuiltinCommands;
import studio.draco.utility.core.Log;
import studio.draco.utility.core.config.ConfigManager;
import studio.draco.utility.core.config.Json;
import studio.draco.utility.core.config.LegacyImporter;
import studio.draco.utility.core.lifecycle.Lifecycle;
import studio.draco.utility.core.lifecycle.Lifecycle.Phase;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleManager;
import studio.draco.utility.core.policy.PolicyManager;
import studio.draco.utility.event.TickEvent;
import studio.draco.utility.event.WorldEvent;
import studio.draco.utility.gui.ClickGui;
import studio.draco.utility.gui.HudEditorScreen;
import studio.draco.utility.gui.MapScreen;
import studio.draco.utility.gui.ToolsScreen;
import studio.draco.utility.hud.elements.ActiveModulesElement;
import studio.draco.utility.hud.elements.ArmorElement;
import studio.draco.utility.hud.elements.MinimapElement;
import studio.draco.utility.hud.elements.NotificationsElement;
import studio.draco.utility.hud.elements.SimpleElements;
import studio.draco.utility.hud.elements.TargetHudElement;
import studio.draco.utility.input.Keys;
import studio.draco.utility.modules.combat.AutoClick;
import studio.draco.utility.modules.combat.AutoPvP;
import studio.draco.utility.modules.combat.AutoTotem;
import studio.draco.utility.modules.misc.ClientSettings;
import studio.draco.utility.modules.movement.Flight;
import studio.draco.utility.modules.movement.Speed;
import studio.draco.utility.modules.movement.Sprint;
import studio.draco.utility.modules.player.AntiAfk;
import studio.draco.utility.modules.player.AutoRespawn;
import studio.draco.utility.modules.render.BlockESP;
import studio.draco.utility.modules.render.Breadcrumbs;
import studio.draco.utility.modules.render.ESP;
import studio.draco.utility.modules.render.Fullbright;
import studio.draco.utility.modules.render.OreESP;
import studio.draco.utility.modules.render.Tracers;
import studio.draco.utility.modules.render.XRay;
import studio.draco.utility.modules.world.Guide;
import studio.draco.utility.modules.world.WaypointMarkers;
import studio.draco.utility.platform.Access;
import studio.draco.utility.render.RenderManager;
import studio.draco.utility.services.BlockScanner;
import studio.draco.utility.services.EntityService;
import studio.draco.utility.services.InfoService;
import studio.draco.utility.services.NightVision;
import studio.draco.utility.services.PlayerService;
import studio.draco.utility.world.minimap.MiniMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Startup in ordered phases (see Lifecycle), then the per-tick wiring of every core system. */
public final class Bootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("DracoUtility");
    private static boolean threadBound;

    private Bootstrap() {}

    public static void start() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path root = configDir.resolve("draco");
        Draco d = Draco.create(root);
        Lifecycle lc = d.lifecycle;

        lc.register(Phase.PRE_INIT, "logging", () -> Log.setSink((level, message, error) -> {
            switch (level) {
                case "ERROR" -> LOGGER.error(message, error);
                case "WARN" -> LOGGER.warn(message);
                default -> LOGGER.info(message);
            }
        }));
        lc.register(Phase.CORE_INIT, "services", () -> registerServices(d));
        lc.register(Phase.CORE_INIT, "persistence", () -> registerPersistence(d));
        lc.register(Phase.CORE_INIT, "module gate and listeners", () -> wireModules(d));
        lc.register(Phase.MODULE_REGISTRATION, "modules", () -> registerModules(d));
        lc.register(Phase.MODULE_REGISTRATION, "hud elements", () -> registerHud(d));
        lc.register(Phase.MODULE_REGISTRATION, "commands", () -> BuiltinCommands.register(d));
        lc.registerOptional(Phase.MODULE_REGISTRATION, "addons", () -> d.addons.loadAll(d));
        lc.registerOptional(Phase.CONFIG_LOAD, "import 1.x settings", () -> {
            try {
                importLegacy(configDir, root);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to import legacy Draco Utility settings", e);
            }
        });
        lc.register(Phase.CONFIG_LOAD, "load config", () -> {
            d.config.loadAll();
            d.config.saveAll();
        });
        lc.register(Phase.POST_INIT, "keys", Keys::register);
        lc.register(Phase.POST_INIT, "events", () -> registerEvents(d));
        lc.register(Phase.SHUTDOWN, "save config", () -> {
            d.config.flush();
            d.config.saveAll();
        });

        try {
            lc.startAll();
            LOGGER.info(lc.report());
            LOGGER.info("Draco Utility {} started: {} modules, {} HUD elements", Draco.VERSION, d.modules.all().size(), d.hud.elements().size());
        } catch (Lifecycle.LifecycleException e) {
            LOGGER.error(lc.report(), e);
        }
    }

    // ------------------------------------------------------------------ steps

    private static void registerServices(Draco d) {
        d.services.register(EntityService.class, new EntityService());
        d.services.register(PlayerService.class, new PlayerService());
        d.services.register(BlockScanner.class, new BlockScanner(d.scheduler));
        d.services.register(InfoService.class, new InfoService());
        d.scheduler.submit(MiniMap.TASK, 3);
    }

    private static ConfigManager.Persistable persist(String id, ConfigManager.Scope scope, Supplier<Map<String, Object>> save,
                                                     Consumer<Map<String, Object>> load, Runnable reset) {
        return new ConfigManager.Persistable() {
            @Override public String id() { return id; }
            @Override public ConfigManager.Scope scope() { return scope; }
            @Override public int schemaVersion() { return 1; }
            @Override public Map<String, Object> save() { return save.get(); }
            @Override public void load(Map<String, Object> data) { load.accept(data); }
            @Override public void reset() { reset.run(); }
        };
    }

    private static void registerPersistence(Draco d) {
        d.config.register(persist("modules", ConfigManager.Scope.PROFILE, d.modules::saveState, d.modules::loadState, d.modules::resetState));
        d.config.register(persist("hud", ConfigManager.Scope.PROFILE, d.hud::save, d.hud::load, d.hud::reset));
        d.config.register(persist("gui", ConfigManager.Scope.PROFILE, ClickGui::saveLayout, ClickGui::loadLayout, ClickGui::resetLayout));
        d.config.register(persist("policy", ConfigManager.Scope.GLOBAL, d.policy::save, d.policy::load, d.policy::reset));
        d.config.register(persist("waypoints", ConfigManager.Scope.GLOBAL, d.waypoints::save, d.waypoints::load, d.waypoints::reset));
        d.config.register(persist("history", ConfigManager.Scope.GLOBAL, d.history::save, d.history::load, () -> d.history.list().clear()));

        Runnable dirty = d.config::markDirty;
        d.modules.setDirtyHook(dirty);
        d.hud.setDirtyHook(dirty);
        d.waypoints.setDirtyHook(dirty);
        d.history.setDirtyHook(dirty);
        d.policy.setDirtyHook(dirty);
    }

    private static void wireModules(Draco d) {
        MinecraftClient mc = MinecraftClient.getInstance();
        d.modules.setGate(() -> Access.inWorld(mc));
        d.modules.setPolicy(d.policy);
        // A module that stops (normally, by an error, or because its onDeactivate threw) always
        // gives back every shared resource it acquired.
        d.modules.addReleaser(m -> {
            d.services.get(EntityService.class).release(m);
            d.services.get(BlockScanner.class).releaseAll(m);
            NightVision.release(m);
        });
        d.modules.addListener(new ModuleManager.Listener() {
            @Override public void onError(Module m, Throwable error, boolean disabled) {
                String msg = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                d.notifications.error(m.name(), disabled ? "Switched off after an error: " + msg : "Error: " + msg);
            }

            @Override public void onRefused(Module m, String reason) {
                d.notifications.warning(m.name(), reason);
            }
        });
    }

    private static void registerModules(Draco d) {
        ModuleManager mm = d.modules;
        ClientSettings settings = new ClientSettings();
        mm.register(settings);
        d.settings = settings;

        mm.register(new AutoClick());
        mm.register(new AutoPvP());
        mm.register(new AutoTotem());
        mm.register(new Flight());
        mm.register(new Sprint());
        mm.register(new Speed());
        mm.register(new ESP());
        mm.register(new Tracers());
        mm.register(new OreESP());
        mm.register(new BlockESP());
        mm.register(new XRay());
        mm.register(new Fullbright());
        mm.register(new Breadcrumbs());
        mm.register(new AutoRespawn());
        mm.register(new AntiAfk());
        mm.register(new Guide());
        mm.register(new WaypointMarkers());
    }

    private static void registerHud(Draco d) {
        d.hud.register(new MinimapElement());
        d.hud.register(new SimpleElements.Watermark());
        d.hud.register(new SimpleElements.Coordinates());
        d.hud.register(new SimpleElements.Direction());
        d.hud.register(new SimpleElements.Speed());
        d.hud.register(new SimpleElements.Info());
        d.hud.register(new SimpleElements.Fps());
        d.hud.register(new SimpleElements.Ping());
        d.hud.register(new SimpleElements.Performance());
        d.hud.register(new ArmorElement());
        d.hud.register(new ActiveModulesElement());
        d.hud.register(new TargetHudElement());
        d.hud.register(new NotificationsElement());
    }

    /** First start after updating from 1.x: convert dracoutility.properties, waypoints and history. */
    private static void importLegacy(Path configDir, Path root) throws IOException {
        Path legacy = configDir.resolve("dracoutility.properties");
        if (!Files.exists(legacy) || Files.exists(root.resolve("config.json"))) return;

        writeEnvelope(root.resolve("profiles").resolve("default").resolve("modules.json"), LegacyImporter.importModules(legacy));
        Path wp = configDir.resolve("dracoutility_waypoints.txt");
        if (Files.exists(wp)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("waypoints", LegacyImporter.importWaypoints(wp));
            writeEnvelope(root.resolve("waypoints.json"), data);
        }
        Path hist = configDir.resolve("dracoutility_history.txt");
        if (Files.exists(hist)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("entries", LegacyImporter.importHistory(hist));
            writeEnvelope(root.resolve("history.json"), data);
        }
        LOGGER.info("Imported Draco Utility 1.x settings from {}", legacy);
    }

    private static void writeEnvelope(Path file, Map<String, Object> data) throws IOException {
        Files.createDirectories(file.getParent());
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("schema", 1L);
        env.put("data", data);
        Files.writeString(file, Json.stringify(env), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ events

    private static void registerEvents(Draco d) {
        ClientTickEvents.START_CLIENT_TICK.register(mc -> startTick(d, mc));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> endTick(d, mc));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            d.policy.setCurrent(serverKey(client));
            d.bus.post(WorldEvent.Join.INSTANCE);
            reportConfigProblems(d, client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            d.modules.deactivateAll();
            d.bus.post(WorldEvent.Leave.INSTANCE);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> d.lifecycle.shutdown());

        RenderManager.register();
        HudRenderCallback.EVENT.register(d.hud::render);

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!d.commands.isCommand(message)) return true;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud != null) mc.inGameHud.getChatHud().addToMessageHistory(message);
            d.commands.execute(message, new BuiltinCommands.ChatSource());
            return false;
        });
    }

    /** Identifies where we are for the server policy: the local world or the server address. */
    private static String serverKey(MinecraftClient client) {
        if (client.isInSingleplayer()) return PolicyManager.SINGLEPLAYER_KEY;
        ServerInfo info = client.getCurrentServerEntry();
        return info == null ? null : info.address;
    }

    private static void reportConfigProblems(Draco d, MinecraftClient mc) {
        List<String> errors = d.config.errors();
        if (errors.isEmpty() || mc.inGameHud == null) return;
        for (String e : errors) mc.inGameHud.getChatHud().addMessage(Text.literal("[Draco] " + e).formatted(Formatting.YELLOW));
        d.config.clearErrors();
    }

    private static void startTick(Draco d, MinecraftClient mc) {
        if (!threadBound) {
            d.bus.bindThread(Thread.currentThread());
            threadBound = true;
        }
        d.profiler.start("tick");
        d.modules.tick();
        d.services.get(PlayerService.class).update(mc);
        d.services.get(EntityService.class).update(mc);
        d.bus.post(TickEvent.Pre.INSTANCE);
    }

    private static void endTick(Draco d, MinecraftClient mc) {
        d.bus.post(TickEvent.Post.INSTANCE);
        NightVision.tick(mc);
        d.keybinds.tick(mc, d.modules.all());

        boolean canOpen = mc.player != null && mc.currentScreen == null;
        while (Keys.gui.wasPressed()) if (canOpen) mc.setScreen(new ClickGui());
        while (Keys.map.wasPressed()) if (canOpen) mc.setScreen(new MapScreen(null));
        while (Keys.tools.wasPressed()) if (canOpen) mc.setScreen(new ToolsScreen(null));
        while (Keys.hud.wasPressed()) if (canOpen) mc.setScreen(new HudEditorScreen(null));

        ClientSettings s = d.settings;
        long budget = (long) ((s == null ? 1.5 : s.budgetMs.get()) * 1_000_000.0);
        d.scheduler.tick(budget);
        d.profiler.record("scheduler", d.scheduler.lastTickNanos());
        d.config.tick();
        d.profiler.end("tick");
    }
}
