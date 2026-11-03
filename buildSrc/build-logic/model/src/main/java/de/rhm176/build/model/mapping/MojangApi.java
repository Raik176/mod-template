package de.rhm176.build.model.mapping;

import de.rhm176.build.model.Json;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MojangApi {
    public static final String DEFAULT_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public record Downloads(String clientJar, String clientMappings, String serverMappings) {}

    private final String manifestUrl;

    public MojangApi() {
        this(DEFAULT_MANIFEST);
    }

    public MojangApi(String manifestUrl) {
        this.manifestUrl = manifestUrl;
    }

    private static final Map<String, Map<String, String>> MANIFESTS = new ConcurrentHashMap<>();
    private static final Map<String, Downloads> DOWNLOADS = new ConcurrentHashMap<>();

    public Downloads downloads(String minecraftVersion) throws IOException {
        String key = manifestUrl + "|" + minecraftVersion;
        Downloads known = DOWNLOADS.get(key);
        if (known != null) return known;

        String versionUrl = versionUrl(minecraftVersion);
        Downloads fetched = parseDownloads(Json.parseObject(Http.text(versionUrl)));
        DOWNLOADS.put(key, fetched);
        return fetched;
    }

    private String versionUrl(String minecraftVersion) throws IOException {
        Map<String, String> urls = MANIFESTS.get(manifestUrl);
        String versionUrl = urls == null ? null : urls.get(minecraftVersion);
        if (versionUrl == null) {
            urls = fetchManifest();
            MANIFESTS.put(manifestUrl, urls);
            versionUrl = urls.get(minecraftVersion);
        }
        if (versionUrl == null) throw new IOException("Minecraft " + minecraftVersion + " is not in Mojang's version manifest");
        return versionUrl;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fetchManifest() throws IOException {
        Map<String, Object> manifest = Json.parseObject(Http.text(manifestUrl));
        Map<String, String> urls = new LinkedHashMap<>();
        for (Object o : (List<Object>) manifest.get("versions")) {
            Map<String, Object> v = (Map<String, Object>) o;
            urls.putIfAbsent((String) v.get("id"), (String) v.get("url"));
        }
        return urls;
    }

    @SuppressWarnings("unchecked")
    private static Downloads parseDownloads(Map<String, Object> version) {
        Map<String, Object> downloads = (Map<String, Object>) version.get("downloads");
        return new Downloads(
                url(downloads, "client"), url(downloads, "client_mappings"), url(downloads, "server_mappings"));
    }

    @SuppressWarnings("unchecked")
    private static String url(Map<String, Object> downloads, String key) {
        Object entry = downloads.get(key);
        return entry instanceof Map ? (String) ((Map<String, Object>) entry).get("url") : null;
    }
}
