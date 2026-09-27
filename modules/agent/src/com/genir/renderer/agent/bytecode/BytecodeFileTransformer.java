package com.genir.renderer.agent.bytecode;

import com.genir.renderer.agent.Platform;
import com.genir.renderer.agent.Transformations;
import com.genir.renderer.bridge.context.Context;
import com.genir.renderer.bridge.context.ContextManager;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class BytecodeFileTransformer implements ClassFileTransformer {
    private static Throwable deferedThrowable = null;

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) {
        injectThrowableIntoRenderer();

        // No class to transform.
        if (className == null) {
            return null;
        }

        // Do not transform bootstrap and platform classes.
        if (loader == null || loader == ClassLoader.getPlatformClassLoader()) {
            return null;
        }
        try {
            var transformer = new BytecodeTransformer(classfileBuffer);
            applyTransform(className, transformer);

            if (transformer.transformApplied) {
                return transformer.targetBytes;
            } else {
                return null;
            }
        } catch (Throwable t) {
            if (deferedThrowable == null) {
                deferedThrowable = t;
                injectThrowableIntoRenderer();
            }

            throw t;
        }
    }

    /**
     * Use the executor exception handling to crash the application
     * in case of bytecode transformation failure.
     */
    private void injectThrowableIntoRenderer() {
        if (deferedThrowable == null) {
            return;
        }

        // Handle cases when the transformation failed
        // before a rendering thread was started.
        final Context context = ContextManager.getThreadContext();
        if (context == null) {
            return;
        }

        final Throwable injection = deferedThrowable;
        context.exec.execute((ctx, args, offset) -> {
            throw new RuntimeException(injection);
        });

        deferedThrowable = null;
    }

    private void applyTransform(String className, BytecodeTransformer transformer) {
        // Platform-varying obfuscated classes
        if (className.equals(Platform.Expression)) {
            transformer.removeMethod("getCommandClass", "(Ljava/lang/String;)Ljava/lang/String;");
            transformer.mergeClass("com/genir/renderer/overrides/Expression");
            return;
        }
        if (className.equals(Platform.ProgressBar)) {
            transformer.removeMethod(Transformations.obfuscation.get("ProgressBar_render"), "(Ljava/lang/String;F)V");
            transformer.mergeClass("com/genir/renderer/overrides/ProgressBar");
            return;
        }
        if (className.equals(Platform.Bounds)) {
            transformer.mergeClass("com/genir/renderer/overrides/Bounds");
            return;
        }
        if (className.equals(Platform.HullSpecStore)) {
            transformer.removeMethod(Transformations.obfuscation.get("HullSpecStore_addHullSpec"),
                    "(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/g;)V");
            transformer.mergeClass("com/genir/renderer/overrides/loading/HullSpecStore");
            return;
        }
        if (className.equals(Platform.WeaponSpecStore)) {
            transformer.removeMethod(Transformations.obfuscation.get("WeaponSpecStore_addWeaponSpec"),
                    "(Ljava/lang/String;Lcom/fs/starfarer/loading/specs/BaseWeaponSpec;)V");
            transformer.removeMethod(Transformations.obfuscation.get("WeaponSpecStore_addProjectileSpec"),
                    "(Ljava/lang/String;Ljava/lang/Object;)V");
            transformer.mergeClass("com/genir/renderer/overrides/loading/WeaponSpecStore");
            return;
        }
        if (className.equals(Platform.SoundStore)) {
            transformer.mergeClass("com/genir/renderer/overrides/loading/SoundStore");
            return;
        }
        if (className.equals(Platform.FileUtils)) {
            transformer.removeMethod(Transformations.obfuscation.getOrDefault("FileLoader_loadInputStreamWithMods", "Ô00000"), "(Ljava/lang/String;)Ljava/io/InputStream;");
            transformer.renameMethod(Transformations.obfuscation.getOrDefault("FileLoader_loadInputStream", "Ó00000"),
                    "FileLoader_loadInputStream_vanilla", "(Ljava/lang/String;Z)Ljava/io/InputStream;");
            transformer.renameMethod(Transformations.obfuscation.getOrDefault("FileLoader_loadInputStreams", "new"),
                    "FileLoader_loadInputStreams_vanilla", "(Ljava/lang/String;)Ljava/util/List;");
            transformer.mergeClass("com/genir/renderer/overrides/loading/FileLoader");
            return;
        }

        // Platform-invariant classes
        switch (className) {
            case "com/fs/graphics/LayeredRenderer":
                transformer.removeMethod("renderOnly", "(Ljava/lang/Object;Ljava/lang/Enum;)V");
                transformer.removeMethod("renderExcluding", "(Ljava/lang/Object;[Ljava/lang/Enum;)V");
                transformer.mergeClass("com/genir/renderer/overrides/LayeredRenderer");
                break;
            case "com/fs/graphics/TextureLoader":
                transformer.renameMethod(Transformations.obfuscation.getOrDefault("TextureLoader_loadTexture", "o00000"), "loadTexture_vanilla",
                        "(Lcom/fs/graphics/Object;Ljava/lang/String;IIIIZ)Lcom/fs/graphics/Object;");
                transformer.mergeClass("com/genir/renderer/overrides/loading/textures/TextureLoader");
                break;
            case "com/fs/starfarer/api/impl/combat/threat/RoilingSwarmEffect":
                transformer.removeMethod("getNumActiveMembers", "()I");
                transformer.mergeClass("com/genir/renderer/overrides/RoilingSwarmEffect");
                break;
            case "com/fs/starfarer/combat/ai/admiral/G":
                transformer.renameMethod(Transformations.obfuscation.getOrDefault("DeploymentManager_pickReinforcement", "o00000"), "pickReinforcement_vanilla",
                        "(Lcom/fs/starfarer/combat/ai/admiral/G$o;FLjava/util/List;Ljava/util/List;Z)Lcom/fs/starfarer/campaign/fleet/FleetMember;");
                transformer.mergeClass("com/genir/renderer/overrides/DeploymentManager");
                break;
            case "com/fs/starfarer/util/Tesselator":
                transformer.removeMethod(Transformations.obfuscation.getOrDefault("Tesselator_renderAsPolygon", "o00000"), "(L" + Platform.Bounds + ";FFF)V");
                transformer.mergeClass("com/genir/renderer/overrides/Tesselator");
                break;
            case "com/fs/starfarer/loading/LoadingUtils":
                transformer.renameMethod(Transformations.obfuscation.getOrDefault("LoadingUtils_filesWithExtensionInDirectoryAbsolute", "Õ00000"), "filesWithExtensionInDirectoryAbsolute_vanilla",
                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;");
                transformer.renameMethod(Transformations.obfuscation.getOrDefault("LoadingUtils_filesWithExtensionInDirectory", "super"), "filesWithExtensionInDirectory_vanilla",
                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;");
                transformer.renameMethod(Transformations.obfuscation.getOrDefault("LoadingUtils_readStreamAsString", "super"), "readStreamAsString_vanilla",
                        "(Ljava/io/InputStream;)Ljava/lang/String;");
                transformer.mergeClass("com/genir/renderer/overrides/loading/LoadingUtils");
                break;

            case "com/fs/starfarer/loading/scripts/ScriptStore":
                transformer.removeMethod(Transformations.obfuscation.getOrDefault("ScriptLoader_queueScript", "Object"), "(Ljava/lang/String;)V");
                transformer.removeMethod(Transformations.obfuscation.getOrDefault("ScriptLoader_startScriptLoadingThread", "int"), "()V");
                transformer.mergeClass("com/genir/renderer/overrides/loading/ScriptStore");
                break;
            case "com/fs/starfarer/combat/CombatEngine":
                transformer.removeMethod("render", "(Z)V");
                transformer.mergeClass("com/genir/renderer/overrides/CombatEngine");
                break;
            case "com/fs/starfarer/loading/SpecStore":
                transformer.renameMethod(Transformations.obfuscation.getOrDefault("SpecStore_init", "ÓO0000"), "init_vanilla", "(Lcom/fs/starfarer/loading/ResourceLoaderState;)V");
                transformer.renameMethod(Transformations.obfuscation.getOrDefault("SpecStore_loadingSoundSets", "ÖO0000"), "loadingSoundSets_vanilla", "(Lcom/fs/starfarer/loading/ResourceLoaderState;)V");
                transformer.mergeClass("com/genir/renderer/overrides/loading/SpecStore");
                break;
            case "com/fs/starfarer/loading/ResourceLoaderState":
                transformer.renameMethod("init", "init_vanilla", "(Ljava/util/Map;)V");
                transformer.removeMethod("queueResource", "(Lcom/fs/starfarer/loading/ResourceLoaderState$o;Ljava/lang/String;I)V");
                transformer.removeMethod("renderProgress", "(F)V");
                transformer.mergeClass("com/genir/renderer/overrides/loading/ResourceLoaderState");
                break;
            case "com/fs/starfarer/combat/CombatState":
                transformer.renameMethod("reloadAssets", "reloadAssets_vanilla", "()V");
                transformer.mergeClass("com/genir/renderer/overrides/CombatState");
                break;
            case "com/fs/graphics/Sprite":
                transformer.removeMethod("render", "(FF)V");
                transformer.mergeClass("com/genir/renderer/overrides/render/Sprite");
                break;
        }
    }
}
