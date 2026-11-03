package de.rhm176.build.model;

import java.util.List;

final class Toml {
    private Toml() {}

    static String string(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    static String text(String value) {
        if (value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return string(value);
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.contains("'''")) return "'''\n" + normalized + "\n'''";
        return "\"\"\"\n" + normalized.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"") + "\n\"\"\"";
    }

    static String array(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(string(values.get(i)));
        }
        return sb.append(']').toString();
    }
}
