package de.rhm176.example_mod.neoforge.platform;

import de.rhm176.example_mod.platform.Platform;
import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

public final class NeoForgePlatform implements Platform {
    private static final String ENVIRONMENT = "net.neoforged.fml.loading.FMLEnvironment";
    private static final String LOADER = "net.neoforged.fml.loading.FMLLoader";

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !isProduction();
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    private static boolean isProduction() {
        Object value = staticMethod(ENVIRONMENT, "isProduction");
        if (!(value instanceof Boolean)) value = staticField(ENVIRONMENT, "production");
        if (!(value instanceof Boolean)) value = staticMethod(LOADER, "isProduction");
        if (value instanceof Boolean) return (Boolean) value;
        Object naming = staticField(ENVIRONMENT, "naming");
        if (!(naming instanceof String)) naming = staticMethod(LOADER, "getNaming");
        return !(naming instanceof String) || "srg".equals(naming);
    }

    private static Object staticField(String type, String name) {
        try {
            return Class.forName(type, false, NeoForgePlatform.class.getClassLoader()).getField(name).get(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Object staticMethod(String type, String name) {
        try {
            return Class.forName(type, false, NeoForgePlatform.class.getClassLoader()).getMethod(name).invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
