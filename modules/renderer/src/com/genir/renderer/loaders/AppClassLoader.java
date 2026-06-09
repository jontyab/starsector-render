package com.genir.renderer.loaders;

import com.genir.renderer.hooks.HookRegistry;
import com.genir.renderer.hooks.Hooks;
import com.genir.renderer.hooks.Resolver;
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
import org.objectweb.asm.Opcodes;

public class AppClassLoader extends ClassLoader {
  static {
    registerAsParallelCapable();
  }

  private JavaAgentLoader javaAgentLoader = null;

  private final List<ClassConstantTransformer> obfTransformers =
      List.of(new ClassConstantTransformer(ObfTransformations.transformations));

  private final List<ClassConstantTransformer> lwjglTransformers =
      List.of(
          new ClassConstantTransformer(
              Arrays.asList(
                  // Replace OpenGL calls.
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/GL11"),
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/GL14"))));

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
      List.of(
          new ClassConstantTransformer(
              Arrays.asList(
                  // Replace OpenGL calls.
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/GL11", "com/genir/renderer/bridge/GL11"),
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/GL14", "com/genir/renderer/bridge/GL14"),
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/Display", "com/genir/renderer/bridge/Display"),
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/opengl/GLContext", "com/genir/renderer/bridge/GLContext"),
                  ClassConstantTransformer.newTransform(
                      "org/lwjgl/util/Display", "com/genir/renderer/bridge/DisplayUtil"),

                  // Replace class loader for loading scripts.
                  ClassConstantTransformer.newTransform(
                      "org/codehaus/janino/JavaSourceClassLoader", "java/lang/ClassLoader"))),
          new ClassConstantTransformer(
              List.of(
                  // Fix org/lwjgl/util/Display -> com/genir/renderer/bridge/DisplayMode transform
                  // caused by a false positive match.
                  ClassConstantTransformer.newTransform(
                      "com/genir/renderer/bridge/DisplayMode", "org/lwjgl/opengl/DisplayMode"))),
          // Obfuscate assembled overrides.
          new ClassConstantTransformer(ObfTransformations.transformations));

  private volatile boolean benchStarted;
  private volatile HookRegistry hookRegistry;

  public AppClassLoader(ClassLoader parent) {
    super(parent);
  }

  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (!benchStarted) {
      benchStarted = true;
      com.genir.renderer.benchmark.BenchmarkLauncher.start();
    }
    if (hookRegistry == null) {
      hookRegistry = initHooks();
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

  private HookRegistry initHooks() {
    HookRegistry reg = new HookRegistry();
    Resolver r = buildResolver();

    // Version: append FR version to title screen string
    reg.register(
        "com/fs/starfarer/Version",
        Hooks.rewriteConstant("Starsector 0.98a-RC8", "Starsector 0.98a-RC8 FR7.2"));

    // FileRepository: make private methods public
    reg.register("com/fs/graphics/L", Hooks.widenAccess());

    // LayeredRenderer: replace renderOnly body
    reg.register(
        "com/fs/graphics/LayeredRenderer",
        Hooks.replaceBody(
            "renderOnly",
            "(Ljava/lang/Object;Ljava/lang/Enum;)V",
            mv -> {
              mv.visitVarInsn(Opcodes.ALOAD, 0);
              mv.visitVarInsn(Opcodes.ALOAD, 2);
              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  "com/fs/graphics/LayeredRenderer",
                  "getList",
                  "(Ljava/lang/Enum;)Ljava/util/List;",
                  false);
              mv.visitVarInsn(Opcodes.ASTORE, 3);
              mv.visitVarInsn(Opcodes.ALOAD, 1);
              mv.visitTypeInsn(Opcodes.CHECKCAST, "com/fs/starfarer/combat/CombatViewport");
              mv.visitVarInsn(Opcodes.ALOAD, 2);
              mv.visitTypeInsn(Opcodes.CHECKCAST, "com/fs/starfarer/api/combat/CombatEngineLayers");
              mv.visitVarInsn(Opcodes.ALOAD, 3);
              mv.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  "com/genir/renderer/overrides/LayeredRenderer",
                  "renderOnly",
                  "(Lcom/fs/starfarer/combat/CombatViewport;Lcom/fs/starfarer/api/combat/CombatEngineLayers;Ljava/util/List;)V",
                  false);
              mv.visitInsn(Opcodes.RETURN);
            }));

    // ScriptStore: replace addScript body
    String addScript = r.method("ScriptStore", "addScript");
    reg.register(
        "com/fs/starfarer/loading/scripts/ScriptStore",
        Hooks.compose(
            Hooks.widenAccess(),
            Hooks.replaceBody(
                addScript,
                "(Ljava/lang/String;)V",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "com/genir/renderer/overrides/loading/ScriptLoader",
                      "addScript",
                      "(Ljava/lang/String;)V",
                      false);
                  mv.visitInsn(Opcodes.RETURN);
                })));

    // DeploymentManager: wrap pickReinforcement with FR logic
    String dmClass = r.className("DeploymentManager");
    String dmMethod = r.method("DeploymentManager", "pickReinforcement");
    String dmDesc = r.methodDesc("DeploymentManager", "pickReinforcement");
    String dmStaticDesc = "(L" + dmClass + ";" + dmDesc.substring(1);
    reg.register(
        dmClass,
        Hooks.compose(
            Hooks.widenAccess(),
            Hooks.wrapMethod(
                dmMethod,
                dmDesc,
                "DeploymentManager_pickReinforcement_vanilla",
                "com/genir/renderer/overrides/DeploymentManager",
                "pickReinforcement",
                dmStaticDesc)));

    // ShipSpecStore: prepend queueShipSprite
    String shipClass = r.className("ShipSpecStore");
    String shipMethod = r.method("ShipSpecStore", "addSpec");
    reg.register(
        shipClass,
        Hooks.prepend(
            shipMethod,
            "(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/g;)V",
            mv -> {
              mv.visitVarInsn(Opcodes.ALOAD, 1);
              mv.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  "com/genir/renderer/overrides/loading/ResourceLoader",
                  "queueShipSprite",
                  "(Lcom/fs/starfarer/loading/specs/g;)V",
                  false);
            }));

    // WeaponSpecStore: prepend queueWeaponSprite + queueProjectileSprite
    String weapClass = r.className("WeaponSpecStore");
    String weapMethod = r.method("WeaponSpecStore", "addSpec");
    reg.register(
        weapClass,
        Hooks.compose(
            Hooks.prepend(
                weapMethod,
                "(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/BaseWeaponSpec;)V",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 1);
                  mv.visitTypeInsn(Opcodes.CHECKCAST, "com/fs/starfarer/api/loading/WeaponSpecAPI");
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "com/genir/renderer/overrides/loading/ResourceLoader",
                      "queueWeaponSprite",
                      "(Lcom/fs/starfarer/api/loading/WeaponSpecAPI;)V",
                      false);
                }),
            Hooks.prepend(
                weapMethod,
                "(Ljava/lang/String;Ljava/lang/Object;)V",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 1);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "com/genir/renderer/overrides/loading/ResourceLoader",
                      "queueProjectileSprite",
                      "(Ljava/lang/Object;)V",
                      false);
                })));

    // ProgressBar: prepend renderBackground
    String barClass = r.className("ProgressBar");
    String barMethod = r.method("ProgressBar", "render");
    reg.register(
        barClass,
        Hooks.prepend(
            barMethod,
            "(Ljava/lang/String;F)V",
            mv -> {
              mv.visitVarInsn(Opcodes.ALOAD, 0);
              mv.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  "com/genir/renderer/overrides/ProgressBar",
                  "renderBackground",
                  "(Ljava/lang/Object;)V",
                  false);
            }));

    // Expression: intercept Class.newInstance → no-op (race condition fix)
    String exprClass = r.className("Expression");
    reg.register(
        exprClass,
        Hooks.intercept(
            "getCommandClass",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "java/lang/Class",
            "newInstance",
            "()Ljava/lang/Object;",
            "com/genir/renderer/overrides/GameState",
            "skipNewInstance",
            "(Ljava/lang/Class;)Ljava/lang/Object;"));

    // RoilingSwarmEffect: replace getNumActiveMembers with members.size() (perf optimization)
    reg.register(
        "com/fs/starfarer/api/impl/combat/threat/RoilingSwarmEffect",
        Hooks.replaceBody(
            "getNumActiveMembers",
            "()I",
            mv -> {
              mv.visitVarInsn(Opcodes.ALOAD, 0);
              mv.visitFieldInsn(
                  Opcodes.GETFIELD,
                  "com/fs/starfarer/api/impl/combat/threat/RoilingSwarmEffect",
                  "members",
                  "Ljava/util/List;");
              mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
              mv.visitInsn(Opcodes.IRETURN);
            }));

    // CombatEngine: replace render body + add accessor methods
    String ftmClass = r.className("FloatingTextManager");
    String ftmRender = r.method("FloatingTextManager", "render");
    reg.register(
        "com/fs/starfarer/combat/CombatEngine",
        Hooks.compose(
            Hooks.widenAccess(),
            Hooks.replaceBody(
                "render",
                "(Z)V",
                mv -> {
                  mv.visitVarInsn(Opcodes.ILOAD, 1);
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "com/genir/renderer/overrides/CombatEngine",
                      "render",
                      "(ZLcom/fs/starfarer/combat/CombatEngine;)V",
                      false);
                  mv.visitInsn(Opcodes.RETURN);
                }),
            Hooks.addMethod(
                Opcodes.ACC_PUBLIC,
                "getHitParticlesGroup",
                "()Lcom/fs/graphics/particle/DynamicParticleGroup;",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  mv.visitFieldInsn(
                      Opcodes.GETFIELD,
                      "com/fs/starfarer/combat/CombatEngine",
                      r.field("CombatEngine", "hitParticles"),
                      "Lcom/fs/graphics/particle/DynamicParticleGroup;");
                  mv.visitInsn(Opcodes.ARETURN);
                }),
            Hooks.addMethod(
                Opcodes.ACC_PUBLIC,
                "renderFloatingTextManager",
                "()V",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  mv.visitMethodInsn(
                      Opcodes.INVOKEVIRTUAL,
                      "com/fs/starfarer/combat/CombatEngine",
                      "getFloatingTextManager",
                      "()L" + ftmClass + ";",
                      false);
                  mv.visitInsn(Opcodes.FCONST_1);
                  mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ftmClass, ftmRender, "(F)V", false);
                  mv.visitInsn(Opcodes.RETURN);
                })));

    // TextureLoader: wrap loadTexture with FR dispatch
    String loadTexture = r.method("TextureLoader", "loadTexture");
    reg.register(
        "com/fs/graphics/TextureLoader",
        Hooks.wrapMethod(
            loadTexture,
            "(Lcom/fs/graphics/Object;Ljava/lang/String;IIIIZ)Lcom/fs/graphics/Object;",
            "loadTexture_vanilla",
            "com/genir/renderer/overrides/loading/TextureLoader",
            "loadTexture",
            "(Ljava/lang/Object;Lcom/fs/graphics/Object;Ljava/lang/String;IIIIZ)Lcom/fs/graphics/Object;"));

    // C (FileUtils): widen access + add loadInputStream/loadInputStreams delegation methods
    reg.register(
        "com/fs/util/C",
        Hooks.compose(
            Hooks.widenAccess(),
            Hooks.addMethod(
                Opcodes.ACC_PUBLIC,
                "FileUtils_loadInputStream",
                "(Ljava/lang/String;Z)Ljava/io/InputStream;",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 1);
                  mv.visitVarInsn(Opcodes.ILOAD, 2);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "com/genir/renderer/overrides/FileUtils",
                      "loadInputStream",
                      "(Ljava/lang/String;Z)Ljava/io/InputStream;",
                      false);
                  mv.visitInsn(Opcodes.ARETURN);
                }),
            Hooks.addMethod(
                Opcodes.ACC_PUBLIC,
                "FileUtils_loadInputStreams",
                "(Ljava/lang/String;)Ljava/util/List;",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 1);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "com/genir/renderer/overrides/FileUtils",
                      "loadInputStreams",
                      "(Ljava/lang/String;)Ljava/util/List;",
                      false);
                  mv.visitInsn(Opcodes.ARETURN);
                })));

    // BaseGameState: intercept Display.update → Sync.syncAndUpdate, Thread.sleep → Sync.sleep
    reg.register(
        "com/fs/starfarer/BaseGameState",
        Hooks.compose(
            Hooks.intercept(
                "traverse",
                "()Ljava/lang/String;",
                "org/lwjgl/opengl/Display",
                "update",
                "(Z)V",
                "com/genir/renderer/overrides/Sync",
                "syncAndUpdate",
                "(Z)V"),
            Hooks.intercept(
                "traverse",
                "()Ljava/lang/String;",
                "java/lang/Thread",
                "sleep",
                "(J)V",
                "com/genir/renderer/overrides/Sync",
                "sleep",
                "(J)V")));

    // CombatState: same intercepts + deployment dialog skip + maybeReloadAssets guard
    reg.register(
        "com/fs/starfarer/combat/CombatState",
        Hooks.compose(
            Hooks.widenAccess(),
            Hooks.intercept(
                "traverse",
                "()Ljava/lang/String;",
                "org/lwjgl/opengl/Display",
                "update",
                "(Z)V",
                "com/genir/renderer/overrides/Sync",
                "syncAndUpdate",
                "(Z)V"),
            Hooks.intercept(
                "traverse",
                "()Ljava/lang/String;",
                "java/lang/Thread",
                "sleep",
                "(J)V",
                "com/genir/renderer/overrides/Sync",
                "sleep",
                "(J)V"),
            Hooks.intercept(
                "traverse",
                "()Ljava/lang/String;",
                "com/fs/starfarer/combat/CombatEngine",
                "isShowDeploymentDialog",
                "()Z",
                "com/genir/renderer/overrides/GameState",
                "isShowDeploymentDialog",
                "(Lcom/fs/starfarer/combat/CombatEngine;)Z"),
            Hooks.intercept(
                "traverse",
                "()Ljava/lang/String;",
                "com/fs/starfarer/combat/CombatState",
                "reloadAssets",
                "()V",
                "com/genir/renderer/overrides/GameState",
                "maybeReloadAssets",
                "(Lcom/fs/starfarer/combat/CombatState;)V")));

    // ResourceLoaderState: intercepts for parallel spec/script loading.
    String specStoreInit = r.method("SpecStore", "init");
    String runScripts = r.method("ScriptStore", "runScripts");
    String joinScripts = r.method("ScriptStore", "joinScripts");
    reg.register(
        "com/fs/starfarer/loading/ResourceLoaderState",
        Hooks.compose(
            Hooks.widenAccess(),
            Hooks.intercept(
                "init",
                "(Ljava/util/Map;)V",
                "com/fs/starfarer/loading/scripts/ScriptStore",
                runScripts,
                "()V",
                "com/genir/renderer/overrides/loading/ScriptLoader",
                "runScriptLoadingThread",
                "()V"),
            Hooks.interceptAndSkip(
                "init",
                "(Ljava/util/Map;)V",
                "com/fs/starfarer/loading/SpecStore",
                specStoreInit,
                "(Lcom/fs/starfarer/loading/ResourceLoaderState;)V",
                "com/genir/renderer/overrides/loading/ResourceLoader",
                "initSpecStore",
                "(Lcom/fs/starfarer/loading/ResourceLoaderState;)V",
                "com/fs/starfarer/loading/scripts/ScriptStore",
                joinScripts,
                "()V"),
            Hooks.intercept(
                "init",
                "(Ljava/util/Map;)V",
                "com/fs/starfarer/loading/scripts/ScriptStore",
                joinScripts,
                "()V",
                "com/genir/renderer/overrides/loading/ScriptLoader",
                "joinScriptLoadingThread",
                "()V"),
            Hooks.intercept(
                "renderProgress",
                "(F)V",
                "com/fs/graphics/Sprite",
                "renderRegionAtCenter",
                "(FFFFFF)V",
                "com/genir/renderer/overrides/loading/ResourceLoader",
                "renderBarAndAnimate",
                "(Lcom/fs/graphics/Sprite;FFFFFF)V"),
            Hooks.intercept(
                "renderProgress",
                "(F)V",
                "org/lwjgl/opengl/Display",
                "update",
                "()V",
                "com/genir/renderer/Noop",
                "noop",
                "()V"),
            Hooks.prepend(
                "queueResource",
                "(Lcom/fs/starfarer/loading/ResourceLoaderState$o;Ljava/lang/String;I)V",
                mv -> {
                  mv.visitVarInsn(Opcodes.ALOAD, 1);
                  mv.visitMethodInsn(
                      Opcodes.INVOKEVIRTUAL,
                      "com/fs/starfarer/loading/ResourceLoaderState$o",
                      "name",
                      "()Ljava/lang/String;",
                      false);
                  mv.visitVarInsn(Opcodes.ALOAD, 2);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "com/genir/renderer/overrides/loading/ResourceLoader",
                      "loadResource",
                      "(Ljava/lang/String;Ljava/lang/String;)V",
                      false);
                })));

    return reg;
  }

  private Resolver buildResolver() {
    // Collect game jar paths from classpath.
    String classpath = System.getProperty("java.class.path", "");
    List<String> jarPaths = new ArrayList<>();
    for (String jar : classpath.split(File.pathSeparator)) {
      if (jar.endsWith(".jar") && !jar.endsWith("fr.jar")) jarPaths.add(jar);
    }
    Resolver r = new Resolver(jarPaths.toArray(new String[0]));

    // Classes with stable (unobfuscated) names — register directly.
    r.registerClass("CombatEngine", "com/fs/starfarer/combat/CombatEngine");
    r.registerClass("TextureLoader", "com/fs/graphics/TextureLoader");
    r.registerClass("SpecStore", "com/fs/starfarer/loading/SpecStore");
    r.registerClass("ScriptStore", "com/fs/starfarer/loading/scripts/ScriptStore");

    // Structural fingerprints for obfuscated classes.
    r.resolveClass(
        "DeploymentManager",
        "com/fs/starfarer/combat/ai/admiral/",
        cls -> cls.methods.size() > 20 && cls.fields.size() > 10);

    r.resolveClass(
        "ShipSpecStore",
        "com/fs/starfarer/loading/",
        cls ->
            cls.methods.stream()
                .anyMatch(
                    m -> m.desc.equals("(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/g;)V")));

    r.resolveClass(
        "WeaponSpecStore",
        "com/fs/starfarer/loading/",
        cls ->
            cls.methods.stream()
                .anyMatch(
                    m ->
                        m.desc.equals(
                            "(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/BaseWeaponSpec;)V")));

    r.resolveClass(
        "ProgressBar",
        "com/fs/starfarer/campaign/save/",
        cls -> cls.methods.stream().anyMatch(m -> m.desc.equals("(Ljava/lang/String;F)V")));

    r.resolveClass(
        "Expression",
        "com/fs/starfarer/campaign/rules/",
        cls -> cls.methods.stream().anyMatch(m -> m.name.equals("getCommandClass")));

    // FloatingTextManager: chained from CombatEngine.getFloatingTextManager() return type.
    Resolver.ClassInfo ceInfo = r.info("CombatEngine");
    String ftmInternal =
        ceInfo.methods.stream()
            .filter(m -> m.name.equals("getFloatingTextManager"))
            .map(m -> m.desc.substring(m.desc.indexOf('L') + 1, m.desc.indexOf(';')))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Cannot resolve FloatingTextManager"));
    r.registerClass("FloatingTextManager", ftmInternal);

    // Method resolution.
    r.resolveMethod(
        "DeploymentManager",
        "pickReinforcement",
        m ->
            m.desc.contains("Lcom/fs/starfarer/campaign/fleet/FleetMember;")
                && (m.access & Opcodes.ACC_PRIVATE) != 0);

    r.resolveMethod(
        "ShipSpecStore", "addSpec", "(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/g;)V");

    r.resolveMethod(
        "WeaponSpecStore",
        "addSpec",
        "(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/BaseWeaponSpec;)V");

    r.resolveMethod("ProgressBar", "render", "(Ljava/lang/String;F)V");

    // Render is the 2nd (F)V method in class declaration order.
    r.resolveMethod("FloatingTextManager", "render", "(F)V", 1);

    // TextureLoader: unique descriptor.
    r.resolveMethod(
        "TextureLoader",
        "loadTexture",
        "(Lcom/fs/graphics/Object;Ljava/lang/String;IIIIZ)Lcom/fs/graphics/Object;");

    // ScriptStore: addScript is the only (String)V method.
    r.resolveMethod("ScriptStore", "addScript", "(Ljava/lang/String;)V");

    // CombatEngine hit particles field: accessed by addHitParticle.
    r.resolveFieldByMethodAccess(
        "CombatEngine", "hitParticles", "addHitParticle",
        "Lcom/fs/graphics/particle/DynamicParticleGroup;");

    // Caller-scanning: discover SpecStore.init, ScriptStore.runScripts, ScriptStore.joinScripts
    // from ResourceLoaderState.init(Map)V call sequence.
    r.resolveResourceLoaderNames();

    return r;
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
