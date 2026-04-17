package com.genir.renderer.loaders;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import static com.genir.renderer.loaders.ClassConstantTransformer.newTransform;

public class ObfTransformations {
    public static List<ClassConstantTransformer.Transform> transformations = load();

    private static List<ClassConstantTransformer.Transform> load() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String name = os.contains("mac") ? "obf_macos.tsv"
                    : os.contains("linux") ? "obf_linux.tsv"
                    : "obf_windows.tsv";
        try (InputStream is = ObfTransformations.class.getResourceAsStream("/mappings/" + name)) {
            if (is == null) throw new RuntimeException("Missing mapping: mappings/" + name);
            return new BufferedReader(new InputStreamReader(is))
                    .lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .map(l -> l.split("\t", 2))
                    .map(p -> newTransform(p[0], p[1]))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mappings/" + name, e);
        }
    }
}
