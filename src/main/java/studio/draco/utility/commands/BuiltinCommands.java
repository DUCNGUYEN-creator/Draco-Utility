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

package studio.draco.utility.commands;

import studio.draco.utility.core.policy.ServerProfile;
import studio.draco.utility.core.policy.PolicyManager;
import studio.draco.utility.core.policy.Capability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import studio.draco.utility.Draco;
import studio.draco.utility.core.command.Command;
import studio.draco.utility.core.command.CommandException;
import studio.draco.utility.core.command.CommandManager;
import studio.draco.utility.core.command.CommandSource;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;
import studio.draco.utility.gui.ClickGui;
import studio.draco.utility.gui.HudEditorScreen;
import studio.draco.utility.gui.MapScreen;
import studio.draco.utility.gui.ToolsScreen;
import studio.draco.utility.input.KeybindManager;
import studio.draco.utility.modules.world.Guide;
import studio.draco.utility.platform.Dim;
import studio.draco.utility.services.Targets;
import studio.draco.utility.world.waypoint.WaypointManager.Waypoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Supplier;

/** Chat commands: .help .toggle .set .bind .modules .profile .waypoint .config .gui .hud .map .tools .perf */
public final class BuiltinCommands {
    private interface Exec { void run(String[] args, CommandSource source) throws CommandException; }
    private interface Suggest { List<String> get(String[] args); }

    /** Prints into the chat window, only visible to you. */
    public static final class ChatSource implements CommandSource {
        private static void send(Text text) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(text);
        }

        @Override public void reply(String text) { send(Text.literal("[Draco] " + text)); }
        @Override public void error(String text) { send(Text.literal("[Draco] " + text).formatted(Formatting.RED)); }
    }

    private BuiltinCommands() {}

    private static void add(CommandManager cm, String name, String usage, String desc, Exec exec, Suggest suggest, String... aliases) {
        cm.register(new Command(name, usage, desc, aliases) {
            @Override public void execute(String[] args, CommandSource source) throws CommandException { exec.run(args, source); }
            @Override public List<String> suggest(String[] args) { return suggest == null ? List.of() : suggest.get(args); }
        });
    }

    private static void add(CommandManager cm, String name, String usage, String desc, Exec exec) {
        add(cm, name, usage, desc, exec, null);
    }

    private static Module module(String name) throws CommandException {
        Module m = Draco.get().modules.get(name);
        if (m == null) throw new CommandException("No module named '" + name + "'");
        return m;
    }

    private static List<String> moduleNames(String prefix) {
        List<String> out = new ArrayList<>();
        for (Module m : Draco.get().modules.all()) if (m.id().startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(m.id());
        return out;
    }

    private static List<String> startingWith(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(o);
        return out;
    }

    private static void open(Supplier<Screen> screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(screen.get())); // after the chat screen has closed
    }

    static int parseKey(String text) throws CommandException {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.equals("none")) return -1;
        if (t.length() == 1) {
            char c = t.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW.GLFW_KEY_A + (c - 'a');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }
        if (t.matches("f([1-9]|1[0-2])")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(t.substring(1)) - 1;
        if (t.matches("mouse[3-8]")) return KeybindManager.MOUSE_BASE + Integer.parseInt(t.substring(5)) - 1;
        switch (t) {
            case "space": return GLFW.GLFW_KEY_SPACE;
            case "tab": return GLFW.GLFW_KEY_TAB;
            case "enter": return GLFW.GLFW_KEY_ENTER;
            case "lshift": return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "rshift": return GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "lctrl": return GLFW.GLFW_KEY_LEFT_CONTROL;
            case "rctrl": return GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "lalt": return GLFW.GLFW_KEY_LEFT_ALT;
            case "ralt": return GLFW.GLFW_KEY_RIGHT_ALT;
            case "insert": return GLFW.GLFW_KEY_INSERT;
            case "delete": return GLFW.GLFW_KEY_DELETE;
            case "home": return GLFW.GLFW_KEY_HOME;
            case "end": return GLFW.GLFW_KEY_END;
            case "up": return GLFW.GLFW_KEY_UP;
            case "down": return GLFW.GLFW_KEY_DOWN;
            case "left": return GLFW.GLFW_KEY_LEFT;
            case "right": return GLFW.GLFW_KEY_RIGHT;
            default:
                try {
                    return Integer.parseInt(t);
                } catch (NumberFormatException e) {
                    throw new CommandException("Unknown key '" + text + "' (try a letter, f1-f12, space, rshift, mouse4 or none)");
                }
        }
    }

    public static void register(Draco d) {
        CommandManager cm = d.commands;

        add(cm, "help", "help [command]", "Lists commands or explains one.", (a, s) -> {
            String p = cm.prefix();
            if (a.length > 0) {
                Command c = cm.find(a[0]);
                if (c == null) throw new CommandException("No command named '" + a[0] + "'");
                s.reply(p + c.usage() + " - " + c.description());
                return;
            }
            s.reply("Commands (prefix " + p + "):");
            for (Command c : cm.all()) s.reply("  " + p + c.usage() + " - " + c.description());
        }, a -> a.length == 1 ? startingWith(cm.all().stream().map(Command::name).toList(), a[0]) : List.of(), "?");

        add(cm, "toggle", "toggle <module>", "Turns a module on or off.", (a, s) -> {
            if (a.length < 1) throw new CommandException("Which module?");
            Module m = module(a[0]);
            d.toggle(m);
            s.reply(m.name() + " is now " + (m.isEnabled() ? "ON" : "OFF")
                    + (m.isEnabled() && !m.isRunning() ? " (waiting: needs a world, and a profile that allows it - see .policy)" : ""));
        }, a -> a.length == 1 ? moduleNames(a[0]) : List.of(), "t");

        add(cm, "enable", "enable <module>", "Turns a module on.", (a, s) -> {
            if (a.length < 1) throw new CommandException("Which module?");
            Module m = module(a[0]);
            if (!m.isEnabled()) d.toggle(m);
            s.reply(m.name() + " is ON");
        }, a -> a.length == 1 ? moduleNames(a[0]) : List.of());

        add(cm, "disable", "disable <module>", "Turns a module off.", (a, s) -> {
            if (a.length < 1) throw new CommandException("Which module?");
            Module m = module(a[0]);
            if (m.isEnabled()) d.toggle(m);
            s.reply(m.name() + " is OFF");
        }, a -> a.length == 1 ? moduleNames(a[0]) : List.of());

        add(cm, "modules", "modules [category]", "Lists modules and whether they are on.", (a, s) -> {
            for (ModuleCategory c : ModuleCategory.values()) {
                if (a.length > 0 && !c.title().equalsIgnoreCase(a[0])) continue;
                StringBuilder sb = new StringBuilder(c.title()).append(": ");
                for (Module m : d.modules.byCategory(c)) sb.append(m.id()).append(m.isEnabled() ? "(on) " : " ");
                s.reply(sb.toString());
            }
        }, a -> a.length == 1 ? startingWith(java.util.Arrays.stream(ModuleCategory.values()).map(ModuleCategory::title).toList(), a[0]) : List.of());

        add(cm, "set", "set <module> [setting] [value]", "Shows or changes a setting.", (a, s) -> {
            if (a.length < 1) throw new CommandException("Which module?");
            Module m = module(a[0]);
            if (a.length == 1) {
                for (SettingGroup g : m.groups()) for (Setting<?> st : g) s.reply("  " + st.id() + " = " + st.valueToString());
                return;
            }
            Setting<?> st = m.setting(a[1]);
            if (st == null) throw new CommandException(m.name() + " has no setting '" + a[1] + "'");
            if (a.length == 2) {
                s.reply(st.name() + " = " + st.valueToString() + "  (" + st.description() + ")");
                return;
            }
            String value = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
            if (!st.parse(value)) throw new CommandException("'" + value + "' is not valid for " + st.name());
            s.reply(st.name() + " = " + st.valueToString());
        }, a -> {
            if (a.length == 1) return moduleNames(a[0]);
            Module m = d.modules.get(a[0]);
            if (a.length == 2 && m != null) {
                List<String> ids = new ArrayList<>();
                for (SettingGroup g : m.groups()) for (Setting<?> st : g) ids.add(st.id());
                return startingWith(ids, a[1]);
            }
            return List.of();
        });

        add(cm, "bind", "bind <module> <key|none>", "Sets the key that toggles a module.", (a, s) -> {
            if (a.length < 2) throw new CommandException("Usage: bind <module> <key|none>");
            Module m = module(a[0]);
            int key = parseKey(a[1]);
            m.setKeybind(key);
            s.reply(m.name() + " bound to " + KeybindManager.keyName(key));
        }, a -> a.length == 1 ? moduleNames(a[0]) : List.of());

        add(cm, "profile", "profile list|load|create|copy|delete|rename|export|import <name> [new]", "Manage setting profiles.", (a, s) -> {
            if (a.length < 1) throw new CommandException("Usage: profile list|load|create|copy|delete|rename|export|import");
            try {
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "list" -> s.reply("Profiles: " + String.join(", ", d.profiles.list()) + "  (active: " + d.profiles.active() + ")");
                    case "load" -> {
                        need(a, 2);
                        d.profiles.switchTo(a[1]);
                        s.reply("Loaded profile " + a[1]);
                        d.notifications.success("Profile", "Loaded " + a[1]);
                    }
                    case "create" -> { need(a, 2); d.profiles.create(a[1]); s.reply("Created profile " + a[1] + " from your current settings"); }
                    case "copy" -> { need(a, 3); d.profiles.copy(a[1], a[2]); s.reply("Copied " + a[1] + " to " + a[2]); }
                    case "delete" -> { need(a, 2); d.profiles.delete(a[1]); s.reply("Deleted profile " + a[1]); }
                    case "rename" -> { need(a, 3); d.profiles.rename(a[1], a[2]); s.reply("Renamed " + a[1] + " to " + a[2]); }
                    case "export" -> {
                        need(a, 2);
                        Path file = d.config.root().resolve("exports").resolve(a[1] + ".json");
                        d.profiles.export(a[1], file);
                        s.reply("Exported to " + file);
                    }
                    case "import" -> {
                        need(a, 2);
                        Path file = d.config.root().resolve("exports").resolve(a[1] + ".json");
                        String name = a.length > 2 ? a[2] : a[1];
                        d.profiles.importFrom(file, name);
                        s.reply("Imported as profile " + name + " (use profile load " + name + ")");
                    }
                    default -> throw new CommandException("Unknown profile action '" + a[0] + "'");
                }
            } catch (IllegalArgumentException | IOException e) {
                throw new CommandException(e.getMessage());
            }
        }, a -> {
            if (a.length == 1) return startingWith(List.of("list", "load", "create", "copy", "delete", "rename", "export", "import"), a[0]);
            if (a.length == 2 && List.of("load", "copy", "delete", "rename", "export").contains(a[0].toLowerCase(Locale.ROOT))) {
                return startingWith(d.profiles.list(), a[1]);
            }
            return List.of();
        });

        add(cm, "waypoint", "waypoint add <name>|list|remove <name>|guide <name>", "Manage waypoints.", (a, s) -> {
            if (a.length < 1) throw new CommandException("Usage: waypoint add|list|remove|guide");
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) throw new CommandException("Join a world first");
            String dim = Dim.of(mc.world);
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    need(a, 2);
                    d.waypoints.add(new Waypoint(a[1], mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), dim));
                    s.reply("Saved " + a[1] + " at " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());
                }
                case "list" -> {
                    List<Waypoint> list = d.waypoints.forDimension(dim);
                    if (list.isEmpty()) s.reply("No waypoints in this dimension");
                    for (Waypoint w : list) s.reply("  " + w.name() + ": " + w.x() + " " + w.y() + " " + w.z());
                }
                case "remove" -> {
                    need(a, 2);
                    Waypoint w = d.waypoints.find(a[1], dim);
                    if (w == null) throw new CommandException("No waypoint named " + a[1]);
                    d.waypoints.remove(w);
                    s.reply("Removed " + a[1]);
                }
                case "guide" -> {
                    need(a, 2);
                    Waypoint w = d.waypoints.find(a[1], dim);
                    Guide g = d.modules.get(Guide.class);
                    if (w == null || g == null) throw new CommandException("No waypoint named " + a[1]);
                    g.kind.set(Targets.Kind.WAYPOINT);
                    g.waypoint.set(w.name());
                    if (!g.isEnabled()) d.toggle(g);
                    s.reply("Guiding to " + w.name());
                }
                default -> throw new CommandException("Unknown waypoint action '" + a[0] + "'");
            }
        }, a -> {
            if (a.length == 1) return startingWith(List.of("add", "list", "remove", "guide"), a[0]);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (a.length == 2 && mc.world != null && List.of("remove", "guide").contains(a[0].toLowerCase(Locale.ROOT))) {
                List<String> names = new ArrayList<>();
                for (Waypoint w : d.waypoints.forDimension(Dim.of(mc.world))) names.add(w.name());
                return startingWith(names, a[1]);
            }
            return List.of();
        }, "wp");

        add(cm, "config", "config save|reload", "Saves or reloads all settings.", (a, s) -> {
            String action = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
            switch (action) {
                case "save" -> { d.config.saveAll(); s.reply("Settings saved to " + d.config.root()); }
                case "reload" -> {
                    d.config.clearErrors();
                    d.config.loadAll();
                    s.reply("Settings reloaded" + (d.config.errors().isEmpty() ? "" : " with problems: " + d.config.errors()));
                }
                default -> throw new CommandException("Usage: config save|reload");
            }
        }, a -> a.length == 1 ? startingWith(List.of("save", "reload"), a[0]) : List.of());

        add(cm, "gui", "gui", "Opens the Click GUI.", (a, s) -> open(ClickGui::new));
        add(cm, "hud", "hud", "Opens the HUD editor.", (a, s) -> open(() -> new HudEditorScreen(null)));
        add(cm, "map", "map", "Opens the large map.", (a, s) -> open(() -> new MapScreen(null)));
        add(cm, "tools", "tools", "Opens teleport, stats and command tools.", (a, s) -> open(() -> new ToolsScreen(null)));

        add(cm, "policy", "policy [trust|list|bind <profile> [server]|unbind [server]|create <name> [copy]|delete <name>|set <profile> <capability> <on|off>|default <profile>|singleplayer <profile>]",
                "Shows or changes which capabilities are allowed where you play.", (a, s) -> {
            PolicyManager pm = d.policy;
            String sub = a.length == 0 ? "show" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "show" -> {
                    String where = pm.current() == null ? "unknown" : pm.current();
                    s.reply("Here: " + where + " -> profile '" + pm.active().id() + "'");
                    StringBuilder on = new StringBuilder();
                    for (Capability c : Capability.values()) on.append(pm.active().allows(c) ? "[x] " : "[ ] ").append(c.key()).append("  ");
                    s.reply(on.toString().trim());
                }
                case "list" -> {
                    for (ServerProfile p : pm.profiles()) {
                        StringBuilder caps = new StringBuilder();
                        for (Capability c : p.allowed()) caps.append(c.key()).append(' ');
                        s.reply(p.id() + (p.isBuiltin() ? " (built-in)" : "") + ": " + caps.toString().trim());
                    }
                    for (Map.Entry<String, String> e : pm.bindings().entrySet()) s.reply("  " + e.getKey() + " -> " + e.getValue());
                    s.reply("  singleplayer -> " + pm.singleplayerProfileId() + ", other servers -> " + pm.defaultProfileId());
                }
                case "trust" -> {
                    if (pm.current() == null) throw new CommandException("Join a world or server first");
                    pm.bind(pm.current(), ServerProfile.PRIVATE);
                    s.reply(pm.current() + " now uses the 'private' profile (everything allowed). Only do this on servers that you run or that allow it.");
                }
                case "bind" -> {
                    if (a.length < 2) throw new CommandException("Which profile?");
                    String key = a.length > 2 ? a[2] : pm.current();
                    if (key == null) throw new CommandException("Join a world or server first, or give a server address");
                    if (!pm.bind(key, a[1])) throw new CommandException("No profile named '" + a[1] + "'");
                    s.reply(key + " -> " + a[1]);
                }
                case "unbind" -> {
                    String key = a.length > 1 ? a[1] : pm.current();
                    if (key == null || !pm.unbind(key)) throw new CommandException("Nothing was bound for that server");
                    s.reply("Unbound " + key);
                }
                case "create" -> {
                    if (a.length < 2) throw new CommandException("Name the new profile (a-z, 0-9, - _ , up to 24 characters)");
                    if (!pm.create(a[1], a.length > 2 ? a[2] : null)) throw new CommandException("Cannot create '" + a[1] + "' (invalid or already exists)");
                    s.reply("Created profile " + a[1].toLowerCase(Locale.ROOT));
                }
                case "delete" -> {
                    if (a.length < 2 || !pm.delete(a[1])) throw new CommandException("Only custom profiles can be deleted");
                    s.reply("Deleted " + a[1]);
                }
                case "set" -> {
                    if (a.length < 4) throw new CommandException("Usage: set <profile> <capability> <on|off>");
                    Capability c = Capability.byKey(a[2]);
                    if (c == null) throw new CommandException("Unknown capability '" + a[2] + "'");
                    if (!pm.setCapability(a[1], c, a[3].equalsIgnoreCase("on") || a[3].equalsIgnoreCase("true")))
                        throw new CommandException("Cannot edit '" + a[1] + "' (unknown, or built-in)");
                    s.reply(a[1] + ": " + c.key() + " " + a[3]);
                }
                case "default" -> {
                    if (a.length < 2 || !pm.setDefault(a[1])) throw new CommandException("No profile named that");
                    s.reply("Servers without a binding now use " + a[1]);
                }
                case "singleplayer" -> {
                    if (a.length < 2 || !pm.bind(PolicyManager.SINGLEPLAYER_KEY, a[1])) throw new CommandException("No profile named that");
                    s.reply("Singleplayer now uses " + a[1]);
                }
                default -> throw new CommandException("Unknown option '" + sub + "'");
            }
        }, a -> {
            if (a.length == 1) return startingWith(List.of("show", "list", "trust", "bind", "unbind", "create", "delete", "set", "default", "singleplayer"), a[0]);
            if (a.length == 2 && List.of("bind", "delete", "set", "default", "singleplayer", "create").contains(a[0].toLowerCase(Locale.ROOT))) {
                List<String> ids = new ArrayList<>();
                for (ServerProfile p : d.policy.profiles()) ids.add(p.id());
                return startingWith(ids, a[1]);
            }
            if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
                List<String> keys = new ArrayList<>();
                for (Capability c : Capability.values()) keys.add(c.key());
                return startingWith(keys, a[2]);
            }
            if (a.length == 4 && a[0].equalsIgnoreCase("set")) return startingWith(List.of("on", "off"), a[3]);
            return List.of();
        });

        add(cm, "compat", "compat", "Shows the game version and which related mods are loaded.", (a, s) -> {
            s.reply("Draco Utility " + Draco.VERSION + " (built for Minecraft 1.21.1)");
            for (String id : new String[]{"fabric-api", "sodium", "iris", "indium", "modmenu", "viafabricplus"}) {
                s.reply("  " + id + ": " + (FabricLoader.getInstance().isModLoaded(id) ? "loaded" : "not loaded"));
            }
            s.reply("Addons: " + (d.addons.loaded().isEmpty() ? "none" : String.join(", ", d.addons.loaded())));
            for (String pr : d.addons.problems()) s.error("Addon problem: " + pr);
            if (FabricLoader.getInstance().isModLoaded("sodium")) s.reply("Sodium support for X-Ray is experimental; if blocks look wrong, turn X-Ray off.");
        });

        add(cm, "perf", "perf", "Shows timing information.", (a, s) -> {
            s.reply(String.format("Tick %.2f ms, 3D render %.2f ms, entities %.2f ms, scheduler %.2f ms (%d tasks)",
                    d.profiler.averageMs("tick"), d.profiler.averageMs("render3d"), d.profiler.averageMs("entities"),
                    d.scheduler.lastTickNanos() / 1e6, d.scheduler.taskCount()));
            s.reply("Modules running " + d.modules.running().size() + "/" + d.modules.all().size()
                    + ", event listeners " + d.bus.listenerCount() + ", scan cycles " + d.profiler.counter("scan.cycles"));
            List<Module> running = new ArrayList<>(d.modules.running());
            running.sort(Comparator.comparingDouble((Module m) -> m.stats().averageMs()).reversed());
            for (int i = 0; i < Math.min(5, running.size()); i++) {
                s.reply(String.format("  %s: %.3f ms/event over %d calls", running.get(i).name(),
                        running.get(i).stats().averageMs(), running.get(i).stats().calls()));
            }
        });
    }

    private static void need(String[] args, int count) throws CommandException {
        if (args.length < count) throw new CommandException("Missing argument");
    }
}
