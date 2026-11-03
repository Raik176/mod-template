import com.sun.net.httpserver.HttpServer;
import de.rhm176.build.model.mapping.*;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class MappingTests {
    static final String CLIENT = String.join("\n",
            "# comment",
            "net.minecraft.world.level.Level -> bsb:",
            "    int ticks -> a",
            "    boolean isClientSide -> b",
            "    1:1:void tick(java.util.function.BooleanSupplier) -> a",
            "    net.minecraft.core.BlockPos getSharedSpawnPos() -> c",
            "    void helper(int[],long) -> d",
            "net.minecraft.core.BlockPos -> fx:",
            "    1:1:void <init>(int,int,int) -> <init>",
            "    int getX() -> u",
            "com.mojang.blaze3d.Blaze3D -> com.mojang.blaze3d.Blaze3D:",
            "    void youJustLostTheGame() -> youJustLostTheGame",
            "");
    static final String SERVER = String.join("\n",
            "net.minecraft.server.dedicated.DedicatedServer -> ads:",
            "    void tickServer() -> t",
            "");
    static final String TSRG1 = String.join("\n",
            "bsb net/minecraft/world/World",
            "\ta field_73011_w",
            "\tb field_72995_K",
            "\ta (Ljava/util/function/BooleanSupplier;)V func_72835_b",
            "\tc ()Lfx; func_175694_M",
            "\td ([IJ)V func_999_d",
            "fx net/minecraft/util/math/BlockPos",
            "\tu ()I func_177958_n",
            "ads net/minecraft/server/dedicated/DedicatedServer",
            "\tt ()V func_71217_p",
            "");
    static final String TSRG2 = String.join("\n",
            "tsrg2 obf srg id",
            "bsb net/minecraft/world/level/Level",
            "\ta f_46433_ 1",
            "\ta (Ljava/util/function/BooleanSupplier;)V m_46653_ 2",
            "\t\t0 p_46654_ 3",
            "fx net/minecraft/core/BlockPos",
            "\tu ()I m_123341_ 4",
            "");

    static void run() throws Exception {
        parsing();
        composition();
        accessTransformers();
        lineEndings();
        pipeline();
    }

    static SrgMappings compose(String tsrg) {
        ProguardMappings p = new ProguardMappings();
        p.add(CLIENT);
        p.add(SERVER);
        return SrgMappings.compose(p, Tsrg.parse(tsrg));
    }

    static void parsing() {
        ProguardMappings p = new ProguardMappings();
        p.add(CLIENT);
        var level = p.classes().get("net/minecraft/world/level/Level");
        ModelSelfTest.eq("proguard obf", "bsb", level.obf);
        ModelSelfTest.eq("proguard field", "b", level.fields.get("isClientSide"));
        ModelSelfTest.eq("proguard method desc", "(Ljava/util/function/BooleanSupplier;)V", level.methods().get(0).officialDescriptor());
        ModelSelfTest.eq("proguard array desc", "([IJ)V", level.methods().get(2).officialDescriptor());
        ModelSelfTest.eq("proguard class return", "()Lnet/minecraft/core/BlockPos;", level.methods().get(1).officialDescriptor());
        ModelSelfTest.eq("proguard ctor", "(III)V", p.classes().get("net/minecraft/core/BlockPos").methods().get(0).officialDescriptor());
        ModelSelfTest.eq("unobfuscated class", "com/mojang/blaze3d/Blaze3D", p.classes().get("com/mojang/blaze3d/Blaze3D").obf);

        Tsrg v1 = Tsrg.parse(TSRG1);
        ModelSelfTest.eq("tsrg1 class", "net/minecraft/world/World", v1.classes.get("bsb"));
        ModelSelfTest.eq("tsrg1 field", "field_72995_K", v1.fields.get("bsb").get("b"));
        ModelSelfTest.eq("tsrg1 method", "func_72835_b", v1.methods.get("bsb").get("a(Ljava/util/function/BooleanSupplier;)V"));
        Tsrg v2 = Tsrg.parse(TSRG2);
        ModelSelfTest.eq("tsrg2 field with id", "f_46433_", v2.fields.get("bsb").get("a"));
        ModelSelfTest.eq("tsrg2 method with id", "m_46653_", v2.methods.get("bsb").get("a(Ljava/util/function/BooleanSupplier;)V"));
        ModelSelfTest.truth("tsrg2 params skipped", !v2.fields.get("bsb").containsKey("0"));
        Tsrg withDesc = Tsrg.parse("tsrg2 obf srg\nbsb net/X\n\ta I f_1_a\n");
        ModelSelfTest.eq("tsrg2 field with descriptor", "f_1_a", withDesc.fields.get("bsb").get("a"));
    }

    static void composition() {
        SrgMappings m = compose(TSRG1);
        ModelSelfTest.eq("MCP class name", "net/minecraft/world/World", m.mapClass("net/minecraft/world/level/Level"));
        ModelSelfTest.eq("field", "field_72995_K", m.field("net/minecraft/world/level/Level", "isClientSide").get());
        ModelSelfTest.eq("method name+desc", List.of("func_72835_b", "(Ljava/util/function/BooleanSupplier;)V"),
                List.of(m.method("net/minecraft/world/level/Level", "tick", "(Ljava/util/function/BooleanSupplier;)V").get()));
        ModelSelfTest.eq("descriptor classes remapped", "()Lnet/minecraft/util/math/BlockPos;",
                m.method("net/minecraft/world/level/Level", "getSharedSpawnPos", "()Lnet/minecraft/core/BlockPos;").get()[1]);
        ModelSelfTest.eq("server only class", "func_71217_p",
                m.method("net/minecraft/server/dedicated/DedicatedServer", "tickServer", "()V").get()[0]);
        ModelSelfTest.eq("unrenamed member keeps obf name", "<init>",
                m.method("net/minecraft/core/BlockPos", "<init>", "(III)V").get()[0]);
        ModelSelfTest.eq("unobfuscated class unchanged", "com/mojang/blaze3d/Blaze3D", m.mapClass("com/mojang/blaze3d/Blaze3D"));

        SrgMappings m2 = compose(TSRG2);
        ModelSelfTest.eq("official class kept (1.17+)", "net/minecraft/world/level/Level", m2.mapClass("net/minecraft/world/level/Level"));
        ModelSelfTest.eq("1.17 field", "f_46433_", m2.field("net/minecraft/world/level/Level", "ticks").get());

        String namedToSrg = m.toNamedToSrg();
        ModelSelfTest.truth("CL line", namedToSrg.contains("CL: net/minecraft/world/level/Level net/minecraft/world/World\n"));
        ModelSelfTest.truth("FD line", namedToSrg.contains("FD: net/minecraft/world/level/Level/isClientSide net/minecraft/world/World/field_72995_K\n"));
        ModelSelfTest.truth("MD line", namedToSrg.contains("MD: net/minecraft/world/level/Level/tick (Ljava/util/function/BooleanSupplier;)V net/minecraft/world/World/func_72835_b (Ljava/util/function/BooleanSupplier;)V\n"));
        String srgToNamed = m.toSrgToNamed();
        ModelSelfTest.truth("inverse MD", srgToNamed.contains("MD: net/minecraft/world/World/func_72835_b (Ljava/util/function/BooleanSupplier;)V net/minecraft/world/level/Level/tick (Ljava/util/function/BooleanSupplier;)V\n"));
        ModelSelfTest.truth("inverse FD", srgToNamed.contains("FD: net/minecraft/world/World/field_72995_K net/minecraft/world/level/Level/isClientSide\n"));
    }

    static void accessTransformers() {
        String at = "# generated\npublic net.minecraft.world.level.Level\n"
                + "public net.minecraft.world.level.Level tick(Ljava/util/function/BooleanSupplier;)V\n"
                + "default-f net.minecraft.world.level.Level isClientSide\n"
                + "protected-f net.minecraft.world.level.Level getSharedSpawnPos()Lnet/minecraft/core/BlockPos;\n";
        var r = AccessTransformerTranslator.toSrg(at, compose(TSRG1));
        ModelSelfTest.eq("AT errors", List.of(), r.errors());
        ModelSelfTest.eq("AT translated (1.16: MCP classes + SRG members)",
                "# generated\npublic net.minecraft.world.World\n"
                        + "public net.minecraft.world.World func_72835_b(Ljava/util/function/BooleanSupplier;)V\n"
                        + "default-f net.minecraft.world.World field_72995_K\n"
                        + "protected-f net.minecraft.world.World func_175694_M()Lnet/minecraft/util/math/BlockPos;\n",
                r.text());
        var r2 = AccessTransformerTranslator.toSrg("public net.minecraft.world.level.Level tick(Ljava/util/function/BooleanSupplier;)V\n", compose(TSRG2));
        ModelSelfTest.eq("AT translated (1.17+: official classes)",
                "public net.minecraft.world.level.Level m_46653_(Ljava/util/function/BooleanSupplier;)V\n", r2.text());
        var bad = AccessTransformerTranslator.toSrg("public net.minecraft.Nope\npublic net.minecraft.world.level.Level missing\n"
                + "public net.minecraft.world.level.Level missing()V\n", compose(TSRG1));
        ModelSelfTest.eq("unresolved targets are errors", 3, bad.errors().size());
    }

    static byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    static byte[] bigJar(int padBytes, String versionJson, int fillerEntries) throws Exception {
        byte[] pad = new byte[padBytes];
        new Random(7).nextBytes(pad);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            put(z, "assets/pad.bin", pad, false);
            put(z, "net/minecraft/Foo.class", "x".getBytes(StandardCharsets.UTF_8), false);
            put(z, "version.json", versionJson.getBytes(StandardCharsets.UTF_8), false);
            for (int i = 0; i < fillerEntries; i++) {
                put(z, "assets/minecraft/filler/f" + i + ".json", new byte[0], false);
            }
        }
        return out.toByteArray();
    }

    static byte[] storedJar(String versionJson) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            put(z, "version.json", versionJson.getBytes(StandardCharsets.UTF_8), true);
        }
        return out.toByteArray();
    }

    static void put(ZipOutputStream z, String name, byte[] data, boolean stored) throws Exception {
        ZipEntry e = new ZipEntry(name);
        if (stored) {
            CRC32 crc = new CRC32();
            crc.update(data);
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            e.setCrc(crc.getValue());
        }
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }

    static long[] parseRange(String header, long total) {
        String spec = header.substring("bytes=".length());
        int dash = spec.indexOf('-');
        String from = spec.substring(0, dash);
        String to = spec.substring(dash + 1);
        if (from.isEmpty()) return new long[] {Math.max(0, total - Long.parseLong(to)), total - 1};
        return new long[] {Long.parseLong(from), to.isEmpty() ? total - 1 : Math.min(Long.parseLong(to), total - 1)};
    }

    static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(out)) {
            z.write(data);
        }
        return out.toByteArray();
    }

    static void lineEndings() {
        String crlf = CLIENT.replace("\n", "\r\n");
        ProguardMappings p = new ProguardMappings();
        p.add(crlf);
        ModelSelfTest.eq("CRLF proguard classes", 3, p.classes().size());
        ModelSelfTest.eq("CRLF proguard methods", 3, p.classes().get("net/minecraft/world/level/Level").methods().size());
        Tsrg t = Tsrg.parse(TSRG1.replace("\n", "\r\n"));
        ModelSelfTest.eq("CRLF tsrg classes", 3, t.classes.size());
        ModelSelfTest.eq("CRLF tsrg method", "func_72835_b", t.methods.get("bsb").get("a(Ljava/util/function/BooleanSupplier;)V"));
    }

    static void pipeline() throws Exception {
        ModelSelfTest.eq("pack_version old", 34, PackFormatResolver.fromVersionJson("{\"pack_version\": {\"resource\": 34, \"data\": 48}}"));
        ModelSelfTest.eq("pack_version new", 84, PackFormatResolver.fromVersionJson("{\"pack_version\": {\"resource_major\": 84, \"resource_minor\": 0, \"data_major\": 101, \"data_minor\": 1}}"));
        ModelSelfTest.eq("pack_version plain", 4, PackFormatResolver.fromVersionJson("{\"pack_version\": 4}"));
        ModelSelfTest.throwsIae("pack_version missing", () -> PackFormatResolver.fromVersionJson("{\"id\": \"x\"}"));

        Map<String, byte[]> files = new HashMap<>();
        Set<String> rangeless = java.util.concurrent.ConcurrentHashMap.newKeySet();
        rangeless.add("/jar/1.16.5.jar");
        AtomicHits hits = new AtomicHits();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = files.get(path);
            hits.count(path);
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (body != null && range != null && (path.startsWith("/jar/") || path.endsWith(".zip")) && !rangeless.contains(path)) {
                long[] r = parseRange(range, body.length);
                byte[] part = Arrays.copyOfRange(body, (int) r[0], (int) r[1] + 1);
                exchange.getResponseHeaders().add("Content-Range", "bytes " + r[0] + "-" + r[1] + "/" + body.length);
                hits.count("range:" + path);
                hits.add("bytes:" + path, part.length);
                exchange.sendResponseHeaders(206, part.length);
                exchange.getResponseBody().write(part);
                exchange.close();
                return;
            }
            if (body != null && (path.startsWith("/jar/") || path.endsWith(".zip"))) hits.add("bytes:" + path, body.length);
            String accept = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            boolean text = path.startsWith("/client/") || path.startsWith("/server/")
                    || path.startsWith("/version/") || path.equals("/manifest.json");
            if (body != null && text && accept != null && accept.contains("gzip")) {
                try {
                    body = gzip(body);
                } catch (Exception e) {
                    throw new java.io.IOException(e);
                }
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
                hits.count("gzip:" + path);
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            for (String mc : new String[] {"1.16.5", "26.2"}) {
                String pack = mc.equals("26.2")
                        ? "{\"pack_version\": {\"resource_major\": 88, \"resource_minor\": 0}}"
                        : "{\"pack_version\": {\"resource\": 6, \"data\": 6}}";
                Map<String, String> jar = new LinkedHashMap<>();
                jar.put("net/minecraft/Foo.class", "x");
                jar.put("version.json", pack);
                files.put("/jar/" + mc + ".jar", zip(jar));
                files.put("/client/" + mc + ".txt", CLIENT.getBytes(StandardCharsets.UTF_8));
                files.put("/server/" + mc + ".txt", SERVER.getBytes(StandardCharsets.UTF_8));
                files.put("/version/" + mc + ".json", ("{\"downloads\": {\"client\": {\"url\": \"" + base + "/jar/" + mc
                        + ".jar\"}, \"client_mappings\": {\"url\": \"" + base + "/client/" + mc
                        + ".txt\"}, \"server_mappings\": {\"url\": \"" + base + "/server/" + mc + ".txt\"}}}").getBytes(StandardCharsets.UTF_8));
            }
            files.put("/jar/26.2.jar", bigJar(3 << 20, "{\"pack_version\": {\"resource_major\": 88, \"resource_minor\": 0}}", 1500));
            files.put("/jar/0.1.jar", zip(Map.of("net/minecraft/Foo.class", "x")));
            files.put("/jar/0.2.jar", storedJar("{\"pack_version\": {\"resource\": 77, \"data\": 77}}"));
            for (String mc : new String[] {"0.1", "0.2"}) {
                files.put("/version/" + mc + ".json", ("{\"downloads\": {\"client\": {\"url\": \"" + base + "/jar/" + mc + ".jar\"}}}")
                        .getBytes(StandardCharsets.UTF_8));
            }
            files.put("/manifest.json", ("{\"versions\": [{\"id\": \"1.16.5\", \"url\": \"" + base + "/version/1.16.5.json\"},"
                    + "{\"id\": \"26.2\", \"url\": \"" + base + "/version/26.2.json\"},"
                    + "{\"id\": \"0.1\", \"url\": \"" + base + "/version/0.1.json\"},"
                    + "{\"id\": \"0.2\", \"url\": \"" + base + "/version/0.2.json\"}]}").getBytes(StandardCharsets.UTF_8));
            files.put("/de/oceanlabs/mcp/mcp_config/maven-metadata.xml", ("<metadata><versioning><versions>"
                    + "<version>1.16.5-20200101.000000</version><version>1.16.5-20210115.111550</version>"
                    + "<version>1.17.1-20211207.000000</version></versions></versioning></metadata>").getBytes(StandardCharsets.UTF_8));
            Map<String, String> mcp = new LinkedHashMap<>();
            mcp.put("config.json", "{\"data\": {\"mappings\": \"config/joined.tsrg\"}}");
            mcp.put("config/joined.tsrg", TSRG1);
            Random noise = new Random(11);
            StringBuilder patches = new StringBuilder();
            for (int i = 0; i < 400_000; i++) patches.append((char) ('a' + noise.nextInt(26)));
            mcp.put("patches/big.txt", patches.toString());
            String mcpPath = "/de/oceanlabs/mcp/mcp_config/1.16.5-20210115.111550/mcp_config-1.16.5-20210115.111550.zip";
            files.put(mcpPath, zip(mcp));

            MojangApi api = new MojangApi(base + "/manifest.json");
            Path cache = Files.createTempDirectory("mapping-cache");
            ModelSelfTest.eq("pack format from the jar (old)", 6, PackFormatResolver.resolve("1.16.5", cache, api));
            ModelSelfTest.eq("pack format from the jar (new)", 88, PackFormatResolver.resolve("26.2", cache, api));
            ModelSelfTest.truth("range read used for the jar", hits.get("range:/jar/26.2.jar") > 0);
            ModelSelfTest.truth("range read transfers a fraction of the jar",
                    hits.get("bytes:/jar/26.2.jar") < files.get("/jar/26.2.jar").length / 4);
            ModelSelfTest.eq("server without range support still works", 0, hits.get("range:/jar/1.16.5.jar"));
            ModelSelfTest.eq("pack format from a stored entry", 77, PackFormatResolver.resolve("0.2", cache, api));
            ModelSelfTest.throwsIo("jar without version.json", () -> PackFormatResolver.resolve("0.1", cache, api));
            ModelSelfTest.eq("manifest fetched once for all versions", 1, hits.get("/manifest.json"));
            ModelSelfTest.eq("manifest transferred gzipped", 1, hits.get("gzip:/manifest.json"));
            int jarHits = hits.get("/jar/26.2.jar");
            ModelSelfTest.eq("pack format cached", 88, PackFormatResolver.resolve("26.2", cache, api));
            ModelSelfTest.eq("no second download", jarHits, hits.get("/jar/26.2.jar"));
            ModelSelfTest.throwsIo("unknown version", () -> PackFormatResolver.resolve("9.9", cache, api));

            SrgMappings m = LegacyForgeMappings.load("1.16.5", cache, api, base);
            ModelSelfTest.eq("pipeline: latest MCPConfig used, mapping composed", "func_72835_b",
                    m.method("net/minecraft/world/level/Level", "tick", "(Ljava/util/function/BooleanSupplier;)V").get()[0]);
            ModelSelfTest.truth("MCPConfig mappings read with ranges", hits.get("range:" + mcpPath) > 0);
            ModelSelfTest.truth("MCPConfig range read skips the other entries",
                    hits.get("bytes:" + mcpPath) < files.get(mcpPath).length / 2);
            rangeless.add(mcpPath);
            int rangeHits = hits.get("range:" + mcpPath);
            ModelSelfTest.eq("MCPConfig mappings without range support", TSRG1, McpConfig.joinedTsrg("1.16.5", base));
            ModelSelfTest.eq("no range request after the server stopped supporting them", rangeHits, hits.get("range:" + mcpPath));
            ModelSelfTest.eq("McpConfig newest version", "1.16.5-20210115.111550", McpConfig.latestVersion("1.16.5", base));
            int mappingHits = hits.get("/client/1.16.5.txt");
            Path cache2 = cache;
            LegacyForgeMappings.load("1.16.5", cache2, api, base);
            ModelSelfTest.eq("mapping download cached", mappingHits, hits.get("/client/1.16.5.txt"));
            ModelSelfTest.throwsIo("no MCPConfig for version", () -> McpConfig.latestVersion("1.12.2", base));

            ModelSelfTest.eq("version json fetched once per version", 1, hits.get("/version/1.16.5.json"));
            ModelSelfTest.eq("mapping transferred gzipped", 1, hits.get("gzip:/client/1.16.5.txt"));
            ModelSelfTest.truth("client mappings cached compressed", Files.isRegularFile(cache.resolve("mojang/1.16.5/client_mappings.txt.gz")));
            ModelSelfTest.truth("server mappings cached compressed", Files.isRegularFile(cache.resolve("mojang/1.16.5/server_mappings.txt.gz")));
            ModelSelfTest.truth("tsrg cached compressed", Files.isRegularFile(cache.resolve("mcpconfig/1.16.5/joined.tsrg.gz")));
            ModelSelfTest.truth("no uncompressed copies", !Files.exists(cache.resolve("mojang/1.16.5/client_mappings.txt"))
                    && !Files.exists(cache.resolve("mcpconfig/1.16.5/joined.tsrg")));
            try (var left = Files.list(cache.resolve("mojang/1.16.5"))) {
                ModelSelfTest.truth("no temp files left behind", left.noneMatch(f -> f.getFileName().toString().endsWith(".tmp")));
            }

            Path corrupt = Files.createTempDirectory("corrupt-cache");
            Files.createDirectories(corrupt.resolve("mojang/1.16.5"));
            Files.createDirectories(corrupt.resolve("mcpconfig/1.16.5"));
            Files.writeString(corrupt.resolve("mojang/1.16.5/client_mappings.txt.gz"), "not gzip");
            Files.writeString(corrupt.resolve("mcpconfig/1.16.5/joined.tsrg.gz"), "not gzip");
            ModelSelfTest.throwsIo("corrupt cache is an IOException",
                    () -> LegacyForgeMappings.load("1.16.5", corrupt, api, base));

            Path legacy = Files.createTempDirectory("legacy-cache");
            Files.createDirectories(legacy.resolve("mojang/1.16.5"));
            Files.createDirectories(legacy.resolve("mcpconfig/1.16.5"));
            Files.writeString(legacy.resolve("mojang/1.16.5/client_mappings.txt"), CLIENT);
            Files.writeString(legacy.resolve("mojang/1.16.5/server_mappings.txt"), SERVER);
            Files.writeString(legacy.resolve("mcpconfig/1.16.5/joined.tsrg"), TSRG1);
            SrgMappings fromLegacy = LegacyForgeMappings.load("1.16.5", legacy, new MojangApi("http://127.0.0.1:1/unreachable.json"), "http://127.0.0.1:1");
            ModelSelfTest.eq("legacy cache readable", "func_72835_b",
                    fromLegacy.method("net/minecraft/world/level/Level", "tick", "(Ljava/util/function/BooleanSupplier;)V").get()[0]);
            ModelSelfTest.eq("server-only class from legacy cache", "func_71217_p",
                    fromLegacy.method("net/minecraft/server/dedicated/DedicatedServer", "tickServer", "()V").get()[0]);
            ModelSelfTest.truth("legacy cache migrated to gzip", Files.isRegularFile(legacy.resolve("mojang/1.16.5/client_mappings.txt.gz"))
                    && Files.isRegularFile(legacy.resolve("mojang/1.16.5/server_mappings.txt.gz"))
                    && Files.isRegularFile(legacy.resolve("mcpconfig/1.16.5/joined.tsrg.gz")));
            ModelSelfTest.truth("legacy plain files removed", !Files.exists(legacy.resolve("mojang/1.16.5/client_mappings.txt"))
                    && !Files.exists(legacy.resolve("mojang/1.16.5/server_mappings.txt"))
                    && !Files.exists(legacy.resolve("mcpconfig/1.16.5/joined.tsrg")));
        } finally {
            server.stop(0);
        }
    }

    static class AtomicHits {
        final Map<String, Integer> map = new HashMap<>();
        synchronized void count(String p) { map.merge(p, 1, Integer::sum); }
        synchronized void add(String p, int n) { map.merge(p, n, Integer::sum); }
        synchronized int get(String p) { return map.getOrDefault(p, 0); }
    }
}
