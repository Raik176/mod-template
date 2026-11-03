package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModMetadata {
    public enum Environment {
        BOTH("*"),
        CLIENT("client"),
        SERVER("server");

        final String fabricValue;

        Environment(String fabricValue) {
            this.fabricValue = fabricValue;
        }
    }

    public enum DependencyType {
        REQUIRED,
        RECOMMENDED,
        SUGGESTED,
        DISCOURAGED,
        INCOMPATIBLE
    }

    public enum Side {
        BOTH,
        CLIENT,
        SERVER
    }

    public enum Ordering {
        NONE,
        BEFORE,
        AFTER
    }

    public record Person(String name, Map<String, String> contact) {
        public Person {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Person name must not be blank");
            contact = contact == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(contact));
        }
    }

    public record Dependency(
            String modId,
            DependencyType type,
            VersionRange range,
            Side side,
            Ordering ordering,
            Set<LoaderType> loaders) {
        public Dependency {
            if (modId == null || modId.isBlank()) throw new IllegalArgumentException("Dependency modId must not be blank");
            loaders = loaders == null || loaders.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(loaders));
        }

        public boolean appliesTo(LoaderType loader) {
            return loaders.isEmpty() || loaders.contains(loader);
        }
    }

    private final String modId;
    private final String version;
    private final String name;
    private final String description;
    private final String license;
    private final String icon;
    private final String homepage;
    private final String sources;
    private final String issues;
    private final String updateUrl;
    private final String credits;
    private final Environment environment;
    private final Integer packFormat;
    private final boolean quilt;
    private final List<Person> authors;
    private final List<Person> contributors;
    private final List<Dependency> dependencies;
    private final Map<String, Object> custom;
    private final Map<LoaderType, String> loaderVersionRanges;
    private final ModMenuMetadata modMenu;

    private ModMetadata(Builder b) {
        if (b.modId == null || !b.modId.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new IllegalArgumentException(
                    "modId must be 2-64 characters of [a-z0-9_] starting with a letter (got '" + b.modId + "')");
        }
        if (b.version == null || b.version.isBlank()) throw new IllegalArgumentException("version must not be blank");
        if (b.name == null || b.name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        modId = b.modId;
        version = b.version;
        name = b.name;
        description = b.description == null ? "" : b.description;
        license = b.license == null || b.license.isBlank() ? "All Rights Reserved" : b.license;
        icon = b.icon;
        homepage = b.homepage;
        sources = b.sources;
        issues = b.issues;
        updateUrl = b.updateUrl;
        credits = b.credits;
        environment = b.environment;
        packFormat = b.packFormat;
        quilt = b.quilt;
        authors = List.copyOf(b.authors);
        contributors = List.copyOf(b.contributors);
        dependencies = List.copyOf(b.dependencies);
        custom = Collections.unmodifiableMap(new LinkedHashMap<>(b.custom));
        loaderVersionRanges = Collections.unmodifiableMap(new EnumMap<LoaderType, String>(b.loaderVersionRanges));
        modMenu = b.modMenu;
        if (modMenu != null && modMenu.parent() != null && modMenu.parent().id().equals(modId)) {
            throw new IllegalArgumentException("A mod can't be its own Mod Menu parent ('" + modId + "')");
        }
    }

    public String modId() { return modId; }
    public String version() { return version; }
    public String name() { return name; }
    public String description() { return description; }
    public String license() { return license; }
    public String icon() { return icon; }
    public String homepage() { return homepage; }
    public String sources() { return sources; }
    public String issues() { return issues; }
    public String updateUrl() { return updateUrl; }
    public String credits() { return credits; }
    public Environment environment() { return environment; }
    public Integer packFormat() { return packFormat; }
    public boolean quilt() { return quilt; }
    public List<Person> authors() { return authors; }
    public List<Person> contributors() { return contributors; }
    public List<Dependency> dependencies() { return dependencies; }
    public Map<String, Object> custom() { return custom; }
    public String loaderVersionRange(LoaderType loader) { return loaderVersionRanges.get(loader); }
    public ModMenuMetadata modMenu() { return modMenu; }

    public static Builder builder() {
        return new Builder();
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modId", modId);
        m.put("version", version);
        m.put("name", name);
        m.put("description", description);
        m.put("license", license);
        putIfSet(m, "icon", icon);
        putIfSet(m, "homepage", homepage);
        putIfSet(m, "sources", sources);
        putIfSet(m, "issues", issues);
        putIfSet(m, "updateUrl", updateUrl);
        putIfSet(m, "credits", credits);
        m.put("environment", environment.name());
        if (packFormat != null) m.put("packFormat", (long) packFormat);
        m.put("quilt", quilt);
        m.put("authors", personsToJson(authors));
        m.put("contributors", personsToJson(contributors));
        List<Object> deps = new ArrayList<>();
        for (Dependency d : dependencies) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("modId", d.modId());
            dm.put("type", d.type().name());
            dm.put("range", d.range().toMaven());
            dm.put("side", d.side().name());
            dm.put("ordering", d.ordering().name());
            List<Object> loaders = new ArrayList<>();
            for (LoaderType l : d.loaders()) loaders.add(l.id());
            dm.put("loaders", loaders);
            deps.add(dm);
        }
        m.put("dependencies", deps);
        m.put("custom", custom);
        Map<String, Object> ranges = new LinkedHashMap<>();
        loaderVersionRanges.forEach((k, v) -> ranges.put(k.id(), v));
        m.put("loaderVersionRanges", ranges);
        if (modMenu != null) m.put("modMenu", modMenu.toJson());
        return Json.write(m);
    }

    @SuppressWarnings("unchecked")
    public static ModMetadata fromJson(String json) {
        Map<String, Object> m = Json.parseObject(json);
        Builder b = builder()
                .modId((String) m.get("modId"))
                .version((String) m.get("version"))
                .name((String) m.get("name"))
                .description((String) m.get("description"))
                .license((String) m.get("license"))
                .icon((String) m.get("icon"))
                .homepage((String) m.get("homepage"))
                .sources((String) m.get("sources"))
                .issues((String) m.get("issues"))
                .updateUrl((String) m.get("updateUrl"))
                .credits((String) m.get("credits"))
                .environment(Environment.valueOf((String) m.get("environment")))
                .quilt(Boolean.TRUE.equals(m.get("quilt")));
        if (m.get("packFormat") != null) b.packFormat(((Number) m.get("packFormat")).intValue());
        for (Object o : (List<Object>) m.get("authors")) b.author(personFromJson((Map<String, Object>) o));
        for (Object o : (List<Object>) m.get("contributors")) b.contributor(personFromJson((Map<String, Object>) o));
        for (Object o : (List<Object>) m.get("dependencies")) {
            Map<String, Object> dm = (Map<String, Object>) o;
            Set<LoaderType> loaders = EnumSet.noneOf(LoaderType.class);
            for (Object l : (List<Object>) dm.get("loaders")) loaders.add(LoaderType.fromId((String) l));
            b.dependency(new Dependency(
                    (String) dm.get("modId"),
                    DependencyType.valueOf((String) dm.get("type")),
                    VersionRange.parse((String) dm.get("range")),
                    Side.valueOf((String) dm.get("side")),
                    Ordering.valueOf((String) dm.get("ordering")),
                    loaders));
        }
        b.custom((Map<String, Object>) m.get("custom"));
        ((Map<String, Object>) m.get("loaderVersionRanges"))
                .forEach((k, v) -> b.loaderVersionRange(LoaderType.fromId(k), (String) v));
        if (m.get("modMenu") != null) b.modMenu(ModMenuMetadata.fromJson((Map<String, Object>) m.get("modMenu")));
        return b.build();
    }

    private static void putIfSet(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isBlank()) m.put(key, value);
    }

    private static List<Object> personsToJson(List<Person> persons) {
        List<Object> out = new ArrayList<>();
        for (Person p : persons) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("name", p.name());
            pm.put("contact", new LinkedHashMap<String, Object>(p.contact()));
            out.add(pm);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Person personFromJson(Map<String, Object> m) {
        Map<String, String> contact = new LinkedHashMap<>();
        ((Map<String, Object>) m.get("contact")).forEach((k, v) -> contact.put(k, (String) v));
        return new Person((String) m.get("name"), contact);
    }

    public static final class Builder {
        private String modId;
        private String version;
        private String name;
        private String description;
        private String license;
        private String icon;
        private String homepage;
        private String sources;
        private String issues;
        private String updateUrl;
        private String credits;
        private Environment environment = Environment.BOTH;
        private Integer packFormat;
        private boolean quilt;
        private final List<Person> authors = new ArrayList<>();
        private final List<Person> contributors = new ArrayList<>();
        private final List<Dependency> dependencies = new ArrayList<>();
        private final Map<String, Object> custom = new LinkedHashMap<>();
        private final Map<LoaderType, String> loaderVersionRanges = new EnumMap<>(LoaderType.class);
        private ModMenuMetadata modMenu;

        private Builder() {}

        public Builder modId(String v) { modId = v; return this; }
        public Builder version(String v) { version = v; return this; }
        public Builder name(String v) { name = v; return this; }
        public Builder description(String v) { description = v; return this; }
        public Builder license(String v) { license = v; return this; }
        public Builder icon(String v) { icon = v; return this; }
        public Builder homepage(String v) { homepage = v; return this; }
        public Builder sources(String v) { sources = v; return this; }
        public Builder issues(String v) { issues = v; return this; }
        public Builder updateUrl(String v) { updateUrl = v; return this; }
        public Builder credits(String v) { credits = v; return this; }
        public Builder environment(Environment v) { environment = v; return this; }
        public Builder packFormat(Integer v) { packFormat = v; return this; }
        public Builder quilt(boolean v) { quilt = v; return this; }
        public Builder author(Person p) { authors.add(p); return this; }
        public Builder author(String name) { return author(new Person(name, null)); }
        public Builder contributor(Person p) { contributors.add(p); return this; }
        public Builder contributor(String name) { return contributor(new Person(name, null)); }
        public Builder dependency(Dependency d) { dependencies.add(d); return this; }
        public Builder modMenu(ModMenuMetadata v) { modMenu = v; return this; }

        public Builder dependency(String modId, DependencyType type, String range) {
            return dependency(new Dependency(
                    modId, type, VersionRange.parse(range), Side.BOTH, Ordering.NONE, Set.of()));
        }

        public Builder custom(Map<String, Object> values) {
            if (values != null) custom.putAll(values);
            return this;
        }

        public Builder custom(String key, Object value) { custom.put(key, value); return this; }

        public Builder loaderVersionRange(LoaderType loader, String mavenRange) {
            if (mavenRange != null && !mavenRange.isBlank()) loaderVersionRanges.put(loader, mavenRange);
            return this;
        }

        public ModMetadata build() { return new ModMetadata(this); }
    }
}
