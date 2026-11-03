package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LoaderMatrix {
    private LoaderMatrix() {}

    public static List<String> validate(String minecraftVersion, Set<String> loaderIds) {
        List<String> problems = new ArrayList<>();
        McVersion version;
        try {
            version = McVersion.parse(minecraftVersion);
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return problems;
        }
        if (!version.isAtLeast(McVersion.MINIMUM)) {
            problems.add("Minecraft " + version + " is older than the supported minimum " + McVersion.MINIMUM);
        }
        if (loaderIds.isEmpty()) {
            problems.add("Minecraft " + version + " enables no loader (expected one of fabric-loader, forge-loader, neoforge-loader)");
        }
        for (String id : loaderIds) {
            LoaderType type;
            try {
                type = LoaderType.fromId(id);
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
                continue;
            }
            if (!type.buildable()) {
                problems.add(type.displayName() + " has no build target; Quilt loads the Fabric jar");
            } else if (!type.supports(version)) {
                problems.add(type.displayName() + " is supported from Minecraft " + type.minimum()
                        + " (got " + version + ")");
            }
        }
        return problems;
    }
}
