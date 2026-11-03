package de.rhm176.build.model.mapping;

import de.rhm176.build.model.Json;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class McpConfig {
    private McpConfig() {}

    public static final String DEFAULT_MAVEN = "https://maven.minecraftforge.net";

    public static String latestVersion(String minecraftVersion, String maven) throws IOException {
        String xml = Http.text(maven + "/de/oceanlabs/mcp/mcp_config/maven-metadata.xml");
        List<String> matching = new ArrayList<>();
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
        while (m.find()) if (m.group(1).startsWith(minecraftVersion + "-")) matching.add(m.group(1));
        if (matching.isEmpty()) throw new IOException("Forge published no MCPConfig for Minecraft " + minecraftVersion);
        Collections.sort(matching);
        return matching.get(matching.size() - 1);
    }

    private static final String DEFAULT_MAPPINGS = "config/joined.tsrg";

    public static String joinedTsrg(String minecraftVersion, String maven) throws IOException {
        String version = latestVersion(minecraftVersion, maven);
        String url = maven + "/de/oceanlabs/mcp/mcp_config/" + version + "/mcp_config-" + version + ".zip";
        RemoteZip zip = RemoteZip.open(url);
        String mappings = zip == null ? null : readMappings(zip);
        return mappings != null ? mappings : readMappings(Http.bytes(url));
    }

    private static String readMappings(RemoteZip zip) throws IOException {
        String path = DEFAULT_MAPPINGS;
        if (zip.contains("config.json")) {
            byte[] config = zip.read("config.json");
            if (config == null) return null;
            path = mappingsPath(new String(config, StandardCharsets.UTF_8));
        }
        if (!zip.contains(path)) throw new IOException("The MCPConfig zip has no '" + path + "'");
        byte[] content = zip.read(path);
        return content == null ? null : new String(content, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static String mappingsPath(String configJson) {
        Object data = Json.parseObject(configJson).get("data");
        if (data instanceof Map<?, ?> d && ((Map<String, Object>) d).get("mappings") instanceof String p) return p;
        return DEFAULT_MAPPINGS;
    }

    static String readMappings(byte[] zip) throws IOException {
        String configJson = null;
        Map<String, String> entries = new java.util.HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                boolean config = e.getName().equals("config.json");
                if (e.isDirectory() || !(config || e.getName().endsWith(".tsrg"))) continue;
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (config) configJson = content;
                else entries.put(e.getName(), content);
            }
        }
        String path = configJson == null ? DEFAULT_MAPPINGS : mappingsPath(configJson);
        String content = entries.get(path);
        if (content == null) throw new IOException("The MCPConfig zip has no '" + path + "'");
        return content;
    }
}
