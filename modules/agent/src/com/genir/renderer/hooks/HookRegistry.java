package com.genir.renderer.hooks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps internal class names to hook factories. Applied during class loading
 * BEFORE constant-pool transforms (operates on vanilla game bytecode).
 *
 * Usage:
 *   registry.register("com/fs/graphics/L", Hooks.widenAccess());
 *   byte[] patched = registry.apply("com/fs/graphics/L", originalBytes);
 */
public class HookRegistry {

  private final Map<String, Function<ClassVisitor, ClassVisitor>> hooks = new HashMap<>();

  public void register(String internalName, Function<ClassVisitor, ClassVisitor> hook) {
    hooks.merge(internalName, hook, Hooks::compose);
  }

  public boolean hasHook(String internalName) {
    return hooks.containsKey(internalName);
  }

  /** Apply hooks to class bytes. Returns null if no hooks registered for this class. */
  public byte[] apply(String internalName, byte[] classBytes) {
    Function<ClassVisitor, ClassVisitor> hook = hooks.get(internalName);
    if (hook == null) return null;

    ClassReader reader = new ClassReader(classBytes);
    // COMPUTE_MAXS for FR-authored bodies (addMethod, wrapMethod, replaceBody).
    // Game runs with -noverify so frame validity is not strictly required.
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    ClassVisitor pipeline = hook.apply(writer);
    // Pass 0 flags — no SKIP_FRAMES, no EXPAND_FRAMES. Preserves game's existing
    // (possibly invalid) frame metadata as-is. Only FR-authored method bodies
    // (which have no frames since they're fresh) need COMPUTE_MAXS.
    reader.accept(pipeline, 0);
    return writer.toByteArray();
  }
}
