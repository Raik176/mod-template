package de.rhm176.example_mod.platform;

public final class Services {
    private static final String[] PLATFORMS = {
        "de.rhm176.example_mod.fabric.platform.FabricPlatform",
        "de.rhm176.example_mod.forge.platform.ForgePlatform",
        "de.rhm176.example_mod.neoforge.platform.NeoForgePlatform",
    };

    public static final Platform PLATFORM = load(Platform.class, PLATFORMS);

    private Services() {}

    public static <T> T load(Class<T> service, String... implementations) {
        ClassLoader loader = Services.class.getClassLoader();
        for (String name : implementations) {
            Class<?> type;
            try {
                type = Class.forName(name, true, loader);
            } catch (ClassNotFoundException e) {
                continue;
            }
            try {
                return service.cast(type.getConstructor().newInstance());
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalStateException("Could not create " + name + " as " + service.getName(), e);
            }
        }
        throw new IllegalStateException("No implementation of " + service.getName() + " found, tried " + String.join(", ", implementations));
    }
}
