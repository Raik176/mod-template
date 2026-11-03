package de.rhm176.example_mod.forge.platform;

import de.rhm176.example_mod.platform.Platform;
import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgePlatform implements Platform {
    private static final String ENVIRONMENT = "net.minecraftforge.fml.loading.FMLEnvironment";
    private static final String LOADER = "net.minecraftforge.fml.loading.FMLLoader";

    @Override
    public String loaderName() {
        return "Forge";
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
            return Class.forName(type, false, ForgePlatform.class.getClassLoader()).getField(name).get(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Object staticMethod(String type, String name) {
        try {
            return Class.forName(type, false, ForgePlatform.class.getClassLoader()).getMethod(name).invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
