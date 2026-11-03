package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResourceRelocation {
    private record Part(String literal, int group) {
        static Part literal(String text) {
            return new Part(text, -1);
        }

        static Part group(int index) {
            return new Part(null, index);
        }
    }

    private final String source;
    private final String target;
    private final Pattern regex;
    private final int groups;
    private final List<Part> parts;

    private ResourceRelocation(String source, String target, Pattern regex, int groups, List<Part> parts) {
        this.source = source;
        this.target = target;
        this.regex = regex;
        this.groups = groups;
        this.parts = parts;
    }

    public static ResourceRelocation of(String source, String target) {
        String normalizedSource = normalize(source);
        String normalizedTarget = normalize(target);
        if (normalizedSource.isEmpty()) throw new IllegalArgumentException("Relocation source cannot be blank");
        if (normalizedSource.endsWith("/")) normalizedSource += "**";
        StringBuilder regex = new StringBuilder();
        int groups = compile(normalizedSource, regex);
        List<Part> parts = parseTarget(normalizedTarget, groups);
        return new ResourceRelocation(normalizedSource, normalizedTarget, Pattern.compile(regex.toString()), groups, parts);
    }

    public static ResourceRelocation renaming(String before, String after) {
        String normalizedAfter = normalize(after);
        ResourceRelocation relocation = of(before, template(normalizedAfter));
        long groupsAfter = normalizedAfter.chars().filter(c -> c == '(').count();
        if (relocation.groups != groupsAfter) {
            throw new IllegalArgumentException(
                    "'" + before + "' captures " + relocation.groups + " group(s) but '" + after + "' captures " + groupsAfter);
        }
        return relocation;
    }

    public static String applyAll(List<ResourceRelocation> relocations, String path) {
        for (ResourceRelocation relocation : relocations) {
            String moved = relocation.apply(path);
            if (moved != null) return moved;
        }
        return path;
    }

    public String source() {
        return source;
    }

    public String target() {
        return target;
    }

    public String apply(String path) {
        Matcher matcher = regex.matcher(normalize(path));
        if (!matcher.matches()) return null;
        StringBuilder result = new StringBuilder();
        for (Part part : parts) {
            if (part.group() < 0) {
                result.append(part.literal());
            } else {
                String captured = matcher.group(part.group());
                if (captured != null) result.append(captured);
            }
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return source + " -> " + target;
    }

    private static String normalize(String path) {
        String normalized = path.replace('\\', '/');
        int start = 0;
        while (start < normalized.length() && normalized.charAt(start) == '/') start++;
        return normalized.substring(start);
    }

    private static int compile(String pattern, StringBuilder regex) {
        int groups = 0;
        int depth = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                        boolean directory = i + 2 < pattern.length() && pattern.charAt(i + 2) == '/';
                        regex.append(directory ? "(?:.*/)?" : ".*");
                        i += directory ? 2 : 1;
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                case '(' -> {
                    depth++;
                    groups++;
                    regex.append('(');
                }
                case ')' -> {
                    if (depth == 0) throw new IllegalArgumentException("Unbalanced ')' in relocation pattern '" + pattern + "'");
                    depth--;
                    regex.append(')');
                }
                default -> {
                    if ("\\.[]{}+-^$|".indexOf(c) >= 0) regex.append('\\');
                    regex.append(c);
                }
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced '(' in relocation pattern '" + pattern + "'");
        return groups;
    }

    private static List<Part> parseTarget(String target, int groups) {
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (c != '$') {
                literal.append(c);
                continue;
            }
            int end = i + 1;
            while (end < target.length() && target.charAt(end) >= '0' && target.charAt(end) <= '9') end++;
            if (end == i + 1) {
                throw new IllegalArgumentException("'$' in relocation target '" + target + "' must be followed by a group number");
            }
            int group = Integer.parseInt(target.substring(i + 1, end));
            if (group > groups) {
                throw new IllegalArgumentException(
                        "Relocation target '" + target + "' references group " + group + " but the source captures " + groups);
            }
            if (literal.length() > 0) {
                parts.add(Part.literal(literal.toString()));
                literal.setLength(0);
            }
            parts.add(Part.group(group));
            i = end - 1;
        }
        if (literal.length() > 0) parts.add(Part.literal(literal.toString()));
        return parts;
    }

    private static String template(String pattern) {
        StringBuilder template = new StringBuilder();
        int group = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '(') {
                int end = pattern.indexOf(')', i);
                if (end < 0) throw new IllegalArgumentException("Unbalanced '(' in relocation pattern '" + pattern + "'");
                if (pattern.substring(i + 1, end).indexOf('(') >= 0) {
                    throw new IllegalArgumentException("Nested groups are not supported in '" + pattern + "'");
                }
                template.append('$').append(++group);
                i = end;
            } else if (c == '*' || c == '?' || c == '$' || c == ')') {
                throw new IllegalArgumentException("'" + c + "' must be inside a group in '" + pattern + "'");
            } else {
                template.append(c);
            }
        }
        return template.toString();
    }
}
