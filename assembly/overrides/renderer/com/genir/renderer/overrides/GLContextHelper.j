.version 61 0
.class public com/genir/renderer/overrides/GLContextHelper
.super java/lang/Object

; Shared drawable for the main thread's GL context.
.field private static sharedDrawable Lorg/lwjgl/opengl/Drawable;

; Create a shared GL context on the calling thread.
; This allows mods that call LWJGL directly (e.g. GraphicsLib)
; to find a valid GL context during onApplicationLoad.
.method public static makeCurrent : ()V
    .code stack 3 locals 1
        ; Display.getDrawable()
        invokestatic Method org/lwjgl/opengl/Display getDrawable ()Lorg/lwjgl/opengl/Drawable;
        astore_0

        ; new SharedDrawable(drawable)
        new org/lwjgl/opengl/SharedDrawable
        dup
        aload_0
        invokespecial Method org/lwjgl/opengl/SharedDrawable <init> (Lorg/lwjgl/opengl/Drawable;)V

        ; Store and make current
        dup
        putstatic Field com/genir/renderer/overrides/GLContextHelper sharedDrawable Lorg/lwjgl/opengl/Drawable;
        invokeinterface InterfaceMethod org/lwjgl/opengl/Drawable makeCurrent ()V 1
        return
    .end code
.end method

; Release the shared GL context from the calling thread.
.method public static releaseCurrent : ()V
    .code stack 1 locals 0
        getstatic Field com/genir/renderer/overrides/GLContextHelper sharedDrawable Lorg/lwjgl/opengl/Drawable;
        ifnull L_end
        getstatic Field com/genir/renderer/overrides/GLContextHelper sharedDrawable Lorg/lwjgl/opengl/Drawable;
        invokeinterface InterfaceMethod org/lwjgl/opengl/Drawable releaseContext ()V 1
L_end:
        return
    .end code
.end method

.end class
