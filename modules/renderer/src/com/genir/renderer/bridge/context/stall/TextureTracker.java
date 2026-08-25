package com.genir.renderer.bridge.context.stall;

import com.genir.renderer.bridge.context.BufferUtil;
import com.genir.renderer.bridge.context.ContextManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.genir.renderer.debug.Debug.asert;

public class TextureTracker { // Context-shared object.
    // Async access to this.boundTextures. Races are acceptable,
    // as long as not leading to out-of-bounds access errors.
    private int[] boundTextures = new int[1];
    private final Map<Integer, TexData> parameterCache = new ConcurrentHashMap<>();

    // Return true only when the binding request is correct and would not cause OpenGL error.
    // TODO GL_INVALID_VALUE is generated if texture is not a name returned from a previous call to glGenTextures.

    // TODO re-bind textures on glPopAttrib!!
    // TODO AttribManager handles glDeleteTextures
    public boolean glBindTexture(int target, int texture) {
        switch (target) {
            case org.lwjgl.opengl.GL11.GL_TEXTURE_2D:
            case org.lwjgl.opengl.GL11.GL_TEXTURE_1D:
            case org.lwjgl.opengl.GL12.GL_TEXTURE_3D:
            case org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP:
            case org.lwjgl.opengl.GL30.GL_TEXTURE_1D_ARRAY:
            case org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY:
            case org.lwjgl.opengl.GL31.GL_TEXTURE_RECTANGLE:
            case org.lwjgl.opengl.GL31.GL_TEXTURE_BUFFER:
            case org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE:
            case org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY:
            case org.lwjgl.opengl.GL40.GL_TEXTURE_CUBE_MAP_ARRAY:
                break;

            // Unhandled texture type.
            default:
                return false;
        }

        // Out of bounds.
        if (texture < 0) {
            return false;
        }

        int[] boundTextures = this.boundTextures;

        // New texture in new range.
        if (texture >= boundTextures.length) {
            synchronized (this) {
                while (this.boundTextures.length <= texture) {
                    this.boundTextures = BufferUtil.reallocate(this.boundTextures.length * 2, this.boundTextures);
                }

                this.boundTextures[texture] = target;
                return true;
            }
        }

        // Re-binding an existing texture.
        if (boundTextures[texture] == target) {
            return true;
        }

        // New texture.
        if (boundTextures[texture] == 0) {
            synchronized (this) {
                this.boundTextures[texture] = target;
                return true;
            }
        }

        // Attempting to re-bind an existing texture with different target.
        asert(boundTextures[texture] != target && boundTextures[texture] != 0);
        return false;
    }

    public boolean glIsTexture(int texture) {
        int[] boundTextures = this.boundTextures;
        return texture >= 0 && texture < boundTextures.length && boundTextures[texture] != 0;
    }


    public void glDeleteTextures(int texture) {
        int[] boundTextures = this.boundTextures;
        if (texture < boundTextures.length) {
            boundTextures[texture] = 0;
        }
    }

    public void updateTextureData(int target, int level, int internalformat, int width, int height) {
        // Do not track mipmaps.
        if (level != 0) {
            return;
        }

        int textureID = getContextAttribTracker().getTextureBinding(target);
        if (textureID == 0) {
            return;
        }

        asert(glIsTexture(textureID));

        TexData data = new TexData(internalformat, width, height);
        parameterCache.put(textureID, data);
    }

    public Integer getTextureData(int target, int pname) {
        switch (pname) {
            case org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH:
            case org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT:
            case org.lwjgl.opengl.GL11.GL_TEXTURE_INTERNAL_FORMAT:
                break;
            default:
                return null;
        }

        int textureID = getContextAttribTracker().getTextureBinding(target);
        TexData data = parameterCache.get(textureID);
        if (data == null) {
            return null;
        }

        return switch (pname) {
            case org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH -> data.width;
            case org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT -> data.height;
            case org.lwjgl.opengl.GL11.GL_TEXTURE_INTERNAL_FORMAT -> data.internalformat;
            default -> null;
        };
    }

    private AttribTracker getContextAttribTracker() {
        // TextureTracker object is shared between contexts, therefore
        // it cannot have a static reference to the context-local AttribTracker.
        return ContextManager.getThreadContext().attribTracker;
    }

    private record TexData(int internalformat, int width, int height) {
    }
}
