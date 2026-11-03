package de.rhm176.build.model.changelog;

public record Section(String type, String title) {
    public Section {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Section type must not be blank");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Section title must not be blank");
        type = type.strip().toLowerCase(java.util.Locale.ROOT);
        title = title.strip();
    }

    public String encode() {
        return type + "=" + title;
    }

    public static Section decode(String encoded) {
        int eq = encoded.indexOf('=');
        if (eq <= 0) throw new IllegalArgumentException("Expected 'type=Title', got '" + encoded + "'");
        return new Section(encoded.substring(0, eq), encoded.substring(eq + 1));
    }
}
