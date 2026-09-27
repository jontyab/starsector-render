package com.fs.starfarer.api.fleet;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
public interface FleetMemberAPI {
    ShipHullSpecAPI getHullSpec();
    boolean isCivilian();
}
