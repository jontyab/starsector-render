package com.genir.renderer.hooks;

import com.genir.renderer.loaders.ClassConstantTransformer;
import com.genir.renderer.loaders.ObfTransformations;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

/**
 * Hook registrations and resolver configuration — the "what" of FR's game modifications.
 * Human-maintained: add/remove hooks here when game changes require it.
 */
public class HookConfig {

  /** Build all hooks and member transforms, returning a ready-to-use HookRegistry. */
  public static HookRegistry build(
      List<ClassConstantTransformer> obfTransformers,
      List<ClassConstantTransformer> starfarerTransformers) {
    HookRegistry reg = new HookRegistry();
    Resolver r = buildResolver();

    // Add resolver-derived member transforms to CP transform lists.
    ClassConstantTransformer memberCT =
        new ClassConstantTransformer(buildMemberTransforms(r));
    obfTransformers.add(memberCT);
    starfarerTransformers.add(memberCT);

    // Obfuscated class names from TSV (used in hook targets and descriptors).
    String fileRepository = ObfTransformations.classOf("FileRepository");
    String textureHandler = ObfTransformations.classOf("TextureHandler");
    String resourceLoader = ObfTransformations.classOf("ResourceLoader");

    // Version: append FR version to title screen string
    reg.register(
        "com/fs/starfarer/Version",
        Hooks.rewriteConstant("Starsector 0.98a-RC8", "Starsector 0.98a-RC8 FR7.2"));

    // FileRepository: make private methods public
    reg.register(fileRepository, Hooks.widenAccess());

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
            "(L" + textureHandler + ";Ljava/lang/String;IIIIZ)L" + textureHandler + ";",
            "loadTexture_vanilla",
            "com/genir/renderer/overrides/loading/TextureLoader",
            "loadTexture",
            "(Ljava/lang/Object;L" + textureHandler + ";Ljava/lang/String;IIIIZ)L" + textureHandler + ";"));

    // C (FileUtils): widen access + add loadInputStream/loadInputStreams delegation methods
    reg.register(
        resourceLoader,
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

  private static Resolver buildResolver() {
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
        cls -> cls.methods.stream().anyMatch(m -> m.desc.equals("(Ljava/lang/String;F)V"))
            && cls.methods.stream().anyMatch(m -> m.desc.equals("(F)V")));

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
    String th = ObfTransformations.classOf("TextureHandler");
    r.resolveMethod(
        "TextureLoader",
        "loadTexture",
        "(L" + th + ";Ljava/lang/String;IIIIZ)L" + th + ";");

    // ScriptStore: addScript is the only (String)V method.
    r.resolveMethod("ScriptStore", "addScript", "(Ljava/lang/String;)V");

    // CombatEngine hit particles: 10th DynamicParticleGroup field (ordinal 9).
    r.resolveField(
        "CombatEngine", "hitParticles",
        "Lcom/fs/graphics/particle/DynamicParticleGroup;", 9);

    // SpecStore.init: first (ResourceLoaderState)V method (ordinal 0).
    r.resolveMethod(
        "SpecStore", "init",
        "(Lcom/fs/starfarer/loading/ResourceLoaderState;)V", 0);

    // ScriptStore.runScripts and joinScripts: ordinals 5 and 6 of ()V
    // (ordinals 0,1 are <clinit> and <init>).
    r.resolveMethod("ScriptStore", "runScripts", "()V", 5);
    r.resolveMethod("ScriptStore", "joinScripts", "()V", 6);

    // --- CP transform member resolution (replaces TSV member entries) ---

    // Classes needed for CP transforms (platform-varying internal names from TSV).
    r.registerClass("TextureHandler", ObfTransformations.classOf("TextureHandler"));
    r.registerClass("FileRepository", ObfTransformations.classOf("FileRepository"));
    r.registerClass("TextureRepository", ObfTransformations.classOf("TextureRepository"));
    r.registerClass("FontRepository", ObfTransformations.classOf("FontRepository"));
    r.registerClass("StarfarerSettings", ObfTransformations.classOf("StarfarerSettings"));
    r.registerClass("ResourceLocation", ObfTransformations.classOf("ResourceLocation"));
    r.registerClass("ResourceLoader", ObfTransformations.classOf("ResourceLoader"));
    r.registerClass("TextureTransformer", ObfTransformations.classOf("TextureTransformer"));

    // TextureHandler: 4 (I)V setters distinguished by ordinal.
    r.resolveMethod("TextureHandler", "setImageHeight", "(I)V", 0);
    r.resolveMethod("TextureHandler", "setImageWidth", "(I)V", 1);
    r.resolveMethod("TextureHandler", "setHeight", "(I)V", 2);
    r.resolveMethod("TextureHandler", "setWidth", "(I)V", 3);
    // 2 (Ljava/lang/String;)V setters.
    r.resolveMethod("TextureHandler", "setStringID", "(Ljava/lang/String;)V", 0);
    r.resolveMethod("TextureHandler", "setPath", "(Ljava/lang/String;)V", 1);
    // 3 (Ljava/awt/Color;)V setters.
    r.resolveMethod("TextureHandler", "serColor1", "(Ljava/awt/Color;)V", 0);
    r.resolveMethod("TextureHandler", "serColor0", "(Ljava/awt/Color;)V", 1);
    r.resolveMethod("TextureHandler", "serColor2", "(Ljava/awt/Color;)V", 2);
    // Unique getTextureID: 3rd ()I method.
    r.resolveMethod("TextureHandler", "getTextureID", "()I", 2);

    // FileRepository: both are 2nd in their descriptor group (ordinal 1).
    r.resolveMethod("FileRepository", "loadImage",
        "(Ljava/lang/String;)Ljava/awt/image/BufferedImage;", 1);
    r.resolveMethod("FileRepository", "loadSound", "(Ljava/lang/String;)[B", 1);

    // TextureRepository: unique descriptor.
    r.resolveMethod("TextureRepository", "addTexture",
        "(Ljava/lang/String;L" + th + ";)V");

    // FontRepository: unique descriptor.
    r.resolveMethod("FontRepository", "defineFont",
        "(Ljava/lang/String;Ljava/lang/String;)V");

    // StarfarerSettings: 2nd (Ljava/lang/String;)F method.
    r.resolveMethod("StarfarerSettings", "getFloatValue", "(Ljava/lang/String;)F", 1);

    // ScriptStore: unique descriptors.
    r.resolveMethod("ScriptStore", "getScriptList", "()Ljava/util/List;");
    r.resolveMethod("ScriptStore", "getPluginSet", "()Ljava/util/Set;");
    // getSecureClassLoader: returns a type in scripts/ package (platform-varying name).
    r.resolveMethod("ScriptStore", "getSecureClassLoader",
        m -> m.desc.startsWith("()Lcom/fs/starfarer/loading/scripts/")
            && !m.desc.equals("()Lorg/codehaus/janino/JavaSourceClassLoader;"));
    // ScriptStore fields: unique descriptors.
    r.resolveField("ScriptStore", "objectRepository",
        "Lcom/fs/util/container/repo/ObjectRepository;");
    r.resolveField("ScriptStore", "javaSourceClassLoader",
        "Lorg/codehaus/janino/JavaSourceClassLoader;");

    // ResourceLocation fields: all unique by descriptor.
    r.resolveField("ResourceLocation", "type", "L" + ObfTransformations.classOf("ResourceLocationType") + ";");
    r.resolveField("ResourceLocation", "path", "Ljava/lang/String;");
    r.resolveField("ResourceLocation", "isMod", "Z");

    // ResourceLoader: unique descriptors.
    r.resolveField("ResourceLoader", "locationFilter", "Ljava/lang/String;");
    r.resolveField("ResourceLoader", "withoutMods", "Z");
    r.resolveMethod("ResourceLoader", "getInstance", "()L" + ObfTransformations.classOf("ResourceLoader") + ";");
    r.resolveMethod("ResourceLoader", "getResourceList", "()Ljava/util/List;");
    // FileUtils methods on same class: unique descriptors.
    r.resolveMethod("ResourceLoader", "loadInputStream",
        "(Ljava/lang/String;Z)Ljava/io/InputStream;");
    r.resolveMethod("ResourceLoader", "loadInputStreams",
        "(Ljava/lang/String;)Ljava/util/List;", 0);

    // ProgressBar: render (F)V unique, setDescription ordinal 2 of (String)V.
    r.resolveMethod("ProgressBar", "renderProgress", "(F)V");
    r.resolveMethod("ProgressBar", "setDescription", "(Ljava/lang/String;)V", 2);

    // TextureTransformer: single-method interface.
    r.resolveMethod("TextureTransformer", "apply",
        "(Ljava/awt/image/BufferedImage;)Ljava/awt/image/BufferedImage;");

    return r;
  }

  /** Build CP transforms mapping FR proxy member names to resolved obfuscated names. */
  private static List<ClassConstantTransformer.Transform> buildMemberTransforms(Resolver r) {
    List<ClassConstantTransformer.Transform> t = new ArrayList<>();
    // TextureHandler
    t.add(ClassConstantTransformer.newTransform("TextureHandler_getTextureID", r.method("TextureHandler", "getTextureID")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_setPath", r.method("TextureHandler", "setPath")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_setHeight", r.method("TextureHandler", "setHeight")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_setWidth", r.method("TextureHandler", "setWidth")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_setImageHeight", r.method("TextureHandler", "setImageHeight")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_setImageWidth", r.method("TextureHandler", "setImageWidth")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_serColor0", r.method("TextureHandler", "serColor0")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_serColor1", r.method("TextureHandler", "serColor1")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_serColor2", r.method("TextureHandler", "serColor2")));
    t.add(ClassConstantTransformer.newTransform("TextureHandler_setStringID", r.method("TextureHandler", "setStringID")));
    // FileRepository
    t.add(ClassConstantTransformer.newTransform("FileRepository_loadImage", r.method("FileRepository", "loadImage")));
    t.add(ClassConstantTransformer.newTransform("FileRepository_loadSound", r.method("FileRepository", "loadSound")));
    // TextureRepository
    t.add(ClassConstantTransformer.newTransform("TextureRepository_addTexture", r.method("TextureRepository", "addTexture")));
    // FontRepository
    t.add(ClassConstantTransformer.newTransform("FontRepository_defineFont", r.method("FontRepository", "defineFont")));
    // StarfarerSettings
    t.add(ClassConstantTransformer.newTransform("StarfarerSettings_getFloatValue", r.method("StarfarerSettings", "getFloatValue")));
    // ScriptStore
    t.add(ClassConstantTransformer.newTransform("ScriptStore_getSecureClassLoader", r.method("ScriptStore", "getSecureClassLoader")));
    t.add(ClassConstantTransformer.newTransform("ScriptStore_getScriptList", r.method("ScriptStore", "getScriptList")));
    t.add(ClassConstantTransformer.newTransform("ScriptStore_getPluginSet", r.method("ScriptStore", "getPluginSet")));
    t.add(ClassConstantTransformer.newTransform("ScriptStore_objectRepository", r.field("ScriptStore", "objectRepository")));
    t.add(ClassConstantTransformer.newTransform("ScriptStore_javaSourceClassLoader", r.field("ScriptStore", "javaSourceClassLoader")));
    // SpecStore
    t.add(ClassConstantTransformer.newTransform("SpecStore_init", r.method("SpecStore", "init")));
    // ResourceLocation
    t.add(ClassConstantTransformer.newTransform("ResourceLocation_type", r.field("ResourceLocation", "type")));
    t.add(ClassConstantTransformer.newTransform("ResourceLocation_path", r.field("ResourceLocation", "path")));
    t.add(ClassConstantTransformer.newTransform("ResourceLocation_isMod", r.field("ResourceLocation", "isMod")));
    // ResourceLoader / FileUtils (same class: com/fs/util/C)
    t.add(ClassConstantTransformer.newTransform("ResourceLoader_locationFilter", r.field("ResourceLoader", "locationFilter")));
    t.add(ClassConstantTransformer.newTransform("ResourceLoader_withoutMods", r.field("ResourceLoader", "withoutMods")));
    t.add(ClassConstantTransformer.newTransform("ResourceLoader_getInstance", r.method("ResourceLoader", "getInstance")));
    t.add(ClassConstantTransformer.newTransform("ResourceLoader_getResourceList", r.method("ResourceLoader", "getResourceList")));
    t.add(ClassConstantTransformer.newTransform("FileUtils_loadInputStream", r.method("ResourceLoader", "loadInputStream")));
    t.add(ClassConstantTransformer.newTransform("FileUtils_loadInputStreams", r.method("ResourceLoader", "loadInputStreams")));
    // FloatingTextManager
    t.add(ClassConstantTransformer.newTransform("FloatingTextManager_render", r.method("FloatingTextManager", "render")));
    // ProgressBar
    t.add(ClassConstantTransformer.newTransform("ProgressBar_setDescription", r.method("ProgressBar", "setDescription")));
    t.add(ClassConstantTransformer.newTransform("ProgressBar_render", r.method("ProgressBar", "renderProgress")));
    t.add(ClassConstantTransformer.newTransform("ProgressBar_renderWithDescription", r.method("ProgressBar", "render")));
    // DeploymentManager
    t.add(ClassConstantTransformer.newTransform("DeploymentManager_pickReinforcement", r.method("DeploymentManager", "pickReinforcement")));
    // TextureLoader
    t.add(ClassConstantTransformer.newTransform("TextureLoader_loadTexture", r.method("TextureLoader", "loadTexture")));
    // TextureTransformer
    t.add(ClassConstantTransformer.newTransform("TextureTransformer_apply", r.method("TextureTransformer", "apply")));
    return t;
  }
}
