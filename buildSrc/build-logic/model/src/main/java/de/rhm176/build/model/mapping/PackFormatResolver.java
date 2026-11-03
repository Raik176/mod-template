package de.rhm176.build.model.mapping;

import de.rhm176.build.model.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PackFormatResolver {
    private PackFormatResolver() {}

    public static int resolve(String minecraftVersion, Path cacheRoot, MojangApi api) throws IOException {
        Path cached = cacheRoot.resolve("pack-format").resolve(minecraftVersion + ".txt");
        if (Files.isRegularFile(cached)) {
            try {
                return Integer.parseInt(Files.readString(cached).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        MojangApi.Downloads downloads = api.downloads(minecraftVersion);
        if (downloads.clientJar() == null) throw new IOException("No client jar listed for Minecraft " + minecraftVersion);
        byte[] entry = RemoteZip.readEntry(downloads.clientJar(), "version.json");
        String versionJson = entry != null
                ? new String(entry, StandardCharsets.UTF_8)
                : Http.stream(downloads.clientJar(), PackFormatResolver::findVersionJson);
        int format = fromVersionJson(versionJson);
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, Integer.toString(format));
        return format;
    }

    static String findVersionJson(InputStream jar) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(jar)) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("version.json")) return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("The client jar contains no version.json");
    }

    @SuppressWarnings("unchecked")
    public static int fromVersionJson(String json) {
        Object value = Json.parseObject(json).get("pack_version");
        if (value instanceof Number n) return n.intValue();
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            for (String key : new String[] {"resource_major", "resource"}) {
                if (map.get(key) instanceof Number n) return n.intValue();
            }
        }
        throw new IllegalArgumentException("version.json has no recognizable pack_version: " + value);
    }
}
