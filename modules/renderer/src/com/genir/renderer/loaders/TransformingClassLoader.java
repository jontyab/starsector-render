package com.genir.renderer.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

/**
 * URLClassLoader that transforms org.dark.* (GraphicsLib) bytecode to route
 * GL calls through FR's bridges. Replaces the javaagent approach which broke
 * DoNotObfuscate.class.isAssignableFrom() on macOS.
 */
public class TransformingClassLoader extends URLClassLoader {
    private static final List<ClassConstantTransformer> modGLTransformers = List.of(
            new ClassConstantTransformer(Arrays.asList(
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/GL11"),
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/GL13", "com/genir/renderer/bridge/GL13"),
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/GL14"),
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/GL20", "com/genir/renderer/bridge/GL20"),
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/GL30", "com/genir/renderer/bridge/GL30"),
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/GL43", "com/genir/renderer/bridge/GL43"),
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/GLContext"),
                    ClassConstantTransformer.newTransform("org/lwjgl/opengl/ARBFramebufferObject", "com/genir/renderer/bridge/GL30")
            ))
    );

    public TransformingClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            if (name.startsWith("org.dark.")) {
                String path = name.replace('.', '/') + ".class";
                try (InputStream stream = getResourceAsStream(path)) {
                    if (stream != null) {
                        byte[] original = stream.readAllBytes();
                        byte[] transformed = ClassTransformer.transformBytes(path, original, modGLTransformers);
                        c = defineClass(name, transformed, 0, transformed.length);
                        if (resolve) resolveClass(c);
                        return c;
                    }
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }

            return super.loadClass(name, resolve);
        }
    }
}
