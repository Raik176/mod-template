package de.rhm176.build.model.changelog;

import java.util.List;

public record ChangelogResult(
        String markdown,
        String tag,
        String previousTag,
        boolean firstRelease,
        int entries,
        String suggestedVersion,
        boolean tagElsewhere,
        List<String> warnings) {}
