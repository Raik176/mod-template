package de.rhm176.build.model;

import java.util.List;
import java.util.Map;

public record MetadataTarget(
        LoaderType loader,
        McVersion minecraft,
        VersionRange minecraftRange,
        String loaderVersion,
        int javaVersion,
        boolean fabricApi,
        Map<String, List<String>> entrypoints,
        List<String> mixinConfigs,
        String accessWidener,
        Integer packFormat,
        boolean modMenu) {
    public MetadataTarget {
        entrypoints = entrypoints == null ? Map.of() : entrypoints;
        mixinConfigs = mixinConfigs == null ? List.of() : mixinConfigs;
    }

    public MetadataTarget(
            LoaderType loader,
            McVersion minecraft,
            VersionRange minecraftRange,
            String loaderVersion,
            int javaVersion,
            boolean fabricApi,
            Map<String, List<String>> entrypoints,
            List<String> mixinConfigs,
            String accessWidener,
            Integer packFormat) {
        this(loader, minecraft, minecraftRange, loaderVersion, javaVersion, fabricApi, entrypoints, mixinConfigs,
                accessWidener, packFormat, false);
    }
}
