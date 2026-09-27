package com.fs.starfarer.api;

/** Game settings. Compile-time stub for starfarer.api.jar. */
public interface SettingsAPI {
    ClassLoader getScriptClassLoader();
    boolean isSoundEnabled();
    float getScreenWidth();
    float getScreenHeight();
    ModManagerAPI getModManager();
}