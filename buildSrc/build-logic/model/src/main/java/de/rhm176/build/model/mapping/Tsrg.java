package de.rhm176.build.model.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class Tsrg {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public final Map<String, String> classes = new HashMap<>();
    public final Map<String, Map<String, String>> fields = new HashMap<>();
    public final Map<String, Map<String, String>> methods = new HashMap<>();

    public static Tsrg parse(String text) {
        return parse(new StringReader(text));
    }

    public static Tsrg parse(Reader reader) {
        Tsrg tsrg = new Tsrg();
        BufferedReader lines = reader instanceof BufferedReader b ? b : new BufferedReader(reader, 1 << 16);
        try {
            int columns = 2;
            String currentClass = null;
            String line = lines.readLine();
            if (line != null && line.startsWith("tsrg2")) {
                columns = WHITESPACE.split(line.trim()).length - 1;
                line = lines.readLine();
            }
            for (; line != null; line = lines.readLine()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                if (line.startsWith("\t\t")) continue;
                String[] t = WHITESPACE.split(line.trim());
                if (line.charAt(0) != '\t') {
                    if (t.length < 2) throw new IllegalArgumentException("Bad class line: " + line);
                    currentClass = t[0];
                    tsrg.classes.put(t[0], t[1]);
                    continue;
                }
                if (currentClass == null) throw new IllegalArgumentException("Member before class: " + line);
                if (t.length >= 3 && t[1].startsWith("(")) {
                    tsrg.methods.computeIfAbsent(currentClass, k -> new HashMap<>()).put(t[0] + t[1], t[2]);
                } else if (t.length >= 2) {
                    String srg = t.length == columns + 1 ? t[2] : t[1];
                    tsrg.fields.computeIfAbsent(currentClass, k -> new HashMap<>()).put(t[0], srg);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return tsrg;
    }
}
