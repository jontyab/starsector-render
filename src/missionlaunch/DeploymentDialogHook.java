package missionlaunch;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/**
 * Hooks CombatEngine.isShowDeploymentDialog() to return false when -DlaunchMission is set.
 * Skips deployment screen so missions auto-start immediately.
 */
public class DeploymentDialogHook implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        
        if (!"com/fs/starfarer/combat/CombatEngine".equals(className)) return null;
        
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if ("isShowDeploymentDialog".equals(name) && "()Z".equals(desc)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            // if (System.getProperty("launchMission") != null) return false;
                            mv.visitLdcInsn("launchMission");
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", 
                                "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            Label cont = new Label();
                            mv.visitJumpInsn(Opcodes.IFNULL, cont);
                            mv.visitInsn(Opcodes.ICONST_0);
                            mv.visitInsn(Opcodes.IRETURN);
                            mv.visitLabel(cont);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }
}
