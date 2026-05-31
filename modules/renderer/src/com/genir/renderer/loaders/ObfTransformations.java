package com.genir.renderer.loaders;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class ObfTransformations {
    public static Map<String, String> transformations = load();

    private static Map<String, String> load() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String name = os.contains("mac") ? "obf_macos.tsv"
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
