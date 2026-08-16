package com.genir.renderer.agent;

import java.lang.instrument.Instrumentation;

public final class Agent {
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassTransformer(), false);
        // Dev tooling: auto-launch a mission via -DlaunchMission=<id> (BenchmarkLauncher
        // lives in the renderer module; reflect to keep the agent self-contained).
        if (System.getProperty("launchMission") != null) {
            try {
                Class.forName("com.genir.renderer.benchmark.BenchmarkLauncher")
                    .getMethod("start")
                    .invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}