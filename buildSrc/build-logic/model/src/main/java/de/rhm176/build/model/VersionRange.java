package de.rhm176.build.model;

public final class VersionRange {
    public static final VersionRange ANY = new VersionRange(null, false, null, false);

    private final String lower;
    private final boolean lowerInclusive;
    private final String upper;
    private final boolean upperInclusive;

    private VersionRange(String lower, boolean lowerInclusive, String upper, boolean upperInclusive) {
        this.lower = lower;
        this.lowerInclusive = lowerInclusive;
        this.upper = upper;
        this.upperInclusive = upperInclusive;
    }

    public static VersionRange between(String lower, boolean lowerInclusive, String upper, boolean upperInclusive) {
        if (lower == null && upper == null) return ANY;
        return new VersionRange(lower, lowerInclusive, upper, upperInclusive);
    }

    public static VersionRange exactly(String version) {
        return new VersionRange(version, true, version, true);
    }

    public static VersionRange atLeast(String version) {
        return new VersionRange(version, true, null, false);
    }

    public static VersionRange parse(String spec) {
        if (spec == null) return ANY;
        String s = spec.trim();
        if (s.isEmpty() || s.equals("*")) return ANY;
        char open = s.charAt(0);
        if (open != '[' && open != '(') return exactly(s);
        char close = s.charAt(s.length() - 1);
        if (close != ']' && close != ')') throw new IllegalArgumentException("Malformed version range '" + spec + "'");
        String body = s.substring(1, s.length() - 1);
        int comma = body.indexOf(',');
        if (comma < 0) {
            if (open != '[' || close != ']') throw new IllegalArgumentException("Malformed version range '" + spec + "'");
            return exactly(body.trim());
        }
        String lo = body.substring(0, comma).trim();
        String hi = body.substring(comma + 1).trim();
        return between(lo.isEmpty() ? null : lo, open == '[', hi.isEmpty() ? null : hi, close == ']');
    }

    public boolean isAny() {
        return lower == null && upper == null;
    }

    public boolean isExact() {
        return lower != null && lower.equals(upper) && lowerInclusive && upperInclusive;
    }

    public String lower() {
        return lower;
    }

    public String upper() {
        return upper;
    }

    public String toMaven() {
        if (isAny()) return "[0,)";
        if (isExact()) return "[" + lower + "]";
        return (lowerInclusive ? "[" : "(") + (lower == null ? "" : lower) + ","
                + (upper == null ? "" : upper) + (upperInclusive ? "]" : ")");
    }

    public String toFabric() {
        if (isAny()) return "*";
        if (isExact()) return lower;
        StringBuilder sb = new StringBuilder();
        if (lower != null) sb.append(lowerInclusive ? ">=" : ">").append(lower);
        if (upper != null) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(upperInclusive ? "<=" : "<").append(upper);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toMaven();
    }
}
