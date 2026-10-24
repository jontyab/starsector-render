package com.genir.renderer.agent;

import java.util.Map;
import static java.util.Map.entry;

/** Platform-specific obfuscated name mappings for macOS and Linux. */
public class Platform {
    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    public static final boolean IS_MAC = OS.contains("mac");
    public static final boolean IS_LINUX = OS.contains("linux");

    // Class names used in BytecodeFileTransformer switch cases
    public static final String Expression = IS_MAC ? "com/fs/starfarer/campaign/rules/super"
            : IS_LINUX ? "com/fs/starfarer/campaign/rules/A" : "com/fs/starfarer/campaign/rules/oOOO";
    public static final String ProgressBar = IS_MAC ? "com/fs/starfarer/campaign/save/new"
            : "com/fs/starfarer/campaign/save/B";
    public static final String Bounds = IS_MAC || IS_LINUX ? "com/fs/starfarer/combat/o0OO/O0OO"
            : "com/fs/starfarer/combat/E/o0OO";
    public static final String HullSpecStore = IS_MAC || IS_LINUX ? "com/fs/starfarer/loading/M"
            : "com/fs/starfarer/loading/oO0O";
    public static final String WeaponSpecStore = IS_MAC ? "com/fs/starfarer/loading/interface"
            : IS_LINUX ? "com/fs/starfarer/loading/o00O" : "com/fs/starfarer/loading/Q";
    public static final String SoundStore = IS_MAC ? "sound/ooOO"
            : IS_LINUX ? "sound/Object" : "sound/C";
    public static final String FileUtils = IS_LINUX
            ? "com/fs/util/ooOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO"
            : "com/fs/util/C";

    public static Map<String, String> obfuscation() {
        return IS_MAC ? MAC_OBFUSCATION : IS_LINUX ? LINUX_OBFUSCATION : Map.of();
    }

    public static Map<String, String> overrides() {
        return IS_MAC ? MAC_OVERRIDES : IS_LINUX ? LINUX_OVERRIDES : Map.of();
    }

    private static final Map<String, String> MAC_OBFUSCATION = Map.ofEntries(
            // Classes
            entry("com/fs/graphics/AlphaAdder", "com/fs/graphics/oO0O"),
            entry("com/fs/graphics/font/FontRepository", "com/fs/graphics/A/String"),
            entry("com/fs/graphics/TextureTransformer", "com/fs/graphics/oooO"),
            entry("com/fs/starfarer/campaign/save/ProgressBar", "com/fs/starfarer/campaign/save/new"),
            entry("com/fs/starfarer/loading/JavaSourceFinder", "com/fs/starfarer/loading/U"),
            entry("com/fs/starfarer/loading/scripts/SecureClassLoader", "com/fs/starfarer/loading/scripts/OoOO"),
            entry("com/fs/starfarer/renderers/AtmosphereRenderer", "com/fs/starfarer/renderers/null"),
            entry("com/fs/starfarer/renderers/FloatingTextManager", "com/fs/starfarer/renderers/interface"),
            entry("com/fs/starfarer/renderers/ShipArrowRenderer", "com/fs/starfarer/renderers/oo0O"),
            entry("sound/OggLoader", "sound/J"),
            entry("sound/SoundBuffer", "sound/F"),
            entry("sound/Track", "sound/D"),
            // Members
            entry("AtmosphereRenderer_init", "o00000"),
            entry("Expression_map", "ø00000"),
            entry("FileLoader_getInstance", "Object"),
            entry("FileLoader_loadInputStream", "Object"),
            entry("FileLoader_loadInputStreams", "Ò00000"),
            entry("FloatingTextManager_render", "o00000"),
            entry("FontRepository_defineFont", "o00000"),
            entry("HullSpecStore_addHullSpec", "o00000"),
            entry("HullSpecStore_hulls", "super"),
            entry("OggLoader_load", "o00000"),
            entry("ProgressBar_render", "super"),
            entry("ProgressBar_renderWithDescription", "super"),
            entry("Rendering_begin", "void"),
            entry("Rendering_setupProjection", "super"),
            entry("ResourceLocation_isMod", "Ô00000"),
            entry("ResourceLocation_type", "o00000"),
            entry("ShipArrowRenderer_init", "super"),
            entry("StarfarerSettings_getBooleanValue", "Õ00000"),
            entry("TextureHandler_setColor0", "Ó00000"),
            entry("TextureHandler_setColor2", "new"),
            entry("TextureHandler_setImageWidth", "new"),
            entry("TextureHandler_setStringID", "new"),
            entry("TextureHandler_setWidth", "Ó00000"),
            entry("TextureRepository_addTexture", "o00000"),
            entry("TextureRepository_getTextureLoader", "Ô00000"),
            entry("TextureTransformer_apply", "super"),
            entry("WeaponSpecStore_addProjectileSpec", "o00000"),
            entry("WeaponSpecStore_addWeaponSpec", "o00000"),
            entry("WeaponSpecStore_weapons", "Ò00000")
    );

    private static final Map<String, String> MAC_OVERRIDES = Map.ofEntries(
            entry("com/genir/renderer/overrides/Bounds", Bounds),
            entry("com/genir/renderer/overrides/Bounds$Segment", Bounds + "$o"),
            entry("com/genir/renderer/overrides/Expression", Expression),
            entry("com/genir/renderer/overrides/loading/HullSpecStore", HullSpecStore),
            entry("com/genir/renderer/overrides/loading/SoundStore", SoundStore),
            entry("com/genir/renderer/overrides/loading/textures/TextureRepository", "com/fs/graphics/void"),
            entry("com/genir/renderer/overrides/loading/WeaponSpecStore", WeaponSpecStore),
            entry("com/genir/renderer/overrides/ProgressBar", ProgressBar)
    );

    private static final Map<String, String> LINUX_OBFUSCATION = Map.ofEntries(
            // Classes
            entry("com/fs/graphics/AlphaAdder", "com/fs/graphics/M"),
            entry("com/fs/graphics/font/FontRepository", "com/fs/graphics/super/D"),
            entry("com/fs/graphics/util/Fps", "com/fs/graphics/util/super"),
            entry("com/fs/starfarer/loading/JavaSourceFinder", "com/fs/starfarer/loading/ooOo"),
            entry("com/fs/starfarer/loading/scripts/SecureClassLoader", "com/fs/starfarer/loading/scripts/new"),
            entry("com/fs/starfarer/renderers/ShipArrowRenderer", "com/fs/starfarer/renderers/public"),
            entry("com/fs/starfarer/util/ScreenshotUtil", "com/fs/starfarer/util/F"),
            entry("com/fs/util/FileLoader", FileUtils),
            entry("com/fs/util/FileLoader$ResourceLocation", FileUtils + "$Oo"),
            entry("com/fs/util/FileLoader$ResourceLocationType", FileUtils + "$o"),
            entry("sound/OggLoader", "sound/J"),
            entry("sound/SoundBuffer", "sound/F"),
            // Members
            entry("AtmosphereRenderer_init", "o00000"),
            entry("Expression_map", "ø00000"),
            entry("FileLoader_getInstance", "Ó00000"),
            entry("FileLoader_getResourceList", "String"),
            entry("FileLoader_loadInputStream", "Ó00000"),
            entry("FileLoader_loadInputStreams", "Ò00000"),
            entry("FileLoader_loadInputStreamWithMods", "String"),
            entry("FileLoader_locationFilter", "Ô00000"),
            entry("FileLoader_withoutMods", "o00000"),
            entry("FloatingTextManager_render", "o00000"),
            entry("HullSpecStore_addHullSpec", "o00000"),
            entry("ImpactSound_init", "super"),
            entry("ProgressBar_setDescription", "new"),
            entry("Rendering_begin", "Ö00000"),
            entry("Rendering_end", "class"),
            entry("ResourceLocation_isMod", "String"),
            entry("ResourceLocation_type", "super"),
            entry("ScriptLoader_queueScript", "Ó00000"),
            entry("ScriptLoader_startScriptLoadingThread", "õ00000"),
            entry("ScriptStore_getScriptList", "class"),
            entry("ScriptStore_getPluginSet", "new"),
            entry("ScriptStore_objectRepository", "class"),
            entry("ScriptStore_javaSourceClassLoader", "float"),
            entry("SoundStore_isOpenALInitialized2", "return"),
            entry("SoundStore_trackMap", "for"),
            entry("SpecStore_init", "public"),
            // IllegalTransformations rewrites this.super before BytecodeFileTransformer runs
            entry("SpecStore_loadingSoundSets", "this_super"),
            entry("StarfarerSettings_getBooleanValue", "Õ00000"),
            entry("StarfarerSettings_getFloatValue", "if"),
            entry("Tesselator_renderAsPolygon", "super"),
            entry("TextureLoader_loadTexture", "super"),
            entry("TextureRepository_addTexture", "o00000"),
            entry("TextureRepository_getTextureLoader", "Ô00000"),
            entry("TextureTransformer_apply", "super"),
            entry("WeaponSpecStore_weapons", "Ò00000"),
            entry("WeaponSpecStore_projectiles", "super")
    );

    private static final Map<String, String> LINUX_OVERRIDES = Map.ofEntries(
            entry("com/genir/renderer/overrides/Bounds", Bounds),
            entry("com/genir/renderer/overrides/Bounds$Segment", Bounds + "$o"),
            entry("com/genir/renderer/overrides/Expression", Expression),
            entry("com/genir/renderer/overrides/loading/FileLoader", FileUtils),
            entry("com/genir/renderer/overrides/loading/HullSpecStore", HullSpecStore),
            entry("com/genir/renderer/overrides/loading/SoundStore", SoundStore),
            entry("com/genir/renderer/overrides/loading/WeaponSpecStore", WeaponSpecStore)
    );
}
