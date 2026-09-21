package com.genir.renderer.overrides;

import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.genir.renderer.bridge.context.Context;
import com.genir.renderer.bridge.context.VertexInterceptor;

import static com.fs.starfarer.api.combat.CombatEngineLayers.*;
import static com.genir.renderer.bridge.context.ContextManager.getThreadContext;

public class CombatEngine {
    public static void render(com.fs.starfarer.combat.CombatEngine engine, boolean var1) {
        if (engine.isDestroyed()) {
            return;
        }

        unlockParticleLimit(engine);

        renderLayer(engine, "GlowyContrailParticles");
        renderLayer(engine, "SmokyContrailParticles");
        renderLayer(engine, BELOW_PLANETS);
        renderLayer(engine, PLANET_LAYER);
        renderLayer(engine, ABOVE_PLANETS);
        renderLayer(engine, CLOUD_LAYER);
        renderLayer(engine, BELOW_SHIPS_LAYER);
        renderLayer(engine, UNDER_SHIPS_LAYER);
        renderLayer(engine, ASTEROIDS_LAYER);
        renderLayer(engine, CAPITAL_SHIPS_LAYER);
        renderLayer(engine, CRUISERS_LAYER);
        renderLayer(engine, DESTROYERS_LAYER);
        renderLayer(engine, FRIGATES_LAYER);
        renderLayer(engine, BELOW_PHASED_SHIPS_LAYER);
        renderLayer(engine, PHASED_SHIPS_LAYER);
        renderLayer(engine, STATION_WEAPONS_LAYER);
        renderLayer(engine, CONTRAILS_LAYER);
        renderLayer(engine, FIGHTERS_LAYER);
        renderLayer(engine, BELOW_INDICATORS_LAYER);
        renderLayer(engine, FF_INDICATORS_LAYER);
        renderLayer(engine, ABOVE_SHIPS_LAYER);
        renderLayer(engine, ABOVE_SHIPS_AND_MISSILES_LAYER);
        renderLayer(engine, "DebrisSystem");
        renderLayer(engine, "ExplosionSystem");
        renderLayer(engine, "SmoothParticles");
        renderLayer(engine, "NebulaParticles");
        renderLayer(engine, "NebulaSmoothParticles");
        renderLayer(engine, "SwirlyNebulaParticles");
        renderLayer(engine, "ExplosionParticles");
        renderLayer(engine, "SmokeParticles");
        renderLayer(engine, "NebulaSmokeParticles");
        renderLayer(engine, "HitParticles");
        renderLayer(engine, "NegativeParticles");
        renderLayer(engine, "NegativeNebulaParticles");
        renderLayer(engine, "NegativeSwirlyNebulaParticles");
        renderLayer(engine, ABOVE_PARTICLES_LOWER);
        renderLayer(engine, ABOVE_PARTICLES);
        renderLayer(engine, JUST_BELOW_WIDGETS);

        if (var1) {
            engine.renderFloatingTextManager();
        }
    }

    private static void renderLayer(com.fs.starfarer.combat.CombatEngine engine, CombatEngineLayers layer) {
        engine.getRenderer().renderOnly(engine.getViewport(), layer);

        final Context context = getThreadContext();
        context.exec.execute(new VertexInterceptor.commitLayer());
    }

    private static void renderLayer(com.fs.starfarer.combat.CombatEngine engine, String layer) {
        final Context context = getThreadContext();
        context.exec.execute(new VertexInterceptor.setReorderDraw(true));

        switch (layer) {
            case "GlowyContrailParticles" -> engine.getGlowyContrailParticles().render(0F, 0F);
            case "SmokyContrailParticles" -> engine.getSmokyContrailParticles().render(0F, 0F);
            case "DebrisSystem" -> engine.getDebrisSystem().render(0F, 0F);
            case "ExplosionSystem" -> engine.getExplosionSystem().render(0F, 0F);
            case "SmoothParticles" -> engine.getSmoothParticles().render(0F, 0F);
            case "NebulaParticles" -> engine.getNebulaParticles().render(0F, 0F);
            case "NebulaSmoothParticles" -> engine.getNebulaSmoothParticles().render(0F, 0F);
            case "SwirlyNebulaParticles" -> engine.getSwirlyNebulaParticles().render(0F, 0F);
            case "ExplosionParticles" -> engine.getExplosionParticles().render(0F, 0F);
            case "SmokeParticles" -> engine.getSmokeParticles().render(0F, 0F);
            case "NebulaSmokeParticles" -> engine.getNebulaSmokeParticles().render(0F, 0F);
            case "HitParticles" -> engine.getHitParticlesGroup().render(0F, 0F);
            case "NegativeParticles" -> engine.getNegativeParticles().render(0F, 0F);
            case "NegativeNebulaParticles" -> engine.getNegativeNebulaParticles().render(0F, 0F);
            case "NegativeSwirlyNebulaParticles" -> engine.getNegativeSwirlyNebulaParticles().render(0F, 0F);
        }

        context.exec.execute(new VertexInterceptor.setReorderDraw(false));
        context.exec.execute(new VertexInterceptor.commitLayer());
    }

    private static void unlockParticleLimit(com.fs.starfarer.combat.CombatEngine engine) {
        int NO_LIMIT = Integer.MAX_VALUE;

        if (engine.getGlowyContrailParticles().getLimit() == NO_LIMIT) {
            return;
        }

        engine.getGlowyContrailParticles().setLimit(NO_LIMIT);
        engine.getSmokyContrailParticles().setLimit(NO_LIMIT);
        engine.getSmoothParticles().setLimit(NO_LIMIT);
        engine.getNebulaParticles().setLimit(NO_LIMIT);
        engine.getNebulaSmoothParticles().setLimit(NO_LIMIT);
        engine.getSwirlyNebulaParticles().setLimit(NO_LIMIT);
        engine.getExplosionParticles().setLimit(NO_LIMIT);
        engine.getSmokeParticles().setLimit(NO_LIMIT);
        engine.getNebulaSmokeParticles().setLimit(NO_LIMIT);
        engine.getHitParticlesGroup().setLimit(NO_LIMIT);
        engine.getNegativeParticles().setLimit(NO_LIMIT);
        engine.getNegativeNebulaParticles().setLimit(NO_LIMIT);
        engine.getNegativeSwirlyNebulaParticles().setLimit(NO_LIMIT);
    }
}
