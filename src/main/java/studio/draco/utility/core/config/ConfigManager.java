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

package studio.draco.utility.core.config;

import studio.draco.utility.core.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Versioned JSON persistence. Files are written through a temporary file (atomic replace where the
 * filesystem allows), backed up before being overwritten, validated on load, and recovered from the
 * newest backup if corrupt. Frequent changes are debounced into one write.
 */
public final class ConfigManager {
    public enum Scope { GLOBAL, PROFILE }

    /** Anything that stores state in a config file. */
    public interface Persistable {
        String id();
        Scope scope();
        int schemaVersion();
        Map<String, Object> save();
        void load(Map<String, Object> data);
        /** Return to defaults; called before load() so a profile switch cannot leak old values. */
        void reset();
    }

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path root;
    private final LongSupplier clockMs;
    private final ConfigMigrator migrator;
    private final List<Persistable> persistables = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final Map<String, Long> lastBackup = new HashMap<>();
    private String activeProfile = "default";
    private boolean dirty;
    private long lastChangeMs;
    private long debounceMs = 2000;
    private long backupIntervalMs = 60_000;
    private int backupsToKeep = 5;

    public ConfigManager(Path root, LongSupplier clockMs, ConfigMigrator migrator) {
        this.root = root;
        this.clockMs = clockMs;
        this.migrator = migrator;
    }

    public Path root() { return root; }
    public Path profilesDir() { return root.resolve("profiles"); }
    public Path profileDir(String name) { return profilesDir().resolve(name); }
    public String activeProfile() { return activeProfile; }
    public void setDebounceMs(long ms) { this.debounceMs = ms; }
    public void setBackupIntervalMs(long ms) { this.backupIntervalMs = ms; }
    public List<String> errors() { return Collections.unmodifiableList(errors); }
    public void clearErrors() { errors.clear(); }

    public void register(Persistable p) { persistables.add(p); }

    void setActiveProfile(String name) { this.activeProfile = name; }

    // --------------------------------------------------------------- dirty / debounce

    public synchronized void markDirty() {
        dirty = true;
        lastChangeMs = clockMs.getAsLong();
    }

    /** Call every tick: writes once the user has stopped changing things for the debounce time. */
    public synchronized void tick() {
        if (dirty && clockMs.getAsLong() - lastChangeMs >= debounceMs) saveAll();
    }

    public synchronized void flush() {
        if (dirty) saveAll();
    }

    // -------------------------------------------------------------------- load

    public synchronized void loadAll() {
        loadMeta();
        for (Persistable p : persistables) loadOne(p);
        dirty = false;
    }

    /** Reloads every profile-scoped file for the active profile (after a profile switch). */
    public synchronized void loadProfileScope() {
        for (Persistable p : persistables) {
            if (p.scope() == Scope.PROFILE) loadOne(p);
        }
        dirty = false;
    }

    private void loadMeta() {
        Path f = root.resolve("config.json");
        if (!Files.exists(f)) return;
        try {
            Map<String, Object> env = Json.map(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
            String name = Json.str(Json.map(env.get("data")).get("activeProfile"), "default");
            if (ProfileManager.isValidName(name)) activeProfile = name;
        } catch (Exception e) {
            problem("config.json unreadable, using the default profile: " + e.getMessage(), e);
        }
    }

    private void loadOne(Persistable p) {
        p.reset();
        Path f = fileFor(p);
        if (!Files.exists(f)) return;
        try {
            p.load(readEnvelope(p, f));
        } catch (Exception e) {
            problem("Could not read " + f.getFileName() + ": " + e.getMessage(), e);
            if (!recover(p, f)) {
                quarantine(f);
                p.reset();
            }
        }
    }

    private Map<String, Object> readEnvelope(Persistable p, Path f) throws IOException {
        Object parsed = Json.parse(Files.readString(f, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map)) throw new IOException("not a JSON object");
        Map<String, Object> env = Json.map(parsed);
        if (!(env.get("schema") instanceof Number schema) || !(env.get("data") instanceof Map)) {
            throw new IOException("missing schema/data fields");
        }
        Map<String, Object> data = Json.map(env.get("data"));
        int version = schema.intValue();
        if (version > p.schemaVersion()) {
            Log.warn(f.getFileName() + " was written by a newer version (schema " + version + ")");
        } else if (version < p.schemaVersion()) {
            data = migrator.migrate(p.id(), version, p.schemaVersion(), data);
        }
        return data;
    }

    private boolean recover(Persistable p, Path broken) {
        Path dir = backupDir(p);
        if (!Files.isDirectory(dir)) return false;
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(x -> x.getFileName().toString().startsWith(p.id() + ".")).forEach(candidates::add);
        } catch (IOException e) {
            return false;
        }
        candidates.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        for (Path c : candidates) {
            try {
                p.load(readEnvelope(p, c));
                Log.warn("Recovered " + broken.getFileName() + " from backup " + c.getFileName());
                errors.add("Recovered " + broken.getFileName() + " from a backup");
                return true;
            } catch (Exception ignored) {
                p.reset();
            }
        }
        return false;
    }

    private void quarantine(Path f) {
        try {
            Files.move(f, f.resolveSibling(f.getFileName() + ".corrupt-" + STAMP.format(Instant.ofEpochMilli(clockMs.getAsLong()))),
                    StandardCopyOption.REPLACE_EXISTING);
            errors.add(f.getFileName() + " was corrupt and has been set aside; defaults are in use");
        } catch (IOException e) {
            Log.error("Could not set aside " + f, e);
        }
    }

    // -------------------------------------------------------------------- save

    public synchronized void saveAll() {
        try {
            Files.createDirectories(profileDir(activeProfile));
        } catch (IOException e) {
            problem("Cannot create config directory: " + e.getMessage(), e);
            return;
        }
        saveMeta();
        for (Persistable p : persistables) saveOne(p);
        dirty = false;
    }

    public synchronized void saveMeta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeProfile", activeProfile);
        writeEnvelope(root.resolve("config.json"), 1, data, root.resolve("backups").resolve("global"), "config");
    }

    private void saveOne(Persistable p) {
        writeEnvelope(fileFor(p), p.schemaVersion(), p.save(), backupDir(p), p.id());
    }

    private void writeEnvelope(Path file, int schema, Map<String, Object> data, Path backupDir, String id) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("schema", (long) schema);
        env.put("data", data);
        try {
            writeAtomic(file, Json.stringify(env), backupDir, id);
        } catch (IOException e) {
            problem("Could not write " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private void writeAtomic(Path file, String text, Path backupDir, String id) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.exists(file) && Files.size(file) > 0) backup(file, backupDir, id);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void backup(Path file, Path dir, String id) throws IOException {
        long now = clockMs.getAsLong();
        String key = dir + "/" + id;
        Long last = lastBackup.get(key);
        if (last != null && now - last < backupIntervalMs) return;
        Files.createDirectories(dir);
        Files.copy(file, dir.resolve(id + "." + STAMP.format(Instant.ofEpochMilli(now)) + ".bak"),
                StandardCopyOption.REPLACE_EXISTING);
        lastBackup.put(key, now);
        List<Path> mine = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(x -> x.getFileName().toString().startsWith(id + ".")).forEach(mine::add);
        }
        mine.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        while (mine.size() > backupsToKeep) Files.deleteIfExists(mine.remove(0));
    }

    // ------------------------------------------------------------------ paths

    private Path fileFor(Persistable p) {
        return p.scope() == Scope.GLOBAL
                ? root.resolve(p.id() + ".json")
                : profileDir(activeProfile).resolve(p.id() + ".json");
    }

    private Path backupDir(Persistable p) {
        return root.resolve("backups").resolve(p.scope() == Scope.GLOBAL ? "global" : "profile-" + activeProfile);
    }

    private void problem(String message, Throwable t) {
        errors.add(message);
        Log.error(message, t);
    }
}
