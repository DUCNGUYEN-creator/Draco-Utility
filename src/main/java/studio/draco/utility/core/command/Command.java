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

import java.util.List;

public abstract class Command {
    private final String name;
    private final String usage;
    private final String description;
    private final String[] aliases;

    protected Command(String name, String usage, String description, String... aliases) {
        this.name = name;
        this.usage = usage;
        this.description = description;
        this.aliases = aliases;
    }

    public final String name() { return name; }
    public final String usage() { return usage; }
    public final String description() { return description; }
    public final String[] aliases() { return aliases.clone(); }

    public abstract void execute(String[] args, CommandSource source) throws CommandException;

    /** Completion candidates for the argument being typed (args = everything after the command name). */
    public List<String> suggest(String[] args) {
        return List.of();
    }
}
