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

package studio.draco.utility.core;

/** Tiny logging facade. The sink is replaced at startup so output goes to the game log. */
public final class Log {
    public interface Sink {
        void log(String level, String message, Throwable error);
    }

    private static volatile Sink sink = (level, message, error) -> {
        System.err.println("[Draco/" + level + "] " + message);
        if (error != null) error.printStackTrace();
    };

    private Log() {}

    public static void setSink(Sink s) {
        sink = s == null ? sink : s;
    }

    public static void info(String message) { sink.log("INFO", message, null); }
    public static void warn(String message) { sink.log("WARN", message, null); }
    public static void error(String message, Throwable error) { sink.log("ERROR", message, error); }
}
