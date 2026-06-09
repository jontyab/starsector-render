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
 */
public class Resolver {

  private final Map<String, ClassInfo> classInfoByInternal = new HashMap<>();
  private final Map<String, String> resolvedClasses = new HashMap<>();
  private final Map<String, String> resolvedMethods = new HashMap<>();
  private final Map<String, String> resolvedFields = new HashMap<>();

  /** Scan all classes from the given jar files. */
  public Resolver(String... jarPaths) {
    for (String path : jarPaths) {
      scanJar(path);
    }
  }

  /** Register a known (unobfuscated) class by its internal name. */
  public void registerClass(String logicalName, String internalName) {
    resolvedClasses.put(logicalName, internalName);
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

  /** Resolve a method by descriptor and position (Nth match). */
  public void resolveMethod(String classLogical, String methodLogical, String desc, int nth) {
    ClassInfo info = requireInfo(classLogical);
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
        "Failed to resolve method: " + classLogical + "." + methodLogical
            + " desc=" + desc + " nth=" + nth + " (found " + count + " matches)");
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
    ClassInfo info = requireInfo(classLogical);
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

  /** Resolve a field by descriptor and position (Nth match). */
  public void resolveField(String classLogical, String fieldLogical, String desc, int nth) {
    ClassInfo info = requireInfo(classLogical);
    int count = 0;
    for (MemberInfo f : info.fields) {
      if (f.desc.equals(desc)) {
        if (count == nth) {
          resolvedFields.put(classLogical + "." + fieldLogical, f.name);
          return;
        }
        count++;
      }
    }
    throw new RuntimeException(
        "Failed to resolve field: " + classLogical + "." + fieldLogical
            + " desc=" + desc + " nth=" + nth + " (found " + count + " matches)");
  }

  /** Resolve a field by unique descriptor. */
  public void resolveField(String classLogical, String fieldLogical, String desc) {
    resolveField(classLogical, fieldLogical, desc, 0);
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

  /** Get ClassInfo for a resolved class. */
  public ClassInfo info(String logicalName) {
    return classInfoByInternal.get(className(logicalName));
  }

  private ClassInfo requireInfo(String classLogical) {
    String internal = resolvedClasses.get(classLogical);
    if (internal == null) throw new RuntimeException("Class not resolved: " + classLogical);
    ClassInfo info = classInfoByInternal.get(internal);
    if (info == null) throw new RuntimeException("ClassInfo not found: " + internal);
    return info;
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
