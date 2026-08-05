package com.genir.renderer.agent;

import com.genir.renderer.hooks.HookConfig;
import com.genir.renderer.hooks.HookRegistry;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Map;

public class ClassTransformer implements ClassFileTransformer {
    private static final Map<String, String> memberTransforms = HookConfig.buildMemberTransforms();

    private final ConstantTransformer obfTransformer = new ConstantTransformer(
            ObfTransformations.transformations,
            memberTransforms
    );

    private final ConstantTransformer scriptTransformer = new ConstantTransformer(ScriptTransformations.transformations);

    private final ConstantTransformer starfarerTransformer = new ConstantTransformer(
            // Replace OpenGL calls.
            Map.of(
                    "org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/commands/GL11",
                    "org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/commands/GL14",
                    "org/lwjgl/opengl/Display", "com/genir/renderer/bridge/commands/Display",
                    "org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/commands/GLContext",
                    "org/lwjgl/util/Display", "com/genir/renderer/bridge/commands/DisplayUtil"
            ),

            // Replace class loader for loading scripts.
            Map.of(
                    "org/codehaus/janino/JavaSourceClassLoader", "java/lang/ClassLoader"
            ),

            // Allow vanilla access to OpenGL display lists.
            Map.of(
                    "glGenLists", "glGenLists_restricted",
                    "glNewList", "glNewList_restricted",
                    "glEndList", "glEndList_restricted",
                    "glCallList", "glCallList_restricted"
            ),

            // Obfuscate assembled overrides.
            ObfTransformations.transformations,

            // Resolver-derived member names.
            memberTransforms,

            IllegalTransformations.transformations
    );

    private final ConstantTransformer xstreamTransformer = new ConstantTransformer(
            // Use memory-optimized Path implementation
            Map.of(
                    "com/thoughtworks/xstream/io/path/Path", "com/genir/renderer/overrides/xstream/Path"
            )
    );

    private final ConstantTransformer lwjglTransformer = new ConstantTransformer(
            // Replace OpenGL calls.
            Map.of(
                    "org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/commands/GL11",
                    "org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/commands/GL14"
            )
    );

    private final HookRegistry hookRegistry = HookConfig.build();

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) {
        byte[] bytes = classfileBuffer;

        // Hooks first: they match vanilla obfuscated names/descriptors in the
        // game bytecode. CP transforms run after so they rewrite both vanilla
        // references and any FR references emitted by the hooks.
        if (className != null && hookRegistry.hasHook(className)) {
            bytes = hookRegistry.apply(className, bytes);
        }

        ConstantTransformer transformer = selectTransformers(loader, className);
        if (transformer != null) {
            bytes = transformer.apply(bytes);
        }

        if (Arrays.equals(bytes, classfileBuffer)) {
            return null;
        }

        return bytes;
    }

    private ConstantTransformer selectTransformers(ClassLoader loader, String binaryOrInternalName) {
        // No class to transform.
        if (binaryOrInternalName == null) {
            return null;
        }

        // Do not transform bootstrap and platform classes.
        if (loader == null || loader == ClassLoader.getPlatformClassLoader()) {
            return null;
        }

        // Transform selected core game classes.
        String name = ClassName.binary(binaryOrInternalName);
        if (name.startsWith("org.lwjgl.util.glu.")) {
            return lwjglTransformer;
        } else if (name.startsWith("com.thoughtworks.xstream.")) {
            return xstreamTransformer;
        } else if (name.startsWith("com.fs.") || name.startsWith("sound.") || name.startsWith("zzz.com.fs.")) {
            return starfarerTransformer;
        } else if (name.startsWith("com.genir.renderer.agent.")) {
            return null;
        } else if (name.startsWith("com.genir.renderer.")) {
            return obfTransformer;
        }

        // Assume classes loaded by loaders other than system loaders are scripts.
        if (loader != ClassLoader.getSystemClassLoader() && loader != this.getClass().getClassLoader()) {
            return scriptTransformer;
        }

        return null;
    }
}
