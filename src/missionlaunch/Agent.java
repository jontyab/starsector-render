package missionlaunch;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

/**
 * Minimal agent for auto-launching missions via -DlaunchMission=<id>.
 * Usage: -javaagent:mission-launch.jar -DlaunchMission=gl_benchmark
 */
public class Agent {
    public static void premain(String args, Instrumentation inst) {
        if (System.getProperty("launchMission") == null) return;
        
        // Start background thread to launch mission when TitleScreenState appears
        MissionLauncher.start();
        
        // Hook CombatEngine to skip deployment dialog
        inst.addTransformer(new DeploymentDialogHook());
    }
}
