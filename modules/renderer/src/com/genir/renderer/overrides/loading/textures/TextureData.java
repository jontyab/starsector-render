package com.genir.renderer.overrides.loading.textures;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class TextureData {
    // DDS data.
    public Path imagePath;
    public Path ddsImagePath;

    // Vanilla data.
    public boolean hasAlpha;
    public ByteBuffer buffer;

    // DDS/Vanilla data.
    public int width;
    public int height;
    public Color mean = Color.white;
    public Color weighted = Color.white;
    public Color median = Color.white;

    public boolean isDDS() {
        return ddsImagePath != null;
    }
}