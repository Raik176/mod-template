package de.rhm176.build.model.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class LegacyForgeMappings {
    private LegacyForgeMappings() {}

    private static final Map<String, SrgMappings> MEMORY = new ConcurrentHashMap<>();

    public static SrgMappings load(String minecraftVersion, Path cacheRoot, MojangApi mojang, String forgeMaven)
            throws IOException {
        String key = cacheRoot.toAbsolutePath() + "|" + minecraftVersion;
        SrgMappings cached = MEMORY.get(key);
        if (cached != null) return cached;
        synchronized (LegacyForgeMappings.class) {
            cached = MEMORY.get(key);
            if (cached != null) return cached;

            Path mojangDir = cacheRoot.resolve("mojang").resolve(minecraftVersion);
            Path client = mojangDir.resolve("client_mappings.txt");
            Path server = mojangDir.resolve("server_mappings.txt");
            Path tsrgFile = cacheRoot.resolve("mcpconfig").resolve(minecraftVersion).resolve("joined.tsrg");

            if (!cached(client) || !cached(tsrgFile)) {
                MojangApi.Downloads downloads = mojang.downloads(minecraftVersion);
                if (downloads.clientMappings() == null) {
                    throw new IOException("Mojang published no official mappings for Minecraft " + minecraftVersion);
                }
                if (!cached(client)) download(client, downloads.clientMappings());
                if (downloads.serverMappings() != null && !cached(server)) {
                    download(server, downloads.serverMappings());
                }
                if (!cached(tsrgFile)) store(tsrgFile, McpConfig.joinedTsrg(minecraftVersion, forgeMaven));
            }

            SrgMappings mappings = compose(client, server, tsrgFile);
            MEMORY.put(key, mappings);
            return mappings;
        }
    }

    private static SrgMappings compose(Path client, Path server, Path tsrgFile) throws IOException {
        try {
            ProguardMappings official = new ProguardMappings();
            try (BufferedReader in = reader(client)) {
                official.add(in);
            }
            if (cached(server)) {
                try (BufferedReader in = reader(server)) {
                    official.add(in);
                }
            }
            Tsrg tsrg;
            try (BufferedReader in = reader(tsrgFile)) {
                tsrg = Tsrg.parse(in);
            }
            return SrgMappings.compose(official, tsrg);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Path compressed(Path plain) {
        return plain.resolveSibling(plain.getFileName() + ".gz");
    }

    private static boolean cached(Path plain) {
        return Files.isRegularFile(compressed(plain)) || Files.isRegularFile(plain);
    }

    private static BufferedReader reader(Path plain) throws IOException {
        Path gz = compressed(plain);
        if (Files.isRegularFile(gz)) {
            InputStream raw = Files.newInputStream(gz);
            try {
                return new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8), 1 << 16);
            } catch (IOException | RuntimeException e) {
                raw.close();
                throw e;
            }
        }
        String text = Files.readString(plain, StandardCharsets.UTF_8);
        try {
            store(plain, text);
        } catch (IOException ignored) {
        }
        return new BufferedReader(new StringReader(text));
    }

    private interface Content {
        void writeTo(OutputStream out) throws IOException;
    }

    private static void download(Path plain, String url) throws IOException {
        Http.stream(url, in -> {
            store(plain, in::transferTo);
            return null;
        });
    }

    private static void store(Path plain, String content) throws IOException {
        store(plain, out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static void store(Path plain, Content content) throws IOException {
        Path target = compressed(plain);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp), 1 << 16)) {
                content.writeTo(out);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
        Files.deleteIfExists(plain);
    }
}
