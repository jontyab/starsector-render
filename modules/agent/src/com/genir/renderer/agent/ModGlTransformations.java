package com.genir.renderer.agent;

import java.util.Map;

import static java.util.Map.entry;

/** GL rewrites applied to GraphicsLib (org.dark.*) mod classes. */
public class ModGlTransformations {
    public static Map<String, String> transformations = Map.<String, String>ofEntries(
            // Replace OpenGL calls.
            entry("org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/commands/GL11"),
            entry("org/lwjgl/opengl/GL13", "com/genir/renderer/bridge/commands/GL13"),
            entry("org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/commands/GL14"),
            entry("org/lwjgl/opengl/GL20", "com/genir/renderer/bridge/commands/GL20"),
            entry("org/lwjgl/opengl/GL30", "com/genir/renderer/bridge/commands/GL30"),
            entry("org/lwjgl/opengl/GL43", "com/genir/renderer/bridge/commands/GL43"),
            entry("org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/commands/GLContext"),
            // ARB framebuffer methods have identical names/signatures to GL30.
            entry("org/lwjgl/opengl/ARBFramebufferObject", "com/genir/renderer/bridge/commands/GL30")
    );
}
