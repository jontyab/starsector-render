package com.genir.renderer.loaders;

import com.genir.renderer.hooks.HookConfig;
import com.genir.renderer.hooks.HookRegistry;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

public class AppClassLoader extends ClassLoader {
  static {
    registerAsParallelCapable();
  }

  private JavaAgentLoader javaAgentLoader = null;

  private final List<ClassConstantTransformer> obfTransformers =
      new ArrayList<>(List.of(new ClassConstantTransformer(ObfTransformations.transformations)));

  private final List<ClassConstantTransformer> lwjglTransformers =
      List.of(
          new ClassConstantTransformer(
              Arrays.asList(
                  // Replace OpenGL calls.
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/commands/GL11"),
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/commands/GL14"))));

  private final List<ClassConstantTransformer> xstreamTransformers =
      List.of(
          new ClassConstantTransformer(
              List.of(
                  // Use memory-optimized Path implementation
                  ClassConstantTransformer.newTransform(
                      "com/thoughtworks/xstream/io/path/Path",
                      "com/genir/renderer/overrides/xstream/Path"))),
          new ClassConstantTransformer(
              Arrays.asList(
                  // Fix transforms caused by a false positive match.
                  ClassConstantTransformer.newTransform(
                      "com/genir/renderer/overrides/xstream/PathTracker",
                      "com/thoughtworks/xstream/io/path/PathTracker"),
                  ClassConstantTransformer.newTransform(
                      "com/genir/renderer/overrides/xstream/PathTrackingReader",
                      "com/thoughtworks/xstream/io/path/PathTrackingReader"),
                  ClassConstantTransformer.newTransform(
                      "com/genir/renderer/overrides/xstream/PathTrackingWriter",
                      "com/thoughtworks/xstream/io/path/PathTrackingWriter"))));

  private final List<ClassConstantTransformer> starfarerTransformers =
      new ArrayList<>(
          List.of(
              new ClassConstantTransformer(
                  Arrays.asList(
                      // Replace OpenGL calls.
                      ClassConstantTransformer.newTransform(
                          "org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/commands/GL11"),
                      ClassConstantTransformer.newTransform(
                          "org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/commands/GL14"),
                      ClassConstantTransformer.newTransform(
                          "org/lwjgl/opengl/Display", "com/genir/renderer/bridge/commands/Display"),
                      ClassConstantTransformer.newTransform(
                          "org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/commands/GLContext"),
                      ClassConstantTransformer.newTransform(
                          "org/lwjgl/util/Display", "com/genir/renderer/bridge/commands/DisplayUtil"),

                      // Replace class loader for loading scripts.
                      ClassConstantTransformer.newTransform(
                          "org/codehaus/janino/JavaSourceClassLoader",
                          "java/lang/ClassLoader"))),
              new ClassConstantTransformer(
                  List.of(
                      // Fix org/lwjgl/util/Display -> com/genir/renderer/bridge/DisplayMode
                      // caused by a false positive match.
                      ClassConstantTransformer.newTransform(
                          "com/genir/renderer/bridge/commands/DisplayMode",
                          "org/lwjgl/opengl/DisplayMode"))),
              // Obfuscate assembled overrides.
              new ClassConstantTransformer(ObfTransformations.transformations)));

  private volatile HookRegistry hookRegistry;

  public AppClassLoader(ClassLoader parent) {
    super(parent);
  }

  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (hookRegistry == null) {
      hookRegistry = HookConfig.build(obfTransformers, starfarerTransformers);
    }

    synchronized (getClassLoadingLock(name)) {
      // Class does not require transformation
      // and should be loaded by the parent.
      if (selectTransformer(name) == null) {
        try {
          return getParent().loadClass(name);
        } catch (ClassNotFoundException e) {
          // Fallback to javaagent loader.
          if (javaAgentLoader != null) {
            return javaAgentLoader.loadClass(name, resolve);
          } else {
            throw e;
          }
        }
      }

      Class<?> loaded = findLoadedClass(name);
      if (loaded != null) {
        return loaded;
      }

      return findClass(name);
    }
  }

  @Override
  public InputStream getResourceAsStream(String internalName) {
    // Class does not require transformation
    // and should be loaded by the parent.
    if (selectTransformer(internalName) == null) {
      return getParent().getResourceAsStream(internalName);
    }

    // Return local transformed class.
    try {
      return new ByteArrayInputStream(findBytecode(internalName));
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  // Return local transformed class bytecode.
  public byte[] findBytecode(String internalName) throws ClassNotFoundException {
    // Requested class is non-local and should have been loaded by the parent.
    if (selectTransformer(internalName) == null) {
      throw new ClassNotFoundException(ClassName.binary(internalName));
    }

    String bare =
        internalName.endsWith(".class")
            ? internalName.substring(0, internalName.length() - 6)
            : internalName;

    try {
      byte[] originalBytes;
      if (hookRegistry != null && hookRegistry.hasHook(bare)) {
        // Load from game jar (bypass fr.jar shadow) and apply ASM hooks.
        originalBytes = loadFromGameJars(bare);
        originalBytes = hookRegistry.apply(bare, originalBytes);
      } else {
        InputStream stream = super.getResourceAsStream(internalName);
        if (stream == null) {
          throw new ClassNotFoundException(ClassName.binary(internalName));
        }
        originalBytes = stream.readAllBytes();
      }
      return ClassTransformer.transformBytes(
          internalName, originalBytes, selectTransformer(internalName));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] loadFromGameJars(String bare) throws IOException, ClassNotFoundException {
    String entry = bare + ".class";
    String classpath = System.getProperty("java.class.path", "");
    for (String jar : classpath.split(File.pathSeparator)) {
      if (!jar.endsWith(".jar") || jar.endsWith("fr.jar")) continue;
      try (JarFile jf = new JarFile(jar)) {
        var je = jf.getJarEntry(entry);
        if (je != null) return jf.getInputStream(je).readAllBytes();
      } catch (IOException ignored) {
      }
    }
    throw new ClassNotFoundException("Not found in game jars: " + bare);
  }

  @Override
  public Class<?> findClass(String name) throws ClassNotFoundException {
    String internalName = ClassName.internal(name);
    byte[] bytecode = findBytecode(internalName);
    ProtectionDomain pd =
        ClassTransformer.getResourceProtectionDomain(
            internalName, super.findResource(internalName), this);
    return super.defineClass(name, bytecode, 0, bytecode.length, pd);
  }

  private List<ClassConstantTransformer> selectTransformer(String binaryOrInternalName) {
    String name = ClassName.binary(binaryOrInternalName);
    if (name.startsWith("org.lwjgl.util.glu.")) {
      return lwjglTransformers;
    } else if (name.startsWith("com.thoughtworks.xstream.")) {
      return xstreamTransformers;
    } else if (name.startsWith("com.fs.") || name.startsWith("zzz.com.fs.")) {
      return starfarerTransformers;
    } else if (name.startsWith("com.genir.renderer.")) {
      return obfTransformers;
    }

    // Do not intercept this class.
    return null;
  }

  // Signature expected by the JVM instrumentation mechanism.
  @SuppressWarnings("unused")
  void appendToClassPathForInstrumentation(String jar) {
    if (javaAgentLoader == null) {
      javaAgentLoader = new JavaAgentLoader(new URL[] {}, getParent());
    }

    try {
      File file = new File(jar);
      URL url = file.toURI().toURL();
      javaAgentLoader.addURL(url);
    } catch (Exception e) {
      throw new RuntimeException("Failed to append agent jar to class path: " + jar, e);
    }
  }

  private static class JavaAgentLoader extends URLClassLoader {
    public JavaAgentLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      return super.loadClass(name, resolve);
    }

    public void addURL(URL url) {
      super.addURL(url);
    }
  }
}
