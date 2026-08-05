package com.genir.renderer.benchmark;

import java.lang.reflect.*;

/** Auto-launches a mission via -DlaunchMission=<id>. */
public class BenchmarkLauncher {
  public static void start() {
    String mission = System.getProperty("launchMission");
    if (mission == null) return;

    Thread t =
        new Thread(
            () -> {
              try {
                launch(mission);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            },
            "FR-BenchmarkLauncher");
    t.setDaemon(true);
    t.start();
  }

  private static void launch(String id) throws Exception {
    ClassLoader cl = ClassLoader.getSystemClassLoader();
    Class<?> driverClass = cl.loadClass("com.fs.state.AppDriver");
    Field instF = driverClass.getDeclaredField("instance");
    instF.setAccessible(true);
    Field stateF = driverClass.getDeclaredField("currState");
    stateF.setAccessible(true);

    // Poll for TitleScreenState.
    Object title = null;
    while (title == null) {
      Thread.sleep(1000);
      Object drv = instF.get(null);
      if (drv == null) continue;
      Object s = stateF.get(drv);
      if (s != null && s.getClass().getName().contains("TitleScreenState")) title = s;
    }
    Thread.sleep(1000);

    // Discover mission types from missionAccepted(OO0O) signature.
    Method accept = null;
    for (Method m : title.getClass().getMethods())
      if (m.getName().equals("missionAccepted") && m.getParameterCount() == 1) {
        accept = m;
        break;
      }
    Class<?> defClass = accept.getParameterTypes()[0];
    Constructor<?> defCtor = null;
    for (Constructor<?> c : defClass.getConstructors())
      if (c.getParameterCount() == 1) {
        defCtor = c;
        break;
      }
    Class<?> specClass = defCtor.getParameterTypes()[0];

    // Create spec, set class name, launch.
    Object spec = specClass.getConstructor(String.class).newInstance(id);
    Field f = specClass.getDeclaredField("Object");
    f.setAccessible(true);
    f.set(spec, "data.missions." + id + ".MissionDefinition");
    accept.invoke(title, defCtor.newInstance(spec));
  }
}
