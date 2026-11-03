package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MixinConfigMerger {
    private MixinConfigMerger() {}

    static String compatibilityLevel(int javaVersion) {
        return "JAVA_" + Math.min(javaVersion, 21);
    }

    public static String merge(String sourceJson, Map<String, List<String>> discovered, int javaVersion) {
        return merge(sourceJson, discovered, javaVersion, null);
    }

    @SuppressWarnings("unchecked")
    public static String merge(
            String sourceJson, Map<String, List<String>> discovered, int javaVersion, String refmap) {
        String plain = Json5.toJson(sourceJson, "");
        if (plain == null) throw new IllegalArgumentException("The mixin config is empty");
        Map<String, Object> root = Json.parseObject(plain);
        Object pkgValue = root.get("package");
        String pkg = pkgValue instanceof String s ? stripTrailingDots(s) : null;

        for (Map.Entry<String, List<String>> bucket : discovered.entrySet()) {
            if (bucket.getValue().isEmpty()) continue;
            Object existing = root.get(bucket.getKey());
            if (existing != null && !(existing instanceof List)) {
                throw new IllegalArgumentException("'" + bucket.getKey() + "' must be a JSON array");
            }
            List<Object> array = existing == null ? new ArrayList<>() : (List<Object>) existing;
            Set<String> seen = new LinkedHashSet<>();
            for (Object o : array) if (o instanceof String s) seen.add(s);
            for (String className : bucket.getValue()) {
                String entry = className;
                if (pkg != null && !pkg.isEmpty()) {
                    String prefix = pkg + ".";
                    if (!className.startsWith(prefix)) {
                        throw new IllegalArgumentException("Mixin class '" + className
                                + "' does not belong to package '" + pkg + "' declared by the mixin config");
                    }
                    entry = className.substring(prefix.length());
                }
                if (seen.add(entry)) array.add(entry);
            }
            root.put(bucket.getKey(), array);
        }
        if (refmap != null) root.put("refmap", refmap);
        if (root.containsKey("compatibilityLevel")) {
            root.put("compatibilityLevel", compatibilityLevel(javaVersion));
        }
        return Json.write(root);
    }

    private static String stripTrailingDots(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    public static Map<String, List<String>> emptyBuckets() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("mixins", new ArrayList<>());
        m.put("client", new ArrayList<>());
        m.put("server", new ArrayList<>());
        return m;
    }
}
