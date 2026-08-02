package com.genir.renderer.hooks;

import java.util.Arrays;
import java.util.function.Function;
import org.objectweb.asm.*;

/**
 * ASM hooking primitives for runtime game class transformation. Each method returns a ClassVisitor
 * factory (parent → child) that can be composed via {@link #compose} for multi-hook classes.
 */
public class Hooks {

  /**
   * Inserts arbitrary bytecode at the beginning of a matched method. The emitter writes
   * instructions into the MethodVisitor before the original body.
   */
  public static Function<ClassVisitor, ClassVisitor> prepend(
      String methodName, String methodDesc, java.util.function.Consumer<MethodVisitor> emitter) {
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
      int access, String name, String desc, java.util.function.Consumer<MethodVisitor> emitter) {
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
   * Intercepts a call within a method, replaces it with a different call, then skips all bytecode
   * until a forward anchor call is reached. The anchor call itself is NOT skipped — it executes
   * normally (or can be independently intercepted by another hook in the composition).
   */
  public static Function<ClassVisitor, ClassVisitor> interceptAndSkip(
      String inMethodName,
      String inMethodDesc,
      String victimOwner,
      String victimName,
      String victimDesc,
      String replacementOwner,
      String replacementName,
      String replacementDesc,
      String anchorOwner,
      String anchorName,
      String anchorDesc) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (!name.equals(inMethodName) || !desc.equals(inMethodDesc)) return mv;
            // Toggle this.mv between delegate and null to control skipping.
            return new MethodVisitor(Opcodes.ASM9, mv) {
              private final MethodVisitor delegate = mv;

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean itf) {
                if (this.mv == null
                    && owner.equals(anchorOwner)
                    && name.equals(anchorName)
                    && desc.equals(anchorDesc)) {
                  this.mv = delegate;
                  delegate.visitMethodInsn(opcode, owner, name, desc, itf);
                } else if (this.mv != null
                    && owner.equals(victimOwner)
                    && name.equals(victimName)
                    && desc.equals(victimDesc)) {
                  delegate.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      replacementOwner,
                      replacementName,
                      replacementDesc,
                      false);
                  this.mv = null;
                } else if (this.mv != null) {
                  delegate.visitMethodInsn(opcode, owner, name, desc, itf);
                }
              }

              @Override
              public void visitTryCatchBlock(Label s, Label e, Label h, String t) {
                // Always emit — orphaned ranges are harmless under -noverify.
                delegate.visitTryCatchBlock(s, e, h, t);
              }
            };
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
      String methodName, String methodDesc, java.util.function.Consumer<MethodVisitor> emitter) {
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
}
