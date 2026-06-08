package com.genir.renderer.hooks;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.*;

/**
 * Discovers obfuscated class/method/field names by structural fingerprinting. Scans game jars with
 * ClassReader (SKIP_CODE | SKIP_DEBUG) — fast, no instantiation.
 *
 * <p>Usage: Resolver r = new Resolver(gameJarPaths); r.resolveClass("DeploymentManager",
 * "com/fs/starfarer/combat/ai/admiral/", cls ->
 * cls.interfaces.contains("com/fs/starfarer/api/combat/CombatFleetManagerAPI$AssignmentInfo"));
 * r.resolveMethod("DeploymentManager", "pickReinforcement", "(...)V", 0); String obfName =
 * r.method("DeploymentManager", "pickReinforcement");
 */
public class Resolver {

  private final Map<String, ClassInfo> classInfoByInternal = new HashMap<>();
  private final Map<String, String> resolvedClasses = new HashMap<>(); // logical -> internal name
  private final Map<String, String> resolvedMethods =
      new HashMap<>(); // "class.method" -> obfuscated name
  private final Map<String, String> resolvedFields =
      new HashMap<>(); // "class.field" -> obfuscated name
  private final String[] jarPaths;

  /** Scan all classes from the given jar files. */
  public Resolver(String... jarPaths) {
    this.jarPaths = jarPaths;
    for (String path : jarPaths) {
      scanJar(path);
    }
  }

  /** Register a known (unobfuscated) class by its internal name. */
  public void registerClass(String logicalName, String internalName) {
    resolvedClasses.put(logicalName, internalName);
  }

  /** Register a method name discovered by external means (e.g. caller bytecode scanning). */
  public void registerMethod(String classLogical, String methodLogical, String obfName) {
    resolvedMethods.put(classLogical + "." + methodLogical, obfName);
  }

  /** Resolve an obfuscated class by package prefix and structural predicate. */
  public void resolveClass(
      String logicalName, String packagePrefix, java.util.function.Predicate<ClassInfo> predicate) {
    for (ClassInfo info : classInfoByInternal.values()) {
      if (!info.name.startsWith(packagePrefix)) continue;
      if (predicate.test(info)) {
        resolvedClasses.put(logicalName, info.name);
        return;
      }
    }
    throw new RuntimeException("Failed to resolve class: " + logicalName);
  }

  /** Resolve a method by descriptor pattern and position (Nth match). */
  public void resolveMethod(String classLogical, String methodLogical, String desc, int nth) {
    String internal = resolvedClasses.get(classLogical);
    if (internal == null) throw new RuntimeException("Class not resolved: " + classLogical);
    ClassInfo info = classInfoByInternal.get(internal);
    if (info == null) throw new RuntimeException("ClassInfo not found: " + internal);

    int count = 0;
    for (MemberInfo m : info.methods) {
      if (m.desc.equals(desc)) {
        if (count == nth) {
          resolvedMethods.put(classLogical + "." + methodLogical, m.name);
          return;
        }
        count++;
      }
    }
    throw new RuntimeException(
        "Failed to resolve method: "
            + classLogical
            + "."
            + methodLogical
            + " desc="
            + desc
            + " nth="
            + nth
            + " (found "
            + count
            + " matches)");
  }

  /** Resolve a method by unique descriptor (must be exactly 1 match). */
  public void resolveMethod(String classLogical, String methodLogical, String desc) {
    resolveMethod(classLogical, methodLogical, desc, 0);
  }

  /** Resolve a method by predicate (must match exactly 1). */
  public void resolveMethod(
      String classLogical,
      String methodLogical,
      java.util.function.Predicate<MemberInfo> predicate) {
    String internal = resolvedClasses.get(classLogical);
    if (internal == null) throw new RuntimeException("Class not resolved: " + classLogical);
    ClassInfo info = classInfoByInternal.get(internal);
    MemberInfo match = null;
    for (MemberInfo m : info.methods) {
      if (predicate.test(m)) {
        if (match != null)
          throw new RuntimeException("Ambiguous method: " + classLogical + "." + methodLogical);
        match = m;
      }
    }
    if (match == null)
      throw new RuntimeException("Failed to resolve method: " + classLogical + "." + methodLogical);
    resolvedMethods.put(classLogical + "." + methodLogical, match.name);
  }

  /** Look up a resolved class internal name. */
  public String className(String logicalName) {
    String r = resolvedClasses.get(logicalName);
    if (r == null) throw new RuntimeException("Unresolved class: " + logicalName);
    return r;
  }

  /** Look up a resolved method name. */
  public String method(String classLogical, String methodLogical) {
    String r = resolvedMethods.get(classLogical + "." + methodLogical);
    if (r == null)
      throw new RuntimeException("Unresolved method: " + classLogical + "." + methodLogical);
    return r;
  }

  /** Look up the descriptor of a resolved method. */
  public String methodDesc(String classLogical, String methodLogical) {
    String methodName = method(classLogical, methodLogical);
    ClassInfo info = classInfoByInternal.get(resolvedClasses.get(classLogical));
    for (MemberInfo m : info.methods) {
      if (m.name.equals(methodName)) return m.desc;
    }
    throw new RuntimeException("Method not found in class: " + classLogical + "." + methodLogical);
  }


  /** Look up a resolved field name. */
  public String field(String classLogical, String fieldLogical) {
    String r = resolvedFields.get(classLogical + "." + fieldLogical);
    if (r == null)
      throw new RuntimeException("Unresolved field: " + classLogical + "." + fieldLogical);
    return r;
  }

  /** Register a field name discovered by external means. */
  public void registerField(String classLogical, String fieldLogical, String obfName) {
    resolvedFields.put(classLogical + "." + fieldLogical, obfName);
  }

  /** Resolve a field by predicate (must match exactly 1). */
  public void resolveField(
      String classLogical,
      String fieldLogical,
      java.util.function.Predicate<MemberInfo> predicate) {
    String internal = resolvedClasses.get(classLogical);
    if (internal == null) throw new RuntimeException("Class not resolved: " + classLogical);
    ClassInfo info = classInfoByInternal.get(internal);
    MemberInfo match = null;
    for (MemberInfo f : info.fields) {
      if (predicate.test(f)) {
        if (match != null)
          throw new RuntimeException("Ambiguous field: " + classLogical + "." + fieldLogical);
        match = f;
      }
    }
    if (match == null)
      throw new RuntimeException("Failed to resolve field: " + classLogical + "." + fieldLogical);
    resolvedFields.put(classLogical + "." + fieldLogical, match.name);
  }

  // TODO: workaround — scans method code bodies because SKIP_CODE metadata is insufficient.
  // Ideally fields would be resolvable by structural predicate alone.
  /**
   * Resolve a field by scanning a method body for the first GETFIELD with a matching descriptor.
   */
  public void resolveFieldByMethodAccess(
      String classLogical, String fieldLogical, String methodPrefix, String fieldDesc) {
    String internal = className(classLogical);
    byte[] bytes = loadBytecode(internal);
    String[] result = new String[1];
    new ClassReader(bytes)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access, String name, String desc, String sig, String[] ex) {
                if (!name.startsWith(methodPrefix) || result[0] != null) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitFieldInsn(int op, String owner, String fname, String fdesc) {
                    if (op == Opcodes.GETFIELD && fdesc.equals(fieldDesc)
                        && owner.equals(internal) && result[0] == null) {
                      result[0] = fname;
                    }
                  }
                };
              }
            },
            0);
    if (result[0] == null) {
      throw new RuntimeException(
          "Failed to resolve field via " + internal + "." + methodPrefix + " -> " + fieldDesc);
    }
    resolvedFields.put(classLogical + "." + fieldLogical, result[0]);
  }

  // TODO: workaround — hardcoded knowledge of ResourceLoaderState.init call structure.
  // Fragile if game reorders calls or adds new ScriptStore methods.
  /**
   * Scans ResourceLoaderState.init(Map)V call sequence to discover SpecStore.init,
   * ScriptStore.runScripts, and ScriptStore.joinScripts by structural anchors.
   */
  public void resolveResourceLoaderNames() {
    byte[] bytes = loadBytecode("com/fs/starfarer/loading/ResourceLoaderState");
    String[] result = new String[3]; // [specStoreInit, runScripts, joinScripts]
    new ClassReader(bytes)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access, String name, String desc, String sig, String[] ex) {
                if (!name.equals("init") || !desc.equals("(Ljava/util/Map;)V")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                  boolean specStoreFound = false;
                  String addScriptName = null;

                  @Override
                  public void visitMethodInsn(
                      int op, String owner, String n, String d, boolean itf) {
                    if (owner.equals("com/fs/starfarer/loading/SpecStore")
                        && d.equals("(Lcom/fs/starfarer/loading/ResourceLoaderState;)V")) {
                      result[0] = n;
                      specStoreFound = true;
                    }
                    if (owner.equals("com/fs/starfarer/loading/scripts/ScriptStore")) {
                      if (d.equals("(Ljava/lang/String;)V")) addScriptName = n;
                      if (d.equals("()V")) {
                        if (addScriptName != null && !specStoreFound && result[1] == null)
                          result[1] = n;
                        if (specStoreFound && result[2] == null) result[2] = n;
                      }
                    }
                  }
                };
              }
            },
            0);
    if (result[0] == null || result[1] == null || result[2] == null) {
      throw new RuntimeException(
          "ResourceLoaderState scan failed: specStoreInit="
              + result[0] + " runScripts=" + result[1] + " joinScripts=" + result[2]);
    }
    resolvedMethods.put("SpecStore.init", result[0]);
    resolvedMethods.put("ScriptStore.runScripts", result[1]);
    resolvedMethods.put("ScriptStore.joinScripts", result[2]);
  }

  /** Load raw class bytecode (with code bodies) from game jars. */
  private byte[] loadBytecode(String internalName) {
    String entry = internalName + ".class";
    for (String path : jarPaths) {
      try (JarFile jf = new JarFile(path)) {
        var je = jf.getJarEntry(entry);
        if (je != null) return jf.getInputStream(je).readAllBytes();
      } catch (IOException ignored) {
      }
    }
    throw new RuntimeException("Not found in game jars: " + internalName);
  }

  /** Get ClassInfo for a resolved class. */
  public ClassInfo info(String logicalName) {
    return classInfoByInternal.get(className(logicalName));
  }

  private void scanJar(String path) {
    try (JarFile jar = new JarFile(path)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (!entry.getName().endsWith(".class")) continue;
        try (InputStream is = jar.getInputStream(entry)) {
          ClassReader cr = new ClassReader(is);
          ClassInfo info = new ClassInfo();
          cr.accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(
                    int v,
                    int access,
                    String name,
                    String sig,
                    String superName,
                    String[] interfaces) {
                  info.name = name;
                  info.superName = superName;
                  info.access = access;
                  if (interfaces != null) Collections.addAll(info.interfaces, interfaces);
                }

                @Override
                public FieldVisitor visitField(
                    int access, String name, String desc, String sig, Object value) {
                  info.fields.add(new MemberInfo(name, desc, access));
                  return null;
                }

                @Override
                public MethodVisitor visitMethod(
                    int access, String name, String desc, String sig, String[] ex) {
                  info.methods.add(new MemberInfo(name, desc, access));
                  return null;
                }
              },
              ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
          classInfoByInternal.put(info.name, info);
        }
      }
    } catch (IOException ignored) {
      // Jar may not exist on disk (e.g. AppleJavaExtensions.jar in classpath).
    }
  }

  public static class ClassInfo {
    public String name;
    public String superName;
    public int access;
    public List<String> interfaces = new ArrayList<>();
    public List<MemberInfo> fields = new ArrayList<>();
    public List<MemberInfo> methods = new ArrayList<>();
  }

  public static class MemberInfo {
    public final String name;
    public final String desc;
    public final int access;

    public MemberInfo(String name, String desc, int access) {
      this.name = name;
      this.desc = desc;
      this.access = access;
    }
  }
}
