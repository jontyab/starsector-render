package com.genir.renderer.loaders;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;

import static com.genir.renderer.loaders.ClassConstantTransformer.newTransform;

/** Java agent that intercepts mod class loading to route GL calls through FR bridges. */
public class ModTransformAgent {

    private static final List<ClassConstantTransformer> MOD_GL_TRANSFORMS = List.of(
            new ClassConstantTransformer(Arrays.asList(
                    newTransform("org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/GL11"),
                    newTransform("org/lwjgl/opengl/GL13", "com/genir/renderer/bridge/GL13"),
                    newTransform("org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/GL14"),
                    newTransform("org/lwjgl/opengl/GL20", "com/genir/renderer/bridge/GL20"),
                    newTransform("org/lwjgl/opengl/GL30", "com/genir/renderer/bridge/GL30"),
                    newTransform("org/lwjgl/opengl/GL43", "com/genir/renderer/bridge/GL43"),
                    newTransform("org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/GLContext"),
                    // ARB framebuffer methods have identical names/signatures to GL30.
                    newTransform("org/lwjgl/opengl/ARBFramebufferObject", "com/genir/renderer/bridge/GL30")
            ))
    );

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className != null && className.startsWith("org/dark/")) {
                    return ClassTransformer.transformBytes(className + ".class", classfileBuffer, MOD_GL_TRANSFORMS);
                }
                return null;
            }
        });
    }
}
