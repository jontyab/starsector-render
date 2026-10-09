package com.fs.starfarer.api.impl.combat.threat;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
public class RoilingSwarmEffect {
    public static class RoilingSwarmParams {
        public boolean renderFlashOnSameLayer;
        public float baseSpriteSize;
        public float alphaMult;
        public float alphaMultBase;
        public float alphaMultFlash;
        public float flashCoreRadiusMult;
        public float flashRadius;
        public Color color;
        public Color flashCoreColor;
        public Color flashFringeColor;
    }
    public static class SwarmMember {
        public SpriteAPI sprite;
        public Vector2f loc;
        public float angle;
        public float scale;
        public Fader fader;
        public Fader flash;
    }
    public interface Fader {
        float getBrightness();
    }
}
