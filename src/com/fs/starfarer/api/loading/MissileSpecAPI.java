package com.fs.starfarer.api.loading;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
public interface MissileSpecAPI {
    ShipHullSpecAPI getHullSpec();
    String getGlowSpriteName();
    String getCoreTex();
    String getFringeTex();
}
