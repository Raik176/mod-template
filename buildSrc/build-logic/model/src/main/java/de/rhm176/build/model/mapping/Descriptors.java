package de.rhm176.build.model.mapping;

import java.util.function.UnaryOperator;

final class Descriptors {
    private Descriptors() {}

    static String fromJavaType(String type) {
        int dims = 0;
        String base = type;
        while (base.endsWith("[]")) {
            dims++;
            base = base.substring(0, base.length() - 2);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[".repeat(Math.max(0, dims)));
        switch (base) {
            case "void" -> sb.append('V');
            case "boolean" -> sb.append('Z');
            case "byte" -> sb.append('B');
            case "char" -> sb.append('C');
            case "short" -> sb.append('S');
            case "int" -> sb.append('I');
            case "long" -> sb.append('J');
            case "float" -> sb.append('F');
            case "double" -> sb.append('D');
            default -> sb.append('L').append(base.replace('.', '/')).append(';');
        }
        return sb.toString();
    }

    static String mapClasses(String descriptor, UnaryOperator<String> mapper) {
        StringBuilder sb = new StringBuilder(descriptor.length() + 16);
        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end < 0) throw new IllegalArgumentException("Malformed descriptor: " + descriptor);
                sb.append('L').append(mapper.apply(descriptor.substring(i + 1, end))).append(';');
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
