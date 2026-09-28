package com.fs.starfarer.api;
import com.fs.starfarer.api.graphics.SpriteAPI;
public interface SettingsAPI {
    SpriteAPI getSprite(String category, String key);
    ModManagerAPI getModManager();
    boolean isSoundEnabled();
    float getScreenWidth();
    float getScreenHeight();
    ClassLoader getScriptClassLoader();
}
