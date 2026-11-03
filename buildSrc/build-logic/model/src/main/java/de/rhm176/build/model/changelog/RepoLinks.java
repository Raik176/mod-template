package de.rhm176.build.model.changelog;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepoLinks {
    private static final Pattern URL = Pattern.compile(
            "^(?:(?:https?|ssh|git)://(?:[^@/]+@)?|[^@/\\s]+@)(?<host>[^/:]+)(?::\\d+)?[/:](?<path>[^\\s?#]+?)(?:\\.git)?/?$");

    private enum Style {
        GITHUB,
        GITLAB,
        GITEA
    }

    private final String web;
    private final String slug;
    private final Style style;

    private RepoLinks(String web, String slug, Style style) {
        this.web = web;
        this.slug = slug;
        this.style = style;
    }

    public static Optional<RepoLinks> parse(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        Matcher m = URL.matcher(url.strip());
        if (!m.matches()) return Optional.empty();
        String host = m.group("host").toLowerCase(Locale.ROOT);
        String path = m.group("path");
        String[] parts = path.split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return Optional.empty();
        String slug = parts[0] + "/" + parts[1];
        if (slug.endsWith(".git")) slug = slug.substring(0, slug.length() - 4);
        Style style = host.contains("gitlab")
                ? Style.GITLAB
                : host.contains("codeberg") || host.contains("gitea") || host.contains("forgejo")
                        ? Style.GITEA
                        : Style.GITHUB;
        return Optional.of(new RepoLinks("https://" + host + "/" + slug, slug, style));
    }

    public String slug() {
        return slug;
    }

    public String web() {
        return web;
    }

    public String commit(String sha) {
        return web + (style == Style.GITLAB ? "/-/commit/" : "/commit/") + sha;
    }

    public String pullRequest(int number) {
        return switch (style) {
            case GITHUB -> web + "/pull/" + number;
            case GITLAB -> web + "/-/merge_requests/" + number;
            case GITEA -> web + "/pulls/" + number;
        };
    }

    public String compare(String from, String to) {
        return web + (style == Style.GITLAB ? "/-/compare/" : "/compare/") + from + "..." + to;
    }

    public Optional<String> rawFile(String branch, String path) {
        if (branch == null || branch.isBlank() || path == null || path.isBlank()) return Optional.empty();
        String file = path.replace('\\', '/').replaceFirst("^/+", "");
        return switch (style) {
            case GITHUB -> web.equals("https://github.com/" + slug)
                    ? Optional.of("https://raw.githubusercontent.com/" + slug + "/" + branch + "/" + file)
                    : Optional.empty();
            case GITLAB -> Optional.of(web + "/-/raw/" + branch + "/" + file);
            case GITEA -> Optional.of(web + "/raw/branch/" + branch + "/" + file);
        };
    }

    public Optional<String> blobFile(String branch, String path) {
        if (branch == null || branch.isBlank() || path == null || path.isBlank()) return Optional.empty();
        String file = path.replace('\\', '/').replaceFirst("^/+", "");
        return Optional.of(
                switch (style) {
                    case GITHUB -> web + "/blob/" + branch + "/" + file;
                    case GITLAB -> web + "/-/blob/" + branch + "/" + file;
                    case GITEA -> web + "/src/branch/" + branch + "/" + file;
                });
    }

    public String history(String tag) {
        return switch (style) {
            case GITHUB -> web + "/commits/" + tag;
            case GITLAB -> web + "/-/commits/" + tag;
            case GITEA -> web + "/commits/tag/" + tag;
        };
    }
}
