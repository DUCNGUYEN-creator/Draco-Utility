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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Named sets of module / HUD / GUI settings: create, copy, rename, delete, switch, import, export. */
public final class ProfileManager {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final String FORMAT = "draco-profile";

    private final ConfigManager config;

    public ProfileManager(ConfigManager config) {
        this.config = config;
    }

    public static boolean isValidName(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public String active() { return config.activeProfile(); }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        try (Stream<Path> dirs = Files.isDirectory(config.profilesDir()) ? Files.list(config.profilesDir()) : Stream.<Path>empty()) {
            dirs.filter(Files::isDirectory).forEach(d -> names.add(d.getFileName().toString()));
        } catch (IOException ignored) {
            // An unreadable directory just means "no profiles yet".
        }
        if (!names.contains(active())) names.add(active());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean exists(String name) {
        return list().contains(name);
    }

    /** Creates a new profile as a copy of the current settings. */
    public void create(String name) throws IOException {
        copy(active(), name);
    }

    public void copy(String source, String target) throws IOException {
        requireValid(target);
        if (exists(target)) throw new IllegalArgumentException("Profile already exists: " + target);
        if (!exists(source)) throw new IllegalArgumentException("No such profile: " + source);
        if (source.equals(active())) config.saveAll();
        copyDirectory(config.profileDir(source), config.profileDir(target));
    }

    public void delete(String name) throws IOException {
        if (!exists(name)) throw new IllegalArgumentException("No such profile: " + name);
        if (name.equals(active())) throw new IllegalArgumentException("Cannot delete the active profile");
        deleteDirectory(config.profileDir(name));
    }

    public void rename(String oldName, String newName) throws IOException {
        requireValid(newName);
        if (!exists(oldName)) throw new IllegalArgumentException("No such profile: " + oldName);
        if (exists(newName)) throw new IllegalArgumentException("Profile already exists: " + newName);
        if (oldName.equals(active())) config.saveAll();
        Files.move(config.profileDir(oldName), config.profileDir(newName));
        if (oldName.equals(active())) {
            config.setActiveProfile(newName);
            config.saveMeta();
        }
    }

    public void switchTo(String name) {
        if (!exists(name)) throw new IllegalArgumentException("No such profile: " + name);
        if (name.equals(active())) return;
        config.flush();
        config.setActiveProfile(name);
        config.loadProfileScope();
        config.saveMeta();
    }

    /** Writes one profile (all its files) into a single shareable JSON file. */
    public void export(String name, Path file) throws IOException {
        if (!exists(name)) throw new IllegalArgumentException("No such profile: " + name);
        if (name.equals(active())) config.saveAll();
        Map<String, Object> files = new LinkedHashMap<>();
        Path dir = config.profileDir(name);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> found = Files.list(dir)) {
                for (Path p : (Iterable<Path>) found.filter(x -> x.getFileName().toString().endsWith(".json"))::iterator) {
                    String id = p.getFileName().toString().replaceFirst("\\.json$", "");
                    files.put(id, Json.parse(Files.readString(p, StandardCharsets.UTF_8)));
                }
            }
        }
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", FORMAT);
        bundle.put("version", 1L);
        bundle.put("name", name);
        bundle.put("files", files);
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, Json.stringify(bundle), StandardCharsets.UTF_8);
    }

    /** Reads an exported profile into a new profile called {@code newName}. It is not activated. */
    public void importFrom(Path file, String newName) throws IOException {
        requireValid(newName);
        if (exists(newName)) throw new IllegalArgumentException("Profile already exists: " + newName);
        Map<String, Object> bundle = Json.map(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
        if (!FORMAT.equals(bundle.get("format"))) throw new IllegalArgumentException("Not a Draco profile file");
        Map<String, Object> files = Json.map(bundle.get("files"));
        Path dir = config.profileDir(newName);
        Files.createDirectories(dir);
        for (Map.Entry<String, Object> e : files.entrySet()) {
            if (!VALID.matcher(e.getKey()).matches()) continue; // never trust file names from an import
            Files.writeString(dir.resolve(e.getKey() + ".json"), Json.stringify(e.getValue()), StandardCharsets.UTF_8);
        }
    }

    private static void requireValid(String name) {
        if (!isValidName(name)) throw new IllegalArgumentException("Invalid profile name (use letters, digits, - and _, max 32): " + name);
    }

    private static void copyDirectory(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }
}
