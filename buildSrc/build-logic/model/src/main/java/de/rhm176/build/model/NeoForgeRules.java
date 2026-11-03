package de.rhm176.build.model;

public final class NeoForgeRules {
    private NeoForgeRules() {}

    public static boolean usesNeoGradle(McVersion minecraft) {
        return minecraft.isBefore("1.20.4");
    }
}
