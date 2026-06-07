package com.genir.renderer.hooks;

import java.util.function.Function;
import org.objectweb.asm.*;

/**
 * ASM hooking primitives for runtime game class transformation. Each method returns a ClassVisitor
 * factory (parent → child) that can be composed via {@link #compose} for multi-hook classes.
 *
 * <p>Constraints: - Game runs with -noverify; bytecode has invalid stack frames. - Never use
 * COMPUTE_FRAMES (crashes on obfuscated types). - COMPUTE_MAXS is safe for FR-authored bodies only.
 * - Patchers operate on vanilla game bytecode BEFORE constant-pool transforms.
 */
public class Hooks {

  /**
   * Replaces the body of a matched method with a static delegation call. The target static receives
   * all original args (including 'this' for instance methods).
   */
  public static Function<ClassVisitor, ClassVisitor> delegateBody(
      String methodName,
      String methodDesc,
      String targetOwner,
      String targetName,
      String targetDesc) {
    return parent ->
        new ClassVisitor(Opcodes.ASM9, parent) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor out = super.visitMethod(access, name, desc, sig, ex);
            if (!name.equals(methodName) || !desc.equals(methodDesc)) return out;
            // Return a visitor that writes the delegation body, ignoring original bytecode
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitCode() {
                out.visitCode();
                // Load all arguments (including this for instance methods)
                int slot = 0;
                boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                if (!isStatic) {
                  out.visitVarInsn(Opcodes.ALOAD, slot++);
                }
                for (Type argType : Type.getArgumentTypes(desc)) {
                  out.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), slot);
                  slot += argType.getSize();
                }
                out.visitMethodInsn(
                    Opcodes.INVOKESTATIC, targetOwner, targetName, targetDesc, false);
                Type ret = Type.getReturnType(desc);
                out.visitInsn(ret.getOpcode(Opcodes.IRETURN));
                out.visitMaxs(0, 0);
                out.visitEnd();
              }

              // Swallow all original bytecode — don't forward to mv
              @Override
              public void visitInsn(int opcode) {}

              @Override
              public void visitVarInsn(int opcode, int var) {}

              @Override
              public void visitFieldInsn(int opcode, String o, String n, String d) {}

              @Override
              public void visitMethodInsn(int opcode, String o, String n, String d, boolean i) {}

              @Override
              public void visitJumpInsn(int opcode, Label label) {}

              @Override
              public void visitLabel(Label label) {}

              @Override
              public void visitLdcInsn(Object value) {}

              @Override
              public void visitTypeInsn(int opcode, String type) {}

              @Override
              public void visitMaxs(int maxStack, int maxLocals) {}

              @Override
              public void visitEnd() {}

              @Override
              public void visitFrame(int t, int nL, Object[] l, int nS, Object[] s) {}

              @Override
              public void visitLineNumber(int line, Label start) {}

              @Override
              public void visitLocalVariable(
                  String n, String d, String s, Label st, Label en, int i) {}

              @Override
              public void visitTryCatchBlock(Label s, Label e, Label h, String t) {}

              @Override
              public void visitTableSwitchInsn(int min, int max, Label d, Label... l) {}

              @Override
              public void visitLookupSwitchInsn(Label d, int[] k, Label[] l) {}

              @Override
              public void visitMultiANewArrayInsn(String d, int n) {}

              @Override
              public void visitIincInsn(int var, int inc) {}

              @Override
              public void visitIntInsn(int opcode, int operand) {}
            };
          }
        };
  }

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

  /** Adds a new static method to the class. Body delegates to another static. */
  public static Function<ClassVisitor, ClassVisitor> addMethod(
      int access,
      String name,
      String desc,
      String targetOwner,
      String targetName,
      String targetDesc) {
    return addMethod(
        access,
        name,
        desc,
        mv -> {
          int slot = 0;
          if ((access & Opcodes.ACC_STATIC) == 0) {
            mv.visitVarInsn(Opcodes.ALOAD, slot++);
          }
          for (Type argType : Type.getArgumentTypes(desc)) {
            mv.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), slot);
            slot += argType.getSize();
          }
          mv.visitMethodInsn(Opcodes.INVOKESTATIC, targetOwner, targetName, targetDesc, false);
          mv.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
        });
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
            return new MethodVisitor(Opcodes.ASM9, mv) {
              boolean skipping = false;

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean itf) {
                if (skipping
                    && owner.equals(anchorOwner)
                    && name.equals(anchorName)
                    && desc.equals(anchorDesc)) {
                  // Reached anchor: stop skipping, emit the anchor call normally
                  skipping = false;
                  super.visitMethodInsn(opcode, owner, name, desc, itf);
                } else if (!skipping
                    && owner.equals(victimOwner)
                    && name.equals(victimName)
                    && desc.equals(victimDesc)) {
                  // Emit replacement call, start skipping
                  super.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      replacementOwner,
                      replacementName,
                      replacementDesc,
                      false);
                  skipping = true;
                } else if (!skipping) {
                  super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
              }

              // Swallow all bytecode while skipping
              @Override
              public void visitInsn(int opcode) {
                if (!skipping) super.visitInsn(opcode);
              }

              @Override
              public void visitVarInsn(int opcode, int var) {
                if (!skipping) super.visitVarInsn(opcode, var);
              }

              @Override
              public void visitFieldInsn(int op, String o, String n, String d) {
                if (!skipping) super.visitFieldInsn(op, o, n, d);
              }

              @Override
              public void visitJumpInsn(int opcode, Label label) {
                if (!skipping) super.visitJumpInsn(opcode, label);
              }

              @Override
              public void visitLabel(Label label) {
                if (!skipping) super.visitLabel(label);
              }

              @Override
              public void visitLdcInsn(Object value) {
                if (!skipping) super.visitLdcInsn(value);
              }

              @Override
              public void visitTypeInsn(int opcode, String type) {
                if (!skipping) super.visitTypeInsn(opcode, type);
              }

              @Override
              public void visitIntInsn(int opcode, int operand) {
                if (!skipping) super.visitIntInsn(opcode, operand);
              }

              @Override
              public void visitIincInsn(int var, int inc) {
                if (!skipping) super.visitIincInsn(var, inc);
              }

              @Override
              public void visitTableSwitchInsn(int min, int max, Label d, Label... l) {
                if (!skipping) super.visitTableSwitchInsn(min, max, d, l);
              }

              @Override
              public void visitLookupSwitchInsn(Label d, int[] k, Label[] l) {
                if (!skipping) super.visitLookupSwitchInsn(d, k, l);
              }

              @Override
              public void visitMultiANewArrayInsn(String d, int n) {
                if (!skipping) super.visitMultiANewArrayInsn(d, n);
              }

              @Override
              public void visitFrame(int t, int nL, Object[] l, int nS, Object[] s) {
                if (!skipping) super.visitFrame(t, nL, l, nS, s);
              }

              @Override
              public void visitLineNumber(int line, Label start) {
                if (!skipping) super.visitLineNumber(line, start);
              }

              @Override
              public void visitTryCatchBlock(Label s, Label e, Label h, String t) {
                // Always emit try-catch blocks — they reference labels that may
                // span the entire method. Orphaned ranges are harmless under -noverify.
                super.visitTryCatchBlock(s, e, h, t);
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
              boolean started;

              @Override
              public void visitCode() {
                if (!started) {
                  started = true;
                  out.visitCode();
                  emitter.accept(out);
                  out.visitMaxs(0, 0);
                  out.visitEnd();
                }
              }

              @Override
              public void visitEnd() {}
            };
          }
        };
  }
}
