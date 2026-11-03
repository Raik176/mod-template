package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MetadataGenerators {
    private MetadataGenerators() {}

    public static GeneratedOutput generate(ModMetadata metadata, MetadataTarget target) {
        List<GeneratedFile> files = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        LoaderType loader = target.loader();
        switch (loader) {
            case FABRIC -> {
                files.add(new GeneratedFile(
                        loader.metadataPath(target.minecraft()),
                        Json.write(FabricModJsonGenerator.build(metadata, target, warnings))));
                if (metadata.quilt()) {
                    files.add(new GeneratedFile(
                            LoaderType.QUILT.metadataPath(target.minecraft()),
                            Json.write(QuiltModJsonGenerator.build(metadata, target, warnings))));
                }
            }
            case QUILT -> files.add(new GeneratedFile(
                    loader.metadataPath(target.minecraft()),
                    Json.write(QuiltModJsonGenerator.build(metadata, target, warnings))));
            case FORGE, NEOFORGE -> {
                files.add(new GeneratedFile(
                        loader.metadataPath(target.minecraft()),
                        ModsTomlGenerator.build(metadata, target, warnings)));
                packMcmeta(metadata, target, files, warnings);
            }
        }
        return new GeneratedOutput(files, warnings);
    }

    private static void packMcmeta(
            ModMetadata metadata, MetadataTarget target, List<GeneratedFile> files, List<String> warnings) {
        Integer format = metadata.packFormat() != null ? metadata.packFormat() : target.packFormat();
        if (format == null) {
            warnings.add("pack.mcmeta was not generated: the pack format of Minecraft " + target.minecraft()
                    + " could not be determined. Set modMetadata.packFormat or ship your own pack.mcmeta.");
            return;
        }
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("description", metadata.name());
        pack.put("pack_format", (long) format);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pack", pack);
        files.add(new GeneratedFile("pack.mcmeta", Json.write(root)));
    }
}
