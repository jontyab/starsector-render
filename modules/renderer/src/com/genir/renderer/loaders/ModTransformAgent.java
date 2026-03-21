package com.genir.renderer.loaders;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Java agent that intercepts mod class loading to route GL calls through FR bridges. */
public class ModTransformAgent {

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className != null && className.startsWith("org/dark/")) {
                    return ClassTransformer.transformBytes(className + ".class", classfileBuffer, ClassConstantTransformer.MOD_GL_TRANSFORMS);
                }
                return null;
            }
        });
    }
}
