package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FabricModJsonGenerator {
    private FabricModJsonGenerator() {}

    static Map<String, Object> build(ModMetadata m, MetadataTarget t, List<String> warnings) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", 1L);
        json.put("id", m.modId());
        json.put("version", m.version());
        json.put("name", m.name());
        if (!m.description().isEmpty()) json.put("description", m.description());
        if (!m.authors().isEmpty()) json.put("authors", persons(m.authors()));
        if (!m.contributors().isEmpty()) json.put("contributors", persons(m.contributors()));

        Map<String, Object> contact = new LinkedHashMap<>();
        if (m.homepage() != null) contact.put("homepage", m.homepage());
        if (m.sources() != null) contact.put("sources", m.sources());
        if (m.issues() != null) contact.put("issues", m.issues());
        if (!contact.isEmpty()) json.put("contact", contact);

        json.put("license", m.license());
        if (m.icon() != null) json.put("icon", m.icon());
        json.put("environment", m.environment().fabricValue);

        if (!t.entrypoints().isEmpty()) {
            Map<String, Object> entrypoints = new LinkedHashMap<>();
            t.entrypoints().forEach((key, values) -> entrypoints.put(key, new ArrayList<Object>(values)));
            json.put("entrypoints", entrypoints);
        }
        if (!t.mixinConfigs().isEmpty()) json.put("mixins", new ArrayList<Object>(t.mixinConfigs()));
        if (t.accessWidener() != null) json.put("accessWidener", t.accessWidener());

        Map<String, Object> depends = new LinkedHashMap<>();
        depends.put("fabricloader", ">=" + t.loaderVersion());
        depends.put("minecraft", t.minecraftRange().toFabric());
        depends.put("java", ">=" + t.javaVersion());
        if (t.fabricApi()) depends.put("fabric-api", "*");
        Map<String, Object> recommends = new LinkedHashMap<>();
        Map<String, Object> suggests = new LinkedHashMap<>();
        Map<String, Object> conflicts = new LinkedHashMap<>();
        Map<String, Object> breaks = new LinkedHashMap<>();

        for (ModMetadata.Dependency d : m.dependencies()) {
            if (!d.appliesTo(LoaderType.FABRIC)) continue;
            String predicate = d.range().toFabric();
            switch (d.type()) {
                case REQUIRED -> depends.put(d.modId(), predicate);
                case RECOMMENDED -> recommends.put(d.modId(), predicate);
                case SUGGESTED -> suggests.put(d.modId(), predicate);
                case DISCOURAGED -> conflicts.put(d.modId(), predicate);
                case INCOMPATIBLE -> breaks.put(d.modId(), predicate);
            }
        }
        json.put("depends", depends);
        if (!recommends.isEmpty()) json.put("recommends", recommends);
        if (!suggests.isEmpty()) json.put("suggests", suggests);
        if (!conflicts.isEmpty()) json.put("conflicts", conflicts);
        if (!breaks.isEmpty()) json.put("breaks", breaks);
        Map<String, Object> custom = new LinkedHashMap<>(m.custom());
        if (t.modMenu() && m.modMenu() != null) {
            Map<String, Object> modMenu = m.modMenu().toMetadata();
            warnings.addAll(m.modMenu().warnings());
            Object own = custom.get("modmenu");
            if (own != null && !(own instanceof Map)) {
                warnings.add("custom(\"modmenu\") is not an object, so the modMenu { } settings were left out of fabric.mod.json");
            } else if (!modMenu.isEmpty() || own != null) {
                if (own instanceof Map<?, ?> ownMap) ownMap.forEach((k, v) -> modMenu.put(String.valueOf(k), v));
                custom.put("modmenu", modMenu);
            }
        }
        if (!custom.isEmpty()) json.put("custom", custom);
        return json;
    }

    private static List<Object> persons(List<ModMetadata.Person> persons) {
        List<Object> out = new ArrayList<>();
        for (ModMetadata.Person p : persons) {
            if (p.contact().isEmpty()) {
                out.add(p.name());
            } else {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("name", p.name());
                o.put("contact", new LinkedHashMap<String, Object>(p.contact()));
                out.add(o);
            }
        }
        return out;
    }
}
