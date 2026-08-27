package com.genir.renderer.bridge.context;

import com.genir.renderer.bridge.commands.GLSync;
import com.genir.renderer.bridge.interfaces.GLCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Frame {
    public GLCommand[] commands = new GLCommand[1];
    public int commandsSize = 0;

    public float[] args = new float[5];
    public int argsOffset = 0;

    public List<GLSync> fences = new ArrayList<>();

    public void add(GLCommand command) {
        if (commands.length <= commandsSize) {
            commands = Arrays.copyOf(commands, commands.length * 2);
            args = Arrays.copyOf(args, args.length * 2);
        }

        commands[commandsSize] = command;
        commandsSize++;
    }

    public void clear() {
        commandsSize = 0;
        argsOffset = 0;
        fences.clear();
    }
}
