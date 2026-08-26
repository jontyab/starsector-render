package com.genir.renderer.bridge.context;

/**
 * Track attributes used by reordered draws - vanilla Roiling Swarm and particles.
 */
public class ReorderedDrawContext {
    // Mode.
    public int mode;

    // Texture.
    public boolean enableTexture;
    public int texture2D;

    // Blend.
    public boolean enableBlend;
    public int blendSfactor;
    public int blendDfactor;
    public int blendEquation;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReorderedDrawContext that = (ReorderedDrawContext) o;

        // Compare mode and enabled tests.
        if (mode != that.mode) return false;
        if (enableTexture != that.enableTexture) return false;
        if (enableBlend != that.enableBlend) return false;

        // Compare texture context.
        if (enableTexture) {
            if (texture2D != that.texture2D) return false;
        }

        // Compare blend context.
        if (enableBlend) {
            if (blendSfactor != that.blendSfactor) return false;
            if (blendDfactor != that.blendDfactor) return false;
            if (blendEquation != that.blendEquation) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return texture2D;
    }
}
