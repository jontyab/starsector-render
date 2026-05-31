package com.fs.starfarer.api;

import java.util.List;

/** Mod loading state. Compile-time stub for starfarer.api.jar. */
public interface ModManagerAPI {
    List<ModPlugin> getEnabledModPlugins();
    List<ModSpecAPI> getEnabledModsCopy();
}