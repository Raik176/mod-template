package de.rhm176.build.model.changelog;

public final class ChangelogException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ChangelogException(String message) {
        super(message);
    }

    public ChangelogException(String message, Throwable cause) {
        super(message, cause);
    }
}
