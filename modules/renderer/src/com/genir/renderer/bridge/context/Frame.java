package com.genir.renderer.bridge.context;

import com.genir.renderer.bridge.commands.GLSync;
import com.genir.renderer.bridge.interfaces.GLCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Frame {
    public final static int ARGS_NUM = 4;

    public GLCommand[] commands = new GLCommand[1];
    public float[] args = new float[ARGS_NUM];
    public int commandsSize = 0;

    public List<GLSync> fences = new ArrayList<>();

    public int add(GLCommand command) {
        if (commands.length <= commandsSize) {
            commands = Arrays.copyOf(commands, commands.length * 2);
            args = Arrays.copyOf(args, args.length * 2);
        }

        commands[commandsSize] = command;
        commandsSize++;

        return (commandsSize - 1) * ARGS_NUM;
    }

    public void clear() {
        commandsSize = 0;
        fences.clear();
    }
}
