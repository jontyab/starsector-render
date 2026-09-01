package com.genir.renderer.overrides.loading.textures;

import java.util.Set;

/**
 * Textures that should use vanilla processing without optimizations to avoid visual errors.
 */
public class Blacklist {
    private static final Set<String> doNotModify = Set.of(
            // Prevent vertical bar visual error in campaign radar.
            normalize("graphics/ui/bottomright_all.png"),
            normalize("graphics/ui/bottomright_all_course_on.png"),
            normalize("graphics/ui/bottomright_all_radar_off.png"),
            normalize("graphics/ui/bottomright_radar_holder.png"),
            normalize("graphics/ui/bottomright_radar_projection.png")
    );

    private static String normalize(String path) {
        return path.replace("\\", "/");
    }

    public static boolean doNotModify(String path) {
        return doNotModify.contains(normalize(path));
    }
}
