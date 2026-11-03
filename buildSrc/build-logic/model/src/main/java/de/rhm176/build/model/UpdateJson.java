package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class UpdateJson {
    private UpdateJson() {}

    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[*+-]\\s+");
    private static final Pattern QUOTE = Pattern.compile("^\\s*>\\s?");

    public static String url(String explicit, String repository, String branch, String path) {
        if (explicit != null && !explicit.isBlank()) {
            String url = explicit.strip();
            if (!url.matches("https?://[^\\s\"]+")) {
                throw new IllegalArgumentException("The update.json url must be an http:// or https:// URL (got '" + explicit + "')");
            }
            return url;
        }
        if (repository == null || repository.isBlank()) return null;
        return de.rhm176.build.model.changelog.RepoLinks.parse(repository)
                .flatMap(r -> r.rawFile(branch, path))
                .orElse(null);
    }

    public static String homepage(
            String explicit, String modrinth, String curseforge, String githubRepository, String modHomepage) {
        if (isSet(explicit)) return explicit.strip();
        if (isSet(modrinth)) return "https://modrinth.com/mod/" + modrinth.strip();
        if (isSet(curseforge)) return "https://www.curseforge.com/projects/" + curseforge.strip();
        if (isSet(githubRepository)) return "https://github.com/" + githubRepository.strip() + "/releases";
        return isSet(modHomepage) ? modHomepage.strip() : null;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    public static String merge(
            String existing,
            String homepage,
            String modVersion,
            Collection<String> minecraftVersions,
            String changelog,
            boolean recommended) {
        if (modVersion == null || modVersion.isBlank()) throw new IllegalArgumentException("The mod version is empty");
        Set<String> minecraft = new LinkedHashSet<>();
        if (minecraftVersions != null) {
            for (String v : minecraftVersions) {
                if (v == null || !v.matches("\\d+(\\.\\d+)+")) {
                    throw new IllegalArgumentException("Not a Minecraft release version: '" + v + "'");
                }
                minecraft.add(v);
            }
        }
        if (minecraft.isEmpty()) {
            throw new IllegalArgumentException("No Forge or NeoForge build to write promos for");
        }

        Map<String, Object> old;
        if (existing == null || existing.isBlank()) {
            old = new LinkedHashMap<>();
        } else {
            try {
                old = Json.parseObject(existing);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("The existing update file is not a JSON object (" + e.getMessage()
                        + "); fix or delete it", e);
            }
        }

        Object oldPromos = old.get("promos");
        if (oldPromos != null && !(oldPromos instanceof Map)) {
            throw new IllegalArgumentException("'promos' in the existing update file is not an object");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Object oldHomepage = old.get("homepage");
        String home = homepage != null && !homepage.isBlank() ? homepage.strip()
                : oldHomepage instanceof String s ? s : null;
        if (home != null) result.put("homepage", home);

        String text = plainText(changelog);
        for (Map.Entry<String, Object> e : old.entrySet()) {
            String key = e.getKey();
            if (key.equals("homepage") || key.equals("promos")) continue;
            if (!minecraft.contains(key)) result.put(key, e.getValue());
        }
        for (String mc : minecraft) {
            Object current = old.get(mc);
            if (current != null && !(current instanceof Map)) {
                throw new IllegalArgumentException("'" + mc + "' in the existing update file is not an object");
            }
            Map<String, Object> versions = new LinkedHashMap<>();
            versions.put(modVersion, text);
            if (current != null) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) current).entrySet()) {
                    if (!String.valueOf(e.getKey()).equals(modVersion)) versions.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            result.put(mc, versions);
        }

        Map<String, Object> promos = new LinkedHashMap<>();
        if (oldPromos != null) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) oldPromos).entrySet()) promos.put(String.valueOf(e.getKey()), e.getValue());
        }
        for (String mc : minecraft) {
            promos.put(mc + "-latest", modVersion);
            if (recommended) promos.put(mc + "-recommended", modVersion);
        }
        result.put("promos", promos);
        return Json.write(result);
    }

    public static String plainText(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        List<String> out = new ArrayList<>();
        for (String raw : markdown.strip().split("\\R", -1)) {
            String line = raw;
            var heading = HEADING.matcher(line);
            boolean isHeading = false;
            if (heading.matches()) {
                line = heading.group(1) + ":";
                isHeading = true;
            }
            line = QUOTE.matcher(line).replaceFirst("    ");
            line = BULLET.matcher(line).replaceFirst("$1- ");
            line = MARKDOWN_LINK.matcher(line).replaceAll("$1");
            line = line.replace("**", "").replace("__", "").replace("`", "");
            line = line.replaceAll("(?<![\\w*])_([^_\\s][^_]*)_(?![\\w*])", "$1");
            line = line.stripTrailing();
            if (isHeading && !out.isEmpty() && !out.get(out.size() - 1).isEmpty()) out.add("");
            out.add(line);
        }
        StringBuilder sb = new StringBuilder();
        int blank = 0;
        for (String line : out) {
            if (line.isEmpty()) {
                if (++blank > 1) continue;
            } else {
                blank = 0;
            }
            sb.append(line).append('\n');
        }
        return sb.toString().strip();
    }
}
