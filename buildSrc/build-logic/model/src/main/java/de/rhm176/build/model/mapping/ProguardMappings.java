package de.rhm176.build.model.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProguardMappings {
    public record Member(String officialName, String officialDescriptor, String obfName) {}

    public static final class ClassEntry {
        public final String official;
        public final String obf;
        public final Map<String, String> fields = new LinkedHashMap<>();
        final List<Member> methodList = new ArrayList<>();

        public List<Member> methods() {
            return methodList;
        }

        ClassEntry(String official, String obf) {
            this.official = official;
            this.obf = obf;
        }
    }

    private final Map<String, ClassEntry> classes = new LinkedHashMap<>();

    public Map<String, ClassEntry> classes() {
        return classes;
    }

    public void add(String text) {
        add(new StringReader(text));
    }

    public void add(Reader reader) {
        BufferedReader lines = reader instanceof BufferedReader b ? b : new BufferedReader(reader, 1 << 16);
        ClassEntry current = null;
        try {
            for (String raw = lines.readLine(); raw != null; raw = lines.readLine()) current = addLine(raw, current);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ClassEntry addLine(String raw, ClassEntry current) {
        if (raw.isBlank() || raw.startsWith("#")) return current;
        if (!Character.isWhitespace(raw.charAt(0))) {
            int arrow = raw.indexOf(" -> ");
            if (arrow < 0 || !raw.endsWith(":")) throw new IllegalArgumentException("Bad class line: " + raw);
            String official = raw.substring(0, arrow).replace('.', '/');
            String obf = raw.substring(arrow + 4, raw.length() - 1).replace('.', '/');
            return classes.computeIfAbsent(official, o -> new ClassEntry(o, obf));
        }
        if (current == null) throw new IllegalArgumentException("Member before class: " + raw);
        String line = raw.trim();
        int arrow = line.lastIndexOf(" -> ");
        if (arrow < 0) throw new IllegalArgumentException("Bad member line: " + raw);
        String obfName = line.substring(arrow + 4).trim();
        String left = line.substring(0, arrow);
        left = stripLineNumbers(left);
        int paren = left.indexOf('(');
        if (paren < 0) {
            int space = left.lastIndexOf(' ');
            current.fields.put(left.substring(space + 1), obfName);
        } else {
            int close = left.indexOf(')', paren);
            String head = left.substring(0, paren);
            int space = head.lastIndexOf(' ');
            String returnType = head.substring(0, space);
            String name = head.substring(space + 1);
            String args = left.substring(paren + 1, close);
            StringBuilder desc = new StringBuilder("(");
            if (!args.isBlank()) {
                for (String arg : args.split(",")) desc.append(Descriptors.fromJavaType(arg.trim()));
            }
            desc.append(')').append(Descriptors.fromJavaType(returnType.trim()));
            current.methodList.add(new Member(name, desc.toString(), obfName));
        }
        return current;
    }

    private static String stripLineNumbers(String s) {
        int first = digitsEnd(s, 0);
        if (first == 0 || first >= s.length() || s.charAt(first) != ':') return s;
        int second = digitsEnd(s, first + 1);
        if (second == first + 1 || second >= s.length() || s.charAt(second) != ':') return s;
        return s.substring(second + 1);
    }

    private static int digitsEnd(String s, int from) {
        int i = from;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
        return i;
    }
}
