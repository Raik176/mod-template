package de.rhm176.build.model;

import java.util.List;

public record GeneratedOutput(List<GeneratedFile> files, List<String> warnings) {}
