package de.rhm176.example_mod.platform;

import java.nio.file.Path;

public interface Platform {
    String loaderName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Path configDirectory();
}
