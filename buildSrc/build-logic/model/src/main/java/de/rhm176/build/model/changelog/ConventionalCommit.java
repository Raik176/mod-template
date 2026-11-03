package de.rhm176.build.model.changelog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ConventionalCommit(
        String type,
        String scope,
        boolean breaking,
        String description,
        List<String> breakingNotes,
        Integer pullRequest,
        String subject,
        String revertedHash) {

    private static final Pattern HEADER = Pattern.compile(
            "^(?<type>[A-Za-z][A-Za-z0-9-]*)(?:\\((?<scope>[^()]*)\\))?(?<bang>!)?:\\s*(?<desc>\\S.*)$");
    private static final Pattern GIT_REVERT = Pattern.compile("^Revert \"(?<inner>.+)\"\\.?$");
    private static final Pattern PULL_REQUEST = Pattern.compile("\\s*\\(#(\\d+)\\)\\s*$");
    private static final Pattern BREAKING_FOOTER = Pattern.compile("^BREAKING[ -]CHANGE:\\s*(.*)$");
    private static final Pattern OTHER_FOOTER = Pattern.compile("^(?:[A-Za-z][A-Za-z0-9-]*: .*|[A-Za-z][A-Za-z0-9-]* #.*)$");
    private static final Pattern REVERTS_COMMIT = Pattern.compile("(?m)^This reverts commit ([0-9a-f]{7,40})\\b");

    public ConventionalCommit {
        breakingNotes = List.copyOf(breakingNotes);
    }

    public static Optional<ConventionalCommit> parse(String message) {
        if (message == null) return Optional.empty();
        String[] lines = message.replace("\r\n", "\n").strip().split("\n", -1);
        String subject = lines[0].strip();
        if (subject.isEmpty()) return Optional.empty();

        String body = String.join("\n", List.of(lines).subList(1, lines.length));
        String revertedHash = null;
        Matcher reverts = REVERTS_COMMIT.matcher(body);
        if (reverts.find()) revertedHash = reverts.group(1);

        Matcher gitRevert = GIT_REVERT.matcher(subject);
        if (gitRevert.matches()) {
            return Optional.of(new ConventionalCommit(
                    "revert", null, false, gitRevert.group("inner").strip(), List.of(), null, subject, revertedHash));
        }

        Matcher header = HEADER.matcher(subject);
        if (!header.matches()) return Optional.empty();

        String description = header.group("desc").strip();
        Integer pullRequest = null;
        Matcher pr = PULL_REQUEST.matcher(description);
        if (pr.find()) {
            pullRequest = Integer.valueOf(pr.group(1));
            description = description.substring(0, pr.start()).strip();
        }
        if (description.isEmpty()) return Optional.empty();

        List<String> notes = breakingNotes(lines);
        String scope = header.group("scope");
        if (scope != null && scope.isBlank()) scope = null;
        return Optional.of(new ConventionalCommit(
                header.group("type").toLowerCase(Locale.ROOT),
                scope == null ? null : scope.strip(),
                header.group("bang") != null || !notes.isEmpty(),
                description,
                notes,
                pullRequest,
                subject,
                revertedHash));
    }

    private static List<String> breakingNotes(String[] lines) {
        List<String> notes = new ArrayList<>();
        StringBuilder current = null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].stripTrailing();
            Matcher breaking = BREAKING_FOOTER.matcher(line);
            if (breaking.matches()) {
                if (current != null) notes.add(current.toString().strip());
                current = new StringBuilder(breaking.group(1));
            } else if (current != null) {
                if (line.isBlank() || OTHER_FOOTER.matcher(line).matches()) {
                    notes.add(current.toString().strip());
                    current = null;
                } else {
                    current.append('\n').append(line.strip());
                }
            }
        }
        if (current != null) notes.add(current.toString().strip());
        notes.removeIf(String::isBlank);
        return notes;
    }

    public ConventionalCommit withPullRequest(int number) {
        return new ConventionalCommit(
                type, scope, breaking, description, breakingNotes, number, subject, revertedHash);
    }
}
