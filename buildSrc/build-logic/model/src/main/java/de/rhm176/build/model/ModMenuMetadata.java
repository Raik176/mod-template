package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ModMenuMetadata {
    public enum Badge {
        LIBRARY("library"),
        DEPRECATED("deprecated");

        private final String id;

        Badge(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Badge fromId(String id) {
            for (Badge b : values()) if (b.id.equals(id)) return b;
            throw new IllegalArgumentException(
                    "Mod Menu supports the badges 'library' and 'deprecated', not '" + id + "'");
        }
    }

    public static final List<String> LINK_TYPES = List.of(
            "buymeacoffee", "coindrop", "crowdin", "curseforge", "discord", "donate", "flattr", "github_releases",
            "github_sponsors", "kofi", "liberapay", "mastodon", "modrinth", "opencollective", "patreon", "paypal",
            "reddit", "twitch", "twitter", "wiki", "youtube");

    private static final Set<String> NEWER_LINK_TYPES = Set.of("bluesky");

    private static final Pattern BARE_TYPE = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern TRANSLATION_KEY = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)+");
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"]+");

    public record Parent(String id, String name, String description, String icon, Set<Badge> badges) {
        public Parent {
            if (id == null || !MOD_ID.matcher(id).matches()) {
                throw new IllegalArgumentException(
                        "Mod Menu parent id must be 2-64 characters of [a-z0-9_-] starting with a letter (got '" + id + "')");
            }
            name = blankToNull(name);
            description = blankToNull(description);
            icon = blankToNull(icon);
            badges = badges == null || badges.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(badges));
        }

        public Parent(String id) {
            this(id, null, null, null, null);
        }

        public boolean isReference() {
            return name == null && description == null && icon == null && badges.isEmpty();
        }

        Object toJson() {
            if (isReference()) return id;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            if (name != null) m.put("name", name);
            if (description != null) m.put("description", description);
            if (icon != null) m.put("icon", icon);
            if (!badges.isEmpty()) m.put("badges", badgeIds(badges));
            return m;
        }

        @SuppressWarnings("unchecked")
        static Parent fromJson(Object o) {
            if (o instanceof String s) return new Parent(s);
            Map<String, Object> m = (Map<String, Object>) o;
            Set<Badge> badges = EnumSet.noneOf(Badge.class);
            Object list = m.get("badges");
            if (list != null) for (Object b : (List<Object>) list) badges.add(Badge.fromId((String) b));
            return new Parent(
                    (String) m.get("id"), (String) m.get("name"), (String) m.get("description"),
                    (String) m.get("icon"), badges);
        }
    }

    private final Map<String, String> links;
    private final Set<Badge> badges;
    private final Parent parent;
    private final Boolean updateChecker;
    private final boolean autoLinks;

    private ModMenuMetadata(Builder b) {
        links = Collections.unmodifiableMap(new LinkedHashMap<>(b.links));
        badges = b.badges.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(b.badges));
        parent = b.parent;
        updateChecker = b.updateChecker;
        autoLinks = b.autoLinks;
    }

    public Map<String, String> links() { return links; }
    public Set<Badge> badges() { return badges; }
    public Parent parent() { return parent; }
    public Boolean updateChecker() { return updateChecker; }
    public boolean autoLinks() { return autoLinks; }

    public static Builder builder() {
        return new Builder();
    }

    public static String linkKey(String typeOrKey) {
        String t = typeOrKey == null ? "" : typeOrKey.trim();
        if (BARE_TYPE.matcher(t).matches()) return "modmenu." + t;
        if (TRANSLATION_KEY.matcher(t).matches()) return t;
        throw new IllegalArgumentException("'" + typeOrKey
                + "' is not a Mod Menu link type (like 'discord') or a translation key (like 'my_mod.wiki')");
    }

    public ModMenuMetadata resolve(String modrinth, String curseforge, String githubRepository) {
        Builder b = toBuilder();
        if (autoLinks) {
            if (isSet(modrinth)) b.linkIfAbsent("modmenu.modrinth", "https://modrinth.com/mod/" + modrinth.trim());
            if (isSet(curseforge)) {
                b.linkIfAbsent("modmenu.curseforge", "https://www.curseforge.com/projects/" + curseforge.trim());
            }
            if (isSet(githubRepository)) {
                b.linkIfAbsent("modmenu.github_releases", "https://github.com/" + githubRepository.trim() + "/releases");
            }
        }
        if (updateChecker == null && isSet(modrinth)) b.updateChecker(true);
        return b.build();
    }

    public List<String> warnings() {
        List<String> out = new ArrayList<>();
        for (String key : links.keySet()) {
            if (!key.startsWith("modmenu.")) continue;
            String type = key.substring("modmenu.".length());
            if (!LINK_TYPES.contains(type) && !NEWER_LINK_TYPES.contains(type)) {
                out.add("Mod Menu has no link type '" + type + "' (it knows " + String.join(", ", LINK_TYPES)
                        + "); the link would show as '" + key + "'. Use your own key, like 'my_mod." + type
                        + "', with a translation.");
            }
        }
        return out;
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!links.isEmpty()) m.put("links", new LinkedHashMap<String, Object>(links));
        if (!badges.isEmpty()) m.put("badges", badgeIds(badges));
        if (parent != null) m.put("parent", parent.toJson());
        if (updateChecker != null) m.put("update_checker", updateChecker);
        return m;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("links", new LinkedHashMap<String, Object>(links));
        m.put("badges", badgeIds(badges));
        if (parent != null) m.put("parent", parent.toJson());
        if (updateChecker != null) m.put("updateChecker", updateChecker);
        m.put("autoLinks", autoLinks);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ModMenuMetadata fromJson(Map<String, Object> m) {
        Builder b = builder();
        ((Map<String, Object>) m.get("links")).forEach((k, v) -> b.link(k, (String) v));
        for (Object o : (List<Object>) m.get("badges")) b.badge(Badge.fromId((String) o));
        if (m.get("parent") != null) b.parent(Parent.fromJson(m.get("parent")));
        if (m.get("updateChecker") != null) b.updateChecker((Boolean) m.get("updateChecker"));
        b.autoLinks(!Boolean.FALSE.equals(m.get("autoLinks")));
        return b.build();
    }

    private Builder toBuilder() {
        Builder b = builder().autoLinks(autoLinks).updateChecker(updateChecker).parent(parent);
        links.forEach(b::link);
        badges.forEach(b::badge);
        return b;
    }

    private static List<Object> badgeIds(Set<Badge> badges) {
        List<Object> ids = new ArrayList<>();
        for (Badge b : badges) ids.add(b.id());
        return ids;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public static final class Builder {
        private final Map<String, String> links = new LinkedHashMap<>();
        private final Set<Badge> badges = EnumSet.noneOf(Badge.class);
        private Parent parent;
        private Boolean updateChecker;
        private boolean autoLinks = true;

        private Builder() {}

        public Builder link(String typeOrKey, String url) {
            String key = linkKey(typeOrKey);
            if (url == null || !URL.matcher(url.trim()).matches()) {
                throw new IllegalArgumentException(
                        "The Mod Menu link '" + typeOrKey + "' needs an http:// or https:// URL (got '" + url + "')");
            }
            links.put(key, url.trim());
            return this;
        }

        private Builder linkIfAbsent(String key, String url) {
            if (!links.containsKey(key)) links.put(key, url);
            return this;
        }

        public Builder badge(Badge badge) {
            badges.add(badge);
            return this;
        }

        public Builder parent(Parent parent) {
            this.parent = parent;
            return this;
        }

        public Builder updateChecker(Boolean enabled) {
            updateChecker = enabled;
            return this;
        }

        public Builder autoLinks(boolean enabled) {
            autoLinks = enabled;
            return this;
        }

        public ModMenuMetadata build() {
            return new ModMenuMetadata(this);
        }
    }
}
