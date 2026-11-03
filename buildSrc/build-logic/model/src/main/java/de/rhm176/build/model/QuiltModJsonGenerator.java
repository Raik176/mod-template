package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QuiltModJsonGenerator {
    private QuiltModJsonGenerator() {}

    private static final Map<String, String> ENTRYPOINT_KEYS =
            Map.of("main", "init", "client", "client_init", "server", "server_init");

    static Map<String, Object> build(ModMetadata m, MetadataTarget t, List<String> warnings) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1L);

        Map<String, Object> loader = new LinkedHashMap<>();
        loader.put("group", m.modId());
        loader.put("id", m.modId());
        loader.put("version", m.version());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", m.name());
        if (!m.description().isEmpty()) metadata.put("description", m.description());
        Map<String, Object> contributors = new LinkedHashMap<>();
        for (ModMetadata.Person p : m.authors()) contributors.put(p.name(), "Author");
        for (ModMetadata.Person p : m.contributors()) contributors.put(p.name(), "Contributor");
        if (!contributors.isEmpty()) metadata.put("contributors", contributors);
        Map<String, Object> contact = new LinkedHashMap<>();
        if (m.homepage() != null) contact.put("homepage", m.homepage());
        if (m.sources() != null) contact.put("sources", m.sources());
        if (m.issues() != null) contact.put("issues", m.issues());
        if (!contact.isEmpty()) metadata.put("contact", contact);
        if (m.icon() != null) metadata.put("icon", m.icon());
        metadata.put("license", m.license());
        loader.put("metadata", metadata);
        loader.put("intermediate_mappings", "net.fabricmc:intermediary");

        if (!t.entrypoints().isEmpty()) {
            Map<String, Object> entrypoints = new LinkedHashMap<>();
            t.entrypoints().forEach((key, values) ->
                    entrypoints.put(ENTRYPOINT_KEYS.getOrDefault(key, key), new ArrayList<Object>(values)));
            loader.put("entrypoints", entrypoints);
        }

        List<Object> depends = new ArrayList<>();
        depends.add(dep("minecraft", t.minecraftRange().toFabric(), false));
        List<Object> breaks = new ArrayList<>();
        for (ModMetadata.Dependency d : m.dependencies()) {
            if (!d.appliesTo(LoaderType.QUILT) && !d.appliesTo(LoaderType.FABRIC)) continue;
            String predicate = d.range().toFabric();
            switch (d.type()) {
                case REQUIRED -> depends.add(dep(d.modId(), predicate, false));
                case RECOMMENDED, SUGGESTED -> depends.add(dep(d.modId(), predicate, true));
                case INCOMPATIBLE -> breaks.add(dep(d.modId(), predicate, false));
                case DISCOURAGED -> warnings.add("quilt.mod.json cannot express a discouraged dependency on '"
                        + d.modId() + "'; skipped");
            }
        }
        loader.put("depends", depends);
        if (!breaks.isEmpty()) loader.put("breaks", breaks);
        root.put("quilt_loader", loader);

        if (!t.mixinConfigs().isEmpty()) root.put("mixin", new ArrayList<Object>(t.mixinConfigs()));
        if (t.accessWidener() != null) root.put("access_widener", t.accessWidener());
        Map<String, Object> minecraft = new LinkedHashMap<>();
        minecraft.put("environment", switch (m.environment()) {
            case BOTH -> "*";
            case CLIENT -> "client";
            case SERVER -> "dedicated_server";
        });
        root.put("minecraft", minecraft);
        if (t.modMenu() && m.modMenu() != null) {
            Map<String, Object> modMenu = m.modMenu().toMetadata();
            if (!modMenu.isEmpty()) root.put("modmenu", modMenu);
        }
        return root;
    }

    private static Map<String, Object> dep(String id, String versions, boolean optional) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("versions", versions);
        if (optional) d.put("optional", true);
        return d;
    }
}
