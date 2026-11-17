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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer (maps, lists, strings, numbers, booleans, null). Kept dependency
 * free so the config layer can be tested without Minecraft.
 */
public final class Json {
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) { super(message); }
    }

    /** Nesting limit for objects and arrays; deeper input is rejected instead of overflowing the stack. */
    public static final int MAX_DEPTH = 32;

    private final String s;
    private int pos;
    private int depth;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != p.s.length()) throw p.error("Unexpected trailing data");
        return v;
    }

    // ----------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static String str(Object o, String def) { return o instanceof String s ? s : def; }
    public static double num(Object o, double def) { return o instanceof Number n ? n.doubleValue() : def; }
    public static long lng(Object o, long def) { return o instanceof Number n ? n.longValue() : def; }
    public static boolean bool(Object o, boolean def) { return o instanceof Boolean b ? b : def; }

    // ------------------------------------------------------------------ parsing

    private JsonException error(String msg) {
        return new JsonException(msg + " at position " + pos);
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private Object value() {
        if (pos >= s.length()) throw error("Unexpected end");
        char c = s.charAt(pos);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default: return number();
        }
    }

    private Object literal(String word, Object result) {
        if (!s.startsWith(word, pos)) throw error("Invalid literal");
        pos += word.length();
        return result;
    }

    private void enter() {
        if (++depth > MAX_DEPTH) throw error("Nesting deeper than " + MAX_DEPTH + " levels");
    }

    private Map<String, Object> object() {
        enter();
        try {
            return objectBody();
        } finally {
            depth--;
        }
    }

    private Map<String, Object> objectBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (pos < s.length() && s.charAt(pos) == '}') { pos++; return m; }
        while (true) {
            skipWs();
            if (pos >= s.length() || s.charAt(pos) != '"') throw error("Expected string key");
            String key = string();
            skipWs();
            if (pos >= s.length() || s.charAt(pos) != ':') throw error("Expected ':'");
            pos++;
            skipWs();
            m.put(key, value());
            skipWs();
            if (pos >= s.length()) throw error("Unterminated object");
            char c = s.charAt(pos++);
            if (c == '}') return m;
            if (c != ',') throw error("Expected ',' or '}'");
        }
    }

    private List<Object> array() {
        enter();
        try {
            return arrayBody();
        } finally {
            depth--;
        }
    }

    private List<Object> arrayBody() {
        List<Object> l = new ArrayList<>();
        pos++; // [
        skipWs();
        if (pos < s.length() && s.charAt(pos) == ']') { pos++; return l; }
        while (true) {
            skipWs();
            l.add(value());
            skipWs();
            if (pos >= s.length()) throw error("Unterminated array");
            char c = s.charAt(pos++);
            if (c == ']') return l;
            if (c != ',') throw error("Expected ',' or ']'");
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        pos++; // opening quote
        while (true) {
            if (pos >= s.length()) throw error("Unterminated string");
            char c = s.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            if (pos >= s.length()) throw error("Bad escape");
            char e = s.charAt(pos++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > s.length()) throw error("Bad unicode escape");
                    try {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("Bad unicode escape");
                    }
                    pos += 4;
                    break;
                default: throw error("Bad escape");
            }
        }
    }

    /** Strict JSON number: -?(0|[1-9][0-9]*)(.[0-9]+)?([eE][+-]?[0-9]+)? ; integers become Long when they fit. */
    private Object number() {
        int start = pos;
        if (pos < s.length() && s.charAt(pos) == '-') pos++;
        if (pos >= s.length()) throw error("Bad number");
        if (s.charAt(pos) == '0') {
            pos++;
        } else if (isDigit(pos)) {
            while (isDigit(pos)) pos++;
        } else {
            pos = start;
            throw error("Unexpected character '" + s.charAt(start) + "'");
        }
        boolean integral = true;
        if (pos < s.length() && s.charAt(pos) == '.') {
            integral = false;
            pos++;
            if (!isDigit(pos)) throw error("Digits expected after '.'");
            while (isDigit(pos)) pos++;
        }
        if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
            integral = false;
            pos++;
            if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
            if (!isDigit(pos)) throw error("Digits expected in exponent");
            while (isDigit(pos)) pos++;
        }
        String t = s.substring(start, pos);
        if (integral) {
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException tooBig) {
                // falls through to double
            }
        }
        double d = Double.parseDouble(t);
        if (Double.isInfinite(d)) throw error("Number out of range '" + t + "'");
        return d;
    }

    private boolean isDigit(int i) {
        return i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9';
    }

    // ------------------------------------------------------------------ writing

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
    }

    private static void write(StringBuilder sb, Object v, int level) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Boolean || v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            sb.append(v);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            sb.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : String.valueOf(d));
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                indent(sb, level + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), level + 1);
                if (++i < m.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, level);
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            List<Object> items = new ArrayList<>();
            for (Object o : it) items.add(o);
            if (items.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            for (int i = 0; i < items.size(); i++) {
                indent(sb, level + 1);
                write(sb, items.get(i), level + 1);
                if (i + 1 < items.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, level);
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void quote(StringBuilder sb, String t) {
        sb.append('"');
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
