package com.genir.renderer.bridge.context;

import org.lwjgl.BufferUtils;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class BufferUtil {
    public static FloatBuffer reallocate(int size, FloatBuffer old) {
        FloatBuffer n = BufferUtils.createFloatBuffer(size);

        n.put(0, old, 0, Math.min(size, old.limit()));
        n.position(Math.min(size, old.position()));
        return n;
    }

    public static int capacityRequired(Buffer buffer, int elements) {
        int capacityRequired = buffer.position() + elements;
        int capacity = buffer.capacity();

        // Resize only if needed.
        if (capacity >= capacityRequired) {
            return -1;
        }

        do {
            capacity *= 2;
        } while (capacity < capacityRequired);

        return capacity;
    }

    public static void putIfPossible(IntBuffer buffer, int element) {
        if (buffer != null && buffer.hasRemaining()) {
            buffer.put(element);
        }
    }
}
