package de.rhm176.build.model;

import java.util.Arrays;

public final class McVersion implements Comparable<McVersion> {
    public static final McVersion MINIMUM = parse("1.14.4");
    public static final McVersion FIRST_UNOBFUSCATED = parse("26.1");

    private final int[] parts;
    private final String text;

    private McVersion(String text, int[] parts) {
        this.text = text;
        this.parts = parts;
    }

    public static McVersion parse(String text) {
        if (text == null || !text.matches("\\d+(\\.\\d+)+")) {
            throw new IllegalArgumentException("Not a Minecraft release version: '" + text + "'");
        }
        String[] split = text.split("\\.");
        int[] parts = new int[split.length];
        for (int i = 0; i < split.length; i++) parts[i] = Integer.parseInt(split[i]);
        return new McVersion(text, parts);
    }

    public static int compare(String a, String b) {
        return parse(a).compareTo(parse(b));
    }

    public boolean isAtLeast(McVersion other) {
        return compareTo(other) >= 0;
    }

    public boolean isAtLeast(String other) {
        return isAtLeast(parse(other));
    }

    public boolean isBefore(String other) {
        return compareTo(parse(other)) < 0;
    }

    public int javaVersion() {
        if (isBefore("1.5")) return 5;
        if (isBefore("1.6")) return 6;
        if (isBefore("1.17")) return 8;
        if (isBefore("1.18")) return 16;
        if (isBefore("1.20.5")) return 17;
        if (isBefore("26.1")) return 21;
        return 25;
    }

    @Override
    public int compareTo(McVersion other) {
        int length = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < length; i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof McVersion v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        int end = parts.length;
        while (end > 1 && parts[end - 1] == 0) end--;
        return Arrays.hashCode(Arrays.copyOf(parts, end));
    }

    @Override
    public String toString() {
        return text;
    }
}
