package com.genir.renderer.bridge.context;

import com.genir.renderer.bridge.context.stall.AttribState;

/**
 * Track attributes used by reordered draws - vanilla Roiling Swarm and particles.
 * A subset of attributes in AttribState class plus draw mode.
 */
public class ReorderedDrawContext {
    // Mode.
    public final int mode;

    // Texture.
    public final boolean enableTexture;
    public final int texture2D;

    // Blend.
    public final boolean enableBlend;
    public final int blendSfactor;
    public final int blendDfactor;
    public final int blendEquation;

    public ReorderedDrawContext(int mode, AttribState attribs) {
        this.mode = mode;

        this.enableTexture = attribs.enableTexture2D;
        this.texture2D = attribs.texture2DUnit0;

        this.enableBlend = attribs.enableBlend;
        this.blendSfactor = attribs.blend.sfactorRGB;
        this.blendDfactor = attribs.blend.dfactorRGB;
        this.blendEquation = attribs.blendEquation;
    }

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
