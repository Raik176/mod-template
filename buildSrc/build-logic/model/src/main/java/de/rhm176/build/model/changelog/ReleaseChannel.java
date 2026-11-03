package de.rhm176.build.model.changelog;

import java.util.Locale;

public enum ReleaseChannel {
    STABLE,
    BETA,
    ALPHA;

    public static ReleaseChannel of(String version) {
        String core = version.strip();
        int plus = core.indexOf('+');
        if (plus >= 0) core = core.substring(0, plus);
        int dash = core.indexOf('-');
        if (dash < 0) return STABLE;
        return core.substring(dash + 1).toLowerCase(Locale.ROOT).contains("alpha") ? ALPHA : BETA;
    }

    public static ReleaseChannel parse(String name) {
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "stable", "release" -> STABLE;
            case "beta" -> BETA;
            case "alpha" -> ALPHA;
            default -> throw new IllegalArgumentException(
                    "Unknown release channel '" + name + "' (expected stable, beta or alpha)");
        };
    }
}
