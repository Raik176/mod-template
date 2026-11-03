package de.rhm176.build.model;

import java.util.Optional;

public final class ForgeMixinRules {
    private ForgeMixinRules() {}

    public static String refmapName(String modId) {
        return modId + ".refmap.json";
    }

    public static boolean usesSrgNames(McVersion minecraft) {
        return minecraft.isBefore("1.20.2");
    }

    public static boolean needsRefmap(McVersion minecraft) {
        return minecraft.isBefore("1.20.2");
    }

    public static boolean usesLegacyPlugin(McVersion minecraft) {
        return minecraft.isAtLeast("1.17") && minecraft.isBefore("1.20.2");
    }

    public static boolean generatesOwnRefmap(McVersion minecraft) {
        return needsRefmap(minecraft) && !usesLegacyPlugin(minecraft);
    }

    public static boolean needsMixinBootstrap(McVersion minecraft) {
        return minecraft.isAtLeast("1.14") && minecraft.isBefore("1.15");
    }

    public static Optional<String> minimumForgeVersion(McVersion minecraft) {
        if (minecraft.isAtLeast("1.14") && minecraft.isBefore("1.15")) return Optional.of("28.1.45");
        if (minecraft.isAtLeast("1.15") && minecraft.isBefore("1.16")) return Optional.of("31.2.44");
        if (minecraft.isAtLeast("1.16") && minecraft.isBefore("1.16.2")) return Optional.of("32.0.72");
        return Optional.empty();
    }
}
