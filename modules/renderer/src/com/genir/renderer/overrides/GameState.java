package com.genir.renderer.overrides;

import com.fs.starfarer.combat.CombatState;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GameState {
    public static boolean gameInitialized = false;

    /** Intercept for CombatState.reloadAssets — skip when CTRL or SHIFT held (hotkey conflict). */
    public static void maybeReloadAssets(CombatState state) throws IOException {
        boolean ctrl = Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
        boolean shift = Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
        if (ctrl || shift) return;
        state.reloadAssets();
    }

    /** Intercept for Class.newInstance in Expression.getCommandClass — skip instantiation (race fix). */
    public static Object skipNewInstance(Class<?> c) { return null; }
}
