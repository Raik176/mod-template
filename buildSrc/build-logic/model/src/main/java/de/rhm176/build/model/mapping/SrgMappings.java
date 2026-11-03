package de.rhm176.build.model.mapping;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class SrgMappings {
    final Map<String, String> classes = new TreeMap<>();
    final Map<String, String> fields = new TreeMap<>();
    final Map<String, String[]> methods = new TreeMap<>();

    public String mapClass(String officialInternal) {
        return classes.getOrDefault(officialInternal, officialInternal);
    }

    public boolean hasClass(String officialInternal) {
        return classes.containsKey(officialInternal);
    }

    public String mapDescriptor(String officialDescriptor) {
        return Descriptors.mapClasses(officialDescriptor, this::mapClass);
    }

    public Optional<String> field(String ownerOfficial, String name) {
        return Optional.ofNullable(fields.get(ownerOfficial + "|" + name));
    }

    public Optional<String[]> method(String ownerOfficial, String name, String descriptor) {
        return Optional.ofNullable(methods.get(ownerOfficial + "|" + name + "|" + descriptor));
    }

    public int classCount() {
        return classes.size();
    }

    public int memberCount() {
        return fields.size() + methods.size();
    }

    public String toNamedToSrg() {
        StringBuilder sb = new StringBuilder();
        classes.forEach((o, s) -> sb.append("CL: ").append(o).append(' ').append(s).append('\n'));
        fields.forEach((key, srg) -> {
            int bar = key.indexOf('|');
            String owner = key.substring(0, bar);
            String name = key.substring(bar + 1);
            sb.append("FD: ").append(owner).append('/').append(name).append(' ')
                    .append(mapClass(owner)).append('/').append(srg).append('\n');
        });
        methods.forEach((key, srg) -> {
            String[] k = key.split("\\|", 3);
            sb.append("MD: ").append(k[0]).append('/').append(k[1]).append(' ').append(k[2]).append(' ')
                    .append(mapClass(k[0])).append('/').append(srg[0]).append(' ').append(srg[1]).append('\n');
        });
        return sb.toString();
    }

    public String toSrgToNamed() {
        StringBuilder sb = new StringBuilder();
        classes.forEach((o, s) -> sb.append("CL: ").append(s).append(' ').append(o).append('\n'));
        fields.forEach((key, srg) -> {
            int bar = key.indexOf('|');
            String owner = key.substring(0, bar);
            String name = key.substring(bar + 1);
            sb.append("FD: ").append(mapClass(owner)).append('/').append(srg).append(' ')
                    .append(owner).append('/').append(name).append('\n');
        });
        methods.forEach((key, srg) -> {
            String[] k = key.split("\\|", 3);
            sb.append("MD: ").append(mapClass(k[0])).append('/').append(srg[0]).append(' ').append(srg[1]).append(' ')
                    .append(k[0]).append('/').append(k[1]).append(' ').append(k[2]).append('\n');
        });
        return sb.toString();
    }

    public static SrgMappings compose(ProguardMappings official, Tsrg srg) {
        SrgMappings out = new SrgMappings();
        Map<String, String> officialToObf = new TreeMap<>();
        for (ProguardMappings.ClassEntry c : official.classes().values()) officialToObf.put(c.official, c.obf);

        for (ProguardMappings.ClassEntry c : official.classes().values()) {
            out.classes.put(c.official, srg.classes.getOrDefault(c.obf, c.obf));
        }
        for (ProguardMappings.ClassEntry c : official.classes().values()) {
            Map<String, String> srgFields = srg.fields.getOrDefault(c.obf, Map.of());
            for (Map.Entry<String, String> f : c.fields.entrySet()) {
                out.fields.put(c.official + "|" + f.getKey(), srgFields.getOrDefault(f.getValue(), f.getValue()));
            }
            Map<String, String> srgMethods = srg.methods.getOrDefault(c.obf, Map.of());
            for (ProguardMappings.Member m : c.methods()) {
                String obfDescriptor = Descriptors.mapClasses(m.officialDescriptor(), n -> officialToObf.getOrDefault(n, n));
                String name = srgMethods.getOrDefault(m.obfName() + obfDescriptor, m.obfName());
                out.methods.put(c.official + "|" + m.officialName() + "|" + m.officialDescriptor(),
                        new String[] {name, out.mapDescriptor(m.officialDescriptor())});
            }
        }
        return out;
    }
}
