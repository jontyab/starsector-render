package com.genir.renderer.hooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.*;

/**
 * ASM hooking primitives for runtime game class transformation. Each method returns a ClassVisitor
 * factory (parent → child) that can be composed via {@link #compose} for multi-hook classes.
 *
 * <p>Low-level primitives (intercept, widenAccess, addField, renameMethod, removeMethod,
 * rewriteConstant, replaceBody) manipulate bytecode directly. The declarative layer on top
 * (replaceBody, replaceWith, addForwarder, insertCall, addGetter) emits boilerplate for the
 * common pattern — forwarding a game method body to an FR static method in the renderer module.
 * {@link #body()} builds bespoke bodies without raw MethodVisitor noise.
 */
public class Hooks {

  /**
   * Inserts arbitrary bytecode at the beginning of a matched method. The emitter writes
   * instructions into the MethodVisitor before the original body.
   */
  public static Function<ClassVisitor, ClassVisitor> prepend(
      String methodName, String methodDesc, Consumer<MethodVisitor> emitter) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (!name.equals(methodName) || !desc.equals(methodDesc)) return mv;
            return new MethodVisitor(Opcodes.ASM9, mv) {
              @Override
              public void visitCode() {
                super.visitCode();
                emitter.accept(mv);
              }
            };
          }
        };
  }

  /**
   * Replaces a specific method call instruction within a method body. E.g., replace
   * Display.update() → Sync.syncAndUpdate().
   */
  public static Function<ClassVisitor, ClassVisitor> intercept(
      String inMethodName,
      String inMethodDesc,
      String victimOwner,
      String victimName,
      String victimDesc,
      String replacementOwner,
      String replacementName,
      String replacementDesc) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (!name.equals(inMethodName) || !desc.equals(inMethodDesc)) return mv;
            return new MethodVisitor(Opcodes.ASM9, mv) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean itf) {
                if (owner.equals(victimOwner)
                    && name.equals(victimName)
                    && desc.equals(victimDesc)) {
                  super.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      replacementOwner,
                      replacementName,
                      replacementDesc,
                      false);
                } else {
                  super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
              }
            };
          }
        };
  }

  /** Adds a new field to the class. */
  public static Function<ClassVisitor, ClassVisitor> addField(String name, String descriptor, int access) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public void visitEnd() {
            cv.visitField(access, name, descriptor, null, null).visitEnd();
            super.visitEnd();
          }
        };
  }

  /** Makes all methods and fields in the class public. */
  public static Function<ClassVisitor, ClassVisitor> widenAccess() {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            return super.visitMethod(makePublic(access), name, desc, sig, ex);
          }

          @Override
          public FieldVisitor visitField(
              int access, String name, String desc, String sig, Object value) {
            return super.visitField(makePublic(access), name, desc, sig, value);
          }

          private int makePublic(int access) {
            return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
          }
        };
  }

  /** Adds a new method to the class with a custom body emitter. */
  public static Function<ClassVisitor, ClassVisitor> addMethod(
      int access, String name, String desc, Consumer<MethodVisitor> emitter) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public void visitEnd() {
            MethodVisitor mv = super.visitMethod(access, name, desc, null, null);
            mv.visitCode();
            emitter.accept(mv);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            super.visitEnd();
          }
        };
  }

  /**
   * Renames an existing method (e.g., loadTexture → loadTexture$original) and adds a new method
   * under the original name that delegates to a static hook.
   */
  public static Function<ClassVisitor, ClassVisitor> wrapMethod(
      String methodName,
      String methodDesc,
      String newOriginalName,
      String hookOwner,
      String hookName,
      String hookDesc) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            if (name.equals(methodName) && desc.equals(methodDesc)) {
              // Rename existing method to $original
              return super.visitMethod(access, newOriginalName, desc, sig, ex);
            }
            return super.visitMethod(access, name, desc, sig, ex);
          }

          @Override
          public void visitEnd() {
            // Add wrapper under original name
            MethodVisitor mv =
                super.visitMethod(Opcodes.ACC_PUBLIC, methodName, methodDesc, null, null);
            mv.visitCode();
            int slot = 0;
            mv.visitVarInsn(Opcodes.ALOAD, slot++); // this
            for (Type argType : Type.getArgumentTypes(methodDesc)) {
              mv.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), slot);
              slot += argType.getSize();
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, hookOwner, hookName, hookDesc, false);
            mv.visitInsn(Type.getReturnType(methodDesc).getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            super.visitEnd();
          }
        };
  }

  /**
   * Renames an existing method (e.g., init → init_vanilla in v0.8.0).
   */
  public static Function<ClassVisitor, ClassVisitor> renameMethod(
      String methodName, String methodDesc, String newName) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            if (name.equals(methodName) && desc.equals(methodDesc)) {
              return super.visitMethod(access, newName, desc, sig, ex);
            }
            return super.visitMethod(access, name, desc, sig, ex);
          }
        };
  }

  /** Compose two hooks on the same class. */
  public static Function<ClassVisitor, ClassVisitor> compose(
      Function<ClassVisitor, ClassVisitor> first, Function<ClassVisitor, ClassVisitor> second) {
    return parent -> first.apply(second.apply(parent));
  }

  /** Compose multiple hooks on the same class. */
  @SafeVarargs
  public static Function<ClassVisitor, ClassVisitor> compose(
      Function<ClassVisitor, ClassVisitor>... hooks) {
    return Arrays.stream(hooks).reduce(Function.identity(), Hooks::compose);
  }

  /** Removes a method (e.g., a vanilla method replaced by an FR addMethod version). */
  public static Function<ClassVisitor, ClassVisitor> removeMethod(
      String methodName, String methodDesc) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            if (name.equals(methodName) && desc.equals(methodDesc)) {
              return null;
            }
            return super.visitMethod(access, name, desc, sig, ex);
          }
        };
  }

  /** Rewrites all LDC instructions matching {@code from} to {@code to}. */
  public static Function<ClassVisitor, ClassVisitor> rewriteConstant(Object from, Object to) {
    return parent -> new ClassVisitor(Opcodes.ASM9, parent) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, sig, ex)) {
          @Override
          public void visitLdcInsn(Object value) {
            super.visitLdcInsn(from.equals(value) ? to : value);
          }
        };
      }
    };
  }

  /** Replaces the body of a matched method with a custom emitter. */
  public static Function<ClassVisitor, ClassVisitor> replaceBody(
      String methodName, String methodDesc, Consumer<MethodVisitor> emitter) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor out = super.visitMethod(access, name, desc, sig, ex);
            if (!name.equals(methodName) || !desc.equals(methodDesc)) return out;
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitCode() {
                out.visitCode();
                emitter.accept(out);
                out.visitMaxs(0, 0);
                out.visitEnd();
              }
            };
          }
        };
  }

  // ---------------------------------------------------------------------
  // Declarative layer: forward bodies to FR static methods instead of
  // hand-writing bytecode. FR methods live in the renderer module, written
  // in ordinary Java; these hooks only emit the call.
  //
  // forwardBody convention: the FR method's arguments map 1:1 onto the
  // target's local slots, with the receiver (this) FIRST when the FR
  // method takes it — the same convention wrapMethod uses. So an instance
  // method render(Z)V forwards to FR.render(CombatEngine, boolean).
  // ---------------------------------------------------------------------

  /** Emits {@code this + args → INVOKESTATIC fr → return}, replacing the target body. */
  public static Function<ClassVisitor, ClassVisitor> replaceWith(
      String methodName, String methodDesc, String frClass, String frMethod, String frDesc) {
    return replaceBody(
        methodName, methodDesc, forwardBody(false, methodDesc, frClass, frMethod, frDesc));
  }

  /** Adds a public method whose body forwards to an FR static. */
  public static Function<ClassVisitor, ClassVisitor> addForwarder(
      String name, String desc, String frClass, String frMethod, String frDesc) {
    return addMethod(
        Opcodes.ACC_PUBLIC, name, desc, forwardBody(true, desc, frClass, frMethod, frDesc));
  }

  /**
   * Prepends a call to an FR static with the given locals as arguments
   * (0 = this). Reference arguments get a redundant-but-safe CHECKCAST to the
   * FR parameter type.
   */
  public static Function<ClassVisitor, ClassVisitor> insertCall(
      String methodName,
      String methodDesc,
      String frClass,
      String frMethod,
      String frDesc,
      int... argLocals) {
    Type[] frArgs = Type.getArgumentTypes(frDesc);
    if (frArgs.length != argLocals.length) {
      throw new IllegalArgumentException(
          "insertCall: " + frMethod + " takes " + frArgs.length
              + " args, got " + argLocals.length + " locals");
    }
    return prepend(
        methodName,
        methodDesc,
        mv -> {
          for (int i = 0; i < argLocals.length; i++) {
            Type t = frArgs[i];
            mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), argLocals[i]);
            if (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY) {
              mv.visitTypeInsn(Opcodes.CHECKCAST, t.getInternalName());
            }
          }
          mv.visitMethodInsn(Opcodes.INVOKESTATIC, frClass, frMethod, frDesc, false);
        });
  }

  /** Adds a public getter that returns a field of the transformed class. */
  public static Function<ClassVisitor, ClassVisitor> addGetter(
      String name, String retDesc, String field, String fieldDesc) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          private String owner;

          @Override
          public void visit(
              int version, int access, String name, String sig, String superName, String[] interfaces) {
            owner = name;
            super.visit(version, access, name, sig, superName, interfaces);
          }

          @Override
          public void visitEnd() {
            MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, name, "()" + retDesc, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, field, fieldDesc);
            mv.visitInsn(Type.getType(retDesc).getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            super.visitEnd();
          }
        };
  }

  /**
   * Emits a forward-this-and-args call; see the convention note above. Slots derive from the
   * target's local layout: a static target starts args at slot 0, an instance target has the
   * receiver at slot 0 and params after. Added methods are always instance (slot 0 = receiver).
   */
  private static Consumer<MethodVisitor> forwardBody(
      boolean addedMethod, String targetDesc, String frClass, String frMethod, String frDesc) {
    Type[] targetArgs = Type.getArgumentTypes(targetDesc);
    Type[] frArgs = Type.getArgumentTypes(frDesc);
    Type ret = Type.getReturnType(targetDesc);
    boolean pushThis = frArgs.length == targetArgs.length + 1;
    int argStart = pushThis ? 1 : 0;
    int baseSlot = addedMethod || pushThis ? 1 : 0;
    int[] slots = new int[frArgs.length - argStart];
    int slot = baseSlot;
    for (int i = argStart; i < frArgs.length; i++) {
      slots[i - argStart] = slot;
      slot += frArgs[i].getSize();
    }
    return mv -> {
      if (pushThis) mv.visitVarInsn(Opcodes.ALOAD, 0);
      for (int i = argStart; i < frArgs.length; i++) {
        mv.visitVarInsn(frArgs[i].getOpcode(Opcodes.ILOAD), slots[i - argStart]);
      }
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, frClass, frMethod, frDesc, false);
      mv.visitInsn(ret.getOpcode(Opcodes.IRETURN));
    };
  }

  /** Readable builder for bespoke method bodies (replaces raw MethodVisitor code). */
  public static Body body() {
    return new Body();
  }

  public static final class Body implements Consumer<MethodVisitor> {
    private final List<Consumer<MethodVisitor>> steps =
        new ArrayList<>();

    private Body() {}

    public Body load(int slot) {
      return step(mv -> mv.visitVarInsn(Opcodes.ALOAD, slot));
    }

    public Body store(int slot) {
      return step(mv -> mv.visitVarInsn(Opcodes.ASTORE, slot));
    }

    public Body cast(String internalName) {
      return step(mv -> mv.visitTypeInsn(Opcodes.CHECKCAST, internalName));
    }

    public Body getField(String owner, String name, String desc) {
      return step(mv -> mv.visitFieldInsn(Opcodes.GETFIELD, owner, name, desc));
    }

    public Body invokeVirtual(String owner, String name, String desc) {
      return step(mv -> mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc, false));
    }

    public Body invokeInterface(String owner, String name, String desc) {
      return step(mv -> mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, desc, true));
    }

    public Body invokeStatic(String owner, String name, String desc) {
      return step(mv -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false));
    }

    public Body loadFloat(float value) {
      return step(mv -> mv.visitLdcInsn(value));
    }

    public Body returnVoid() {
      return step(mv -> mv.visitInsn(Opcodes.RETURN));
    }

    public Body returnInt() {
      return step(mv -> mv.visitInsn(Opcodes.IRETURN));
    }

    private Body step(Consumer<MethodVisitor> s) {
      steps.add(s);
      return this;
    }

    @Override
    public void accept(MethodVisitor mv) {
      for (Consumer<MethodVisitor> s : steps) s.accept(mv);
    }
  }
}
