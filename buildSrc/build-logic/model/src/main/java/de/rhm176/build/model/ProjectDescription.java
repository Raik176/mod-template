package de.rhm176.build.model;

import de.rhm176.build.model.changelog.RepoLinks;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectDescription {
    private ProjectDescription() {}

    public static final int MODRINTH_MAX_LENGTH = 65536;

    public static final String OFF = "<!-- sync:off -->";
    public static final String ON = "<!-- sync:on -->";

    public record Result(String text, List<String> unresolved) {}

    private static final Pattern MD_URL = Pattern.compile("\\]\\(\\s*(<[^>\\n]*>|[^)\\s]+)");
    private static final Pattern HTML_ATTR = Pattern.compile("(?i)\\b(src|href)(\\s*=\\s*)([\"'])([^\"'\\n]*)\\3");
    private static final Pattern REFERENCE = Pattern.compile("^(\\s{0,3}\\[[^\\]\\n]+\\]:\\s*)(<[^>\\n]*>|\\S+)");
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern IMAGE_FILE = Pattern.compile("(?i)\\.(png|jpe?g|gif|webp|svg|avif)$");

    public static Result prepare(String readme, String readmeDirectory, RepoLinks repository, String branch) {
        String text = readme == null ? "" : readme.replace("\r\n", "\n").replace('\r', '\n');
        text = withoutSkipped(text);

        List<String> unresolved = new ArrayList<>();
        List<String> out = new ArrayList<>();
        boolean fenced = false;
        String fence = null;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (fence == null && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
                fence = trimmed.substring(0, 3);
                fenced = true;
                out.add(line);
                continue;
            }
            if (fenced) {
                if (trimmed.startsWith(fence)) {
                    fenced = false;
                    fence = null;
                }
                out.add(line);
                continue;
            }
            out.add(rewriteLine(line, readmeDirectory, repository, branch, unresolved));
        }
        String result = String.join("\n", out).strip();
        if (result.isEmpty()) throw new IllegalArgumentException("The README has nothing left to publish");
        if (result.length() > MODRINTH_MAX_LENGTH) {
            throw new IllegalArgumentException("The description is " + result.length() + " characters, and Modrinth takes at most "
                    + MODRINTH_MAX_LENGTH + ". Shorten the README or keep parts out with " + OFF + " ... " + ON);
        }
        return new Result(result + "\n", List.copyOf(unresolved));
    }

    private static String withoutSkipped(String text) {
        List<String> kept = new ArrayList<>();
        boolean skipping = false;
        for (String line : text.split("\n", -1)) {
            String stripped = line.strip();
            boolean off = stripped.equals(OFF), on = stripped.equals(ON);
            if (off) {
                if (skipping) throw new IllegalArgumentException("Two " + OFF + " without a " + ON + " between them");
                skipping = true;
                continue;
            }
            if (on) {
                if (!skipping) throw new IllegalArgumentException(ON + " without a " + OFF + " before it");
                skipping = false;
                continue;
            }
            if (!skipping) kept.add(line);
        }
        if (skipping) throw new IllegalArgumentException(OFF + " is never closed with " + ON);
        return String.join("\n", kept);
    }

    private static String rewriteLine(String line, String dir, RepoLinks repo, String branch, List<String> unresolved) {
        if (line.indexOf('`') < 0) return rewriteText(line, dir, repo, branch, unresolved);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            int open = line.indexOf('`', i);
            if (open < 0) {
                sb.append(rewriteText(line.substring(i), dir, repo, branch, unresolved));
                break;
            }
            int ticks = 1;
            while (open + ticks < line.length() && line.charAt(open + ticks) == '`') ticks++;
            String marker = "`".repeat(ticks);
            int close = line.indexOf(marker, open + ticks);
            if (close < 0) {
                sb.append(rewriteText(line.substring(i), dir, repo, branch, unresolved));
                break;
            }
            sb.append(rewriteText(line.substring(i, open), dir, repo, branch, unresolved));
            sb.append(line, open, close + ticks);
            i = close + ticks;
        }
        return sb.toString();
    }

    private static String rewriteText(String s, String dir, RepoLinks repo, String branch, List<String> unresolved) {
        Matcher m = MD_URL.matcher(s);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String raw = m.group(1);
            boolean angle = raw.startsWith("<");
            String url = angle ? raw.substring(1, raw.length() - 1) : raw;
            boolean image = isImage(s, m.start());
            String replaced = absolute(url, image, dir, repo, branch, unresolved);
            sb.append(s, last, m.start(1)).append(angle ? "<" + replaced + ">" : replaced);
            last = m.end(1);
        }
        sb.append(s.substring(last));
        s = sb.toString();

        m = HTML_ATTR.matcher(s);
        sb = new StringBuilder();
        last = 0;
        while (m.find()) {
            boolean image = m.group(1).equalsIgnoreCase("src");
            String replaced = absolute(m.group(4), image, dir, repo, branch, unresolved);
            sb.append(s, last, m.start()).append(m.group(1)).append(m.group(2)).append(m.group(3)).append(replaced).append(m.group(3));
            last = m.end();
        }
        sb.append(s.substring(last));
        s = sb.toString();

        m = REFERENCE.matcher(s);
        if (m.find()) {
            String raw = m.group(2);
            boolean angle = raw.startsWith("<");
            String url = angle ? raw.substring(1, raw.length() - 1) : raw;
            String replaced = absolute(url, IMAGE_FILE.matcher(stripSuffix(url)).find(), dir, repo, branch, unresolved);
            s = m.group(1) + (angle ? "<" + replaced + ">" : replaced) + s.substring(m.end());
        }
        return s;
    }

    private static boolean isImage(String s, int closeBracket) {
        int depth = 0;
        for (int i = closeBracket; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ']') depth++;
            else if (c == '[' && --depth == 0) return i > 0 && s.charAt(i - 1) == '!';
        }
        return false;
    }

    private static String stripSuffix(String url) {
        int cut = url.length();
        int hash = url.indexOf('#'), query = url.indexOf('?');
        if (hash >= 0) cut = Math.min(cut, hash);
        if (query >= 0) cut = Math.min(cut, query);
        return url.substring(0, cut);
    }

    private static String absolute(String url, boolean image, String dir, RepoLinks repo, String branch, List<String> unresolved) {
        if (url.isEmpty() || url.startsWith("#") || url.startsWith("//") || SCHEME.matcher(url).find()) return url;
        String path = stripSuffix(url);
        String suffix = url.substring(path.length());
        String resolved = resolve(path, dir);
        if (resolved == null || repo == null) {
            unresolved.add(url);
            return url;
        }
        var target = image ? repo.rawFile(branch, resolved) : repo.blobFile(branch, resolved);
        if (target.isEmpty()) {
            unresolved.add(url);
            return url;
        }
        return target.get() + suffix;
    }

    private static String resolve(String path, String dir) {
        List<String> parts = new ArrayList<>();
        String joined = path.startsWith("/") ? path : (dir == null || dir.isBlank() ? "" : dir.replace('\\', '/') + "/") + path;
        for (String part : joined.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (parts.isEmpty()) return null;
                parts.remove(parts.size() - 1);
            } else {
                parts.add(part);
            }
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }
}
