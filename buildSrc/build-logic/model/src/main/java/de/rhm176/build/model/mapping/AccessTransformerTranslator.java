package de.rhm176.build.model.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public final class AccessTransformerTranslator {
    private AccessTransformerTranslator() {}

    public record Result(String text, List<String> errors) {}

    public static Result toSrg(String accessTransformer, SrgMappings mappings) {
        List<String> errors = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        int lineNumber = 0;
        for (String raw : accessTransformer.split("\\R")) {
            lineNumber++;
            String line = raw;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) {
                if (raw.startsWith("#")) out.append(raw).append('\n');
                continue;
            }
            String[] t = line.split("\\s+");
            if (t.length < 2 || t.length > 3) {
                errors.add("Line " + lineNumber + ": cannot parse '" + line + "'");
                continue;
            }
            String owner = t[1].replace('.', '/');
            if (!mappings.hasClass(owner)) {
                errors.add("Line " + lineNumber + ": unknown class '" + t[1] + "'");
                continue;
            }
            String srgOwner = mappings.mapClass(owner).replace('/', '.');
            if (t.length == 2) {
                out.append(t[0]).append(' ').append(srgOwner).append('\n');
                continue;
            }
            String member = t[2];
            int paren = member.indexOf('(');
            if (paren < 0) {
                Optional<String> field = mappings.field(owner, member);
                if (field.isEmpty()) {
                    errors.add("Line " + lineNumber + ": no field '" + member + "' in " + t[1]);
                    continue;
                }
                out.append(t[0]).append(' ').append(srgOwner).append(' ').append(field.get()).append('\n');
            } else {
                String name = member.substring(0, paren);
                String descriptor = member.substring(paren);
                Optional<String[]> method = mappings.method(owner, name, descriptor);
                if (method.isEmpty()) {
                    errors.add("Line " + lineNumber + ": no method '" + member + "' in " + t[1]);
                    continue;
                }
                out.append(t[0]).append(' ').append(srgOwner).append(' ')
                        .append(method.get()[0]).append(method.get()[1]).append('\n');
            }
        }
        return new Result(out.toString(), errors);
    }
}
