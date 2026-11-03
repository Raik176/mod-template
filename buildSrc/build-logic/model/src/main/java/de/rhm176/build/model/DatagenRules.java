package de.rhm176.build.model;

public final class DatagenRules {
    private DatagenRules() {}

    public static final String FIRST_MINECRAFT = "1.18";

    public static boolean supports(McVersion minecraft) {
        return minecraft.isAtLeast(FIRST_MINECRAFT);
    }
}
