package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClassTweakerConverter {
    private ClassTweakerConverter() {}

    public record Result(String accessTransformer, List<String> warnings) {}

    public static Result toAccessTransformer(String classTweaker) {
        List<String> warnings = new ArrayList<>();
        Set<String> lines = new LinkedHashSet<>();
        boolean sawHeader = false;
        int lineNumber = 0;
        for (String raw : classTweaker.split("\\R")) {
            lineNumber++;
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            if (!sawHeader) {
                if (!(t[0].equals("classTweaker") || t[0].equals("accessWidener")) || t.length < 3) {
                    throw new IllegalArgumentException("Line " + lineNumber + ": expected a 'classTweaker v1 <namespace>' header");
                }
                if (!t[2].equals("named")) {
                    throw new IllegalArgumentException("Line " + lineNumber + ": only the 'named' (official) namespace can be converted, got '" + t[2] + "'");
                }
                sawHeader = true;
                continue;
            }
            String directive = t[0].startsWith("transitive-") ? t[0].substring("transitive-".length()) : t[0];
            if (t.length < 3) {
                warnings.add("Line " + lineNumber + ": malformed directive '" + line + "'; skipped");
                continue;
            }
            String kind = t[1];
            String owner = t[2].replace('/', '.');
            switch (kind) {
                case "class" -> {
                    String access = switch (directive) {
                        case "accessible" -> "public";
                        case "extendable" -> "public-f";
                        default -> null;
                    };
                    if (access == null) unsupported(warnings, lineNumber, line);
                    else lines.add(access + " " + owner);
                }
                case "method" -> {
                    if (t.length < 5) {
                        warnings.add("Line " + lineNumber + ": method directive needs name and descriptor; skipped");
                        break;
                    }
                    String access = switch (directive) {
                        case "accessible" -> "public";
                        case "extendable" -> "protected-f";
                        default -> null;
                    };
                    if (access == null) unsupported(warnings, lineNumber, line);
                    else lines.add(access + " " + owner + " " + t[3] + t[4]);
                }
                case "field" -> {
                    if (t.length < 4) {
                        warnings.add("Line " + lineNumber + ": field directive needs a name; skipped");
                        break;
                    }
                    String access = switch (directive) {
                        case "accessible" -> "public";
                        case "mutable" -> "default-f";
                        default -> null;
                    };
                    if (access == null) unsupported(warnings, lineNumber, line);
                    else lines.add(access + " " + owner + " " + t[3]);
                }
                default -> warnings.add("Line " + lineNumber + ": unsupported target '" + kind + "'; skipped");
            }
        }
        if (!sawHeader) throw new IllegalArgumentException("Empty class tweaker (missing header)");
        StringBuilder out = new StringBuilder("# Generated from a class tweaker. Do not edit.\n");
        for (String l : lines) out.append(l).append('\n');
        return new Result(out.toString(), warnings);
    }

    private static void unsupported(List<String> warnings, int line, String text) {
        warnings.add("Line " + line + ": '" + text + "' has no access transformer equivalent; skipped");
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }
}
