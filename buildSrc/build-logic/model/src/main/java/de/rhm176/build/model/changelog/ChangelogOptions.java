package de.rhm176.build.model.changelog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ChangelogOptions {
    public Path repository;

    public String version;

    public String tagPrefix = "v";

    public String repositoryUrl;

    public String since;

    public boolean includeUnconventional;

    public List<Section> sections = defaultSections();

    public String breakingTitle = "\u26A0\uFE0F Breaking Changes";
    public String otherTitle = "\uD83D\uDCE6 Other Changes";

    public static List<Section> defaultSections() {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("feat", "\u2728 Features"));
        sections.add(new Section("fix", "\uD83D\uDC1B Bug Fixes"));
        sections.add(new Section("perf", "\u26A1 Performance"));
        sections.add(new Section("revert", "\u21A9\uFE0F Reverts"));
        return sections;
    }
}
