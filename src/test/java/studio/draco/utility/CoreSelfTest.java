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

package studio.draco.utility;

import studio.draco.utility.core.Log;
import studio.draco.utility.core.command.Command;
import studio.draco.utility.core.command.CommandException;
import studio.draco.utility.core.command.CommandManager;
import studio.draco.utility.core.config.ConfigManager;
import studio.draco.utility.core.config.ConfigMigrator;
import studio.draco.utility.core.config.Json;
import studio.draco.utility.core.config.LegacyImporter;
import studio.draco.utility.core.config.ProfileManager;
import studio.draco.utility.core.event.CancellableEvent;
import studio.draco.utility.core.event.Event;
import studio.draco.utility.core.event.EventBus;
import studio.draco.utility.core.event.EventPriority;
import studio.draco.utility.core.lifecycle.Lifecycle;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.module.ModuleManager;
import studio.draco.utility.core.module.ModuleState;
import studio.draco.utility.core.notification.NotificationManager;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.policy.PolicyManager;
import studio.draco.utility.core.policy.ServerProfile;
import studio.draco.utility.core.scheduler.Task;
import studio.draco.utility.core.scheduler.WorkScheduler;
import studio.draco.utility.core.service.ServiceRegistry;
import studio.draco.utility.core.util.LongList;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Self-contained tests for the Minecraft-free core. Run with: gradlew selfTest
 * (plain Java, no JUnit, so it needs no extra dependency).
 */
public final class CoreSelfTest {
    private static int passed;
    private static int failed;

    private static void check(boolean condition, String name) {
        if (condition) { passed++; } else { failed++; System.out.println("  FAIL: " + name); }
    }

    private static void eq(Object expected, Object actual, String name) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) { passed++; } else { failed++; System.out.println("  FAIL: " + name + " expected=" + expected + " actual=" + actual); }
    }

    // ------------------------------------------------------------------ fixtures
    static final class Ping implements Event { }
    static final class Stop extends CancellableEvent { }

    enum Mode { A, B, C }

    static class TestModule extends Module {
        final AtomicInteger activations = new AtomicInteger();
        final AtomicInteger deactivations = new AtomicInteger();
        final AtomicInteger pings = new AtomicInteger();
        boolean failOnActivate;
        boolean failOnPing;
        final Setting.Dbl speed = general.dbl("speed", "Speed", "", 1.0, 0.0, 5.0);
        final Setting.Choice<Mode> mode = general.choice("mode", "Mode", "", Mode.A);

        TestModule(String id) {
            super(id, "Test " + id, "A test module", ModuleCategory.MISC);
        }

        @Override protected void onActivate() throws Exception {
            activations.incrementAndGet();
            on(Ping.class, e -> {
                pings.incrementAndGet();
                if (failOnPing) throw new IllegalStateException("boom");
            });
            if (failOnActivate) throw new Exception("cannot start");
        }

        @Override protected void onDeactivate() { deactivations.incrementAndGet(); }
    }

    public static void main(String[] args) throws Exception {
        List<String> logged = new ArrayList<>();
        Log.setSink((level, msg, err) -> logged.add(level + ": " + msg));

        testEventBus();
        testSettings();
        testModules();
        testJson();
        testConfig();
        testProfiles();
        testScheduler();
        testLifecycle();
        testNotifications();
        testLegacy();
        testCommands();
        testPolicy();
        testHardening();

        System.out.println("Core self-test: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // --------------------------------------------------------------------- tests

    static void testEventBus() {
        EventBus bus = new EventBus();
        List<String> order = new ArrayList<>();
        Object a = new Object(), b = new Object();
        bus.subscribe(Ping.class, EventPriority.LOW, a, null, e -> order.add("low"));
        bus.subscribe(Ping.class, EventPriority.HIGHEST, a, null, e -> order.add("highest"));
        bus.subscribe(Ping.class, EventPriority.NORMAL, b, null, e -> order.add("normal1"));
        bus.subscribe(Ping.class, EventPriority.NORMAL, b, null, e -> order.add("normal2"));
        bus.post(new Ping());
        eq(List.of("highest", "normal1", "normal2", "low"), order, "priority order, stable inside a priority");

        eq(2, bus.listenerCount(a), "listener count per owner");
        eq(2, bus.unsubscribeAll(a), "unsubscribeAll removes owner's listeners");
        order.clear();
        bus.post(new Ping());
        eq(List.of("normal1", "normal2"), order, "listeners gone after unsubscribeAll");

        AtomicInteger n = new AtomicInteger();
        bus.subscribe(Stop.class, EventPriority.HIGH, a, null, e -> { n.incrementAndGet(); e.cancel(); });
        bus.subscribe(Stop.class, EventPriority.LOW, b, null, e -> n.addAndGet(10));
        bus.post(new Stop());
        eq(1, n.get(), "cancelled event is not delivered to lower priority");

        AtomicInteger dup = new AtomicInteger();
        for (int i = 0; i < 3; i++) bus.subscribe(Ping.class, EventPriority.NORMAL, a, "same-key", e -> dup.incrementAndGet());
        order.clear();
        bus.post(new Ping());
        eq(1, dup.get(), "duplicate (type, owner, key) registered once");

        AtomicInteger survivors = new AtomicInteger();
        EventBus bus2 = new EventBus();
        List<Throwable> errors = new ArrayList<>();
        bus2.setErrorHandler((owner, ev, t) -> errors.add(t));
        bus2.subscribe(Ping.class, a, e -> { throw new RuntimeException("x"); });
        bus2.subscribe(Ping.class, b, e -> survivors.incrementAndGet());
        bus2.post(new Ping());
        eq(1, survivors.get(), "exception in one listener does not stop the others");
        eq(1, errors.size(), "error handler notified");

        EventBus bus3 = new EventBus();
        bus3.bindThread(Thread.currentThread());
        AtomicInteger hits = new AtomicInteger();
        bus3.subscribe(Ping.class, a, e -> hits.incrementAndGet());
        Thread t = new Thread(() -> bus3.post(new Ping()));
        t.start();
        try { t.join(); } catch (InterruptedException ignored) { }
        eq(0, hits.get(), "post from a foreign thread is dropped");
        eq(1, bus3.threadViolations(), "thread violation counted");
        bus3.post(new Ping());
        eq(1, hits.get(), "post from the bound thread works");
    }

    static void testSettings() {
        SettingGroup g = new SettingGroup("G");
        Setting.Int i = g.integer("i", "I", "", 5, 0, 10);
        Setting.Dbl d = g.dbl("d", "D", "", 1.5, 0, 3);
        Setting.Bool b = g.bool("b", "B", "", true);
        Setting.Choice<Mode> c = g.choice("c", "C", "", Mode.B);
        Setting.Color col = g.color("col", "Col", "", 0xFF8800);
        Setting.Text txt = g.text("t", "T", "", "hello", 5);
        Setting.Key key = g.key("k", "K", "", 82);
        AtomicInteger dirty = new AtomicInteger();
        g.setDirtyHook(dirty::incrementAndGet);

        i.set(99);
        eq(10, i.get(), "int clamps to max");
        i.set(-4);
        eq(0, i.get(), "int clamps to min");
        d.set(Double.NaN);
        eq(0.0, d.get(), "NaN becomes min");
        eq(true, dirty.get() >= 3, "dirty hook fired on changes");
        int before = dirty.get();
        i.set(0);
        eq(before, dirty.get(), "no dirty signal when value is unchanged");

        txt.set("abcdefgh");
        eq("abcde", txt.get(), "text truncated to max length");
        eq(Mode.C, c.next(), "enum next");
        eq(Mode.B, c.previous(), "enum previous");
        eq(Mode.A, c.previous() == Mode.A ? Mode.A : null, "enum previous wraps");
        check(c.parse("c") && c.get() == Mode.C, "enum parse is case-insensitive");
        check(!c.parse("zzz"), "enum parse rejects unknown");

        eq("#FF8800", col.toJson(), "color json");
        check(col.parse("#00ff00") && col.get() == 0x00FF00, "color parse");
        check(!col.parse("#12"), "color rejects short value");
        check(key.parse("none") && key.get() == -1, "key none");

        Object json = i.toJson();
        Setting.Int other = new Setting.Int("i", "I", "", 5, 0, 10);
        check(other.fromJson(json), "int json roundtrip accepted");
        eq(i.get(), other.get(), "int json roundtrip value");
        check(!b.fromJson(12), "bool rejects a number");

        d.set(2.0);
        d.reset();
        eq(1.5, d.get(), "reset restores default");

        boolean[] visible = {false};
        b.visibleWhen(() -> visible[0]);
        check(!b.isVisible(), "visibility predicate false");
        visible[0] = true;
        check(b.isVisible(), "visibility predicate true");

        boolean threw = false;
        try { g.bool("b", "again", "", false); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "duplicate setting id rejected");
    }

    static void testModules() {
        AtomicLong clock = new AtomicLong(0);
        EventBus bus = new EventBus();
        ServiceRegistry services = new ServiceRegistry();
        ModuleManager mm = new ModuleManager(bus, services, clock::get);
        boolean[] gate = {true};
        mm.setGate(() -> gate[0]);
        List<String> events = new ArrayList<>();
        mm.addListener(new ModuleManager.Listener() {
            @Override public void onState(Module m, ModuleState from, ModuleState to) { events.add(m.id() + ":" + to); }
            @Override public void onError(Module m, Throwable e, boolean disabled) { events.add(m.id() + ":error" + (disabled ? "!" : "")); }
            @Override public void onRefused(Module m, String reason) { events.add(m.id() + ":refused"); }
        });
        AtomicInteger dirty = new AtomicInteger();
        mm.setDirtyHook(dirty::incrementAndGet);

        TestModule a = new TestModule("a");
        TestModule b = new TestModule("b");
        mm.register(a);
        mm.register(b);
        eq(ModuleState.DISABLED, a.state(), "registered module starts DISABLED");

        a.setEnabled(true);
        check(a.isRunning(), "enable starts the module");
        eq(ModuleState.ENABLED, a.state(), "state ENABLED");
        eq(1, bus.listenerCount(a), "listener registered during activation");
        bus.post(new Ping());
        eq(1, a.pings.get(), "listener receives events");

        a.setEnabled(false);
        check(!a.isRunning(), "disable stops the module");
        eq(0, bus.listenerCount(a), "listeners removed on disable");
        eq(1, a.deactivations.get(), "onDeactivate called once");
        bus.post(new Ping());
        eq(1, a.pings.get(), "no events after disable");

        // gate (permission / in-world)
        a.setEnabled(true);
        gate[0] = false;
        mm.tick();
        eq(ModuleState.SUSPENDED, a.state(), "closed gate suspends an enabled module");
        check(a.isEnabled() && !a.isRunning(), "intent kept while suspended");
        eq(0, bus.listenerCount(a), "suspended module has no listeners");
        gate[0] = true;
        mm.tick();
        eq(ModuleState.ENABLED, a.state(), "open gate resumes the module");
        a.setEnabled(false);

        // activation failure rolls back
        TestModule bad = new TestModule("bad");
        bad.failOnActivate = true;
        mm.register(bad);
        bad.setEnabled(true);
        eq(ModuleState.FAILED, bad.state(), "failed activation -> FAILED");
        check(!bad.isEnabled() && !bad.isRunning(), "rollback clears intent and running flag");
        eq(0, bus.listenerCount(bad), "rollback removes listeners registered before the failure");
        check(events.contains("bad:error!"), "listener told about the failure");

        // missing service dependency
        class NeedsService extends TestModule {
            NeedsService() { super("needs"); requires(String.class); }
        }
        NeedsService needs = new NeedsService();
        mm.register(needs);
        needs.setEnabled(true);
        eq(ModuleState.FAILED, needs.state(), "missing service refuses activation");
        services.register(String.class, "ok");
        needs.setEnabled(true);
        check(needs.isRunning(), "starts once the service exists");
        needs.setEnabled(false);

        // conflicts
        class Conflicting extends TestModule {
            Conflicting() { super("c1"); conflictsWith("c2"); }
        }
        Conflicting c1 = new Conflicting();
        TestModule c2 = new TestModule("c2");
        mm.register(c1);
        mm.register(c2);
        c2.setEnabled(true);
        check(!mm.setEnabled(c1, true), "conflicting module refused");
        check(!c1.isEnabled(), "refused module stays off");
        check(events.contains("c1:refused"), "refusal reported");
        c2.setEnabled(false);

        // runtime errors disable the module after three in 5 s, without touching others
        TestModule flaky = new TestModule("flaky");
        flaky.failOnPing = true;
        mm.register(flaky);
        flaky.setEnabled(true);
        b.setEnabled(true);
        bus.post(new Ping());
        bus.post(new Ping());
        check(flaky.isRunning(), "still running after two errors");
        bus.post(new Ping());
        check(!flaky.isRunning(), "disabled after three errors");
        eq(ModuleState.FAILED, flaky.state(), "state FAILED after repeated errors");
        check(b.isRunning(), "other modules unaffected");
        b.setEnabled(false);

        // error window expires
        TestModule slow = new TestModule("slow");
        slow.failOnPing = true;
        mm.register(slow);
        slow.setEnabled(true);
        bus.post(new Ping());
        bus.post(new Ping());
        clock.addAndGet(6000);
        bus.post(new Ping());
        check(slow.isRunning(), "errors older than the window are forgotten");
        slow.setEnabled(false);

        // deactivateAll keeps intent
        a.setEnabled(true);
        mm.deactivateAll();
        check(a.isEnabled() && !a.isRunning(), "deactivateAll keeps user intent");
        eq(ModuleState.SUSPENDED, a.state(), "state SUSPENDED after deactivateAll");
        mm.tick();
        check(a.isRunning(), "module restarts on next tick");
        a.setEnabled(false);

        // persistence
        a.speed.set(3.5);
        a.mode.set(Mode.C);
        a.setKeybind(70);
        a.setFavorite(true);
        a.setEnabled(true);
        Map<String, Object> saved = mm.saveState();
        String text = Json.stringify(saved);
        mm.resetState();
        eq(1.0, a.speed.get(), "resetState restores defaults");
        check(!a.isEnabled() && a.keybind() == -1, "resetState clears enabled flag and bind");
        mm.loadState(Json.map(Json.parse(text)));
        eq(3.5, a.speed.get(), "setting restored from JSON");
        eq(Mode.C, a.mode.get(), "enum restored from JSON");
        eq(70, a.keybind(), "keybind restored");
        check(a.isFavorite() && a.isEnabled(), "favorite and enabled restored");
        mm.tick();
        check(a.isRunning(), "restored module starts via the gate");
        a.setEnabled(false);

        // unknown / broken data does not throw
        Map<String, Object> broken = new LinkedHashMap<>();
        Map<String, Object> junk = new LinkedHashMap<>();
        junk.put("enabled", "yes");
        junk.put("settings", Map.of("speed", "fast", "mode", "Z", "ghost", 1));
        broken.put("a", junk);
        broken.put("nothere", Map.of());
        mm.loadState(broken);
        eq(3.5, a.speed.get(), "invalid setting values are ignored");

        // search
        List<Module> found = mm.search("test a");
        check(!found.isEmpty() && found.get(0) == a, "search ranks the exact match first");
        check(mm.search("speed").contains(a), "search finds modules by setting name");
        eq(a, mm.get("A"), "lookup is case-insensitive");
        check(dirty.get() > 0, "dirty hook triggered by state changes");

        // non-toggleable module
        class AlwaysOn extends Module {
            AlwaysOn() { super("always", "Always", "", ModuleCategory.MISC); }
            @Override public boolean isToggleable() { return false; }
            @Override public boolean ignoresGate() { return true; }
        }
        AlwaysOn always = new AlwaysOn();
        mm.register(always);
        gate[0] = false;
        mm.tick();
        check(always.isRunning(), "gate-ignoring module runs while the gate is closed");
        check(!mm.setEnabled(always, false), "non-toggleable module cannot be disabled");
        gate[0] = true;
    }

    static void testJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "line\n\"quoted\" \\ tab\t");
        m.put("n", 42L);
        m.put("d", 1.5);
        m.put("b", true);
        m.put("z", null);
        m.put("list", List.of(1L, "two", Map.of("k", false)));
        m.put("empty", new LinkedHashMap<>());
        String text = Json.stringify(m);
        Map<String, Object> back = Json.map(Json.parse(text));
        eq(m.get("s"), back.get("s"), "string escapes roundtrip");
        eq(42L, back.get("n"), "integers parse as Long");
        eq(1.5, back.get("d"), "doubles parse as Double");
        eq(true, back.get("b"), "boolean roundtrip");
        check(back.containsKey("z") && back.get("z") == null, "null roundtrip");
        eq(3, Json.list(back.get("list")).size(), "list roundtrip");
        eq("é\u00e9", Json.str(Json.parse("\"\\u00e9\u00e9\""), ""), "unicode escape");
        for (String bad : new String[]{"{", "[1,]", "{\"a\" 1}", "tru", "\"abc", "1 2", "{\"a\":}"}) {
            boolean threw = false;
            try { Json.parse(bad); } catch (Json.JsonException e) { threw = true; }
            check(threw, "rejects malformed JSON: " + bad);
        }
    }

    /** A tiny persistable for the config tests. */
    static final class Store implements ConfigManager.Persistable {
        final String id;
        final ConfigManager.Scope scope;
        long value = 7;
        int schema = 1;
        Store(String id, ConfigManager.Scope scope) { this.id = id; this.scope = scope; }
        public String id() { return id; }
        public ConfigManager.Scope scope() { return scope; }
        public int schemaVersion() { return schema; }
        public Map<String, Object> save() { Map<String, Object> m = new LinkedHashMap<>(); m.put("value", value); return m; }
        public void load(Map<String, Object> data) { value = Json.lng(data.get("value"), 7); }
        public void reset() { value = 7; }
    }

    static void testConfig() throws Exception {
        Path dir = Files.createTempDirectory("draco-cfg");
        AtomicLong clock = new AtomicLong(1_000_000);
        ConfigManager cm = new ConfigManager(dir, clock::get, new ConfigMigrator());
        cm.setBackupIntervalMs(0);
        Store s = new Store("modules", ConfigManager.Scope.PROFILE);
        Store g = new Store("waypoints", ConfigManager.Scope.GLOBAL);
        cm.register(s);
        cm.register(g);

        s.value = 123;
        g.value = 456;
        cm.saveAll();
        check(Files.exists(dir.resolve("config.json")), "meta file written");
        check(Files.exists(dir.resolve("profiles/default/modules.json")), "profile file in profile dir");
        check(Files.exists(dir.resolve("waypoints.json")), "global file at root");
        check(Files.notExists(dir.resolve("profiles/default/modules.json.tmp")), "no temp file left behind");

        s.value = 1;
        g.value = 1;
        cm.loadAll();
        eq(123L, s.value, "profile value reloaded");
        eq(456L, g.value, "global value reloaded");

        // debounce
        s.value = 200;
        cm.markDirty();
        clock.addAndGet(500);
        cm.tick();
        String early = Files.readString(dir.resolve("profiles/default/modules.json"));
        check(early.contains("123"), "no write before the debounce time");
        clock.addAndGet(2000);
        cm.tick();
        String late = Files.readString(dir.resolve("profiles/default/modules.json"));
        check(late.contains("200"), "write after the debounce time");

        // backups are created when overwriting
        cm.saveAll();
        check(Files.isDirectory(dir.resolve("backups/profile-default")), "backup directory created");

        // corrupt file: recover from the newest good backup
        clock.addAndGet(5);
        cm.saveAll();
        Files.writeString(dir.resolve("profiles/default/modules.json"), "{ this is not json");
        s.value = 1;
        cm.loadAll();
        eq(200L, s.value, "corrupt file recovered from backup");
        check(!cm.errors().isEmpty(), "problem recorded");

        // corrupt and no backups: defaults + quarantine
        Path dir2 = Files.createTempDirectory("draco-cfg2");
        ConfigManager cm2 = new ConfigManager(dir2, clock::get, new ConfigMigrator());
        Store s2 = new Store("modules", ConfigManager.Scope.PROFILE);
        cm2.register(s2);
        Files.createDirectories(dir2.resolve("profiles/default"));
        Files.writeString(dir2.resolve("profiles/default/modules.json"), "garbage");
        s2.value = 99;
        cm2.loadAll();
        eq(7L, s2.value, "defaults used when nothing can be recovered");
        boolean quarantined = false;
        try (var files = Files.list(dir2.resolve("profiles/default"))) {
            quarantined = files.anyMatch(p -> p.getFileName().toString().contains(".corrupt-"));
        }
        check(quarantined, "corrupt file set aside, not deleted");

        // schema validation
        Files.writeString(dir2.resolve("profiles/default/modules.json"), "{\"data\": {}}");
        cm2.loadAll();
        eq(7L, s2.value, "file without a schema field is rejected");

        // migration
        Path dir3 = Files.createTempDirectory("draco-cfg3");
        ConfigMigrator mig = new ConfigMigrator();
        mig.register("modules", 1, data -> {
            Map<String, Object> out = new LinkedHashMap<>(data);
            out.put("value", Json.lng(data.get("oldName"), 0) + 1000);
            return out;
        });
        ConfigManager cm3 = new ConfigManager(dir3, clock::get, mig);
        Store s3 = new Store("modules", ConfigManager.Scope.PROFILE);
        s3.schema = 2;
        cm3.register(s3);
        Files.createDirectories(dir3.resolve("profiles/default"));
        Files.writeString(dir3.resolve("profiles/default/modules.json"), "{\"schema\":1,\"data\":{\"oldName\":5}}");
        cm3.loadAll();
        eq(1005L, s3.value, "old schema migrated step by step");
    }

    static void testProfiles() throws Exception {
        Path dir = Files.createTempDirectory("draco-prof");
        AtomicLong clock = new AtomicLong(0);
        ConfigManager cm = new ConfigManager(dir, clock::get, new ConfigMigrator());
        Store s = new Store("modules", ConfigManager.Scope.PROFILE);
        Store g = new Store("waypoints", ConfigManager.Scope.GLOBAL);
        cm.register(s);
        cm.register(g);
        ProfileManager pm = new ProfileManager(cm);

        s.value = 11;
        g.value = 77;
        cm.saveAll();
        eq(List.of("default"), pm.list(), "starts with the default profile");

        pm.create("pvp");
        check(pm.exists("pvp"), "profile created");
        s.value = 22;
        cm.saveAll();
        pm.switchTo("pvp");
        eq(11L, s.value, "new profile started as a copy of the old settings");
        s.value = 33;
        cm.saveAll();
        pm.switchTo("default");
        eq(22L, s.value, "switching back restores that profile's values");
        eq(77L, g.value, "global data is not touched by profile switches");
        eq("default", pm.active(), "active profile updated");

        boolean threw = false;
        try { pm.delete("default"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "cannot delete the active profile");
        threw = false;
        try { pm.create("../evil"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "path-like profile names rejected");

        Path export = dir.resolve("exports/pvp.json");
        pm.export("pvp", export);
        pm.importFrom(export, "pvp-copy");
        pm.switchTo("pvp-copy");
        eq(33L, s.value, "imported profile carries the exported values");
        pm.switchTo("default");

        pm.rename("pvp-copy", "survival");
        check(pm.exists("survival") && !pm.exists("pvp-copy"), "rename moves the profile");
        pm.delete("survival");
        check(!pm.exists("survival"), "delete removes the profile");

        // active profile persists across restarts
        pm.switchTo("pvp");
        ConfigManager cm2 = new ConfigManager(dir, clock::get, new ConfigMigrator());
        Store s2 = new Store("modules", ConfigManager.Scope.PROFILE);
        cm2.register(s2);
        cm2.loadAll();
        eq("pvp", cm2.activeProfile(), "active profile remembered");
        eq(33L, s2.value, "active profile's data loaded at startup");
    }

    static void testScheduler() {
        AtomicLong now = new AtomicLong(0);
        WorkScheduler ws = new WorkScheduler(now::get);
        List<String> ran = new ArrayList<>();
        class Job implements Task {
            final String name;
            final long cost;
            int steps;
            boolean done;
            Job(String name, long cost) { this.name = name; this.cost = cost; }
            public String name() { return name; }
            public void step(long deadline) { ran.add(name); steps++; now.addAndGet(cost); }
            public boolean isDone() { return done; }
        }
        Job high = new Job("high", 600), mid = new Job("mid", 600), low = new Job("low", 600);
        ws.submit(low, 1);
        ws.submit(high, 10);
        ws.submit(mid, 5);
        ws.tick(1000);
        eq(List.of("high", "mid"), ran, "budget limits how many tasks run, by priority");
        eq(1, ws.lastTasksSkipped(), "skipped task counted");
        ran.clear();
        ws.tick(1000);
        check(ran.contains("low"), "starved task runs first next tick (no starvation)");

        Job once = new Job("once", 10);
        once.done = true;
        ws.submit(once, 100);
        int before = ws.taskCount();
        ws.tick(100_000);
        eq(before - 1, ws.taskCount(), "finished task removed");

        WorkScheduler ws2 = new WorkScheduler(now::get);
        Task crashing = new Task() {
            public String name() { return "crash"; }
            public void step(long d) { throw new IllegalStateException("x"); }
        };
        ws2.submit(crashing, 1);
        ws2.tick(1000);
        eq(0, ws2.taskCount(), "crashing task removed, scheduler survives");

        WorkScheduler ws3 = new WorkScheduler(now::get);
        Job j = new Job("always", 5_000_000);
        ws3.submit(j, 1);
        ws3.tick(1);
        eq(1, j.steps, "at least one task always runs even with a tiny budget");
        WorkScheduler.Handle h = ws3.submit(new Job("cancelled", 1), 1);
        h.cancel();
        ws3.tick(1_000_000);
        check(!ran.contains("cancelled"), "cancelled task never runs");
    }

    static void testLifecycle() {
        Lifecycle lc = new Lifecycle();
        List<String> order = new ArrayList<>();
        lc.register(Lifecycle.Phase.CORE_INIT, "core", () -> order.add("core"));
        lc.register(Lifecycle.Phase.PRE_INIT, "pre", () -> order.add("pre"));
        lc.registerOptional(Lifecycle.Phase.POST_INIT, "optional-fails", () -> { throw new IllegalStateException("meh"); });
        lc.register(Lifecycle.Phase.POST_INIT, "post", () -> order.add("post"));
        lc.register(Lifecycle.Phase.SHUTDOWN, "bye", () -> order.add("bye"));
        lc.startAll();
        eq(List.of("pre", "core", "post"), order, "phases run in order, optional failure does not abort");
        check(lc.hasErrors() && lc.report().contains("optional-fails"), "report lists the failed step");
        lc.shutdown();
        eq("bye", order.get(order.size() - 1), "shutdown phase runs");

        Lifecycle bad = new Lifecycle();
        List<String> after = new ArrayList<>();
        bad.register(Lifecycle.Phase.CORE_INIT, "critical", () -> { throw new IllegalStateException("no"); });
        bad.register(Lifecycle.Phase.CORE_INIT, "never", () -> after.add("x"));
        boolean threw = false;
        try { bad.startAll(); } catch (Lifecycle.LifecycleException e) { threw = true; }
        check(threw && after.isEmpty(), "critical failure aborts the phase");
    }

    static void testNotifications() {
        AtomicLong clock = new AtomicLong(0);
        NotificationManager nm = new NotificationManager(clock::get);
        nm.configure(true, 1000, 3);
        for (int i = 0; i < 5; i++) nm.info("t", "m" + i);
        eq(3, nm.visible().size(), "limited to the maximum visible");
        eq("m4", nm.visible().get(2).message(), "newest kept");
        clock.addAndGet(1500);
        eq(0, nm.visible().size(), "expired notifications dropped");
        nm.configure(false, 1000, 3);
        nm.info("t", "ignored");
        eq(0, nm.visible().size(), "disabled notifications are not shown");
    }

    static void testLegacy() throws Exception {
        Path dir = Files.createTempDirectory("draco-legacy");
        Path props = dir.resolve("dracoutility.properties");
        Files.writeString(props, String.join("\n",
                "fly=true", "flySpeed=0.8", "esp=true", "espEntities=false", "espOres=true",
                "xray=true", "xrayOutline=false", "outlineRadius=4",
                "guide=true", "guideType=3", "oreFilter=7", "oreRadius=32",
                "autoClick=true", "clickPlace=true", "clickDelayTicks=5"));
        Map<String, Object> mods = LegacyImporter.importModules(props);
        Map<String, Object> flight = Json.map(mods.get("flight"));
        eq(true, flight.get("enabled"), "legacy fly -> flight enabled");
        eq(0.8, Json.map(flight.get("settings")).get("speed"), "legacy flySpeed -> speed");
        eq("DIAMOND", Json.map(Json.map(mods.get("oreesp")).get("settings")).get("filter"), "legacy ore filter index mapped");
        eq("PASSIVE", Json.map(Json.map(mods.get("guide")).get("settings")).get("kind"), "legacy guide type mapped");
        eq("PLACE", Json.map(Json.map(mods.get("autoclick")).get("settings")).get("mode"), "legacy click mode mapped");
        eq(4.0, Json.map(Json.map(mods.get("autoclick")).get("settings")).get("cps"), "legacy delay -> clicks per second");
        eq(false, Json.map(Json.map(mods.get("esp")).get("settings")).get("players"), "legacy espEntities -> player toggle");

        Path wp = dir.resolve("wp.txt");
        Files.writeString(wp, "Home\t10\t64\t-20\tminecraft:overworld\nbroken line\nBase\t1\t2\t3\tminecraft:the_nether\n");
        List<Object> wps = LegacyImporter.importWaypoints(wp);
        eq(2, wps.size(), "legacy waypoints imported, broken lines skipped");
        eq("Home", Json.map(wps.get(0)).get("name"), "legacy waypoint name");
    }

    static void testCommands() throws Exception {
        CommandManager cm = new CommandManager(() -> ".");
        List<String> out = new ArrayList<>();
        var src = new studio.draco.utility.core.command.CommandSource() {
            public void reply(String t) { out.add("R:" + t); }
            public void error(String t) { out.add("E:" + t); }
        };
        cm.register(new Command("echo", "echo <text>", "Echoes", "say") {
            @Override public void execute(String[] args, studio.draco.utility.core.command.CommandSource s) throws CommandException {
                if (args.length == 0) throw new CommandException("Nothing to echo");
                s.reply(String.join("|", args));
            }
            @Override public List<String> suggest(String[] args) { return List.of("alpha", "beta"); }
        });
        eq(List.of("a b", "c", ""), CommandManager.tokenize("\"a b\" c \"\""), "tokenizer keeps quoted text");
        check(!cm.execute("hello", src), "non-prefixed text is not a command");
        check(cm.execute(".echo one \"two words\"", src), "command handled");
        eq("R:one|two words", out.get(0), "arguments passed through");
        out.clear();
        cm.execute(".say", src);
        check(out.get(0).startsWith("E:Nothing to echo"), "CommandException shown as an error");
        out.clear();
        cm.execute(".nope", src);
        check(out.get(0).startsWith("E:Unknown command"), "unknown command reported");
        eq(List.of("echo"), cm.suggest(".ec"), "command name completion");
        eq(List.of("alpha", "beta"), cm.suggest(".echo "), "argument completion delegated");
    }
    // ------------------------------------------------------------- 2.0.0 additions

    static class CapModule extends TestModule {
        CapModule(String id, Capability... caps) {
            super(id);
            capabilities(caps);
        }
    }

    static void testPolicy() {
        PolicyManager pm = new PolicyManager();
        pm.setCurrent(PolicyManager.SINGLEPLAYER_KEY);
        eq(ServerProfile.PRIVATE, pm.active().id(), "singleplayer uses the private profile");
        check(pm.active().allows(Capability.COMBAT), "private allows everything");

        pm.setCurrent("Play.Example.com:25565");
        eq("play.example.com", pm.current(), "server address normalized");
        eq(ServerProfile.VISUAL, pm.active().id(), "unknown server gets the restrictive default");
        check(pm.active().allows(Capability.LOCAL_VISUAL), "visual allows local rendering");
        check(!pm.active().allows(Capability.WORLD_INFORMATION), "visual does not allow world information");
        check(!pm.active().allows(Capability.MOVEMENT), "visual does not allow movement");
        check(pm.denyReason(List.of(Capability.MOVEMENT), "Flight").contains("Flight"), "deny reason names the module");
        eq(null, pm.denyReason(List.of(Capability.LOCAL_UI), "Gui"), "allowed capability has no reason");
        eq(null, pm.denyReason(List.of(), "Nothing"), "module without capabilities is never blocked");

        check(pm.bind("play.example.com", ServerProfile.PRIVATE), "bind a server to a profile");
        eq(ServerProfile.PRIVATE, pm.active().id(), "bound server uses its profile");
        check(!pm.bind("x.com", "does-not-exist"), "binding to an unknown profile fails");
        check(pm.unbind("play.example.com"), "unbind");
        eq(ServerProfile.VISUAL, pm.active().id(), "unbound server is restricted again");

        check(!pm.setCapability(ServerProfile.PRIVATE, Capability.COMBAT, false), "built-in profile cannot be edited");
        check(pm.create("lab", ServerProfile.INFO), "custom profile created");
        check(!pm.create("lab", null), "duplicate profile rejected");
        check(!pm.create("Bad Name!", null), "invalid profile id rejected");
        check(pm.setCapability("lab", Capability.MOVEMENT, true), "custom profile editable");
        pm.bind("lab.example.com", "lab");

        Map<String, Object> saved = pm.save();
        PolicyManager back = new PolicyManager();
        back.load(Json.map(Json.parse(Json.stringify(saved))));
        back.setCurrent("lab.example.com");
        eq("lab", back.active().id(), "bindings survive a save/load");
        check(back.active().allows(Capability.MOVEMENT) && back.active().allows(Capability.WORLD_INFORMATION), "custom profile survives a save/load");
        check(!back.active().allows(Capability.COMBAT), "custom profile keeps its limits");
        check(back.delete("lab"), "custom profile deleted");
        eq(ServerProfile.VISUAL, back.profileFor("lab.example.com").id(), "deleting a profile never widens access");

        // Module manager honours the policy.
        AtomicLong clock = new AtomicLong(0);
        ModuleManager mm = new ModuleManager(new EventBus(), new ServiceRegistry(), clock::get);
        PolicyManager live = new PolicyManager();
        mm.setPolicy(live);
        List<String> refused = new ArrayList<>();
        mm.addListener(new ModuleManager.Listener() {
            @Override public void onRefused(Module m, String reason) { refused.add(m.id()); }
        });
        CapModule fly = new CapModule("fly", Capability.MOVEMENT);
        CapModule fb = new CapModule("fb", Capability.LOCAL_VISUAL);
        CapModule none = new CapModule("none");
        mm.register(fly);
        mm.register(fb);
        mm.register(none);
        live.setCurrent("some.server.net");
        check(!mm.setEnabled(fly, true), "movement module refused on a restricted server");
        check(!fly.isEnabled() && !fly.isRunning(), "refused module stays off");
        check(refused.contains("fly"), "refusal is reported");
        check(mm.setEnabled(fb, true) && fb.isRunning(), "visual module runs on a restricted server");
        check(mm.setEnabled(none, true) && none.isRunning(), "module without capabilities always runs");

        live.setCurrent(PolicyManager.SINGLEPLAYER_KEY);
        check(mm.setEnabled(fly, true) && fly.isRunning(), "movement module runs in singleplayer");
        live.setCurrent("some.server.net");
        mm.tick();
        check(!fly.isRunning(), "module is stopped when the profile no longer allows it");
        eq(ModuleState.SUSPENDED, fly.state(), "blocked module is SUSPENDED, not lost");
        check(fly.isEnabled(), "user's choice kept while blocked");
        check(fly.blockedReason() != null && fly.blockedReason().contains("Movement"), "blocked reason explains why");
        live.setCurrent(PolicyManager.SINGLEPLAYER_KEY);
        mm.tick();
        check(fly.isRunning(), "module resumes when allowed again");
        eq(null, fly.blockedReason(), "reason cleared when allowed");
    }

    static class LeakyModule extends TestModule {
        LeakyModule(String id) { super(id); }
        @Override protected void onDeactivate() { throw new IllegalStateException("cleanup failed"); }
    }

    static void testHardening() throws Exception {
        // Resources are released even if onDeactivate throws.
        EventBus bus = new EventBus();
        ModuleManager mm = new ModuleManager(bus, new ServiceRegistry(), System::currentTimeMillis);
        List<String> released = new ArrayList<>();
        mm.addReleaser(m -> released.add(m.id()));
        LeakyModule leaky = new LeakyModule("leaky");
        mm.register(leaky);
        leaky.setEnabled(true);
        leaky.setEnabled(false);
        check(released.contains("leaky"), "releaser runs although onDeactivate threw");
        eq(0, bus.listenerCount(leaky), "listeners removed although onDeactivate threw");
        TestModule failing = new TestModule("failing");
        failing.failOnActivate = true;
        mm.register(failing);
        failing.setEnabled(true);
        check(released.contains("failing"), "releaser runs after a failed activation");

        // Tokenizer.
        boolean threw = false;
        try { CommandManager.tokenize("set blocks \"chest"); } catch (CommandException e) { threw = true; }
        check(threw, "unclosed quote is a syntax error");
        eq(List.of("set", "blocks", "chest"), CommandManager.tokenizeLenient("set blocks \"chest"), "lenient tokenizer never throws");
        eq(List.of("say", "a\"b"), CommandManager.tokenize("say a\\\"b"), "escaped quote kept");
        eq(List.of("a b"), CommandManager.tokenize("\"a b\""), "quoted token still works");
        CommandManager cm = new CommandManager(() -> ".");
        List<String> out = new ArrayList<>();
        cm.execute(".x \"oops", new studio.draco.utility.core.command.CommandSource() {
            public void reply(String t) { out.add("R:" + t); }
            public void error(String t) { out.add("E:" + t); }
        });
        check(!out.isEmpty() && out.get(0).startsWith("E:Missing closing quote"), "execute reports a clear syntax error");

        LongList ll = new LongList(4);
        for (long i = 0; i < 1000; i++) ll.add(i * 3);
        eq(1000, ll.size(), "LongList grows");
        eq(2997L, ll.get(999), "LongList keeps values");
        long[] snap = ll.toArray();
        ll.clear();
        eq(0, ll.size(), "LongList clear");
        eq(1000, snap.length, "LongList snapshot is independent");

        // JSON depth and numbers.
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 40; i++) deep.append('[');
        for (int i = 0; i < 40; i++) deep.append(']');
        threw = false;
        try { Json.parse(deep.toString()); } catch (Json.JsonException e) { threw = true; }
        check(threw, "too deep JSON rejected instead of overflowing the stack");
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < Json.MAX_DEPTH; i++) ok.append('[');
        for (int i = 0; i < Json.MAX_DEPTH; i++) ok.append(']');
        Json.parse(ok.toString());
        passed++;
        eq(1.5e3, Json.parse("1.5e3"), "scientific notation");
        eq(-2L, Json.parse("-2"), "negative integer");
        eq(0L, Json.parse("0"), "zero");
        check(Json.parse("123456789012345678901234567890") instanceof Double, "huge integer becomes a double");
        for (String bad : new String[]{"01", "1.", ".5", "-", "1e", "1e+", "--1", "+1", "1e999"}) {
            threw = false;
            try { Json.parse(bad); } catch (Json.JsonException e) { threw = true; }
            check(threw, "rejects bad number: " + bad);
        }
    }
}
