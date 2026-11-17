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

package studio.draco.utility.core.command;

import studio.draco.utility.core.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Parses and dispatches prefixed chat commands such as ".draco toggle esp". */
public final class CommandManager {
    private final Map<String, Command> byName = new LinkedHashMap<>();
    private final List<Command> commands = new ArrayList<>();
    private final Supplier<String> prefix;

    public CommandManager(Supplier<String> prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        String p = prefix.get();
        return p == null || p.isEmpty() ? "." : p;
    }

    public void register(Command c) {
        commands.add(c);
        byName.put(c.name().toLowerCase(Locale.ROOT), c);
        for (String a : c.aliases()) byName.put(a.toLowerCase(Locale.ROOT), c);
    }

    public List<Command> all() { return List.copyOf(commands); }
    public Command find(String name) { return byName.get(name.toLowerCase(Locale.ROOT)); }
    public boolean isCommand(String line) { return line != null && line.startsWith(prefix()); }

    /** Runs the line if it starts with the prefix. Returns true when it was handled. */
    public boolean execute(String line, CommandSource source) {
        if (!isCommand(line)) return false;
        List<String> tokens;
        try {
            tokens = tokenize(line.substring(prefix().length()));
        } catch (CommandException e) {
            source.error(e.getMessage());
            return true;
        }
        if (tokens.isEmpty()) {
            source.reply("Type " + prefix() + "help for a list of commands.");
            return true;
        }
        Command c = find(tokens.get(0));
        if (c == null) {
            source.error("Unknown command '" + tokens.get(0) + "'. Type " + prefix() + "help.");
            return true;
        }
        String[] args = tokens.subList(1, tokens.size()).toArray(new String[0]);
        try {
            c.execute(args, source);
        } catch (CommandException e) {
            source.error(e.getMessage());
            source.reply("Usage: " + prefix() + c.usage());
        } catch (Throwable t) {
            Log.error("Command '" + c.name() + "' crashed", t);
            source.error("Command failed: " + t);
        }
        return true;
    }

    /** Completion candidates for a partially typed line (without needing the GUI). */
    public List<String> suggest(String line) {
        if (!isCommand(line)) return List.of();
        String body = line.substring(prefix().length());
        boolean trailingSpace = body.endsWith(" ");
        List<String> tokens = tokenizeLenient(body);
        if (tokens.isEmpty() || (tokens.size() == 1 && !trailingSpace)) {
            String typed = tokens.isEmpty() ? "" : tokens.get(0).toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Command c : commands) if (c.name().startsWith(typed)) out.add(c.name());
            return out;
        }
        Command c = find(tokens.get(0));
        if (c == null) return List.of();
        List<String> rest = new ArrayList<>(tokens.subList(1, tokens.size()));
        if (trailingSpace) rest.add("");
        return c.suggest(rest.toArray(new String[0]));
    }

    /**
     * Splits on whitespace, keeping "quoted text" together. A backslash escapes the next character
     * (\" gives a literal quote). A quote that is never closed is an error, not silently swallowed.
     */
    public static List<String> tokenize(String s) throws CommandException {
        return tokenize(s, true);
    }

    /** Like tokenize() but never throws: an unclosed quote just runs to the end (used for completion). */
    public static List<String> tokenizeLenient(String s) {
        try {
            return tokenize(s, false);
        } catch (CommandException impossible) {
            throw new IllegalStateException(impossible); // lenient mode never throws
        }
    }

    private static List<String> tokenize(String s, boolean strict) throws CommandException {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean has = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length() && (s.charAt(i + 1) == '"' || s.charAt(i + 1) == '\\')) {
                cur.append(s.charAt(++i));
                has = true;
            } else if (ch == '"') {
                quoted = !quoted;
                has = true;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (has) { out.add(cur.toString()); cur.setLength(0); has = false; }
            } else {
                cur.append(ch);
                has = true;
            }
        }
        if (quoted && strict) throw new CommandException("Missing closing quote (\")");
        if (has) out.add(cur.toString());
        return out;
    }
}
