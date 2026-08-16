package com.genir.renderer.agent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ObfTransformations {
    public static Map<String, String> transformations = load();
    private static final Map<String, String> classMap = buildClassMap();

    /** Look up the obfuscated internal name for a logical proxy class name. */
    public static String classOf(String logical) {
        String result = classMap.get(logical);
        if (result == null) throw new RuntimeException("Unknown class mapping: " + logical);
        return result;
    }

    private static Map<String, String> buildClassMap() {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, String> t : transformations.entrySet()) {
            // Transform key is e.g. "proxy/com/fs/graphics/TextureHandler"
            // Extract simple name: last segment, or after '$' for inner classes.
            String from = t.getKey();
            int dollar = from.lastIndexOf('$');
            int slash = from.lastIndexOf('/');
            String key = from.substring(Math.max(dollar, slash) + 1);
            map.put(key, t.getValue());
        }
        return map;
    }

    private static Map<String, String> load() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String name = "obf_windows.tsv"; // Windows-only
        if (false) name = os.contains("mac") ? "obf_macos.tsv"
                    : os.contains("linux") ? "obf_linux.tsv"
                    : "obf_windows.tsv";
        try (InputStream is = ObfTransformations.class.getResourceAsStream("/mappings/" + name)) {
            if (is == null) throw new RuntimeException("Missing mapping: mappings/" + name);
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .map(l -> l.split("\t", 2))
                    .collect(Collectors.toMap(p -> p[0], p -> p[1]));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mappings/" + name, e);
        }
    }
}
