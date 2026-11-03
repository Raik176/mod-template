package de.rhm176.build.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

public final class Json5 {
    private static final int MAX_DEPTH = 512;

    private Json5() {}

    public static boolean isCommented(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".json5") || lower.endsWith(".jsonc");
    }

    public static String outputPath(String path) {
        if (!isCommented(path)) return path;
        String base = path.substring(0, path.length() - ".json5".length());
        String lower = base.toLowerCase(Locale.ROOT);
        return lower.endsWith(".json") || lower.endsWith(".mcmeta") ? base : base + ".json";
    }

    public static List<String> sourcePaths(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) return List.of(path, path + "5", path + "c");
        if (lower.endsWith(".mcmeta")) return List.of(path, path + ".json5", path + ".jsonc");
        return List.of(path);
    }

    public static String toJson(String text, String indent) {
        for (int i = 0; i < indent.length(); i++) {
            if (indent.charAt(i) != ' ' && indent.charAt(i) != '\t') {
                throw new IllegalArgumentException("The JSON indent can only hold spaces and tabs");
            }
        }
        Parser p = new Parser(text, indent);
        p.skip();
        if (p.pos >= text.length()) return null;
        p.value(0);
        p.skip();
        if (p.pos != text.length()) throw p.error("Unexpected content after the JSON value");
        if (!indent.isEmpty()) p.out.append('\n');
        return p.out.toString();
    }

    private static boolean isIdentifierStart(char c) {
        return c == '$' || c == '_' || Character.isLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isLetterOrDigit(c);
    }

    private static boolean isWhitespace(char c) {
        return c == '\uFEFF' || Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029';
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    boolean pair = Character.isHighSurrogate(c)
                            && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1));
                    if (pair) {
                        sb.append(c).append(s.charAt(++i));
                    } else if (c < 0x20 || Character.isSurrogate(c)) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        final String s;
        final String indent;
        final StringBuilder out = new StringBuilder();
        int pos;

        Parser(String s, String indent) {
            this.s = s;
            this.indent = indent;
        }

        IllegalArgumentException error(String message) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < Math.min(pos, s.length()); i++) {
                if (s.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new IllegalArgumentException(message + " (line " + line + ", column " + column + ")");
        }

        boolean peek(char c) {
            return pos < s.length() && s.charAt(pos) == c;
        }

        void expect(char c) {
            if (!peek(c)) throw error("Expected '" + c + "'");
            pos++;
        }

        void skip() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (isWhitespace(c)) {
                    pos++;
                } else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/') {
                    pos += 2;
                    while (pos < s.length() && !isLineBreak(s.charAt(pos))) pos++;
                } else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '*') {
                    int end = s.indexOf("*/", pos + 2);
                    if (end < 0) throw error("Unterminated comment");
                    pos = end + 2;
                } else {
                    return;
                }
            }
        }

        void newline(int level) {
            if (indent.isEmpty()) return;
            out.append('\n');
            for (int i = 0; i < level; i++) out.append(indent);
        }

        void value(int level) {
            if (level > MAX_DEPTH) throw error("Nesting is too deep");
            if (pos >= s.length()) throw error("Unexpected end of input");
            char c = s.charAt(pos);
            if (c == '{') object(level);
            else if (c == '[') array(level);
            else if (c == '"' || c == '\'') out.append(quoted(string()));
            else if (word("true") || word("false") || word("null")) return;
            else number();
        }

        private String quoted(String text) {
            StringBuilder sb = new StringBuilder(text.length() + 2);
            quote(sb, text);
            return sb.toString();
        }

        boolean word(String word) {
            if (!s.startsWith(word, pos)) return false;
            int end = pos + word.length();
            if (end < s.length() && isIdentifierPart(s.charAt(end))) return false;
            pos = end;
            out.append(word);
            return true;
        }

        void object(int level) {
            pos++;
            out.append('{');
            boolean first = true;
            while (true) {
                skip();
                if (peek('}')) {
                    pos++;
                    if (!first) newline(level);
                    out.append('}');
                    return;
                }
                if (!first) out.append(',');
                newline(level + 1);
                key();
                skip();
                expect(':');
                out.append(indent.isEmpty() ? ":" : ": ");
                skip();
                value(level + 1);
                first = false;
                skip();
                if (peek(',')) {
                    pos++;
                } else if (!peek('}')) {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        void array(int level) {
            pos++;
            out.append('[');
            boolean first = true;
            while (true) {
                skip();
                if (peek(']')) {
                    pos++;
                    if (!first) newline(level);
                    out.append(']');
                    return;
                }
                if (!first) out.append(',');
                newline(level + 1);
                value(level + 1);
                first = false;
                skip();
                if (peek(',')) {
                    pos++;
                } else if (!peek(']')) {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        void key() {
            if (peek('"') || peek('\'')) {
                out.append(quoted(string()));
                return;
            }
            if (pos >= s.length() || !isIdentifierStart(s.charAt(pos))) throw error("Expected a key");
            int start = pos;
            while (pos < s.length() && isIdentifierPart(s.charAt(pos))) pos++;
            out.append(quoted(s.substring(start, pos)));
        }

        String string() {
            char quote = s.charAt(pos++);
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) throw error("Unterminated string");
                char c = s.charAt(pos++);
                if (c == quote) return sb.toString();
                if (c == '\\') {
                    escape(sb);
                } else if (c == '\n' || c == '\r') {
                    pos--;
                    throw error("Unterminated string");
                } else {
                    sb.append(c);
                }
            }
        }

        void escape(StringBuilder sb) {
            if (pos >= s.length()) throw error("Unterminated escape sequence");
            char e = s.charAt(pos++);
            switch (e) {
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'v' -> sb.append('\u000B');
                case '0' -> {
                    if (pos < s.length() && isDigit(s.charAt(pos))) {
                        pos--;
                        throw error("Invalid escape sequence");
                    }
                    sb.append('\0');
                }
                case 'x' -> sb.append((char) hexDigits(2));
                case 'u' -> sb.append((char) hexDigits(4));
                case '\r' -> {
                    if (peek('\n')) pos++;
                }
                case '\n', '\u2028', '\u2029' -> {}
                default -> {
                    if (isDigit(e)) {
                        pos--;
                        throw error("Invalid escape sequence");
                    }
                    sb.append(e);
                }
            }
        }

        int hexDigits(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                int digit = pos < s.length() ? hex(s.charAt(pos)) : -1;
                if (digit < 0) throw error("Expected " + count + " hexadecimal digits");
                value = value * 16 + digit;
                pos++;
            }
            return value;
        }

        void number() {
            boolean negative = false;
            if (peek('+') || peek('-')) {
                negative = s.charAt(pos) == '-';
                pos++;
            }
            if (s.startsWith("Infinity", pos) || s.startsWith("NaN", pos)) {
                throw error("Infinity and NaN cannot be written in JSON");
            }
            if (s.startsWith("0x", pos) || s.startsWith("0X", pos)) {
                pos += 2;
                int start = pos;
                while (pos < s.length() && hex(s.charAt(pos)) >= 0) pos++;
                if (start == pos) throw error("Expected hexadecimal digits");
                out.append(negative ? "-" : "").append(new BigInteger(s.substring(start, pos), 16));
                return;
            }
            int integerStart = pos;
            while (pos < s.length() && isDigit(s.charAt(pos))) pos++;
            String integer = s.substring(integerStart, pos);
            String fraction = "";
            if (peek('.')) {
                pos++;
                int fractionStart = pos;
                while (pos < s.length() && isDigit(s.charAt(pos))) pos++;
                fraction = s.substring(fractionStart, pos);
            }
            if (integer.isEmpty() && fraction.isEmpty()) {
                throw error(pos < s.length() ? "Unexpected character '" + s.charAt(pos) + "'" : "Unexpected end of input");
            }
            if (integer.length() > 1 && integer.charAt(0) == '0') {
                pos = integerStart;
                throw error("Numbers cannot have leading zeros");
            }
            String exponent = "";
            if (peek('e') || peek('E')) {
                int exponentStart = pos++;
                if (peek('+') || peek('-')) pos++;
                int digitsStart = pos;
                while (pos < s.length() && isDigit(s.charAt(pos))) pos++;
                if (digitsStart == pos) throw error("Expected exponent digits");
                exponent = s.substring(exponentStart, pos);
            }
            out.append(negative ? "-" : "")
                    .append(integer.isEmpty() ? "0" : integer)
                    .append(fraction.isEmpty() ? "" : "." + fraction)
                    .append(exponent);
        }
    }
}
