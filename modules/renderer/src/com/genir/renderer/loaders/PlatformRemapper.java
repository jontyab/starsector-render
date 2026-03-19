package com.genir.renderer.loaders;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * ASM-based remapper that handles class, method, and field name remapping
 * using platform-specific mapping tables. Replaces the build-time
 * rewrite_j_files.py + ClassConstantTransformer approach.
 */
public class PlatformRemapper extends Remapper {
    private final Map<String, String> classMap = new HashMap<>();
    // Key: "owner\tname", Value: target name
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();

    public PlatformRemapper() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String platform = os.contains("mac") ? "macos"
                        : os.contains("linux") ? "linux"
                        : "windows";

        // On Windows, no remapping needed for assembly overrides
        if (platform.equals("windows")) return;

        loadClassMappings(platform);
        loadMemberMappings(platform);
    }

    public boolean isEmpty() {
        return classMap.isEmpty() && fieldMap.isEmpty() && methodMap.isEmpty();
    }

    private void loadClassMappings(String platform) {
        String path = "/mappings/asm_classes_" + platform + ".tsv";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return;
            new BufferedReader(new InputStreamReader(is)).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .forEach(l -> {
                        String[] p = l.split("\t", 2);
                        if (p.length == 2) classMap.put(p[0], p[1]);
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }

    private void loadMemberMappings(String platform) {
        String path = "/mappings/asm_members_" + platform + ".tsv";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return;
            new BufferedReader(new InputStreamReader(is)).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .forEach(l -> {
                        String[] p = l.split("\t");
                        if (p.length < 3) return;
                        String owner = p[0];
                        String from = stripQuotes(p[1]);
                        String to = stripQuotes(p[2]);
                        String kind = p.length >= 4 ? p[3] : "both";
                        String key = owner + "\t" + from;
                        if (kind.equals("field") || kind.equals("both")) fieldMap.put(key, to);
                        if (kind.equals("method") || kind.equals("both")) methodMap.put(key, to);
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'"))
            return s.substring(1, s.length() - 1);
        return s;
    }

    @Override
    public String map(String internalName) {
        return classMap.getOrDefault(internalName, internalName);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String mapped = fieldMap.get(owner + "\t" + name);
        if (mapped != null) return mapped;
        // Try with remapped owner (in case the reference uses the old class name)
        String remappedOwner = map(owner);
        if (!remappedOwner.equals(owner)) {
            mapped = fieldMap.get(remappedOwner + "\t" + name);
            if (mapped != null) return mapped;
        }
        return name;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        String mapped = methodMap.get(owner + "\t" + name);
        if (mapped != null) return mapped;
        String remappedOwner = map(owner);
        if (!remappedOwner.equals(owner)) {
            mapped = methodMap.get(remappedOwner + "\t" + name);
            if (mapped != null) return mapped;
        }
        return name;
    }

    /** Apply this remapper to class bytecode, skipping self-rename. */
    public byte[] remap(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        String thisClass = reader.getClassName();
        ClassWriter writer = new ClassWriter(0);
        Remapper wrapper = new Remapper() {
            @Override public String map(String n) {
                return n.equals(thisClass) ? n : PlatformRemapper.this.map(n);
            }
            @Override public String mapFieldName(String o, String n, String d) {
                return PlatformRemapper.this.mapFieldName(o, n, d);
            }
            @Override public String mapMethodName(String o, String n, String d) {
                return PlatformRemapper.this.mapMethodName(o, n, d);
            }
        };
        reader.accept(new ClassRemapper(writer, wrapper), 0);
        return writer.toByteArray();
    }
}
