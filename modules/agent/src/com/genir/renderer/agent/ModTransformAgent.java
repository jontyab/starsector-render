package com.genir.renderer.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;

/** Java agent that intercepts mod class loading to route GL calls through FR bridges. */
public class ModTransformAgent {

    private static final ConstantTransformer MOD_GL_TRANSFORM = new ConstantTransformer(
            Map.of(
                    "org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/commands/GL11",
                    "org/lwjgl/opengl/GL13", "com/genir/renderer/bridge/commands/GL13",
                    "org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/commands/GL14",
                    "org/lwjgl/opengl/GL20", "com/genir/renderer/bridge/commands/GL20",
                    "org/lwjgl/opengl/GL30", "com/genir/renderer/bridge/commands/GL30",
                    "org/lwjgl/opengl/GL43", "com/genir/renderer/bridge/commands/GL43",
                    "org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/commands/GLContext",
                    // ARB framebuffer methods have identical names/signatures to GL30.
                    "org/lwjgl/opengl/ARBFramebufferObject", "com/genir/renderer/bridge/commands/GL30"
            )
    );

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className != null && className.startsWith("org/dark/")) {
                    return ClassTransformer.transformBytes(className + ".class", classfileBuffer, MOD_GL_TRANSFORM);
                }
                return null;
            }
        });
    }
}
