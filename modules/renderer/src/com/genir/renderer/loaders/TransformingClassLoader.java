package com.genir.renderer.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * URLClassLoader that transforms org.dark.* (GraphicsLib) bytecode to route
 * GL calls through FR's bridges. Replaces the javaagent approach which broke
 * DoNotObfuscate.class.isAssignableFrom() on macOS.
 */
public class TransformingClassLoader extends URLClassLoader {

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
                        byte[] transformed = ClassTransformer.transformBytes(path, original, ClassConstantTransformer.MOD_GL_TRANSFORMS);
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
