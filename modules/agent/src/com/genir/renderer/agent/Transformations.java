package com.genir.renderer.agent;

import java.util.Map;

import static java.util.Map.entry;

public class Transformations {
    public static Map<String, String> scriptLoader = Map.<String, String>ofEntries(
            // Use memory-optimized Path implementation.
            entry("org/codehaus/janino/JavaSourceClassLoader", "java/lang/ClassLoader")
    );

    public static Map<String, String> xstream = Map.<String, String>ofEntries(
            // Use memory-optimized Path implementation.
            entry("com/thoughtworks/xstream/io/path/Path", "com/genir/renderer/overrides/xstream/Path")
    );

    public static Map<String, String> lwjgl = Map.<String, String>ofEntries(
            // Replace selected OpenGL calls. Replacing all calls breaks LWJGL functionality.
            entry("org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/opengl/GL11"),
            entry("org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/opengl/GL14")
    );

    public static Map<String, String> glDrainErrors = Map.<String, String>ofEntries(
            // Use non-blocking glGetError replacement.
            entry("glGetError", "glDrainErrors")
    );

    public static Map<String, String> opengl = Map.<String, String>ofEntries(
            // Replace all OpenGL calls.
            entry("org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/opengl/GL11"),
            entry("org/lwjgl/opengl/GL13", "com/genir/renderer/bridge/opengl/GL13"),
            entry("org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/opengl/GL14"),
            entry("org/lwjgl/opengl/GL15", "com/genir/renderer/bridge/opengl/GL15"),
            entry("org/lwjgl/opengl/GL20", "com/genir/renderer/bridge/opengl/GL20"),
            entry("org/lwjgl/opengl/GL30", "com/genir/renderer/bridge/opengl/GL30"),
            entry("org/lwjgl/opengl/GL31", "com/genir/renderer/bridge/opengl/GL31"),
            entry("org/lwjgl/opengl/GL32", "com/genir/renderer/bridge/opengl/GL32"),
            entry("org/lwjgl/opengl/GL33", "com/genir/renderer/bridge/opengl/GL33"),
            entry("org/lwjgl/opengl/GL40", "com/genir/renderer/bridge/opengl/GL40"),
            entry("org/lwjgl/opengl/GL41", "com/genir/renderer/bridge/opengl/GL41"),
            entry("org/lwjgl/opengl/GL42", "com/genir/renderer/bridge/opengl/GL42"),
            entry("org/lwjgl/opengl/GL43", "com/genir/renderer/bridge/opengl/GL43"),
            entry("org/lwjgl/opengl/GL44", "com/genir/renderer/bridge/opengl/GL44"),

            entry("org/lwjgl/opengl/Display", "com/genir/renderer/bridge/commands/Display"),
            entry("org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/commands/GLContext"),
            entry("org/lwjgl/opengl/GLSync", "com/genir/renderer/bridge/commands/GLSync"),
            entry("org/lwjgl/opengl/SharedDrawable", "com/genir/renderer/bridge/commands/SharedDrawable")
    );

    public static Map<String, String> obfuscation = loadObfuscation();

    private static Map<String, String> loadObfuscation() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String name = os.contains("mac") ? "obf_macos.tsv"
                    : os.contains("linux") ? "obf_linux.tsv"
                    : "obf_windows.tsv";
        try (java.io.InputStream is = Transformations.class.getResourceAsStream("/mappings/" + name)) {
            if (is == null) throw new RuntimeException("Missing mapping: mappings/" + name);
            return new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))
                    .lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .map(l -> l.split("\t", 2))
                    .collect(java.util.stream.Collectors.toMap(p -> p[0], p -> p[1]));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mappings/" + name, e);
        }
    }
}
