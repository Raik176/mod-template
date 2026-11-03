package de.rhm176.build.model;

public enum LoaderType {
    FABRIC("fabric", "Fabric", "1.14.4", true),
    QUILT("quilt", "Quilt", "1.14.4", false),
    FORGE("forge", "Forge", "1.14.4", true),
    NEOFORGE("neoforge", "NeoForge", "1.20.2", true);

    private final String id;
    private final String displayName;
    private final McVersion minimum;
    private final boolean buildable;

    LoaderType(String id, String displayName, String minimum, boolean buildable) {
        this.id = id;
        this.displayName = displayName;
        this.minimum = McVersion.parse(minimum);
        this.buildable = buildable;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public McVersion minimum() {
        return minimum;
    }

    public boolean buildable() {
        return buildable;
    }

    public boolean supports(McVersion version) {
        return version.isAtLeast(McVersion.MINIMUM) && version.isAtLeast(minimum);
    }

    public static LoaderType fromId(String id) {
        for (LoaderType type : values()) if (type.id.equals(id)) return type;
        throw new IllegalArgumentException("Unknown loader '" + id + "'");
    }

    public String branchName() {
        return id;
    }

    public static LoaderType fromBranchName(String branch) {
        return fromId(branch);
    }

    public String nodeName(String minecraftVersion) {
        return minecraftVersion + "-" + id;
    }

    public static String minecraftOfVcsEntry(String entry) {
        int dash = entry.lastIndexOf('-');
        if (dash > 0) {
            String suffix = entry.substring(dash + 1);
            for (LoaderType type : values()) if (type.id.equals(suffix)) return entry.substring(0, dash);
        }
        return entry;
    }

    public String metadataPath(McVersion version) {
        return switch (this) {
            case FABRIC -> "fabric.mod.json";
            case QUILT -> "quilt.mod.json";
            case NEOFORGE -> version.isAtLeast("1.20.5") ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
            default -> "META-INF/mods.toml";
        };
    }
}
