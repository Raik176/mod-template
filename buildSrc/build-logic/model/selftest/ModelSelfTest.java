import de.rhm176.build.model.*;
import java.util.*;

public class ModelSelfTest {
    static int failures, checks;

    static void eq(String what, Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            failures++;
            System.out.println("FAIL " + what + "\n  expected: " + expected + "\n  actual:   " + actual);
        }
    }

    static void truth(String what, boolean cond) { eq(what, true, cond); }

    interface IoRunnable { void run() throws Exception; }

    static void throwsIo(String what, IoRunnable r) {
        checks++;
        try { r.run(); failures++; System.out.println("FAIL " + what + ": no exception"); }
        catch (java.io.IOException e) {  }
        catch (Exception e) { failures++; System.out.println("FAIL " + what + ": " + e); }
    }

    static void throwsIae(String what, Runnable r) {
        checks++;
        try { r.run(); failures++; System.out.println("FAIL " + what + ": no exception"); }
        catch (IllegalArgumentException e) {  }
    }

    static ModMetadata sample() {
        return ModMetadata.builder().modId("example_mod").version("1.0.0").name("Example Mod")
                .description("Example Mod").license("MIT").icon("assets/example_mod/icon.png")
                .homepage("https://rhm176.de").sources("https://github.com/x/y").issues("https://github.com/x/y/issues")
                .author("rhm176")
                .dependency("example_lib", ModMetadata.DependencyType.REQUIRED, "[1.0.0,2.0.0)")
                .dependency("other", ModMetadata.DependencyType.SUGGESTED, "*")
                .dependency("clash", ModMetadata.DependencyType.DISCOURAGED, "*")
                .dependency("bad", ModMetadata.DependencyType.INCOMPATIBLE, "[1.0]")
                .custom("example_mod", Map.of("example", "example")).build();
    }

    static MetadataTarget target(LoaderType l, String mc, String loaderVersion) {
        McVersion v = McVersion.parse(mc);
        return new MetadataTarget(l, v, VersionRange.exactly(mc), loaderVersion, v.javaVersion(),
                l == LoaderType.FABRIC, Map.of("main", List.of("a.B")), List.of("example_mod.mixins.json"),
                l == LoaderType.FABRIC ? "example_mod.ct" : null, 70);
    }

    static MetadataTarget withoutPackFormat(MetadataTarget t) {
        return new MetadataTarget(t.loader(), t.minecraft(), t.minecraftRange(), t.loaderVersion(), t.javaVersion(),
                t.fabricApi(), t.entrypoints(), t.mixinConfigs(), t.accessWidener(), null);
    }

    public static void main(String[] args) throws Exception {
        versions();
        ranges();
        json();
        json5();
        matrix();
        generators();
        modMenu();
        updateJson();
        mixinExtras();
        datagenRules();
        projectDescription();
        classTweaker();
        mixins();
        forgeMixinRules();
        resourceRelocations();
        MappingTests.run();
        ChangelogTests.run();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    static void versions() {
        truth("1.14.4 < 1.21.1", McVersion.parse("1.14.4").compareTo(McVersion.parse("1.21.1")) < 0);
        truth("1.21.11 > 1.21.2 (numeric)", McVersion.parse("1.21.11").compareTo(McVersion.parse("1.21.2")) > 0);
        truth("26.1 > 1.21.11", McVersion.parse("26.1").compareTo(McVersion.parse("1.21.11")) > 0);
        eq("1.21 == 1.21.0", McVersion.parse("1.21"), McVersion.parse("1.21.0"));
        eq("java 1.16.5", 8, McVersion.parse("1.16.5").javaVersion());
        eq("java 1.17.1", 16, McVersion.parse("1.17.1").javaVersion());
        eq("java 1.20.4", 17, McVersion.parse("1.20.4").javaVersion());
        eq("java 1.20.5", 21, McVersion.parse("1.20.5").javaVersion());
        eq("java 1.21.11", 21, McVersion.parse("1.21.11").javaVersion());
        eq("java 26.1", 25, McVersion.parse("26.1").javaVersion());
        throwsIae("snapshot rejected", () -> McVersion.parse("26w14a"));
    }

    static void ranges() {
        eq("any maven", "[0,)", VersionRange.parse("*").toMaven());
        eq("any fabric", "*", VersionRange.parse("").toFabric());
        eq("exact maven", "[1.21.1]", VersionRange.parse("1.21.1").toMaven());
        eq("exact fabric", "1.21.1", VersionRange.parse("[1.21.1]").toFabric());
        eq("half-open maven", "[1.0.0,2.0.0)", VersionRange.parse("[1.0.0,2.0.0)").toMaven());
        eq("half-open fabric", ">=1.0.0 <2.0.0", VersionRange.parse("[1.0.0,2.0.0)").toFabric());
        eq("open lower", ">1 <=2", VersionRange.parse("(1,2]").toFabric());
        eq("lower only", ">=52", VersionRange.parse("[52,)").toFabric());
        eq("upper only", "<2", VersionRange.parse("(,2)").toFabric());
        throwsIae("malformed", () -> VersionRange.parse("[1,2"));
    }

    static void json() {
        Map<String, Object> m = Json.parseObject("{\"a\": [1, 2.5, \"x\\n\\u00e9\", true, null], \"b\": {}}");
        eq("parse list", List.of(1L, 2.5, "x\n\u00e9", true), ((List<?>) m.get("a")).subList(0, 4));
        eq("round trip", m, Json.parseObject(Json.write(m)));
        eq("empty containers", "{\n  \"b\": {},\n  \"c\": []\n}\n",
                Json.write(new LinkedHashMap<String, Object>() {{ put("b", new LinkedHashMap<>()); put("c", new ArrayList<>()); }}));
        throwsIae("trailing garbage", () -> Json.parse("{} x"));
        ModMetadata original = sample();
        ModMetadata copy = ModMetadata.fromJson(original.toJson());
        eq("metadata round trip", original.toJson(), copy.toJson());
        throwsIae("bad mod id", () -> ModMetadata.builder().modId("Bad-Id").version("1").name("n").build());
    }

    static void json5() {
        eq("comments and trailing commas", "{\"a\":1,\"b\":[1,2]}",
                Json5.toJson("// head\n{ /* x */ \"a\": 1, // y\n \"b\": [1, 2,], }\n// tail", ""));
        eq("pretty by default indent", "{\n  \"a\": 1,\n  \"b\": [\n    1,\n    {}\n  ],\n  \"c\": []\n}\n",
                Json5.toJson("{a:1,b:[1,{},],c:[]}", "  "));
        eq("tab indent", "{\n\t\"a\": true\n}\n", Json5.toJson("{a:true}", "\t"));
        eq("single quotes, unquoted keys", "{\"it's\":\"say \\\"hi\\\"\",\"$ref\":null,\"_x\":false,\"\u00fcn\":1}",
                Json5.toJson("{'it\\'s': 'say \"hi\"', $ref: null, _x: false, \u00fcn: 1}", ""));
        eq("reserved words as keys", "{\"true\":1,\"null\":2}", Json5.toJson("{true: 1, null: 2}", ""));
        eq("escapes", "[\"AB\",\"a\\nb\",\"\\u000b\",\"\\u0001\",\"\\u0000\",\"q\"]",
                Json5.toJson("['\\x41\\u0042', 'a\\nb', '\\v', '\\x01', '\\0', '\\q']", ""));
        eq("line continuation", "\"ab\"", Json5.toJson("'a\\\nb'", ""));
        eq("raw tab in string is escaped", "\"a\\tb\"", Json5.toJson("\"a\tb\"", ""));
        eq("surrogate pair kept", "\"\ud83d\ude00\"", Json5.toJson("\"\\ud83d\\ude00\"", ""));
        eq("lone surrogate escaped", "\"\\ud800\"", Json5.toJson("\"\\ud800\"", ""));
        eq("numbers are not rounded", "[31,-255,1,0.5,5,-0.5e3,1E+2,1.0,12345678901234567890.123456789,-0,0,2e-3]",
                Json5.toJson("[0x1F, -0xff, +1, .5, 5., -.5e3, 1E+2, 1.0, 12345678901234567890.123456789, -0, 0, 2e-3]", ""));
        eq("big hex", "18446744073709551615", Json5.toJson("0xFFFFFFFFFFFFFFFF", ""));
        eq("duplicate keys and order kept", "{\"b\":1,\"a\":2,\"b\":3}", Json5.toJson("{b:1,a:2,b:3}", ""));
        eq("empty containers stay", "{\"a\":{},\"b\":[]}", Json5.toJson("{a:{},b:[]}", ""));
        eq("top-level array", "[1]", Json5.toJson("[1,]", ""));
        eq("bom and whitespace", "{}", Json5.toJson("\uFEFF\u00a0 {}\u2028", ""));
        eq("plain json is unchanged", "{\"a\":[1,2.5,\"x\"],\"b\":{\"c\":null}}",
                Json5.toJson("{\"a\": [1, 2.5, \"x\"], \"b\": {\"c\": null}}", ""));

        eq("blank has no content", null, Json5.toJson("", "  "));
        eq("comments only has no content", null, Json5.toJson("// off\n/* {\"a\": 1} */\n", "  "));

        String on = "{\n  \"pack_format\": /*34*/ 48 //? comment\n}\n";
        String off = "{\n  \"pack_format\": 34 /*48*/\n}\n";
        eq("stonecutter style, enabled branch", "{\"pack_format\":48}", Json5.toJson(on, ""));
        eq("stonecutter style, disabled branch", "{\"pack_format\":34}", Json5.toJson(off, ""));
        eq("whole file switched off", null, Json5.toJson("/*\n{\"a\": 1}\n*/", ""));

        throwsIae("infinity", () -> Json5.toJson("[Infinity]", ""));
        throwsIae("negative infinity", () -> Json5.toJson("[-Infinity]", ""));
        throwsIae("nan", () -> Json5.toJson("[NaN]", ""));
        throwsIae("leading zero", () -> Json5.toJson("[01]", ""));
        throwsIae("bare dot", () -> Json5.toJson("[.]", ""));
        throwsIae("bare sign", () -> Json5.toJson("[-]", ""));
        throwsIae("empty exponent", () -> Json5.toJson("[1e]", ""));
        throwsIae("empty hex", () -> Json5.toJson("[0x]", ""));
        throwsIae("missing comma", () -> Json5.toJson("{\"a\":1 \"b\":2}", ""));
        throwsIae("missing comma in array", () -> Json5.toJson("[1 2]", ""));
        throwsIae("double comma", () -> Json5.toJson("[1,,2]", ""));
        throwsIae("lone comma in array", () -> Json5.toJson("[,]", ""));
        throwsIae("lone comma in object", () -> Json5.toJson("{,}", ""));
        throwsIae("missing value", () -> Json5.toJson("{\"a\":}", ""));
        throwsIae("missing colon", () -> Json5.toJson("{\"a\" 1}", ""));
        throwsIae("numeric key", () -> Json5.toJson("{1: 2}", ""));
        throwsIae("unterminated object", () -> Json5.toJson("{\"a\": 1", ""));
        throwsIae("unterminated array", () -> Json5.toJson("[1,", ""));
        throwsIae("unterminated string", () -> Json5.toJson("\"abc", ""));
        throwsIae("raw line break in string", () -> Json5.toJson("\"a\nb\"", ""));
        throwsIae("unterminated comment", () -> Json5.toJson("{} /* x", ""));
        throwsIae("trailing content", () -> Json5.toJson("{} {}", ""));
        throwsIae("trailing word", () -> Json5.toJson("{} x", ""));
        throwsIae("word that only starts like a literal", () -> Json5.toJson("[trueish]", ""));
        throwsIae("bad unicode escape", () -> Json5.toJson("\"\\u12\"", ""));
        throwsIae("bad hex escape", () -> Json5.toJson("\"\\xZZ\"", ""));
        throwsIae("octal escape", () -> Json5.toJson("\"\\1\"", ""));
        throwsIae("nul followed by digit", () -> Json5.toJson("\"\\01\"", ""));
        throwsIae("indent must be whitespace", () -> Json5.toJson("{}", "x"));
        throwsIae("nesting limit", () -> Json5.toJson("[".repeat(600), ""));
        eq("nesting within the limit", "[".repeat(100) + "]".repeat(100), Json5.toJson("[".repeat(100) + "]".repeat(100), ""));

        try {
            Json5.toJson("{\n  \"a\": 1,\n  \"b\": oops\n}", "");
            truth("error location: no exception", false);
        } catch (IllegalArgumentException e) {
            truth("error names the line: " + e.getMessage(), e.getMessage().contains("line 3, column 8"));
        }

        truth("json5 is commented", Json5.isCommented("data/x/a.json5"));
        truth("jsonc is commented", Json5.isCommented("data/x/a.jsonc"));
        truth("extension case is ignored", Json5.isCommented("A.JSON5"));
        truth("json is not commented", !Json5.isCommented("a.json"));
        truth("json5 inside a name is not commented", !Json5.isCommented("a.json5.txt"));
        eq("json5 output", "data/x/recipes/a.json", Json5.outputPath("data/x/recipes/a.json5"));
        eq("jsonc output", "a.json", Json5.outputPath("a.jsonc"));
        eq("uppercase output", "A.json", Json5.outputPath("A.JSON5"));
        eq("mcmeta keeps its extension", "pack.mcmeta", Json5.outputPath("pack.mcmeta.json5"));
        eq("animation mcmeta", "assets/x/textures/a.png.mcmeta", Json5.outputPath("assets/x/textures/a.png.mcmeta.jsonc"));
        eq("double json extension", "a.json", Json5.outputPath("a.json.json5"));
        eq("json untouched", "a.json", Json5.outputPath("a.json"));
        eq("other untouched", "a.png", Json5.outputPath("a.png"));
        eq("json sources", List.of("m.json", "m.json5", "m.jsonc"), Json5.sourcePaths("m.json"));
        eq("mcmeta sources", List.of("pack.mcmeta", "pack.mcmeta.json5", "pack.mcmeta.jsonc"), Json5.sourcePaths("pack.mcmeta"));
        eq("other sources", List.of("a.cfg"), Json5.sourcePaths("a.cfg"));
        for (String path : List.of("m.json", "pack.mcmeta", "a/b/c.json", "x.png.mcmeta")) {
            for (String source : Json5.sourcePaths(path)) eq("source maps back: " + source, path, Json5.outputPath(source));
        }

        List<ResourceRelocation> up = VanillaResourceRelocations.between("1.20.1", "1.21.1");
        eq("relocation sees the converted name", "data/ns/recipe/a.json",
                ResourceRelocation.applyAll(up, Json5.outputPath("data/ns/recipes/a.json5")));
        eq("relocation of a nested json5 tag", "data/ns/tags/block/mineable/pick.json",
                ResourceRelocation.applyAll(up, Json5.outputPath("data/ns/tags/blocks/mineable/pick.jsonc")));
        ResourceRelocation byName = ResourceRelocation.of("data/(*)/old/(*).json", "data/$1/new/$2.json");
        eq("rules written against .json match json5 sources", "data/ns/new/a.json",
                ResourceRelocation.applyAll(List.of(byName), Json5.outputPath("data/ns/old/a.json5")));

        String mixin = "// c\n{ package: 'de.x.mixin', compatibilityLevel: 'JAVA_21', mixins: ['A',], }";
        Map<String, List<String>> found = MixinConfigMerger.emptyBuckets();
        found.get("mixins").add("de.x.mixin.B");
        Map<String, Object> merged = Json.parseObject(MixinConfigMerger.merge(mixin, found, 17));
        eq("json5 mixin config merged", List.of("A", "B"), merged.get("mixins"));
        eq("json5 mixin config compat level", "JAVA_17", merged.get("compatibilityLevel"));
        throwsIae("empty mixin config", () -> MixinConfigMerger.merge("// nothing", MixinConfigMerger.emptyBuckets(), 17));
    }

    static void matrix() {
        eq("fabric 1.14.4 ok", List.of(), LoaderMatrix.validate("1.14.4", Set.of("fabric")));
        eq("forge 1.14.4 ok", List.of(), LoaderMatrix.validate("1.14.4", Set.of("forge")));
        eq("26.2 all ok", List.of(), LoaderMatrix.validate("26.2", Set.of("fabric", "forge", "neoforge")));
        eq("neoforge 1.20.1 rejected", 1, LoaderMatrix.validate("1.20.1", Set.of("neoforge")).size());
        eq("neoforge 1.20.2 ok", List.of(), LoaderMatrix.validate("1.20.2", Set.of("neoforge")));
        eq("neoforge 1.20.3 ok", List.of(), LoaderMatrix.validate("1.20.3", Set.of("neoforge")));
        eq("neoforge 1.20.4 ok", List.of(), LoaderMatrix.validate("1.20.4", Set.of("neoforge")));
        truth("neoforge supports 1.20.2 but not 1.20.1", LoaderType.NEOFORGE.supports(McVersion.parse("1.20.2"))
                && !LoaderType.NEOFORGE.supports(McVersion.parse("1.20.1")));
        eq("below minimum: version + both loaders", 3, LoaderMatrix.validate("1.13.2", Set.of("forge", "fabric")).size());
        eq("quilt not buildable", 1, LoaderMatrix.validate("1.21.1", Set.of("quilt")).size());
        eq("no loader", 1, LoaderMatrix.validate("1.21.1", Set.of()).size());
        eq("node name", "1.21.1-forge", LoaderType.FORGE.nodeName("1.21.1"));
        eq("branch name", "neoforge", LoaderType.NEOFORGE.branchName());
        eq("branch loader", LoaderType.NEOFORGE, LoaderType.fromBranchName("neoforge"));
        eq("vcs entry node form", "26.1.2", LoaderType.minecraftOfVcsEntry("26.1.2-fabric"));
        eq("vcs entry bare", "1.21.1", LoaderType.minecraftOfVcsEntry("1.21.1"));
        eq("vcs entry unknown suffix", "1.21.1-rc1", LoaderType.minecraftOfVcsEntry("1.21.1-rc1"));
        eq("metadata path neoforge new", "META-INF/neoforge.mods.toml", LoaderType.NEOFORGE.metadataPath(McVersion.parse("1.21.1")));
        eq("metadata path neoforge old", "META-INF/mods.toml", LoaderType.NEOFORGE.metadataPath(McVersion.parse("1.20.4")));
        eq("metadata path neoforge 1.20.2", "META-INF/mods.toml", LoaderType.NEOFORGE.metadataPath(McVersion.parse("1.20.2")));
        truth("neoforge uses NeoGradle for 1.20.2 and 1.20.3", NeoForgeRules.usesNeoGradle(McVersion.parse("1.20.2"))
                && NeoForgeRules.usesNeoGradle(McVersion.parse("1.20.3")));
        truth("neoforge uses ModDevGradle from 1.20.4", !NeoForgeRules.usesNeoGradle(McVersion.parse("1.20.4"))
                && !NeoForgeRules.usesNeoGradle(McVersion.parse("1.20.6"))
                && !NeoForgeRules.usesNeoGradle(McVersion.parse("1.21.1"))
                && !NeoForgeRules.usesNeoGradle(McVersion.parse("26.2")));
    }

    static void generators() {
        ModMetadata m = sample();

        GeneratedOutput fabric = MetadataGenerators.generate(m, target(LoaderType.FABRIC, "1.21.1", "0.18.4"));
        eq("fabric file", "fabric.mod.json", fabric.files().get(0).path());
        Map<String, Object> fj = Json.parseObject(fabric.files().get(0).content());
        eq("fabric id", "example_mod", fj.get("id"));
        @SuppressWarnings("unchecked") Map<String, Object> depends = (Map<String, Object>) fj.get("depends");
        eq("fabric loader dep", ">=0.18.4", depends.get("fabricloader"));
        eq("fabric mc dep", "1.21.1", depends.get("minecraft"));
        eq("fabric java dep", ">=21", depends.get("java"));
        eq("fabric api dep", "*", depends.get("fabric-api"));
        eq("fabric user dep", ">=1.0.0 <2.0.0", depends.get("example_lib"));
        truth("fabric breaks", fj.containsKey("breaks") && fj.containsKey("conflicts") && fj.containsKey("suggests"));
        eq("fabric aw", "example_mod.ct", fj.get("accessWidener"));
        eq("fabric no warnings", List.of(), fabric.warnings());

        GeneratedOutput forge = MetadataGenerators.generate(m, target(LoaderType.FORGE, "1.21.1", "52.1.16"));
        eq("forge files", List.of("META-INF/mods.toml", "pack.mcmeta"),
                forge.files().stream().map(GeneratedFile::path).toList());
        String toml = forge.files().get(0).content();
        truth("forge loaderVersion", toml.contains("loaderVersion=\"[52,)\""));
        truth("forge mc dep exact", toml.contains("versionRange=\"[1.21.1]\""));
        truth("forge mandatory key", toml.contains("mandatory=true") && toml.contains("mandatory=false"));
        truth("forge has no [[mixins]]", !toml.contains("[[mixins]]"));
        eq("forge warnings (discouraged + incompatible)", 2, forge.warnings().size());
        truth("pack format 1.21.1", forge.files().get(1).content().contains("\"pack_format\": 70"));

        GeneratedOutput neo = MetadataGenerators.generate(m, target(LoaderType.NEOFORGE, "1.21.1", "21.1.228"));
        eq("neoforge file", "META-INF/neoforge.mods.toml", neo.files().get(0).path());
        String ntoml = neo.files().get(0).content();
        truth("neoforge typed deps", ntoml.contains("type=\"incompatible\"") && ntoml.contains("type=\"discouraged\"") && !ntoml.contains("mandatory"));
        truth("neoforge mixins", ntoml.contains("[[mixins]]\nconfig=\"example_mod.mixins.json\""));
        truth("neoforge dep range", ntoml.contains("modId=\"neoforge\"\ntype=\"required\"\nversionRange=\"[21.1,)\""));
        eq("neoforge no warnings", List.of(), neo.warnings());

        GeneratedOutput neo4 = MetadataGenerators.generate(m, target(LoaderType.NEOFORGE, "1.20.4", "20.4.237"));
        eq("neoforge 1.20.4 file", "META-INF/mods.toml", neo4.files().get(0).path());
        truth("neoforge 1.20.4 mandatory", neo4.files().get(0).content().contains("mandatory=true"));

        GeneratedOutput neo2 = MetadataGenerators.generate(m, target(LoaderType.NEOFORGE, "1.20.2", "20.2.86"));
        eq("neoforge 1.20.2 file", "META-INF/mods.toml", neo2.files().get(0).path());
        String neo2toml = neo2.files().get(0).content();
        truth("neoforge 1.20.2 mandatory", neo2toml.contains("mandatory=true") && !neo2toml.contains("type=\"required\""));
        truth("neoforge 1.20.2 dep range", neo2toml.contains("modId=\"neoforge\"\nmandatory=true\nversionRange=\"[20.2,)\""));
        truth("neoforge 1.20.2 mixins", neo2toml.contains("[[mixins]]\nconfig=\"example_mod.mixins.json\""));
        truth("neoforge 1.20.2 no forge dep", !neo2toml.contains("modId=\"forge\""));
        eq("java 1.20.2", 17, McVersion.parse("1.20.2").javaVersion());

        GeneratedOutput old = MetadataGenerators.generate(m, target(LoaderType.FORGE, "1.14.4", "28.2.26"));
        truth("forge 1.14.4 range", old.files().get(0).content().contains("loaderVersion=\"[28,)\""));
        truth("pack format 1.14.4", old.files().get(1).content().contains("\"pack_format\": 70"));

        GeneratedOutput future = MetadataGenerators.generate(m, withoutPackFormat(target(LoaderType.FORGE, "26.3", "65.0.0")));
        eq("unknown pack format -> no pack.mcmeta", 1, future.files().size());
        truth("unknown pack format warns", future.warnings().stream().anyMatch(w -> w.contains("pack.mcmeta")));
        ModMetadata withFormat = ModMetadata.fromJson(m.toJson().replace("\"quilt\": false", "\"quilt\": false,\n  \"packFormat\": 99"));
        GeneratedOutput explicit = MetadataGenerators.generate(withFormat, target(LoaderType.FORGE, "26.3", "65.0.0"));
        truth("explicit pack format wins", explicit.files().get(1).content().contains("\"pack_format\": 99"));

        ModMetadata quilt = ModMetadata.fromJson(m.toJson().replace("\"quilt\": false", "\"quilt\": true"));
        GeneratedOutput both = MetadataGenerators.generate(quilt, target(LoaderType.FABRIC, "1.21.1", "0.18.4"));
        eq("fabric + quilt files", List.of("fabric.mod.json", "quilt.mod.json"),
                both.files().stream().map(GeneratedFile::path).toList());
        truth("quilt skips discouraged", both.warnings().stream().anyMatch(w -> w.contains("discouraged")));

        ModMetadata overrides = ModMetadata.builder().modId("ov").version("1").name("n")
                .loaderVersionRange(LoaderType.FORGE, "[50,52)")
                .dependency("minecraft", ModMetadata.DependencyType.REQUIRED, "[1.21,1.22)").build();
        String otoml = MetadataGenerators.generate(overrides, target(LoaderType.FORGE, "1.21.1", "52.1.16")).files().get(0).content();
        truth("loader range override", otoml.contains("loaderVersion=\"[50,52)\""));
        eq("user minecraft dep replaces auto", 1, otoml.split("modId=\"minecraft\"", -1).length - 1);

        ModMetadata multiline = ModMetadata.builder().modId("ml").version("1").name("n").description("line one\nline two").build();
        truth("multiline description literal", MetadataGenerators.generate(multiline, target(LoaderType.FORGE, "1.21.1", "52.1.16"))
                .files().get(0).content().contains("description='''\nline one\nline two\n'''"));
    }


    @SuppressWarnings("unchecked")

    static void projectDescription() {
        var repo = de.rhm176.build.model.changelog.RepoLinks.parse("https://github.com/o/r").orElseThrow();
        var codeberg = de.rhm176.build.model.changelog.RepoLinks.parse("https://codeberg.org/o/r").orElseThrow();
        String gh = "https://github.com/o/r/";
        String raw = "https://raw.githubusercontent.com/o/r/main/";

        eq("blob github", gh + "blob/main/docs/a.md", repo.blobFile("main", "docs/a.md").orElseThrow());
        eq("blob codeberg", "https://codeberg.org/o/r/src/branch/main/a.md", codeberg.blobFile("main", "/a.md").orElseThrow());

        eq("image and link", "![logo](" + raw + "assets/logo.png) and [docs](" + gh + "blob/main/docs/a.md)\n",
                ProjectDescription.prepare("![logo](assets/logo.png) and [docs](docs/a.md)", "", repo, "main").text());
        eq("badge image inside a link", "[![b](" + raw + "b.svg)](" + gh + "blob/main/LICENSE)\n",
                ProjectDescription.prepare("[![b](b.svg)](LICENSE)", "", repo, "main").text());
        eq("absolute, anchor and mailto stay", "[a](https://x.y) [b](#c) [d](mailto:a@b.c) [e](//x.y/z)\n",
                ProjectDescription.prepare("[a](https://x.y) [b](#c) [d](mailto:a@b.c) [e](//x.y/z)", "", repo, "main").text());
        eq("fragment and query kept", "[a](" + gh + "blob/main/a.md#top)\n",
                ProjectDescription.prepare("[a](a.md#top)", "", repo, "main").text());
        eq("title kept", "![a](" + raw + "a.png \"T\")\n", ProjectDescription.prepare("![a](a.png \"T\")", "", repo, "main").text());
        eq("angle brackets", "[a](<" + gh + "blob/main/my file.md>)\n", ProjectDescription.prepare("[a](<my file.md>)", "", repo, "main").text());
        eq("html attributes", "<img src=\"" + raw + "a.png\" alt=\"x\"> <a href=\"" + gh + "blob/main/b.md\">b</a>\n",
                ProjectDescription.prepare("<img src=\"a.png\" alt=\"x\"> <a href=\"b.md\">b</a>", "", repo, "main").text());
        eq("reference definition", "[x]: " + gh + "blob/main/a.md\n[y]: " + raw + "a.png\n",
                ProjectDescription.prepare("[x]: a.md\n[y]: a.png", "", repo, "main").text());
        eq("readme in a folder", "![a](" + raw + "docs/img/a.png) [b](" + gh + "blob/main/c.md) [c](" + gh + "blob/main/root.md)\n",
                ProjectDescription.prepare("![a](img/a.png) [b](../c.md) [c](/root.md)", "docs", repo, "main").text());
        eq("code is left alone", "`[a](a.md)` and [b](" + gh + "blob/main/b.md)\n```\n[c](c.md)\n```\n",
                ProjectDescription.prepare("`[a](a.md)` and [b](b.md)\n```\n[c](c.md)\n```", "", repo, "main").text());
        eq("tilde fence", "~~~\n![a](a.png)\n~~~\n", ProjectDescription.prepare("~~~\n![a](a.png)\n~~~", "", repo, "main").text());
        eq("crlf", "a\nb\n", ProjectDescription.prepare("a\r\nb\r\n", "", repo, "main").text());

        var unresolved = ProjectDescription.prepare("[a](a.md) [b](../../x.md)", "docs", repo, "main");
        eq("outside the repository stays and is reported", List.of("../../x.md"), unresolved.unresolved());
        var noRepo = ProjectDescription.prepare("[a](a.md)", "", null, "main");
        eq("no repository: stays and is reported", "[a](a.md)\n", noRepo.text());
        eq("no repository: reported", List.of("a.md"), noRepo.unresolved());
        var unknownHost = ProjectDescription.prepare("![a](a.png)", "", de.rhm176.build.model.changelog.RepoLinks.parse("https://git.example.com/o/r").orElseThrow(), "main");
        eq("image on an unknown host has no raw url", List.of("a.png"), unknownHost.unresolved());

        eq("skipped part", "top\nbottom\n", ProjectDescription.prepare("top\n<!-- sync:off -->\n## Building\nrun it\n<!-- sync:on -->\nbottom", "", repo, "main").text());
        eq("two skipped parts", "a\nc\ne\n", ProjectDescription.prepare("a\n<!-- sync:off -->\nb\n<!-- sync:on -->\nc\n<!-- sync:off -->\nd\n<!-- sync:on -->\ne", "", repo, "main").text());
        eq("a marker inside a sentence is just text", "Use `<!-- sync:off -->` and `<!-- sync:on -->` like this.\n",
                ProjectDescription.prepare("Use `<!-- sync:off -->` and `<!-- sync:on -->` like this.", "", repo, "main").text());
        eq("marker with spaces around it", "a\nc\n", ProjectDescription.prepare("a\n  <!-- sync:off -->  \nb\n<!-- sync:on -->\nc", "", repo, "main").text());
        throwsIae("unclosed off", () -> ProjectDescription.prepare("a\n<!-- sync:off -->\nb", "", repo, "main"));
        throwsIae("on without off", () -> ProjectDescription.prepare("a\n<!-- sync:on -->\nb", "", repo, "main"));
        throwsIae("off twice", () -> ProjectDescription.prepare("<!-- sync:off -->\n<!-- sync:off -->\n<!-- sync:on -->", "", repo, "main"));
        throwsIae("nothing left", () -> ProjectDescription.prepare("<!-- sync:off -->\nall\n<!-- sync:on -->", "", repo, "main"));
        throwsIae("too long", () -> ProjectDescription.prepare("x".repeat(ProjectDescription.MODRINTH_MAX_LENGTH + 1), "", repo, "main"));
        eq("exactly the limit is fine", ProjectDescription.MODRINTH_MAX_LENGTH + 1,
                ProjectDescription.prepare("x".repeat(ProjectDescription.MODRINTH_MAX_LENGTH), "", repo, "main").text().length());
    }

    static void datagenRules() {
        truth("datagen 1.18", DatagenRules.supports(McVersion.parse("1.18")));
        truth("datagen 1.21.1", DatagenRules.supports(McVersion.parse("1.21.1")));
        truth("datagen 26.1", DatagenRules.supports(McVersion.parse("26.1")));
        truth("no datagen 1.17.1", !DatagenRules.supports(McVersion.parse("1.17.1")));
        truth("no datagen 1.14.4", !DatagenRules.supports(McVersion.parse("1.14.4")));
        eq("datagen from", "1.18", DatagenRules.FIRST_MINECRAFT);
    }

    static void mixinExtras() {
        McVersion v121 = McVersion.parse("1.21.1");
        eq("extras fabric new", MixinExtrasRules.Delivery.PROVIDED_BY_LOADER, MixinExtrasRules.delivery(LoaderType.FABRIC, v121, "0.18.4"));
        eq("extras fabric 0.15.0", MixinExtrasRules.Delivery.PROVIDED_BY_LOADER, MixinExtrasRules.delivery(LoaderType.FABRIC, v121, "0.15.0"));
        eq("extras fabric old", MixinExtrasRules.Delivery.BUNDLED, MixinExtrasRules.delivery(LoaderType.FABRIC, McVersion.parse("1.20.1"), "0.14.25"));
        eq("extras neoforge new", MixinExtrasRules.Delivery.PROVIDED_BY_LOADER, MixinExtrasRules.delivery(LoaderType.NEOFORGE, v121, "21.1.228"));
        eq("extras neoforge cutoff", MixinExtrasRules.Delivery.PROVIDED_BY_LOADER, MixinExtrasRules.delivery(LoaderType.NEOFORGE, McVersion.parse("1.20.2"), "20.2.84"));
        eq("extras neoforge before cutoff", MixinExtrasRules.Delivery.BUNDLED, MixinExtrasRules.delivery(LoaderType.NEOFORGE, McVersion.parse("1.20.2"), "20.2.59-beta"));
        eq("extras forge 1.18.2", MixinExtrasRules.Delivery.BUNDLED, MixinExtrasRules.delivery(LoaderType.FORGE, McVersion.parse("1.18.2"), "40.2.0"));
        eq("extras forge 1.21.1", MixinExtrasRules.Delivery.BUNDLED, MixinExtrasRules.delivery(LoaderType.FORGE, v121, "52.1.16"));
        eq("extras forge 1.16.5", MixinExtrasRules.Delivery.UNSUPPORTED, MixinExtrasRules.delivery(LoaderType.FORGE, McVersion.parse("1.16.5"), "36.2.39"));
        eq("extras forge 1.18.1", MixinExtrasRules.Delivery.UNSUPPORTED, MixinExtrasRules.delivery(LoaderType.FORGE, McVersion.parse("1.18.1"), "39.1.0"));
        eq("extras artifact fabric", "mixinextras-fabric", MixinExtrasRules.bundledArtifact(LoaderType.FABRIC));
        eq("extras artifact forge", "mixinextras-forge", MixinExtrasRules.bundledArtifact(LoaderType.FORGE));
        eq("extras artifact neoforge", "mixinextras-neoforge", MixinExtrasRules.bundledArtifact(LoaderType.NEOFORGE));
        truth("compare numeric not textual", MixinExtrasRules.compareDotted("0.100.0", "0.15.0") > 0);
        truth("compare missing part is 0", MixinExtrasRules.compareDotted("20.2", "20.2.0") == 0);
        truth("compare ignores suffix", MixinExtrasRules.compareDotted("21.1.228-beta", "21.1.228") == 0);
        truth("compare blank is 0", MixinExtrasRules.compareDotted("", "0") == 0);
    }

    static void updateJson() {
        eq("raw url github", "https://raw.githubusercontent.com/o/r/main/update.json",
                UpdateJson.url(null, "https://github.com/o/r", "main", "update.json"));
        eq("raw url github deeper link", "https://raw.githubusercontent.com/o/r/dev/x/u.json",
                UpdateJson.url(" ", "https://github.com/o/r/issues", "dev", "/x/u.json"));
        eq("raw url explicit wins", "https://example.com/u.json",
                UpdateJson.url("https://example.com/u.json", "https://github.com/o/r", "main", "update.json"));
        eq("raw url gitlab", "https://gitlab.com/o/r/-/raw/main/update.json",
                UpdateJson.url(null, "https://gitlab.com/o/r", "main", "update.json"));
        eq("raw url codeberg", "https://codeberg.org/o/r/raw/branch/main/update.json",
                UpdateJson.url(null, "https://codeberg.org/o/r", "main", "update.json"));
        eq("raw url unknown host", null, UpdateJson.url(null, "https://git.example.com/o/r", "main", "update.json"));
        eq("raw url no repository", null, UpdateJson.url(null, null, "main", "update.json"));
        throwsIae("raw url not http", () -> UpdateJson.url("ftp://x/y", null, "main", "update.json"));

        eq("homepage explicit", "https://a", UpdateJson.homepage("https://a", "m", "1", "o/r", "https://h"));
        eq("homepage modrinth", "https://modrinth.com/mod/m", UpdateJson.homepage(null, "m", "1", "o/r", "https://h"));
        eq("homepage curseforge", "https://www.curseforge.com/projects/1", UpdateJson.homepage("", null, "1", "o/r", "https://h"));
        eq("homepage github", "https://github.com/o/r/releases", UpdateJson.homepage(null, null, null, "o/r", "https://h"));
        eq("homepage mod url", "https://h", UpdateJson.homepage(null, null, null, null, "https://h"));
        eq("homepage none", null, UpdateJson.homepage(null, null, null, null, null));

        eq("plain text", "Features:\n\n- Adds a thing (abc1234)\n- Scope: fixes it\n\nFull Changelog: v1...v2",
                UpdateJson.plainText("### Features\n\n- Adds a thing ([`abc1234`](https://x/y))\n- **Scope:** fixes it\n\n\n\n**Full Changelog**: [`v1...v2`](https://x)\n"));
        eq("plain text first release", "First release!", UpdateJson.plainText("**First release!**"));
        eq("plain text empty", "", UpdateJson.plainText(null));
        eq("plain text quote", "- Breaking\n    note", UpdateJson.plainText("* Breaking\n  > note"));

        String first = UpdateJson.merge(null, "https://modrinth.com/mod/m", "1.0.0", List.of("1.21", "1.21.1"), "### Features\n- a", true);
        Map<String, Object> j = Json.parseObject(first);
        eq("first keys", List.of("homepage", "1.21", "1.21.1", "promos"), new ArrayList<>(j.keySet()));
        eq("first homepage", "https://modrinth.com/mod/m", j.get("homepage"));
        @SuppressWarnings("unchecked") Map<String, Object> promos = (Map<String, Object>) j.get("promos");
        eq("first promos", Map.of("1.21-latest", "1.0.0", "1.21-recommended", "1.0.0",
                "1.21.1-latest", "1.0.0", "1.21.1-recommended", "1.0.0"), promos);
        eq("first changelog", Map.of("1.0.0", "Features:\n- a"), j.get("1.21.1"));

        String second = UpdateJson.merge(first, null, "1.1.0-beta.1", List.of("1.21.1"), "- b", false);
        j = Json.parseObject(second);
        eq("second keys", List.of("homepage", "1.21", "1.21.1", "promos"), new ArrayList<>(j.keySet()));
        @SuppressWarnings("unchecked") Map<String, Object> v121 = (Map<String, Object>) j.get("1.21.1");
        eq("second order", List.of("1.1.0-beta.1", "1.0.0"), new ArrayList<>(v121.keySet()));
        eq("second homepage kept", "https://modrinth.com/mod/m", j.get("homepage"));
        @SuppressWarnings("unchecked") Map<String, Object> p2 = (Map<String, Object>) j.get("promos");
        eq("second latest", "1.1.0-beta.1", p2.get("1.21.1-latest"));
        eq("second recommended untouched", "1.0.0", p2.get("1.21.1-recommended"));
        eq("second other mc untouched", "1.0.0", p2.get("1.21-latest"));
        eq("second other mc history", Map.of("1.0.0", "Features:\n- a"), j.get("1.21"));

        String again = UpdateJson.merge(second, null, "1.0.0", List.of("1.21.1"), "- fixed text", true);
        j = Json.parseObject(again);
        @SuppressWarnings("unchecked") Map<String, Object> v3 = (Map<String, Object>) j.get("1.21.1");
        eq("again order", List.of("1.0.0", "1.1.0-beta.1"), new ArrayList<>(v3.keySet()));
        eq("again text", "- fixed text", v3.get("1.0.0"));

        eq("stable", again, UpdateJson.merge(again, null, "1.0.0", List.of("1.21.1"), "- fixed text", true));
        truth("trailing newline", again.endsWith("}\n"));

        throwsIae("merge garbage", () -> UpdateJson.merge("not json", null, "1", List.of("1.21"), "", true));
        throwsIae("merge array", () -> UpdateJson.merge("[]", null, "1", List.of("1.21"), "", true));
        throwsIae("merge bad promos", () -> UpdateJson.merge("{\"promos\": 1}", null, "1", List.of("1.21"), "", true));
        throwsIae("merge bad mc entry", () -> UpdateJson.merge("{\"1.21\": []}", null, "1", List.of("1.21"), "", true));
        throwsIae("merge no minecraft", () -> UpdateJson.merge(null, null, "1", List.of(), "", true));
        throwsIae("merge bad minecraft", () -> UpdateJson.merge(null, null, "1", List.of("latest"), "", true));
        throwsIae("merge no version", () -> UpdateJson.merge(null, null, " ", List.of("1.21"), "", true));

        String foreign = UpdateJson.merge("{\"homepage\":\"https://old\",\"custom\":{\"a\":1}}", null, "1.0.0", List.of("1.21"), "", true);
        j = Json.parseObject(foreign);
        eq("foreign kept", Map.of("a", 1L), j.get("custom") instanceof Map<?, ?> m ? normalize(m) : null);
        eq("foreign homepage kept", "https://old", j.get("homepage"));
    }

    private static Map<String, Object> normalize(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v instanceof Number n ? (Object) n.longValue() : v));
        return out;
    }

    static void modMenu() {
        MetadataTarget plain = target(LoaderType.FABRIC, "1.21.1", "0.18.4");
        MetadataTarget active = new MetadataTarget(plain.loader(), plain.minecraft(), plain.minecraftRange(), plain.loaderVersion(),
                plain.javaVersion(), plain.fabricApi(), Map.of("main", List.of("a.B"), "modmenu", List.of("a.M")),
                plain.mixinConfigs(), plain.accessWidener(), plain.packFormat(), true);
        ModMenuMetadata mm = ModMenuMetadata.builder()
                .link("discord", "https://discord.gg/x").link("my_mod.wiki", "https://wiki.example/")
                .badge(ModMenuMetadata.Badge.LIBRARY).parent(new ModMenuMetadata.Parent("flamingo")).updateChecker(false).build();
        ModMetadata base = ModMetadata.builder().modId("example_mod").version("1.0.0").name("Example Mod").modMenu(mm).build();

        eq("link key from type", "modmenu.discord", ModMenuMetadata.linkKey("discord"));
        eq("own key stays", "my_mod.wiki", ModMenuMetadata.linkKey("my_mod.wiki"));
        throwsIae("bad link key", () -> ModMenuMetadata.linkKey("Not A Key"));
        throwsIae("link needs a url", () -> ModMenuMetadata.builder().link("discord", "discord.gg/x"));
        throwsIae("unknown badge", () -> ModMenuMetadata.Badge.fromId("client"));
        throwsIae("bad parent id", () -> new ModMenuMetadata.Parent("Bad Id"));
        throwsIae("own parent", () -> ModMetadata.builder().modId("a_b").version("1").name("n")
                .modMenu(ModMenuMetadata.builder().parent(new ModMenuMetadata.Parent("a_b")).build()).build());

        Map<String, Object> off = Json.parseObject(MetadataGenerators.generate(base, plain).files().get(0).content());
        truth("no modmenu block without the flag", !off.containsKey("custom"));
        GeneratedOutput out = MetadataGenerators.generate(base, active);
        Map<String, Object> fj = Json.parseObject(out.files().get(0).content());
        Map<String, Object> mj = (Map<String, Object>) ((Map<String, Object>) fj.get("custom")).get("modmenu");
        eq("links", Map.of("modmenu.discord", "https://discord.gg/x", "my_mod.wiki", "https://wiki.example/"), mj.get("links"));
        eq("badges", List.of("library"), mj.get("badges"));
        eq("parent as plain string", "flamingo", mj.get("parent"));
        eq("update_checker false", false, mj.get("update_checker"));
        eq("modmenu entrypoint", List.of("a.M"), ((Map<String, Object>) fj.get("entrypoints")).get("modmenu"));
        eq("no warnings", List.of(), out.warnings());

        ModMenuMetadata dummy = ModMenuMetadata.builder().parent(new ModMenuMetadata.Parent("fake-mod", "Fake", "Does things", "assets/x/i.png",
                java.util.EnumSet.of(ModMenuMetadata.Badge.LIBRARY))).build();
        Map<String, Object> dj = dummy.toMetadata();
        eq("dummy parent object", Map.of("id", "fake-mod", "name", "Fake", "description", "Does things", "icon", "assets/x/i.png", "badges", List.of("library")), dj.get("parent"));
        eq("nothing set, nothing written", Map.of(), ModMenuMetadata.builder().build().toMetadata());

        eq("round trip", base.toJson(), ModMetadata.fromJson(base.toJson()).toJson());
        truth("no key without modMenu", !sample().toJson().contains("modMenu"));
        eq("round trip parent object", dummy.toJson(), ModMenuMetadata.fromJson(Json.parseObject(Json.write(dummy.toJson()))).toJson());


        ModMenuMetadata none = ModMenuMetadata.builder().build().resolve(null, " ", null);
        eq("nothing published: nothing derived", Map.of(), none.toMetadata());
        ModMenuMetadata all = ModMenuMetadata.builder().link("modrinth", "https://example.org/mine").build().resolve("AANobbMI", "123456", "me/repo");
        eq("derived links, own one kept", Map.of("modmenu.modrinth", "https://example.org/mine", "modmenu.curseforge", "https://www.curseforge.com/projects/123456",
                "modmenu.github_releases", "https://github.com/me/repo/releases"), all.links());
        eq("modrinth project states the update checker", true, all.updateChecker());
        eq("explicit false is kept", false, ModMenuMetadata.builder().updateChecker(false).build().resolve("AANobbMI", null, null).updateChecker());
        eq("autoLinks off", Map.of(), ModMenuMetadata.builder().autoLinks(false).build().resolve("a1", "1", "me/repo").links());
        eq("autoLinks survives the round trip", false, ModMenuMetadata.fromJson(Json.parseObject(Json.write(ModMenuMetadata.builder().autoLinks(false).build().toJson()))).autoLinks());

        ModMetadata own = ModMetadata.builder().modId("example_mod").version("1").name("n").modMenu(mm)
                .custom("modmenu", Map.of("update_checker", true, "extra", 1L)).build();
        Map<String, Object> merged = (Map<String, Object>) ((Map<String, Object>) Json.parseObject(MetadataGenerators.generate(own, active).files().get(0).content()).get("custom")).get("modmenu");
        eq("custom() overrides one key", true, merged.get("update_checker"));
        truth("and keeps the generated ones", merged.containsKey("links") && merged.containsKey("badges") && merged.containsKey("extra"));
        ModMetadata clash = ModMetadata.builder().modId("example_mod").version("1").name("n").modMenu(mm).custom("modmenu", "nope").build();
        truth("non-object custom modmenu warns", MetadataGenerators.generate(clash, active).warnings().stream().anyMatch(w -> w.contains("not an object")));
        ModMetadata onlyCustom = ModMetadata.builder().modId("example_mod").version("1").name("n").modMenu(ModMenuMetadata.builder().build())
                .custom("modmenu", Map.of("badges", List.of("deprecated"))).build();
        eq("empty settings keep your own custom", List.of("deprecated"), ((Map<String, Object>) ((Map<String, Object>) Json.parseObject(
                MetadataGenerators.generate(onlyCustom, active).files().get(0).content()).get("custom")).get("modmenu")).get("badges"));

        ModMenuMetadata typo = ModMenuMetadata.builder().link("discrod", "https://x.example").link("bluesky", "https://x.example").build();
        eq("typo warns once, bluesky doesn't", 1, typo.warnings().size());

        ModMetadata q = ModMetadata.fromJson(base.toJson().replace("\"quilt\": false", "\"quilt\": true"));
        GeneratedOutput both = MetadataGenerators.generate(q, active);
        Map<String, Object> qj = Json.parseObject(both.files().get(1).content());
        eq("quilt file", "quilt.mod.json", both.files().get(1).path());
        eq("quilt modmenu object", mj, qj.get("modmenu"));
        eq("quilt entrypoint key stays", List.of("a.M"), ((Map<String, Object>) ((Map<String, Object>) qj.get("quilt_loader")).get("entrypoints")).get("modmenu"));
        truth("quilt without the flag has none", !Json.parseObject(MetadataGenerators.generate(q, plain).files().get(1).content()).containsKey("modmenu"));

        for (LoaderType l : List.of(LoaderType.FORGE, LoaderType.NEOFORGE)) {
            String f = MetadataGenerators.generate(base, new MetadataTarget(l, McVersion.parse("1.21.1"), VersionRange.exactly("1.21.1"), "1.0", 21, false,
                    Map.of(), List.of(), null, 70, true)).files().get(0).content();
            truth(l.id() + " ignores it", !f.toLowerCase().contains("modmenu"));
        }
    }

    static void forgeMixinRules() {
        eq("refmap name", "example_mod.refmap.json", ForgeMixinRules.refmapName("example_mod"));
        truth("SRG up to 1.20.1", ForgeMixinRules.needsRefmap(McVersion.parse("1.20.1")));
        truth("no SRG from 1.20.2", !ForgeMixinRules.needsRefmap(McVersion.parse("1.20.2")));
        truth("legacy plugin 1.17.1", ForgeMixinRules.usesLegacyPlugin(McVersion.parse("1.17.1")));
        truth("legacy plugin 1.20.1", ForgeMixinRules.usesLegacyPlugin(McVersion.parse("1.20.1")));
        truth("no legacy plugin 1.20.2", !ForgeMixinRules.usesLegacyPlugin(McVersion.parse("1.20.2")));
        truth("no legacy plugin 1.16.5", !ForgeMixinRules.usesLegacyPlugin(McVersion.parse("1.16.5")));
        truth("own refmap 1.14.4 - 1.16.5", ForgeMixinRules.generatesOwnRefmap(McVersion.parse("1.16.5"))
                && ForgeMixinRules.generatesOwnRefmap(McVersion.parse("1.14.4")));
        truth("no own refmap 1.17 (legacy plugin) and 1.20.2 (not needed)", !ForgeMixinRules.generatesOwnRefmap(McVersion.parse("1.17"))
                && !ForgeMixinRules.generatesOwnRefmap(McVersion.parse("1.20.2")));
        truth("SRG names up to 1.20.1", ForgeMixinRules.usesSrgNames(McVersion.parse("1.20.1")) && !ForgeMixinRules.usesSrgNames(McVersion.parse("1.20.2")));
        truth("bootstrap 1.14.4 only", ForgeMixinRules.needsMixinBootstrap(McVersion.parse("1.14.4"))
                && !ForgeMixinRules.needsMixinBootstrap(McVersion.parse("1.15.2")));
        eq("min forge 1.14.4", "28.1.45", ForgeMixinRules.minimumForgeVersion(McVersion.parse("1.14.4")).get());
        eq("min forge 1.15.2", "31.2.44", ForgeMixinRules.minimumForgeVersion(McVersion.parse("1.15.2")).get());
        eq("min forge 1.16.1", "32.0.72", ForgeMixinRules.minimumForgeVersion(McVersion.parse("1.16.1")).get());
        truth("no min forge 1.16.5", ForgeMixinRules.minimumForgeVersion(McVersion.parse("1.16.5")).isEmpty());

        ModMetadata m = ModMetadata.builder().modId("mm").version("1").name("n").build();
        String t14 = MetadataGenerators.generate(m, target(LoaderType.FORGE, "1.14.4", "28.2.26")).files().get(0).content();
        truth("1.14.4 forge range raised", t14.contains("loaderVersion=\"[28,)\"") && t14.contains("modId=\"forge\"\nmandatory=true\nversionRange=\"[28.1.45,)\""));
        truth("1.14.4 mixinbootstrap dep", t14.contains("modId=\"mixinbootstrap\"\nmandatory=true\nversionRange=\"[1.0.3,)\""));
        String t15 = MetadataGenerators.generate(m, target(LoaderType.FORGE, "1.15.2", "31.2.57")).files().get(0).content();
        truth("1.15.2 forge min", t15.contains("versionRange=\"[31.2.44,)\"") && !t15.contains("mixinbootstrap"));
        String t121 = MetadataGenerators.generate(m, target(LoaderType.FORGE, "1.21.1", "52.1.16")).files().get(0).content();
        truth("1.21.1 unchanged", t121.contains("versionRange=\"[52,)\"") && !t121.contains("mixinbootstrap"));
        MetadataTarget noMixins = new MetadataTarget(LoaderType.FORGE, McVersion.parse("1.14.4"), VersionRange.exactly("1.14.4"),
                "28.2.26", 8, false, Map.of(), List.of(), null, null);
        truth("no mixins -> no bootstrap", !MetadataGenerators.generate(m, noMixins).files().get(0).content().contains("mixinbootstrap"));
        MetadataTarget neo = target(LoaderType.NEOFORGE, "1.21.1", "21.1.228");
        truth("neoforge never gets bootstrap", !MetadataGenerators.generate(m, neo).files().get(0).content().contains("mixinbootstrap"));

        String src = "{\"package\":\"p\",\"compatibilityLevel\":\"JAVA_21\"}";
        eq("refmap written", "example_mod.refmap.json",
                Json.parseObject(MixinConfigMerger.merge(src, MixinConfigMerger.emptyBuckets(), 17, "example_mod.refmap.json")).get("refmap"));
        eq("no refmap by default", null, Json.parseObject(MixinConfigMerger.merge(src, MixinConfigMerger.emptyBuckets(), 17)).get("refmap"));
    }

    static String moved(List<ResourceRelocation> rules, String path) {
        return ResourceRelocation.applyAll(rules, path);
    }

    static void resourceRelocations() {
        ResourceRelocation groups = ResourceRelocation.of("assets/(*)/foo/(**)", "assets/$1/bar/$2");
        eq("groups", "assets/a/bar/x/y.png", groups.apply("assets/a/foo/x/y.png"));
        eq("groups empty tail", "assets/a/bar/", groups.apply("assets/a/foo/"));
        eq("star stays in segment", null, groups.apply("assets/a/b/foo/x.png"));
        eq("no match", null, groups.apply("data/a/foo/x.png"));
        eq("backslashes", "assets/a/bar/x.png", groups.apply("assets\\a\\foo\\x.png"));
        eq("leading slash", "assets/a/bar/x.png", groups.apply("/assets/a/foo/x.png"));

        ResourceRelocation tail = ResourceRelocation.of("data/old/", "moved/$0");
        eq("trailing slash", "moved/data/old/x/y.json", tail.apply("data/old/x/y.json"));

        ResourceRelocation anyDepth = ResourceRelocation.of("a/**/b.txt", "$0.bak");
        eq("** zero dirs", "a/b.txt.bak", anyDepth.apply("a/b.txt"));
        eq("** many dirs", "a/x/y/b.txt.bak", anyDepth.apply("a/x/y/b.txt"));
        eq("? one char", "t/ab.json", ResourceRelocation.of("t/(?)b.json", "t/$1b.json").apply("t/ab.json"));
        eq("? not two chars", null, ResourceRelocation.of("t/?b.json", "x").apply("t/aab.json"));
        eq("dot is literal", null, ResourceRelocation.of("a.json", "x").apply("aXjson"));
        eq("applyAll first match wins", "one", moved(List.of(ResourceRelocation.of("a", "one"), ResourceRelocation.of("a", "two")), "a"));
        eq("applyAll no match", "z", moved(List.of(ResourceRelocation.of("a", "one")), "z"));

        throwsIae("unbalanced (", () -> ResourceRelocation.of("a/(*", "x"));
        throwsIae("unbalanced )", () -> ResourceRelocation.of("a/*)", "x"));
        throwsIae("group out of range", () -> ResourceRelocation.of("a/(*)", "$2"));
        throwsIae("bare dollar", () -> ResourceRelocation.of("a/(*)", "$x"));
        throwsIae("blank source", () -> ResourceRelocation.of("/", "x"));
        throwsIae("renaming uncaptured wildcard", () -> ResourceRelocation.renaming("a/(*)", "b/*"));
        throwsIae("renaming group mismatch", () -> ResourceRelocation.renaming("a/(*)/(*)", "b/(*)"));

        List<ResourceRelocation> up = VanillaResourceRelocations.between("1.20.1", "1.21.1");
        eq("loot_tables", "data/ns/loot_table/blocks/a.json", moved(up, "data/ns/loot_tables/blocks/a.json"));
        eq("advancements", "data/ns/advancement/recipes/a.json", moved(up, "data/ns/advancements/recipes/a.json"));
        eq("recipes", "data/ns/recipe/a.json", moved(up, "data/ns/recipes/a.json"));
        eq("functions", "data/ns/function/a/b.mcfunction", moved(up, "data/ns/functions/a/b.mcfunction"));
        eq("structures", "data/ns/structure/a.nbt", moved(up, "data/ns/structures/a.nbt"));
        eq("predicates", "data/ns/predicate/a.json", moved(up, "data/ns/predicates/a.json"));
        eq("item_modifiers", "data/ns/item_modifier/a.json", moved(up, "data/ns/item_modifiers/a.json"));
        eq("tags/blocks", "data/ns/tags/block/mineable/a.json", moved(up, "data/ns/tags/blocks/mineable/a.json"));
        eq("tags/items", "data/ns/tags/item/a.json", moved(up, "data/ns/tags/items/a.json"));
        eq("tags/fluids", "data/ns/tags/fluid/a.json", moved(up, "data/ns/tags/fluids/a.json"));
        eq("tags/entity_types", "data/ns/tags/entity_type/a.json", moved(up, "data/ns/tags/entity_types/a.json"));
        eq("tags/game_events", "data/ns/tags/game_event/a.json", moved(up, "data/ns/tags/game_events/a.json"));
        eq("tags/functions", "data/ns/tags/function/load.json", moved(up, "data/ns/tags/functions/load.json"));
        eq("worldgen untouched", "data/ns/worldgen/biome/a.json", moved(up, "data/ns/worldgen/biome/a.json"));
        eq("assets untouched", "assets/ns/models/item/a.json", moved(up, "assets/ns/models/item/a.json"));
        eq("armor textures not before 1.21.2", "assets/ns/textures/models/armor/a_layer_1.png", moved(up, "assets/ns/textures/models/armor/a_layer_1.png"));

        List<ResourceRelocation> down = VanillaResourceRelocations.between("1.21.1", "1.20.1");
        eq("reverse loot_table", "data/ns/loot_tables/a.json", moved(down, "data/ns/loot_table/a.json"));
        eq("reverse tags/block", "data/ns/tags/blocks/a.json", moved(down, "data/ns/tags/block/a.json"));
        eq("reverse leaves old paths", "data/ns/loot_tables/a.json", moved(down, "data/ns/loot_tables/a.json"));

        eq("same side 1.21 -> 1.21.1", List.of(), VanillaResourceRelocations.between("1.21", "1.21.1"));
        eq("same side 1.20.1 -> 1.20.4", List.of(), VanillaResourceRelocations.between("1.20.1", "1.20.4"));
        eq("same version", List.of(), VanillaResourceRelocations.between("26.1", "26.1"));
        eq("1.21 is the boundary", "data/ns/recipe/a.json", moved(VanillaResourceRelocations.between("1.20.6", "1.21"), "data/ns/recipes/a.json"));

        List<ResourceRelocation> equipment = VanillaResourceRelocations.between("1.21.1", "1.21.4");
        eq("armor layer 1", "assets/ns/textures/entity/equipment/humanoid/steel.png", moved(equipment, "assets/ns/textures/models/armor/steel_layer_1.png"));
        eq("armor layer 2", "assets/ns/textures/entity/equipment/humanoid_leggings/steel.png", moved(equipment, "assets/ns/textures/models/armor/steel_layer_2.png"));
        eq("armor mcmeta", "assets/ns/textures/entity/equipment/humanoid/steel.png.mcmeta", moved(equipment, "assets/ns/textures/models/armor/steel_layer_1.png.mcmeta"));
        eq("armor underscores", "assets/ns/textures/entity/equipment/humanoid/dark_steel.png", moved(equipment, "assets/ns/textures/models/armor/dark_steel_layer_1.png"));
        eq("turtle", "assets/minecraft/textures/entity/equipment/humanoid/turtle_scute.png", moved(equipment, "assets/minecraft/textures/models/armor/turtle_layer_1.png"));
        eq("trim", "assets/ns/textures/trims/entity/humanoid/mark.png", moved(equipment, "assets/ns/textures/trims/models/armor/mark.png"));
        eq("trim leggings", "assets/ns/textures/trims/entity/humanoid_leggings/mark.png", moved(equipment, "assets/ns/textures/trims/models/armor/mark_leggings.png"));
        eq("wolf armor", "assets/minecraft/textures/entity/equipment/wolf_body/armadillo_scute.png", moved(equipment, "assets/minecraft/textures/entity/wolf/wolf_armor.png"));
        eq("wolf armor overlay", "assets/minecraft/textures/entity/equipment/wolf_body/armadillo_scute_overlay.png", moved(equipment, "assets/minecraft/textures/entity/wolf/wolf_armor_overlay.png"));
        eq("elytra", "assets/minecraft/textures/entity/equipment/wings/elytra.png", moved(equipment, "assets/minecraft/textures/entity/elytra.png"));
        eq("data untouched in 1.21.2 step", "data/ns/recipes/a.json", moved(equipment, "data/ns/recipes/a.json"));

        List<ResourceRelocation> latest = VanillaResourceRelocations.between("1.21.5", "26.3");
        eq("configured_feature", "data/ns/worldgen/feature/a.json", moved(latest, "data/ns/worldgen/configured_feature/a.json"));
        eq("configured_carver", "data/ns/worldgen/carver/a.json", moved(latest, "data/ns/worldgen/configured_carver/a.json"));
        eq("palette", "assets/ns/textures/palettes/trim/gold.png", moved(latest, "assets/ns/textures/trims/color_palettes/gold.png"));
        eq("base palette", "assets/minecraft/textures/palettes/trim_base.png", moved(latest, "assets/minecraft/textures/trims/color_palettes/trim_palette.png"));
        eq("26.1 does not rename palettes", "assets/ns/textures/trims/color_palettes/gold.png", moved(VanillaResourceRelocations.between("1.21.5", "26.1"), "assets/ns/textures/trims/color_palettes/gold.png"));
        eq("reverse base palette", "assets/minecraft/textures/trims/color_palettes/trim_palette.png", moved(VanillaResourceRelocations.between("26.3", "26.2"), "assets/minecraft/textures/palettes/trim_base.png"));
        eq("reverse palette", "assets/ns/textures/trims/color_palettes/gold.png", moved(VanillaResourceRelocations.between("26.3", "26.2"), "assets/ns/textures/palettes/trim/gold.png"));

        List<String> samples = List.of(
                "data/ns/loot_tables/blocks/a.json", "data/ns/advancements/recipes/a.json", "data/ns/recipes/a.json",
                "data/ns/functions/a.mcfunction", "data/ns/structures/a.nbt", "data/ns/predicates/a.json",
                "data/ns/item_modifiers/a.json", "data/ns/tags/blocks/a.json", "data/ns/tags/items/a.json",
                "data/ns/tags/fluids/a.json", "data/ns/tags/entity_types/a.json", "data/ns/tags/game_events/a.json",
                "data/ns/tags/functions/load.json", "data/ns/worldgen/configured_feature/a.json",
                "data/ns/worldgen/configured_carver/a.json", "assets/ns/textures/models/armor/steel_layer_1.png",
                "assets/ns/textures/models/armor/steel_layer_2.png.mcmeta",
                "assets/minecraft/textures/models/armor/turtle_layer_1.png",
                "assets/ns/textures/trims/models/armor/mark.png", "assets/ns/textures/trims/models/armor/mark_leggings.png",
                "assets/minecraft/textures/entity/wolf/wolf_armor.png", "assets/minecraft/textures/entity/elytra.png",
                "assets/ns/textures/trims/color_palettes/gold.png",
                "assets/minecraft/textures/trims/color_palettes/trim_palette.png", "assets/ns/models/item/a.json");
        List<ResourceRelocation> forth = VanillaResourceRelocations.between("1.14.4", "26.3");
        List<ResourceRelocation> back = VanillaResourceRelocations.between("26.3", "1.14.4");
        for (String sample : samples) {
            eq("round trip " + sample, sample, moved(back, moved(forth, sample)));
        }
    }

    static void classTweaker() {
        String ct = "classTweaker v1 named\n\naccessible class net/minecraft/server/MinecraftServer\n"
                + "# comment\nextendable class net/minecraft/Foo$Bar\n"
                + "accessible method net/minecraft/Foo tick (Z)V\nextendable method net/minecraft/Foo run ()V\n"
                + "accessible field net/minecraft/Foo count I\nmutable field net/minecraft/Foo flag Z\n"
                + "transitive-accessible class net/minecraft/Baz\naccessible class net/minecraft/Baz\n"
                + "mutable class net/minecraft/Nope\n";
        ClassTweakerConverter.Result r = ClassTweakerConverter.toAccessTransformer(ct);
        eq("converted AT",
                "# Generated from a class tweaker. Do not edit.\n"
                        + "public net.minecraft.server.MinecraftServer\n"
                        + "public-f net.minecraft.Foo$Bar\n"
                        + "public net.minecraft.Foo tick(Z)V\n"
                        + "protected-f net.minecraft.Foo run()V\n"
                        + "public net.minecraft.Foo count\n"
                        + "default-f net.minecraft.Foo flag\n"
                        + "public net.minecraft.Baz\n",
                r.accessTransformer());
        eq("one warning for mutable class", 1, r.warnings().size());
        throwsIae("intermediary rejected", () -> ClassTweakerConverter.toAccessTransformer("classTweaker v1 intermediary\n"));
        throwsIae("missing header", () -> ClassTweakerConverter.toAccessTransformer("accessible class a/B\n"));
        eq("accessWidener header accepted", "# Generated from a class tweaker. Do not edit.\npublic a.B\n",
                ClassTweakerConverter.toAccessTransformer("accessWidener v2 named\naccessible class a/B\n").accessTransformer());
    }

    static void mixins() {
        String src = "{\n  \"required\": true,\n  \"package\": \"de.x.mixin\",\n  \"compatibilityLevel\": \"JAVA_21\",\n  \"mixins\": [\"Existing\"]\n}";
        Map<String, List<String>> found = MixinConfigMerger.emptyBuckets();
        found.get("mixins").addAll(List.of("de.x.mixin.Existing", "de.x.mixin.MinecraftServerMixin"));
        found.get("client").add("de.x.mixin.client.MinecraftClientMixin");
        String out = MixinConfigMerger.merge(src, found, 17);
        Map<String, Object> j = Json.parseObject(out);
        eq("mixins merged without duplicates", List.of("Existing", "MinecraftServerMixin"), j.get("mixins"));
        eq("client bucket created", List.of("client.MinecraftClientMixin"), j.get("client"));
        eq("compat level follows java", "JAVA_17", j.get("compatibilityLevel"));
        eq("compat level capped", "JAVA_21", Json.parseObject(MixinConfigMerger.merge(src, found, 25)).get("compatibilityLevel"));
        eq("compat level java 8", "JAVA_8", Json.parseObject(MixinConfigMerger.merge(src, found, 8)).get("compatibilityLevel"));
        eq("untouched when absent", null, Json.parseObject(MixinConfigMerger.merge("{\"package\":\"p\"}", MixinConfigMerger.emptyBuckets(), 17)).get("compatibilityLevel"));
        Map<String, List<String>> bad = MixinConfigMerger.emptyBuckets();
        bad.get("server").add("other.pkg.Thing");
        throwsIae("class outside package", () -> MixinConfigMerger.merge(src, bad, 17));
    }
}
