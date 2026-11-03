package de.rhm176.build.model;

public final class MixinExtrasRules {
    private MixinExtrasRules() {}

    public static final String DEFAULT_VERSION = "0.5.5";

    public enum Delivery {
        PROVIDED_BY_LOADER,
        BUNDLED,
        UNSUPPORTED
    }

    public static Delivery delivery(LoaderType loader, McVersion minecraft, String loaderVersion) {
        return switch (loader) {
            case FABRIC, QUILT -> compareDotted(loaderVersion, "0.15.0") >= 0 ? Delivery.PROVIDED_BY_LOADER : Delivery.BUNDLED;
            case NEOFORGE -> compareDotted(loaderVersion, "20.2.84") >= 0 ? Delivery.PROVIDED_BY_LOADER : Delivery.BUNDLED;
            case FORGE -> minecraft.isAtLeast("1.18.2") ? Delivery.BUNDLED : Delivery.UNSUPPORTED;
        };
    }

    public static String unsupportedReason(LoaderType loader, McVersion minecraft) {
        if (loader == LoaderType.FORGE) {
            return "Forge older than Minecraft 1.18.2 has no Mixin that MixinExtras supports";
        }
        return loader.displayName() + " " + minecraft + " is not supported";
    }

    public static String bundledArtifact(LoaderType loader) {
        return switch (loader) {
            case FABRIC, QUILT -> "mixinextras-fabric";
            case FORGE -> "mixinextras-forge";
            case NEOFORGE -> "mixinextras-neoforge";
        };
    }

    public static int compareDotted(String a, String b) {
        int[] x = numbers(a);
        int[] y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] numbers(String version) {
        String text = version == null ? "" : version.strip();
        int[] parts = new int[8];
        int count = 0;
        int i = 0;
        while (i < text.length() && count < parts.length) {
            if (!Character.isDigit(text.charAt(i))) break;
            int value = 0;
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                value = Math.min(value, 100_000_000) * 10 + (text.charAt(i) - '0');
                i++;
            }
            parts[count++] = value;
            if (i < text.length() && text.charAt(i) == '.') i++;
            else break;
        }
        return java.util.Arrays.copyOf(parts, count);
    }
}
