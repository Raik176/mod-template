package de.rhm176.build.model.changelog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemVer(int major, int minor, int patch, List<String> prerelease) implements Comparable<SemVer> {
    private static final Pattern PATTERN = Pattern.compile(
            "(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?");

    public enum Bump {
        NONE,
        PATCH,
        MINOR,
        MAJOR
    }

    public SemVer {
        prerelease = List.copyOf(prerelease);
    }

    public static Optional<SemVer> parse(String text) {
        if (text == null) return Optional.empty();
        Matcher m = PATTERN.matcher(text.strip());
        if (!m.matches()) return Optional.empty();
        try {
            int major = Integer.parseInt(m.group(1));
            int minor = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            int patch = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            List<String> pre = new ArrayList<>();
            if (m.group(4) != null) pre.addAll(List.of(m.group(4).split("\\.")));
            return Optional.of(new SemVer(major, minor, patch, pre));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean isPrerelease() {
        return !prerelease.isEmpty();
    }

    public SemVer next(Bump bump) {
        return switch (bump) {
            case MAJOR -> new SemVer(major + 1, 0, 0, List.of());
            case MINOR -> new SemVer(major, minor + 1, 0, List.of());
            case PATCH -> new SemVer(major, minor, patch + 1, List.of());
            case NONE -> this;
        };
    }

    @Override
    public int compareTo(SemVer other) {
        int c = Integer.compare(major, other.major);
        if (c != 0) return c;
        c = Integer.compare(minor, other.minor);
        if (c != 0) return c;
        c = Integer.compare(patch, other.patch);
        if (c != 0) return c;
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return Boolean.compare(prerelease.isEmpty(), other.prerelease.isEmpty());
        }
        int shared = Math.min(prerelease.size(), other.prerelease.size());
        for (int i = 0; i < shared; i++) {
            c = compareIdentifier(prerelease.get(i), other.prerelease.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    private static int compareIdentifier(String a, String b) {
        boolean aNumeric = a.chars().allMatch(Character::isDigit);
        boolean bNumeric = b.chars().allMatch(Character::isDigit);
        if (aNumeric && bNumeric) {
            String x = a.replaceFirst("^0+(?=.)", "");
            String y = b.replaceFirst("^0+(?=.)", "");
            return x.length() != y.length() ? Integer.compare(x.length(), y.length()) : x.compareTo(y);
        }
        if (aNumeric) return -1;
        if (bNumeric) return 1;
        return a.compareTo(b);
    }

    @Override
    public String toString() {
        String base = major + "." + minor + "." + patch;
        return prerelease.isEmpty() ? base : base + "-" + String.join(".", prerelease);
    }
}
